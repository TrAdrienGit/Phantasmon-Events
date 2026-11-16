package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.Outcome;
import com.mystaria.phantasmon_backend.events.trahison.rules.DoorGroup;
import com.mystaria.phantasmon_backend.events.trahison.rules.Placement;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;
import com.mystaria.phantasmon_backend.events.trahison.rules.Vote;

/**
 * The only way the state of a game leaves the backend: {@code EventsTrahisonView} data, one per recipient (architecture.md
 * section 4, "Qui voit quoi"; documented field by field in protocole.md). The result is made of maps, lists, strings,
 * numbers and booleans only, ready for any JSON mapper.
 *
 * <p>Secrets: the votes of a round that is not resolved yet (no RESULTS so far) are given to the game master only, and
 * a player gets his own vote and whether his side voted, never the other side's. The log, the seed and the settings
 * are for the game master only. Call it while the game is locked ({@link GameService#read}).
 */
public final class Views {

	/** Who the view is for. */
	public enum Kind {
		/** A living player of the game. */
		PLAYER,
		/** A dead player of the game. */
		GHOST,
		/** Anyone else: a hub member, or whoever asked. */
		SPECTATOR,
		/** An admin who runs the game and does not play it. */
		GM
	}

	private Views() {
	}

	/** The kind of view this account gets: players first (a game master who plays is a player), then game masters. */
	public static Kind kindOf(Game g, UUID uuid) {
		int i = g.indexOf(uuid);
		if (i >= 0) {
			return g.players.get(i).alive ? Kind.PLAYER : Kind.GHOST;
		}
		return g.gameMasters.contains(uuid) ? Kind.GM : Kind.SPECTATOR;
	}

	/** The view of a hub that has no game: only the hub and the phase {@code NONE}. */
	public static Map<String, Object> none(String hub) {
		return orderedMap("hub", hub, "phase", "NONE");
	}

	public static Map<String, Object> build(Game g, UUID viewer, Instant now) {
		return build(g, kindOf(g, viewer), viewer, now);
	}

	public static Map<String, Object> build(Game g, Kind kind, UUID viewer, Instant now) {
		RoundState r = g.currentRound();
		Map<String, Object> v = new LinkedHashMap<>();
		v.put("hub", g.hub);
		v.put("game_id", g.id.toString());
		v.put("layout_version", g.layoutVersion);
		v.put("phase", g.phase.name());
		v.put("round", g.round);
		v.put("final_loop", r != null && r.finalLoop);
		v.put("final_loops", g.finalLoops);
		v.put("mode", g.settings.mode.name());
		v.put("deadline", g.deadline == null ? null : g.deadline.toEpochMilli());
		v.put("paused", g.paused);
		v.put("auto_paused", g.autoPaused);
		v.put("remaining_ms", remaining(g, now));
		v.put("server_time", now.toEpochMilli());

		List<Object> players = new ArrayList<>();
		for (int i = 0; i < g.players.size(); i++) {
			PlayerState p = g.players.get(i);
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("index", i);
			m.put("name", p.name);
			m.put("uuid", p.uuid.toString());
			m.put("connected", p.connected);
			m.put("alive", p.alive);
			m.put("is_bot", p.bot);
			players.add(m);
		}
		v.put("players", players);

		List<Object> bracelets = new ArrayList<>();
		for (BraceletState b : g.bracelets) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("index", b.index);
			m.put("points", b.points);
			Placement pl = b.placement;
			m.put("color", pl == null ? null : pl.color().name());
			m.put("status", pl == null ? null : pl.status().name());
			m.put("holder", b.holder);
			m.put("owner_alive", b.ownerAlive);
			bracelets.add(m);
		}
		v.put("bracelets", bracelets);

		List<Object> doors = new ArrayList<>();
		for (int k = 0; k < Door.ALL.size(); k++) {
			Door d = Door.ALL.get(k);
			doors.add(orderedMap("index", k, "colors", List.of(d.first().name(), d.second().name())));
		}
		v.put("doors", doors);

