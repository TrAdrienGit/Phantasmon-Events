package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.Locale;

/** How the bots vote; set by the game master (protocol value: lower case). */
public enum BotPolicy {
	ALLY, BETRAY, RANDOM;

	/** Parses a protocol value ({@code ally}, {@code betray}, {@code random}), case-insensitively; null when unknown. */
	public static BotPolicy parse(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
