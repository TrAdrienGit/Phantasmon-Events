package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.List;

/**
 * What {@link RoomService#apply} did: the step concerned (null if none), whether it is now solved, whether the whole room
 * is now finished (exit open), and the items picked up.
 */
public record ActionResult(String step, boolean stepSolved, boolean roomFinished, List<String> pickedItems) {

	public ActionResult {
		pickedItems = pickedItems == null ? List.of() : List.copyOf(pickedItems);
	}
}
