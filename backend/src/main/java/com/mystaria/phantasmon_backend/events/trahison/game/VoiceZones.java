package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * T5.2, T5.3, T5.4: who can hear whom, as the backend knows it. The voice itself is filtered on each listener's client (it
 * mutes or lowers the others' voices, Documentation/trahison/solutions-techniques.md S4); this class only says, for one
 * viewer, which players share his <em>sound zone</em>, who holds the announce key and whom his ear listens to.
 *
 * <p>The sound zone of a player is his zone of the plan, or, when he stands in a cabin that is closed right now, that cabin
 * (a closed cabin is watertight, whatever its neighbours). The open doors change nothing: it is the zone that counts, and a
 * corridor is a zone of its own. A living viewer never receives the zone of the others, only who is in his own: that is
 * what he would hear anyway, and the others' places stay secret (architecture.md, "Qui voit quoi"). Pure functions.
 */
public final class VoiceZones {

	/** Prefix of the sound zone of a closed cabin, so that it never equals a zone id of the plan. */
	public static final String CABIN_PREFIX = "cabin:";

	private VoiceZones() {
	}

	/**
	 * The sound zone of someone standing in {@code zone} (null when his zone is unknown): the cabin whose inside it is, when
	 * that cabin is closed, else the zone itself.
	 *
	 * @param cabinZones cabin id to the zone id inside it
	 * @param locks      the cabins that are closed right now (cabin id to its lock)
	 */
	public static String soundZone(String zone, Map<String, String> cabinZones, Map<String, CabinLock> locks) {
		if (zone == null || zone.isBlank()) {
			return null;
		}
		for (Map.Entry<String, String> e : cabinZones.entrySet()) {
			if (zone.equals(e.getValue()) && locks.containsKey(e.getKey())) {
				return CABIN_PREFIX + e.getKey();
			}
		}
		return zone;
	}

	/** The sound zone of this seat. */
	public static String soundZoneOf(Game g, int seat) {
		return seat < 0 || seat >= g.players.size() ? null : soundZone(g.players.get(seat).zone, g.cabinZones, g.cabinLocks);
	}

	/**
	 * The sound zone a cabin has right now: its own when closed, else the zone inside it; null for an unknown cabin. This is
	 * what the ear listens to (T5.4).
	 */
	public static String cabinSoundZone(Game g, String cabin) {
		String zone = g.cabinZones.get(cabin);
		if (zone == null) {
			return null;
		}
		return g.cabinLocks.containsKey(cabin) ? CABIN_PREFIX + cabin : zone;
	}

	/** The seats of the humans-and-bots standing in {@code soundZone} (everybody, the viewer's own seat included). */
	public static List<Integer> seatsIn(Game g, String soundZone) {
		List<Integer> out = new ArrayList<>();
		if (soundZone == null) {
			return out;
		}
		for (int i = 0; i < g.players.size(); i++) {
			if (soundZone.equals(soundZoneOf(g, i))) {
				out.add(i);
			}
		}
		return out;
	}

	/**
	 * The {@code voice} block of the view of one account. {@code seat} is his seat (-1 when he does not play) and
	 * {@code seesAll} whether he is one of those who see every position (game masters, ghosts, spectators).
	 * <ul>
	 *   <li>{@code zone}: his own sound zone, null when he has none;</li>
	 *   <li>{@code same}: the other seats in it;</li>
	 *   <li>{@code announcing}: the accounts of the game masters holding their announce key (public: they speak to all);</li>
	 *   <li>{@code ear}: while his ear listens (T7.2), the seats inside the cabin it listens to, else null;</li>
	 *   <li>{@code hear} (D200): only for a living viewer, the seats of his sound zone and of his ear's cabin that are really
	 *   alive, computed with the full truth: a hidden death (D97) is absent at once, and the list does not say who is dead (a
	 *   seat that is absent may as well be elsewhere). Null when {@code seesAll} or when he does not play: he hears everybody;</li>
	 *   <li>{@code sound_zones}: only when {@code seesAll}, the sound zone of every seat.</li>
	 * </ul>
	 */
	public static Map<String, Object> block(Game g, int seat, boolean seesAll) {
		Map<String, Object> out = new java.util.LinkedHashMap<>();
		String mine = soundZoneOf(g, seat);
		List<Object> same = new ArrayList<>();
		for (int i : seatsIn(g, mine)) {
			if (i != seat) {
				same.add(i);
			}
		}
		List<Object> announcing = new ArrayList<>();
		if (g.phase != Phase.ENDED) {
			for (UUID gm : g.announcing) {
				announcing.add(gm.toString());
			}
		}
		String cabin = seat >= 0 ? g.listening.get(seat) : null;
		List<Object> ear = null;
		if (cabin != null && g.phase == Phase.VOTE) {
			ear = new ArrayList<>(seatsIn(g, cabinSoundZone(g, cabin)));
		}
		out.put("zone", mine);
		out.put("same", same);
		out.put("announcing", announcing);
		out.put("ear", ear);
		if (seesAll || seat < 0) {
			out.put("hear", null);
		} else {
			List<Object> hear = new ArrayList<>();
			java.util.Set<Integer> audible = new java.util.TreeSet<>(seatsIn(g, mine));
			if (ear != null) {
				for (Object o : ear) {
					audible.add((Integer) o);
				}
			}
			for (int i : audible) {
				if (i != seat && g.players.get(i).alive) {
					hear.add(i);
				}
			}
			out.put("hear", hear);
		}
		if (seesAll) {
			List<Object> zones = new ArrayList<>();
			for (int i = 0; i < g.players.size(); i++) {
				Map<String, Object> m = new java.util.LinkedHashMap<>();
				m.put("index", i);
				m.put("sound_zone", soundZoneOf(g, i));
				zones.add(m);
			}
			out.put("sound_zones", zones);
		} else {
			out.put("sound_zones", null);
		}
		return out;
	}
}
