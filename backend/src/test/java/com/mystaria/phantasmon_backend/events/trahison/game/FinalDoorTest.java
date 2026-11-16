package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.Box;
import com.mystaria.phantasmon_backend.events.trahison.layout.Layout;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutDoor;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutZone;
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T2.5: the pupitre of the final door, the window in which only the 9-point bracelets pass, and the end of the game. */
class FinalDoorTest {

	private static final String HUB = "hub-f";
	private static final int SECONDS = 10;
	private static final int DOOR_SECONDS = 7;
	private static final Box BOX = new Box(0, 64, 0, 0, 66, 2);

	private ManualClock clock;
	private MemoryStore store;
	private GameService service;
	private final UUID[] ids = new UUID[9];

	/** A plan with only the final door: the pupitre in front of it (its threshold), the hall, and the zone beyond. */
	private static LayoutContent plan(String threshold) {
		return new LayoutContent(
			List.of(new LayoutZone("hall", "hall", List.of(BOX)), new LayoutZone("pupitre", "hall", List.of(BOX)),
				new LayoutZone("beyond", "final", List.of(BOX))),
			List.of(new LayoutDoor("door_final", "final", BOX, "hall", "beyond", null, null, threshold)), null, null, null);
	}

	private GameService newService(LayoutContent plan) {
		GameService s = new GameService(store, clock, clock);
		if (plan != null) {
			s.useLayouts((hub, version) -> Layout.of(hub, 3, false, Instant.EPOCH, plan));
		}
		return s;
	}

