package com.mystaria.phantasmon_backend.events.trahison.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutcomeTest {

	@Test
	void deadAtZeroPoints() {
		assertThat(Outcome.isDeadByPoints(0)).isTrue();
		assertThat(Outcome.isDeadByPoints(1)).isFalse();
	}

	@Test
	void finalDoorNeedsNineAndAlive() {
		assertThat(Outcome.canOpenFinalDoor(9, true)).isTrue();
		assertThat(Outcome.canOpenFinalDoor(12, true)).isTrue();
		assertThat(Outcome.canOpenFinalDoor(8, true)).isFalse();
		assertThat(Outcome.canOpenFinalDoor(9, false)).isFalse();
	}

	@Test
	void openersAreWristBraceletsAtNineOrMoreOfLivingPlayers() {
		int[] pts = {9, 8, 10, 9, 0, 3, 3, 3, 3};
		boolean[] alive = {true, true, true, false, false, true, true, true, true};
		assertThat(Outcome.finalDoorOpeners(pts, alive)).containsExactly(0, 2);
	}

	@Test
	void newlyDeadAreLivingPlayersAtZero() {
		int[] pts = {0, 0, 3, 3, 3, 3, 3, 3, 3};
		boolean[] alive = {true, false, true, true, true, true, true, true, true};
		assertThat(Outcome.newlyDead(pts, alive)).containsExactly(0);
	}

	@Test
	void nobodyOpensWhenNobodyHasNine() {
		assertThat(Outcome.finalDoorOpeners(new int[] {3, 3, 3, 3, 3, 3, 3, 3, 3}, new boolean[9])).isEmpty();
	}

	@Test
	void wrongArrayLengthIsRejected() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
			() -> Outcome.newlyDead(new int[2], new boolean[9]));
	}

	@Test
	void theFourEndings() {
		assertThat(Outcome.ending(0, 9)).isEqualTo(Outcome.Ending.NOBODY);
		assertThat(Outcome.ending(1, 9)).isEqualTo(Outcome.Ending.SOLO);
		assertThat(Outcome.ending(2, 9)).isEqualTo(Outcome.Ending.SOME);
		assertThat(Outcome.ending(8, 9)).isEqualTo(Outcome.Ending.SOME);
		assertThat(Outcome.ending(9, 9)).isEqualTo(Outcome.Ending.ALL);
		assertThat(Outcome.Ending.SOLO.key()).isEqualTo("solo");
	}
}
