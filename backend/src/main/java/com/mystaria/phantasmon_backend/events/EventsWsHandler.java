package com.mystaria.phantasmon_backend.events;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.addon.WsMessageHandler;

/** Phantasmon Events' WebSocket messages, mirror hubs (D-38), through the core's add-on extension point (TODO-35). */
@Component
public class EventsWsHandler implements WsMessageHandler {

	private static final Set<String> TYPES = Set.of("EventsMirrorBlocks", "EventsMirrorTile", "EventsMirrorSubscribe",
			"EventsMirrorUnsubscribe", "EventsMirrorGhosts", "EventsMirrorWatch", "EventsMirrorUnwatch");

	private final MirrorService mirrorService;

	public EventsWsHandler(MirrorService mirrorService) {
		this.mirrorService = mirrorService;
	}

	@Override
	public boolean handles(String type) {
		return TYPES.contains(type);
	}

	@Override
	public void handle(UUID playerUuid, String type, Map<String, Object> data) {
		switch (type) {
			case "EventsMirrorBlocks" -> mirrorService.receiveBlocks(playerUuid, data);
			case "EventsMirrorTile" -> mirrorService.receiveTile(playerUuid, data);
			case "EventsMirrorSubscribe" -> mirrorService.subscribe(playerUuid, data.get("hub"), true);
			case "EventsMirrorUnsubscribe" -> mirrorService.subscribe(playerUuid, data.get("hub"), false);
			case "EventsMirrorGhosts" -> mirrorService.receiveGhosts(playerUuid, data);
			case "EventsMirrorWatch" -> mirrorService.watch(playerUuid, data.get("hub"), true);
			case "EventsMirrorUnwatch" -> mirrorService.watch(playerUuid, data.get("hub"), false);
			default -> throw new IllegalArgumentException(type);
		}
	}

	@Override
	public void onDisconnect(UUID playerUuid) {
		mirrorService.onDisconnect(playerUuid);
	}
}