	@BeforeEach
	void setUp() {
		clock = new ManualClock();
		store = new MemoryStore();
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
		}
		service = newService(plan("pupitre"));
	}

	/** Plays the game to its FINAL stage by the game master's hand: nobody votes, so every bracelet reads 9. */
	private void toFinal() {
		Settings settings = Settings.fast(SECONDS).withoutCabins();
		settings.finalDoorSeconds = DOOR_SECONDS;
		service.create(HUB, settings, 11L);
		for (int i = 0; i < 9; i++) {
			service.join(HUB, ids[i], "p" + i);
		}
		service.start(HUB);
		service.next(HUB); // DOORS
		for (int round = 1; round <= 3; round++) {
			Split s = Doors.validSplits(service.read(HUB, g -> g.currentRound().assignment())).get(0);
			for (int k = 0; k < 3; k++) {
				for (int b : s.groups().get(k).members()) {
					service.passDoor(HUB, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
				}
			}
			for (int n = 0; n < 5; n++) {
				service.next(HUB); // ROOMS, FREE_TIME, VOTE, RESULTS, then DOORS (FINAL after round 3)
			}
		}
		assertThat(phase()).isEqualTo(Phase.FINAL);
	}

	private Phase phase() {
		return service.read(HUB, g -> g.phase);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> view(UUID who) {
		return service.read(HUB, g -> Views.build(g, who, clock.instant()));
	}

	@SuppressWarnings("unchecked")
	private String finalDoorFor(UUID who) {
		return (String) ((Map<String, Object>) view(who).get("doors_state")).get("door_final");
	}

	private void stand(int player, String zone) {
		service.setZone(HUB, ids[player], zone, 1, 65, 1);
	}

	private static void assertCode(Runnable call, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(TrahisonException.class, ex -> assertThat(ex.code()).isEqualTo(code));
	}

	@Test
	void theFinalDoorOpensOnlyForTheNineBraceletsAndTheGameEndsWhenTheyAreThrough() {
		toFinal();
		service.setPoints(HUB, 8, 5); // the ninth wrist reads 5
		assertThat(finalDoorFor(ids[0])).isEqualTo("CLOSED");
		assertThat(view(ids[0]).get("final_door")).isNull();

		stand(0, "pupitre");
		service.openFinalDoor(HUB, ids[0]);
		assertThat(phase()).isEqualTo(Phase.FINAL); // the game goes on while the door is open
		// Per viewer: open for the eight who read 9, shut for the ninth, for a spectator and for the game master.
		for (int i = 0; i < 8; i++) {
			assertThat(finalDoorFor(ids[i])).as("player " + i).isEqualTo("OPEN");
		}
		assertThat(finalDoorFor(ids[8])).isEqualTo("CLOSED");
		assertThat(finalDoorFor(new UUID(7, 7))).isEqualTo("CLOSED");
		@SuppressWarnings("unchecked")
		Map<String, Object> window = (Map<String, Object>) view(ids[3]).get("final_door");
		assertThat(window).containsEntry("openers", 8);
		assertThat(window.get("open_until")).isEqualTo(clock.instant().toEpochMilli() + DOOR_SECONDS * 1000L);
		// Everything else of the plan is untouched; the stage clock now counts the door's time.
		assertThat(service.<Instant>read(HUB, g -> g.deadline)).isEqualTo(clock.instant().plusSeconds(DOOR_SECONDS));

		// The eight walk through, one after the other; the door stays open its whole window (D19), then the game ends.
		for (int i = 0; i < 7; i++) {
			stand(i, "beyond");
			assertThat(phase()).isEqualTo(Phase.FINAL);
		}
		stand(8, "beyond"); // the ninth is not let through: he stands there but never counted
		assertThat(phase()).isEqualTo(Phase.FINAL);
		stand(7, "beyond");
		assertThat(phase()).isEqualTo(Phase.FINAL); // all eight are through, the door is still open
		assertThat(service.find(HUB).finalDoor).isNotNull();
		clock.advanceSeconds(DOOR_SECONDS - 1);
		assertThat(phase()).isEqualTo(Phase.FINAL);
		clock.advanceSeconds(1);
		assertThat(phase()).isEqualTo(Phase.ENDED);
		assertThat(service.find(HUB).winners).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
		assertThat(service.find(HUB).finalDoor).isNull();
		assertThat(service.find(HUB).log).extracting(e -> e.type).contains("final_door", "final_cross", "final_door_closed", "ended");
		assertThat(view(ids[0]).get("winners")).isEqualTo(List.of(0, 1, 2, 3, 4, 5, 6, 7));
		clock.advanceSeconds(DOOR_SECONDS + SECONDS); // the timers of the door and of the stage were cancelled: nothing moves
		assertThat(phase()).isEqualTo(Phase.ENDED);
		assertThat(service.find(HUB).winners).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
	}

	@Test
	void aSoloExitOpensTheDoorForOneAndEndsWhenHeIsThrough() {
		toFinal();
		for (int i = 0; i < 9; i++) {
			service.setPoints(HUB, i, i == 4 ? 12 : 8);
		}
		stand(4, "pupitre");
		service.openFinalDoor(HUB, ids[4]);
		assertThat(finalDoorFor(ids[4])).isEqualTo("OPEN");
		for (int i = 0; i < 9; i++) {
			if (i != 4) {
				assertThat(finalDoorFor(ids[i])).isEqualTo("CLOSED");
			}
		}
		stand(4, "beyond");
		assertThat(phase()).isEqualTo(Phase.FINAL);
		clock.advanceSeconds(DOOR_SECONDS);
		assertThat(phase()).isEqualTo(Phase.ENDED);
		assertThat(service.find(HUB).winners).containsExactly(4);
	}

	@Test
	void theDoorClosesForEverWhenItsTimeIsUpAndOnlyThoseWhoCrossedEscape() {
		toFinal();
		stand(0, "pupitre");
		service.openFinalDoor(HUB, ids[0]);
		stand(1, "beyond");
		stand(2, "beyond");
		clock.advanceSeconds(DOOR_SECONDS - 1);
		assertThat(phase()).isEqualTo(Phase.FINAL);
		stand(3, "beyond"); // the last second still counts
		clock.advanceSeconds(1);
		assertThat(phase()).isEqualTo(Phase.ENDED);
		assertThat(service.find(HUB).winners).containsExactly(1, 2, 3);
		assertThat(finalDoorFor(ids[0])).isEqualTo("OPEN"); // the game is over: the hub is free again
		assertThat(service.find(HUB).log).extracting(e -> e.type).contains("final_door_closed");
		assertThat(clock.pending()).isZero();
		// Nothing can be opened any more.
		assertCode(() -> service.openFinalDoor(HUB, ids[0]), TrahisonException.GAME_ENDED);
	}

	@Test
	void nobodyThroughInTimeEndsTheGameWithNoWinner() {
		toFinal();
		stand(0, "pupitre");
		service.openFinalDoor(HUB, ids[0]);
		clock.advanceSeconds(DOOR_SECONDS);
		assertThat(phase()).isEqualTo(Phase.ENDED);
		assertThat(service.find(HUB).winners).isEmpty();
	}

	@Test
	void theFinalDoorRefusesWhatItShould() {
		// Outside FINAL
		Settings settings = Settings.fast(SECONDS).withoutCabins();
		service.create(HUB, settings, 11L);
		for (int i = 0; i < 9; i++) {
			service.join(HUB, ids[i], "p" + i);
		}
		assertCode(() -> service.openFinalDoor(HUB, ids[0]), TrahisonException.WRONG_PHASE);
		service.start(HUB);
		service.next(HUB);
		assertCode(() -> service.openFinalDoor(HUB, ids[0]), TrahisonException.WRONG_PHASE);
		service = newService(plan("pupitre")); // (a fresh service, then play to FINAL)
		store.rows.clear();
		toFinal();
		// Under 9
		service.setPoints(HUB, 5, 8);
		stand(5, "pupitre");
		assertCode(() -> service.openFinalDoor(HUB, ids[5]), TrahisonException.CANNOT_OPEN_FINAL);
		assertThat(service.find(HUB).finalDoor).isNull();
		assertThat(finalDoorFor(ids[0])).isEqualTo("CLOSED");
		// Away from the pupitre (no zone known, then the hall)
		assertCode(() -> service.openFinalDoor(HUB, ids[0]), TrahisonException.NOT_AT_FINAL_DOOR);
		stand(0, "hall");
		assertCode(() -> service.openFinalDoor(HUB, ids[0]), TrahisonException.NOT_AT_FINAL_DOOR);
		// A stranger
		assertCode(() -> service.openFinalDoor(HUB, new UUID(9, 9)), TrahisonException.NOT_A_PLAYER);
		assertThat(phase()).isEqualTo(Phase.FINAL);
	}

	@Test
	void aPlanWithoutAPupitreZoneDoesNotAskWhereHeStands() {
		service = newService(plan(null));
		toFinal();
		service.openFinalDoor(HUB, ids[0]); // no zone reported at all
		assertThat(finalDoorFor(ids[0])).isEqualTo("OPEN");
		stand(0, "beyond");
		assertThat(service.find(HUB).finalDoor).isNotNull(); // the other eight are still expected
	}

	@Test
	void pressingThePupitreWhileTheDoorIsOpenChangesNothing() {
		toFinal();
		stand(0, "pupitre");
		stand(1, "pupitre");
		service.openFinalDoor(HUB, ids[0]);
		FinalDoorWindow first = service.find(HUB).finalDoor;
		clock.advanceSeconds(2);
		service.openFinalDoor(HUB, ids[1]);
		assertThat(service.find(HUB).finalDoor).isEqualTo(first);
		assertThat(service.find(HUB).log.stream().filter(e -> e.type.equals("final_door")).count()).isEqualTo(1L);
	}

	@Test
	void theVoteLoopGoesOnWhileTheDoorIsShutAndPausesWhileItIsOpen() {
		toFinal();
		clock.advanceSeconds(SECONDS); // nobody opened it: another vote
		assertThat(phase()).isEqualTo(Phase.VOTE);
		assertThat(service.find(HUB).finalLoops).isEqualTo(1);
		clock.advanceSeconds(SECONDS);
		clock.advanceSeconds(SECONDS);
		assertThat(phase()).isEqualTo(Phase.FINAL);
		stand(0, "pupitre");
		service.setPoints(HUB, 0, 9);
		service.openFinalDoor(HUB, ids[0]);
		clock.advanceSeconds(DOOR_SECONDS - 1);
		assertThat(phase()).isEqualTo(Phase.FINAL);
		assertThat(service.find(HUB).finalLoops).isEqualTo(1); // no new loop starts under an open door
		clock.advanceSeconds(1);
		assertThat(phase()).isEqualTo(Phase.ENDED);
	}

	@Test
	void theGameMasterCanCloseTheOpenDoorAtOnce() {
		toFinal();
		stand(0, "pupitre");
		service.openFinalDoor(HUB, ids[0]);
		stand(2, "beyond");
		service.next(HUB);
		assertThat(phase()).isEqualTo(Phase.ENDED);
		assertThat(service.find(HUB).winners).containsExactly(2);
	}

	@Test
	void theOpenDoorSurvivesARestartAndStillCloses() {
		toFinal();
		stand(0, "pupitre");
		service.openFinalDoor(HUB, ids[0]);
		stand(3, "beyond");
		clock.advanceSeconds(3);
		GameService reborn = newService(plan("pupitre"));
		reborn.loadActiveGames();
		assertThat(reborn.<FinalDoorWindow>read(HUB, g -> g.finalDoor)).isNotNull();
		assertThat(reborn.<Phase>read(HUB, g -> g.phase)).isEqualTo(Phase.FINAL);
		clock.advanceSeconds(DOOR_SECONDS - 3);
		assertThat(reborn.<Phase>read(HUB, g -> g.phase)).isEqualTo(Phase.ENDED);
		assertThat(reborn.<List<Integer>>read(HUB, g -> g.winners)).containsExactly(3);
	}

	@Test
	void aPlanWithoutAFinalDoorHasTheSameWindowThenEveryOpenerWins() {
		service = newService(null);
		toFinal();
		service.setPoints(HUB, 7, 6);
		service.openFinalDoor(HUB, ids[2]);
		assertThat(phase()).isEqualTo(Phase.FINAL); // D21: the window runs, with no door to cross
		FinalDoorWindow window = service.find(HUB).finalDoor;
		assertThat(window).isNotNull();
		assertThat(window.openers()).containsExactly(0, 1, 2, 3, 4, 5, 6, 8);
		assertThat(view(ids[0]).get("final_door")).isNotNull(); // everybody gets the countdown
		clock.advanceSeconds(DOOR_SECONDS - 1);
		assertThat(phase()).isEqualTo(Phase.FINAL);
		clock.advanceSeconds(1);
		assertThat(phase()).isEqualTo(Phase.ENDED);
		assertThat(service.find(HUB).winners).containsExactly(0, 1, 2, 3, 4, 5, 6, 8);
	}

	@Test
	void theWindowLastsOneMinuteByDefault() {
		assertThat(new Settings().finalDoorSeconds).isEqualTo(60);
	}
}
