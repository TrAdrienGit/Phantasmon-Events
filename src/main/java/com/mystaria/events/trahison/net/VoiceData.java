package com.mystaria.events.trahison.net;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The {@code voice} block of the view (T5.2 to T5.4, protocole.md), kept beside it like {@link GmData}: who stands in this
 * viewer's sound zone, which game masters hold their announce key, and whom his ear listens to. No rule lives here: the
 * gain rules are in {@code voice.VoiceGains}. Plain data, no Minecraft class, so it is tested without the game.
 */
public final class VoiceData {

	/**
	 * {@code zone}: this viewer's own sound zone (null if unknown); {@code same}: the seats in it; {@code announcing}: the
	 * accounts of the game masters holding the announce key; {@code ear}: the seats of the cabin his ear listens to, null when
	 * it does not.
	 */
	public record Voice(String zone, Set<Integer> same, Set<UUID> announcing, Set<Integer> ear) {

		public static final Voice NONE = new Voice(null, Set.of(), Set.of(), null);
	}

	private static volatile Voice current = Voice.NONE;

	private VoiceData() {
	}

	/** The latest block, {@link Voice#NONE} without a game. */
	public static Voice current() {
		return current;
	}

	static void clear() {
		current = Voice.NONE;
	}

	static void update(Map<String, Object> data) {
		current = parse(data.get("voice"));
	}

	/** Reads a {@code voice} block; anything malformed gives {@link Voice#NONE}. */
	public static Voice parse(Object block) {
		if (!(block instanceof Map<?, ?> m)) {
			return Voice.NONE;
		}
		Set<Integer> ear = m.get("ear") instanceof List<?> l ? seats(l) : null;
		return new Voice(m.get("zone") instanceof String s ? s : null, m.get("same") instanceof List<?> l ? seats(l) : Set.of(),
			m.get("announcing") instanceof List<?> l ? accounts(l) : Set.of(), ear);
	}

	private static Set<Integer> seats(List<?> list) {
		Set<Integer> out = new HashSet<>();
		for (Object o : list) {
			if (o instanceof Number n) {
				out.add(n.intValue());
			}
		}
		return Set.copyOf(out);
	}

	private static Set<UUID> accounts(List<?> list) {
		Set<UUID> out = new HashSet<>();
		for (Object o : list) {
			try {
				out.add(UUID.fromString(String.valueOf(o)));
			} catch (IllegalArgumentException ignored) {
				// not an account id: skipped
			}
		}
		return Set.copyOf(out);
	}
}
