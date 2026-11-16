package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.CodeBreaker;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests of {@link CodeBreaker}. */
class CodeBreakerTest {

	final CodeBreaker breaker = new CodeBreaker();

	static ObjectNode params(int length, int alphabet, String kind, boolean repeats, int maxTries) {
		ObjectNode p = Json.object();
		p.put("length", length);
		p.put("alphabet", alphabet);
		p.put("kind", kind);
		p.put("repeats", repeats);
		p.put("maxTries", maxTries);
		return p;
	}

	static ObjectNode key(String key) {
		ObjectNode in = Json.object();
		in.put("key", key);
		return in;
	}

	/** Types a try (token indices) and presses enter; returns the result of enter. */
	InputResult submit(MechanismState state, int... guess) {
		MechanismState s = state;
		for (int t : guess) {
			s = breaker.onInput(s, 0, key(String.valueOf(t))).state();
		}
		return breaker.onInput(s, 0, key("enter"));
	}

	static int[] code(MechanismState state) {
		JsonNode c = state.data().get("code");
		int[] out = new int[c.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = c.get(i).asInt();
		}
		return out;
	}

	/** Mastermind feedback computed independently of the mechanism. */
	static int[] feedback(int[] code, int[] guess) {
		int exact = 0;
		List<Integer> restCode = new ArrayList<>();
		List<Integer> restGuess = new ArrayList<>();
		for (int i = 0; i < code.length; i++) {
			if (code[i] == guess[i]) {
				exact++;
			} else {
				restCode.add(code[i]);
				restGuess.add(guess[i]);
			}
		}
		int misplaced = 0;
		for (Integer g : restGuess) {
			if (restCode.remove(g)) {
				misplaced++;
			}
		}
		return new int[] { exact, misplaced };
	}

	/** The candidates a player can still think of: combinations consistent with every answer shown in the history. */
	static List<int[]> candidates(JsonNode view) {
		int length = view.get("length").asInt();
		int alphabet = view.get("alphabet").size();
		boolean repeats = view.get("repeats").asBoolean();
		List<int[]> out = new ArrayList<>();
		int total = (int) Math.pow(alphabet, length);
		for (int n = 0; n < total; n++) {
			int[] c = new int[length];
			int v = n;
			for (int i = 0; i < length; i++) {
				c[i] = v % alphabet;
				v /= alphabet;
			}
			if (!repeats && java.util.Arrays.stream(c).distinct().count() != length) {
				continue;
			}
			boolean ok = true;
			for (JsonNode row : view.get("history")) {
				int[] g = new int[length];
				for (int i = 0; i < length; i++) {
					g[i] = row.get("guess").get(i).asInt();
				}
				int[] f = feedback(c, g);
				if (f[0] != row.get("exact").asInt() || f[1] != row.get("misplaced").asInt()) {
					ok = false;
					break;
				}
			}
			if (ok) {
				out.add(c);
			}
		}
		return out;
	}

	@Test
	void thousandRandomGenerationsAreSolvableByLogicAlone() {
		SplittableRandom pick = new SplittableRandom(21);
		for (long seed = 0; seed < 1000; seed++) {
			int length = 3 + pick.nextInt(3);
			boolean repeats = pick.nextBoolean();
			int alphabet = Math.max(repeats ? 4 : length, 4 + pick.nextInt(6));
			String kind = pick.nextBoolean() ? "symbols" : "digits";
			MechanismState state = breaker.generate(new SplittableRandom(seed), params(length, alphabet, kind, repeats, 0));
			assertTrue(state.clues().isEmpty(), "no clue is needed");
			assertFalse(state.data().get("solved").asBoolean());
			assertEquals(alphabet, state.data().get("alphabet").size());
			int[] code = code(state);
			assertEquals(length, code.length);
			if (!repeats) {
				assertEquals(length, java.util.Arrays.stream(code).distinct().count());
			}
			// Play like a patient player: always type a combination consistent with all the answers so far.
			MechanismState s = state;
			SplittableRandom choose = new SplittableRandom(seed);
			boolean solved = false;
			for (int turn = 0; turn < 40 && !solved; turn++) {
				List<int[]> left = candidates(breaker.view(s, ViewerKind.PLAYER));
				assertFalse(left.isEmpty(), "the hidden combination stays a candidate, seed " + seed);
				int[] guess = left.get(choose.nextInt(left.size()));
				InputResult r = submit(s, guess);
				// The answer is the Mastermind answer.
				JsonNode row = r.state().data().get("history").get(turn);
				int[] f = feedback(code, guess);
				assertEquals(f[0], row.get("exact").asInt(), "seed " + seed);
				assertEquals(f[1], row.get("misplaced").asInt(), "seed " + seed);
				s = r.state();
				solved = r.solved();
			}
			assertTrue(solved, "solved by consistent guesses, seed " + seed);
			assertTrue(s.data().get("solved").asBoolean());
		}
	}

