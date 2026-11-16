package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mystaria.phantasmon_backend.events.trahison.rules.Assignment;
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.DoorGroup;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Placement;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;
import com.mystaria.phantasmon_backend.events.trahison.rules.Vote;

/**
 * One round (rounds 1 to 3, then one per final-door vote loop numbered 4, 5...). Group arrays are indexed like
 * {@link Door#ALL}. Votes are secret: nothing here may leave the backend before RESULTS (views, T1.3).
 */
public class RoundState {
	public int number;
	public boolean finalLoop;
	public long seed;
	public List<Placement> placements = new ArrayList<>();
	/** 0 or 1, the index in {@link Doors#validSplits}; null until the doors closed (or at once for a final loop). */
	public Integer splitIndex;
	/** Door index (0..2) each player passed, -1 when none. */
	public int[] passedDoor = new int[9];
	public List<Integer> penalized = new ArrayList<>();
	public boolean[] roomSolved = new boolean[3];
	public Vote[] pairVote = new Vote[3];
	/** Player index whose vote counted for the pair of each group, -1 when none. */
	public int[] pairVoter = new int[] { -1, -1, -1 };
	public Vote[] soloVote = new Vote[3];
	/** Point change of each bracelet, set at RESULTS; null before. */
	public int[] deltas;
	public List<Integer> newlyDead = new ArrayList<>();

	public RoundState() {
		Arrays.fill(passedDoor, -1);
	}

	public Assignment assignment() {
		return new Assignment(placements);
	}

	/** The split of the round; only valid once {@link #splitIndex} is set. */
	public Split split() {
		return Doors.validSplits(assignment()).get(splitIndex);
	}

	/** Index (0..2, as {@link Door#ALL}) of the group containing this bracelet in the round's split. */
	public int groupOf(int bracelet) {
		Split s = split();
		for (int k = 0; k < 3; k++) {
			DoorGroup g = s.group(Door.ALL.get(k));
			if (g.members().contains(bracelet)) {
				return k;
			}
		}
		throw new IllegalStateException("bracelet " + bracelet + " is in no group");
	}
}
