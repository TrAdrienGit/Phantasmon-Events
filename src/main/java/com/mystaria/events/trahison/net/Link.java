package com.mystaria.events.trahison.net;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import com.mystaria.events.trahison.hud.Announcer;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;

/**
 * The wiring between Phantasmon's WebSocket and the game's client state. Receives {@code EventsTrahisonView} and
 * {@code EventsTrahisonSay}; follows the hub the player is in and asks for the view when joining one (a module is not told
 * when a player connects), retrying until the socket is up, and again every {@link #KEEP_ALIVE_TICKS} (a reconnected
 * socket is otherwise unknown to the backend, which marks the player disconnected). Client thread.
 */
public final class Link {

	public static final String VIEW = "EventsTrahisonView";
	public static final String SAY = "EventsTrahisonSay";

	private static final int RETRY_TICKS = 40;
	private static final int KEEP_ALIVE_TICKS = 600;

	private static String hub;
	private static boolean viewPending;
	private static int ticks;

	private Link() {
	}

	/** Called once by {@code Trahison.register()}. */
	public static void register() {
		PhantasmonApi.onEventsMessage((type, data) -> {
			if (VIEW.equals(type)) {
				GameView.update(data);
			} else if (SAY.equals(type)) {
				Announcer.onSay(data);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(Link::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			hub = null;
			viewPending = false;
			GameView.clear();
			Announcer.clear();
		}));
	}

	/** The hub the player has joined (a mirror hub's name), or null. */
	public static String hub() {
		return hub;
	}

	private static void tick(Minecraft client) {
		String now = currentHub(client);
		if (now == null ? hub != null : !now.equals(hub)) {
			hub = now;
			GameView.clear();
			viewPending = now != null;
			ticks = 0;
		}
		if (hub == null) {
			return;
		}
		ticks++;
		if (viewPending && ticks % RETRY_TICKS == 1 || ticks % KEEP_ALIVE_TICKS == 0) {
			if (Messages.view()) {
				viewPending = false;
			}
		}
	}

	private static String currentHub(Minecraft client) {
		if (client.player == null || PhantasmonApi.hubs() == null) {
			return null;
		}
		HubAnchorDto anchor = PhantasmonApi.hubs().joinedAnchor();
		return anchor == null ? null : anchor.hub();
	}
}
