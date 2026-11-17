package com.mystaria.phantasmon_backend.events.trahison.pokemon;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** A field power a partner Pokemon can hold (T8.3): what its trainer may do at a {@code power_spot}. */
public enum Power {
	FLASH, STRENGTH, CUT, SURF;

	public static final List<Power> ALL = List.of(values());

	/** The power named by a room definition ({@code flash}, {@code FLASH}...), empty when it is none of the four. */
	public static Optional<Power> parse(String name) {
		if (name == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
