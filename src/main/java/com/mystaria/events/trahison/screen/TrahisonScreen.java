package com.mystaria.events.trahison.screen;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.hud.Announcer;
import com.mystaria.events.trahison.hud.HudSuppression;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Base of the game's "window" screens (charte-ui.md section 2): a veil, a stage drawn by the subclass, a confirmation modal,
 * tooltips, the opening animation (160 ms, 6 px slide and fade), and the plumbing every screen shares: hiding the HUD while
 * open, never pausing the game, Escape cancelling the modal first and then closing. It also draws the {@link Announcer}
 * itself, so the hostess's lines and the refusals stay visible above the window. Holds no game rule. Client thread.
 */
public abstract class TrahisonScreen extends Screen {

	/** A modal question; {@code danger}: the confirmation is a destructive action. */
	protected record Modal(Component title, Component text, Component yes, boolean danger, Runnable action) {
	}

	protected final ZoneUi ui = new ZoneUi();
	private final ZoneUi modalUi = new ZoneUi();
	protected Modal modal;
	private long openedAt;
	private final int accent;

	protected TrahisonScreen(Component title, int accent) {
		super(title);
		this.accent = accent;
	}

	/** Draws the window, using {@link #ui} for its controls. Called with the opening slide already applied. */
	protected abstract void drawStage(GuiGraphics g, int mouseX, int mouseY);

	/** A mouse wheel turn over the stage; return true when handled. */
	protected boolean scrolled(double mouseX, double mouseY, double amount) {
		return false;
	}

	@Override
	protected void init() {
		HudSuppression.hide(this);
		if (openedAt == 0) {
			openedAt = System.currentTimeMillis();
		}
	}

	@Override
	public void removed() {
		HudSuppression.show(this);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The veil is drawn in {@link #render}, with the opening fade. */
	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float open = TrahisonUi.easeOut(TrahisonUi.progress(openedAt, 160));
		g.fill(0, 0, width, height, TrahisonUi.withAlpha(TrahisonPalette.VEIL, Math.round(((TrahisonPalette.VEIL >>> 24) & 0xFF) * open)));
		boolean asking = modal != null;
		ui.begin(asking ? -1000 : mouseX, asking ? -1000 : mouseY);
		g.pose().pushPose();
		g.pose().translate(0, Math.round((1f - open) * 6f), 0);
		drawStage(g, mouseX, mouseY);
		g.pose().popPose();
		if (ui.tooltip != null) {
			TrahisonUi.tooltip(g, ui.tooltip, mouseX, mouseY, width, height);
		}
		if (asking) {
			drawModal(g, mouseX, mouseY);
		}
		Announcer.render(g, width, height, height - TrahisonUi.SAFE_MARGIN * 4);
	}

	private void drawModal(GuiGraphics g, int mouseX, int mouseY) {
		modalUi.begin(mouseX, mouseY);
		int[] r = TrahisonUi.modal(g, width, height, 248, 112, modal.title(), modal.danger() ? TrahisonPalette.DANGER : accent);
		MenuUi.paragraph(g, modal.text(), r[0], r[1], r[2], TrahisonPalette.TEXT_SECONDARY);
		int by = r[1] + r[3] - TrahisonUi.BUTTON_H;
		Modal m = modal;
		modalUi.button(g, r[0], by, 84, TrahisonUi.BUTTON_H, Component.translatable("phantasmon_events.trahison.cancel"), () -> modal = null);
		modalUi.button(g, r[0] + r[2] - 104, by, 104, TrahisonUi.BUTTON_H, m.yes(), m.danger() ? ButtonKind.DANGER : ButtonKind.PRIMARY, null, () -> {
			modal = null;
			m.action().run();
		});
		if (modalUi.tooltip != null) {
			TrahisonUi.tooltip(g, modalUi.tooltip, mouseX, mouseY, width, height);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (modal != null) {
			modalUi.click(mouseX, mouseY, button);
			return true;
		}
		return ui.click(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return modal == null && scrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (modal != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				modal = null;
			} else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				Modal m = modal;
				modal = null;
				m.action().run();
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** Scroll offset kept inside {@code 0..max(0, overflow)}. */
	protected static int clamp(int scroll, int overflow) {
		return Math.max(0, Math.min(scroll, Math.max(0, overflow)));
	}
}
