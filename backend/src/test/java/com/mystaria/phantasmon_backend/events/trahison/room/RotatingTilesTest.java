package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayDeque;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.RotatingTiles;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests of {@link RotatingTiles}: the circuit rules are rebuilt here from the view, not taken from the mechanism. */
class RotatingTilesTest {

	final RotatingTiles mech = new RotatingTiles();

	static ObjectNode params(int size, int fixed) {
		ObjectNode p = Json.object();
		p.put("size", size);
		p.put("fixed", fixed);
		return p;
	}

	static ObjectNode gesture(int cell, int delta) {
		ObjectNode in = Json.object();
		in.put("cell", cell);
		in.put("delta", delta);
		return in;
	}

	/** Branches of a tile as booleans {N, E, S, W}, rotated clockwise. */
	static boolean[] branches(String type, int rot) {
		boolean[] base = switch (type) {
			case "straight" -> new boolean[] { true, false, true, false };
			case "corner" -> new boolean[] { true, true, false, false };
			case "tee" -> new boolean[] { true, true, true, false };
			case "end" -> new boolean[] { true, false, false, false };
			default -> new boolean[4];
		};
		boolean[] out = new boolean[4];
		for (int s = 0; s < 4; s++) {
			out[(s + rot) % 4] = base[s];
		}
		return out;
	}

	/** Is the lamp lit for these rotations: flood fill from the left edge of the source row. */
	static boolean lit(JsonNode view, int[] rots) {
		int n = view.get("size").asInt();
		int source = view.get("source").asInt();
		int sink = view.get("sink").asInt();
		int[] dr = { -1, 0, 1, 0 };
		int[] dc = { 0, 1, 0, -1 };
		boolean[] seen = new boolean[n * n];
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		if (branches(view.get("types").get(source * n).asString(), rots[source * n])[3]) {
			seen[source * n] = true;
			queue.add(source * n);
		}
		while (!queue.isEmpty()) {
			int cell = queue.poll();
			boolean[] b = branches(view.get("types").get(cell).asString(), rots[cell]);
			for (int s = 0; s < 4; s++) {
				int r = cell / n + dr[s];
				int c = cell % n + dc[s];
				if (b[s] && r >= 0 && c >= 0 && r < n && c < n && !seen[r * n + c]
						&& branches(view.get("types").get(r * n + c).asString(), rots[r * n + c])[(s + 2) % 4]) {
					seen[r * n + c] = true;
					queue.add(r * n + c);
				}
			}
		}
		int lamp = sink * n + n - 1;
		return seen[lamp] && branches(view.get("types").get(lamp).asString(), rots[lamp])[1];
	}

