package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.Typewriter;
import com.mystaria.events.trahison.minigame.widget.Widget;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Base of every puzzle screen of the betrayal game: a full-screen stage (window mode of the charter, in whole GUI
 * pixels so text stays crisp) over an "old abandoned complex" backdrop, a framed panel with the mechanism's title, an
 * exit button, a collapsible help area, the content area where the subclass lays out its pieces, and a footer row of
 * the group's player heads with a highlight on whoever made the last gesture.
 *
 * <p>Sync model (architecture.md section 7): a gesture is applied at once by the subclass (optimistic) and sent with
 * {@link #sendGesture}; every {@code EventsTrahisonPuzzle} for the step then calls {@link #applyView}, which brings the
 * screen back to the authoritative state, and {@link #onRemote} so that other players' gestures can be animated as a
 * cursor in their colour. The screen holds no game rule. In offline mode (debug command) nothing is sent and the
 * subclass applies gestures locally, so the look can be judged without a backend.
 *
 * <p>Subclasses build their {@link Widget}s in the constructor and register them with {@link #add}; {@link #layout}
 * positions them inside the content rectangle.
 */
public abstract class MiniGameScreen extends Screen {

	private static final int STAGE_W = 400;
	private static final int STAGE_H = 240;
	private static final int FOOTER_H = 28;
	private static final long APPEAR_MS = 160;
	/** Delay between the first frame of the unlock animation and the screen closing. */
	private static final long CLOSE_AFTER_MS = 950;

	/** The open mini-game screen, for the HUD to hide itself ({@link #isOpen}). */
	private static MiniGameScreen current;
	private static boolean helpOpenPref;

	private final String mechanism;
	private final String step;
	private final boolean offline;
	/** The group whose room this puzzle is in (-1: the player's own, no need to say); set by the router, free time visits (D8) need it. */
	private int group = -1;
	/** The round of that room (0: the current one, nothing to say); free time visits of earlier rounds' rooms (D8) need it. */
	private int round;
	private final List<Widget> widgets = new ArrayList<>();
	private final Map<Integer, Remote> remotes = new HashMap<>();
	private final long openedAt = System.currentTimeMillis();
	private Widget captured;

	protected int stageX;
	protected int stageY;
	protected int stageW;
	protected int stageH;
	/** The content rectangle: where subclasses lay out and draw their pieces. */
	protected int contentX;
	protected int contentY;
	protected int contentW;
	protected int contentH;

	private boolean helpOpen = helpOpenPref;
	private int pressedHeader = -1;
	private boolean solved;
	private boolean lockedOpen;
	private long solvedAt;
	private long errorAt = -1000;
	private int lastActor = -1;
	private long lastActorAt;
	private long lastDemo = System.currentTimeMillis();
	private int demoCounter;
	private boolean closing;
	private Typewriter helpWriter;
	private boolean openSoundPlayed;

	private record Remote(int x, int y, long at) {
	}

	protected MiniGameScreen(String mechanism, String step, boolean offline) {
		super(Component.translatable("phantasmon_events.trahison.minigame." + mechanism + ".title"));
		this.mechanism = mechanism;
		this.step = step;
		this.offline = offline;
	}

	// ---------------------------------------------------------------- API for the HUD and the router

	/** Whether a mini-game screen is open: the game HUD hides itself meanwhile. */
	public static boolean isOpen() {
		return current != null;
	}

	public int group() {
		return group;
	}

	/** Says which room's puzzle this is, so the gestures go to that room. */
	void aimAt(int group) {
		aimAt(0, group);
	}

	/** As above, for the room of group {@code group} in round {@code round} (an earlier round's room, visited in the free time). */
	void aimAt(int round, int group) {
		this.round = round;
		this.group = group;
	}

	public int round() {
		return round;
	}

	public String step() {
		return step;
	}

	public String mechanism() {
		return mechanism;
	}

	public boolean offline() {
		return offline;
	}

	public boolean isSolved() {
		return solved;
	}

	// ---------------------------------------------------------------- subclass contract

	/** Positions the widgets inside the content rectangle (fields {@code contentX..contentH}). Called on init and resize. */
	protected abstract void layout();

	/** Brings the screen to the authoritative state of a mechanism view. Never plays sounds for the player's own gestures. */
	protected abstract void applyView(Map<String, Object> view);

	/** Another player's gesture ({@code events} of the mechanism's {@code last}); typically calls {@link #remoteTouch}. */
	protected void onRemote(int player, List<Map<String, Object>> events) {
	}

	/** Offline demo only: act as another player would, so the cursor animation can be judged. */
	protected void simulateRemote(int player) {
	}

	/** Extra drawing under the widgets (labels, wells). Default: nothing. */
	protected void drawUnder(GuiGraphics g, int mx, int my) {
	}

	/** Extra drawing over the widgets. Default: nothing. */
	protected void drawOver(GuiGraphics g, int mx, int my) {
	}

	/** A key pressed while the screen is interactive; return true if used. */
	protected boolean key(int key) {
		return false;
	}

	/** Help text, from the language file; override for a computed one. */
	protected Component helpText() {
		return Component.translatable("phantasmon_events.trahison.minigame." + mechanism + ".help");
	}

	/** Removes every widget: for a mechanism whose pieces depend on data that arrives later (the view). */
	protected final void clearWidgets() {
		widgets.clear();
	}

	protected final <T extends Widget> T add(T widget) {
		widgets.add(widget);
		return widget;
	}

	// ---------------------------------------------------------------- gestures, sync, feedback

	/** Index used for the local player's colour and highlight. */
	protected final int ownIndex() {
		return PuzzleScreens.ownIndex() >= 0 ? PuzzleScreens.ownIndex() : 0;
	}

	/**
	 * Sends the local player's gesture ({@code puzzle_input}); the caller has already applied it on screen. Offline: no
	 * message. If the backend link is down, the gesture is refused with the error feedback.
	 */
	protected final void sendGesture(Map<String, Object> input) {
		markActor(ownIndex());
		if (offline) {
			return;
		}
		Map<String, Object> message = new java.util.LinkedHashMap<>();
		message.put("action", "puzzle_input");
		message.put("step", step);
		message.put("input", input);
		if (group >= 0) {
			message.put("group", group);
			if (round > 0) {
				message.put("round", round);
			}
		}
		if (!PhantasmonApi.send("EventsTrahisonAction", message)) {
			fail();
		}
	}

	/** Error feedback: shake 200 ms, danger flash on the frame, dull sound. */
	public final void fail() {
		errorAt = System.currentTimeMillis();
		MiniGameSounds.error();
	}

	/** Starts the unlock animation, then the screen closes by itself. Idempotent. */
	protected final void succeed() {
		if (solved) {
			return;
		}
		solved = true;
		solvedAt = System.currentTimeMillis();
		MiniGameSounds.success();
	}

	/** Shows a pulse and a name tag in player {@code player}'s colour at a screen point (their cursor). */
	protected final void remoteTouch(int player, int x, int y) {
		remotes.put(player, new Remote(x, y, System.currentTimeMillis()));
		markActor(player);
	}

	private void markActor(int player) {
		lastActor = player;
		lastActorAt = System.currentTimeMillis();
	}

	/** Called by the router for every {@code EventsTrahisonPuzzle} of this step. */
	public final void onPuzzle(PuzzleScreens.Msg m) {
		if ("reset".equals(m.reason())) {
			solved = false;
			lockedOpen = false;
		}
		applyView(m.view());
		if (m.lastPlayer() >= 0 && m.lastPlayer() != PuzzleScreens.ownIndex() && !m.lastEvents().isEmpty()) {
			markActor(m.lastPlayer());
			onRemote(m.lastPlayer(), m.lastEvents());
		}
		if (m.solved() && !solved) {
			if ("open".equals(m.reason())) {
				solved = true;
				lockedOpen = true;
			} else {
				succeed();
			}
		}
	}

	/** Opens or closes the help; the text writes itself each time it opens. */
	private void setHelp(boolean open) {
		helpOpen = open;
		helpWriter = null;
	}

	private boolean locked() {
		return solved || helpOpen;
	}

	// ---------------------------------------------------------------- screen life

	@Override
	protected void init() {
		current = this;
		int[] r = TrahisonUi.stage(width, height, STAGE_W, STAGE_H);
		stageX = r[0];
		stageY = r[1];
		stageW = r[2];
		stageH = r[3];
		contentX = stageX + 8;
		contentY = stageY + 28;
		contentW = stageW - 16;
		contentH = stageH - 28 - FOOTER_H - 4;
		layout();
		if (!openSoundPlayed) {
			openSoundPlayed = true;
			MiniGameSounds.open();
		}
	}

	@Override
	public void removed() {
		if (current == this) {
			current = null;
		}
		helpOpenPref = helpOpen;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void render(GuiGraphics g, int mx, int my, float partial) {
		long now = System.currentTimeMillis();
		Backdrop.render(g, width, height);

		for (Widget w : widgets) {
			w.enabled = !locked() && !closing;
		}
		if (offline && !solved && !helpOpen && now - lastDemo > 3200) {
			lastDemo = now;
			simulateRemote(1 + demoCounter++ % 2);
		}

		float appear = TrahisonUi.easeOut((now - openedAt) / (float) APPEAR_MS);
		int dx = MiniGameFx.shakeOffset(now - errorAt);
		int dy = Math.round((1f - appear) * 6f);
		g.pose().pushPose();
		g.pose().translate(dx, dy, 0);

		TrahisonUi.titledPanel(g, stageX, stageY, stageW, stageH, getTitle(), TrahisonPalette.ACCENT);
		headerButtons(g, mx, my);

		TrahisonUi.well(g, contentX, contentY, contentW, contentH);
		TrahisonUi.dots(g, contentX, contentY, contentX + contentW, contentY + contentH);
		drawUnder(g, mx, my);
		for (Widget w : widgets) {
			w.render(g, mx, my);
		}
		drawOver(g, mx, my);
		renderRemotes(g, now);
		if (helpOpen) {
			renderHelp(g);
		}
		footer(g, mx, my, now);

		g.pose().popPose();

		if (appear < 1f) {
			g.fill(stageX - 1, stageY - 1, stageX + stageW + 1, stageY + stageH + 1,
					TrahisonUi.withAlpha(TrahisonPalette.BG_DEEP, (int) ((1f - appear) * 0xFF)));
		}
		MiniGameFx.errorFlash(g, stageX + dx, stageY + dy, stageW, stageH, now - errorAt);
		if (solved && !lockedOpen) {
			float t = Math.min(1f, (now - solvedAt) / (float) MiniGameFx.SUCCESS_MS);
			MiniGameFx.success(g, contentX, contentY, contentW, contentH, contentX + contentW / 2, contentY + contentH / 2 - 6, t);
			if (!closing && now - solvedAt >= CLOSE_AFTER_MS) {
				closing = true;
				onClose();
				return;
			}
		}
		tooltips(g, mx, my);
	}

	/** Our own background is drawn in {@link #render}; vanilla's blur must not be. */
	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
	}

	private int[] helpButton() {
		return new int[] { stageX + stageW - 8 - 18 - 4 - 18, stageY + 3, 18, 16 };
	}

	private int[] closeButton() {
		return new int[] { stageX + stageW - 8 - 18, stageY + 3, 18, 16 };
	}

	private void headerButtons(GuiGraphics g, int mx, int my) {
		int[] help = helpButton();
		boolean helpHot = TrahisonUi.inside(mx, my, help[0], help[1], help[2], help[3]);
		TrahisonUi.button(g, help[0], help[1], help[2], help[3], Component.literal("?"), helpOpen ? ButtonKind.PRIMARY : ButtonKind.DEFAULT,
				TrahisonUi.stateOf(true, helpHot, pressedHeader == 0 && helpHot));
		int[] close = closeButton();
		boolean closeHot = TrahisonUi.inside(mx, my, close[0], close[1], close[2], close[3]);
		TrahisonUi.button(g, close[0], close[1], close[2], close[3], Component.empty(), ButtonKind.DANGER,
				TrahisonUi.stateOf(true, closeHot, pressedHeader == 1 && closeHot));
		TrahisonUi.glyph(g, TrahisonUi.Glyph.BETRAY, close[0] + 5, close[1] + 4 + (pressedHeader == 1 && closeHot ? 1 : 0), 8, TrahisonPalette.TEXT);
	}

	private void renderHelp(GuiGraphics g) {
		g.fill(contentX, contentY, contentX + contentW, contentY + contentH, TrahisonUi.withAlpha(TrahisonPalette.BG_DEEP, 0xB8));
		int pw = contentW - 16;
		Component text = helpText();
		int textH = MenuUi.paragraphHeight(text, pw - 16);
		int ph = Math.min(contentH - 8, textH + 32);
		int px = contentX + 8;
		int py = contentY + (contentH - ph) / 2;
		TrahisonUi.titledPanel(g, px, py, pw, ph, Component.translatable("phantasmon_events.trahison.minigame.help"), TrahisonPalette.ACCENT);
		if (helpWriter == null) {
			helpWriter = new Typewriter(text);
		}
		helpWriter.draw(g, px + 8, py + 28, pw - 16, TrahisonPalette.TEXT_SECONDARY);
	}

	private void renderRemotes(GuiGraphics g, long now) {
		remotes.entrySet().removeIf(e -> now - e.getValue().at() > 1800);
		for (Map.Entry<Integer, Remote> e : remotes.entrySet()) {
			Remote r = e.getValue();
			long age = now - r.at();
			int color = MiniGameFx.playerColor(e.getKey());
			float ring = TrahisonUi.easeOut(age / 700f);
			if (age < 700) {
				int rr = 4 + Math.round(16 * ring);
				TrahisonUi.ring(g, r.x(), r.y(), rr, rr - 2, TrahisonUi.withAlpha(color, (int) (0xB0 * (1f - ring))));
			}
			int fade = age > 1400 ? (int) (0xFF * (1f - (age - 1400) / 400f)) : 0xFF;
			// Pointer: an outlined triangle with its tip on the point.
			for (int i = 0; i < 9; i++) {
				g.fill(r.x() + 1, r.y() + i + 1, r.x() + 2 + Math.max(1, (9 - i) / 2 + 1), r.y() + i + 2, TrahisonUi.withAlpha(TrahisonPalette.BORDER, fade));
			}
			for (int i = 0; i < 9; i++) {
				g.fill(r.x(), r.y() + i, r.x() + Math.max(1, (9 - i) / 2 + 1), r.y() + i + 1, TrahisonUi.withAlpha(color, fade));
			}
			Component name = Component.literal(nameOf(e.getKey()));
			int tw = TrahisonUi.width(name) + 8;
			g.fill(r.x() + 8, r.y() + 8, r.x() + 8 + tw, r.y() + 20, TrahisonUi.withAlpha(TrahisonPalette.BG_DEEP, Math.min(fade, 0xD8)));
			TrahisonUi.textEllipsis(g, name, r.x() + 12, r.y() + 10, 90, TrahisonUi.withAlpha(color, fade));
		}
	}

	// ---------------------------------------------------------------- footer: watchers

	private record Watcher(int index, String name, UUID uuid) {
	}

	private List<Watcher> watchers() {
		List<Watcher> list = new ArrayList<>();
		Minecraft mc = Minecraft.getInstance();
		String own = mc.player != null ? mc.player.getGameProfile().getName() : "";
		UUID ownUuid = mc.player != null ? mc.player.getUUID() : null;
		if (offline) {
			list.add(new Watcher(0, own, ownUuid));
			list.add(new Watcher(1, Component.translatable("phantasmon_events.trahison.minigame.demo_player", 1).getString(), UUID.nameUUIDFromBytes("demo1".getBytes())));
			list.add(new Watcher(2, Component.translatable("phantasmon_events.trahison.minigame.demo_player", 2).getString(), UUID.nameUUIDFromBytes("demo2".getBytes())));
			return list;
		}
		for (int index : PuzzleScreens.groupMembers()) {
			PuzzleScreens.Player p = PuzzleScreens.player(index);
			list.add(p != null ? new Watcher(index, p.name(), p.uuid()) : new Watcher(index, "#" + (index + 1), null));
		}
		if (list.isEmpty()) {
			list.add(new Watcher(ownIndex(), own, ownUuid));
		}
		return list;
	}

	private String nameOf(int index) {
		for (Watcher w : watchers()) {
			if (w.index() == index) {
				return w.name();
			}
		}
		return "#" + (index + 1);
	}

	private PlayerSkin skinOf(UUID uuid) {
		if (uuid == null) {
			return DefaultPlayerSkin.get(new UUID(0L, 0L));
		}
		var connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(uuid);
		return info != null ? info.getSkin() : DefaultPlayerSkin.get(uuid);
	}

	private Component hoveredWatcher;

	private void footer(GuiGraphics g, int mx, int my, long now) {
		int fy = stageY + stageH - FOOTER_H;
		g.fill(stageX + 1, fy, stageX + stageW - 1, fy + 1, TrahisonPalette.BORDER);
		g.fill(stageX + 1, fy + 1, stageX + stageW - 1, fy + 2, TrahisonPalette.SHINE);
		hoveredWatcher = null;
		Component label = Component.translatable("phantasmon_events.trahison.minigame.watchers");
		int lx = stageX + 8;
		MenuUi.text(g, label, lx, fy + 10, 80, TrahisonPalette.TEXT_MUTED);
		int hx = lx + Math.min(80, TrahisonUi.width(label)) + 8;
		for (Watcher w : watchers()) {
			int by = fy + 4;
			boolean recent = w.index() == lastActor && now - lastActorAt < 1500;
			int color = MiniGameFx.playerColor(w.index());
			g.fill(hx - 1, by - 1, hx + 21, by + 21, recent ? TrahisonUi.lerp(TrahisonUi.pulse(600), TrahisonPalette.ACCENT_DIM, TrahisonPalette.ACCENT_HOT) : TrahisonPalette.BORDER);
			g.fill(hx, by, hx + 20, by + 20, TrahisonPalette.PANEL_SUNKEN);
			PlayerFaceRenderer.draw(g, skinOf(w.uuid()), hx + 2, by + 1, 16);
			g.fill(hx + 1, by + 18, hx + 19, by + 20, color);
			if (TrahisonUi.inside(mx, my, hx, by, 20, 20)) {
				hoveredWatcher = w.index() == ownIndex() ? Component.translatable("phantasmon_events.trahison.minigame.you", w.name()) : Component.literal(w.name());
			}
			hx += 24;
		}
		int right = stageX + stageW - 8;
		if (solved) {
			Component t = Component.translatable("phantasmon_events.trahison.minigame.unlocked");
			int pw = TrahisonUi.width(t) + 10;
			TrahisonUi.pill(g, right - pw, fy + 10, t, TrahisonPalette.SUCCESS_DARK, TrahisonPalette.TEXT);
		} else if (offline) {
			Component t = Component.translatable("phantasmon_events.trahison.minigame.offline");
			int pw = TrahisonUi.width(t) + 10;
			TrahisonUi.pill(g, right - pw, fy + 10, t, TrahisonPalette.GAME_MASTER_DARK, TrahisonPalette.TEXT);
		}
	}

	private void tooltips(GuiGraphics g, int mx, int my) {
		Component tip = null;
		int[] help = helpButton();
		int[] close = closeButton();
		if (TrahisonUi.inside(mx, my, close[0], close[1], close[2], close[3])) {
			tip = Component.translatable("phantasmon_events.trahison.minigame.close");
		} else if (TrahisonUi.inside(mx, my, help[0], help[1], help[2], help[3])) {
			tip = Component.translatable(helpOpen ? "phantasmon_events.trahison.minigame.help_hide" : "phantasmon_events.trahison.minigame.help_show");
		} else if (hoveredWatcher != null) {
			tip = hoveredWatcher;
		} else if (!locked()) {
			for (Widget w : widgets) {
				Component t = w.tooltip(mx, my);
				if (t != null) {
					tip = t;
					break;
				}
			}
		}
		if (tip != null) {
			TrahisonUi.tooltip(g, tip, mx, my, width, height);
		}
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			int[] close = closeButton();
			int[] help = helpButton();
			if (TrahisonUi.inside(mx, my, close[0], close[1], close[2], close[3])) {
				pressedHeader = 1;
				MiniGameSounds.click();
				return true;
			}
			if (TrahisonUi.inside(mx, my, help[0], help[1], help[2], help[3])) {
				pressedHeader = 0;
				MiniGameSounds.click();
				return true;
			}
		}
		if (helpOpen) {
			// Any click on the help overlay dismisses it.
			helpOpen = false;
			return true;
		}
		if (!locked() && !closing) {
			for (int i = widgets.size() - 1; i >= 0; i--) {
				Widget w = widgets.get(i);
				if (w.mouseClicked(mx, my, button)) {
					captured = w;
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (captured != null) {
			return captured.mouseDragged(mx, my, button, dx, dy);
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (pressedHeader >= 0) {
			int id = pressedHeader;
			pressedHeader = -1;
			int[] r = id == 0 ? helpButton() : closeButton();
			if (TrahisonUi.inside(mx, my, r[0], r[1], r[2], r[3])) {
				if (id == 1) {
					onClose();
				} else {
					setHelp(!helpOpen);
				}
			}
			return true;
		}
		if (captured != null) {
			Widget w = captured;
			captured = null;
			return w.mouseReleased(mx, my, button);
		}
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (!locked() && !closing) {
			for (int i = widgets.size() - 1; i >= 0; i--) {
				if (widgets.get(i).mouseScrolled(mx, my, scrollY)) {
					return true;
				}
			}
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (key == InputConstants.KEY_H || key == InputConstants.KEY_F1) {
			setHelp(!helpOpen);
			return true;
		}
		if (helpOpen && key != InputConstants.KEY_ESCAPE) {
			helpOpen = false;
			return true;
		}
		if (!locked() && !closing && key(key)) {
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}
}
