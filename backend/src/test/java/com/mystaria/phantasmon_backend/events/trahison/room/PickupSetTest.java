package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Clue;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ClueType;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.PickupSet;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests of {@link PickupSet}. */
class PickupSetTest {

	final PickupSet set = new PickupSet();

	static ObjectNode params(int spots, int filled) {
		ObjectNode p = Json.object();
		p.put("spots", spots);
		p.put("filled", filled);
		return p;
	}

	static List<Clue> items(MechanismState state) {
		return state.clues().stream().filter(c -> c.type() == ClueType.ITEM).toList();
	}

	@Test
	void thousandRandomGenerationsAreSolvable() {
		SplittableRandom pick = new SplittableRandom(11);
		for (long seed = 0; seed < 1000; seed++) {
			int spots = 2 + pick.nextInt(5);
			int filled = 1 + pick.nextInt(spots);
			MechanismState state = set.generate(new SplittableRandom(seed), params(spots, filled));
			List<Clue> items = items(state);
			assertEquals(filled, items.size(), "seed " + seed);
			assertFalse(state.data().get("solved").asBoolean(), "never starts solved");
			Set<String> roles = new HashSet<>();
			Set<String> ids = new HashSet<>();
			for (Clue c : items) {
				assertTrue(c.role().matches("spot_[1-" + spots + "]"), c.role());
				assertTrue(set.clueRoles(params(spots, filled)).contains(c.role()));
				assertTrue(roles.add(c.role()), "one object per spot");
				assertTrue(ids.add(c.value().asString()), "distinct objects");
			}
			// Solve it from the clues alone, through the real engine: only the last pick up reports solved.
			MechanismState s = state;
			for (int i = 0; i < items.size(); i++) {
				Optional<InputResult> r = set.onPickUp(s, 0, items.get(i).role(), items.get(i).value().asString());
				assertTrue(r.isPresent(), "seed " + seed);
				assertEquals(i == items.size() - 1, r.get().solved(), "seed " + seed + " item " + i);
				s = r.get().state();
			}
			assertTrue(s.data().get("solved").asBoolean());
		}
	}

	@Test
	void sameSeedSameSpotsAndObjects() {
		JsonNode p = params(6, 3);
		MechanismState a = set.generate(new SplittableRandom(7), p);
		MechanismState b = set.generate(new SplittableRandom(7), p);
		assertEquals(a.data(), b.data());
		assertEquals(a.clues().stream().map(Clue::toJson).toList(), b.clues().stream().map(Clue::toJson).toList());
		Set<String> distinct = new HashSet<>();
		for (long seed = 0; seed < 50; seed++) {
			distinct.add(set.generate(new SplittableRandom(seed), p).data().toString());
		}
		assertTrue(distinct.size() > 40, "seeds fill other spots with other objects");
	}

	@Test
	void theSeedPicksWhichSpotsAreFilled() {
		Set<String> seen = new HashSet<>();
		for (long seed = 0; seed < 100; seed++) {
			for (Clue c : items(set.generate(new SplittableRandom(seed), params(6, 2)))) {
				seen.add(c.role());
			}
		}
		assertEquals(6, seen.size(), "every spot gets filled by some seed");
	}

	@Test
	void viewNeverHoldsTheHiddenObjects() {
		for (long seed = 0; seed < 200; seed++) {
			MechanismState state = set.generate(new SplittableRandom(seed), params(6, 4));
			for (ViewerKind kind : new ViewerKind[] { ViewerKind.PLAYER, ViewerKind.SPECTATOR }) {
				JsonNode view = set.view(state, kind);
				String json = Json.write(view);
				for (Clue c : items(state)) {
					assertFalse(json.contains("\"" + c.value().asString() + "\""), json);
				}
				assertFalse(json.contains("required") || json.contains("filled"), json);
				assertEquals(4, view.get("total").asInt());
				assertEquals(0, view.get("delivered").size());
			}
			JsonNode gm = set.view(state, ViewerKind.GAME_MASTER);
			assertEquals(4, gm.get("required").size());
			assertEquals(4, gm.get("filled").size());
			// A delivered object shows (the players handed it over), the others stay hidden.
			String first = items(state).get(0).value().asString();
			MechanismState after = set.onPickUp(state, 0, "spot_1", first).get().state();
			String json = Json.write(set.view(after, ViewerKind.PLAYER));
			assertTrue(json.contains("\"" + first + "\""));
			for (Clue c : items(state).subList(1, 4)) {
				assertFalse(json.contains("\"" + c.value().asString() + "\""));
			}
		}
	}

	@Test
	void anUnexpectedOrRepeatedObjectIsIgnored() {
		MechanismState state = set.generate(new SplittableRandom(4), params(6, 3));
		assertTrue(set.onPickUp(state, 0, "spot_1", "toaster").isEmpty());
		String first = items(state).get(0).value().asString();
		MechanismState after = set.onPickUp(state, 0, "spot_1", first).get().state();
		assertTrue(set.onPickUp(after, 0, "spot_1", first).isEmpty(), "picked up twice");
		assertEquals("collect", set.onPickUp(state, 0, "spot_1", first).get().events().get(0).get("type").asString());
	}

