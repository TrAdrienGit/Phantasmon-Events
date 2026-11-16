package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.List;

/**
 * A role of a mechanism that a step binds to a slot of the room ({@code Step.slots}): its name and the kinds of slot it
 * accepts ({@code RoomValidator.KINDS}). Listed by {@code GET /events/trahison/mechanisms} for the room editor.
 */
public record SlotRole(String role, List<String> kinds) {

	public SlotRole {
		kinds = List.copyOf(kinds);
	}

	public static SlotRole of(String role, String... kinds) {
		return new SlotRole(role, List.of(kinds));
	}
}
