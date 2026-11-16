package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.Set;

import tools.jackson.databind.JsonNode;

/**
 * An action a player sends from inside a room ({@code EventsTrahisonAction}, protocole.md section 3):
 * {@code use_slot {slot}}, {@code puzzle_input {step, input}}, {@code pick_up {slot}}.
 */
public sealed interface RoomAction {

	/** The action names {@link #parse} understands, so a handler can route them to the room service. */
	Set<String> NAMES = Set.of("use_slot", "puzzle_input", "pick_up");

	record UseSlot(String slot) implements RoomAction {
	}

	record PuzzleInput(String step, JsonNode input) implements RoomAction {
	}

	record PickUp(String slot) implements RoomAction {
	}

	/** Reads the {@code data} of an {@code EventsTrahisonAction}; a malformed one raises {@code ERROR_TRAHISON_BAD_ACTION}. */
	static RoomAction parse(JsonNode data) {
		String action = data == null ? "" : data.path("action").asString("");
		return switch (action) {
			case "use_slot" -> new UseSlot(text(data, "slot"));
			case "pick_up" -> new PickUp(text(data, "slot"));
			case "puzzle_input" -> {
				JsonNode input = data.get("input");
				if (input == null || input.isNull()) {
					throw new RoomException(RoomException.BAD_ACTION, "puzzle_input needs an input");
				}
				yield new PuzzleInput(text(data, "step"), input);
			}
			default -> throw new RoomException(RoomException.BAD_ACTION, "not a room action: " + action);
		};
	}

	private static String text(JsonNode data, String field) {
		String value = data.path(field).asString("");
		if (value.isBlank()) {
			throw new RoomException(RoomException.BAD_ACTION, "missing " + field);
		}
		return value;
	}
}
