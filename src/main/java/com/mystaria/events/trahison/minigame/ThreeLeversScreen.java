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
 * Preview of {@code three_levers}, for {@code /phantasmon event trahison minigame three_levers} only. The mechanism is played
 * in the world (a click on a lever slot, see {@code world/LeverState} and the lever's render): this screen is never opened
 * by the router, it shows the three levers, the lease and the shared light so the look can be judged without a room. Offline,
 * the buttons engage a lever for a few seconds, the two demo players engage the others from time to time, and all three
 * engaged at once unlock it. The order and the delay of the real mechanism are not simulated here. It also reads a real view
 * ({@code levers[i] = {on, ms}}), so it would follow a backend if ever opened on a step.
 */
public final class ThreeLeversScreen extends MiniGameScreen {

	private static final long LEASE_MS = 6000;

	private final PushButton[] buttons = new PushButton[3];
	/** Local time (ms) each lever's lease ends; 0 = waiting. */
	private final long[] until = new long[3];
	private final int[] by = { -1, -1, -1 };

	public ThreeLeversScreen(String step, boolean offline) {
		super("three_levers", step, offline);
		for (int i = 0; i < 3; i++) {
			int lever = i;
			buttons[i] = add(new PushButton(() -> Component.translatable("phantasmon_events.trahison.minigame.three_levers.lever", lever + 1),
					ButtonKind.PRIMARY, () -> pull(lever, ownIndex())));
		}
	}

	private boolean on(int lever, long now) {
		return until[lever] > now;
	}

	private int count(long now) {
		int n = 0;
		for (int i = 0; i < 3; i++) {
			if (on(i, now)) {
				n++;
			}
		}
		return n;
	}

	/** A click: one lever per player, engaged for the lease; all three at once unlock (offline only). */
	private void pull(int lever, int player) {
		long now = System.currentTimeMillis();
		if (on(lever, now) && by[lever] != player) {
			return;
		}
		for (int i = 0; i < 3; i++) {
			if (i != lever && on(i, now) && by[i] == player) {
				until[i] = 0;
			}
		}
		until[lever] = now + LEASE_MS;
		by[lever] = player;
		MiniGameSounds.detent(0.8f + 0.3f * count(now));
		if (offline() && count(now) == 3) {
			succeed();
		}
	}

	@Override
	protected void layout() {
		int bw = 84;
		int gap = (contentW - 3 * bw) / 4;
		for (int i = 0; i < 3; i++) {
			buttons[i].at(contentX + gap + i * (bw + gap), contentY + contentH - 34, bw, 24);
		}
	}

	@Override
	protected void applyView(Map<String, Object> view) {
		long now = System.currentTimeMillis();
		List<Object> levers = PuzzleScreens.asList(view.get("levers"));
		for (int i = 0; i < 3 && i < levers.size(); i++) {
			Map<String, Object> l = PuzzleScreens.asMap(levers.get(i));
			until[i] = Boolean.TRUE.equals(l.get("on")) ? now + PuzzleScreens.asInt(l.get("ms"), 0) : 0;
			by[i] = PuzzleScreens.asInt(l.get("by"), -1);
		}
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		for (Map<String, Object> e : events) {
			int lever = PuzzleScreens.asInt(e.get("lever"), 0) - 1;
			if (lever >= 0 && lever < 3) {
				remoteTouch(player, buttons[lever].centerX(), buttons[lever].centerY() - 40);
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		long now = System.currentTimeMillis();
		for (int i = 0; i < 3; i++) {
			if (!on(i, now)) {
				pull(i, player);
				remoteTouch(player, buttons[i].centerX(), buttons[i].centerY() - 40);
				return;
			}
		}
	}

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		long now = System.currentTimeMillis();
		int count = count(now);
		int top = contentY + 34;
		int bottom = contentY + contentH - 50;
		// the shared light: one pip per engaged lever
		int pipsX = contentX + contentW / 2 - 17;
		for (int i = 0; i < 3; i++) {
			int x = pipsX + i * 12;
			g.fill(x - 1, contentY + 10, x + 9, contentY + 20, TrahisonPalette.BORDER);
			g.fill(x, contentY + 11, x + 8, contentY + 19, i < count || isSolved() ? TrahisonPalette.SUCCESS : TrahisonPalette.PANEL_SUNKEN);
		}
		for (int i = 0; i < 3; i++) {
			int cx = buttons[i].centerX();
			boolean engaged = on(i, now) || isSolved();
			g.fill(cx - 8, top - 1, cx + 8, bottom + 1, TrahisonPalette.BORDER);
			g.fill(cx - 7, top, cx + 7, bottom, TrahisonPalette.PANEL_SUNKEN);
			int knobY = engaged ? bottom - 14 : top + 2;
			boolean blink = engaged && !isSolved() && until[i] - now < 2000 && now / 150 % 2 == 0;
			int color = isSolved() ? TrahisonPalette.SUCCESS : !engaged ? TrahisonPalette.DANGER : blink ? TrahisonPalette.ACCENT_DIM : TrahisonPalette.ACCENT_HOT;
			g.fill(cx - 3, top + 2, cx + 3, bottom - 2, TrahisonPalette.EDGE);
			g.fill(cx - 9, knobY, cx + 9, knobY + 12, TrahisonPalette.BORDER);
			g.fill(cx - 8, knobY + 1, cx + 8, knobY + 11, color);
			Component state = Component.translatable(engaged ? "phantasmon_events.trahison.minigame.three_levers.engaged"
					: "phantasmon_events.trahison.minigame.three_levers.waiting");
			MenuUi.centered(g, state, cx, bottom + 6, 90, engaged ? TrahisonPalette.TEXT : TrahisonPalette.TEXT_MUTED);
		}
		MenuUi.centered(g, Component.translatable("phantasmon_events.trahison.minigame.three_levers.preview"), contentX + contentW / 2,
				contentY + 24, contentW - 16, TrahisonPalette.TEXT_MUTED);
	}

	@Override
	protected boolean key(int key) {
		if (key >= 49 && key <= 51) { // 1, 2, 3
			buttons[key - 49].activate();
			return true;
		}
		return false;
	}
}
