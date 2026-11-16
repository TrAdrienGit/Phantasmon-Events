package com.mystaria.events.trahison.screen;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.hud.BraceletHud;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Bracelet;
import com.mystaria.events.trahison.net.GameView.Door;
import com.mystaria.events.trahison.net.GameView.Group;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.Player;
import com.mystaria.events.trahison.net.GameView.VoteResult;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.net.GameView.Vote;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.events.trahison.net.Messages;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.events.trahison.ui.TrahisonUi.GameColor;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The provisional player screen of the betrayal game (step T1.4), in the charter's "window" mode: the list of players with
 * their bracelets on the left, and on the right the step, its countdown and the buttons of the step in progress (join,
 * doors, simulated room, vote with a confirmation, final door, results, winners). It shows what the latest
 * {@link GameView} says and sends actions through {@link Messages}; the backend refuses whatever is wrong. Escape closes it,
 * closing loses nothing. Client thread.
 */
public final class GameScreen extends TrahisonScreen {

	private static final String T = "phantasmon_events.trahison.";
	/**
	 * Display hint only: the bracelet reading that opens the final door. The button is greyed below it; the backend is the
	 * one that decides.
	 */
	private static final int FINAL_DOOR_POINTS = 9;
	private static final int ROW_PITCH = 24;
	private static final int ROW_H = 22;
	private static final long PENDING_MS = 3000;

	private int listScroll;
	private int ctxScroll;
	private int ctxContent;
	// Geometry of the last frame, for the wheel.
	private int listX;
	private int listW;
	private int ctxX;
	private Vote pendingVote;
	private long pendingAt;
	private Vote shownVote;
	private long shownVoteAt;

	private GameScreen() {
		super(Component.translatable(T + "title"), TrahisonPalette.ACCENT);
	}

	/** Opens the screen ({@code /phantasmon event trahison menu}, or the key). */
	public static void open() {
		Minecraft.getInstance().setScreen(new GameScreen());
	}

	// ---------------------------------------------------------------- frame

	@Override
	protected void drawStage(GuiGraphics g, int mouseX, int mouseY) {
		View v = GameView.orEmpty();
		int[] r = TrahisonUi.stage(width, height, 372, 252);
		int x = r[0];
		int y = r[1];
		int w = r[2];
		int h = r[3];
		TrahisonUi.titledPanel(g, x, y, w, h, title, TrahisonPalette.ACCENT);
		if (v.hasGame()) {
			Component label = Component.translatable(T + "phase." + v.phase().key());
			int pw = TrahisonUi.width(label) + 10;
			TrahisonUi.pill(g, x + w - 6 - pw, y + 4, label, TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_SECONDARY);
		}
		int top = y + 28;
		int bottom = y + h - 8;
		int lw = w >= 340 ? 172 : 152;
		listX = x + 8;
		listW = lw;
		ctxX = listX + lw + 8;
		drawPlayers(g, v, listX, top, lw, bottom - top);
		drawContext(g, v, ctxX, top, x + w - 8 - ctxX, bottom - top);
	}

	// ---------------------------------------------------------------- the nine

	private void drawPlayers(GuiGraphics g, View v, int x, int y, int w, int h) {
		MenuUi.text(g, Component.translatable(T + "players", v.players().size()), x, y, w, TrahisonPalette.TEXT_MUTED);
		int wy = y + 12;
		int wh = h - 12;
		TrahisonUi.well(g, x, wy, w, wh);
		if (v.players().isEmpty()) {
			MenuUi.paragraph(g, Component.translatable(T + "players.none"), x + 6, wy + 6, w - 12, TrahisonPalette.TEXT_MUTED);
			return;
		}
		int content = v.players().size() * ROW_PITCH + 2;
		listScroll = clamp(listScroll, content - wh);
		boolean bar = content > wh;
		ui.clip(g, x + 1, wy + 1, x + w - 1, wy + wh - 1);
		int ry = wy + 2 - listScroll;
		for (Player p : v.players()) {
			drawPlayerRow(g, v, p, x + 2, ry, w - 4 - (bar ? 4 : 0));
			ry += ROW_PITCH;
		}
		ui.unclip(g);
		if (bar) {
			int[] thumb = MenuUi.thumb(wy + 1, wh - 2, content, listScroll);
			g.fill(x + w - 4, thumb[0], x + w - 2, thumb[0] + thumb[1], TrahisonPalette.EDGE);
		}
	}

