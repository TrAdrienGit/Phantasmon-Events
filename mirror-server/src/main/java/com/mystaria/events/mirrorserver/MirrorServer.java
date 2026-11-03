package com.mystaria.events.mirrorserver;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Phantasmon Events' mirror copies, built in <b>this dedicated server's</b> world (Adrien 2026-11-02, our own server:
 * the copy is no longer a single-player world). It does what the admin's client did in single player
 * ({@code MirrorCopy}): the copy anchors posed on this server (by an admin, as before) are listed from the backend, their
 * terrain fetched tile by tile (16 × 16, the whole height), then every change followed, one way only — but by polling
 * the backend ({@code /server/mirror/...}, a shared token) instead of a player's WebSocket, so it works with nobody
 * connected. Blocks are written on the server thread, at most {@value #BUDGET_MILLIS} ms of each tick.
 *
 * <p><b>Ghost blocks</b>: what an operator places or breaks by hand in a copy ({@link HandEdits}) is sent to the backend
 * and shown, client-only, in the source zone and the other copies — as from the admin's single-player copy before.
 * Breaking a ghost removes it; breaking a copied block stays here until it changes in the source.
 *
 * <p>Configuration: {@code config/phantasmon-mirror-server.json} — {@code backend_url}, {@code token}, and
 * {@code address}: this server's address as its players type it (the anchors are keyed by its fingerprint, as
 * Phantasmon computes it on the client). Without a token, the mod does nothing.
 */
public final class MirrorServer implements DedicatedServerModInitializer {

	private static final Logger LOG = LoggerFactory.getLogger("phantasmon-mirror-server");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	static final int MIN_Y = -64;
	static final int HEIGHT = 384;
	static final int TILE = 16;
	static final int TILE_VOLUME = TILE * TILE * HEIGHT;
	private static final int LIST_TICKS = 200;
	private static final int POLL_TICKS = 20;
	private static final int MAX_FETCHING = 2;
	private static final int MAX_WAITING = 4;
	private static final long BUDGET_MILLIS = 15;

	/** {@code config/phantasmon-mirror-server.json}. */
	static final class Config {
		String backend_url = "https://server-linux.example.ts.net";
		String token = "";
		String address = "";
	}

	/** Blocks to write in one dimension. */
	private record Write(ResourceKey<Level> dimension, List<BlockPos> positions, List<Parsed> states) {
	}

	/** A block to write: its state, and its block entity's data or null. */
	private record Parsed(BlockState state, CompoundTag nbt) {
	}

	private static final class Copy {
		final String hub;
		final ResourceKey<Level> dimension;
		final int minX;
		final int minZ;
		final int side;
		final Set<Long> toFetch = new LinkedHashSet<>();
		int fetching;
		/** Tile → version of the last blocks changed in it: an older tile fetched is fetched again. */
		final Map<Long, Long> changedAt = new HashMap<>();
		/** Ghost blocks, zone-relative {@code BlockPos.asLong} → state: tiles written leave them be. */
		final Map<Long, String> ghosts = new HashMap<>();
		/** Ghost blocks placed or broken here, to send. */
		final List<List<Object>> ghostsOut = new ArrayList<>();
		/** The last feed message read; -1 while starting. */
		long since = -1;
		boolean polling;

		Copy(String hub, ResourceKey<Level> dimension, int minX, int minZ, int side) {
			this.hub = hub;
			this.dimension = dimension;
			this.minX = minX;
			this.minZ = minZ;
			this.side = side;
		}

		boolean inside(int x, int z) {
			return x >= 0 && z >= 0 && x < side && z < side;
		}

		boolean sameZone(Copy other) {
			return dimension.equals(other.dimension) && minX == other.minX && minZ == other.minZ && side == other.side;
		}
	}

	private final Map<String, Copy> copies = new ConcurrentHashMap<>();
	private final Deque<Write> writes = new ArrayDeque<>();
	private final Map<String, Parsed> parsed = new ConcurrentHashMap<>();
	private HttpClient http;
	private Config config;
	private String fingerprint;
	private MinecraftServer server;
	private int ticks;
	private boolean listing;
	private int cursor;

