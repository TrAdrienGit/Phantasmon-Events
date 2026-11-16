package com.mystaria.events.trahison.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import com.mystaria.events.trahison.edit.TrahisonExport.PlaceRoom;
import com.mystaria.phantasmon.client.hub.HubSchematic;

/**
 * Round trips of the two writers through the hub's own reader, in the four orientations. No Minecraft bootstrap: the state
 * turner is a stand-in that turns {@code facing} the way {@code BlockState.rotate} does (north, east, south, west clockwise).
 */
class SchematicWriterTest {

	private static final String[] SIDES = { "north", "east", "south", "west" };
	private static final int DATA_VERSION = 3955;

	/** What {@code BlockState.rotate} does to the {@code facing} of stairs and signs, and to a sign's 16-step {@code rotation}. */
	private static UnaryOperator<CompoundTag> fakeTurner(int k) {
		return tag -> {
			CompoundTag p = tag.getCompound("Properties");
			if (p.contains("facing")) {
				for (int i = 0; i < 4; i++) {
					if (SIDES[i].equals(p.getString("facing"))) {
						p.putString("facing", SIDES[(i + k) % 4]);
						break;
					}
				}
			}
			if (p.contains("rotation")) {
				p.putString("rotation", Integer.toString((Integer.parseInt(p.getString("rotation")) + 4 * k) % 16));
			}
			return tag;
		};
	}

	private static CompoundTag state(String name, String... props) {
		CompoundTag t = new CompoundTag();
		t.putString("Name", name);
		if (props.length > 0) {
			CompoundTag p = new CompoundTag();
			for (int i = 0; i < props.length; i += 2) {
				p.putString(props[i], props[i + 1]);
			}
			t.put("Properties", p);
		}
		return t;
	}

	private static CompoundTag sign(String text) {
		CompoundTag t = new CompoundTag();
		t.putString("id", "minecraft:sign");
		t.putBoolean("is_waxed", true);
		CompoundTag front = new CompoundTag();
		ListTag lines = new ListTag();
		lines.add(StringTag.valueOf(text));
		front.put("messages", lines);
		t.put("front_text", front);
		return t;
	}

	/** A 48 x 24 x 48 room with a floor, stairs, a door, a sign carrying data, a corner block, and `extra` more distinct states. */
	private static HubSchematic room(int extra) {
		List<HubSchematic.Block> blocks = new ArrayList<>();
		for (int x = 0; x < 48; x += 3) {
			for (int z = 0; z < 48; z += 3) {
				blocks.add(new HubSchematic.Block(x, 0, z, state("minecraft:stone"), null));
			}
		}
		blocks.add(new HubSchematic.Block(5, 1, 7, state("minecraft:oak_stairs", "facing", "north", "half", "bottom", "shape", "straight"), null));
		blocks.add(new HubSchematic.Block(6, 1, 7, state("minecraft:oak_door", "facing", "east", "half", "lower"), null));
		blocks.add(new HubSchematic.Block(40, 2, 3, state("minecraft:oak_wall_sign", "facing", "south"), sign("hello")));
		blocks.add(new HubSchematic.Block(1, 2, 44, state("minecraft:oak_sign", "rotation", "2"), sign("up")));
		blocks.add(new HubSchematic.Block(0, 23, 0, state("minecraft:gold_block"), null));
		blocks.add(new HubSchematic.Block(47, 23, 47, state("minecraft:diamond_block"), null));
		blocks.add(new HubSchematic.Block(47, 0, 1, state("minecraft:emerald_block"), null));
		for (int i = 0; i < extra; i++) {
			blocks.add(new HubSchematic.Block(10 + i % 20, 5 + i / 20, 20, state("test:block_" + i, "n", Integer.toString(i)), null));
		}
		return new HubSchematic(48, 24, 48, blocks);
	}

