package com.mystaria.phantasmon_backend.events.trahison.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class AssignmentTest {

	private static void assertShape(Assignment a) {
		Set<Integer> seen = new HashSet<>();
		for (GameColor c : GameColor.values()) {
			assertThat(a.pair(c)).hasSize(2);
			seen.addAll(a.pair(c));
			seen.add(a.solo(c));
			assertThat(a.color(a.solo(c))).isEqualTo(c);
			assertThat(a.status(a.solo(c))).isEqualTo(Status.SOLO);
		}
		assertThat(seen).hasSize(9);
	}

	@Test
	void sameSeedSameResultDifferentSeedsVary() {
		assertThat(Assignment.generate(42)).isEqualTo(Assignment.generate(42));
		History h = History.EMPTY.after(Assignment.generate(7), Doors.validSplits(Assignment.generate(7)).get(0));
		assertThat(Assignment.generate(42, h)).isEqualTo(Assignment.generate(42, h));
		Set<Assignment> distinct = new HashSet<>();
		for (long s = 0; s < 50; s++) {
			distinct.add(Assignment.generate(s));
		}
		assertThat(distinct.size()).isGreaterThan(30);
	}

	@Test
	void everyResultHasOnePairAndOneSoloPerColour() {
		for (long s = 0; s < 100; s++) {
			assertShape(Assignment.generate(s));
		}
	}

	@Test
	void constructorRejectsBadShapes() {
		Placement red = new Placement(GameColor.RED, Status.PAIR);
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
			() -> new Assignment(List.of(red)));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
			() -> new Assignment(java.util.Collections.nCopies(9, red)));
	}

	/** Three consecutive rounds, a random split chosen each time: the constraints must hold every time. */
	@Test
	void thousandSeedsRespectConstraintsOverThreeRounds() {
		for (long seed = 0; seed < 1000; seed++) {
			SplittableRandom players = new SplittableRandom(seed * 31 + 5);
			History history = History.EMPTY;
			for (int round = 0; round < 3; round++) {
				Assignment a = Assignment.generate(seed * 10 + round, history);
				assertShape(a);
				Assignment.Violations v = a.violations(history);
				assertThat(v.none()).as("seed %d round %d: %s", seed, round, v).isTrue();
				// Independent re-check against the raw history.
				for (GameColor c : GameColor.values()) {
					var p = a.pair(c);
					assertThat(history.paired()).doesNotContain(History.Link.of(p.get(0), p.get(1)));
				}
				List<Split> splits = Doors.validSplits(a);
				boolean freshSplit = false;
				for (Split s : splits) {
					boolean fresh = true;
					for (DoorGroup g : s.groups()) {
						for (int p : g.pair()) {
							if (history.faced().contains(History.Link.of(p, g.solo()))) {
								fresh = false;
							}
						}
					}
					freshSplit |= fresh;
				}
				assertThat(freshSplit).as("seed %d round %d has a fully fresh split", seed, round).isTrue();
				history = history.after(a, splits.get(players.nextInt(2)));
			}
		}
	}

	@Test
	void firstRoundWithEmptyHistoryHasNoViolation() {
		assertThat(Assignment.generate(3).violations(History.EMPTY)).isEqualTo(new Assignment.Violations(0, 0, 0));
	}

	@Test
	void historyRecordsPairsAndFacings() {
		Assignment a = Assignment.generate(11);
		Split s = Doors.validSplits(a).get(1);
		History h = History.EMPTY.after(a, s);
		assertThat(h.paired()).hasSize(3);
		assertThat(h.faced()).hasSize(6);
		assertThat(History.EMPTY.afterPairingOnly(a).faced()).isEmpty();
		assertThat(History.Link.of(4, 2)).isEqualTo(History.Link.of(2, 4));
	}

	/**
	 * Fallback: when every couple has already been paired the first constraint cannot hold; the generator must still
	 * return a valid assignment with the fewest repeats (here 3 is unavoidable only if all 36 couples are used).
	 */
	@Test
	void impossibleHistoryFallsBackToValidAssignmentWithMinimalViolations() {
		Set<History.Link> all = new HashSet<>();
		for (int i = 0; i < 9; i++) {
			for (int j = i + 1; j < 9; j++) {
				all.add(History.Link.of(i, j));
			}
		}
		History h = new History(all, Set.of());
		Assignment a = Assignment.generate(5, h);
		assertShape(a);
		assertThat(a.violations(h).pairRepeats()).isEqualTo(3);

		// Only couples inside {0,1,2} are free: every assignment pairs at most one of them... a minimum exists.
		Set<History.Link> most = new HashSet<>(all);
		most.remove(History.Link.of(0, 1));
		History h2 = new History(most, Set.of());
		Assignment b = Assignment.generate(5, h2);
		assertThat(b.violations(h2).pairRepeats()).isEqualTo(2);
		assertThat(b.pair(b.color(0))).contains(0, 1);
	}

	@Test
	void facingConstraintFallsBackWhenNoFreshSplitExists() {
		Set<History.Link> all = new HashSet<>();
		for (int i = 0; i < 9; i++) {
			for (int j = i + 1; j < 9; j++) {
				all.add(History.Link.of(i, j));
			}
		}
		History h = new History(Set.of(), all);
		Assignment a = Assignment.generate(9, h);
		assertShape(a);
		assertThat(a.violations(h)).isEqualTo(new Assignment.Violations(0, 6, 6));
	}

	@Test
	void pairingRuleOutranksFacingRule() {
		// Pairing 0-1 is forbidden, and 0 facing 1 is "free". Facing is irrelevant when pairing repeats are avoidable.
		History h = new History(Set.of(History.Link.of(0, 1)), Set.of());
		for (long s = 0; s < 100; s++) {
			Assignment a = Assignment.generate(s, h);
			assertThat(a.violations(h).pairRepeats()).isZero();
		}
	}
}
