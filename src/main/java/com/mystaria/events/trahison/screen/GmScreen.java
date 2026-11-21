package com.mystaria.events.trahison.screen;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.resources.language.I18n;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.hud.BraceletHud;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Bracelet;
import com.mystaria.events.trahison.net.GameView.Gm;
import com.mystaria.events.trahison.net.GmData;
import com.mystaria.events.trahison.net.Secrets;
import com.mystaria.events.trahison.room.RoomView;
import com.mystaria.events.trahison.room.Rooms;
import com.mystaria.events.trahison.net.GameView.GmVote;
import com.mystaria.events.trahison.net.GameView.Member;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.events.trahison.net.Messages;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The provisional game master screen (step T1.4), for Phantasmon's admins and the game's game masters
 * ({@code /phantasmon event trahison gm}), in the charter's game-master tint: create the game of the hub, in its lobby add
 * dummy players with a vote policy and give the game master role to people (D7: none, one or several; admins only), start,
 * pause, resume, next step, extend, end, correct a bracelet's points, and read what only a game master sees (the votes
 * cast so far, the seed, the dummy players' policy). The backend decides: once the game runs only its assigned game
 * masters may act, and it refuses the others. Client thread.
 */
public final class GmScreen extends TrahisonScreen {

	private static final String T = "phantasmon_events.trahison.gm.";
	private static final String G = "phantasmon_events.trahison.";
	private static final String[] POLICIES = { "ally", "betray", "random" };
	private static final int ROW_PITCH = 20;
	private static final int EXTEND_SECONDS = 60;

	/** The pages of the right side (T7.4). */
	private enum Tab {
		PLAYERS, ROOMS, VOTES, LOG, OPTIONS
	}

	/** The filters of the journal. */
	private enum Filter {
		ALL, VOTES, ROOMS, ITEMS, DEATHS, TEMPTATIONS, GM
	}

	private static final Map<String, Filter> LOG_KINDS = new LinkedHashMap<>();

	static {
		for (String t : List.of("vote", "results", "close_cabin", "cabin_enter", "cabin_locked", "cabin_released", "cabin_free", "bet", "foresee")) {
			LOG_KINDS.put(t, Filter.VOTES);
		}
		for (String t : List.of("room_solved", "rooms_timeout", "gm_hint", "gm_solve_room")) {
			LOG_KINDS.put(t, Filter.ROOMS);
		}
		for (String t : List.of("item_found", "item_used", "untied", "unlock", "letter", "shield", "roles")) {
			LOG_KINDS.put(t, Filter.ITEMS);
		}
		for (String t : List.of("killed", "revived", "bracelet_taken", "body_seen", "game_master_killed", "doors_closed")) {
			LOG_KINDS.put(t, Filter.DEATHS);
		}
		for (String t : List.of("offer", "offer_taken", "leak", "race")) {
			LOG_KINDS.put(t, Filter.TEMPTATIONS);
		}
		for (String t : List.of("gm_whisper", "gm_say", "set_points", "set_door", "pause", "resume", "extend", "advanced", "redraw", "replace_room",
				"bot_policy", "settings", "game_master")) {
			LOG_KINDS.put(t, Filter.GM);
		}
	}

	private static final int LOG_ROWS = 120;
	private static final List<String> ROLES = List.of("INFILTRATOR", "FERRYMAN", "GUARDIAN", "SEER", "GAMBLER", "LONER");
	private static final List<String> ITEMS = List.of("KNIFE", "BONDS", "LOCK", "EAR", "SHIELD", "PASS", "LETTER");

	private Tab tab = Tab.PLAYERS;
	private Filter filter = Filter.ALL;
	private int selected = -1;
	private boolean gameMasterMode;
	private boolean fast;
	private String policy = "random";
	private int leftScroll;
	private int leftContent;
	private int rightScroll;
	private int rightContent;
	private int rightX;

	private GmScreen() {
		super(Component.translatable(T + "title"), TrahisonPalette.GAME_MASTER);
	}

