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
 * Offline (debug command) the screen holds a fake code and shows it in a side note, so the look can be judged.
 */
public final class KeypadScreen extends MiniGameScreen {

	private static final String OFFLINE_CODE = "4271";
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

	public KeypadScreen(String step, boolean offline) {
		super("keypad", step, offline);
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

	// ---------------------------------------------------------------- gestures

	/** A key pressed here (mouse or keyboard): the entry changes at once, then the gesture goes to the backend. */
	private void press(String key) {
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
			if (entry.equals(OFFLINE_CODE)) {
				unlocked = true;
				succeed();
			} else {
				entry = "";
				wrong++;
				errorAt = System.currentTimeMillis();
				fail();
			}
		}
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

	private int displayWidth() {
		return length * CELL_W + (length - 1) * CELL_GAP;
	}

	private int displayX() {
		return contentX + (contentW - displayWidth()) / 2;
	}

	/** The clear button sits right of the display, whose width depends on the code length (known once a view arrives). */
	private void placeClear() {
		clear.at(displayX() + displayWidth() + 10, contentY + 8 + (CELL_H - 22) / 2, 30, 22);
	}

	// ---------------------------------------------------------------- sync

	@Override
	protected void applyView(Map<String, Object> view) {
		int newLength = Math.max(1, Math.min(8, PuzzleScreens.asInt(view.get("length"), length)));
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
		boolean error = now - errorAt < ERROR_MS;
		int x = displayX();
		int y = contentY + 8;
		int frame = unlocked || isSolved() ? TrahisonPalette.SUCCESS : error ? TrahisonPalette.DANGER : TrahisonPalette.EDGE;
		g.fill(x - 4, y - 4, x + displayWidth() + 4, y + CELL_H + 4, TrahisonPalette.BORDER);
		g.fill(x - 3, y - 3, x + displayWidth() + 3, y + CELL_H + 3, frame);
		for (int i = 0; i < length; i++) {
			int cx = x + i * (CELL_W + CELL_GAP);
			g.fill(cx, y, cx + CELL_W, y + CELL_H, error ? TrahisonUi.lerp(0.25f, TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.DANGER_DARK) : TrahisonPalette.PANEL_SUNKEN);
			g.fill(cx, y, cx + CELL_W, y + 1, TrahisonPalette.BORDER);
			if (i < entry.length()) {
				Component c = Component.literal(String.valueOf(entry.charAt(i)));
				int w = TrahisonUi.width(c) * 2;
				TrahisonUi.bigText(g, c, cx + (CELL_W - w) / 2, y + (CELL_H - 14) / 2, 2f,
						unlocked || isSolved() ? TrahisonPalette.SUCCESS : error ? TrahisonPalette.DANGER : TrahisonPalette.ACCENT_HOT);
			} else if (i == entry.length() && !isSolved() && TrahisonUi.pulse(900) > 0.5f) {
				g.fill(cx + CELL_W / 2 - 6, y + CELL_H - 8, cx + CELL_W / 2 + 6, y + CELL_H - 6, TrahisonPalette.ACCENT_DIM);
			} else {
				g.fill(cx + CELL_W / 2 - 3, y + CELL_H / 2 - 1, cx + CELL_W / 2 + 3, y + CELL_H / 2 + 1, TrahisonPalette.TEXT_DISABLED);
			}
		}
		int noteX = contentX + 10;
		int noteY = contentY + contentH - 22;
		if (wrong > 0) {
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.keypad.wrong", wrong), noteX, noteY, 110, TrahisonPalette.TEXT_MUTED);
		}
		if (offline()) {
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.keypad.test_code", OFFLINE_CODE), noteX, noteY + 11, 150,
					TrahisonPalette.GAME_MASTER);
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
