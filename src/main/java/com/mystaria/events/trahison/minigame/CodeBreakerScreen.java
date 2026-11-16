package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.PushButton;
import com.mystaria.events.trahison.minigame.widget.Widget;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonState;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code code_breaker}: a terminal that guesses a hidden combination. The group types a try with the token
 * keys, confirms it, and the terminal answers how many tokens are well placed (filled disc) and misplaced (ring); the
 * answers stay on the screen as a typed history. Nothing in the room tells the combination: logic is enough.
 *
 * <p>
 * Gesture: {@code {key: "0".."9" | "del" | "clear" | "enter"}} where a digit is the index of a token in the view's
 * {@code alphabet} (glyph ids or digits). View: {@code {alphabet, kind, length, repeats, maxTries, entry, history[{guess,
 * exact, misplaced}], tries, resets, solved}}; the game master's view adds {@code code}, which this screen never shows.
 * Tokens and {@code del}/{@code clear} apply on screen at once; {@code enter} waits for the backend, whose answer is a new
 * history row, a {@code solved} (unlock) or a higher {@code resets} (the terminal redrew its combination: error
 * feedback). Keyboard: digits (1..n for symbols, 0..n-1 for digits), Backspace, Delete, Enter.
 *
 * <p>
 * Offline (debug command) the screen holds a fake combination and plays the answers itself, shown in a note.
 */
public final class CodeBreakerScreen extends MiniGameScreen {

	private static final int[] OFFLINE_CODE = { 2, 0, 4, 1 };
	private static final int KEY_W = 38;
	private static final int KEY_H = 24;
	private static final int KEY_GAP = 4;
	private static final int ROW_H = 13;
	private static final int CELL = 15;
	private static final long FLASH_MS = 900;

	private record Row(int[] guess, int exact, int misplaced) {
	}

	/** A token key: a raised button carrying a glyph or a digit, and its index as a small number for the keyboard. */
	private final class TokenKey extends Widget {

		private final int index;
		private boolean down;
		private long flashUntil;

		TokenKey(int index) {
			this.index = index;
		}

		void flash(long ms) {
			flashUntil = System.currentTimeMillis() + ms;
		}

		void activate() {
			if (!enabled) {
				return;
			}
			flash(120);
			MiniGameSounds.click();
			press(Integer.toString(index));
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (button != 0 || !hovered(mx, my)) {
				return false;
			}
			down = true;
			MiniGameSounds.click();
			return true;
		}

		@Override
		public boolean mouseReleased(double mx, double my, int button) {
			if (!down) {
				return false;
			}
			down = false;
			if (hovered(mx, my)) {
				press(Integer.toString(index));
			}
			return true;
		}

		@Override
		public void render(GuiGraphics g, int mx, int my) {
			boolean hot = hovered(mx, my);
			boolean pressed = (down && hot) || System.currentTimeMillis() < flashUntil;
			boolean blocked = !repeats && entry.indexOf((char) ('0' + index)) >= 0;
			ButtonState state = !enabled ? ButtonState.DISABLED : pressed ? ButtonState.PRESSED : hot ? ButtonState.HOVER : ButtonState.NORMAL;
			TrahisonUi.button(g, x, y, w, h, Component.empty(), ButtonKind.DEFAULT, state);
			int shift = pressed ? 1 : 0;
			int color = blocked || !enabled ? TrahisonPalette.TEXT_DISABLED : TrahisonPalette.ACCENT_HOT;
			drawToken(g, index, x + w / 2 + 3, y + h / 2 + shift, 13, color);
			MenuUi.text(g, Component.literal(label()), x + 3, y + 3 + shift, 10, TrahisonPalette.TEXT_MUTED);
		}

		private String label() {
			return Integer.toString(symbols ? index + 1 : index);
		}
	}

