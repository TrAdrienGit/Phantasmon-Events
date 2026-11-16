package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.List;

/**
 * The glyph ids mechanisms may show and clue with ({@link ClueType#SYMBOLS} values are arrays of these ids). The
 * client draws them in {@code minigame/Glyphs}, which must define exactly the same ids: a test on each side keeps the
 * two lists equal, and adding a glyph means adding it in both places. The names describe the shape only; the glyphs are
 * abstract marks of our own.
 */
public final class SymbolSet {

	/** Order matters only for stability: a seed draws from this list, so reordering changes every old tirage. */
	public static final List<String> IDS = List.of(
			"eye", "spiral", "trident", "crescent", "hourglass", "key", "dot_triangle", "cross_circle", "wave", "fork",
			"star", "diamond", "gear", "bolt", "hexagon", "loop_arrow", "ladder", "trio", "arch", "chevrons");

	private SymbolSet() {
	}
}
