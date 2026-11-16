package com.mystaria.phantasmon_backend.events.trahison.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The colour and status of the nine bracelets for one round. Bracelets are identified by their index 0..8 everywhere
 * in this package. For each of the three colours there is exactly one pair (two bracelets) and one solo.
 *
 * <h2>Generation</h2>
 * {@link #generate(long, History)} searches <em>all</em> 45 360 possible assignments (so "a solution exists" is
 * decided exactly, not by luck) and picks, with a seeded random choice, one of the cheapest. Cost is ordered
 * lexicographically, highest priority first:
 * <ol>
 *   <li>number of couples placed in the same pair that were already in a pair together;</li>
 *   <li>number of repeated facings in the <em>best</em> of the two valid splits (see {@link Doors#validSplits}).
 *       The players pick the split, so it is enough that one of the two is fresh; 0 means a fully fresh split
 *       exists;</li>
 *   <li>number of repeated facings in the worse split (tie-breaker only).</li>
 * </ol>
 * If no assignment satisfies a constraint, the generator falls back gracefully: it returns an assignment with the
 * fewest violations of the highest-priority constraint first, then the next. The same seed and history always give
 * the same result.
 */
public record Assignment(List<Placement> placements) {

	public static final int BRACELETS = 9;

	public Assignment {
		placements = List.copyOf(placements);
		if (placements.size() != BRACELETS) {
			throw new IllegalArgumentException("An assignment covers exactly 9 bracelets");
		}
		for (GameColor c : GameColor.values()) {
			int pair = 0;
			int solo = 0;
			for (Placement p : placements) {
				if (p.color() == c) {
					if (p.status() == Status.PAIR) {
						pair++;
					} else {
						solo++;
					}
				}
			}
			if (pair != 2 || solo != 1) {
				throw new IllegalArgumentException("Colour " + c + " needs one pair and one solo");
			}
		}
	}

	public Placement placement(int bracelet) {
		return placements.get(bracelet);
	}

	public GameColor color(int bracelet) {
		return placements.get(bracelet).color();
	}

	public Status status(int bracelet) {
		return placements.get(bracelet).status();
	}

	/** The two bracelets of the pair of this colour, ascending. */
	public List<Integer> pair(GameColor color) {
		List<Integer> out = new ArrayList<>(2);
		for (int i = 0; i < BRACELETS; i++) {
			if (color(i) == color && status(i) == Status.PAIR) {
				out.add(i);
			}
		}
		return out;
	}

	/** The solo bracelet of this colour. */
	public int solo(GameColor color) {
		for (int i = 0; i < BRACELETS; i++) {
			if (color(i) == color && status(i) == Status.SOLO) {
				return i;
			}
		}
		throw new IllegalStateException("unreachable");
	}

	/**
	 * How many constraints this assignment breaks against a history.
	 *
	 * @param pairRepeats     couples in the same pair that already were in a pair together
	 * @param bestSplitRepeat repeated facings in the fresher of the two splits
	 * @param worstSplitRepeat repeated facings in the other split
	 */
	public record Violations(int pairRepeats, int bestSplitRepeat, int worstSplitRepeat) {
		/** True when both constraints that matter (priorities 1 and 2) hold. */
		public boolean none() {
			return pairRepeats == 0 && bestSplitRepeat == 0;
		}

		long cost() {
			return pairRepeats * 10_000L + bestSplitRepeat * 100L + worstSplitRepeat;
		}
	}

	public Violations violations(History history) {
		boolean[][] paired = matrix(history.paired());
		boolean[][] faced = matrix(history.faced());
		int[] layout = new int[9];
		int k = 0;
		for (GameColor c : GameColor.values()) {
			var pair = pair(c);
			layout[k++] = pair.get(0);
			layout[k++] = pair.get(1);
			layout[k++] = solo(c);
		}
		return violationsOf(paired, faced, layout);
	}

	/** Same as {@code generate(seed, History.EMPTY)}. */
	public static Assignment generate(long seed) {
		return generate(seed, History.EMPTY);
	}

	/**
	 * Draws the assignment of a round. Deterministic for a given {@code seed} and {@code history}; see the class
	 * comment for the constraints and the fallback.
	 */
	public static Assignment generate(long seed, History history) {
		boolean[][] paired = matrix(history.paired());
		boolean[][] faced = matrix(history.faced());
		Search search = new Search(paired, faced);
		search.run(0, 0, new int[9]);
		int pick = new SplittableRandom(seed).nextInt(search.best.size());
		return fromLayout(search.best.get(pick));
	}

	// layout = [pairA0, pairA1, solo0, pairB0, pairB1, solo1, ...] indexed by colour ordinal

	private static Assignment fromLayout(int[] layout) {
		Placement[] out = new Placement[BRACELETS];
		for (GameColor c : GameColor.values()) {
			int base = c.ordinal() * 3;
			out[layout[base]] = new Placement(c, Status.PAIR);
			out[layout[base + 1]] = new Placement(c, Status.PAIR);
			out[layout[base + 2]] = new Placement(c, Status.SOLO);
		}
		return new Assignment(List.of(out));
	}

	private static boolean[][] matrix(Iterable<History.Link> links) {
		boolean[][] m = new boolean[BRACELETS][BRACELETS];
		for (History.Link l : links) {
			if (l.lo() < 0 || l.hi() >= BRACELETS) {
				throw new IllegalArgumentException("Bracelet index out of range in history: " + l);
			}
			m[l.lo()][l.hi()] = true;
			m[l.hi()][l.lo()] = true;
		}
		return m;
	}

	private static Violations violationsOf(boolean[][] paired, boolean[][] faced, int[] layout) {
		int pairRepeats = 0;
		int splitA = 0;
		int splitB = 0;
		for (int c = 0; c < 3; c++) {
			int a = layout[c * 3];
			int b = layout[c * 3 + 1];
			if (paired[a][b]) {
				pairRepeats++;
			}
			// Split A: the pair of colour c faces the solo of colour c+1; split B: the solo of colour c+2.
			int soloA = layout[((c + 1) % 3) * 3 + 2];
			int soloB = layout[((c + 2) % 3) * 3 + 2];
			splitA += (faced[a][soloA] ? 1 : 0) + (faced[b][soloA] ? 1 : 0);
			splitB += (faced[a][soloB] ? 1 : 0) + (faced[b][soloB] ? 1 : 0);
		}
		return new Violations(pairRepeats, Math.min(splitA, splitB), Math.max(splitA, splitB));
	}

	/** Exhaustive enumeration keeping every assignment of minimal cost. */
	private static final class Search {
		private final boolean[][] paired;
		private final boolean[][] faced;
		private final List<int[]> best = new ArrayList<>();
		private long bestCost = Long.MAX_VALUE;

		Search(boolean[][] paired, boolean[][] faced) {
			this.paired = paired;
			this.faced = faced;
		}

		void run(int color, int used, int[] layout) {
			if (color == 3) {
				long cost = violationsOf(paired, faced, layout).cost();
				if (cost < bestCost) {
					bestCost = cost;
					best.clear();
				}
				if (cost == bestCost) {
					best.add(layout.clone());
				}
				return;
			}
			int base = color * 3;
			for (int a = 0; a < BRACELETS; a++) {
				if ((used & (1 << a)) != 0) {
					continue;
				}
				for (int b = a + 1; b < BRACELETS; b++) {
					if ((used & (1 << b)) != 0) {
						continue;
					}
					for (int s = 0; s < BRACELETS; s++) {
						if (s == a || s == b || (used & (1 << s)) != 0) {
							continue;
						}
						layout[base] = a;
						layout[base + 1] = b;
						layout[base + 2] = s;
						run(color + 1, used | (1 << a) | (1 << b) | (1 << s), layout);
					}
				}
			}
		}
	}
}
