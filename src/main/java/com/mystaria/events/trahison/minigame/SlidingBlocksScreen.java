package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
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
 * Mechanism {@code sliding_blocks}: a board of blocks that only slide along their axis; the marked piece (block 0, on the
 * exit row) must leave by the opening on the right. Drag a block along its axis, or click it and use the arrow keys.
 *
 * <p>
 * Gesture: {@code {block, delta}}, delta a signed number of cells (right or down positive). View:
 * {@code {size, exitRow, blocks: [{r, c, len, h}...], moves, solved}}. The screen clamps a drag to the free cells it sees
 * (so the piece does not cross another block while dragged); the backend decides and the next view recalls the screen.
 * Offline the screen draws a fake board and checks the exit itself.
 */
public final class SlidingBlocksScreen extends MiniGameScreen {

	/** A block as the view tells it. */
	private static final class Block {
		int r;
		int c;
		final int len;
		final boolean h;

		Block(int r, int c, int len, boolean h) {
			this.r = r;
			this.c = c;
			this.len = len;
			this.h = h;
		}

		int cellR(int k) {
			return h ? r : r + k;
		}

		int cellC(int k) {
			return h ? c + k : c;
		}
	}

	private final List<Block> blocks = new ArrayList<>();
	private final Pad pad;
	private int size;
	private int exitRow;
	private int moves;
	private int cell = 16;
	private int boardX;
	private int boardY;
	private int selected = -1;
	/** Dragging: the block, the mouse position along the axis at the grab, and the shown offset in cells (continuous). */
	private int dragBlock = -1;
	private double grab;
	private float dragOffset;
	private int dragMin;
	private int dragMax;
	private long touchedAt;
	private int touched = -1;

	public SlidingBlocksScreen(String step, boolean offline) {
		super("sliding_blocks", step, offline);
		pad = add(new Pad().paint(this::paintBoard).onClick(this::press).onDrag(this::dragTo).onRelease(this::let));
		if (offline) {
			buildFake(new Random());
		}
	}

	// ---------------------------------------------------------------- offline fake generator

	/** The backend's recipe in miniature: piece on the opening, blocks without overlap, then legal random slides. */
	private void buildFake(Random rnd) {
		size = 6;
		exitRow = 2;
		moves = 0;
		do {
			blocks.clear();
			blocks.add(new Block(exitRow, size - 2, 2, true));
			for (int tries = 0; blocks.size() < 7 && tries < 300; tries++) {
				boolean h = rnd.nextBoolean();
				int len = rnd.nextInt(3) == 0 ? 3 : 2;
				Block b = new Block(rnd.nextInt(h ? size : size - len + 1), rnd.nextInt(h ? size - len + 1 : size), len, h);
				boolean[][] grid = occupancy(-1);
				boolean free = true;
				for (int k = 0; k < len; k++) {
					free &= !grid[b.cellR(k)][b.cellC(k)];
				}
				if (free) {
					blocks.add(b);
				}
			}
			for (int i = 0; i < 40; i++) {
				int index = rnd.nextInt(blocks.size());
				int dir = rnd.nextBoolean() ? 1 : -1;
				int free = room(index, dir);
				if (free > 0) {
					slide(blocks.get(index), dir * (1 + rnd.nextInt(free)));
				}
			}
		} while (offlineSolved());
	}

	private boolean offlineSolved() {
		return blocks.get(0).c + blocks.get(0).len == size;
	}

	// ---------------------------------------------------------------- the board (display helpers, the backend decides)

	private boolean[][] occupancy(int except) {
		boolean[][] grid = new boolean[size][size];
		for (int i = 0; i < blocks.size(); i++) {
			if (i != except) {
				Block b = blocks.get(i);
				for (int k = 0; k < b.len; k++) {
					grid[b.cellR(k)][b.cellC(k)] = true;
				}
			}
		}
		return grid;
	}

	/** Free cells in a row from block {@code index} in direction {@code dir}: how far a drag may go. */
	private int room(int index, int dir) {
		Block b = blocks.get(index);
		boolean[][] grid = occupancy(index);
		int free = 0;
		while (true) {
			int next = dir > 0 ? (b.h ? b.c + b.len : b.r + b.len) + free : (b.h ? b.c : b.r) - 1 - free;
			if (next < 0 || next >= size || (b.h ? grid[b.r][next] : grid[next][b.c])) {
				return free;
			}
			free++;
		}
	}

	private static void slide(Block b, int delta) {
		if (b.h) {
			b.c += delta;
		} else {
			b.r += delta;
		}
	}

