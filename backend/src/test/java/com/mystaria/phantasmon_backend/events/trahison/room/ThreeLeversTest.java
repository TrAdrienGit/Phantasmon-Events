package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.RoomFixtures.TestClock;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.DebugButton;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ThreeLevers;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests of {@link ThreeLevers}: the lease, the order, the delay, the three distinct players. */
class ThreeLeversTest {

	final TestClock clock = new TestClock();
	final ThreeLevers levers = new ThreeLevers(clock);

	static ObjectNode params(boolean ordered, int gap, int hold, boolean solo) {
		ObjectNode p = Json.object();
		p.put("ordered", ordered);
		p.put("gap", gap);
		p.put("hold", hold);
		p.put("solo", solo);
		return p;
	}

	void advance(long ms) {
		clock.now = clock.now.plusMillis(ms);
	}

	InputResult pull(MechanismState state, int player, int lever) {
		return levers.onUse(state, player, "lever_" + lever);
	}

	/** The levers in the order the game master sees. */
	static int[] order(ThreeLevers m, MechanismState state) {
		JsonNode o = m.view(state, ViewerKind.GAME_MASTER).get("order");
		return new int[] { o.get(0).asInt(), o.get(1).asInt(), o.get(2).asInt() };
	}

	/** Three players, one per lever, in the drawn order, one second apart. */
	InputResult solve(MechanismState state) {
		InputResult r = InputResult.unchanged(state);
		int[] order = order(levers, state);
		for (int i = 0; i < 3; i++) {
			r = pull(r.state(), 10 + i, order[i]);
			advance(1000);
		}
		return r;
	}

	@Test
	void thousandRandomGenerationsAreSolvableByThreePlayers() {
		SplittableRandom pick = new SplittableRandom(11);
		for (long seed = 0; seed < 1000; seed++) {
			int gap = 2 + pick.nextInt(7);
			int hold = 2 * gap + pick.nextInt(31 - 2 * gap);
			MechanismState state = levers.generate(new SplittableRandom(seed), params(pick.nextBoolean(), gap, Math.min(30, hold), false));
			assertFalse(state.data().get("solved").asBoolean(), "never starts solved, seed " + seed);
			assertEquals(0, levers.view(state, ViewerKind.PLAYER).get("count").asInt());
			// Each engagement is spaced by the delay the seed drew (the longest allowed), the worst case for the lease.
			long drawn = levers.view(state, ViewerKind.GAME_MASTER).get("gapMs").asLong();
			InputResult r = InputResult.unchanged(state);
			int[] order = order(levers, state);
			for (int i = 0; i < 3; i++) {
				assertFalse(r.solved(), "seed " + seed);
				r = pull(r.state(), 5 + i, order[i]);
				advance(drawn);
			}
			assertTrue(r.solved(), "seed " + seed);
			assertEquals(3, levers.view(r.state(), ViewerKind.PLAYER).get("count").asInt());
		}
	}

	@Test
	void aSinglePlayerIsRefusedWhateverTheyTry() {
		SplittableRandom pick = new SplittableRandom(3);
		for (long seed = 0; seed < 300; seed++) {
			boolean ordered = seed % 2 == 0;
			MechanismState state = levers.generate(new SplittableRandom(seed), params(ordered, 5, 12, false));
			for (int i = 0; i < 60; i++) {
				InputResult r = pull(state, 7, 1 + pick.nextInt(3));
				assertFalse(r.solved(), "a lone player never solves it, seed " + seed);
				state = r.state();
				advance(pick.nextInt(1500));
			}
			assertTrue(levers.view(state, ViewerKind.PLAYER).get("count").asInt() <= 1);
		}
		// Two players are not enough either.
		MechanismState two = levers.generate(new SplittableRandom(1), params(false, 5, 12, false));
		for (int i = 0; i < 40; i++) {
			InputResult r = pull(two, i % 2, 1 + pick.nextInt(3));
			assertFalse(r.solved());
			two = r.state();
			advance(300);
		}
	}

	@Test
	void soloTrialLetsOnePlayerEngageAllThree() {
		MechanismState state = levers.generate(new SplittableRandom(2), params(true, 5, 30, true));
		int[] order = order(levers, state);
		InputResult r = InputResult.unchanged(state);
		for (int i = 0; i < 3; i++) {
			r = pull(r.state(), 0, order[i]);
			advance(9000); // slower than the delay: ignored in the trial
		}
		assertTrue(r.solved());
	}

	@Test
	void sameSeedSameDrawAndSeedsVary() {
		JsonNode p = params(true, 5, 12, false);
		assertEquals(levers.generate(new SplittableRandom(7), p).data(), levers.generate(new SplittableRandom(7), p).data());
		Set<String> orders = new HashSet<>();
		Set<Long> gaps = new HashSet<>();
		for (long seed = 0; seed < 100; seed++) {
			MechanismState s = levers.generate(new SplittableRandom(seed), p);
			orders.add(java.util.Arrays.toString(order(levers, s)));
			gaps.add(levers.view(s, ViewerKind.GAME_MASTER).get("gapMs").asLong());
		}
		assertEquals(6, orders.size(), "all the orders come up");
		assertEquals(Set.of(3000L, 4000L, 5000L), gaps);
		// Unordered: the order field is the identity, nothing to find out.
		MechanismState free = levers.generate(new SplittableRandom(7), params(false, 5, 12, false));
		assertEquals("[1, 2, 3]", java.util.Arrays.toString(order(levers, free)));
	}

