package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A safe with a circular dial of {@code positions} stops and a combination of {@code numbers} stops to find in order,
 * alternating direction (clockwise for the first, then counter-clockwise, and so on). It is meant to be opened by
 * sound: every tick the dial turns, the backend says how close the dial is to the number it is looking for, as a
 * <em>cue</em>, and the client plays it with vanilla sounds (a click that rises, a bell near the number, a clunk on it).
 *
 * <p>
 * Gestures: {@code {"delta": +1 | -1}} turns the dial one stop (+1 clockwise); {@code {"lock": true}} tries the current
 * number: right when the dial is on it and the last tick turned the way this number asks for, in which case the next
 * number is looked for (the dial must be turned again first); otherwise the safe forgets the numbers found and starts over.
 *
 * <h2>The secret and the cue</h2>
 * The combination never leaves the backend (the game master's view aside). What the players get is the rule's feedback:
 * the cue {@code hit} (the dial is on the number looked for), {@code near} (within 2 stops), {@code mid} (within
 * {@code max(4, positions / 6)} stops, only when {@code wide}) or {@code far}, and the count of numbers found. The
 * exact-position cue is what makes the safe solvable: a sweep of one turn finds each number.
 *
 * <h2>Parameters</h2>
 * <ul>
 * <li>{@code positions} 24..60 (default 40);</li>
 * <li>{@code numbers} 3..5 (default 3);</li>
 * <li>{@code wide} (default true): whether the {@code mid} cue exists; without it only {@code near} and {@code hit} speak.</li>
 * </ul>
 *
 * <h2>Generation</h2>
 * Numbers are drawn at least 3 stops apart from the previous one (and from the dial's start for the first), so the cues
 * of two numbers never overlap. Solvable by construction: the dial is circular, so every stop can be reached turning
 * either way; the tests play a solver that uses only the cues.
 */
@Component
public class SafeDial implements Mechanism {

	public static final String ID = "safe_dial";
	public static final String TERMINAL_ROLE = "terminal";

	static final int MIN_POSITIONS = 24;
	static final int MAX_POSITIONS = 60;
	static final int DEFAULT_POSITIONS = 40;
	static final int MIN_NUMBERS = 3;
	static final int MAX_NUMBERS = 5;
	static final int DEFAULT_NUMBERS = 3;
	private static final int NEAR = 2;
	private static final int MIN_GAP = 3;

	record Config(int positions, int numbers, boolean wide) {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int difficulty(JsonNode params) {
		Config c = config(params);
		int score = (c.numbers() - MIN_NUMBERS) + (c.positions() >= 48 ? 1 : 0) + (c.wide() ? 0 : 1);
		return score <= 0 ? 1 : score <= 2 ? 2 : 3;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		Config c = config(params);
		return 40 + c.numbers() * (20 + c.positions() / 2) + (c.wide() ? 0 : 30);
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(TERMINAL_ROLE, "terminal", "pedestal"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(
				new MechanismParam("positions", "int", MIN_POSITIONS, MAX_POSITIONS, String.valueOf(DEFAULT_POSITIONS),
						"Stops on the dial"),
				new MechanismParam("numbers", "int", MIN_NUMBERS, MAX_NUMBERS, String.valueOf(DEFAULT_NUMBERS),
						"Numbers of the combination"),
				new MechanismParam("wide", "bool", 0, 0, "true", "Tell the players when the dial is within a few stops (the mid cue)"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		check(params, "positions", MIN_POSITIONS, MAX_POSITIONS, problems);
		check(params, "numbers", MIN_NUMBERS, MAX_NUMBERS, problems);
		if (params != null && params.has("wide") && !params.get("wide").isBoolean()) {
			problems.add("wide must be true or false");
		}
		return problems;
	}

	@Override
	public List<String> validateStep(com.mystaria.phantasmon_backend.events.trahison.room.Step step) {
		return step.slots().isEmpty() ? List.of("missing=slot:" + TERMINAL_ROLE) : List.of();
	}

	private static void check(JsonNode params, String name, int min, int max, List<String> problems) {
		if (params == null || !params.has(name)) {
			return;
		}
		JsonNode v = params.get(name);
		if (!v.isInt() || v.asInt() < min || v.asInt() > max) {
			problems.add(name + " must be an integer " + min + ".." + max);
		}
	}

	static Config config(JsonNode params) {
		return new Config(clamp(intParam(params, "positions", DEFAULT_POSITIONS), MIN_POSITIONS, MAX_POSITIONS),
				clamp(intParam(params, "numbers", DEFAULT_NUMBERS), MIN_NUMBERS, MAX_NUMBERS),
				params == null || params.path("wide").asBoolean(true));
	}

	private static int intParam(JsonNode params, String name, int fallback) {
		return params != null && params.path(name).isNumber() ? params.get(name).asInt() : fallback;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	// ---------------------------------------------------------------- rules

	static int distance(int positions, int a, int b) {
		int d = Math.floorMod(a - b, positions);
		return Math.min(d, positions - d);
	}

	/** The way the number of stage {@code stage} must be reached: +1 for the first, then alternating. */
	static int dirOf(int stage) {
		return stage % 2 == 0 ? 1 : -1;
	}

	public static String cue(int positions, boolean wide, int position, int target) {
		int d = distance(positions, position, target);
		if (d == 0) {
			return "hit";
		}
		if (d <= NEAR) {
			return "near";
		}
		return wide && d <= Math.max(4, positions / 6) ? "mid" : "far";
	}

	// ---------------------------------------------------------------- generation

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		Config c = config(params);
		int start = rng.nextInt(c.positions());
		int[] combination = new int[c.numbers()];
		int previous = start;
		for (int i = 0; i < combination.length; i++) {
			int next;
			do {
				next = rng.nextInt(c.positions());
			} while (distance(c.positions(), next, previous) < MIN_GAP);
			combination[i] = next;
			previous = next;
		}
		ObjectNode data = Json.object();
		data.put("positions", c.positions());
		data.put("wide", c.wide());
		data.set("combination", ints(combination));
		data.put("position", start);
		data.put("stage", 0);
		data.put("lastDir", 0);
		data.put("resets", 0);
		data.put("solved", false);
		return new MechanismState(data, List.of());
	}

	// ---------------------------------------------------------------- gestures

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		boolean turn = input != null && input.path("delta").isInt();
		boolean lock = input != null && input.path("lock").isBoolean() && input.get("lock").asBoolean();
		if (turn == lock) {
			throw new IllegalArgumentException("delta (-1 or 1) or lock (true), one of them");
		}
		if (turn && Math.abs(input.get("delta").asInt()) != 1) {
			throw new IllegalArgumentException("delta -1 or 1");
		}
		ObjectNode old = state.data();
		if (old.path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		int positions = old.get("positions").asInt();
		boolean wide = old.get("wide").asBoolean();
		int[] combination = intArray(old.get("combination"));
		int position = old.get("position").asInt();
		int stage = old.get("stage").asInt();
		ObjectNode data = state.copyData();
		ObjectNode event = Json.object();
		boolean solved = false;
		if (turn) {
			int delta = input.get("delta").asInt();
			position = Math.floorMod(position + delta, positions);
			data.put("position", position);
			data.put("lastDir", delta);
			event.put("type", "tick");
			event.put("delta", delta);
			event.put("cue", cue(positions, wide, position, combination[stage]));
		} else {
			boolean ok = position == combination[stage] && old.get("lastDir").asInt() == dirOf(stage);
			event.put("type", "lock");
			event.put("ok", ok);
			if (ok) {
				stage++;
				solved = stage == combination.length;
				data.put("stage", stage);
				data.put("lastDir", 0);
			} else {
				stage = 0;
				data.put("stage", 0);
				data.put("lastDir", 0);
				data.put("resets", old.get("resets").asInt() + 1);
			}
			event.put("stage", stage);
		}
		data.put("solved", solved);
		return new InputResult(state.withData(data), List.of(event), solved);
	}

	// ---------------------------------------------------------------- view

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		int[] combination = intArray(d.get("combination"));
		int stage = d.get("stage").asInt();
		boolean solved = d.path("solved").asBoolean(false);
		ObjectNode view = Json.object();
		view.set("positions", d.get("positions").deepCopy());
		view.set("position", d.get("position").deepCopy());
		view.put("numbers", combination.length);
		view.set("stage", d.get("stage").deepCopy());
		view.put("turn", dirOf(stage));
		view.put("cue", solved ? "hit" : cue(d.get("positions").asInt(), d.get("wide").asBoolean(), d.get("position").asInt(), combination[stage]));
		view.set("lastDir", d.get("lastDir").deepCopy());
		view.set("resets", d.get("resets").deepCopy());
		view.put("solved", solved);
		if (viewer == ViewerKind.GAME_MASTER) {
			view.set("combination", d.get("combination").deepCopy());
		}
		return view;
	}

	private static ArrayNode ints(int[] values) {
		ArrayNode array = Json.array();
		for (int v : values) {
			array.add(v);
		}
		return array;
	}

	private static int[] intArray(JsonNode array) {
		int[] out = new int[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).asInt();
		}
		return out;
	}
}
