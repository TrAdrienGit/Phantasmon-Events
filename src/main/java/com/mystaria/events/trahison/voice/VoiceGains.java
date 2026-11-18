package com.mystaria.events.trahison.voice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The gain rules of the betrayal game's voice (T5.2, T5.3, T5.4; solutions-techniques.md S4 and S5), pure: no Minecraft class,
 * no voice-chat class, so they are tested without the game. The backend says who stands in whose sound zone, who is dead and
 * who holds the announce key; this class only turns that into a gain 0..1 per voice, at the listener's end.
 *
 * <p>Rules for a <em>living</em> listener, in order, for each speaker:
 * announcing game master: full; dead (as far as this listener knows): muted; the ear's target: full; same sound zone: full up
 * to {@link Ranges#full()} blocks, then falling to 0 at {@link Ranges#max()}; anybody else, and anybody the game does not know
 * (a spectator, a stranger): muted. A ghost, a spectator or a game master hears everybody at full gain. Outside a game nothing
 * is filtered.
 */
public final class VoiceGains {

	/** The ranges within a sound zone, in blocks. */
	public record Ranges(double full, double max, double whisperFull, double whisperMax) {

		/** Voice: full to 4 blocks, nothing from 8. Whisper: full to 1.5, nothing from 3 (the proportions of the voice). */
		public static final Ranges DEFAULT = new Ranges(4, 8, 1.5, 3);
	}

	/** One voice's rule: a fixed gain, or "by distance inside the zone". */
	public record Entry(boolean byDistance, double value) {

		public static final Entry MUTE = new Entry(false, 0);
		public static final Entry FULL = new Entry(false, 1);

		public static Entry fixed(double gain) {
			return new Entry(false, Math.max(0, Math.min(1, gain)));
		}

		/** {@code distance} in blocks to the listener; NaN when the speaker is not in the world (then the zone alone decides: full). */
		public static Entry zone(double distance) {
			return new Entry(true, distance);
		}
	}

	/**
	 * One player the listener may hear. {@code id}: his account; {@code avatarId}: the entity id of his hub-copy avatar, whose
	 * voice arrives under that key (S5), or null; {@code dead}: dead as far as the listener knows; {@code sameZone}: stands in
	 * the listener's sound zone; {@code ear}: in the cabin the listener's ear listens to; {@code announcing}: a game master
	 * holding the announce key; {@code distance}: blocks to the listener, NaN if unknown.
	 */
	public record Speaker(UUID id, UUID avatarId, boolean dead, boolean sameZone, boolean ear, boolean announcing, double distance) {
	}

	/** What the game thread knows each tick; {@code inGame} false outside a game. */
	public record Input(boolean inGame, boolean listenerLiving, List<Speaker> speakers) {
	}

	/**
	 * The result: a rule per voice key, and what an unknown key gets ({@code unknownGain}). Immutable, handed to the audio thread.
	 */
	public record Table(Map<UUID, Entry> entries, double unknownGain) {

		/** No filtering at all. */
		public static final Table OPEN = new Table(Map.of(), 1);

		public Table {
			entries = Map.copyOf(entries);
		}

		/** The gain for the voice under {@code key}; {@code whispering}: he uses the whisper key. */
		public double gain(UUID key, boolean whispering, Ranges ranges) {
			Entry e = key == null ? null : entries.get(key);
			if (e == null) {
				return unknownGain;
			}
			return e.byDistance() ? falloff(e.value(), whispering, ranges) : e.value();
		}

		public double gain(UUID key, boolean whispering) {
			return gain(key, whispering, Ranges.DEFAULT);
		}

		/** The same table with these keys forced to a gain (the admin test command, T5.1). */
		public Table withForced(Map<UUID, Double> forced) {
			if (forced.isEmpty()) {
				return this;
			}
			Map<UUID, Entry> all = new HashMap<>(entries);
			forced.forEach((k, v) -> all.put(k, Entry.fixed(v)));
			return new Table(all, unknownGain);
		}
	}

	private VoiceGains() {
	}

	/** Builds the table of this tick. */
	public static Table compute(Input in) {
		if (!in.inGame()) {
			return Table.OPEN;
		}
		Map<UUID, Entry> out = new HashMap<>();
		for (Speaker s : in.speakers()) {
			Entry rule = rule(in.listenerLiving(), s);
			if (s.id() != null) {
				out.put(s.id(), rule);
			}
			if (s.avatarId() != null) {
				out.put(s.avatarId(), rule);
			}
		}
		// A voice the game does not list (a spectator, somebody who is not in it) reaches a living listener muted, the others at full.
		return new Table(out, in.listenerLiving() ? 0 : 1);
	}

	private static Entry rule(boolean listenerLiving, Speaker s) {
		if (s.announcing() || !listenerLiving) {
			return Entry.FULL;
		}
		if (s.dead()) {
			return Entry.MUTE;
		}
		if (s.ear()) {
			return Entry.FULL;
		}
		return s.sameZone() ? Entry.zone(s.distance()) : Entry.MUTE;
	}

	/** Gain by distance inside a sound zone: 1 up to the full range, linear down to 0 at the maximum; NaN (not in the world) is full. */
	public static double falloff(double distance, boolean whispering, Ranges r) {
		if (Double.isNaN(distance)) {
			return 1;
		}
		double full = whispering ? r.whisperFull() : r.full();
		double max = whispering ? r.whisperMax() : r.max();
		if (distance <= full) {
			return 1;
		}
		if (distance >= max) {
			return 0;
		}
		return (max - distance) / (max - full);
	}

	/**
	 * Applies a gain to a PCM frame (16 bit). An empty frame (end of speech) and a gain of 1 or more are returned as they are;
	 * a gain of 0 gives silence of the same length (never null), anything else scales every sample, clamped.
	 */
	public static short[] apply(short[] frame, double gain) {
		if (frame == null || frame.length == 0 || gain >= 1) {
			return frame;
		}
		short[] out = new short[frame.length];
		if (gain <= 0) {
			return out;
		}
		for (int i = 0; i < frame.length; i++) {
			long v = Math.round(frame[i] * gain);
			out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
		}
		return out;
	}
}
