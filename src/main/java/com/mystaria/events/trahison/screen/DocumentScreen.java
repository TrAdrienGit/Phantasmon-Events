package com.mystaria.events.trahison.screen;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mystaria.events.trahison.minigame.Glyphs;
import com.mystaria.events.trahison.room.Documents;
import com.mystaria.events.trahison.room.Documents.Page;
import com.mystaria.events.trahison.room.Pictures;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.WireColor;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The reading screen (T3.3): a window with a page well, previous / next buttons (arrow keys too) and the page count. A text page
 * is wrapped, a picture line draws the pack's picture for its id ({@link Pictures}: a placeholder until a real file is dropped in),
 * digits are written large, symbols are drawn with the mini-games' own {@link Glyphs}, colours as squares with their symbol and
 * name (never the colour alone). Holds no game state. Client thread.
 */
public final class DocumentScreen extends TrahisonScreen {

	private static final String T = "phantasmon_events.trahison.";
	private static final int W = 300;
	private static final int H = 220;

	private final Component heading;
	private final List<Page> pages;
	private int page;

	public DocumentScreen(Component heading, List<Page> pages) {
		super(Component.translatable(T + "document.title"), TrahisonPalette.ACCENT);
		this.heading = heading;
		this.pages = pages;
	}

	@Override
	protected void drawStage(GuiGraphics g, int mouseX, int mouseY) {
		int[] r = TrahisonUi.stage(width, height, W, H);
		int x = r[0];
		int y = r[1];
		int w = r[2];
		int h = r[3];
		TrahisonUi.titledPanel(g, x, y, w, h, heading, TrahisonPalette.ACCENT);
		int wellX = x + 8;
		int wellY = y + 28;
		int wellW = w - 16;
		int wellH = h - 28 - 8 - TrahisonUi.BUTTON_H - 6;
		TrahisonUi.well(g, wellX, wellY, wellW, wellH);
		drawPage(g, pages.get(page), wellX + 8, wellY + 8, wellW - 16, wellH - 16);
		int by = y + h - 8 - TrahisonUi.BUTTON_H;
		ui.button(g, x + 8, by, 28, TrahisonUi.BUTTON_H, Component.literal("<"), page > 0 ? () -> page-- : null);
		ui.button(g, x + 40, by, 28, TrahisonUi.BUTTON_H, Component.literal(">"), page + 1 < pages.size() ? () -> page++ : null);
		MenuUi.centered(g, Component.literal((page + 1) + " / " + pages.size()), x + w / 2, by + 5, 80, TrahisonPalette.TEXT_MUTED);
		ui.button(g, x + w - 8 - 84, by, 84, TrahisonUi.BUTTON_H, Component.translatable(T + "close"), TrahisonUi.ButtonKind.PRIMARY, null, this::onClose);
	}

	private void drawPage(GuiGraphics g, Page p, int x, int y, int w, int h) {
		switch (p.kind()) {
			case "digits" -> {
				String digits = String.join("", p.lines());
				float scale = Math.max(2, Math.min(6, w / Math.max(1f, digits.length() * 9f)));
				int tw = Math.round(digits.length() * 9 * scale / 2);
				TrahisonUi.bigText(g, Component.literal(digits), x + (w - tw) / 2, y + h / 2 - Math.round(4 * scale), scale, TrahisonPalette.ACCENT_HOT);
			}
			case "symbols" -> {
				int size = Math.max(9, Math.min(40, w / Math.max(1, p.lines().size()) - 6));
				int total = p.lines().size() * (size + 6) - 6;
				int gx = x + (w - total) / 2 + size / 2;
				for (String id : p.lines()) {
					Glyphs.draw(g, id, gx, y + h / 2f, size, 0, TrahisonPalette.ACCENT_HOT);
					gx += size + 6;
				}
			}
			case "colors" -> {
				int size = Math.max(14, Math.min(36, w / Math.max(1, p.lines().size()) - 8));
				int total = p.lines().size() * (size + 8) - 8;
				int cx = x + (w - total) / 2;
				for (String name : p.lines()) {
					WireColor c = colorOf(name);
					g.fill(cx - 1, y + h / 2 - size / 2 - 1, cx + size + 1, y + h / 2 + size / 2 + 1, TrahisonPalette.BORDER);
					g.fill(cx, y + h / 2 - size / 2, cx + size, y + h / 2 + size / 2, c == null ? TrahisonPalette.TEXT_MUTED : c.base);
					if (c != null) {
						TrahisonUi.glyph(g, c.glyph, cx + (size - 8) / 2, y + h / 2 - 4, 8, c.dark);
						MenuUi.centered(g, Component.translatable(T + "color." + name.toLowerCase(java.util.Locale.ROOT)), cx + size / 2,
								y + h / 2 + size / 2 + 6, size + 8, TrahisonPalette.TEXT_SECONDARY);
					}
					cx += size + 8;
				}
			}
			case "image" -> drawImage(g, p.lines().get(0), x, y, w, h);
			default -> {
				int ty = y;
				for (String line : p.lines()) {
					String image = Documents.imageOf(line);
					if (image != null) {
						int ih = Math.min(h - (ty - y), 96);
						if (ih > 12) {
							drawImage(g, image, x, ty, w, ih);
							ty += ih + 4;
						}
					} else if (line.isBlank()) {
						ty += TrahisonUi.LINE / 2;
					} else {
						ty += MenuUi.paragraph(g, Component.literal(line), x, ty, w, TrahisonPalette.TEXT_SECONDARY);
					}
					if (ty > y + h) {
						break;
					}
				}
			}
		}
	}

	private static void drawImage(GuiGraphics g, String id, int x, int y, int w, int h) {
		ResourceLocation texture = Pictures.doc(id);
		if (texture != null) {
			Pictures.draw(g, texture, x, y, w, h);
		} else {
			TrahisonUi.well(g, x, y, w, h);
		}
		if (!Pictures.hasDoc(id)) {
			MenuUi.centered(g, Component.literal(id), x + w / 2, y + h - 10, w, TrahisonPalette.TEXT_MUTED);
		}
	}

	private static WireColor colorOf(String name) {
		return WireColor.of(name);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (modal == null) {
			if (keyCode == GLFW.GLFW_KEY_LEFT && page > 0) {
				page--;
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_RIGHT && page + 1 < pages.size()) {
				page++;
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				onClose();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
