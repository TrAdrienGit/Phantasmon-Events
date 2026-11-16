package com.mystaria.events.trahison.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Immediate-mode clickable zones for the game's screens (charte-ui.md section 2): a control is drawn and declares its zone in
 * the same pass, so what is drawn is what is clickable. Zones remember the clip they were drawn in, so a control scrolled
 * out of view takes no click. A zone receives the mouse button (0 left, 1 right). Client thread.
 */
final class ZoneUi {

	private record Zone(int x, int y, int w, int h, int clipTop, int clipBottom, IntConsumer action) {
	}

	private final List<Zone> zones = new ArrayList<>();
	private int clipTop = Integer.MIN_VALUE;
	private int clipBottom = Integer.MAX_VALUE;
	private int mouseX;
	private int mouseY;
	/** The tooltip of the control under the cursor this frame, or null. */
	Component tooltip;

	/** A new frame: zones are declared again. A mouse position far off-screen disables hovering (a modal is on top). */
	void begin(int mouseX, int mouseY) {
		zones.clear();
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		clipTop = Integer.MIN_VALUE;
		clipBottom = Integer.MAX_VALUE;
		tooltip = null;
	}

	void clip(GuiGraphics g, int x1, int y1, int x2, int y2) {
		g.enableScissor(x1, y1, x2, y2);
		clipTop = y1;
		clipBottom = y2;
	}

	void unclip(GuiGraphics g) {
		g.disableScissor();
		clipTop = Integer.MIN_VALUE;
		clipBottom = Integer.MAX_VALUE;
	}

	boolean hovered(int x, int y, int w, int h) {
		return mouseY >= clipTop && mouseY < clipBottom && TrahisonUi.inside(mouseX, mouseY, x, y, w, h);
	}

	/** Declares a clickable zone; {@code action} null: nothing to click. */
	void zone(int x, int y, int w, int h, IntConsumer action) {
		if (action != null) {
			zones.add(new Zone(x, y, w, h, clipTop, clipBottom, action));
		}
	}

	/** Shows {@code tip} when the cursor is on the rectangle. */
	void tip(int x, int y, int w, int h, Component tip) {
		if (tip != null && hovered(x, y, w, h)) {
			tooltip = tip;
		}
	}

	/** A button; {@code action} null: disabled (it still shows its tooltip, which should say why). */
	void button(GuiGraphics g, int x, int y, int w, int h, Component label, ButtonKind kind, Component tip, Runnable action) {
		boolean hovered = hovered(x, y, w, h);
		boolean pressed = hovered && action != null && Minecraft.getInstance().mouseHandler.isLeftPressed();
		TrahisonUi.button(g, x, y, w, h, label, kind, TrahisonUi.stateOf(action != null, hovered, pressed));
		tip(x, y, w, h, tip);
		zone(x, y, w, h, action == null ? null : button -> {
			if (button == 0) {
				action.run();
			}
		});
	}

	void button(GuiGraphics g, int x, int y, int w, int h, Component label, Runnable action) {
		button(g, x, y, w, h, label, ButtonKind.DEFAULT, null, action);
	}

	/** A click: the topmost zone under it acts. Returns whether one did. */
	boolean click(double x, double y, int button) {
		for (int i = zones.size() - 1; i >= 0; i--) {
			Zone zone = zones.get(i);
			if (y >= zone.clipTop() && y < zone.clipBottom() && TrahisonUi.inside(x, y, zone.x(), zone.y(), zone.w(), zone.h())) {
				MenuUi.click();
				zone.action().accept(button);
				return true;
			}
		}
		return false;
	}
}
