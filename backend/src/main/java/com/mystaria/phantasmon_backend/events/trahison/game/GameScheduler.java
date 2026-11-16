package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Instant;

/** Runs a task at an instant. A task whose instant is already past runs as soon as possible. */
public interface GameScheduler {

	interface Handle {
		void cancel();
	}

	Handle schedule(Instant at, Runnable task);
}
