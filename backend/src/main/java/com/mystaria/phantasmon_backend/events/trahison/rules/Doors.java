package com.mystaria.phantasmon_backend.events.trahison.rules;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The three doors. Each door mixes two colours and lets through a pair of one colour and the solo of the other.
 * Since each colour's pair must go with the solo of a different colour, there are exactly two valid splits of the
 * nine bracelets (the two cyclic orientations):
 * <ul>
 *   <li>split 0: pair RED + solo GREEN, pair GREEN + solo BLUE, pair BLUE + solo RED;</li>
 *   <li>split 1: pair RED + solo BLUE, pair BLUE + solo GREEN, pair GREEN + solo RED.</li>
 * </ul>
 * Bracelets are identified by index 0..8.
 */
public final class Doors {

	private Doors() {
	}

	/**
	 * A door opens as soon as the presented bracelets include a PAIR bracelet of one of its colours and a SOLO
	 * bracelet of its other colour, whatever the number of carriers (a dead bracelet kept by a player counts).
	 * Presenting extra bracelets, or several of the same kind, changes nothing.
	 */
	public static boolean canOpen(Door door, Assignment assignment, Collection<Integer> presented) {
		boolean[] pair = new boolean[3];
		boolean[] solo = new boolean[3];
		for (int b : presented) {
			if (b < 0 || b >= Assignment.BRACELETS) {
				throw new IllegalArgumentException("Bracelet index out of range: " + b);
			}
			GameColor c = assignment.color(b);
			if (!door.contains(c)) {
				continue;
			}
			if (assignment.status(b) == Status.PAIR) {
				pair[c.ordinal()] = true;
			} else {
				solo[c.ordinal()] = true;
			}
		}
		GameColor a = door.first();
		GameColor b = door.second();
		return (pair[a.ordinal()] && solo[b.ordinal()]) || (pair[b.ordinal()] && solo[a.ordinal()]);
	}

	/** The two valid splits of the nine bracelets, index 0 then index 1 (see the class comment). */
	public static List<Split> validSplits(Assignment assignment) {
		return List.of(split(assignment, 1), split(assignment, 2));
	}

	/**
	 * Finds which valid split the players agreed on, given who ended up behind each door (the set of bracelets that
	 * actually passed, keyed by door). Empty when the groups match neither split.
	 */
	public static Optional<Split> findSplit(Assignment assignment, Map<Door, Set<Integer>> membersByDoor) {
		for (Split s : validSplits(assignment)) {
			boolean ok = true;
			for (DoorGroup g : s.groups()) {
				if (!g.members().equals(Set.copyOf(membersByDoor.getOrDefault(g.door(), Set.of())))) {
					ok = false;
					break;
				}
			}
			if (ok) {
				return Optional.of(s);
			}
		}
		return Optional.empty();
	}

	/** offset 1: the pair of colour c goes with the solo of the next colour; offset 2: of the one after. */
	private static Split split(Assignment assignment, int offset) {
		GameColor[] colors = GameColor.values();
		DoorGroup[] groups = new DoorGroup[3];
		for (int i = 0; i < 3; i++) {
			GameColor pairColor = colors[i];
			GameColor soloColor = colors[(i + offset) % 3];
			Door door = Door.of(pairColor, soloColor);
			groups[Door.ALL.indexOf(door)] = new DoorGroup(door, pairColor, soloColor,
				assignment.pair(pairColor), assignment.solo(soloColor));
		}
		return new Split(List.of(groups));
	}
}
