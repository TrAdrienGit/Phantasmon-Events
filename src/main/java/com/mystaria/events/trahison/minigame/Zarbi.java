package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import com.mystaria.events.trahison.room.Pictures;

/**
 * The Zarbi (Unown) alphabet: 26 glyphs read from one texture, {@code textures/trahison/zarbi/glyphs.png}, a grid of 13 columns by
 * 2 rows (A..M, then N..Z), white strokes on transparent so any palette colour tints them. The file shipped is a generated
 * placeholder ({@code tools/zarbi_placeholder.py}) to be redrawn by hand: any square cell size works, nothing else changes. Nothing
 * here comes from the Pokemon games. Client thread.
 */
public final class Zarbi {

	public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("phantasmon_events", "textures/trahison/zarbi/glyphs.png");
	private static final int COLS = 13;
	private static final int ROWS = 2;

	private Zarbi() {
	}

	/** Whether a character has a glyph (letters only; spaces are gaps). */
	public static boolean has(char c) {
		return Character.toUpperCase(c) >= 'A' && Character.toUpperCase(c) <= 'Z';
	}

	/** Draws the glyph of one letter in a {@code size x size} box, tinted {@code color} (ARGB); does nothing for a non-letter. */
	public static void draw(GuiGraphics g, char c, int x, int y, int size, int color) {
		if (!has(c)) {
			return;
		}
		int[] s = Pictures.size(TEXTURE);
		if (s == null) {
			g.fill(x + 1, y + 1, x + size - 1, y + size - 1, color); // no texture: a plain block, still countable
			return;
		}
		int cellW = s[0] / COLS;
		int cellH = s[1] / ROWS;
		int index = Character.toUpperCase(c) - 'A';
		g.setColor((color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f, (color >>> 24) / 255f);
		g.blit(TEXTURE, x, y, size, size, (index % COLS) * cellW, (index / COLS) * cellH, cellW, cellH, s[0], s[1]);
		g.setColor(1f, 1f, 1f, 1f);
	}

	/** Width of a text on one line: {@code size} per glyph plus a gap of a quarter, and a space is a half glyph wider. */
	public static int width(String text, int size) {
		return text.length() * step(size);
	}

	private static int step(int size) {
		return size + Math.max(1, size / 4);
	}

	/** Cuts a text into lines of at most {@code maxWidth}, at spaces (a word longer than the line is cut). */
	public static List<String> wrap(String text, int size, int maxWidth) {
		int perLine = Math.max(1, maxWidth / step(size));
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			while (word.length() > perLine) {
				if (line.length() > 0) {
					lines.add(line.toString());
					line.setLength(0);
				}
				lines.add(word.substring(0, perLine));
				word = word.substring(perLine);
			}
			if (line.length() > 0 && line.length() + 1 + word.length() > perLine) {
				lines.add(line.toString());
				line.setLength(0);
			}
			if (line.length() > 0) {
				line.append(' ');
			}
			line.append(word);
		}
		if (line.length() > 0) {
			lines.add(line.toString());
		}
		return lines;
	}

	/** Height of a wrapped text. */
	public static int height(String text, int size, int maxWidth) {
		return wrap(text, size, maxWidth).size() * (size + 4);
	}

	/** The largest of {@code sizes} (descending) at which the text fits in {@code maxHeight}; the smallest when none does. */
	public static int fit(String text, int[] sizes, int maxWidth, int maxHeight) {
		for (int size : sizes) {
			if (height(text, size, maxWidth) <= maxHeight) {
				return size;
			}
		}
		return sizes[sizes.length - 1];
	}

	/** Draws a wrapped text centred in {@code maxWidth}; returns its height. */
	public static int drawText(GuiGraphics g, String text, int x, int y, int maxWidth, int size, int color) {
		int ty = y;
		for (String line : wrap(text, size, maxWidth)) {
			int lx = x + (maxWidth - width(line, size) + Math.max(1, size / 4)) / 2;
			for (int i = 0; i < line.length(); i++) {
				draw(g, line.charAt(i), lx + i * step(size), ty, size, color);
			}
			ty += size + 4;
		}
		return ty - y;
	}
}
