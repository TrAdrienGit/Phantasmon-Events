package com.mystaria.events.tournament;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * A double elimination bracket drawn as tournament sites do (2026-11-11): the winners bracket in columns, each round
 * centred between the two matches feeding it, the losers bracket below, the grand final (and its reset) after the
 * winners final, elbow lines from each match to the next. A match is a two-line box — face, name, games won — its
 * left edge coloured by its state, the winner bright and the loser dimmed, the local player's matches outlined.
 * Positions follow the backend's {@code DoubleElimination} (rounds from 1, positions from 0).
 */
public final class BracketView {

	public static final int BW = 124;
	public static final int BH = 28;
	private static final int GAP = 28;
	private static final int VG = 10;
	private static final int LABEL = 14;

	/** Where each match sits ({@code bracket|round|position} → x, y), and the whole drawing's size. */
	public record Layout(Map<String, int[]> boxes, int width, int height, int losersTop, int finalX) {
	}

	private BracketView() {
	}

	private static String key(String bracket, int round, int position) {
		return bracket + "|" + round + "|" + position;
	}

	public static Layout layout(TournamentDto t) {
		Map<String, int[]> boxes = new HashMap<>();
		List<TournamentDto.Match> all = t.matches() == null ? List.of() : t.matches();
		int k = all.stream().filter(m -> "WINNERS".equals(m.bracket())).mapToInt(TournamentDto.Match::round).max().orElse(0);
		if (k == 0) {
			return new Layout(boxes, 0, 0, 0, 0);
		}
		int n = 1 << k;
		// Winners: round 1 stacked, every later round centred on the two matches feeding it.
		Map<Integer, int[]> winnersY = new HashMap<>();
		for (int r = 1; r <= k; r++) {
			int count = n >> r;
			int[] ys = new int[count];
			for (int p = 0; p < count; p++) {
				ys[p] = r == 1 ? LABEL + p * (BH + VG) : (winnersY.get(r - 1)[2 * p] + winnersY.get(r - 1)[2 * p + 1]) / 2;
				boxes.put(key("WINNERS", r, p), new int[] { (r - 1) * (BW + GAP), ys[p] });
			}
			winnersY.put(r, ys);
		}
		int winnersBottom = LABEL + (n / 2) * (BH + VG);
		int finalX = k * (BW + GAP);
		int finalY = winnersY.get(k)[0];
		boxes.put(key("FINAL", 1, 0), new int[] { finalX, finalY });
		boxes.put(key("RESET", 1, 0), new int[] { finalX + BW + GAP, finalY });
		// Losers: odd rounds feed the next one straight across, even rounds pair up into the next.
		int losersTop = winnersBottom + 18;
		int width = finalX + 2 * BW + GAP;
		int height = winnersBottom;
		Map<Integer, int[]> losersY = new HashMap<>();
		for (int l = 1; l <= 2 * (k - 1); l++) {
			int count = n >> ((l + 1) / 2 + 1);
			int[] ys = new int[count];
			for (int p = 0; p < count; p++) {
				if (l == 1) {
					ys[p] = losersTop + LABEL + p * (BH + VG);
				} else if (l % 2 == 0) {
					ys[p] = losersY.get(l - 1)[p];
				} else {
					ys[p] = (losersY.get(l - 1)[2 * p] + losersY.get(l - 1)[2 * p + 1]) / 2;
				}
				int x = (l - 1) * (BW + GAP);
				boxes.put(key("LOSERS", l, p), new int[] { x, ys[p] });
				width = Math.max(width, x + BW);
				height = Math.max(height, ys[p] + BH);
			}
			losersY.put(l, ys);
		}
		return new Layout(boxes, width + 4, height + 4, k > 1 ? losersTop : 0, finalX);
	}

