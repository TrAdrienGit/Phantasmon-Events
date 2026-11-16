package com.mystaria.events.trahison;

import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * Client side of the betrayal game, a part of Phantasmon Events. The backend decides everything; this side only
 * shows what it receives and sends the player's actions (Documentation/trahison/protocole.md).
 */
public final class Trahison {

	public static final String PING = "EventsTrahisonPing";
	public static final String PONG = "EventsTrahisonPong";

	/** The provisional player screen's key: unbound by default (the command {@code /phantasmon event trahison menu} always works). */
	public static final KeyMapping MENU_KEY = new KeyMapping("key.phantasmon_events.trahison_menu",
			InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.phantasmon_events");

	/** Key V: close the cabin you stand in, then open its voting pupitre (T2.4). Rebindable. */
	public static final KeyMapping CABIN_KEY = new KeyMapping("key.phantasmon_events.trahison_cabin",
			InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_V, "key.categories.phantasmon_events");

	private Trahison() {
	}

	/** Called once by {@code PhantasmonEvents}. */
	public static void register() {
		com.mystaria.events.trahison.edit.TrahisonEditor.register();
		com.mystaria.events.trahison.minigame.PuzzleScreens.install();
		com.mystaria.events.trahison.net.Link.register();
		com.mystaria.events.trahison.hud.BraceletHud.register();
		com.mystaria.events.trahison.world.Zones.register();
		com.mystaria.events.trahison.world.FakeBlocks.register();
		com.mystaria.events.trahison.world.DoorPanels.register();
		com.mystaria.events.trahison.world.CabinFlow.register();
		com.mystaria.events.trahison.world.ResultsBoardWorld.register();
		com.mystaria.events.trahison.room.Rooms.register();
		com.mystaria.events.trahison.room.Inventory.register();
		com.mystaria.events.trahison.world.Interactables.register();
		com.mystaria.events.trahison.hud.CabinPrompt.register();
		com.mystaria.events.trahison.hud.ResultsHud.register();
		KeyBindingHelper.registerKeyBinding(MENU_KEY);
		KeyBindingHelper.registerKeyBinding(CABIN_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (MENU_KEY.consumeClick()) {
				if (client.screen == null && client.player != null) {
					com.mystaria.events.trahison.screen.GameScreen.open();
				}
			}
		});
		PhantasmonApi.onEventsMessage((type, data) -> {
			if (PONG.equals(type)) {
				Minecraft client = Minecraft.getInstance();
				client.execute(() -> chat(Component.translatable("phantasmon_events.trahison.pong")));
			}
		});
	}

	/** {@code /phantasmon event trahison ping}: checks the link with the backend module. */
	public static void ping() {
		if (!PhantasmonApi.send(PING, Map.of("nonce", Long.toString(System.nanoTime())))) {
			chat(Component.translatable("phantasmon_events.trahison.not_connected"));
		}
	}

	private static void chat(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(message, false);
		}
	}
}
