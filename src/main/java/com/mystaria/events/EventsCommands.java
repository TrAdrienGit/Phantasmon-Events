package com.mystaria.events;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;

import com.mystaria.events.hub.MirrorHubCommands;
import com.mystaria.events.tournament.TournamentScreen;

/**
 * Phantasmon Events' commands, all under {@code /phantasmon event} (Adrien 2026-10-30: as few commands as possible,
 * the rest in menus):
 * <ul>
 * <li>{@code tournament}: opens the tournaments menu, where players register and check in, and admins create and run
 * the tournaments;</li>
 * <li>{@code hub ...}: the mirror hubs, admins only ({@link MirrorHubCommands}).</li>
 * </ul>
 */
public final class EventsCommands {

	private EventsCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("phantasmon").then(ClientCommandManager.literal("event")
						.then(ClientCommandManager.literal("tournament").executes(context -> {
							// The chat closes after the command: the screen opens on the next tick.
							Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new TournamentScreen()));
							return 1;
						}))
						.then(MirrorHubCommands.node()))));
	}
}