	private void move(int index, int delta) {
		if (delta == 0) {
			return;
		}
		slide(blocks.get(index), delta);
		moves++;
		selected = index;
		MiniGameSounds.clunk();
		sendGesture(Map.of("block", index, "delta", delta));
		if (offline() && offlineSolved()) {
			succeed();
		}
	}

	// ---------------------------------------------------------------- layout and sync

	@Override
	protected void layout() {
		int side = Math.max(60, Math.min(contentH - 8, contentW - 120));
		cell = Math.max(8, side / Math.max(1, size == 0 ? 6 : size));
		int total = cell * (size == 0 ? 6 : size);
		boardX = contentX + 8;
		boardY = contentY + (contentH - total) / 2;
		pad.at(boardX, boardY, total + cell, total);
	}

	@Override
	protected void applyView(Map<String, Object> view) {
		int newSize = PuzzleScreens.asInt(view.get("size"), size);
		List<Object> raw = PuzzleScreens.asList(view.get("blocks"));
		if (newSize <= 0 || raw.isEmpty()) {
			return;
		}
		boolean rebuild = newSize != size || raw.size() != blocks.size();
		size = newSize;
		exitRow = PuzzleScreens.asInt(view.get("exitRow"), exitRow);
		moves = PuzzleScreens.asInt(view.get("moves"), moves);
		if (rebuild) {
			blocks.clear();
			for (Object o : raw) {
				Map<String, Object> m = PuzzleScreens.asMap(o);
				blocks.add(new Block(PuzzleScreens.asInt(m.get("r"), 0), PuzzleScreens.asInt(m.get("c"), 0),
						PuzzleScreens.asInt(m.get("len"), 2), Boolean.TRUE.equals(m.get("h"))));
			}
			selected = -1;
			layout();
		} else if (dragBlock < 0) {
			for (int i = 0; i < raw.size(); i++) {
				Map<String, Object> m = PuzzleScreens.asMap(raw.get(i));
				blocks.get(i).r = PuzzleScreens.asInt(m.get("r"), blocks.get(i).r);
				blocks.get(i).c = PuzzleScreens.asInt(m.get("c"), blocks.get(i).c);
			}
		}
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		for (Map<String, Object> e : events) {
			int i = PuzzleScreens.asInt(e.get("block"), -1);
			if ("slide".equals(e.get("type")) && i >= 0 && i < blocks.size()) {
				touched = i;
				touchedAt = System.currentTimeMillis();
				MiniGameSounds.clunk();
				Block b = blocks.get(i);
				remoteTouch(player, boardX + (int) ((b.c + (b.h ? b.len : 1) / 2.0) * cell),
						boardY + (int) ((b.r + (b.h ? 1 : b.len) / 2.0) * cell));
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		if (blocks.isEmpty()) {
			return;
		}
		int index = (int) ((System.currentTimeMillis() / 3200 + player) % blocks.size());
		int dir = room(index, 1) > 0 ? 1 : -1;
		if (room(index, dir) > 0) {
			slide(blocks.get(index), dir);
			onRemote(player, List.of(Map.of("type", "slide", "block", index, "delta", dir)));
			if (offlineSolved()) {
				succeed();
			}
		}
	}

	// ---------------------------------------------------------------- input

	private int blockAt(double mx, double my) {
		int c = (int) Math.floor((mx - boardX) / cell);
		int r = (int) Math.floor((my - boardY) / cell);
		for (int i = 0; i < blocks.size(); i++) {
			Block b = blocks.get(i);
			for (int k = 0; k < b.len; k++) {
				if (b.cellR(k) == r && b.cellC(k) == c) {
					return i;
				}
			}
		}
		return -1;
	}

	private boolean press(double mx, double my, int button) {
		if (button != 0 || blocks.isEmpty()) {
			return false;
		}
		int i = blockAt(mx, my);
		if (i < 0) {
			return false;
		}
		selected = i;
		dragBlock = i;
		Block b = blocks.get(i);
		grab = b.h ? mx : my;
		dragOffset = 0;
		dragMin = -room(i, -1);
		dragMax = room(i, 1);
		MiniGameSounds.click();
		return true;
	}

	private void dragTo(double mx, double my) {
		if (dragBlock < 0) {
			return;
		}
		double d = ((blocks.get(dragBlock).h ? mx : my) - grab) / cell;
		dragOffset = (float) Math.max(dragMin, Math.min(dragMax, d));
	}

	private boolean let(double mx, double my, int button) {
		if (dragBlock < 0) {
			return false;
		}
		int index = dragBlock;
		int delta = Math.round(dragOffset);
		dragBlock = -1;
		dragOffset = 0;
		move(index, delta);
		return true;
	}

	@Override
	protected boolean key(int key) {
		if (blocks.isEmpty()) {
			return false;
		}
		if (key == 258) { // tab: next block
			selected = (selected + 1) % blocks.size();
			MiniGameSounds.click();
			return true;
		}
		int dx = key == 262 ? 1 : key == 263 ? -1 : 0;
		int dy = key == 264 ? 1 : key == 265 ? -1 : 0;
		if ((dx == 0 && dy == 0) || selected < 0) {
			return false;
		}
		Block b = blocks.get(selected);
		int dir = b.h ? dx : dy;
		if (dir != 0 && room(selected, dir) > 0) {
			move(selected, dir);
		}
		return true;
	}

	// ---------------------------------------------------------------- drawing

	private void paintBoard(GuiGraphics g, int mx, int my, boolean hot) {
		if (blocks.isEmpty() || size == 0) {
			MenuUi.centered(g, Component.translatable("phantasmon_events.trahison.minigame.waiting"), contentX + contentW / 2,
					contentY + contentH / 2, contentW - 20, TrahisonPalette.TEXT_MUTED);
			return;
		}
		int total = cell * size;
		g.fill(boardX - 2, boardY - 2, boardX + total + 2, boardY + total + 2, TrahisonPalette.BORDER);
		g.fill(boardX, boardY, boardX + total, boardY + total, TrahisonPalette.PANEL_SUNKEN);
		for (int i = 1; i < size; i++) {
			g.fill(boardX + i * cell, boardY, boardX + i * cell + 1, boardY + total, TrahisonPalette.DOTS);
			g.fill(boardX, boardY + i * cell, boardX + total, boardY + i * cell + 1, TrahisonPalette.DOTS);
		}
		// The opening: a gap in the frame on the right, with an arrow pointing out.
		int ey = boardY + exitRow * cell;
		int color = isSolved() ? TrahisonPalette.SUCCESS : TrahisonPalette.ACCENT;
		g.fill(boardX + total, ey + 1, boardX + total + 2, ey + cell - 1, TrahisonPalette.PANEL_SUNKEN);
		for (int k = 0; k < 4; k++) {
			g.fill(boardX + total + 3 + k, ey + cell / 2 - 3 + k, boardX + total + 4 + k, ey + cell / 2 + 4 - k, color);
		}
		long now = System.currentTimeMillis();
		for (int i = 0; i < blocks.size(); i++) {
			Block b = blocks.get(i);
			float off = i == dragBlock ? dragOffset : 0;
			int x = boardX + Math.round(((b.h ? b.c + off : b.c)) * cell);
			int y = boardY + Math.round(((b.h ? b.r : b.r + off)) * cell);
			int w = b.h ? b.len * cell : cell;
			int h = b.h ? cell : b.len * cell;
			boolean piece = i == 0;
			int fill = piece ? (isSolved() ? TrahisonPalette.SUCCESS_DARK : TrahisonPalette.ACCENT_DIM) : i % 2 == 0 ? TrahisonPalette.PANEL_RAISED : TrahisonPalette.PANEL;
			int edge = piece ? color : i == selected ? TrahisonPalette.ACCENT_HOT : TrahisonPalette.EDGE;
			if (i == touched && now - touchedAt < 700) {
				edge = TrahisonPalette.ACCENT_HOT;
			}
			g.fill(x + 1, y + 1, x + w - 1, y + h - 1, edge);
			g.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);
			// Grooves along the axis show how the block may move, without relying on colour.
			int groove = TrahisonUi.withAlpha(TrahisonPalette.TEXT_MUTED, 0x80);
			if (b.h) {
				g.fill(x + 5, y + cell / 2 - 3, x + w - 5, y + cell / 2 - 2, groove);
				g.fill(x + 5, y + cell / 2 + 2, x + w - 5, y + cell / 2 + 3, groove);
			} else {
				g.fill(x + cell / 2 - 3, y + 5, x + cell / 2 - 2, y + h - 5, groove);
				g.fill(x + cell / 2 + 2, y + 5, x + cell / 2 + 3, y + h - 5, groove);
			}
			if (piece) {
				TrahisonUi.disc(g, x + w - cell / 2, y + h / 2, Math.max(2, cell / 6), color);
			}
		}
		// Side panel: the move counter and the rule in one line.
		int px = boardX + total + 20;
		int pw = contentX + contentW - px - 8;
		if (pw > 40) {
			TrahisonUi.textEllipsis(g, Component.translatable("phantasmon_events.trahison.minigame.sliding_blocks.moves", moves), px, contentY + 12, pw, TrahisonPalette.TEXT);
			TrahisonUi.textEllipsis(g, Component.translatable("phantasmon_events.trahison.minigame.sliding_blocks.goal"), px, contentY + 28, pw, TrahisonPalette.TEXT_SECONDARY);
		}
	}
}
