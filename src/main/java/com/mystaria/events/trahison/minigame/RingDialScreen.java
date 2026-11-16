package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.RotaryRing;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code ring_dial}: concentric rings of glyphs, turned until the marked radius (the top) reads the target
 * sequence, one glyph per ring from the outside in. Turning a ring may turn others (links the players discover by
 * turning; the view only tells them when the room asks for it).
 *
 * <p>
 * Gesture: {@code {ring, delta: +1 | -1}}, ring 0 being the outermost, +1 one step clockwise. View:
 * {@code {symbols: [[id...]...], offsets: [n...], showLinks, links?, solved}} (the game master's view adds
 * {@code target}). An offset {@code o} means the ring turned {@code o} steps clockwise from its slot 0 at the top, so the
 * glyph on the marked radius is {@code symbols[ring][(-o) mod n]}. The screen never holds the answer, except for the
 * game master and the offline test, which show it in a side panel.
 *
 * <p>
 * The rings are built when the first view arrives (their number and sizes come from it) and again if a view describes
 * another puzzle. Rules of the optimistic update: a ring the player turns moves at once, and so do the rings of the
 * links the screen knows (all of them offline, only the visible ones online); what the screen does not know moves when
 * the backend's view comes back, which is how the players see a hidden link.
 */
public final class RingDialScreen extends MiniGameScreen {

	private static final int MAX_RADIUS = 84;
	private static final int SIDE_PANEL_W = 76;
	private static final int LEGEND_W = 118;

	/** The rules of one puzzle: what the backend tells (online) or the fake generator draws (offline). */
	private record Link(int from, int to, int steps) {
	}

	private final List<RotaryRing> rings = new ArrayList<>();
	private List<List<String>> symbols = List.of();
	private List<Link> links = List.of();
	private boolean showLinks;
	/** The target, when this screen may show it: offline test or game master's view. Null for players. */
	private List<String> target;
	private int selected;
	private int hubRadius = 12;
	private int clusterX;
	private int clusterY;
	private int radius;

	/** Offline only: the fake puzzle's links (applied locally even when hidden) and answer. */
	private List<Link> hiddenLinks = List.of();
	private List<String> offlineTarget = List.of();

	public RingDialScreen(String step, boolean offline) {
		super("ring_dial", step, offline);
		if (offline) {
			buildFake(new Random());
		}
	}

	// ---------------------------------------------------------------- offline fake generator

	/**
	 * Same recipe as the backend, in miniature: draw glyphs, links and a target, then scramble with legal moves so that
	 * the puzzle is solvable and never starts solved. 4 rings of 8, 7, 6, 6 glyphs, 2 links.
	 */
	private void buildFake(Random rnd) {
		int count = 4;
		List<List<String>> syms = new ArrayList<>();
		int[] sizes = new int[count];
		int[] solved = new int[count];
		List<String> tgt = new ArrayList<>();
		for (int r = 0; r < count; r++) {
			List<String> pool = new ArrayList<>(Glyphs.IDS);
			Collections.shuffle(pool, rnd);
			sizes[r] = Math.max(6, 8 - r);
			syms.add(List.copyOf(pool.subList(0, sizes[r])));
			int slot = rnd.nextInt(sizes[r]);
			tgt.add(syms.get(r).get(slot));
			solved[r] = Math.floorMod(-slot, sizes[r]);
		}
		List<Link> fake = new ArrayList<>();
		fake.add(new Link(0, 2, 1));
		fake.add(new Link(2, 3, -1));
		int[] offsets;
		do {
			offsets = solved.clone();
			for (int m = 0; m < 24; m++) {
				int ring = rnd.nextInt(count);
				int delta = rnd.nextBoolean() ? 1 : -1;
				offsets[ring] = Math.floorMod(offsets[ring] + delta, sizes[ring]);
				for (Link l : fake) {
					if (l.from() == ring) {
						offsets[l.to()] = Math.floorMod(offsets[l.to()] + delta * l.steps(), sizes[l.to()]);
					}
				}
			}
		} while (java.util.Arrays.equals(offsets, solved));
		hiddenLinks = fake;
		offlineTarget = tgt;
		target = tgt;
		buildRings(syms);
		for (int r = 0; r < count; r++) {
			rings.get(r).setPosition(offsets[r]);
		}
	}

	// ---------------------------------------------------------------- building

