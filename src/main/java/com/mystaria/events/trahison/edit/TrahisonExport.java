package com.mystaria.events.trahison.edit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.nbt.CompoundTag;

import com.mystaria.events.trahison.world.RoomFrame;
import com.mystaria.phantasmon.client.hub.HubSchematic;

/**
 * The files the admin pastes with Axiom for one game (T4.2, architecture.md section 8): for each of the nine places the drawn
 * room's blocks turned to the place's orientation, as {@code place_<id>.schem} and {@code place_<id>.litematic}, one file with
 * all the places at their relative positions ({@code all_places.*}) and {@code paste_sheet.txt}, which says for every place
 * which corner to paste at. No Minecraft registry here: the state turner and the data version come from the caller, so the
 * whole thing is tested. Every file written is read back with {@link HubSchematic#read} and compared block by block
 * with what was meant, and a mismatch stops the export.
 */
public final class TrahisonExport {

	/** A place and the room it plays: hub coordinates of the place's minimum corner, and the room's canonical file. */
	public record PlaceRoom(String placeId, int round, int originX, int originY, int originZ, String orientation,
			String roomId, String roomName, byte[] file, RoomDto room) {
	}

	/** What was written: the folder and the names of the files in it. */
	public record Written(Path folder, List<String> files, List<String> notes) {
	}

	private TrahisonExport() {
	}

	/**
	 * Writes the files into {@code folder} (created if needed). {@code turner} gives the state turner for a number of quarter
	 * turns ({@link RoomTurn#minecraftStates} in game).
	 */
	public static Written write(Path folder, String gameId, List<PlaceRoom> places, int dataVersion, String author,
			java.util.function.IntFunction<UnaryOperator<CompoundTag>> turner) throws IOException {
		return write(folder, gameId, places, dataVersion, author, turner, null);
	}