	private void drawPlayerRow(GuiGraphics g, View v, Player p, int x, int y, int w) {
		boolean me = v.viewer().index() != null && v.viewer().index() == p.index();
		boolean ghost = !p.alive();
		TrahisonUi.row(g, x, y, w, ROW_H, ui.hovered(x, y, w, ROW_H), me);
		// Head: a real player's face, or a placeholder for a dummy player.
		if (p.bot() || p.uuid() == null) {
			g.fill(x + 6, y + 4, x + 20, y + 18, TrahisonPalette.PANEL_RAISED);
			MenuUi.outline(g, x + 6, y + 4, x + 20, y + 18, TrahisonPalette.EDGE);
			g.fill(x + 9, y + 8, x + 11, y + 10, TrahisonPalette.TEXT_MUTED);
			g.fill(x + 15, y + 8, x + 17, y + 10, TrahisonPalette.TEXT_MUTED);
			g.fill(x + 9, y + 13, x + 17, y + 14, TrahisonPalette.TEXT_MUTED);
		} else {
			if (ghost) {
				int c = TrahisonPalette.GHOST;
				g.setColor((c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f, 0.5f);
			}
			PhantasmonApi.drawPlayerHead(g, p.uuid(), x + 6, y + 4, 14);
			g.setColor(1f, 1f, 1f, 1f);
		}
		Bracelet b = v.bracelet(p.index());
		int right = x + w - 3;
		if (b != null) {
			right -= TrahisonUi.BRACELET_W;
			TrahisonUi.bracelet(g, right, y, b.color(), b.points(), Boolean.TRUE.equals(b.pair()), ghost);
			if (b.color() != null) {
				ui.tip(right, y, TrahisonUi.BRACELET_W, ROW_H, Component.translatable(T + "bracelet.tip", Shown.colorName(b.color()), Shown.side(b.pair()), b.points()));
			}
			right -= 4;
		}
		if (v.phase() == Phase.RESULTS && v.results() != null && p.index() < v.results().deltas().size()) {
			int delta = v.results().deltas().get(p.index());
			Component chip = Component.literal(delta > 0 ? "+" + delta : Integer.toString(delta));
			int cw = TrahisonUi.width(chip) + 10;
			TrahisonUi.pill(g, right - cw, y + 5, chip, delta > 0 ? TrahisonPalette.SUCCESS_DARK : delta < 0 ? TrahisonPalette.DANGER_DARK : TrahisonPalette.PANEL_SUNKEN,
					delta > 0 ? TrahisonPalette.SUCCESS : delta < 0 ? TrahisonPalette.DANGER : TrahisonPalette.TEXT_MUTED);
			right -= cw + 4;
		}
		int nameX = x + 24;
		TrahisonUi.textEllipsis(g, Component.literal(p.name()), nameX, y + 2, right - nameX, ghost ? TrahisonPalette.GHOST : TrahisonPalette.TEXT);
		Component tag = ghost ? Component.translatable(T + "player.ghost") : !p.connected() ? Component.translatable(T + "player.offline")
				: p.bot() ? Component.translatable(T + "player.bot") : me ? Component.translatable(T + "player.you") : null;
		if (tag != null) {
			int color = ghost ? TrahisonPalette.GHOST : !p.connected() ? TrahisonPalette.DANGER : me ? TrahisonPalette.ACCENT : TrahisonPalette.TEXT_MUTED;
			TrahisonUi.textEllipsis(g, tag, nameX, y + 12, right - nameX, color);
		}
	}

	// ---------------------------------------------------------------- the step in progress

	private void drawContext(GuiGraphics g, View v, int x, int y, int w, int h) {
		int bottom = y + h;
		if (!v.hasGame()) {
			drawNoGame(g, x, y, w);
			return;
		}
		TrahisonUi.textEllipsis(g, BraceletHud.phaseLine(v), x, y, w - (v.paused() ? 56 : 0), TrahisonPalette.TEXT);
		if (v.paused()) {
			Component label = Component.translatable(T + (v.autoPaused() ? "hud.waiting" : "hud.paused"));
			int pw = TrahisonUi.width(label) + 10;
			TrahisonUi.pill(g, x + w - pw, y - 1, label, TrahisonPalette.GAME_MASTER_DARK, TrahisonPalette.GAME_MASTER);
		}
		int cy = y + 14;
		long remaining = GameView.remainingMs();
		if (remaining >= 0) {
			Component t = BraceletHud.time(remaining);
			int tw = TrahisonUi.width(t) * 2;
			int color = v.paused() ? TrahisonPalette.TEXT_MUTED : remaining < 15_000 ? TrahisonPalette.DANGER : remaining < 60_000 ? TrahisonPalette.GAME_MASTER : TrahisonPalette.TEXT;
			TrahisonUi.bigText(g, t, x, cy, 2f, color);
			TrahisonUi.progressBar(g, x + tw + 6, cy + 4, Math.max(8, w - tw - 6), 8, GameView.fraction(),
					remaining < 15_000 ? TrahisonPalette.DANGER : remaining < 60_000 ? TrahisonPalette.GAME_MASTER : TrahisonPalette.ACCENT);
			cy += 20;
		}
		g.fill(x, cy, x + w, cy + 1, TrahisonPalette.EDGE);
		cy += 5;
		drawIdentity(g, v, x, cy, w);
		cy += 16;
		// The step's own content scrolls under the fixed header.
		int viewH = bottom - cy;
		ctxScroll = clamp(ctxScroll, ctxContent - viewH);
		ui.clip(g, x - 1, cy, x + w + 1, bottom);
		int ay = cy - ctxScroll;
		int used = switch (v.phase()) {
			case LOBBY -> drawLobby(g, v, x, ay, w);
			case PROLOGUE, FREE_TIME -> hint(g, T + "hint." + v.phase().key(), x, ay, w);
			case DOORS -> drawDoors(g, v, x, ay, w);
			case ROOMS -> drawRooms(g, v, x, ay, w);
			case VOTE -> drawVote(g, v, x, ay, w);
			case RESULTS -> drawResults(g, v, x, ay, w);
			case FINAL -> drawFinal(g, v, x, ay, w);
			case ENDED -> drawEnded(g, v, x, ay, w);
			default -> 0;
		};
		ui.unclip(g);
		ctxContent = used;
		if (used > viewH) {
			int[] thumb = MenuUi.thumb(cy, viewH, used, ctxScroll);
			g.fill(x + w + 1, thumb[0], x + w + 3, thumb[0] + thumb[1], TrahisonPalette.EDGE);
		}
	}

	private void drawNoGame(GuiGraphics g, int x, int y, int w) {
		boolean inHub = Link.hub() != null;
		int used = MenuUi.paragraph(g, Component.translatable(T + (inHub ? "nogame" : "nohub")), x, y, w, TrahisonPalette.TEXT_SECONDARY);
		if (inHub) {
			ui.button(g, x, y + used + 8, w, TrahisonUi.BUTTON_H, Component.translatable(T + "refresh"), ButtonKind.DEFAULT, null, Messages::view);
		}
	}

	/** Who the viewer is: their bracelet's colour and side, or ghost, spectator, game master. */
	private void drawIdentity(GuiGraphics g, View v, int x, int y, int w) {
		ViewerKind kind = v.viewer().kind();
		Bracelet own = v.ownBracelet();
		if (kind == ViewerKind.PLAYER && own != null && own.color() != null) {
			int pw = Shown.colorPill(g, x, y, own.color());
			TrahisonUi.pill(g, x + pw + 4, y, Shown.side(own.pair()), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_SECONDARY);
			ui.tip(x, y, w, 12, Component.translatable(T + "identity.tip", Shown.colorName(own.color()), Shown.side(own.pair())));
		} else if (kind == ViewerKind.GHOST) {
			TrahisonUi.pill(g, x, y, Component.translatable(T + "hud.ghost"), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.GHOST);
		} else if (kind == ViewerKind.GM) {
			TrahisonUi.pill(g, x, y, Component.translatable(T + "viewer.gm"), TrahisonPalette.GAME_MASTER_DARK, TrahisonPalette.GAME_MASTER);
		} else if (v.viewer().index() == null) {
			TrahisonUi.pill(g, x, y, Component.translatable(T + "viewer.spectator"), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_SECONDARY);
		} else {
			TrahisonUi.pill(g, x, y, Component.translatable(T + "viewer.registered"), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_SECONDARY);
		}
	}

	private int hint(GuiGraphics g, String key, int x, int y, int w) {
		return MenuUi.paragraph(g, Component.translatable(key), x, y, w, TrahisonPalette.TEXT_SECONDARY);
	}

	// ---------------------------------------------------------------- lobby, doors, rooms

	private int drawLobby(GuiGraphics g, View v, int x, int y, int w) {
		boolean registered = v.viewer().index() != null;
		int used = hint(g, T + (registered ? "hint.lobby_in" : "hint.lobby_out"), x, y, w) + 8;
		if (registered) {
			ui.button(g, x, y + used, w, TrahisonUi.BUTTON_H, Component.translatable(T + "leave"), ButtonKind.DEFAULT, null, Messages::leave);
		} else {
			ui.button(g, x, y + used, w, TrahisonUi.BUTTON_H, Component.translatable(T + "join"), ButtonKind.PRIMARY, null, Messages::join);
		}
		return used + TrahisonUi.BUTTON_H;
	}

	private int drawDoors(GuiGraphics g, View v, int x, int y, int w) {
		int used = hint(g, T + "hint.doors", x, y, w) + 6;
		for (Door d : v.doors()) {
			drawDoorButton(g, v, d, x, y + used, w);
			used += TrahisonUi.BUTTON_H + 4;
		}
		return used;
	}

	private void drawDoorButton(GuiGraphics g, View v, Door door, int x, int y, int w) {
		Bracelet own = v.ownBracelet();
		boolean alive = v.viewer().kind() == ViewerKind.PLAYER;
		boolean passed = v.viewer().doorPassed() != null && v.viewer().doorPassed() == door.index();
		boolean fits = own != null && own.color() != null && door.colors().contains(own.color());
		boolean enabled = alive && v.viewer().doorPassed() == null && fits && !v.paused();
		Component tip = enabled ? null : Component.translatable(T + (!alive ? "door.tip.alive" : v.viewer().doorPassed() != null ? "door.tip.passed"
				: v.paused() ? "door.tip.paused" : "door.tip.color"), own == null || own.color() == null ? "" : Shown.colorName(own.color()));
		boolean hovered = ui.hovered(x, y, w, TrahisonUi.BUTTON_H);
		boolean pressed = hovered && enabled && Minecraft.getInstance().mouseHandler.isLeftPressed();
		TrahisonUi.button(g, x, y, w, TrahisonUi.BUTTON_H, Component.empty(), passed ? ButtonKind.PRIMARY : ButtonKind.DEFAULT, TrahisonUi.stateOf(enabled, hovered, pressed));
		Component label = Component.translatable(T + (passed ? "door.passed" : "door"), door.index() + 1);
		int labelColor = passed ? TrahisonPalette.BORDER : enabled ? TrahisonPalette.TEXT : TrahisonPalette.TEXT_DISABLED;
		int need = 6 + TrahisonUi.width(label) + 6;
		for (GameColor c : door.colors()) {
			need += Shown.colorPillWidth(c) + 2;
		}
		int labelW = Math.max(20, w - 12);
		int right = x + w - 4;
		if (need <= w) {
			// Pills with the colour names, right-aligned.
			for (int i = door.colors().size() - 1; i >= 0; i--) {
				GameColor c = door.colors().get(i);
				right -= Shown.colorPillWidth(c);
				Shown.colorPill(g, right, y + 3, c);
				right -= 2;
			}
			labelW = right - (x + 6);
		} else {
			// Too narrow for names: symbols only, names in the tooltip.
			for (int i = door.colors().size() - 1; i >= 0; i--) {
				right -= 10;
				TrahisonUi.colorGlyph(g, door.colors().get(i), right, y + 5, 8);
			}
			labelW = right - (x + 6);
			Component names = Shown.colorName(door.colors().get(0)).copy().append(" + ").append(Shown.colorName(door.colors().get(1)));
			tip = tip == null ? names : tip.copy().append(" (").append(names).append(")");
		}
		TrahisonUi.textEllipsis(g, label, x + 6, y + 5, labelW, labelColor);
		ui.tip(x, y, w, TrahisonUi.BUTTON_H, tip);
		if (enabled) {
			ui.zone(x, y, w, TrahisonUi.BUTTON_H, b -> {
				if (b == 0) {
					Messages.passDoor(door.index());
				}
			});
		}
	}

	private int drawRooms(GuiGraphics g, View v, int x, int y, int w) {
		Group group = v.ownGroup();
		if (group == null) {
			return hint(g, T + "hint.rooms_none", x, y, w);
		}
		int used = groupBlock(g, v, group, x, y, w);
		Component state = Component.translatable(T + (group.roomSolved() ? "room.solved" : "room.open"));
		TrahisonUi.pill(g, x, y + used, state, group.roomSolved() ? TrahisonPalette.SUCCESS_DARK : TrahisonPalette.PANEL_SUNKEN,
				group.roomSolved() ? TrahisonPalette.SUCCESS : TrahisonPalette.TEXT_SECONDARY);
		used += 18;
		boolean can = v.viewer().kind() == ViewerKind.PLAYER && !group.roomSolved() && !v.paused();
		ui.button(g, x, y + used, w, TrahisonUi.BUTTON_H, Component.translatable(T + "debug_solve"), ButtonKind.PRIMARY,
				Component.translatable(T + "debug_solve.tip"), can ? Messages::debugSolve : null);
		return used + TrahisonUi.BUTTON_H;
	}

	/** The viewer's group: each side with its colour, and who is in it. Returns its height. */
	private int groupBlock(GuiGraphics g, View v, Group group, int x, int y, int w) {
		int used = 0;
		for (boolean pair : new boolean[] { true, false }) {
			GameColor c = pair ? group.pairColor() : group.soloColor();
			Component side = Shown.side(pair);
			MenuUi.text(g, side, x, y + used + 2, w, TrahisonPalette.TEXT_MUTED);
			if (c != null) {
				Shown.colorPill(g, x + TrahisonUi.width(side) + 6, y + used, c);
			}
			used += 14;
			String names = pair ? Shown.names(v, group.pair()) : v.nameOf(group.solo());
			TrahisonUi.textEllipsis(g, Component.literal(names), x + 4, y + used, w - 4, TrahisonPalette.TEXT);
			used += 14;
		}
		return used;
	}

	// ---------------------------------------------------------------- vote

	private int drawVote(GuiGraphics g, View v, int x, int y, int w) {
		Group group = v.ownGroup();
		boolean player = v.viewer().kind() == ViewerKind.PLAYER && group != null;
		int used = 0;
		if (group != null) {
			used += groupBlock(g, v, group, x, y, w) + 4;
		}
		if (!player) {
			return used + hint(g, T + "hint.vote_watch", x, y + used, w);
		}
		Vote own = v.viewer().ownVote();
		if (own == null && pendingVote != null && System.currentTimeMillis() - pendingAt < PENDING_MS) {
			own = pendingVote;
		}
		boolean voted = own != null || v.viewer().sideVoted();
		if (own != shownVote) {
			shownVote = own;
			shownVoteAt = System.currentTimeMillis();
		}
		float dim = own == null ? 0f : TrahisonUi.progress(shownVoteAt, 150);
		boolean can = !voted && !v.paused();
		int cw = (w - 4) / 2;
		int ch = 36;
		Boolean pairSide = v.viewer().pairSide();
		for (Vote vote : Vote.values()) {
			int cx = x + (vote == Vote.ALLY ? 0 : cw + 4);
			boolean chosen = own == vote;
			voteCard(g, cx, y + used, cw, ch, vote, can, chosen, !chosen && own != null ? dim : 0f, can ? () -> askVote(vote, Boolean.TRUE.equals(pairSide)) : null);
		}
		used += ch + 6;
		Component status = own != null ? Component.translatable(T + "vote.cast", Shown.voteName(own))
				: v.viewer().sideVoted() ? Component.translatable(T + "vote.side_done") : Component.translatable(T + "vote.waiting");
		used += MenuUi.paragraph(g, status, x, y + used, w, own != null ? Shown.voteTone(own) : TrahisonPalette.TEXT_SECONDARY) + 4;
		return used;
	}

	/** A vote button: its symbol and name; the chosen one stays lit, the other darkens (dim 0..1). */
	private void voteCard(GuiGraphics g, int x, int y, int w, int h, Vote vote, boolean enabled, boolean chosen, float dim, Runnable action) {
		boolean hovered = ui.hovered(x, y, w, h);
		int tone = Shown.voteTone(vote);
		int dark = Shown.voteDark(vote);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, chosen ? tone : TrahisonPalette.BORDER);
		int top = hovered && enabled ? TrahisonUi.lerp(0.5f, dark, tone) : dark;
		g.fillGradient(x, y, x + w, y + h, TrahisonUi.lerp(dim, top, TrahisonPalette.PANEL), TrahisonUi.lerp(dim, TrahisonPalette.PANEL, TrahisonPalette.PANEL_SUNKEN));
		MenuUi.raised(g, x, y, x + w, y + h);
		boolean muted = !enabled && !chosen;
		int glyphColor = muted ? TrahisonPalette.TEXT_DISABLED : TrahisonUi.lerp(dim, tone, TrahisonPalette.TEXT_DISABLED);
		TrahisonUi.glyph(g, Shown.voteGlyph(vote), x + w / 2 - 6, y + 6, 12, glyphColor);
		MenuUi.centered(g, Shown.voteName(vote), x + w / 2, y + h - 14, w - 6, muted ? TrahisonPalette.TEXT_DISABLED : TrahisonUi.lerp(dim, TrahisonPalette.TEXT, TrahisonPalette.TEXT_DISABLED));
		ui.zone(x, y, w, h, action == null ? null : b -> {
			if (b == 0) {
				action.run();
			}
		});
	}

