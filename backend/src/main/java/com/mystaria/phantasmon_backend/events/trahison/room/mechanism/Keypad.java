package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.events.trahison.room.Give;
import com.mystaria.phantasmon_backend.events.trahison.room.Step;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A wall keypad: the group types a code and confirms it. The code is drawn from the seed and produced as a
 * {@link ClueType#DIGITS} clue, role {@value #CODE_ROLE}, that the room shows somewhere else (a wall panel, a document, a
 * clue of another step: {@code Step.gives}); the keypad itself never says it. The players find the clue, come back and
 * type it. What is typed is shared by everybody at the screen.
 *
 * <h2>Parameters</h2>
 * <ul>
 * <li>{@code length} 3..6 (default 4): number of digits of the code.</li>
 * </ul>
 *
 * <h2>Gestures</h2>
 * {@code {"key": "0".."9" | "del" | "clear" | "enter"}}. A digit is appended to the entry (ignored when the entry is as
 * long as the code), {@code del} removes the last digit, {@code clear} empties the entry, {@code enter} compares: the
 * right code solves the step; anything else (a short entry included) empties the entry and counts one error
 * ({@code wrong} in the view). <b>Lock-out</b> (decisions-tranchees.md, D30): the {@value #LOCK_AFTER}rd wrong try in a row locks the
 * keypad for {@value #LOCK_SECONDS} seconds: every key is ignored until then, the failure count starts again, the event
 * is {@code wrong} with {@code locked: true} and {@code ms}, and the view carries {@code lock_ms} (milliseconds left, 0 when
 * free; the client counts down from the moment it receives it). The expiry is lazy, measured on the clock this bean holds.
 *
 * <h2>Generation</h2>
 * Every digit is drawn independently (a code may start with 0). Always solvable: typing the code and {@code enter} solves
 * it, and the code is in the clue.
 */
@Component
public class Keypad implements Mechanism {

	public static final String ID = "keypad";
	public static final String CODE_ROLE = "code";
	public static final String KEYPAD_ROLE = "keypad";

	static final int MIN_LENGTH = 3;
	static final int MAX_LENGTH = 6;
	static final int DEFAULT_LENGTH = 4;
	static final int LOCK_AFTER = 3;
	static final int LOCK_SECONDS = 10;

	private final Clock clock;

	@Autowired
	public Keypad() {
		this(Clock.systemUTC());
	}

	/** With the clock the lock-out is measured on (tests move it). */
	public Keypad(Clock clock) {
		this.clock = clock;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int difficulty(JsonNode params) {
		int length = length(params);
		return length <= 3 ? 1 : length <= 5 ? 2 : 3;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		return 30 + 12 * length(params);
	}

	@Override
	public List<String> clueRoles(JsonNode params) {
		return List.of(CODE_ROLE);
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(KEYPAD_ROLE, "keypad", "terminal"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(new MechanismParam("length", "int", MIN_LENGTH, MAX_LENGTH, String.valueOf(DEFAULT_LENGTH),
				"Number of digits of the code; the code itself is a digits clue (role code) to show elsewhere"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		if (params != null && params.has("length")) {
			JsonNode v = params.get("length");
			if (!v.isInt() || v.asInt() < MIN_LENGTH || v.asInt() > MAX_LENGTH) {
				problems.add("length must be an integer " + MIN_LENGTH + ".." + MAX_LENGTH);
			}
		}
		return problems;
	}

	/** The code clue must be shown from the moment the step opens, and something must be bound to type on. */
	@Override
	public List<String> validateStep(Step step) {
		List<String> problems = new ArrayList<>();
		if (step.gives().stream().noneMatch(g -> CODE_ROLE.equals(g.clue()) && Give.OPEN.equals(g.when()))) {
			problems.add("missing=" + CODE_ROLE + ";when=open");
		}
		if (step.slots().isEmpty()) {
			problems.add("missing=slot:" + KEYPAD_ROLE);
		}
		return problems;
	}

	static int length(JsonNode params) {
		int v = params != null && params.path("length").isNumber() ? params.get("length").asInt() : DEFAULT_LENGTH;
		return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, v));
	}

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		int length = length(params);
		StringBuilder code = new StringBuilder();
		for (int i = 0; i < length; i++) {
			code.append((char) ('0' + rng.nextInt(10)));
		}
		ObjectNode data = Json.object();
		data.put("code", code.toString());
		data.put("length", length);
		data.put("entry", "");
		data.put("wrong", 0);
		data.put("fails", 0);
		data.put("lockedUntil", 0L);
		data.put("solved", false);
		return new MechanismState(data, List.of(Clue.digits(CODE_ROLE, code.toString())));
	}

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		String key = input != null && input.path("key").isString() ? input.get("key").asString() : null;
		if (key == null || !(key.matches("[0-9]") || key.equals("del") || key.equals("clear") || key.equals("enter"))) {
			throw new IllegalArgumentException("key: a digit, del, clear or enter");
		}
		ObjectNode old = state.data();
		if (old.path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		long now = clock.millis();
		if (old.path("lockedUntil").asLong(0) > now) {
			return InputResult.unchanged(state); // locked out: every key is ignored
		}
		String entry = old.path("entry").asString("");
		int length = old.path("length").asInt();
		ObjectNode data = state.copyData();
		ObjectNode event = Json.object();
		boolean solved = false;
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
				if (entry.equals(old.path("code").asString(""))) {
					solved = true;
					data.put("solved", true);
					event.put("type", "unlock");
				} else {
					data.put("entry", "");
					data.put("wrong", old.path("wrong").asInt() + 1);
					event.put("type", "wrong");
					int fails = old.path("fails").asInt() + 1;
					if (fails >= LOCK_AFTER) {
						fails = 0;
						data.put("lockedUntil", now + LOCK_SECONDS * 1000L);
						event.put("locked", true);
						event.put("ms", LOCK_SECONDS * 1000L);
					}
					data.put("fails", fails);
				}
			}
			default -> {
				if (entry.length() >= length) {
					return InputResult.unchanged(state);
				}
				data.put("entry", entry + key);
				event.put("type", "key");
				event.put("key", key);
			}
		}
		return new InputResult(state.withData(data), List.of(event), solved);
	}

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		ObjectNode view = Json.object();
		view.put("length", d.path("length").asInt());
		view.put("entry", d.path("entry").asString(""));
		view.put("wrong", d.path("wrong").asInt());
		view.put("solved", d.path("solved").asBoolean(false));
		view.put("lock_ms", Math.max(0L, d.path("lockedUntil").asLong(0) - clock.millis()));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.put("code", d.path("code").asString(""));
		}
		return view;
	}
}
