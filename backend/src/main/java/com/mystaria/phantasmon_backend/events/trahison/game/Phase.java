package com.mystaria.phantasmon_backend.events.trahison.game;

/** The stages of a game (Documentation/trahison/architecture.md section 4). */
public enum Phase {
	LOBBY, PROLOGUE, DOORS, ROOMS, FREE_TIME, VOTE, RESULTS, FINAL, ENDED;

	/** True for the stages that run on a timer (everything between the lobby and the end). */
	public boolean timed() {
		return this != LOBBY && this != ENDED;
	}
}
