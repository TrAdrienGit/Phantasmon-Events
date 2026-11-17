package com.mystaria.phantasmon_backend.events.trahison.game;

/**
 * Told by {@link GameService} after each change. Called <b>while the game is locked</b>, so a listener sees a
 * consistent state and may build its per-recipient views from {@code game} directly; it must be quick and must not call
 * back into {@link GameService} from another thread. An exception thrown by a listener is logged and ignored.
 */
public interface GameListener {

	/** A game was created, entered a new stage, ended, or was paused/resumed/extended (the deadline changed). */
	default void phaseChanged(Game game) {
	}

	/**
	 * A change that does not move the stage: {@code patch} is one of {@code join}, {@code leave}, {@code door},
	 * {@code vote} (someone voted; the side is not told), {@code room}, {@code points}, {@code connection},
	 * {@code timer}.
	 */
	default void changed(Game game, String patch) {
	}

	/** A player's zone changed (at most once per second per game): only the game masters' views show zones. */
	default void zonesChanged(Game game) {
	}

	/** A player went into a zone of the plan for the first time (T11.2): only his own view changes. */
	default void exploredChanged(Game game, int seat) {
	}

	/** A private line for one seat (a role, a letter): {@code key} is a language key, {@code args} its arguments. Never a bot. */
	default void whispered(Game game, int seat, String key, java.util.List<String> args) {
	}
}
