package com.mystaria.events.trahison.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.events.trahison.net.Messages;
import com.mystaria.events.trahison.room.Documents;
import com.mystaria.events.trahison.room.Inventory;
import com.mystaria.events.trahison.room.RoomView;
import com.mystaria.events.trahison.room.RoomView.SlotView;
import com.mystaria.events.trahison.room.Rooms;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;

/**
 * The objects of the rooms in the world (T3.3, solution S3). For every active slot the backend lists, a local
 * {@link InteractableEntity} gives the slot its hitbox; the slots' place comes from the room's place and orientation
 * ({@link RoomFrame}, {@link HubBlocks}). What stands there is drawn by {@link InteractableRender}.
 *
 * <p>A click on an object never reaches the server: {@code UseEntityCallback} (and {@code AttackEntityCallback}) answer
 * {@code FAIL} for these entities, and because the entity reach of the game is 3 blocks while ours is
 * {@value #REACH}, the click is taken from the key itself at the start of the tick whenever an object is aimed
 * ({@link #pick}), so there is no distance at which it leaks. Only a living player who may act in that room
 * ({@link Rooms#canAct}) can aim or click; everyone else only sees the objects. What a click does is decided here by what the slot
 * shows, and the result always comes back as the next {@code EventsTrahisonRoom}: nothing is applied locally.
 * Client thread.
 */
public final class Interactables {

	/** How far an object can be reached, in blocks. */
	public static final double REACH = 4.0;
	private static final int REFRESH_TICKS = 40;

	/** One object of the world: where it stands, what it shows (the latest message) and its hitbox entity. */
	public record Item(String key, int round, int group, SlotView slot, AABB box, double faceX, double faceZ, InteractableEntity entity) {

		/** The key of the item's room in {@link Rooms#all()}. */
		public int roomKey() {
			return round * 10 + group;
		}

		/** What a click does now: {@code use_item}, {@code use}, {@code pickup}, {@code examine}, or null (nothing to do). */
		public String action(String held) {
			if (slot.openPuzzle() != null) {
				return held != null ? "use_item" : "use";
			}
			if (slot.item() != null) {
				return "pickup";
			}
			return slot.readable().isEmpty() ? null : "examine";
		}
	}

	private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
	private static ClientLevel level;
	private static boolean dirty = true;
	private static int sinceSync;
	private static HubBlocks lastBlocks;

	private Interactables() {
	}

	public static void register() {
		Rooms.addListener(() -> dirty = true);
		ClientTickEvents.START_CLIENT_TICK.register(Interactables::tick);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(InteractableRender::render);
		// The documented route: a use or an attack on one of our entities stays on the client.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() || !(entity instanceof InteractableEntity own)) {
				return InteractionResult.PASS;
			}
			if (hand == InteractionHand.MAIN_HAND) {
				Item item = ITEMS.get(own.key());
				if (item != null) {
					activate(item);
				}
			}
			return InteractionResult.FAIL;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
				world.isClientSide() && entity instanceof InteractableEntity ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	/** The objects now standing in the world, by key. */
	static Map<String, Item> items() {
		return ITEMS;
	}

	// ---------------------------------------------------------------- upkeep

	private static void tick(Minecraft mc) {
		if (mc.level != level) {
			clear();
			level = mc.level;
		}
		if (level == null) {
			return;
		}
		HubAnchorDto anchor = PhantasmonApi.hubs() == null ? null : PhantasmonApi.hubs().joinedAnchor();
		View view = GameView.current();
		boolean show = anchor != null && view != null && view.hasGame() && anchor.hub().equals(Link.hub()) && !Rooms.all().isEmpty();
		if (!show) {
			if (!ITEMS.isEmpty()) {
				clear();
			}
			return;
		}
		HubBlocks blocks = HubBlocks.of(anchor);
		if (dirty || !blocks.equals(lastBlocks) || ++sinceSync >= REFRESH_TICKS) {
			dirty = false;
			sinceSync = 0;
			lastBlocks = blocks;
			sync(blocks);
		}
		// Take the click of whatever is aimed, so it never reaches the server at any distance.
		if (mc.screen == null && mc.player != null && !mc.player.isSpectator()) {
			Item aimed = pick(mc);
			if (aimed != null) {
				while (mc.options.keyUse.consumeClick()) {
					activate(aimed);
				}
				while (mc.options.keyAttack.consumeClick()) {
					// swallowed: nothing to attack or break here
				}
			}
		}
	}

	/** Drops every entity (new world, no game, left the hub). */
	public static void clear() {
		for (Item item : ITEMS.values()) {
			item.entity().discard();
		}
		ITEMS.clear();
		dirty = true;
	}

