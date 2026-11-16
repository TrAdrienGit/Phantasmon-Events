package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.Widget;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.WireColor;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code wiring}: a panel of terminals to join with wires. Left terminals are numbered; right terminals carry a
 * colour and its symbol (red triangle, green disc, blue square, yellow diamond, white ring: never the colour alone). A clue elsewhere in the room says
 * which colour each numbered terminal must reach; here the group drags wires.
 *
 * <p>
 * Gestures: {@code {op: "connect", left, right}} and {@code {op: "disconnect", left | right}}. View: {@code {wires, right[],
 * links[], wrong, solved}} (the game master's view adds the {@code scheme}, which this screen never shows). A terminal
 * holds one wire. Drag from a free terminal to one on the other side to wire it; drag from a wired terminal to take its
 * wire off (drop on another terminal to move it, anywhere else to remove it); or click a terminal then a terminal on the
 * other side; right-click a terminal to remove its wire. A full panel that is wrong is a short circuit (D39): the backend
 * counts it ({@code wrong}), drops every wire (the next view has empty {@code links}) and the screen shakes. Other players' wires appear in the view, and their touch shows as a cursor in
 * their colour.
 *
 * <p>
 * Offline (debug command) the screen holds a fake scheme and judges the full panel itself, shown in a note.
 */
public final class WiringScreen extends MiniGameScreen {

	private static final List<String> OFFLINE_SCHEME = List.of("red", "yellow", "white", "red");
	private static final List<String> OFFLINE_RIGHT = List.of("white", "red", "red", "yellow");
	private static final int RADIUS = 9;

	private final Panel panel;
	private int wires = 4;
	private List<String> right = new ArrayList<>(OFFLINE_RIGHT);
	private int[] links = new int[4];
	private int wrong = -1;
	private boolean unlocked;
	/** The terminal picked by a plain click, waiting for its partner: {side, index} or null. */
	private int[] selected;

	public WiringScreen(String step, boolean offline) {
		super("wiring", step, offline);
		Arrays.fill(links, -1);
		panel = add(new Panel());
		if (offline) {
			wrong = 0;
		} else {
			wires = 0;
			right = new ArrayList<>();
			links = new int[0];
		}
	}

	// ---------------------------------------------------------------- geometry

	private int leftX() {
		return contentX + 84;
	}

	private int rightX() {
		return contentX + contentW - 84;
	}

	private int termY(int index) {
		int top = contentY + 30;
		int bottom = contentY + contentH - 22;
		return wires <= 1 ? (top + bottom) / 2 : top + index * (bottom - top) / (wires - 1);
	}

	private int termX(int side) {
		return side == 0 ? leftX() : rightX();
	}

	/** The terminal under a point as {side, index}, or null. */
	private int[] terminalAt(double mx, double my) {
		for (int side = 0; side < 2; side++) {
			for (int i = 0; i < wires; i++) {
				double dx = mx - termX(side);
				double dy = my - termY(i);
				if (dx * dx + dy * dy <= (RADIUS + 3) * (RADIUS + 3)) {
					return new int[] { side, i };
				}
			}
		}
		return null;
	}

	private static WireColor colorOf(String name) {
		return WireColor.of(name);
	}

	private int rightOf(int left) {
		return left >= 0 && left < links.length ? links[left] : -1;
	}

	private int leftOfRight(int rightIndex) {
		for (int i = 0; i < links.length; i++) {
			if (links[i] == rightIndex) {
				return i;
			}
		}
		return -1;
	}

	// ---------------------------------------------------------------- gestures

	/** Wires left {@code l} to right {@code r} on screen (a terminal holds one wire), sends it, and judges when offline. */
	private void connect(int l, int r) {
		if (links[l] == r) {
			return;
		}
		for (int i = 0; i < links.length; i++) {
			if (i == l || links[i] == r) {
				links[i] = -1;
			}
		}
		links[l] = r;
		Map<String, Object> in = new LinkedHashMap<>();
		in.put("op", "connect");
		in.put("left", l);
		in.put("right", r);
		sendGesture(in);
		if (offline()) {
			boolean full = Arrays.stream(links).allMatch(v -> v >= 0);
			if (full) {
				boolean ok = true;
				for (int i = 0; i < links.length; i++) {
					ok &= OFFLINE_SCHEME.get(i).equals(right.get(links[i]));
				}
				if (ok) {
					unlocked = true;
					succeed();
				} else {
					wrong++;
					fail();
				}
			}
		}
	}

	private void disconnectLeft(int l) {
		if (links[l] < 0) {
			return;
		}
		links[l] = -1;
		sendGesture(new LinkedHashMap<>(Map.of("op", "disconnect", "left", l)));
	}

	/** A click on two terminals of different sides wires them (the alternative to dragging). */
	private void clickTerminal(int[] t) {
		if (selected == null) {
			selected = t;
			MiniGameSounds.click();
			return;
		}
		if (selected[0] == t[0]) {
			selected = selected[1] == t[1] ? null : t;
			MiniGameSounds.click();
			return;
		}
		int l = selected[0] == 0 ? selected[1] : t[1];
		int r = selected[0] == 0 ? t[1] : selected[1];
		selected = null;
		MiniGameSounds.clunk();
		connect(l, r);
	}

	// ---------------------------------------------------------------- layout

	@Override
	protected void layout() {
		panel.at(contentX, contentY, contentW, contentH);
	}

	// ---------------------------------------------------------------- sync

	@Override
	protected void applyView(Map<String, Object> view) {
		List<String> newRight = new ArrayList<>();
		for (Object o : PuzzleScreens.asList(view.get("right"))) {
			newRight.add(PuzzleScreens.asString(o, "?"));
		}
		wires = Math.max(0, Math.min(8, PuzzleScreens.asInt(view.get("wires"), wires)));
		right = newRight;
		List<Object> l = PuzzleScreens.asList(view.get("links"));
		if (!panel.dragging()) {
			links = new int[wires];
			for (int i = 0; i < wires; i++) {
				links[i] = i < l.size() ? PuzzleScreens.asInt(l.get(i), -1) : -1;
			}
		}
		if (selected != null && selected[1] >= wires) {
			selected = null;
		}
		int w = PuzzleScreens.asInt(view.get("wrong"), 0);
		if (wrong >= 0 && w > wrong) {
			fail();
		}
		wrong = w;
		unlocked = Boolean.TRUE.equals(view.get("solved"));
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		for (Map<String, Object> e : events) {
			String type = PuzzleScreens.asString(e.get("type"), "");
			int left = PuzzleScreens.asInt(e.get("left"), -1);
			int rightIndex = PuzzleScreens.asInt(e.get("right"), -1);
			if (left >= 0 && left < wires && (type.equals("connect") || type.equals("disconnect"))) {
				int tx = type.equals("connect") && rightIndex >= 0 && rightIndex < wires ? rightX() : leftX();
				int ty = type.equals("connect") && rightIndex >= 0 && rightIndex < wires ? termY(rightIndex) : termY(left);
				remoteTouch(player, tx + 10, ty - 6);
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		int i = (int) (System.currentTimeMillis() / 3200 % Math.max(1, wires));
		remoteTouch(player, leftX() + 10, termY(i) - 6);
	}

	// ---------------------------------------------------------------- drawing

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		boolean done = unlocked || isSolved();
		// The plate: a riveted metal strip between the two columns of terminals.
		int px = leftX() - 28;
		int pw = rightX() + 28 - px;
		g.fill(px - 1, contentY + 4, px + pw + 1, contentY + contentH - 4, TrahisonPalette.BORDER);
		g.fillGradient(px, contentY + 5, px + pw, contentY + contentH - 5, TrahisonPalette.PANEL_RAISED, TrahisonPalette.PANEL);
		MenuUi.raised(g, px, contentY + 5, px + pw, contentY + contentH - 5);
		for (int rx : new int[] { px + 4, px + pw - 6 }) {
			for (int ry : new int[] { contentY + 8, contentY + contentH - 10 }) {
				g.fill(rx, ry, rx + 2, ry + 2, TrahisonPalette.EDGE_LIGHT);
			}
		}
		MenuUi.centered(g, Component.translatable("phantasmon_events.trahison.minigame.wiring.left"), leftX(), contentY + 9, 70, TrahisonPalette.TEXT_MUTED);
		MenuUi.centered(g, Component.translatable("phantasmon_events.trahison.minigame.wiring.right"), rightX(), contentY + 9, 70, TrahisonPalette.TEXT_MUTED);
		if (wrong > 0) {
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.wiring.wrong", wrong), contentX + 8, contentY + contentH - 14, 120, TrahisonPalette.TEXT_MUTED);
		}
		if (offline()) {
			StringBuilder scheme = new StringBuilder();
			for (int i = 0; i < OFFLINE_SCHEME.size(); i++) {
				scheme.append(i + 1).append("=").append(Component.translatable("phantasmon_events.trahison.color." + OFFLINE_SCHEME.get(i)).getString()).append(' ');
			}
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.wiring.test_scheme", scheme.toString().trim()), contentX + 8,
					contentY + contentH - 14, contentW - 16, TrahisonPalette.GAME_MASTER);
		}
		// Wires under the terminals.
		int[] t = panel.dragTerminal();
		for (int i = 0; i < links.length && i < wires; i++) {
			int r = links[i];
			if (r >= 0 && r < right.size()) {
				WireColor c = colorOf(right.get(r));
				wire(g, leftX(), termY(i), rightX(), termY(r), c, done);
			}
		}
		if (t != null) {
			int ax = termX(t[0]);
			int ay = termY(t[1]);
			WireColor held = t[0] == 1 && t[1] < right.size() ? colorOf(right.get(t[1])) : panel.heldColor();
			wire(g, ax, ay, panel.mouseX(), panel.mouseY(), held, false);
		}
	}

	/** A wire between two points: a sagging curve in the wire's colour, with its symbol at the right end. */
	private void wire(GuiGraphics g, int x1, int y1, int x2, int y2, WireColor c, boolean done) {
		int tone = done ? TrahisonPalette.SUCCESS : c != null ? c.base : TrahisonPalette.EDGE_LIGHT;
		int dark = done ? TrahisonPalette.SUCCESS_DARK : c != null ? c.dark : TrahisonPalette.EDGE;
		int light = done ? TrahisonPalette.SUCCESS : c != null ? c.light : TrahisonPalette.TEXT_SECONDARY;
		double dx = x2 - x1;
		double dy = y2 - y1;
		double dist = Math.hypot(dx, dy);
		double sag = Math.min(10, dist / 14);
		double c1x = x1 + dx * 0.3;
		double c1y = y1 + dy * 0.1 + sag;
		double c2x = x1 + dx * 0.7;
		double c2y = y2 - dy * 0.1 + sag;
		int steps = (int) Math.max(16, dist / 2);
		for (int pass = 0; pass < 3; pass++) {
			int color = pass == 0 ? TrahisonUi.withAlpha(TrahisonPalette.BORDER, 0xB0) : pass == 1 ? dark : tone;
			int off = pass == 0 ? 2 : 0;
			int size = pass == 2 ? 2 : 4;
			if (pass == 1) {
				size = 3;
			}
			for (int i = 0; i <= steps; i++) {
				double s = i / (double) steps;
				double u = 1 - s;
				double bx = u * u * u * x1 + 3 * u * u * s * c1x + 3 * u * s * s * c2x + s * s * s * x2;
				double by = u * u * u * y1 + 3 * u * u * s * c1y + 3 * u * s * s * c2y + s * s * s * y2;
				int ix = (int) Math.round(bx) - size / 2;
				int iy = (int) Math.round(by) - size / 2 + off;
				g.fill(ix, iy, ix + size, iy + size, color);
			}
		}
		for (int i = 0; i <= steps; i += 2) {
			double s = i / (double) steps;
			double u = 1 - s;
			double bx = u * u * u * x1 + 3 * u * u * s * c1x + 3 * u * s * s * c2x + s * s * s * x2;
			double by = u * u * u * y1 + 3 * u * u * s * c1y + 3 * u * s * s * c2y + s * s * s * y2;
			g.fill((int) Math.round(bx), (int) Math.round(by) - 1, (int) Math.round(bx) + 1, (int) Math.round(by), light);
		}
	}

	private void terminal(GuiGraphics g, int side, int index, boolean hot, boolean done) {
		int cx = termX(side);
		int cy = termY(index);
		boolean wired = side == 0 ? rightOf(index) >= 0 : leftOfRight(index) >= 0;
		boolean pick = selected != null && selected[0] == side && selected[1] == index;
		WireColor c = side == 1 && index < right.size() ? colorOf(right.get(index)) : null;
		TrahisonUi.disc(g, cx, cy, RADIUS + 2, pick ? TrahisonPalette.ACCENT_HOT : hot ? TrahisonPalette.EDGE_LIGHT : TrahisonPalette.BORDER);
		TrahisonUi.disc(g, cx, cy, RADIUS, done ? TrahisonPalette.SUCCESS_DARK : c != null ? c.dark : TrahisonPalette.PANEL_RAISED);
		TrahisonUi.disc(g, cx, cy, RADIUS - 2, c != null ? c.base : TrahisonPalette.PANEL_SUNKEN);
		if (c != null) {
			// Colour and symbol together; the symbol is dark on the colour.
			TrahisonUi.glyph(g, c.glyph, cx - 4, cy - 4, 8, TrahisonPalette.BORDER);
		} else {
			Component n = Component.literal(Integer.toString(index + 1));
			MenuUi.centered(g, n, cx, cy - 4, 14, wired ? TrahisonPalette.ACCENT_HOT : TrahisonPalette.TEXT);
		}
		if (wired) {
			g.fill(cx + (side == 0 ? RADIUS + 3 : -RADIUS - 5), cy - 1, cx + (side == 0 ? RADIUS + 5 : -RADIUS - 3), cy + 1, TrahisonPalette.ACCENT);
		}
	}

	@Override
	protected void drawOver(GuiGraphics g, int mx, int my) {
		boolean done = unlocked || isSolved();
		int[] hotT = terminalAt(mx, my);
		for (int side = 0; side < 2; side++) {
			for (int i = 0; i < wires; i++) {
				terminal(g, side, i, hotT != null && hotT[0] == side && hotT[1] == i, done);
			}
		}
	}

	// ---------------------------------------------------------------- the panel widget

	/** The one widget: the whole panel, which turns presses and drags on terminals into wires. */
	private final class Panel extends Widget {

		/** The terminal the wire being dragged is fixed to ({side, index}), or null. */
		private int[] anchor;
		/** The colour of a wire picked up from a left terminal (the colour of the right one it was on). */
		private WireColor held;
		/** Where the picked-up wire was plugged, to restore it when dropped back on the same terminal: {left, right}. */
		private int[] origin;
		private double mouseX;
		private double mouseY;
		private double pressX;
		private double pressY;
		private boolean moved;
		private int[] pressed;

		boolean dragging() {
			return anchor != null;
		}

		int[] dragTerminal() {
			return anchor;
		}

		WireColor heldColor() {
			return held;
		}

		int mouseX() {
			return (int) mouseX;
		}

		int mouseY() {
			return (int) mouseY;
		}

		@Override
		public boolean hovered(double mx, double my) {
			return enabled && terminalAt(mx, my) != null;
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			int[] t = terminalAt(mx, my);
			if (t == null) {
				if (selected != null) {
					selected = null;
				}
				return false;
			}
			if (button == 1) {
				int l = t[0] == 0 ? t[1] : leftOfRight(t[1]);
				if (l >= 0) {
					MiniGameSounds.clunk();
					disconnectLeft(l);
				}
				return true;
			}
			if (button != 0) {
				return false;
			}
			pressed = t;
			pressX = mx;
			pressY = my;
			mouseX = mx;
			mouseY = my;
			moved = false;
			return true;
		}

		@Override
		public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
			if (pressed == null) {
				return false;
			}
			mouseX = mx;
			mouseY = my;
			if (!moved && Math.hypot(mx - pressX, my - pressY) > 4) {
				moved = true;
				selected = null;
				begin(pressed);
			}
			return true;
		}

		/** Starts dragging: from a free terminal the wire is new; from a wired one the wire is taken off its far end. */
		private void begin(int[] t) {
			int l = t[0] == 0 ? t[1] : leftOfRight(t[1]);
			int r = t[0] == 1 ? t[1] : rightOf(t[1]);
			if (l >= 0 && r >= 0 && links[l] == r) {
				// Picked up: the wire stays fixed at the terminal on the other side of the one we grabbed.
				origin = new int[] { l, r };
				held = r < right.size() ? colorOf(right.get(r)) : null;
				links[l] = -1;
				anchor = t[0] == 0 ? new int[] { 1, r } : new int[] { 0, l };
			} else {
				origin = null;
				held = null;
				anchor = t;
			}
			MiniGameSounds.click();
		}

		@Override
		public boolean mouseReleased(double mx, double my, int button) {
			int[] start = pressed;
			pressed = null;
			if (start == null) {
				return false;
			}
			if (!moved || anchor == null) {
				anchor = null;
				clickTerminal(start);
				return true;
			}
			int[] a = anchor;
			int[] o = origin;
			anchor = null;
			origin = null;
			held = null;
			int[] drop = terminalAt(mx, my);
			if (drop != null && drop[0] != a[0]) {
				int l = a[0] == 0 ? a[1] : drop[1];
				int r = a[0] == 0 ? drop[1] : a[1];
				if (o != null && o[0] == l && o[1] == r) {
					links[l] = r; // dropped back where it was: nothing changed
					MiniGameSounds.clunk();
					return true;
				}
				if (o != null) {
					links[o[0]] = o[1]; // restored, then moved by the gesture below
				}
				MiniGameSounds.clunk();
				connect(l, r);
				return true;
			}
			if (o != null) {
				// Dropped in the void: the wire is removed.
				links[o[0]] = o[1];
				MiniGameSounds.clunk();
				disconnectLeft(o[0]);
			}
			return true;
		}

		@Override
		public void render(GuiGraphics g, int mx, int my) {
			// Everything is drawn by the screen (under and over); the widget only takes the mouse.
			if (anchor != null) {
				mouseX = mx;
				mouseY = my;
			}
		}
	}
}
