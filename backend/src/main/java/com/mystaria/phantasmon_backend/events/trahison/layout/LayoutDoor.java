package com.mystaria.phantasmon_backend.events.trahison.layout;

import java.util.List;

/**
 * A door: a real opening closed by fake blocks. {@code type} is one of {@link LayoutValidator#DOOR_TYPES}; {@code block}
 * is the box of fake blocks; {@code zoneA} and {@code zoneB} the two zones it links. A {@code color} door also has its
 * {@code round} (1 to 3) and its two {@code colors} (lower-case {@code GameColor} names, any order).
 *
 * <p>A colour door is crossed from {@code zoneA} (the hall side) to {@code zoneB} (the room side). {@code threshold} (colour
 * doors only, optional) is the zone in front of it where the players present them bracelets; without it the threshold is
 * {@code zoneA}.
 */
public record LayoutDoor(String id, String type, Box block, String zoneA, String zoneB, Integer round, List<String> colors,
		String threshold) {

	public LayoutDoor {
		colors = colors == null ? List.of() : List.copyOf(colors);
	}

	/** A door without a threshold zone of its own. */
	public LayoutDoor(String id, String type, Box block, String zoneA, String zoneB, Integer round, List<String> colors) {
		this(id, type, block, zoneA, zoneB, round, colors, null);
	}

	/** The zone where the bracelets are presented to this (colour) door: {@code threshold}, else {@code zoneA}. */
	public String thresholdZone() {
		return threshold != null && !threshold.isBlank() ? threshold : zoneA;
	}
}
