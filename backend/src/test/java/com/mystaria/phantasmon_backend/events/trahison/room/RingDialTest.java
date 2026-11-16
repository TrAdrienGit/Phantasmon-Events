package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.RingDial;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.SymbolSet;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link RingDial}. The solvability check is independent of the mechanism's own move code: it rebuilds the
 * rules from the game master's view (symbols, links, target) and searches the whole state space breadth first, then
 * replays the shortest solution through {@code onInput} to prove that the engine and the model agree.
 */
class RingDialTest {

	final RingDial dial = new RingDial();

	static ObjectNode params(int rings, int symbols, int links) {
		ObjectNode p = Json.object();
		p.put("rings", rings);
		p.put("symbols", symbols);
		p.put("links", links);
		return p;
	}

	static ObjectNode gesture(int ring, int delta) {
		ObjectNode in = Json.object();
		in.put("ring", ring);
		in.put("delta", delta);
		return in;
	}

	/** The rules as the players see them, read from the game master's view only. */
	record Model(int[] sizes, int[][] links, int[] solved, int[] start) {

		static Model of(RingDial d, MechanismState s) {
			JsonNode v = d.view(s, ViewerKind.GAME_MASTER);
			int rings = v.get("symbols").size();
			int[] sizes = new int[rings];
			int[] solved = new int[rings];
			int[] start = new int[rings];
			for (int r = 0; r < rings; r++) {
				sizes[r] = v.get("symbols").get(r).size();
				int slot = -1;
				for (int k = 0; k < sizes[r]; k++) {
					if (v.get("symbols").get(r).get(k).asString().equals(v.get("target").get(r).asString())) {
						slot = k;
					}
				}
				solved[r] = Math.floorMod(-slot, sizes[r]);
				start[r] = v.get("offsets").get(r).asInt();
			}
			int[][] links = new int[v.get("links").size()][];
			for (int i = 0; i < links.length; i++) {
				JsonNode l = v.get("links").get(i);
				links[i] = new int[] { l.get("from").asInt(), l.get("to").asInt(), l.get("steps").asInt() };
			}
			return new Model(sizes, links, solved, start);
		}

		int[] apply(int[] offsets, int ring, int delta) {
			int[] next = offsets.clone();
			next[ring] = Math.floorMod(next[ring] + delta, sizes[ring]);
			for (int[] l : links) {
				if (l[0] == ring) {
					next[l[1]] = Math.floorMod(next[l[1]] + delta * l[2], sizes[l[1]]);
				}
			}
			return next;
		}

		long encode(int[] o) {
			long code = 0;
			for (int i = 0; i < o.length; i++) {
				code = code * sizes[i] + o[i];
			}
			return code;
		}

		/** Shortest list of {ring, delta} from the start to the solved offsets, or null when unreachable. */
		List<int[]> shortestSolution() {
			record Node(int[] offsets, Node from, int ring, int delta) {
			}
			ArrayDeque<Node> queue = new ArrayDeque<>();
			Set<Long> seen = new HashSet<>();
			queue.add(new Node(start, null, -1, 0));
			seen.add(encode(start));
			while (!queue.isEmpty()) {
				Node n = queue.poll();
				if (java.util.Arrays.equals(n.offsets, solved)) {
					List<int[]> path = new ArrayList<>();
					for (Node x = n; x.from != null; x = x.from) {
						path.add(0, new int[] { x.ring, x.delta });
					}
					return path;
				}
				for (int r = 0; r < sizes.length; r++) {
					for (int d = -1; d <= 1; d += 2) {
						int[] next = apply(n.offsets, r, d);
						if (seen.add(encode(next))) {
							queue.add(new Node(next, n, r, d));
						}
					}
				}
			}
			return null;
		}
	}

	@Test
	void thousandRandomGenerationsAreSolvableAndNeverStartSolved() {
		SplittableRandom pick = new SplittableRandom(99);
		for (long seed = 0; seed < 1000; seed++) {
			int rings = 3 + pick.nextInt(3);
			int symbols = 6 + pick.nextInt(7);
			int links = pick.nextInt(4);
			MechanismState state = dial.generate(new SplittableRandom(seed), params(rings, symbols, links));
			assertFalse(state.data().get("solved").asBoolean(), "never starts solved, seed " + seed);
			Model model = Model.of(dial, state);
			assertFalse(java.util.Arrays.equals(model.start, model.solved), "never starts on the target, seed " + seed);
			assertEquals(links, model.links.length);
			// Exhaustive search is cheap up to 12^5 states, so it runs on every generation.
			List<int[]> solution = model.shortestSolution();
			assertTrue(solution != null && !solution.isEmpty(), "solvable, seed " + seed);
			// Replay through the real engine: the last gesture must report solved, no earlier one.
			MechanismState s = state;
			for (int i = 0; i < solution.size(); i++) {
				InputResult r = dial.onInput(s, 0, gesture(solution.get(i)[0], solution.get(i)[1]));
				assertEquals(i == solution.size() - 1, r.solved(), "seed " + seed + " step " + i);
				s = r.state();
			}
			assertTrue(s.data().get("solved").asBoolean());
		}
	}

