package com.mystaria.phantasmon_backend.events.trahison.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DoorsTest {

	/** Bracelets 0,1 RED pair; 2 RED solo; 3,4 GREEN pair; 5 GREEN solo; 6,7 BLUE pair; 8 BLUE solo. */
	private static Assignment fixed() {
		List<Placement> p = new ArrayList<>();
		for (GameColor c : GameColor.values()) {
			p.add(new Placement(c, Status.PAIR));
			p.add(new Placement(c, Status.PAIR));
			p.add(new Placement(c, Status.SOLO));
		}
		return new Assignment(p);
	}

	@Test
	void doorIsUnorderedAndThereAreThree() {
		assertThat(Door.of(GameColor.GREEN, GameColor.RED)).isEqualTo(Door.of(GameColor.RED, GameColor.GREEN));
		assertThat(Door.ALL).hasSize(3).doesNotHaveDuplicates();
	}

	@Test
	void canOpenWithPairOfOneColourAndSoloOfTheOther() {
		Assignment a = fixed();
		Door rg = Door.of(GameColor.RED, GameColor.GREEN);
		assertThat(Doors.canOpen(rg, a, List.of(0, 5))).isTrue();   // RED pair + GREEN solo
		assertThat(Doors.canOpen(rg, a, List.of(3, 2))).isTrue();   // GREEN pair + RED solo
		assertThat(Doors.canOpen(rg, a, List.of(0, 1, 2))).isFalse(); // RED only
		assertThat(Doors.canOpen(rg, a, List.of(0, 3))).isFalse();   // two pairs
		assertThat(Doors.canOpen(rg, a, List.of(2, 5))).isFalse();   // two solos
		assertThat(Doors.canOpen(rg, a, List.of(0, 8))).isFalse();   // BLUE solo is not on this door
		assertThat(Doors.canOpen(rg, a, List.of())).isFalse();
	}

	@Test
	void extraOrDuplicateBraceletsDoNotMatter() {
		Assignment a = fixed();
		Door rb = Door.of(GameColor.RED, GameColor.BLUE);
		assertThat(Doors.canOpen(rb, a, List.of(0, 0, 1, 8, 8, 4))).isTrue();
		// A single carrier holding both bracelets counts the same as two carriers.
		assertThat(Doors.canOpen(rb, a, Set.of(6, 2))).isTrue();
	}

	@Test
	void outOfRangeBraceletIsRejected() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
			() -> Doors.canOpen(Door.ALL.get(0), fixed(), List.of(9)));
	}

	@Test
	void bothSplitsOfAFixedAssignment() {
		List<Split> splits = Doors.validSplits(fixed());
		assertThat(splits).hasSize(2);

		Split s0 = splits.get(0);
		assertThat(s0.group(Door.of(GameColor.RED, GameColor.GREEN)).members()).containsExactly(0, 1, 5);
		assertThat(s0.group(Door.of(GameColor.GREEN, GameColor.BLUE)).members()).containsExactly(3, 4, 8);
		assertThat(s0.group(Door.of(GameColor.RED, GameColor.BLUE)).members()).containsExactly(2, 6, 7);

		Split s1 = splits.get(1);
		assertThat(s1.group(Door.of(GameColor.RED, GameColor.BLUE)).members()).containsExactly(0, 1, 8);
		assertThat(s1.group(Door.of(GameColor.GREEN, GameColor.BLUE)).members()).containsExactly(5, 6, 7);
		assertThat(s1.group(Door.of(GameColor.RED, GameColor.GREEN)).members()).containsExactly(2, 3, 4);
	}

	@Test
	void splitsAreValidPartitionsAndEveryGroupOpensItsDoor() {
		for (long seed = 0; seed < 200; seed++) {
			Assignment a = Assignment.generate(seed);
			List<Split> splits = Doors.validSplits(a);
			assertThat(splits.get(0)).isNotEqualTo(splits.get(1));
			for (Split s : splits) {
				Set<Integer> all = new HashSet<>();
				Set<Door> doors = new HashSet<>();
				for (DoorGroup g : s.groups()) {
					assertThat(g.members()).hasSize(3);
					all.addAll(g.members());
					doors.add(g.door());
					assertThat(g.door().contains(g.pairColor())).isTrue();
					assertThat(g.door().contains(g.soloColor())).isTrue();
					assertThat(g.pairColor()).isNotEqualTo(g.soloColor());
					assertThat(Doors.canOpen(g.door(), a, g.members())).isTrue();
				}
				assertThat(all).hasSize(9);
				assertThat(doors).hasSize(3);
			}
		}
	}

	@Test
	void findSplitRecognisesEachSplitAndRejectsOthers() {
		Assignment a = fixed();
		List<Split> splits = Doors.validSplits(a);
		for (Split s : splits) {
			Map<Door, Set<Integer>> m = new java.util.HashMap<>();
			for (DoorGroup g : s.groups()) {
				m.put(g.door(), g.members());
			}
			assertThat(Doors.findSplit(a, m)).contains(s);
		}
		Map<Door, Set<Integer>> bad = Map.of(Door.ALL.get(0), Set.of(0, 1, 3));
		assertThat(Doors.findSplit(a, bad)).isEmpty();
	}
}