	private final PushButton del;
	private final PushButton clear;
	private final PushButton enter;
	private final List<TokenKey> keys = new ArrayList<>();
	private List<String> alphabet = new ArrayList<>();
	private boolean symbols = true;
	private int length = 4;
	private boolean repeats;
	private int maxTries;
	private String entry = "";
	private List<Row> history = new ArrayList<>();
	private int tries;
	/** Resets so far, -1 until the first view (so reopening the screen does not replay an old reset). */
	private int resets = -1;
	private boolean unlocked;
	private long resetAt = -10000;
	private long rowAt = -10000;

	public CodeBreakerScreen(String step, boolean offline) {
		super("code_breaker", step, offline);
		del = new PushButton(() -> Component.translatable("phantasmon_events.trahison.minigame.code_breaker.key_del"), ButtonKind.DEFAULT, () -> press("del"));
		clear = new PushButton(() -> Component.translatable("phantasmon_events.trahison.minigame.code_breaker.key_clear"), ButtonKind.DANGER, () -> press("clear"));
		enter = new PushButton(() -> Component.translatable("phantasmon_events.trahison.minigame.code_breaker.key_enter"), ButtonKind.PRIMARY, () -> press("enter"));
		if (offline) {
			resets = 0;
			List<String> ids = new ArrayList<>(Glyphs.IDS.subList(0, 6));
			rebuild(ids, true);
		} else {
			rebuild(List.of(), true);
		}
	}

	/** The keys depend on the alphabet, known once a view arrives: build them again whenever it changes. */
	private void rebuild(List<String> newAlphabet, boolean newSymbols) {
		alphabet = new ArrayList<>(newAlphabet);
		symbols = newSymbols;
		clearWidgets();
		keys.clear();
		for (int i = 0; i < alphabet.size(); i++) {
			keys.add(add(new TokenKey(i)));
		}
		add(del);
		add(clear);
		add(enter);
		if (contentW > 0) {
			layout();
		}
	}

	// ---------------------------------------------------------------- tokens

