package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A dropped player: the game goes on, and in AUTO mode the clock waits for him for at most five minutes. */
class ConnectionTest {

	private static final String HUB = "hub-c";

	private ManualClock clock;
	private GameService service;
	private final UUID[] ids = new UUID[9];

	@BeforeEach
	void setUp() {
		clock = new ManualClock();
		service = new GameService(new MemoryStore(), clock, clock);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
		}
	}

	private void start(Mode mode) {
		Settings s = Settings.fast(600);
		s.mode = mode;
		service.create(HUB, s, 3L);
		if (mode == Mode.GAME_MASTER) {
			service.setGameMaster(HUB, UUID.randomUUID(), "gm", true);
		}
		for (int i = 0; i < 9; i++) {
			service.join(HUB, ids[i], "p" + i);
		}
		service.start(HUB);
	}

	private Game game() {
		return service.find(HUB);
	}

	@Test
	void aDisconnectionPausesTheClockAndTheReconnectionResumesIt() {
		start(Mode.AUTO);
		clock.advanceSeconds(100);
		service.setConnected(HUB, ids[4], false);
		Game g = game();
		assertThat(g.paused).isTrue();
		assertThat(g.autoPaused).isTrue();
		assertThat(g.remainingMs).isEqualTo(500_000L);
		assertThat(g.players.get(4).connected).isFalse();
		clock.advanceSeconds(200); // time passes, the stage does not
		assertThat(g.phase).isEqualTo(Phase.PROLOGUE);
		assertThat(g.remainingMs).isEqualTo(500_000L);
		service.setConnected(HUB, ids[4], true);
		assertThat(g.paused).isFalse();
		assertThat(g.autoPaused).isFalse();
		assertThat(g.deadline).isEqualTo(clock.instant().plusSeconds(500));
		clock.advanceSeconds(500);
		assertThat(g.phase).isEqualTo(Phase.DOORS);
	}

	@Test
	void theAutomaticPauseEndsAfterFiveMinutes() {
		start(Mode.AUTO);
		service.setConnected(HUB, ids[0], false);
		assertThat(game().paused).isTrue();
		clock.advance(GameService.AUTO_PAUSE_MAX.minusSeconds(1));
		assertThat(game().paused).isTrue();
		clock.advanceSeconds(1);
		assertThat(game().paused).isFalse();
		assertThat(game().autoPaused).isFalse();
		assertThat(game().players.get(0).connected).isFalse(); // still away: the game goes on without him
		clock.advance(Duration.ofSeconds(600));
		assertThat(game().phase).isEqualTo(Phase.DOORS);
		assertThat(clock.pending()).isEqualTo(1); // only the DOORS timer
	}

	@Test
	void theClockWaitsForEveryLivingPlayerWhoDropped() {
		start(Mode.AUTO);
		service.setConnected(HUB, ids[0], false);
		service.setConnected(HUB, ids[1], false);
		service.setConnected(HUB, ids[0], true);
		assertThat(game().paused).isTrue();
		service.setConnected(HUB, ids[1], true);
		assertThat(game().paused).isFalse();
	}

	@Test
	void aGameMasterGameNeverPausesByItself() {
		start(Mode.GAME_MASTER);
		service.setConnected(HUB, ids[0], false);
		assertThat(game().paused).isFalse();
		assertThat(game().players.get(0).connected).isFalse();
	}

	@Test
	void aDeadPlayerDroppingChangesNothing() {
		start(Mode.AUTO);
		game().players.get(2).alive = false;
		service.setConnected(HUB, ids[2], false);
		assertThat(game().paused).isFalse();
	}

	@Test
	void theGameMasterTakesOverAnAutomaticPauseOrEndsIt() {
		start(Mode.AUTO);
		service.setConnected(HUB, ids[0], false);
		service.pause(HUB); // takes the pause over: only he lifts it now
		assertThat(game().paused).isTrue();
		assertThat(game().autoPaused).isFalse();
		service.setConnected(HUB, ids[0], true);
		assertThat(game().paused).isTrue();
		clock.advance(Duration.ofMinutes(10));
		assertThat(game().paused).isTrue();
		service.resume(HUB);
		assertThat(game().paused).isFalse();

		service.setConnected(HUB, ids[1], false);
		assertThat(game().autoPaused).isTrue();
		service.resume(HUB); // lifted by hand: the stale five-minute timer does nothing later
		assertThat(game().paused).isFalse();
		clock.advance(Duration.ofMinutes(6));
		assertThat(game().paused).isFalse();
	}

	@Test
	void anAutomaticPauseSurvivesAReload() {
		MemoryStore store = new MemoryStore();
		ManualClock c = new ManualClock();
		GameService first = new GameService(store, c, c);
		Settings s = Settings.fast(600);
		first.create(HUB, s, 3L);
		for (int i = 0; i < 9; i++) {
			first.join(HUB, ids[i], "p" + i);
		}
		first.start(HUB);
		first.setConnected(HUB, ids[0], false);
		GameService second = new GameService(store, c, c);
		second.loadActiveGames();
		assertThat(second.find(HUB).autoPaused).isTrue();
		c.advance(GameService.AUTO_PAUSE_MAX);
		assertThat(second.find(HUB).paused).isFalse();
	}
}
