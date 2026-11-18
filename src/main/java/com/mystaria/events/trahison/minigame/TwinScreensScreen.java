package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.Pad;
import com.mystaria.events.trahison.minigame.widget.PushButton;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code twin_screens}: two distant terminals, each showing in the room half of the problem (a sequence of
 * glyphs, given as a clue where the terminal is); a terminal is typed with the sequence the <em>other</em> terminal shows,
 * so two different players must read to each other. This screen is the keyboard of the terminal the player used: they
 * hold that seat, and cannot hold the other one.
 *
 * <p>
 * Gesture: {@code {seat: "left" | "right", symbols: [id...]}}. View: {@code {alphabet: [id...], length, seats: {left,
 * right}, latched: {left, right}, wrong, solved}} where a seat holds a player index or null. This screen never holds
 * a half: the keyboard only offers the alphabet, and an entry waits for the backend, which answers by latching the
 * terminal ({@code latched}) or counting a mistake ({@code wrong}, which clears the entry with the error feedback).
 * Keys: 1 to 9 and 0 pick the glyphs of the alphabet in order, Backspace erases, Delete clears, Enter sends. Offline the
 * screen holds a fake pair of halves and shows what to type, so the look can be judged.
 */
public final class TwinScreensScreen extends MiniGameScreen {

	private static final int CELL = 24;
	private static final int GAP = 4;

	private final Pad palette;
	private final PushButton erase;
	private final PushButton send;
	private List<String> alphabet = List.of();
	private final List<String> entry = new ArrayList<>();
	private int length = 4;
	private int leftHolder = -1;
	private int rightHolder = -1;
	private boolean leftLatched;
	private boolean rightLatched;
	/** Wrong tries so far, -1 until the first view (so reopening the screen does not replay an old error). */
	private int wrong = -1;
	private long errorAt = -10000;
	private int hovered = -1;
	private int paletteX;
	private int paletteY;
	private int columns = 8;

	/** Offline only: the two halves, as the room would show them. */
	private List<String> offlineLeft = List.of();
	private List<String> offlineRight = List.of();

	public TwinScreensScreen(String step, boolean offline) {
		super("twin_screens", step, offline);
		palette = add(new Pad().paint(this::paintPalette).onClick(this::pick));
		erase = add(new PushButton(() -> Component.translatable("phantasmon_events.trahison.minigame.twin_screens.erase"), ButtonKind.DEFAULT, this::backspace));
		send = add(new PushButton(() -> Component.translatable("phantasmon_events.trahison.minigame.twin_screens.send"), ButtonKind.PRIMARY, this::submit));
		if (offline) {
			List<String> pool = new ArrayList<>(Glyphs.IDS);
			Random rnd = new Random();
			Collections.shuffle(pool, rnd);
			alphabet = List.copyOf(pool.subList(0, 10));
			List<String> a = new ArrayList<>(alphabet);
			Collections.shuffle(a, rnd);
			offlineLeft = List.copyOf(a.subList(0, 4));
			Collections.shuffle(a, rnd);
			offlineRight = List.copyOf(a.subList(0, 4));
			leftHolder = 0;
			wrong = 0;
		}
	}

	// ---------------------------------------------------------------- seats

	/** The terminal this player holds: "left", "right" or null. */
	private String seat() {
		int me = ownIndex();
		return leftHolder == me ? "left" : rightHolder == me ? "right" : null;
	}

	private boolean seatLatched() {
		return "left".equals(seat()) ? leftLatched : "right".equals(seat()) && rightLatched;
	}

	private boolean canType() {
		return seat() != null && !seatLatched() && !isSolved();
	}

	// ---------------------------------------------------------------- gestures

	private boolean pick(double mx, double my, int button) {
		int i = cellAt(mx, my);
		if (button != 0 || i < 0) {
			return false;
		}
		addGlyph(i);
		return true;
	}

	private void addGlyph(int index) {
		if (!canType() || index < 0 || index >= alphabet.size() || entry.size() >= length) {
			return;
		}
		entry.add(alphabet.get(index));
		MiniGameSounds.detent(0.9f + 0.08f * entry.size());
	}

	private void backspace() {
		if (canType() && !entry.isEmpty()) {
			entry.remove(entry.size() - 1);
			MiniGameSounds.click();
		}
	}

