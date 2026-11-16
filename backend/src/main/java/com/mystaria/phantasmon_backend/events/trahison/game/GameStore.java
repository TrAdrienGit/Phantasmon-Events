package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.List;

/** Where games are kept between restarts. */
public interface GameStore {

	/** Inserts or replaces the game's row. */
	void save(Game game);

	/** Every game that has not ended. */
	List<Game> loadActive();

	/**
	 * The rooms played in the last {@code games} games other than {@code exclude} (T4.1): room id to how many games ago it was
	 * played (1 = the latest game that played rooms). Only rooms actually started count. Empty by default.
	 */
	default java.util.Map<String, Integer> recentRooms(java.util.UUID exclude, int games) {
		return java.util.Map.of();
	}
}
