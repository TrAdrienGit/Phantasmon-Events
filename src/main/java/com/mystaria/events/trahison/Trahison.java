package com.mystaria.events.trahison;

import java.util.Map;

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

	private Trahison() {
	}

	/** Called once by {@code PhantasmonEvents}. */
	public static void register() {
		PhantasmonApi.onEventsMessage((type, data) -> {
			if (PONG.equals(type)) {
				Minecraft client = Minecraft.getInstance();
				client.execute(() -> chat(Component.translatable("phantasmon_events.trahison.pong")));
			}
		});
	}

	/** {@code /phantasmon event trahison}: checks the link with the backend module. */
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