	private void submit() {
		String seat = seat();
		if (!canType() || entry.size() != length) {
			return;
		}
		sendGesture(Map.of("seat", seat, "symbols", new ArrayList<>(entry)));
		if (offline()) {
			List<String> expected = "left".equals(seat) ? offlineRight : offlineLeft;
			if (entry.equals(expected)) {
				leftLatched = true;
				MiniGameSounds.clunk();
			} else {
				errorAt = System.currentTimeMillis();
				fail();
				entry.clear();
			}
		}
	}

	@Override
	protected boolean key(int key) {
		if (key >= 49 && key <= 57) {
			addGlyph(key - 49);
		} else if (key == 48) {
			addGlyph(9);
		} else if (key == 259) {
			backspace();
		} else if (key == 261) {
			entry.clear();
		} else if (key == 257) {
			submit();
		} else {
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- layout and sync

	private int cellAt(double mx, double my) {
		int c = (int) Math.floor((mx - paletteX) / (double) (CELL + GAP));
		int r = (int) Math.floor((my - paletteY) / (double) (CELL + GAP));
		int i = r * columns + c;
		boolean inCell = mx - paletteX - c * (CELL + GAP) < CELL && my - paletteY - r * (CELL + GAP) < CELL;
		return c >= 0 && c < columns && r >= 0 && inCell && i < alphabet.size() ? i : -1;
	}

	@Override
	protected void layout() {
		columns = Math.max(4, Math.min(8, (contentW - 16 + GAP) / (CELL + GAP)));
		int rows = (Math.max(1, alphabet.size()) + columns - 1) / columns;
		int w = columns * (CELL + GAP) - GAP;
		paletteX = contentX + (contentW - w) / 2;
		paletteY = contentY + contentH - rows * (CELL + GAP) + GAP - 4;
		palette.at(paletteX, paletteY, w, rows * (CELL + GAP));
		int entryW = length * (CELL + GAP) - GAP;
		int ex = contentX + (contentW - entryW) / 2;
		int by = contentY + 20;
		erase.at(ex + entryW + 8, by, 40, 24);
		send.at(ex + entryW + 8 + 44, by, 48, 24);
	}

	@Override
	protected void applyView(Map<String, Object> view) {
		List<String> incoming = new ArrayList<>();
		for (Object o : PuzzleScreens.asList(view.get("alphabet"))) {
			incoming.add(PuzzleScreens.asString(o, "?"));
		}
		int newLength = PuzzleScreens.asInt(view.get("length"), length);
		if (!incoming.isEmpty() && (!incoming.equals(alphabet) || newLength != length)) {
			alphabet = incoming;
			length = newLength;
			entry.clear();
			layout();
		}
		Map<String, Object> seats = PuzzleScreens.asMap(view.get("seats"));
		leftHolder = PuzzleScreens.asInt(seats.get("left"), -1);
		rightHolder = PuzzleScreens.asInt(seats.get("right"), -1);
		Map<String, Object> latched = PuzzleScreens.asMap(view.get("latched"));
		boolean wasLatched = seatLatched();
		leftLatched = Boolean.TRUE.equals(latched.get("left"));
		rightLatched = Boolean.TRUE.equals(latched.get("right"));
		if (!wasLatched && seatLatched()) {
			entry.clear();
			MiniGameSounds.clunk();
		}
		int w = PuzzleScreens.asInt(view.get("wrong"), wrong);
		if (wrong >= 0 && w > wrong) {
			errorAt = System.currentTimeMillis();
			entry.clear();
			fail();
		}
		wrong = w;
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		for (Map<String, Object> e : events) {
			if ("latch".equals(e.get("type"))) {
				MiniGameSounds.clunk();
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		if (offline() && player == 1 && leftLatched && !rightLatched) {
			rightHolder = 1;
			rightLatched = true;
			MiniGameSounds.clunk();
			succeed();
		}
	}

	// ---------------------------------------------------------------- drawing

	private String holderName(int index) {
		if (index < 0) {
			return null;
		}
		PuzzleScreens.Player p = PuzzleScreens.player(index);
		return p != null ? p.name() : "#" + (index + 1);
	}

	private void paintPalette(GuiGraphics g, int mx, int my, boolean hot) {
		hovered = hot ? cellAt(mx, my) : -1;
		boolean on = canType();
		for (int i = 0; i < alphabet.size(); i++) {
			int x = paletteX + (i % columns) * (CELL + GAP);
			int y = paletteY + (i / columns) * (CELL + GAP);
			g.fill(x, y, x + CELL, y + CELL, TrahisonPalette.BORDER);
			g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, !on ? TrahisonPalette.PANEL : i == hovered ? TrahisonPalette.EDGE : TrahisonPalette.PANEL_RAISED);
			Glyphs.draw(g, alphabet.get(i), x + CELL / 2f, y + CELL / 2f, 14, 0, on ? TrahisonPalette.TEXT : TrahisonPalette.TEXT_DISABLED);
		}
	}

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		int cx = contentX + contentW / 2;
		String seat = seat();
		long now = System.currentTimeMillis();
		// Header: which terminal this is, and what the other one does.
		Component head = seat == null ? Component.translatable("phantasmon_events.trahison.minigame.twin_screens.no_seat")
				: Component.translatable("phantasmon_events.trahison.minigame.twin_screens.seat_" + seat);
		TrahisonUi.textEllipsis(g, head, contentX + 8, contentY + 6, contentW - 16, seat == null ? TrahisonPalette.TEXT_MUTED : TrahisonPalette.ACCENT);
		String other = "left".equals(seat) ? "right" : "left";
		boolean otherLatched = "left".equals(seat) ? rightLatched : leftLatched;
		String name = holderName("left".equals(seat) ? rightHolder : leftHolder);
		Component status = seat == null ? Component.empty()
				: otherLatched ? Component.translatable("phantasmon_events.trahison.minigame.twin_screens.other_done", Component.translatable("phantasmon_events.trahison.minigame.twin_screens.name_" + other))
				: name == null ? Component.translatable("phantasmon_events.trahison.minigame.twin_screens.other_free", Component.translatable("phantasmon_events.trahison.minigame.twin_screens.name_" + other))
				: Component.translatable("phantasmon_events.trahison.minigame.twin_screens.other_held", Component.translatable("phantasmon_events.trahison.minigame.twin_screens.name_" + other), name);
		int sw = Math.min(contentW / 2, 200);
		TrahisonUi.textEllipsis(g, status, contentX + contentW - 8 - sw, contentY + 6, sw, otherLatched ? TrahisonPalette.SUCCESS : TrahisonPalette.TEXT_SECONDARY);
		// The entry row: one box per glyph, red-framed for a moment after a mistake, green once the terminal is locked.
		int entryW = length * (CELL + GAP) - GAP;
		int ex = contentX + (contentW - entryW) / 2;
		int ey = contentY + 20;
		boolean bad = now - errorAt < 500;
		for (int i = 0; i < length; i++) {
			int x = ex + i * (CELL + GAP);
			int frame = seatLatched() ? TrahisonPalette.SUCCESS : bad ? TrahisonPalette.DANGER : i == entry.size() && canType() ? TrahisonPalette.ACCENT : TrahisonPalette.BORDER;
			g.fill(x, ey, x + CELL, ey + CELL, frame);
			g.fill(x + 1, ey + 1, x + CELL - 1, ey + CELL - 1, TrahisonPalette.PANEL_SUNKEN);
			if (i < entry.size()) {
				Glyphs.draw(g, entry.get(i), x + CELL / 2f, ey + CELL / 2f, 14, 0, TrahisonPalette.TEXT);
			}
		}
		if (seatLatched()) {
			TrahisonUi.textEllipsis(g, Component.translatable("phantasmon_events.trahison.minigame.twin_screens.locked"), contentX + 8, ey + CELL + 8, contentW - 16, TrahisonPalette.SUCCESS);
		}
		if (offline()) {
			// Offline note: what the room would show at the other terminal, so the test can be played alone.
			List<String> expected = "left".equals(seat) ? offlineRight : offlineLeft;
			int nx = contentX + 8;
			TrahisonUi.textEllipsis(g, Component.translatable("phantasmon_events.trahison.minigame.twin_screens.offline_note"), nx, ey + 2, 80, TrahisonPalette.GAME_MASTER);
			for (int i = 0; i < expected.size(); i++) {
				Glyphs.draw(g, expected.get(i), nx + 8 + i * 16, ey + 24, 12, 0, TrahisonPalette.GAME_MASTER);
			}
		}
		if (alphabet.isEmpty()) {
			MenuUi.centered(g, Component.translatable("phantasmon_events.trahison.minigame.waiting"), cx, contentY + contentH / 2, contentW - 20, TrahisonPalette.TEXT_MUTED);
		}
	}
}