	@Test
	void viewNeverHoldsTheOrderOrTheDelayExceptForTheGameMaster() {
		for (long seed = 0; seed < 200; seed++) {
			MechanismState state = levers.generate(new SplittableRandom(seed), params(true, 5, 12, false));
			int[] order = order(levers, state);
			InputResult r = pull(state, 0, order[0]);
			r = pull(r.state(), 1, order[0] == 1 ? 2 : 1); // may be a wrong try
			for (MechanismState s : List.of(state, r.state())) {
				for (ViewerKind kind : new ViewerKind[] { ViewerKind.PLAYER, ViewerKind.SPECTATOR }) {
					String text = Json.write(levers.view(s, kind));
					assertFalse(text.contains("order") || text.contains("gapMs") || text.contains("holdMs"), text);
				}
			}
			assertEquals("[levers, count, resets, solved, order, gapMs]", levers.view(state, ViewerKind.GAME_MASTER).propertyNames().toString());
		}
	}

	@Test
	void theLeaseLastsThenExpires() {
		MechanismState state = levers.generate(new SplittableRandom(4), params(false, 5, 12, false));
		InputResult r = pull(state, 0, 2);
		JsonNode v = levers.view(r.state(), ViewerKind.PLAYER);
		assertTrue(v.get("levers").get(1).get("on").asBoolean());
		assertEquals(12000, v.get("levers").get(1).get("ms").asInt());
		assertEquals(0, v.get("levers").get(0).get("ms").asInt());
		advance(11000);
		assertEquals(1, levers.view(r.state(), ViewerKind.PLAYER).get("count").asInt());
		assertEquals(1000, levers.view(r.state(), ViewerKind.PLAYER).get("levers").get(1).get("ms").asInt());
		// Clicking again renews it; another player's click on it does nothing.
		InputResult other = pull(r.state(), 1, 2);
		assertTrue(other.events().isEmpty());
		InputResult renew = pull(r.state(), 0, 2);
		assertEquals("hold", renew.events().get(0).get("type").asString());
		advance(3000);
		assertEquals(1, levers.view(renew.state(), ViewerKind.PLAYER).get("count").asInt());
		advance(10000);
		assertEquals(0, levers.view(renew.state(), ViewerKind.PLAYER).get("count").asInt());
	}

	@Test
	void aWrongOrderLetsEverythingGoAndCountsAReset() {
		MechanismState state = levers.generate(new SplittableRandom(9), params(true, 8, 20, false));
		int[] order = order(levers, state);
		InputResult first = pull(state, 0, order[0]);
		advance(500);
		InputResult wrong = pull(first.state(), 1, order[2]);
		assertEquals("wrong", wrong.events().get(0).get("type").asString());
		JsonNode v = levers.view(wrong.state(), ViewerKind.PLAYER);
		assertEquals(0, v.get("count").asInt());
		assertEquals(1, v.get("resets").asInt());
		// Starting with a lever that is not the first is wrong too, without anything engaged.
		InputResult wrongFirst = pull(wrong.state(), 2, order[1]);
		assertEquals("wrong", wrongFirst.events().get(0).get("type").asString());
		assertEquals(2, levers.view(wrongFirst.state(), ViewerKind.PLAYER).get("resets").asInt());
		// And the right order still works afterwards.
		assertTrue(solve(wrongFirst.state()).solved());
	}

	@Test
	void tooSlowLetsGoAndStartsAgain() {
		MechanismState state = levers.generate(new SplittableRandom(5), params(true, 2, 30, false));
		int[] order = order(levers, state);
		InputResult a = pull(state, 0, order[0]);
		advance(2500); // past any gap the seed can draw (2 at most)
		InputResult b = pull(a.state(), 1, order[1]);
		// The first lever was let go (slow), the second is not the first of the order: a wrong try after the slow one.
		assertEquals("slow", b.events().get(0).get("type").asString());
		assertEquals("wrong", b.events().get(1).get("type").asString());
		assertEquals(0, levers.view(b.state(), ViewerKind.PLAYER).get("count").asInt());
		// Unordered: too slow simply starts again with the lever just pulled.
		MechanismState free = levers.generate(new SplittableRandom(5), params(false, 2, 30, false));
		InputResult c = pull(free, 0, 1);
		advance(2500);
		InputResult d = pull(c.state(), 1, 2);
		assertEquals("slow", d.events().get(0).get("type").asString());
		assertEquals(1, levers.view(d.state(), ViewerKind.PLAYER).get("count").asInt());
		assertEquals(1, d.state().data().get("levers").get(1).get("by").asInt());
	}