	@Test
	void scrambleLeavesAtLeastTwoRingsOffTarget() {
		for (long seed = 0; seed < 300; seed++) {
			MechanismState state = dial.generate(new SplittableRandom(seed), params(4, 8, 2));
			Model model = Model.of(dial, state);
			int off = 0;
			for (int r = 0; r < model.sizes.length; r++) {
				off += model.start[r] != model.solved[r] ? 1 : 0;
			}
			assertTrue(off >= 2, "seed " + seed);
		}
	}

	@Test
	void sameSeedSameResultAndOtherSeedsDiffer() {
		JsonNode p = params(4, 9, 3);
		MechanismState a = dial.generate(new SplittableRandom(7), p);
		MechanismState b = dial.generate(new SplittableRandom(7), p);
		assertEquals(a.data(), b.data());
		assertEquals(a.clues().get(0).toJson(), b.clues().get(0).toJson());
		Set<String> distinct = new HashSet<>();
		for (long seed = 0; seed < 50; seed++) {
			distinct.add(dial.generate(new SplittableRandom(seed), p).data().toString());
		}
		assertTrue(distinct.size() > 45, "seeds give different puzzles");
	}

	@Test
	void clueIsTheTargetSequenceOfKnownGlyphs() {
		MechanismState state = dial.generate(new SplittableRandom(5), params(5, 12, 3));
		assertEquals(1, state.clues().size());
		var clue = state.clues().get(0);
		assertEquals("symbols", clue.role());
		assertEquals("symbols", clue.type().wire());
		assertEquals(5, clue.value().size());
		for (JsonNode id : clue.value()) {
			assertTrue(SymbolSet.IDS.contains(id.asString()), id.asString());
		}
		assertEquals(List.of("symbols"), dial.clueRoles(Json.object()));
	}

	@Test
	void ringsHoldDistinctGlyphsOfTheSharedSet() {
		assertTrue(SymbolSet.IDS.size() >= 16);
		assertEquals(SymbolSet.IDS.size(), new HashSet<>(SymbolSet.IDS).size());
		MechanismState state = dial.generate(new SplittableRandom(1), params(5, 12, 0));
		JsonNode symbols = state.data().get("symbols");
		int[] expected = { 12, 11, 10, 9, 8 };
		for (int r = 0; r < 5; r++) {
			assertEquals(expected[r], symbols.get(r).size());
			Set<String> ids = new HashSet<>();
			symbols.get(r).forEach(n -> ids.add(n.asString()));
			assertEquals(expected[r], ids.size(), "distinct in ring " + r);
			assertTrue(SymbolSet.IDS.containsAll(ids));
		}
		// Small rings never go under 6 symbols.
		assertEquals(6, dial.generate(new SplittableRandom(1), params(5, 6, 0)).data().get("symbols").get(4).size());
	}

	@Test
	void playerViewNeverHoldsTheTargetOrTheSolution() {
		for (long seed = 0; seed < 200; seed++) {
			MechanismState state = dial.generate(new SplittableRandom(seed), params(4, 8, 3));
			for (ViewerKind kind : new ViewerKind[] { ViewerKind.PLAYER, ViewerKind.SPECTATOR }) {
				JsonNode view = dial.view(state, kind);
				String json = Json.write(view);
				assertFalse(json.contains("target"), json);
				assertFalse(json.contains("solvedOffsets"), json);
				assertFalse(json.contains("links"), "links stay hidden by default: " + json);
				// The only way to guess the target from a view is to read it: the view's glyph order and offsets
				// must not be the stored target sequence next to a "target" key, and the solved offsets must not appear.
				assertEquals("[symbols, offsets, showLinks, solved]", view.propertyNames().toString());
			}
			assertTrue(dial.view(state, ViewerKind.GAME_MASTER).has("target"));
		}
	}