	@Test
	void sameSeedSameCombinationAndOtherSeedsDiffer() {
		JsonNode p = params(4, 6, "symbols", false, 0);
		MechanismState a = breaker.generate(new SplittableRandom(7), p);
		MechanismState b = breaker.generate(new SplittableRandom(7), p);
		assertEquals(a.data(), b.data());
		Set<String> codes = new HashSet<>();
		for (long seed = 0; seed < 60; seed++) {
			MechanismState s = breaker.generate(new SplittableRandom(seed), p);
			codes.add(s.data().get("alphabet") + "|" + s.data().get("code"));
		}
		assertTrue(codes.size() > 55, "seeds give different combinations: " + codes.size());
	}

	@Test
	void viewNeverHoldsTheCombinationExceptForTheGameMaster() {
		for (long seed = 0; seed < 200; seed++) {
			MechanismState state = breaker.generate(new SplittableRandom(seed), params(4, 6, "digits", true, 8));
			MechanismState tried = submit(state, 0, 1, 2, 3).state();
			for (ViewerKind kind : new ViewerKind[] { ViewerKind.PLAYER, ViewerKind.SPECTATOR }) {
				for (MechanismState s : List.of(state, tried)) {
					JsonNode view = breaker.view(s, kind);
					assertEquals("[alphabet, kind, length, repeats, maxTries, entry, history, tries, resets, solved]", view.propertyNames().toString());
					assertFalse(Json.write(view).contains("\"code\"") || Json.write(view).contains("seed"), Json.write(view));
				}
			}
			assertEquals(state.data().get("code"), breaker.view(state, ViewerKind.GAME_MASTER).get("code"));
		}
	}

	@Test
	void theEntryIsSharedCappedAndWithoutRepeatsHoldsEachTokenOnce() {
		MechanismState state = breaker.generate(new SplittableRandom(1), params(3, 5, "digits", false, 0));
		MechanismState s = state;
		for (String k : new String[] { "2", "2", "4", "1", "0" }) {
			s = breaker.onInput(s, 0, key(k)).state();
		}
		assertEquals("241", s.data().get("entry").asString(), "the second 2 and the fourth token are ignored");
		assertTrue(breaker.onInput(s, 0, key("3")).events().isEmpty(), "full entry refuses more");
		s = breaker.onInput(s, 1, key("del")).state();
		assertEquals("24", s.data().get("entry").asString());
		InputResult digit = breaker.onInput(s, 1, key("3"));
		assertEquals("key", digit.events().get(0).get("type").asString());
		s = breaker.onInput(s, 0, key("clear")).state();
		assertEquals("", s.data().get("entry").asString());
		assertTrue(breaker.onInput(s, 0, key("del")).events().isEmpty());
		// With repeats a token may come again.
		MechanismState r = breaker.generate(new SplittableRandom(1), params(3, 5, "digits", true, 0));
		for (String k : new String[] { "2", "2", "2" }) {
			r = breaker.onInput(r, 0, key(k)).state();
		}
		assertEquals("222", r.data().get("entry").asString());
		// A short entry is not a try.
		MechanismState shortEntry = breaker.onInput(state, 0, key("1")).state();
		InputResult enter = breaker.onInput(shortEntry, 0, key("enter"));
		assertTrue(enter.events().isEmpty());
		assertEquals(0, enter.state().data().get("tries").asInt());
	}

	@Test
	void feedbackCountsWellPlacedAndMisplacedWithRepeats() {
		MechanismState state = breaker.generate(new SplittableRandom(9), params(4, 6, "digits", true, 0));
		// Force a known combination 1,1,2,3.
		ObjectNode data = state.copyData();
		data.set("code", Json.array().add(1).add(1).add(2).add(3));
		MechanismState known = state.withData(data);
		JsonNode row = submit(known, 1, 2, 1, 1).state().data().get("history").get(0);
		// code 1123 / try 1211: position 1 matches; rest code {1,2,3} try {2,1,1}: 2 and one 1 in common.
		assertEquals(1, row.get("exact").asInt());
		assertEquals(2, row.get("misplaced").asInt());
		InputResult first = submit(known, 3, 3, 3, 3);
		assertEquals("feedback", first.events().get(0).get("type").asString());
		assertEquals(1, first.events().get(0).get("exact").asInt());
		assertEquals(0, first.events().get(0).get("misplaced").asInt());
		InputResult win = submit(known, 1, 1, 2, 3);
		assertTrue(win.solved());
		assertEquals("unlock", win.events().get(0).get("type").asString());
		assertEquals(1, win.state().data().get("tries").asInt());
		// A solved step ignores everything.
		InputResult again = breaker.onInput(win.state(), 0, key("1"));
		assertFalse(again.solved());
		assertTrue(again.events().isEmpty());
	}