	private void askVote(Vote vote, boolean pair) {
		modal = new Modal(Component.translatable(T + "confirm.vote.title"),
				Component.translatable(T + (pair ? "confirm.vote.pair" : "confirm.vote.solo"), Shown.voteName(vote)),
				Shown.voteName(vote), vote == Vote.BETRAY, () -> {
					pendingVote = vote;
					pendingAt = System.currentTimeMillis();
					Messages.vote(vote == Vote.ALLY);
				});
	}

	// ---------------------------------------------------------------- results, final, end

	private int drawResults(GuiGraphics g, View v, int x, int y, int w) {
		GameView.Results res = v.results();
		if (res == null) {
			return hint(g, T + "hint.results_none", x, y, w);
		}
		int used = MenuUi.paragraph(g, Component.translatable(T + (res.finalLoop() ? "results.final" : "results.round"), res.round()), x, y, w, TrahisonPalette.TEXT_MUTED) + 2;
		for (VoteResult vr : res.votes()) {
			used += resultCard(g, v, vr, x, y + used, w) + 4;
		}
		if (!res.newlyDead().isEmpty()) {
			String names = res.newlyDead().stream().map(v::nameOf).collect(java.util.stream.Collectors.joining(", "));
			used += MenuUi.paragraph(g, Component.translatable(T + "results.dead", names), x, y + used, w, TrahisonPalette.GHOST) + 4;
		}
		return used;
	}