	@Test
	void unorderedAcceptsAnyOrder() {
		for (int[] order : new int[][] { { 1, 2, 3 }, { 3, 2, 1 }, { 2, 3, 1 } }) {
			MechanismState state = levers.generate(new SplittableRandom(6), params(false, 5, 12, false));
			InputResult r = InputResult.unchanged(state);
			for (int i = 0; i < 3; i++) {
				r = pull(r.state(), i, order[i]);
				advance(1000);
			}
			assertTrue(r.solved());
		}
	}

	@Test
	void aPlayerWhoMovesToAnotherLeverLetsGoOfTheFirst() {
		MechanismState state = levers.generate(new SplittableRandom(6), params(false, 5, 12, false));
		InputResult r = pull(state, 0, 1);
		r = pull(r.state(), 1, 2);
		r = pull(r.state(), 0, 3); // player 0 leaves lever 1
		JsonNode v = levers.view(r.state(), ViewerKind.PLAYER);
		assertFalse(v.get("levers").get(0).get("on").asBoolean());
		assertEquals(2, v.get("count").asInt());
		assertFalse(r.solved());
	}

	@Test
	void gesturesAfterSolvingAreIgnoredAndBadOnesRefused() {
		MechanismState state = levers.generate(new SplittableRandom(8), params(true, 5, 12, false));
		InputResult done = solve(state);
		assertTrue(done.solved());
		assertTrue(pull(done.state(), 0, 1).events().isEmpty());
		assertTrue(levers.view(done.state(), ViewerKind.PLAYER).get("levers").get(0).get("on").asBoolean());
		assertTrue(levers.onUse(state, 0, "lever_9").events().isEmpty());
		ObjectNode bad = Json.object();
		bad.put("lever", 4);
		assertThrows(IllegalArgumentException.class, () -> levers.onInput(state, 0, bad));
		ObjectNode good = Json.object();
		good.put("lever", 2);
		MechanismState free = levers.generate(new SplittableRandom(8), params(false, 5, 12, false));
		assertEquals(0, levers.onInput(free, 0, good).state().data().get("levers").get(1).get("by").asInt());
	}

	@Test
	void parametersAreValidated() {
		assertEquals(List.of(), levers.validateParams(Json.object()));
		assertEquals(List.of(), levers.validateParams(params(true, 8, 16, false)));
		assertFalse(levers.validateParams(params(true, 9, 20, false)).isEmpty());
		assertFalse(levers.validateParams(params(true, 5, 4, false)).isEmpty());
		assertEquals(List.of("hold must be at least twice gap"), levers.validateParams(params(true, 8, 15, false)));
		ObjectNode bad = Json.object();
		bad.put("ordered", "yes");
		assertFalse(levers.validateParams(bad).isEmpty());
		assertEquals(3, levers.slotRoles().size());
		assertTrue(levers.slotRoles().stream().allMatch(r -> r.kinds().equals(List.of("lever"))));
		assertTrue(levers.difficulty(Json.object()) >= 1 && levers.expectedSeconds(Json.object()) > 0);
	}

	// ---------------------------------------------------------------- through the room service

	static RoomDefinition room() {
		return new RoomDefinition("levers_room", "Levers", "tester", 1, 3, 100,
				List.of(RoomFixtures.slot("l1", "lever", 1, 1, 1), RoomFixtures.slot("l2", "lever", 40, 1, 1),
						RoomFixtures.slot("l3", "lever", 40, 1, 40), RoomFixtures.slot("exit", "exit", 20, 0, 20)),
				List.of(new Step("pull", "three_levers", null, Map.of("lever_1", "l1", "lever_2", "l2", "lever_3", "l3"), List.of(),
								List.of(), List.of()),
						new Step("out", "debug_button", null, Map.of("exit", "exit"), List.of("pull"), List.of(), List.of())),
				null);
	}

	@Test
	void threeMembersOpenTheExitAndOneCannot() {
		MechanismRegistry registry = new MechanismRegistry(List.of(new DebugButton(), levers));
		assertEquals(List.of(), RoomValidator.validate(room(), registry::find));
		RoomService rooms = new RoomService(registry, clock);
		rooms.start("hub", 0, List.of(0, 1, 2), room(), 31L);
		// The levers show their puzzle as slots.
		JsonNode view = rooms.roomView("hub", 0, ViewerKind.PLAYER);
		int shown = 0;
		for (JsonNode slot : view.get("slots")) {
			if (slot.get("id").asString().startsWith("l")) {
				assertEquals("lever_" + slot.get("id").asString().substring(1), slot.get("shows").get(0).get("role").asString());
				shown++;
			}
		}
		assertEquals(3, shown);
		// Alone: the three levers, whatever the order, never solve.
		for (int i = 0; i < 12; i++) {
			assertFalse(rooms.apply("hub", 0, 0, new RoomAction.UseSlot("l" + (1 + i % 3))).stepSolved());
			advance(700);
		}
		// Three players in the drawn order.
		int[] order = order(levers, rooms.find("hub", 0).orElseThrow().steps.get("pull").state);
		advance(60000);
		ActionResult last = null;
		for (int i = 0; i < 3; i++) {
			last = rooms.apply("hub", 0, i, new RoomAction.UseSlot("l" + order[i]));
			advance(1000);
		}
		assertTrue(last.stepSolved());
	}
}