	private static HubSchematic.Block block(HubSchematic s, int x, int y, int z) {
		return s.blocks().stream().filter(b -> b.x() == x && b.y() == y && b.z() == z).findFirst().orElse(null);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3 })
	void bothFormatsReadBackBlockForBlockInEveryOrientation(int k) throws IOException {
		HubSchematic turned = RoomTurn.turn(room(0), k, fakeTurner(k));
		assertEquals(48, turned.sizeX());
		assertEquals(48, turned.sizeZ());
		for (boolean litematic : new boolean[] { false, true }) {
			byte[] file = litematic ? SchematicWriter.litematic(turned, DATA_VERSION, "t", "a") : SchematicWriter.sponge(turned, DATA_VERSION, "t", "a");
			assertNull(TrahisonExport.compare(turned, HubSchematic.read(file, litematic)), "format litematic=" + litematic);
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3 })
	void blocksStatesAndBlockEntitiesTurnTogether(int k) {
		HubSchematic turned = RoomTurn.turn(room(0), k, fakeTurner(k));
		// Corner markers: (0,0) gold, (47,47) diamond, (47,1) emerald, in a 48 x 48 square turned clockwise k times.
		int[][] gold = { { 0, 0 }, { 47, 0 }, { 47, 47 }, { 0, 47 } };
		int[][] diamond = { { 47, 47 }, { 0, 47 }, { 0, 0 }, { 47, 0 } };
		assertNotNull(block(turned, gold[k][0], 23, gold[k][1]), "gold at its turned corner");
		assertNotNull(block(turned, diamond[k][0], 23, diamond[k][1]), "diamond at its turned corner");
		// Stairs at (5, 7) facing north, door facing east: they turn with the room.
		int[] stairs = RoomTurn.position(5, 7, 48, 48, k);
		HubSchematic.Block s = block(turned, stairs[0], 1, stairs[1]);
		assertEquals(SIDES[k % 4], s.state().getCompound("Properties").getString("facing"));
		int[] door = RoomTurn.position(6, 7, 48, 48, k);
		assertEquals(SIDES[(1 + k) % 4], block(turned, door[0], 1, door[1]).state().getCompound("Properties").getString("facing"));
		// The sign's data rides on its block, its 16-step rotation turns by 4 per quarter turn.
		int[] sign = RoomTurn.position(1, 44, 48, 48, k);
		HubSchematic.Block upright = block(turned, sign[0], 2, sign[1]);
		assertEquals(Integer.toString((2 + 4 * k) % 16), upright.state().getCompound("Properties").getString("rotation"));
		assertEquals("minecraft:sign", upright.blockEntity().getString("id"));
		// Four more turns bring everything back.
		HubSchematic back = RoomTurn.turn(turned, 4 - k, fakeTurner(4 - k));
		assertNull(TrahisonExport.compare(room(0), back));
	}

	@Test
	void turnedPositionsAgreeWithTheContinuousRoomFrame() {
		for (int k = 0; k < 4; k++) {
			for (int[] p : new int[][] { { 0, 0 }, { 5, 40 }, { 47, 3 }, { 23, 47 } }) {
				int[] b = RoomTurn.position(p[0], p[1], 48, 48, k);
				double[] c = com.mystaria.events.trahison.world.RoomFrame.rotate(p[0] + 0.5, p[1] + 0.5, k);
				assertEquals(Math.floor(c[0]), b[0], "x k=" + k);
				assertEquals(Math.floor(c[1]), b[1], "z k=" + k);
			}
		}
	}

	@Test
	void paletteBeyondOneByteAndLitematicBitsCrossLongBoundaries() throws IOException {
		HubSchematic big = room(300);
		for (boolean litematic : new boolean[] { false, true }) {
			byte[] file = litematic ? SchematicWriter.litematic(big, DATA_VERSION, "t", "a") : SchematicWriter.sponge(big, DATA_VERSION, "t", "a");
			assertNull(TrahisonExport.compare(big, HubSchematic.read(file, litematic)));
		}
		// 7 states + air = 8 entries: 3 bits, which does not divide 64.
		HubSchematic small = new HubSchematic(5, 5, 5, room(0).blocks().stream().limit(0).toList());
		List<HubSchematic.Block> blocks = new ArrayList<>();
		for (int i = 0; i < 125; i++) {
			blocks.add(new HubSchematic.Block(i % 5, i / 25, (i / 5) % 5, state("test:b" + (i % 7)), null));
		}
		small = new HubSchematic(5, 5, 5, blocks);
		assertNull(TrahisonExport.compare(small, HubSchematic.read(SchematicWriter.litematic(small, DATA_VERSION, "t", "a"), true)));
		assertNull(TrahisonExport.compare(small, HubSchematic.read(SchematicWriter.sponge(small, DATA_VERSION, "t", "a"), false)));
	}

	@Test
	void anEmptyBoxAndAnOutOfBoxBlockAreHandled() throws IOException {
		HubSchematic empty = new HubSchematic(4, 3, 2, List.of());
		assertNull(TrahisonExport.compare(empty, HubSchematic.read(SchematicWriter.sponge(empty, DATA_VERSION, "e", "a"), false)));
		assertNull(TrahisonExport.compare(empty, HubSchematic.read(SchematicWriter.litematic(empty, DATA_VERSION, "e", "a"), true)));
		HubSchematic bad = new HubSchematic(2, 2, 2, List.of(new HubSchematic.Block(2, 0, 0, state("minecraft:stone"), null)));
		org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> SchematicWriter.sponge(bad, DATA_VERSION, "b", "a"));
	}

	@Test
	void theExportWritesEveryFileForNinePlacesInAllOrientations(@TempDir Path folder) throws IOException {
		byte[] canonical = SchematicWriter.sponge(room(0), DATA_VERSION, "room", "a");
		RoomDto def = new RoomDto();
		RoomDto.SlotDto slot = new RoomDto.SlotDto();
		slot.id = "wall";
		slot.kind = "wall_clue";
		slot.position = new RoomDto.P(10, 1, 0);
		slot.orientation = "south";
		slot.size = new RoomDto.P(1, 1, 1);
		def.slots.add(slot);
		List<PlaceRoom> places = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			places.add(new PlaceRoom("place_" + (i / 3 + 1) + "_" + (char) ('a' + i % 3), i / 3 + 1, 100 + (i % 3) * 60, 64, -200 + (i / 3) * 60,
					SIDES[i % 4], "room" + i, "Room " + i, canonical, def));
		}
		TrahisonExport.Written written = TrahisonExport.write(folder, "g1", places, DATA_VERSION, "a", RoomTurn_fake());
		assertTrue(Files.exists(folder.resolve("place_1_a.schem")));
		assertTrue(Files.exists(folder.resolve("place_3_c.litematic")));
		assertTrue(Files.exists(folder.resolve("all_places.schem")));
		assertTrue(Files.exists(folder.resolve("all_places.litematic")));
		assertTrue(written.files().contains("paste_sheet.txt"));
		String sheet = Files.readString(folder.resolve("paste_sheet.txt"));
		assertTrue(sheet.contains("x=160 y=64 z=-200"), sheet);
		assertTrue(sheet.contains("wall (wall_clue)"));

		// The single file holds every place where its origin says: read it back and look at one marker per place.
		HubSchematic all = HubSchematic.read(Files.readAllBytes(folder.resolve("all_places.schem")), false);
		assertEquals(60 * 2 + 48, all.sizeX());
		int[] minOrigin = { 100, 64, -200 };
		for (PlaceRoom p : places) {
			int k = com.mystaria.events.trahison.world.RoomFrame.quarterTurns(p.orientation());
			int[] gold = RoomTurn.position(0, 0, 48, 48, k);
			HubSchematic.Block b = block(all, p.originX() - minOrigin[0] + gold[0], 23, p.originZ() - minOrigin[2] + gold[1]);
			assertNotNull(b, p.placeId());
			assertEquals("minecraft:gold_block", b.name());
		}
	}

	private static java.util.function.IntFunction<UnaryOperator<CompoundTag>> RoomTurn_fake() {
		return SchematicWriterTest::fakeTurner;
	}

	@Test
	void minecraftsOwnRotationTurnsRealStates() {
		try {
			net.minecraft.SharedConstants.tryDetectVersion();
			net.minecraft.server.Bootstrap.bootStrap();
		} catch (Throwable unavailable) {
			org.junit.jupiter.api.Assumptions.abort("Minecraft bootstrap unavailable: " + unavailable);
		}
		CompoundTag stairs = state("minecraft:oak_stairs", "facing", "north", "half", "bottom", "shape", "straight", "waterlogged", "false");
		CompoundTag turned = RoomTurn.minecraftStates(1).apply(stairs.copy());
		assertEquals("east", turned.getCompound("Properties").getString("facing"));
		CompoundTag log = state("minecraft:oak_log", "axis", "x");
		assertEquals("z", RoomTurn.minecraftStates(1).apply(log.copy()).getCompound("Properties").getString("axis"));
		CompoundTag unknown = state("nomod:thing", "facing", "north");
		assertEquals(unknown, RoomTurn.minecraftStates(1).apply(unknown.copy()));
		CompoundTag sign = state("minecraft:oak_sign", "rotation", "0", "waterlogged", "false");
		assertEquals("4", RoomTurn.minecraftStates(1).apply(sign.copy()).getCompound("Properties").getString("rotation"));
	}
}