	/** One door's vote: the pair's names and vote, the solo's name and vote. Returns the card's height. */
	private int resultCard(GuiGraphics g, View v, VoteResult vr, int x, int y, int w) {
		int h = 44;
		TrahisonUi.panel(g, x, y, w, h);
		resultLine(g, v, x + 6, y + 5, w - 12, vr.pairColor(), Shown.names(v, vr.pair()), vr.pairVote(), vr.pairCast());
		resultLine(g, v, x + 6, y + 25, w - 12, vr.soloColor(), v.nameOf(vr.solo()), vr.soloVote(), vr.soloCast());
		return h;
	}

	private void resultLine(GuiGraphics g, View v, int x, int y, int w, GameColor color, String names, Vote vote, boolean cast) {
		if (color != null) {
			TrahisonUi.colorGlyph(g, color, x, y + 2, 8);
		}
		int vw = vote == null ? 0 : Shown.votePillWidth(vote);
		TrahisonUi.textEllipsis(g, Component.literal(names), x + 12, y + 2, w - 12 - vw - 4, TrahisonPalette.TEXT);
		if (vote != null) {
			Shown.votePill(g, x + w - vw, y, vote, cast);
			if (!cast) {
				ui.tip(x + w - vw, y, vw, 12, Component.translatable(T + "results.default"));
			}
		}
		if (color != null) {
			ui.tip(x, y, 10, 12, Shown.colorName(color));
		}
	}

