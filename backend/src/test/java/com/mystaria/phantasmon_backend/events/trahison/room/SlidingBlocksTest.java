package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.SlidingBlocks;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests of {@link SlidingBlocks}; solvability is proved by a breadth-first search written independently of the mechanism. */
class SlidingBlocksTest {

	final SlidingBlocks mech = new SlidingBlocks();

	static ObjectNode params(int size, int blocks, int shuffle) {
		ObjectNode p = Json.object();
		p.put("size", size);
		p.put("blocks", blocks);
		p.put("shuffle", shuffle);
		return p;
	}

	static ObjectNode gesture(int block, int delta) {
		ObjectNode in = Json.object();
		in.put("block", block);
		in.put("delta", delta);
		return in;
	}

	/** Board rebuilt from the view: positions along each block's axis, searched one cell at a time. */
	record Model(int size, int[] fixed, int[] len, boolean[] horizontal, int[] start) {

		static Model of(JsonNode view) {
			int n = view.get("blocks").size();
			int[] fixed = new int[n];
			int[] len = new int[n];
			boolean[] h = new boolean[n];
			int[] start = new int[n];
			for (int i = 0; i < n; i++) {
				JsonNode b = view.get("blocks").get(i);
				h[i] = b.get("h").asBoolean();
				len[i] = b.get("len").asInt();
				fixed[i] = h[i] ? b.get("r").asInt() : b.get("c").asInt();
				start[i] = h[i] ? b.get("c").asInt() : b.get("r").asInt();
			}
			return new Model(view.get("size").asInt(), fixed, len, h, start);
		}

		boolean free(int[] pos, int block, int newPos) {
			if (newPos < 0 || newPos + len[block] > size) {
				return false;
			}
			for (int o = 0; o < pos.length; o++) {
				if (o == block) {
					continue;
				}
				for (int a = 0; a < len[block]; a++) {
					int r = horizontal[block] ? fixed[block] : newPos + a;
					int c = horizontal[block] ? newPos + a : fixed[block];
					for (int b = 0; b < len[o]; b++) {
						int r2 = horizontal[o] ? fixed[o] : pos[o] + b;
						int c2 = horizontal[o] ? pos[o] + b : fixed[o];
						if (r == r2 && c == c2) {
							return false;
						}
					}
				}
			}
			return true;
		}

		long encode(int[] pos) {
			long code = 0;
			for (int p : pos) {
				code = code * 8 + p;
			}
			return code;
		}

		/** Shortest list of {block, +1|-1} slides to free the piece, or null. */
		List<int[]> shortest() {
			record Node(int[] pos, Node from, int block, int dir) {
			}
			ArrayDeque<Node> queue = new ArrayDeque<>();
			Map<Long, Boolean> seen = new HashMap<>();
			queue.add(new Node(start, null, -1, 0));
			seen.put(encode(start), true);
			while (!queue.isEmpty()) {
				Node n = queue.poll();
				if (n.pos[0] + len[0] == size) {
					List<int[]> path = new ArrayList<>();
					for (Node x = n; x.from != null; x = x.from) {
						path.add(0, new int[] { x.block, x.dir });
					}
					return path;
				}
				for (int b = 0; b < n.pos.length; b++) {
					for (int d = -1; d <= 1; d += 2) {
						if (free(n.pos, b, n.pos[b] + d)) {
							int[] next = n.pos.clone();
							next[b] += d;
							if (seen.put(encode(next), true) == null) {
								queue.add(new Node(next, n, b, d));
							}
						}
					}
				}
			}
			return null;
		}
	}

	@Test
	void thousandRandomGenerationsAreSolvableAndNeverStartSolved() {
		SplittableRandom pick = new SplittableRandom(5);
		for (long seed = 0; seed < 1000; seed++) {
			int size = 5 + pick.nextInt(2);
			int blocks = 3 + pick.nextInt(5);
			MechanismState state = mech.generate(new SplittableRandom(seed), params(size, blocks, 6 + pick.nextInt(30)));
			assertFalse(state.data().get("solved").asBoolean(), "seed " + seed);
			Model model = Model.of(mech.view(state, ViewerKind.PLAYER));
			assertFalse(model.start[0] + 2 == model.size, "piece not on the opening, seed " + seed);
			List<int[]> solution = model.shortest();
			assertTrue(solution != null && !solution.isEmpty(), "solvable, seed " + seed);
			MechanismState s = state;
			for (int i = 0; i < solution.size(); i++) {
				InputResult r = mech.onInput(s, 0, gesture(solution.get(i)[0], solution.get(i)[1]));
				assertEquals(i == solution.size() - 1, r.solved(), "seed " + seed + " step " + i);
				s = r.state();
			}
			assertTrue(s.data().get("solved").asBoolean());
		}
	}

