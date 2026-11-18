package com.mystaria.events.trahison.edit;

import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The room editor's one-line guide while a template is being built: "Next: id - hint (n left)", at the top left of the screen,
 * shown only when no screen is open (the panel's "To place" tab says the same). Client thread.
 */
final class RoomHud {

	private static final int H = 16;

	private RoomHud() {
	}

	static void register() {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	private static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (!RoomEditor.isActive() || mc.screen != null || !TrahisonUi.hudVisible()) {
			return;
		}
		List<String> todo = RoomEditor.todo();
		if (todo.isEmpty()) {
			return;
		}
		String next = todo.get(0);
		String hint = RoomEditor.hints.getOrDefault(next, "");
		Component left = RoomUi.t("hud.left", todo.size());
		int leftW = TrahisonUi.width(left);
		int x = TrahisonUi.SAFE_MARGIN;
		int y = TrahisonUi.SAFE_MARGIN;
		int w = g.guiWidth() - 2 * TrahisonUi.SAFE_MARGIN;
		TrahisonUi.panel(g, x, y, w, H);
		g.fill(x + 1, y + 2, x + 3, y + H - 2, TrahisonPalette.ACCENT);
		// The count stays readable on a narrow screen: the id and the hint give way, not the count.
		MenuUi.text(g, left, x + w - leftW - 6, y + 4, leftW, TrahisonPalette.TEXT_SECONDARY);
		Component line = hint.isEmpty() ? RoomUi.t("hud.next", next) : RoomUi.t("hud.next_hint", next, hint);
		TrahisonUi.textEllipsis(g, line, x + 10, y + 4, w - 10 - leftW - 14, TrahisonPalette.ACCENT);
	}
}
