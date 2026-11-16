package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** A button that solves its step with one press (any input, or using its slot). For tests and trials of the room frame. */
@Component
public class DebugButton implements Mechanism {

	public static final String ID = "debug_button";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public List<MechanismParam> params() {
		return List.of();
	}

	@Override
	public int difficulty(JsonNode params) {
		return 1;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		return 10;
	}

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		ObjectNode data = Json.object();
		data.put("pressed", false);
		return new MechanismState(data, List.of());
	}

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		return press(state);
	}

	@Override
	public InputResult onUse(MechanismState state, int player, String role) {
		return press(state);
	}

	private InputResult press(MechanismState state) {
		if (state.data().path("pressed").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		ObjectNode data = state.copyData();
		data.put("pressed", true);
		ObjectNode event = Json.object();
		event.put("type", "press");
		return new InputResult(state.withData(data), List.of(event), true);
	}

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode view = Json.object();
		view.put("pressed", state.data().path("pressed").asBoolean(false));
		return view;
	}
}
