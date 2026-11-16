package com.mystaria.phantasmon_backend.events.trahison.game;

import tools.jackson.databind.json.JsonMapper;

/** Lossless JSON form of a {@link Game}: what is stored in {@code trahison_games.state}. */
public final class GameJson {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private GameJson() {
	}

	public static String write(Game game) {
		return MAPPER.writeValueAsString(game);
	}

	public static Game read(String json) {
		return MAPPER.readValue(json, Game.class);
	}
}
