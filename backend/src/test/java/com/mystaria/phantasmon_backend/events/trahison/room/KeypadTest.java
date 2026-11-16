package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Keypad;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests of {@link Keypad}. */
class KeypadTest {

	final RoomFixtures.TestClock clock = new RoomFixtures.TestClock();
	final Keypad pad = new Keypad(clock);

	static ObjectNode params(int length) {
		ObjectNode p = Json.object();
		p.put("length", length);
		return p;
	}

	static ObjectNode key(String key) {
		ObjectNode in = Json.object();
		in.put("key", key);
		return in;
	}

	/** Types a string of keys; returns the last result. */
	InputResult type(MechanismState state, String... keys) {
		InputResult last = new InputResult(state, List.of(), false);
		for (String k : keys) {
			last = pad.onInput(last.state(), 0, key(k));
		}
		return last;
	}

	static String code(MechanismState state) {
		return state.clues().get(0).value().asString();
	}

	@Test
	void thousandRandomGenerationsAreSolvableAndNeverStartSolved() {
		SplittableRandom pick = new SplittableRandom(5);
		for (long seed = 0; seed < 1000; seed++) {
			int length = 3 + pick.nextInt(4);
			MechanismState state = pad.generate(new SplittableRandom(seed), params(length));
			assertFalse(state.data().get("solved").asBoolean(), "never starts solved, seed " + seed);
			assertEquals(1, state.clues().size());
			assertEquals("code", state.clues().get(0).role());
			assertEquals("digits", state.clues().get(0).type().wire());
			String code = code(state);
			assertEquals(length, code.length());
			assertTrue(code.matches("[0-9]+"), code);
			// Solve it from the clue alone, through the real engine: only the last key reports solved.
			MechanismState s = state;
			for (int i = 0; i < code.length(); i++) {
				InputResult r = pad.onInput(s, 0, key(String.valueOf(code.charAt(i))));
				assertFalse(r.solved(), "typing does not solve, seed " + seed);
				s = r.state();
			}
			InputResult done = pad.onInput(s, 0, key("enter"));
			assertTrue(done.solved(), "seed " + seed);
			assertTrue(done.state().data().get("solved").asBoolean());
		}
	}

	@Test
	void sameSeedSameCodeAndOtherSeedsDiffer() {
		JsonNode p = params(5);
		MechanismState a = pad.generate(new SplittableRandom(7), p);
		MechanismState b = pad.generate(new SplittableRandom(7), p);
		assertEquals(a.data(), b.data());
		assertEquals(code(a), code(b));
		Set<String> codes = new HashSet<>();
		for (long seed = 0; seed < 50; seed++) {
			codes.add(code(pad.generate(new SplittableRandom(seed), p)));
		}
		assertTrue(codes.size() > 45, "seeds give different codes");
	}

	@Test
	void viewNeverHoldsTheCodeExceptForTheGameMaster() {
		for (long seed = 0; seed < 200; seed++) {
			MechanismState state = pad.generate(new SplittableRandom(seed), params(4));
			String code = code(state);
			for (ViewerKind kind : new ViewerKind[] { ViewerKind.PLAYER, ViewerKind.SPECTATOR }) {
				JsonNode view = pad.view(state, kind);
				assertFalse(Json.write(view).contains("code"), Json.write(view));
				assertEquals("[length, entry, wrong, solved, lock_ms]", view.propertyNames().toString());
				assertEquals("", view.get("entry").asString());
			}
			assertEquals(code, pad.view(state, ViewerKind.GAME_MASTER).get("code").asString());
			// Even after a wrong try and a full entry, the view only echoes what the players typed.
			MechanismState typed = type(state, "1", "2", "3").state();
			assertEquals("123", pad.view(typed, ViewerKind.PLAYER).get("entry").asString());
		}
	}

	@Test
	void entryIsSharedCappedAndEditable() {
		MechanismState state = pad.generate(new SplittableRandom(1), params(3));
		state = type(state, "4", "2", "7", "9").state(); // the fourth digit is ignored
		assertEquals("427", state.data().get("entry").asString());
		InputResult extra = pad.onInput(state, 0, key("1"));
		assertTrue(extra.events().isEmpty(), "a refused digit is not an event");
		state = type(state, "del").state();
		assertEquals("42", state.data().get("entry").asString());
		state = type(state, "clear").state();
		assertEquals("", state.data().get("entry").asString());
		assertTrue(pad.onInput(state, 0, key("del")).events().isEmpty());
		InputResult digit = pad.onInput(state, 1, key("5"));
		assertEquals("key", digit.events().get(0).get("type").asString());
		assertEquals("5", digit.events().get(0).get("key").asString());
	}

