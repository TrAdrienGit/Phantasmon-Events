package com.mystaria.events;

import net.minecraft.client.gui.GuiGraphics;

import com.mystaria.phantasmon.client.gui.PhantasmonTheme;

/**
 * The events' screens in Phantasmon's look (Adrien 2026-11-08): with the Cobblemon look, a grey card behind the content,
 * edged and lit as Showtime's menu, and teal titles; with V1, Minecraft's plain screen as before.
 */
public final class EventsLook {

	private static final int CARD = 0xE62F2F2F;
	private static final int HEAD = 0xFF3D3D3D;
	private static final int EDGE = 0xFF676767;
	private static final int ACCENT = 0xFF38C4BC;

	private EventsLook() {
	}

	/** Behind a screen's content, {@code halfWidth} each side of its centre, under a title band. */
	public static void card(GuiGraphics g, int width, int height, int halfWidth) {
		if (!PhantasmonTheme.cobblemon()) {
			return;
		}
		int x0 = Math.max(4, width / 2 - halfWidth);
		int x1 = Math.min(width - 4, width / 2 + halfWidth);
		int y0 = 4;
		int y1 = height - 4;
		g.fill(x0, y0, x1, y1, CARD);
		g.fill(x0, y0, x1, y0 + 26, HEAD);
		g.fill(x0, y0 + 26, x1, y0 + 27, ACCENT);
		g.fill(x0, y0, x1, y0 + 1, EDGE);
		g.fill(x0, y1 - 1, x1, y1, EDGE);
		g.fill(x0, y0, x0 + 1, y1, EDGE);
		g.fill(x1 - 1, y0, x1, y1, EDGE);
	}

	/** A screen title's colour. */
	public static int title() {
		return PhantasmonTheme.cobblemon() ? ACCENT : 0xFFFFFFFF;
	}
}
