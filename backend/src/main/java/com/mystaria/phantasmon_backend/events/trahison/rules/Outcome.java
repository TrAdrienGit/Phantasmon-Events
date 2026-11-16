package com.mystaria.phantasmon_backend.events.trahison.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Who is dead and who may open the final door. Bracelet indices 0..8; "wrist points" are the points of the bracelet
 * a player wears from the start (never a picked-up one).
 */
public final class Outcome {

	private Outcome() {
	}

	public static final int START_POINTS = 3;
	public static final int WIN_POINTS = 9;

	/** A bracelet at 0 points means its wearer is dead. (A player can also die by other means, tracked by the caller.) */
	public static boolean isDeadByPoints(int points) {
		return points <= 0;
	}

	/** The final door only reads the wrist bracelet: 9 points or more, and the player alive. */
	public static boolean canOpenFinalDoor(int wristPoints, boolean alive) {
		return alive && wristPoints >= WIN_POINTS;
	}

	/**
	 * Bracelets (length-9 arrays) whose wearer may open the final door. {@code alive[i]} is false for a player who was
	 * killed or is at 0 points; a killed player with 9 points still cannot open it.
	 */
	public static List<Integer> finalDoorOpeners(int[] wristPoints, boolean[] alive) {
		check(wristPoints.length, alive.length);
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < wristPoints.length; i++) {
			if (canOpenFinalDoor(wristPoints[i], alive[i])) {
				out.add(i);
			}
		}
		return out;
	}

	/** Players still marked alive whose bracelet is now at 0 points: they die. */
	public static List<Integer> newlyDead(int[] wristPoints, boolean[] alive) {
		check(wristPoints.length, alive.length);
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < wristPoints.length; i++) {
			if (alive[i] && isDeadByPoints(wristPoints[i])) {
				out.add(i);
			}
		}
		return out;
	}

	/**
	 * D12: whether a playing game master is dead. {@code masters}: the seats of the game masters who play; {@code alive}: the
	 * flags of the seats. Whatever killed him counts (doors, bracelet at 0, and what T6 adds).
	 */
	public static boolean gameMasterKilled(java.util.Collection<Integer> masters, boolean[] alive) {
		for (int m : masters) {
			if (m >= 0 && m < alive.length && !alive[m]) {
				return true;
			}
		}
		return false;
	}

	/** D12, D44: when a playing game master is killed, the other participants still alive at that moment win. */
	public static List<Integer> winnersWhenGameMasterKilled(java.util.Collection<Integer> masters, boolean[] alive) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < alive.length; i++) {
			if (!masters.contains(i) && alive[i]) {
				out.add(i);
			}
		}
		return out;
	}

	private static void check(int a, int b) {
		if (a != Assignment.BRACELETS || b != Assignment.BRACELETS) {
			throw new IllegalArgumentException("Expected arrays of length 9");
		}
	}
}