	private void drawToken(GuiGraphics g, int index, int cx, int cy, int size, int color) {
		if (index < 0 || index >= alphabet.size()) {
			return;
		}
		if (symbols) {
			Glyphs.draw(g, alphabet.get(index), cx, cy, Math.max(9, size), 0, color);
		} else {
			Component c = Component.literal(alphabet.get(index));
			int tw = TrahisonUi.width(c);
			MenuUi.text(g, c, cx - tw / 2, cy - 4, 14, color);
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
				if (entry.length() != length) {
					return;
				}
			}
			default -> {
				if (entry.length() >= length || (!repeats && entry.contains(key))) {
					return;
				}
				entry += key;
			}
		}
		sendGesture(Map.of("key", key));
		if (offline() && key.equals("enter")) {
			offlineEnter();
		}
	}

	private void offlineEnter() {
		int[] guess = new int[length];
		for (int i = 0; i < length; i++) {
			guess[i] = entry.charAt(i) - '0';
		}
		int exact = 0;
		int common = 0;
		for (int i = 0; i < length; i++) {
			if (guess[i] == OFFLINE_CODE[i]) {
				exact++;
			}
			for (int j = 0; j < length; j++) {
				if (guess[i] == OFFLINE_CODE[j]) {
					common++;
					break;
				}
			}
		}
		history.add(new Row(guess, exact, common - exact));
		tries++;
		entry = "";
		rowAt = System.currentTimeMillis();
		if (exact == length) {
			unlocked = true;
			succeed();
		}
	}

	private TokenKey keyOf(String key) {
		if (key.length() == 1 && Character.isDigit(key.charAt(0))) {
			int i = key.charAt(0) - '0';
			return i < keys.size() ? keys.get(i) : null;
		}
		return null;
	}

	// ---------------------------------------------------------------- layout

	private static final int TERM_W = 236;
	private int termX() {
		return contentX + 6;
	}

	private int termY() {
		return contentY + 6;
	}

	private int termH() {
		return contentH - 12;
	}

	private int keysX() {
		return termX() + TERM_W + 10;
	}

	@Override
	protected void layout() {
		int kx = keysX();
		int ky = contentY + 10;
		for (int i = 0; i < keys.size(); i++) {
			keys.get(i).at(kx + (i % 3) * (KEY_W + KEY_GAP), ky + (i / 3) * (KEY_H + KEY_GAP), KEY_W, KEY_H);
		}
		int cy = contentY + contentH - 6 - KEY_H;
		del.at(kx, cy, KEY_W, KEY_H);
		clear.at(kx + KEY_W + KEY_GAP, cy, KEY_W, KEY_H);
		enter.at(kx + 2 * (KEY_W + KEY_GAP), cy, KEY_W, KEY_H);
	}

	// ---------------------------------------------------------------- sync

	@Override
	protected void applyView(Map<String, Object> view) {
		List<String> newAlphabet = new ArrayList<>();
		for (Object o : PuzzleScreens.asList(view.get("alphabet"))) {
			newAlphabet.add(PuzzleScreens.asString(o, "?"));
		}
		boolean newSymbols = !"digits".equals(PuzzleScreens.asString(view.get("kind"), "symbols"));
		if (!newAlphabet.isEmpty() && (!newAlphabet.equals(alphabet) || newSymbols != symbols)) {
			rebuild(newAlphabet, newSymbols);
		}
		length = Math.max(1, Math.min(8, PuzzleScreens.asInt(view.get("length"), length)));
		repeats = Boolean.TRUE.equals(view.get("repeats"));
		maxTries = PuzzleScreens.asInt(view.get("maxTries"), 0);
		entry = PuzzleScreens.asString(view.get("entry"), "");
		List<Row> rows = new ArrayList<>();
		for (Object o : PuzzleScreens.asList(view.get("history"))) {
			Map<String, Object> r = PuzzleScreens.asMap(o);
			List<Object> g = PuzzleScreens.asList(r.get("guess"));
			int[] guess = new int[g.size()];
			for (int i = 0; i < guess.length; i++) {
				guess[i] = PuzzleScreens.asInt(g.get(i), 0);
			}
			rows.add(new Row(guess, PuzzleScreens.asInt(r.get("exact"), 0), PuzzleScreens.asInt(r.get("misplaced"), 0)));
		}
		if (rows.size() > history.size() && resets >= 0) {
			rowAt = System.currentTimeMillis();
		}
		history = rows;
		tries = PuzzleScreens.asInt(view.get("tries"), rows.size());
		int r = PuzzleScreens.asInt(view.get("resets"), 0);
		if (resets >= 0 && r > resets) {
			resetAt = System.currentTimeMillis();
			fail();
		}
		resets = r;
		unlocked = Boolean.TRUE.equals(view.get("solved"));
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		for (Map<String, Object> e : events) {
			String type = PuzzleScreens.asString(e.get("type"), "");
			Widget b = switch (type) {
				case "key" -> {
					String k = PuzzleScreens.asString(e.get("key"), "");
					yield switch (k) {
						case "del" -> del;
						case "clear" -> clear;
						default -> keyOf(k);
					};
				}
				case "feedback", "unlock", "reset" -> enter;
				default -> null;
			};
			if (b instanceof PushButton p) {
				p.flash(200);
			} else if (b instanceof TokenKey t) {
				t.flash(200);
			}
			if (b != null) {
				remoteTouch(player, b.centerX() + 14, b.centerY() - 8);
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		if (keys.isEmpty()) {
			return;
		}
		TokenKey b = keys.get((int) (System.currentTimeMillis() / 3200 % keys.size()));
		b.flash(200);
		remoteTouch(player, b.centerX() + 14, b.centerY() - 8);
	}

	// ---------------------------------------------------------------- drawing

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		long now = System.currentTimeMillis();
		int x = termX();
		int y = termY();
		int w = TERM_W;
		int h = termH();
		boolean done = unlocked || isSolved();
		// The screen: a dark glass with the frame's colour telling the state, and scanlines.
		int frame = done ? TrahisonPalette.SUCCESS : now - resetAt < 700 ? TrahisonPalette.DANGER : TrahisonPalette.EDGE;
		g.fill(x - 2, y - 2, x + w + 2, y + h + 2, TrahisonPalette.BORDER);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, frame);
		g.fill(x, y, x + w, y + h, TrahisonPalette.BG_DEEP);
		for (int ly = y + 1; ly < y + h; ly += 3) {
			g.fill(x, ly, x + w, ly + 1, TrahisonUi.withAlpha(TrahisonPalette.ACCENT, 0x0C));
		}
		// Header: prompt, tries and the legend of the two answers.
		Component head = maxTries > 0
				? Component.translatable("phantasmon_events.trahison.minigame.code_breaker.tries_max", tries, maxTries)
				: Component.translatable("phantasmon_events.trahison.minigame.code_breaker.tries", tries);
		MenuUi.text(g, Component.literal("> "), x + 6, y + 5, 12, TrahisonPalette.ACCENT_DIM);
		MenuUi.text(g, head, x + 16, y + 5, 110, TrahisonPalette.ACCENT);
		int lx = x + w - 6;
		Component misplacedLabel = Component.translatable("phantasmon_events.trahison.minigame.code_breaker.legend_misplaced");
		int mw = Math.min(54, TrahisonUi.width(misplacedLabel));
		lx -= mw;
		MenuUi.text(g, misplacedLabel, lx, y + 5, 54, TrahisonPalette.TEXT_MUTED);
		TrahisonUi.ring(g, lx - 6, y + 9, 3, 2, TrahisonPalette.ACCENT);
		Component exactLabel = Component.translatable("phantasmon_events.trahison.minigame.code_breaker.legend_exact");
		int ew = Math.min(54, TrahisonUi.width(exactLabel));
		lx -= 12 + ew;
		MenuUi.text(g, exactLabel, lx, y + 5, 54, TrahisonPalette.TEXT_MUTED);
		TrahisonUi.disc(g, lx - 6, y + 9, 3, TrahisonPalette.SUCCESS);
		g.fill(x + 4, y + 17, x + w - 4, y + 18, TrahisonPalette.EDGE);

		// History: the last rows that fit, then the entry line.
		int entryY = y + h - 20;
		int rowsTop = y + 22;
		int visible = Math.max(1, (entryY - 4 - rowsTop) / ROW_H);
		int from = Math.max(0, history.size() - visible);
		for (int i = from; i < history.size(); i++) {
			drawRow(g, history.get(i), i, x + 6, rowsTop + (i - from) * ROW_H, now, i == history.size() - 1);
		}
		if (history.isEmpty()) {
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.code_breaker.empty"), x + 8, rowsTop + 2, w - 16, TrahisonPalette.TEXT_DISABLED);
		}
		// Entry line: the shared try being typed, cells and a blinking cursor.
		g.fill(x + 4, entryY - 3, x + w - 4, entryY - 2, TrahisonPalette.EDGE);
		MenuUi.text(g, Component.literal(">"), x + 6, entryY + 4, 10, TrahisonPalette.ACCENT);
		for (int i = 0; i < length; i++) {
			int cx = x + 18 + i * (CELL + 3);
			g.fill(cx, entryY, cx + CELL, entryY + CELL, TrahisonPalette.BORDER);
			g.fill(cx + 1, entryY + 1, cx + CELL - 1, entryY + CELL - 1, TrahisonPalette.PANEL_SUNKEN);
			if (i < entry.length()) {
				drawToken(g, entry.charAt(i) - '0', cx + CELL / 2, entryY + CELL / 2, 11, done ? TrahisonPalette.SUCCESS : TrahisonPalette.ACCENT_HOT);
			} else if (i == entry.length() && !done && TrahisonUi.pulse(900) > 0.5f) {
				g.fill(cx + 3, entryY + CELL - 4, cx + CELL - 3, entryY + CELL - 3, TrahisonPalette.ACCENT_DIM);
			}
		}
		if (now - resetAt < 2200) {
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.code_breaker.reset"), x + 18 + length * (CELL + 3) + 8, entryY + 4,
					w - (18 + length * (CELL + 3) + 14), TrahisonPalette.DANGER);
		} else if (resets > 0) {
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.code_breaker.resets", resets), x + 18 + length * (CELL + 3) + 8,
					entryY + 4, w - (18 + length * (CELL + 3) + 14), TrahisonPalette.TEXT_MUTED);
		}
		if (offline()) {
			StringBuilder code = new StringBuilder();
			for (int t : OFFLINE_CODE) {
				code.append(symbols ? t + 1 : t).append(' ');
			}
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.code_breaker.test_code", code.toString().trim()), keysX(), contentY + contentH - 44,
					128, TrahisonPalette.GAME_MASTER);
		}
	}

	private void drawRow(GuiGraphics g, Row row, int index, int x, int y, long now, boolean latest) {
		if (latest && now - rowAt < FLASH_MS) {
			float f = 1f - (now - rowAt) / (float) FLASH_MS;
			g.fill(x - 3, y - 2, x + TERM_W - 14, y + ROW_H - 2, TrahisonUi.withAlpha(TrahisonPalette.ACCENT, (int) (0x30 * f)));
		}
		boolean win = row.exact() == row.guess().length;
		MenuUi.text(g, Component.literal(String.format("%02d", index + 1)), x, y + 1, 14, TrahisonPalette.TEXT_DISABLED);
		for (int i = 0; i < row.guess().length; i++) {
			drawToken(g, row.guess()[i], x + 24 + i * (CELL + 1), y + 5, 11, win ? TrahisonPalette.SUCCESS : TrahisonPalette.TEXT_SECONDARY);
		}
		int fx = x + 24 + row.guess().length * (CELL + 1) + 10;
		// Well placed: a filled disc and its number; misplaced: a ring and its number. Shapes differ, colour only helps.
		TrahisonUi.disc(g, fx, y + 5, 3, row.exact() > 0 ? TrahisonPalette.SUCCESS : TrahisonPalette.TEXT_DISABLED);
		MenuUi.text(g, Component.literal(Integer.toString(row.exact())), fx + 7, y + 1, 12, row.exact() > 0 ? TrahisonPalette.SUCCESS : TrahisonPalette.TEXT_DISABLED);
		TrahisonUi.ring(g, fx + 28, y + 5, 3, 2, row.misplaced() > 0 ? TrahisonPalette.ACCENT : TrahisonPalette.TEXT_DISABLED);
		MenuUi.text(g, Component.literal(Integer.toString(row.misplaced())), fx + 35, y + 1, 12, row.misplaced() > 0 ? TrahisonPalette.ACCENT : TrahisonPalette.TEXT_DISABLED);
	}

	// ---------------------------------------------------------------- keyboard

	@Override
	protected boolean key(int key) {
		String k = null;
		int digit = key >= 48 && key <= 57 ? key - 48 : key >= 320 && key <= 329 ? key - 320 : -1;
		if (digit >= 0) {
			int index = symbols ? digit - 1 : digit;
			if (index >= 0 && index < keys.size()) {
				keys.get(index).activate();
				return true;
			}
			return false;
		} else if (key == 259) { // backspace
			k = "del";
		} else if (key == 261) { // delete
			k = "clear";
		} else if (key == 257 || key == 335) { // enter, numeric pad enter
			k = "enter";
		}
		PushButton b = k == null ? null : switch (k) {
			case "del" -> del;
			case "clear" -> clear;
			default -> enter;
		};
		if (b == null) {
			return false;
		}
		b.activate();
		return true;
	}
}
