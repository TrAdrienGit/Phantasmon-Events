package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.Set;

import tools.jackson.databind.JsonNode;

/**
 * An action a player sends from inside a room ({@code EventsTrahisonAction}, protocole.md section 3):
 * {@code use_slot {slot}}, {@code puzzle_input {step, input}}, {@code pick_up {slot}}, the inventory's
 * {@code give_item {item, to}}, {@code use_item {item, slot}}, {@code combine {a, b}}, and the free time's {@code replay}.
 * Every one may carry a {@code group} (see {@link #targetGroup}): the room it concerns, own group when absent.
 */
public sealed interface RoomAction {

	/** The action names {@link #parse} understands, so a handler can route them to the room service. */
	Set<String> NAMES = Set.of("use_slot", "puzzle_input", "pick_up", "give_item", "use_item", "combine", "replay");

	record UseSlot(String slot) implements RoomAction {
	}

	record PuzzleInput(String step, JsonNode input) implements RoomAction {
	}

	record PickUp(String slot) implements RoomAction {
	}

	/** Hand an item the actor carries to the group member with index {@code to}. */
	record GiveItem(String item, int to) implements RoomAction {
	}

	/** Use an item the actor carries on an active slot. */
	record UseItem(String item, String slot) implements RoomAction {
	}

	/** Combine two items the actor carries, following a recipe of the room. */
	record CombineItems(String a, String b) implements RoomAction {
	}

	/** Puts the room back to a fresh puzzle state (free time only, D8): new solutions, no points, nothing new opens. */
	record Replay() implements RoomAction {
	}

	/**
	 * The room an action is aimed at ({@code group} of the message: 0..2), or null for the sender's own. Only the free time
	 * accepts a group other than the sender's (D8: players visit the other rooms).
	 */
	static Integer targetGroup(JsonNode data) {
		JsonNode g = data == null ? null : data.get("group");
		if (g == null || g.isNull()) {
			return null;
		}
		if (!g.isNumber() || g.asInt() < 0) {
			throw new RoomException(RoomException.BAD_ACTION, "group must be a group index");
		}
		return g.asInt();
	}

	/** Reads the {@code data} of an {@code EventsTrahisonAction}; a malformed one raises {@code ERROR_TRAHISON_BAD_ACTION}. */
	static RoomAction parse(JsonNode data) {
		String action = data == null ? "" : data.path("action").asString("");
		return switch (action) {
			case "use_slot" -> new UseSlot(text(data, "slot"));
			case "pick_up" -> new PickUp(text(data, "slot"));
			case "give_item" -> {
				JsonNode to = data.get("to");
				if (to == null || !to.isNumber() || to.asInt() < 0) {
					throw new RoomException(RoomException.BAD_ACTION, "give_item needs a player index in to");
				}
				yield new GiveItem(text(data, "item"), to.asInt());
			}
			case "replay" -> new Replay();
			case "use_item" -> new UseItem(text(data, "item"), text(data, "slot"));
			case "combine" -> new CombineItems(text(data, "a"), text(data, "b"));
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
