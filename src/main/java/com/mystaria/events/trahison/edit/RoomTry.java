package com.mystaria.events.trahison.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.minigame.MiniGameScreen;
import com.mystaria.events.trahison.minigame.PuzzleScreens;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.events.trahison.net.Messages;
import com.mystaria.events.trahison.room.RoomView;
import com.mystaria.events.trahison.room.Rooms;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.world.RoomFrame;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The room editor's try ({@code /phantasmon event trahison room try}): the room being built is played where it stands, with
 * no game, no hub and no fake blocks. The backend runs an admin-only instance of the saved room (decisions.md, D280 to D289);
 * everything else is the machinery of a game room: the room messages feed {@code Rooms}, the slots become interactive
 * ({@code Interactables}), the puzzles open their screens ({@code PuzzleScreens}), the bag and the documents work as usual.
 * What differs: the room is anchored on the editor's frame (the same turn as the editor draws its slots), and the single
 * player is player 0.
 *
 * <p>Choice (D281): editing stops the try. Any change of the room in the editor, leaving the editor, loading or creating
 * another room, or leaving the world ends it. Client thread.
 */
public final class RoomTry {

	private static final String T = "phantasmon_events.trahison.room_edit.try.";
	/** The prefix of a trial room's hub name (the backend's {@code RoomService.TRIAL_PREFIX}). */
	public static final String HUB_PREFIX = "try:";
	/** Ticks to wait for the room before giving up (the backend refused: not saved, not an admin...). */
	private static final int WAIT_TICKS = 100;

	/** One step as the backend told it. */
	private record StepState(String id, boolean open, boolean solved) {
	}

	private static boolean active;
	private static boolean received;
	private static int waited;
	private static RoomTemplate.Frame frame;
	private static String roomId;
	private static boolean exitOpen;
	private static final List<StepState> STEPS = new ArrayList<>();

	private RoomTry() {
	}

	/** Called by {@link RoomEditor#register()}. */
	static void register() {
		PhantasmonApi.onEventsMessage((type, data) -> {
			if (Rooms.ROOM.equals(type) && data.get("hub") instanceof String hub && owns(hub)) {
				Minecraft.getInstance().execute(() -> onRoom(data));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(RoomTry::tick);
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> stop(false, null)));
	}

	/** Whether a try is running (the room is anchored on the editor's frame). */
	public static boolean active() {
		return active;
	}

	/** Whether {@code hub} is the hub name of a trial room. */
	public static boolean owns(String hub) {
		return hub != null && hub.startsWith(HUB_PREFIX);
	}

	// ---------------------------------------------------------------- where the slots stand

	/**
	 * The world box of a slot of the trial room, {@code {minX, minY, minZ, maxX, maxY, maxZ}} (a block-aligned box, the way the
	 * editor draws it), or null when no try runs.
	 */
	public static double[] worldBox(RoomView.SlotView slot) {
		RoomTemplate.Frame f = frame;
		if (!active || f == null) {
			return null;
		}
		Box b = f.toWorld(new Box(slot.x(), slot.y(), slot.z(), slot.x() + slot.sx() - 1, slot.y() + slot.sy() - 1, slot.z() + slot.sz() - 1));
		return new double[] { b.minX, b.minY, b.minZ, b.maxX + 1, b.maxY + 1, b.maxZ + 1 };
	}

	/** The side a slot faces in the world, as a unit vector {@code {dx, dz}} (the editor's canonical orientation turned by the frame). */
	public static int[] face(RoomView.SlotView slot) {
		RoomTemplate.Frame f = frame;
		return RoomFrame.direction(f == null ? slot.orientation() : f.orientationToWorld(slot.orientation()));
	}

	// ---------------------------------------------------------------- commands

	private static Component t(String key, Object... args) {
		return Component.translatable(T + key, args);
	}

	private static void chat(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(message, false);
		}
	}

	/** {@code room try}. */
	public static void start() {
		Minecraft mc = Minecraft.getInstance();
		if (!RoomEditor.isActive()) {
			chat(t("refuse.no_editor"));
			return;
		}
		if (mc.getSingleplayerServer() == null || mc.player == null || mc.level == null) {
			chat(t("refuse.not_singleplayer"));
			return;
		}
		if (Link.hub() != null) {
			chat(t("refuse.in_hub"));
			return;
		}
		if (RoomEditor.dirty || RoomEditor.room.id == null || RoomEditor.busy) {
			chat(t("refuse.unsaved"));
			return;
		}
		if (active) {
			stop(true, null);
		}
		frame = RoomEditor.frame;
		roomId = RoomEditor.room.id;
		active = true;
		received = false;
		waited = 0;
		exitOpen = false;
		STEPS.clear();
		if (!Messages.tryAction("try_start", Map.of("room", roomId))) {
			active = false;
			frame = null;
			return;
		}
		if (mc.screen instanceof RoomScreen) {
			mc.setScreen(null);
		}
		chat(t("started", roomId));
	}

