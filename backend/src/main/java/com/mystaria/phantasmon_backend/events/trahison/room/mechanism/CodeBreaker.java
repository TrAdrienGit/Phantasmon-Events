package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.events.trahison.room.Step;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A terminal that guesses a hidden combination (Mastermind): the group types a try, the terminal answers how many
 * tokens are <em>well placed</em> (right token, right position) and how many are <em>misplaced</em> (right token, wrong
 * position), and the history of tries stays on the screen. No clue is needed: logic is enough.
 *
 * <h2>Parameters</h2>
 * <ul>
 * <li>{@code length} 3..5 (default 4): tokens in the combination;</li>
 * <li>{@code alphabet} 4..9 (default 6): how many different tokens there are;</li>
 * <li>{@code kind} {@code symbols} (default) or {@code digits}: what the tokens look like. Symbols are glyph ids drawn
 * from {@link SymbolSet} by the seed (the alphabet is part of the view); digits are {@code 0..alphabet-1};</li>
 * <li>{@code repeats} (default false): whether a token may appear several times in the combination (without repeats the
 * alphabet must be at least {@code length});</li>
 * <li>{@code maxTries} 0 or 6..30 (default 0 = no limit): tries before the terminal resets. There is no dead end: when
 * the tries run out the <em>same</em> combination stays and the history is cleared ({@code resets} counts them, D36).</li>
 * </ul>
 *
 * <h2>Model</h2>
 * A token is its index into the {@code alphabet} list (0..alphabet-1); a combination and a try are arrays of indices.
 * Gesture {@code {"key": "0".."9" | "del" | "clear" | "enter"}}: a digit appends that token to the shared entry
 * (ignored when the entry is full, or when repeats are off and the token is already in it), {@code del} and
 * {@code clear} edit the entry, {@code enter} sends it (a short entry is ignored). The answer is appended to the
 * history: {@code exact} = positions with the same token, {@code misplaced} = (tokens in common, counted with
 * multiplicity) minus {@code exact}. A full-length try with {@code exact == length} solves the step.
 *
 * <h2>Generation</h2>
 * The combination is drawn from the seed; it never starts "solved" because nothing has been tried. Always solvable:
 * every try is answered, tries are unlimited or reset, and a candidate consistent with the history always exists (the
 * hidden combination itself), so a player who plays any consistent combination converges. The tests solve 1,000
 * draws that way through the real engine.
 */
@Component
public class CodeBreaker implements Mechanism {

	public static final String ID = "code_breaker";
	public static final String TERMINAL_ROLE = "terminal";

	static final int MIN_LENGTH = 3;
	static final int MAX_LENGTH = 5;
	static final int DEFAULT_LENGTH = 4;
	static final int MIN_ALPHABET = 4;
	static final int MAX_ALPHABET = 9;
	static final int DEFAULT_ALPHABET = 6;
	static final int MIN_TRIES = 6;
	static final int MAX_TRIES = 30;

