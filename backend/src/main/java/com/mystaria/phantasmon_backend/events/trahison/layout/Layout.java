package com.mystaria.phantasmon_backend.events.trahison.layout;

import java.time.Instant;
import java.util.List;

/**
 * A saved plan of a hub. {@code version} is 0 for a hub that has none yet (then everything is empty). A {@code draft}
 * is an incomplete plan; a game only starts on a complete one.
 */
public record Layout(String hub, int version, boolean draft, Instant updatedAt, List<LayoutZone> zones, List<LayoutDoor> doors,
		List<RoomSlot> roomSlots, List<Cabin> cabins, List<HidingSpot> hidingSpots, ResultsBoard resultsBoard, List<Floor> floors) {

	public static Layout of(String hub, int version, boolean draft, Instant updatedAt, LayoutContent content) {
		return new Layout(hub, version, draft, updatedAt, content.zones(), content.doors(), content.roomSlots(),
				content.cabins(), content.hidingSpots(), content.resultsBoard(), content.floors());
	}

	public LayoutContent content() {
		return new LayoutContent(zones, doors, roomSlots, cabins, hidingSpots, resultsBoard, floors);
	}
}