	static int[] rots(JsonNode array) {
		int[] out = new int[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).asInt();
		}
		return out;
	}

	@Test
	void thousandRandomGenerationsAreSolvableAndNeverStartSolved() {
		SplittableRandom pick = new SplittableRandom(17);
		for (long seed = 0; seed < 1000; seed++) {
			int size = 3 + pick.nextInt(3);
			int fixed = pick.nextInt(7);
			MechanismState state = mech.generate(new SplittableRandom(seed), params(size, fixed));
			JsonNode gm = mech.view(state, ViewerKind.GAME_MASTER);
			int[] start = rots(gm.get("rots"));
			int[] solution = rots(gm.get("solution"));
			assertFalse(state.data().get("solved").asBoolean());
			assertFalse(lit(gm, start), "never starts lit, seed " + seed);
			assertTrue(lit(gm, solution), "the solution lights the lamp, seed " + seed);
			// Fixed tiles already sit on their solution.
			for (int i = 0; i < start.length; i++) {
				if (gm.get("fixed").get(i).asBoolean()) {
					assertEquals(solution[i], start[i], "fixed tile in place, seed " + seed);
				}
			}
			// Replay: clicks from the start to the solution, through the real engine.
			MechanismState s = state;
			boolean solved = false;
			for (int cell = 0; cell < start.length && !solved; cell++) {
				for (int k = Math.floorMod(solution[cell] - start[cell], 4); k > 0 && !solved; k--) {
					InputResult r = mech.onInput(s, 0, gesture(cell, 1));
					s = r.state();
					solved = r.solved();
					assertEquals(solved, lit(mech.view(s, ViewerKind.PLAYER), rots(s.data().get("rots"))), "seed " + seed);
				}
			}
			assertTrue(solved && s.data().get("solved").asBoolean(), "seed " + seed);
		}
	}

	@Test
	void viewShowsPoweredCellsNeverTheSolution() {
		for (long seed = 0; seed < 100; seed++) {
			MechanismState state = mech.generate(new SplittableRandom(seed), params(4, 2));
			JsonNode view = mech.view(state, ViewerKind.PLAYER);
			assertFalse(Json.write(view).contains("solution"));
			assertEquals(16, view.get("powered").size());
			assertTrue(mech.view(state, ViewerKind.GAME_MASTER).has("solution"));
			assertTrue(mech.clueRoles(Json.object()).isEmpty());
		}
	}

	@Test
	void turningBackUndoesAndOldStateIsKept() {
		MechanismState state = mech.generate(new SplittableRandom(3), params(4, 0));
		String before = state.data().toString();
		MechanismState turned = mech.onInput(state, 0, gesture(5, 1)).state();
		assertEquals(before, state.data().toString());
		assertEquals(state.data().get("rots"), mech.onInput(turned, 0, gesture(5, -1)).state().data().get("rots"));
		assertEquals("turn", mech.onInput(state, 0, gesture(5, 1)).events().get(0).get("type").asString());
	}

	@Test
	void fixedTilesRefuseAndInputsAreValidated() {
		MechanismState state = mech.generate(new SplittableRandom(6), params(4, 3));
		int fixedCell = -1;
		for (int i = 0; i < 16; i++) {
			if (state.data().get("fixed").get(i).asBoolean()) {
				fixedCell = i;
			}
		}
		assertTrue(fixedCell >= 0);
		final int cell = fixedCell;
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, gesture(cell, 1)));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, null));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, Json.object()));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, gesture(16, 1)));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, gesture(-1, 1)));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, gesture(0, 2)));
	}

	@Test
	void solvedStepIgnoresFurtherGestures() {
		MechanismState state = mech.generate(new SplittableRandom(8), params(3, 0));
		int[] start = rots(state.data().get("rots"));
		int[] solution = rots(state.data().get("solution"));
		MechanismState s = state;
		for (int cell = 0; cell < 9 && !s.data().get("solved").asBoolean(); cell++) {
			for (int k = Math.floorMod(solution[cell] - start[cell], 4); k > 0 && !s.data().get("solved").asBoolean(); k--) {
				s = mech.onInput(s, 0, gesture(cell, 1)).state();
			}
		}
		assertTrue(s.data().get("solved").asBoolean());
		InputResult again = mech.onInput(s, 0, gesture(0, 1));
		assertFalse(again.solved());
		assertTrue(again.events().isEmpty());
	}

	@Test
	void sameSeedSameBoardAndParamsAreChecked() {
		assertEquals(mech.generate(new SplittableRandom(2), params(5, 2)).data(), mech.generate(new SplittableRandom(2), params(5, 2)).data());
		assertTrue(mech.validateParams(Json.object()).isEmpty());
		assertFalse(mech.validateParams(params(2, 0)).isEmpty());
		assertFalse(mech.validateParams(params(6, 0)).isEmpty());
		assertFalse(mech.validateParams(params(4, 7)).isEmpty());
		assertTrue(mech.difficulty(params(5, 0)) > mech.difficulty(params(3, 0)));
		assertTrue(mech.expectedSeconds(params(5, 0)) > mech.expectedSeconds(params(3, 0)));
		assertEquals(List.of("terminal"), mech.slotRoles().stream().map(r -> r.role()).toList());
	}
}
