package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.List;

/**
 * A colour door that is open for a few seconds because the bracelets at its threshold satisfy it. {@code untilMs}: epoch
 * millis when it closes; {@code expected}: the players who stood at the threshold with a bracelet of the group when it
 * opened (the door closes at once when all of them have crossed).
 */
public record DoorWindow(long untilMs, List<Integer> expected) {

	public DoorWindow {
		expected = expected == null ? List.of() : List.copyOf(expected);
	}
}
