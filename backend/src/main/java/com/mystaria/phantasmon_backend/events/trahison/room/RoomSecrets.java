package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.TreeSet;

/**
 * D228: the free-time hiding place of a room is drawn by the seed among several <em>candidates</em> the room declares: every
 * slot of the kind {@code secret}, plus the slot named by the legacy {@code secret} field of the definition (which keeps an
 * old room valid: one candidate, always drawn). One game plays one candidate; the others are inert. Pure: the same room and
 * the same seed always give the same candidate, whatever the order the slots were authored in.
 */
public final class RoomSecrets {

	/** The slot kind that marks a candidate. */
	public static final String KIND = "secret";
	/** Candidates a well-made room declares at least (fewer is only a warning). */
	public static final int MIN_CANDIDATES = 2;

	private static final long SALT = 0x5EC12E7D228L;

	private RoomSecrets() {
	}

	/** The ids of the candidate slots, sorted (so the draw does not depend on the authoring order). */
	public static List<String> candidates(RoomDefinition room) {
		TreeSet<String> out = new TreeSet<>();
		for (Slot s : room.slots()) {
			if (KIND.equals(s.kind())) {
				out.add(s.id());
			}
		}
		if (room.secret() != null && room.slot(room.secret()).isPresent()) {
			out.add(room.secret());
		}
		return new ArrayList<>(out);
	}

	/** The candidate this seed draws, empty when the room declares none. */
	public static Optional<String> draw(RoomDefinition room, long seed) {
		List<String> all = candidates(room);
		if (all.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(all.get(new Random(seed ^ SALT).nextInt(all.size())));
	}
}
