package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.Set;
import java.util.TreeSet;

/**
 * Whether one door of the plan is open. {@code openFor} is the set of player indices who see it open although it is
 * closed for everybody else (the final door, which only lets 9-point bracelets through: each client decides to remove
 * its own fake wall from the view it gets). Empty for every door in T2.2.
 */
public record DoorState(boolean open, Set<Integer> openFor) {

	public static final DoorState OPEN = new DoorState(true, Set.of());
	public static final DoorState CLOSED = new DoorState(false, Set.of());

	public DoorState {
		openFor = openFor == null ? Set.of() : Set.copyOf(new TreeSet<>(openFor));
	}

	public static DoorState of(boolean open) {
		return open ? OPEN : CLOSED;
	}

	/** What a viewer sees: open for everybody, or closed but open for him. {@code player}: his index, -1 if none. */
	public boolean openFor(int player) {
		return open || player >= 0 && openFor.contains(player);
	}
}
