package com.mystaria.events.trahison.minigame;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.PushButton;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code keypad}: a wall keypad. The group types a code (3 to 6 digits, found elsewhere in the room) and
 * confirms it. What is typed is shared: everyone at the screen sees the same display, and a key pressed by someone else
 * lights up in their colour.
 *
 * <p>
 * Gesture: {@code {key: "0".."9" | "del" | "clear" | "enter"}}. View: {@code {length, entry, wrong, solved}} (the game
 * master's view adds {@code code}; this screen never shows it). A digit, {@code del} and {@code clear} apply on screen at
 * once; {@code enter} waits for the backend, whose answer is either {@code solved} (unlock animation) or a higher
 * {@code wrong} count (error feedback: shake, flash, dull sound, red display). The keyboard works too: digits (row or
 * numeric pad), Backspace, Delete (clear) and Enter.
 *
 * <p>
 * Lock-out (decisions-tranchees.md, D30): the view also carries {@code lock_ms}, the milliseconds the keypad stays locked
 * after the third wrong try in a row (0 when free). The screen counts down from the moment it receives it: the display
 * turns red and says how many seconds are left, and keys are ignored (the backend ignores them too).
 *
 * <p>
 * Offline (debug command) the screen holds a fake code and shows it in a side note, so the look can be judged.
 */
public class KeypadScreen extends MiniGameScreen {

		private static final int KEY_W = 56;
	private static final int KEY_H = 26;
	private static final int KEY_GAP = 4;
	private static final int CELL_W = 30;
	private static final int CELL_H = 34;
	private static final int CELL_GAP = 6;
	private static final long ERROR_MS = 600;

	private final PushButton[] digits = new PushButton[10];
	private final PushButton del;
	private final PushButton clear;
	private final PushButton enter;
	private int length = 4;
	private String entry = "";
	/** Wrong tries so far, -1 until the first view (so reopening the screen does not replay an old error). */
	private int wrong = -1;
	private boolean unlocked;
	private long errorAt = -10000;
	/** When the lock-out ends (milliseconds, wall clock), 0 when the keypad is free. */
	private long lockedUntil;
	private int offlineFails;

	public KeypadScreen(String step, boolean offline) {
		this("keypad", step, offline);
	}

	/** For the keypad variants (dex_code, unown_text): the same pad under another mechanism id, title and help. */
	protected KeypadScreen(String mechanism, String step, boolean offline) {
		super(mechanism, step, offline);
		for (int i = 0; i < 10; i++) {
			final String key = Integer.toString(i);
			digits[i] = add(new PushButton(() -> Component.literal(key), ButtonKind.DEFAULT, () -> press(key)));
		}
		del = add(new PushButton(() -> Component.translatable("phantasmon_events.trahison.minigame.keypad.key_del"), ButtonKind.DEFAULT, () -> press("del")));
		enter = add(new PushButton(() -> Component.translatable("phantasmon_events.trahison.minigame.keypad.key_enter"), ButtonKind.PRIMARY, () -> press("enter")));
		clear = add(new PushButton(() -> Component.translatable("phantasmon_events.trahison.minigame.keypad.key_clear"), ButtonKind.DANGER, () -> press("clear")));
		if (offline) {
			wrong = 0;
		}
	}

	// ---------------------------------------------------------------- variants

	/** The code the offline preview accepts. */
	protected String offlineCode() {
		return "4271";
	}

	/** Compact pad: the keys in one row under the display, so a band of the screen above is free ({@link #bandHeight}). */
	protected boolean compact() {
		return false;
	}

	/** Height reserved above the display in compact mode, for the variant to draw in ({@link #drawBand}). */
	protected int bandHeight() {
		return 0;
	}

	/** The digits are shown in groups of this many, with a wider gap between groups (0: no groups). */
	protected int groupSize() {
		return 0;
	}

	/** Drawn in the band above the display (compact mode only), or beside the pad otherwise. */
	protected void drawBand(GuiGraphics g, int x, int y, int w, int h) {
	}

	// ---------------------------------------------------------------- gestures

	/** A key pressed here (mouse or keyboard): the entry changes at once, then the gesture goes to the backend. */
	private void press(String key) {
		if (locked()) {
			return;
		}
		switch (key) {
			case "del" -> {
				if (entry.isEmpty()) {
					return;
				}
				entry = entry.substring(0, entry.length() - 1);
			}
			case "clear" -> {
				if (entry.isEmpty()) {
					return;
				}
				entry = "";
			}
			case "enter" -> {
				// the backend decides; offline, the fake code does
			}
			default -> {
				if (entry.length() >= length) {
					return;
				}
				entry += key;
			}
		}
		sendGesture(Map.of("key", key));
		if (offline() && key.equals("enter")) {
			if (entry.equals(offlineCode())) {
				unlocked = true;
				succeed();
			} else {
				entry = "";
				wrong++;
				errorAt = System.currentTimeMillis();
				if (++offlineFails >= 3) {
					offlineFails = 0;
					lockedUntil = errorAt + 10_000;
				}
				fail();
			}
		}
	}

	private boolean locked() {
		return lockedUntil > System.currentTimeMillis();
	}

	private PushButton buttonOf(String key) {
		return switch (key) {
			case "del" -> del;
			case "clear" -> clear;
			case "enter" -> enter;
			default -> key.length() == 1 && Character.isDigit(key.charAt(0)) ? digits[key.charAt(0) - '0'] : null;
		};
	}

	// ---------------------------------------------------------------- layout

	@Override
	protected void layout() {
		if (compact()) {
			int gap = 2;
			int kw = Math.max(16, Math.min(30, (contentW - 16 - 11 * gap) / 12));
			int kh = 22;
			int total = 12 * kw + 11 * gap;
			int left = contentX + (contentW - total) / 2;
			int top = displayY() + cellH() + 8;
			for (int n = 0; n < 10; n++) {
				digits[n].at(left + n * (kw + gap), top, kw, kh);
			}
			del.at(left + 10 * (kw + gap), top, kw, kh);
			enter.at(left + 11 * (kw + gap), top, kw, kh);
			placeClear();
			return;
		}
		int gridW = 3 * KEY_W + 2 * KEY_GAP;
		int left = contentX + (contentW - gridW) / 2;
		int top = contentY + 8 + CELL_H + 8;
		// 1 2 3 / 4 5 6 / 7 8 9 / < 0 OK
		for (int n = 1; n <= 9; n++) {
			digits[n].at(left + ((n - 1) % 3) * (KEY_W + KEY_GAP), top + ((n - 1) / 3) * (KEY_H + KEY_GAP), KEY_W, KEY_H);
		}
		del.at(left, top + 3 * (KEY_H + KEY_GAP), KEY_W, KEY_H);
		digits[0].at(left + KEY_W + KEY_GAP, top + 3 * (KEY_H + KEY_GAP), KEY_W, KEY_H);
		enter.at(left + 2 * (KEY_W + KEY_GAP), top + 3 * (KEY_H + KEY_GAP), KEY_W, KEY_H);
		placeClear();
	}

	private int displayY() {
		return contentY + (compact() ? bandHeight() + 6 : 8);
	}

	private int cellH() {
		return compact() ? 26 : CELL_H;
	}

	/** Cells narrow down when the code is long, so nine digits still fit. */
	private int cellW() {
		return length > 6 ? 24 : compact() ? 26 : CELL_W;
	}

	/** Extra gap before cell {@code i} because a group starts there. */
	private int groupGap(int i) {
		return groupSize() > 0 && i > 0 && i % groupSize() == 0 ? 8 : 0;
	}

	private int cellX(int i) {
		int x = displayX();
		for (int k = 0; k < i; k++) {
			x += cellW() + CELL_GAP + groupGap(k + 1);
		}
		return x;
	}

	private int fullWidth() {
		int w = length * cellW() + (length - 1) * CELL_GAP;
		for (int i = 1; i < length; i++) {
			w += groupGap(i);
		}
		return w;
	}

	private int displayX() {
		return contentX + (contentW - fullWidth()) / 2;
	}

	/** The clear button sits right of the display, whose width depends on the code length (known once a view arrives). */
	private void placeClear() {
		int x = displayX() + fullWidth() + 8;
		clear.at(Math.min(x, contentX + contentW - 34), displayY() + (cellH() - 22) / 2, 30, 22);
	}

	// ---------------------------------------------------------------- sync

	@Override
	protected void applyView(Map<String, Object> view) {
		int newLength = Math.max(1, Math.min(12, PuzzleScreens.asInt(view.get("length"), length)));
		if (newLength != length) {
			length = newLength;
			placeClear();
		}
		entry = PuzzleScreens.asString(view.get("entry"), "");
		int w = PuzzleScreens.asInt(view.get("wrong"), 0);
		if (wrong >= 0 && w > wrong) {
			errorAt = System.currentTimeMillis();
			fail();
		}
		wrong = w;
		int lockMs = PuzzleScreens.asInt(view.get("lock_ms"), 0);
		lockedUntil = lockMs > 0 ? System.currentTimeMillis() + lockMs : 0;
		unlocked = Boolean.TRUE.equals(view.get("solved"));
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		for (Map<String, Object> e : events) {
			String type = PuzzleScreens.asString(e.get("type"), "");
			PushButton b = switch (type) {
				case "key" -> buttonOf(PuzzleScreens.asString(e.get("key"), ""));
				case "wrong", "unlock" -> enter;
				default -> null;
			};
			if (b != null) {
				b.flash(200);
				remoteTouch(player, b.centerX() + 14, b.centerY() - 8);
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		PushButton b = digits[(int) (System.currentTimeMillis() / 3200 % 10)];
		b.flash(200);
		remoteTouch(player, b.centerX() + 14, b.centerY() - 8);
	}

	// ---------------------------------------------------------------- drawing

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		placeClear();
		long now = System.currentTimeMillis();
		boolean lock = lockedUntil > now;
		boolean error = lock || now - errorAt < ERROR_MS;
		int x = displayX();
		int y = displayY();
		int ch = cellH();
		int cw = cellW();
		if (compact() && bandHeight() > 0) {
			drawBand(g, contentX + 8, contentY + 4, contentW - 16, bandHeight());
		}
		int frame = unlocked || isSolved() ? TrahisonPalette.SUCCESS : error ? TrahisonPalette.DANGER : TrahisonPalette.EDGE;
		g.fill(x - 4, y - 4, x + fullWidth() + 4, y + ch + 4, TrahisonPalette.BORDER);
		g.fill(x - 3, y - 3, x + fullWidth() + 3, y + ch + 3, frame);
		for (int i = 0; i < length; i++) {
			int cx = cellX(i);
			g.fill(cx, y, cx + cw, y + ch, error ? TrahisonUi.lerp(0.25f, TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.DANGER_DARK) : TrahisonPalette.PANEL_SUNKEN);
			g.fill(cx, y, cx + cw, y + 1, TrahisonPalette.BORDER);
			if (i < entry.length()) {
				Component c = Component.literal(String.valueOf(entry.charAt(i)));
				int w = TrahisonUi.width(c) * 2;
				TrahisonUi.bigText(g, c, cx + (cw - w) / 2, y + (ch - 14) / 2, 2f,
						unlocked || isSolved() ? TrahisonPalette.SUCCESS : error ? TrahisonPalette.DANGER : TrahisonPalette.ACCENT_HOT);
			} else if (i == entry.length() && !isSolved() && TrahisonUi.pulse(900) > 0.5f) {
				g.fill(cx + cw / 2 - 6, y + ch - 8, cx + cw / 2 + 6, y + ch - 6, TrahisonPalette.ACCENT_DIM);
			} else {
				g.fill(cx + cw / 2 - 3, y + ch / 2 - 1, cx + cw / 2 + 3, y + ch / 2 + 1, TrahisonPalette.TEXT_DISABLED);
			}
		}
		if (lock) {
			int seconds = (int) Math.ceil((lockedUntil - now) / 1000.0);
			g.fill(x, y, x + fullWidth(), y + ch, TrahisonUi.lerp(0.5f, TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.DANGER_DARK));
			MenuUi.centered(g, Component.translatable("phantasmon_events.trahison.minigame.keypad.locked", seconds), x + fullWidth() / 2, y + (ch - 8) / 2,
					fullWidth() - 8, TrahisonPalette.DANGER);
		}
		if (!compact()) {
			drawBand(g, contentX + 6, contentY + 8, 94, contentH - 40);
		}
		int noteX = contentX + 10;
		int noteY = compact() ? y + (ch - 8) / 2 : contentY + contentH - 22;
		int room = x - contentX - 16;
		if (wrong > 0 && (!compact() || room >= 60)) {
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.keypad.wrong", wrong), noteX, noteY, compact() ? room : 110, TrahisonPalette.TEXT_MUTED);
		}
		if (offline() && !compact()) {
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.keypad.test_code", offlineCode()), noteX, noteY + 11, 125, TrahisonPalette.GAME_MASTER);
		} else if (offline() && contentX + contentW - (clear.x() + clear.w()) >= 70) {
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.keypad.test_code", offlineCode()), clear.x() + clear.w() + 6, noteY,
					contentX + contentW - (clear.x() + clear.w()) - 10, TrahisonPalette.GAME_MASTER);
		}
	}

	// ---------------------------------------------------------------- keyboard

	@Override
	protected boolean key(int key) {
		String k = null;
		if (key >= 48 && key <= 57) { // digit row
			k = Integer.toString(key - 48);
		} else if (key >= 320 && key <= 329) { // numeric pad
			k = Integer.toString(key - 320);
		} else if (key == 259) { // backspace
			k = "del";
		} else if (key == 261) { // delete
			k = "clear";
		} else if (key == 257 || key == 335) { // enter, numeric pad enter
			k = "enter";
		}
		PushButton b = k == null ? null : buttonOf(k);
		if (b == null) {
			return false;
		}
		b.activate();
		return true;
	}
}
