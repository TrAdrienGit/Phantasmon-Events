package com.mystaria.events.trahison.screen;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mystaria.events.trahison.Trahison;
import com.mystaria.events.trahison.edit.LayoutDto;
import com.mystaria.events.trahison.map.MapModel;
import com.mystaria.events.trahison.map.MapModel.DoorShape;
import com.mystaria.events.trahison.map.MapModel.FloorInfo;
import com.mystaria.events.trahison.map.MapModel.FloorMap;
import com.mystaria.events.trahison.map.MapModel.Rect;
import com.mystaria.events.trahison.map.MapModel.RoomShape;
import com.mystaria.events.trahison.map.MapModel.Stroke;
import com.mystaria.events.trahison.map.MapNotes;
import com.mystaria.events.trahison.map.NoteBuffer;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.net.MapData;
import com.mystaria.events.trahison.room.Pictures;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.events.trahison.ui.TrahisonUi.GameColor;
import com.mystaria.events.trahison.world.HubBlocks;
import com.mystaria.events.trahison.world.Plan;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The map of the complex (T11), opened with the map key once the game has left its lobby. A window with two tabs: the
 * <b>map</b>, drawn from the plan the client already holds (rooms in flat colours, hatched while the player has never been in
 * them, doors in two colours that change live, a tab per floor, the player's own arrow), and the <b>notes</b>, a page of free
 * text. On the map a pen and an eraser annotate; strokes and notes are kept by the backend for this player alone
 * ({@link MapNotes}) and, once the game is over, everybody's can be browsed (D15). The wheel zooms (over the tabs of the floors it
 * changes floor), the right button drags, "follow" keeps the player in the middle. A hand-made picture per floor
 * ({@code textures/trahison/map/<floor id>.png}) replaces the flat colours. The screen holds no rule: what a viewer may know is
 * decided by the backend (charte-ui.md section 14). Client thread.
 */
public final class MapScreen extends TrahisonScreen {

	private static final String T = "phantasmon_events.trahison.map.";
	private static final int W = 720;
	private static final int H = 420;
	private static final int ROW = 14;
	private static final int GAP = 4;
	/** The pen inks: palette tokens, never a game colour (a drawing is not a door). */
	private static final int[] INKS = { TrahisonPalette.ACCENT_HOT, TrahisonPalette.ALLY, TrahisonPalette.BETRAY, TrahisonPalette.TEXT };
	private static final double MAX_ZOOM = 16;
	private static final double MIN_ZOOM = 0.05;
	private static final int LINE_H = 10;

	private enum Tab { MAP, NOTES }

	private enum Tool { PEN, ERASER }

	/** One control of the toolbar: its width and how it is drawn at a place. */
	private record Item(int width, java.util.function.BiConsumer<Integer, Integer> paint) {
	}

	// Kept from one opening to the next.
	private static Tab tab = Tab.MAP;
	private static Tool tool = Tool.PEN;
	private static int ink;
	private static boolean follow;
	private static String lastFloor;

	private final NoteBuffer buffer = new NoteBuffer(MapNotes.MAX_NOTES, s -> TrahisonUi.width(Component.literal(s)));
	private boolean bufferLoaded;
	/** -1: my own page; else an index into {@link MapNotes#everyone()} (once the game is over). */
	private int viewing = -1;

	private List<FloorInfo> floors = List.of();
	private int floor;
	private double zoom = 1;
	private double centerX;
	private double centerZ;
	private boolean fitted;
	private boolean panning;
	private boolean drawing;
	private Stroke stroke;
	private int notesScroll;

	// Geometry of this frame, for the mouse.
	private int ax;
	private int ay;
	private int aw;
	private int ah;
	private int tabsX;
	private int tabsY;
	private int tabsW;
	private boolean legendInline;
	private int notesX;
	private int notesY;
	private int notesW;
	private int notesH;
	private int notesTextW = 100;

	// What the map shows, rebuilt when the plan, the floor or the room names change.
	private LayoutDto cachePlan;
	private int cacheFloor = -1;
	private Object cacheNames;
	private FloorMap map;

	public MapScreen() {
		super(Component.translatable(T + "title"), TrahisonPalette.ACCENT);
	}

