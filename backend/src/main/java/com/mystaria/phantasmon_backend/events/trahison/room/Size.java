package com.mystaria.phantasmon_backend.events.trahison.room;

/** The extent of a slot in blocks (at least 1 each); the slot's position is its minimum corner. */
public record Size(int x, int y, int z) {

	public static final Size ONE = new Size(1, 1, 1);
}