	/** Opens the screen; refused (a chat line) for someone who is neither an admin nor a game master of the game. */
	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (!PhantasmonApi.isAdmin() && GameView.orEmpty().viewer().kind() != ViewerKind.GM
				&& !GameView.orEmpty().viewer().isGameMaster()) {
			mc.player.displayClientMessage(Component.translatable(T + "not_admin"), false);
			return;
		}
		DrawPanel.install();
		mc.setScreen(new GmScreen());
	}

	@Override
	protected void drawStage(GuiGraphics g, int mouseX, int mouseY) {
		View v = GameView.orEmpty();
		int[] r = TrahisonUi.stage(width, height, 480, 280);
		int x = r[0];
		int y = r[1];
		int w = r[2];
		int h = r[3];
		TrahisonUi.titledPanel(g, x, y, w, h, title, TrahisonPalette.GAME_MASTER);
		Component hub = Link.hub() == null ? Component.translatable(T + "no_hub") : Component.literal(Link.hub());
		int pw = TrahisonUi.width(hub) + 10;
		TrahisonUi.pill(g, x + w - 6 - pw, y + 4, hub, TrahisonPalette.GAME_MASTER_DARK, TrahisonPalette.GAME_MASTER);
		int top = y + 28;
		int bottom = y + h - 8;
		int lw = w >= 400 ? 164 : w >= 340 ? 152 : 128;
		int lx = x + 8;
		rightX = lx + lw + 8;
		drawControls(g, v, lx, top, lw, bottom - top);
		drawRight(g, v, rightX, top, x + w - 8 - rightX, bottom - top);
	}

	// ---------------------------------------------------------------- left: status and controls

	private void drawControls(GuiGraphics g, View v, int x, int y, int w, int h) {
		TrahisonUi.panel(g, x - 2, y - 2, w + 4, h + 4, TrahisonPalette.GAME_MASTER);
		int ix = x + 4;
		int iw = w - 8;
		int top = y + 4;
		int bottom = y + h - 2;
		leftScroll = clamp(leftScroll, leftContent - (bottom - top));
		ui.clip(g, x - 1, y, x + w + 1, bottom);
		int cy = top - leftScroll;
		int used = 0;
		Gm gm = v.gm();
		if (v.hasGame()) {
			TrahisonUi.textEllipsis(g, BraceletHud.phaseLine(v), ix, cy, iw, TrahisonPalette.TEXT);
			used += 12;
			long remaining = GameView.remainingMs();
			int px = ix;
			if (remaining >= 0) {
				Component t = BraceletHud.time(remaining);
				TrahisonUi.bigText(g, t, px, cy + used, 2f, v.paused() ? TrahisonPalette.TEXT_MUTED : TrahisonPalette.TEXT);
				px += TrahisonUi.width(t) * 2 + 6;
			}
			if (v.paused()) {
				px += TrahisonUi.pill(g, px, cy + used + 2, Component.translatable(G + (v.autoPaused() ? "hud.waiting" : "hud.paused")),
						TrahisonPalette.GAME_MASTER_DARK, TrahisonPalette.GAME_MASTER) + 4;
			}
			TrahisonUi.pill(g, px, cy + used + 2, Component.translatable(T + "mode." + v.mode().toLowerCase(Locale.ROOT)), TrahisonPalette.PANEL_SUNKEN,
					TrahisonPalette.TEXT_SECONDARY);
			used += 20;
			if (gm != null) {
				used += info(g, ix, cy + used, iw, Component.translatable(T + "seed", Long.toString(gm.seed())));
				used += info(g, ix, cy + used, iw, Component.translatable(T + "bot_policy", policyName(gm.botPolicy().toLowerCase(Locale.ROOT))));
			}
			used += 4;
		}
		used += drawActions(g, v, ix, cy + used, iw);
		ui.unclip(g);
		leftContent = used + 8;
	}

	private int info(GuiGraphics g, int x, int y, int w, Component text) {
		TrahisonUi.textEllipsis(g, text, x, y, w, TrahisonPalette.TEXT_MUTED);
		return 12;
	}

	private int drawActions(GuiGraphics g, View v, int x, int y, int w) {
		int bh = TrahisonUi.BUTTON_H;
		int half = (w - 4) / 2;
		int used = 0;
		Phase phase = v.phase();
		if (!v.hasGame() || phase == Phase.ENDED) {
			ui.button(g, x, y, w, bh, Component.translatable(T + "mode_button", Component.translatable(T + "mode." + (gameMasterMode ? "game_master" : "auto"))),
					ButtonKind.DEFAULT, Component.translatable(T + "mode_button.tip"), () -> gameMasterMode = !gameMasterMode);
			used += bh + 4;
			ui.button(g, x, y + used, w, bh, Component.translatable(T + (fast ? "fast_on" : "fast_off")), ButtonKind.DEFAULT,
					Component.translatable(T + "fast.tip"), () -> fast = !fast);
			used += bh + 8;
			boolean hub = Link.hub() != null;
			ui.button(g, x, y + used, w, bh, Component.translatable(T + "create"), ButtonKind.PRIMARY, hub ? null : Component.translatable(T + "no_hub.tip"),
					hub ? () -> Messages.create(gameMasterMode ? "GAME_MASTER" : "AUTO", fast) : null);
			return used + bh;
		}
		if (!isGameMaster(v) && phase != Phase.LOBBY) {
			return MenuUi.paragraph(g, Component.translatable(T + "not_game_master"), x, y, w, TrahisonPalette.TEXT_MUTED);
		}
		if (phase == Phase.LOBBY) {
			ui.button(g, x, y, half, bh, Component.translatable(T + "bot_one"), ButtonKind.DEFAULT, null, () -> Messages.addBots(1, currentPolicy(v)));
			ui.button(g, x + half + 4, y, half, bh, Component.translatable(T + "bot_fill"), ButtonKind.DEFAULT, Component.translatable(T + "bot_fill.tip"),
					() -> Messages.addBots(0, currentPolicy(v)));
			used += bh + 4;
			used += policyButton(g, v, x, y + used, w);
			used += 4;
			ui.button(g, x, y + used, w, bh, Component.translatable(T + "export"), ButtonKind.DEFAULT, Component.translatable(T + "export.tip"),
					com.mystaria.events.trahison.edit.ExportFlow::exportDrawn);
			used += bh + 4;
			ui.button(g, x, y + used, w, bh, Component.translatable(G + "check.button"), ButtonKind.DEFAULT, Component.translatable(G + "check.button.tip"),
					com.mystaria.events.trahison.edit.PlaceCheck::run);
			used += bh + 4;
			ui.button(g, x, y + used, w, bh, Component.translatable(T + "start"), ButtonKind.PRIMARY, Component.translatable(T + "start.tip"), Messages::start);
			used += bh + 4;
			ui.button(g, x, y + used, w, bh, Component.translatable(T + "end"), ButtonKind.DANGER, null, this::askEnd);
			return used + bh;
		}
		ui.button(g, x, y, half, bh, Component.translatable(T + (v.paused() ? "resume" : "pause")), ButtonKind.PRIMARY, null, v.paused() ? Messages::resume : Messages::pause);
		ui.button(g, x + half + 4, y, half, bh, Component.translatable(T + "next"), ButtonKind.DEFAULT, Component.translatable(T + "next.tip"), Messages::next);
		used += bh + 4;
		ui.button(g, x, y + used, half, bh, Component.translatable(T + "extend", EXTEND_SECONDS), ButtonKind.DEFAULT, null, () -> Messages.extend(EXTEND_SECONDS));
		ui.button(g, x + half + 4, y + used, half, bh, Component.translatable(T + "shorten", EXTEND_SECONDS), ButtonKind.DEFAULT, null, () -> Messages.extend(-EXTEND_SECONDS));
		used += bh + 4;
		used += policyButton(g, v, x, y + used, w);
		used += 4;
		ui.button(g, x, y + used, w, bh, Component.translatable(T + "say"), ButtonKind.DEFAULT, Component.translatable(T + "say.tip"),
				() -> chat("/phantasmon event trahison say "));
		used += bh + 4;
		ui.button(g, x, y + used, w, bh, Component.translatable(T + "end"), ButtonKind.DANGER, null, this::askEnd);
		return used + bh;
	}

	/** Opens the chat with a command started: the words of a game master are typed there (the screen has no text field). */
	private static void chat(String start) {
		Minecraft mc = Minecraft.getInstance();
		mc.tell(() -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(start)));
	}

	/** The viewer is one of the game's game masters (the backend lets only them act once the game runs). */
	private static boolean isGameMaster(View v) {
		return v.viewer().kind() == ViewerKind.GM || v.viewer().isGameMaster(); // D12: a game master may also play
	}

	/** The policy of the dummy players: the game's own when the view tells it, else the last chosen here. */
	private String currentPolicy(View v) {
		return v.gm() != null ? v.gm().botPolicy().toLowerCase(Locale.ROOT) : policy;
	}

	private int policyButton(GuiGraphics g, View v, int x, int y, int w) {
		String current = currentPolicy(v);
		ui.button(g, x, y, w, TrahisonUi.BUTTON_H, Component.translatable(T + "policy_button", policyName(current)), ButtonKind.DEFAULT,
				Component.translatable(T + "policy_button.tip"), () -> {
					int next = 0;
					for (int i = 0; i < POLICIES.length; i++) {
						if (POLICIES[i].equals(current)) {
							next = (i + 1) % POLICIES.length;
						}
					}
					policy = POLICIES[next];
					Messages.setBotPolicy(policy);
				});
		return TrahisonUi.BUTTON_H;
	}

	private static Component policyName(String policy) {
		return Component.translatable(T + "policy." + policy);
	}

	private void askEnd() {
		modal = new Modal(Component.translatable(T + "confirm_end.title"), Component.translatable(T + "confirm_end.text"),
				Component.translatable(T + "end"), true, Messages::end);
	}

	// ---------------------------------------------------------------- right: the tabs

	private void drawRight(GuiGraphics g, View v, int x, int y, int w, int h) {
		if (!v.hasGame()) {
			MenuUi.paragraph(g, Component.translatable(T + "nothing"), x, y, w, TrahisonPalette.TEXT_MUTED);
			return;
		}
		Tab[] tabs = Tab.values();
		int tw = w / tabs.length;
		for (int i = 0; i < tabs.length; i++) {
			Tab t = tabs[i];
			int tx = x + i * tw;
			int width = i == tabs.length - 1 ? w - i * tw : tw - 2;
			TrahisonUi.tab(g, tx, y + 1, width, 14, Component.translatable(T + "tab." + t.name().toLowerCase(Locale.ROOT)), t == tab, ui.hovered(tx, y + 1, width, 14));
			ui.zone(tx, y + 1, width, 14, b -> {
				tab = t;
				rightScroll = 0;
			});
		}
		int cy = y + 18;
		int ch = h - 18;
		TrahisonUi.well(g, x, cy, w, ch);
		rightScroll = clamp(rightScroll, rightContent - (ch - 4));
		ui.clip(g, x + 1, cy + 1, x + w - 1, cy + ch - 1);
		boolean bar = rightContent > ch - 4;
		int rw = w - 8 - (bar ? 4 : 0);
		int ry = cy + 4 - rightScroll;
		int used = switch (tab) {
			case PLAYERS -> v.phase() == Phase.LOBBY ? lobbyMasters(g, v, x + 4, ry, rw) : playersTab(g, v, x + 4, ry, rw);
			case ROOMS -> roomsTab(g, v, x + 4, ry, rw);
			case VOTES -> votesTab(g, v, x + 4, ry, rw);
			case LOG -> logTab(g, v, x + 4, ry, rw);
			case OPTIONS -> optionsTab(g, v, x + 4, ry, rw);
		};
		ui.unclip(g);
		rightContent = used + 8;
		if (bar) {
			int[] thumb = MenuUi.thumb(cy + 1, ch - 2, rightContent + 4, rightScroll);
			if (thumb != null) {
				g.fill(x + w - 4, thumb[0], x + w - 2, thumb[0] + thumb[1], TrahisonPalette.EDGE);
				bars.add(x + w - 4, cy + 1, ch - 2, rightScroll, rightContent + 4, 16, s -> rightScroll = s);
			}
		}
	}

	private static String lower(String s) {
		return s.toLowerCase(Locale.ROOT);
	}

	private static Component roleName(String role) {
		return Component.translatable(G + "role." + lower(role));
	}

	private static Component itemName(String item) {
		return Component.translatable(G + "item." + lower(item));
	}

	private int heading(GuiGraphics g, String key, int x, int y, int w) {
		MenuUi.text(g, Component.translatable(T + key), x, y, w, TrahisonPalette.GAME_MASTER);
		return 14;
	}

	// ---- players

	/** Every player at a glance (points, colour, zone, role, items, state), and the gestures on the one selected. */
	private int playersTab(GuiGraphics g, View v, int x, int y, int w) {
		int used = heading(g, "players", x, y, w);
		boolean voting = v.phase() == Phase.VOTE;
		for (Bracelet b : v.bracelets()) {
			int i = b.index();
			boolean sel = i == selected;
			int rowH = 30;
			boolean hover = ui.hovered(x, y + used, w, rowH);
			TrahisonUi.row(g, x, y + used, w, rowH, hover, sel);
			ui.zone(x, y + used, w, rowH, btn -> selected = sel ? -1 : i);
			drawPlayerRow(g, v, b, x, y + used, w);
			used += rowH + 2;
			if (sel) {
				used += drawPlayerActions(g, v, i, voting, x, y + used, w);
			}
		}
		return used;
	}

	private void drawPlayerRow(GuiGraphics g, View v, Bracelet b, int x, int y, int w) {
		int i = b.index();
		GameView.Player pl = v.player(i);
		boolean ghost = pl != null && !pl.alive();
		if (b.color() != null) {
			TrahisonUi.colorGlyph(g, b.color(), x + 5, y + 4, 8);
			ui.tip(x + 4, y + 3, 10, 12, Component.translatable(G + "bracelet.tip", Shown.colorName(b.color()), Shown.side(b.pair()), b.points()));
		}
		int plus = x + w - 18;
		int minus = plus - 18;
		Component points = Component.literal(Integer.toString(b.points()));
		int pw = Math.max(TrahisonUi.width(points), 12);
		int px = minus - 6 - pw;
		TrahisonUi.textEllipsis(g, Component.literal(v.nameOf(i)), x + 18, y + 4, px - (x + 18) - 4, ghost ? TrahisonPalette.GHOST : TrahisonPalette.TEXT);
		MenuUi.text(g, points, px + pw - TrahisonUi.width(points), y + 4, 24, TrahisonPalette.TEXT);
		ui.button(g, minus, y + 1, 16, 14, Component.literal("-"), () -> Messages.setPoints(i, b.points() - 1));
		ui.button(g, plus, y + 1, 16, 14, Component.literal("+"), () -> Messages.setPoints(i, b.points() + 1));
		int cx = x + 4;
		int py = y + 16;
		int limit = x + w - 4;
		String role = GmData.role(i);
		if (role != null) {
			cx += pill(g, cx, py, roleName(role), TrahisonPalette.GAME_MASTER_DARK, TrahisonPalette.GAME_MASTER, limit,
					Component.translatable(G + "role.desc." + lower(role)));
		}
		List<String> carried = GmData.carried(i);
		if (!carried.isEmpty()) {
			List<Component> names = new ArrayList<>();
			carried.forEach(it -> names.add(itemName(it)));
			cx += pill(g, cx, py, Component.translatable(T + "items_count", carried.size()), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_SECONDARY, limit,
					Component.literal(String.join(", ", names.stream().map(Component::getString).toList())));
		}
		String zone = zoneOf(v, i);
		if (zone != null) {
			cx += pill(g, cx, py, Component.literal(zone), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_MUTED, limit, Component.translatable(T + "zone.tip"));
		}
		if (ghost) {
			cx += pill(g, cx, py, Component.translatable(T + "state.dead"), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.GHOST, limit, null);
		}
		if (tied(i)) {
			cx += pill(g, cx, py, Component.translatable(T + "state.tied"), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.DANGER, limit, null);
		}
		if (pl != null && !pl.connected() && !pl.bot()) {
			cx += pill(g, cx, py, Component.translatable(T + "state.offline"), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_MUTED, limit, null);
		}
		if (pl != null && pl.bot()) {
			cx += pill(g, cx, py, Component.translatable(T + "state.bot"), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_MUTED, limit, null);
		}
		if (GmData.offer().seat() == i && GmData.offer().made()) {
			pill(g, cx, py, Component.translatable(T + "state.offer"), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.ALLY, limit, Component.translatable(T + "state.offer.tip"));
		}
	}

	/** A pill that is skipped (width 0) when it no longer fits in the row. */
	private int pill(GuiGraphics g, int x, int y, Component label, int fill, int text, int limit, Component tip) {
		int width = TrahisonUi.width(label) + 10;
		if (x + width > limit) {
			return 0;
		}
		TrahisonUi.pill(g, x, y, label, fill, text);
		if (tip != null) {
			ui.tip(x, y, width, 12, tip);
		}
		return width + 3;
	}

	private static String zoneOf(View v, int seat) {
		if (v.gm() == null) {
			return null;
		}
		for (GameView.GmZone z : v.gm().zones()) {
			if (z.index() == seat) {
				return z.zone();
			}
		}
		return null;
	}

	private static boolean tied(int seat) {
		for (Secrets.Rope r : Secrets.ropes()) {
			if (r.player() == seat) {
				return true;
			}
		}
		return false;
	}

	private int drawPlayerActions(GuiGraphics g, View v, int seat, boolean voting, int x, int y, int w) {
		GameView.Player pl = v.player(seat);
		boolean alive = pl != null && pl.alive();
		boolean bot = pl != null && pl.bot();
		int bw = (w - 8) / 3;
		int bh = TrahisonUi.BUTTON_H;
		ui.button(g, x, y, bw, bh, Component.translatable(T + "whisper"), ButtonKind.DEFAULT, Component.translatable(T + "whisper.tip"),
				bot ? null : () -> chat("/phantasmon event trahison whisper " + seat + " "));
		boolean canOffer = voting && alive && !bot;
		ui.button(g, x + bw + 4, y, bw, bh, Component.translatable(T + "offer"), ButtonKind.DEFAULT,
				Component.translatable(T + (canOffer ? "offer.tip" : "offer.tip_off")), canOffer ? () -> Messages.offer(seat) : null);
		if (alive) {
			ui.button(g, x + 2 * (bw + 4), y, bw, bh, Component.translatable(T + "kill"), ButtonKind.DANGER, Component.translatable(T + "kill.tip"),
					() -> modal = new Modal(Component.translatable(T + "kill.title", v.nameOf(seat)), Component.translatable(T + "kill.text"),
							Component.translatable(T + "kill"), true, () -> Messages.kill(seat)));
		} else {
			ui.button(g, x + 2 * (bw + 4), y, bw, bh, Component.translatable(T + "revive"), ButtonKind.PRIMARY, Component.translatable(T + "revive.tip"),
					() -> Messages.revive(seat));
		}
		return bh + 4;
	}

	// ---- lobby: who runs the game (D7)

	/** D7: who runs the game. The game masters first, then the people around who are not playing; an admin gives or takes the role with a click. */
	private int lobbyMasters(GuiGraphics g, View v, int x, int y, int w) {
		int used = 0;
		GameView.Lobby lobby = GameView.lobby();
		MenuUi.text(g, Component.translatable(T + "masters", lobby.gameMasters().size()), x, y + used, w, TrahisonPalette.GAME_MASTER);
		used += 14;
		boolean admin = PhantasmonApi.isAdmin();
		if (lobby.gameMasters().isEmpty()) {
			used += MenuUi.paragraph(g, Component.translatable(T + (v.mode().equals("GAME_MASTER") ? "masters.none_needed" : "masters.none")), x, y + used, w,
					TrahisonPalette.TEXT_MUTED) + 4;
		}
		List<Member> rows = new ArrayList<>(lobby.gameMasters());
		for (Member m : lobby.members()) {
			if (!lobby.isGameMaster(m.uuid())) {
				rows.add(m);
			}
		}
		if (rows.isEmpty()) {
			used += MenuUi.paragraph(g, Component.translatable(T + "masters.nobody"), x, y + used, w, TrahisonPalette.TEXT_MUTED) + 4;
		}
		for (Member m : rows) {
			boolean master = lobby.isGameMaster(m.uuid());
			int rowY = y + used;
			TrahisonUi.row(g, x, rowY, w, 18, ui.hovered(x, rowY, w, 18), false);
			int bw = 74;
			TrahisonUi.textEllipsis(g, Component.literal(m.name()), x + 6, rowY + 5, w - bw - 14, master ? TrahisonPalette.GAME_MASTER : TrahisonPalette.TEXT);
			Component label = Component.translatable(T + (master ? "master_remove" : "master_add"));
			ui.button(g, x + w - bw - 2, rowY + 1, bw, 16, label, master ? ButtonKind.DANGER : ButtonKind.DEFAULT,
					admin ? Component.translatable(T + (master ? "master_remove.tip" : "master_add.tip")) : Component.translatable(T + "master_admin_only"),
					admin ? () -> Messages.setGameMaster(m.uuid(), !master) : null);
			used += ROW_PITCH;
		}
		return used;
	}

	// ---- rooms

	private int roomsTab(GuiGraphics g, View v, int x, int y, int w) {
		int used = 0;
		if (v.phase() != Phase.LOBBY) {
			used += heading(g, "rooms", x, y, w);
			boolean rooming = v.phase() == Phase.ROOMS;
			for (int k = 0; k < 3; k++) {
				used += drawRoomCard(g, v, k, rooming, x, y + used, w) + 4;
			}
			used += 4;
		}
		used += DrawPanel.draw(g, ui, v, x, y + used, w);
		return used;
	}

	private int drawRoomCard(GuiGraphics g, View v, int group, boolean rooming, int x, int y, int w) {
		RoomView room = Rooms.all().get(v.round() * 10 + group);
		int h = 44;
		TrahisonUi.panel(g, x, y, w, h);
		Component title = room == null ? Component.translatable(T + "room.none", group + 1)
				: Component.translatable(T + "room.title", group + 1, room.name() == null ? room.room() : room.name());
		TrahisonUi.textEllipsis(g, title, x + 6, y + 4, w - 12, TrahisonPalette.TEXT);
		if (room == null) {
			return h;
		}
		StringBuilder who = new StringBuilder();
		for (int m : room.members()) {
			who.append(who.length() == 0 ? "" : ", ").append(v.nameOf(m));
		}
		TrahisonUi.textEllipsis(g, Component.literal(who.toString()), x + 6, y + 16, w - 12, TrahisonPalette.TEXT_MUTED);
		int cx = x + 6;
		Component state = Component.translatable(T + (room.exitOpen() ? "room.done" : "room.playing"));
		cx += TrahisonUi.pill(g, cx, y + 29, state, TrahisonPalette.PANEL_SUNKEN, room.exitOpen() ? TrahisonPalette.SUCCESS : TrahisonPalette.ACCENT) + 3;
		cx += TrahisonUi.pill(g, cx, y + 29, Component.translatable(T + "room.hints", room.hints().size()), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_SECONDARY) + 3;
		if (room.secret() != null) { // D228: the live hiding place of the free time, among the room's candidates
			String lying = GmData.hiddenAt("room:" + room.round() + ":" + room.group() + ":" + room.secret()); // the object that lies there, if any
			Component pill = lying == null ? Component.translatable(T + "room.secret", room.secret())
					: Component.translatable(T + "room.secret.item", room.secret(), itemName(lying));
			TrahisonUi.pill(g, cx, y + 29, pill, TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.GAME_MASTER);
		}
		boolean live = rooming && !room.exitOpen();
		int bw = 52;
		ui.button(g, x + w - 2 * bw - 10, y + 26, bw, 14, Component.translatable(T + "room.hint"), ButtonKind.DEFAULT,
				Component.translatable(T + (live ? "room.hint.tip" : "room.off")), live ? () -> Messages.hint(group) : null);
		ui.button(g, x + w - bw - 6, y + 26, bw, 14, Component.translatable(T + "room.solve"), ButtonKind.DANGER,
				Component.translatable(T + (live ? "room.solve.tip" : "room.off")), live ? () -> Messages.solveRoom(group) : null);
		return h;
	}

	// ---- votes

	private int votesTab(GuiGraphics g, View v, int x, int y, int w) {
		int used = heading(g, "votes", x, y, w);
		Gm gm = v.gm();
		if (gm == null || gm.votes().isEmpty()) {
			used += MenuUi.paragraph(g, Component.translatable(T + "votes.none"), x, y + used, w, TrahisonPalette.TEXT_MUTED);
		} else {
			for (GmVote vote : gm.votes()) {
				drawVoteRow(g, v, vote, x, y + used, w);
				used += 30;
			}
		}
		used += 6;
		GmData.Offer offer = GmData.offer();
		Component line = offer.seat() < 0 ? Component.translatable(T + "offer.none")
				: Component.translatable(T + (offer.made() ? "offer.made" : "offer.pending"), v.nameOf(offer.seat()));
		TrahisonUi.textEllipsis(g, line, x, y + used, w, offer.made() ? TrahisonPalette.ALLY : TrahisonPalette.TEXT_MUTED);
		return used + 12;
	}

	private void drawVoteRow(GuiGraphics g, View v, GmVote vote, int x, int y, int w) {
		TrahisonUi.panel(g, x, y, w, 26);
		MenuUi.text(g, Component.translatable(G + "door", vote.door() + 1), x + 6, y + 9, 38, TrahisonPalette.TEXT_SECONDARY);
		int px = x + 6 + 40;
		MenuUi.text(g, Shown.side(true), px, y + 3, 28, TrahisonPalette.TEXT_MUTED);
		MenuUi.text(g, Shown.side(false), px, y + 15, 28, TrahisonPalette.TEXT_MUTED);
		int pillX = px + 30;
		int used = gmVotePill(g, pillX, y + 2, vote.pairVote());
		if (vote.pairVoter() != null) {
			TrahisonUi.textEllipsis(g, Component.literal(v.nameOf(vote.pairVoter())), pillX + used + 4, y + 3, x + w - 4 - (pillX + used + 4), TrahisonPalette.TEXT_MUTED);
		}
		gmVotePill(g, pillX, y + 14, vote.soloVote());
	}

	/** A vote cast (symbol and name), or a muted "..." when not yet. Returns its width. */
	private int gmVotePill(GuiGraphics g, int x, int y, GameView.Vote vote) {
		if (vote == null) {
			return TrahisonUi.pill(g, x, y, Component.translatable(T + "vote_pending"), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_MUTED);
		}
		return Shown.votePill(g, x, y, vote, true);
	}

	// ---- journal

	private int logTab(GuiGraphics g, View v, int x, int y, int w) {
		int used = 0;
		int cx = x;
		int cyRow = y;
		for (Filter f : Filter.values()) {
			Component label = Component.translatable(T + "filter." + lower(f.name()));
			int fw = TrahisonUi.width(label) + 10;
			if (cx + fw > x + w) {
				cx = x;
				cyRow += 16;
			}
			TrahisonUi.tab(g, cx, cyRow, fw, 12, label, f == filter, ui.hovered(cx, cyRow, fw, 12));
			ui.zone(cx, cyRow, fw, 12, b -> {
				filter = f;
				rightScroll = 0;
			});
			cx += fw + 3;
		}
		used = cyRow - y + 18;
		List<GmData.Entry> shown = new ArrayList<>();
		List<GmData.Entry> log = GmData.log();
		for (int i = log.size() - 1; i >= 0 && shown.size() < LOG_ROWS; i--) {
			GmData.Entry e = log.get(i);
			if (filter == Filter.ALL || LOG_KINDS.get(e.type()) == filter) {
				shown.add(e);
			}
		}
		if (shown.isEmpty()) {
			return used + MenuUi.paragraph(g, Component.translatable(T + "log.none"), x, y + used, w, TrahisonPalette.TEXT_MUTED);
		}
		for (GmData.Entry e : shown) {
			int tw = 40;
			MenuUi.text(g, Component.literal(LocalTime.ofInstant(Instant.ofEpochMilli(e.at()), ZoneId.systemDefault()).withNano(0).toString()), x, y + used, tw + 8,
					TrahisonPalette.TEXT_MUTED);
			Component text = summary(v, e);
			TrahisonUi.textEllipsis(g, text, x + tw + 10, y + used, w - tw - 10, tone(e.type()));
			ui.tip(x, y + used - 1, w, 11, text);
			used += 12;
		}
		return used;
	}

	private static int tone(String type) {
		Filter f = LOG_KINDS.get(type);
		return f == Filter.GM ? TrahisonPalette.GAME_MASTER : f == Filter.DEATHS ? TrahisonPalette.DANGER
				: f == Filter.TEMPTATIONS ? TrahisonPalette.ALLY : TrahisonPalette.TEXT_SECONDARY;
	}

	private static final Set<String> SEAT_KEYS = Set.of("player", "by", "about", "victim", "bracelet", "body");

	/** One line for a log entry: its name (translated when we have one) and its data, seats shown by name. */
	private static Component summary(View v, GmData.Entry e) {
		String key = T + "log." + e.type();
		String head = I18n.exists(key) ? I18n.get(key) : e.type();
		StringBuilder sb = new StringBuilder(head);
		e.data().forEach((k, val) -> {
			if (val instanceof Map<?, ?> || val instanceof List<?> && ((List<?>) val).size() > 4) {
				return;
			}
			String shown = String.valueOf(val);
			if (SEAT_KEYS.contains(k) && val instanceof Number n) {
				shown = v.nameOf(n.intValue());
			}
			sb.append(" | ").append(k).append(": ").append(shown);
		});
		return Component.literal(sb.toString());
	}

	// ---- settings

	private int optionsTab(GuiGraphics g, View v, int x, int y, int w) {
		boolean lobby = v.phase() == Phase.LOBBY;
		GmData.Options o = GmData.options();
		int used = heading(g, "options", x, y, w);
		if (!lobby) {
			used += MenuUi.paragraph(g, Component.translatable(T + "options.locked"), x, y + used, w, TrahisonPalette.TEXT_MUTED) + 4;
		}
		used += toggle(g, "offers", o.offers(), lobby, x, y + used, w);
		used += toggle(g, "leaks", o.leaks(), lobby, x, y + used, w);
		used += toggle(g, "race", o.race(), lobby, x, y + used, w);
		used += toggle(g, "announce_deaths", o.announceDeaths(), lobby, x, y + used, w);
		used += 4;
		used += counter(g, Component.translatable(T + "role_count"), o.roleCount(), 0, 6, lobby, x, y + used, w,
				n -> Messages.setSettings(Map.of("role_count", n)));
		int cx = x;
		int cyRow = y + used;
		for (String role : ROLES) {
			boolean on = o.roles().contains(role);
			Component label = roleName(role);
			int fw = TrahisonUi.width(label) + 10;
			if (cx + fw > x + w) {
				cx = x;
				cyRow += 16;
			}
			TrahisonUi.tab(g, cx, cyRow, fw, 12, label, on, lobby && ui.hovered(cx, cyRow, fw, 12));
			ui.tip(cx, cyRow, fw, 12, Component.translatable(G + "role.desc." + lower(role)));
			if (lobby) {
				ui.zone(cx, cyRow, fw, 12, b -> {
					List<String> next = new ArrayList<>(o.roles());
					if (on) {
						next.remove(role);
					} else {
						next.add(role);
					}
					Messages.setSettings(Map.of("roles", next));
				});
			}
			cx += fw + 3;
		}
		used = cyRow - y + 20;
		used += heading(g, "items", x, y + used, w);
		for (String item : ITEMS) {
			used += counter(g, itemName(item), o.items().getOrDefault(item, 0), 0, 2, lobby, x, y + used, w,
					n -> Messages.setSettings(Map.of("items", Map.of(item, n))));
		}
		return used;
	}

	private int toggle(GuiGraphics g, String key, boolean on, boolean enabled, int x, int y, int w) {
		TrahisonUi.row(g, x, y, w, 18, ui.hovered(x, y, w, 18), false);
		TrahisonUi.textEllipsis(g, Component.translatable(T + "opt." + key), x + 6, y + 5, w - 70, TrahisonPalette.TEXT);
		ui.tip(x, y, w - 66, 18, Component.translatable(T + "opt." + key + ".tip"));
		ui.button(g, x + w - 62, y + 1, 60, 16, Component.translatable(T + (on ? "on" : "off")), on ? ButtonKind.PRIMARY : ButtonKind.DEFAULT, null,
				enabled ? () -> Messages.setSettings(Map.of(key, !on)) : null);
		return ROW_PITCH;
	}

	private int counter(GuiGraphics g, Component label, int value, int min, int max, boolean enabled, int x, int y, int w,
			java.util.function.IntConsumer set) {
		TrahisonUi.row(g, x, y, w, 18, ui.hovered(x, y, w, 18), false);
		TrahisonUi.textEllipsis(g, label, x + 6, y + 5, w - 70, TrahisonPalette.TEXT);
		int plus = x + w - 18;
		int minus = plus - 36;
		ui.button(g, minus, y + 1, 16, 16, Component.literal("-"), ButtonKind.DEFAULT, null, enabled && value > min ? () -> set.accept(value - 1) : null);
		MenuUi.centered(g, Component.literal(Integer.toString(value)), minus + 26, y + 5, 16, TrahisonPalette.TEXT);
		ui.button(g, plus, y + 1, 16, 16, Component.literal("+"), ButtonKind.DEFAULT, null, enabled && value < max ? () -> set.accept(value + 1) : null);
		return ROW_PITCH;
	}

	// ---------------------------------------------------------------- input

	@Override
	protected boolean scrolled(double mouseX, double mouseY, double amount) {
		int step = (int) Math.round(-amount * 20);
		if (mouseX < rightX - 4) {
			leftScroll = Math.max(0, leftScroll + step);
		} else {
			rightScroll = Math.max(0, rightScroll + step);
		}
		return true;
	}
}
