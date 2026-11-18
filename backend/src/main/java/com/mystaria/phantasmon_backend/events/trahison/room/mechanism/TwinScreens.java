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
 * Two distant terminals that each show half of the problem, played by two different players. The steps binds the roles
 * {@code left} and {@code right} to two slots; the mechanism draws two sequences of glyphs and gives each as a clue, shown
 * where its terminal is ({@code left_code} on the left slot, {@code right_code} on the right slot). What a terminal
 * must be typed with is what the <em>other</em> one shows: the players have to read their screen to each other.
 *
 * <p>
 * Using a slot takes its seat ({@link #onUse}): a player cannot take a seat while holding the other one, and the last
 * player to use a free seat holds it. A gesture {@code {"seat": "left" | "right", "symbols": [id...]}} (a whole sequence
 * from the alphabet of the view) is accepted only from the player holding that seat, and only if that player did not
 * latch the other seat. A right sequence latches the seat; a wrong one only counts. The step is solved when both are latched.
 *
 * <h2>Secrets</h2>
 * The two sequences stay in the state and in the clues ({@code ClueType.SYMBOLS}, which the room shows only at the
 * terminals); the view carries the alphabet, the seats, the latches and the count of wrong tries, never a sequence (the
 * game master's view adds them).
 *
 * <h2>Parameters</h2>
 * <ul>
 * <li>{@code length} 3..6 (default 4): glyphs per sequence, distinct;</li>
 * <li>{@code alphabet} 6..16 (default 10): glyphs offered by the keyboard of the terminals.</li>
 * </ul>
 *
 * <h2>Generation</h2>
 * Both sequences are drawn from the alphabet, distinct from each other. Solvable by construction: the expected entries are
 * exactly the clues the room gives, and the alphabet holds every glyph of both. The tests play the whole protocol.
 */
@Component
public class TwinScreens implements Mechanism {

	public static final String ID = "twin_screens";
	public static final String LEFT = "left";
	public static final String RIGHT = "right";
	public static final String LEFT_CODE = "left_code";
	public static final String RIGHT_CODE = "right_code";

	static final int MIN_LENGTH = 3;
	static final int MAX_LENGTH = 6;
	static final int DEFAULT_LENGTH = 4;
	static final int MIN_ALPHABET = 6;
	static final int MAX_ALPHABET = 16;
	static final int DEFAULT_ALPHABET = 10;

	record Config(int length, int alphabet) {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int difficulty(JsonNode params) {
		Config c = config(params);
		int score = (c.length() - MIN_LENGTH) + (c.alphabet() >= 13 ? 1 : 0);
		return score <= 1 ? 1 : score <= 3 ? 2 : 3;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		Config c = config(params);
		return 60 + 18 * c.length() + 4 * c.alphabet();
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(LEFT, "terminal", "pedestal"), SlotRole.of(RIGHT, "terminal", "pedestal"));
	}

	@Override
	public List<String> clueRoles(JsonNode params) {
		return List.of(LEFT_CODE, RIGHT_CODE);
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(
				new MechanismParam("length", "int", MIN_LENGTH, MAX_LENGTH, String.valueOf(DEFAULT_LENGTH), "Glyphs per sequence"),
				new MechanismParam("alphabet", "int", MIN_ALPHABET, MAX_ALPHABET, String.valueOf(DEFAULT_ALPHABET),
						"Glyphs on the keyboard of the terminals"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		check(params, "length", MIN_LENGTH, MAX_LENGTH, problems);
		check(params, "alphabet", MIN_ALPHABET, MAX_ALPHABET, problems);
		if (problems.isEmpty() && config(params).length() > config(params).alphabet()) {
			problems.add("length must not exceed alphabet");
		}
		return problems;
	}

	/** Both terminals on two different slots, and each half given at its own terminal as soon as the step opens. */
	@Override
	public List<String> validateStep(Step step) {
		List<String> problems = new ArrayList<>();
		for (String[] pair : new String[][] { { LEFT, LEFT_CODE }, { RIGHT, RIGHT_CODE } }) {
			String slot = step.slots().get(pair[0]);
			if (slot == null) {
				problems.add("missing=slot:" + pair[0]);
			} else if (step.gives().stream().noneMatch(g -> pair[1].equals(g.clue()) && slot.equals(g.slot()) && !g.whenSolved())) {
				problems.add("missing=" + pair[1] + ";when=" + Give.OPEN);
			}
		}
		if (step.slots().get(LEFT) != null && step.slots().get(LEFT).equals(step.slots().get(RIGHT))) {
			problems.add("same_slot=" + LEFT + "," + RIGHT);
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

	static Config config(JsonNode params) {
		return new Config(clamp(intParam(params, "length", DEFAULT_LENGTH), MIN_LENGTH, MAX_LENGTH),
				clamp(intParam(params, "alphabet", DEFAULT_ALPHABET), MIN_ALPHABET, MAX_ALPHABET));
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
		List<String> pool = new ArrayList<>(SymbolSet.IDS);
		shuffle(pool, rng);
		List<String> alphabet = List.copyOf(pool.subList(0, Math.min(c.alphabet(), pool.size())));
		List<String> left;
		List<String> right;
		do {
			left = draw(rng, alphabet, c.length());
			right = draw(rng, alphabet, c.length());
		} while (left.equals(right));
		ObjectNode data = Json.object();
		data.set("alphabet", strings(alphabet));
		data.put("length", c.length());
		data.set(LEFT_CODE, strings(left));
		data.set(RIGHT_CODE, strings(right));
		ObjectNode seats = data.putObject("seats");
		seats.put(LEFT, -1);
		seats.put(RIGHT, -1);
		ObjectNode latched = data.putObject("latched");
		latched.put(LEFT, false);
		latched.put(RIGHT, false);
		ObjectNode by = data.putObject("by");
		by.put(LEFT, -1);
		by.put(RIGHT, -1);
		data.put("wrong", 0);
		data.put("solved", false);
		return new MechanismState(data, List.of(new Clue(LEFT_CODE, ClueType.SYMBOLS, strings(left)),
				new Clue(RIGHT_CODE, ClueType.SYMBOLS, strings(right))));
	}

	private static List<String> draw(RandomGenerator rng, List<String> alphabet, int length) {
		List<String> pool = new ArrayList<>(alphabet);
		shuffle(pool, rng);
		return List.copyOf(pool.subList(0, length));
	}

	// ---------------------------------------------------------------- gestures

	@Override
	public InputResult onUse(MechanismState state, int player, String role) {
		if (!LEFT.equals(role) && !RIGHT.equals(role)) {
			return InputResult.unchanged(state);
		}
		ObjectNode old = state.data();
		String other = LEFT.equals(role) ? RIGHT : LEFT;
		ObjectNode event = Json.object();
		event.put("seat", role);
		if (old.path("solved").asBoolean(false) || old.get("seats").get(other).asInt() == player
				|| old.get("by").get(other).asInt() == player) {
			event.put("type", "seat_refused");
			return new InputResult(state, List.of(event), false);
		}
		ObjectNode data = state.copyData();
		((ObjectNode) data.get("seats")).put(role, player);
		event.put("type", "seat");
		return new InputResult(state.withData(data), List.of(event), false);
	}

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		if (input == null || !input.path("seat").isString() || !input.path("symbols").isArray()) {
			throw new IllegalArgumentException("seat and symbols are required");
		}
		String seat = input.get("seat").asString();
		if (!LEFT.equals(seat) && !RIGHT.equals(seat)) {
			throw new IllegalArgumentException("seat left or right");
		}
		ObjectNode old = state.data();
		List<String> alphabet = list(old.get("alphabet"));
		List<String> entry = new ArrayList<>();
		for (JsonNode n : input.get("symbols")) {
			if (!n.isString() || !alphabet.contains(n.asString())) {
				throw new IllegalArgumentException("symbols come from the alphabet");
			}
			entry.add(n.asString());
		}
		if (entry.size() != old.get("length").asInt()) {
			throw new IllegalArgumentException("exactly " + old.get("length").asInt() + " symbols");
		}
		String other = LEFT.equals(seat) ? RIGHT : LEFT;
		if (old.get("seats").get(seat).asInt() != player) {
			throw new IllegalArgumentException("take the " + seat + " terminal first");
		}
		if (old.path("solved").asBoolean(false) || old.get("latched").get(seat).asBoolean()) {
			return InputResult.unchanged(state);
		}
		if (old.get("by").get(other).asInt() == player) {
			throw new IllegalArgumentException("another player must use this terminal");
		}
		boolean ok = entry.equals(list(old.get(LEFT.equals(seat) ? RIGHT_CODE : LEFT_CODE)));
		ObjectNode data = state.copyData();
		ObjectNode event = Json.object();
		event.put("seat", seat);
		boolean solved = false;
		if (ok) {
			((ObjectNode) data.get("latched")).put(seat, true);
			((ObjectNode) data.get("by")).put(seat, player);
			solved = data.get("latched").get(other).asBoolean();
			event.put("type", "latch");
		} else {
			data.put("wrong", old.get("wrong").asInt() + 1);
			event.put("type", "wrong");
		}
		data.put("solved", solved);
		return new InputResult(state.withData(data), List.of(event), solved);
	}

	// ---------------------------------------------------------------- view

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		ObjectNode view = Json.object();
		view.set("alphabet", d.get("alphabet").deepCopy());
		view.set("length", d.get("length").deepCopy());
		ObjectNode seats = view.putObject("seats");
		for (String seat : new String[] { LEFT, RIGHT }) {
			int holder = d.get("seats").get(seat).asInt();
			if (holder < 0) {
				seats.putNull(seat);
			} else {
				seats.put(seat, holder);
			}
		}
		view.set("latched", d.get("latched").deepCopy());
		view.set("wrong", d.get("wrong").deepCopy());
		view.put("solved", d.path("solved").asBoolean(false));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.set(LEFT_CODE, d.get(LEFT_CODE).deepCopy());
			view.set(RIGHT_CODE, d.get(RIGHT_CODE).deepCopy());
		}
		return view;
	}

	private static ArrayNode strings(List<String> values) {
		ArrayNode array = Json.array();
		values.forEach(array::add);
		return array;
	}

	private static List<String> list(JsonNode array) {
		List<String> out = new ArrayList<>();
		array.forEach(n -> out.add(n.asString()));
		return out;
	}

	private static void shuffle(List<?> list, RandomGenerator rng) {
		for (int i = list.size() - 1; i > 0; i--) {
			Collections.swap(list, i, rng.nextInt(i + 1));
		}
	}
}
