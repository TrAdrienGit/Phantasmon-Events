package com.mystaria.events.trahison.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What only a game master's view carries beyond {@link GameView.Gm} (T7.4): every role and carried items, the log of the game,
 * the state of the cabin offer and the settings of the temptations and the roles. Fed by {@link GameView#update}; empty for
 * anyone else. Nothing here is a rule: the backend decides, the screen only shows it. Client thread.
 */
public final class GmData {

	/** One line of the log of the game; {@code data} is what the backend wrote with it. */
	public record Entry(long at, String type, Map<String, Object> data) {
	}

	/** The cabin offer of the vote: the seat it is meant for ({@code -1}: none), and whether it was handed over. */
	public record Offer(int seat, boolean made, boolean forced) {
	}

	/** The settings the screen edits. */
	public record Options(boolean offers, boolean leaks, boolean race, boolean announceDeaths, int roleCount, List<String> roles,
			Map<String, Integer> items) {
	}

	private static List<String> roles = List.of();
	private static List<List<String>> carried = List.of();
	private static Map<String, String> hidden = Map.of();
	private static List<Entry> log = List.of();
	private static Offer offer = new Offer(-1, false, false);
	private static Options options = new Options(true, true, true, false, 3, List.of(), Map.of());

	private GmData() {
	}

	/** The role of a seat (a name of the backend's {@code Role}) or null. */
	public static String role(int seat) {
		return seat >= 0 && seat < roles.size() ? roles.get(seat) : null;
	}

	/** The items a seat carries (names of the backend's {@code Item}). */
	public static List<String> carried(int seat) {
		return seat >= 0 && seat < carried.size() ? carried.get(seat) : List.of();
	}

	/** The item still lying in a hiding spot (plan spot id, or {@code room:<round>:<group>:<slot>} for a room's live secret), or null. */
	public static String hiddenAt(String spot) {
		return hidden.get(spot);
	}

	public static List<Entry> log() {
		return log;
	}

	public static Offer offer() {
		return offer;
	}

	public static Options options() {
		return options;
	}

	static void clear() {
		roles = List.of();
		carried = List.of();
		hidden = Map.of();
		log = List.of();
		offer = new Offer(-1, false, false);
	}

	@SuppressWarnings("unchecked")
	static void update(Map<String, Object> data) {
		clear();
		if (!(data.get("gm") instanceof Map<?, ?> raw)) {
			return;
		}
		Map<String, Object> gm = (Map<String, Object>) raw;
		List<String> r = new ArrayList<>();
		for (Object o : list(gm.get("roles"))) {
			r.add(o instanceof Map<?, ?> m && m.get("role") != null ? m.get("role").toString() : null);
		}
		roles = r;
		List<List<String>> c = new ArrayList<>();
		for (Object o : list(gm.get("player_items"))) {
			List<String> own = new ArrayList<>();
			if (o instanceof Map<?, ?> m) {
				list(m.get("items")).forEach(i -> own.add(String.valueOf(i)));
			}
			c.add(own);
		}
		carried = c;
		Map<String, String> h = new LinkedHashMap<>();
		for (Object o : list(gm.get("hidden_items"))) {
			if (o instanceof Map<?, ?> m && m.get("spot") != null && m.get("item") != null) {
				h.put(m.get("spot").toString(), m.get("item").toString());
			}
		}
		hidden = h;
		List<Entry> entries = new ArrayList<>();
		for (Object o : list(gm.get("log"))) {
			if (o instanceof Map<?, ?> m && m.get("type") != null) {
				Map<String, Object> d = m.get("data") instanceof Map<?, ?> dm ? new LinkedHashMap<>((Map<String, Object>) dm) : new LinkedHashMap<>();
				entries.add(new Entry(m.get("at") instanceof Number n ? n.longValue() : 0, m.get("type").toString(), d));
			}
		}
		log = entries;
		if (gm.get("offer") instanceof Map<?, ?> o) {
			offer = new Offer(o.get("seat") instanceof Number n ? n.intValue() : -1, Boolean.TRUE.equals(o.get("made")), Boolean.TRUE.equals(o.get("forced")));
		}
		Map<String, Object> settings = gm.get("settings") instanceof Map<?, ?> s ? (Map<String, Object>) s : Map.of();
		Map<String, Object> rs = gm.get("role_settings") instanceof Map<?, ?> s ? (Map<String, Object>) s : Map.of();
		Map<String, Integer> items = new LinkedHashMap<>();
		if (rs.get("items") instanceof Map<?, ?> im) {
			im.forEach((k, v) -> items.put(String.valueOf(k), v instanceof Number n ? n.intValue() : 0));
		}
		List<String> activeRoles = new ArrayList<>();
		list(rs.get("roles")).forEach(x -> activeRoles.add(String.valueOf(x)));
		options = new Options(!Boolean.FALSE.equals(settings.get("offers")), !Boolean.FALSE.equals(settings.get("leaks")),
				!Boolean.FALSE.equals(settings.get("race")), Boolean.TRUE.equals(settings.get("announce_deaths")),
				rs.get("role_count") instanceof Number n ? n.intValue() : 3, activeRoles, items);
	}

	private static List<?> list(Object o) {
		return o instanceof List<?> l ? l : List.of();
	}
}
