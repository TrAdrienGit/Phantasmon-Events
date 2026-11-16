package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

/** The production {@link GameScheduler}: one daemon thread. */
public class SpringGameScheduler implements GameScheduler {

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
		ScheduledFuture<?> future = executor.schedule(task, delay, TimeUnit.MILLISECONDS);
		return () -> future.cancel(false);
	}

	@PreDestroy
	void stop() {
		executor.shutdownNow();
	}
}
