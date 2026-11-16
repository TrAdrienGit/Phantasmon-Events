package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.rules.Assignment;
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.DoorGroup;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;
import com.mystaria.phantasmon_backend.events.trahison.rules.Vote;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameServiceTest {

	private static final String HUB = "hub-a";
	private static final int SECONDS = 10;

	private ManualClock clock;
	private MemoryStore store;
	private GameService service;
	private final UUID[] ids = new UUID[9];
	private final List<String> events = new ArrayList<>();

	@BeforeEach
	void setUp() {
		clock = new ManualClock();
		store = new MemoryStore();
		service = new GameService(store, clock, clock);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
		}
		service.addListener(new GameListener() {
			@Override
			public void phaseChanged(Game game) {
				events.add("phase:" + game.phase);
			}

			@Override
			public void changed(Game game, String patch) {
				events.add(patch);
			}
		});
	}

	private Game lobbyWith(int players, long seed) {
		Game g = service.create(HUB, Settings.fast(SECONDS).withoutCabins(), seed);
		for (int i = 0; i < players; i++) {
			service.join(HUB, ids[i], "p" + i);
		}
		return g;
	}

	private Game started(long seed) {
		lobbyWith(9, seed);
		return service.start(HUB);
	}

	private Phase phase() {
		return service.read(HUB, g -> g.phase);
	}

	private void toNextStage() {
		clock.advanceSeconds(SECONDS);
	}

	private Split split() {
		return service.read(HUB, g -> g.currentRound().split());
	}

	private Assignment assignment() {
		return service.read(HUB, g -> g.currentRound().assignment());
	}

	/** Everybody takes the door of split {@code index} (0 or 1). */
	private void everyonePasses(int index) {
		Split s = Doors.validSplits(assignment()).get(index);
		for (int k = 0; k < 3; k++) {
			for (int b : s.groups().get(k).members()) {
				service.passDoor(HUB, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
			}
		}
	}

	private void everyoneVotes(Vote pairSide, Vote soloSide) {
		Split s = split();
		for (DoorGroup g : s.groups()) {
			service.vote(HUB, ids[g.pair().get(0)], pairSide);
			service.vote(HUB, ids[g.solo()], soloSide);
		}
	}

	private int points(int i) {
		return service.read(HUB, g -> g.bracelets.get(i).points);
	}

	@Test
	void anEntireGameRunsOnTheClockAlone() {
		started(42);
		assertThat(phase()).isEqualTo(Phase.PROLOGUE);
		for (int round = 1; round <= 3; round++) {
			if (round == 1) {
				toNextStage();
			}
			assertThat(phase()).isEqualTo(Phase.DOORS);
			assertThat(service.find(HUB).round).isEqualTo(round);
			everyonePasses(round % 2);
			toNextStage();
			assertThat(phase()).isEqualTo(Phase.ROOMS);
			service.debugSolve(HUB, ids[0]);
			assertThat(service.find(HUB).currentRound().roomSolved).containsExactly(
				split().groups().stream().map(g -> g.members().contains(0)).toArray(Boolean[]::new));
			toNextStage();
			assertThat(phase()).isEqualTo(Phase.FREE_TIME);
			toNextStage();
			assertThat(phase()).isEqualTo(Phase.VOTE);
			everyoneVotes(Vote.ALLY, Vote.ALLY);
			toNextStage();
			assertThat(phase()).isEqualTo(Phase.RESULTS);
			for (int i = 0; i < 9; i++) {
				assertThat(points(i)).isEqualTo(3 + 2 * round);
			}
			toNextStage();
		}
		assertThat(phase()).isEqualTo(Phase.FINAL);
		assertThat(service.find(HUB).round).isEqualTo(3);

		// One loop of the final: fresh assignment, split by the seed, everybody allies again.
		toNextStage();
		assertThat(phase()).isEqualTo(Phase.VOTE);
		assertThat(service.find(HUB).currentRound().finalLoop).isTrue();
		everyoneVotes(Vote.ALLY, Vote.ALLY);
		toNextStage();
		assertThat(phase()).isEqualTo(Phase.RESULTS);
		assertThat(points(0)).isEqualTo(11);
		toNextStage();
		assertThat(phase()).isEqualTo(Phase.FINAL);
		assertThat(service.find(HUB).finalLoops).isEqualTo(1);

		service.openFinalDoor(HUB, ids[4]);
		assertThat(phase()).isEqualTo(Phase.ENDED);
		assertThat(service.find(HUB).winners).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8);
		assertThat(service.find(HUB).endedAt).isNotNull();
		assertThat(clock.pending()).isZero();
		assertThat(events).contains("phase:LOBBY", "phase:PROLOGUE", "phase:ENDED", "vote", "door");
		assertThat(service.find(HUB).log).extracting(e -> e.type).contains("created", "start", "round_start",
			"doors_closed", "results", "final_door", "ended");
		// a new game may now be created in the same hub
		assertThat(service.create(HUB, null, 1L).phase).isEqualTo(Phase.LOBBY);
	}

	@Test
	void theFinalLoopsUntilSomeoneOpensTheDoor() {
		started(7);
		service.next(HUB); // DOORS
		service.next(HUB); // ROOMS
		service.next(HUB);
		service.next(HUB); // VOTE
		service.next(HUB); // RESULTS
		service.next(HUB); // round 2 DOORS
		for (int i = 0; i < 4; i++) {
			service.next(HUB); // ... up to RESULTS of round 2
		}
		for (int i = 0; i < 6; i++) {
			service.next(HUB); // round 3 through RESULTS then FINAL
		}
		assertThat(phase()).isEqualTo(Phase.FINAL);
		// Nobody at 9 (nobody voted: ALLY/ALLY gives +2 each round: 3 + 6 = 9? the doors penalty took 1 each round).
		assertThatThrownBy(() -> service.openFinalDoor(HUB, ids[0]))
			.isInstanceOf(TrahisonException.class).extracting("code").isEqualTo(TrahisonException.CANNOT_OPEN_FINAL);
		toNextStage();
		assertThat(phase()).isEqualTo(Phase.VOTE);
		toNextStage();
		toNextStage();
		assertThat(phase()).isEqualTo(Phase.FINAL);
		assertThat(service.find(HUB).finalLoops).isEqualTo(1);
		service.setPoints(HUB, 2, 9);
		service.openFinalDoor(HUB, ids[2]);
		assertThat(service.find(HUB).winners).containsExactly(2);
	}

	@Test
	void aGameMasterEndsTheGame() {
		started(1);
		service.end(HUB);
		assertThat(phase()).isEqualTo(Phase.ENDED);
		assertThat(clock.pending()).isZero();
		assertCode(() -> service.next(HUB), TrahisonException.WRONG_PHASE);
		assertCode(() -> service.end(HUB), TrahisonException.GAME_ENDED);
	}

	@Test
	void pauseFreezesTheRemainingTimeAndResumeRecomputesTheDeadline() {
		started(3);
		clock.advanceSeconds(4);
		service.pause(HUB);
		assertThat(service.find(HUB).remainingMs).isEqualTo(6000);
		assertThat(service.find(HUB).deadline).isNull();
		clock.advanceSeconds(1000);
		assertThat(phase()).isEqualTo(Phase.PROLOGUE);
		assertCode(() -> service.pause(HUB), TrahisonException.PAUSED);
		service.resume(HUB);
		assertThat(service.find(HUB).deadline).isEqualTo(clock.instant().plusSeconds(6));
		clock.advanceSeconds(5);
		assertThat(phase()).isEqualTo(Phase.PROLOGUE);
		clock.advanceSeconds(1);
		assertThat(phase()).isEqualTo(Phase.DOORS);
		assertCode(() -> service.resume(HUB), TrahisonException.NOT_PAUSED);

		// actions are refused while paused, extend adds time
		service.pause(HUB);
		assertCode(() -> service.passDoor(HUB, ids[0], 0), TrahisonException.PAUSED);
		service.extend(HUB, 5);
		assertThat(service.find(HUB).remainingMs).isEqualTo(15000);
		service.resume(HUB);
		clock.advanceSeconds(14);
		assertThat(phase()).isEqualTo(Phase.DOORS);
		clock.advanceSeconds(1);
		assertThat(phase()).isEqualTo(Phase.ROOMS);
	}

	@Test
	void gameMasterModeDoesNotAdvanceOnItsOwn() {
		Settings s = Settings.fast(SECONDS).withoutCabins();
		s.mode = Mode.GAME_MASTER;
		service.create(HUB, s, 5L);
		for (int i = 0; i < 9; i++) {
			service.join(HUB, ids[i], "p" + i);
		}
		service.start(HUB);
		clock.advanceSeconds(1000);
		assertThat(phase()).isEqualTo(Phase.PROLOGUE);
		service.next(HUB);
		assertThat(phase()).isEqualTo(Phase.DOORS);
	}

	@Test
	void theGameReloadsFromJsonInTheMiddleOfAVote() throws Exception {
		started(99);
		toNextStage();
		everyonePasses(0);
		toNextStage();
		toNextStage();
		toNextStage();
		assertThat(phase()).isEqualTo(Phase.VOTE);
		Split s = split();
		service.vote(HUB, ids[s.groups().get(0).pair().get(1)], Vote.BETRAY);
		clock.advanceSeconds(3);

		String before = GameJson.write(service.find(HUB));
		var mapper = JsonMapper.builder().build();
		assertThat(mapper.readTree(GameJson.write(GameJson.read(before)))).isEqualTo(mapper.readTree(before));
		// timerVersion is bumped by the reschedule after a reload, the one field allowed to differ
		var expected = (tools.jackson.databind.node.ObjectNode) mapper.readTree(before);
		expected.remove("timerVersion");

		// A restarted backend: new service, same store, a fresh scheduler.
		ManualClock sameTime = clock; // the clock keeps running across the restart
		GameService reborn = new GameService(store, sameTime, sameTime);
		reborn.loadActiveGames();
		Game g = reborn.find(HUB);
		var actual = (tools.jackson.databind.node.ObjectNode) mapper.readTree(GameJson.write(g));
		actual.remove("timerVersion");
		assertThat(actual).isEqualTo(expected);
		assertThat(g.currentRound().pairVote[0]).isEqualTo(Vote.BETRAY);
		assertThat(g.currentRound().pairVoter[0]).isEqualTo(s.groups().get(0).pair().get(1));
		assertCode(() -> reborn.vote(HUB, ids[s.groups().get(0).pair().get(0)], Vote.ALLY), TrahisonException.ALREADY_VOTED);

		// Its deadline was rescheduled: 7 seconds left in the vote.
		clock.advanceSeconds(6);
		assertThat(reborn.find(HUB).phase).isEqualTo(Phase.VOTE);
		clock.advanceSeconds(1);
		assertThat(reborn.find(HUB).phase).isEqualTo(Phase.RESULTS);
		// the betrayal of the pair against an ALLY solo: pair +3, solo -2
		DoorGroup dg = s.groups().get(0);
		assertThat(reborn.find(HUB).bracelets.get(dg.pair().get(0)).points).isEqualTo(6);
		assertThat(reborn.find(HUB).bracelets.get(dg.solo()).points).isEqualTo(1);
	}

	@Test
	void aDeadlinePassedWhileDownFiresAtOnceAfterReload() {
		started(11);
		ManualClock later = new ManualClock();
		later.advanceSeconds(500); // the backend was down past the deadline
		GameService reborn = new GameService(store, later, later);
		reborn.loadActiveGames();
		assertThat(reborn.find(HUB).phase).isEqualTo(Phase.PROLOGUE);
		later.advanceSeconds(0); // the overdue timer runs
		assertThat(reborn.find(HUB).phase).isEqualTo(Phase.DOORS);
	}

	@Test
	void sameSeedGivesTheSameRounds() {
		started(2024);
		toNextStage();
		List<?> first = service.find(HUB).currentRound().placements;
		long seed = service.find(HUB).currentRound().seed;

		ManualClock c2 = new ManualClock();
		GameService other = new GameService(new MemoryStore(), c2, c2);
		other.create("hub-b", Settings.fast(SECONDS).withoutCabins(), 2024L);
		for (int i = 0; i < 9; i++) {
			other.join("hub-b", ids[i], "p" + i);
		}
		other.start("hub-b");
		c2.advanceSeconds(SECONDS);
		assertThat(other.find("hub-b").currentRound().placements).isEqualTo(first);
		assertThat(other.find("hub-b").currentRound().seed).isEqualTo(seed);
	}

	@Test
	void aBetrayalAndADeathBecomeAGhost() {
		started(5);
		toNextStage();
		everyonePasses(0);
		Split s = Doors.validSplits(assignment()).get(0);
		toNextStage();
		toNextStage();
		toNextStage();
		DoorGroup g0 = s.groups().get(0);
		service.setPoints(HUB, g0.solo(), 1);
		service.vote(HUB, ids[g0.pair().get(0)], Vote.BETRAY);
		service.vote(HUB, ids[g0.solo()], Vote.ALLY);
		toNextStage();
		assertThat(phase()).isEqualTo(Phase.RESULTS);
		assertThat(points(g0.solo())).isZero();
		assertThat(points(g0.pair().get(0))).isEqualTo(6);
		assertThat(points(g0.pair().get(1))).isEqualTo(6);
		Game g = service.find(HUB);
		assertThat(g.players.get(g0.solo()).alive).isFalse();
		assertThat(g.bracelets.get(g0.solo()).ownerAlive).isFalse();
		assertThat(g.currentRound().newlyDead).containsExactly(g0.solo());
		// the other two groups did not vote: ALLY both, +2 each
		DoorGroup g1 = s.groups().get(1);
		assertThat(points(g1.solo())).isEqualTo(5);

		// round 2: the ghost cannot take a door, and his bracelet is not penalized
		toNextStage();
		assertThat(phase()).isEqualTo(Phase.DOORS);
		assertCode(() -> service.passDoor(HUB, ids[g0.solo()], 0), TrahisonException.DEAD);
		toNextStage();
		assertThat(points(g0.solo())).isZero();
		assertThat(service.find(HUB).currentRound().penalized).doesNotContain(g0.solo());
	}

	@Test
	void anAbsenteeGetsTheNoDoorPenaltyAndTheSeedChoosesTheSplit() {
		started(8);
		toNextStage();
		toNextStage();
		assertThat(phase()).isEqualTo(Phase.ROOMS);
		Game g = service.find(HUB);
		assertThat(g.currentRound().splitIndex).isNotNull();
		assertThat(g.currentRound().penalized).hasSize(9);
		for (int i = 0; i < 9; i++) {
			assertThat(points(i)).isEqualTo(2);
		}
	}

	@Test
	void doorsAreCheckedAgainstTheBraceletAndThePassesSoFar() {
		started(21);
		toNextStage();
		Assignment a = assignment();
		Split s0 = Doors.validSplits(a).get(0);
		DoorGroup first = s0.groups().get(0);
		int member = first.pair().get(0);
		// a door that does not carry his colour
		int wrong = Door.ALL.indexOf(Door.ALL.stream().filter(d -> !d.contains(a.color(member))).findFirst().orElseThrow());
		assertCode(() -> service.passDoor(HUB, ids[member], wrong), TrahisonException.WRONG_DOOR);
		service.passDoor(HUB, ids[member], Door.ALL.indexOf(first.door()));
		assertCode(() -> service.passDoor(HUB, ids[member], Door.ALL.indexOf(first.door())), TrahisonException.ALREADY_PASSED);
		// a bracelet of split 1 going to a door of split 1 that disagrees with split 0 is refused
		Split s1 = Doors.validSplits(a).get(1);
		DoorGroup other = s1.groups().stream().filter(gr -> !gr.members().contains(member)
			&& s0.groups().stream().noneMatch(g0 -> g0.door().equals(gr.door()) && g0.members().equals(gr.members()))).findFirst().orElseThrow();
		int stranger = other.members().stream().filter(b -> !first.members().contains(b)).findFirst().orElseThrow();
		assertCode(() -> service.passDoor(HUB, ids[stranger], Door.ALL.indexOf(other.door())), TrahisonException.DOOR_INCOMPATIBLE);
		assertCode(() -> service.passDoor(HUB, ids[member], 5), TrahisonException.BAD_ARGUMENT);
		// outside its stage
		assertCode(() -> service.vote(HUB, ids[member], Vote.ALLY), TrahisonException.WRONG_PHASE);
		assertCode(() -> service.passDoor(HUB, UUID.randomUUID(), 0), TrahisonException.NOT_A_PLAYER);
	}

	@Test
	void aPairHasOneVoteAndASoloVotesOnce() {
		started(33);
		toNextStage();
		everyonePasses(0);
		toNextStage();
		toNextStage();
		toNextStage();
		DoorGroup g0 = split().groups().get(0);
		service.vote(HUB, ids[g0.pair().get(1)], Vote.BETRAY);
		assertCode(() -> service.vote(HUB, ids[g0.pair().get(0)], Vote.ALLY), TrahisonException.ALREADY_VOTED);
		assertCode(() -> service.vote(HUB, ids[g0.pair().get(1)], Vote.ALLY), TrahisonException.ALREADY_VOTED);
		service.vote(HUB, ids[g0.solo()], Vote.ALLY);
		assertCode(() -> service.vote(HUB, ids[g0.solo()], Vote.BETRAY), TrahisonException.ALREADY_VOTED);
		assertCode(() -> service.openFinalDoor(HUB, ids[0]), TrahisonException.WRONG_PHASE);
	}

	@Test
	void theLobbyRules() {
		lobbyWith(8, 1);
		assertCode(() -> service.start(HUB), TrahisonException.NOT_ENOUGH_PLAYERS);
		assertCode(() -> service.join(HUB, ids[0], "again"), TrahisonException.ALREADY_JOINED);
		assertCode(() -> service.create(HUB, null, null), TrahisonException.GAME_EXISTS);
		service.join(HUB, ids[8], "p8");
		assertCode(() -> service.join(HUB, UUID.randomUUID(), "tenth"), TrahisonException.GAME_FULL);
		service.leave(HUB, ids[8]);
		assertCode(() -> service.leave(HUB, ids[8]), TrahisonException.NOT_A_PLAYER);
		service.join(HUB, ids[8], "p8");
		service.start(HUB);
		assertCode(() -> service.join(HUB, UUID.randomUUID(), "late"), TrahisonException.NOT_IN_LOBBY);
		assertCode(() -> service.leave(HUB, ids[0]), TrahisonException.NOT_IN_LOBBY);
		assertCode(() -> service.start(HUB), TrahisonException.NOT_IN_LOBBY);
		assertCode(() -> service.pause("nowhere"), TrahisonException.NO_GAME);
		assertThat(store.saves).isGreaterThan(10);
	}

	private static void assertCode(Runnable op, String code) {
		assertThatThrownBy(op::run).isInstanceOf(TrahisonException.class).extracting("code").isEqualTo(code);
	}
}
