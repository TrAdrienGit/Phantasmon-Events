package com.mystaria.phantasmon_backend.events.trahison.room;

/**
 * Where a clue of a step shows: the clue {@code role} (one of the mechanism's clue roles) appears in {@code slot}.
 * {@code when} is {@code open} (default: as soon as the step is open, so the players can solve it) or {@code solved}
 * (only once the step is solved: its reward).
 */
public record Give(String clue, String slot, String when) {

	public static final String OPEN = "open";
	public static final String SOLVED = "solved";

	public Give {
		when = when == null ? OPEN : when;
	}

	public boolean whenSolved() {
		return SOLVED.equals(when);
	}
}
