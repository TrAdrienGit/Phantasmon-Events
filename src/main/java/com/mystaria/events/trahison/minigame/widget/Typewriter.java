package com.mystaria.events.trahison.minigame.widget;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Text that writes itself letter by letter at {@link TrahisonUi#TYPEWRITER_CPS} characters per second, pausing
 * 300 ms on {@code . ! ?}, with a pulsing block cursor. A click or the space key, through {@link #skip}, shows it all.
 * Not a clickable widget: {@link #draw} paints it where the caller wants and returns its height.
 */
public final class Typewriter {

	private static final long PAUSE_MS = 300;
	private final Component text;
	private long startMs;
	private boolean skipped;
	private List<String> cachedLines = List.of();
	private int cachedWidth = -1;

	public Typewriter(Component text) {
		this.text = text;
		this.startMs = System.currentTimeMillis();
	}

	public void restart() {
		startMs = System.currentTimeMillis();
		skipped = false;
	}

	public void skip() {
		skipped = true;
	}

	/** Characters of {@code full} visible now, counting the pauses on sentence ends. */
	private int visible(String full) {
		if (skipped) {
			return full.length();
		}
		long elapsed = System.currentTimeMillis() - startMs;
		long perChar = (long) (1000f / TrahisonUi.TYPEWRITER_CPS);
		int shown = 0;
		long t = 0;
		for (int i = 0; i < full.length(); i++) {
			t += perChar;
			if (t > elapsed) {
				break;
			}
			shown = i + 1;
			char c = full.charAt(i);
			if (c == '.' || c == '!' || c == '?') {
				t += PAUSE_MS;
			}
		}
		return shown;
	}

	public boolean done() {
		String full = text.getString();
		return visible(full) >= full.length();
	}

	private List<String> lines(int width) {
		if (width != cachedWidth) {
			cachedWidth = width;
			cachedLines = new ArrayList<>();
			Style style = Style.EMPTY.withFont(ResourceLocation.parse("uniform"));
			for (var piece : Minecraft.getInstance().font.getSplitter().splitLines(text.getString(), Math.max(10, width), style)) {
				cachedLines.add(piece.getString());
			}
		}
		return cachedLines;
	}

	/** Height the whole text takes at {@code width}. */
	public int height(int width) {
		return lines(width).size() * TrahisonUi.LINE;
	}

	/** Draws the part written so far; returns the full height. */
	public int draw(GuiGraphics g, int x, int y, int width, int color) {
		List<String> lines = lines(width);
		String full = text.getString();
		int left = visible(full);
		int consumed = 0;
		int cursorX = x;
		int cursorY = y;
		for (int i = 0; i < lines.size() && left > 0; i++) {
			String line = lines.get(i);
			// splitLines drops the spaces at wrap points: count the characters it kept, then skip the lost blanks.
			int take = Math.min(left, line.length());
			String part = line.substring(0, take);
			MenuUi.text(g, Component.literal(part), x, y + i * TrahisonUi.LINE, width + 8, color);
			cursorX = x + MenuUi.width(Component.literal(part));
			cursorY = y + i * TrahisonUi.LINE;
			left -= take;
			consumed += take;
			while (consumed < full.length() && left > 0 && Character.isWhitespace(full.charAt(consumed)) && take == line.length()) {
				consumed++;
				left--;
			}
		}
		boolean typing = !done();
		if (typing || TrahisonUi.pulse(1200) > 0.5f) {
			g.fill(cursorX + 1, cursorY, cursorX + 6, cursorY + 8, TrahisonUi.withAlpha(TrahisonPalette.ACCENT, typing ? 0xFF : 0xA0));
		}
		return lines.size() * TrahisonUi.LINE;
	}
}
