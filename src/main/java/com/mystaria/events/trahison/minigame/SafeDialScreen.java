package com.mystaria.events.trahison.minigame;

import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.PushButton;
import com.mystaria.events.trahison.minigame.widget.RotaryRing;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code safe_dial}: a safe opened by sound. Turn the dial (drag it, wheel, or Left and Right keys), listen, and
 * lock (button or Enter) when the dial sits on the number; the numbers must be reached turning the way the screen says
 * (right first, then left, and so on). The cue of every tick comes from the backend as {@code cue} in the view and is played
 * here with vanilla sounds: a dull click far away, a higher click closer, a bell near the number, a clunk and a bell on it.
 * A lamp repeats the cue for those who cannot hear it.
 *
 * <p>
 * Gesture: {@code {delta: +1 | -1}} (one stop) or {@code {lock: true}}. View: {@code {positions, position, numbers, stage,
 * turn, cue, lastDir, resets, solved}} (the game master's view adds {@code combination}; the screen never shows it). A
 * wrong lock makes the safe forget the numbers found: the screen hears {@code stage} fall back or {@code resets} grow and
 * plays the error sound. Offline the screen draws a fake combination and applies the same rules locally.
 */
public final class SafeDialScreen extends MiniGameScreen {

	private RotaryRing dial;
	private PushButton lock;
	private int positions = 40;
	private int numbers = 3;
	private int stage;
	private int turn = 1;
	private int lastDir;
	private int resets;
	private int position;
	private String cue = "far";
	private boolean heard;
	private long cueAt;
	private int cx;
	private int cy;
	private int radius = 48;

	/** Offline only: the fake combination and the local rules. */
	private int[] combination = new int[0];

	public SafeDialScreen(String step, boolean offline) {
		super("safe_dial", step, offline);
		build();
		if (offline) {
			Random rnd = new Random();
			combination = new int[3];
			int previous = rnd.nextInt(positions);
			position = previous;
			for (int i = 0; i < combination.length; i++) {
				do {
					combination[i] = rnd.nextInt(positions);
				} while (distance(combination[i], previous) < 3);
				previous = combination[i];
			}
			dial.setPosition(position);
			cue = offlineCue();
		}
	}

	/** The dial has one stop per position, so it is built again when a view brings another count. */
	private void build() {
		clearWidgets();
		dial = add(new RotaryRing(40, 24, positions)
				.tone(TrahisonPalette.PANEL_RAISED)
				.tip(Component.translatable("phantasmon_events.trahison.minigame.safe_dial.dial"))
				.onStep(this::stepped));
		dial.painter(this::paintDial);
		lock = add(new PushButton(() -> Component.translatable("phantasmon_events.trahison.minigame.safe_dial.lock"), ButtonKind.PRIMARY, this::tryLock));
		if (contentW > 0) {
			layout();
		}
	}

	// ---------------------------------------------------------------- offline rules (the backend's, in miniature)

	private int distance(int a, int b) {
		int d = Math.floorMod(a - b, positions);
		return Math.min(d, positions - d);
	}

	private String offlineCue() {
		int d = distance(position, combination[stage]);
		return d == 0 ? "hit" : d <= 2 ? "near" : d <= Math.max(4, positions / 6) ? "mid" : "far";
	}

	// ---------------------------------------------------------------- gestures

	private void stepped(int delta) {
		position = Math.floorMod(position + delta, positions);
		lastDir = delta;
		sendGesture(Map.of("delta", delta));
		if (offline()) {
			cue = offlineCue();
			heard = true;
			cueAt = System.currentTimeMillis();
			MiniGameSounds.cue(cue);
		}
	}

	private void tryLock() {
		sendGesture(Map.of("lock", true));
		if (!offline() || stage >= combination.length) {
			return;
		}
		if (position == combination[stage] && lastDir == turn) {
			stage++;
			lastDir = 0;
			if (stage == combination.length) {
				succeed();
				return;
			}
			turn = -turn;
			MiniGameSounds.clunk();
		} else {
			stage = 0;
			lastDir = 0;
			turn = 1;
			resets++;
			fail();
		}
		cue = offlineCue();
	}

	@Override
	protected boolean key(int key) {
		switch (key) {
			case 263 -> dial.turn(-1);
			case 262 -> dial.turn(1);
			case 257, 32 -> lock.activate();
			default -> {
				return false;
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- layout and sync

	@Override
	protected void layout() {
		radius = Math.max(30, Math.min(70, contentH / 2 - 6));
		cx = contentX + 8 + radius + 4;
		cy = contentY + contentH / 2;
		dial.radii(radius, Math.max(16, radius * 45 / 100)).at(cx - radius, cy - radius, 2 * radius, 2 * radius);
		int panelX = cx + radius + 16;
		lock.at(panelX, contentY + contentH - 30, Math.max(60, Math.min(120, contentX + contentW - panelX - 8)), 22);
	}

	@Override
	protected void applyView(Map<String, Object> view) {
		int newPositions = PuzzleScreens.asInt(view.get("positions"), positions);
		if (newPositions != positions && newPositions >= 2) {
			positions = newPositions;
			build();
		}
		int oldPosition = position;
		int oldStage = stage;
		int oldResets = resets;
		position = PuzzleScreens.asInt(view.get("position"), position);
		numbers = PuzzleScreens.asInt(view.get("numbers"), numbers);
		stage = PuzzleScreens.asInt(view.get("stage"), stage);
		turn = PuzzleScreens.asInt(view.get("turn"), turn);
		lastDir = PuzzleScreens.asInt(view.get("lastDir"), lastDir);
		resets = PuzzleScreens.asInt(view.get("resets"), resets);
		cue = PuzzleScreens.asString(view.get("cue"), cue);
		if (!dial.dragging()) {
			dial.setPosition(position);
		}
		if (offline()) {
			return;
		}
		// The backend's answer is the feedback: hear it once per change (the first view only sets the baseline).
		if (heard) {
			if (resets > oldResets || stage < oldStage) {
				fail();
			} else if (stage > oldStage) {
				MiniGameSounds.clunk();
			} else if (position != oldPosition) {
				MiniGameSounds.cue(cue);
			}
			if (position != oldPosition || stage != oldStage || resets != oldResets) {
				cueAt = System.currentTimeMillis();
			}
		}
		heard = true;
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		for (Map<String, Object> e : events) {
			if ("tick".equals(e.get("type"))) {
				dial.touch();
				remoteTouch(player, cx + radius * 7 / 10, cy - radius * 7 / 10);
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		if (combination.length == 0) {
			return;
		}
		int delta = player == 1 ? 1 : -1;
		dial.setPosition(dial.position() + delta);
		position = dial.position();
		lastDir = delta;
		cue = offlineCue();
		onRemote(player, List.of(Map.of("type", "tick", "delta", delta)));
	}

	// ---------------------------------------------------------------- drawing

	/** What turns with the dial: the scale, a long mark every fifth stop. The needle is the fixed pointer at the top. */
	private void paintDial(GuiGraphics g, int ringCx, int ringCy, int outer, int inner, float angle) {
		float step = 360f / positions;
		for (int k = 0; k < positions; k++) {
			boolean major = k % 5 == 0;
			// Stop k sits at angle - k * step: turning clockwise raises the number under the pointer.
			TrahisonUi.rotatedFill(g, ringCx, ringCy, angle - k * step, -1, -(outer - 1), 1, -(outer - (major ? 9 : 5)),
					major ? TrahisonPalette.TEXT_SECONDARY : TrahisonPalette.TEXT_MUTED);
		}
	}

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		TrahisonUi.disc(g, cx, cy, dial.inner() - 2, TrahisonPalette.PANEL_SUNKEN);
		TrahisonUi.textEllipsis(g, Component.literal(String.valueOf(position)), cx - 8, cy - 4, 28, TrahisonPalette.TEXT);
		int panelX = cx + radius + 16;
		int pw = contentX + contentW - panelX - 8;
		if (pw < 40) {
			return;
		}
		// The numbers found: one pip per number of the combination.
		TrahisonUi.textEllipsis(g, Component.translatable("phantasmon_events.trahison.minigame.safe_dial.found", stage, numbers), panelX, contentY + 10, pw, TrahisonPalette.TEXT_SECONDARY);
		for (int i = 0; i < numbers; i++) {
			int px = panelX + 6 + i * 16;
			TrahisonUi.disc(g, px, contentY + 34, 6, TrahisonPalette.BORDER);
			TrahisonUi.disc(g, px, contentY + 34, 5, i < stage ? (isSolved() ? TrahisonPalette.SUCCESS : TrahisonPalette.ACCENT) : TrahisonPalette.PANEL_SUNKEN);
		}
		// The way the next number wants to be reached.
		Component way = Component.translatable(turn > 0 ? "phantasmon_events.trahison.minigame.safe_dial.turn_right" : "phantasmon_events.trahison.minigame.safe_dial.turn_left");
		TrahisonUi.textEllipsis(g, way, panelX, contentY + 52, pw, TrahisonPalette.ACCENT);
		// The lamp: the cue as light, brighter and warmer the closer the dial is.
		long age = System.currentTimeMillis() - cueAt;
		int base = switch (cue) {
			case "hit" -> TrahisonPalette.SUCCESS;
			case "near" -> TrahisonPalette.ACCENT_HOT;
			case "mid" -> TrahisonPalette.ACCENT_DIM;
			default -> TrahisonPalette.PANEL_RAISED;
		};
		int lamp = age < 400 ? TrahisonUi.lerp(1f - age / 400f, base, TrahisonPalette.TEXT) : base;
		TrahisonUi.disc(g, panelX + 10, contentY + 80, 9, TrahisonPalette.BORDER);
		TrahisonUi.disc(g, panelX + 10, contentY + 80, 8, lamp);
		TrahisonUi.textEllipsis(g, Component.translatable("phantasmon_events.trahison.minigame.safe_dial.listen"), panelX + 26, contentY + 76, pw - 26, TrahisonPalette.TEXT_MUTED);
	}

	@Override
	protected void drawOver(GuiGraphics g, int mx, int my) {
		// The pointer: fixed at the top of the dial, pointing down at the scale.
		int color = isSolved() ? TrahisonPalette.SUCCESS : TrahisonPalette.ACCENT;
		int top = cy - radius;
		for (int row = 0; row < 6; row++) {
			g.fill(cx - 5 + row, top - 8 + row, cx + 5 - row, top - 7 + row, color);
		}
		if (lastDir != 0) {
			Component arrow = Component.literal(lastDir > 0 ? ">" : "<");
			MenuUi.text(g, arrow, cx + (lastDir > 0 ? 10 : -16), top - 10, 12, TrahisonPalette.TEXT_MUTED);
		}
	}
}
