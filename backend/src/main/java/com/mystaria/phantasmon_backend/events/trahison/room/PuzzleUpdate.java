package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.List;

import tools.jackson.databind.node.ObjectNode;

/**
 * News about one puzzle. {@code reason}: {@code open} (a player opened its screen: send to {@code onlyTo}), {@code input}
 * (a gesture of {@code player}, with its {@code events}, for everyone who watches), {@code solved}, {@code reset}.
 * {@code onlyTo}: the one player index to send to; null: the group, the spectators and the game masters.
 */
public record PuzzleUpdate(String step, String reason, Integer player, List<ObjectNode> events, Integer onlyTo) {

	public PuzzleUpdate {
		events = events == null ? List.of() : List.copyOf(events);
	}
}