	/**
	 * The bracket with its top-left at {@code (ox, oy)}; {@code uuidOf} gives the faces, {@code me} the local player's
	 * name (their matches outlined).
	 */
	public static void render(GuiGraphics g, TournamentDto t, Layout layout, int ox, int oy, Function<String, UUID> uuidOf, String me) {
		MenuUi.Palette c = MenuUi.p();
		List<TournamentDto.Match> all = t.matches() == null ? List.of() : t.matches();
		int k = all.stream().filter(m -> "WINNERS".equals(m.bracket())).mapToInt(TournamentDto.Match::round).max().orElse(0);
		if (k == 0) {
			return;
		}
		// Section titles.
		MenuUi.text(g, Component.translatable("phantasmon_events.tp.bracket.winners"), ox, oy, 200, c.accent());
		MenuUi.text(g, Component.translatable("phantasmon_events.tp.bracket.final"), ox + layout.finalX(), oy, BW * 2, 0xFFFFD84A);
		if (layout.losersTop() > 0) {
			g.fill(ox, oy + layout.losersTop() - 8, ox + layout.width() - 4, oy + layout.losersTop() - 7, c.raised());
			MenuUi.text(g, Component.translatable("phantasmon_events.tp.bracket.losers"), ox, oy + layout.losersTop(), 200, c.danger());
		}
		// Lines first, under the boxes.
		for (TournamentDto.Match m : all) {
			int[] from = layout.boxes().get(key(m.bracket(), m.round(), m.position()));
			int[] to = switch (m.bracket()) {
				case "WINNERS" -> layout.boxes().get(m.round() < k ? key("WINNERS", m.round() + 1, m.position() / 2) : key("FINAL", 1, 0));
				case "LOSERS" -> layout.boxes().get(m.round() == 2 * (k - 1) ? key("FINAL", 1, 0)
						: m.round() % 2 == 1 ? key("LOSERS", m.round() + 1, m.position()) : key("LOSERS", m.round() + 1, m.position() / 2));
				case "FINAL" -> reset(all) ? layout.boxes().get(key("RESET", 1, 0)) : null;
				default -> null;
			};
			if (from == null || to == null || "-".equals(m.player1()) && "-".equals(m.player2())) {
				continue; // a double bye: no match to show
			}
			boolean decided = "DONE".equals(m.status());
			int colour = decided ? c.light() : c.edge();
			int x1 = ox + from[0] + BW;
			int y1 = oy + from[1] + BH / 2;
			int x2 = ox + to[0];
			int y2 = oy + to[1] + BH / 2;
			int mid = "LOSERS".equals(m.bracket()) && m.round() == 2 * (k - 1) ? x1 + 10 : (x1 + x2) / 2;
			g.fill(x1, y1, mid + 1, y1 + 1, colour);
			g.fill(mid, Math.min(y1, y2), mid + 1, Math.max(y1, y2) + 1, colour);
			g.fill(mid, y2, x2, y2 + 1, colour);
		}
		for (TournamentDto.Match m : all) {
			if ("RESET".equals(m.bracket()) && !reset(all) || "-".equals(m.player1()) && "-".equals(m.player2())) {
				continue; // the reset final exists only when the losers bracket's champion won the grand final; no double byes
			}
			int[] box = layout.boxes().get(key(m.bracket(), m.round(), m.position()));
			if (box != null) {
				match(g, m, ox + box[0], oy + box[1], BW, uuidOf, me);
			}
		}
	}

	/** Whether the reset final is played (its players known). */
	private static boolean reset(List<TournamentDto.Match> all) {
		return all.stream().anyMatch(m -> "RESET".equals(m.bracket()) && m.player1() != null && !"-".equals(m.player1()));
	}

	/** One match: two lines (face, name, games won), its state on the left edge. */
	public static void match(GuiGraphics g, TournamentDto.Match m, int x, int y, int w, Function<String, UUID> uuidOf, String me) {
		MenuUi.Palette c = MenuUi.p();
		boolean walkover = m.walkover();
		boolean mine = me != null && (me.equals(m.player1()) || me.equals(m.player2()));
		g.fill(x - 1, y - 1, x + w + 1, y + BH + 1, mine ? c.accent() : c.black());
		g.fill(x, y, x + w, y + BH, walkover ? c.dark() : c.panel());
		MenuUi.raised(g, x, y, x + w, y + BH);
		g.fill(x, y, x + 3, y + BH, stateColour(m.status(), walkover));
		g.fill(x + 3, y + BH / 2, x + w, y + BH / 2 + 1, c.black());
		line(g, m, m.player1(), m.wins1(), x, y, w, uuidOf);
		line(g, m, m.player2(), m.wins2(), x, y + BH / 2, w, uuidOf);
		if ("PLAYING".equals(m.status())) {
			// A blinking dot: being played now.
			if ((System.currentTimeMillis() / 600) % 2 == 0) {
				g.fill(x + w - 5, y + 2, x + w - 2, y + 5, 0xFF4AA3FF);
			}
		}
	}

	private static void line(GuiGraphics g, TournamentDto.Match m, String player, int wins, int x, int y, int w, Function<String, UUID> uuidOf) {
		MenuUi.Palette c = MenuUi.p();
		int h = BH / 2;
		if (player == null || "-".equals(player)) {
			MenuUi.text(g, Component.translatable(player == null ? "phantasmon_events.tp.match.tbd" : "phantasmon_events.tp.match.nobody"),
					x + 18, y + (h - 8) / 2 + 1, w - 40, c.edge());
			return;
		}
		boolean won = player.equals(m.winner());
		boolean lost = m.winner() != null && !won;
		UUID uuid = uuidOf.apply(player);
		if (uuid != null) {
			PhantasmonApi.drawPlayerHead(g, uuid, x + 6, y + (h - 9) / 2, 9);
		}
		if (won) {
			g.fill(x + 3, y, x + w, y + h, 0x1876FFB0);
		}
		Component name = Component.literal(player).withStyle(s -> s.withBold(won));
		MenuUi.text(g, name, x + 18, y + (h - 8) / 2 + 1, w - 42, lost ? c.edge() : c.white());
		boolean scored = !m.walkover() && (!"PENDING".equals(m.status()) || wins > 0);
		if (scored) {
			String score = m.forfeit() && lost ? "F" : String.valueOf(wins);
			int sx = x + w - 14;
			g.fill(sx - 2, y + 1, x + w - 1, y + h - 1, won ? 0xFF1F5E3C : c.dark());
			MenuUi.centered(g, Component.literal(score), sx + 6, y + (h - 8) / 2 + 1, 12, won ? 0xFF76FFB0 : c.muted());
		}
	}

	/** A match state's colour: to play (amber), being played (blue), decided (green), walkover (grey). */
	public static int stateColour(String status, boolean walkover) {
		if (walkover) {
			return MenuUi.p().edge();
		}
		return switch (status) {
			case "PLAYING" -> 0xFF4AA3FF;
			case "DONE" -> 0xFF4CD07D;
			default -> 0xFFFFB23F;
		};
	}
}
