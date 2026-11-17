package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.events.trahison.room.Give;
import com.mystaria.phantasmon_backend.events.trahison.room.Step;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A sentence written in the Unown alphabet (Zarbi) on a wall or a document, that hides a code spelled with digit words. The
 * clue {@code wall_text} ({@link ClueType#TEXT}) holds the sentence in plain capital letters: the client draws it with its own
 * Zarbi glyphs, one per letter. The clue {@code alphabet} ({@link ClueType#DOCUMENT}, one page) is the key: the 26
 * letters, for players to match glyphs and letters. The players type the digits on a terminal of the step.
 *
 * <h2>Parameters</h2>
 * <ul><li>{@code length} 3..4 (default 3): number of digits of the code.</li></ul>
 * <h2>Gesture</h2> {@code {"code": "142"}} (see {@link CodeEntry}).
 * <h2>Generation</h2> One of {@value #TEMPLATE_COUNT} sentence templates, and the digits drawn by the seed. Always solvable: the
 * sentence spells the code.
 */
@Component
public class UnownText implements Mechanism {

	public static final String ID = "unown_text";
	public static final String TEXT_ROLE = "wall_text";
	public static final String ALPHABET_ROLE = "alphabet";
	public static final String TERMINAL_ROLE = "terminal";
	static final int MIN_LENGTH = 3;
	static final int MAX_LENGTH = 4;
	public static final String[] DIGIT_WORDS = { "ZERO", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE" };
	/** {@code %s} is the digit words; letters and spaces only, so every character has a glyph. */
	static final List<String> TEMPLATES = List.of(
			"THE DOOR OPENS WITH %s",
			"SAY %s TO THE GUARDIAN",
			"THE OLD ONES KEPT THE SECRET %s",
			"ONLY THE NUMBER %s WAKES THE LOCK",
			"WHISPER %s AND PASS");
	static final int TEMPLATE_COUNT = 5;

	@Override
	public String id() {
		return ID;
	}

	static int length(JsonNode params) {
		return CodeEntry.intParam(params, "length", MIN_LENGTH, MIN_LENGTH, MAX_LENGTH);
	}

	@Override
	public int difficulty(JsonNode params) {
		return length(params) == 3 ? 1 : 2;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		return 90 + 30 * length(params);
	}

	@Override
	public List<String> clueRoles(JsonNode params) {
		return List.of(TEXT_ROLE, ALPHABET_ROLE);
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(TERMINAL_ROLE, "keypad", "terminal"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(new MechanismParam("length", "int", MIN_LENGTH, MAX_LENGTH, String.valueOf(MIN_LENGTH),
				"Number of digits spelled by the sentence"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		CodeEntry.checkInt(params, "length", MIN_LENGTH, MAX_LENGTH, problems);
		return problems;
	}

	@Override
	public List<String> validateStep(Step step) {
		List<String> problems = new ArrayList<>();
		for (String role : clueRoles(step.params())) {
			if (step.gives().stream().noneMatch(g -> role.equals(g.clue()) && Give.OPEN.equals(g.when()))) {
				problems.add("missing=" + role + ";when=open");
			}
		}
		if (step.slots().isEmpty()) {
			problems.add("missing=slot:" + TERMINAL_ROLE);
		}
		return problems;
	}

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		int length = length(params);
		StringBuilder code = new StringBuilder();
		List<String> words = new ArrayList<>();
		for (int i = 0; i < length; i++) {
			int d = rng.nextInt(10);
			code.append((char) ('0' + d));
			words.add(DIGIT_WORDS[d]);
		}
		String sentence = TEMPLATES.get(rng.nextInt(TEMPLATES.size())).formatted(String.join(" ", words));
		StringBuilder key = new StringBuilder();
		for (char c = 'A'; c <= 'Z'; c++) {
			key.append(c).append(c == 'M' || c == 'Z' ? '\n' : ' ');
		}
		ObjectNode data = Json.object();
		data.put("code", code.toString());
		data.put("wrong", 0);
		data.put("solved", false);
		return new MechanismState(data, List.of(Clue.text(TEXT_ROLE, sentence),
				Clue.document(ALPHABET_ROLE, List.of(key.toString().strip()))));
	}

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		return CodeEntry.submit(state, input, MAX_LENGTH);
	}

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode view = CodeEntry.view(state, viewer);
		view.put("length", state.data().path("code").asString("").length());
		return view;
	}
}