	private int drawFinal(GuiGraphics g, View v, int x, int y, int w) {
		int used = hint(g, T + "hint.final", x, y, w) + 8;
		Bracelet own = v.ownBracelet();
		boolean can = v.viewer().kind() == ViewerKind.PLAYER && own != null && own.points() >= FINAL_DOOR_POINTS && !v.paused();
		ui.button(g, x, y + used, w, TrahisonUi.BUTTON_H, Component.translatable(T + "final.open"), ButtonKind.PRIMARY,
				can ? null : Component.translatable(T + "final.need", FINAL_DOOR_POINTS), can ? Messages::openFinalDoor : null);
		used += TrahisonUi.BUTTON_H + 8;
		if (v.finalLoops() > 0) {
			used += MenuUi.paragraph(g, Component.translatable(T + "final.loops", v.finalLoops()), x, y + used, w, TrahisonPalette.TEXT_MUTED);
		}
		return used;
	}

	private int drawEnded(GuiGraphics g, View v, int x, int y, int w) {
		List<Integer> winners = v.winners() == null ? List.of() : v.winners();
		if (winners.isEmpty()) {
			return hint(g, T + "ended.none", x, y, w);
		}
		int used = MenuUi.paragraph(g, Component.translatable(T + "ended.winners"), x, y, w, TrahisonPalette.SUCCESS) + 4;
		for (int index : winners) {
			Player p = v.player(index);
			TrahisonUi.row(g, x, y + used, w, 18, false, false);
			if (p != null && !p.bot() && p.uuid() != null) {
				PhantasmonApi.drawPlayerHead(g, p.uuid(), x + 6, y + used + 3, 12);
			} else {
				g.fill(x + 6, y + used + 3, x + 18, y + used + 15, TrahisonPalette.PANEL_RAISED);
			}
			TrahisonUi.textEllipsis(g, Component.literal(v.nameOf(index)), x + 24, y + used + 5, w - 30, TrahisonPalette.TEXT);
			used += 20;
		}
		return used;
	}

	// ---------------------------------------------------------------- input

	@Override
	protected boolean scrolled(double mouseX, double mouseY, double amount) {
		int step = (int) Math.round(-amount * 20);
		if (mouseX < listX + listW + 4) {
			listScroll = Math.max(0, listScroll + step);
		} else {
			ctxScroll = Math.max(0, ctxScroll + step);
		}
		return true;
	}
}
