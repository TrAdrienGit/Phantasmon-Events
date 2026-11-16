package com.mystaria.phantasmon_backend.events.trahison.game;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code trahison_games} (migration V6). Plain JDBC rather than a JPA entity: the state is one opaque jsonb document
 * written whole, which an entity would only wrap.
 */
public class JdbcGameStore implements GameStore {

	private final JdbcTemplate jdbc;

	public JdbcGameStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(Game game) {
		Instant now = Instant.now();
		jdbc.update("""
				INSERT INTO trahison_games (id, hub, state, created_at, updated_at, ended_at)
				VALUES (?, ?, ?::jsonb, ?, ?, ?)
				ON CONFLICT (id) DO UPDATE
				SET state = EXCLUDED.state, updated_at = EXCLUDED.updated_at, ended_at = EXCLUDED.ended_at
				""", game.id, game.hub, GameJson.write(game),
				Timestamp.from(game.createdAt != null ? game.createdAt : now), Timestamp.from(now),
				game.endedAt == null ? null : Timestamp.from(game.endedAt));
	}

	@Override
	public List<Game> loadActive() {
		return jdbc.query("SELECT state::text FROM trahison_games WHERE ended_at IS NULL ORDER BY created_at",
				(rs, i) -> GameJson.read(rs.getString(1)));
	}

	@Override
	public java.util.Map<String, Integer> recentRooms(java.util.UUID exclude, int games) {
		java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
		if (games <= 0) {
			return out;
		}
		List<String> states = jdbc.query("""
				SELECT state::text FROM trahison_games WHERE id <> ? AND jsonb_array_length(state -> 'rounds') > 0
				ORDER BY created_at DESC LIMIT ?
				""", (rs, i) -> rs.getString(1), exclude, games);
		int ago = 0;
		for (String json : states) {
			ago++;
			Game g = GameJson.read(json);
			for (RoundState r : g.rounds) {
				for (tools.jackson.databind.JsonNode room : r.rooms) {
					if (room != null && room.path("definition").hasNonNull("id")) {
						out.putIfAbsent(room.path("definition").get("id").asString(), ago);
					}
				}
			}
		}
		return out;
	}
}
