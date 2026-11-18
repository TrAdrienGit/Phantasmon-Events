package com.mystaria.events.trahison.minigame;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.Pad;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code rotating_tiles}: a board of pipe tiles turned a quarter turn per click until the current that enters
 * on the left, in the source row, reaches the lamp on the right. Left click or Enter turns clockwise, right click turns
 * the other way, the arrow keys move the cursor. Cells the current reaches are drawn lit.
 *
 * <p>
 * Gesture: {@code {cell: row * size + col, delta: +1 | -1}}. View: {@code {size, source, sink, types, rots, fixed, powered,
 * lit, solved}} (the game master's view adds {@code solution}). A turn shows at once; which cells are lit comes only from
 * the view (offline, from a small local flood fill so the look can be judged). Fixed tiles carry a rivet and refuse.
 */
public final class RotatingTilesScreen extends MiniGameScreen {

	private static final String[] TYPES = { "straight", "corner", "tee", "end", "empty" };
	private static final int[] BASE = { 0b0101, 0b0011, 0b0111, 0b0001, 0 };

	private final Pad pad;
	private int size;
	private int source;
	private int sink;
	private int[] types = new int[0];
	private int[] rots = new int[0];
	private boolean[] fixed = new boolean[0];
	private boolean[] powered = new boolean[0];
	private boolean lit;
	private int cell = 20;
	private int boardX;
	private int boardY;
	private int cursor;
	private int touched = -1;
	private long touchedAt;

	public RotatingTilesScreen(String step, boolean offline) {
		super("rotating_tiles", step, offline);
		pad = add(new Pad().paint(this::paintBoard).onClick(this::press).onWheel((mx, my, d) -> {
			int i = cellAt(mx, my);
			if (i < 0 || d == 0) {
				return false;
			}
			turn(i, d > 0 ? 1 : -1);
			return true;
		}));
		if (offline) {
			buildFake(new Random());
		}
	}

	// ---------------------------------------------------------------- offline fake generator

	/** A random self-avoiding path with the right tiles on it, decoys elsewhere, everything turned at random. */
	private void buildFake(Random rnd) {
		size = 4;
		source = rnd.nextInt(size);
		sink = rnd.nextInt(size);
		List<Integer> path = new ArrayList<>();
		walk(rnd, source * size, sink * size + size - 1, new boolean[size * size], path);
		types = new int[size * size];
		rots = new int[size * size];
		fixed = new boolean[size * size];
		int[] solution = new int[size * size];
		boolean[] onPath = new boolean[size * size];
		for (int i = 0; i < path.size(); i++) {
			int c = path.get(i);
			int in = i == 0 ? 3 : (sideBetween(path.get(i - 1), c) + 2) % 4;
			int out = i == path.size() - 1 ? 1 : sideBetween(c, path.get(i + 1));
			int wanted = (1 << in) | (1 << out);
			onPath[c] = true;
			types[c] = wanted == 0b0101 || wanted == 0b1010 ? 0 : 1;
			for (int r = 0; r < 4; r++) {
				if (mask(types[c], r) == wanted) {
					solution[c] = r;
					break;
				}
			}
		}
		for (int c = 0; c < size * size; c++) {
			if (!onPath[c]) {
				int roll = rnd.nextInt(10);
				types[c] = roll < 3 ? 0 : roll < 6 ? 1 : roll < 8 ? 2 : roll < 9 ? 3 : 4;
			}
		}
		fixed[path.get(path.size() / 2)] = true;
		do {
			for (int c = 0; c < size * size; c++) {
				rots[c] = fixed[c] ? solution[c] : rnd.nextInt(4);
			}
			flood();
		} while (lit);
	}

	private static final int[] DR = { -1, 0, 1, 0 };
	private static final int[] DC = { 0, 1, 0, -1 };

	private int sideBetween(int from, int to) {
		int dr = to / size - from / size;
		int dc = to % size - from % size;
		for (int s = 0; s < 4; s++) {
			if (DR[s] == dr && DC[s] == dc) {
				return s;
			}
		}
		return 0;
	}

	private boolean walk(Random rnd, int c, int goal, boolean[] seen, List<Integer> path) {
		seen[c] = true;
		path.add(c);
		if (c == goal) {
			return true;
		}
		List<Integer> sides = new ArrayList<>(List.of(0, 1, 2, 3));
		Collections.shuffle(sides, rnd);
		for (int s : sides) {
			int nr = c / size + DR[s];
			int nc = c % size + DC[s];
			if (nr >= 0 && nc >= 0 && nr < size && nc < size && !seen[nr * size + nc] && walk(rnd, nr * size + nc, goal, seen, path)) {
				return true;
			}
		}
		path.remove(path.size() - 1);
		return false;
	}

	private static int mask(int type, int rot) {
		int m = BASE[type];
		int r = Math.floorMod(rot, 4);
		return ((m << r) | (m >> (4 - r))) & 0b1111;
	}

	/** Offline only: lights the cells the current reaches. Online the view says which cells are lit. */
	private void flood() {
		powered = new boolean[size * size];
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		int first = source * size;
		if ((mask(types[first], rots[first]) & 8) != 0) {
			powered[first] = true;
			queue.add(first);
		}
		while (!queue.isEmpty()) {
			int c = queue.poll();
			int m = mask(types[c], rots[c]);
			for (int s = 0; s < 4; s++) {
				int nr = c / size + DR[s];
				int nc = c % size + DC[s];
				if ((m & (1 << s)) != 0 && nr >= 0 && nc >= 0 && nr < size && nc < size) {
					int n = nr * size + nc;
					if (!powered[n] && (mask(types[n], rots[n]) & (1 << ((s + 2) % 4))) != 0) {
						powered[n] = true;
						queue.add(n);
					}
				}
			}
		}
		int lamp = sink * size + size - 1;
		lit = powered[lamp] && (mask(types[lamp], rots[lamp]) & 2) != 0;
	}

	// ---------------------------------------------------------------- gestures

	private int cellAt(double mx, double my) {
		int c = (int) Math.floor((mx - boardX) / cell);
		int r = (int) Math.floor((my - boardY) / cell);
		return size > 0 && r >= 0 && c >= 0 && r < size && c < size ? r * size + c : -1;
	}

	private boolean press(double mx, double my, int button) {
		int i = cellAt(mx, my);
		if (i < 0 || (button != 0 && button != 1)) {
			return false;
		}
		cursor = i;
		turn(i, button == 0 ? 1 : -1);
		return true;
	}

	private void turn(int index, int delta) {
		if (index < 0 || index >= rots.length) {
			return;
		}
		if (fixed[index]) {
			fail();
			return;
		}
		rots[index] = Math.floorMod(rots[index] + delta, 4);
		touched = index;
		touchedAt = System.currentTimeMillis();
		MiniGameSounds.detent(delta > 0 ? 1.1f : 0.9f);
		sendGesture(Map.of("cell", index, "delta", delta));
		if (offline()) {
			flood();
			if (lit) {
				succeed();
			}
		}
	}

	@Override
	protected boolean key(int key) {
		if (size == 0) {
			return false;
		}
		int r = cursor / size;
		int c = cursor % size;
		switch (key) {
			case 265 -> r = Math.max(0, r - 1);
			case 264 -> r = Math.min(size - 1, r + 1);
			case 263 -> c = Math.max(0, c - 1);
			case 262 -> c = Math.min(size - 1, c + 1);
			case 257, 32 -> {
				turn(cursor, 1);
				return true;
			}
			default -> {
				return false;
			}
		}
		cursor = r * size + c;
		MiniGameSounds.click();
		return true;
	}

	// ---------------------------------------------------------------- layout and sync

	@Override
	protected void layout() {
		int n = size == 0 ? 4 : size;
		cell = Math.max(14, Math.min(40, (contentH - 12) / n));
		boardX = contentX + 28;
		boardY = contentY + (contentH - cell * n) / 2;
		pad.at(boardX - 16, boardY, cell * n + 32, cell * n);
	}

	@Override
	protected void applyView(Map<String, Object> view) {
		int newSize = PuzzleScreens.asInt(view.get("size"), size);
		List<Object> t = PuzzleScreens.asList(view.get("types"));
		List<Object> r = PuzzleScreens.asList(view.get("rots"));
		if (newSize <= 0 || t.size() != newSize * newSize || r.size() != t.size()) {
			return;
		}
		boolean resized = newSize != size;
		size = newSize;
		source = PuzzleScreens.asInt(view.get("source"), source);
		sink = PuzzleScreens.asInt(view.get("sink"), sink);
		types = new int[t.size()];
		rots = new int[t.size()];
		fixed = new boolean[t.size()];
		powered = new boolean[t.size()];
		List<Object> f = PuzzleScreens.asList(view.get("fixed"));
		List<Object> p = PuzzleScreens.asList(view.get("powered"));
		for (int i = 0; i < t.size(); i++) {
			String name = PuzzleScreens.asString(t.get(i), "empty");
			for (int k = 0; k < TYPES.length; k++) {
				if (TYPES[k].equals(name)) {
					types[i] = k;
				}
			}
			rots[i] = PuzzleScreens.asInt(r.get(i), 0);
			fixed[i] = i < f.size() && Boolean.TRUE.equals(f.get(i));
			powered[i] = i < p.size() && Boolean.TRUE.equals(p.get(i));
		}
		lit = Boolean.TRUE.equals(view.get("lit"));
		if (resized) {
			cursor = 0;
			layout();
		}
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		for (Map<String, Object> e : events) {
			int i = PuzzleScreens.asInt(e.get("cell"), -1);
			if ("turn".equals(e.get("type")) && i >= 0 && size > 0 && i < size * size) {
				touched = i;
				touchedAt = System.currentTimeMillis();
				MiniGameSounds.detent(PuzzleScreens.asInt(e.get("delta"), 1) > 0 ? 1.1f : 0.9f);
				remoteTouch(player, boardX + (i % size) * cell + cell / 2, boardY + (i / size) * cell + cell / 2);
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		if (size == 0) {
			return;
		}
		int i = (int) ((System.currentTimeMillis() / 3200 * 5 + player * 3) % (size * size));
		if (!fixed[i]) {
			rots[i] = Math.floorMod(rots[i] + 1, 4);
			flood();
			onRemote(player, List.of(Map.of("type", "turn", "cell", i, "delta", 1)));
			if (lit) {
				succeed();
			}
		}
	}

	// ---------------------------------------------------------------- drawing

	private void paintBoard(GuiGraphics g, int mx, int my, boolean hot) {
		if (size == 0) {
			MenuUi.centered(g, Component.translatable("phantasmon_events.trahison.minigame.waiting"), contentX + contentW / 2,
					contentY + contentH / 2, contentW - 20, TrahisonPalette.TEXT_MUTED);
			return;
		}
		int total = cell * size;
		g.fill(boardX - 2, boardY - 2, boardX + total + 2, boardY + total + 2, TrahisonPalette.BORDER);
		int live = isSolved() ? TrahisonPalette.SUCCESS : TrahisonPalette.ACCENT_HOT;
		long now = System.currentTimeMillis();
		for (int i = 0; i < size * size; i++) {
			int x = boardX + (i % size) * cell;
			int y = boardY + (i / size) * cell;
			boolean on = powered.length > i && powered[i];
			g.fill(x, y, x + cell, y + cell, (i / size + i % size) % 2 == 0 ? TrahisonPalette.PANEL : TrahisonPalette.PANEL_RAISED);
			if (i == touched && now - touchedAt < 500) {
				g.fill(x, y, x + cell, y + cell, TrahisonUi.withAlpha(TrahisonPalette.ACCENT, 0x50));
			}
			int m = mask(types[i], rots[i]);
			int color = on ? live : TrahisonPalette.TEXT_SECONDARY;
			int cx = x + cell / 2;
			int cy = y + cell / 2;
			int half = Math.max(2, cell / 10);
			for (int s = 0; s < 4; s++) {
				if ((m & (1 << s)) != 0) {
					int x1 = s == 3 ? x : s == 1 ? cx : cx - half;
					int x2 = s == 3 ? cx : s == 1 ? x + cell : cx + half;
					int y1 = s == 0 ? y : s == 2 ? cy : cy - half;
					int y2 = s == 0 ? cy : s == 2 ? y + cell : cy + half;
					g.fill(x1, y1, x2, y2, color);
				}
			}
			if (m != 0) {
				TrahisonUi.disc(g, cx, cy, half + 1, color);
			}
			if (fixed.length > i && fixed[i]) {
				g.fill(x + 2, y + 2, x + 5, y + 5, TrahisonPalette.TEXT_MUTED);
			}
			if (i == cursor && hot) {
				g.fill(x, y, x + cell, y + 1, TrahisonPalette.ACCENT);
				g.fill(x, y + cell - 1, x + cell, y + cell, TrahisonPalette.ACCENT);
				g.fill(x, y, x + 1, y + cell, TrahisonPalette.ACCENT);
				g.fill(x + cell - 1, y, x + cell, y + cell, TrahisonPalette.ACCENT);
			}
		}
		// The source: a bar entering the left edge. The lamp: a disc at the right edge, lit when the circuit is closed.
		int sy = boardY + source * cell + cell / 2;
		g.fill(boardX - 14, sy - 2, boardX - 2, sy + 2, TrahisonPalette.ACCENT);
		TrahisonUi.disc(g, boardX - 14, sy, 4, TrahisonPalette.ACCENT);
		int ly = boardY + sink * cell + cell / 2;
		g.fill(boardX + total + 2, ly - 2, boardX + total + 10, ly + 2, lit ? live : TrahisonPalette.TEXT_MUTED);
		TrahisonUi.disc(g, boardX + total + 14, ly, 5, TrahisonPalette.BORDER);
		TrahisonUi.disc(g, boardX + total + 14, ly, 4, lit ? live : TrahisonPalette.PANEL_SUNKEN);
		int px = boardX + total + 28;
		int pw = contentX + contentW - px - 8;
		if (pw > 40) {
			TrahisonUi.textEllipsis(g, Component.translatable("phantasmon_events.trahison.minigame.rotating_tiles.goal"), px, contentY + 12, pw, TrahisonPalette.TEXT_SECONDARY);
			TrahisonUi.textEllipsis(g, Component.translatable(lit ? "phantasmon_events.trahison.minigame.rotating_tiles.lit" : "phantasmon_events.trahison.minigame.rotating_tiles.dark"),
					px, contentY + 28, pw, lit ? TrahisonPalette.SUCCESS : TrahisonPalette.TEXT_MUTED);
		}
	}
}
