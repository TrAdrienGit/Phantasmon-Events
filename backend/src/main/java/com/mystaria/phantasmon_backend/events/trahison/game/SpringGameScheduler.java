package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The production {@link GameScheduler}: one daemon thread. */
public class SpringGameScheduler implements GameScheduler {

	private static final Logger log = LoggerFactory.getLogger(SpringGameScheduler.class);

	private final Clock clock;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "trahison-timers");
		thread.setDaemon(true);
		return thread;
	});

	public SpringGameScheduler(Clock clock) {
		this.clock = clock;
	}

	@Override
	public Handle schedule(Instant at, Runnable task) {
		long delay = Math.max(0, Duration.between(clock.instant(), at).toMillis());
		ScheduledFuture<?> future = executor.schedule(() -> {
			try {
				task.run();
			} catch (RuntimeException e) {
				// an uncaught exception would vanish inside the future and leave the game silently stuck
				log.error("Trahison timer task failed", e);
			}
		}, delay, TimeUnit.MILLISECONDS);
		return () -> future.cancel(false);
	}

	@PreDestroy
	void stop() {
		executor.shutdownNow();
	}
}
