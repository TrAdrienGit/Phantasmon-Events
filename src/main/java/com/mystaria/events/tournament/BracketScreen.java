package com.mystaria.events.tournament;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * A double elimination bracket in full screen (2026-11-11), from the Tournaments page: the whole tree at a zoom that
 * fits the screen, dragged with the mouse and zoomed with the wheel; kept up to date. Escape goes back to the menu.
 */
public final class BracketScreen extends Screen {

	private final Screen parent;
	private final String name;
	private TournamentDto tournament;
	private float zoom = -1;
	private float panX;
	private float panY;
	private double lastX = Double.NaN;
	private double lastY;
	private long loadedAt;

	public BracketScreen(Screen parent, String name) {
		super(Component.literal(name));
		this.parent = parent;
		this.name = name;
	}

	@Override
	protected void init() {
		load();
	}

	private void load() {
		loadedAt = System.currentTimeMillis();
		PhantasmonApi.get("/tournaments/" + name, TournamentDto.class)
				.thenAccept(t -> Minecraft.getInstance().execute(() -> tournament = t))
				.exceptionally(ex -> null);
	}

	@Override
	public void tick() {
		if (System.currentTimeMillis() - loadedAt > 10_000) {
			load(); // results come in while it is open
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		MenuUi.Palette c = MenuUi.p();
		g.fill(0, 0, width, height, c.dark());
		MenuUi.dots(g, 0, 24, width, height);
		MenuUi.header(g, 0, 0, width, 24);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		MenuUi.Palette c = MenuUi.p();
		MenuUi.text(g, Component.literal(name).withStyle(s -> s.withBold(true)), 21, 7, width / 2, c.white());
		MenuUi.text(g, Component.translatable("phantasmon_events.tp.bracket.controls"), width / 2, 8, width / 2 - 10, c.muted());
		if (tournament == null) {
			MenuUi.centered(g, Component.translatable("phantasmon_events.tp.loading"), width / 2, height / 2, width, c.muted());
			return;
		}
		BracketView.Layout layout = BracketView.layout(tournament);
		if (layout.width() == 0) {
			return;
		}
		int areaW = width - 16;
		int areaH = height - 36;
		if (zoom < 0) {
			zoom = Math.max(0.35f, Math.min(1.6f, Math.min(areaW / (float) layout.width(), areaH / (float) layout.height())));
			panX = (areaW - layout.width() * zoom) / 2f;
			panY = Math.max(0, (areaH - layout.height() * zoom) / 2f);
		}
		Map<String, UUID> byName = new HashMap<>();
		tournament.players().forEach(p -> byName.put(p.username(), p.uuid()));
		String me = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile().getName();
		g.enableScissor(8, 28, width - 8, height - 8);
		g.pose().pushPose();
		g.pose().translate(8 + panX, 28 + panY, 0);
		g.pose().scale(zoom, zoom, 1f);
		BracketView.render(g, tournament, layout, 0, 0, byName::get, me);
		g.pose().popPose();
		g.disableScissor();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		lastX = mouseX;
		lastY = mouseY;
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (!Double.isNaN(lastX)) {
			panX += (float) (mouseX - lastX);
			panY += (float) (mouseY - lastY);
		}
		lastX = mouseX;
		lastY = mouseY;
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		lastX = Double.NaN;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (zoom <= 0) {
			return true;
		}
		// Zoom round the mouse.
		float before = zoom;
		zoom = Math.max(0.3f, Math.min(2.5f, zoom * (scrollY > 0 ? 1.12f : 1 / 1.12f)));
		double localX = (mouseX - 8 - panX) / before;
		double localY = (mouseY - 28 - panY) / before;
		panX = (float) (mouseX - 8 - localX * zoom);
		panY = (float) (mouseY - 28 - localY * zoom);
		return true;
	}
}
