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
	private com.mystaria.phantasmon_backend.events.trahison.layout.LayoutService layouts;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void aGameIsWrittenAtEachChangeAndReloadedWhileUnfinished() {
		String hub = "persist-" + UUID.randomUUID();
		Settings withRoom = Settings.fast(60);
		withRoom.testRoom = "room0"; // D73: a game needs nine rooms in the bank, or a test room
		var plan = com.mystaria.phantasmon_backend.events.trahison.layout.LayoutFixtures.complete();
		Game g = service.create(hub, withRoom, 77L);
		for (int i = 0; i < 9; i++) {
			service.join(hub, new UUID(1, i), "p" + i);
		}
		layouts.save(new UUID(9, 9), hub, new com.mystaria.phantasmon_backend.events.trahison.layout.LayoutRequest(null, false,
			plan.zones(), plan.doors(), plan.roomSlots(), plan.cabins(), plan.hidingSpots())); // a game only starts on a complete plan
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

	@Test
	void theRoomsPlayedInRecentGamesAreFoundInTheTable() {
		Game g = new Game();
		g.id = UUID.randomUUID();
		g.hub = "recent-" + g.id;
		g.createdAt = java.time.Instant.now().plusSeconds(3600);
		RoundState r = new RoundState();
		r.number = 1;
		tools.jackson.databind.node.ObjectNode room = tools.jackson.databind.json.JsonMapper.builder().build().createObjectNode();
		room.putObject("definition").put("id", "played_room");
		r.rooms.add(room);
		g.rounds.add(r);
		store.save(g);
		assertThat(store.recentRooms(UUID.randomUUID(), 3)).containsEntry("played_room", 1);
		assertThat(store.recentRooms(g.id, 3)).doesNotContainKey("played_room");
		assertThat(store.recentRooms(UUID.randomUUID(), 0)).isEmpty();
	}
}
