package com.mystaria.events.trahison.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.phantasmon.client.api.MenuHost;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuPage;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The game master panel as a page of the Phantasmon menu, for admins (T7.4): a short page with the way in, because the panel
 * itself is a full window ({@link GmScreen}). Client thread.
 */
public final class GmMenuPage implements MenuPage {

	private static final String ID = "trahison_gm";
	private static final String T = "phantasmon_events.trahison.gm.";

	private int x;
	private int y;
	private int right;

	private GmMenuPage(MenuHost host) {
	}

	/** Registers the page after Hubs; only admins see it. */
	public static void register() {
		PhantasmonApi.registerMenuPage(new PhantasmonApi.MenuPageEntry(ID, Component.translatable(T + "page"),
				() -> Minecraft.getInstance().player != null && PhantasmonApi.isAdmin(), GmMenuPage::new));
	}

	@Override
	public Component title() {
		return Component.translatable(T + "page");
	}

	@Override
	public void layout(int x, int y, int right, int bottom) {
		this.x = x;
		this.y = y;
		this.right = right;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY) {
		int h = MenuUi.paragraph(g, Component.translatable(T + "page.text"), x + 8, y + 8, right - x - 16, TrahisonPalette.TEXT_SECONDARY);
		boolean hover = MenuUi.inside(mouseX, mouseY, x + 8, y + 16 + h, 120, 20);
		MenuUi.primary(g, x + 8, y + 16 + h, 120, 20, Component.translatable(T + "page.open"), hover, true);
		openY = y + 16 + h;
	}

	private int openY;

	@Override
	public boolean click(double mouseX, double mouseY) {
		if (MenuUi.inside(mouseX, mouseY, x + 8, openY, 120, 20)) {
			MenuUi.click();
			Minecraft.getInstance().tell(GmScreen::open);
			return true;
		}
		return false;
	}
}