	/** {@code room try reset}: draws the room again with another seed. */
	public static void reset() {
		if (requireActive() && Messages.tryAction("try_reset", null)) {
			exitOpen = false;
			chat(t("reset"));
		}
	}

	/** {@code room try skip}: solves the step(s) open now. */
	public static void skip() {
		if (requireActive() && Messages.tryAction("try_skip", null)) {
			chat(t("skipped"));
		}
	}

	private static boolean requireActive() {
		if (!active) {
			chat(t("not_running"));
			return false;
		}
		return true;
	}

	/** {@code room try stop}. */
	public static void stop() {
		if (!active) {
			chat(t("not_running"));
			return;
		}
		stop(true, t("stopped"));
	}

	/** Ends the try; {@code tell}: tells the backend. {@code message}: what to say, or null for nothing. Safe to call when none runs. */
	static void stop(boolean tell, Component message) {
		if (!active) {
			return;
		}
		active = false;
		frame = null;
		roomId = null;
		exitOpen = false;
		STEPS.clear();
		if (tell) {
			Messages.tryAction("try_stop", null);
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof MiniGameScreen) {
			mc.setScreen(null);
		}
		PuzzleScreens.clearTrial();
		Rooms.clear();
		if (message != null) {
			chat(message);
		}
	}

	// ---------------------------------------------------------------- what the backend tells

	private static void onRoom(Map<String, Object> data) {
		if (!active) {
			return;
		}
		boolean first = !received;
		received = true;
		exitOpen = Boolean.TRUE.equals(data.get("exit_open"));
		STEPS.clear();
		if (data.get("steps") instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof Map<?, ?> step && step.get("id") instanceof String id) {
					STEPS.add(new StepState(id, Boolean.TRUE.equals(step.get("open")), Boolean.TRUE.equals(step.get("solved"))));
				}
			}
		}
		if (!first && exitOpen) {
			com.mystaria.events.trahison.hud.Announcer.say(t("exit_open"));
		}
	}

	private static void tick(Minecraft mc) {
		if (!active || received) {
			return;
		}
		if (++waited >= WAIT_TICKS) {
			stop(false, t("no_answer", roomId));
		}
	}

	// ---------------------------------------------------------------- the panel

	private static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (!active || mc.screen != null || !TrahisonUi.hudVisible() || MiniGameScreen.isOpen()) {
			return;
		}
		List<StepState> open = new ArrayList<>();
		int done = 0;
		for (StepState s : STEPS) {
			done += s.solved() ? 1 : 0;
			if (s.open() && !s.solved()) {
				open.add(s);
			}
		}
		int x = TrahisonUi.SAFE_MARGIN;
		int y = TrahisonUi.SAFE_MARGIN;
		int w = Math.min(g.guiWidth() - 2 * x, 260);
		int rows = 1 + Math.max(1, open.size());
		int h = 8 + rows * 12;
		TrahisonUi.panel(g, x, y, w, h);
		g.fill(x + 1, y + 2, x + 3, y + h - 2, TrahisonPalette.ACCENT);
		TrahisonUi.textEllipsis(g, t("hud.running", roomId == null ? "" : roomId), x + 10, y + 5, w - 16, TrahisonPalette.ACCENT);
		int ry = y + 5 + 12;
		if (exitOpen) {
			TrahisonUi.textEllipsis(g, t("hud.exit_open", done, STEPS.size()), x + 10, ry, w - 16, TrahisonPalette.SUCCESS);
		} else if (!received) {
			TrahisonUi.textEllipsis(g, t("hud.waiting"), x + 10, ry, w - 16, TrahisonPalette.TEXT_SECONDARY);
		} else {
			for (StepState s : open) {
				String mechanism = mechanismOf(s.id());
				Component line = t("hud.step", STEPS.indexOf(s) + 1, STEPS.size(), s.id(), mechanism == null ? Component.empty() : RoomUi.mechanism(mechanism));
				TrahisonUi.textEllipsis(g, line, x + 10, ry, w - 16, TrahisonPalette.TEXT);
				ry += 12;
			}
		}
	}

	private static String mechanismOf(String stepId) {
		for (RoomDto.StepDto step : RoomEditor.room.steps) {
			if (stepId.equals(step.id)) {
				return step.mechanism;
			}
		}
		return null;
	}
}
