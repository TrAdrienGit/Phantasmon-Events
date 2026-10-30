package com.mystaria.events.hub;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;

/**
 * Builds the copies of the mirror hubs (D-38) in a single-player world, in real blocks (choice of Adrien): the player's
 * COPY anchors of this world fetch the backend's terrain tile by tile (16 × 16, the whole height), then follow every
 * change sent by the server's players, one way only. Same heights as on the server (y -64 to 319), whatever the height
 * the anchor was posed at; never turned. A block changed by hand in the copy stays until the same block changes on the
 * server. A resized hub is built again.
 *
 * <p><b>Ghost blocks</b> (Adrien 2026-10-30): what the admin places by hand in the copy ({@link MirrorEdits}) is sent to
 * the backend and shown, client-only, to the players in the source zone ({@link MirrorGhosts}) and in the other
 * copies. Breaking a ghost removes it; breaking a real block stays here. A ghost removed (broken, cleared, or replaced
 * by a real block on the server) gives its tile back from the backend's terrain.
 *
 * <p>Blocks are written by the integrated server, in batches sized to take about {@value #TARGET_MILLIS} ms of each
 * tick, so a large zone fills in progressively without freezing the game.
 */
public final class MirrorCopy {

	private static final Logger LOG = LoggerFactory.getLogger(MirrorCopy.class);
	private static final int REFRESH_TICKS = 200;
	private static final int MAX_FETCHING = 2;
	/** Fetched tiles waiting to be written, at most (memory). */
	private static final int MAX_WAITING = 4;
	private static final long TARGET_MILLIS = 15;
	private static final int GHOST_FLUSH_TICKS = 10;

	/** Blocks to write: a fetched tile, or blocks the server changed. */
	private record Write(List<BlockPos> positions, List<MirrorTerrain.Block> states) {
	}

	private static final class Copy {
		final HubAnchorDto anchor;
		final MirrorTerrain.Zone zone;
		final Set<Long> toFetch = new LinkedHashSet<>();
		int fetching;
		/** Tile → version of the last blocks changed in it: an older tile fetched is fetched again. */
		final Map<Long, Long> changedAt = new HashMap<>();
		/** Ghost blocks, zone-relative {@code BlockPos.asLong} → state: tiles written leave them be. */
		final Map<Long, String> ghosts = new HashMap<>();
		/** Ghost blocks placed or broken here, to send. */
		final List<List<Object>> ghostsOut = new ArrayList<>();

		Copy(HubAnchorDto anchor) {
			this.anchor = anchor;
			this.zone = MirrorTerrain.Zone.of(anchor);
		}
	}

	private static final Map<String, Copy> COPIES = new HashMap<>();
	private static final Deque<Write> WRITES = new ArrayDeque<>();
	/** The block being written in the head of WRITES. */
	private static int cursor;
	private static int batch = 4096;
	private static volatile boolean writing;
	private static int ticks;
	private static boolean listing;

	private MirrorCopy() {
	}

