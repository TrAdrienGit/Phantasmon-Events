package com.mystaria.events.trahison.minigame.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

/**
 * A free-form area of a mini-game screen: the screen draws it through {@link #paint} and handles the mouse through the
 * callbacks, in screen coordinates. For boards (blocks, tiles) whose pieces are not worth a widget each. A click is taken
 * when {@link #onClick} returns true; the drag and the release then come back to this pad.
 */
public final class Pad extends Widget {

	/** Draws the pad; {@code hot} is true while the mouse is over it. */
	public interface Paint {
		void paint(GuiGraphics g, int mx, int my, boolean hot);
	}

	/** A mouse event at a point (screen pixels); true when handled. */
	public interface Mouse {
		boolean at(double mx, double my, int button);
	}

	/** A drag step to a point. */
	public interface Drag {
		void to(double mx, double my);
	}

	private Paint paint = (g, mx, my, hot) -> { };
	private Mouse click = (mx, my, button) -> false;
	private Mouse release = (mx, my, button) -> false;
	private Drag drag = (mx, my) -> { };
	private Mouse wheel = (mx, my, delta) -> false;
	private Function<double[], Component> tip = p -> null;

	public Pad paint(Paint paint) {
		this.paint = paint;
		return this;
	}

	public Pad onClick(Mouse click) {
		this.click = click;
		return this;
	}

	public Pad onRelease(Mouse release) {
		this.release = release;
		return this;
	}

	public Pad onDrag(Drag drag) {
		this.drag = drag;
		return this;
	}

	/** The wheel: the third argument is the scroll amount, not a button. */
	public Pad onWheel(Mouse wheel) {
		this.wheel = wheel;
		return this;
	}

	/** Tooltip for a point {@code {mx, my}}, or null. */
	public Pad tip(Function<double[], Component> tip) {
		this.tip = tip;
		return this;
	}

	@Override
	public void render(GuiGraphics g, int mx, int my) {
		paint.paint(g, mx, my, hovered(mx, my));
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		return hovered(mx, my) && click.at(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		drag.to(mx, my);
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		return release.at(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double delta) {
		return hovered(mx, my) && wheel.at(mx, my, (int) Math.signum(delta));
	}

	@Override
	public Component tooltip(double mx, double my) {
		return hovered(mx, my) ? tip.apply(new double[] { mx, my }) : null;
	}
}
