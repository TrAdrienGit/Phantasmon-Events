package com.mystaria.phantasmon_backend.events.trahison.layout;

import java.util.List;

/** The fixed plan of the complex, without its version: what is stored as JSON and what the validator checks. */
public record LayoutContent(List<LayoutZone> zones, List<LayoutDoor> doors, List<RoomSlot> roomSlots, List<Cabin> cabins,
		List<HidingSpot> hidingSpots, ResultsBoard resultsBoard) {

	/** A plan without a results board. */
	public LayoutContent(List<LayoutZone> zones, List<LayoutDoor> doors, List<RoomSlot> roomSlots, List<Cabin> cabins,
			List<HidingSpot> hidingSpots) {
		this(zones, doors, roomSlots, cabins, hidingSpots, null);
	}

	public LayoutContent {
		zones = zones == null ? List.of() : List.copyOf(zones);
		doors = doors == null ? List.of() : List.copyOf(doors);
		roomSlots = roomSlots == null ? List.of() : List.copyOf(roomSlots);
		cabins = cabins == null ? List.of() : List.copyOf(cabins);
		hidingSpots = hidingSpots == null ? List.of() : List.copyOf(hidingSpots);
	}

	public static LayoutContent empty() {
		return new LayoutContent(null, null, null, null, null, null);
	}
}
