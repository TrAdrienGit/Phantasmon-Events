package com.mystaria.events.hub;

import java.util.ArrayList;
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
import net.minecraft.world.level.block.state.BlockState;

import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;

/**
 * Feeds the mirror hubs (D-38, choice of Adrien: any Phantasmon player): on a multiplayer server, the source zone of a
 * mirror hub is read tile by tile (16 × 16, the whole height) as its chunks arrive; a tile whose hash differs from the
 * backend's is sent whole ({@code EventsMirrorTile}), then only the blocks the server changes
 * ({@code EventsMirrorBlocks}, from {@code ClientLevelMirrorMixin}). Nothing is ever sent to the Minecraft server.
 * Client thread.
 */
public final class MirrorCapture {

	private static final Logger LOG = LoggerFactory.getLogger(MirrorCapture.class);
	private static final int FLUSH_TICKS = 10;
	private static final int BATCH = 4096;

	private static final class Source {
		final HubAnchorDto anchor;
		final MirrorTerrain.Zone zone;
		/** Tile → the backend's hash (as last known); absent until the backend's info arrived. */
		Map<Long, Long> hashes;
		boolean fetching;
		final Set<Long> toScan = new LinkedHashSet<>();
		final List<List<Object>> changes = new ArrayList<>();

		Source(HubAnchorDto anchor) {
			this.anchor = anchor;
			this.zone = MirrorTerrain.Zone.of(anchor);
		}

		/** The tiles a world chunk overlaps. */
		void queue(ChunkPos chunk) {
			int x0 = Math.max(0, chunk.getMinBlockX() - zone.minX());
			int x1 = Math.min(zone.side() - 1, chunk.getMaxBlockX() - zone.minX());
			int z0 = Math.max(0, chunk.getMinBlockZ() - zone.minZ());
			int z1 = Math.min(zone.side() - 1, chunk.getMaxBlockZ() - zone.minZ());
			for (int tx = x0 / MirrorTerrain.TILE; x0 <= x1 && tx <= x1 / MirrorTerrain.TILE; tx++) {
				for (int tz = z0 / MirrorTerrain.TILE; z0 <= z1 && tz <= z1 / MirrorTerrain.TILE; tz++) {
					toScan.add(MirrorTerrain.key(tx, tz));
				}
			}
		}
	}

	/** Hub → its source zone on this server. */
	private static final Map<String, Source> SOURCES = new HashMap<>();
	private static int ticks;