	/** The validated parameters of one step. */
	record Config(int length, int alphabet, boolean symbols, boolean repeats, int maxTries) {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int difficulty(JsonNode params) {
		Config c = config(params);
		int score = (c.length() - MIN_LENGTH) + (c.alphabet() >= 7 ? 1 : 0) + (c.repeats() ? 1 : 0)
				+ (c.maxTries() > 0 && c.maxTries() <= 8 ? 1 : 0);
		return score <= 0 ? 1 : score <= 3 ? 2 : 3;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		Config c = config(params);
		return 60 + 25 * c.length() + 10 * c.alphabet() + (c.repeats() ? 40 : 0);
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(TERMINAL_ROLE, "terminal", "pedestal"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(
				new MechanismParam("length", "int", MIN_LENGTH, MAX_LENGTH, String.valueOf(DEFAULT_LENGTH), "Tokens in the hidden combination"),
				new MechanismParam("alphabet", "int", MIN_ALPHABET, MAX_ALPHABET, String.valueOf(DEFAULT_ALPHABET),
						"Different tokens available (at least length when repeats are off)"),
				new MechanismParam("kind", "text", 0, 0, "symbols", "symbols (glyphs drawn by the seed) or digits"),
				new MechanismParam("repeats", "bool", 0, 0, "false", "A token may appear several times in the combination"),
				new MechanismParam("maxTries", "int", 0, MAX_TRIES, "0",
						"Tries before the terminal clears its history (same combination); 0 = no limit, else 6..30"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		check(params, "length", MIN_LENGTH, MAX_LENGTH, problems);
		check(params, "alphabet", MIN_ALPHABET, MAX_ALPHABET, problems);
		if (params != null && params.has("kind") && !(params.get("kind").isString()
				&& (params.get("kind").asString().equals("symbols") || params.get("kind").asString().equals("digits")))) {
			problems.add("kind must be symbols or digits");
		}
		if (params != null && params.has("repeats") && !params.get("repeats").isBoolean()) {
			problems.add("repeats must be true or false");
		}
		if (params != null && params.has("maxTries")) {
			JsonNode v = params.get("maxTries");
			if (!v.isInt() || !(v.asInt() == 0 || (v.asInt() >= MIN_TRIES && v.asInt() <= MAX_TRIES))) {
				problems.add("maxTries must be 0 or an integer " + MIN_TRIES + ".." + MAX_TRIES);
			}
		}
		if (problems.isEmpty()) {
			Config c = config(params);
			if (!c.repeats() && intParam(params, "alphabet", DEFAULT_ALPHABET) < c.length()) {
				problems.add("alphabet must be at least length when repeats are off");
			}
		}
		return problems;
	}

	/** The terminal needs a slot to be played on (no clue is needed). */
	@Override
	public List<String> validateStep(Step step) {
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

	/** Reads the parameters with their defaults; out-of-range values are clamped (validateParams reports them). */
	static Config config(JsonNode params) {
		int length = clamp(intParam(params, "length", DEFAULT_LENGTH), MIN_LENGTH, MAX_LENGTH);
		boolean repeats = params != null && params.path("repeats").asBoolean(false);
		int alphabet = clamp(intParam(params, "alphabet", DEFAULT_ALPHABET), MIN_ALPHABET, MAX_ALPHABET);
		if (!repeats) {
			alphabet = Math.max(alphabet, length);
		}
		boolean symbols = !(params != null && "digits".equals(params.path("kind").asString("symbols")));
		int tries = intParam(params, "maxTries", 0);
		tries = tries <= 0 ? 0 : clamp(tries, MIN_TRIES, MAX_TRIES);
		return new Config(length, alphabet, symbols, repeats, tries);
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
		List<String> tokens = new ArrayList<>();
		if (c.symbols()) {
			List<String> pool = new ArrayList<>(SymbolSet.IDS);
			shuffle(pool, rng);
			tokens.addAll(pool.subList(0, c.alphabet()));
		} else {
			for (int i = 0; i < c.alphabet(); i++) {
				tokens.add(String.valueOf(i));
			}
		}
		long seed = rng.nextLong();
		ObjectNode data = Json.object();
		data.set("alphabet", strings(tokens));
		data.put("kind", c.symbols() ? "symbols" : "digits");
		data.put("length", c.length());
		data.put("repeats", c.repeats());
		data.put("maxTries", c.maxTries());
		data.put("seed", seed);
		data.set("code", ints(draw(new SplittableRandom(seed), c.length(), c.alphabet(), c.repeats())));
		data.put("entry", "");
		data.set("history", Json.array());
		data.put("tries", 0);
		data.put("resets", 0);
		data.put("solved", false);
		return new MechanismState(data, List.of());
	}

	/** One combination of indices; without repeats, distinct ones. Same generator state, same combination. */
	private static int[] draw(RandomGenerator rng, int length, int alphabet, boolean repeats) {
		int[] code = new int[length];
		if (repeats) {
			for (int i = 0; i < length; i++) {
				code[i] = rng.nextInt(alphabet);
			}
		} else {
			List<Integer> pool = new ArrayList<>();
			for (int i = 0; i < alphabet; i++) {
				pool.add(i);
			}
			shuffle(pool, rng);
			for (int i = 0; i < length; i++) {
				code[i] = pool.get(i);
			}
		}
		return code;
	}

	/** Positions with the same token, and tokens in common counted with multiplicity minus those: {@code {exact, misplaced}}. */
	static int[] score(int[] code, int[] guess, int alphabet) {
		int exact = 0;
		int[] inCode = new int[alphabet];
		int[] inGuess = new int[alphabet];
		for (int i = 0; i < code.length; i++) {
			if (code[i] == guess[i]) {
				exact++;
			}
			inCode[code[i]]++;
			inGuess[guess[i]]++;
		}
		int common = 0;
		for (int t = 0; t < alphabet; t++) {
			common += Math.min(inCode[t], inGuess[t]);
		}
		return new int[] { exact, common - exact };
	}

	// ---------------------------------------------------------------- gestures

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		String key = input != null && input.path("key").isString() ? input.get("key").asString() : null;
		if (key == null || !(key.matches("[0-9]") || key.equals("del") || key.equals("clear") || key.equals("enter"))) {
			throw new IllegalArgumentException("key: a token index, del, clear or enter");
		}
		ObjectNode old = state.data();
		int alphabet = old.get("alphabet").size();
		if (key.matches("[0-9]") && key.charAt(0) - '0' >= alphabet) {
			throw new IllegalArgumentException("token 0.." + (alphabet - 1));
		}
		if (old.path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		String entry = old.path("entry").asString("");
		int length = old.path("length").asInt();
		ObjectNode data = state.copyData();
		ObjectNode event = Json.object();
		switch (key) {
			case "del" -> {
				if (entry.isEmpty()) {
					return InputResult.unchanged(state);
				}
				data.put("entry", entry.substring(0, entry.length() - 1));
				event.put("type", "key");
				event.put("key", key);
			}
			case "clear" -> {
				if (entry.isEmpty()) {
					return InputResult.unchanged(state);
				}
				data.put("entry", "");
				event.put("type", "key");
				event.put("key", key);
			}
			case "enter" -> {
				if (entry.length() != length) {
					return InputResult.unchanged(state);
				}
				return enter(state, data, entry, alphabet);
			}
			default -> {
				if (entry.length() >= length || (!old.path("repeats").asBoolean(false) && entry.contains(key))) {
					return InputResult.unchanged(state);
				}
				data.put("entry", entry + key);
				event.put("type", "key");
				event.put("key", key);
			}
		}
		return new InputResult(state.withData(data), List.of(event), false);
	}

	private InputResult enter(MechanismState state, ObjectNode data, String entry, int alphabet) {
		int length = entry.length();
		int[] guess = new int[length];
		for (int i = 0; i < length; i++) {
			guess[i] = entry.charAt(i) - '0';
		}
		int[] code = intArray(data.get("code"));
		int[] s = score(code, guess, alphabet);
		ObjectNode row = Json.object();
		row.set("guess", ints(guess));
		row.put("exact", s[0]);
		row.put("misplaced", s[1]);
		ArrayNode history = (ArrayNode) data.get("history");
		history.add(row);
		int tries = data.path("tries").asInt() + 1;
		data.put("tries", tries);
		data.put("entry", "");
		ObjectNode event = Json.object();
		if (s[0] == length) {
			data.put("solved", true);
			event.put("type", "unlock");
			return new InputResult(state.withData(data), List.of(event), true);
		}
		event.put("type", "feedback");
		event.put("exact", s[0]);
		event.put("misplaced", s[1]);
		int max = data.path("maxTries").asInt();
		if (max > 0 && tries >= max) {
			// Out of tries: the same combination, the history cleared (decisions-tranchees.md, D36).
			int resets = data.path("resets").asInt() + 1;
			data.set("history", Json.array());
			data.put("tries", 0);
			data.put("resets", resets);
			event.put("type", "reset");
		}
		return new InputResult(state.withData(data), List.of(event), false);
	}

	// ---------------------------------------------------------------- views

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		ObjectNode view = Json.object();
		view.set("alphabet", d.get("alphabet").deepCopy());
		view.put("kind", d.path("kind").asString("symbols"));
		view.put("length", d.path("length").asInt());
		view.put("repeats", d.path("repeats").asBoolean(false));
		view.put("maxTries", d.path("maxTries").asInt());
		view.put("entry", d.path("entry").asString(""));
		view.set("history", d.get("history").deepCopy());
		view.put("tries", d.path("tries").asInt());
		view.put("resets", d.path("resets").asInt());
		view.put("solved", d.path("solved").asBoolean(false));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.set("code", d.get("code").deepCopy());
		}
		return view;
	}

	// ---------------------------------------------------------------- JSON helpers

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

	private static void shuffle(List<?> list, RandomGenerator rng) {
		for (int i = list.size() - 1; i > 0; i--) {
			Collections.swap(list, i, rng.nextInt(i + 1));
		}
	}
}
