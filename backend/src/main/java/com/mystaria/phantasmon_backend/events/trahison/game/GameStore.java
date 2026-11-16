package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.List;

/** Where games are kept between restarts. */
public interface GameStore {

	/** Inserts or replaces the game's row. */
	void save(Game game);

	/** Every game that has not ended. */
	List<Game> loadActive();
}