	public static void register() {
		PhantasmonApi.onEventsMessage((type, data) -> {
			Copy copy = COPIES.get(String.valueOf(data.get("hub")));
			if (copy == null) {
				return;
			}
			switch (type) {
				case "EventsMirrorBlocks" -> receiveBlocks(copy, data);
				case "EventsMirrorGhosts" -> {
					if (data.get("changes") instanceof List<?> changes) {
						receiveGhosts(copy, changes);
					}
				}
				case "EventsMirrorTileChanged" -> {
					if (data.get("tx") instanceof Number tx && data.get("tz") instanceof Number tz) {
						copy.toFetch.add(MirrorTerrain.key(tx.intValue(), tz.intValue()));
					}
				}
				case "EventsMirrorReset" -> {
					stop(copy.anchor.hub()); // resized: the next refresh starts it again with the new size
					ticks = 0;
				}
				default -> {
				}
			}
		});
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.hasSingleplayerServer() || !PhantasmonApi.isAuthenticated()) {
			return;
		}
		if (ticks++ % REFRESH_TICKS == 0 && !listing) {
			refresh(mc.player.level().dimension().location().toString());
		}
		for (Copy copy : COPIES.values()) {
			while (copy.fetching < MAX_FETCHING && WRITES.size() < MAX_WAITING && !copy.toFetch.isEmpty()) {
				long key = copy.toFetch.iterator().next();
				copy.toFetch.remove(key);
				fetchTile(copy, (int) (key >> 32), (int) key);
			}
		}
		edits(mc);
		if (ticks % GHOST_FLUSH_TICKS == 0) {
			for (Copy copy : COPIES.values()) {
				flushGhosts(copy);
			}
		}
		write(mc);
	}

	/** Left the world: stop building and listening. */
	public static void reset() {
		for (String hub : List.copyOf(COPIES.keySet())) {
			stop(hub);
		}
		WRITES.clear();
		cursor = 0;
		ticks = 0;
		listing = false;
	}

	private static void stop(String hub) {
		COPIES.remove(hub);
		PhantasmonApi.send("EventsMirrorUnsubscribe", Map.of("hub", hub));
	}

	/** The player's copy anchors in this world: new ones started, deleted or resized ones stopped (and restarted). */
	private static void refresh(String dimension) {
		listing = true;
		String fingerprint = PhantasmonApi.serverFingerprint();
		PhantasmonApi.myAnchors().thenAccept(mine -> Minecraft.getInstance().execute(() -> {
			listing = false;
			Set<String> here = new HashSet<>();
			for (HubAnchorDto anchor : mine) {
				if (!anchor.mirror() || !HubAnchorDto.ROLE_COPY.equals(anchor.role())
						|| !anchor.serverFingerprint().equals(fingerprint) || !anchor.dimension().equals(dimension)) {
					continue;
				}
				here.add(anchor.hub());
				Copy copy = COPIES.get(anchor.hub());
				if (copy != null && !copy.zone.equals(MirrorTerrain.Zone.of(anchor))) {
					stop(anchor.hub());
					copy = null;
				}
				if (copy == null) {
					start(new Copy(anchor));
				}
			}
			for (String hub : List.copyOf(COPIES.keySet())) {
				if (!here.contains(hub)) {
					stop(hub);
				}
			}
		})).exceptionally(ex -> {
			Minecraft.getInstance().execute(() -> listing = false);
			return null;
		});
	}

	private static void start(Copy copy) {
		String hub = copy.anchor.hub();
		COPIES.put(hub, copy);
		// Subscribed first, so no change falls between the tiles fetched and the stream.
		PhantasmonApi.send("EventsMirrorSubscribe", Map.of("hub", hub));
		PhantasmonApi.get("/events/mirror/" + hub + "/ghosts", List.class)
				.thenAccept(list -> Minecraft.getInstance().execute(() -> {
					if (COPIES.get(hub) == copy) {
						receiveGhosts(copy, list);
					}
				}))
				.exceptionally(ex -> {
					LOG.warn("Could not fetch the ghost blocks of mirror hub {}", hub, ex);
					return null;
				});
		PhantasmonApi.get("/events/mirror/" + hub, MirrorTerrain.Info.class)
				.thenAccept(info -> Minecraft.getInstance().execute(() -> {
					if (COPIES.get(hub) != copy) {
						return;
					}
					if (info.side() != copy.zone.side()) {
						COPIES.remove(hub); // resized meanwhile: the next refresh starts again
						return;
					}
					for (long[] tile : info.tiles()) {
						copy.toFetch.add(MirrorTerrain.key((int) tile[0], (int) tile[1]));
					}
					LOG.info("Mirror hub {}: copy of {} tiles being built", hub, info.tiles().size());
				}))
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> COPIES.remove(hub, copy));
					LOG.warn("Could not fetch the terrain of mirror hub {}", hub, ex);
					return null;
				});
	}

	private static void fetchTile(Copy copy, int tx, int tz) {
		String hub = copy.anchor.hub();
		copy.fetching++;
		PhantasmonApi.get("/events/mirror/" + hub + "/tile/" + tx + "/" + tz, MirrorTerrain.TileView.class)
				.thenAccept(tile -> {
					// Decoded off the client thread: up to 98 304 blocks.
					short[] ids = tile.ids();
					Minecraft.getInstance().execute(() -> {
						copy.fetching--;
						if (COPIES.get(hub) != copy) {
							return;
						}
						long key = MirrorTerrain.key(tx, tz);
						if (copy.changedAt.getOrDefault(key, Long.MIN_VALUE) > tile.version()) {
							copy.toFetch.add(key); // older than blocks already written: fetched again
							return;
						}
						List<MirrorTerrain.Block> palette = new ArrayList<>();
						tile.palette().forEach(name -> palette.add(MirrorTerrain.block(name)));
						List<BlockPos> positions = new ArrayList<>();
						List<MirrorTerrain.Block> states = new ArrayList<>();
						int ox = copy.zone.minX() + tx * MirrorTerrain.TILE;
						int oz = copy.zone.minZ() + tz * MirrorTerrain.TILE;
						int i = 0;
						for (int y = 0; y < MirrorTerrain.HEIGHT; y++) {
							for (int z = 0; z < MirrorTerrain.TILE; z++) {
								for (int x = 0; x < MirrorTerrain.TILE; x++, i++) {
									short id = ids[i];
									if (id >= 0 && id < palette.size() && (copy.ghosts.isEmpty()
											|| !copy.ghosts.containsKey(BlockPos.asLong(tx * MirrorTerrain.TILE + x, y, tz * MirrorTerrain.TILE + z)))) {
										positions.add(new BlockPos(ox + x, MirrorTerrain.MIN_Y + y, oz + z));
										states.add(palette.get(id));
									}
								}
							}
						}
						WRITES.add(new Write(positions, states));
					});
				})
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> copy.fetching--);
					LOG.warn("Could not fetch a tile of mirror hub {}", hub, ex);
					return null;
				});
	}

	private static void receiveBlocks(Copy copy, Map<String, Object> data) {
		long version = data.get("version") instanceof Number number ? number.longValue() : 0;
		if (!(data.get("changes") instanceof List<?> changes)) {
			return;
		}
		List<BlockPos> positions = new ArrayList<>();
		List<MirrorTerrain.Block> states = new ArrayList<>();
		for (Object raw : changes) {
			if (raw instanceof List<?> change && change.size() == 4 && change.get(0) instanceof Number x
					&& change.get(1) instanceof Number y && change.get(2) instanceof Number z && change.get(3) instanceof String state
					&& copy.zone.inside(x.intValue(), z.intValue())) {
				positions.add(new BlockPos(copy.zone.minX() + x.intValue(), MirrorTerrain.MIN_Y + y.intValue(),
						copy.zone.minZ() + z.intValue()));
				states.add(MirrorTerrain.block(state));
				copy.changedAt.put(MirrorTerrain.key(x.intValue() / MirrorTerrain.TILE, z.intValue() / MirrorTerrain.TILE), version);
			}
		}
		if (!positions.isEmpty()) {
			WRITES.add(new Write(positions, states));
		}
	}

	/** {@code [x, y, z, state]} zone-relative; {@code ""} removes the ghost and gives its tile back. */
	private static void receiveGhosts(Copy copy, List<?> changes) {
		List<BlockPos> positions = new ArrayList<>();
		List<MirrorTerrain.Block> states = new ArrayList<>();
		for (Object raw : changes) {
			if (!(raw instanceof List<?> change) || change.size() != 4 || !(change.get(0) instanceof Number nx)
					|| !(change.get(1) instanceof Number ny) || !(change.get(2) instanceof Number nz)
					|| !(change.get(3) instanceof String state)) {
				continue;
			}
			int x = nx.intValue();
			int y = ny.intValue();
			int z = nz.intValue();
			if (!copy.zone.inside(x, z) || y < 0 || y >= MirrorTerrain.HEIGHT) {
				continue;
			}
			if (state.isEmpty()) {
				copy.ghosts.remove(BlockPos.asLong(x, y, z));
				copy.toFetch.add(MirrorTerrain.key(x / MirrorTerrain.TILE, z / MirrorTerrain.TILE));
			} else {
				copy.ghosts.put(BlockPos.asLong(x, y, z), state);
				positions.add(new BlockPos(copy.zone.minX() + x, MirrorTerrain.MIN_Y + y, copy.zone.minZ() + z));
				states.add(MirrorTerrain.block(state));
			}
		}
		if (!positions.isEmpty()) {
			WRITES.add(new Write(positions, states));
		}
	}

	/** The blocks changed by hand in a copy: placed ones become ghosts, a broken ghost goes. */
	private static void edits(Minecraft mc) {
		List<MirrorEdits.Edit> edits = MirrorEdits.drain();
		if (edits.isEmpty() || mc.player == null) {
			return;
		}
		var dimension = mc.player.level().dimension();
		for (MirrorEdits.Edit edit : edits) {
			if (!edit.dimension().equals(dimension)) {
				continue;
			}
			for (Copy copy : COPIES.values()) {
				int x = edit.pos().getX() - copy.zone.minX();
				int y = edit.pos().getY() - MirrorTerrain.MIN_Y;
				int z = edit.pos().getZ() - copy.zone.minZ();
				if (!copy.zone.inside(x, z) || y < 0 || y >= MirrorTerrain.HEIGHT) {
					continue;
				}
				long key = BlockPos.asLong(x, y, z);
				if (edit.name().isEmpty()) {
					if (copy.ghosts.remove(key) != null) {
						copy.ghostsOut.add(List.of(x, y, z, ""));
					}
				} else if (!edit.name().equals(copy.ghosts.put(key, edit.name()))) {
					copy.ghostsOut.add(List.of(x, y, z, edit.name()));
				}
			}
		}
	}

	private static void flushGhosts(Copy copy) {
		if (copy.ghostsOut.isEmpty()) {
			return;
		}
		List<List<Object>> out = new ArrayList<>(copy.ghostsOut);
		copy.ghostsOut.clear();
		for (int from = 0; from < out.size(); from += 4096) {
			PhantasmonApi.send("EventsMirrorGhosts", Map.of("hub", copy.anchor.hub(),
					"changes", out.subList(from, Math.min(out.size(), from + 4096))));
		}
	}

	/** A batch of blocks to the integrated server; its size follows how long the last one took. */
	private static void write(Minecraft mc) {
		var server = mc.getSingleplayerServer();
		if (server == null || mc.player == null || writing || WRITES.isEmpty()) {
			return;
		}
		var dimension = mc.player.level().dimension();
		List<BlockPos> positions = new ArrayList<>(batch);
		List<MirrorTerrain.Block> states = new ArrayList<>(batch);
		while (positions.size() < batch && !WRITES.isEmpty()) {
			Write head = WRITES.peek();
			int end = Math.min(head.positions().size(), cursor + batch - positions.size());
			positions.addAll(head.positions().subList(cursor, end));
			states.addAll(head.states().subList(cursor, end));
			cursor = end;
			if (cursor >= head.positions().size()) {
				WRITES.poll();
				cursor = 0;
			}
		}
		writing = true;
		server.execute(() -> {
			long start = System.nanoTime();
			try {
				ServerLevel level = server.getLevel(dimension);
				if (level == null) {
					return;
				}
				for (int i = 0; i < positions.size(); i++) {
					BlockPos pos = positions.get(i);
					MirrorTerrain.Block block = states.get(i);
					BlockState state = block.state();
					if (!level.isInWorldBounds(pos)) {
						continue;
					}
					if (level.getBlockState(pos) != state) {
						level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
					}
					if (block.nbt() != null) {
						BlockEntity blockEntity = level.getBlockEntity(pos);
						if (blockEntity != null) {
							blockEntity.loadWithComponents(block.nbt(), level.registryAccess());
							blockEntity.setChanged();
							level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
						}
					}
				}
			} finally {
				long millis = Math.max(1, (System.nanoTime() - start) / 1_000_000);
				int next = (int) (positions.size() * TARGET_MILLIS / millis);
				batch = Math.max(1024, Math.min(65536, next));
				writing = false;
			}
		});
	}
}
