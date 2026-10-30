package com.mystaria.events.hub;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.mystaria.phantasmon.client.hub.HubAnchorDto;

/**
 * The terrain of a mirror hub as the backend keeps it (D-38): a square of side {@code 2 * radius + 1} around the
 * anchor's centre block, the whole height of the world (y -64 to 319), never turned, cut into tiles of 16 × 16 blocks
 * from the zone's north-west corner. A tile travels as {@value #TILE_VOLUME} int16 ids into its own palette of block
 * state strings (order y, z, x; {@code -1} unknown or outside the zone), gzipped and in base64.
 *
 * <p>A block with a block entity whose data the server sends to the clients carries it, in the same string, as SNBT
 * after the state ({@code templates:fence[...]{...}}): Templates' blocks keep in it the block they look like, signs
 * their text.
 */
final class MirrorTerrain {

	static final int MIN_Y = -64;
	static final int HEIGHT = 384;
	static final int TILE = 16;
	static final int TILE_VOLUME = TILE * TILE * HEIGHT;

	/** {@code GET /events/mirror/{hub}}: the known tiles, {@code [tx, tz, hash]}. */
	record Info(String hub, int radius, int side, long version, List<long[]> tiles) {
	}

	/** {@code GET /events/mirror/{hub}/tile/{tx}/{tz}}. */
	record TileView(int tx, int tz, long version, List<String> palette, String blocks) {

		short[] ids() {
			short[] ids = new short[TILE_VOLUME];
			try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(
					Base64.getDecoder().decode(blocks))))) {
				for (int i = 0; i < ids.length; i++) {
					ids[i] = in.readShort();
				}
			} catch (IOException ex) {
				throw new IllegalStateException("Unreadable mirror tile " + tx + ", " + tz, ex);
			}
			return ids;
		}
	}

	/** Where an anchor's zone stands: its north-west corner and its side. */
	record Zone(int minX, int minZ, int side) {

		static Zone of(HubAnchorDto anchor) {
			int side = anchor.size().x();
			return new Zone((int) Math.floor(anchor.origin().x()) - side / 2, (int) Math.floor(anchor.origin().z()) - side / 2, side);
		}

		int radius() {
			return side / 2;
		}

		int tiles() {
			return (side + TILE - 1) / TILE;
		}

		boolean inside(int x, int z) {
			return x >= 0 && z >= 0 && x < side && z < side;
		}
	}

	private static final Map<BlockState, String> NAMES = new IdentityHashMap<>();
	private static final Map<String, Block> BLOCKS = new ConcurrentHashMap<>();

	/** A block to write: its state, and its block entity's data or null. */
	record Block(BlockState state, CompoundTag nbt) {
	}

	private MirrorTerrain() {
	}

	static long key(int tx, int tz) {
		return ((long) tx << 32) | (tz & 0xffffffffL);
	}

	/** FNV-1a 64 over the state's UTF-8 bytes and a 0, as the backend computes a tile's hash. */
	static long fnv(long hash, String state) {
		for (byte b : state.getBytes(StandardCharsets.UTF_8)) {
			hash = (hash ^ (b & 0xff)) * 0x100000001b3L;
		}
		return hash * 0x100000001b3L;
	}

	static String encode(short[] ids) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
			for (short id : ids) {
				out.writeShort(id);
			}
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		return Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	/** The block as a string, without block entity data. */
	static String stateName(BlockState state) {
		synchronized (NAMES) {
			return NAMES.computeIfAbsent(state, BlockStateParser::serialize);
		}
	}

	/** The block as a string, with its block entity's data if it has some. Thread of {@code level}. */
	static String name(Level level, BlockPos pos, BlockState state) {
		String name = stateName(state);
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

	/** The block of that string; air for a block of a mod this game lacks. */
	static Block block(String name) {
		return BLOCKS.computeIfAbsent(name, key -> {
			try {
				BlockStateParser.BlockResult result = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), key, true);
				return new Block(result.blockState(), result.nbt());
			} catch (CommandSyntaxException ex) {
				return new Block(Blocks.AIR.defaultBlockState(), null);
			}
		});
	}
}
