package com.mystaria.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import com.mystaria.events.hub.MirrorCapture;
import com.mystaria.events.hub.MirrorCopy;
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
		MirrorCopy.register();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			MirrorCapture.tick();
			MirrorCopy.tick();
		});
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> MirrorCapture.onChunkLoad(chunk.getPos()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			MirrorCapture.reset();
			MirrorCopy.reset();
		}));
		LOG.info("Phantasmon Events loaded");
	}
}