	@Override
	public void onInitializeServer() {
		config = loadConfig();
		if (config.token == null || config.token.isBlank() || config.address == null || config.address.isBlank()) {
			LOG.warn("No token or address in config/phantasmon-mirror-server.json: mirror copies are not built");
			return;
		}
		fingerprint = sha256Hex(normalizedAddress(config.address));
		http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
		ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> server = null);
		ServerTickEvents.END_SERVER_TICK.register(this::tick);
		HandEdits.listen(this::onHandEdit);
		LOG.info("Mirror copies of this server ({}) built from {}", config.address, config.backend_url);
	}

	private static Config loadConfig() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("phantasmon-mirror-server.json");
		try {
			if (Files.exists(file)) {
				try (Reader reader = Files.newBufferedReader(file)) {
					Config read = GSON.fromJson(reader, Config.class);
					if (read != null) {
						return read;
					}
				}
			}
			Config fresh = new Config();
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(fresh, writer);
			}
			return fresh;
		} catch (IOException | RuntimeException ex) {
			LOG.warn("Unreadable {}", file, ex);
			return new Config();
		}
	}

	/** As Phantasmon's client: lower case, no default port, no trailing dot. */
	static String normalizedAddress(String address) {
		String normalized = address.strip().toLowerCase(Locale.ROOT);
		if (normalized.endsWith(":25565")) {
			normalized = normalized.substring(0, normalized.length() - ":25565".length());
		}
		while (normalized.endsWith(".")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}

	static String sha256Hex(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	// ---------------------------------------------------------------- server thread

	private void tick(MinecraftServer tickServer) {
		ticks++;
		if (ticks % LIST_TICKS == 1 && !listing) {
			list();
		}
		for (Copy copy : copies.values()) {
			if (copy.since >= 0 && !copy.polling && ticks % POLL_TICKS == 0) {
				poll(copy);
			}
			while (copy.fetching < MAX_FETCHING && writes.size() < MAX_WAITING && !copy.toFetch.isEmpty()) {
				long key = copy.toFetch.iterator().next();
				copy.toFetch.remove(key);
				fetchTile(copy, (int) (key >> 32), (int) key);
			}
		}
		if (ticks % 10 == 0) {
			copies.values().forEach(this::flushGhosts);
		}
		write(tickServer);
	}

	/** An operator changed a block by hand: in a copy, it becomes a ghost block (or a broken ghost goes). */
	private void onHandEdit(HandEdits.Edit edit) {
		BlockState state = edit.level().getBlockState(edit.pos());
		String name = state.isAir() ? "" : name(edit.level(), edit.pos(), state);
		for (Copy copy : copies.values()) {
			if (copy.since < 0 || !copy.dimension.equals(edit.level().dimension())) {
				continue;
			}
			int x = edit.pos().getX() - copy.minX;
			int y = edit.pos().getY() - MIN_Y;
			int z = edit.pos().getZ() - copy.minZ;
			if (!copy.inside(x, z) || y < 0 || y >= HEIGHT) {
				continue;
			}
			long key = BlockPos.asLong(x, y, z);
			if (name.isEmpty()) {
				if (copy.ghosts.remove(key) != null) {
					copy.ghostsOut.add(List.of(x, y, z, ""));
				}
			} else if (!name.equals(copy.ghosts.put(key, name))) {
				copy.ghostsOut.add(List.of(x, y, z, name));
			}
		}
	}

	private void flushGhosts(Copy copy) {
		if (copy.ghostsOut.isEmpty()) {
			return;
		}
		List<List<Object>> out = new ArrayList<>(copy.ghostsOut);
		copy.ghostsOut.clear();
		for (int from = 0; from < out.size(); from += 4096) {
			post("/server/mirror/" + encode(copy.hub) + "/ghosts", Map.of("changes", out.subList(from, Math.min(out.size(), from + 4096))))
					.exceptionally(ex -> {
						LOG.warn("Ghost blocks of mirror hub {} not sent", copy.hub, ex);
						return null;
					});
		}
	}

	/** The block as a string, with its block entity's data if it has some (as the clients send them). */
	private static String name(ServerLevel level, BlockPos pos, BlockState state) {
		String name = BlockStateParser.serialize(state);
		if (!state.hasBlockEntity()) {
			return name;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity == null) {
			return name;
		}
		CompoundTag tag = blockEntity.saveWithoutMetadata(level.registryAccess());
		return tag.isEmpty() ? name : name + tag;
	}

	/** Writes queued blocks for at most {@value #BUDGET_MILLIS} ms. */
	private void write(MinecraftServer tickServer) {
		long end = System.nanoTime() + BUDGET_MILLIS * 1_000_000;
		while (!writes.isEmpty() && System.nanoTime() < end) {
			Write head = writes.peek();
			ServerLevel level = tickServer.getLevel(head.dimension());
			if (level == null) {
				writes.poll();
				cursor = 0;
				continue;
			}
			int stop = Math.min(head.positions().size(), cursor + 512);
			for (int i = cursor; i < stop; i++) {
				BlockPos pos = head.positions().get(i);
				Parsed block = head.states().get(i);
				if (!level.isInWorldBounds(pos)) {
					continue;
				}
				if (level.getBlockState(pos) != block.state()) {
					level.setBlock(pos, block.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
				}
				if (block.nbt() != null) {
					BlockEntity blockEntity = level.getBlockEntity(pos);
					if (blockEntity != null) {
						blockEntity.loadWithComponents(block.nbt(), level.registryAccess());
						blockEntity.setChanged();
						level.sendBlockUpdated(pos, block.state(), block.state(), Block.UPDATE_CLIENTS);
					}
				}
			}
			cursor = stop;
			if (cursor >= head.positions().size()) {
				writes.poll();
				cursor = 0;
			}
		}
	}

	/** Runs {@code task} on the server thread (if the server still runs). */
	private void onServer(Runnable task) {
		MinecraftServer current = server;
		if (current != null) {
			current.execute(task);
		}
	}

	// ---------------------------------------------------------------- backend

	private CompletableFuture<JsonElement> get(String path) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(config.backend_url.replaceAll("/+$", "") + path))
				.timeout(Duration.ofSeconds(30)).header("X-Phantasmon-Server-Token", config.token.strip()).GET().build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
			if (response.statusCode() != 200) {
				throw new IllegalStateException("HTTP " + response.statusCode() + " on " + path);
			}
			return JsonParser.parseString(response.body());
		});
	}

	private CompletableFuture<JsonElement> post(String path, Object body) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(config.backend_url.replaceAll("/+$", "") + path))
				.timeout(Duration.ofSeconds(30)).header("X-Phantasmon-Server-Token", config.token.strip())
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
			if (response.statusCode() != 200) {
				throw new IllegalStateException("HTTP " + response.statusCode() + " on " + path);
			}
			return JsonParser.parseString(response.body());
		});
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	/** The copy anchors of this server: new ones started, deleted or moved ones stopped (and started again). */
	private void list() {
		listing = true;
		get("/server/mirror/copies?fingerprint=" + fingerprint).whenComplete((json, error) -> onServer(() -> {
			listing = false;
			if (error != null) {
				LOG.debug("Mirror copies not listed", error);
				return;
			}
			Set<String> here = new HashSet<>();
			for (JsonElement element : json.getAsJsonArray()) {
				JsonObject o = element.getAsJsonObject();
				ResourceLocation dimension = ResourceLocation.tryParse(o.get("dimension").getAsString());
				if (dimension == null) {
					continue;
				}
				Copy wanted = new Copy(o.get("hub").getAsString(), ResourceKey.create(Registries.DIMENSION, dimension),
						o.get("min_x").getAsInt(), o.get("min_z").getAsInt(), o.get("side").getAsInt());
				here.add(wanted.hub);
				Copy known = copies.get(wanted.hub);
				if (known == null || !known.sameZone(wanted)) {
					start(wanted);
				}
			}
			copies.keySet().removeIf(hub -> !here.contains(hub));
		}));
	}

	/** Follows the feed from now, then fetches every known tile and the ghost blocks. */
	private void start(Copy copy) {
		copies.put(copy.hub, copy);
		String hub = encode(copy.hub);
		get("/server/mirror/" + hub + "/feed?since=0").thenCompose(feed -> {
			long next = feed.getAsJsonObject().get("next").getAsLong();
			return get("/server/mirror/" + hub).thenCompose(info -> get("/server/mirror/" + hub + "/ghosts")
					.thenApply(ghosts -> new JsonElement[] { info, ghosts, new com.google.gson.JsonPrimitive(next) }));
		}).whenComplete((all, error) -> onServer(() -> {
			if (copies.get(copy.hub) != copy) {
				return;
			}
			if (error != null) {
				copies.remove(copy.hub, copy);
				LOG.warn("Could not start the copy of mirror hub {}", copy.hub, error);
				return;
			}
			JsonObject info = all[0].getAsJsonObject();
			if (info.get("side").getAsInt() != copy.side) {
				copies.remove(copy.hub, copy); // resized meanwhile: the next listing starts it again
				return;
			}
			receiveGhosts(copy, all[1].getAsJsonArray());
			JsonArray tiles = info.getAsJsonArray("tiles");
			for (JsonElement tile : tiles) {
				JsonArray t = tile.getAsJsonArray();
				copy.toFetch.add(key(t.get(0).getAsInt(), t.get(1).getAsInt()));
			}
			copy.since = all[2].getAsLong();
			LOG.info("Mirror hub {}: copy of {} tiles being built in {}", copy.hub, tiles.size(), copy.dimension.location());
		}));
	}

	private void poll(Copy copy) {
		copy.polling = true;
		get("/server/mirror/" + encode(copy.hub) + "/feed?since=" + copy.since).whenComplete((json, error) -> onServer(() -> {
			copy.polling = false;
			if (copies.get(copy.hub) != copy || error != null) {
				return;
			}
			JsonObject feed = json.getAsJsonObject();
			if (feed.get("reset").getAsBoolean()) {
				LOG.info("Mirror hub {}: changes missed, building the copy again", copy.hub);
				start(new Copy(copy.hub, copy.dimension, copy.minX, copy.minZ, copy.side));
				return;
			}
			for (JsonElement element : feed.getAsJsonArray("messages")) {
				JsonObject message = element.getAsJsonObject();
				JsonObject data = message.getAsJsonObject("data");
				switch (message.get("type").getAsString()) {
					case "EventsMirrorBlocks" -> receiveBlocks(copy, data.get("version").getAsLong(), data.getAsJsonArray("changes"));
					case "EventsMirrorGhosts" -> receiveGhosts(copy, data.getAsJsonArray("changes"));
					case "EventsMirrorTileChanged" -> copy.toFetch.add(key(data.get("tx").getAsInt(), data.get("tz").getAsInt()));
					case "EventsMirrorReset" -> {
						copies.remove(copy.hub, copy); // resized: the next listing starts it again with the new size
						return;
					}
					default -> {
					}
				}
			}
			copy.since = feed.get("next").getAsLong();
		}));
	}

	private void fetchTile(Copy copy, int tx, int tz) {
		copy.fetching++;
		get("/server/mirror/" + encode(copy.hub) + "/tile/" + tx + "/" + tz).thenApply(json -> {
			// Decoded off the server thread: up to 98 304 blocks.
			JsonObject tile = json.getAsJsonObject();
			return new Object[] { tile.get("version").getAsLong(), tile.getAsJsonArray("palette"), ids(tile.get("blocks").getAsString()) };
		}).whenComplete((tile, error) -> onServer(() -> {
			copy.fetching--;
			if (copies.get(copy.hub) != copy) {
				return;
			}
			if (error != null) {
				LOG.warn("Could not fetch a tile of mirror hub {}", copy.hub, error);
				return;
			}
			long key = key(tx, tz);
			if (copy.changedAt.getOrDefault(key, Long.MIN_VALUE) > (long) tile[0]) {
				copy.toFetch.add(key); // older than blocks already written: fetched again
				return;
			}
			List<Parsed> palette = new ArrayList<>();
			for (JsonElement name : (JsonArray) tile[1]) {
				palette.add(parse(name.getAsString()));
			}
			short[] ids = (short[]) tile[2];
			List<BlockPos> positions = new ArrayList<>();
			List<Parsed> states = new ArrayList<>();
			int i = 0;
			for (int y = 0; y < HEIGHT; y++) {
				for (int z = 0; z < TILE; z++) {
					for (int x = 0; x < TILE; x++, i++) {
						short id = ids[i];
						if (id >= 0 && id < palette.size()
								&& !copy.ghosts.containsKey(BlockPos.asLong(tx * TILE + x, y, tz * TILE + z))) {
							positions.add(new BlockPos(copy.minX + tx * TILE + x, MIN_Y + y, copy.minZ + tz * TILE + z));
							states.add(palette.get(id));
						}
					}
				}
			}
			writes.add(new Write(copy.dimension, positions, states));
		}));
	}

	private void receiveBlocks(Copy copy, long version, JsonArray changes) {
		List<BlockPos> positions = new ArrayList<>();
		List<Parsed> states = new ArrayList<>();
		for (JsonElement element : changes) {
			JsonArray c = element.getAsJsonArray();
			int x = c.get(0).getAsInt();
			int y = c.get(1).getAsInt();
			int z = c.get(2).getAsInt();
			if (!copy.inside(x, z)) {
				continue;
			}
			positions.add(new BlockPos(copy.minX + x, MIN_Y + y, copy.minZ + z));
			states.add(parse(c.get(3).getAsString()));
			copy.changedAt.put(key(x / TILE, z / TILE), version);
		}
		if (!positions.isEmpty()) {
			writes.add(new Write(copy.dimension, positions, states));
		}
	}

	/** {@code [x, y, z, state]} zone-relative; {@code ""} removes the ghost and gives its tile back. */
	private void receiveGhosts(Copy copy, JsonArray changes) {
		List<BlockPos> positions = new ArrayList<>();
		List<Parsed> states = new ArrayList<>();
		for (JsonElement element : changes) {
			JsonArray c = element.getAsJsonArray();
			int x = c.get(0).getAsInt();
			int y = c.get(1).getAsInt();
			int z = c.get(2).getAsInt();
			String state = c.get(3).getAsString();
			if (!copy.inside(x, z) || y < 0 || y >= HEIGHT) {
				continue;
			}
			if (state.isEmpty()) {
				copy.ghosts.remove(BlockPos.asLong(x, y, z));
				copy.toFetch.add(key(x / TILE, z / TILE));
			} else {
				copy.ghosts.put(BlockPos.asLong(x, y, z), state);
				positions.add(new BlockPos(copy.minX + x, MIN_Y + y, copy.minZ + z));
				states.add(parse(state));
			}
		}
		if (!positions.isEmpty()) {
			writes.add(new Write(copy.dimension, positions, states));
		}
	}

	// ---------------------------------------------------------------- terrain format (as MirrorTerrain on the client)

	static long key(int tx, int tz) {
		return ((long) tx << 32) | (tz & 0xffffffffL);
	}

	/** A tile's ids: {@value #TILE_VOLUME} int16 (order y, z, x; -1 unknown), gzipped, base64. */
	static short[] ids(String blocks) {
		short[] ids = new short[TILE_VOLUME];
		try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(blocks))))) {
			for (int i = 0; i < ids.length; i++) {
				ids[i] = in.readShort();
			}
		} catch (IOException ex) {
			throw new IllegalStateException("Unreadable mirror tile", ex);
		}
		return ids;
	}

	/** A block state string, with block entity data as SNBT after it; air for a block this server lacks. */
	private Parsed parse(String name) {
		return parsed.computeIfAbsent(name, key -> {
			try {
				BlockStateParser.BlockResult result = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), key, true);
				return new Parsed(result.blockState(), result.nbt());
			} catch (CommandSyntaxException ex) {
				return new Parsed(Blocks.AIR.defaultBlockState(), null);
			}
		});
	}
}
