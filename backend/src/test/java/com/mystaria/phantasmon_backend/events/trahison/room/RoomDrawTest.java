package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T4.1: the draw of the nine rooms, on a made-up bank. */
class RoomDrawTest {

	private static final String[] MECHS = { "keypad", "ring_dial", "wiring", "code_breaker", "pickup_set", "three_dials" };

	/** A room with {@code difficulty} and one mechanism (several steps of it when {@code steps} is above 1). */
	private static RoomDefinition room(String id, int difficulty, String mechanism) {
		return new RoomDefinition(id, id, "t", 1, difficulty, 60, List.of(new Slot("a", "terminal", new Pos(1, 1, 1), "north", null)),
				List.of(new Step("s", mechanism, null, Map.of(), List.of(), List.of(), List.of())), null);
	}

	/** {@code n} rooms, difficulties 2 to 7, mechanisms rotating. */
	private static List<RoomDefinition> bank(int n) {
		List<RoomDefinition> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(room("r" + i, 2 + i % 6, MECHS[(i / 6 + i) % MECHS.length]));
		}
		return out;
	}

	@Test
	void nineDifferentRoomsInThreeRoundsOfThree() {
		RoomDraw.Result r = RoomDraw.draw(bank(30), 1L, Map.of(), RoomDraw.Params.DEFAULT);
		assertThat(r.picks()).hasSize(9);
		assertThat(r.picks().stream().map(RoomDraw.Pick::room).distinct()).hasSize(9);
		for (int round = 1; round <= 3; round++) {
			final int rd = round;
			assertThat(r.picks().stream().filter(p -> p.round() == rd).map(RoomDraw.Pick::group).toList()).containsExactly(0, 1, 2);
		}
	}

	@Test
	void sameSeedSameDraw() {
		assertThat(RoomDraw.draw(bank(30), 99L, Map.of(), RoomDraw.Params.DEFAULT))
				.isEqualTo(RoomDraw.draw(bank(30), 99L, Map.of(), RoomDraw.Params.DEFAULT));
		assertThat(RoomDraw.draw(bank(30), 99L, Map.of(), RoomDraw.Params.DEFAULT))
				.isNotEqualTo(RoomDraw.draw(bank(30), 100L, Map.of(), RoomDraw.Params.DEFAULT));
	}

	@Test
	void balancedVariedAndFreshOverAThousandSeeds() {
		List<RoomDefinition> bank = bank(30);
		Set<String> recent = Set.of("r0", "r1", "r2", "r3", "r4");
		Map<String, Integer> history = Map.of("r0", 1, "r1", 1, "r2", 2, "r3", 3, "r4", 3, "r5", 9);
		int relaxed = 0;
		for (long seed = 0; seed < 1000; seed++) {
			RoomDraw.Result r = RoomDraw.draw(bank, seed, history, RoomDraw.Params.DEFAULT);
			relaxed += r.relaxed().isEmpty() ? 0 : 1;
			for (int round = 1; round <= 3; round++) {
				final int rd = round;
				List<RoomDraw.Pick> in = r.picks().stream().filter(p -> p.round() == rd).toList();
				int lo = in.stream().mapToInt(RoomDraw.Pick::difficulty).min().getAsInt();
				int hi = in.stream().mapToInt(RoomDraw.Pick::difficulty).max().getAsInt();
				assertThat(hi - lo).as("spread, seed %d round %d", seed, round).isLessThanOrEqualTo(1);
				assertThat(in.stream().map(RoomDraw.Pick::mechanism).distinct()).as("variety, seed %d", seed).hasSize(3);
			}
			assertThat(r.picks().stream().map(RoomDraw.Pick::room)).as("recent, seed %d", seed).doesNotContainAnyElementsOf(recent);
			assertThat(r.picks().stream().map(RoomDraw.Pick::room).distinct()).hasSize(9);
		}
		assertThat(relaxed).isZero();
	}

	@Test
	void risingDifficultyWhenAsked() {
		for (long seed = 0; seed < 200; seed++) {
			RoomDraw.Result r = RoomDraw.draw(bank(40), seed, Map.of(), new RoomDraw.Params(1, 1, 3));
			assertThat(r.relaxed()).isEmpty();
			assertThat(r.total(2)).isGreaterThanOrEqualTo(r.total(1) + 1);
			assertThat(r.total(3)).isGreaterThanOrEqualTo(r.total(2) + 1);
		}
	}

	@Test
	void recentRoomsComeBackWhenTheBankIsTooSmallAndTheDrawSaysSo() {
		List<RoomDefinition> bank = bank(10);
		Map<String, Integer> history = Map.of("r0", 1, "r1", 1, "r2", 2);
		RoomDraw.Result r = RoomDraw.draw(bank, 5L, history, RoomDraw.Params.DEFAULT);
		assertThat(r.picks().stream().map(RoomDraw.Pick::room).distinct()).hasSize(9);
		assertThat(r.relaxed()).contains(RoomDraw.RECENT);
	}

	@Test
	void notEnoughMechanismsRelaxesVarietyAndSaysSo() {
		List<RoomDefinition> bank = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			bank.add(room("m" + i, 3, i % 2 == 0 ? "keypad" : "wiring"));
		}
		RoomDraw.Result r = RoomDraw.draw(bank, 3L, Map.of(), RoomDraw.Params.DEFAULT);
		assertThat(r.picks()).hasSize(9);
		assertThat(r.relaxed()).contains(RoomDraw.VARIETY);
	}

	@Test
	void unevenBankRelaxesTheSpreadAsLittleAsPossible() {
		List<RoomDefinition> bank = new ArrayList<>();
		int[] d = { 1, 1, 1, 6, 6, 6, 11, 11, 11 };
		for (int i = 0; i < d.length; i++) {
			bank.add(room("u" + i, d[i], MECHS[i % MECHS.length]));
		}
		RoomDraw.Result r = RoomDraw.draw(bank, 8L, Map.of(), RoomDraw.Params.DEFAULT);
		assertThat(r.picks()).hasSize(9);
		assertThat(r.relaxed()).doesNotContain(RoomDraw.REPEAT);
		for (int round = 1; round <= 3; round++) {
			final int rd = round;
			assertThat(r.picks().stream().filter(p -> p.round() == rd).map(RoomDraw.Pick::difficulty).distinct()).hasSize(1);
		}
	}

	@Test
	void aBankOfFewRoomsRepeatsThemAndSaysSo() {
		RoomDraw.Result r = RoomDraw.draw(bank(4), 2L, Map.of(), RoomDraw.Params.DEFAULT);
		assertThat(r.picks()).hasSize(9);
		assertThat(r.relaxed()).contains(RoomDraw.REPEAT);
		for (int round = 1; round <= 3; round++) {
			final int rd = round;
			assertThat(r.picks().stream().filter(p -> p.round() == rd).map(RoomDraw.Pick::room).distinct()).hasSize(3);
		}
	}

	@Test
	void aSingleRoomBankStillDraws() {
		RoomDraw.Result r = RoomDraw.draw(List.of(room("only", 3, "keypad")), 2L, Map.of(), RoomDraw.Params.DEFAULT);
		assertThat(r.picks()).hasSize(9);
		assertThat(r.relaxed()).contains(RoomDraw.REPEAT);
	}

	@Test
	void anEmptyBankIsRefused() {
		assertThatThrownBy(() -> RoomDraw.draw(List.of(), 1L, Map.of(), RoomDraw.Params.DEFAULT))
				.isInstanceOfSatisfying(RoomDraw.DrawException.class, ex -> assertThat(ex.code()).isEqualTo(RoomDraw.EMPTY_BANK));
	}

	@Test
	void threeLeversOnlyGoesToAGroupOfThree() {
		List<RoomDefinition> bank = bank(24);
		bank.set(0, room("levers", 3, "three_levers"));
		// the groups of round 1 have 3, 2 and 2 players: the room never goes to the last two, so only group 0 can hold it
		int[][] sizes = { { 3, 2, 2 }, { 3, 3, 3 }, { 3, 3, 3 } };
		boolean seen = false;
		for (long seed = 0; seed < 300; seed++) {
			RoomDraw.Result r = RoomDraw.draw(bank, seed, Map.of(), RoomDraw.Params.DEFAULT, sizes);
			for (RoomDraw.Pick p : r.picks()) {
				if (p.room().equals("levers")) {
					seen = true;
					assertThat(sizes[p.round() - 1][p.group()]).isGreaterThanOrEqualTo(3);
				}
			}
		}
		assertThat(seen).isTrue();
		// only the trio's seat of the whole game is the first one of round 1: every other room can sit anywhere
		int[][] small = { { 2, 2, 2 }, { 2, 2, 2 }, { 2, 2, 2 } };
		for (long seed = 0; seed < 100; seed++) {
			assertThat(RoomDraw.draw(bank, seed, Map.of(), RoomDraw.Params.DEFAULT, small).picks())
					.noneMatch(p -> p.room().equals("levers"));
		}
		// and nothing but three-lever rooms with small groups cannot be drawn
		List<RoomDefinition> onlyLevers = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			onlyLevers.add(room("l" + i, 3, "three_levers"));
		}
		assertThatThrownBy(() -> RoomDraw.draw(onlyLevers, 1L, Map.of(), RoomDraw.Params.DEFAULT, small))
				.isInstanceOfSatisfying(RoomDraw.DrawException.class, ex -> assertThat(ex.code()).isEqualTo(RoomDraw.NO_SOLUTION));
	}

	@Test
	void replacingARoomKeepsTheRestAndMarksItManual() {
		List<RoomDefinition> bank = bank(30);
		RoomDraw.Result r = RoomDraw.draw(bank, 4L, Map.of(), RoomDraw.Params.DEFAULT);
		RoomDefinition other = room("x", 4, "keypad");
		RoomDraw.Result changed = RoomDraw.replace(r, 2, 1, other, null);
		assertThat(changed.pick(2, 1).room()).isEqualTo("x");
		assertThat(changed.pick(2, 1).manual()).isTrue();
		Set<String> same = new HashSet<>();
		for (RoomDraw.Pick p : changed.picks()) {
			if (!(p.round() == 2 && p.group() == 1)) {
				same.add(p.room());
				assertThat(p).isEqualTo(r.pick(p.round(), p.group()));
			}
		}
		assertThat(same).hasSize(8);
		RoomDefinition levers = room("lv", 3, "three_levers");
		assertThatThrownBy(() -> RoomDraw.replace(r, 1, 0, levers, new int[][] { { 2, 3, 3 }, { 3, 3, 3 }, { 3, 3, 3 } }))
				.isInstanceOfSatisfying(RoomDraw.DrawException.class, ex -> assertThat(ex.code()).isEqualTo(RoomDraw.GROUP_TOO_SMALL));
	}

	@Test
	void mainMechanismIsTheMostUsedOne() {
		RoomDefinition d = new RoomDefinition("m", "m", "t", 1, 3, 10, List.of(), List.of(
				new Step("a", "keypad", null, Map.of(), List.of(), List.of(), List.of()),
				new Step("b", "wiring", null, Map.of(), List.of(), List.of(), List.of()),
				new Step("c", "wiring", null, Map.of(), List.of(), List.of(), List.of())), null);
		assertThat(RoomDraw.mainMechanism(d)).isEqualTo("wiring");
		assertThat(RoomDraw.minGroup(d)).isEqualTo(1);
	}
}
