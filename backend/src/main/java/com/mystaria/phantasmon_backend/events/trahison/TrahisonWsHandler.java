package com.mystaria.phantasmon_backend.events.trahison;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.addon.WsMessageHandler;
import com.mystaria.phantasmon_backend.websocket.SessionRegistry;
import com.mystaria.phantasmon_backend.websocket.WsMessage;

/**
 * The betrayal game's WebSocket messages (Documentation/trahison/protocole.md), a second {@link WsMessageHandler} next
 * to {@code EventsWsHandler}. It takes every type starting with {@code EventsTrahison}; for now only the Ping/Pong
 * that checks the link between the client and the module.
 */
@Component
public class TrahisonWsHandler implements WsMessageHandler {

	static final String PREFIX = "EventsTrahison";
	static final String PING = "EventsTrahisonPing";
	static final String PONG = "EventsTrahisonPong";

	private final SessionRegistry sessionRegistry;

	public TrahisonWsHandler(SessionRegistry sessionRegistry) {
		this.sessionRegistry = sessionRegistry;
	}

	@Override
	public boolean handles(String type) {
		return type != null && type.startsWith(PREFIX);
	}

	@Override
	public void handle(UUID playerUuid, String type, Map<String, Object> data) {
		if (PING.equals(type)) {
			Object nonce = data == null ? null : data.get("nonce");
			Map<String, Object> reply = nonce == null ? Map.of() : Map.of("nonce", nonce);
			sessionRegistry.send(playerUuid, WsMessage.of(PONG, reply));
			return;
		}
		// Later steps add their messages here; an unknown one is ignored.
	}

	@Override
	public void onDisconnect(UUID playerUuid) {
		// No per-player state yet.
	}
}
