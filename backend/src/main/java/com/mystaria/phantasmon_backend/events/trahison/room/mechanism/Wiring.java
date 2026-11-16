package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.events.trahison.room.Give;
import com.mystaria.phantasmon_backend.events.trahison.room.Step;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A panel of terminals to join with wires. The left terminals are numbered 1..n from the top; each right terminal has a
 * colour (the three colours of the game, with their symbols: red triangle, green disc, blue square, so colour is never
 * the only signal). The seed draws the <em>scheme</em>: for every left terminal, the colour of the right terminal it must
 * be wired to. The scheme is produced as a {@link ClueType#COLORS} clue, role {@value #SCHEME_ROLE}, that the room shows
 * somewhere else (a wall panel, a document...): the n-th colour of the clue is for left terminal n. The panel never says
 * it, so the clue is needed (decisions.md, D37).
 *
 * <h2>Parameters</h2>
 * <ul>
 * <li>{@code wires} 3..6 (default 4): number of terminals on each side;</li>
 * <li>{@code colors} 2..3 (default 3): how many of the colours the scheme uses (each used at least once). With more wires
 * than colours several right terminals share a colour, and any of them will do for a left terminal of that colour.</li>
 * </ul>
 *
 * <h2>Gestures</h2>
 * {@code {"op": "connect", "left": i, "right": j}} wires left terminal i to right terminal j (a terminal holds one wire:
 * the wires it already had are removed); {@code {"op": "disconnect", "left": i}} or {@code {"op": "disconnect", "right":
 * j}} removes a wire. When all the wires are placed the panel checks them: every left terminal wired to a right terminal
 * of the colour the scheme gives it solves the step; any other full set counts one short circuit ({@code wrong} in the
 * view) and stays as it is, to be corrected.
 *
 * <h2>Generation</h2>
 * The scheme is drawn from the seed, the right colours are a shuffle of the same colours, so a complete matching always
 * exists (the tests solve 1,000 draws from the clue alone through the engine). The panel starts with no wire, so never
 * solved. The view never holds the scheme, except for the game master.
 */
@Component
public class Wiring implements Mechanism {

	public static final String ID = "wiring";
	public static final String SCHEME_ROLE = "scheme";
	public static final String PANEL_ROLE = "panel";
	/** The colour names, as the client's {@code GameColor} knows them. */
	public static final List<String> COLORS = List.of("red", "green", "blue");

	static final int MIN_WIRES = 3;
	static final int MAX_WIRES = 6;
	static final int DEFAULT_WIRES = 4;
	static final int MIN_COLORS = 2;
	static final int MAX_COLORS = 3;
	static final int DEFAULT_COLORS = 3;

	record Config(int wires, int colors) {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int difficulty(JsonNode params) {
		Config c = config(params);
		return c.wires() <= 3 ? 1 : c.wires() <= 5 ? 2 : 3;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		Config c = config(params);
		return 40 + 25 * c.wires() + (c.colors() == 2 ? 0 : 10);
	}

	@Override
	public List<String> clueRoles(JsonNode params) {
		return List.of(SCHEME_ROLE);
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(PANEL_ROLE, "terminal", "pedestal"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(
				new MechanismParam("wires", "int", MIN_WIRES, MAX_WIRES, String.valueOf(DEFAULT_WIRES), "Terminals on each side of the panel"),
				new MechanismParam("colors", "int", MIN_COLORS, MAX_COLORS, String.valueOf(DEFAULT_COLORS),
						"Colours the scheme uses (red, green, blue); the scheme is a colours clue (role scheme) to show elsewhere"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		check(params, "wires", MIN_WIRES, MAX_WIRES, problems);
		check(params, "colors", MIN_COLORS, MAX_COLORS, problems);
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

	/** The scheme clue must be shown from the moment the step opens, and a slot must be bound to play on. */
	@Override
	public List<String> validateStep(Step step) {
		List<String> problems = new ArrayList<>();
		if (step.gives().stream().noneMatch(g -> SCHEME_ROLE.equals(g.clue()) && Give.OPEN.equals(g.when()))) {
			problems.add("missing=" + SCHEME_ROLE + ";when=open");
		}
		if (step.slots().isEmpty()) {
			problems.add("missing=slot:" + PANEL_ROLE);
		}
		return problems;
	}

	static Config config(JsonNode params) {
		int wires = clamp(intParam(params, "wires", DEFAULT_WIRES), MIN_WIRES, MAX_WIRES);
		int colors = clamp(intParam(params, "colors", DEFAULT_COLORS), MIN_COLORS, MAX_COLORS);
		return new Config(wires, colors);
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
		List<String> palette = new ArrayList<>(COLORS);
		shuffle(palette, rng);
		palette = palette.subList(0, c.colors());
		// Every colour of the palette at least once, the rest at random, then in any order.
		List<String> scheme = new ArrayList<>(palette);
		while (scheme.size() < c.wires()) {
			scheme.add(palette.get(rng.nextInt(palette.size())));
		}
		shuffle(scheme, rng);
		List<String> right = new ArrayList<>(scheme);
		shuffle(right, rng);
		ObjectNode data = Json.object();
		data.put("wires", c.wires());
		data.set("scheme", strings(scheme));
		data.set("right", strings(right));
		data.set("links", links(new int[c.wires()], true));
		data.put("wrong", 0);
		data.put("solved", false);
		return new MechanismState(data, List.of(new Clue(SCHEME_ROLE, ClueType.COLORS, strings(scheme))));
	}

	// ---------------------------------------------------------------- gestures

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		String op = input != null && input.path("op").isString() ? input.get("op").asString() : null;
		ObjectNode old = state.data();
		int n = old.path("wires").asInt();
		if ("connect".equals(op)) {
			int left = terminal(input, "left", n);
			int right = terminal(input, "right", n);
			if (old.path("solved").asBoolean(false)) {
				return InputResult.unchanged(state);
			}
			int[] links = intArray(old.get("links"));
			if (links[left] == right) {
				return InputResult.unchanged(state);
			}
			for (int i = 0; i < n; i++) {
				if (i == left || links[i] == right) {
					links[i] = -1;
				}
			}
			links[left] = right;
			return finish(state, links, event("connect", left, right));
		}
		if ("disconnect".equals(op)) {
			boolean byLeft = input.has("left");
			int index = terminal(input, byLeft ? "left" : "right", n);
			if (old.path("solved").asBoolean(false)) {
				return InputResult.unchanged(state);
			}
			int[] links = intArray(old.get("links"));
			int left = -1;
			for (int i = 0; i < n; i++) {
				if (byLeft ? i == index && links[i] >= 0 : links[i] == index) {
					left = i;
				}
			}
			if (left < 0) {
				return InputResult.unchanged(state);
			}
			int right = links[left];
			links[left] = -1;
			ObjectNode data = state.copyData();
			data.set("links", links(links, false));
			return new InputResult(state.withData(data), List.of(event("disconnect", left, right)), false);
		}
		throw new IllegalArgumentException("op: connect or disconnect");
	}

	private static int terminal(JsonNode input, String field, int n) {
		if (input == null || !input.path(field).isInt()) {
			throw new IllegalArgumentException(field + " is required");
		}
		int v = input.get(field).asInt();
		if (v < 0 || v >= n) {
			throw new IllegalArgumentException(field + " 0.." + (n - 1));
		}
		return v;
	}

	private static ObjectNode event(String type, int left, int right) {
		ObjectNode e = Json.object();
		e.put("type", type);
		e.put("left", left);
		e.put("right", right);
		return e;
	}

	/** Stores the new links and, when every wire is placed, checks them. */
	private InputResult finish(MechanismState state, int[] links, ObjectNode first) {
		ObjectNode old = state.data();
		ObjectNode data = state.copyData();
		data.set("links", links(links, false));
		List<ObjectNode> events = new ArrayList<>();
		events.add(first);
		boolean solved = false;
		boolean full = true;
		for (int l : links) {
			full &= l >= 0;
		}
		if (full) {
			solved = matches(old.get("scheme"), old.get("right"), links);
			if (solved) {
				data.put("solved", true);
				ObjectNode unlock = Json.object();
				unlock.put("type", "unlock");
				events.add(unlock);
			} else {
				data.put("wrong", old.path("wrong").asInt() + 1);
				ObjectNode wrong = Json.object();
				wrong.put("type", "wrong");
				events.add(wrong);
			}
		}
		return new InputResult(state.withData(data), events, solved);
	}

	/** Every left terminal wired to a right terminal of the colour the scheme gives it. */
	static boolean matches(JsonNode scheme, JsonNode right, int[] links) {
		for (int i = 0; i < links.length; i++) {
			if (links[i] < 0 || !scheme.get(i).asString().equals(right.get(links[i]).asString())) {
				return false;
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- views

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		ObjectNode view = Json.object();
		view.put("wires", d.path("wires").asInt());
		view.set("right", d.get("right").deepCopy());
		view.set("links", d.get("links").deepCopy());
		view.put("wrong", d.path("wrong").asInt());
		view.put("solved", d.path("solved").asBoolean(false));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.set("scheme", d.get("scheme").deepCopy());
		}
		return view;
	}

	// ---------------------------------------------------------------- JSON helpers

	private static ArrayNode strings(List<String> values) {
		ArrayNode array = Json.array();
		values.forEach(array::add);
		return array;
	}

	private static ArrayNode links(int[] values, boolean empty) {
		ArrayNode array = Json.array();
		for (int v : values) {
			array.add(empty ? -1 : v);
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

	private static void shuffle(List<?> list, RandomGenerator rng) {
		for (int i = list.size() - 1; i > 0; i--) {
			Collections.swap(list, i, rng.nextInt(i + 1));
		}
	}
}
