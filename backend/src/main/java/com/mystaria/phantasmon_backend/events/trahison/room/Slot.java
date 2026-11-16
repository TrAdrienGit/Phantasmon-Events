package com.mystaria.phantasmon_backend.events.trahison.room;

import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;

/**
 * A marked place of a room where the game shows something. {@code position} is relative to the origin of the room's
 * place (x and z in 0..47, y in 0..23); {@code orientation} is {@code north|east|south|west}; {@code size} defaults to
 * one block.
 */
public record Slot(String id, String kind, Pos position, String orientation, Size size) {

	public Slot {
		orientation = orientation == null ? "north" : orientation;
		size = size == null ? Size.ONE : size;
	}
}
