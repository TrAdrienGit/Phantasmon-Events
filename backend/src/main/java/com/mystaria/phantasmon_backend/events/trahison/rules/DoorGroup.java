package com.mystaria.phantasmon_backend.events.trahison.rules;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The three bracelets that go through one door in a split, and who votes against whom: the {@code pair}
 * (two bracelets of {@code pairColor}) faces the {@code solo} (one bracelet of {@code soloColor}).
 * Bracelet indices are 0..8. {@code pair} is sorted ascending.
 */
public record DoorGroup(Door door, GameColor pairColor, GameColor soloColor, List<Integer> pair, int solo) {
	public DoorGroup {
		pair = List.copyOf(pair);
	}

	/** The three bracelet indices of this group. */
	public Set<Integer> members() {
		Set<Integer> s = new TreeSet<>(pair);
		s.add(solo);
		return s;
	}
}
