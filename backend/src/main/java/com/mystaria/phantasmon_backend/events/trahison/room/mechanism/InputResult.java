package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.List;

import tools.jackson.databind.node.ObjectNode;

/** The outcome of a gesture: the new state, the events to show the other players, and whether the step is now solved. */
public record InputResult(MechanismState state, List<ObjectNode> events, boolean solved) {

	public InputResult {
		events = List.copyOf(events);
	}

	/** Nothing happened. */
	public static InputResult unchanged(MechanismState state) {
		return new InputResult(state, List.of(), false);
	}
}
