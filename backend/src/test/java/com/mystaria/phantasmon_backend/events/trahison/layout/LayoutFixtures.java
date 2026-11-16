package com.mystaria.phantasmon_backend.events.trahison.layout;

import java.util.ArrayList;
import java.util.List;

/** A complete, valid plan for the tests: the three rounds, six cabins, nine places and a final door. */
final class LayoutFixtures {

	static final String[] PAIRS = { "red_green", "red_blue", "green_blue" };
	static final String[][] COLORS = { { "red", "green" }, { "red", "blue" }, { "green", "blue" } };

	private LayoutFixtures() {
	}

	/** A 5 x 5 x 5 box at grid cell {@code n} (cells never touch). */
	static Box cell(int n) {
		int x = n * 10;
		return new Box(x, 64, 0, x + 4, 68, 4);
	}

	static LayoutContent complete() {
		List<LayoutZone> zones = new ArrayList<>();
		List<LayoutDoor> doors = new ArrayList<>();
		List<RoomSlot> slots = new ArrayList<>();
		List<Cabin> cabins = new ArrayList<>();
		int n = 0;
		zones.add(new LayoutZone("hall", "hall", List.of(cell(n++))));
		zones.add(new LayoutZone("vote", "vote", List.of(cell(n++))));
		zones.add(new LayoutZone("final", "final", List.of(cell(n++))));
		doors.add(new LayoutDoor("door_final", "final", cell(n++), "hall", "final", null, null));
		for (int round = 1; round <= 3; round++) {
			zones.add(new LayoutZone("doors_" + round, "doors", List.of(cell(n++))));
			for (int p = 0; p < 3; p++) {
				zones.add(new LayoutZone("slot_" + round + "_" + p, "room_slot", List.of(cell(n++))));
				doors.add(new LayoutDoor("door_" + round + "_" + PAIRS[p], "color", cell(n++), "doors_" + round,
						"slot_" + round + "_" + p, round, List.of(COLORS[p])));
				slots.add(new RoomSlot("place_" + round + "_" + p, round, new Pos(n * 10, 64, 100), "north"));
			}
		}
		for (int p = 0; p < 3; p++) {
			for (String side : List.of("pair", "solo")) {
				String id = PAIRS[p] + "_" + side;
				zones.add(new LayoutZone("cabin_" + id, "cabin", List.of(cell(n++))));
				doors.add(new LayoutDoor("cabin_door_" + id, "cabin", cell(n++), "vote", "cabin_" + id, null, null));
				cabins.add(new Cabin("cabin_" + id, PAIRS[p], side, "cabin_" + id, "cabin_door_" + id));
			}
		}
		return new LayoutContent(zones, doors, slots, cabins, List.of(new HidingSpot("spot_1", new Pos(1, 64, 1))));
	}

	/** The plan with its zones, doors, slots or cabins replaced where given (null keeps). */
	static LayoutContent with(LayoutContent base, List<LayoutZone> zones, List<LayoutDoor> doors, List<RoomSlot> slots,
			List<Cabin> cabins) {
		return new LayoutContent(zones == null ? base.zones() : zones, doors == null ? base.doors() : doors,
				slots == null ? base.roomSlots() : slots, cabins == null ? base.cabins() : cabins, base.hidingSpots());
	}
}
