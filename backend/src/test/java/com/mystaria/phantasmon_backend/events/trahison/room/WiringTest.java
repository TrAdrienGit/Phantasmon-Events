package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Clue;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Wiring;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests of {@link Wiring}. */
class WiringTest {

	final Wiring wiring = new Wiring();

	static ObjectNode params(int wires, int colors) {
		ObjectNode p = Json.object();
		p.put("wires", wires);
		p.put("colors", colors);
		return p;
	}

	static ObjectNode connect(int left, int right) {
		ObjectNode in = Json.object();
		in.put("op", "connect");
		in.put("left", left);
		in.put("right", right);
		return in;
	}

	static ObjectNode disconnect(String side, int index) {
		ObjectNode in = Json.object();
		in.put("op", "disconnect");
		in.put(side, index);
		return in;
	}

	static List<String> scheme(MechanismState state) {
		List<String> out = new java.util.ArrayList<>();
		state.clues().get(0).value().forEach(v -> out.add(v.asString()));
		return out;
	}

	static List<String> right(MechanismState state) {
		List<String> out = new java.util.ArrayList<>();
		state.data().get("right").forEach(v -> out.add(v.asString()));
		return out;
	}

	@Test
	void thousandRandomGenerationsAreSolvableFromTheClueAlone() {
		SplittableRandom pick = new SplittableRandom(11);
		for (long seed = 0; seed < 1000; seed++) {
			int wires = 3 + pick.nextInt(4);
			int colors = 2 + pick.nextInt(Math.min(4, wires - 1)); // 2..min(5, wires)
			MechanismState state = wiring.generate(new SplittableRandom(seed), params(wires, colors));
			assertEquals(2, state.clues().size());
			assertEquals("scheme", state.clues().get(0).role());
			assertEquals("colors", state.clues().get(0).type().wire());
			List<String> scheme = scheme(state);
			List<String> right = right(state);
			assertEquals(wires, scheme.size());
			assertEquals(colors, new HashSet<>(scheme).size(), "every colour of the palette is used, seed " + seed);
			assertFalse(state.data().get("solved").asBoolean());
			// Solve from the clue and the panel's public colours, through the real engine, in a scrambled order.
			boolean[] used = new boolean[wires];
			MechanismState s = state;
			InputResult last = null;
			for (int step = 0; step < wires; step++) {
				// the order of the lefts is a permutation: walk 0..wires-1 rotated by the seed
				int left = (step + (int) (seed % wires)) % wires;
				int target = -1;
				for (int j = 0; j < wires && target < 0; j++) {
					if (!used[j] && right.get(j).equals(scheme.get(left))) {
						target = j;
					}
				}
				assertTrue(target >= 0, "a free right terminal of the colour exists, seed " + seed);
				used[target] = true;
				last = wiring.onInput(s, 0, connect(left, target));
				assertEquals(step == wires - 1, last.solved(), "only the last wire solves, seed " + seed);
				s = last.state();
			}
			assertTrue(last.solved());
			assertTrue(s.data().get("solved").asBoolean());
		}
	}

	@Test
	void sameSeedSameSchemeAndOtherSeedsDiffer() {
		JsonNode p = params(5, 3);
		MechanismState a = wiring.generate(new SplittableRandom(7), p);
		MechanismState b = wiring.generate(new SplittableRandom(7), p);
		assertEquals(a.data(), b.data());
		assertEquals(scheme(a), scheme(b));
		Set<String> schemes = new HashSet<>();
		for (long seed = 0; seed < 50; seed++) {
			MechanismState s = wiring.generate(new SplittableRandom(seed), p);
			schemes.add(scheme(s) + "|" + right(s));
		}
		assertTrue(schemes.size() > 30, "seeds give different panels: " + schemes.size());
	}

	@Test
	void viewNeverHoldsTheSchemeExceptForTheGameMaster() {
		for (long seed = 0; seed < 200; seed++) {
			MechanismState state = wiring.generate(new SplittableRandom(seed), params(4, 3));
			for (ViewerKind kind : new ViewerKind[] { ViewerKind.PLAYER, ViewerKind.SPECTATOR }) {
				JsonNode view = wiring.view(state, kind);
				assertEquals("[wires, right, links, wrong, solved]", view.propertyNames().toString());
				assertFalse(Json.write(view).contains("scheme"));
			}
			assertEquals(state.clues().get(0).value(), wiring.view(state, ViewerKind.GAME_MASTER).get("scheme"));
		}
	}

