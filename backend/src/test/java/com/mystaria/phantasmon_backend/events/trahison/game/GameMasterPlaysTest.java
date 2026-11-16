package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Outcome;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;
import com.mystaria.phantasmon_backend.events.trahison.rules.Vote;

import static org.assertj.core.api.Assertions.assertThat;

/** D12: a game master who also plays is a secret role; killing him makes every other participant win. */
class GameMasterPlaysTest {

	private static final String HUB = "hub-gm";
	private static final int SECONDS = 10;

	private ManualClock clock;
	private GameService service;
	private final UUID[] ids = new UUID[9];
	private final UUID watcher = new UUID(5, 5);

	@BeforeEach
	void setUp() {
		clock = new ManualClock();
		service = new GameService(new MemoryStore(), clock, clock);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
		}
	}

	/** Nine players, ids[3] also a game master (nominated after he joined), plus a game master who only supervises. */
	private void lobby() {
		service.create(HUB, Settings.fast(SECONDS).withoutCabins(), 9L);
		for (int i = 0; i < 9; i++) {
			service.join(HUB, ids[i], "p" + i);
		}
		service.setGameMaster(HUB, ids[3], "p3", true);
		service.setGameMaster(HUB, watcher, "watcher", true);
	}

	private Phase phase() {
		return service.read(HUB, g -> g.phase);
	}

	private Map<String, Object> view(UUID who) {
		return service.read(HUB, g -> Views.build(g, who, clock.instant()));
	}

	private static List<?> masters(Map<String, Object> view) {
		return (List<?>) view.get("game_masters");
	}

	private void passDoors(int except) {
		Split s = Doors.validSplits(service.read(HUB, g -> g.currentRound().assignment())).get(0);
		for (int k = 0; k < 3; k++) {
			for (int b : s.groups().get(k).members()) {
				if (b != except) {
					service.passDoor(HUB, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
				}
			}
		}
	}

	@Test
	void aGameMasterMayJoinBeforeOrAfterBeingNamed() {
		service.create(HUB, Settings.fast(SECONDS).withoutCabins(), 9L);
		UUID early = new UUID(7, 7);
		service.setGameMaster(HUB, early, "early", true);
		service.join(HUB, early, "early"); // named, then joins
		service.join(HUB, ids[0], "p0");
		assertThat(service.find(HUB).playingGameMasters()).containsExactly(0);
		// D46: a second game master cannot play: neither named after joining, nor joining after being named
		UUID other = new UUID(8, 8);
		service.setGameMaster(HUB, other, "other", true);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.join(HUB, other, "other"))
			.isInstanceOfSatisfying(TrahisonException.class, ex -> assertThat(ex.code()).isEqualTo(TrahisonException.ONE_PLAYING_GAME_MASTER));
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.setGameMaster(HUB, ids[0], "p0", true))
			.isInstanceOfSatisfying(TrahisonException.class, ex -> assertThat(ex.code()).isEqualTo(TrahisonException.ONE_PLAYING_GAME_MASTER));
	}

	@Test
	void nobodyElseLearnsWhoPlaysAsGameMaster() {
		lobby();
		service.start(HUB);
		// The others see only the game master who supervises; the game masters see everybody who holds the role.
		assertThat(masters(view(ids[0]))).hasSize(1);
		assertThat(masters(view(ids[0])).toString()).contains("watcher").doesNotContain("p3");
		assertThat(masters(view(new UUID(9, 9)))).hasSize(1);
		assertThat(masters(view(ids[3]))).hasSize(2);
		assertThat(masters(view(watcher))).hasSize(2);
		// His own view is a player view with the game master block on top; nobody else gets it or the flag.
		assertThat(view(ids[3]).get("gm")).isNotNull();
		assertThat(((Map<?, ?>) view(ids[3]).get("viewer")).get("kind")).isEqualTo("PLAYER");
		assertThat(((Map<?, ?>) view(ids[3]).get("viewer")).get("is_game_master")).isEqualTo(true);
		assertThat(view(ids[4]).get("gm")).isNull();
		assertThat(((Map<?, ?>) view(ids[4]).get("viewer")).get("is_game_master")).isEqualTo(false);
		assertThat(view(ids[3]).get("players").toString()).doesNotContain("game_master");
		// He holds the powers: nothing in what the players receive says who used them.
		service.next(HUB);
		service.pause(HUB);
		assertThat(view(ids[0]).get("paused")).isEqualTo(true);
		service.resume(HUB);
	}

	@Test
	void killedAtTheDoorsEndsTheGameAndTheOthersWin() {
		lobby();
		service.start(HUB);
		service.next(HUB); // DOORS
		passDoors(3); // he does not pass any door: he dies (D2)
		service.next(HUB);
		assertThat(phase()).isEqualTo(Phase.ENDED);
		assertThat(service.find(HUB).winners).containsExactly(0, 1, 2, 4, 5, 6, 7, 8);
		assertThat(service.find(HUB).log).extracting(e -> e.type).contains("game_master_killed", "ended");
		assertThat(view(ids[0]).get("winners")).isEqualTo(List.of(0, 1, 2, 4, 5, 6, 7, 8));
		assertThat(clock.pending()).isZero();
	}

	@Test
	void aBraceletAtZeroEndsTheGameToo() {
		lobby();
		service.start(HUB);
		service.next(HUB);
		passDoors(-1);
		service.next(HUB); // ROOMS
		service.next(HUB); // FREE_TIME
		service.next(HUB); // VOTE
		service.setPoints(HUB, 3, 1);
		for (var group : service.read(HUB, g -> g.currentRound().split()).groups()) {
			if (group.members().contains(3)) {
				boolean pair = group.pair().contains(3);
				// his side allies and the other side betrays: -2, floored at 0 (he dies)
				service.vote(HUB, ids[group.pair().get(0)], pair ? Vote.ALLY : Vote.BETRAY);
				service.vote(HUB, ids[group.solo()], pair ? Vote.BETRAY : Vote.ALLY);
			}
		}
		service.next(HUB); // the votes are resolved
		assertThat(phase()).isEqualTo(Phase.ENDED);
		assertThat(service.find(HUB).winners).containsExactly(0, 1, 2, 4, 5, 6, 7, 8);
	}

	@Test
	void theOtherPlayersDyingDoesNotEndIt() {
		lobby();
		service.start(HUB);
		service.next(HUB);
		passDoors(5);
		service.next(HUB);
		assertThat(phase()).isEqualTo(Phase.ROOMS);
		assertThat(service.find(HUB).winners).isEmpty();
	}

	@Test
	void theRulesArePure() {
		boolean[] alive = { true, true, true, true, true, true, true, true, true };
		assertThat(Outcome.gameMasterKilled(List.of(3), alive)).isFalse();
		assertThat(Outcome.gameMasterKilled(List.of(), new boolean[9])).isFalse();
		alive[3] = false;
		assertThat(Outcome.gameMasterKilled(List.of(3), alive)).isTrue();
		assertThat(Outcome.winnersWhenGameMasterKilled(List.of(3), alive)).containsExactly(0, 1, 2, 4, 5, 6, 7, 8);
		alive[6] = false; // D44: only the others still alive win
		assertThat(Outcome.winnersWhenGameMasterKilled(List.of(3), alive)).containsExactly(0, 1, 2, 4, 5, 7, 8);
	}

	@Test
	void aGameMasterWhoDiesOfHisOwnPowersGivesNobodyTheWinAndStaysAGhostWithHisPowers() {
		lobby();
		service.start(HUB);
		service.next(HUB);
		passDoors(-1);
		service.next(HUB); // ROOMS
		service.next(HUB);
		service.next(HUB); // VOTE
		service.setPoints(HUB, 3, 0, ids[3]); // by himself (D50)
		for (var group : service.read(HUB, g -> g.currentRound().split()).groups()) {
			if (group.members().contains(3)) {
				boolean pair = group.pair().contains(3);
				service.vote(HUB, ids[group.pair().get(0)], pair ? Vote.ALLY : Vote.BETRAY);
				service.vote(HUB, ids[group.solo()], pair ? Vote.BETRAY : Vote.ALLY);
			}
		}
		service.next(HUB); // resolved: he dies, nobody wins by it (D50)
		assertThat(service.find(HUB).players.get(3).alive).isFalse();
		assertThat(phase()).isEqualTo(Phase.RESULTS);
		assertThat(service.find(HUB).winners).isEmpty();
		assertThat(view(ids[3]).get("gm")).isNotNull(); // D51: a ghost who keeps his powers
		assertThat(((Map<?, ?>) view(ids[3]).get("viewer")).get("kind")).isEqualTo("GHOST");
	}

	@Test
	void theFinalDoorWindowIsCappedAt300Seconds() {
		Settings s = Settings.fast(SECONDS);
		s.finalDoorSeconds = 9999;
		service.create("hub-cap", s, 1L);
		assertThat(service.find("hub-cap").settings.finalDoorSeconds).isEqualTo(300);
	}

	@Test
	void spectatorsAreToldOnlyToThoseWhoWatch() {
		service.create(HUB, Settings.fast(SECONDS).withoutCabins(), 9L);
		for (int i = 0; i < 9; i++) {
			service.join(HUB, ids[i], "p" + i);
		}
		service.spectate(HUB, watcher, "eye", true);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.spectate(HUB, ids[0], "x", true))
			.isInstanceOf(TrahisonException.class);
		assertThat(view(watcher).get("spectators").toString()).contains("eye");
		assertThat(view(ids[0]).get("spectators")).isEqualTo(List.of());
	}
}
