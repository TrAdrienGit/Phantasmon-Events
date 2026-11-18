package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.SymbolSet;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.TwinScreens;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests of {@link TwinScreens}: the two-player protocol, who may act, and what the views hold. */
class TwinScreensTest {

	final TwinScreens mech = new TwinScreens();

	static ObjectNode params(int length, int alphabet) {
		ObjectNode p = Json.object();
		p.put("length", length);
		p.put("alphabet", alphabet);
		return p;
	}

	static ObjectNode entry(String seat, List<String> symbols) {
		ObjectNode in = Json.object();
		in.put("seat", seat);
		ArrayNode array = in.putArray("symbols");
		symbols.forEach(array::add);
		return in;
	}

	static List<String> list(JsonNode array) {
		List<String> out = new ArrayList<>();
		array.forEach(n -> out.add(n.asString()));
		return out;
	}

	@Test
	void thousandGenerationsAreSolvableByTwoPlayers() {
		SplittableRandom pick = new SplittableRandom(23);
		for (long seed = 0; seed < 1000; seed++) {
			int length = 3 + pick.nextInt(4);
			int alphabet = Math.max(length, 6) + pick.nextInt(11 - Math.max(0, length - 6));
			MechanismState state = mech.generate(new SplittableRandom(seed), params(length, Math.min(16, alphabet)));
			JsonNode gm = mech.view(state, ViewerKind.GAME_MASTER);
			List<String> left = list(gm.get("left_code"));
			List<String> right = list(gm.get("right_code"));
			List<String> alpha = list(gm.get("alphabet"));
			assertEquals(length, left.size());
			assertEquals(length, new HashSet<>(left).size(), "distinct glyphs, seed " + seed);
			assertEquals(length, new HashSet<>(right).size());
			assertNotEquals(left, right);
			assertTrue(alpha.containsAll(left) && alpha.containsAll(right) && SymbolSet.IDS.containsAll(alpha));
			// The clues are the two halves, and nothing else.
			assertEquals(2, state.clues().size());
			assertEquals("left_code", state.clues().get(0).role());
			assertEquals(left, list(state.clues().get(0).value()));
			assertEquals(right, list(state.clues().get(1).value()));
			// Player 4 takes the left terminal, player 7 the right one; each types what the other terminal shows.
			MechanismState s = mech.onUse(state, 4, "left").state();
			s = mech.onUse(s, 7, "right").state();
			InputResult first = mech.onInput(s, 4, entry("left", right));
			assertFalse(first.solved());
			assertEquals("latch", first.events().get(0).get("type").asString());
			InputResult second = mech.onInput(first.state(), 7, entry("right", left));
			assertTrue(second.solved(), "seed " + seed);
			assertTrue(second.state().data().get("solved").asBoolean());
		}
	}

	@Test
	void playerViewNeverHoldsAHalf() {
		MechanismState state = mech.generate(new SplittableRandom(5), params(4, 10));
		for (ViewerKind kind : new ViewerKind[] { ViewerKind.PLAYER, ViewerKind.SPECTATOR }) {
			JsonNode view = mech.view(state, kind);
			String json = Json.write(view);
			assertFalse(json.contains("left_code") || json.contains("right_code"), json);
			assertEquals("[alphabet, length, seats, latched, wrong, solved]", view.propertyNames().toString());
		}
		assertTrue(mech.view(state, ViewerKind.GAME_MASTER).has("left_code"));
		assertEquals(List.of("left_code", "right_code"), mech.clueRoles(Json.object()));
		assertEquals(List.of("left", "right"), mech.slotRoles().stream().map(r -> r.role()).toList());
	}

	@Test
	void aWrongEntryOnlyCounts() {
		MechanismState state = mech.generate(new SplittableRandom(6), params(4, 10));
		MechanismState s = mech.onUse(state, 1, "left").state();
		List<String> wrong = list(s.data().get("alphabet")).subList(0, 4);
		List<String> right = list(s.data().get("right_code"));
		if (wrong.equals(right)) {
			wrong = new ArrayList<>(List.of(wrong.get(1), wrong.get(0), wrong.get(2), wrong.get(3)));
		}
		InputResult r = mech.onInput(s, 1, entry("left", wrong));
		assertEquals("wrong", r.events().get(0).get("type").asString());
		assertEquals(1, r.state().data().get("wrong").asInt());
		assertFalse(r.state().data().get("latched").get("left").asBoolean());
		// Typing the half the left terminal itself shows is wrong too: it is the other terminal's job.
		InputResult own = mech.onInput(s, 1, entry("left", list(s.data().get("left_code"))));
		assertEquals("wrong", own.events().get(0).get("type").asString());
	}

