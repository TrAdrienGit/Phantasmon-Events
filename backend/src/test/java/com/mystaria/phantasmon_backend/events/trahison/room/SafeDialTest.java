package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.SafeDial;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests of {@link SafeDial}: a solver that only reads the player view's cues opens every generated safe. */
class SafeDialTest {

	final SafeDial mech = new SafeDial();

	static ObjectNode params(int positions, int numbers, boolean wide) {
		ObjectNode p = Json.object();
		p.put("positions", positions);
		p.put("numbers", numbers);
		p.put("wide", wide);
		return p;
	}

	static ObjectNode turn(int delta) {
		ObjectNode in = Json.object();
		in.put("delta", delta);
		return in;
	}

	static ObjectNode lock() {
		ObjectNode in = Json.object();
		in.put("lock", true);
		return in;
	}

	/** Plays the safe with what a player sees: the cue after each tick and the way the next number wants to be reached. */
	@Test
	void thousandSafesOpenWithACueOnlySolver() {
		SplittableRandom pick = new SplittableRandom(41);
		for (long seed = 0; seed < 1000; seed++) {
			int positions = 24 + pick.nextInt(37);
			int numbers = 3 + pick.nextInt(3);
			MechanismState state = mech.generate(new SplittableRandom(seed), params(positions, numbers, pick.nextBoolean()));
			assertFalse(state.data().get("solved").asBoolean());
			MechanismState s = state;
			int ticks = 0;
			boolean solved = false;
			for (int found = 0; found < numbers && !solved; found++) {
				JsonNode view = mech.view(s, ViewerKind.PLAYER);
				assertEquals(found, view.get("stage").asInt());
				int dir = view.get("turn").asInt();
				boolean hit = false;
				for (int i = 0; i <= positions && !hit; i++) {
					InputResult r = mech.onInput(s, 0, turn(dir));
					s = r.state();
					ticks++;
					hit = "hit".equals(r.events().get(0).get("cue").asString());
				}
				assertTrue(hit, "a sweep finds the number, seed " + seed);
				InputResult r = mech.onInput(s, 0, lock());
				assertTrue(r.events().get(0).get("ok").asBoolean(), "seed " + seed);
				solved = r.solved();
				s = r.state();
			}
			assertTrue(solved && s.data().get("solved").asBoolean(), "seed " + seed);
			assertTrue(ticks <= numbers * (positions + 1));
		}
	}

	@Test
	void combinationNeverLeavesThePlayerView() {
		for (long seed = 0; seed < 100; seed++) {
			MechanismState state = mech.generate(new SplittableRandom(seed), params(40, 4, true));
			for (ViewerKind kind : new ViewerKind[] { ViewerKind.PLAYER, ViewerKind.SPECTATOR }) {
				String json = Json.write(mech.view(state, kind));
				assertFalse(json.contains("combination"), json);
			}
			assertTrue(mech.view(state, ViewerKind.GAME_MASTER).has("combination"));
			assertTrue(mech.clueRoles(Json.object()).isEmpty());
		}
	}

	@Test
	void cueBandsFollowTheDistance() {
		MechanismState state = mech.generate(new SplittableRandom(1), params(60, 3, true));
		int target = mech.view(state, ViewerKind.GAME_MASTER).get("combination").get(0).asInt();
		assertEquals("hit", SafeDial.cue(60, true, target, target));
		assertEquals("near", SafeDial.cue(60, true, (target + 2) % 60, target));
		assertEquals("mid", SafeDial.cue(60, true, (target + 8) % 60, target));
		assertEquals("far", SafeDial.cue(60, true, (target + 30) % 60, target));
		assertEquals("far", SafeDial.cue(60, false, (target + 8) % 60, target));
		assertEquals("near", SafeDial.cue(60, false, Math.floorMod(target - 1, 60), target));
	}

	@Test
	void wrongLockOrWrongWayStartsOver() {
		SplittableRandom rng = new SplittableRandom(7);
		MechanismState state = mech.generate(rng, params(40, 3, true));
		int[] combination = new int[3];
		for (int i = 0; i < 3; i++) {
			combination[i] = mech.view(state, ViewerKind.GAME_MASTER).get("combination").get(i).asInt();
		}
		// A lock anywhere but on the number resets (and counts).
		InputResult off = mech.onInput(state, 0, lock());
		assertFalse(off.events().get(0).get("ok").asBoolean());
		assertEquals(1, off.state().data().get("resets").asInt());
		// Reach number 0 the wrong way (counter-clockwise) and lock: refused although on the stop.
		MechanismState s = state;
		int position = state.data().get("position").asInt();
		while (position != combination[0]) {
			s = mech.onInput(s, 0, turn(-1)).state();
			position = s.data().get("position").asInt();
		}
		assertFalse(mech.onInput(s, 0, lock()).events().get(0).get("ok").asBoolean());
		// Then the right way: one full tick away and back clockwise.
		s = mech.onInput(s, 0, turn(-1)).state();
		s = mech.onInput(s, 0, turn(1)).state();
		InputResult ok = mech.onInput(s, 0, lock());
		assertTrue(ok.events().get(0).get("ok").asBoolean());
		assertEquals(1, ok.state().data().get("stage").asInt());
		// After a good lock the dial must be turned again before the next lock.
		assertEquals(0, ok.state().data().get("lastDir").asInt());
		assertFalse(mech.onInput(ok.state(), 0, lock()).events().get(0).get("ok").asBoolean());
	}

	@Test
	void inputsAreValidatedAndSolvedIgnoresGestures() {
		MechanismState state = mech.generate(new SplittableRandom(3), params(40, 3, true));
		String before = state.data().toString();
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, null));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, Json.object()));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, turn(0)));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, turn(2)));
		ObjectNode both = turn(1);
		both.put("lock", true);
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, both));
		mech.onInput(state, 0, turn(1));
		assertEquals(before, state.data().toString());
		ObjectNode done = state.copyData();
		done.put("solved", true);
		InputResult again = mech.onInput(state.withData(done), 0, turn(1));
		assertTrue(again.events().isEmpty() && !again.solved());
	}

	@Test
	void sameSeedSameSafeAndParamsAreChecked() {
		assertEquals(mech.generate(new SplittableRandom(2), params(50, 5, false)).data(),
				mech.generate(new SplittableRandom(2), params(50, 5, false)).data());
		assertTrue(mech.validateParams(Json.object()).isEmpty());
		assertFalse(mech.validateParams(params(23, 3, true)).isEmpty());
		assertFalse(mech.validateParams(params(40, 6, true)).isEmpty());
		ObjectNode bad = params(40, 3, true);
		bad.put("wide", "yes");
		assertFalse(mech.validateParams(bad).isEmpty());
		assertTrue(mech.difficulty(params(60, 5, false)) > mech.difficulty(params(24, 3, true)));
		assertTrue(mech.expectedSeconds(params(60, 5, false)) > mech.expectedSeconds(params(24, 3, true)));
	}
}