	@Test
	void aTerminalHoldsOneWireAndWiresCanBeRemoved() {
		MechanismState state = wiring.generate(new SplittableRandom(1), params(4, 3));
		InputResult r = wiring.onInput(state, 0, connect(0, 2));
		assertEquals("connect", r.events().get(0).get("type").asString());
		assertEquals("[2,-1,-1,-1]", r.state().data().get("links").toString());
		// Same link again: nothing. A new left on the same right steals it. A new right for the same left moves it.
		assertTrue(wiring.onInput(r.state(), 0, connect(0, 2)).events().isEmpty());
		MechanismState s = wiring.onInput(r.state(), 1, connect(1, 2)).state();
		assertEquals("[-1,2,-1,-1]", s.data().get("links").toString());
		s = wiring.onInput(s, 1, connect(1, 0)).state();
		assertEquals("[-1,0,-1,-1]", s.data().get("links").toString());
		// Disconnect by either side; an empty terminal is ignored.
		InputResult byRight = wiring.onInput(s, 0, disconnect("right", 0));
		assertEquals("[-1,-1,-1,-1]", byRight.state().data().get("links").toString());
		assertEquals(1, byRight.events().get(0).get("left").asInt());
		InputResult byLeft = wiring.onInput(s, 0, disconnect("left", 1));
		assertEquals("[-1,-1,-1,-1]", byLeft.state().data().get("links").toString());
		assertTrue(wiring.onInput(s, 0, disconnect("left", 3)).events().isEmpty());
	}

	@Test
	void aFullWrongSetIsAShortCircuitThatDropsEveryWire() {
		// Find a seed whose panel has a wrong full set that is not a valid one: wire everything on the "wrong" side.
		MechanismState state = wiring.generate(new SplittableRandom(4), params(3, 3));
		List<String> scheme = scheme(state);
		List<String> right = right(state);
		// All three colours are distinct here, so any permutation other than the matching one is wrong.
		int[] wrong = new int[3];
		for (int i = 0; i < 3; i++) {
			wrong[i] = right.indexOf(scheme.get((i + 1) % 3));
		}
		MechanismState s = state;
		InputResult r = null;
		for (int i = 0; i < 3; i++) {
			r = wiring.onInput(s, 0, connect(i, wrong[i]));
			s = r.state();
		}
		assertFalse(r.solved());
		assertEquals("wrong", r.events().get(1).get("type").asString());
		assertEquals(1, s.data().get("wrong").asInt());
		assertEquals("[-1,-1,-1]", s.data().get("links").toString(), "a short circuit drops every wire (D39)");
		assertTrue(r.events().get(1).get("cleared").asBoolean());
		assertEquals("[-1,-1,-1]", wiring.view(s, ViewerKind.PLAYER).get("links").toString());
		// Starting over from the empty panel works.
		for (int i = 0; i < 3; i++) {
			r = wiring.onInput(s, 0, connect(i, right.indexOf(scheme.get(i))));
			s = r.state();
		}
		assertTrue(r.solved());
	}

	@Test
	void solvedStepIgnoresFurtherGestures() {
		MechanismState state = wiring.generate(new SplittableRandom(3), params(3, 3));
		List<String> scheme = scheme(state);
		List<String> right = right(state);
		MechanismState s = state;
		for (int i = 0; i < 3; i++) {
			s = wiring.onInput(s, 0, connect(i, right.indexOf(scheme.get(i)))).state();
		}
		InputResult again = wiring.onInput(s, 0, disconnect("left", 0));
		assertFalse(again.solved());
		assertTrue(again.events().isEmpty());
		assertEquals(s.data(), again.state().data());
	}

	@Test
	void onInputDoesNotModifyTheOldState() {
		MechanismState state = wiring.generate(new SplittableRandom(3), params(4, 3));
		String before = state.data().toString();
		wiring.onInput(state, 0, connect(0, 1));
		wiring.onInput(state, 0, disconnect("left", 0));
		assertEquals(before, state.data().toString());
	}

	@Test
	void inputsAreValidated() {
		MechanismState state = wiring.generate(new SplittableRandom(3), params(4, 3));
		assertThrows(IllegalArgumentException.class, () -> wiring.onInput(state, 0, null));
		assertThrows(IllegalArgumentException.class, () -> wiring.onInput(state, 0, Json.object()));
		assertThrows(IllegalArgumentException.class, () -> wiring.onInput(state, 0, connect(4, 0)));
		assertThrows(IllegalArgumentException.class, () -> wiring.onInput(state, 0, connect(0, -1)));
		ObjectNode noRight = Json.object();
		noRight.put("op", "connect");
		noRight.put("left", 0);
		assertThrows(IllegalArgumentException.class, () -> wiring.onInput(state, 0, noRight));
		assertThrows(IllegalArgumentException.class, () -> wiring.onInput(state, 0, disconnect("up", 0)));
	}

