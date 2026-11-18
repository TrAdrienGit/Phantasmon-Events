package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A square board of tiles with pipe-like branches (straight, corner, tee, dead end, empty) that turn a quarter turn per
 * gesture {@code {"cell": r * size + c, "delta": +1 | -1}} (+1 clockwise). Current enters the board on the left edge, in
 * the source row, and the lamp sits on the right edge, in the sink row: the step is solved when the current, flowing from
 * tile to tile through matching branches, reaches the lamp tile and leaves it through its right side. Fixed tiles
 * (a rivet on screen) cannot be turned. The view says which cells are powered, which is what the players see light up.
 *
 * <h2>Parameters</h2>
 * <ul>
 * <li>{@code size} 3..5 (default 4);</li>
 * <li>{@code fixed} 0..6 (default 0): tiles of the hidden circuit that start in place and locked, at most the circuit length
 * minus one.</li>
 * </ul>
 *
 * <h2>Generation (solvable by construction)</h2>
 * A random self-avoiding path from the source cell to the sink cell is drawn; its tiles are straights and corners whose
 * rotation joins the two neighbours on the path, and every other cell gets a random decoy. The rotations of the path
 * are the solution (kept in the state, shown only to the game master); the other tiles are turned at random, redrawn
 * while the circuit already works, so it never starts solved. The tests rebuild the rules independently and play the
 * solution through {@code onInput}.
 */
@Component
public class RotatingTiles implements Mechanism {

	public static final String ID = "rotating_tiles";
	public static final String TERMINAL_ROLE = "terminal";

	static final int MIN_SIZE = 3;
	static final int MAX_SIZE = 5;
	static final int DEFAULT_SIZE = 4;
	static final int MIN_FIXED = 0;
	static final int MAX_FIXED = 6;
	static final int DEFAULT_FIXED = 0;

	/** Sides, clockwise from the top: bit {@code 1 << side}. */
	private static final int[] DR = { -1, 0, 1, 0 };
	private static final int[] DC = { 0, 1, 0, -1 };
	private static final int E = 1;
	private static final int W = 3;

	private static final String[] TYPES = { "straight", "corner", "tee", "end", "empty" };
	/** Branch mask of each type at rotation 0: straight N-S, corner N-E, tee N-E-S, end N. */
	private static final int[] BASE = { 0b0101, 0b0011, 0b0111, 0b0001, 0 };

