package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A store that keeps the JSON text, so every save goes through the real serialization. */
final class MemoryStore implements GameStore {

	final Map<UUID, String> rows = new LinkedHashMap<>();
	int saves;

	@Override
	public void save(Game game) {
		rows.put(game.id, GameJson.write(game));
		saves++;
	}

	@Override
	public List<Game> loadActive() {
		return rows.values().stream().map(GameJson::read).filter(g -> g.endedAt == null).toList();
	}
}
