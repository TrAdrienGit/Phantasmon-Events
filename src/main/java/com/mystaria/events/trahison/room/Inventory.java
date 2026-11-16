package com.mystaria.events.trahison.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.Messages;
import com.mystaria.events.trahison.room.RoomView.ItemView;
import com.mystaria.phantasmon.client.hub.HubAvatars;

/**
 * The player's virtual inventory for the room (T3.3): what the backend says the group's bag holds, split into the items he
 * carries and those his teammates carry, plus the one thing that is only local: the item he <i>holds</i> (selected, so that
 * a click on an object of the world uses it). Everything that changes the bag goes to the backend ({@link Messages}); the
 * client only shows the state the next {@code EventsTrahisonRoom} brings. Opens with a key ({@link #KEY}); the item bar
 * ({@link ItemBar}) shows the bag on screen. Client thread.
 */
public final class Inventory {

	/** Opens (or closes) the full inventory screen. */
	public static final KeyMapping KEY = new KeyMapping("key.phantasmon_events.trahison_inventory", InputConstants.Type.KEYSYM,
			org.lwjgl.glfw.GLFW.GLFW_KEY_I, "key.categories.phantasmon_events");
	/** Cycles the held item through the bag (and none). */
	public static final KeyMapping CYCLE_KEY = new KeyMapping("key.phantasmon_events.trahison_item_cycle", InputConstants.Type.KEYSYM,
			org.lwjgl.glfw.GLFW.GLFW_KEY_G, "key.categories.phantasmon_events");

	/** How close a teammate must be to hand him an item, in blocks (the backend adds a little slack). */
	public static final double GIVE_REACH = 4.0;

	private static final String T = "phantasmon_events.trahison.";
	private static final Map<String, ResourceLocation> ICONS = new HashMap<>();
	private static String held;

	private Inventory() {
	}

