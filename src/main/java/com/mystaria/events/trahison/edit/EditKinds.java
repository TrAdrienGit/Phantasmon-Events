package com.mystaria.events.trahison.edit;

import java.util.List;

import com.mystaria.events.trahison.ui.TrahisonPalette;

/** The kinds of element the plan holds, their types, and the colour each type is drawn in (palette tokens only). */
final class EditKinds {

	enum Kind {
		ZONE("zone"), DOOR("door"), SLOT("slot"), CABIN("cabin"), SPOT("spot");

		final String key;

		Kind(String key) {
			this.key = key;
		}
	}

	// Same values as the backend's LayoutValidator.
	static final List<String> ZONE_TYPES = List.of("hall", "corridor", "doors", "room_slot", "vote", "cabin", "final", "alcove");
	static final List<String> DOOR_TYPES = List.of("color", "room_exit", "cabin", "final", "plain");
	static final List<String> SIDES = List.of("pair", "solo");
	static final List<String> ORIENTATIONS = List.of("north", "east", "south", "west");
	/** The three colour pairs, as a cabin's {@code serves} and a colour door's two colours. */
	static final List<String> PAIRS = List.of("red_green", "red_blue", "green_blue");
	/** Edge length of a room's place, in blocks, and its height. */
	static final int SLOT_SIDE = 48;
	static final int SLOT_HEIGHT = 24;

	private EditKinds() {
	}

	static int zoneColor(String type) {
		return switch (type == null ? "" : type) {
			case "hall" -> TrahisonPalette.ACCENT;
			case "corridor" -> TrahisonPalette.EDGE_LIGHT;
			case "doors" -> TrahisonPalette.GAME_MASTER;
			case "room_slot" -> TrahisonPalette.BLUE;
			case "vote" -> TrahisonPalette.BETRAY;
			case "cabin" -> TrahisonPalette.BETRAY_DARK;
			case "final" -> TrahisonPalette.SUCCESS;
			case "alcove" -> TrahisonPalette.GHOST;
			default -> TrahisonPalette.TEXT_MUTED;
		};
	}

	static int doorColor(String type) {
		return switch (type == null ? "" : type) {
			case "color" -> TrahisonPalette.ALLY;
			case "room_exit" -> TrahisonPalette.GREEN_LIGHT;
			case "cabin" -> TrahisonPalette.BETRAY;
			case "final" -> TrahisonPalette.SUCCESS;
			default -> TrahisonPalette.TEXT_MUTED;
		};
	}

	static final int SLOT_COLOR = TrahisonPalette.BLUE_LIGHT;
	static final int SPOT_COLOR = TrahisonPalette.RED_LIGHT;
	static final int SELECTED_COLOR = TrahisonPalette.TEXT;
	static final int SELECTION_COLOR = TrahisonPalette.ACCENT_HOT;

	/** The two colour names of a pair id ({@code red_green}). */
	static List<String> colorsOf(String pair) {
		return List.of(pair.split("_"));
	}

	/** The pair id of a door's two colours in any order, or null. */
	static String pairOf(List<String> colors) {
		if (colors == null || colors.size() != 2) {
			return null;
		}
		for (String pair : PAIRS) {
			List<String> c = colorsOf(pair);
			if (c.containsAll(colors) && colors.containsAll(c)) {
				return pair;
			}
		}
		return null;
	}
}