	/** Puts the entities in line with the rooms the backend described: new slots appear, gone ones vanish, changed ones are redrawn. */
	private static void sync(HubBlocks blocks) {
		Map<String, Item> wanted = new LinkedHashMap<>();
		for (RoomView room : Rooms.all().values()) {
			RoomView.Place place = room.place();
			if (place == null) {
				continue;
			}
			int k = RoomFrame.quarterTurns(place.orientation());
			for (SlotView slot : room.slots()) {
				if (slot.shows().isEmpty() || slot.isExit() && slot.openPuzzle() == null && !slot.solvedPuzzle() && slot.item() == null) {
					continue;
				}
				double[] b = RoomFrame.box(place.x(), place.y(), place.z(), k, slot.x(), slot.y(), slot.z(), slot.sx(), slot.sy(), slot.sz());
				AABB box = new AABB(blocks.originX() + b[0], b[1], blocks.originZ() + b[2], blocks.originX() + b[3], b[4], blocks.originZ() + b[5]);
				int[] face = RoomFrame.direction(RoomFrame.facing(slot.orientation(), k));
				String key = room.key() + "#" + slot.id();
				Item old = ITEMS.get(key);
				InteractableEntity entity = old != null && old.box().equals(box) && !old.entity().isRemoved() ? old.entity() : null;
				if (entity == null) {
					if (old != null) {
						old.entity().discard();
					}
					entity = new InteractableEntity(level, key, box);
					level.addEntity(entity);
				}
				wanted.put(key, new Item(key, room.round(), room.group(), slot, box, face[0], face[1], entity));
			}
		}
		for (Map.Entry<String, Item> e : ITEMS.entrySet()) {
			if (!wanted.containsKey(e.getKey())) {
				e.getValue().entity().discard();
			}
		}
		ITEMS.clear();
		ITEMS.putAll(wanted);
	}

	// ---------------------------------------------------------------- aiming and clicking

	/** The object the player aims at within {@value #REACH} blocks and no wall in between, if he may act on it; else null. */
	public static Item pick(Minecraft mc) {
		if (mc.player == null || mc.level == null || ITEMS.isEmpty()) {
			return null;
		}
		Vec3 eye = mc.player.getEyePosition();
		Vec3 end = eye.add(mc.player.getViewVector(1f).scale(REACH));
		Item best = null;
		double bestDistance = Double.MAX_VALUE;
		Vec3 bestPoint = null;
		Map<Integer, RoomView> rooms = new HashMap<>(Rooms.all());
		for (Item item : ITEMS.values()) {
			if (!Rooms.canAct(rooms.get(item.roomKey())) || item.action(Inventory.held()) == null) {
				continue;
			}
			AABB box = item.box().inflate(0.05);
			java.util.Optional<Vec3> hit = box.contains(eye) ? java.util.Optional.of(eye) : box.clip(eye, end);
			if (hit.isEmpty()) {
				continue;
			}
			double d = hit.get().distanceToSqr(eye);
			if (d < bestDistance) {
				bestDistance = d;
				best = item;
				bestPoint = hit.get();
			}
		}
		if (best == null) {
			return null;
		}
		// A wall between us and the object hides it (stopping just short of the object's own box: it may stand in a block).
		Vec3 toward = bestPoint.subtract(eye);
		if (toward.lengthSqr() > 1.0e-4) {
			Vec3 short_ = eye.add(toward.scale(Math.max(0, 1 - 0.04 / toward.length())));
			HitResult wall = mc.level.clip(new ClipContext(eye, short_, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
			if (wall.getType() != HitResult.Type.MISS) {
				return null;
			}
		}
		return best;
	}

	/** What a click on the object does: always a message to the backend, or a reading screen. */
	static void activate(Item item) {
		RoomView room = Rooms.all().get(item.roomKey());
		if (!Rooms.canAct(room)) {
			return;
		}
		String action = item.action(Inventory.held());
		if (action == null) {
			return;
		}
		SlotView slot = item.slot();
		Messages.inRoom(item.round(), item.group(), () -> {
			switch (action) {
				case "use_item" -> Inventory.useOn(Inventory.held(), slot.id());
				case "use" -> Messages.useSlot(slot.id());
				case "pickup" -> Inventory.pickUp(slot.id());
				default -> Documents.open(room, slot);
			}
		});
	}

	/** The slots of {@code room} that stand within reach of the player, nearest first (for the inventory screen's "use on"). */
	public static List<Item> nearby(int round, int group) {
		Minecraft mc = Minecraft.getInstance();
		List<Item> out = new ArrayList<>();
		if (mc.player == null) {
			return out;
		}
		Vec3 at = mc.player.position();
		for (Item item : ITEMS.values()) {
			if (item.round() == round && item.group() == group && item.slot().openPuzzle() != null && item.box().getCenter().distanceTo(at) <= 8) {
				out.add(item);
			}
		}
		out.sort(java.util.Comparator.comparingDouble(i -> i.box().getCenter().distanceToSqr(at)));
		return out;
	}
}
