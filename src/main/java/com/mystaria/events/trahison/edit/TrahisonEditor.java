package com.mystaria.events.trahison.edit;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;

import com.mystaria.events.trahison.edit.EditKinds.Kind;
import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.LayoutDto.Cabin;
import com.mystaria.events.trahison.edit.LayoutDto.Door;
import com.mystaria.events.trahison.edit.LayoutDto.Pos;
import com.mystaria.events.trahison.edit.LayoutDto.Slot;
import com.mystaria.events.trahison.edit.LayoutDto.Spot;
import com.mystaria.events.trahison.edit.LayoutDto.Zone;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;
import com.mystaria.phantasmon.client.network.BackendApiException;

/**
 * The plan editor (Documentation/trahison/etapes/T2-plan-portes.md, T2.1), for Phantasmon's admins inside a hub:
 * {@code /phantasmon event trahison edit} turns it on. While it is on, left and right clicks do not reach the server
 * (the two corners of a box are set instead) and {@link #OPEN_KEY} opens the side panel ({@link EditorScreen}).
 *
 * <p>The plan is kept in hub coordinates ({@link HubGrid} converts); the backend validates and versions it. The
 * editor holds no game rule. Client thread.
 */
public final class TrahisonEditor {

	/** Default key K: the editor's side panel while the editor is on. Rebindable in the controls. */
	public static final KeyMapping OPEN_KEY = new KeyMapping("key.phantasmon_events.trahison_edit",
			InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_K, "key.categories.phantasmon_events");

	private static boolean active;
	/** The hub the plan below was loaded for. */
	private static String hub;
	static LayoutDto layout = new LayoutDto();
	static boolean dirty;
	static boolean busy;
	static Component status;
	static boolean statusError;
	/** The problems the backend found in the last save, shown in the panel. */
	static List<Issue> issues = new ArrayList<>();
	static final Selection selection = new Selection();
	static Kind selectedKind = Kind.ZONE;
	static String selectedId;

	/** One validation problem from the backend: its code, the elements concerned and a machine-readable complement. */
	record Issue(String code, List<String> ids, String info) {
	}

	private TrahisonEditor() {
	}

	// ---------------------------------------------------------------- registration

