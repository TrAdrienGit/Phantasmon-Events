package com.mystaria.events.trahison.map;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The text of the notes page and its caret (T11.3): free multi-line text, edited at the caret, wrapped to a width for display.
 * Pure: the width of a text comes from a function, so it is tested without Minecraft. Offsets count characters of {@link #text}.
 */
public final class NoteBuffer {

	/** One displayed line: characters {@code start} to {@code end} (exclusive) of the text; a hard break is not part of it. */
	public record Line(int start, int end) {
	}

	private final int max;
	private final ToIntFunction<String> widthOf;
	private StringBuilder text = new StringBuilder();
	private int caret;
	/** The x the caret keeps while moving up and down (-1: take the current one). */
	private int desiredX = -1;

	public NoteBuffer(int max, ToIntFunction<String> widthOf) {
		this.max = max;
		this.widthOf = widthOf;
	}

	public String text() {
		return text.toString();
	}

	public int caret() {
		return caret;
	}

	/** Replaces the whole text (what the backend gave); the caret goes to the end. */
	public void set(String value) {
		text = new StringBuilder(value.length() > max ? value.substring(0, max) : value);
		caret = text.length();
		desiredX = -1;
	}

	/** Types a character; line breaks are accepted, other control characters are not. Returns whether the text changed. */
	public boolean insert(char c) {
		if (c != '\n' && (c < 32 || c == 127) || text.length() >= max) {
			return false;
		}
		text.insert(caret++, c);
		desiredX = -1;
		return true;
	}

	/** Pastes a text (line breaks become {@code \n}, other control characters are dropped). Returns whether the text changed. */
	public boolean paste(String value) {
		boolean changed = false;
		for (char c : value.replace("\r\n", "\n").replace('\r', '\n').toCharArray()) {
			changed |= insert(c);
		}
		return changed;
	}

	public boolean backspace() {
		if (caret == 0) {
			return false;
		}
		text.deleteCharAt(--caret);
		desiredX = -1;
		return true;
	}

	public boolean delete() {
		if (caret >= text.length()) {
			return false;
		}
		text.deleteCharAt(caret);
		desiredX = -1;
		return true;
	}

	public void left() {
		caret = Math.max(0, caret - 1);
		desiredX = -1;
	}

	public void right() {
		caret = Math.min(text.length(), caret + 1);
		desiredX = -1;
	}

	// ---------------------------------------------------------------- layout

	/** The displayed lines for a wrap width in pixels: cut at the last space that fits, else inside a word. Never empty. */
	public List<Line> lines(int wrapWidth) {
		List<Line> out = new ArrayList<>();
		String s = text.toString();
		int start = 0;
		while (true) {
			int hard = s.indexOf('\n', start);
			int paragraphEnd = hard < 0 ? s.length() : hard;
			int lineStart = start;
			while (true) {
				int end = fit(s, lineStart, paragraphEnd, wrapWidth);
				out.add(new Line(lineStart, end));
				if (end >= paragraphEnd) {
					break;
				}
				lineStart = end;
			}
			if (hard < 0) {
				return out;
			}
			start = hard + 1;
		}
	}

	/** The end of the line that starts at {@code from}: as much as fits, cut after a space when it can. */
	private int fit(String s, int from, int limit, int wrapWidth) {
		if (from >= limit) {
			return limit;
		}
		int lastSpace = -1;
		int end = from;
		while (end < limit) {
			if (widthOf.applyAsInt(s.substring(from, end + 1)) > wrapWidth && end > from) {
				return lastSpace > from ? lastSpace + 1 : end;
			}
			if (s.charAt(end) == ' ') {
				lastSpace = end;
			}
			end++;
		}
		return limit;
	}

	/** The index of the line holding offset {@code offset}: the last one that starts at or before it. */
	public static int lineOf(List<Line> lines, int offset) {
		int found = 0;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).start() <= offset) {
				found = i;
			}
		}
		return found;
	}

	/** The pixel x of an offset inside its line. */
	public int xOf(Line line, int offset) {
		return widthOf.applyAsInt(text.substring(line.start(), Math.max(line.start(), Math.min(offset, line.end()))));
	}

	/** The offset in {@code line} nearest to pixel {@code x}. */
	public int offsetAt(Line line, int x) {
		int best = line.start();
		int bestDistance = Integer.MAX_VALUE;
		for (int o = line.start(); o <= line.end(); o++) {
			int d = Math.abs(xOf(line, o) - x);
			if (d < bestDistance) {
				best = o;
				bestDistance = d;
			}
		}
		return best;
	}

	/** Moves the caret one displayed line up or down, keeping its column in pixels. */
	public void vertical(int delta, int wrapWidth) {
		List<Line> lines = lines(wrapWidth);
		int at = lineOf(lines, caret);
		int target = at + delta;
		if (target < 0 || target >= lines.size()) {
			caret = target < 0 ? 0 : text.length();
			desiredX = -1;
			return;
		}
		if (desiredX < 0) {
			desiredX = xOf(lines.get(at), caret);
		}
		caret = offsetAt(lines.get(target), desiredX);
	}

	public void home(int wrapWidth) {
		List<Line> lines = lines(wrapWidth);
		caret = lines.get(lineOf(lines, caret)).start();
		desiredX = -1;
	}

	public void end(int wrapWidth) {
		List<Line> lines = lines(wrapWidth);
		caret = lines.get(lineOf(lines, caret)).end();
		desiredX = -1;
	}

	/** Puts the caret at a click: line {@code lineIndex}, nearest to pixel {@code x}. */
	public void click(int lineIndex, int x, int wrapWidth) {
		List<Line> lines = lines(wrapWidth);
		int i = Math.max(0, Math.min(lines.size() - 1, lineIndex));
		caret = offsetAt(lines.get(i), x);
		desiredX = -1;
	}
}
