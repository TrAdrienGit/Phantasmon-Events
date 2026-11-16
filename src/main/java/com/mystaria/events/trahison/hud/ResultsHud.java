package com.mystaria.events.trahison.hud;

import java.util.Locale;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.edit.LayoutDto;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Results;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.Vote;
import com.mystaria.events.trahison.net.GameView.VoteResult;
import com.mystaria.events.trahison.screen.TrahisonScreen;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.GameColor;
import com.mystaria.events.trahison.ui.TrahisonUi.Glyph;
import com.mystaria.events.trahison.world.Plan;
import com.mystaria.events.trahison.world.ResultsBoardWorld;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The results on the HUD, for a hub whose plan has no results board (T2.4): a compact panel at the top left, drawn while
 * the step is RESULTS, that reveals the votes door by door at the board's pace ({@link ResultsReveal}), then the viewer's
 * own points. A hub with a board shows the results on it ({@code ResultsBoardWorld}) and this panel stays away. Nothing is
 * clickable; it hides with F1, under the debug screen and while a game screen is open. Client thread.
 */
public final class ResultsHud {

	private static final String T = "phantasmon_events.trahison.";
	private static final int LINE = 12;

	private ResultsHud() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	private static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		View view = GameView.current();
		if (!TrahisonUi.hudVisible() || view == null || !view.hasGame() || mc.screen instanceof TrahisonScreen || HudSuppression.hidden()
				|| com.mystaria.events.trahison.minigame.MiniGameScreen.isOpen()) {
			ResultsReveal.elapsed(view);
			return;
		}
		long elapsed = ResultsReveal.elapsed(view);
		LayoutDto plan = Plan.get(view.hub(), view.layoutVersion());
		if (elapsed < 0 || plan == null || ResultsBoardWorld.hasBoard(plan)) {
			return;
		}
		Results results = view.results();
		int w = Math.min(204, g.guiWidth() / 2 - 2 * TrahisonUi.SAFE_MARGIN);
		int shown = ResultsReveal.doorsShown(elapsed, results.votes().size());
		boolean points = ResultsReveal.pointsShown(elapsed, results.votes().size());
		boolean mine = points && view.viewer().index() != null && view.viewer().index() < results.deltas().size();
		int h = 8 + 14 + results.votes().size() * (2 * LINE + 4) + (mine ? LINE + 2 : LINE) + 4;
		int x = TrahisonUi.SAFE_MARGIN;
		int y = TrahisonUi.SAFE_MARGIN;
		TrahisonUi.panel(g, x, y, w, h);
		MenuUi.text(g, Component.translatable(T + (results.finalLoop() ? "results.final" : "results.round"), results.round()).copy()
				.withStyle(st -> st.withBold(true)), x + 6, y + 5, w - 12, TrahisonPalette.TEXT);
		g.fill(x + 6, y + 16, x + w - 6, y + 17, TrahisonPalette.ACCENT_DIM);
		int cy = y + 22;
		int index = 0;
		for (VoteResult vr : results.votes()) {
			boolean open = index < shown;
			line(g, view, x + 6, cy, w - 12, vr.pairColor(), String.join(" + ", vr.pair().stream().map(view::nameOf).toList()), open ? vr.pairVote() : null, vr.pairCast());
			line(g, view, x + 6, cy + LINE, w - 12, vr.soloColor(), view.nameOf(vr.solo()), open ? vr.soloVote() : null, vr.soloCast());
			cy += 2 * LINE + 4;
			index++;
		}
		if (!points) {
			MenuUi.text(g, Component.translatable(T + "board.waiting"), x + 6, cy, w - 12, TrahisonPalette.TEXT_MUTED);
		} else if (mine) {
			int index2 = view.viewer().index();
			int delta = results.deltas().get(index2);
			GameView.Bracelet b = view.bracelet(index2);
			Component chip = Component.literal(delta > 0 ? "+" + delta : Integer.toString(delta));
			MenuUi.text(g, Component.translatable(T + "results.hud.mine", chip, b == null ? 0 : b.points()), x + 6, cy, w - 12,
					delta > 0 ? TrahisonPalette.SUCCESS : delta < 0 ? TrahisonPalette.DANGER : TrahisonPalette.TEXT_SECONDARY);
		}
	}

	private static void line(GuiGraphics g, View view, int x, int y, int w, GameColor color, String names, Vote vote, boolean cast) {
		int vw;
		if (vote != null) {
			Component name = Component.translatable(T + "vote." + vote.name().toLowerCase(Locale.ROOT));
			vw = TrahisonUi.width(name) + 22;
			int tone = vote == Vote.ALLY ? TrahisonPalette.ALLY : TrahisonPalette.BETRAY;
			int dark = vote == Vote.ALLY ? TrahisonPalette.ALLY_DARK : TrahisonPalette.BETRAY_DARK;
			TrahisonUi.glyphPill(g, x + w - vw, y, vote == Vote.ALLY ? Glyph.ALLY : Glyph.BETRAY, name, cast ? dark : TrahisonPalette.PANEL_SUNKEN,
					cast ? tone : TrahisonPalette.TEXT_MUTED, cast ? TrahisonPalette.TEXT : TrahisonPalette.TEXT_MUTED);
		} else {
			Component hidden = Component.literal("?");
			vw = TrahisonUi.width(hidden) + 10;
			TrahisonUi.pill(g, x + w - vw, y, hidden, TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_MUTED);
		}
		int nx = x;
		if (color != null) {
			TrahisonUi.colorGlyph(g, color, x, y + 2, 8);
			nx += 12;
		}
		TrahisonUi.textEllipsis(g, Component.literal(names), nx, y + 2, x + w - vw - 4 - nx, TrahisonPalette.TEXT);
	}
}