	@Test
	void linksAreShownOnlyWhenAskedFor() {
		ObjectNode p = params(3, 8, 2);
		p.put("showLinks", true);
		MechanismState state = dial.generate(new SplittableRandom(4), p);
		JsonNode view = dial.view(state, ViewerKind.PLAYER);
		assertEquals(2, view.get("links").size());
		assertTrue(view.get("showLinks").asBoolean());
		assertFalse(Json.write(view).contains("target"));
	}

	@Test
	void aLinkTurnsTheLinkedRingToo() {
		MechanismState state = dial.generate(new SplittableRandom(11), params(3, 8, 3));
		Model model = Model.of(dial, state);
		for (int ring = 0; ring < 3; ring++) {
			InputResult r = dial.onInput(state, 0, gesture(ring, 1));
			int[] expected = model.apply(model.start, ring, 1);
			for (int i = 0; i < 3; i++) {
				assertEquals(expected[i], r.state().data().get("offsets").get(i).asInt());
			}
			assertEquals(1, r.events().size());
			assertEquals("turn", r.events().get(0).get("type").asString());
		}
	}

	@Test
	void turningBackUndoesTheMove() {
		MechanismState state = dial.generate(new SplittableRandom(2), params(5, 10, 3));
		MechanismState moved = dial.onInput(state, 0, gesture(2, 1)).state();
		MechanismState back = dial.onInput(moved, 0, gesture(2, -1)).state();
		assertEquals(state.data().get("offsets"), back.data().get("offsets"));
	}

	@Test
	void onInputDoesNotModifyTheOldState() {
		MechanismState state = dial.generate(new SplittableRandom(3), params(4, 8, 2));
		String before = state.data().toString();
		dial.onInput(state, 0, gesture(1, 1));
		assertEquals(before, state.data().toString());
	}

	@Test
	void inputsAreValidated() {
		MechanismState state = dial.generate(new SplittableRandom(3), params(3, 8, 1));
		assertThrows(IllegalArgumentException.class, () -> dial.onInput(state, 0, null));
		assertThrows(IllegalArgumentException.class, () -> dial.onInput(state, 0, Json.object()));
		assertThrows(IllegalArgumentException.class, () -> dial.onInput(state, 0, gesture(3, 1)));
		assertThrows(IllegalArgumentException.class, () -> dial.onInput(state, 0, gesture(-1, 1)));
		assertThrows(IllegalArgumentException.class, () -> dial.onInput(state, 0, gesture(0, 0)));
		assertThrows(IllegalArgumentException.class, () -> dial.onInput(state, 0, gesture(0, 2)));
		ObjectNode text = Json.object();
		text.put("ring", "0");
		text.put("delta", 1);
		assertThrows(IllegalArgumentException.class, () -> dial.onInput(state, 0, text));
	}

	@Test
	void solvedStepIgnoresFurtherGestures() {
		MechanismState state = dial.generate(new SplittableRandom(8), params(3, 6, 1));
		MechanismState s = state;
		for (int[] move : Model.of(dial, state).shortestSolution()) {
			s = dial.onInput(s, 0, gesture(move[0], move[1])).state();
		}
		assertTrue(s.data().get("solved").asBoolean());
		InputResult again = dial.onInput(s, 0, gesture(0, 1));
		assertFalse(again.solved());
		assertTrue(again.events().isEmpty());
		assertEquals(s.data(), again.state().data());
	}

	@Test
	void validatesParams() {
		assertTrue(dial.validateParams(Json.object()).isEmpty());
		assertTrue(dial.validateParams(params(5, 12, 3)).isEmpty());
		assertFalse(dial.validateParams(params(2, 8, 1)).isEmpty());
		assertFalse(dial.validateParams(params(6, 8, 1)).isEmpty());
		assertFalse(dial.validateParams(params(3, 5, 1)).isEmpty());
		assertFalse(dial.validateParams(params(3, 13, 1)).isEmpty());
		assertFalse(dial.validateParams(params(3, 8, 4)).isEmpty());
		ObjectNode bad = params(3, 8, 1);
		bad.put("showLinks", "yes");
		assertFalse(dial.validateParams(bad).isEmpty());
	}

	@Test
	void difficultyAndTimeGrowWithTheParams() {
		assertEquals(2, dial.difficulty(Json.object()));
		assertEquals(1, dial.difficulty(params(3, 6, 0)));
		assertEquals(3, dial.difficulty(params(5, 12, 3)));
		assertTrue(dial.expectedSeconds(params(5, 12, 3)) > dial.expectedSeconds(params(3, 6, 0)));
		assertNotEquals(0, dial.expectedSeconds(Json.object()));
	}
}
