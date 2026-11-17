package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** The shared gesture of the "read something, type the code" Cobblemon steps: {@code {"code": "0251"}}. */
final class CodeEntry {

	private CodeEntry() {
	}

	/** Compares the typed code with {@code data.code}; the right one solves the step, a wrong one counts an error. */
	static InputResult submit(MechanismState state, JsonNode input, int maxLength) {
		String code = input != null && input.path("code").isString() ? input.get("code").asString() : null;
		if (code == null || !code.matches("[0-9]{1," + maxLength + "}")) {
			throw new IllegalArgumentException("code: 1 to " + maxLength + " digits");
		}
		ObjectNode old = state.data();
		if (old.path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		ObjectNode data = state.copyData();
		ObjectNode event = Json.object();
		boolean solved = code.equals(old.path("code").asString(""));
		if (solved) {
			data.put("solved", true);
			event.put("type", "unlock");
		} else {
			data.put("wrong", old.path("wrong").asInt() + 1);
			event.put("type", "wrong");
		}
		return new InputResult(state.withData(data), List.of(event), solved);
	}

	/** The view every code step starts from: error count and solved flag (the game master also gets the code). */
	static ObjectNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		ObjectNode view = Json.object();
		view.put("wrong", d.path("wrong").asInt());
		view.put("solved", d.path("solved").asBoolean(false));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.put("code", d.path("code").asString(""));
		}
		return view;
	}

	static int intParam(JsonNode params, String name, int fallback, int min, int max) {
		int v = params != null && params.path(name).isNumber() ? params.get(name).asInt() : fallback;
		return Math.max(min, Math.min(max, v));
	}

	static void checkInt(JsonNode params, String name, int min, int max, List<String> problems) {
		if (params != null && params.has(name)) {
			JsonNode v = params.get(name);
			if (!v.isInt() || v.asInt() < min || v.asInt() > max) {
				problems.add(name + " must be an integer " + min + ".." + max);
			}
		}
	}
}
