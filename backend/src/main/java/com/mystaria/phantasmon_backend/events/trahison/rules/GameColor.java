package com.mystaria.phantasmon_backend.events.trahison.rules;

/** Neutral bracelet colours. Their themed names (types) only exist in the language files. */
public enum GameColor {
	RED, GREEN, BLUE;

	/** The colour after this one in the cycle RED, GREEN, BLUE. */
	GameColor next() {
		return values()[(ordinal() + 1) % 3];
	}
}
