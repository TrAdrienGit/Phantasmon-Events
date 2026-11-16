package com.mystaria.phantasmon_backend.events.trahison.rules;

import java.util.HashSet;
import java.util.Set;

/**
 * What happened in previous rounds, used by {@link Assignment#generate} to avoid repeats. Bracelet indices 0..8.
 * Immutable: {@link #after} returns a new history.
 *
 * @param paired unordered couples that were in the same pair
 * @param faced  unordered couples that faced each other in a vote (a pair member against the solo)
 */
public record History(Set<Link> paired, Set<Link> faced) {

	public static final History EMPTY = new History(Set.of(), Set.of());

	/** An unordered couple of bracelets; stored with {@code lo < hi}. */
	public record Link(int lo, int hi) {
		public Link {
			if (lo == hi) {
				throw new IllegalArgumentException("A link needs two different bracelets");
			}
			if (lo > hi) {
				int t = lo;
				lo = hi;
				hi = t;
			}
		}

		public static Link of(int a, int b) {
			return new Link(a, b);
		}
	}

	public History {
		paired = Set.copyOf(paired);
		faced = Set.copyOf(faced);
	}

	/** Adds the pairs of a round, and the facings of the split the players actually chose. */
	public History after(Assignment assignment, Split chosen) {
		History h = afterPairingOnly(assignment);
		Set<Link> f = new HashSet<>(h.faced);
		for (DoorGroup g : chosen.groups()) {
			for (int p : g.pair()) {
				f.add(Link.of(p, g.solo()));
			}
		}
		return new History(h.paired, f);
	}

	/** Adds the pairs of a round only (use when the chosen split is unknown, for example when the round was skipped). */
	public History afterPairingOnly(Assignment assignment) {
		Set<Link> p = new HashSet<>(paired);
		for (GameColor c : GameColor.values()) {
			var pair = assignment.pair(c);
			p.add(Link.of(pair.get(0), pair.get(1)));
		}
		return new History(p, faced);
	}
}
