package com.mystaria.events.hub;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;

/**
 * The ghost blocks of the mirror hubs (Adrien 2026-10-30), on a multiplayer server: blocks an admin placed in a copy,
 * shown in the source zone as <b>client-only</b> blocks — real blocks of this client's world (same rendering, light,
 * shaders, collisions), unknown to the Minecraft server, like the hubs' builds (D-34).
 *
 * <ul>
 * <li>What the server sends for a ghost's position is kept aside ({@code ClientLevelMirrorMixin}) and given back when
 * the ghost goes; {@link MirrorCapture} reads that real block, never the ghost.</li>
 * <li>They can't be broken nor built against ({@code MultiPlayerGameModeGhostMixin}).</li>
 * </ul>
 * Client thread.
 */
public final class MirrorGhosts {

	private static final Logger LOG = LoggerFactory.getLogger(MirrorGhosts.class);
	/** Tell the renderer, but no neighbour shape updates: real blocks around are left as the server made them. */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private record Ghost(String hub, MirrorTerrain.Block block) {
	}

	/** Hub → its source zone on this server, watched. */
	private static final Map<String, MirrorTerrain.Zone> WATCHED = new HashMap<>();
	private static ClientLevel level;
	/** {@code BlockPos.asLong} → ghost. */
	private static final Map<Long, Ghost> GHOSTS = new HashMap<>();
	/** {@code BlockPos.asLong} → what the server says stands under a ghost shown. */
	private static final Map<Long, BlockState> SERVER = new HashMap<>();
	private static final Map<Long, Set<Long>> BY_CHUNK = new HashMap<>();
	private static final Set<Long> DIRTY_CHUNKS = new LinkedHashSet<>();

	private MirrorGhosts() {
	}

