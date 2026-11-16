package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mystaria.phantasmon_backend.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/** The game engine against the real {@code trahison_games} table (migration V6), through the wired beans. */
@Import(TestcontainersConfiguration.class)
// Same properties as the other integration tests: one shared Spring context.
@SpringBootTest(properties = { "phantasmon.logging.enabled=false", "phantasmon.admin.file=build/test-admins.txt" })
class GamePersistenceIntegrationTest {

	@Autowired
	private GameService service;

	@Autowired
	private GameStore store;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void aGameIsWrittenAtEachChangeAndReloadedWhileUnfinished() {
		String hub = "persist-" + UUID.randomUUID();
		Game g = service.create(hub, Settings.fast(60), 77L);
		for (int i = 0; i < 9; i++) {
			service.join(hub, new UUID(1, i), "p" + i);
		}
		service.start(hub);
		service.next(hub); // DOORS

		String state = jdbc.queryForObject("SELECT state::text FROM trahison_games WHERE id = ?", String.class, g.id);
		Game back = GameJson.read(state);
		assertThat(back.phase).isEqualTo(Phase.DOORS);
		assertThat(back.players).hasSize(9);
		assertThat(back.currentRound().placements).hasSize(9);
		assertThat(store.loadActive()).extracting(x -> x.id).contains(g.id);

		service.end(hub);
		assertThat(jdbc.queryForObject("SELECT ended_at IS NOT NULL FROM trahison_games WHERE id = ?", Boolean.class, g.id)).isTrue();
		assertThat(store.loadActive()).extracting(x -> x.id).doesNotContain(g.id);
	}
}
