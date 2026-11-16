package com.mystaria.phantasmon_backend.events.trahison.room;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;

/** Wires the room engine with the real clock. Listeners are added by whoever sends the views (the WebSocket handler). */
@Configuration(proxyBeanMethods = false)
public class TrahisonRoomConfig {

	@Bean
	RoomService trahisonRoomService(MechanismRegistry registry) {
		return new RoomService(registry, Clock.systemUTC());
	}
}