	private MirrorCapture() {
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || mc.hasSingleplayerServer() || !PhantasmonApi.isAuthenticated()) {
			return;
		}
		follow(PhantasmonApi.anchorsHere());
		// One tile (16 × 16 × 384 blocks) read per tick, all sources together.
		for (Source source : SOURCES.values()) {
			if (source.hashes != null && !source.toScan.isEmpty()) {
				long key = source.toScan.iterator().next();
				source.toScan.remove(key);
				scan(mc.level, source, (int) (key >> 32), (int) key);
				break;
			}
		}
		if (++ticks % FLUSH_TICKS == 0) {
			SOURCES.values().forEach(MirrorCapture::flush);
		}
	}

	/** A chunk arrived from the server: its tiles are read. */
	public static void onChunkLoad(ChunkPos chunk) {
		for (Source source : SOURCES.values()) {
			source.queue(chunk);
		}
	}

	/** The server set a block (ClientLevelMirrorMixin), {@code old} the one it replaces. */
	public static void onBlock(BlockPos pos, BlockState old, BlockState state) {
		if (old == state) {
			return;
		}
		for (Source source : SOURCES.values()) {
			int x = pos.getX() - source.zone.minX();
			int z = pos.getZ() - source.zone.minZ();
			int y = pos.getY() - MirrorTerrain.MIN_Y;
			if (source.hashes != null && source.zone.inside(x, z) && y >= 0 && y < MirrorTerrain.HEIGHT) {
				source.changes.add(List.of(x, y, z, MirrorTerrain.name(state)));
				source.hashes.remove(MirrorTerrain.key(x / MirrorTerrain.TILE, z / MirrorTerrain.TILE));
			}
		}
	}

	public static void reset() {
		SOURCES.clear();
	}

	/** The source anchors of this server: new ones fetched and read, gone or resized ones forgotten. */
	private static void follow(List<HubAnchorDto> anchors) {
		Set<String> here = new HashSet<>();
		for (HubAnchorDto anchor : anchors) {
			if (!anchor.mirror() || !HubAnchorDto.ROLE_SOURCE.equals(anchor.role())) {
				continue;
			}
			here.add(anchor.hub());
			Source source = SOURCES.get(anchor.hub());
			if (source == null || !source.zone.equals(MirrorTerrain.Zone.of(anchor))) {
				source = new Source(anchor);
				SOURCES.put(anchor.hub(), source);
			}
			if (source.hashes == null && !source.fetching) {
				fetch(source);
			}
		}
		SOURCES.keySet().retainAll(here);
	}

	private static void fetch(Source source) {
		String hub = source.anchor.hub();
		source.fetching = true;
		PhantasmonApi.get("/events/mirror/" + hub, MirrorTerrain.Info.class)
				.thenAccept(info -> Minecraft.getInstance().execute(() -> {
					source.fetching = false;
					if (SOURCES.get(hub) != source || info.side() != source.zone.side()) {
						return; // resized meanwhile: started again
					}
					Map<Long, Long> hashes = new HashMap<>();
					for (long[] tile : info.tiles()) {
						hashes.put(MirrorTerrain.key((int) tile[0], (int) tile[1]), tile[2]);
					}
					source.hashes = hashes;
					for (int tx = 0; tx < source.zone.tiles(); tx++) {
						for (int tz = 0; tz < source.zone.tiles(); tz++) {
							source.toScan.add(MirrorTerrain.key(tx, tz));
						}
					}
				}))
				.exceptionally(ex -> {
					Minecraft.getInstance().execute(() -> source.fetching = false);
					LOG.warn("Could not fetch the terrain of mirror hub {}", hub, ex);
					return null;
				});
	}

	/** Reads a tile if all its chunks are here; sends it if it differs from the backend's. */
	private static void scan(ClientLevel level, Source source, int tx, int tz) {
		int ox = source.zone.minX() + tx * MirrorTerrain.TILE;
		int oz = source.zone.minZ() + tz * MirrorTerrain.TILE;
		int last = MirrorTerrain.TILE - 1;
		for (int cx = ox >> 4; cx <= (ox + last) >> 4; cx++) {
			for (int cz = oz >> 4; cz <= (oz + last) >> 4; cz++) {
				if (!level.getChunkSource().hasChunk(cx, cz)) {
					return; // read when that chunk arrives
				}
			}
		}
		short[] ids = new short[MirrorTerrain.TILE_VOLUME];
		Map<String, Short> palette = new HashMap<>();
		List<String> names = new ArrayList<>();
		long hash = 0xcbf29ce484222325L;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int i = 0;
		for (int y = 0; y < MirrorTerrain.HEIGHT; y++) {
			for (int z = 0; z < MirrorTerrain.TILE; z++) {
				for (int x = 0; x < MirrorTerrain.TILE; x++, i++) {
					if (!source.zone.inside(tx * MirrorTerrain.TILE + x, tz * MirrorTerrain.TILE + z)) {
						ids[i] = -1;
						continue;
					}
					String name = MirrorTerrain.name(level.getBlockState(pos.set(ox + x, MirrorTerrain.MIN_Y + y, oz + z)));
					ids[i] = palette.computeIfAbsent(name, key -> {
						names.add(key);
						return (short) (names.size() - 1);
					});
					hash = MirrorTerrain.fnv(hash, name);
				}
			}
		}
		hash = hash == 0 ? 1 : hash;
		long key = MirrorTerrain.key(tx, tz);
		Long known = source.hashes.get(key);
		if (known != null && known == hash) {
			return;
		}
		if (PhantasmonApi.send("EventsMirrorTile", Map.of("hub", source.anchor.hub(), "radius", source.zone.radius(),
				"tx", tx, "tz", tz, "palette", names, "blocks", MirrorTerrain.encode(ids)))) {
			source.hashes.put(key, hash);
		}
	}

	private static void flush(Source source) {
		if (source.changes.isEmpty()) {
			return;
		}
		List<List<Object>> changes = new ArrayList<>(source.changes);
		source.changes.clear();
		for (int from = 0; from < changes.size(); from += BATCH) {
			if (!PhantasmonApi.send("EventsMirrorBlocks", Map.of("hub", source.anchor.hub(), "radius", source.zone.radius(),
					"changes", changes.subList(from, Math.min(changes.size(), from + BATCH))))) {
				source.hashes = null; // not sent: fetch the backend's hashes again and read everything
				return;
			}
		}
	}
}
