package com.mystaria.phantasmon_backend.events.trahison.game;

/**
 * AUTO: the end of a stage's timer moves the game on by itself. GAME_MASTER: the timer is only a countdown shown to the
 * players; the game moves on when the game master asks ({@code next}), and he can pause and extend it.
 */
public enum Mode {
	AUTO, GAME_MASTER
}
