package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The draw of a game's nine rooms (T4.1, plan.md section 7): three per round, from the bank, from a seed. Pure: no clock,
 * no database, the same inputs give the same draw.
 *
 * <p>Constraints, strictest first; when the bank cannot meet them all the draw relaxes them one by one, in this order, and
 * says which ones the result really breaks ({@link Result#relaxed}):
 * <ol>
 *   <li>{@code rise}: with {@link Params#rise} above 0, a round's total difficulty is at least the previous round's plus
 *       {@code rise};</li>
 *   <li>{@code variety}: no two rooms of a round share their main mechanism ({@link #mainMechanism});</li>
 *   <li>{@code recent}: a room played in one of the last {@link Params#avoidGames} games is not drawn;</li>
 *   <li>{@code spread}: in a round, the difficulties of the three rooms differ by at most {@link Params#maxSpread}
 *       (relaxed by growing the allowed spread, so the result stays as balanced as the bank allows);</li>
 *   <li>{@code repeat}: nine different rooms (relaxed by playing some rooms twice, never twice in a round when the bank
 *       has three rooms or more).</li>
 * </ol>
 * One constraint is never relaxed: a room needing a group of a given size (see {@link #MIN_GROUP}) is only put where the
 * group is that large.
 */
public final class RoomDraw {

	public static final int ROUNDS = 3;
	public static final int PER_ROUND = 3;
	public static final int ROOMS = ROUNDS * PER_ROUND;

	/** The mechanisms that need a group of at least this many players (the others work for any size). */
	public static final Map<String, Integer> MIN_GROUP = Map.of("three_levers", 3);

	public static final String RISE = "rise";
	public static final String VARIETY = "variety";
	public static final String RECENT = "recent";
	public static final String SPREAD = "spread";
	public static final String REPEAT = "repeat";

	public static final String EMPTY_BANK = "ERROR_TRAHISON_DRAW_EMPTY_BANK";
	public static final String GROUP_TOO_SMALL = "ERROR_TRAHISON_DRAW_GROUP_TOO_SMALL";
	public static final String NO_SOLUTION = "ERROR_TRAHISON_DRAW_NO_SOLUTION";

	/** Search budget (triples tried) of one attempt, so a bank with no solution fails fast instead of hanging. */
	private static final int BUDGET = 300_000;

	private RoomDraw() {
	}

	/**
	 * @param maxSpread  largest allowed difference between the hardest and the easiest room of a round
	 * @param rise       minimum growth of the round's total from one round to the next (0: no constraint)
	 * @param avoidGames how many past games count as recent
	 */
	public record Params(int maxSpread, int rise, int avoidGames) {
		public static final Params DEFAULT = new Params(1, 0, 3);

		public Params {
			maxSpread = Math.max(0, maxSpread);
			rise = Math.max(0, rise);
			avoidGames = Math.max(0, avoidGames);
		}
	}

	/** One drawn room: the {@code group} (0 to 2) is the door and the place of the round it is played in. */
	public record Pick(int round, int group, String room, String name, int difficulty, int expectedSeconds, String mechanism,
			boolean manual, String place, String orientation) {

		public Pick withPlace(String newPlace, String newOrientation) {
			return new Pick(round, group, room, name, difficulty, expectedSeconds, mechanism, manual, newPlace, newOrientation);
		}
	}

	/** The nine picks, round by round, and the constraints they break (a subset of {@code rise variety recent spread repeat}). */
	public record Result(long seed, List<Pick> picks, List<String> relaxed) {
		public Result {
			picks = List.copyOf(picks);
			relaxed = List.copyOf(relaxed);
		}

		public Pick pick(int round, int group) {
			return picks.stream().filter(p -> p.round() == round && p.group() == group).findFirst().orElse(null);
		}

		/** Total difficulty of a round (1 to 3). */
		public int total(int round) {
			return picks.stream().filter(p -> p.round() == round).mapToInt(Pick::difficulty).sum();
		}
	}

	public static final class DrawException extends RuntimeException {
		private final String code;

		public DrawException(String code, String message) {
			super(message);
			this.code = code;
		}

		public String code() {
			return code;
		}
	}

	/** The mechanism used by most steps of the room, the first one met on a tie; "" for a room without steps. */
	public static String mainMechanism(RoomDefinition room) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (Step s : room.steps()) {
			counts.merge(s.mechanism(), 1, Integer::sum);
		}
		String best = "";
		int bestCount = 0;
		for (Map.Entry<String, Integer> e : counts.entrySet()) {
			if (e.getValue() > bestCount) {
				best = e.getKey();
				bestCount = e.getValue();
			}
		}
		return best;
	}

	/** The smallest group the room can be played by: the largest need of its mechanisms, 1 when none. */
	public static int minGroup(RoomDefinition room) {
		int need = 1;
		for (Step s : room.steps()) {
			need = Math.max(need, MIN_GROUP.getOrDefault(s.mechanism(), 1));
		}
		return need;
	}

	/** {@link #draw(List, long, Map, Params, int[][])} with groups of three everywhere (the game's groups are always trios). */
	public static Result draw(List<RoomDefinition> bank, long seed, Map<String, Integer> history, Params params) {
		return draw(bank, seed, history, params, null);
	}

	/**
	 * @param bank       the rooms that can be drawn
	 * @param seed       the draw is a function of it
	 * @param history    room id to how many games ago it was played (1 = the last game); missing: never or long ago
	 * @param groupSizes {@code [round - 1][group]}: players in each group; null: 3 everywhere
	 * @throws DrawException an empty bank, or no way to put the rooms that need big groups where the groups are big enough
	 */
	public static Result draw(List<RoomDefinition> bank, long seed, Map<String, Integer> history, Params params, int[][] groupSizes) {
		Params p = params == null ? Params.DEFAULT : params;
		Map<String, Integer> past = history == null ? Map.of() : history;
		int[][] sizes = groupSizes == null ? defaultSizes() : groupSizes;
		Map<String, RoomDefinition> unique = new LinkedHashMap<>();
		for (RoomDefinition r : bank) {
			unique.putIfAbsent(r.id(), r);
		}
		if (unique.isEmpty()) {
			throw new DrawException(EMPTY_BANK, "the room bank is empty");
		}
		List<RoomDefinition> rooms = new ArrayList<>(unique.values());
		int low = rooms.stream().mapToInt(RoomDefinition::difficulty).min().orElse(0);
		int high = rooms.stream().mapToInt(RoomDefinition::difficulty).max().orElse(0);

		// Levels: how many constraints are relaxed, in order rise, variety, recent, spread, repeat.
		for (int level = 0; level <= 5; level++) {
			boolean needRise = level < 1 && p.rise() > 0;
			boolean needVariety = level < 2;
			boolean needRecent = level < 3;
			boolean repeat = level >= 5;
			int firstSpread = p.maxSpread();
			int lastSpread = level >= 4 ? Math.max(firstSpread, high - low) : firstSpread;
			for (int spread = firstSpread; spread <= lastSpread; spread++) {
				Search s = new Search(rooms, seed, past, p, sizes, needRise, needVariety, needRecent, spread, repeat);
				Result found = s.run();
				if (found != null) {
					return found;
				}
			}
		}
		throw new DrawException(NO_SOLUTION, "no way to place these rooms in these groups");
	}

	/** A new draw with the room at {@code (round, group)} replaced by {@code room} (marked manual); the rest is kept. */
	public static Result replace(Result result, int round, int group, RoomDefinition room, int[][] groupSizes) {
		int[][] sizes = groupSizes == null ? defaultSizes() : groupSizes;
		Pick old = result.pick(round, group);
		if (old == null) {
			throw new IllegalArgumentException("no pick for round " + round + " group " + group);
		}
		if (minGroup(room) > sizes[round - 1][group]) {
			throw new DrawException(GROUP_TOO_SMALL, "room " + room.id() + " needs a group of " + minGroup(room));
		}
		List<Pick> picks = new ArrayList<>();
		for (Pick pick : result.picks()) {
			picks.add(pick == old ? new Pick(round, group, room.id(), room.name(), room.difficulty(), room.expectedSeconds(),
					mainMechanism(room), true, old.place(), old.orientation()) : pick);
		}
		return new Result(result.seed(), picks, result.relaxed());
	}

	/** Group sizes of the engine: always trios. */
	public static int[][] defaultSizes() {
		int[][] sizes = new int[ROUNDS][PER_ROUND];
		for (int[] row : sizes) {
			java.util.Arrays.fill(row, 3);
		}
		return sizes;
	}

	/** One attempt at a fixed set of constraints; null when there is no solution (or the budget ran out). */
	private static final class Search {
		private final List<RoomDefinition> pool = new ArrayList<>();
		private final long seed;
		private final Params params;
		private final int[][] sizes;
		private final boolean rise;
		private final boolean variety;
		private final int spread;
		private final boolean repeat;
		private final Set<String> recent = new HashSet<>();
		private final String[] mechanism;
		private int tried;
		/** The largest total three rooms of the pool can make (an upper bound for the rise). */
		private int topTotal;
		private final int distinctIds;

		/** [round][group] to the index in {@link #pool}. */
		private final int[][] chosen = new int[ROUNDS][PER_ROUND];
		private final boolean[] used;

		Search(List<RoomDefinition> rooms, long seed, Map<String, Integer> past, Params params, int[][] sizes, boolean rise,
				boolean variety, boolean avoidRecent, int spread, boolean repeat) {
			this.seed = seed;
			this.params = params;
			this.sizes = sizes;
			this.rise = rise;
			this.variety = variety;
			this.spread = spread;
			this.repeat = repeat;
			for (RoomDefinition r : rooms) {
				Integer ago = past.get(r.id());
				if (ago != null && ago <= params.avoidGames()) {
					recent.add(r.id());
				}
			}
			Random rng = new Random(seed ^ 0x5DEECE66DL);
			List<RoomDefinition> fresh = new ArrayList<>();
			List<RoomDefinition> stale = new ArrayList<>();
			for (RoomDefinition r : rooms) {
				(recent.contains(r.id()) ? stale : fresh).add(r);
			}
			Collections.shuffle(fresh, rng);
			Collections.shuffle(stale, rng);
			pool.addAll(fresh);
			if (!avoidRecent) {
				pool.addAll(stale);
			}
			if (repeat) {
				int copies = (ROOMS + rooms.size() - 1) / rooms.size();
				List<RoomDefinition> base = List.copyOf(pool);
				for (int c = 1; c < copies; c++) {
					pool.addAll(base);
				}
			}
			distinctIds = rooms.size();
			topTotal = pool.stream().mapToInt(RoomDefinition::difficulty).boxed().sorted(Collections.reverseOrder()).limit(PER_ROUND)
					.mapToInt(Integer::intValue).sum();
			mechanism = new String[pool.size()];
			for (int i = 0; i < pool.size(); i++) {
				mechanism[i] = mainMechanism(pool.get(i));
			}
			used = new boolean[pool.size()];
		}

		Result run() {
			if (pool.size() < ROOMS || !round(0)) {
				return null;
			}
			List<Pick> picks = new ArrayList<>();
			for (int r = 0; r < ROUNDS; r++) {
				for (int g = 0; g < PER_ROUND; g++) {
					RoomDefinition room = pool.get(chosen[r][g]);
					picks.add(new Pick(r + 1, g, room.id(), room.name(), room.difficulty(), room.expectedSeconds(),
							mechanism[chosen[r][g]], false, null, null));
				}
			}
			return new Result(seed, picks, violations(picks));
		}

		private boolean round(int r) {
			if (r == ROUNDS) {
				return true;
			}
			int n = pool.size();
			for (int i = 0; i < n; i++) {
				if (used[i]) {
					continue;
				}
				for (int j = i + 1; j < n; j++) {
					if (used[j] || !compatible(i, j)) {
						continue;
					}
					for (int k = j + 1; k < n; k++) {
						if (used[k] || !compatible(i, k) || !compatible(j, k)) {
							continue;
						}
						if (++tried > BUDGET) {
							return false;
						}
						if (!balanced(i, j, k) || !rises(r, i, j, k)) {
							continue;
						}
						int[] order = place(r, i, j, k);
						if (order == null) {
							continue;
						}
						used[i] = used[j] = used[k] = true;
						System.arraycopy(order, 0, chosen[r], 0, PER_ROUND);
						if (round(r + 1)) {
							return true;
						}
						used[i] = used[j] = used[k] = false;
						if (tried > BUDGET) {
							return false;
						}
					}
				}
			}
			return false;
		}

		/** Two rooms may share a round: different rooms (unless the bank is tiny) and, with variety, different mechanisms. */
		private boolean compatible(int a, int b) {
			if (pool.get(a).id().equals(pool.get(b).id()) && (!repeat || distinctIds >= PER_ROUND)) {
				return false;
			}
			return !variety || mechanism[a].isEmpty() || !mechanism[a].equals(mechanism[b]);
		}

		private boolean balanced(int a, int b, int c) {
			int lo = Math.min(Math.min(d(a), d(b)), d(c));
			int hi = Math.max(Math.max(d(a), d(b)), d(c));
			return hi - lo <= spread;
		}

		private boolean rises(int r, int a, int b, int c) {
			if (!rise) {
				return true;
			}
			// the rounds still to come must be able to grow past this one: prune what leaves no room above
			if (d(a) + d(b) + d(c) + params.rise() * (ROUNDS - 1 - r) > topTotal) {
				return false;
			}
			if (r == 0) {
				return true;
			}
			int before = 0;
			for (int g = 0; g < PER_ROUND; g++) {
				before += d(chosen[r - 1][g]);
			}
			return d(a) + d(b) + d(c) - before >= params.rise();
		}

		private int d(int index) {
			return pool.get(index).difficulty();
		}

		/** The three rooms in group order for round {@code r}, so that each group is big enough; null when impossible. */
		private int[] place(int r, int a, int b, int c) {
			int[][] perms = { { a, b, c }, { a, c, b }, { b, a, c }, { b, c, a }, { c, a, b }, { c, b, a } };
			int start = (int) Math.floorMod(seed + r, 6L);
			for (int t = 0; t < 6; t++) {
				int[] perm = perms[(start + t) % 6];
				boolean fits = true;
				for (int g = 0; g < PER_ROUND && fits; g++) {
					fits = minGroup(pool.get(perm[g])) <= sizes[r][g];
				}
				if (fits) {
					return perm;
				}
			}
			return null;
		}

		/** What the chosen picks really break, in the order of the relaxation ladder. */
		private List<String> violations(List<Pick> picks) {
			List<String> out = new ArrayList<>();
			int[] totals = new int[ROUNDS];
			boolean varietyBroken = false;
			boolean spreadBroken = false;
			boolean recentUsed = false;
			Set<String> seen = new HashSet<>();
			boolean repeated = false;
			for (int r = 1; r <= ROUNDS; r++) {
				int lo = Integer.MAX_VALUE;
				int hi = Integer.MIN_VALUE;
				Set<String> mechs = new HashSet<>();
				for (Pick pk : picks) {
					if (pk.round() != r) {
						continue;
					}
					totals[r - 1] += pk.difficulty();
					lo = Math.min(lo, pk.difficulty());
					hi = Math.max(hi, pk.difficulty());
					if (!pk.mechanism().isEmpty() && !mechs.add(pk.mechanism())) {
						varietyBroken = true;
					}
					recentUsed |= recent.contains(pk.room());
					repeated |= !seen.add(pk.room());
				}
				spreadBroken |= hi - lo > params.maxSpread();
			}
			boolean riseBroken = params.rise() > 0 && (totals[1] - totals[0] < params.rise() || totals[2] - totals[1] < params.rise());
			if (riseBroken) {
				out.add(RISE);
			}
			if (varietyBroken) {
				out.add(VARIETY);
			}
			if (recentUsed) {
				out.add(RECENT);
			}
			if (spreadBroken) {
				out.add(SPREAD);
			}
			if (repeated) {
				out.add(REPEAT);
			}
			return out;
		}
	}
}
