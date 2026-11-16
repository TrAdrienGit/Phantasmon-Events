package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ThreeDials;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeDialsTest {

	final ThreeDials dials = new ThreeDials();

	@Test
	void thousandSeedsAreSolvableAndNeverStartSolved() {
		for (long seed = 0; seed < 1000; seed++) {
			MechanismState state = dials.generate(new SplittableRandom(seed), Json.object());
			assertEquals(1, state.clues().size());
			String code = state.clues().get(0).value().asString();
			assertEquals(3, code.length());
			assertFalse(state.data().get("solved").asBoolean(), "never starts solved");
			InputResult last = null;
			for (int d = 0; d < 3; d++) {
				int have = state.data().get("dials").get(d).asInt();
				int want = code.charAt(d) - '0';
				if (have != want) {
					ObjectNode in = Json.object();
					in.put("dial", d);
					in.put("delta", Math.floorMod(want - have, 10));
					last = dials.onInput(state, 0, in);
					state = last.state();
				}
			}
			assertTrue(last.solved(), "seed " + seed);
			assertTrue(state.data().get("solved").asBoolean());
		}
	}

	@Test
	void onInputDoesNotModifyTheOldState() {
		MechanismState state = dials.generate(new SplittableRandom(3), Json.object());
		String before = state.data().toString();
		ObjectNode in = Json.object();
		in.put("dial", 0);
		in.put("delta", 1);
		dials.onInput(state, 0, in);
		assertEquals(before, state.data().toString());
	}

	@Test
	void playerViewHasOnlyThePositions() {
		MechanismState state = dials.generate(new SplittableRandom(3), Json.object());
		assertEquals("[dials, solved]", dials.view(state, ViewerKind.PLAYER).propertyNames().toString());
		assertTrue(dials.view(state, ViewerKind.GAME_MASTER).has("target"));
	}
}
