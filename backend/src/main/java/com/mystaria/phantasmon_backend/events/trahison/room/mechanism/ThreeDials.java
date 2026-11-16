package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Three dials of 0 to 9 to align on a target drawn from the seed. The target is also produced as a {@code digits} clue
 * (role {@code code}) that the room shows somewhere else. Input {@code {"dial": 0..2, "delta": -9..9}} turns a dial
 * (wrapping). The view gives the dial positions only; the game master's view adds the target.
 */
@Component
public class ThreeDials implements Mechanism {

	public static final String ID = "three_dials";
	public static final String CODE = "code";
	static final int DIALS = 3;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int difficulty(JsonNode params) {
		return 1;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		return 60;
	}

	@Override
	public List<String> clueRoles(JsonNode params) {
		return List.of(CODE);
	}

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		int[] target = new int[DIALS];
		int[] dials = new int[DIALS];
		StringBuilder code = new StringBuilder();
		for (int i = 0; i < DIALS; i++) {
			target[i] = rng.nextInt(10);
			code.append(target[i]);
		}
		// Never start solved: the first dial begins on another digit.
		dials[0] = (target[0] + 1 + rng.nextInt(9)) % 10;
		for (int i = 1; i < DIALS; i++) {
			dials[i] = rng.nextInt(10);
		}
		ObjectNode data = Json.object();
		data.set("target", digits(target));
		data.set("dials", digits(dials));
		data.put("solved", false);
		return new MechanismState(data, List.of(Clue.digits(CODE, code.toString())));
	}

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		if (input == null || !input.path("dial").isNumber() || !input.path("delta").isNumber()) {
			throw new IllegalArgumentException("dial and delta are required");
		}
		int dial = input.path("dial").asInt();
		int delta = input.path("delta").asInt();
		if (dial < 0 || dial >= DIALS || delta < -9 || delta > 9) {
			throw new IllegalArgumentException("dial 0.." + (DIALS - 1) + ", delta -9..9");
		}
		if (state.data().path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		ObjectNode data = state.copyData();
		ArrayNode dials = (ArrayNode) data.get("dials");
		int value = Math.floorMod(dials.get(dial).asInt() + delta, 10);
		dials.set(dial, value);
		boolean solved = true;
		for (int i = 0; i < DIALS; i++) {
			solved &= dials.get(i).asInt() == data.get("target").get(i).asInt();
		}
		data.put("solved", solved);
		ObjectNode event = Json.object();
		event.put("type", "dial");
		event.put("dial", dial);
		event.put("value", value);
		return new InputResult(state.withData(data), List.of(event), solved);
	}

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode view = Json.object();
		view.set("dials", state.data().get("dials").deepCopy());
		view.put("solved", state.data().path("solved").asBoolean(false));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.set("target", state.data().get("target").deepCopy());
		}
		return view;
	}

	private static ArrayNode digits(int[] values) {
		ArrayNode array = Json.array();
		for (int v : values) {
			array.add(v);
		}
		return array;
	}
}