	@Test
	void validatesParamsAndWiring() {
		assertTrue(wiring.validateParams(Json.object()).isEmpty());
		assertTrue(wiring.validateParams(params(3, 2)).isEmpty());
		assertTrue(wiring.validateParams(params(6, 3)).isEmpty());
		assertFalse(wiring.validateParams(params(2, 3)).isEmpty());
		assertFalse(wiring.validateParams(params(7, 3)).isEmpty());
		assertFalse(wiring.validateParams(params(4, 1)).isEmpty());
		assertTrue(wiring.validateParams(params(4, 4)).isEmpty());
		assertTrue(wiring.validateParams(params(6, 5)).isEmpty());
		assertFalse(wiring.validateParams(params(4, 6)).isEmpty());
		assertFalse(wiring.validateParams(params(3, 4)).isEmpty(), "colors at most wires");
		Step ok = new Step("w", "wiring", null, Map.of("panel", "t"), List.of(), List.of(new Give("scheme", "wall", null)), List.of());
		assertTrue(wiring.validateStep(ok).isEmpty());
		Step late = new Step("w", "wiring", null, Map.of("panel", "t"), List.of(), List.of(new Give("scheme", "wall", "solved")), List.of());
		assertFalse(wiring.validateStep(late).isEmpty());
		Step none = new Step("w", "wiring", null, Map.of(), List.of(), List.of(), List.of());
		assertEquals(2, wiring.validateStep(none).size());
		// D37: the text document does as well as the colours clue, from the start.
		Step text = new Step("w", "wiring", null, Map.of("panel", "t"), List.of(), List.of(new Give("scheme_text", "desk", null)), List.of());
		assertTrue(wiring.validateStep(text).isEmpty());
		Step textLate = new Step("w", "wiring", null, Map.of("panel", "t"), List.of(), List.of(new Give("scheme_text", "desk", "solved")), List.of());
		assertFalse(wiring.validateStep(textLate).isEmpty());
	}

	@Test
	void theSchemeIsAlsoATextDocument() {
		for (long seed = 0; seed < 100; seed++) {
			MechanismState state = wiring.generate(new SplittableRandom(seed), params(5, 3));
			Clue doc = state.clues().get(1);
			assertEquals("scheme_text", doc.role());
			assertEquals("document", doc.type().wire());
			assertEquals(1, doc.value().size());
			String[] lines = doc.value().get(0).asString().split("\n");
			List<String> scheme = scheme(state);
			assertEquals(5, lines.length);
			for (int i = 0; i < 5; i++) {
				assertEquals((i + 1) + " : @color:" + scheme.get(i), lines[i]);
			}
		}
	}

	@Test
	void yellowAndWhiteAreWireColoursOnlyUsedFromFourColours() {
		Set<String> seen = new HashSet<>();
		for (long seed = 0; seed < 200; seed++) {
			for (int colors = 2; colors <= 3; colors++) {
				assertTrue(GAME.containsAll(new HashSet<>(scheme(wiring.generate(new SplittableRandom(seed), params(6, colors))))),
						"up to three colours: game colours only");
			}
			Set<String> four = new HashSet<>(scheme(wiring.generate(new SplittableRandom(seed), params(6, 4))));
			assertEquals(Set.of("red", "green", "blue", "yellow"), four);
			Set<String> five = new HashSet<>(scheme(wiring.generate(new SplittableRandom(seed), params(6, 5))));
			assertEquals(Set.of("red", "green", "blue", "yellow", "white"), five);
			seen.addAll(five);
		}
		assertTrue(seen.contains("yellow") && seen.contains("white"));
		assertEquals(List.of("red", "green", "blue"), Wiring.GAME_COLORS);
	}

	static final Set<String> GAME = Set.of("red", "green", "blue");

	@Test
	void difficultyAndTimeGrowWithTheWires() {
		assertEquals(1, wiring.difficulty(params(3, 3)));
		assertEquals(2, wiring.difficulty(Json.object()));
		assertEquals(3, wiring.difficulty(params(6, 3)));
		assertTrue(wiring.expectedSeconds(params(6, 3)) > wiring.expectedSeconds(params(3, 3)));
		assertEquals(List.of("scheme", "scheme_text"), wiring.clueRoles(Json.object()));
		assertEquals("panel", wiring.slotRoles().get(0).role());
	}
}