	/** Called once by {@code Trahison.register()}. */
	public static void register() {
		KeyBindingHelper.registerKeyBinding(KEY);
		KeyBindingHelper.registerKeyBinding(CYCLE_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(Inventory::tick);
		Rooms.addListener(Inventory::dropVanished);
		ItemBar.register();
	}

	private static void tick(Minecraft mc) {
		while (KEY.consumeClick()) {
			if (mc.screen == null && mc.player != null && Rooms.current() != null) {
				mc.setScreen(new com.mystaria.events.trahison.screen.InventoryScreen());
			}
		}
		while (CYCLE_KEY.consumeClick()) {
			if (mc.screen == null) {
				cycleHeld();
			}
		}
	}

	// ---------------------------------------------------------------- the bag

	/** Items the local player carries (those without a holder too), in the order they were picked up. */
	public static List<ItemView> mine() {
		RoomView room = Rooms.current();
		return room == null ? List.of() : room.carriedBy(Rooms.ownIndex());
	}

	/** Items his teammates carry. */
	public static List<ItemView> others() {
		RoomView room = Rooms.current();
		return room == null ? List.of() : room.carriedByOthers(Rooms.ownIndex());
	}

	public static boolean carries(String item) {
		for (ItemView i : mine()) {
			if (i.id().equals(item)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- the held item

	/** The item selected to be used on the world, or null. Always one the player still carries. */
	public static String held() {
		return held;
	}

	public static void hold(String item) {
		held = item == null || !carries(item) ? null : item;
	}

	/** Selects the next item of the bag; after the last one, none. */
	public static void cycleHeld() {
		List<ItemView> mine = mine();
		if (mine.isEmpty()) {
			held = null;
			return;
		}
		int at = -1;
		for (int i = 0; i < mine.size(); i++) {
			if (mine.get(i).id().equals(held)) {
				at = i;
				break;
			}
		}
		held = at + 1 >= mine.size() ? null : mine.get(at + 1).id();
	}

	private static void dropVanished() {
		if (held != null && !carries(held)) {
			held = null;
		}
	}

	// ---------------------------------------------------------------- names and pictures

	/** The display name of an item: its lang key when the pack has one, else its id written out. */
	public static Component name(String id) {
		String key = T + "item." + id;
		return I18n.exists(key) ? Component.translatable(key) : Component.literal(pretty(id));
	}

	/** The item's description, or null when the pack has none. */
	public static Component description(String id) {
		String key = T + "item." + id + ".desc";
		return I18n.exists(key) ? Component.translatable(key) : null;
	}

	/** {@code brass_key} becomes {@code Brass key}. */
	static String pretty(String id) {
		String s = id.replace('_', ' ').replace('-', ' ').trim();
		return s.isEmpty() ? id : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
	}

	/**
	 * The picture of an item: {@code assets/phantasmon_events/textures/trahison/items/<id>.png} (any size, drawn into a
	 * square), or null when there is none (a coloured gem is drawn instead). Replace a placeholder by dropping a file there.
	 */
	public static ResourceLocation icon(String id) {
		ResourceLocation cached = ICONS.get(id);
		if (cached == null) {
			ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("phantasmon_events", "textures/trahison/items/" + id + ".png");
			cached = Minecraft.getInstance().getResourceManager().getResource(rl).isPresent() ? rl : NONE;
			ICONS.put(id, cached);
		}
		return cached == NONE ? null : cached;
	}

	private static final ResourceLocation NONE = ResourceLocation.fromNamespaceAndPath("phantasmon_events", "none");

	/** A stable tint for an item without a picture, from its id, among the charter's tones. */
	public static int tint(String id) {
		int[] tones = { 0xFF5FE0D0, 0xFFFFD36B, 0xFFC04BD8, 0xFF8FBBFF, 0xFF8FE89A, 0xFFFF8A80 };
		return tones[Math.floorMod(id.hashCode(), tones.length)];
	}

	/** Draws an item's picture in a {@code size} square, or a gem of its tint when the pack has no picture for it. */
	public static void drawIcon(net.minecraft.client.gui.GuiGraphics g, String id, int x, int y, int size) {
		ResourceLocation icon = icon(id);
		if (icon != null) {
			Pictures.draw(g, icon, x, y, size, size);
			return;
		}
		int tint = tint(id);
		int cx = x + size / 2;
		int cy = y + size / 2;
		int half = Math.max(3, size / 2 - 2) * 7 / 10;
		int in = Math.max(2, half * 7 / 10);
		com.mystaria.events.trahison.ui.TrahisonUi.rotatedFill(g, cx, cy, 45f, -half, -half, half, half, com.mystaria.events.trahison.ui.TrahisonPalette.BORDER);
		com.mystaria.events.trahison.ui.TrahisonUi.rotatedFill(g, cx, cy, 45f, -in, -in, in, in, tint);
		com.mystaria.events.trahison.ui.TrahisonUi.rotatedFill(g, cx, cy, 45f, -in, -in, 0, 0, com.mystaria.events.trahison.ui.TrahisonUi.withAlpha(0xFFFFFF, 0x70));
	}

	// ---------------------------------------------------------------- actions (all go to the backend)

	public static void give(String item, int to) {
		Messages.giveItem(item, to);
	}

	public static void useOn(String item, String slot) {
		Messages.useItem(item, slot);
	}

	public static void combine(String a, String b) {
		Messages.combine(a, b);
	}

	public static void pickUp(String slot) {
		Messages.pickUp(slot);
	}

	// ---------------------------------------------------------------- teammates

	/**
	 * Who the local player can hand an item to: the group's other living members; in the free time (D8) any other living
	 * player of the game, since players visit each other's rooms.
	 */
	public static List<Integer> teammates() {
		RoomView room = Rooms.current();
		View v = GameView.current();
		List<Integer> out = new ArrayList<>();
		if (room == null || v == null) {
			return out;
		}
		int own = Rooms.ownIndex();
		if (Rooms.roaming(v)) {
			for (GameView.Player p : v.players()) {
				if (p.index() != own && p.alive()) {
					out.add(p.index());
				}
			}
			return out;
		}
		for (int m : room.members()) {
			GameView.Player p = v.player(m);
			if (m != own && p != null && p.alive()) {
				out.add(m);
			}
		}
		return out;
	}

	/** Distance in blocks to a teammate, or -1 when he is nowhere in sight (the real player or his avatar is looked for). */
	public static double distanceTo(int playerIndex) {
		Minecraft mc = Minecraft.getInstance();
		View v = GameView.current();
		GameView.Player p = v == null ? null : v.player(playerIndex);
		UUID uuid = p == null ? null : p.uuid();
		if (mc.player == null || mc.level == null || uuid == null) {
			return -1;
		}
		Player other = HubAvatars.playerOrAvatar(mc.level, uuid);
		return other == null ? -1 : other.distanceTo(mc.player);
	}

	public static boolean near(int playerIndex) {
		double d = distanceTo(playerIndex);
		return d >= 0 && d <= GIVE_REACH;
	}

	public static String teammateName(int playerIndex) {
		View v = GameView.current();
		return v == null ? "#" + (playerIndex + 1) : v.nameOf(playerIndex);
	}
}
