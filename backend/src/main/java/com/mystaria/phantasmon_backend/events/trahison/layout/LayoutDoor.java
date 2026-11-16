package com.mystaria.phantasmon_backend.events.trahison.layout;

import java.util.List;

/**
 * A door: a real opening closed by fake blocks. {@code type} is one of {@link LayoutValidator#DOOR_TYPES}; {@code block}
 * is the box of fake blocks; {@code zoneA} and {@code zoneB} the two zones it links. A {@code color} door also has its
 * {@code round} (1 to 3) and its two {@code colors} (lower-case {@code GameColor} names, any order).
 */
public record LayoutDoor(String id, String type, Box block, String zoneA, String zoneB, Integer round, List<String> colors) {

	public LayoutDoor {
		colors = colors == null ? List.of() : List.copyOf(colors);
	}
}
