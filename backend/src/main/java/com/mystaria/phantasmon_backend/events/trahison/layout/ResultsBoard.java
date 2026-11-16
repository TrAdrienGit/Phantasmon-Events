package com.mystaria.phantasmon_backend.events.trahison.layout;

/**
 * The big board of the results in the hall (T2.4, optional in the plan): {@code position} is the lower corner of the board
 * that is on the left when one faces it, {@code facing} the side the board looks towards (one of
 * {@link LayoutValidator#ORIENTATIONS}: the side the readers stand on), {@code width} and {@code height} its size in blocks.
 */
public record ResultsBoard(Pos position, String facing, Integer width, Integer height) {

	public static final int MIN_SIZE = 4;
	public static final int MAX_SIZE = 64;
}
