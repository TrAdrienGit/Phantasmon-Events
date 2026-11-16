package com.mystaria.events.trahison.minigame.widget;

import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.MiniGameSounds;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonState;

/**
 * A push button: pressed (sunken, label shifted) while the mouse is held on it, it acts on release inside. The same
 * shapes as every other button of the game ({@link TrahisonUi#button}); {@link #flash} shows it pressed for a moment
 * when someone else, or the keyboard, presses it.
 */
public final class PushButton extends Widget {

	private final Supplier<Component> label;
	private final Runnable action;
	private final ButtonKind kind;
	private boolean down;
	private long flashUntil;

	public PushButton(Supplier<Component> label, ButtonKind kind, Runnable action) {
		this.label = label;
		this.kind = kind;
		this.action = action;
	}

	/** Shows the button pressed for {@code ms}. */
	public void flash(long ms) {
		flashUntil = System.currentTimeMillis() + ms;
	}

	/** Presses it as the keyboard would. */
	public void activate() {
		if (!enabled) {
			return;
		}
		flash(120);
		MiniGameSounds.click();
		action.run();
	}

	public boolean isDown() {
		return down || System.currentTimeMillis() < flashUntil;
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
			action.run();
		}
		return true;
	}

	@Override
	public void render(GuiGraphics g, int mx, int my) {
		boolean hot = hovered(mx, my);
		ButtonState state = TrahisonUi.stateOf(enabled, hot, (down && hot) || System.currentTimeMillis() < flashUntil);
		TrahisonUi.button(g, x, y, w, h, label.get(), kind, state);
	}
}