	@Test
	void largeBoardsStillDrawWithoutOverlap() {
		for (long seed = 0; seed < 200; seed++) {
			MechanismState state = mech.generate(new SplittableRandom(seed), params(7, 10, 60));
			JsonNode blocks = state.data().get("blocks");
			boolean[][] grid = new boolean[7][7];
			for (JsonNode b : blocks) {
				for (int k = 0; k < b.get("len").asInt(); k++) {
					int r = b.get("r").asInt() + (b.get("h").asBoolean() ? 0 : k);
					int c = b.get("c").asInt() + (b.get("h").asBoolean() ? k : 0);
					assertTrue(r < 7 && c < 7 && !grid[r][c], "seed " + seed);
					grid[r][c] = true;
				}
			}
			assertEquals(0, blocks.get(0).get("r").asInt() - state.data().get("exitRow").asInt());
		}
	}

	@Test
	void sameSeedSameBoard() {
		assertEquals(mech.generate(new SplittableRandom(3), params(6, 7, 30)).data(),
				mech.generate(new SplittableRandom(3), params(6, 7, 30)).data());
	}

	@Test
	void blockedAndMalformedGesturesAreRefusedAndTheOldStateIsKept() {
		MechanismState state = mech.generate(new SplittableRandom(1), params(6, 6, 24));
		String before = state.data().toString();
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, null));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, Json.object()));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, gesture(99, 1)));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, gesture(0, 0)));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, gesture(0, 6)));
		assertThrows(IllegalArgumentException.class, () -> mech.onInput(state, 0, gesture(1, -6)));
		// Every block slid far enough off the board is blocked: some slide of 5 cells must fail.
		int refused = 0;
		for (int b = 0; b < 7; b++) {
			for (int d : new int[] { -5, 5 }) {
				try {
					mech.onInput(state, 0, gesture(b, d));
				} catch (IllegalArgumentException ex) {
					refused++;
				}
			}
		}
		assertTrue(refused > 0);
		assertEquals(before, state.data().toString());
	}

	@Test
	void aSlideMovesTheBlockAndCountsAMove() {
		MechanismState state = mech.generate(new SplittableRandom(2), params(6, 6, 24));
		Model model = Model.of(mech.view(state, ViewerKind.PLAYER));
		for (int b = 0; b < model.start.length; b++) {
			for (int d = -1; d <= 1; d += 2) {
				if (model.free(model.start, b, model.start[b] + d)) {
					InputResult r = mech.onInput(state, 0, gesture(b, d));
					JsonNode moved = r.state().data().get("blocks").get(b);
					assertEquals(model.start[b] + d, model.horizontal[b] ? moved.get("c").asInt() : moved.get("r").asInt());
					assertEquals(1, r.state().data().get("moves").asInt());
					assertEquals("slide", r.events().get(0).get("type").asString());
					return;
				}
			}
		}
		throw new AssertionError("no legal slide found");
	}

	@Test
	void solvedBoardIgnoresFurtherGestures() {
		MechanismState state = mech.generate(new SplittableRandom(4), params(5, 3, 6));
		MechanismState s = state;
		for (int[] move : Model.of(mech.view(state, ViewerKind.PLAYER)).shortest()) {
			s = mech.onInput(s, 0, gesture(move[0], move[1])).state();
		}
		InputResult again = mech.onInput(s, 0, gesture(0, -1));
		assertFalse(again.solved());
		assertTrue(again.events().isEmpty());
	}

	@Test
	void viewHoldsNoMoreThanTheBoard() {
		MechanismState state = mech.generate(new SplittableRandom(9), params(6, 6, 24));
		for (ViewerKind kind : ViewerKind.values()) {
			assertEquals("[size, exitRow, blocks, moves, solved]", mech.view(state, kind).propertyNames().toString());
		}
		assertTrue(mech.clueRoles(Json.object()).isEmpty());
	}

	@Test
	void validatesParamsAndGrowsWithDifficulty() {
		assertTrue(mech.validateParams(Json.object()).isEmpty());
		assertFalse(mech.validateParams(params(4, 6, 24)).isEmpty());
		assertFalse(mech.validateParams(params(6, 11, 24)).isEmpty());
		assertFalse(mech.validateParams(params(6, 6, 5)).isEmpty());
		assertTrue(mech.difficulty(params(7, 10, 60)) > mech.difficulty(params(5, 3, 6)));
		assertTrue(mech.expectedSeconds(params(7, 10, 60)) > mech.expectedSeconds(params(5, 3, 6)));
	}
}
