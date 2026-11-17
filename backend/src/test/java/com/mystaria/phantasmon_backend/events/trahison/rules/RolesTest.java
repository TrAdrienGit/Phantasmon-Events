package com.mystaria.phantasmon_backend.events.trahison.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** T7.1 and T7.2: the pure rules of the roles (draw, objectives) and of the item draw. */
class RolesTest {

	private static final List<Integer> ALL = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);

	@Test
	void theDrawGivesDifferentRolesToDifferentSeatsAndRepeatsWithTheSeed() {
		Map<Integer, Role> a = Roles.draw(42, Role.drawable(), 3, ALL);
		assertThat(a).hasSize(3);
		assertThat(Set.copyOf(a.values())).hasSize(3);
		assertThat(Roles.draw(42, Role.drawable(), 3, ALL)).isEqualTo(a);
		assertThat(Roles.draw(43, Role.drawable(), 3, ALL)).isNotEqualTo(a);
	}

	@Test
	void theDrawKeepsToTheActiveRolesAndTheEligibleSeats() {
		Map<Integer, Role> a = Roles.draw(1, List.of(Role.SEER, Role.LONER), 6, List.of(2, 5, 7));
		assertThat(a).hasSize(2);
		assertThat(a.values()).containsExactlyInAnyOrder(Role.SEER, Role.LONER);
		assertThat(a.keySet()).isSubsetOf(2, 5, 7);
		assertThat(Roles.draw(1, Role.drawable(), 0, ALL)).isEmpty();
		assertThat(Roles.draw(1, List.of(Role.GAME_MASTER), 3, ALL)).isEmpty(); // never drawn
		assertThat(Roles.draw(1, Role.drawable(), 6, List.of(4))).hasSize(1);
	}

	@Test
	void objectivesAreEvaluatedAtTheEnd() {
		Map<Integer, Role> roles = Map.of(0, Role.INFILTRATOR, 1, Role.FERRYMAN, 2, Role.GUARDIAN, 3, Role.LONER, 4, Role.SEER, 5, Role.GAMBLER);
		boolean[] alive = new boolean[9];
		java.util.Arrays.fill(alive, true);
		// nobody out, nobody dead: only the infiltrator who betrayed
		assertThat(Outcome.objectivesMet(roles, alive, List.of(), Set.of(0))).containsExactly(0);
		// two ghosts: the ferryman wins even if he is dead himself
		alive[1] = false;
		alive[8] = false;
		assertThat(Outcome.objectivesMet(roles, alive, List.of(), Set.of())).containsExactly(1);
		// five escape: the guardian; the loner only when he is alone
		assertThat(Outcome.objectivesMet(roles, alive, List.of(0, 2, 3, 4, 5), Set.of())).containsExactlyInAnyOrder(1, 2);
		assertThat(Outcome.objectivesMet(roles, alive, List.of(3), Set.of())).containsExactlyInAnyOrder(1, 3);
		assertThat(Outcome.objectivesMet(roles, alive, List.of(3, 4), Set.of())).containsExactly(1);
		// the seer and the gambler have powers, no objective
		assertThat(Roles.hasObjective(Role.SEER)).isFalse();
		assertThat(Roles.hasObjective(Role.GAMBLER)).isFalse();
	}

	@Test
	void itemsAreHiddenOnePerSpotFromTheSeedAndTheRestIsUnplaced() {
		Map<Item, Integer> counts = new EnumMap<>(Item.class);
		for (Item i : Item.values()) {
			counts.put(i, 1);
		}
		List<String> spots = List.of("a", "b", "c", "d", "e", "f", "g", "h");
		ItemDraw.Result r = ItemDraw.draw(7, counts, spots);
		assertThat(r.placed()).hasSize(7);
		assertThat(Set.copyOf(r.placed().values())).hasSize(7);
		assertThat(r.unplaced()).isEmpty();
		assertThat(ItemDraw.draw(7, counts, spots)).isEqualTo(r);
		ItemDraw.Result few = ItemDraw.draw(7, counts, List.of("a", "b", "c"));
		assertThat(few.placed()).hasSize(3);
		assertThat(few.unplaced()).hasSize(4);
		counts.put(Item.KNIFE, 5); // clamped to 2
		counts.put(Item.LETTER, 0);
		ItemDraw.Result more = ItemDraw.draw(7, counts, spots);
		assertThat(more.placed().values().stream().filter(i -> i == Item.KNIFE).count()).isEqualTo(2);
		assertThat(more.placed().values()).doesNotContain(Item.LETTER);
	}
}
