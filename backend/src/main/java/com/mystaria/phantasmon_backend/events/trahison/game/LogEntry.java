package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One thing that happened. Internal: a vote is logged without its side while the round runs (it appears in the
 * {@code results} entry), but views must still never ship the log to a player.
 */
public class LogEntry {
	public Instant at;
	public String type;
	public Map<String, Object> data = new LinkedHashMap<>();

	public LogEntry() {
	}

	public LogEntry(Instant at, String type, Map<String, Object> data) {
		this.at = at;
		this.type = type;
		this.data = new LinkedHashMap<>(data);
	}
}