	private void buildRings(List<List<String>> newSymbols) {
		clearWidgets();
		rings.clear();
		symbols = newSymbols;
		selected = Math.min(selected, Math.max(0, newSymbols.size() - 1));
		for (int i = 0; i < newSymbols.size(); i++) {
			final int index = i;
			int n = newSymbols.get(i).size();
			RotaryRing ring = new RotaryRing(10, 4, n)
					.tone(i % 2 == 0 ? TrahisonPalette.PANEL_RAISED : TrahisonPalette.PANEL)
					.tip(Component.translatable("phantasmon_events.trahison.minigame.ring_dial.ring", i + 1))
					.onStep(delta -> stepped(index, delta));
			ring.painter((g, cx, cy, outer, inner, angle) -> paintRing(g, index, cx, cy, outer, inner, angle));
			rings.add(add(ring));
		}
		for (int i = 0; i < rings.size(); i++) {
			rings.get(i).selected(i == selected);
		}
		if (contentW > 0) {
			layout();
		}
	}

	// ---------------------------------------------------------------- gestures

	/** The player turned ring {@code index} by one stop (the ring already moved). */
	private void stepped(int index, int delta) {
		selected = index;
		sendGesture(Map.of("ring", index, "delta", delta));
		propagate(index, delta, offline() ? hiddenLinks : links);
		if (offline()) {
			checkOfflineSolved();
		}
	}

	/** Turns the rings that ring {@code from} drives, by the links the screen knows. */
	private void propagate(int from, int delta, List<Link> known) {
		for (Link l : known) {
			if (l.from() == from && l.to() < rings.size()) {
				RotaryRing target = rings.get(l.to());
				target.setPosition(target.position() + delta * l.steps());
				target.touch();
			}
		}
	}

	private void checkOfflineSolved() {
		for (int r = 0; r < rings.size(); r++) {
			if (!symbols.get(r).get(topSlot(r)).equals(offlineTarget.get(r))) {
				return;
			}
		}
		succeed();
	}

	private int topSlot(int ring) {
		return Math.floorMod(-rings.get(ring).position(), symbols.get(ring).size());
	}

	// ---------------------------------------------------------------- layout

	@Override
	protected void layout() {
		int reserveRight = target != null ? SIDE_PANEL_W : 0;
		int reserveLeft = showLinks && !links.isEmpty() ? LEGEND_W : 0;
		int areaW = contentW - reserveLeft - reserveRight;
		clusterX = contentX + reserveLeft + areaW / 2;
		clusterY = contentY + contentH / 2;
		radius = Math.max(30, Math.min(MAX_RADIUS, Math.min(contentH / 2 - 8, areaW / 2 - 2)));
		hubRadius = Math.max(10, radius * 17 / 100);
		int count = rings.size();
		if (count == 0) {
			return;
		}
		float thickness = (radius - hubRadius) / (float) count;
		for (int i = 0; i < count; i++) {
			int outer = Math.round(radius - i * thickness);
			int inner = Math.max(hubRadius, Math.round(outer - thickness) + 1);
			rings.get(i).radii(outer, inner).at(clusterX - outer, clusterY - outer, 2 * outer, 2 * outer);
		}
	}

	// ---------------------------------------------------------------- sync

	@Override
	protected void applyView(Map<String, Object> view) {
		List<List<String>> incoming = readSymbols(view.get("symbols"));
		if (!incoming.isEmpty() && !incoming.equals(symbols)) {
			buildRings(incoming);
		}
		List<Object> offsets = PuzzleScreens.asList(view.get("offsets"));
		for (int i = 0; i < rings.size() && i < offsets.size(); i++) {
			if (!rings.get(i).dragging()) {
				rings.get(i).setPosition(PuzzleScreens.asInt(offsets.get(i), rings.get(i).position()));
			}
		}
		boolean links0 = showLinks;
		boolean hadTarget = target != null;
		showLinks = Boolean.TRUE.equals(view.get("showLinks"));
		if (view.get("links") != null) {
			links = readLinks(view.get("links"));
		}
		if (!offline()) {
			List<String> t = readIds(view.get("target"));
			target = t.isEmpty() ? null : t;
		}
		if ((links0 != showLinks || hadTarget != (target != null)) && contentW > 0) {
			layout();
		}
	}