	@Test
	void theCounterForTheHudFollowsThePickUps() {
		MechanismState state = set.generate(new SplittableRandom(4), params(6, 3));
		assertEquals("[0, 3]", java.util.Arrays.toString(set.progress(state).orElseThrow()));
		Clue first = items(state).get(0);
		state = set.onPickUp(state, 0, first.role(), first.value().asString()).get().state();
		assertEquals("[1, 3]", java.util.Arrays.toString(set.progress(state).orElseThrow()));
	}

	@Test
	void solvedStepRefusesMore() {
		MechanismState s = set.generate(new SplittableRandom(4), params(6, 1));
		String only = items(s).get(0).value().asString();
		s = set.onPickUp(s, 0, "spot_1", only).get().state();
		assertTrue(s.data().get("solved").asBoolean());
		assertTrue(set.onPickUp(s, 0, "spot_1", only).isEmpty());
	}

	@Test
	void onPickUpDoesNotModifyTheOldState() {
		MechanismState state = set.generate(new SplittableRandom(3), params(6, 3));
		String before = state.data().toString();
		set.onPickUp(state, 0, "spot_1", items(state).get(0).value().asString());
		assertEquals(before, state.data().toString());
		assertTrue(set.onInput(state, 0, Json.object()).events().isEmpty());
		assertTrue(set.onItem(state, 0, "deposit", items(state).get(0).value().asString()).isEmpty(), "items are no longer taken anywhere");
	}

	@Test
	void aDocumentIsGivenAsAClue() {
		ObjectNode p = params(4, 2);
		p.put("document", "Page one\nline two\n---\nPage two\n@image:plan");
		assertTrue(set.validateParams(p).isEmpty());
		assertEquals(List.of("spot_1", "spot_2", "spot_3", "spot_4", "document"), set.clueRoles(p));
		assertEquals(List.of("spot_1", "spot_2", "spot_3", "spot_4", "document"), set.clueRoles(params(4, 2)));
		MechanismState state = set.generate(new SplittableRandom(1), p);
		Clue doc = state.clues().stream().filter(c -> c.type() == ClueType.DOCUMENT).findFirst().orElseThrow();
		assertEquals("document", doc.role());
		assertEquals(2, doc.value().size());
		assertEquals("Page one\nline two", doc.value().get(0).asString());
		assertEquals("Page two\n@image:plan", doc.value().get(1).asString());
		// The document is not part of what must be collected.
		assertEquals(2, set.view(state, ViewerKind.PLAYER).get("total").asInt());
		// Without a document param there is no document clue.
		assertEquals(0, set.generate(new SplittableRandom(1), params(4, 2)).clues().stream().filter(c -> c.type() == ClueType.DOCUMENT).count());
	}

	@Test
	void customPoolIsUsed() {
		ObjectNode p = params(3, 3);
		p.put("items", "alpha, beta ,gamma");
		assertTrue(set.validateParams(p).isEmpty());
		Set<String> got = new HashSet<>();
		items(set.generate(new SplittableRandom(9), p)).forEach(c -> got.add(c.value().asString()));
		assertEquals(Set.of("alpha", "beta", "gamma"), got);
	}

	@Test
	void validatesParamsAndWiring() {
		assertTrue(set.validateParams(Json.object()).isEmpty());
		assertFalse(set.validateParams(params(1, 1)).isEmpty());
		assertFalse(set.validateParams(params(7, 1)).isEmpty());
		assertFalse(set.validateParams(params(3, 0)).isEmpty());
		assertFalse(set.validateParams(params(3, 4)).isEmpty(), "filled at most spots");
		ObjectNode few = params(4, 3);
		few.put("items", "a,b");
		assertFalse(set.validateParams(few).isEmpty());
		ObjectNode bad = params(4, 2);
		bad.put("items", "ok,not ok!");
		assertFalse(set.validateParams(bad).isEmpty());
		// Wiring: every spot must be given from the start; no slot is needed (D32).
		List<Give> gives = new ArrayList<>();
		for (int i = 1; i <= 3; i++) {
			gives.add(new Give("spot_" + i, "p" + i, null));
		}
		Step ok = new Step("s", "pickup_set", params(3, 2), Map.of("spot_1", "box"), List.of(), gives, List.of());
		assertTrue(set.validateStep(ok).isEmpty());
		Step partial = new Step("s", "pickup_set", params(3, 2), Map.of("spot_1", "box"), List.of(), gives.subList(0, 2), List.of());
		assertEquals(List.of("missing=spot_3;when=open"), set.validateStep(partial));
		Step ghostDoc = new Step("s", "pickup_set", params(3, 2), Map.of("spot_1", "box"), List.of(),
				List.of(gives.get(0), gives.get(1), gives.get(2), new Give("document", "desk", null)), List.of());
		assertEquals(List.of("missing=param:document"), set.validateStep(ghostDoc));
		Step noSlot = new Step("s", "pickup_set", params(3, 2), Map.of(), List.of(), gives, List.of());
		assertTrue(set.validateStep(noSlot).isEmpty(), "picking up is enough: no deposit slot needed");
	}

	@Test
	void difficultyAndTimeGrowWithTheNumberOfObjects() {
		assertEquals(1, set.difficulty(params(6, 1)));
		assertEquals(2, set.difficulty(Json.object()));
		assertEquals(3, set.difficulty(params(6, 6)));
		assertTrue(set.expectedSeconds(params(6, 6)) > set.expectedSeconds(params(6, 1)));
		assertTrue(set.slotRoles().isEmpty(), "no slot to bring objects to (D32)");
	}
}
