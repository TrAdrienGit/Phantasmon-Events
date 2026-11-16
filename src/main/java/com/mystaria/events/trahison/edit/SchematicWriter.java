package com.mystaria.events.trahison.edit;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import com.mystaria.phantasmon.client.hub.HubSchematic;

/**
 * Writes a {@link HubSchematic} (the reader's own model: a box and its non-air blocks, states as {@code {Name, Properties}},
 * block entities as their data with an {@code id}) as a Sponge {@code .schem} version 2 or as a Litematica
 * {@code .litematic}, following solutions-techniques.md S11. Only {@code net.minecraft.nbt}: no registry, no bootstrap, so
 * it is unit-tested by reading its output back with {@link HubSchematic#read}. The caller gives the data version
 * ({@code SharedConstants.getCurrentVersion().getDataVersion().getVersion()} in game).
 *
 * <p>Axis orders (the two formats agree on this one): index {@code x + z * sizeX + y * sizeX * sizeZ}. Sponge stores the
 * indices as varints; Litematica packs them on {@code max(2, bits)} bits that run across {@code long} boundaries.
 */
public final class SchematicWriter {

	/** The largest box either format is asked to hold (blocks in the box, air included). */
	public static final long MAX_VOLUME = 64L * 1024 * 1024;

	private SchematicWriter() {
	}

	// ---- Sponge v2 ----