	/** Opens the map when there is one to show; otherwise says why in the action bar. */
	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		View v = GameView.current();
		if (v == null || !v.hasGame() || v.phase() == Phase.LOBBY) {
			mc.player.displayClientMessage(Component.translatable(T + "unavailable"), true);
			return;
		}
		if (Plan.get(v.hub(), v.layoutVersion()) == null) {
			mc.player.displayClientMessage(Component.translatable(T + "loading"), true);
			return;
		}
		mc.setScreen(new MapScreen());
	}

	// ---------------------------------------------------------------- state

	private LayoutDto plan() {
		View v = GameView.current();
		return v == null || !v.hasGame() ? null : Plan.get(v.hub(), v.layoutVersion());
	}

	private boolean over() {
		View v = GameView.current();
		return v != null && v.phase() == Phase.ENDED;
	}

	/** Whether the pen and the notes work: his own page, the game still on, the backend's copy loaded. */
	private boolean editable() {
		return viewing < 0 && !over() && MapNotes.loaded();
	}

	private FloorInfo floorInfo() {
		return floors.get(Math.max(0, Math.min(floor, floors.size() - 1)));
	}

	private String floorId() {
		return floorInfo().id();
	}

	/** The strokes shown on a floor: mine, or those of the person being browsed. */
	private List<Stroke> strokes(String floorId) {
		if (viewing >= 0) {
			List<MapNotes.Person> people = MapNotes.everyone();
			if (people == null || viewing >= people.size()) {
				return List.of();
			}
			return people.get(viewing).strokes().getOrDefault(floorId, List.of());
		}
		return MapNotes.strokes(floorId);
	}

	@Override
	protected void init() {
		super.init();
		LayoutDto plan = plan();
		if (plan == null) {
			return;
		}
		floors = MapModel.floors(plan);
		floor = 0;
		for (int i = 0; i < floors.size(); i++) {
			if (floors.get(i).id().equals(lastFloor)) {
				floor = i;
			}
		}
		double[] me = ownPosition();
		if (me != null) {
			int here = MapModel.floorAt(floors, me[3]);
			if (here >= 0) {
				floor = here; // the map opens on the floor the player stands on
			}
		}
		fitted = false;
	}

	@Override
	public void removed() {
		super.removed();
		lastFloor = floors.isEmpty() ? null : floorId();
	}

	// ---------------------------------------------------------------- frame

	@Override
	protected void drawStage(GuiGraphics g, int mouseX, int mouseY) {
		LayoutDto plan = plan();
		if (plan == null || floors.isEmpty()) {
			onClose();
			return;
		}
		int[] r = TrahisonUi.stage(width, height, W, H);
		int x = r[0];
		int y = r[1];
		int w = r[2];
		int h = r[3];
		View v = GameView.current();
		TrahisonUi.titledPanel(g, x, y, w, h, Component.translatable(T + "title"), TrahisonPalette.ACCENT);
		drawBadge(g, v, x + w - 8, y + 5);
		if (!bufferLoaded && MapNotes.loaded()) {
			buffer.set(MapNotes.notes());
			bufferLoaded = true;
		}
		int left = x + 8;
		int right = x + w - 8;
		int top = y + 26;
		// ---- row 1: the two tabs, then the floors
		int tabX = left;
		for (Tab t : Tab.values()) {
			Component label = Component.translatable(T + "tab." + t.name().toLowerCase(java.util.Locale.ROOT));
			int tw = Math.max(48, TrahisonUi.width(label) + 14);
			TrahisonUi.tab(g, tabX, top, tw, ROW, label, t == tab, ui.hovered(tabX, top, tw, ROW));
			Tab target = t;
			ui.zone(tabX, top, tw, ROW, b -> switchTab(target));
			tabX += tw + 2;
		}
		tabsW = 0;
		if (tab == Tab.MAP && floors.size() > 1) {
			int avail = right - (tabX + 8);
			int fw = Math.max(24, Math.min(72, (avail - 2 * (floors.size() - 1)) / floors.size()));
			int total = floors.size() * fw + 2 * (floors.size() - 1);
			tabsX = right - total;
			tabsY = top;
			tabsW = total;
			for (int i = 0; i < floors.size(); i++) {
				int fx = tabsX + i * (fw + 2);
				int index = i;
				TrahisonUi.tab(g, fx, top, fw, ROW, floorLabel(floors.get(i)), i == floor, ui.hovered(fx, top, fw, ROW));
				ui.zone(fx, top, fw, ROW, b -> selectFloor(index));
			}
		}
		int contentTop = top + ROW + GAP;
		int bottom = y + h - 8;
		if (tab == Tab.MAP) {
			drawMapTab(g, plan, v, left, contentTop, right - left, bottom - contentTop, mouseX, mouseY);
		} else {
			drawNotesTab(g, left, contentTop, right - left, bottom - contentTop);
		}
	}

	/** A pill in the title bar: this viewer watches everything, or his page is not loaded yet. */
	private void drawBadge(GuiGraphics g, View v, int right, int py) {
		if (v == null) {
			return;
		}
		if (MapData.exploredAll() && v.viewer().kind() != ViewerKind.PLAYER) {
			Component label = Component.translatable(T + "badge.watching");
			int w = TrahisonUi.width(label) + 10;
			TrahisonUi.pill(g, right - w, py, label, TrahisonPalette.GAME_MASTER_DARK, TrahisonPalette.GAME_MASTER);
		} else if (!MapNotes.loaded()) {
			Component label = Component.translatable(T + "badge.loading");
			int w = TrahisonUi.width(label) + 10;
			TrahisonUi.pill(g, right - w, py, label, TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_MUTED);
		}
	}

	private Component floorLabel(FloorInfo f) {
		return f.name() == null ? Component.translatable(T + "floor.main") : Component.literal(f.name());
	}

	private void switchTab(Tab target) {
		tab = target;
		drawing = false;
		stroke = null;
		panning = false;
	}

	private void selectFloor(int index) {
		if (index != floor && index >= 0 && index < floors.size()) {
			floor = index;
			fitted = false;
			follow = false;
		}
	}

	// ---------------------------------------------------------------- map tab

	private void drawMapTab(GuiGraphics g, LayoutDto plan, View v, int x, int y, int w, int h, int mouseX, int mouseY) {
		legendInline = h >= 190;
		int rows = toolbar(g, x, y, w);
		int areaTop = y + rows * (ROW + 2) + 2;
		int legendH = legendInline ? ROW + 2 : 0;
		ax = x;
		ay = areaTop;
		aw = w;
		ah = Math.max(24, y + h - areaTop - legendH);
		if (!fitted) {
			fit();
		}
		double[] me = ownPosition();
		if (follow && me != null) {
			int here = MapModel.floorAt(floors, me[3]);
			if (here >= 0) {
				floor = here;
			}
			centerX = me[0];
			centerZ = me[1];
		}
		rebuild(plan);
		drawArea(g, v, me, mouseX, mouseY);
		if (legendInline) {
			drawLegend(g, x, y + h - ROW, x + w);
		}
	}

	/** Lays the tools out, flowing onto a second row when the window is narrow; returns how many rows they took. */
	private int toolbar(GuiGraphics g, int x, int y, int w) {
		boolean edit = editable();
		boolean ended = over();
		List<Item> items = new ArrayList<>();
		if (ended) {
			items.add(new Item(130, (px, py) -> viewButton(g, px, py, 130)));
		} else {
			Component pen = Component.translatable(T + "tool.pen");
			Component eraser = Component.translatable(T + "tool.eraser");
			int penW = TrahisonUi.width(pen) + 12;
			int eraserW = TrahisonUi.width(eraser) + 12;
			Component why = edit ? null : Component.translatable(T + (MapNotes.loaded() ? "tool.readonly" : "tool.wait"));
			items.add(new Item(penW, (px, py) -> ui.button(g, px, py, penW, ROW, pen, tool == Tool.PEN ? ButtonKind.PRIMARY : ButtonKind.DEFAULT,
					edit ? Component.translatable(T + "tool.pen.tip") : why, edit ? () -> tool = Tool.PEN : null)));
			items.add(new Item(eraserW, (px, py) -> ui.button(g, px, py, eraserW, ROW, eraser, tool == Tool.ERASER ? ButtonKind.PRIMARY : ButtonKind.DEFAULT,
					edit ? Component.translatable(T + "tool.eraser.tip") : why, edit ? () -> tool = Tool.ERASER : null)));
			items.add(new Item(INKS.length * (ROW + 2) - 2, (px, py) -> inks(g, px, py, edit)));
		}
		Component followLabel = Component.translatable(T + "follow");
		int followW = TrahisonUi.width(followLabel) + 12;
		items.add(new Item(followW, (px, py) -> ui.button(g, px, py, followW, ROW, followLabel, follow ? ButtonKind.PRIMARY : ButtonKind.DEFAULT,
				Component.translatable(T + "follow.tip"), () -> {
					follow = !follow;
					fitted = follow;
				})));
		Component fitLabel = Component.translatable(T + "fit");
		int fitW = TrahisonUi.width(fitLabel) + 12;
		items.add(new Item(fitW, (px, py) -> ui.button(g, px, py, fitW, ROW, fitLabel, ButtonKind.DEFAULT, Component.translatable(T + "fit.tip"), () -> {
			follow = false;
			fit();
		})));
		if (!ended) {
			Component clear = Component.translatable(T + "clear");
			int clearW = TrahisonUi.width(clear) + 12;
			boolean any = !strokes(floorId()).isEmpty();
			items.add(new Item(clearW, (px, py) -> ui.button(g, px, py, clearW, ROW, clear, ButtonKind.DANGER, Component.translatable(T + "clear.tip"),
					edit && any ? this::askClear : null)));
		}
		if (!legendInline) {
			items.add(new Item(16, (px, py) -> ui.button(g, px, py, 16, ROW, Component.literal("?"), ButtonKind.DEFAULT,
					Component.translatable(T + "legend.all"), null)));
		}
		int cx = x;
		int cy = y;
		int rowCount = 1;
		for (Item item : items) {
			if (cx > x && cx + item.width() > x + w) {
				cx = x;
				cy += ROW + 2;
				rowCount++;
			}
			item.paint().accept(cx, cy);
			cx += item.width() + GAP;
		}
		return rowCount;
	}

	private void inks(GuiGraphics g, int x, int y, boolean edit) {
		for (int i = 0; i < INKS.length; i++) {
			int ix = x + i * (ROW + 2);
			boolean selected = i == ink && tool == Tool.PEN;
			g.fill(ix - 1, y - 1, ix + ROW + 1, y + ROW + 1, selected ? TrahisonPalette.TEXT : TrahisonPalette.BORDER);
			g.fill(ix, y, ix + ROW, y + ROW, edit ? INKS[i] : TrahisonUi.withAlpha(INKS[i], 0x60));
			MenuUi.outline(g, ix, y, ix + ROW, y + ROW, TrahisonPalette.BORDER);
			if (selected) {
				g.fill(ix + ROW / 2 - 1, y + ROW / 2 - 1, ix + ROW / 2 + 1, y + ROW / 2 + 1, TrahisonPalette.BORDER);
			}
			ui.tip(ix, y, ROW, ROW, Component.translatable(T + "ink." + i));
			int index = i;
			ui.zone(ix, y, ROW, ROW, edit ? b -> {
				ink = index;
				tool = Tool.PEN;
			} : null);
		}
	}

	/** The button that goes from one person's page to the next once the game is over (D15). */
	private void viewButton(GuiGraphics g, int x, int y, int w) {
		List<MapNotes.Person> people = MapNotes.everyone();
		Component label = viewing < 0 || people == null || viewing >= people.size() ? Component.translatable(T + "mine")
				: Component.translatable(T + "of", people.get(viewing).name());
		ui.button(g, x, y, w, ROW, label, ButtonKind.PRIMARY, Component.translatable(T + (people == null ? "wait_all" : "browse.tip")),
				people == null || people.isEmpty() ? null : () -> {
					viewing = viewing + 1 >= people.size() ? -1 : viewing + 1;
					drawing = false;
					notesScroll = 0;
				});
	}

	private void askClear() {
		String id = floorId();
		modal = new Modal(Component.translatable(T + "clear.title"), Component.translatable(T + "clear.text", floorLabel(floorInfo())),
				Component.translatable(T + "clear"), true, () -> {
					MapNotes.strokes(id).clear();
					MapNotes.changed();
				});
	}

	/** Zoom and centre that show the whole floor. */
	private void fit() {
		LayoutDto plan = plan();
		FloorMap m = plan == null ? null : MapModel.build(plan, floorInfo(), MapData.rooms());
		if (m == null || m.bounds() == null) {
			zoom = 2;
			centerX = 0;
			centerZ = 0;
		} else {
			Rect b = m.bounds();
			double usableW = Math.max(16, aw > 0 ? aw : 200);
			double usableH = Math.max(16, ah > 0 ? ah : 120);
			zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min(usableW / (b.width() + 6), usableH / (b.depth() + 6))));
			centerX = (b.minX() + b.maxX() + 1) / 2.0;
			centerZ = (b.minZ() + b.maxZ() + 1) / 2.0;
		}
		fitted = true;
	}

	private void rebuild(LayoutDto plan) {
		Object names = MapData.rooms();
		if (map == null || plan != cachePlan || floor != cacheFloor || names != cacheNames) {
			cachePlan = plan;
			cacheFloor = floor;
			cacheNames = names;
			map = MapModel.build(plan, floorInfo(), MapData.rooms());
		}
	}

	// ---- the drawing

	private double sx(double x) {
		return ax + aw / 2.0 + (x - centerX) * zoom;
	}

	private double sz(double z) {
		return ay + ah / 2.0 + (z - centerZ) * zoom;
	}

	private double wx(double px) {
		return centerX + (px - ax - aw / 2.0) / zoom;
	}

	private double wz(double pz) {
		return centerZ + (pz - ay - ah / 2.0) / zoom;
	}

	/** A rectangle of blocks on screen: {@code {x0, y0, x1, y1}}, at least one pixel each way. */
	private int[] px(Rect r) {
		int x0 = (int) Math.floor(sx(r.minX()));
		int z0 = (int) Math.floor(sz(r.minZ()));
		int x1 = Math.max(x0 + 1, (int) Math.floor(sx(r.maxX() + 1)));
		int z1 = Math.max(z0 + 1, (int) Math.floor(sz(r.maxZ() + 1)));
		return new int[] { x0, z0, x1, z1 };
	}

	private boolean offscreen(int[] p) {
		return p[2] < ax || p[0] > ax + aw || p[3] < ay || p[1] > ay + ah;
	}

	private void drawArea(GuiGraphics g, View v, double[] me, int mouseX, int mouseY) {
		g.fill(ax - 1, ay - 1, ax + aw + 1, ay + ah + 1, TrahisonPalette.BORDER);
		g.fill(ax, ay, ax + aw, ay + ah, TrahisonPalette.BG_DEEP);
		ui.clip(g, ax, ay, ax + aw, ay + ah);
		TrahisonUi.dots(g, ax, ay, ax + aw, ay + ah);
		boolean inside = TrahisonUi.inside(mouseX, mouseY, ax, ay, aw, ah);
		Component hover = null;
		if (map != null && !map.isEmpty()) {
			// An optional hand-made picture of the floor, over the floor's bounds, replaces the flat colours.
			ResourceLocation picture = ResourceLocation.fromNamespaceAndPath(Pictures.NAMESPACE, "textures/trahison/map/" + Pictures.clean(floorId()) + ".png");
			int[] size = Pictures.size(picture);
			boolean flat = size == null;
			if (!flat && map.bounds() != null) {
				int[] b = px(map.bounds());
				g.blit(picture, b[0], b[1], b[2] - b[0], b[3] - b[1], 0, 0, size[0], size[1], size[0], size[1]);
			}
			for (RoomShape room : map.rooms()) {
				drawRoom(g, room, flat);
			}
			for (RoomShape room : map.rooms()) {
				drawLabel(g, room);
			}
			for (DoorShape door : map.doors()) {
				drawDoor(g, door);
			}
			if (inside) {
				hover = hoverText(mouseX, mouseY);
			}
		} else {
			MenuUi.centered(g, Component.translatable(T + "empty"), ax + aw / 2, ay + ah / 2 - 4, aw - 16, TrahisonPalette.TEXT_MUTED);
		}
		for (Stroke s : strokes(floorId())) {
			drawStroke(g, s);
		}
		if (stroke != null) {
			drawStroke(g, stroke);
		}
		drawCursors(g, v, me, mouseX, mouseY);
		if (inside && editable() && tool == Tool.ERASER) {
			TrahisonUi.ring(g, mouseX, mouseY, 5, 4, TrahisonPalette.TEXT);
		}
		if (editable() && MapNotes.floorFull(floorId())) {
			Component full = Component.translatable(T + "full");
			int fw = TrahisonUi.width(full) + 10;
			TrahisonUi.pill(g, ax + aw - fw - 4, ay + 4, full, TrahisonPalette.DANGER_DARK, TrahisonPalette.TEXT);
		}
		ui.unclip(g);
		if (hover != null && ui.tooltip == null) {
			ui.tooltip = hover;
		}
	}

	private static int roomFill(String type) {
		return switch (type == null ? "" : type) {
			case "room_slot" -> TrahisonUi.lerp(0.45f, TrahisonPalette.PANEL, TrahisonPalette.BLUE_DARK);
			case "hall" -> TrahisonPalette.PANEL_RAISED;
			case "doors" -> TrahisonUi.lerp(0.4f, TrahisonPalette.PANEL, TrahisonPalette.GAME_MASTER_DARK);
			case "vote" -> TrahisonUi.lerp(0.5f, TrahisonPalette.PANEL, TrahisonPalette.BETRAY_DARK);
			case "cabin" -> TrahisonUi.lerp(0.3f, TrahisonPalette.PANEL, TrahisonPalette.BETRAY_DARK);
			case "final" -> TrahisonUi.lerp(0.5f, TrahisonPalette.PANEL, TrahisonPalette.SUCCESS_DARK);
			case "alcove" -> TrahisonPalette.PANEL_SUNKEN;
			default -> TrahisonPalette.PANEL;
		};
	}

	private void drawRoom(GuiGraphics g, RoomShape room, boolean flat) {
		boolean hatched = room.isRoom() && !MapData.explored(room.zone());
		for (Rect r : room.rects()) {
			int[] p = px(r);
			if (offscreen(p)) {
				continue;
			}
			if (flat) {
				g.fill(p[0], p[1], p[2], p[3], hatched ? TrahisonPalette.PANEL_SUNKEN : roomFill(room.type()));
			} else if (hatched) {
				g.fill(p[0], p[1], p[2], p[3], TrahisonUi.withAlpha(TrahisonPalette.PANEL_SUNKEN, 0xB0));
			}
			if (hatched) {
				hatch(g, p[0], p[1], p[2], p[3]);
			}
			MenuUi.outline(g, p[0], p[1], p[2], p[3], hatched ? TrahisonPalette.EDGE : TrahisonPalette.EDGE_LIGHT);
		}
	}

	/** Diagonal lines over a rectangle, drawn once rotated and clipped to it. */
	private void hatch(GuiGraphics g, int x0, int z0, int x1, int z1) {
		int w = x1 - x0;
		int h = z1 - z0;
		if (w < 4 || h < 4) {
			return;
		}
		int reach = (w + h) / 2 + 2;
		g.enableScissor(Math.max(x0, ax), Math.max(z0, ay), Math.min(x1, ax + aw), Math.min(z1, ay + ah));
		g.pose().pushPose();
		g.pose().translate((x0 + x1) / 2f, (z0 + z1) / 2f, 0);
		g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45f));
		for (int t = -reach; t < reach; t += 6) {
			g.fill(-reach, t, reach, t + 1, TrahisonUi.withAlpha(TrahisonPalette.EDGE, 0xA0));
		}
		g.pose().popPose();
		g.disableScissor();
	}

	private void drawLabel(GuiGraphics g, RoomShape room) {
		Component name = roomName(room);
		if (name == null) {
			return;
		}
		Rect big = room.rects().get(0);
		for (Rect r : room.rects()) {
			if ((long) r.width() * r.depth() > (long) big.width() * big.depth()) {
				big = r;
			}
		}
		int[] p = px(big);
		if (p[2] - p[0] < TrahisonUi.width(name) + 6 || p[3] - p[1] < 14) {
			return; // too small on screen: the name hides, the tooltip still tells it
		}
		boolean hatched = room.isRoom() && !MapData.explored(room.zone());
		MenuUi.centered(g, name, (p[0] + p[2]) / 2, (p[1] + p[3]) / 2 - 4, p[2] - p[0] - 6, hatched ? TrahisonPalette.TEXT_MUTED : TrahisonPalette.TEXT);
	}

	/** A room's name: the room drawn there, else its place, else the kind of zone; null for a zone with no name to give. */
	private static Component roomName(RoomShape room) {
		if (room.label() != null) {
			return Component.literal(room.label());
		}
		return switch (room.type() == null ? "" : room.type()) {
			case "hall", "corridor", "doors", "vote", "cabin", "final", "alcove" -> Component.translatable(T + "zone." + room.type());
			default -> room.isRoom() ? Component.literal(room.zone()) : null;
		};
	}

	private void drawDoor(GuiGraphics g, DoorShape door) {
		int[] p = px(door.rect());
		if (offscreen(p)) {
			return;
		}
		int x0 = p[0];
		int x1 = p[2];
		int z0 = p[1];
		int z1 = p[3];
		int cx = (x0 + x1) / 2;
		int cz = (z0 + z1) / 2;
		boolean alongX = x1 - x0 >= z1 - z0;
		// At least 4 px thick across, so a door stays visible when the map is zoomed out.
		if (alongX && z1 - z0 < 4) {
			z0 = cz - 2;
			z1 = cz + 2;
		} else if (!alongX && x1 - x0 < 4) {
			x0 = cx - 2;
			x1 = cx + 2;
		}
		Boolean open = GameView.orEmpty().doorsOpen().get(door.id());
		if (open == null) {
			g.fill(x0 - 1, z0 - 1, x1 + 1, z1 + 1, TrahisonPalette.BORDER);
			g.fill(x0, z0, x1, z1, TrahisonPalette.TEXT_MUTED);
		} else if (!open) {
			g.fill(x0 - 1, z0 - 1, x1 + 1, z1 + 1, TrahisonPalette.BORDER);
			g.fill(x0, z0, x1, z1, TrahisonPalette.DANGER);
		} else {
			// Open: the leaf is gone and only the two jambs remain, so the state never rests on the colour alone.
			int jamb = Math.max(2, (alongX ? x1 - x0 : z1 - z0) / 4);
			int ax1 = alongX ? x0 + jamb : x1;
			int az1 = alongX ? z1 : z0 + jamb;
			int bx0 = alongX ? x1 - jamb : x0;
			int bz0 = alongX ? z0 : z1 - jamb;
			g.fill(x0 - 1, z0 - 1, ax1 + 1, az1 + 1, TrahisonPalette.BORDER);
			g.fill(bx0 - 1, bz0 - 1, x1 + 1, z1 + 1, TrahisonPalette.BORDER);
			g.fill(x0, z0, ax1, az1, TrahisonPalette.SUCCESS);
			g.fill(bx0, bz0, x1, z1, TrahisonPalette.SUCCESS);
		}
		// A colour door carries the symbols of its colours once it is large enough to read them.
		if ("color".equals(door.type()) && zoom >= 3) {
			int gx = cx - door.colors().size() * 4;
			for (String name : door.colors()) {
				GameColor gc = colorOf(name);
				if (gc != null) {
					TrahisonUi.glyph(g, gc.glyph, gx, z0 - 10, 8, gc.light);
				}
				gx += 8;
			}
		}
	}

	private static GameColor colorOf(String name) {
		try {
			return GameColor.valueOf(name.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private void drawStroke(GuiGraphics g, Stroke s) {
		int color = INKS[Math.floorMod(s.color, INKS.length)];
		List<Double> p = s.points;
		if (p.size() == 2) {
			TrahisonUi.disc(g, (int) Math.round(sx(p.get(0))), (int) Math.round(sz(p.get(1))), 2, color);
			return;
		}
		for (int i = 0; i + 3 < p.size(); i += 2) {
			double x1 = sx(p.get(i));
			double z1 = sz(p.get(i + 1));
			double x2 = sx(p.get(i + 2));
			double z2 = sz(p.get(i + 3));
			if (Math.max(x1, x2) < ax || Math.min(x1, x2) > ax + aw || Math.max(z1, z2) < ay || Math.min(z1, z2) > ay + ah) {
				continue;
			}
			double len = Math.hypot(x2 - x1, z2 - z1);
			if (len < 0.5) {
				continue;
			}
			int half = (int) Math.ceil(len / 2) + 1;
			TrahisonUi.rotatedFill(g, (int) Math.round((x1 + x2) / 2), (int) Math.round((z1 + z2) / 2),
					(float) Math.toDegrees(Math.atan2(z2 - z1, x2 - x1)), -half, -1, half, 1, color);
		}
	}

	// ---- cursors

	/** {@code {hub x, hub z, yaw, world y}} of the local player, or null when he is not in the hub. */
	private static double[] ownPosition() {
		LocalPlayer player = Minecraft.getInstance().player;
		var anchor = PhantasmonApi.hubs() == null ? null : PhantasmonApi.hubs().joinedAnchor();
		if (player == null || anchor == null) {
			return null;
		}
		HubBlocks blocks = HubBlocks.of(anchor);
		return new double[] { blocks.hubX(player.getX()), blocks.hubZ(player.getZ()), player.getYRot(), player.getY() };
	}

	private void drawCursors(GuiGraphics g, View v, double[] me, int mouseX, int mouseY) {
		if (over() || v == null) {
			return;
		}
		// The others, for those who watch: a disc in the bracelet's colour with its symbol.
		for (MapData.Cursor c : MapData.cursors()) {
			if (MapModel.floorAt(floors, c.y()) != floor) {
				continue;
			}
			int cx = (int) Math.round(sx(c.x()));
			int cz = (int) Math.round(sz(c.z()));
			var bracelet = v.bracelet(c.index());
			GameColor color = bracelet == null ? null : bracelet.color();
			TrahisonUi.disc(g, cx, cz, 6, TrahisonPalette.BORDER);
			TrahisonUi.disc(g, cx, cz, 5, color == null ? TrahisonPalette.GHOST : color.dark);
			if (color != null) {
				TrahisonUi.glyph(g, color.glyph, cx - 3, cz - 3, 6, color.light);
			}
			if (TrahisonUi.inside(mouseX, mouseY, cx - 6, cz - 6, 12, 12)) {
				ui.tooltip = Component.translatable(T + "player", v.nameOf(c.index()));
			}
		}
		// My own arrow, in my bracelet's colour: it points where I look (north is up).
		if (me == null || MapModel.floorAt(floors, me[3]) != floor) {
			return;
		}
		var own = v.ownBracelet();
		GameColor color = own == null ? null : own.color();
		int cx = (int) Math.round(sx(me[0]));
		int cz = (int) Math.round(sz(me[1]));
		double yaw = Math.toRadians(me[2]);
		float degrees = (float) Math.toDegrees(Math.atan2(-Math.sin(yaw), -Math.cos(yaw)));
		g.pose().pushPose();
		g.pose().translate(cx, cz, 0);
		g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
		int fill = color == null ? TrahisonPalette.ACCENT : color.base;
		for (int row = 0; row < 12; row++) {
			int half = (row + 1) / 2;
			g.fill(-half - 1, -7 + row, half + 2, -6 + row, TrahisonPalette.BORDER);
		}
		for (int row = 0; row < 10; row++) {
			int half = (row + 1) / 2;
			g.fill(-half, -6 + row, half + 1, -5 + row, fill);
		}
		g.pose().popPose();
		if (color != null) {
			TrahisonUi.glyph(g, color.glyph, cx + 5, cz + 3, 6, color.light);
		}
		if (TrahisonUi.inside(mouseX, mouseY, cx - 7, cz - 7, 14, 14)) {
			ui.tooltip = Component.translatable(T + "you");
		}
	}

	// ---- hover

	private Component hoverText(int mouseX, int mouseY) {
		for (DoorShape door : map.doors()) {
			int[] p = px(door.rect());
			if (TrahisonUi.inside(mouseX, mouseY, p[0] - 2, p[1] - 2, p[2] - p[0] + 4, p[3] - p[1] + 4)) {
				Boolean open = GameView.orEmpty().doorsOpen().get(door.id());
				Component state = Component.translatable(T + (open == null ? "door.unknown" : open ? "door.open" : "door.closed"));
				if ("color".equals(door.type()) && door.colors().size() == 2) {
					return Component.translatable(T + "door.colors", Component.translatable("phantasmon_events.trahison.color." + door.colors().get(0)),
							Component.translatable("phantasmon_events.trahison.color." + door.colors().get(1)), state);
				}
				return Component.translatable(T + "door", state);
			}
		}
		double x = wx(mouseX);
		double z = wz(mouseY);
		for (RoomShape room : map.rooms()) {
			if (room.contains(x, z)) {
				Component name = roomName(room);
				if (name == null) {
					return null;
				}
				return room.isRoom() && !MapData.explored(room.zone()) ? Component.translatable(T + "room.unexplored", name) : name;
			}
		}
		return null;
	}

	private void drawLegend(GuiGraphics g, int x, int y, int right) {
		int cx = x;
		cx = legendItem(g, cx, y, right, 0, Component.translatable(T + "legend.closed"));
		cx = legendItem(g, cx, y, right, 1, Component.translatable(T + "legend.open"));
		cx = legendItem(g, cx, y, right, 2, Component.translatable(T + "legend.unexplored"));
		legendItem(g, cx, y, right, 3, Component.translatable(T + (MapData.exploredAll() ? "legend.players" : "legend.you")));
	}

	/** One legend entry: a small sample then its text; skipped when it would not fit. Returns where the next one starts. */
	private int legendItem(GuiGraphics g, int x, int y, int right, int kind, Component label) {
		int total = 16 + TrahisonUi.width(label);
		if (x + total > right) {
			return x;
		}
		int iy = y + 3;
		switch (kind) {
			case 0 -> {
				g.fill(x, iy, x + 12, iy + 6, TrahisonPalette.BORDER);
				g.fill(x + 1, iy + 1, x + 11, iy + 5, TrahisonPalette.DANGER);
			}
			case 1 -> {
				g.fill(x, iy, x + 4, iy + 6, TrahisonPalette.BORDER);
				g.fill(x + 8, iy, x + 12, iy + 6, TrahisonPalette.BORDER);
				g.fill(x + 1, iy + 1, x + 3, iy + 5, TrahisonPalette.SUCCESS);
				g.fill(x + 9, iy + 1, x + 11, iy + 5, TrahisonPalette.SUCCESS);
			}
			case 2 -> {
				g.fill(x, iy - 1, x + 12, iy + 7, TrahisonPalette.PANEL_SUNKEN);
				g.enableScissor(x, iy - 1, x + 12, iy + 7);
				for (int t = -8; t < 14; t += 4) {
					TrahisonUi.rotatedFill(g, x + 6, iy + 3, 45f, -9, t - 4, 9, t - 3, TrahisonPalette.EDGE);
				}
				g.disableScissor();
				MenuUi.outline(g, x, iy - 1, x + 12, iy + 7, TrahisonPalette.EDGE);
			}
			default -> TrahisonUi.disc(g, x + 6, iy + 3, 3, TrahisonPalette.ACCENT);
		}
		MenuUi.text(g, label, x + 16, y + 3, right - x - 16, TrahisonPalette.TEXT_SECONDARY);
		return x + total + 10;
	}

	// ---------------------------------------------------------------- notes tab

	private void drawNotesTab(GuiGraphics g, int x, int y, int w, int h) {
		int top = y;
		if (over()) {
			viewButton(g, x, top, 130);
			top += ROW + GAP;
		}
		int boxH = y + h - top;
		TrahisonUi.well(g, x, top, w, boxH);
		notesX = x;
		notesY = top;
		notesW = w;
		notesH = boxH;
		int tx = x + 6;
		int tw = w - 12 - 6;
		notesTextW = tw;
		boolean mine = viewing < 0;
		NoteBuffer shown = buffer;
		if (!mine) {
			List<MapNotes.Person> people = MapNotes.everyone();
			shown = new NoteBuffer(MapNotes.MAX_NOTES, s -> TrahisonUi.width(Component.literal(s)));
			shown.set(people != null && viewing < people.size() ? people.get(viewing).notes() : "");
		}
		List<NoteBuffer.Line> lines = shown.lines(tw);
		int innerH = boxH - 8;
		int content = lines.size() * LINE_H;
		int caretLine = NoteBuffer.lineOf(lines, shown.caret());
		if (mine && editable()) { // keep the caret in view while typing
			if (caretLine * LINE_H < notesScroll) {
				notesScroll = caretLine * LINE_H;
			} else if ((caretLine + 1) * LINE_H > notesScroll + innerH) {
				notesScroll = (caretLine + 1) * LINE_H - innerH;
			}
		}
		notesScroll = clamp(notesScroll, content - innerH);
		String text = shown.text();
		ui.clip(g, x + 2, top + 2, x + w - 2, top + boxH - 2);
		if (text.isEmpty()) {
			String key = mine && !MapNotes.loaded() ? "badge.loading" : editable() ? "notes.hint" : "notes.empty";
			MenuUi.text(g, Component.translatable(T + key), tx, top + 6, tw, TrahisonPalette.TEXT_MUTED);
		}
		for (int i = 0; i < lines.size(); i++) {
			int ly = top + 4 + i * LINE_H - notesScroll;
			if (ly + LINE_H < top || ly > top + boxH) {
				continue;
			}
			NoteBuffer.Line line = lines.get(i);
			MenuUi.text(g, Component.literal(text.substring(line.start(), line.end())), tx, ly, tw + 8, TrahisonPalette.TEXT);
		}
		if (mine && editable() && System.currentTimeMillis() / 500 % 2 == 0) {
			int cx = tx + shown.xOf(lines.get(caretLine), shown.caret());
			int cy = top + 3 + caretLine * LINE_H - notesScroll;
			g.fill(cx, cy, cx + 1, cy + 9, TrahisonPalette.ACCENT);
		}
		ui.unclip(g);
		if (content > innerH) {
			int thumb = Math.max(12, innerH * innerH / content);
			int thumbY = top + 4 + (innerH - thumb) * notesScroll / Math.max(1, content - innerH);
			g.fill(x + w - 5, thumbY, x + w - 3, thumbY + thumb, TrahisonPalette.EDGE_LIGHT);
		}
		Component count = Component.literal(text.length() + "/" + MapNotes.MAX_NOTES);
		int cw = TrahisonUi.width(count);
		MenuUi.text(g, count, x + w - cw - 10, top + boxH - 11, cw + 2, text.length() >= MapNotes.MAX_NOTES ? TrahisonPalette.DANGER : TrahisonPalette.TEXT_DISABLED);
	}

	private void notesChanged() {
		MapNotes.setNotes(buffer.text());
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (modal != null || super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (tab == Tab.NOTES) {
			if (button == 0 && editable() && TrahisonUi.inside(mouseX, mouseY, notesX, notesY, notesW, notesH)) {
				int line = (int) Math.floor((mouseY - notesY - 4 + notesScroll) / (double) LINE_H);
				buffer.click(line, (int) mouseX - (notesX + 6), notesTextW);
				return true;
			}
			return false;
		}
		if (!TrahisonUi.inside(mouseX, mouseY, ax, ay, aw, ah)) {
			return false;
		}
		if (button == 1) {
			panning = true;
			follow = false;
			return true;
		}
		if (button == 0 && editable()) {
			if (tool == Tool.PEN && !MapNotes.floorFull(floorId())) {
				drawing = true;
				stroke = new Stroke(ink);
				stroke.add(wx(mouseX), wz(mouseY));
			} else if (tool == Tool.ERASER) {
				drawing = true;
				erase(mouseX, mouseY);
			}
			return true;
		}
		return false;
	}

	private void erase(double mouseX, double mouseY) {
		if (MapModel.erase(MapNotes.strokes(floorId()), wx(mouseX), wz(mouseY), 4 / zoom) > 0) {
			MapNotes.changed();
		}
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (panning && button == 1) {
			centerX -= dx / zoom;
			centerZ -= dy / zoom;
			return true;
		}
		if (drawing && button == 0 && tab == Tab.MAP) {
			if (tool == Tool.ERASER) {
				erase(mouseX, mouseY);
			} else if (stroke != null) {
				double x = wx(mouseX);
				double z = wz(mouseY);
				int n = stroke.points.size();
				// A point every 3 screen pixels at least, and never past the floor's limit (the backend's).
				if (Math.hypot(x - stroke.points.get(n - 2), z - stroke.points.get(n - 1)) * zoom >= 3
						&& MapModel.pointCount(MapNotes.strokes(floorId())) + stroke.pointCount() < MapNotes.MAX_POINTS_PER_FLOOR) {
					stroke.add(x, z);
				}
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 1) {
			panning = false;
		}
		if (button == 0 && drawing) {
			drawing = false;
			if (stroke != null) {
				if (editable()) {
					MapNotes.strokes(floorId()).add(stroke);
					MapNotes.changed();
				}
				stroke = null;
			}
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	protected boolean scrolled(double mouseX, double mouseY, double amount) {
		if (tab == Tab.NOTES) {
			notesScroll = Math.max(0, notesScroll - (int) Math.signum(amount) * 20);
			return true;
		}
		if (floors.size() > 1 && TrahisonUi.inside(mouseX, mouseY, tabsX, tabsY, tabsW, ROW)) {
			selectFloor(floor + (amount > 0 ? -1 : 1));
			return true;
		}
		if (TrahisonUi.inside(mouseX, mouseY, ax, ay, aw, ah)) {
			// The block under the cursor stays under it (the player stays in the middle while following).
			double bx = wx(mouseX);
			double bz = wz(mouseY);
			zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * Math.pow(1.2, amount)));
			if (!follow) {
				centerX = bx - (mouseX - ax - aw / 2.0) / zoom;
				centerZ = bz - (mouseY - ay - ah / 2.0) / zoom;
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (modal == null && tab == Tab.NOTES && editable() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
			int wrap = notesTextW;
			boolean handled = true;
			switch (keyCode) {
				case GLFW.GLFW_KEY_BACKSPACE -> buffer.backspace();
				case GLFW.GLFW_KEY_DELETE -> buffer.delete();
				case GLFW.GLFW_KEY_LEFT -> buffer.left();
				case GLFW.GLFW_KEY_RIGHT -> buffer.right();
				case GLFW.GLFW_KEY_UP -> buffer.vertical(-1, wrap);
				case GLFW.GLFW_KEY_DOWN -> buffer.vertical(1, wrap);
				case GLFW.GLFW_KEY_HOME -> buffer.home(wrap);
				case GLFW.GLFW_KEY_END -> buffer.end(wrap);
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> buffer.insert('\n');
				case GLFW.GLFW_KEY_V -> {
					if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
						buffer.paste(Minecraft.getInstance().keyboardHandler.getClipboard());
					} else {
						handled = false;
					}
				}
				default -> handled = false;
			}
			if (handled) {
				notesChanged();
				return true;
			}
			return true; // the other keys are typed text (charTyped), the map key included
		}
		if (modal == null && tab == Tab.MAP && Trahison.MAP_KEY.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (modal == null && tab == Tab.NOTES && editable()) {
			if (buffer.insert(chr)) {
				notesChanged();
			}
			return true;
		}
		return super.charTyped(chr, modifiers);
	}
}
