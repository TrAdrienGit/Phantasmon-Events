package com.mystaria.events.trahison.minigame.widget;

import net.minecraft.client.gui.GuiGraphics;

import com.mystaria.events.trahison.minigame.MiniGameSounds;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;

/**
 * A wire pulled by its plug: fixed at an anchor, hanging as a bezier curve that sags when slack, ending in a plug
 * dragged anywhere within {@code reach} pixels of its rest position. Released, the plug springs back (or stays at
 * the point it was dropped on if {@link #sticky} is on). {@link #onDrop} receives the plug's final position.
 */
public final class PullWire extends Widget {

	/** Receives the plug position when it is dropped. */
	public interface DropListener {
		void dropped(int plugX, int plugY);
	}

	private final int anchorX;
	private final int anchorY;
	private final int restX;
	private final int restY;
	private final int reach;
	private final int tone;
	private final int toneDark;
	private final int toneLight;
	private float plugX;
	private float plugY;
	private boolean dragging;
	private boolean sticky;
	private long lastRender;
	private DropListener onDrop = (px, py) -> { };

	public PullWire(int anchorX, int anchorY, int restX, int restY, int reach, TrahisonUi.GameColor color) {
		this.anchorX = anchorX;
		this.anchorY = anchorY;
		this.restX = restX;
		this.restY = restY;
		this.reach = reach;
		this.tone = color.base;
		this.toneDark = color.dark;
		this.toneLight = color.light;
		this.plugX = restX;
		this.plugY = restY;
	}

	public PullWire sticky(boolean sticky) {
		this.sticky = sticky;
		return this;
	}

	public PullWire onDrop(DropListener onDrop) {
		this.onDrop = onDrop;
		return this;
	}

	/** Moves the plug (a sync, or a remote player's gesture); it eases there when not sticky-dropped. */
	public void setPlug(int px, int py) {
		plugX = px;
		plugY = py;
	}

	public int plugX() {
		return Math.round(plugX);
	}

	public int plugY() {
		return Math.round(plugY);
	}

	@Override
	public int centerX() {
		return Math.round(plugX);
	}

	@Override
	public int centerY() {
		return Math.round(plugY);
	}

	@Override
	public boolean hovered(double mx, double my) {
		return enabled && TrahisonUi.inside(mx, my, Math.round(plugX) - 8, Math.round(plugY) - 10, 16, 20);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0 || !hovered(mx, my)) {
			return false;
		}
		dragging = true;
		MiniGameSounds.click();
		return true;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (!dragging) {
			return false;
		}
		double ox = mx - restX;
		double oy = my - restY;
		double len = Math.sqrt(ox * ox + oy * oy);
		if (len > reach) {
			ox = ox / len * reach;
			oy = oy / len * reach;
		}
		plugX = (float) (restX + ox);
		plugY = (float) (restY + oy);
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (!dragging) {
			return false;
		}
		dragging = false;
		MiniGameSounds.clunk();
		onDrop.dropped(Math.round(plugX), Math.round(plugY));
		return true;
	}

	@Override
	public void render(GuiGraphics g, int mx, int my) {
		long now = System.currentTimeMillis();
		long dt = lastRender == 0 ? 0 : now - lastRender;
		lastRender = now;
		if (!dragging && !sticky) {
			plugX = approach(plugX, restX, dt, 90f);
			plugY = approach(plugY, restY, dt, 90f);
		}
		boolean hot = hovered(mx, my) || dragging;
		float px = plugX;
		float py = plugY;

		// Socket.
		TrahisonUi.disc(g, anchorX, anchorY, 6, TrahisonPalette.BORDER);
		TrahisonUi.disc(g, anchorX, anchorY, 5, TrahisonPalette.EDGE);
		TrahisonUi.disc(g, anchorX, anchorY, 3, TrahisonPalette.PANEL_SUNKEN);

		// The wire: a cubic bezier that hangs lower the more slack it has.
		double dx = px - anchorX;
		double dy = py - anchorY;
		double dist = Math.sqrt(dx * dx + dy * dy);
		double length = Math.max(1, Math.hypot(restX - anchorX, restY - anchorY) + reach * 0.4);
		double slack = Math.max(0, 1 - dist / length);
		double sag = 6 + 34 * slack;
		double c1x = anchorX + dx * 0.25;
		double c1y = anchorY + dy * 0.25 + sag;
		double c2x = anchorX + dx * 0.75;
		double c2y = anchorY + dy * 0.75 + sag;
		int steps = (int) Math.max(12, dist / 2);
		for (int pass = 0; pass < 3; pass++) {
			int color = pass == 0 ? TrahisonUi.withAlpha(TrahisonPalette.BORDER, 0xA0) : pass == 1 ? toneDark : tone;
			int off = pass == 0 ? 2 : 0;
			int size = pass == 2 ? 2 : 3;
			for (int i = 0; i <= steps; i++) {
				double t = i / (double) steps;
				double u = 1 - t;
				double bx = u * u * u * anchorX + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * px;
				double by = u * u * u * anchorY + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * py;
				int ix = (int) Math.round(bx) - size / 2;
				int iy = (int) Math.round(by) - size / 2 + off;
				g.fill(ix, iy, ix + size, iy + size, color);
			}
		}
		// A thin highlight along the top of the wire.
		for (int i = 0; i <= steps; i += 2) {
			double t = i / (double) steps;
			double u = 1 - t;
			double bx = u * u * u * anchorX + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * px;
			double by = u * u * u * anchorY + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * py;
			g.fill((int) Math.round(bx), (int) Math.round(by) - 1, (int) Math.round(bx) + 1, (int) Math.round(by), toneLight);
		}

		// Plug: a small raised block with a coloured band.
		int ix = Math.round(px);
		int iy = Math.round(py);
		g.fill(ix - 8, iy - 10, ix + 8, iy + 10, TrahisonPalette.BORDER);
		g.fillGradient(ix - 7, iy - 9, ix + 7, iy + 9, hot ? TrahisonPalette.EDGE_LIGHT : TrahisonPalette.EDGE, TrahisonPalette.PANEL_RAISED);
		g.fill(ix - 7, iy - 3, ix + 7, iy + 3, enabled ? tone : TrahisonPalette.PANEL);
		g.fill(ix - 7, iy - 3, ix + 7, iy - 2, toneLight);
		g.fill(ix - 3, iy + 6, ix - 2, iy + 8, TrahisonPalette.BORDER);
		g.fill(ix + 2, iy + 6, ix + 3, iy + 8, TrahisonPalette.BORDER);
	}
}
