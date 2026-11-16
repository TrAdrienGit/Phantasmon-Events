package com.mystaria.events.trahison.screen;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.Vote;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.GameColor;
import com.mystaria.events.trahison.ui.TrahisonUi.Glyph;

/** Names and small shared drawings of the game's screens: game colours, votes, players. Display only. */
final class Shown {

	private static final String T = "phantasmon_events.trahison.";

	private Shown() {
	}

	static Component colorName(GameColor color) {
		return Component.translatable(T + "color." + color.name().toLowerCase(Locale.ROOT));
	}

	static Component side(Boolean pair) {
		return Component.translatable(T + (Boolean.TRUE.equals(pair) ? "status.pair" : "status.solo"));
	}

	static Component voteName(Vote vote) {
		return Component.translatable(T + "vote." + vote.name().toLowerCase(Locale.ROOT));
	}

	/** A game colour as a pill: its symbol, then its name. Returns the width. */
	static int colorPill(GuiGraphics g, int x, int y, GameColor color) {
		return TrahisonUi.glyphPill(g, x, y, color.glyph, colorName(color), color.dark, color.light, TrahisonPalette.TEXT);
	}

	/** Width {@link #colorPill} would have. */
	static int colorPillWidth(GameColor color) {
		return TrahisonUi.width(colorName(color)) + 22;
	}

	static Glyph voteGlyph(Vote vote) {
		return vote == Vote.ALLY ? Glyph.ALLY : Glyph.BETRAY;
	}

	static int voteTone(Vote vote) {
		return vote == Vote.ALLY ? TrahisonPalette.ALLY : TrahisonPalette.BETRAY;
	}

	static int voteDark(Vote vote) {
		return vote == Vote.ALLY ? TrahisonPalette.ALLY_DARK : TrahisonPalette.BETRAY_DARK;
	}

	/** A vote as a pill; {@code cast} false (a default vote nobody cast) is drawn muted. Returns the width. */
	static int votePill(GuiGraphics g, int x, int y, Vote vote, boolean cast) {
		return TrahisonUi.glyphPill(g, x, y, voteGlyph(vote), voteName(vote), cast ? voteDark(vote) : TrahisonPalette.PANEL_SUNKEN,
				cast ? voteTone(vote) : TrahisonPalette.TEXT_MUTED, cast ? TrahisonPalette.TEXT : TrahisonPalette.TEXT_MUTED);
	}

	static int votePillWidth(Vote vote) {
		return TrahisonUi.width(voteName(vote)) + 22;
	}

	static String names(View view, List<Integer> players) {
		return players.stream().map(view::nameOf).collect(Collectors.joining(" + "));
	}
}
