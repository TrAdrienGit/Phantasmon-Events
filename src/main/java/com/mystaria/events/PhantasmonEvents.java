package com.mystaria.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import com.mystaria.events.hub.MirrorCapture;
import com.mystaria.events.hub.MirrorCopy;
import com.mystaria.events.hub.MirrorGhosts;
import com.mystaria.events.hub.MirrorHubCommands;
import com.mystaria.events.tournament.TournamentCommands;

/** Phantasmon Events: add-on of Phantasmon for Adrien's community events (mirror hubs first). Client only. */
public final class PhantasmonEvents implements ClientModInitializer {

	public static final String MOD_ID = "phantasmon_events";
	private static final Logger LOG = LoggerFactory.getLogger(PhantasmonEvents.class);

	@Override
	public void onInitializeClient() {
		MirrorHubCommands.register();
		TournamentCommands.register();
		com.mystaria.events.tournament.TournamentScreen.register();
		com.mystaria.events.tournament.TournamentAnnouncer.register();
		com.mystaria.events.tournament.TournamentOpponent.register();
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((graphics, tickCounter) ->
				com.mystaria.events.tournament.TournamentOpponent.render(graphics));
		net.minecraft.client.KeyMapping tournaments = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
				new net.minecraft.client.KeyMapping("key.phantasmon_events.tournaments", com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(),
						"key.categories.phantasmon_events"));
		MirrorCopy.register();
		MirrorGhosts.register();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (tournaments.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new com.mystaria.events.tournament.TournamentScreen());
				}
			}
			MirrorCapture.tick();
			MirrorGhosts.tick();
			com.mystaria.events.tournament.TournamentOpponent.tick();
			MirrorCopy.tick();
		});
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			MirrorCapture.onChunkLoad(chunk.getPos());
			MirrorGhosts.onChunkLoad(level, chunk.getPos());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			MirrorCapture.reset();
			MirrorGhosts.reset();
			com.mystaria.events.tournament.TournamentOpponent.reset();
			MirrorCopy.reset();
		}));
		LOG.info("Phantasmon Events loaded");
	}
}