	record Config(int size, int fixed) {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int difficulty(JsonNode params) {
		Config c = config(params);
		int score = (c.size() - MIN_SIZE) * 2 - Math.min(2, c.fixed() / 2);
		return score <= 0 ? 1 : score <= 2 ? 2 : 3;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		Config c = config(params);
		return Math.max(40, 30 + 12 * c.size() * c.size() - 6 * c.fixed());
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(TERMINAL_ROLE, "terminal", "pedestal"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(
				new MechanismParam("size", "int", MIN_SIZE, MAX_SIZE, String.valueOf(DEFAULT_SIZE), "Side of the square board"),
				new MechanismParam("fixed", "int", MIN_FIXED, MAX_FIXED, String.valueOf(DEFAULT_FIXED),
						"Tiles of the circuit that start in place and locked (a free hint)"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		check(params, "size", MIN_SIZE, MAX_SIZE, problems);
		check(params, "fixed", MIN_FIXED, MAX_FIXED, problems);
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
		return new Config(clamp(intParam(params, "size", DEFAULT_SIZE), MIN_SIZE, MAX_SIZE),
				clamp(intParam(params, "fixed", DEFAULT_FIXED), MIN_FIXED, MAX_FIXED));
	}

	private static int intParam(JsonNode params, String name, int fallback) {
		return params != null && params.path(name).isNumber() ? params.get(name).asInt() : fallback;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	// ---------------------------------------------------------------- rules

	/** Branch mask of a tile of type index {@code type} turned {@code rot} quarter turns clockwise. */
	static int mask(int type, int rot) {
		int m = BASE[type];
		int r = Math.floorMod(rot, 4);
		return ((m << r) | (m >> (4 - r))) & 0b1111;
	}

	/** The powered cells (row-major flags) and whether the lamp is lit, from the types and rotations of every cell. */
	static boolean[] power(int size, int[] types, int[] rots, int source, int sink, boolean[] lit) {
		boolean[] powered = new boolean[size * size];
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		int first = source * size;
		if ((mask(types[first], rots[first]) & (1 << W)) != 0) {
			powered[first] = true;
			queue.add(first);
		}
		while (!queue.isEmpty()) {
			int cell = queue.poll();
			int r = cell / size;
			int c = cell % size;
			int m = mask(types[cell], rots[cell]);
			for (int side = 0; side < 4; side++) {
				if ((m & (1 << side)) == 0) {
					continue;
				}
				int nr = r + DR[side];
				int nc = c + DC[side];
				if (nr < 0 || nc < 0 || nr >= size || nc >= size) {
					continue;
				}
				int next = nr * size + nc;
				if (!powered[next] && (mask(types[next], rots[next]) & (1 << ((side + 2) % 4))) != 0) {
					powered[next] = true;
					queue.add(next);
				}
			}
		}
		int lamp = sink * size + size - 1;
		lit[0] = powered[lamp] && (mask(types[lamp], rots[lamp]) & (1 << E)) != 0;
		return powered;
	}

	// ---------------------------------------------------------------- generation

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		Config c = config(params);
		int n = c.size();
		int source = rng.nextInt(n);
		int sink = rng.nextInt(n);
		List<Integer> path = drawPath(rng, n, source, sink);
		int[] types = new int[n * n];
		int[] solution = new int[n * n];
		boolean[] onPath = new boolean[n * n];
		for (int i = 0; i < path.size(); i++) {
			int cell = path.get(i);
			// The branches of this tile: the side facing the previous cell (the left edge for the first), and the side it leaves by.
			int in = i == 0 ? W : (sideBetween(path.get(i - 1), cell, n) + 2) % 4;
			int out = i == path.size() - 1 ? E : sideBetween(cell, path.get(i + 1), n);
			int wanted = (1 << in) | (1 << out);
			onPath[cell] = true;
			types[cell] = wanted == 0b0101 || wanted == 0b1010 ? 0 : 1;
			for (int r = 0; r < 4; r++) {
				if (mask(types[cell], r) == wanted) {
					solution[cell] = r;
					break;
				}
			}
		}
		for (int cell = 0; cell < n * n; cell++) {
			if (!onPath[cell]) {
				int roll = rng.nextInt(10);
				types[cell] = roll < 3 ? 0 : roll < 6 ? 1 : roll < 8 ? 2 : roll < 9 ? 3 : 4;
			}
		}
		// Locked hints: tiles of the circuit that start in place.
		boolean[] fixed = new boolean[n * n];
		List<Integer> hints = new ArrayList<>(path);
		Collections.shuffle(hints, new java.util.Random(rng.nextLong()));
		for (int i = 0; i < Math.min(c.fixed(), path.size() - 1); i++) {
			fixed[hints.get(i)] = true;
		}
		int[] rots = new int[n * n];
		boolean[] lit = new boolean[1];
		for (int attempt = 0; attempt < 1000; attempt++) {
			for (int cell = 0; cell < n * n; cell++) {
				rots[cell] = fixed[cell] ? solution[cell] : rng.nextInt(4);
			}
			power(n, types, rots, source, sink, lit);
			if (!lit[0]) {
				break;
			}
		}
		if (lit[0]) { // practically unreachable: break the circuit on a free tile of the path
			for (int cell : path) {
				if (!fixed[cell]) {
					rots[cell] = (solution[cell] + 1) % 4;
					power(n, types, rots, source, sink, lit);
					if (!lit[0]) {
						break;
					}
				}
			}
		}
		ObjectNode data = Json.object();
		data.put("size", n);
		data.put("source", source);
		data.put("sink", sink);
		data.set("types", typesJson(types));
		data.set("rots", ints(rots));
		data.set("fixed", bools(fixed));
		data.set("solution", ints(solution));
		data.put("solved", false);
		return new MechanismState(data, List.of());
	}

	private static int sideBetween(int from, int to, int n) {
		int dr = to / n - from / n;
		int dc = to % n - from % n;
		for (int side = 0; side < 4; side++) {
			if (DR[side] == dr && DC[side] == dc) {
				return side;
			}
		}
		throw new IllegalStateException("cells are not neighbours");
	}

	/** A random self-avoiding path from (source, 0) to (sink, n - 1), by depth-first search in random order. */
	private static List<Integer> drawPath(RandomGenerator rng, int n, int source, int sink) {
		List<Integer> path = new ArrayList<>();
		boolean[] seen = new boolean[n * n];
		int start = source * n;
		int goal = sink * n + n - 1;
		walk(rng, n, start, goal, seen, path);
		return path;
	}

	private static boolean walk(RandomGenerator rng, int n, int cell, int goal, boolean[] seen, List<Integer> path) {
		seen[cell] = true;
		path.add(cell);
		if (cell == goal) {
			return true;
		}
		List<Integer> sides = new ArrayList<>(List.of(0, 1, 2, 3));
		for (int i = 3; i > 0; i--) {
			Collections.swap(sides, i, rng.nextInt(i + 1));
		}
		for (int side : sides) {
			int nr = cell / n + DR[side];
			int nc = cell % n + DC[side];
			if (nr >= 0 && nc >= 0 && nr < n && nc < n && !seen[nr * n + nc] && walk(rng, n, nr * n + nc, goal, seen, path)) {
				return true;
			}
		}
		path.remove(path.size() - 1);
		return false;
	}

	// ---------------------------------------------------------------- gestures

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		if (input == null || !input.path("cell").isInt() || !input.path("delta").isInt()) {
			throw new IllegalArgumentException("cell and delta are required");
		}
		ObjectNode old = state.data();
		int n = old.get("size").asInt();
		int cell = input.get("cell").asInt();
		int delta = input.get("delta").asInt();
		if (cell < 0 || cell >= n * n || (delta != 1 && delta != -1)) {
			throw new IllegalArgumentException("cell 0.." + (n * n - 1) + ", delta -1 or 1");
		}
		if (old.get("fixed").get(cell).asBoolean()) {
			throw new IllegalArgumentException("the tile is fixed");
		}
		if (old.path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		int[] rots = intArray(old.get("rots"));
		rots[cell] = Math.floorMod(rots[cell] + delta, 4);
		boolean[] lit = new boolean[1];
		power(n, typeIndexes(old), rots, old.get("source").asInt(), old.get("sink").asInt(), lit);
		ObjectNode data = state.copyData();
		data.set("rots", ints(rots));
		data.put("solved", lit[0]);
		ObjectNode event = Json.object();
		event.put("type", "turn");
		event.put("cell", cell);
		event.put("delta", delta);
		return new InputResult(state.withData(data), List.of(event), lit[0]);
	}

	// ---------------------------------------------------------------- view

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		int n = d.get("size").asInt();
		boolean[] lit = new boolean[1];
		boolean[] powered = power(n, typeIndexes(d), intArray(d.get("rots")), d.get("source").asInt(), d.get("sink").asInt(), lit);
		ObjectNode view = Json.object();
		view.set("size", d.get("size").deepCopy());
		view.set("source", d.get("source").deepCopy());
		view.set("sink", d.get("sink").deepCopy());
		view.set("types", d.get("types").deepCopy());
		view.set("rots", d.get("rots").deepCopy());
		view.set("fixed", d.get("fixed").deepCopy());
		view.set("powered", bools(powered));
		view.put("lit", lit[0]);
		view.put("solved", d.path("solved").asBoolean(false));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.set("solution", d.get("solution").deepCopy());
		}
		return view;
	}

	private static int[] typeIndexes(ObjectNode data) {
		int[] out = new int[data.get("types").size()];
		for (int i = 0; i < out.length; i++) {
			String name = data.get("types").get(i).asString();
			for (int t = 0; t < TYPES.length; t++) {
				if (TYPES[t].equals(name)) {
					out[i] = t;
				}
			}
		}
		return out;
	}

	private static ArrayNode typesJson(int[] types) {
		ArrayNode array = Json.array();
		for (int t : types) {
			array.add(TYPES[t]);
		}
		return array;
	}

	private static ArrayNode ints(int[] values) {
		ArrayNode array = Json.array();
		for (int v : values) {
			array.add(v);
		}
		return array;
	}

	private static ArrayNode bools(boolean[] values) {
		ArrayNode array = Json.array();
		for (boolean v : values) {
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
