package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Clock;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

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
	GameService trahisonGameService(GameStore store, GameScheduler scheduler) {
		return new GameService(store, Clock.systemUTC(), scheduler);
	}

	/** A game running when the backend stopped resumes: its deadline is scheduled again (at once if it passed). */
	@EventListener(ApplicationReadyEvent.class)
	void reloadGames(ApplicationReadyEvent event) {
		event.getApplicationContext().getBean(GameService.class).loadActiveGames();
	}
}
