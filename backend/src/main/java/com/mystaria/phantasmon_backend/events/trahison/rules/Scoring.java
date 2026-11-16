package com.mystaria.phantasmon_backend.events.trahison.rules;

/**
 * The vote scoring. Pure functions, bracelet indices 0..8.
 *
 * <pre>
 * Plan, section 3, "Le vote". Each door: the pair faces the solo. Points are per bracelet.
 *
 *   My vote | Opponent's vote |  Me | Opponent
 *   --------+-----------------+-----+---------
 *   ALLY    | ALLY            |  +2 |  +2
 *   BETRAY  | ALLY            |  +3 |  -2
 *   ALLY    | BETRAY          |  -2 |  +3
 *   BETRAY  | BETRAY          |   0 |   0
 *
 * - Both members of a pair get the same delta, whichever of them voted.
 * - A missing vote (not cast in time, tied up, dead bracelet) counts as ALLY.
 * - A bracelet never goes below 0 ({@link #apply}). Reaching 0 is handled by {@link Outcome}.
 * </pre>
 */
public final class Scoring {

	private Scoring() {
	}

	/** A room not solved after 30 minutes opens anyway and each member of the group loses this many points. */
	public static final int ROOM_TIMEOUT_PENALTY = -1;

	/** Point change of the pair bracelets and of the solo bracelet. */
	public record Result(int pairDelta, int soloDelta) {
	}

	/** Treats a missing vote ({@code null}) as {@link Vote#ALLY}. */
	public static Vote effective(Vote vote) {
		return vote == null ? Vote.ALLY : vote;
	}

	/** Points won or lost by the pair and by the solo. {@code null} means no vote, counted as ALLY. */
	public static Result resolve(Vote pairVote, Vote soloVote) {
		Vote p = effective(pairVote);
		Vote s = effective(soloVote);
		return new Result(delta(p, s), delta(s, p));
	}

	/** The change for a side voting {@code mine} against {@code theirs}. */
	private static int delta(Vote mine, Vote theirs) {
		if (mine == Vote.ALLY) {
			return theirs == Vote.ALLY ? 2 : -2;
		}
		return theirs == Vote.ALLY ? 3 : 0;
	}

	/**
	 * The point change of each of the nine bracelets for one door group (0 for the six not in the group).
	 * Votes may be {@code null} (counted as ALLY).
	 */
	public static int[] deltas(DoorGroup group, Vote pairVote, Vote soloVote) {
		Result r = resolve(pairVote, soloVote);
		int[] out = new int[Assignment.BRACELETS];
		for (int b : group.pair()) {
			out[b] = r.pairDelta();
		}
		out[group.solo()] = r.soloDelta();
		return out;
	}

	/** {@code points + delta}, floored at 0. */
	public static int apply(int points, int delta) {
		return Math.max(0, points + delta);
	}

	/** Applies per-bracelet deltas to per-bracelet points (both length 9) and returns the new points. */
	public static int[] applyAll(int[] points, int[] deltas) {
		if (points.length != Assignment.BRACELETS || deltas.length != Assignment.BRACELETS) {
			throw new IllegalArgumentException("Expected arrays of length 9");
		}
		int[] out = new int[points.length];
		for (int i = 0; i < out.length; i++) {
			out[i] = apply(points[i], deltas[i]);
		}
		return out;
	}
}