	@Test
	void aWrongCodeEmptiesTheEntryAndCountsTheError() {
		MechanismState state = pad.generate(new SplittableRandom(2), params(4));
		String code = code(state);
		String wrong = code.equals("0000") ? "1111" : "0000";
		InputResult r = type(state, wrong.substring(0, 1), wrong.substring(1, 2), wrong.substring(2, 3), wrong.substring(3, 4), "enter");
		assertFalse(r.solved());
		assertEquals("wrong", r.events().get(0).get("type").asString());
		assertEquals("", r.state().data().get("entry").asString());
		assertEquals(1, r.state().data().get("wrong").asInt());
		// A short entry is wrong too.
		InputResult shortTry = type(r.state(), "1", "enter");
		assertEquals(2, shortTry.state().data().get("wrong").asInt());
		assertFalse(shortTry.state().data().get("solved").asBoolean());
		// And the right one still works afterwards.
		MechanismState s = shortTry.state();
		for (char c : code.toCharArray()) {
			s = pad.onInput(s, 0, key(String.valueOf(c))).state();
		}
		assertTrue(pad.onInput(s, 0, key("enter")).solved());
	}

	@Test
	void solvedStepIgnoresFurtherKeys() {
		MechanismState state = pad.generate(new SplittableRandom(3), params(3));
		MechanismState s = state;
		for (char c : code(state).toCharArray()) {
			s = pad.onInput(s, 0, key(String.valueOf(c))).state();
		}
		s = pad.onInput(s, 0, key("enter")).state();
		InputResult again = pad.onInput(s, 0, key("1"));
		assertFalse(again.solved());
		assertTrue(again.events().isEmpty());
		assertEquals(s.data(), again.state().data());
	}

	@Test
	void onInputDoesNotModifyTheOldState() {
		MechanismState state = pad.generate(new SplittableRandom(3), params(4));
		String before = state.data().toString();
		pad.onInput(state, 0, key("1"));
		pad.onInput(state, 0, key("enter"));
		assertEquals(before, state.data().toString());
	}

	@Test
	void inputsAreValidated() {
		MechanismState state = pad.generate(new SplittableRandom(3), params(4));
		assertThrows(IllegalArgumentException.class, () -> pad.onInput(state, 0, null));
		assertThrows(IllegalArgumentException.class, () -> pad.onInput(state, 0, Json.object()));
		assertThrows(IllegalArgumentException.class, () -> pad.onInput(state, 0, key("10")));
		assertThrows(IllegalArgumentException.class, () -> pad.onInput(state, 0, key("x")));
		ObjectNode number = Json.object();
		number.put("key", 5);
		assertThrows(IllegalArgumentException.class, () -> pad.onInput(state, 0, number));
	}

	@Test
	void validatesParamsAndWiring() {
		assertTrue(pad.validateParams(Json.object()).isEmpty());
		assertTrue(pad.validateParams(params(3)).isEmpty());
		assertTrue(pad.validateParams(params(6)).isEmpty());
		assertFalse(pad.validateParams(params(2)).isEmpty());
		assertFalse(pad.validateParams(params(7)).isEmpty());
		ObjectNode text = Json.object();
		text.put("length", "4");
		assertFalse(pad.validateParams(text).isEmpty());
		// Wiring: the code must be given from the start, and a slot must be bound.
		Step ok = new Step("k", "keypad", null, java.util.Map.of("keypad", "pad"), List.of(), List.of(new Give("code", "wall", null)), List.of());
		assertTrue(pad.validateStep(ok).isEmpty());
		Step late = new Step("k", "keypad", null, java.util.Map.of("keypad", "pad"), List.of(), List.of(new Give("code", "wall", "solved")), List.of());
		assertFalse(pad.validateStep(late).isEmpty());
		Step none = new Step("k", "keypad", null, java.util.Map.of(), List.of(), List.of(), List.of());
		assertEquals(2, pad.validateStep(none).size());
	}

