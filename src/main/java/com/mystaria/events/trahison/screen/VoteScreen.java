package com.mystaria.events.trahison.screen;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.hud.BraceletHud;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Bracelet;
import com.mystaria.events.trahison.net.GameView.Group;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.net.GameView.Vote;
import com.mystaria.events.trahison.net.Messages;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The voting pupitre inside a closed cabin (T2.4), in the charter's "window" mode: the player's colour and side, two big
 * cards (S'allier in gold, Trahir in violet, each with its symbol), and a confirm button. A card only selects; the vote is
 * sent by "Confirm" (Enter), and the choice stays lit once cast while the other darkens (charter, section 7). It opens when
 * his cabin locks with him inside (automatically, see {@code CabinFlow}, or with the key) and closes by itself when the step
 * ends or he leaves the cabin; closing it by hand loses nothing (the key opens it again). Once his group has voted the cabin's
 * door opens for him. Left and
 * Right pick a side. The pair's behaviour is the backend's: the first of the pair to vote decides for both, and either of them
 * may vote from the locked cabin. Readable from 320 x 180 GUI pixels up. Client thread.
 */
public final class VoteScreen extends TrahisonScreen {

	private static final String T = "phantasmon_events.trahison.";
	private static final long PENDING_MS = 3000;

	private Vote selected;
	private Vote pending;
	private long pendingAt;
	private Vote shownVote;
	private long shownVoteAt;

	private VoteScreen() {
		super(Component.translatable(T + "votepad.title"), TrahisonPalette.ACCENT);
	}

	/** Opens the pupitre (the cabin key, or by itself after closing the cabin). */
	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof VoteScreen)) {
			mc.setScreen(new VoteScreen());
		}
	}

	// ---------------------------------------------------------------- frame

	@Override
	protected void drawStage(GuiGraphics g, int mouseX, int mouseY) {
		View v = GameView.orEmpty();
		if (!stillHis(v)) {
			onClose();
			return;
		}
		int[] r = TrahisonUi.stage(width, height, 340, 180);
		int x = r[0];
		int y = r[1];
		int w = r[2];
		int h = r[3];
		TrahisonUi.titledPanel(g, x, y, w, h, title, TrahisonPalette.ACCENT);
		header(g, v, x, y, w);
		int ix = x + 8;
		int iw = w - 16;
		int top = y + 28;
		identity(g, v, ix, top, iw);

		int buttonY = y + h - 8 - TrahisonUi.BUTTON_H;
		int statusY = buttonY - 4 - TrahisonUi.LINE;
		int cardsTop = top + 18;
		int cardH = Math.max(40, Math.min(96, statusY - 4 - cardsTop));
		Vote own = ownVote(v);
		boolean voted = own != null || v.viewer().sideVoted();
		boolean can = !voted && !v.paused();
		if (own != shownVote) {
			shownVote = own;
			shownVoteAt = System.currentTimeMillis();
		}
		float dim = own == null ? 0f : TrahisonUi.progress(shownVoteAt, 150);
		int cw = (iw - 8) / 2;
		for (Vote vote : Vote.values()) {
			int cx = ix + (vote == Vote.ALLY ? 0 : cw + 8);
			boolean chosen = own == vote || own == null && !voted && selected == vote;
			card(g, cx, cardsTop, cw, cardH, vote, can, chosen, own != null && own != vote ? dim : 0f);
		}
		status(g, v, own, ix, statusY, iw);

		int backW = 84;
		ui.button(g, ix, buttonY, backW, TrahisonUi.BUTTON_H, Component.translatable(T + "votepad.back"), ButtonKind.DEFAULT,
				Component.translatable(T + "votepad.back.tip"), this::onClose);
		Component confirmLabel = selected == null || voted ? Component.translatable(T + "votepad.confirm")
				: Component.translatable(T + "votepad.confirm.vote", Shown.voteName(selected));
		Component why = voted ? null : selected == null ? Component.translatable(T + "votepad.confirm.pick") : v.paused() ? Component.translatable(T + "door.tip.paused") : null;
		ui.button(g, ix + iw - 156, buttonY, 156, TrahisonUi.BUTTON_H, confirmLabel, ButtonKind.PRIMARY, why,
				can && selected != null ? this::confirm : null);
	}

	/** Still a living player of a VOTE step, inside his own locked cabin (once his group voted the pupitre stays readable). */
	private static boolean stillHis(View v) {
		return v.hasGame() && v.phase() == Phase.VOTE && v.viewer().kind() == ViewerKind.PLAYER
				&& com.mystaria.events.trahison.world.CabinFlow.atPupitre();
	}

	private void header(GuiGraphics g, View v, int x, int y, int w) {
		int right = x + w - 6;
		long remaining = GameView.remainingMs();
		if (remaining >= 0) {
			Component t = BraceletHud.time(remaining);
			int pw = TrahisonUi.width(t) + 10;
			int color = v.paused() ? TrahisonPalette.TEXT_MUTED : remaining < 15_000 ? TrahisonPalette.DANGER : remaining < 60_000 ? TrahisonPalette.GAME_MASTER : TrahisonPalette.TEXT;
			TrahisonUi.pill(g, right - pw, y + 4, t, TrahisonPalette.PANEL_SUNKEN, color);
			right -= pw + 4;
		}
		boolean released = v.viewer().ownVote() != null || v.viewer().sideVoted();
		Component closed = Component.translatable(T + (released ? "votepad.open_door" : "votepad.closed"));
		int cw = TrahisonUi.width(closed) + 10;
		if (right - cw > x + 80) {
			TrahisonUi.pill(g, right - cw, y + 4, closed, TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_SECONDARY);
			ui.tip(right - cw, y + 4, cw, 12, Component.translatable(T + (released ? "votepad.open_door.tip" : "votepad.closed.tip")));
		}
	}

	/** Who votes: the player's colour and side, and his group's names. */
	private void identity(GuiGraphics g, View v, int x, int y, int w) {
		Bracelet own = v.ownBracelet();
		Group group = v.ownGroup();
		int px = x;
		if (own != null && own.color() != null) {
			px += Shown.colorPill(g, px, y, own.color()) + 4;
		}
		boolean pair = Boolean.TRUE.equals(v.viewer().pairSide());
		px += TrahisonUi.pill(g, px, y, Shown.side(pair), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_SECONDARY) + 6;
		if (group != null) {
			String names = pair ? Shown.names(v, group.pair()) : v.nameOf(group.solo());
			TrahisonUi.textEllipsis(g, Component.literal(names), px, y + 2, x + w - px, TrahisonPalette.TEXT_MUTED);
		}
	}

	private Vote ownVote(View v) {
		Vote own = v.viewer().ownVote();
		if (own == null && pending != null && System.currentTimeMillis() - pendingAt < PENDING_MS) {
			own = pending;
		}
		return own;
	}

	/** A card: symbol, name, what it means in a line or two. {@code chosen}: lit; {@code dim} 0..1: the other card darkening. */
	private void card(GuiGraphics g, int x, int y, int w, int h, Vote vote, boolean enabled, boolean chosen, float dim) {
		boolean hovered = ui.hovered(x, y, w, h);
		int tone = Shown.voteTone(vote);
		int dark = Shown.voteDark(vote);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, chosen ? tone : TrahisonPalette.BORDER);
		if (chosen) {
			g.fill(x - 2, y - 2, x + w + 2, y - 1, TrahisonUi.withAlpha(tone, 0x80));
		}
		int top = hovered && enabled ? TrahisonUi.lerp(0.5f, dark, tone) : chosen ? TrahisonUi.lerp(0.25f, dark, tone) : dark;
		g.fillGradient(x, y, x + w, y + h, TrahisonUi.lerp(dim, top, TrahisonPalette.PANEL), TrahisonUi.lerp(dim, TrahisonPalette.PANEL, TrahisonPalette.PANEL_SUNKEN));
		MenuUi.raised(g, x, y, x + w, y + h);
		boolean muted = !enabled && !chosen;
		int glyphColor = muted ? TrahisonPalette.TEXT_DISABLED : TrahisonUi.lerp(dim, tone, TrahisonPalette.TEXT_DISABLED);
		int nameColor = muted ? TrahisonPalette.TEXT_DISABLED : TrahisonUi.lerp(dim, TrahisonPalette.TEXT, TrahisonPalette.TEXT_DISABLED);
		int textColor = muted ? TrahisonPalette.TEXT_DISABLED : TrahisonUi.lerp(dim, TrahisonPalette.TEXT_SECONDARY, TrahisonPalette.TEXT_DISABLED);
		boolean tall = h >= 64;
		int gs = tall ? 16 : 12;
		TrahisonUi.glyph(g, Shown.voteGlyph(vote), x + w / 2 - gs / 2, y + 6, gs, glyphColor);
		int ny = y + 6 + gs + 4;
		MenuUi.centered(g, Shown.voteName(vote).copy().withStyle(s -> s.withBold(true)), x + w / 2, ny, w - 8, nameColor);
		int dy = ny + 12;
		if (dy + TrahisonUi.LINE <= y + h - 2) {
			MenuUi.paragraph(g, Component.translatable(T + "votepad." + vote.name().toLowerCase(java.util.Locale.ROOT) + ".desc"), x + 6, dy, w - 12, textColor);
		}
		ui.zone(x, y, w, h, enabled ? b -> {
			if (b == 0) {
				selected = vote;
			}
		} : null);
	}

	private void status(GuiGraphics g, View v, Vote own, int x, int y, int w) {
		Component text;
		int color = TrahisonPalette.TEXT_SECONDARY;
		if (own != null) {
			text = Component.translatable(T + "vote.cast", Shown.voteName(own));
			color = Shown.voteTone(own);
		} else if (v.viewer().sideVoted()) {
			text = Component.translatable(T + "vote.side_done");
		} else if (selected != null) {
			text = Component.translatable(T + "votepad.picked", Shown.voteName(selected));
		} else {
			text = Component.translatable(T + "vote.waiting");
		}
		TrahisonUi.textEllipsis(g, text, x + 2, y, w - 4, color);
	}

	private void confirm() {
		Vote vote = selected;
		if (vote == null) {
			return;
		}
		pending = vote;
		pendingAt = System.currentTimeMillis();
		Messages.vote(vote == Vote.ALLY);
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (modal == null) {
			View v = GameView.orEmpty();
			boolean can = v.viewer().ownVote() == null && !v.viewer().sideVoted() && pending == null;
			if (keyCode == GLFW.GLFW_KEY_LEFT && can) {
				selected = Vote.ALLY;
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_RIGHT && can) {
				selected = Vote.BETRAY;
				return true;
			}
			if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && can && selected != null && !v.paused()) {
				confirm();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
