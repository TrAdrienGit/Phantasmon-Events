package com.mystaria.events.tournament;

import java.time.Instant;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.tournament.ui.Ui;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Admins: a new tournament, or the dates of one (plan-tournois.md §8, 2026-11-11), inside the Tournaments page: its
 * name, kind (Swiss or double elimination), games per match, Smogon format as chips, then the four dates of its steps
 * — registration opening and closing, check-in opening and closing — each a date and a time where only the digits are
 * typed ({@code 10112026} shows {@code 10-11-2026}, {@code 1910} shows {@code 19:10}). The check-in opens by itself at
 * its date and the tournament starts at its closing; every date is optional. Editing keeps the name, kind and format.
 */
public final class TournamentForm {

	/** Formats a tournament may use (the backend checks them), National Dex first, with their short names. */
	public static final List<String[]> FORMATS = List.of(
			new String[] { "gen9nationaldex", "NatDex OU" },
			new String[] { "gen9nationaldexubers", "NatDex Ubers" },
			new String[] { "gen9nationaldexuu", "NatDex UU" },
			new String[] { "gen9nationaldexru", "NatDex RU" },
			new String[] { "gen9nationaldexmonotype", "NatDex Monotype" },
			new String[] { "gen9nationaldexlc", "NatDex LC" },
			new String[] { "gen9nationaldex1v1", "NatDex 1v1" },
			new String[] { "gen9nationaldexag", "NatDex AG" },
			new String[] { "gen9ou", "OU" },
			new String[] { "gen9ubers", "Ubers" },
			new String[] { "gen9uu", "UU" },
			new String[] { "gen9ru", "RU" },
			new String[] { "gen9nu", "NU" },
			new String[] { "gen9pu", "PU" },
			new String[] { "gen9zu", "ZU" },
			new String[] { "gen9lc", "LC" },
			new String[] { "gen9monotype", "Monotype" },
			new String[] { "gen91v1", "1v1" },
			new String[] { "gen9anythinggoes", "Anything Goes" });

	/** The four dated steps: their API field and their label. */
	private static final String[][] DATES = {
			{ "registration_opens_at", "phantasmon_events.tp.form.registration_opens" },
			{ "registration_closes_at", "phantasmon_events.tp.form.registration_closes" },
			{ "check_in_opens_at", "phantasmon_events.tp.form.check_in_opens" },
			{ "check_in_closes_at", "phantasmon_events.tp.form.check_in_closes" } };

	private final TournamentsPage page;
	private final TournamentDto editing;
	/** The readable name ("Weekly OU #1"); the backend derives the id from it. */
	private final Ui.Field name = new Ui.Field(Ui.Kind.TEXT, 48);
	private final Ui.Field[] dates = new Ui.Field[4];
	private final Ui.Field[] times = new Ui.Field[4];
	private String kind = "SWISS";
	private int bestOf = 1;
	private String format = FORMATS.get(0)[0];
	private int scroll;
	private boolean busy;
	private Component error;

	TournamentForm(TournamentsPage page, TournamentDto editing) {
		this.page = page;
		this.editing = editing;
		String[] existing = editing == null ? new String[4]
				: new String[] { editing.registrationOpensAt(), editing.registrationClosesAt(), editing.checkInOpensAt(), editing.checkInClosesAt() };
		for (int i = 0; i < 4; i++) {
			dates[i] = new Ui.Field(Ui.Kind.DATE, 8);
			times[i] = new Ui.Field(Ui.Kind.TIME, 4);
			String[] digits = TournamentsPage.digits(existing[i]);
			dates[i].value = digits[0];
			times[i].value = digits[1];
		}
		if (editing != null) {
			name.value = editing.title();
		}
	}

	TournamentDto editing() {
		return editing;
	}

	void scroll(double amount) {
		scroll -= (int) Math.round(amount * 20);
	}

