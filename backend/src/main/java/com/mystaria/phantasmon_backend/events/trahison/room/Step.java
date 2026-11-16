package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * One link of a room's chain. {@code slots} maps each role of the mechanism (the role {@code exit} opens the room's
 * exit) to a slot id; {@code needs} are the steps to finish first; {@code gives} places the step's clues; {@code hints}
 * are up to three texts, from vague to direct.
 */
public record Step(String id, String mechanism, JsonNode params, Map<String, String> slots, List<String> needs,
		List<Give> gives, List<String> hints) {

	public static final int MAX_HINTS = 3;
	public static final String EXIT_ROLE = "exit";

	public Step {
		params = params == null ? JsonNodeFactory.instance.objectNode() : params;
		slots = slots == null ? Map.of() : Map.copyOf(slots);
		needs = needs == null ? List.of() : List.copyOf(needs);
		gives = gives == null ? List.of() : List.copyOf(gives);
		hints = hints == null ? List.of() : List.copyOf(hints);
	}
}
