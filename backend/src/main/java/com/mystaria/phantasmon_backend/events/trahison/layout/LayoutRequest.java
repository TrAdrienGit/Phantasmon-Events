package com.mystaria.phantasmon_backend.events.trahison.layout;

import java.util.List;

/**
 * Body of a save. {@code baseVersion}: the version the editor started from (absent: no conflict check);
 * {@code draft}: a work in progress, saved without the completeness checks.
 */
public record LayoutRequest(Integer baseVersion, boolean draft, List<LayoutZone> zones, List<LayoutDoor> doors,
		List<RoomSlot> roomSlots, List<Cabin> cabins, List<HidingSpot> hidingSpots, ResultsBoard resultsBoard,
		List<Floor> floors) {

	/** A body without floors. */
	public LayoutRequest(Integer baseVersion, boolean draft, List<LayoutZone> zones, List<LayoutDoor> doors,
			List<RoomSlot> roomSlots, List<Cabin> cabins, List<HidingSpot> hidingSpots, ResultsBoard resultsBoard) {
		this(baseVersion, draft, zones, doors, roomSlots, cabins, hidingSpots, resultsBoard, null);
	}

	/** A body without a results board. */
	public LayoutRequest(Integer baseVersion, boolean draft, List<LayoutZone> zones, List<LayoutDoor> doors,
			List<RoomSlot> roomSlots, List<Cabin> cabins, List<HidingSpot> hidingSpots) {
		this(baseVersion, draft, zones, doors, roomSlots, cabins, hidingSpots, null, null);
	}

	public LayoutContent content() {
		return new LayoutContent(zones, doors, roomSlots, cabins, hidingSpots, resultsBoard, floors);
	}
}
