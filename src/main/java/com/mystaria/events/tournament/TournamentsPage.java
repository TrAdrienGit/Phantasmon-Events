package com.mystaria.events.tournament;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.tournament.ui.Ui;
import com.mystaria.phantasmon.client.api.MenuHost;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuPage;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The Tournaments page of the Phantasmon menu (2026-11-11, replaces the old tournaments screen), built as a tournament
 * site: the list of tournaments as cards (status, format, players' faces, next date); a tournament's page with its
 * header and main action (register, check in), its steps and their dates, and tabs — players, matches, standings,
 * bracket (double elimination; also full screen) and, for admins, the steps to take by hand. Admins act on a player or
 * a match by clicking it, and create or edit a tournament in a guided form. Kept up to date while open
 * ({@code EventsTournamentChanged}).
 */
public final class TournamentsPage implements MenuPage {

	public static final String ID = "tournaments";
	private static final int CARD_H = 36;
	private static final String[] STEPS = { "REGISTRATION", "CHECK_IN", "RUNNING", "FINISHED" };

	private enum View { LIST, DETAIL, FORM }

	private enum Tab { PLAYERS, MATCHES, STANDINGS, BRACKET, ADMIN }

	private static TournamentsPage open;

	private final MenuHost host;
	private final Ui ui = new Ui();
	private List<TournamentDto> tournaments = List.of();
	private boolean loaded;
	private View view = View.LIST;
	private Tab tab = Tab.PLAYERS;
	private String selected;
	private boolean showOver;
	private int scroll;
	private int round;
	private int panX;
	private int panY;
	private double dragX = Double.NaN;
	private double dragY;
	/** A player's or a match's admin actions, open over the page. */
	private Popup popup;
	private TournamentForm form;

	private int x;
	private int y;
	private int right;
	private int bottom;
	private int contentTop;

	private record Popup(int x, int y, List<Map.Entry<Component, Runnable>> actions, boolean[] danger) {
	}

	public TournamentsPage(MenuHost host) {
		this.host = host;
		open = this;
		refresh();
	}

	/** Phantasmon Events' startup: the menu page, and a refresh on every {@code EventsTournamentChanged}. */
	public static void register() {
		PhantasmonApi.registerMenuPage(new PhantasmonApi.MenuPageEntry(ID, Component.translatable("phantasmon_events.tp.title"),
				() -> Minecraft.getInstance().player != null, TournamentsPage::new));
		PhantasmonApi.onEventsMessage((type, data) -> {
			if ("EventsTournamentChanged".equals(type) && open != null) {
				open.refresh();
			}
		});
	}

	@Override
	public Component title() {
		return switch (view) {
			case LIST -> Component.translatable("phantasmon_events.tp.title");
			case DETAIL -> Component.literal(current() == null ? "" : current().name());
			case FORM -> Component.translatable(form != null && form.editing() != null ? "phantasmon_events.tp.form.edit_title"
					: "phantasmon_events.tp.form.create_title");
		};
	}

	@Override
	public boolean wide() {
		return true;
	}

	@Override
	public void close() {
		if (open == this) {
			open = null;
		}
	}

	// ---------------------------------------------------------------- data

	void refresh() {
		PhantasmonApi.get("/tournaments", TournamentDto[].class).thenAccept(all -> Minecraft.getInstance().execute(() -> {
			tournaments = Arrays.asList(all);
			loaded = true;
			if (view == View.DETAIL && current() == null) {
				view = View.LIST;
			}
		})).exceptionally(this::failed);
	}

	private TournamentDto current() {
		return tournaments.stream().filter(t -> t.name().equals(selected)).findFirst().orElse(null);
	}

	Void failed(Throwable failure) {
		Minecraft.getInstance().execute(() -> {
			Component message = TournamentMessages.error(failure);
			if (host.dialogOpen()) {
				host.dialogError(message);
			} else {
				host.status(message, true);
			}
		});
		return null;
	}

	/** A request whose answer is the tournament: the page shows it, the footer says it went through. */
	private void send(CompletableFuture<TournamentDto> request, Component done) {
		request.thenAccept(t -> Minecraft.getInstance().execute(() -> {
			host.closeDialog();
			host.status(done == null ? Component.empty() : done, false);
			refresh();
		})).exceptionally(this::failed);
	}

	private static String me() {
		return Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile().getName();
	}

	private static TournamentDto.Player mine(TournamentDto t) {
		UUID me = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
		return t.players().stream().filter(p -> p.uuid().equals(me)).findFirst().orElse(null);
	}

	private static Map<String, UUID> uuids(TournamentDto t) {
		Map<String, UUID> byName = new HashMap<>();
		t.players().forEach(p -> byName.put(p.username(), p.uuid()));
		return byName;
	}

	private static boolean over(TournamentDto t) {
		return "FINISHED".equals(t.status()) || "CANCELLED".equals(t.status());
	}

	// ---------------------------------------------------------------- layout and drawing

	@Override
	public void layout(int x, int y, int right, int bottom) {
		this.x = x;
		this.y = y;
		this.right = right;
		this.bottom = bottom;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY) {
		ui.begin(popup != null ? -1 : mouseX, popup != null ? -1 : mouseY);
		switch (view) {
			case LIST -> renderList(g);
			case DETAIL -> {
				TournamentDto t = current();
				if (t == null) {
					view = View.LIST;
					renderList(g);
				} else {
					renderDetail(g, t);
				}
			}
			case FORM -> form.render(g, ui, x, y, right, bottom);
		}
		if (popup != null) {
			ui.begin(mouseX, mouseY);
			renderPopup(g);
		}
	}

	// ---- the list

	private void renderList(GuiGraphics g) {
		MenuUi.Palette c = MenuUi.p();
		ui.filter(g, x, y, 92, 14, Component.translatable("phantasmon_events.tp.filter.current"), !showOver, () -> {
			showOver = false;
			scroll = 0;
		});
		ui.filter(g, x + 96, y, 92, 14, Component.translatable("phantasmon_events.tp.filter.over"), showOver, () -> {
			showOver = true;
			scroll = 0;
		});
		if (PhantasmonApi.isAdmin()) {
			ui.primary(g, right - 110, y, 110, 14, Component.translatable("phantasmon_events.tp.create"), () -> {
				form = new TournamentForm(this, null);
				view = View.FORM;
				scroll = 0;
			});
		}
		List<TournamentDto> shown = tournaments.stream().filter(t -> over(t) == showOver).toList();
		int top = y + 22;
		if (!loaded) {
			MenuUi.text(g, Component.translatable("phantasmon_events.tp.loading"), x, top + 4, right - x, c.muted());
			return;
		}
		if (shown.isEmpty()) {
			MenuUi.paragraph(g, Component.translatable(showOver ? "phantasmon_events.tp.none_over" : "phantasmon_events.tp.none"),
					x, top + 4, right - x, c.muted());
			return;
		}
		int total = shown.size() * (CARD_H + 5);
		int view = bottom - top;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, total - view)));
		ui.clip(g, x, top, right, bottom);
		for (int i = 0; i < shown.size(); i++) {
			int cy = top + i * (CARD_H + 5) - scroll;
			if (cy + CARD_H < top || cy > bottom) {
				continue;
			}
			card(g, shown.get(i), x, cy, right - x - 8);
		}
		ui.unclip(g);
		MenuUi.scrollbar(g, right - 5, top, view, total, scroll, false);
	}

	private void card(GuiGraphics g, TournamentDto t, int cx, int cy, int w) {
		MenuUi.Palette c = MenuUi.p();
		boolean hovered = ui.hovered(cx, cy, w, CARD_H);
		MenuUi.row(g, cx, cy, w, CARD_H, hovered);
		g.fill(cx, cy, cx + 3, cy + CARD_H, statusColour(t.status()));
		int chipW = statusChip(g, t.status(), cx + w - 6, cy + 5);
		MenuUi.text(g, Component.literal(t.name()).withStyle(s -> s.withBold(true)), cx + 10, cy + 6, w - chipW - 120, c.white());
		// Faces of the first players, overlapping, and the count.
		int shownHeads = Math.min(6, t.players().size());
		int hx = cx + w - chipW - 18 - shownHeads * 8;
		for (int i = 0; i < shownHeads; i++) {
			g.fill(hx + i * 8 - 1, cy + 4, hx + i * 8 + 11, cy + 16, c.black());
			PhantasmonApi.drawPlayerHead(g, t.players().get(i).uuid(), hx + i * 8, cy + 5, 10);
		}
		Component info = Component.literal(t.formatName() + "  ·  Bo" + t.bestOf() + "  ·  ")
				.append(Component.translatable(t.swiss() ? "phantasmon_events.tp.kind.swiss" : "phantasmon_events.tp.kind.bracket"))
				.append("  ·  ").append(Component.translatable("phantasmon_events.tp.players_count", t.players().size()));
		MenuUi.text(g, info, cx + 10, cy + 21, w - 150, c.muted());
		Component next = nextStep(t);
		if (next != null) {
			int nw = Math.min(140, MenuUi.width(next) + 2);
			MenuUi.text(g, next, cx + w - 6 - nw, cy + 21, nw, c.muted());
		}
		TournamentDto.Player me = mine(t);
		if (me != null) {
			Component badge = Component.translatable(me.checkedIn() ? "phantasmon_events.tp.me.present" : "phantasmon_events.tp.me.registered");
			int bw = MenuUi.width(badge) + 8;
			int bx = cx + 10 + Math.min(MenuUi.width(Component.literal(t.name()).withStyle(s -> s.withBold(true))), w - chipW - 120) + 8;
			g.fill(bx, cy + 4, bx + bw, cy + 15, me.checkedIn() ? 0xFF1F5E3C : c.accentDim());
			MenuUi.text(g, badge, bx + 4, cy + 6, bw, c.white());
		}
		ui.zone(cx, cy, w, CARD_H, () -> {
			selected = t.name();
			view = View.DETAIL;
			tab = t.currentRound() > 0 ? Tab.MATCHES : Tab.PLAYERS;
			round = t.currentRound();
			scroll = 0;
			panX = 0;
			panY = 0;
		});
	}

	/** A status label in a coloured pill, its right edge at {@code rightX}; returns its width. */
	private static int statusChip(GuiGraphics g, String status, int rightX, int top) {
		Component label = Component.translatable("phantasmon_events.tournament.status." + status.toLowerCase(Locale.ROOT));
		int w = MenuUi.width(label) + 10;
		int colour = statusColour(status);
		g.fill(rightX - w, top, rightX, top + 12, darker(colour));
		g.fill(rightX - w, top, rightX, top + 1, colour);
		MenuUi.centered(g, label, rightX - w / 2, top + 2, w - 4, 0xFFFFFFFF);
		return w;
	}

	static int statusColour(String status) {
		return switch (status) {
			case "REGISTRATION" -> 0xFF4AA3FF;
			case "CHECK_IN" -> 0xFFFFB23F;
			case "RUNNING" -> 0xFF4CD07D;
			case "CANCELLED" -> 0xFFD9534F;
			default -> 0xFF8D8D8D;
		};
	}

	private static int darker(int colour) {
		int r = ((colour >> 16) & 0xFF) * 45 / 100;
		int gr = ((colour >> 8) & 0xFF) * 45 / 100;
		int b = (colour & 0xFF) * 45 / 100;
		return 0xFF000000 | (r << 16) | (gr << 8) | b;
	}

	/** What comes next and when: "Check-in · 12 nov. 19:00"; null when nothing is dated. */
	private static Component nextStep(TournamentDto t) {
		Instant now = Instant.now();
		String[][] steps = {
				{ "phantasmon_events.tp.next.registration", t.registrationOpensAt() },
				{ "phantasmon_events.tp.next.registration_end", t.registrationClosesAt() },
				{ "phantasmon_events.tp.next.check_in", t.checkInOpensAt() },
				{ "phantasmon_events.tp.next.start", t.checkInClosesAt() } };
		if (!"REGISTRATION".equals(t.status()) && !"CHECK_IN".equals(t.status())) {
			return t.currentRound() > 0 && "RUNNING".equals(t.status())
					? Component.translatable(t.swiss() ? "phantasmon_events.tp.round_of" : "phantasmon_events.tp.wave_of",
							t.currentRound(), t.plannedRounds() == null ? "?" : t.plannedRounds())
					: null;
		}
		for (String[] step : steps) {
			if (step[1] != null && Instant.parse(step[1]).isAfter(now)) {
				return Component.translatable(step[0], when(step[1]));
			}
		}
		return null;
	}

	static String when(String iso) {
		if (iso == null) {
			return "—";
		}
		Locale locale = Locale.forLanguageTag(Minecraft.getInstance().options.languageCode.replace('_', '-'));
		return DateTimeFormatter.ofPattern("d MMM HH:mm", locale).format(Instant.parse(iso).atZone(ZoneId.systemDefault()));
	}

	// ---- a tournament

	private void renderDetail(GuiGraphics g, TournamentDto t) {
		MenuUi.Palette c = MenuUi.p();
		boolean admin = PhantasmonApi.isAdmin();
		// Header: back, status, the main action.
		ui.mini(g, x, y, Component.literal("<"), false, () -> {
			view = View.LIST;
			scroll = 0;
		});
		int chipW = statusChip(g, t.status(), x + 24 + statusWidth(t.status()), y + 1);
		int ax = right;
		TournamentDto.Player me = mine(t);
		if (!over(t)) {
			if (me == null && t.registrationOpen()) {
				ax -= 104;
				ui.primary(g, ax, y, 104, 14, Component.translatable("phantasmon_events.tp.action.register"),
						() -> send(PhantasmonApi.post("/tournaments/" + t.name() + "/register", null, TournamentDto.class),
								Component.translatable("phantasmon_events.tp.done.registered")));
			}
			if (me != null && "CHECK_IN".equals(t.status()) && !me.checkedIn()) {
				ax -= 104;
				ui.primary(g, ax, y, 104, 14, Component.translatable("phantasmon_events.tp.action.check_in"),
						() -> send(PhantasmonApi.post("/tournaments/" + t.name() + "/check-in", PhantasmonApi.cobblemonParty(), TournamentDto.class),
								Component.translatable("phantasmon_events.tp.done.checked_in")));
			}
			if (me != null && !"RUNNING".equals(t.status())) {
				ax -= 108;
				ui.button(g, ax, y, 104, 14, Component.translatable("phantasmon_events.tp.action.unregister"),
						() -> host.confirm(Component.translatable("phantasmon_events.tp.action.unregister"),
								List.of(Component.translatable("phantasmon_events.tp.confirm.unregister", t.name())),
								Component.translatable("phantasmon_events.tp.action.unregister"), true,
								() -> send(PhantasmonApi.delete("/tournaments/" + t.name() + "/register", TournamentDto.class), null)));
			}
		}
		if (me != null) {
			Component state = Component.translatable(me.dropped() ? "phantasmon_events.tp.me.dropped"
					: me.checkedIn() ? "phantasmon_events.tp.me.present" : "phantasmon_events.tp.me.registered");
			int sw = MenuUi.width(state) + 10;
			ax -= sw + 4;
			g.fill(ax, y + 1, ax + sw, y + 13, me.dropped() ? c.dangerDim() : me.checkedIn() ? 0xFF1F5E3C : c.accentDim());
			MenuUi.centered(g, state, ax + sw / 2, y + 3, sw - 4, c.white());
		}
		// Format, games, kind, players.
		long present = t.players().stream().filter(TournamentDto.Player::checkedIn).count();
		Component info = Component.literal(t.formatName() + "  ·  Bo" + t.bestOf() + "  ·  ")
				.append(Component.translatable(t.swiss() ? "phantasmon_events.tp.kind.swiss" : "phantasmon_events.tp.kind.bracket"))
				.append("  ·  ").append(Component.translatable("phantasmon_events.tp.players_present", t.players().size(), present));
		MenuUi.text(g, info, x + 28 + chipW, y + 3, ax - x - 36 - chipW, c.muted());
		int sy = y + 20;
		stepper(g, t, x, sy, right);
		// Tabs.
		int ty = sy + 32;
		List<Tab> tabs = new ArrayList<>(List.of(Tab.PLAYERS));
		if (t.currentRound() > 0 || t.matches() != null && !t.matches().isEmpty()) {
			tabs.add(Tab.MATCHES);
			tabs.add(Tab.STANDINGS);
			if (!t.swiss()) {
				tabs.add(Tab.BRACKET);
			}
		}
		if (admin) {
			tabs.add(Tab.ADMIN);
		}
		if (!tabs.contains(tab)) {
			tab = Tab.PLAYERS;
		}
		int tx = x;
		for (Tab each : tabs) {
			Component label = Component.translatable("phantasmon_events.tp.tab." + each.name().toLowerCase(Locale.ROOT));
			int tw = Math.max(64, MenuUi.width(label) + 16);
			ui.chip(g, tx, ty, tw, 15, label, each == tab, () -> {
				tab = each;
				scroll = 0;
			});
			tx += tw + 4;
		}
		contentTop = ty + 22;
		switch (tab) {
			case PLAYERS -> players(g, t);
			case MATCHES -> matches(g, t);
			case STANDINGS -> standings(g, t);
			case BRACKET -> bracket(g, t);
			case ADMIN -> admin(g, t);
		}
	}

	private static int statusWidth(String status) {
		return MenuUi.width(Component.translatable("phantasmon_events.tournament.status." + status.toLowerCase(Locale.ROOT))) + 10;
	}

	/** The four steps, a line between them, each with its date; the current one lit. */
	private void stepper(GuiGraphics g, TournamentDto t, int left, int top, int rightEdge) {
		MenuUi.Palette c = MenuUi.p();
		int current = Arrays.asList(STEPS).indexOf("CANCELLED".equals(t.status()) ? "FINISHED" : t.status());
		String[] dates = { t.registrationOpensAt(), t.checkInOpensAt(), t.checkInClosesAt(), null };
		int span = (rightEdge - left) / STEPS.length;
		g.fill(left, top + 26, rightEdge, top + 27, c.raised());
		for (int i = 0; i < STEPS.length; i++) {
			int cx = left + i * span + 6;
			boolean done = i < current;
			boolean now = i == current;
			if (i < STEPS.length - 1) {
				g.fill(cx + 4, top + 4, left + (i + 1) * span + 6, top + 5, done ? c.good() : c.edge());
			}
			int dot = "CANCELLED".equals(t.status()) && i == 3 ? c.danger() : now ? c.accent() : done ? c.good() : c.edge();
			g.fill(cx - 1, top, cx + 9, top + 9, c.black());
			g.fill(cx, top + 1, cx + 8, top + 8, dot);
			if (now) {
				MenuUi.outline(g, cx - 3, top - 2, cx + 11, top + 11, c.accentHot());
			}
			Component label = Component.translatable("CANCELLED".equals(t.status()) && i == 3 ? "phantasmon_events.tournament.status.cancelled"
					: "phantasmon_events.tp.step." + STEPS[i].toLowerCase(Locale.ROOT));
			MenuUi.text(g, label, cx + 13, top, span - 18, now ? c.white() : done ? c.muted() : c.edge());
			if (dates[i] != null) {
				MenuUi.text(g, Component.literal(when(dates[i])), cx + 13, top + 11, span - 18, c.edge());
			}
		}
	}

	// ---- tabs

	/** A scrolled area under the tabs; returns its top. {@code total}: the content's height. */
	private int area(GuiGraphics g, int total) {
		int view = bottom - contentTop;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, total - view)));
		MenuUi.scrollbar(g, right - 5, contentTop, view, total, scroll, false);
		ui.clip(g, x, contentTop, right, bottom);
		return contentTop - scroll;
	}

	private void players(GuiGraphics g, TournamentDto t) {
		MenuUi.Palette c = MenuUi.p();
		boolean admin = PhantasmonApi.isAdmin() && !over(t);
		int w = right - x - 8;
		int columns = Math.max(1, w / 150);
		int cellW = (w - (columns - 1) * 4) / columns;
		int rows = (t.players().size() + columns - 1) / columns;
		int head = admin ? 22 : 0;
		int top = area(g, head + rows * 20 + 4);
		if (admin) {
			ui.button(g, x, top, 120, 14, Component.translatable("phantasmon_events.tp.admin.add_player"),
					() -> host.ask(Component.translatable("phantasmon_events.tp.admin.add_player"),
							List.of(Component.translatable("phantasmon_events.tp.admin.player_name")), List.of(),
							Component.translatable("phantasmon_events.tp.admin.add"), values -> send(PhantasmonApi.post(
									"/admin/tournaments/" + t.name() + "/players", Map.of("username", values.get(0)), TournamentDto.class),
									Component.translatable("phantasmon_events.tp.done.added", values.get(0)))));
			MenuUi.text(g, Component.translatable("phantasmon_events.tp.admin.click_player"), x + 128, top + 3, w - 130, c.edge());
		}
		if (t.players().isEmpty()) {
			MenuUi.text(g, Component.translatable("phantasmon_events.tp.no_players"), x, top + head + 4, w, c.muted());
		}
		String me = me();
		for (int i = 0; i < t.players().size(); i++) {
			TournamentDto.Player p = t.players().get(i);
			int px = x + (i % columns) * (cellW + 4);
			int py = top + head + (i / columns) * 20;
			boolean hovered = admin && ui.hovered(px, py, cellW, 18);
			MenuUi.row(g, px, py, cellW, 18, hovered);
			if (p.username().equals(me)) {
				g.fill(px, py, px + 2, py + 18, c.accent());
			}
			g.fill(px + 4, py + 2, px + 18, py + 16, c.black());
			PhantasmonApi.drawPlayerHead(g, p.uuid(), px + 5, py + 3, 12);
			MenuUi.text(g, Component.literal(p.username()), px + 22, py + 5, cellW - 80, p.dropped() ? c.edge() : c.white());
			Component state = Component.translatable(p.dropped() ? "phantasmon_events.tp.player.dropped"
					: p.checkedIn() ? "phantasmon_events.tp.player.present" : "phantasmon_events.tp.player.waiting");
			int colour = p.dropped() ? c.danger() : p.checkedIn() ? c.good() : c.muted();
			int sw = MenuUi.width(state);
			MenuUi.text(g, state, px + cellW - sw - 6, py + 5, sw + 2, colour);
			if (admin) {
				int ax = px;
				int ay = py + 18;
				ui.zone(px, py, cellW, 18, () -> playerPopup(t, p, ax, ay));
			}
		}
		ui.unclip(g);
	}

	private void playerPopup(TournamentDto t, TournamentDto.Player p, int px, int py) {
		String base = "/admin/tournaments/" + t.name();
		List<Map.Entry<Component, Runnable>> actions = new ArrayList<>();
		List<Boolean> danger = new ArrayList<>();
		boolean running = "RUNNING".equals(t.status());
		if (running) {
			actions.add(Map.entry(Component.translatable("phantasmon_events.tp.admin.win", p.username()), () -> send(PhantasmonApi.post(
					base + "/result", Map.of("username", p.username(), "forfeit", false), TournamentDto.class), null)));
			danger.add(false);
			actions.add(Map.entry(Component.translatable("phantasmon_events.tp.admin.forfeit", p.username()), () -> send(PhantasmonApi.post(
					base + "/result", Map.of("username", p.username(), "forfeit", true), TournamentDto.class), null)));
			danger.add(false);
			actions.add(Map.entry(Component.translatable("phantasmon_events.tp.admin.reinvite"), () -> send(PhantasmonApi.post(
					base + "/reinvite", Map.of("username", p.username()), TournamentDto.class),
					Component.translatable("phantasmon_events.tp.done.reinvited", p.username()))));
			danger.add(false);
		}
		actions.add(Map.entry(Component.translatable(running ? "phantasmon_events.tp.admin.drop" : "phantasmon_events.tp.admin.remove"),
				() -> host.confirm(Component.translatable(running ? "phantasmon_events.tp.admin.drop" : "phantasmon_events.tp.admin.remove"),
						List.of(Component.translatable(running ? "phantasmon_events.tp.confirm.drop" : "phantasmon_events.tp.confirm.remove", p.username())),
						Component.translatable("phantasmon_events.tp.confirm.yes"), true,
						() -> send(PhantasmonApi.delete(base + "/players/" + p.username(), TournamentDto.class), null))));
		danger.add(true);
		boolean[] flags = new boolean[danger.size()];
		for (int i = 0; i < flags.length; i++) {
			flags[i] = danger.get(i);
		}
		popup = new Popup(px, py, actions, flags);
	}

	private void matchPopup(TournamentDto t, TournamentDto.Match m, int px, int py) {
		if (m.player1() == null || m.player2() == null || m.walkover()) {
			return;
		}
		String base = "/admin/tournaments/" + t.name();
		List<Map.Entry<Component, Runnable>> actions = new ArrayList<>();
		for (String player : List.of(m.player1(), m.player2())) {
			actions.add(Map.entry(Component.translatable("phantasmon_events.tp.admin.win", player), () -> send(PhantasmonApi.post(
					base + "/result", Map.of("username", player, "forfeit", false), TournamentDto.class), null)));
		}
		for (String player : List.of(m.player1(), m.player2())) {
			actions.add(Map.entry(Component.translatable("phantasmon_events.tp.admin.forfeit", player), () -> send(PhantasmonApi.post(
					base + "/result", Map.of("username", player, "forfeit", true), TournamentDto.class), null)));
		}
		if (!"DONE".equals(m.status())) {
			actions.add(Map.entry(Component.translatable("phantasmon_events.tp.admin.reinvite"), () -> send(PhantasmonApi.post(
					base + "/reinvite", Map.of("username", m.player1()), TournamentDto.class),
					Component.translatable("phantasmon_events.tp.done.reinvited", m.player1() + " / " + m.player2()))));
		}
		popup = new Popup(px, py, actions, new boolean[actions.size()]);
	}

	private void renderPopup(GuiGraphics g) {
		MenuUi.Palette c = MenuUi.p();
		int w = 0;
		for (var action : popup.actions()) {
			w = Math.max(w, MenuUi.width(action.getKey()) + 24);
		}
		int h = popup.actions().size() * 17 + 6;
		int px = Math.min(popup.x(), right - w);
		int py = popup.y() + h > bottom ? popup.y() - h - 20 : popup.y();
		g.pose().pushPose();
		g.pose().translate(0, 0, 300);
		g.fill(px - 1, py - 1, px + w + 1, py + h + 1, c.black());
		g.fill(px, py, px + w, py + h, c.panel());
		MenuUi.raised(g, px, py, px + w, py + h);
		g.fill(px, py, px + w, py + 1, c.accent());
		for (int i = 0; i < popup.actions().size(); i++) {
			var action = popup.actions().get(i);
			int by = py + 3 + i * 17;
			Runnable run = () -> {
				popup = null;
				action.getValue().run();
			};
			if (popup.danger()[i]) {
				ui.danger(g, px + 3, by, w - 6, 15, action.getKey(), run);
			} else {
				ui.button(g, px + 3, by, w - 6, 15, action.getKey(), run);
			}
		}
		g.pose().popPose();
	}

	private void matches(GuiGraphics g, TournamentDto t) {
		MenuUi.Palette c = MenuUi.p();
		boolean admin = PhantasmonApi.isAdmin() && "RUNNING".equals(t.status());
		int w = right - x - 8;
		List<TournamentDto.Match> shown;
		int head = 20;
		if (t.swiss()) {
			int last = Math.max(1, t.currentRound());
			round = round <= 0 || round > last ? last : round;
			shown = t.matchesOf(round);
		} else {
			shown = t.currentMatches();
		}
		int columns = w >= 300 ? 2 : 1;
		int cardW = (w - (columns - 1) * 8) / columns;
		int rows = (shown.size() + columns - 1) / columns;
		int top = area(g, head + rows * (BracketView.BH + 8) + 4);
		if (t.swiss()) {
			int last = Math.max(1, t.currentRound());
			ui.mini(g, x, top, Component.literal("<"), false, round > 1 ? () -> round-- : null);
			MenuUi.centered(g, Component.translatable("phantasmon_events.tp.round_of", round, t.plannedRounds() == null ? "?" : t.plannedRounds())
					.withStyle(s -> s.withBold(true)), x + 70, top + 3, 100, c.white());
			ui.mini(g, x + 122, top, Component.literal(">"), false, round < last ? () -> round++ : null);
		} else {
			MenuUi.text(g, Component.translatable("phantasmon_events.tp.wave_of", t.currentRound(), t.plannedRounds() == null ? "?" : t.plannedRounds())
					.withStyle(s -> s.withBold(true)), x, top + 3, 200, c.white());
		}
		if (admin) {
			MenuUi.text(g, Component.translatable("phantasmon_events.tp.admin.click_match"), x + 150, top + 3, w - 150, c.edge());
		}
		if (shown.isEmpty()) {
			MenuUi.text(g, Component.translatable("phantasmon_events.tp.no_matches"), x, top + head + 4, w, c.muted());
		}
		Map<String, UUID> byName = uuids(t);
		String me = me();
		for (int i = 0; i < shown.size(); i++) {
			TournamentDto.Match m = shown.get(i);
			int mx = x + (i % columns) * (cardW + 8);
			int my = top + head + (i / columns) * (BracketView.BH + 8);
			if (t.swiss() && m.player2() == null) {
				// A bye: the player wins the round alone.
				g.fill(mx - 1, my - 1, mx + cardW + 1, my + BracketView.BH + 1, c.black());
				g.fill(mx, my, mx + cardW, my + BracketView.BH, c.dark());
				UUID uuid = byName.get(m.player1());
				if (uuid != null) {
					PhantasmonApi.drawPlayerHead(g, uuid, mx + 6, my + 9, 10);
				}
				MenuUi.text(g, Component.translatable("phantasmon_events.tp.match.bye", m.player1()), mx + 20, my + 10, cardW - 26, c.muted());
				continue;
			}
			BracketView.match(g, m, mx, my, cardW, byName::get, me);
			if (admin && ui.hovered(mx, my, cardW, BracketView.BH)) {
				MenuUi.outline(g, mx - 1, my - 1, mx + cardW + 1, my + BracketView.BH + 1, c.accentHot());
			}
			if (admin) {
				int ax = mx;
				int ay = my + BracketView.BH + 2;
				ui.zone(mx, my, cardW, BracketView.BH, () -> matchPopup(t, m, ax, ay));
			}
		}
		ui.unclip(g);
	}

	private void standings(GuiGraphics g, TournamentDto t) {
		MenuUi.Palette c = MenuUi.p();
		List<TournamentDto.Standing> rows = t.standings() == null ? List.of() : t.standings();
		int w = right - x - 8;
		int top = area(g, 18 + rows.size() * 17 + 4);
		// Columns: rank, player, then the numbers on the right.
		String[] heads = t.swiss() ? new String[] { "points", "record", "buchholz" } : new String[] { "wins", "losses" };
		int numbers = heads.length * 58;
		g.fill(x, top, x + w, top + 14, c.raised());
		MenuUi.text(g, Component.literal("#"), x + 6, top + 3, 20, c.muted());
		MenuUi.text(g, Component.translatable("phantasmon_events.tp.col.player"), x + 30, top + 3, 120, c.muted());
		for (int i = 0; i < heads.length; i++) {
			MenuUi.centered(g, Component.translatable("phantasmon_events.tp.col." + heads[i]), x + w - numbers + i * 58 + 29, top + 3, 56, c.muted());
		}
		Map<String, UUID> byName = uuids(t);
		String me = me();
		for (int i = 0; i < rows.size(); i++) {
			TournamentDto.Standing s = rows.get(i);
			int ry = top + 17 + i * 17;
			g.fill(x, ry, x + w, ry + 16, i % 2 == 0 ? c.panel() : c.dark());
			if (s.username().equals(me)) {
				g.fill(x, ry, x + 2, ry + 16, c.accent());
			}
			int medal = s.rank() == 1 ? 0xFFFFD84A : s.rank() == 2 ? 0xFFD8DEE6 : s.rank() == 3 ? 0xFFE0975A : c.muted();
			MenuUi.text(g, Component.literal(String.valueOf(s.rank())).withStyle(st -> st.withBold(s.rank() <= 3)), x + 6, ry + 4, 22, medal);
			UUID uuid = byName.get(s.username());
			if (uuid != null) {
				PhantasmonApi.drawPlayerHead(g, uuid, x + 30, ry + 2, 12);
			}
			MenuUi.text(g, Component.literal(s.username()), x + 47, ry + 4, w - numbers - 52, s.dropped() ? c.edge() : c.white());
			String[] values = t.swiss() ? new String[] { String.valueOf(s.points()), s.wins() + " - " + s.losses(), String.valueOf(s.buchholz()) }
					: new String[] { String.valueOf(s.wins()), String.valueOf(s.losses()) };
			for (int v = 0; v < values.length; v++) {
				MenuUi.centered(g, Component.literal(values[v]).withStyle(st -> st.withBold(false)), x + w - numbers + v * 58 + 29, ry + 4, 56,
						v == 0 ? c.white() : c.muted());
			}
		}
		if (rows.isEmpty()) {
			MenuUi.text(g, Component.translatable("phantasmon_events.tp.no_standings"), x, top + 22, w, c.muted());
		}
		ui.unclip(g);
	}

	private void bracket(GuiGraphics g, TournamentDto t) {
		MenuUi.Palette c = MenuUi.p();
		BracketView.Layout layout = BracketView.layout(t);
		ui.button(g, right - 100, contentTop - 20, 100, 15, Component.translatable("phantasmon_events.tp.bracket.full_screen"),
				() -> Minecraft.getInstance().setScreen(new BracketScreen(Minecraft.getInstance().screen, selected)));
		int viewW = right - x;
		int viewH = bottom - contentTop;
		panX = Math.max(Math.min(0, viewW - layout.width()), Math.min(0, panX));
		panY = Math.max(Math.min(0, viewH - layout.height()), Math.min(0, panY));
		g.fill(x, contentTop, right, bottom, c.dark());
		ui.clip(g, x, contentTop, right, bottom);
		BracketView.render(g, t, layout, x + 4 + panX, contentTop + 4 + panY, uuids(t)::get, me());
		ui.unclip(g);
		if (layout.width() > viewW || layout.height() > viewH) {
			MenuUi.text(g, Component.translatable("phantasmon_events.tp.bracket.drag"), x + 4, bottom - 10, 260, c.edge());
		}
	}

	private void admin(GuiGraphics g, TournamentDto t) {
		MenuUi.Palette c = MenuUi.p();
		String base = "/admin/tournaments/" + t.name();
		int w = right - x - 8;
		int top = area(g, 200);
		boolean running = "RUNNING".equals(t.status());
		int bw = 128;
		int columns = Math.max(1, (w + 6) / (bw + 6));
		List<Object[]> buttons = new ArrayList<>();
		if (!over(t)) {
			if (!running) {
				buttons.add(new Object[] { "edit", false, (Runnable) () -> {
					form = new TournamentForm(this, t);
					view = View.FORM;
					scroll = 0;
				} });
				if ("REGISTRATION".equals(t.status())) {
					buttons.add(new Object[] { "open_check_in", false, (Runnable) () -> send(PhantasmonApi.post(base + "/check-in", null, TournamentDto.class),
							Component.translatable("phantasmon_events.tp.done.check_in_open")) });
				}
				buttons.add(new Object[] { "start", false, (Runnable) () -> host.confirm(Component.translatable("phantasmon_events.tp.admin.start"),
						List.of(Component.translatable("phantasmon_events.tp.confirm.start")), Component.translatable("phantasmon_events.tp.admin.start"), false,
						() -> send(PhantasmonApi.post(base + "/start", null, TournamentDto.class), Component.translatable("phantasmon_events.tp.done.started"))) });
			} else {
				buttons.add(new Object[] { t.swiss() ? "next_round" : "next_wave", false, (Runnable) () -> send(PhantasmonApi.post(base + "/next-round", null,
						TournamentDto.class), null) });
				if (t.swiss()) {
					buttons.add(new Object[] { "round_plus", false, (Runnable) () -> send(PhantasmonApi.post(base + "/rounds", Map.of("delta", 1), TournamentDto.class), null) });
					buttons.add(new Object[] { "round_minus", false, (Runnable) () -> send(PhantasmonApi.post(base + "/rounds", Map.of("delta", -1), TournamentDto.class), null) });
				}
				buttons.add(new Object[] { "finish", false, (Runnable) () -> host.confirm(Component.translatable("phantasmon_events.tp.admin.finish"),
						List.of(Component.translatable("phantasmon_events.tp.confirm.finish")), Component.translatable("phantasmon_events.tp.admin.finish"), false,
						() -> send(PhantasmonApi.post(base + "/finish", null, TournamentDto.class), null)) });
			}
			buttons.add(new Object[] { "cancel", true, (Runnable) () -> host.confirm(Component.translatable("phantasmon_events.tp.admin.cancel"),
					List.of(Component.translatable("phantasmon_events.tp.confirm.cancel", t.name())), Component.translatable("phantasmon_events.tp.admin.cancel"), true,
					() -> send(PhantasmonApi.post(base + "/cancel", null, TournamentDto.class), null)) });
		} else {
			buttons.add(new Object[] { "delete", true, (Runnable) () -> host.confirm(Component.translatable("phantasmon_events.tp.admin.delete"),
					List.of(Component.translatable("phantasmon_events.tp.confirm.delete", t.name())), Component.translatable("phantasmon_events.tp.admin.delete"), true,
					() -> PhantasmonApi.delete(base, Void.class).thenAccept(ignored -> Minecraft.getInstance().execute(() -> {
						host.closeDialog();
						view = View.LIST;
						selected = null;
						refresh();
					})).exceptionally(this::failed)) });
		}
		for (int i = 0; i < buttons.size(); i++) {
			Object[] b = buttons.get(i);
			int bx = x + (i % columns) * (bw + 6);
			int by = top + (i / columns) * 20;
			Component label = Component.translatable("phantasmon_events.tp.admin." + b[0]);
			if ((Boolean) b[1]) {
				ui.danger(g, bx, by, bw, 15, label, (Runnable) b[2]);
			} else {
				ui.button(g, bx, by, bw, 15, label, (Runnable) b[2]);
			}
		}
		int ny = top + ((buttons.size() + columns - 1) / columns) * 20 + 8;
		// What the dates will do on their own.
		List<Component> notes = new ArrayList<>();
		if ("REGISTRATION".equals(t.status()) && t.checkInOpensAt() != null) {
			notes.add(Component.translatable("phantasmon_events.tp.admin.auto_check_in", when(t.checkInOpensAt())));
		}
		if (("REGISTRATION".equals(t.status()) || "CHECK_IN".equals(t.status())) && t.checkInClosesAt() != null) {
			notes.add(Component.translatable("phantasmon_events.tp.admin.auto_start", when(t.checkInClosesAt())));
		}
		if (running) {
			notes.add(Component.translatable("phantasmon_events.tp.admin.web", "server-linux.tail192969.ts.net/t/" + t.name()));
		}
		for (Component note : notes) {
			ny += MenuUi.paragraph(g, note, x, ny, w, c.muted()) + 4;
		}
		ui.unclip(g);
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean click(double mouseX, double mouseY) {
		if (popup != null) {
			boolean used = ui.click(mouseX, mouseY);
			if (!used) {
				popup = null;
			}
			return true;
		}
		if (view == View.DETAIL && tab == Tab.BRACKET && mouseY >= contentTop && mouseY < bottom && mouseX >= x && mouseX < right) {
			dragX = mouseX;
			dragY = mouseY;
		}
		return ui.click(mouseX, mouseY);
	}

	@Override
	public boolean drag(double mouseX, double mouseY) {
		if (!Double.isNaN(dragX)) {
			panX += (int) Math.round(mouseX - dragX);
			panY += (int) Math.round(mouseY - dragY);
			dragX = mouseX;
			dragY = mouseY;
			return true;
		}
		return false;
	}

	@Override
	public void release() {
		dragX = Double.NaN;
	}

	@Override
	public boolean scroll(double mouseX, double mouseY, double amount) {
		if (view == View.DETAIL && tab == Tab.BRACKET) {
			panY += (int) Math.round(amount * 20);
			return true;
		}
		if (view == View.FORM) {
			form.scroll(amount);
			return true;
		}
		scroll -= (int) Math.round(amount * 20);
		return true;
	}

	@Override
	public boolean charTyped(char chr) {
		return ui.charTyped(chr);
	}

	@Override
	public boolean key(int keyCode, boolean control) {
		if (popup != null && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			popup = null;
			return true;
		}
		return ui.key(keyCode, control);
	}

	// ---------------------------------------------------------------- for the form

	MenuHost host() {
		return host;
	}

	/** The form is done (created, saved or cancelled): back to the tournament, or the list. */
	void formDone(String name) {
		form = null;
		if (name != null) {
			selected = name;
			view = View.DETAIL;
			tab = Tab.PLAYERS;
		} else {
			view = selected != null && current() != null ? View.DETAIL : View.LIST;
		}
		scroll = 0;
		refresh();
	}

	/**
	 * A guided date and time into an instant (local time; no time: midnight); null if the date is empty.
	 * {@link java.time.DateTimeException} for a day or an hour that does not exist (31-02, 25:00).
	 */
	static Instant instant(Ui.Field date, Ui.Field time) {
		if (date.value.length() != 8) {
			return null;
		}
		String d = date.value;
		String clock = time.value.length() == 4 ? time.value : "0000";
		return LocalDateTime.of(Integer.parseInt(d.substring(4)), Integer.parseInt(d.substring(2, 4)), Integer.parseInt(d.substring(0, 2)),
				Integer.parseInt(clock.substring(0, 2)), Integer.parseInt(clock.substring(2))).atZone(ZoneId.systemDefault()).toInstant();
	}

	/** An instant into the guided fields' digits ({@code ddMMyyyy}, {@code HHmm}). */
	static String[] digits(String iso) {
		if (iso == null) {
			return new String[] { "", "" };
		}
		var local = Instant.parse(iso).atZone(ZoneId.systemDefault());
		return new String[] { DateTimeFormatter.ofPattern("ddMMyyyy").format(local), DateTimeFormatter.ofPattern("HHmm").format(local) };
	}
}
