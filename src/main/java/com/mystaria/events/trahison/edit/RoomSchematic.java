package com.mystaria.events.trahison.edit;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.mystaria.phantasmon.client.hub.HubSchematic;

/**
 * Reads a room's blocks out of a world and writes them as a Sponge {@code .schem} version 2, and the other way round
 * (solutions S11 and S12). The file is always the room in its <b>canonical orientation</b> ({@link RoomTemplate}): the
 * world's blocks are turned back by the builder's orientation when saved and turned again when pasted, so a room built
 * facing any way is stored the same. Everything here runs on the integrated server's thread (the full block entities
 * only exist there), on a {@link ServerLevel}.
 *
 * <p>File: root compound named {@code Schematic}, {@code Version} 2, {@code DataVersion}, {@code Width/Height/Length} =
 * 48 / 24 / 48, {@code Palette} (state text to index, air is 0), {@code BlockData} (varints, order
 * {@code x + z * Width + y * Width * Length}), {@code BlockEntities} ({@code Pos}, {@code Id}, then the data). The reader is
 * Phantasmon's {@link HubSchematic}, the same one that reads the hub's file, so what is written is checked by reading it back.
 */
final class RoomSchematic {

	private static final int AIR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

	/** What a capture found, besides the file: how many blocks, and whether the entry or exit opening is not air. */
	record Capture(byte[] file, int blocks, int blockEntities, boolean entryBlocked, boolean exitBlocked) {
	}

	private RoomSchematic() {
	}

	/** Refuses a template that would leave the world's build height. */
	static void checkHeight(ServerLevel level, RoomTemplate.Frame frame) {
		if (frame.ay() < level.getMinBuildHeight() || frame.ay() + RoomTemplate.SIZE_Y > level.getMaxBuildHeight()) {
			throw new IllegalStateException("height");
		}
	}

	static Capture capture(ServerLevel level, RoomTemplate.Frame frame, String name, String author) throws IOException {
		checkHeight(level, frame);
		Rotation back = Rotation.values()[Math.floorMod(-frame.k(), 4)];
		int w = RoomTemplate.SIZE_X;
		int h = RoomTemplate.SIZE_Y;
		int l = RoomTemplate.SIZE_Z;
		Map<String, Integer> palette = new LinkedHashMap<>();
		palette.put("minecraft:air", 0);
		ByteArrayOutputStream data = new ByteArrayOutputStream(w * h * l / 2);
		ListTag entities = new ListTag();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int blocks = 0;
		boolean entryBlocked = false;
		boolean exitBlocked = false;
		for (int y = 0; y < h; y++) {
			for (int z = 0; z < l; z++) {
				for (int x = 0; x < w; x++) {
					int[] world = frame.toWorld(x, y, z);
					pos.set(world[0], world[1], world[2]);
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) {
						writeVarInt(data, 0);
						continue;
					}
					blocks++;
					entryBlocked |= inside(RoomTemplate.ENTRY, x, y, z);
					exitBlocked |= inside(RoomTemplate.EXIT, x, y, z);
					BlockState canonical = state.rotate(back);
					String text = BlockStateParser.serialize(canonical);
					Integer index = palette.get(text);
					if (index == null) {
						index = palette.size();
						palette.put(text, index);
					}
					writeVarInt(data, index);
					BlockEntity entity = level.getBlockEntity(pos);
					if (entity != null) {
						CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
						CompoundTag out = new CompoundTag();
						out.putIntArray("Pos", new int[] { x, y, z });
						out.putString("Id", tag.getString("id"));
						for (String key : tag.getAllKeys()) {
							if (!key.equals("x") && !key.equals("y") && !key.equals("z") && !key.equals("id")) {
								out.put(key, tag.get(key));
							}
						}
						entities.add(out);
					}
				}
			}
		}
		CompoundTag paletteTag = new CompoundTag();
		palette.forEach(paletteTag::putInt);
		CompoundTag schematic = new CompoundTag();
		schematic.putInt("Version", 2);
		schematic.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		schematic.putShort("Width", (short) w);
		schematic.putShort("Height", (short) h);
		schematic.putShort("Length", (short) l);
		schematic.putIntArray("Offset", new int[] { 0, 0, 0 });
		schematic.putInt("PaletteMax", palette.size());
		schematic.put("Palette", paletteTag);
		schematic.putByteArray("BlockData", data.toByteArray());
		schematic.put("BlockEntities", entities);
		CompoundTag metadata = new CompoundTag();
		metadata.putString("Name", name == null ? "" : name);
		metadata.putString("Author", author == null ? "" : author);
		metadata.putLong("Date", System.currentTimeMillis());
		metadata.putString("Generator", "Phantasmon Events room editor");
		schematic.put("Metadata", metadata);

