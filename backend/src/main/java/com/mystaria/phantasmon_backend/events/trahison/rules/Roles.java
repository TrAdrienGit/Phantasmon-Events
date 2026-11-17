package com.mystaria.phantasmon_backend.events.trahison.rules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** The secret roles' pure rules (T7.1): the draw, and who fulfilled his objective at the end. */
public final class Roles {

	private Roles() {
	}

	/** Points the infiltrator gets for betraying in round 1. */
	public static final int INFILTRATOR_BONUS = 1;
	/** Points of a right bet (once per game). */
	public static final int GAMBLER_BONUS = 1;
	/** The ferryman wins with at least this many ghosts at the end. */
	public static final int FERRYMAN_GHOSTS = 2;
	/** The guardian wins when at least this many players walk through the final door. */
	public static final int GUARDIAN_ESCAPED = 5;
	public static final int MAX_ROLES = 6;

	/**
	 * Hands {@code count} different roles of {@code active} to as many different seats of {@code eligible}, from {@code seed}
	 * only. Fewer when there are not enough roles or seats. The result is in seat order.
	 */
	public static Map<Integer, Role> draw(long seed, Collection<Role> active, int count, Collection<Integer> eligible) {
		List<Role> pool = new ArrayList<>();
		for (Role r : Role.drawable()) { // fixed order, so the same seed always gives the same draw
			if (active.contains(r)) {
				pool.add(r);
			}
		}
		List<Integer> seats = new ArrayList<>(new java.util.TreeSet<>(eligible));
		Random random = new Random(seed ^ 0x7E57ED5EEDL);
		Collections.shuffle(pool, random);
		Collections.shuffle(seats, random);
		int n = Math.max(0, Math.min(count, Math.min(pool.size(), seats.size())));
		Map<Integer, Role> picked = new java.util.TreeMap<>();
		for (int i = 0; i < n; i++) {
			picked.put(seats.get(i), pool.get(i));
		}
		return new LinkedHashMap<>(picked);
	}

	/**
	 * The seats whose objective is met at the end. {@code infiltratorBetrayed}: the infiltrators who betrayed in round 1;
	 * {@code winners}: who walked through the final door. The seer and the gambler have powers, not objectives; the game
	 * master who plays has none either.
	 */
	public static List<Integer> objectivesMet(Map<Integer, Role> roles, boolean[] alive, Collection<Integer> winners,
			Set<Integer> infiltratorBetrayed) {
		int ghosts = 0;
		for (boolean a : alive) {
			if (!a) {
				ghosts++;
			}
		}
		List<Integer> met = new ArrayList<>();
		for (Map.Entry<Integer, Role> e : new java.util.TreeMap<>(roles).entrySet()) {
			int seat = e.getKey();
			boolean ok = switch (e.getValue()) {
				case INFILTRATOR -> infiltratorBetrayed.contains(seat);
				case FERRYMAN -> ghosts >= FERRYMAN_GHOSTS;
				case GUARDIAN -> winners.size() >= GUARDIAN_ESCAPED;
				case LONER -> winners.size() == 1 && winners.contains(seat);
				case SEER, GAMBLER, GAME_MASTER -> false;
			};
			if (ok) {
				met.add(seat);
			}
		}
		return met;
	}

	/** Whether this role has a win condition (shown as such at the end). */
	public static boolean hasObjective(Role role) {
		return role == Role.INFILTRATOR || role == Role.FERRYMAN || role == Role.GUARDIAN || role == Role.LONER;
	}
}
