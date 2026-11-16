package com.mystaria.phantasmon_backend.events.trahison.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScoringTest {

	private static void check(Vote pair, Vote solo, int pairDelta, int soloDelta) {
		Scoring.Result r = Scoring.resolve(pair, solo);
		assertThat(r.pairDelta()).as("pair %s vs solo %s", pair, solo).isEqualTo(pairDelta);
		assertThat(r.soloDelta()).as("solo %s vs pair %s", solo, pair).isEqualTo(soloDelta);
	}

	@Test
	void allFourCases() {
		check(Vote.ALLY, Vote.ALLY, 2, 2);
		check(Vote.BETRAY, Vote.ALLY, 3, -2);
		check(Vote.ALLY, Vote.BETRAY, -2, 3);
		check(Vote.BETRAY, Vote.BETRAY, 0, 0);
	}

	@Test
	void missingVoteCountsAsAlly() {
		check(null, null, 2, 2);
		check(null, Vote.BETRAY, -2, 3);
		check(Vote.BETRAY, null, 3, -2);
		check(null, Vote.ALLY, 2, 2);
		assertThat(Scoring.effective(null)).isEqualTo(Vote.ALLY);
		assertThat(Scoring.effective(Vote.BETRAY)).isEqualTo(Vote.BETRAY);
	}

	@Test
	void deltasGivePairMembersTheSameResultAndLeaveOthersAtZero() {
		Assignment a = Assignment.generate(1);
		DoorGroup g = Doors.validSplits(a).get(0).groups().get(0);
		int[] d = Scoring.deltas(g, Vote.BETRAY, Vote.ALLY);
		for (int i = 0; i < 9; i++) {
			if (g.pair().contains(i)) {
				assertThat(d[i]).isEqualTo(3);
			} else if (i == g.solo()) {
				assertThat(d[i]).isEqualTo(-2);
			} else {
				assertThat(d[i]).isZero();
			}
		}
	}

	@Test
	void pointsNeverGoBelowZero() {
		assertThat(Scoring.apply(1, -2)).isZero();
		assertThat(Scoring.apply(0, -2)).isZero();
		assertThat(Scoring.apply(3, -2)).isEqualTo(1);
		assertThat(Scoring.apply(3, 3)).isEqualTo(6);
		int[] out = Scoring.applyAll(new int[] {3, 1, 0, 9, 3, 3, 3, 3, 3}, new int[] {-2, -2, -2, 2, 0, 3, 0, 0, 0});
		assertThat(List.of(out[0], out[1], out[2], out[3], out[5])).containsExactly(1, 0, 0, 11, 6);
	}

	@Test
	void threeAlliancesGiveExactlyNine() {
		int p = Outcome.START_POINTS;
		for (int i = 0; i < 3; i++) {
			p = Scoring.apply(p, Scoring.resolve(Vote.ALLY, Vote.ALLY).pairDelta());
		}
		assertThat(p).isEqualTo(Outcome.WIN_POINTS);
	}

	@Test
	void applyAllRejectsWrongLength() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
			() -> Scoring.applyAll(new int[3], new int[9]));
	}
}