		byte[] file = write(schematic);
		// Checked by reading it back with the reader of the hub's files.
		HubSchematic back2 = HubSchematic.read(file, false);
		if (back2.sizeX() != w || back2.sizeY() != h || back2.sizeZ() != l || back2.blocks().size() != blocks) {
			throw new IOException("The schematic read back does not match what was captured");
		}
		return new Capture(file, blocks, entities.size(), entryBlocked, exitBlocked);
	}

	private static boolean inside(LayoutDto.Box box, int x, int y, int z) {
		return x >= box.minX && x <= box.maxX && y >= box.minY && y <= box.maxY && z >= box.minZ && z <= box.maxZ;
	}

	/** A gzip-compressed NBT file whose root compound is named {@code Schematic}. */
	private static byte[] write(CompoundTag schematic) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
			out.writeByte(Tag.TAG_COMPOUND);
			out.writeUTF("Schematic");
			schematic.write(out);
		}
		return bytes.toByteArray();
	}

	private static void writeVarInt(ByteArrayOutputStream out, int value) {
		int v = value;
		while ((v & ~0x7F) != 0) {
			out.write(v & 0x7F | 0x80);
			v >>>= 7;
		}
		out.write(v);
	}

	/**
	 * Clears the template's box, then pastes the file turned by the frame's orientation. Blocks the game does not know
	 * become air (as for the hub's files). Returns the number of blocks placed.
	 */
	static int paste(ServerLevel level, RoomTemplate.Frame frame, byte[] file) throws IOException {
		checkHeight(level, frame);
		HubSchematic schematic = HubSchematic.read(file, false);
		if (schematic.sizeX() != RoomTemplate.SIZE_X || schematic.sizeY() != RoomTemplate.SIZE_Y || schematic.sizeZ() != RoomTemplate.SIZE_Z) {
			throw new IOException("size");
		}
		Rotation turn = Rotation.values()[Math.floorMod(frame.k(), 4)];
		LayoutDto.Box box = frame.toWorld(RoomTemplate.BOX);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int y = box.minY; y <= box.maxY; y++) {
			for (int z = box.minZ; z <= box.maxZ; z++) {
				for (int x = box.minX; x <= box.maxX; x++) {
					pos.set(x, y, z);
					if (!level.getBlockState(pos).isAir()) {
						level.setBlock(pos, air, AIR_FLAGS);
					}
				}
			}
		}
		int placed = 0;
		List<Runnable> entityJobs = new ArrayList<>();
		for (HubSchematic.Block block : schematic.blocks()) {
			BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), block.state()).rotate(turn);
			if (state.isAir()) {
				continue;
			}
			int[] world = frame.toWorld(block.x(), block.y(), block.z());
			BlockPos at = new BlockPos(world[0], world[1], world[2]);
			level.setBlock(at, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			placed++;
			if (block.blockEntity() != null) {
				entityJobs.add(() -> restoreEntity(level, at, state, block.blockEntity()));
			}
		}
		// Block entities last: placing a neighbour must not replace one that was just filled.
		entityJobs.forEach(Runnable::run);
		return placed;
	}

	private static void restoreEntity(ServerLevel level, BlockPos at, BlockState state, CompoundTag data) {
		CompoundTag tag = data.copy();
		tag.putInt("x", at.getX());
		tag.putInt("y", at.getY());
		tag.putInt("z", at.getZ());
		BlockEntity entity = level.getBlockEntity(at);
		if (entity == null) {
			entity = BlockEntity.loadStatic(at, state, tag, level.registryAccess());
			if (entity != null) {
				level.setBlockEntity(entity);
			}
		} else {
			entity.loadWithComponents(tag, level.registryAccess());
		}
		if (entity != null) {
			entity.setChanged();
			level.sendBlockUpdated(at, state, state, Block.UPDATE_ALL);
		}
	}
}
