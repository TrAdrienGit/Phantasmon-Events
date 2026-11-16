package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.DetentKnob;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code three_dials}: three dials of 0..9 to line up. A gesture is {@code {dial, delta}}; the view is
 * {@code {dials: [a, b, c], solved}} and never holds the answer. Offline, the screen picks a local target so that the
 * unlock animation can be seen.
 */
public final class ThreeDialsScreen extends MiniGameScreen {

	private static final int[] OFFLINE_TARGET = { 3, 7, 1 };
	private final List<DetentKnob> dials = new ArrayList<>();
	private int selected = 0;

	public ThreeDialsScreen(String step, boolean offline) {
		super("three_dials", step, offline);
		for (int i = 0; i < 3; i++) {
			final int index = i;
			DetentKnob knob = add(new DetentKnob(10).onTurn(delta -> turned(index, delta)));
			dials.add(knob);
		}
		if (offline) {
			// Start away from the target so the screen is not solved at once.
			dials.get(0).setValue(8);
			dials.get(1).setValue(2);
			dials.get(2).setValue(5);
		}
	}

	private void turned(int index, int delta) {
		selected = index;
		sendGesture(Map.of("dial", index, "delta", delta));
		if (offline()) {
			checkOfflineSolved();
		}
	}

	private void checkOfflineSolved() {
		for (int i = 0; i < 3; i++) {
			if (dials.get(i).value() != OFFLINE_TARGET[i]) {
				return;
			}
		}
		succeed();
	}

	@Override
	protected void layout() {
		int size = 64;
		int gap = Math.min(40, (contentW - 3 * size) / 4);
		int total = 3 * size + 2 * gap;
		int left = contentX + (contentW - total) / 2;
		int top = contentY + (contentH - size) / 2;
		for (int i = 0; i < 3; i++) {
			dials.get(i).at(left + i * (size + gap), top, size, size);
		}
	}

	@Override
	protected void applyView(Map<String, Object> view) {
		List<Object> values = PuzzleScreens.asList(view.get("dials"));
		for (int i = 0; i < 3 && i < values.size(); i++) {
			dials.get(i).setValue(PuzzleScreens.asInt(values.get(i), dials.get(i).value()));
		}
		boolean done = Boolean.TRUE.equals(view.get("solved"));
		for (DetentKnob k : dials) {
			k.glow(done ? TrahisonPalette.SUCCESS : 0);
		}
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		for (Map<String, Object> e : events) {
			if ("dial".equals(e.get("type"))) {
				int i = Math.max(0, Math.min(2, PuzzleScreens.asInt(e.get("dial"), 0)));
				dials.get(i).touch();
				remoteTouch(player, dials.get(i).centerX() + 14, dials.get(i).centerY() - 10);
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		int i = (int) (System.currentTimeMillis() / 3200 % 3);
		DetentKnob k = dials.get(i);
		k.setValue(k.value() + (player == 1 ? 1 : -1));
		k.touch();
		remoteTouch(player, k.centerX() + 14, k.centerY() - 10);
	}

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		// Caption plate under each dial: its number, so players can talk about "the second dial".
		for (int i = 0; i < 3; i++) {
			DetentKnob k = dials.get(i);
			Component label = Component.translatable("phantasmon_events.trahison.minigame.three_dials.dial", i + 1);
			int w = TrahisonUi.width(label) + 12;
			int x = k.centerX() - w / 2;
			int y = k.y() + k.h() + 16;
			g.fill(x - 1, y - 1, x + w + 1, y + 13, TrahisonPalette.BORDER);
			g.fill(x, y, x + w, y + 12, i == selected ? TrahisonPalette.ACCENT_DIM : TrahisonPalette.PANEL_RAISED);
			MenuUi.centered(g, label, x + w / 2, y + 2, w - 4, TrahisonPalette.TEXT);
		}
	}

	@Override
	protected boolean key(int key) {
		switch (key) {
			case 263 -> { // left
				selected = Math.floorMod(selected - 1, 3);
				return true;
			}
			case 262 -> { // right
				selected = Math.floorMod(selected + 1, 3);
				return true;
			}
			case 265 -> { // up
				dials.get(selected).turn(1);
				return true;
			}
			case 264 -> { // down
				dials.get(selected).turn(-1);
				return true;
			}
			default -> {
				return false;
			}
		}
	}
}