	/** Called once by {@code Trahison.register()}. */
	public static void register() {
		KeyBindingHelper.registerKeyBinding(OPEN_KEY);
		RoomEditor.register();
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (active && client.screen == null && client.player != null) {
				// Before the game's own key handling, so that nothing reaches the server.
				while (client.options.keyAttack.consumeClick()) {
					setCorner(true);
				}
				while (client.options.keyUse.consumeClick()) {
					setCorner(false);
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_KEY.consumeClick()) {
				if (RoomEditor.isActive()) {
					// The room editor owns the key while it is on (it is a singleplayer tool: the plan editor is off then).
					RoomEditor.openPanel();
				} else if (active && client.screen == null) {
					client.setScreen(new EditorScreen());
				}
			}
			if (active && !stillInHub()) {
				active = false;
				chat(Component.translatable("phantasmon_events.trahison.edit.left_hub"), true);
			}
		});
		// Belt and braces: whatever the game tries while the editor is on (held buttons repeat) is refused.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> editing(level.isClientSide) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> editing(level.isClientSide) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> editing(level.isClientSide)
				? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> editing(level.isClientSide) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> editing(level.isClientSide) ? InteractionResult.FAIL : InteractionResult.PASS);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(EditWorld::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			active = false;
			hub = null;
			layout = new LayoutDto();
			dirty = false;
			selection.clear();
			selectedId = null;
			issues = new ArrayList<>();
		}));
	}

	private static boolean editing(boolean clientSide) {
		return active && clientSide;
	}

	// ---------------------------------------------------------------- state

	public static boolean isActive() {
		return active;
	}

	/** Turns the plan editor off (the room editor takes the clicks and the key). */
	static void deactivate() {
		active = false;
	}

	static String hub() {
		return hub;
	}

	/** The current anchor's grid, or null outside a hub. */
	static HubGrid grid() {
		HubAnchorDto anchor = anchor();
		return anchor == null ? null : new HubGrid(anchor.coordinates());
	}

	private static HubAnchorDto anchor() {
		return PhantasmonApi.hubs() == null ? null : PhantasmonApi.hubs().joinedAnchor();
	}

	private static boolean stillInHub() {
		HubAnchorDto anchor = anchor();
		return Minecraft.getInstance().player != null && anchor != null && anchor.hub().equals(hub);
	}

	/** {@code /phantasmon event trahison edit}: on or off. */
	public static void toggle() {
		if (active) {
			active = false;
			chat(Component.translatable("phantasmon_events.trahison.edit.off"), false);
			return;
		}
		if (!PhantasmonApi.isAdmin()) {
			chat(Component.translatable("phantasmon_events.trahison.edit.not_admin"), false);
			return;
		}
		HubAnchorDto anchor = anchor();
		if (anchor == null) {
			chat(Component.translatable("phantasmon_events.trahison.edit.not_in_hub"), false);
			return;
		}
		active = true;
		if (!anchor.hub().equals(hub)) {
			hub = anchor.hub();
			layout = new LayoutDto();
			dirty = false;
			selectedId = null;
			selection.clear();
			issues = new ArrayList<>();
			reload();
		}
		chat(Component.translatable("phantasmon_events.trahison.edit.on", hub, OPEN_KEY.getTranslatedKeyMessage()), false);
	}

	// ---------------------------------------------------------------- the two corners

	private static void setCorner(boolean first) {
		BlockPos pos = Selection.pick(Minecraft.getInstance());
		if (pos == null) {
			chat(Component.translatable("phantasmon_events.trahison.edit.no_block"), true);
			return;
		}
		selection.set(first, pos);
		chat(Component.translatable(first ? "phantasmon_events.trahison.edit.corner_a" : "phantasmon_events.trahison.edit.corner_b",
				pos.getX(), pos.getY(), pos.getZ()), true);
	}

	/** The selection in this world's blocks, {@code {minX, minY, minZ, maxX, maxY, maxZ}}, or null. */
	static int[] selectionLocal() {
		return selection.local();
	}

	/** The selection in hub coordinates, or null. */
	static Box selectionHub() {
		int[] s = selectionLocal();
		HubGrid grid = grid();
		return s == null || grid == null ? null : grid.toHub(s[0], s[1], s[2], s[3], s[4], s[5]);
	}

	static void clearSelection() {
		selection.clear();
	}

	// ---------------------------------------------------------------- the plan

	static List<String> ids(Kind kind) {
		List<String> ids = new ArrayList<>();
		switch (kind) {
			case ZONE -> layout.zones.forEach(e -> ids.add(e.id));
			case DOOR -> layout.doors.forEach(e -> ids.add(e.id));
			case SLOT -> layout.roomSlots.forEach(e -> ids.add(e.id));
			case CABIN -> layout.cabins.forEach(e -> ids.add(e.id));
			case SPOT -> layout.hidingSpots.forEach(e -> ids.add(e.id));
		}
		return ids;
	}

	static Zone zone(String id) {
		return layout.zones.stream().filter(e -> e.id.equals(id)).findFirst().orElse(null);
	}

	static Door door(String id) {
		return layout.doors.stream().filter(e -> e.id.equals(id)).findFirst().orElse(null);
	}

	static Slot slot(String id) {
		return layout.roomSlots.stream().filter(e -> e.id.equals(id)).findFirst().orElse(null);
	}

	static Cabin cabin(String id) {
		return layout.cabins.stream().filter(e -> e.id.equals(id)).findFirst().orElse(null);
	}

	static Spot spot(String id) {
		return layout.hidingSpots.stream().filter(e -> e.id.equals(id)).findFirst().orElse(null);
	}

	/** The type shown beside an element in lists: a zone's or door's type, a slot's round, a cabin's side. */
	static String typeOf(Kind kind, String id) {
		return switch (kind) {
			case ZONE -> zone(id) == null ? null : zone(id).type;
			case DOOR -> door(id) == null ? null : door(id).type;
			default -> null;
		};
	}

	static boolean exists(Kind kind, String id) {
		return ids(kind).contains(id);
	}

	/** A new element with a free id, selected. Returns its id. */
	static String create(Kind kind) {
		String id = freeId(kind);
		switch (kind) {
			case ZONE -> {
				Zone zone = new Zone();
				zone.id = id;
				zone.type = "hall";
				layout.zones.add(zone);
			}
			case DOOR -> {
				Door door = new Door();
				door.id = id;
				door.type = "plain";
				layout.doors.add(door);
			}
			case SLOT -> {
				Slot slot = new Slot();
				slot.id = id;
				slot.round = 1;
				slot.orientation = "north";
				layout.roomSlots.add(slot);
			}
			case CABIN -> {
				Cabin cabin = new Cabin();
				cabin.id = id;
				cabin.serves = EditKinds.PAIRS.get(0);
				cabin.side = "pair";
				layout.cabins.add(cabin);
			}
			case SPOT -> {
				Spot spot = new Spot();
				spot.id = id;
				layout.hidingSpots.add(spot);
			}
		}
		select(kind, id);
		changed();
		return id;
	}

	private static String freeId(Kind kind) {
		List<String> used = ids(kind);
		for (int n = 1;; n++) {
			String id = kind.key + "_" + n;
			if (!used.contains(id)) {
				return id;
			}
		}
	}

	/** Removes an element; whatever pointed at it forgets it. */
	static void delete(Kind kind, String id) {
		switch (kind) {
			case ZONE -> {
				layout.zones.removeIf(e -> e.id.equals(id));
				layout.doors.forEach(d -> {
					d.zoneA = id.equals(d.zoneA) ? null : d.zoneA;
					d.zoneB = id.equals(d.zoneB) ? null : d.zoneB;
					d.threshold = id.equals(d.threshold) ? null : d.threshold;
				});
				layout.cabins.forEach(c -> c.zone = id.equals(c.zone) ? null : c.zone);
			}
			case DOOR -> {
				layout.doors.removeIf(e -> e.id.equals(id));
				layout.cabins.forEach(c -> c.door = id.equals(c.door) ? null : c.door);
			}
			case SLOT -> layout.roomSlots.removeIf(e -> e.id.equals(id));
			case CABIN -> layout.cabins.removeIf(e -> e.id.equals(id));
			case SPOT -> layout.hidingSpots.removeIf(e -> e.id.equals(id));
		}
		if (kind == selectedKind && id.equals(selectedId)) {
			selectedId = null;
		}
		changed();
	}

	/**
	 * Renames an element and what pointed at it. Refused (false) for an id the backend would refuse (letters, digits, {@code _}
	 * and {@code -}, 1 to 40) or one already used by another element of the kind.
	 */
	static boolean rename(Kind kind, String from, String to) {
		if (from.equals(to)) {
			return true;
		}
		if (!to.matches("[A-Za-z0-9_-]{1,40}") || exists(kind, to)) {
			return false;
		}
		switch (kind) {
			case ZONE -> {
				zone(from).id = to;
				layout.doors.forEach(d -> {
					d.zoneA = from.equals(d.zoneA) ? to : d.zoneA;
					d.zoneB = from.equals(d.zoneB) ? to : d.zoneB;
					d.threshold = from.equals(d.threshold) ? to : d.threshold;
				});
				layout.cabins.forEach(c -> c.zone = from.equals(c.zone) ? to : c.zone);
			}
			case DOOR -> {
				door(from).id = to;
				layout.cabins.forEach(c -> c.door = from.equals(c.door) ? to : c.door);
			}
			case SLOT -> slot(from).id = to;
			case CABIN -> cabin(from).id = to;
			case SPOT -> spot(from).id = to;
		}
		if (kind == selectedKind && from.equals(selectedId)) {
			selectedId = to;
		}
		changed();
		return true;
	}

	static void select(Kind kind, String id) {
		selectedKind = kind;
		selectedId = id;
	}

	/** Marks the plan as changed since the last save or load. */
	static void changed() {
		dirty = true;
	}

	// ---------------------------------------------------------------- the selection applied to an element

	static void addBox(Zone zone) {
		Box box = selectionHub();
		if (box != null) {
			zone.boxes.add(box);
			changed();
		}
	}

	static void replaceBoxes(Zone zone) {
		Box box = selectionHub();
		if (box != null) {
			zone.boxes.clear();
			zone.boxes.add(box);
			changed();
		}
	}

	static void setBlock(Door door) {
		Box box = selectionHub();
		if (box != null) {
			door.block = box;
			changed();
		}
	}

	/** A room's place starts at the selection's minimum corner. */
	static void setOrigin(Slot slot) {
		Box box = selectionHub();
		if (box != null) {
			slot.origin = new Pos(box.minX, box.minY, box.minZ);
			changed();
		}
	}

	static void setPosition(Spot spot) {
		Box box = selectionHub();
		if (box != null) {
			spot.position = new Pos(box.minX, box.minY, box.minZ);
			changed();
		}
	}

	// ---------------------------------------------------------------- backend

	/** The saved plan comes back, replacing the one being edited. */
	static void reload() {
		if (hub == null || busy) {
			return;
		}
		busy = true;
		setStatus(Component.translatable("phantasmon_events.trahison.edit.loading"), false);
		PhantasmonApi.get("/events/trahison/layout/" + URLEncoder.encode(hub, StandardCharsets.UTF_8), LayoutDto.class)
				.whenComplete((loaded, failure) -> Minecraft.getInstance().execute(() -> {
					busy = false;
					if (failure != null || loaded == null) {
						setStatus(failureMessage(failure), true);
						return;
					}
					layout = loaded.normalized();
					dirty = false;
					issues = new ArrayList<>();
					if (selectedId != null && !exists(selectedKind, selectedId)) {
						selectedId = null;
					}
					setStatus(Component.translatable("phantasmon_events.trahison.edit.loaded", layout.version), false);
				}));
	}

	/** What goes to the backend: the plan and where it comes from. */
	private record SaveBody(int baseVersion, boolean draft, List<Zone> zones, List<Door> doors, List<Slot> roomSlots,
			List<Cabin> cabins, List<Spot> hidingSpots) {
	}

	/** Saves a new version; {@code draft}: an incomplete plan, without the completeness checks. */
	static void save(boolean draft) {
		if (hub == null || busy) {
			return;
		}
		busy = true;
		setStatus(Component.translatable("phantasmon_events.trahison.edit.saving"), false);
		SaveBody body = new SaveBody(layout.version, draft, layout.zones, layout.doors, layout.roomSlots, layout.cabins, layout.hidingSpots);
		PhantasmonApi.post("/events/trahison/layout/" + URLEncoder.encode(hub, StandardCharsets.UTF_8), body, LayoutDto.class)
				.whenComplete((saved, failure) -> Minecraft.getInstance().execute(() -> {
					busy = false;
					if (failure != null || saved == null) {
						issues = issuesOf(failure);
						setStatus(failureMessage(failure), true);
						EditorScreen.onSaveFailed(!issues.isEmpty());
						return;
					}
					layout = saved.normalized();
					dirty = false;
					issues = new ArrayList<>();
					setStatus(Component.translatable(saved.draft ? "phantasmon_events.trahison.edit.saved_draft"
							: "phantasmon_events.trahison.edit.saved", layout.version), false);
				}));
	}

	private static BackendApiException apiException(Throwable failure) {
		Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
		return cause instanceof BackendApiException api ? api : null;
	}

	private static Component failureMessage(Throwable failure) {
		BackendApiException api = apiException(failure);
		if (api == null) {
			return Component.translatable("phantasmon_events.trahison.edit.error.network");
		}
		return Component.translatableWithFallback("phantasmon_events.trahison.edit.error." + api.errorCode().toLowerCase(java.util.Locale.ROOT),
				api.errorCode());
	}

	/** The backend's {@code details.errors} of a refused plan. */
	@SuppressWarnings("unchecked")
	private static List<Issue> issuesOf(Throwable failure) {
		List<Issue> found = new ArrayList<>();
		BackendApiException api = apiException(failure);
		if (api == null || !(api.details().get("errors") instanceof List<?> errors)) {
			return found;
		}
		for (Object error : errors) {
			if (error instanceof Map<?, ?> map) {
				List<String> ids = new ArrayList<>();
				if (map.get("ids") instanceof List<?> list) {
					list.forEach(id -> ids.add(String.valueOf(id)));
				}
				found.add(new Issue(String.valueOf(map.get("code")), ids, map.get("info") == null ? "" : String.valueOf(map.get("info"))));
			}
		}
		return found;
	}

	static void setStatus(Component message, boolean error) {
		status = message;
		statusError = error;
	}

	// ---------------------------------------------------------------- helpers

	private static void chat(Component message, boolean actionBar) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(message, actionBar);
		}
	}
}
