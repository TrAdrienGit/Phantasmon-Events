package com.mystaria.phantasmon_backend.events.trahison.rules;

import java.util.List;
import java.util.Objects;

/**
 * One of the three doors: an unordered mix of two different colours. {@code first} always has the lower ordinal, so
 * {@code Door.of(RED, GREEN).equals(Door.of(GREEN, RED))}.
 */
public record Door(GameColor first, GameColor second) {

	public Door {
		Objects.requireNonNull(first);
		Objects.requireNonNull(second);
		if (first == second) {
			throw new IllegalArgumentException("A door mixes two different colours");
		}
		if (first.ordinal() > second.ordinal()) {
			GameColor t = first;
			first = second;
			second = t;
		}
	}

	public static Door of(GameColor a, GameColor b) {
		return new Door(a, b);
	}

	/** The three doors, in a fixed order: RED-GREEN, RED-BLUE, GREEN-BLUE. */
	public static final List<Door> ALL = List.of(
		new Door(GameColor.RED, GameColor.GREEN),
		new Door(GameColor.RED, GameColor.BLUE),
		new Door(GameColor.GREEN, GameColor.BLUE));

	public boolean contains(GameColor c) {
		return c == first || c == second;
	}

	/** The other colour of this door. */
	public GameColor other(GameColor c) {
		if (c == first) {
			return second;
		}
		if (c == second) {
			return first;
		}
		throw new IllegalArgumentException(c + " is not on door " + this);
	}
}