	@Test
	void runningOutOfTriesKeepsTheSameCombinationAndClearsTheHistory() {
		MechanismState state = breaker.generate(new SplittableRandom(5), params(3, 4, "digits", false, 6));
		MechanismState s = state;
		int[] first = code(state);
		int[] wrong = wrongGuess(first, 4);
		for (int i = 0; i < 5; i++) {
			InputResult r = submit(s, wrong);
			assertEquals("feedback", r.events().get(0).get("type").asString());
			s = r.state();
		}
		assertEquals(5, s.data().get("history").size());
		InputResult last = submit(s, wrong);
		assertFalse(last.solved());
		assertEquals("reset", last.events().get(0).get("type").asString());
		assertEquals(1, last.state().data().get("resets").asInt());
		assertEquals(0, last.state().data().get("tries").asInt());
		assertEquals(0, last.state().data().get("history").size());
		assertArrayEquals(first, code(last.state()), "same combination after a reset (D36)");
		// Deterministic: the same play on the same seed gives the same state.
		MechanismState again = state;
		for (int i = 0; i < 6; i++) {
			again = submit(again, wrong).state();
		}
		assertEquals(last.state().data(), again.data());
		// And the combination can still be found and solves the step after the reset.
		assertTrue(submit(last.state(), first).solved());
	}

	static int[] wrongGuess(int[] code, int alphabet) {
		int[] g = new int[code.length];
		for (int i = 0; i < g.length; i++) {
			g[i] = (code[i] + 1) % alphabet;
		}
		java.util.Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < g.length; i++) {
			int v = g[i];
			while (!seen.add(v)) {
				v = (v + 1) % alphabet;
			}
			g[i] = v;
		}
		return g;
	}

	@Test
	void onInputDoesNotModifyTheOldState() {
		MechanismState state = breaker.generate(new SplittableRandom(3), params(4, 6, "symbols", false, 6));
		String before = state.data().toString();
		submit(state, 0, 1, 2, 3);
		breaker.onInput(state, 0, key("1"));
		assertEquals(before, state.data().toString());
	}

	@Test
	void inputsAreValidated() {
		MechanismState state = breaker.generate(new SplittableRandom(3), params(4, 6, "symbols", false, 0));
		assertThrows(IllegalArgumentException.class, () -> breaker.onInput(state, 0, null));
		assertThrows(IllegalArgumentException.class, () -> breaker.onInput(state, 0, Json.object()));
		assertThrows(IllegalArgumentException.class, () -> breaker.onInput(state, 0, key("x")));
		assertThrows(IllegalArgumentException.class, () -> breaker.onInput(state, 0, key("10")));
		assertThrows(IllegalArgumentException.class, () -> breaker.onInput(state, 0, key("6")), "token outside the alphabet");
	}

	@Test
	void validatesParamsAndWiring() {
		assertTrue(breaker.validateParams(Json.object()).isEmpty());
		assertTrue(breaker.validateParams(params(5, 9, "digits", true, 30)).isEmpty());
		assertTrue(breaker.validateParams(params(3, 4, "symbols", false, 6)).isEmpty());
		assertFalse(breaker.validateParams(params(2, 6, "symbols", false, 0)).isEmpty());
		assertFalse(breaker.validateParams(params(4, 10, "symbols", false, 0)).isEmpty());
		assertFalse(breaker.validateParams(params(4, 6, "letters", false, 0)).isEmpty());
		assertFalse(breaker.validateParams(params(4, 6, "symbols", false, 3)).isEmpty());
		assertFalse(breaker.validateParams(params(4, 6, "symbols", false, 31)).isEmpty());
		assertFalse(breaker.validateParams(params(5, 4, "symbols", false, 0)).isEmpty(), "no repeats needs alphabet >= length");
		assertTrue(breaker.validateParams(params(5, 4, "symbols", true, 0)).isEmpty());
		Step ok = new Step("c", "code_breaker", null, Map.of("terminal", "t"), List.of(), List.of(), List.of());
		assertTrue(breaker.validateStep(ok).isEmpty(), "no clue is required");
		Step none = new Step("c", "code_breaker", null, Map.of(), List.of(), List.of(), List.of());
		assertEquals(1, breaker.validateStep(none).size());
	}

	@Test
	void difficultyAndTimeGrowWithTheSize() {
		assertEquals(1, breaker.difficulty(params(3, 4, "digits", false, 0)));
		assertEquals(2, breaker.difficulty(Json.object()));
		assertEquals(3, breaker.difficulty(params(5, 9, "symbols", true, 6)));
		assertTrue(breaker.expectedSeconds(params(5, 9, "symbols", true, 0)) > breaker.expectedSeconds(params(3, 4, "symbols", false, 0)));
		assertEquals(List.of(), breaker.clueRoles(Json.object()));
		assertEquals("terminal", breaker.slotRoles().get(0).role());
	}
}
