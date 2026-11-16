package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.List;

/**
 * A vote cabin held by the group it serves during VOTE (D4, D5). Born when a living human of the group enters the cabin.
 * <ul>
 *   <li>waiting ({@code !locked}): the door is closed to everybody who is not of the group, and open to the group, until
 *       {@code windowUntilMs} (epoch ms; 0 when there is no window) so that the partner of a pair can join;</li>
 *   <li>{@code locked}: the door is closed to everybody (nobody leaves before the group has voted);</li>
 *   <li>{@code released}: the group voted: the door opens for whoever is inside, and stays closed to the others until the
 *       group has left.</li>
 * </ul>
 * {@code first}: the player who entered first (or who closed the cabin); {@code expected}: the living humans of the group
 * when it was born, those who must be inside for the cabin to lock at once.
 */
public record CabinLock(int first, long windowUntilMs, boolean locked, boolean released, List<Integer> expected) {

	public CabinLock {
		expected = expected == null ? List.of() : List.copyOf(expected);
	}

	public boolean waiting() {
		return !locked && !released;
	}

	public CabinLock lockedNow() {
		return new CabinLock(first, 0, true, false, expected);
	}

	public CabinLock releasedNow() {
		return new CabinLock(first, 0, true, true, expected);
	}

	/** The view's name of the state: {@code WAITING}, {@code LOCKED} or {@code RELEASED}. */
	public String state() {
		return released ? "RELEASED" : locked ? "LOCKED" : "WAITING";
	}
}
