package com.mystaria.phantasmon_backend.events.trahison.game;

import com.mystaria.phantasmon_backend.events.trahison.layout.Layout;

/** Where a game finds the plan of its hub ({@code LayoutService} in production). */
@FunctionalInterface
public interface LayoutLookup {

	/** The plan of the hub at that version; version 0 or less: the latest. An unknown version: the latest. */
	Layout find(String hub, int version);
}
