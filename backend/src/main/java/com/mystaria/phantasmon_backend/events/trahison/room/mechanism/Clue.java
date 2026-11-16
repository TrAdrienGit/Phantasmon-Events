package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A typed piece of information a mechanism produces when it generates its solution. {@code role} names it inside the
 * mechanism ({@code "code"}); {@link com.mystaria.phantasmon_backend.events.trahison.room.Give} says in which slot of the
 * room it shows up. A clue is meant to be seen by the players once its step is open, so it is not a secret of the step.
 */
public record Clue(String role, ClueType type, JsonNode value) {

	public ObjectNode toJson() {
		ObjectNode node = Json.object();
		node.put("role", role);
		node.put("type", type.wire());
		node.set("value", value);
		return node;
	}

	public static Clue digits(String role, String digits) {
		return new Clue(role, ClueType.DIGITS, Json.text(digits));
	}

	public static Clue text(String role, String text) {
		return new Clue(role, ClueType.TEXT, Json.text(text));
	}

	/** A document of several pages (see {@link ClueType#DOCUMENT}). */
	public static Clue document(String role, java.util.List<String> pages) {
		tools.jackson.databind.node.ArrayNode array = Json.array();
		pages.forEach(array::add);
		return new Clue(role, ClueType.DOCUMENT, array);
	}

	public static Clue item(String role, String itemId) {
		return new Clue(role, ClueType.ITEM, Json.text(itemId));
	}
}
