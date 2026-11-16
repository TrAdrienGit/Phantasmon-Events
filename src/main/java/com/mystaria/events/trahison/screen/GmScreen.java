package com.mystaria.events.trahison.screen;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.hud.BraceletHud;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Bracelet;
import com.mystaria.events.trahison.net.GameView.Gm;
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
		mc.setScreen(new GmScreen());
	}

	@Override
	protected void drawStage(GuiGraphics g, int mouseX, int mouseY) {
		View v = GameView.orEmpty();
		int[] r = TrahisonUi.stage(width, height, 372, 252);
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
		int lw = w >= 340 ? 172 : 152;
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
		ui.button(g, x, y + used, w, bh, Component.translatable(T + "end"), ButtonKind.DANGER, null, this::askEnd);
		return used + bh;
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

	// ---------------------------------------------------------------- right: bracelets and votes

	private void drawRight(GuiGraphics g, View v, int x, int y, int w, int h) {
		if (v.phase() == Phase.LOBBY) {
			drawLobbyMasters(g, v, x, y, w, h);
			return;
		}
		if (v.bracelets().isEmpty() && (v.gm() == null || v.gm().votes().isEmpty())) {
			MenuUi.paragraph(g, Component.translatable(T + "nothing"), x, y, w, TrahisonPalette.TEXT_MUTED);
			return;
		}
		TrahisonUi.well(g, x, y, w, h);
		rightScroll = clamp(rightScroll, rightContent - (h - 4));
		ui.clip(g, x + 1, y + 1, x + w - 1, y + h - 1);
		int ry = y + 4 - rightScroll;
		int used = 0;
		boolean bar = rightContent > h - 4;
		int rw = w - 8 - (bar ? 4 : 0);
		if (!v.bracelets().isEmpty()) {
			MenuUi.text(g, Component.translatable(T + "bracelets"), x + 4, ry + used, rw, TrahisonPalette.GAME_MASTER);
			used += 14;
			for (Bracelet b : v.bracelets()) {
				drawBraceletRow(g, v, b, x + 4, ry + used, rw);
				used += ROW_PITCH;
			}
			used += 4;
		}
		if (v.gm() != null && !v.gm().votes().isEmpty()) {
			MenuUi.text(g, Component.translatable(T + "votes"), x + 4, ry + used, rw, TrahisonPalette.GAME_MASTER);
			used += 14;
			for (GmVote vote : v.gm().votes()) {
				drawVoteRow(g, v, vote, x + 4, ry + used, rw);
				used += 30;
			}
		}
		ui.unclip(g);
		rightContent = used + 8;
		if (bar) {
			int[] thumb = MenuUi.thumb(y + 1, h - 2, rightContent + 4, rightScroll);
			if (thumb != null) {
				g.fill(x + w - 4, thumb[0], x + w - 2, thumb[0] + thumb[1], TrahisonPalette.EDGE);
			}
		}
	}

	/**
	 * D7: who runs the game. The game masters first, then the people around who are not playing; an admin gives or takes back
	 * the role with a click (none is fine: the game then runs on its own).
	 */
	private void drawLobbyMasters(GuiGraphics g, View v, int x, int y, int w, int h) {
		TrahisonUi.well(g, x, y, w, h);
		rightScroll = clamp(rightScroll, rightContent - (h - 4));
		ui.clip(g, x + 1, y + 1, x + w - 1, y + h - 1);
		int ry = y + 4 - rightScroll;
		int used = 0;
		boolean bar = rightContent > h - 4;
		int rw = w - 8 - (bar ? 4 : 0);
		GameView.Lobby lobby = GameView.lobby();
		MenuUi.text(g, Component.translatable(T + "masters", lobby.gameMasters().size()), x + 4, ry + used, rw, TrahisonPalette.GAME_MASTER);
		used += 14;
		boolean admin = PhantasmonApi.isAdmin();
		if (lobby.gameMasters().isEmpty()) {
			used += MenuUi.paragraph(g, Component.translatable(T + (v.mode().equals("GAME_MASTER") ? "masters.none_needed" : "masters.none")), x + 4, ry + used, rw,
					TrahisonPalette.TEXT_MUTED) + 4;
		}
		java.util.List<Member> rows = new java.util.ArrayList<>(lobby.gameMasters());
		for (Member m : lobby.members()) {
			if (!lobby.isGameMaster(m.uuid())) {
				rows.add(m);
			}
		}
		if (rows.isEmpty()) {
			used += MenuUi.paragraph(g, Component.translatable(T + "masters.nobody"), x + 4, ry + used, rw, TrahisonPalette.TEXT_MUTED) + 4;
		}
		for (Member m : rows) {
			boolean master = lobby.isGameMaster(m.uuid());
			int rx = x + 4;
			int rowY = ry + used;
			TrahisonUi.row(g, rx, rowY, rw, 18, ui.hovered(rx, rowY, rw, 18), false);
			int bw = 74;
			TrahisonUi.textEllipsis(g, Component.literal(m.name()), rx + 6, rowY + 5, rw - bw - 14, master ? TrahisonPalette.GAME_MASTER : TrahisonPalette.TEXT);
			Component label = Component.translatable(T + (master ? "master_remove" : "master_add"));
			ui.button(g, rx + rw - bw - 2, rowY + 1, bw, 16, label, master ? ButtonKind.DANGER : ButtonKind.DEFAULT,
					admin ? Component.translatable(T + (master ? "master_remove.tip" : "master_add.tip")) : Component.translatable(T + "master_admin_only"),
					admin ? () -> Messages.setGameMaster(m.uuid(), !master) : null);
			used += ROW_PITCH;
		}
		ui.unclip(g);
		rightContent = used + 8;
		if (bar) {
			int[] thumb = MenuUi.thumb(y + 1, h - 2, rightContent + 4, rightScroll);
			if (thumb != null) {
				g.fill(x + w - 4, thumb[0], x + w - 2, thumb[0] + thumb[1], TrahisonPalette.EDGE);
			}
		}
	}

	private void drawBraceletRow(GuiGraphics g, View v, Bracelet b, int x, int y, int w) {
		boolean ghost = v.player(b.index()) != null && !v.player(b.index()).alive();
		TrahisonUi.row(g, x, y, w, 18, ui.hovered(x, y, w, 18), false);
		if (b.color() != null) {
			TrahisonUi.colorGlyph(g, b.color(), x + 5, y + 5, 8);
			ui.tip(x + 4, y + 3, 10, 12, Component.translatable(G + "bracelet.tip", Shown.colorName(b.color()), Shown.side(b.pair()), b.points()));
		}
		String name = v.nameOf(b.index());
		int plus = x + w - 18;
		int minus = plus - 18;
		Component points = Component.literal(Integer.toString(b.points()));
		int pw = TrahisonUi.width(points);
		int px = minus - 6 - Math.max(pw, 12);
		TrahisonUi.textEllipsis(g, Component.literal(name), x + 18, y + 5, px - (x + 18) - 4, ghost ? TrahisonPalette.GHOST : TrahisonPalette.TEXT);
		MenuUi.text(g, points, px + Math.max(pw, 12) - pw, y + 5, 24, TrahisonPalette.TEXT);
		ui.button(g, minus, y + 1, 16, 16, Component.literal("-"), () -> Messages.setPoints(b.index(), b.points() - 1));
		ui.button(g, plus, y + 1, 16, 16, Component.literal("+"), () -> Messages.setPoints(b.index(), b.points() + 1));
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