		boolean splitKnown = r != null && r.splitIndex != null;
		v.put("groups", splitKnown ? groups(r) : null);
		v.put("doors_passed", r == null ? null : ints(r.passedDoor));
		v.put("doors_state", doorsState(g, kind == Kind.PLAYER || kind == Kind.GHOST ? g.indexOf(viewer) : -1));
		v.put("door_hints", doorHints(g));
		v.put("cabins_required", g.settings.cabinsRequired);
		v.put("cabins", cabins(g, kind == Kind.PLAYER || kind == Kind.GHOST ? g.indexOf(viewer) : -1));
		v.put("viewer", viewer(g, r, kind, viewer));
		v.put("results", results(g));
		v.put("winners", g.phase == Phase.ENDED ? new ArrayList<>(g.winners) : null);
		v.put("gm", kind == Kind.GM ? gmBlock(g, r) : null);
		return v;
	}

	/** {@code door id -> "OPEN" | "CLOSED"} as this viewer sees it (a door closed for all may be open for him). */
	private static Map<String, Object> doorsState(Game g, int viewerIndex) {
		Map<String, Object> out = new LinkedHashMap<>();
		g.doors.forEach((id, state) -> out.put(id, state.openFor(viewerIndex) ? "OPEN" : "CLOSED"));
		return out;
	}

	/**
	 * What each colour door of the round says about its threshold (DOORS only; empty otherwise), the same for every
	 * recipient: who stands there is visible to all anyway. {@code open_until}: when the door closes again, null while it is
	 * closed.
	 */
	private static List<Object> doorHints(Game g) {
		List<Object> out = new ArrayList<>();
		for (DoorHint h : g.doorHints) {
			DoorWindow window = g.doorWindows.get(h.door());
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("door", h.door());
			m.put("index", h.index());
			m.put("colors", new ArrayList<>(h.colors()));
			m.put("present", h.present());
			m.put("open_until", window == null ? null : window.untilMs());
			m.put("ready", h.ready());
			m.put("incompatible", h.incompatible());
			m.put("missing", needs(h.missing()));
			m.put("alt_missing", h.altMissing() == null ? null : needs(h.altMissing()));
			out.add(m);
		}
		return out;
	}

	/**
	 * The cabins of the plan and whether each is closed, the same for every recipient (a closed cabin door is visible to all);
	 * {@code closed_by_me}: the viewer is the one who closed it. Who is inside is never given.
	 */
	private static List<Object> cabins(Game g, int viewerIndex) {
		List<Object> out = new ArrayList<>();
		for (String id : g.cabinIds) {
			Integer by = g.closedCabins.get(id);
			out.add(orderedMap("id", id, "closed", by != null, "closed_by_me", by != null && by == viewerIndex));
		}
		return out;
	}

	private static List<Object> needs(List<DoorHint.Need> needs) {
		List<Object> out = new ArrayList<>();
		for (DoorHint.Need n : needs) {
			out.add(orderedMap("color", n.color(), "status", n.status()));
		}
		return out;
	}

	private static Long remaining(Game g, Instant now) {
		if (g.paused) {
			return g.remainingMs;
		}
		if (g.deadline == null) {
			return null;
		}
		return Math.max(0, g.deadline.toEpochMilli() - now.toEpochMilli());
	}

	/** The groups of the current round, once its split is known: who goes behind each door and who faces whom. */
	private static List<Object> groups(RoundState r) {
		Split s = r.split();
		List<Object> out = new ArrayList<>();
		for (int k = 0; k < Door.ALL.size(); k++) {
			DoorGroup g = s.group(Door.ALL.get(k));
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("door", k);
			m.put("pair_color", g.pairColor().name());
			m.put("solo_color", g.soloColor().name());
			m.put("pair", new ArrayList<>(g.pair()));
			m.put("solo", g.solo());
			m.put("room_solved", r.roomSolved[k]);
			out.add(m);
		}
		return out;
	}

	/** What the viewer is: his kind and, if he plays, his side of the current round and his own vote. */
	private static Map<String, Object> viewer(Game g, RoundState r, Kind kind, UUID uuid) {
		int index = kind == Kind.PLAYER || kind == Kind.GHOST ? g.indexOf(uuid) : -1;
		Integer group = null;
		String side = null;
		Integer passed = null;
		boolean sideVoted = false;
		String ownVote = null;
		String myCabin = index >= 0 ? g.myCabins.get(index) : null;
		if (index >= 0 && r != null) {
			passed = r.passedDoor[index] >= 0 ? r.passedDoor[index] : null;
			if (r.splitIndex != null) {
				int k = r.groupOf(index);
				group = k;
				boolean pair = r.split().group(Door.ALL.get(k)).pair().contains(index);
				side = pair ? "PAIR" : "SOLO";
				Vote cast = pair ? r.pairVote[k] : r.soloVote[k];
				sideVoted = cast != null;
				// Own vote: the one he cast himself. For a pair, the member who did not vote is not told which way
				// his partner went (the vote engaged both of them).
				boolean mine = !pair || r.pairVoter[k] == index;
				ownVote = cast != null && mine ? cast.name() : null;
			}
		}
		// The final door's rule (rules.Outcome) decides, so no client knows the threshold.
		boolean canOpenFinal = index >= 0 && Outcome.canOpenFinalDoor(g.bracelets.size() > index ? g.bracelets.get(index).points : 0,
			g.players.get(index).alive) && kind == Kind.PLAYER;
		return orderedMap("kind", kind.name(), "index", index >= 0 ? index : null, "group", group, "side", side,
			"door_passed", passed, "side_voted", sideVoted, "own_vote", ownVote, "my_cabin", myCabin, "can_open_final_door", canOpenFinal);
	}

	/**
	 * The latest round whose votes are resolved (RESULTS reached): votes of every group, point changes and the newly
	 * dead. Null before the first results. Nothing of a round still being played is ever in it.
	 */
	private static Map<String, Object> results(Game g) {
		RoundState r = null;
		for (int i = g.rounds.size() - 1; i >= 0; i--) {
			if (g.rounds.get(i).deltas != null) {
				r = g.rounds.get(i);
				break;
			}
		}
		if (r == null) {
			return null;
		}
		Split s = r.split();
		List<Object> votes = new ArrayList<>();
		for (int k = 0; k < Door.ALL.size(); k++) {
			DoorGroup dg = s.group(Door.ALL.get(k));
			votes.add(orderedMap("door", k, "pair_color", dg.pairColor().name(), "solo_color", dg.soloColor().name(),
				"pair", new ArrayList<>(dg.pair()), "solo", dg.solo(),
				"pair_vote", effective(r.pairVote[k]), "pair_cast", r.pairVote[k] != null,
				"pair_voter", r.pairVoter[k] >= 0 ? r.pairVoter[k] : null,
				"solo_vote", effective(r.soloVote[k]), "solo_cast", r.soloVote[k] != null));
		}
		return orderedMap("round", r.number, "final_loop", r.finalLoop, "votes", votes, "deltas", ints(r.deltas),
			"newly_dead", new ArrayList<>(r.newlyDead));
	}

	/** Everything the game master adds: the votes cast so far in the current round, the settings, the seed. */
	private static Map<String, Object> gmBlock(Game g, RoundState r) {
		List<Object> votes = new ArrayList<>();
		if (r != null && r.splitIndex != null) {
			for (int k = 0; k < Door.ALL.size(); k++) {
				votes.add(orderedMap("door", k, "pair_vote", name(r.pairVote[k]),
					"pair_voter", r.pairVoter[k] >= 0 ? r.pairVoter[k] : null, "solo_vote", name(r.soloVote[k])));
			}
		}
		Map<String, Object> durations = new LinkedHashMap<>();
		for (Phase p : Phase.values()) {
			if (p.timed()) {
				durations.put(p.name(), g.settings.durationSeconds(p));
			}
		}
		List<Object> masters = new ArrayList<>();
		g.gameMasters.forEach(u -> masters.add(u.toString()));
		List<Object> zones = new ArrayList<>();
		for (int i = 0; i < g.players.size(); i++) {
			PlayerState p = g.players.get(i);
			zones.add(orderedMap("index", i, "zone", p.zone, "x", p.x, "y", p.y, "z", p.z));
		}
		return orderedMap("votes", votes, "zones", zones,
			"settings", orderedMap("mode", g.settings.mode.name(), "durations", durations,
				"no_door_penalty", g.settings.noDoorPenalty, "door_open_seconds", g.settings.doorOpenSeconds,
				"cabins_required", g.settings.cabinsRequired),
			"bot_policy", g.botPolicy.name(), "seed", g.seed, "game_masters", masters);
	}

	private static String name(Vote v) {
		return v == null ? null : v.name();
	}

	private static String effective(Vote v) {
		return (v == null ? Vote.ALLY : v).name();
	}

	private static List<Object> ints(int[] a) {
		List<Object> out = new ArrayList<>();
		for (int x : a) {
			out.add(x);
		}
		return out;
	}

	static Map<String, Object> orderedMap(Object... kv) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], kv[i + 1]);
		}
		return m;
	}
}
