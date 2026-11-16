package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** A clock that only moves when told, and the {@link GameScheduler} that fires its tasks as the clock reaches them. */
final class ManualClock extends Clock implements GameScheduler {

	private record Task(Instant at, Runnable run, boolean[] cancelled) {
	}

	private Instant now = Instant.parse("2026-11-16T20:00:00Z");
	private final List<Task> tasks = new ArrayList<>();

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		return now;
	}

	@Override
	public Handle schedule(Instant at, Runnable task) {
		boolean[] cancelled = new boolean[1];
		tasks.add(new Task(at, task, cancelled));
		return () -> cancelled[0] = true;
	}

	int pending() {
		return (int) tasks.stream().filter(t -> !t.cancelled[0]).count();
	}

	/** Moves time forward, running every due task in order (a task may schedule the next one). */
	void advance(Duration d) {
		Instant target = now.plus(d);
		while (true) {
			Task next = null;
			for (Task t : tasks) {
				if (!t.cancelled[0] && !t.at.isAfter(target) && (next == null || t.at.isBefore(next.at))) {
					next = t;
				}
			}
			if (next == null) {
				break;
			}
			tasks.remove(next);
			if (next.at.isAfter(now)) {
				now = next.at;
			}
			next.run.run();
		}
		now = target;
	}

	void advanceSeconds(long s) {
		advance(Duration.ofSeconds(s));
	}
}