	void render(GuiGraphics g, Ui ui, int x, int y, int right, int bottom) {
		MenuUi.Palette c = MenuUi.p();
		int w = right - x - 8;
		int total = editing == null ? 330 : 190;
		int view = bottom - y;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, total - view)));
		MenuUi.scrollbar(g, right - 5, y, view, total, scroll, false);
		ui.clip(g, x, y, right, bottom);
		int top = y - scroll;
		int row = top;
		if (editing == null) {
			ui.field(g, name, x, row + 10, Math.min(w, 260), 15, Component.translatable("phantasmon_events.tp.form.name"),
					Component.translatable("phantasmon_events.tp.form.name_hint").getString(), false);
			if (!name.value.isBlank()) {
				// The id the backend will derive (a suffix may follow if another tournament has it).
				MenuUi.text(g, Component.translatable("phantasmon_events.tp.form.id", slug(name.value)), x + Math.min(w, 260) + 8, row + 14,
						Math.max(40, w - Math.min(w, 260) - 8), c.edge());
			}
			row += 34;
			MenuUi.text(g, Component.translatable("phantasmon_events.tp.form.kind"), x, row, 200, c.muted());
			ui.chip(g, x, row + 11, 120, 15, Component.translatable("phantasmon_events.tp.kind.swiss"), "SWISS".equals(kind), () -> kind = "SWISS");
			ui.chip(g, x + 124, row + 11, 140, 15, Component.translatable("phantasmon_events.tp.kind.bracket"), "BRACKET".equals(kind), () -> kind = "BRACKET");
			int bx = x + 280;
			if (bx + 130 > right) {
				row += 34;
				bx = x;
			}
			MenuUi.text(g, Component.translatable("phantasmon_events.tp.form.best_of"), bx, bx == x ? row : row, 200, c.muted());
			for (int i = 0; i < 3; i++) {
				int value = 1 + 2 * i;
				ui.chip(g, bx + i * 42, row + 11, 38, 15, Component.literal("Bo" + value), bestOf == value, () -> bestOf = value);
			}
			row += 34;
			MenuUi.text(g, Component.translatable("phantasmon_events.tp.form.format"), x, row, 200, c.muted());
			int columns = Math.max(2, w / 110);
			int fw = (w - (columns - 1) * 4) / columns;
			for (int i = 0; i < FORMATS.size(); i++) {
				String[] f = FORMATS.get(i);
				ui.filter(g, x + (i % columns) * (fw + 4), row + 11 + (i / columns) * 17, fw, 14, Component.literal(f[1]),
						f[0].equals(format), () -> format = f[0]);
			}
			row += 11 + ((FORMATS.size() + columns - 1) / columns) * 17 + 8;
		} else {
			MenuUi.text(g, Component.translatable("phantasmon_events.tp.form.fixed", editing.formatName(), editing.bestOf(),
					Component.translatable(editing.swiss() ? "phantasmon_events.tp.kind.swiss" : "phantasmon_events.tp.kind.bracket")),
					x, row + 2, w, c.muted());
			row += 18;
		}
		// The dates: one line each, the step's name then its date, time, and a cross to clear them.
		MenuUi.text(g, Component.translatable("phantasmon_events.tp.form.dates").withStyle(s -> s.withBold(true)), x, row, w, c.white());
		row += 14;
		for (int i = 0; i < 4; i++) {
			int ly = row + i * 22;
			MenuUi.text(g, Component.translatable(DATES[i][1]), x, ly + 4, 170, c.muted());
			boolean badDate = !dates[i].completeOrEmpty() || dates[i].value.length() == 8 && !validDate(dates[i], times[i]);
			ui.field(g, dates[i], x + 176, ly, 82, 15, null, "JJ-MM-AAAA", badDate);
			ui.field(g, times[i], x + 264, ly, 46, 15, null, "HH:MM", !times[i].completeOrEmpty());
			int index = i;
			if (!dates[i].value.isEmpty() || !times[i].value.isEmpty()) {
				ui.mini(g, x + 316, ly, Component.literal("x"), true, () -> {
					dates[index].value = "";
					times[index].value = "";
				});
			}
		}
		row += 4 * 22 + 4;
		row += MenuUi.paragraph(g, Component.translatable("phantasmon_events.tp.form.dates_note"), x, row, w, c.edge()) + 8;
		if (error != null) {
			row += MenuUi.paragraph(g, error, x, row, w, c.danger()) + 6;
		}
		ui.button(g, x, row, 110, 16, Component.translatable("gui.cancel"), () -> page.formDone(null));
		ui.primary(g, x + 116, row, 140, 16, Component.translatable(editing == null ? "phantasmon_events.tp.form.create"
				: "phantasmon_events.tp.form.save"), busy ? null : this::submit);
		ui.unclip(g);
	}

	/** The backend's id rule ({@code TournamentService.slug}), to show what the name will give. */
	static String slug(String displayName) {
		String plain = java.text.Normalizer.normalize(displayName, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String slug = plain.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (slug.length() > 32) {
			slug = slug.substring(0, 32).replaceAll("_+$", "");
		}
		return slug.length() < 2 ? "tournoi" : slug;
	}

	private static boolean validDate(Ui.Field date, Ui.Field time) {
		try {
			TournamentsPage.instant(date, time);
			return true;
		} catch (DateTimeException ex) {
			return false;
		}
	}

	private void submit() {
		Map<String, Object> body = new HashMap<>();
		List<String> clear = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			if (!dates[i].completeOrEmpty() || !times[i].completeOrEmpty() || dates[i].value.isEmpty() && !times[i].value.isEmpty()) {
				error = Component.translatable("phantasmon_events.tp.form.bad_date", Component.translatable(DATES[i][1]));
				return;
			}
			Instant at;
			try {
				at = TournamentsPage.instant(dates[i], times[i]);
			} catch (DateTimeException ex) {
				error = Component.translatable("phantasmon_events.tp.form.bad_date", Component.translatable(DATES[i][1]));
				return;
			}
			if (at != null) {
				body.put(DATES[i][0], at.toString());
			} else {
				clear.add(DATES[i][0]);
			}
		}
		error = null;
		busy = true;
		var request = editing != null ? schedule(body, clear) : create(body);
		request.thenAccept(t -> Minecraft.getInstance().execute(() -> {
			busy = false;
			page.host().status(Component.translatable(editing != null ? "phantasmon_events.tp.done.saved" : "phantasmon_events.tp.done.created"), false);
			page.formDone(t.name());
		})).exceptionally(failure -> {
			Minecraft.getInstance().execute(() -> {
				busy = false;
				error = TournamentMessages.error(failure);
			});
			return null;
		});
	}

	private java.util.concurrent.CompletableFuture<TournamentDto> schedule(Map<String, Object> body, List<String> clear) {
		body.put("clear", clear);
		return PhantasmonApi.post("/admin/tournaments/" + editing.name() + "/schedule", body, TournamentDto.class);
	}

	private java.util.concurrent.CompletableFuture<TournamentDto> create(Map<String, Object> body) {
		body.put("display_name", name.value.strip());
		body.put("kind", kind);
		body.put("best_of", bestOf);
		body.put("format_id", format);
		return PhantasmonApi.post("/admin/tournaments", body, TournamentDto.class);
	}
}
