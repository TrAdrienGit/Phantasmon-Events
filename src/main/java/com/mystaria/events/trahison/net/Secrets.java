package com.mystaria.events.trahison.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the latest view says about the secret roles and the hidden items (T7): this player's own role, what the role
 * remembers, the items he carries, the ropes (visible to everybody) and, once the game ended, every role. Kept beside
 * {@link GameView.View}, fed by {@link GameView#update}. Nothing here is a rule: the backend decides, this only shows. Client thread.
 */
public final class Secrets {

	/** What the role remembers; the fields of the other roles are null or false. */
	public record RoleInfo(boolean used, Integer group, String side, String vote, boolean paid, String bet, boolean done) {
		public static final RoleInfo NONE = new RoleInfo(false, null, null, null, false, null, false);
	}

	/** A player tied up and when the rope lets go (server epoch ms). */
	public record Rope(int player, long until) {
	}

	/** The reveal at the end: a player's role and whether he met his objective. */
	public record Reveal(int index, String role, boolean objectiveMet) {
	}

	private static String role;
	private static RoleInfo info = RoleInfo.NONE;
	private static List<String> items = List.of();
	private static Long restrainedUntil;
	private static String listening;
	private static List<Rope> ropes = List.of();
	private static List<Reveal> reveal;
	private static boolean spectating;

	private Secrets() {
	}

	/** This player's own role ({@code INFILTRATOR}...{@code GAME_MASTER}), or null. */
	public static String role() {
		return role;
	}

	public static RoleInfo info() {
		return info;
	}

	/** The items this player carries (names of {@code Item}), in the order he found them. */
	public static List<String> items() {
		return items;
	}

	public static List<Rope> ropes() {
		return ropes;
	}

	/** Every role, only once the game has ended; null before. */
	public static List<Reveal> reveal() {
		return reveal;
	}

	/** D111: whether this account registered as a spectator. */
	public static boolean spectating() {
		return spectating;
	}

	/** The cabin this player listens to with the ear during this vote, or null. */
	public static String listening() {
		return listening;
	}

	/** Whether this player is tied up right now (the server's clock, as the view gave it). */
	public static boolean restrained() {
		return restrainedUntil != null && restrainedUntil > GameView.serverNow();
	}

	/** When his rope lets go (server epoch ms), or 0. */
	public static long restrainedUntil() {
		return restrained() ? restrainedUntil : 0;
	}

	public static void clear() {
		role = null;
		info = RoleInfo.NONE;
		items = List.of();
		restrainedUntil = null;
		listening = null;
		ropes = List.of();
		reveal = null;
		spectating = false;
	}

	/** Replaces the state by a received {@code EventsTrahisonView}. */
	@SuppressWarnings("unchecked")
	static void update(Map<String, Object> data) {
		clear();
		spectating = Boolean.TRUE.equals(data.get("viewer_spectating"));
		if (!(data.get("viewer") instanceof Map<?, ?> viewer)) {
			return;
		}
		Map<String, Object> v = (Map<String, Object>) viewer;
		role = v.get("role") == null ? null : v.get("role").toString();
		if (v.get("role_info") instanceof Map<?, ?> ri) {
			Map<String, Object> m = (Map<String, Object>) ri;
			info = new RoleInfo(Boolean.TRUE.equals(m.get("used")), num(m.get("group")), str(m.get("side")), str(m.get("vote")),
					Boolean.TRUE.equals(m.get("paid")), str(m.get("bet")), Boolean.TRUE.equals(m.get("done")));
		}
		List<String> own = new ArrayList<>();
		if (v.get("items") instanceof List<?> list) {
			list.forEach(o -> own.add(String.valueOf(o)));
		}
		items = List.copyOf(own);
		restrainedUntil = v.get("restrained_until") instanceof Number n ? n.longValue() : null;
		listening = str(v.get("listening"));
		List<Rope> tied = new ArrayList<>();
		if (data.get("restrained") instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof Map<?, ?> m && m.get("player") instanceof Number p && m.get("until") instanceof Number u) {
					tied.add(new Rope(p.intValue(), u.longValue()));
				}
			}
		}
		ropes = List.copyOf(tied);
		if (data.get("roles") instanceof List<?> list) {
			List<Reveal> out = new ArrayList<>();
			for (Object o : list) {
				if (o instanceof Map<?, ?> m && m.get("index") instanceof Number i && m.get("role") != null) {
					out.add(new Reveal(i.intValue(), m.get("role").toString(), Boolean.TRUE.equals(m.get("objective_met"))));
				}
			}
			reveal = List.copyOf(out);
		}
	}

	private static Integer num(Object o) {
		return o instanceof Number n ? Integer.valueOf(n.intValue()) : null;
	}

	private static String str(Object o) {
		return o == null ? null : o.toString();
	}
}
