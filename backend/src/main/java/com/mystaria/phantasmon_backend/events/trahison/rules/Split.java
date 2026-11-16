package com.mystaria.phantasmon_backend.events.trahison.rules;

import java.util.List;

/**
 * One of the two valid ways to send the nine bracelets through the three doors (see {@link Doors#validSplits}).
 * Holds three groups of three bracelets, one per door.
 */
public record Split(List<DoorGroup> groups) {
	public Split {
		groups = List.copyOf(groups);
	}

	/** The group that goes through the given door. */
	public DoorGroup group(Door door) {
		for (DoorGroup g : groups) {
			if (g.door().equals(door)) {
				return g;
			}
		}
		throw new IllegalArgumentException("No group for " + door);
	}
}