	public static byte[] sponge(HubSchematic s, int dataVersion, String name, String author) throws IOException {
		check(s, 65535);
		int w = s.sizeX();
		int l = s.sizeZ();
		List<HubSchematic.Block> sorted = new ArrayList<>(s.blocks());
		sorted.sort(Comparator.comparingLong(b -> index(b, w, l)));

		Map<String, Integer> palette = new LinkedHashMap<>();
		palette.put("minecraft:air", 0);
		ByteArrayOutputStream data = new ByteArrayOutputStream(Math.max(16, sorted.size() + 16));
		ListTag entities = new ListTag();
		long next = 0;
		for (HubSchematic.Block b : sorted) {
			long at = index(b, w, l);
			if (at < next) {
				throw new IOException("Two blocks at " + b.x() + " " + b.y() + " " + b.z());
			}
			for (long gap = next; gap < at; gap++) {
				data.write(0);
			}
			String text = stateText(b.state());
			Integer id = palette.get(text);
			if (id == null) {
				id = palette.size();
				palette.put(text, id);
			}
			writeVarInt(data, id);
			next = at + 1;
			if (b.blockEntity() != null) {
				CompoundTag entity = b.blockEntity().copy();
				String blockEntityId = entity.getString("id");
				entity.remove("id");
				entity.remove("x");
				entity.remove("y");
				entity.remove("z");
				entity.putIntArray("Pos", new int[] { b.x(), b.y(), b.z() });
				entity.putString("Id", blockEntityId);
				entities.add(entity);
			}
		}
		for (long gap = next; gap < (long) w * s.sizeY() * l; gap++) {
			data.write(0);
		}

		CompoundTag paletteTag = new CompoundTag();
		palette.forEach(paletteTag::putInt);
		CompoundTag schematic = new CompoundTag();
		schematic.putInt("Version", 2);
		schematic.putInt("DataVersion", dataVersion);
		schematic.putShort("Width", (short) w);
		schematic.putShort("Height", (short) s.sizeY());
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
		metadata.putString("Generator", "Phantasmon Events");
		schematic.put("Metadata", metadata);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
			out.writeByte(Tag.TAG_COMPOUND);
			out.writeUTF("Schematic");
			schematic.write(out);
		}
		return bytes.toByteArray();
	}

	// ---- Litematica ----

	public static byte[] litematic(HubSchematic s, int dataVersion, String name, String author) throws IOException {
		check(s, Integer.MAX_VALUE);
		int w = s.sizeX();
		int h = s.sizeY();
		int l = s.sizeZ();
		long volume = (long) w * h * l;
		List<CompoundTag> palette = new ArrayList<>();
		Map<String, Integer> ids = new LinkedHashMap<>();
		CompoundTag air = new CompoundTag();
		air.putString("Name", "minecraft:air");
		palette.add(air);
		ids.put("minecraft:air", 0);
		int[] index = new int[s.blocks().size()];
		long[] at = new long[index.length];
		for (int i = 0; i < index.length; i++) {
			HubSchematic.Block b = s.blocks().get(i);
			at[i] = index(b, w, l);
			String text = stateText(b.state());
			Integer id = ids.get(text);
			if (id == null) {
				id = palette.size();
				ids.put(text, id);
				palette.add(b.state().copy());
			}
			index[i] = id;
		}
		int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(palette.size() - 1));
		long[] states = new long[(int) ((volume * bits + 63) / 64)];
		ListTag tiles = new ListTag();
		for (int i = 0; i < index.length; i++) {
			long start = at[i] * bits;
			int word = (int) (start >>> 6);
			int offset = (int) (start & 63);
			long value = index[i];
			states[word] |= value << offset;
			if (offset + bits > 64) {
				states[word + 1] |= value >>> (64 - offset);
			}
			HubSchematic.Block b = s.blocks().get(i);
			if (b.blockEntity() != null) {
				CompoundTag entity = b.blockEntity().copy();
				entity.putInt("x", b.x());
				entity.putInt("y", b.y());
				entity.putInt("z", b.z());
				tiles.add(entity);
			}
		}
		ListTag paletteTag = new ListTag();
		paletteTag.addAll(palette);

		long time = System.currentTimeMillis();
		CompoundTag size = vec(w, h, l);
		CompoundTag region = new CompoundTag();
		region.put("Position", vec(0, 0, 0));
		region.put("Size", size.copy());
		region.put("BlockStatePalette", paletteTag);
		region.putLongArray("BlockStates", states);
		region.put("TileEntities", tiles);
		region.put("Entities", new ListTag());
		region.put("PendingBlockTicks", new ListTag());
		region.put("PendingFluidTicks", new ListTag());
		CompoundTag regions = new CompoundTag();
		String regionName = name == null || name.isBlank() ? "room" : name;
		regions.put(regionName, region);

		CompoundTag metadata = new CompoundTag();
		metadata.putString("Name", name == null ? "" : name);
		metadata.putString("Author", author == null ? "" : author);
		metadata.putString("Description", "");
		metadata.put("EnclosingSize", size);
		metadata.putInt("RegionCount", 1);
		metadata.putInt("TotalBlocks", s.blocks().size());
		metadata.putInt("TotalVolume", (int) volume);
		metadata.putLong("TimeCreated", time);
		metadata.putLong("TimeModified", time);

		CompoundTag root = new CompoundTag();
		root.putInt("Version", 6);
		root.putInt("SubVersion", 1);
		root.putInt("MinecraftDataVersion", dataVersion);
		root.put("Metadata", metadata);
		root.put("Regions", regions);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, bytes);
		return bytes.toByteArray();
	}

	// ---- shared ----

	/** {@code minecraft:oak_stairs[facing=north,half=bottom]}: the properties sorted by name, none: no brackets. */
	static String stateText(CompoundTag state) {
		String name = state.getString("Name");
		CompoundTag properties = state.getCompound("Properties");
		if (properties.isEmpty()) {
			return name;
		}
		String[] keys = properties.getAllKeys().toArray(new String[0]);
		Arrays.sort(keys);
		StringBuilder text = new StringBuilder(name).append('[');
		for (int i = 0; i < keys.length; i++) {
			if (i > 0) {
				text.append(',');
			}
			text.append(keys[i]).append('=').append(properties.getString(keys[i]));
		}
		return text.append(']').toString();
	}

	private static CompoundTag vec(int x, int y, int z) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("x", x);
		tag.putInt("y", y);
		tag.putInt("z", z);
		return tag;
	}

	private static long index(HubSchematic.Block b, int w, int l) {
		return b.x() + (long) b.z() * w + (long) b.y() * w * l;
	}

	private static void check(HubSchematic s, int maxEdge) throws IOException {
		long volume = (long) s.sizeX() * s.sizeY() * s.sizeZ();
		if (s.sizeX() <= 0 || s.sizeY() <= 0 || s.sizeZ() <= 0 || s.sizeX() > maxEdge || s.sizeY() > maxEdge || s.sizeZ() > maxEdge) {
			throw new IOException("Bad size " + s.sizeX() + " x " + s.sizeY() + " x " + s.sizeZ());
		}
		if (volume > MAX_VOLUME) {
			throw new IOException("Box of " + volume + " blocks is too big");
		}
		for (HubSchematic.Block b : s.blocks()) {
			if (b.x() < 0 || b.y() < 0 || b.z() < 0 || b.x() >= s.sizeX() || b.y() >= s.sizeY() || b.z() >= s.sizeZ()) {
				throw new IOException("Block outside the box at " + b.x() + " " + b.y() + " " + b.z());
			}
		}
	}

	private static void writeVarInt(ByteArrayOutputStream out, int value) {
		int v = value;
		while ((v & ~0x7F) != 0) {
			out.write(v & 0x7F | 0x80);
			v >>>= 7;
		}
		out.write(v);
	}
}