	@Test
	void difficultyAndTimeGrowWithTheLength() {
		assertEquals(1, pad.difficulty(params(3)));
		assertEquals(2, pad.difficulty(Json.object()));
		assertEquals(3, pad.difficulty(params(6)));
		assertTrue(pad.expectedSeconds(params(6)) > pad.expectedSeconds(params(3)));
		assertEquals(List.of("code"), pad.clueRoles(Json.object()));
		assertEquals("keypad", pad.slotRoles().get(0).role());
	}

	/** Types a wrong code of the right length and confirms it. */
	MechanismState wrongTry(MechanismState state) {
		String code = code(state);
		char[] bad = code.toCharArray();
		bad[0] = bad[0] == '0' ? '1' : '0';
		String[] keys = new String[bad.length + 1];
		for (int i = 0; i < bad.length; i++) {
			keys[i] = String.valueOf(bad[i]);
		}
		keys[bad.length] = "enter";
		return type(state, keys).state();
	}

	@Test
	void theThirdWrongTryLocksTheKeypadForTenSeconds() {
		MechanismState state = pad.generate(new SplittableRandom(8), params(3));
		state = wrongTry(state);
		state = wrongTry(state);
		assertEquals(0, pad.view(state, ViewerKind.PLAYER).get("lock_ms").asLong(), "two errors do not lock");
		MechanismState before = state;
		InputResult lockedTry = wrongOf(before);
		JsonNode event = wrongEvent(lockedTry);
		assertTrue(event.get("locked").asBoolean());
		assertEquals(10000, event.get("ms").asLong());
		state = lockedTry.state();
		assertEquals(10000, pad.view(state, ViewerKind.PLAYER).get("lock_ms").asLong());
		// Locked: every key is ignored, even the right code.
		String code = code(state);
		for (int i = 0; i < code.length(); i++) {
			InputResult r = pad.onInput(state, 0, key(String.valueOf(code.charAt(i))));
			assertTrue(r.events().isEmpty());
			assertEquals(state.data(), r.state().data());
		}
		assertFalse(pad.onInput(state, 0, key("enter")).solved());
		clock.now = clock.now.plusSeconds(4);
		assertEquals(6000, pad.view(state, ViewerKind.PLAYER).get("lock_ms").asLong());
		assertTrue(pad.onInput(state, 0, key("1")).events().isEmpty(), "still locked at 4 s");
		clock.now = clock.now.plusSeconds(6);
		assertEquals(0, pad.view(state, ViewerKind.PLAYER).get("lock_ms").asLong());
		assertEquals("key", pad.onInput(state, 0, key("1")).events().get(0).get("type").asString());
	}

	private JsonNode wrongEvent(InputResult r) {
		return r.events().stream().filter(e -> "wrong".equals(e.path("type").asString(""))).findFirst().orElse(null);
	}

	private InputResult wrongOf(MechanismState state) {
		String code = code(state);
		char[] bad = code.toCharArray();
		bad[0] = bad[0] == '0' ? '1' : '0';
		String[] keys = new String[bad.length + 1];
		for (int i = 0; i < bad.length; i++) {
			keys[i] = String.valueOf(bad[i]);
		}
		keys[bad.length] = "enter";
		return type(state, keys);
	}

	@Test
	void afterTheLockTheCountStartsAgainAndTheRightCodeSolves() {
		MechanismState state = pad.generate(new SplittableRandom(9), params(4));
		for (int i = 0; i < 3; i++) {
			state = wrongTry(state);
		}
		assertEquals(3, state.data().get("wrong").asInt());
		clock.now = clock.now.plusSeconds(11);
		state = wrongTry(state);
		state = wrongTry(state);
		assertEquals(0, pad.view(state, ViewerKind.PLAYER).get("lock_ms").asLong(), "two new errors: not locked yet");
		InputResult third = wrongOf(state);
		assertTrue(wrongEvent(third).get("locked").asBoolean(), "the third error in the new series locks again");
		clock.now = clock.now.plusSeconds(11);
		MechanismState s = third.state();
		for (char c : code(s).toCharArray()) {
			s = pad.onInput(s, 0, key(String.valueOf(c))).state();
		}
		assertTrue(pad.onInput(s, 0, key("enter")).solved());
	}
}