	private static List<List<String>> readSymbols(Object raw) {
		List<List<String>> out = new ArrayList<>();
		for (Object ring : PuzzleScreens.asList(raw)) {
			List<String> ids = readIds(ring);
			if (ids.isEmpty()) {
				return List.of();
			}
			out.add(ids);
		}
		return out;
	}

	private static List<String> readIds(Object raw) {
		List<String> ids = new ArrayList<>();
		for (Object o : PuzzleScreens.asList(raw)) {
			ids.add(PuzzleScreens.asString(o, "?"));
		}
		return ids;
	}

	private static List<Link> readLinks(Object raw) {
		List<Link> out = new ArrayList<>();
		for (Object o : PuzzleScreens.asList(raw)) {
			Map<String, Object> m = PuzzleScreens.asMap(o);
			out.add(new Link(PuzzleScreens.asInt(m.get("from"), 0), PuzzleScreens.asInt(m.get("to"), 0),
					PuzzleScreens.asInt(m.get("steps"), 1)));
		}
		return out;
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		for (Map<String, Object> e : events) {
			if ("turn".equals(e.get("type"))) {
				int i = Math.max(0, Math.min(rings.size() - 1, PuzzleScreens.asInt(e.get("ring"), 0)));
				if (rings.isEmpty()) {
					return;
				}
				rings.get(i).touch();
				MiniGameSounds.detent(0.8f);
				// The pointer sits at the ring's upper right, where the player's hand would be.
				RotaryRing r = rings.get(i);
				int mid = (r.outer() + r.inner()) / 2;
				remoteTouch(player, clusterX + mid * 7 / 10, clusterY - mid * 7 / 10);
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		if (rings.isEmpty()) {
			return;
		}
		int i = (int) ((System.currentTimeMillis() / 3200 + player) % rings.size());
		int delta = player == 1 ? 1 : -1;
		RotaryRing r = rings.get(i);
		r.setPosition(r.position() + delta);
		propagate(i, delta, hiddenLinks);
		checkOfflineSolved();
		onRemote(player, List.of(Map.of("type", "turn", "ring", i, "delta", delta)));
	}

	// ---------------------------------------------------------------- drawing

	/** Draws the glyphs of ring {@code index}: each turns with the ring, its top pointing outwards. */
	private void paintRing(GuiGraphics g, int index, int cx, int cy, int outer, int inner, float angle) {
		List<String> ids = symbols.get(index);
		int n = ids.size();
		float step = 360f / n;
		float mid = (outer + inner) / 2f;
		int size = Math.max(9, Math.min(16, Math.min(outer - inner - 2, (int) (2 * Math.PI * mid / n * 0.8))));
		for (int k = 0; k < n; k++) {
			float a = angle + k * step;
			float turned = ((a % 360f) + 360f) % 360f;
			float onTop = Math.min(turned, 360f - turned); // degrees away from the marked radius
			double rad = Math.toRadians(a);
			float x = cx + (float) (mid * Math.sin(rad));
			float y = cy - (float) (mid * Math.cos(rad));
			int color = isSolved() ? TrahisonPalette.SUCCESS
					: onTop < step / 2 ? TrahisonPalette.ACCENT_HOT : TrahisonPalette.TEXT_SECONDARY;
			Glyphs.draw(g, ids.get(k), x, y, size, a, color);
		}
	}

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		// The hub: a sunken cap in the middle, with a small indicator that pulses while the puzzle is open.
		TrahisonUi.disc(g, clusterX, clusterY, hubRadius, TrahisonPalette.BORDER);
		TrahisonUi.disc(g, clusterX, clusterY, hubRadius - 1, TrahisonPalette.PANEL_SUNKEN);
		int dot = isSolved() ? TrahisonPalette.SUCCESS : TrahisonUi.lerp(TrahisonUi.pulse(2400), TrahisonPalette.ACCENT_DIM, TrahisonPalette.ACCENT);
		TrahisonUi.disc(g, clusterX, clusterY, Math.max(2, hubRadius / 3), dot);
		if (rings.isEmpty()) {
			MenuUi.centered(g, Component.translatable("phantasmon_events.trahison.minigame.waiting"), clusterX, clusterY + hubRadius + 6,
					contentW - 20, TrahisonPalette.TEXT_MUTED);
			return;
		}
		if (showLinks && !links.isEmpty()) {
			drawLegend(g);
		}
		if (target != null) {
			drawTarget(g);
		}
	}

	@Override
	protected void drawOver(GuiGraphics g, int mx, int my) {
		if (rings.isEmpty()) {
			return;
		}
		// The marked radius: a lit lane from the outer rim to the hub, a pointer above it, and a frame around the slot
		// of each ring. Green once solved.
		int color = isSolved() ? TrahisonPalette.SUCCESS : TrahisonPalette.ACCENT;
		int top = clusterY - radius;
		int laneHalf = Math.max(6, (rings.get(0).outer() - rings.get(0).inner()) / 2 + 1);
		g.fill(clusterX - 1, top - 10, clusterX + 1, clusterY - hubRadius, TrahisonUi.withAlpha(color, 0x38));
		for (int row = 0; row < 6; row++) { // downward pointer
			g.fill(clusterX - 5 + row, top - 12 + row, clusterX + 5 - row, top - 11 + row, color);
		}
		for (RotaryRing ring : rings) {
			int outer = ring.outer() - 2;
			int inner = ring.inner() + 2;
			int half = Math.max(5, Math.min(laneHalf, (outer - inner) / 2 + 1));
			int y1 = clusterY - outer;
			int y2 = clusterY - inner;
			int c = TrahisonUi.withAlpha(color, 0xC0);
			g.fill(clusterX - half, y1, clusterX - half + 1, y2, c);
			g.fill(clusterX + half - 1, y1, clusterX + half, y2, c);
			g.fill(clusterX - half, y1, clusterX + half, y1 + 1, c);
			g.fill(clusterX - half, y2 - 1, clusterX + half, y2, c);
		}
	}

	/** Left panel, only when the room tells the links: "Ring 1 also turns ring 3 by +1". */
	private void drawLegend(GuiGraphics g) {
		int x = contentX + 8;
		int y = contentY + 10;
		TrahisonUi.textEllipsis(g, Component.translatable("phantasmon_events.trahison.minigame.ring_dial.links"), x, y, LEGEND_W - 10, TrahisonPalette.ACCENT);
		y += 14;
		for (Link l : links) {
			Component line = Component.translatable("phantasmon_events.trahison.minigame.ring_dial.link", l.from() + 1, l.to() + 1,
					(l.steps() > 0 ? "+" : "") + l.steps());
			TrahisonUi.textEllipsis(g, line, x, y, LEGEND_W - 10, TrahisonPalette.TEXT_SECONDARY);
			y += 12;
		}
	}

	/** Right panel for the offline test and the game master: the answer, one glyph per ring. */
	private void drawTarget(GuiGraphics g) {
		int x = contentX + contentW - SIDE_PANEL_W + 4;
		int y = contentY + 8;
		Component title = Component.translatable(offline() ? "phantasmon_events.trahison.minigame.ring_dial.target_test"
				: "phantasmon_events.trahison.minigame.ring_dial.target_gm");
		TrahisonUi.textEllipsis(g, title, x, y, SIDE_PANEL_W - 8, TrahisonPalette.GAME_MASTER);
		y += 14;
		for (int i = 0; i < target.size(); i++) {
			g.fill(x, y, x + SIDE_PANEL_W - 10, y + 20, TrahisonPalette.BORDER);
			g.fill(x + 1, y + 1, x + SIDE_PANEL_W - 11, y + 19, TrahisonPalette.PANEL_SUNKEN);
			TrahisonUi.textEllipsis(g, Component.literal(String.valueOf(i + 1)), x + 5, y + 6, 16, TrahisonPalette.TEXT_MUTED);
			Glyphs.draw(g, target.get(i), x + 30, y + 10, 14, 0, TrahisonPalette.GAME_MASTER);
			y += 22;
		}
	}

	@Override
	protected boolean key(int key) {
		if (rings.isEmpty()) {
			return false;
		}
		switch (key) {
			case 265 -> { // up: one ring outwards
				select(selected - 1);
				return true;
			}
			case 264 -> { // down: one ring inwards
				select(selected + 1);
				return true;
			}
			case 263 -> { // left: counter-clockwise
				rings.get(selected).turn(-1);
				return true;
			}
			case 262 -> { // right: clockwise
				rings.get(selected).turn(1);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private void select(int index) {
		selected = Math.floorMod(index, rings.size());
		for (int i = 0; i < rings.size(); i++) {
			rings.get(i).selected(i == selected);
		}
		MiniGameSounds.click();
	}
}
