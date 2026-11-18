package com.mystaria.events.tournament.ui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * A small immediate-mode toolkit over Phantasmon's menu look ({@link MenuUi}): each control is drawn and registers its
 * clickable zone in the same pass, so a page with many views stays plain code. Zones carry the clip they were drawn
 * in (scrolled areas), so a hidden control never takes a click. Also the text fields: plain, or guided for a date
 * ({@code JJ-MM-AAAA}) or a time ({@code HH:MM}) where only the digits are typed. Client thread.
 */
public final class Ui {

	private record Zone(int x, int y, int w, int h, int clipTop, int clipBottom, Runnable action) {
	}

	/** A text field's kind: what may be typed. */
	public enum Kind { TEXT, NAME, DATE, TIME }

	public static final class Field {
		public final Kind kind;
		public final int max;
		/** What was typed: the digits only for a date or a time. */
		public String value = "";

		public Field(Kind kind, int max) {
			this.kind = kind;
			this.max = max;
		}

		/** The value shown: a date's or a time's separators put in as the digits come. */
		public String shown() {
			return switch (kind) {
				case DATE -> value.length() <= 2 ? value : value.length() <= 4 ? value.substring(0, 2) + "-" + value.substring(2)
						: value.substring(0, 2) + "-" + value.substring(2, 4) + "-" + value.substring(4);
				case TIME -> value.length() <= 2 ? value : value.substring(0, 2) + ":" + value.substring(2);
				default -> value;
			};
		}

		/** Sets the shown form (a date's {@code 10-11-2026}): digits kept for a date or a time. */
		public void set(String text) {
			String clean = text == null ? "" : text;
			if (kind == Kind.DATE || kind == Kind.TIME) {
				clean = clean.replaceAll("[^0-9]", "");
			}
			value = clean.length() > limit() ? clean.substring(0, limit()) : clean;
		}

		/** Whether it is complete (a date's 8 digits, a time's 4) or empty. */
		public boolean completeOrEmpty() {
			return value.isEmpty() || kind == Kind.DATE && value.length() == 8 || kind == Kind.TIME && value.length() == 4
					|| kind == Kind.TEXT || kind == Kind.NAME;
		}

		private int limit() {
			return kind == Kind.DATE ? 8 : kind == Kind.TIME ? 4 : max;
		}

		private boolean accepts(char c) {
			return switch (kind) {
				case DATE, TIME -> Character.isDigit(c);
				case NAME -> c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_';
				case TEXT -> c >= 32 && c != 127;
			};
		}
	}

	private final List<Zone> zones = new ArrayList<>();
	private int clipTop = Integer.MIN_VALUE;
	private int clipBottom = Integer.MAX_VALUE;
	private int mouseX;
	private int mouseY;
	private Field focused;

	/** A new frame: the zones are drawn again. */
	public void begin(int mouseX, int mouseY) {
		zones.clear();
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		clipTop = Integer.MIN_VALUE;
		clipBottom = Integer.MAX_VALUE;
	}

	/** Draws only within these rows from now on (a scrolled area); {@link #unclip} ends it. */
	public void clip(GuiGraphics g, int x1, int y1, int x2, int y2) {
		g.enableScissor(x1, y1, x2, y2);
		clipTop = y1;
		clipBottom = y2;
	}

	public void unclip(GuiGraphics g) {
		g.disableScissor();
		clipTop = Integer.MIN_VALUE;
		clipBottom = Integer.MAX_VALUE;
	}

	public boolean hovered(int x, int y, int w, int h) {
		return mouseY >= clipTop && mouseY < clipBottom && MenuUi.inside(mouseX, mouseY, x, y, w, h);
	}

	/** A clickable zone with no drawing of its own (a card, a row). */
	public void zone(int x, int y, int w, int h, Runnable action) {
		if (action != null) {
			zones.add(new Zone(x, y, w, h, clipTop, clipBottom, action));
		}
	}

	public void button(GuiGraphics g, int x, int y, int w, int h, Component label, Runnable action) {
		MenuUi.button(g, x, y, w, h, label, action != null && hovered(x, y, w, h), false, action != null);
		zone(x, y, w, h, action);
	}