	/**
	 * As above; {@code world} (D81) is {@code {worldX, worldZ}} of hub block (0, 0) in the exporting client's world (its mirror
	 * hub), or null when the client is not in the hub: the sheet then also gives each corner in that world's coordinates.
	 */
	public static Written write(Path folder, String gameId, List<PlaceRoom> places, int dataVersion, String author,
			java.util.function.IntFunction<UnaryOperator<CompoundTag>> turner, int[] world) throws IOException {
		if (places.isEmpty()) {
			throw new IOException("no place");
		}
		Files.createDirectories(folder);
		List<String> files = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		List<HubSchematic> turned = new ArrayList<>();
		Map<String, Integer> used = new HashMap<>();
		StringBuilder sheet = new StringBuilder();
		sheet.append("Partie : ").append(gameId).append("\n");
		sheet.append("Fiche de collage Axiom, une section par place. Coordonnées = celles du plan (hub), y = celui du monde.\n");
		if (world != null) {
			sheet.append("Entre parenthèses : les coordonnées du monde du hub miroir du client qui a exporté (le bloc x=0, z=0 du hub est le bloc x=")
					.append(world[0]).append(", z=").append(world[1]).append(" de ce monde).\n");
		} else {
			sheet.append("Les coordonnées du monde du hub miroir ne sont pas données : le client n'était pas dans le hub à l'export.\n");
		}
		sheet.append("Chaque fichier est DÉJÀ tourné pour sa place : colle-le sans rotation ni miroir, avec le coin minimum du\n");
		sheet.append("fichier (x, y et z les plus petits) sur le coin indique. Taille : 48 x 24 x 48 (x, y, z).\n\n");
		for (PlaceRoom p : places) {
			HubSchematic canonical = HubSchematic.read(p.file(), false);
			if (canonical.sizeX() != RoomFrame.SQUARE || canonical.sizeZ() != RoomFrame.SQUARE) {
				throw new IOException("room " + p.roomId() + " is " + canonical.sizeX() + " x " + canonical.sizeZ() + ", not 48 x 48");
			}
			int k = RoomFrame.quarterTurns(p.orientation());
			HubSchematic placed = RoomTurn.turn(canonical, k, turner.apply(k));
			turned.add(placed);
			String base = fileName(p.placeId(), used);
			String title = p.roomName() == null || p.roomName().isBlank() ? p.roomId() : p.roomName();
			byte[] sponge = SchematicWriter.sponge(placed, dataVersion, title, author);
			byte[] litematic = SchematicWriter.litematic(placed, dataVersion, title, author);
			verify(sponge, false, placed, base + ".schem");
			verify(litematic, true, placed, base + ".litematic");
			Files.write(folder.resolve(base + ".schem"), sponge);
			Files.write(folder.resolve(base + ".litematic"), litematic);
			files.add(base + ".schem");
			files.add(base + ".litematic");

			sheet.append("== ").append(p.placeId()).append(" (manche ").append(p.round()).append(") ==\n");
			sheet.append("Salle : ").append(title).append(" [").append(p.roomId()).append("]\n");
			sheet.append("Orientation de la place : ").append(p.orientation()).append(" (").append(k).append(" quart(s) de tour horaire ; le nord de la salle, sa sortie, regarde ")
					.append(p.orientation()).append(")\n");
			sheet.append("Coin où coller (minimum) : x=").append(p.originX()).append(" y=").append(p.originY()).append(" z=").append(p.originZ())
					.append(world(world, p.originX(), p.originZ())).append("\n");
			sheet.append("Coin opposé (maximum) : x=").append(p.originX() + placed.sizeX() - 1).append(" y=").append(p.originY() + placed.sizeY() - 1)
					.append(" z=").append(p.originZ() + placed.sizeZ() - 1)
					.append(world(world, p.originX() + placed.sizeX() - 1, p.originZ() + placed.sizeZ() - 1)).append("\n");
			sheet.append("Fichiers : ").append(base).append(".schem ou ").append(base).append(".litematic\n");
			sheet.append("Blocs : ").append(placed.blocks().size()).append(", dont ")
					.append(placed.blocks().stream().filter(b -> b.blockEntity() != null).count()).append(" avec données (panneaux, têtes...)\n");
			if (p.room() != null && p.room().slots != null && !p.room().slots.isEmpty()) {
				sheet.append("Emplacements après rotation (coin minimum, hub) :\n");
				for (RoomDto.SlotDto slot : p.room().slots) {
					RoomDto.P at = slot.position == null ? new RoomDto.P() : slot.position;
					RoomDto.P size = slot.size == null ? new RoomDto.P(1, 1, 1) : slot.size;
					double[] box = RoomFrame.box(p.originX(), p.originY(), p.originZ(), k, at.x, at.y, at.z, size.x, size.y, size.z);
					sheet.append("  - ").append(slot.id).append(" (").append(slot.kind).append(") : x=").append((int) Math.floor(box[0])).append(" y=")
							.append((int) Math.floor(box[1])).append(" z=").append((int) Math.floor(box[2])).append(", regarde ")
							.append(RoomFrame.facing(slot.orientation, k)).append("\n");
				}
			}
			sheet.append("\n");
		}

		try {
			combined(folder, places, turned, dataVersion, author, files, notes, sheet, world);
		} catch (OutOfMemoryError e) {
			notes.add("all_places: not enough memory, skipped");
			sheet.append("== Fichier unique ==\nNon écrit : pas assez de mémoire pour une boîte aussi grande. Utilise les fichiers par place.\n\n");
		}
		sheet.append("À confirmer en jeu avec l'admin : qu'Axiom colle le coin minimum du fichier sur le coin indiqué, sans décalage,\n");
		sheet.append("pour les deux formats (à essayer sur une seule salle avant la première soirée).\n");
		Files.writeString(folder.resolve("paste_sheet.txt"), sheet.toString(), StandardCharsets.UTF_8);
		files.add("paste_sheet.txt");
		return new Written(folder, List.copyOf(files), List.copyOf(notes));
	}

