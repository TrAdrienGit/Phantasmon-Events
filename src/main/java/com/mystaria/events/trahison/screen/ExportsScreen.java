package com.mystaria.events.trahison.screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The paste files kept on the backend (D80), for admins: the games that have files, then the files of one game, each one
 * downloaded into {@code .minecraft/phantasmon_events/trahison/export/<game>/} with a click. Opened by
 * {@code /phantasmon event trahison exports}. Holds no rule: the backend serves and checks. Client thread.
 */
public final class ExportsScreen extends TrahisonScreen {

	private static final String T = "phantasmon_events.trahison.exports.";

	/** One line of {@code GET /events/trahison/exports} (Gson fills it). */
	public static final class GameDto {
		public String game;
		public List<String> files = new ArrayList<>();
		public String updatedAt;
	}

	private List<GameDto> games;
	private boolean failed;
	private GameDto open;
	private String status = "";
	private int scroll;
	private int content;

	private ExportsScreen() {
		super(Component.translatable(T + "title"), TrahisonPalette.ACCENT);
	}

	public static void open() {
		if (!PhantasmonApi.isAdmin()) {
			Minecraft.getInstance().player.displayClientMessage(Component.translatable(T + "not_admin"), false);
			return;
		}
		ExportsScreen screen = new ExportsScreen();
		Minecraft.getInstance().setScreen(screen);
		screen.refresh();
	}

	private void refresh() {
		PhantasmonApi.get("/events/trahison/exports", GameDto[].class).whenComplete((list, failure) -> Minecraft.getInstance().execute(() -> {
			failed = failure != null || list == null;
			games = failed ? null : List.of(list);
			open = null;
		}));
	}

	private static Path folder(String game) {
		return FabricLoader.getInstance().getGameDir().resolve("phantasmon_events").resolve("trahison").resolve("export").resolve(game);
	}

	private void download(GameDto game, List<String> names) {
		status = Component.translatable(T + "downloading").getString();
		CompletableFuture.runAsync(() -> {
			try {
				Path dir = folder(game.game);
				Files.createDirectories(dir);
				for (String name : names) {
					@SuppressWarnings("rawtypes")
					Map answer = PhantasmonApi.get("/events/trahison/exports/" + game.game + "/files/" + name, Map.class).join();
					Files.write(dir.resolve(name), Base64.getDecoder().decode(String.valueOf(answer.get("data"))));
				}
			} catch (Exception e) {
				throw new java.util.concurrent.CompletionException(e);
			}
		}).whenComplete((v, failure) -> Minecraft.getInstance().execute(() -> status = failure == null
				? Component.translatable(T + "done", names.size(), folder(game.game).toString()).getString()
				: Component.translatable(T + "failed").getString()));
	}

	@Override
	protected void drawStage(GuiGraphics g, int mouseX, int mouseY) {
		int[] r = TrahisonUi.stage(width, height, 320, 220);
		TrahisonUi.titledPanel(g, r[0], r[1], r[2], r[3], title, TrahisonPalette.ACCENT);
		int x = r[0] + 8;
		int w = r[2] - 16;
		int top = r[1] + 28;
		int bottom = r[1] + r[3] - 28;
		MenuUi.text(g, Component.literal(status), x, bottom + 6, w - 90, TrahisonPalette.TEXT_MUTED);
		if (open != null) {
			ui.button(g, r[0] + r[2] - 8 - 80, bottom + 4, 80, 18, Component.translatable(T + "back"), () -> open = null);
		}
		if (games == null) {
			MenuUi.paragraph(g, Component.translatable(T + (failed ? "failed_list" : "loading")), x, top, w, failed ? TrahisonPalette.DANGER : TrahisonPalette.TEXT_MUTED);
			return;
		}
		if (games.isEmpty()) {
			MenuUi.paragraph(g, Component.translatable(T + "none"), x, top, w, TrahisonPalette.TEXT_MUTED);
			return;
		}
		TrahisonUi.well(g, x, top, w, bottom - top);
		scroll = clamp(scroll, content - (bottom - top - 4));
		ui.clip(g, x + 1, top + 1, x + w - 1, bottom - 1);
		int y = top + 4 - scroll;
		int used = 0;
		if (open == null) {
			for (GameDto game : games) {
				TrahisonUi.row(g, x + 4, y + used, w - 8, 18, ui.hovered(x + 4, y + used, w - 8, 18), false);
				TrahisonUi.textEllipsis(g, Component.literal(game.game + "  (" + game.files.size() + ")"), x + 10, y + used + 5, w - 24, TrahisonPalette.TEXT);
				GameDto picked = game;
				ui.zone(x + 4, y + used, w - 8, 18, b -> {
					if (b == 0) {
						open = picked;
						scroll = 0;
					}
				});
				used += 20;
			}
		} else {
			ui.button(g, x + 4, y + used, w - 8, 18, Component.translatable(T + "all", open.files.size()), ButtonKind.PRIMARY, null,
					() -> download(open, open.files));
			used += 22;
			for (String name : open.files) {
				TrahisonUi.row(g, x + 4, y + used, w - 8, 18, ui.hovered(x + 4, y + used, w - 8, 18), false);
				TrahisonUi.textEllipsis(g, Component.literal(name), x + 10, y + used + 5, w - 24, TrahisonPalette.TEXT);
				GameDto picked = open;
				ui.zone(x + 4, y + used, w - 8, 18, b -> {
					if (b == 0) {
						download(picked, List.of(name));
					}
				});
				used += 20;
			}
		}
		ui.unclip(g);
		content = used + 8;
	}

	@Override
	protected boolean scrolled(double mouseX, double mouseY, double amount) {
		scroll = Math.max(0, scroll + (int) Math.round(-amount * 20));
		return true;
	}
}