	public void danger(GuiGraphics g, int x, int y, int w, int h, Component label, Runnable action) {
		MenuUi.button(g, x, y, w, h, label, action != null && hovered(x, y, w, h), true, action != null);
		zone(x, y, w, h, action);
	}

	public void primary(GuiGraphics g, int x, int y, int w, int h, Component label, Runnable action) {
		MenuUi.primary(g, x, y, w, h, label, action != null && hovered(x, y, w, h), action != null);
		zone(x, y, w, h, action);
	}

	public void chip(GuiGraphics g, int x, int y, int w, int h, Component label, boolean selected, Runnable action) {
		MenuUi.chip(g, x, y, w, h, label, selected, hovered(x, y, w, h));
		zone(x, y, w, h, action);
	}

	public void filter(GuiGraphics g, int x, int y, int w, int h, Component label, boolean selected, Runnable action) {
		MenuUi.filter(g, x, y, w, h, label, selected, hovered(x, y, w, h));
		zone(x, y, w, h, action);
	}

	public void mini(GuiGraphics g, int x, int y, Component label, boolean danger, Runnable action) {
		MenuUi.mini(g, x, y, label, hovered(x, y, 18, 14), danger, MenuUi.p().white());
		zone(x, y, 18, 14, action);
	}

	/** A text field, its label above it; a click focuses it. {@code invalid}: its text in red. */
	public void field(GuiGraphics g, Field field, int x, int y, int w, int h, Component label, String placeholder, boolean invalid) {
		MenuUi.field(g, x, y, w, h, label, field.shown(), placeholder, field == focused, invalid);
		zone(x, y, w, h, (FocusKeeper) () -> focused = field);
	}

	// ---------------------------------------------------------------- drop-down lists (2026-11-18)

	/** A drop-down list's choices and its picked one; {@code name} shows a choice. */
	public static final class Dropdown<T> {
		public final List<T> values;
		private final java.util.function.Function<T, Component> name;
		public T value;
		private int scroll;

		public Dropdown(List<T> values, java.util.function.Function<T, Component> name, T value) {
			this.values = values;
			this.name = name;
			this.value = value;
		}

		public Component name(T choice) {
			return name.apply(choice);
		}
	}

	private static final int ROW = 13;
	private static final int MAX_ROWS = 10;
	private Dropdown<?> open;
	private int openX;
	private int openY;
	private int openW;
	private int openH;

