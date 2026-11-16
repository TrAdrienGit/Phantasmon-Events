package com.mystaria.events.trahison.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.edit.RoomDto;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Draw;
import com.mystaria.events.trahison.net.GameView.DrawPick;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.Messages;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The game master screen's view of the draw (T4.1): the nine rooms round by round with their difficulty, mechanism and place,
 * the total of each round, the constraints the draw had to relax, a button to draw again, and a click on a room to put another
 * room of the bank in its place. The backend decides (it refuses a replacement once the round's rooms started); the panel only
 * hides the controls then. Pure display of the view: nothing here is a rule. Client thread.
 */
final class DrawPanel {

	private static final String T = "phantasmon_events.trahison.gm.draw.";
	private static final int ROW_H = 18;

	/** The pick being replaced ({@code round * 10 + group}), or -1. */
	private static int selected = -1;
	/** The bank's rooms that have blocks, once fetched; null until then. */
	private static List<RoomDto.Summary> bank;
	private static boolean loading;
	private static boolean failed;

	private DrawPanel() {
	}

	/**
	 * Gives the export button the drawn rooms (T4.2's {@code ExportFlow.drawn}): place id to room id, in the backend's order. The
	 * export pairs by place id, so the order of the plan never matters. A pick without a place fails the export ("no_places").
	 */
	static void install() {
		com.mystaria.events.trahison.edit.ExportFlow.drawn = (gameId, layout) -> {
			Draw draw = GameView.orEmpty().gm() == null ? null : GameView.orEmpty().gm().draw();
			if (draw == null || draw.picks().stream().anyMatch(p -> p.place() == null)) {
				return java.util.concurrent.CompletableFuture.failedFuture(new IllegalArgumentException("no_places"));
			}
			java.util.Map<String, String> byPlace = new java.util.LinkedHashMap<>();
			for (DrawPick p : draw.picks()) {
				byPlace.put(p.place(), p.room());
			}
			return java.util.concurrent.CompletableFuture.completedFuture(byPlace);
		};
	}

	/** Forgets the selection (the screen was closed or the game changed). */
	static void reset() {
		selected = -1;
	}

	/** Draws the panel at {@code (x, y)} in width {@code w}; returns the height used. */
	static int draw(GuiGraphics g, ZoneUi ui, View v, int x, int y, int w) {
		GameView.Gm gm = v.gm();
		if (gm == null) {
			return 0;
		}
		int used = 0;
		MenuUi.text(g, Component.translatable(T + "title"), x, y, w, TrahisonPalette.GAME_MASTER);
		used += 14;
		Draw draw = gm.draw();
		boolean early = v.phase() == Phase.LOBBY || v.phase() == Phase.PROLOGUE;
		if (draw == null) {
			return used + MenuUi.paragraph(g, Component.translatable(T + "none"), x, y + used, w, TrahisonPalette.TEXT_MUTED) + 4;
		}
		for (int round = 1; round <= 3; round++) {
			Component head = Component.translatable(T + "round", round);
			MenuUi.text(g, head, x, y + used + 3, w, TrahisonPalette.TEXT_SECONDARY);
			TrahisonUi.pill(g, x + w - 56, y + used, Component.translatable(T + "total", draw.total(round)), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_MUTED);
			used += 14;
			boolean editable = editable(v, early, round);
			for (DrawPick pick : draw.picks()) {
				if (pick.round() == round) {
					used += row(g, ui, pick, x, y + used, w, editable);
					if (selected == key(pick)) {
						used += bankList(g, ui, pick, x, y + used, w);
					}
				}
			}
			used += 4;
		}
		if (!draw.relaxed().isEmpty()) {
			List<Component> names = new ArrayList<>();
			draw.relaxed().forEach(code -> names.add(Component.translatable(T + "relaxed." + code)));
			Component joined = Component.empty();
			for (int i = 0; i < names.size(); i++) {
				joined = i == 0 ? names.get(0) : Component.translatable(T + "join", joined, names.get(i));
			}
			used += MenuUi.paragraph(g, Component.translatable(T + "relaxed", joined), x, y + used, w, TrahisonPalette.DANGER) + 4;
		}
		if (early) {
			ui.button(g, x, y + used, w, TrahisonUi.BUTTON_H, Component.translatable(T + "redraw"), ButtonKind.DEFAULT,
					Component.translatable(T + "redraw.tip"), () -> {
						selected = -1;
						Messages.redraw();
					});
			used += TrahisonUi.BUTTON_H + 4;
		}
		return used;
	}

	/** The rooms of a round may be replaced until the round's rooms start: before the end of its doors stage. */
	private static boolean editable(View v, boolean early, int round) {
		if (early) {
			return true;
		}
		return round > v.round() || round == v.round() && v.phase() == Phase.DOORS;
	}

	private static int key(DrawPick pick) {
		return pick.round() * 10 + pick.group();
	}

	private static int row(GuiGraphics g, ZoneUi ui, DrawPick pick, int x, int y, int w, boolean editable) {
		boolean on = selected == key(pick);
		boolean hover = editable && ui.hovered(x, y, w, ROW_H);
		TrahisonUi.row(g, x, y, w, ROW_H, hover, on);
		int px = x + 4;
		px += TrahisonUi.pill(g, px, y + 3, Component.translatable(T + "difficulty", pick.difficulty()), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.ACCENT) + 4;
		Component place = pick.place() == null ? Component.translatable(T + "no_place") : Component.literal(pick.place());
		int pw = Math.min(TrahisonUi.width(place) + 10, w / 3);
		TrahisonUi.textEllipsis(g, Component.literal(pick.name() + (pick.manual() ? " *" : "")), px, y + 5, x + w - pw - 8 - px,
				pick.manual() ? TrahisonPalette.GAME_MASTER : TrahisonPalette.TEXT);
		TrahisonUi.textEllipsis(g, place, x + w - pw - 2, y + 5, pw, pick.place() == null ? TrahisonPalette.DANGER : TrahisonPalette.TEXT_MUTED);
		ui.tip(x, y, w, ROW_H, Component.translatable(T + "row.tip", pick.mechanism().isEmpty() ? "-" : pick.mechanism(), pick.expectedSeconds() / 60,
				pick.orientation() == null ? "-" : pick.orientation()));
		if (editable) {
			ui.zone(x, y, w, ROW_H, button -> {
				if (button == 0) {
					selected = on ? -1 : key(pick);
					if (selected >= 0) {
						loadBank();
					}
				}
			});
		}
		return ROW_H + 2;
	}

	/** The bank's rooms under the selected pick, each a button that puts it there. */
	private static int bankList(GuiGraphics g, ZoneUi ui, DrawPick pick, int x, int y, int w) {
		int used = 0;
		if (bank == null) {
			Component text = Component.translatable(T + (failed ? "bank.failed" : "bank.loading"));
			return MenuUi.paragraph(g, text, x + 8, y, w - 8, failed ? TrahisonPalette.DANGER : TrahisonPalette.TEXT_MUTED) + 2;
		}
		if (bank.isEmpty()) {
			return MenuUi.paragraph(g, Component.translatable(T + "bank.empty"), x + 8, y, w - 8, TrahisonPalette.TEXT_MUTED) + 2;
		}
		for (RoomDto.Summary room : bank) {
			boolean current = room.id.equals(pick.room());
			int rx = x + 8;
			int rw = w - 8;
			TrahisonUi.row(g, rx, y + used, rw, ROW_H, !current && ui.hovered(rx, y + used, rw, ROW_H), current);
			TrahisonUi.textEllipsis(g, Component.literal((room.name == null ? room.id : room.name) + "  (" + room.difficulty + ")"), rx + 4, y + used + 5, rw - 8,
					current ? TrahisonPalette.ACCENT : TrahisonPalette.TEXT);
			if (!current) {
				int round = pick.round();
				int group = pick.group();
				String id = room.id;
				ui.zone(rx, y + used, rw, ROW_H, button -> {
					if (button == 0) {
						selected = -1;
						Messages.replaceRoom(round, group, id);
					}
				});
			}
			used += ROW_H + 1;
		}
		return used + 2;
	}

	private static void loadBank() {
		if (loading) {
			return;
		}
		loading = true;
		failed = false;
		PhantasmonApi.get("/events/trahison/rooms", RoomDto.Summary[].class).whenComplete((rooms, failure) -> Minecraft.getInstance().execute(() -> {
			loading = false;
			if (failure != null || rooms == null) {
				failed = true;
				return;
			}
			List<RoomDto.Summary> usable = new ArrayList<>();
			for (RoomDto.Summary r : rooms) {
				if (r.hasSchematic) {
					usable.add(r);
				}
			}
			bank = usable;
		}));
	}
}
