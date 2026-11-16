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
}