	@Test
	void onePlayerCannotHoldBothTerminals() {
		MechanismState state = mech.generate(new SplittableRandom(8), params(4, 10));
		MechanismState s = mech.onUse(state, 2, "left").state();
		InputResult refused = mech.onUse(s, 2, "right");
		assertEquals("seat_refused", refused.events().get(0).get("type").asString());
		assertTrue(refused.state().data().get("seats").get("right").asInt() < 0);
		// Even through a latched seat: player 2 latches left, then cannot latch right, whoever holds it.
		MechanismState s2 = mech.onInput(s, 2, entry("left", list(s.data().get("right_code")))).state();
		assertEquals("seat_refused", mech.onUse(s2, 2, "right").events().get(0).get("type").asString());
		MechanismState s3 = mech.onUse(s2, 3, "right").state();
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(s3, 2, entry("right", list(s3.data().get("left_code")))));
		assertTrue(mech.onInput(s3, 3, entry("right", list(s3.data().get("left_code")))).solved());
	}

	@Test
	void onlyTheSeatHolderMayEnterAndInputsAreValidated() {
		MechanismState state = mech.generate(new SplittableRandom(9), params(4, 10));
		List<String> alpha = list(state.data().get("alphabet"));
		List<String> four = alpha.subList(0, 4);
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 1, entry("left", four)), "no seat taken");
		MechanismState s = mech.onUse(state, 1, "left").state();
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(s, 2, entry("left", four)), "another holder");
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(s, 1, null));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(s, 1, Json.object()));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(s, 1, entry("middle", four)));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(s, 1, entry("left", four.subList(0, 3))));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(s, 1, entry("left", List.of("a", "b", "c", "d"))));
		// The last player to use a free seat holds it.
		MechanismState taken = mech.onUse(s, 2, "left").state();
		assertEquals(2, taken.data().get("seats").get("left").asInt());
		assertEquals(2, mech.view(taken, ViewerKind.PLAYER).get("seats").get("left").asInt());
		assertTrue(mech.view(taken, ViewerKind.PLAYER).get("seats").get("right").isNull());
	}

	@Test
	void solvedAndLatchedAreStable() {
		MechanismState state = mech.generate(new SplittableRandom(10), params(3, 6));
		MechanismState s = mech.onUse(mech.onUse(state, 0, "left").state(), 1, "right").state();
		s = mech.onInput(s, 0, entry("left", list(s.data().get("right_code")))).state();
		InputResult again = mech.onInput(s, 0, entry("left", list(s.data().get("left_code"))));
		assertTrue(again.events().isEmpty());
		assertEquals(0, again.state().data().get("wrong").asInt());
		s = mech.onInput(s, 1, entry("right", list(s.data().get("left_code")))).state();
		assertTrue(mech.onInput(s, 1, entry("right", list(s.data().get("left_code")))).events().isEmpty());
		assertEquals("seat_refused", mech.onUse(s, 5, "left").events().get(0).get("type").asString());
	}

	@Test
	void sameSeedSameHalvesAndParamsAreChecked() {
		assertEquals(mech.generate(new SplittableRandom(2), params(5, 12)).data(), mech.generate(new SplittableRandom(2), params(5, 12)).data());
		assertTrue(mech.validateParams(Json.object()).isEmpty());
		assertFalse(mech.validateParams(params(2, 10)).isEmpty());
		assertFalse(mech.validateParams(params(7, 10)).isEmpty());
		assertFalse(mech.validateParams(params(4, 17)).isEmpty());
		assertTrue(mech.difficulty(params(6, 16)) > mech.difficulty(params(3, 6)));
		assertTrue(mech.expectedSeconds(params(6, 16)) > mech.expectedSeconds(params(3, 6)));
	}

	@Test
	void theStepNeedsTwoTerminalsEachShowingItsOwnHalf() {
		var give = new com.mystaria.phantasmon_backend.events.trahison.room.Give("left_code", "t1", null);
		var give2 = new com.mystaria.phantasmon_backend.events.trahison.room.Give("right_code", "t2", null);
		var ok = new com.mystaria.phantasmon_backend.events.trahison.room.Step("s", "twin_screens", null,
				Map.of("left", "t1", "right", "t2"), List.of(), List.of(give, give2), List.of());
		assertTrue(mech.validateStep(ok).isEmpty());
		var swapped = new com.mystaria.phantasmon_backend.events.trahison.room.Step("s", "twin_screens", null,
				Map.of("left", "t2", "right", "t1"), List.of(), List.of(give, give2), List.of());
		assertEquals(2, mech.validateStep(swapped).size());
		var same = new com.mystaria.phantasmon_backend.events.trahison.room.Step("s", "twin_screens", null,
				Map.of("left", "t1", "right", "t1"), List.of(), List.of(give, give2), List.of());
		assertTrue(mech.validateStep(same).contains("same_slot=left,right"));
		var none = new com.mystaria.phantasmon_backend.events.trahison.room.Step("s", "twin_screens", null, Map.of(), List.of(), List.of(), List.of());
		assertTrue(mech.validateStep(none).contains("missing=slot:left"));
	}
}
