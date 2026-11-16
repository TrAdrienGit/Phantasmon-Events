package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.List;

/**
 * The final door while it is open (T2.5): {@code untilMs} is the epoch millisecond it closes for ever, {@code openers} the
 * living players whose wrist bracelet read 9 or more when it opened (only they pass: the door is closed for everybody else),
 * {@code openedBy} the one who used the pupitre, {@code crossed} those of them who have walked through so far.
 */
public record FinalDoorWindow(long untilMs, List<Integer> openers, int openedBy, List<Integer> crossed) {

	public FinalDoorWindow {
		openers = openers == null ? List.of() : List.copyOf(openers);
		crossed = crossed == null ? List.of() : List.copyOf(crossed);
	}

	/** The same window with {@code player} counted as crossed. */
	FinalDoorWindow withCrossed(int player) {
		if (crossed.contains(player)) {
			return this;
		}
		List<Integer> next = new java.util.ArrayList<>(crossed);
		next.add(player);
		return new FinalDoorWindow(untilMs, openers, openedBy, next);
	}
}
