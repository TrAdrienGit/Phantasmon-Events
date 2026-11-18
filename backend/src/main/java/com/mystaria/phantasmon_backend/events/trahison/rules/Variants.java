package com.mystaria.phantasmon_backend.events.trahison.rules;

import java.util.SplittableRandom;

/**
 * T9.4: which variant of a hostess line everybody hears. The backend draws a number from the game seed, the line's key and the
 * round; the client takes it modulo the number of variants it has, so every player hears the same one and a replay of the same
 * game says the same things.
 */
public final class Variants {

	/** Upper bound (exclusive) of a variant number. */
	public static final int RANGE = 1_000_000;

	private Variants() {
	}

	/** A stable number in {@code [0, RANGE)} for this game, line and round. */
	public static int pick(long gameSeed, String key, int round) {
		long mixed = gameSeed ^ (key.hashCode() * 0x9E3779B97F4A7C15L) ^ (round * 0xC2B2AE3D27D4EB4FL);
		return new SplittableRandom(mixed).nextInt(RANGE);
	}
}