	/** A closed drop-down: its label above, the picked choice, an arrow; a click opens its list ({@link #overlay}). */
	public <T> void dropdown(GuiGraphics g, Dropdown<T> list, int x, int y, int w, int h, Component label) {
		MenuUi.Palette c = MenuUi.p();
		boolean isOpen = open == list;
		boolean hot = isOpen || hovered(x, y, w, h);
		if (label != null) {
			MenuUi.text(g, label, x, y - 10, w, c.muted());
		}
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, isOpen ? c.accent() : c.black());
		g.fillGradient(x, y, x + w, y + h, hot ? c.edge() : c.raised(), c.panel());
		MenuUi.raised(g, x, y, x + w, y + h);
		MenuUi.text(g, list.name(list.value), x + 5, y + (h - 8) / 2 + 1, w - 18, c.white());
		MenuUi.text(g, Component.literal(isOpen ? "▲" : "▼"), x + w - 11, y + (h - 8) / 2 + 1, 10, hot ? c.accentHot() : c.accent());
		if (isOpen) {
			openX = x;
			openY = y;
			openW = w;
			openH = h;
		}
		zone(x, y, w, h, () -> {
			open = open == list ? null : list;
			if (open != null) {
				list.scroll = Math.max(0, list.values.indexOf(list.value) - MAX_ROWS / 2);
			}
		});
	}

	public boolean listOpen() {
		return open != null;
	}

	public void closeList() {
		open = null;
	}

	/** The open drop-down's list, over everything: called last in a frame. {@code screenHeight}: it opens upward when short of room below. */
	public void overlay(GuiGraphics g, int screenHeight) {
		if (open != null) {
			drawList(g, open, screenHeight);
		}
	}

	private <T> void drawList(GuiGraphics g, Dropdown<T> list, int screenHeight) {
		MenuUi.Palette c = MenuUi.p();
		int rows = Math.min(MAX_ROWS, list.values.size());
		list.scroll = Math.max(0, Math.min(list.scroll, list.values.size() - rows));
		int height = rows * ROW + 2;
		int top = openY + openH + 1;
		if (top + height > screenHeight - 4) {
			top = openY - 1 - height;
		}
		g.pose().pushPose();
		g.pose().translate(0, 0, 400);
		// Any click outside closes it.
		zone(-10000, -10000, 30000, 30000, () -> open = null);
		g.fill(openX - 1, top - 1, openX + openW + 1, top + height + 1, c.accent());
		g.fill(openX, top, openX + openW, top + height, c.dark());
		for (int i = 0; i < rows; i++) {
			T choice = list.values.get(list.scroll + i);
			int ry = top + 1 + i * ROW;
			boolean hot = hovered(openX, ry, openW, ROW);
			if (choice.equals(list.value)) {
				MenuUi.selectedRow(g, openX + 1, ry, openW - 2, ROW);
			} else if (hot) {
				g.fill(openX + 1, ry, openX + openW - 1, ry + ROW, c.raised());
			}
			MenuUi.text(g, list.name(choice), openX + 6, ry + 3, openW - 14, choice.equals(list.value) || hot ? c.white() : c.muted());
			zone(openX, ry, openW, ROW, () -> {
				list.value = choice;
				open = null;
			});
		}
		if (list.values.size() > rows) {
			MenuUi.scrollbar(g, openX + openW - 4, top + 1, rows * ROW, list.values.size() * ROW, list.scroll * ROW, false);
		}
		g.pose().popPose();
	}

	/** The wheel over an open list scrolls it; returns whether it took the wheel. */
	public boolean scrollList(double amount) {
		if (open == null) {
			return false;
		}
		open.scroll -= (int) Math.signum(amount);
		return true;
	}

	/** A click: the topmost zone under it acts. Returns whether one did (a click elsewhere drops the focus). */
	public boolean click(double x, double y) {
		for (int i = zones.size() - 1; i >= 0; i--) {
			Zone zone = zones.get(i);
			if (y >= zone.clipTop() && y < zone.clipBottom() && MenuUi.inside(x, y, zone.x(), zone.y(), zone.w(), zone.h())) {
				Field before = focused;
				zone.action().run();
				if (focused == before && !(zone.action() instanceof FocusKeeper)) {
					focused = null;
				}
				MenuUi.click();
				return true;
			}
		}
		focused = null;
		return false;
	}

	/** An action that keeps the field focused (a field's own zone does). */
	private interface FocusKeeper extends Runnable {
	}

	public Field focused() {
		return focused;
	}

	public void focus(Field field) {
		focused = field;
	}

	public boolean charTyped(char chr) {
		if (focused == null || !focused.accepts(chr) || focused.value.length() >= focused.limit()) {
			return focused != null;
		}
		focused.value += chr;
		return true;
	}

	/** Backspace, paste; Enter and Escape drop the focus. Returns whether a field took the key. */
	public boolean key(int keyCode, boolean control) {
		if (focused == null) {
			return false;
		}
		switch (keyCode) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (!focused.value.isEmpty()) {
					focused.value = control ? "" : focused.value.substring(0, focused.value.length() - 1);
				}
			}
			case GLFW.GLFW_KEY_V -> {
				if (control) {
					String pasted = Minecraft.getInstance().keyboardHandler.getClipboard();
					StringBuilder kept = new StringBuilder(focused.value);
					for (char c : pasted.toCharArray()) {
						if (focused.accepts(c) && kept.length() < focused.limit()) {
							kept.append(c);
						}
					}
					focused.value = kept.toString();
				}
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_TAB -> focused = null;
			default -> {
			}
		}
		return true;
	}
}
