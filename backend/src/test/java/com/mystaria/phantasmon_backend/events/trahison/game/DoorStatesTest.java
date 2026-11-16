package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.Box;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutDoor;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutZone;

import static org.assertj.core.api.Assertions.assertThat;

/** The pure door-state function: defaults per door type, room progress, overrides. */
class DoorStatesTest {

	private static final Box BOX = new Box(0, 64, 0, 0, 66, 2);

	/**
	 * Round 1 only. The colour door of red-green leads to the slot zone 2 (not 0), so the link, not the order, must give
	 * the group of each exit.
	 */
	private static LayoutContent plan(boolean linkedExits) {
		List<LayoutZone> zones = new ArrayList<>(List.of(new LayoutZone("hall", "hall", List.of(BOX)),
			new LayoutZone("doors_1", "doors", List.of(BOX))));
		for (int s = 0; s < 3; s++) {
			zones.add(new LayoutZone("slot_" + s, "room_slot", List.of(BOX)));
		}
		List<LayoutDoor> doors = new ArrayList<>();
		doors.add(new LayoutDoor("c_rg", "color", BOX, "doors_1", "slot_2", 1, List.of("green", "red")));
		doors.add(new LayoutDoor("c_rb", "color", BOX, "doors_1", "slot_0", 1, List.of("red", "blue")));
		doors.add(new LayoutDoor("c_gb", "color", BOX, "doors_1", "slot_1", 1, List.of("green", "blue")));
		for (int s = 0; s < 3; s++) {
			doors.add(new LayoutDoor("exit_" + s, "room_exit", BOX, linkedExits ? "slot_" + s : "nowhere_" + s, "hall", null, null));
		}
		doors.add(new LayoutDoor("cabin_door", "cabin", BOX, "hall", "hall", null, null));
		doors.add(new LayoutDoor("final", "final", BOX, "hall", "hall", null, null));
		doors.add(new LayoutDoor("plain", "plain", BOX, "hall", "hall", null, null));
		return new LayoutContent(zones, doors, null, null, null);
	}

	private static Map<String, DoorState> at(LayoutContent plan, Phase phase, boolean[] solved) {
		return DoorStates.compute(plan, phase, 1, solved, Map.of());
	}

	private static boolean open(Map<String, DoorState> states, String id) {
		return states.get(id).open();
	}

	@Test
	void defaultsOutsideTheRooms() {
		for (Phase phase : new Phase[] { Phase.LOBBY, Phase.DOORS, Phase.FREE_TIME, Phase.VOTE, Phase.FINAL }) {
			Map<String, DoorState> s = at(plan(true), phase, new boolean[3]);
			assertThat(open(s, "c_rg")).as("colour door in %s", phase).isFalse();
			assertThat(open(s, "c_rb")).isFalse();
			assertThat(open(s, "final")).isFalse();
			assertThat(open(s, "cabin_door")).isTrue();
			assertThat(open(s, "plain")).isTrue();
			assertThat(open(s, "exit_0")).as("exit in %s", phase).isTrue();
		}
	}

	@Test
	void everyDoorIsOpenAfterTheGame() {
		Map<String, DoorState> s = at(plan(true), Phase.ENDED, new boolean[3]);
		assertThat(s.values()).allMatch(DoorState::open);
	}

	@Test
	void aRoomExitStaysClosedUntilItsGroupSolvedItsRoom() {
		// Group 0 = red-green, whose colour door leads to slot_2: exit_2 is its exit.
		Map<String, DoorState> none = at(plan(true), Phase.ROOMS, new boolean[3]);
		assertThat(open(none, "exit_0")).isFalse();
		assertThat(open(none, "exit_1")).isFalse();
		assertThat(open(none, "exit_2")).isFalse();
		Map<String, DoorState> g0 = at(plan(true), Phase.ROOMS, new boolean[] { true, false, false });
		assertThat(open(g0, "exit_2")).isTrue();
		assertThat(open(g0, "exit_0")).isFalse();
		assertThat(open(g0, "exit_1")).isFalse();
		// Group 1 = red-blue -> slot_0; group 2 = green-blue -> slot_1.
		Map<String, DoorState> g12 = at(plan(true), Phase.ROOMS, new boolean[] { false, true, true });
		assertThat(open(g12, "exit_0")).isTrue();
		assertThat(open(g12, "exit_1")).isTrue();
		assertThat(open(g12, "exit_2")).isFalse();
	}

	@Test
	void withoutALinkTheOrderOfThePlanDecides() {
		Map<String, DoorState> s = at(plan(false), Phase.ROOMS, new boolean[] { false, true, false });
		assertThat(open(s, "exit_0")).isFalse();
		assertThat(open(s, "exit_1")).isTrue();
		assertThat(open(s, "exit_2")).isFalse();
	}

	@Test
	void anExitOfAnotherRoundIsNotHeldClosed() {
		Map<String, DoorState> s = DoorStates.compute(plan(false), Phase.ROOMS, 2, new boolean[3], Map.of());
		assertThat(open(s, "exit_0")).isTrue(); // its round is 1, we are in round 2
	}

	@Test
	void anOverrideWinsOverTheDefault() {
		Map<String, DoorState> s = DoorStates.compute(plan(true), Phase.ROOMS, 1, new boolean[3],
			Map.of("c_rg", true, "plain", false, "exit_0", true));
		assertThat(open(s, "c_rg")).isTrue();
		assertThat(open(s, "plain")).isFalse();
		assertThat(open(s, "exit_0")).isTrue();
		assertThat(open(s, "final")).isFalse();
	}

	@Test
	void anEmptyPlanHasNoDoor() {
		assertThat(DoorStates.compute(LayoutContent.empty(), Phase.DOORS, 1, null, null)).isEmpty();
	}

	@Test
	void aDoorOpenOnlyForSomePlayers() {
		DoorState state = new DoorState(false, java.util.Set.of(3, 5));
		assertThat(state.openFor(3)).isTrue();
		assertThat(state.openFor(4)).isFalse();
		assertThat(state.openFor(-1)).isFalse();
		assertThat(DoorState.OPEN.openFor(-1)).isTrue();
	}
}
