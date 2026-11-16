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

/** Mechanism {@code debug_button}: a single button; one press solves it. View {@code {pressed}}, event {@code {type: "press"}}. */
public final class DebugButtonScreen extends MiniGameScreen {

	private final PushButton button;
	private boolean pressed;

	public DebugButtonScreen(String step, boolean offline) {
		super("debug_button", step, offline);
		button = add(new PushButton(() -> Component.translatable("phantasmon_events.trahison.minigame.debug_button.press"), ButtonKind.PRIMARY, this::press));
	}

	private void press() {
		pressed = true;
		sendGesture(Map.of("type", "press"));
		if (offline()) {
			succeed();
		}
	}

	@Override
	protected void layout() {
		int w = 120;
		int h = 28;
		button.at(contentX + (contentW - w) / 2, contentY + (contentH - h) / 2, w, h);
	}

	@Override
	protected void applyView(Map<String, Object> view) {
		pressed = Boolean.TRUE.equals(view.get("pressed"));
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		for (Map<String, Object> e : events) {
			if ("press".equals(e.get("type"))) {
				button.flash(250);
				remoteTouch(player, button.centerX() + 20, button.centerY());
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		button.flash(250);
		remoteTouch(player, button.centerX() + 20, button.centerY());
	}

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		Component state = Component.translatable(pressed
				? "phantasmon_events.trahison.minigame.debug_button.state_pressed"
				: "phantasmon_events.trahison.minigame.debug_button.state_idle");
		MenuUi.centered(g, state, button.centerX(), button.y() + button.h() + 12, contentW - 16,
				pressed ? TrahisonPalette.SUCCESS : TrahisonPalette.TEXT_MUTED);
	}

	@Override
	protected boolean key(int key) {
		if (key == 257 || key == 32) {
			button.activate();
			return true;
		}
		return false;
	}
}
