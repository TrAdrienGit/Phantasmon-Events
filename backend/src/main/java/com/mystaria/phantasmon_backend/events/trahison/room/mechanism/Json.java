package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Small helpers to build JSON nodes without a Spring mapper (the mechanisms stay pure). */
public final class Json {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private Json() {
	}

	public static ObjectNode object() {
		return MAPPER.createObjectNode();
	}

	public static ArrayNode array() {
		return MAPPER.createArrayNode();
	}

	public static JsonNode text(String value) {
		return MAPPER.getNodeFactory().stringNode(value);
	}

	public static String write(JsonNode node) {
		return MAPPER.writeValueAsString(node);
	}
}
