package com.mystaria.events.trahison.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Immediate-mode controls for the editor's panel, in the game's own look ({@link TrahisonUi}, {@link TrahisonPalette}):
 * each control is drawn and declares its clickable zone in the same pass, as in {@code tournament.ui.Ui} (the
 * principle of charte-ui.md section 2). Zones remember the clip they were drawn in, so a scrolled-out control never takes
 * a click. A zone receives the mouse button (0 left, 1 right). Client thread.
 */
final class PanelUi {

	private record Zone(int x, int y, int w, int h, int clipTop, int clipBottom, IntConsumer action, Field focus) {
	}

	/** A single-line text field: ids by default (letters, digits, {@code _} and {@code -}), or free text. */
	static final class Field {
		final int max;
		final boolean free;
		String value = "";

		Field(int max) {
			this(max, false);
		}

		Field(int max, boolean free) {
			this.max = max;
			this.free = free;
		}

		private boolean accepts(char c) {
			if (free) {
				return c >= 32 && c != 127;
			}
			return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '-';
		}
	}

	private final List<Zone> zones = new ArrayList<>();
	private int clipTop = Integer.MIN_VALUE;
	private int clipBottom = Integer.MAX_VALUE;
	private int mouseX;
	private int mouseY;
	private Field focused;
	/** The tooltip of the control under the cursor this frame, or null. */
	Component tooltip;

	/** A new frame: zones are declared again. A mouse position off-screen disables hovering (a modal is on top). */
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

	void zone(int x, int y, int w, int h, IntConsumer action) {
		if (action != null) {
			zones.add(new Zone(x, y, w, h, clipTop, clipBottom, action, null));
		}
	}

	/** A button; {@code action} null: disabled. */
	void button(GuiGraphics g, int x, int y, int w, int h, Component label, ButtonKind kind, Component tip, Runnable action) {
		boolean hovered = hovered(x, y, w, h);
		boolean pressed = hovered && action != null && Minecraft.getInstance().mouseHandler.isLeftPressed();
		TrahisonUi.button(g, x, y, w, h, label, kind, TrahisonUi.stateOf(action != null, hovered, pressed));
		if (hovered && tip != null) {
			tooltip = tip;
		}
		zone(x, y, w, h, action == null ? null : button -> {
			if (button == 0) {
				action.run();
			}
		});
	}

	void button(GuiGraphics g, int x, int y, int w, int h, Component label, Runnable action) {
		button(g, x, y, w, h, label, ButtonKind.DEFAULT, null, action);
	}

	/** A control that steps through values: left click forward, right click back. */
	void cycle(GuiGraphics g, int x, int y, int w, int h, Component value, IntConsumer step) {
		boolean hovered = hovered(x, y, w, h);
		TrahisonUi.button(g, x, y, w, h, value, ButtonKind.DEFAULT, TrahisonUi.stateOf(true, hovered, hovered && Minecraft.getInstance().mouseHandler.isLeftPressed()));
		zone(x, y, w, h, button -> step.accept(button == 1 ? -1 : 1));
	}

	/** A text field in a well; the caret blinks while focused. {@code invalid}: its outline is red. */
	void field(GuiGraphics g, Field field, int x, int y, int w, int h, boolean invalid) {
		TrahisonUi.well(g, x, y, w, h);
		boolean focus = field == focused;
		if (focus || invalid) {
			MenuUi.outline(g, x, y, x + w, y + h, invalid ? TrahisonPalette.DANGER : TrahisonPalette.ACCENT);
		}
		String shown = field.value + (focus && System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "");
		int color = invalid ? TrahisonPalette.DANGER : TrahisonPalette.TEXT;
		if (field.free) {
			// Long free text: its end (where the player types) stays visible.
			int width = TrahisonUi.width(Component.literal(shown));
			int inner = w - 8;
			g.enableScissor(x + 3, y, x + w - 3, y + h);
			MenuUi.text(g, Component.literal(shown), x + 4 - Math.max(0, width - inner), y + (h - 8) / 2 + 1, Math.max(width, inner), color);
			g.disableScissor();
		} else {
			MenuUi.text(g, Component.literal(shown), x + 4, y + (h - 8) / 2 + 1, w - 8, color);
		}
		zones.add(new Zone(x, y, w, h, clipTop, clipBottom, null, field));
	}

	/** A click: the topmost zone under it acts. Returns whether one did; any click off a field drops its focus. */
	boolean click(double x, double y, int button) {
		for (int i = zones.size() - 1; i >= 0; i--) {
			Zone zone = zones.get(i);
			if (y >= zone.clipTop() && y < zone.clipBottom() && TrahisonUi.inside(x, y, zone.x(), zone.y(), zone.w(), zone.h())) {
				if (zone.focus() != null) {
					focused = zone.focus();
				} else {
					focused = null;
					zone.action().accept(button);
				}
				MenuUi.click();
				return true;
			}
		}
		focused = null;
		return false;
	}

	Field focused() {
		return focused;
	}

	void unfocus() {
		focused = null;
	}

	boolean charTyped(char chr) {
		if (focused == null) {
			return false;
		}
		if (focused.accepts(chr) && focused.value.length() < focused.max) {
			focused.value += chr;
		}
		return true;
	}

	/** Backspace and paste on the focused field. Returns whether a field took the key. */
	boolean key(int keyCode, boolean control) {
		if (focused == null) {
			return false;
		}
		if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			if (!focused.value.isEmpty()) {
				focused.value = control ? "" : focused.value.substring(0, focused.value.length() - 1);
			}
		} else if (keyCode == GLFW.GLFW_KEY_V && control) {
			StringBuilder kept = new StringBuilder(focused.value);
			for (char c : Minecraft.getInstance().keyboardHandler.getClipboard().toCharArray()) {
				if (focused.accepts(c) && kept.length() < focused.max) {
					kept.append(c);
				}
			}
			focused.value = kept.toString();
		}
		return true;
	}
}
