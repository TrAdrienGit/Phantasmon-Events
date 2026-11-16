package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Clock;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutService;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomCatalog;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomService;

/** Wires the game engine: real clock, timer thread, database store; reloads unfinished games once the app is up. */
@Configuration(proxyBeanMethods = false)
public class TrahisonGameConfig {

	@Bean
	GameStore trahisonGameStore(JdbcTemplate jdbc) {
		return new JdbcGameStore(jdbc);
	}

	@Bean
	GameScheduler trahisonGameScheduler() {
		return new SpringGameScheduler(Clock.systemUTC());
	}

	@Bean
	GameService trahisonGameService(GameStore store, GameScheduler scheduler, GameRooms rooms, LayoutService layouts) {
		GameService service = new GameService(store, Clock.systemUTC(), scheduler);
		service.useRooms(rooms);
		service.useLayouts(layouts::version);
		return service;
	}

	/** The rooms of a round come from the room bank (a test room id in the settings) or the built-in fallback. */
	@Bean
	GameRooms trahisonGameRooms(RoomService roomService, RoomCatalog catalog) {
		return new GameRooms(roomService, catalog::find);
	}

	/** The bots play through the service and listen to it; created before the games are reloaded. */
	@Bean
	Bots trahisonBots(GameService service, GameScheduler scheduler) {
		Bots bots = new Bots(service, Clock.systemUTC(), scheduler);
		service.addListener(bots);
		return bots;
	}

	/** A game running when the backend stopped resumes: its deadline is scheduled again (at once if it passed). */
	@EventListener(ApplicationReadyEvent.class)
	void reloadGames(ApplicationReadyEvent event) {
		event.getApplicationContext().getBean(GameService.class).loadActiveGames();
	}
}
