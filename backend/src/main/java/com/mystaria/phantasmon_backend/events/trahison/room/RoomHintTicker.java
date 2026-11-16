package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * T3.6: every few seconds, asks {@link RoomService} to give the automatic hints. The delays, in minutes without progress,
 * are the property {@code phantasmon.trahison.hint-minutes} (default {@code 10,18,25}; up to three values, ascending).
 */
@Component
public class RoomHintTicker {

	private static final Logger LOG = LoggerFactory.getLogger(RoomHintTicker.class);

	private final RoomService rooms;
	private final long[] delays;

	public RoomHintTicker(RoomService rooms, @Value("${phantasmon.trahison.hint-minutes:10,18,25}") String minutes) {
		this.rooms = rooms;
		this.delays = parse(minutes);
	}

	/** {@code "10,18,25"} to seconds; a bad or empty text gives the defaults. At most {@value Step#MAX_HINTS} values, ascending. */
	static long[] parse(String minutes) {
		try {
			long[] out = Arrays.stream(minutes.split(",")).map(String::trim).filter(s -> !s.isEmpty())
					.mapToLong(s -> Math.round(Double.parseDouble(s) * 60)).filter(v -> v > 0).limit(Step.MAX_HINTS).sorted().toArray();
			return out.length == 0 ? RoomService.DEFAULT_HINT_DELAYS : out;
		} catch (NumberFormatException ex) {
			LOG.warn("Trahison: bad phantasmon.trahison.hint-minutes '{}', using 10,18,25", minutes);
			return RoomService.DEFAULT_HINT_DELAYS;
		}
	}

	@Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
	void tick() {
		try {
			rooms.tickHints(delays).forEach(h -> LOG.info("Trahison hub {} round {} group {}: hint {} of step {}", h.hub(), h.round(),
					h.group(), h.hint().rank(), h.hint().step()));
		} catch (RuntimeException ex) {
			LOG.warn("Trahison: hint tick failed", ex);
		}
	}
}
