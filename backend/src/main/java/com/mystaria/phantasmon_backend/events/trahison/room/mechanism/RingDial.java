package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Concentric rings of symbols to turn until the marked radius (the top of the screen) reads a target sequence, one
 * symbol per ring, outermost ring first. Turning a ring may also turn other rings: a <em>link</em> {@code a -> b, k}
 * makes ring {@code b} turn by {@code k} steps (signed) every time ring {@code a} turns by one. Links are not chained:
 * a ring turned because of a link does not turn the rings it drives itself. By default the links are hidden from the
 * players, who discover them by turning.
 *
 * <h2>Parameters</h2>
 * <ul>
 * <li>{@code rings} 3..5 (default 3): number of rings;</li>
 * <li>{@code symbols} 6..12 (default 8): symbols on the outermost ring; each inner ring has one fewer, never under 6,
 * so the glyphs of the small rings stay readable;</li>
 * <li>{@code links} 0..3 (default 2): number of links, at most {@code rings * (rings - 1)};</li>
 * <li>{@code showLinks} (default false): whether the view tells the links.</li>
 * </ul>
 *
 * <h2>Model</h2>
 * Ring {@code i} carries the distinct glyph ids {@code symbols[i][0..n-1]} (n symbols) in clockwise order. Its offset
 * {@code o} counts the clockwise steps it has turned from the position where slot 0 is at the top, so the symbol at the
 * top is {@code symbols[i][(-o) mod n]}. A gesture {@code {"ring": i, "delta": +1 | -1}} turns ring i one step
 * clockwise (+1) or counter-clockwise (-1), then each linked ring by {@code delta * k}.
 *
 * <h2>Generation (solvable by construction)</h2>
 * Every move has an inverse (the same gesture with the opposite delta), so any state reached from the solved state by
 * legal moves can be solved by playing the scramble backwards. {@link #generate} therefore draws the rings, the links
 * and the target, puts the rings on the target, then plays random legal moves until at least two rings (fewer when
 * there are fewer) are off the target. It never starts solved. The tests also check solvability with a breadth-first
 * search over the whole state space (at most 12^5 states).
 *
 * <p>
 * The target is also produced as a {@link ClueType#SYMBOLS} clue, role {@value #SYMBOLS_ROLE}: an array of glyph ids,
 * outermost ring first, that the room shows somewhere else. The view never holds the target, except for the game
 * master.
 */
@Component
public class RingDial implements Mechanism {

	public static final String ID = "ring_dial";
	public static final String SYMBOLS_ROLE = "symbols";
	public static final String TERMINAL_ROLE = "terminal";

	static final int MIN_RINGS = 3;
	static final int MAX_RINGS = 5;
	static final int DEFAULT_RINGS = 3;
	static final int MIN_SYMBOLS = 6;
	static final int MAX_SYMBOLS = 12;
	static final int DEFAULT_SYMBOLS = 8;
	static final int MIN_LINKS = 0;
	static final int MAX_LINKS = 3;
	static final int DEFAULT_LINKS = 2;
	private static final int[] LINK_STEPS = { -2, -1, 1, 2 };

	/** The validated parameters of one step. */
	record Config(int rings, int symbols, int links, boolean showLinks) {

		/** Number of symbols on ring {@code ring}: one fewer per ring going inwards, never under the minimum. */
		int sizeOf(int ring) {
			return Math.max(MIN_SYMBOLS, symbols - ring);
		}
	}

	/** One link: turning {@code from} by one step turns {@code to} by {@code steps} (signed). */
	record Link(int from, int to, int steps) {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int difficulty(JsonNode params) {
		Config c = config(params);
		int score = (c.rings() - MIN_RINGS) + c.links() + (c.symbols() >= 10 ? 1 : 0);
		return score <= 1 ? 1 : score <= 3 ? 2 : 3;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		Config c = config(params);
		return 60 + 25 * c.rings() + 45 * c.links() + 3 * c.symbols();
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(TERMINAL_ROLE, "terminal", "pedestal"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(
				new MechanismParam("rings", "int", MIN_RINGS, MAX_RINGS, String.valueOf(DEFAULT_RINGS),
						"Number of concentric rings; the target has one symbol per ring"),
				new MechanismParam("symbols", "int", MIN_SYMBOLS, MAX_SYMBOLS, String.valueOf(DEFAULT_SYMBOLS),
						"Symbols on the outermost ring; each inner ring has one fewer (never under 6)"),
				new MechanismParam("links", "int", MIN_LINKS, MAX_LINKS, String.valueOf(DEFAULT_LINKS),
						"Links: turning a ring also turns another one; players discover them by turning"),
				new MechanismParam("showLinks", "bool", 0, 0, "false", "Tell the players the links instead of letting them find out"));
	}

	@Override
	public List<String> clueRoles(JsonNode params) {
		return List.of(SYMBOLS_ROLE);
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		check(params, "rings", MIN_RINGS, MAX_RINGS, problems);
		check(params, "symbols", MIN_SYMBOLS, MAX_SYMBOLS, problems);
		check(params, "links", MIN_LINKS, MAX_LINKS, problems);
		if (params != null && params.has("showLinks") && !params.get("showLinks").isBoolean()) {
			problems.add("showLinks must be true or false");
		}
		if (problems.isEmpty()) {
			Config c = config(params);
			if (c.links() > c.rings() * (c.rings() - 1)) {
				problems.add("links: at most rings * (rings - 1)");
			}
		}
		return problems;
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

	/** Reads the parameters with their defaults; out-of-range values are clamped (validateParams reports them). */
	static Config config(JsonNode params) {
		int rings = clamp(intParam(params, "rings", DEFAULT_RINGS), MIN_RINGS, MAX_RINGS);
		int symbols = clamp(intParam(params, "symbols", DEFAULT_SYMBOLS), MIN_SYMBOLS, MAX_SYMBOLS);
		int links = clamp(intParam(params, "links", DEFAULT_LINKS), MIN_LINKS, Math.min(MAX_LINKS, rings * (rings - 1)));
		boolean show = params != null && params.path("showLinks").asBoolean(false);
		return new Config(rings, symbols, links, show);
	}

	private static int intParam(JsonNode params, String name, int fallback) {
		return params != null && params.path(name).isNumber() ? params.get(name).asInt() : fallback;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	// ---------------------------------------------------------------- generation

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		Config c = config(params);
		// 1. The glyphs of each ring: distinct inside a ring, drawn from the shared set.
		List<List<String>> symbols = new ArrayList<>();
		for (int r = 0; r < c.rings(); r++) {
			List<String> pool = new ArrayList<>(SymbolSet.IDS);
			shuffle(pool, rng);
			symbols.add(List.copyOf(pool.subList(0, c.sizeOf(r))));
		}
		// 2. The links: distinct ordered pairs of different rings, a signed step each.
		List<Link> links = drawLinks(rng, c);
		// 3. The target: one slot per ring at the top. Rings start on it, then are scrambled with legal moves.
		int[] solved = new int[c.rings()];
		List<String> target = new ArrayList<>();
		for (int r = 0; r < c.rings(); r++) {
			int n = c.sizeOf(r);
			int slot = rng.nextInt(n);
			target.add(symbols.get(r).get(slot));
			solved[r] = Math.floorMod(-slot, n);
		}
		int[] offsets = scramble(rng, c, links, solved);

		ObjectNode data = Json.object();
		data.set("symbols", nested(symbols));
		data.set("links", linksJson(links));
		data.set("target", strings(target));
		data.set("solvedOffsets", ints(solved));
		data.set("offsets", ints(offsets));
		data.put("showLinks", c.showLinks());
		data.put("solved", false);
		ArrayNode clue = strings(target);
		return new MechanismState(data, List.of(new Clue(SYMBOLS_ROLE, ClueType.SYMBOLS, clue)));
	}

	private static List<Link> drawLinks(RandomGenerator rng, Config c) {
		List<int[]> pairs = new ArrayList<>();
		for (int a = 0; a < c.rings(); a++) {
			for (int b = 0; b < c.rings(); b++) {
				if (a != b) {
					pairs.add(new int[] { a, b });
				}
			}
		}
		shuffle(pairs, rng);
		List<Link> links = new ArrayList<>();
		for (int i = 0; i < c.links(); i++) {
			int[] p = pairs.get(i);
			// Mostly one step, sometimes two; direction at random.
			int steps = LINK_STEPS[rng.nextInt(4)];
			if (Math.abs(steps) == 2 && rng.nextInt(3) != 0) {
				steps = steps / 2;
			}
			links.add(new Link(p[0], p[1], steps));
		}
		return links;
	}

	/**
	 * Plays random legal moves from the solved offsets, never straight back on the previous move, until enough rings
	 * are off target. After a few failed attempts it forces ring 0 away, which is always possible, so the result is
	 * never solved.
	 */
	private static int[] scramble(RandomGenerator rng, Config c, List<Link> links, int[] solved) {
		int wanted = Math.min(2, c.rings());
		for (int attempt = 0; attempt < 20; attempt++) {
			int[] offsets = solved.clone();
			int moves = 10 + 3 * c.rings() + rng.nextInt(10);
			int lastRing = -1;
			int lastDelta = 0;
			for (int m = 0; m < moves; m++) {
				int ring = rng.nextInt(c.rings());
				int delta = rng.nextBoolean() ? 1 : -1;
				if (ring == lastRing && delta == -lastDelta) {
					delta = -delta;
				}
				move(offsets, sizes(c), links, ring, delta);
				lastRing = ring;
				lastDelta = delta;
			}
			if (misaligned(offsets, solved) >= wanted) {
				return offsets;
			}
		}
		int[] offsets = solved.clone();
		move(offsets, sizes(c), links, 0, 1);
		return offsets;
	}

	private static int[] sizes(Config c) {
		int[] sizes = new int[c.rings()];
		for (int i = 0; i < sizes.length; i++) {
			sizes[i] = c.sizeOf(i);
		}
		return sizes;
	}

	private static int misaligned(int[] offsets, int[] solved) {
		int count = 0;
		for (int i = 0; i < offsets.length; i++) {
			if (offsets[i] != solved[i]) {
				count++;
			}
		}
		return count;
	}

	/** The one rule of the puzzle: turn {@code ring} by {@code delta}, and every ring it drives by {@code delta * k}. */
	static void move(int[] offsets, int[] sizes, List<Link> links, int ring, int delta) {
		offsets[ring] = Math.floorMod(offsets[ring] + delta, sizes[ring]);
		for (Link link : links) {
			if (link.from() == ring) {
				offsets[link.to()] = Math.floorMod(offsets[link.to()] + delta * link.steps(), sizes[link.to()]);
			}
		}
	}

	// ---------------------------------------------------------------- gestures

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		if (input == null || !input.path("ring").isInt() || !input.path("delta").isInt()) {
			throw new IllegalArgumentException("ring and delta are required");
		}
		ObjectNode old = state.data();
		int rings = old.get("offsets").size();
		int ring = input.get("ring").asInt();
		int delta = input.get("delta").asInt();
		if (ring < 0 || ring >= rings || (delta != 1 && delta != -1)) {
			throw new IllegalArgumentException("ring 0.." + (rings - 1) + ", delta -1 or 1");
		}
		if (old.path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		int[] sizes = new int[rings];
		for (int i = 0; i < rings; i++) {
			sizes[i] = old.get("symbols").get(i).size();
		}
		int[] offsets = intArray(old.get("offsets"));
		move(offsets, sizes, readLinks(old), ring, delta);
		boolean solved = java.util.Arrays.equals(offsets, intArray(old.get("solvedOffsets")));
		ObjectNode data = state.copyData();
		data.set("offsets", ints(offsets));
		data.put("solved", solved);
		ObjectNode event = Json.object();
		event.put("type", "turn");
		event.put("ring", ring);
		event.put("delta", delta);
		return new InputResult(state.withData(data), List.of(event), solved);
	}

	// ---------------------------------------------------------------- views

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		ObjectNode view = Json.object();
		view.set("symbols", d.get("symbols").deepCopy());
		view.set("offsets", d.get("offsets").deepCopy());
		boolean show = d.path("showLinks").asBoolean(false);
		view.put("showLinks", show);
		if (show) {
			view.set("links", d.get("links").deepCopy());
		}
		view.put("solved", d.path("solved").asBoolean(false));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.set("target", d.get("target").deepCopy());
			view.set("links", d.get("links").deepCopy());
		}
		return view;
	}

	// ---------------------------------------------------------------- JSON helpers

	private static List<Link> readLinks(ObjectNode data) {
		List<Link> links = new ArrayList<>();
		for (JsonNode l : data.get("links")) {
			links.add(new Link(l.get("from").asInt(), l.get("to").asInt(), l.get("steps").asInt()));
		}
		return links;
	}

	private static ArrayNode linksJson(List<Link> links) {
		ArrayNode array = Json.array();
		for (Link l : links) {
			ObjectNode o = Json.object();
			o.put("from", l.from());
			o.put("to", l.to());
			o.put("steps", l.steps());
			array.add(o);
		}
		return array;
	}

	private static ArrayNode nested(List<List<String>> lists) {
		ArrayNode array = Json.array();
		for (List<String> l : lists) {
			array.add(strings(l));
		}
		return array;
	}

	private static ArrayNode strings(List<String> values) {
		ArrayNode array = Json.array();
		values.forEach(array::add);
		return array;
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

	/** Fisher-Yates with the mechanism's generator, so a seed always gives the same order. */
	private static void shuffle(List<?> list, RandomGenerator rng) {
		for (int i = list.size() - 1; i > 0; i--) {
			Collections.swap(list, i, rng.nextInt(i + 1));
		}
	}
}
