package com.mystaria.events.trahison.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon.client.hub.HubSchematic;

class PlaceCheckTest {

	private static LayoutDto.Slot slot(String id, int round) {
		LayoutDto.Slot s = new LayoutDto.Slot();
		s.id = id;
		s.round = round;
		return s;
	}

	/** The plan lists its places in a different order than their ids: the pairing must follow the place ids. */
	@Test
	void roomsAreKeyedByPlaceIdWhateverTheOrder() {
		LayoutDto layout = new LayoutDto();
		layout.roomSlots.add(slot("z_first", 1));
		layout.roomSlots.add(slot("a_second", 1));
		Map<String, String> byPlace = new LinkedHashMap<>();
		byPlace.put("z_first", "roomA");
		byPlace.put("a_second", "roomB");
		List<TrahisonExport.Pairing> pairs = TrahisonExport.pair(layout, byPlace);
		assertEquals("z_first", pairs.get(0).place().id);
		assertEquals("roomA", pairs.get(0).roomId());
		assertEquals("a_second", pairs.get(1).place().id);
		assertEquals("roomB", pairs.get(1).roomId());
	}

	@Test
	void unknownOrMissingPlacesAreRefused() {
		LayoutDto layout = new LayoutDto();
		layout.roomSlots.add(slot("p1", 1));
		layout.roomSlots.add(slot("p2", 1));
		assertEquals("unknown_place:nope", assertThrows(IllegalArgumentException.class, () -> TrahisonExport.pair(layout, Map.of("nope", "r"))).getMessage());
		assertEquals("count:1:2", assertThrows(IllegalArgumentException.class, () -> TrahisonExport.pair(layout, Map.of("p1", "r"))).getMessage());
		assertEquals("no_places", assertThrows(IllegalArgumentException.class, () -> TrahisonExport.pair(new LayoutDto(), Map.of())).getMessage());
	}

	@Test
	void classifiesTheMatches() {
		assertEquals(PlaceCheck.Status.OK, PlaceCheck.classify(600, 600, 0));
		assertEquals(PlaceCheck.Status.OK, PlaceCheck.classify(0, 0, 0));
		assertEquals(PlaceCheck.Status.MISSING, PlaceCheck.classify(600, 5, 0));
		assertEquals(PlaceCheck.Status.WRONG, PlaceCheck.classify(600, 400, 0));
		assertEquals(PlaceCheck.Status.UNLOADED, PlaceCheck.classify(600, 0, 400));
	}

	@Test
	void sampleKeepsAtMostTheLimitAndSkipsAir() {
		List<HubSchematic.Block> blocks = new ArrayList<>();
		for (int i = 0; i < 2000; i++) {
			CompoundTag state = new CompoundTag();
			state.putString("Name", i % 4 == 0 ? "minecraft:air" : "minecraft:stone");
			blocks.add(new HubSchematic.Block(i % 48, 0, i / 48, state, null));
		}
		List<HubSchematic.Block> sample = PlaceCheck.sample(blocks, 100);
		assertEquals(100, sample.size());
		assertTrue(sample.stream().noneMatch(b -> b.name().endsWith("air")));
	}

	@Test
	void blockDataIsComparedWithoutPositionAndId() {
		CompoundTag expected = new CompoundTag();
		expected.putString("id", "minecraft:sign");
		expected.putInt("x", 1);
		expected.putString("text", "Exit");
		CompoundTag actual = expected.copy();
		actual.putInt("x", 99);
		actual.putString("extra", "ignored");
		assertTrue(PlaceCheck.dataEquals(expected, actual));
		actual.putString("text", "Other");
		assertEquals(false, PlaceCheck.dataEquals(expected, actual));
	}

	@Test
	void sampleKeepsBlocksWithDataFirst() {
		List<HubSchematic.Block> blocks = new ArrayList<>();
		for (int i = 0; i < 1000; i++) {
			CompoundTag state = new CompoundTag();
			state.putString("Name", "minecraft:stone");
			blocks.add(new HubSchematic.Block(i % 48, 0, i / 48, state, i % 100 == 0 ? new CompoundTag() : null));
		}
		List<HubSchematic.Block> sample = PlaceCheck.sample(blocks, 100);
		assertEquals(100, sample.size());
		assertEquals(10, sample.stream().filter(b -> b.blockEntity() != null).count());
	}
}