	/** The single file of every place at its relative position, when it is not too big. */
	private static void combined(Path folder, List<PlaceRoom> places, List<HubSchematic> turned, int dataVersion, String author,
			List<String> files, List<String> notes, StringBuilder sheet, int[] world) throws IOException {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int i = 0; i < places.size(); i++) {
			PlaceRoom p = places.get(i);
			HubSchematic s = turned.get(i);
			minX = Math.min(minX, p.originX());
			minY = Math.min(minY, p.originY());
			minZ = Math.min(minZ, p.originZ());
			maxX = Math.max(maxX, p.originX() + s.sizeX());
			maxY = Math.max(maxY, p.originY() + s.sizeY());
			maxZ = Math.max(maxZ, p.originZ() + s.sizeZ());
		}
		if (maxX - minX > 65535 || maxY - minY > 65535 || maxZ - minZ > 65535) {
			notes.add("all_places: box of " + (maxX - minX) + " x " + (maxY - minY) + " x " + (maxZ - minZ) + " is past the formats' 65535 limit, skipped");
			sheet.append("== Fichier unique ==\nNon écrit : les places sont trop éloignées pour le format (").append(maxX - minX).append(" x ").append(maxY - minY)
					.append(" x ").append(maxZ - minZ).append(" blocs). Utilise les fichiers par place.\n\n");
			return;
		}
		List<HubSchematic.Block> blocks = new ArrayList<>();
		for (int i = 0; i < places.size(); i++) {
			PlaceRoom p = places.get(i);
			for (HubSchematic.Block b : turned.get(i).blocks()) {
				blocks.add(new HubSchematic.Block(b.x() + p.originX() - minX, b.y() + p.originY() - minY, b.z() + p.originZ() - minZ, b.state(), b.blockEntity()));
			}
		}
		HubSchematic all = new HubSchematic(maxX - minX, maxY - minY, maxZ - minZ, blocks);
		byte[] sponge = SchematicWriter.sponge(all, dataVersion, "all_places", author);
		byte[] litematic = SchematicWriter.litematic(all, dataVersion, "all_places", author);
		verify(sponge, false, all, "all_places.schem");
		verify(litematic, true, all, "all_places.litematic");
		Files.write(folder.resolve("all_places.schem"), sponge);
		Files.write(folder.resolve("all_places.litematic"), litematic);
		files.add("all_places.schem");
		files.add("all_places.litematic");
		sheet.append("== Fichier unique des places (all_places.schem ou all_places.litematic) ==\n");
		sheet.append("Les ").append(places.size()).append(" places à leurs positions relatives, déjà tournées.\n");
		sheet.append("Coin où coller (minimum) : x=").append(minX).append(" y=").append(minY).append(" z=").append(minZ).append(world(world, minX, minZ))
				.append("\n");
		sheet.append("Taille : ").append(maxX - minX).append(" x ").append(maxY - minY).append(" x ").append(maxZ - minZ).append(" (x, y, z)\n\n");
	}

	/** " (monde : x=.., z=..)" for a hub block, or nothing without the exporter's world (D81). */
	static String world(int[] world, int hubX, int hubZ) {
		return world == null ? "" : " (monde : x=" + (world[0] + hubX) + " z=" + (world[1] + hubZ) + ")";
	}

	/** {@code place_<id>}, the id made safe for a file name and kept unique. */
	static String fileName(String placeId, Map<String, Integer> used) {
		String id = placeId == null || placeId.isBlank() ? "x" : placeId.replaceAll("[^A-Za-z0-9_-]", "_");
		String base = id.toLowerCase(Locale.ROOT).startsWith("place") ? id : "place_" + id;
		int n = used.merge(base, 1, Integer::sum);
		return n == 1 ? base : base + "_" + n;
	}

	/** Reads a written file back with the hub's reader and compares it to what was meant, block by block. */
	static void verify(byte[] file, boolean litematic, HubSchematic meant, String label) throws IOException {
		String problem = compare(meant, HubSchematic.read(file, litematic));
		if (problem != null) {
			throw new IOException(label + " does not read back: " + problem);
		}
	}

	/** Null when the two have the same box and the same blocks (state and block entity data), else what differs. */
	public static String compare(HubSchematic expected, HubSchematic actual) {
		if (expected.sizeX() != actual.sizeX() || expected.sizeY() != actual.sizeY() || expected.sizeZ() != actual.sizeZ()) {
			return "size " + actual.sizeX() + "x" + actual.sizeY() + "x" + actual.sizeZ() + " instead of " + expected.sizeX() + "x" + expected.sizeY() + "x"
					+ expected.sizeZ();
		}
		if (expected.blocks().size() != actual.blocks().size()) {
			return actual.blocks().size() + " blocks instead of " + expected.blocks().size();
		}
		Map<Long, HubSchematic.Block> byPos = new HashMap<>();
		for (HubSchematic.Block b : actual.blocks()) {
			byPos.put(key(b), b);
		}
		for (HubSchematic.Block e : expected.blocks()) {
			HubSchematic.Block a = byPos.get(key(e));
			if (a == null) {
				return "missing block at " + e.x() + " " + e.y() + " " + e.z();
			}
			if (!SchematicWriter.stateText(e.state()).equals(SchematicWriter.stateText(a.state()))) {
				return "state at " + e.x() + " " + e.y() + " " + e.z() + " is " + SchematicWriter.stateText(a.state()) + " instead of "
						+ SchematicWriter.stateText(e.state());
			}
			if (e.blockEntity() == null ? a.blockEntity() != null : !e.blockEntity().equals(a.blockEntity())) {
				return "block entity data at " + e.x() + " " + e.y() + " " + e.z();
			}
		}
		return null;
	}

	private static long key(HubSchematic.Block b) {
		return ((long) b.x() & 0x1FFFFF) << 42 | ((long) b.y() & 0x1FFFFF) << 21 | ((long) b.z() & 0x1FFFFF);
	}

	/** A place of the plan and the room drawn for it. */
	public record Pairing(LayoutDto.Slot place, String roomId) {
	}

	/**
	 * Pairs each drawn room with its place by place id, in the order of {@code byPlace} (the backend's). Every place of the
	 * plan must be covered and none unknown, else {@code IllegalArgumentException} ({@code count:got:expected},
	 * {@code unknown_place:id}, {@code no_places}).
	 */
	public static List<Pairing> pair(LayoutDto layout, Map<String, String> byPlace) {
		if (layout.roomSlots.isEmpty()) {
			throw new IllegalArgumentException("no_places");
		}
		List<Pairing> out = new ArrayList<>();
		for (Map.Entry<String, String> e : byPlace.entrySet()) {
			LayoutDto.Slot place = layout.roomSlots.stream().filter(s -> e.getKey().equals(s.id)).findFirst()
					.orElseThrow(() -> new IllegalArgumentException("unknown_place:" + e.getKey()));
			out.add(new Pairing(place, e.getValue()));
		}
		if (out.size() != layout.roomSlots.size()) {
			throw new IllegalArgumentException("count:" + out.size() + ":" + layout.roomSlots.size());
		}
		return out;
	}

	/** The places sorted the way the draw lists its rooms: by round, then by id. */
	public static List<LayoutDto.Slot> orderedPlaces(LayoutDto layout) {
		List<LayoutDto.Slot> slots = new ArrayList<>(layout.roomSlots);
		slots.sort(Comparator.comparingInt((LayoutDto.Slot s) -> s.round == null ? 0 : s.round).thenComparing(s -> s.id == null ? "" : s.id));
		return slots;
	}
}
