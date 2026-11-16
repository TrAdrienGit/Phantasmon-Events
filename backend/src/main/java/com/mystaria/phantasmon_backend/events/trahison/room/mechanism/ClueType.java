package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.Locale;

/** The kinds of clue a mechanism can produce (architecture.md section 7). */
public enum ClueType {
	/** A short text; value is a string. */
	TEXT,
	/** A sequence of digits; value is a string such as {@code "371"}. */
	DIGITS,
	/** A sequence of symbol ids; value is an array of strings. */
	SYMBOLS,
	/** A sequence of colour names; value is an array of strings. */
	COLORS,
	/** An image id the client knows; value is a string. */
	IMAGE,
	/**
	 * A document to read: value is an array of pages, each a string (the lines of a page; a line {@code @image:<id>} shows the
	 * picture {@code textures/trahison/docs/<id>.png}).
	 */
	DOCUMENT,
	/** An item the players can pick up; value is the item id (a string). */
	ITEM;

	public String wire() {
		return name().toLowerCase(Locale.ROOT);
	}
}