	public static void register() {
		PhantasmonApi.onEventsMessage((type, data) -> {
			if ("EventsMirrorGhosts".equals(type) && WATCHED.containsKey(String.valueOf(data.get("hub")))
					&& data.get("changes") instanceof List<?> changes) {
				apply(String.valueOf(data.get("hub")), changes);
			}
		});
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != level) {
			reset();
			level = mc.level;
		}
		if (level == null || mc.hasSingleplayerServer() || !PhantasmonApi.isAuthenticated()) {
			return;
		}
		follow(PhantasmonApi.anchorsHere());
		for (Long chunk : List.copyOf(DIRTY_CHUNKS)) {
			if (level.getChunkSource().hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk))) {
				DIRTY_CHUNKS.remove(chunk);
				for (long key : BY_CHUNK.getOrDefault(chunk, Set.of())) {
					show(key);
				}
			}
		}
	}

	/** World left: the ghosts went with it. */
	public static void reset() {
		for (String hub : WATCHED.keySet()) {
			PhantasmonApi.send("EventsMirrorUnwatch", Map.of("hub", hub));
		}
		WATCHED.clear();
		GHOSTS.clear();
		SERVER.clear();
		BY_CHUNK.clear();
		DIRTY_CHUNKS.clear();
		level = null;
	}

	/** The source anchors of this server: new ones watched, gone or resized ones dropped. */
	private static void follow(List<HubAnchorDto> anchors) {
		Set<String> here = new HashSet<>();
		for (HubAnchorDto anchor : anchors) {
			if (!anchor.mirror() || !HubAnchorDto.ROLE_SOURCE.equals(anchor.role())) {
				continue;
			}
			here.add(anchor.hub());
			MirrorTerrain.Zone zone = MirrorTerrain.Zone.of(anchor);
			MirrorTerrain.Zone known = WATCHED.get(anchor.hub());
			if (known != null && !known.equals(zone)) {
				unwatch(anchor.hub());
				known = null;
			}
			if (known == null) {
				watch(anchor.hub(), zone);
			}
		}
		for (String hub : List.copyOf(WATCHED.keySet())) {
			if (!here.contains(hub)) {
				unwatch(hub);
			}
		}
	}

	private static void watch(String hub, MirrorTerrain.Zone zone) {
		WATCHED.put(hub, zone);
		// Watching first, so no change falls between the list fetched and the stream.
		PhantasmonApi.send("EventsMirrorWatch", Map.of("hub", hub));
		PhantasmonApi.get("/events/mirror/" + hub + "/ghosts", List.class)
				.thenAccept(list -> Minecraft.getInstance().execute(() -> {
					if (WATCHED.get(hub) == zone) {
						apply(hub, list);
					}
				}))
				.exceptionally(ex -> {
					LOG.warn("Could not fetch the ghost blocks of mirror hub {}", hub, ex);
					return null;
				});
	}

	private static void unwatch(String hub) {
		WATCHED.remove(hub);
		PhantasmonApi.send("EventsMirrorUnwatch", Map.of("hub", hub));
		for (Map.Entry<Long, Ghost> entry : List.copyOf(GHOSTS.entrySet())) {
			if (entry.getValue().hub().equals(hub)) {
				remove(entry.getKey());
			}
		}
	}

	/** {@code [x, y, z, state]} zone-relative; {@code ""} removes. */
	private static void apply(String hub, List<?> changes) {
		MirrorTerrain.Zone zone = WATCHED.get(hub);
		if (zone == null || level == null) {
			return;
		}
		for (Object raw : changes) {
			if (!(raw instanceof List<?> change) || change.size() != 4 || !(change.get(0) instanceof Number x)
					|| !(change.get(1) instanceof Number y) || !(change.get(2) instanceof Number z)
					|| !(change.get(3) instanceof String state)) {
				continue;
			}
			long key = BlockPos.asLong(zone.minX() + x.intValue(), MirrorTerrain.MIN_Y + y.intValue(), zone.minZ() + z.intValue());
			MirrorTerrain.Block block = state.isEmpty() ? null : MirrorTerrain.block(state);
			if (block == null || block.state().isAir()) {
				remove(key);
			} else {
				set(key, new Ghost(hub, block));
			}
		}
	}

	private static void set(long key, Ghost ghost) {
		Ghost previous = GHOSTS.put(key, ghost);
		BlockPos pos = BlockPos.of(key);
		long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
		BY_CHUNK.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(key);
		if (previous != null && SERVER.containsKey(key) && loaded(pos)) {
			write(pos, ghost.block()); // already shown: the real block stays aside
		} else {
			DIRTY_CHUNKS.add(chunk);
		}
	}

	/** The ghost goes: what the server says stands there comes back. */
	private static void remove(long key) {
		Ghost ghost = GHOSTS.remove(key);
		BlockState real = SERVER.remove(key);
		BlockPos pos = BlockPos.of(key);
		Set<Long> inChunk = BY_CHUNK.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
		if (inChunk != null) {
			inChunk.remove(key);
		}
		if (ghost != null && real != null && level != null && loaded(pos)) {
			level.setBlock(pos, real, FLAGS);
		}
	}

	/** Shows a ghost in a loaded chunk; what stands there now came from the server and is kept aside. */
	private static void show(long key) {
		Ghost ghost = GHOSTS.get(key);
		if (ghost == null) {
			return;
		}
		BlockPos pos = BlockPos.of(key);
		BlockState current = level.getBlockState(pos);
		if (!SERVER.containsKey(key)) {
			SERVER.put(key, current);
		} else if (current == ghost.block().state()) {
			return;
		}
		write(pos, ghost.block());
	}

	private static void write(BlockPos pos, MirrorTerrain.Block block) {
		level.setBlock(pos, block.state(), FLAGS);
		if (block.nbt() != null) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity != null) {
				try {
					blockEntity.loadWithComponents(block.nbt(), level.registryAccess());
				} catch (RuntimeException ex) {
					LOG.debug("Ghost block: block entity data at {} not applied", pos, ex);
				}
			}
		}
	}

	private static boolean loaded(BlockPos pos) {
		return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
	}

	/** Fabric's chunk load: the server (re)sent a chunk, its ghosts are gone — shown again next tick. */
	public static void onChunkLoad(ClientLevel chunkLevel, ChunkPos chunk) {
		if (chunkLevel == level && BY_CHUNK.containsKey(chunk.toLong())) {
			for (long key : BY_CHUNK.get(chunk.toLong())) {
				SERVER.remove(key);
			}
			DIRTY_CHUNKS.add(chunk.toLong());
		}
	}

	/** {@code ClientLevelMirrorMixin}: the server sets a block; under a ghost shown it is kept aside instead. */
	public static boolean interceptServerBlock(ClientLevel serverLevel, BlockPos pos, BlockState state) {
		long key = pos.asLong();
		if (serverLevel != level || !SERVER.containsKey(key)) {
			return false;
		}
		SERVER.put(key, state);
		return true;
	}

	/** Whether a ghost is shown at this position. */
	public static boolean isGhost(ClientLevel atLevel, BlockPos pos) {
		return atLevel == level && SERVER.containsKey(pos.asLong());
	}

	/** The block the server says stands here: under a ghost, the one kept aside. */
	public static BlockState realState(ClientLevel atLevel, BlockPos pos) {
		BlockState real = atLevel == level ? SERVER.get(pos.asLong()) : null;
		return real != null ? real : atLevel.getBlockState(pos);
	}

}
