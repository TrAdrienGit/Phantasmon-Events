package com.mystaria.events.trahison.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mystaria.events.trahison.ui.TrahisonUi.GameColor;

/**
 * The latest {@code EventsTrahisonView} the backend sent us (Documentation/trahison/protocole.md), parsed into small
 * records, plus what only the client can know: the clock offset to the server (for countdowns) and when each bracelet's
 * points last changed (for the HUD's flash). The backend sends a complete view on every change, so {@link #update}
 * replaces everything. No game rule lives here: this only holds and exposes what the view says. Client thread.
 */
public final class GameView {

	public enum Phase {
		NONE, LOBBY, PROLOGUE, DOORS, ROOMS, FREE_TIME, VOTE, RESULTS, FINAL, ENDED;

		static Phase of(String name) {
			try {
				return name == null ? NONE : valueOf(name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return NONE;
			}
		}

		/** Lower-case name, the suffix of the lang keys. */
		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public enum ViewerKind { PLAYER, GHOST, SPECTATOR, GM }

	public enum Vote {
		ALLY, BETRAY;

		static Vote of(Object value) {
			return value == null ? null : "BETRAY".equalsIgnoreCase(value.toString()) ? BETRAY : "ALLY".equalsIgnoreCase(value.toString()) ? ALLY : null;
		}
	}

	public record Player(int index, String name, UUID uuid, boolean connected, boolean alive, boolean bot) {
	}

	/** {@code color} and {@code pair} are null before the first round. */
	public record Bracelet(int index, int points, GameColor color, Boolean pair, Integer holder, boolean ownerAlive) {
	}

	/**
	 * A body the backend told this viewer about (T6.1): the dead player's seat, where he fell (hub coordinates; {@code zone}
	 * may be empty) and whether his bracelet still lies on it.
	 */
	public record Body(int player, String zone, double x, double y, double z, boolean braceletOnBody) {
	}

	/** A living player whose connection dropped (T6.4): where he stood, hub coordinates. */
	public record Away(int player, double x, double y, double z) {
	}

	public record Door(int index, List<GameColor> colors) {
	}

	public record Group(int door, GameColor pairColor, GameColor soloColor, List<Integer> pair, int solo, boolean roomSolved) {
	}

	/** {@code canOpenFinalDoor}: the backend's answer (rules.Outcome) whether this viewer's bracelet opens the final door. */
	public record Viewer(ViewerKind kind, Integer index, Integer group, Boolean pairSide, Integer doorPassed, boolean sideVoted, Vote ownVote,
			boolean canOpenFinalDoor, boolean canRoam, boolean isGameMaster) {
	}

	public record VoteResult(int door, GameColor pairColor, GameColor soloColor, List<Integer> pair, int solo, Vote pairVote,
			boolean pairCast, Integer pairVoter, Vote soloVote, boolean soloCast) {
	}

	public record Results(int round, boolean finalLoop, List<VoteResult> votes, List<Integer> deltas, List<Integer> newlyDead) {
	}

	public record GmVote(int door, Vote pairVote, Integer pairVoter, Vote soloVote) {
	}

	/** Where a player is, as his client last said (zone id or null, hub coordinates), for the game master. */
	public record GmZone(int index, String zone, Double x, Double y, Double z) {
	}

	/** One room of the draw (T4.1): its round (1 to 3) and group (0 to 2, the door and place), its place and the turn applied there (null without a plan). */
	public record DrawPick(int round, int group, String room, String name, int difficulty, int expectedSeconds, String mechanism, boolean manual,
			String place, String orientation) {
	}

	/** The game's nine drawn rooms and the constraints the draw had to relax ({@code rise variety recent spread repeat}). */
	public record Draw(long seed, List<DrawPick> picks, List<String> relaxed) {

		/** Total difficulty of a round (1 to 3). */
		public int total(int round) {
			int sum = 0;
			for (DrawPick p : picks) {
				sum += p.round() == round ? p.difficulty() : 0;
			}
			return sum;
		}
	}

	public record Gm(List<GmVote> votes, String mode, Map<String, Integer> durations, int cabinPartnerSeconds, String botPolicy, long seed,
			List<GmZone> zones, Draw draw) {
	}

	/** An account around the lobby (or a game master of the game): {@code gameMaster} is whether the admins gave it the role (D7). */
	public record Member(UUID uuid, String name, boolean gameMaster) {
	}

	/**
	 * The game masters of the game (public: they may be none) and, in the lobby, the accounts an admin may give the role to
	 * ({@code members}: the people around who are not in the game, game masters included). Kept beside the view, as {@link Cabins}.
	 */
	public record Lobby(List<Member> gameMasters, List<Member> members) {

		public static final Lobby NONE = new Lobby(List.of(), List.of());

		public boolean isGameMaster(UUID account) {
			return gameMasters.stream().anyMatch(m -> m.uuid().equals(account));
		}
	}

	/** One bracelet kind a door's threshold lacks: a game colour, as a pair or as a solo. */
	public record Need(GameColor color, boolean pair) {
	}

	/**
	 * What a colour door of the plan says about its threshold during DOORS (the backend's {@code door_hints}): {@code present}
	 * players stand there, {@code openUntil} (server epoch ms) is set while the door is open, {@code missing} is what
	 * lacks to open it and {@code altMissing} another way of the same length (null if none).
	 */
	public record DoorHint(String door, int index, List<GameColor> colors, int present, Long openUntil, boolean ready,
			boolean incompatible, List<Need> missing, List<Need> altMissing) {
	}

	/**
	 * {@code layoutVersion}: the version of the plan the game uses (0: none). {@code doorsOpen}: door id of the plan to
	 * whether it is open for this viewer (the backend's {@code doors_state}). {@code doorHints}: the colour doors'
	 * thresholds, empty outside DOORS.
	 */
	public record View(String hub, String gameId, int layoutVersion, Map<String, Boolean> doorsOpen, List<DoorHint> doorHints, Phase phase, int round, boolean finalLoop, int finalLoops, String mode, Long deadline,
			boolean paused, boolean autoPaused, Long remainingMs, long serverTime, List<Player> players, List<Bracelet> bracelets,
			List<Door> doors, List<Group> groups, List<Integer> doorsPassed, Viewer viewer, Results results, List<Integer> winners, Gm gm) {

		public boolean hasGame() {
			return phase != Phase.NONE;
		}

		public Player player(int index) {
			return index >= 0 && index < players.size() && players.get(index).index() == index ? players.get(index) : players.stream().filter(p -> p.index() == index).findFirst().orElse(null);
		}

		public Bracelet bracelet(int index) {
			return bracelets.stream().filter(b -> b.index() == index).findFirst().orElse(null);
		}

		/** The viewer's own bracelet (the one on their wrist), or null. */
		public Bracelet ownBracelet() {
			return viewer.index() == null ? null : bracelet(viewer.index());
		}

		/** The group of the door the viewer passed, or null. */
		public Group ownGroup() {
			return viewer.group() == null || groups == null ? null : groups.stream().filter(g -> g.door() == viewer.group()).findFirst().orElse(null);
		}

		public String nameOf(int index) {
			Player p = player(index);
			return p == null ? "?" : p.name();
		}
	}

	/**
	 * One cabin of the plan as the backend's {@code cabins} describes it (D4, D5): {@code state} is {@code FREE},
	 * {@code WAITING} (a member of its group is in and waits for the partner until {@code windowUntil}, server epoch ms),
	 * {@code LOCKED} (the group is in, nobody leaves before it has voted) or {@code RELEASED} (it voted and walks out).
	 */
	public record Cabin(String id, String state, Long windowUntil) {

		/** Its door is shut for those who are not of the group. */
		public boolean closed() {
			return !"FREE".equals(state);
		}

		public boolean waiting() {
			return "WAITING".equals(state);
		}

		/** The group is in and the pupitre works: locked, or released after the vote. */
		public boolean locked() {
			return "LOCKED".equals(state) || "RELEASED".equals(state);
		}
	}

	/**
	 * What T2.4 adds to the view, kept beside it (not inside {@link View}, so the other fields' constructor stays as it is):
	 * {@code required}: votes are cast from a closed cabin; {@code cabins}: every cabin of the plan; {@code myCabin}: the id of
	 * the cabin this viewer closes and votes in (null for a spectator, or before the groups are known).
	 */
	public record Cabins(boolean required, List<Cabin> cabins, String myCabin) {

		public static final Cabins NONE = new Cabins(false, List.of(), null);

		public Cabin cabin(String id) {
			return id == null ? null : cabins.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
		}

		/** The viewer's own cabin, or null. */
		public Cabin mine() {
			return cabin(myCabin);
		}
	}

	/**
	 * The final door while it is open (T2.5, the backend's {@code final_door}), kept beside the view like {@link Cabins}:
	 * {@code openUntil} is the server epoch ms at which it closes for ever, {@code openers} how many bracelets it lets through.
	 * Whether it is open <em>for this viewer</em> is the view's {@code doorsOpen}.
	 */
	public record FinalDoor(long openUntil, int openers) {
	}

	private static final View EMPTY = new View("", "", 0, Map.of(), List.of(), Phase.NONE, 0, false, 0, "AUTO", null, false, false, null, 0, List.of(), List.of(),
			List.of(), null, null, new Viewer(ViewerKind.SPECTATOR, null, null, null, null, false, null, false, false, false), null, null, null);

	private static View current;
	private static Cabins cabinsState = Cabins.NONE;
	private static FinalDoor finalDoorState;
	private static Lobby lobbyState = Lobby.NONE;
	private static List<Body> bodiesState = List.of();
	private static List<Integer> carriedState = List.of();
	private static List<Away> awayState = List.of();
	private static String ghostDisplayState = "SILHOUETTE";
	/** {@code server time - local time} when the last view arrived. */
	private static long clockOffset;
	private static long phaseTotalMs;
	private static String phaseKey = "";
	private static long[] pointsChangedAt = new long[9];
	private static int[] pointsDelta = new int[9];
	private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

	private GameView() {
	}

	// ---------------------------------------------------------------- access

	/** The latest view, or null before any (or outside a hub). A hub without a game gives a view whose phase is NONE. */
	public static View current() {
		return current;
	}

	/** The latest view, or an empty NONE view: never null. */
	public static View orEmpty() {
		return current == null ? EMPTY : current;
	}

	/** The bodies the backend lets this viewer know of (T6.1); empty when he knows none. */
	public static List<Body> bodies() {
		return bodiesState;
	}

	/** T6.3: how the living see a ghost, {@code SILHOUETTE} (black and white) or {@code HIDDEN}; the game master's setting. */
	public static boolean ghostsHidden() {
		return "HIDDEN".equals(ghostDisplayState);
	}

	/** T6.3: the accounts this viewer knows to be dead (the view already hides the deaths he must not know yet). */
	public static java.util.Set<UUID> ghosts() {
		View v = current;
		java.util.Set<UUID> out = new java.util.HashSet<>();
		if (v != null && v.hasGame()) {
			for (Player p : v.players()) {
				if (!p.alive() && p.uuid() != null) {
					out.add(p.uuid());
				}
			}
		}
		return out;
	}

	/** The players whose connection dropped (T6.4), each to be shown as a mannequin where he stood. */
	public static List<Away> away() {
		return awayState;
	}

	/** The seats of the bracelets this viewer picked up from bodies (T6.1), in the order he took them. */
	public static List<Integer> carried() {
		return carriedState;
	}

	/** The game masters and the lobby's candidates of the latest view (D7); {@link Lobby#NONE} without a game. */
	public static Lobby lobby() {
		return lobbyState;
	}

	/** The cabins of the latest view (T2.4); {@link Cabins#NONE} without a game. */
	public static Cabins cabins() {
		return cabinsState;
	}

	/** The final door's window (T2.5) while it is open, else null. */
	public static FinalDoor finalDoor() {
		return finalDoorState;
	}

	/** Seconds the final door stays open, rounded up; 0 when it is shut. */
	public static int finalDoorSeconds() {
		return finalDoorState == null ? 0 : (int) Math.max(0, (finalDoorState.openUntil() - serverNow() + 999) / 1000);
	}

	public static boolean hasGame() {
		return current != null && current.hasGame();
	}

	public static void addListener(Runnable listener) {
		LISTENERS.add(listener);
	}

	/** The server's clock now, in epoch ms, from the offset measured at the last view. */
	public static long serverNow() {
		return System.currentTimeMillis() + clockOffset;
	}

	/** Time left in the current step, or -1 when there is none (lobby, ended). Frozen while paused. */
	public static long remainingMs() {
		View v = current;
		if (v == null) {
			return -1;
		}
		if (v.paused() || v.deadline() == null) {
			return v.remainingMs() == null ? -1 : v.remainingMs();
		}
		return Math.max(0, v.deadline() - serverNow());
	}

	/** Remaining time over the longest remaining time seen in this step (the step's length, to the client's knowledge). */
	public static float fraction() {
		long remaining = remainingMs();
		return remaining < 0 || phaseTotalMs <= 0 ? 0f : Math.min(1f, remaining / (float) phaseTotalMs);
	}

	/** When bracelet {@code index}'s points last changed ({@code System.currentTimeMillis()}), or 0. */
	public static long pointsChangedAt(int index) {
		return index >= 0 && index < pointsChangedAt.length ? pointsChangedAt[index] : 0;
	}

	/** The last change of bracelet {@code index}'s points. */
	public static int lastDelta(int index) {
		return index >= 0 && index < pointsDelta.length ? pointsDelta[index] : 0;
	}

	// ---------------------------------------------------------------- updates

	/** Forgets the view (left the hub, disconnected). */
	public static void clear() {
		current = null;
		cabinsState = Cabins.NONE;
		finalDoorState = null;
		lobbyState = Lobby.NONE;
		bodiesState = List.of();
		carriedState = List.of();
		awayState = List.of();
		ghostDisplayState = "SILHOUETTE";
		phaseKey = "";
		phaseTotalMs = 0;
		pointsChangedAt = new long[9];
		pointsDelta = new int[9];
		fire();
	}

	/** Replaces the state by a received {@code EventsTrahisonView}. */
	public static void update(Map<String, Object> data) {
		View next;
		try {
			next = parse(data);
		} catch (RuntimeException e) {
			return; // A malformed view is ignored: the next one replaces it anyway.
		}
		View previous = current;
		long now = System.currentTimeMillis();
		clockOffset = next.serverTime() > 0 ? next.serverTime() - now : clockOffset;
		current = next;
		cabinsState = next.hasGame() ? parseCabins(data) : Cabins.NONE;
		finalDoorState = next.hasGame() && data.get("final_door") instanceof Map<?, ?> fd && fd.get("open_until") instanceof Number until
				? new FinalDoor(until.longValue(), fd.get("openers") instanceof Number n ? n.intValue() : 0) : null;
		lobbyState = next.hasGame() ? parseLobby(data) : Lobby.NONE;
		bodiesState = next.hasGame() ? parseBodies(data) : List.of();
		awayState = next.hasGame() ? parseAway(data) : List.of();
		ghostDisplayState = "HIDDEN".equals(str(data, "ghost_display")) ? "HIDDEN" : "SILHOUETTE";
		carriedState = next.hasGame() ? ints(map(data, "viewer"), "carried") : List.of();
		String key = next.gameId() + "/" + next.phase() + "/" + next.round() + "/" + next.finalLoop() + "/" + next.finalLoops();
		long remaining = next.remainingMs() == null ? -1 : next.remainingMs();
		if (!key.equals(phaseKey)) {
			phaseKey = key;
			phaseTotalMs = Math.max(0, remaining);
		} else {
			phaseTotalMs = Math.max(phaseTotalMs, remaining);
		}
		if (previous != null && previous.gameId().equals(next.gameId())) {
			for (Bracelet b : next.bracelets()) {
				Bracelet old = previous.bracelet(b.index());
				if (old != null && old.points() != b.points() && b.index() < pointsChangedAt.length) {
					pointsChangedAt[b.index()] = now;
					pointsDelta[b.index()] = b.points() - old.points();
				}
			}
		} else {
			pointsChangedAt = new long[9];
			pointsDelta = new int[9];
		}
		fire();
	}

	private static void fire() {
		LISTENERS.forEach(Runnable::run);
	}

	// ---------------------------------------------------------------- parsing

	private static View parse(Map<String, Object> d) {
		Phase phase = Phase.of(str(d, "phase"));
		if (phase == Phase.NONE) {
			return new View(str(d, "hub") == null ? "" : str(d, "hub"), "", 0, Map.of(), List.of(), Phase.NONE, 0, false, 0, "AUTO", null, false, false, null,
					lng(d, "server_time", 0), List.of(), List.of(), List.of(), null, null, EMPTY.viewer(), null, null, null);
		}
		List<Player> players = new ArrayList<>();
		for (Map<String, Object> p : maps(d, "players")) {
			players.add(new Player(integer(p, "index", players.size()), str(p, "name") == null ? "?" : str(p, "name"), uuid(str(p, "uuid")),
					bool(p, "connected"), bool(p, "alive"), bool(p, "is_bot")));
		}
		List<Bracelet> bracelets = new ArrayList<>();
		for (Map<String, Object> b : maps(d, "bracelets")) {
			String status = str(b, "status");
			bracelets.add(new Bracelet(integer(b, "index", bracelets.size()), integer(b, "points", 0), color(str(b, "color")),
					status == null ? null : "PAIR".equalsIgnoreCase(status), nullableInt(b, "holder"), bool(b, "owner_alive")));
		}
		List<Door> doors = new ArrayList<>();
		for (Map<String, Object> door : maps(d, "doors")) {
			List<GameColor> colors = new ArrayList<>();
			if (door.get("colors") instanceof List<?> list) {
				list.forEach(c -> {
					GameColor color = color(String.valueOf(c));
					if (color != null) {
						colors.add(color);
					}
				});
			}
			doors.add(new Door(integer(door, "index", doors.size()), colors));
		}
		List<Group> groups = null;
		if (d.get("groups") instanceof List<?> raw) {
			groups = new ArrayList<>();
			for (Object o : raw) {
				if (o instanceof Map<?, ?> m) {
					@SuppressWarnings("unchecked")
					Map<String, Object> g = (Map<String, Object>) m;
					groups.add(new Group(integer(g, "door", 0), color(str(g, "pair_color")), color(str(g, "solo_color")), ints(g, "pair"),
							integer(g, "solo", -1), bool(g, "room_solved")));
				}
			}
		}
		List<Integer> passed = d.get("doors_passed") instanceof List<?> ? ints(d, "doors_passed") : null;
		Map<String, Object> v = map(d, "viewer");
		ViewerKind kind;
		try {
			kind = ViewerKind.valueOf(String.valueOf(v.get("kind")).toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			kind = ViewerKind.SPECTATOR;
		}
		String side = str(v, "side");
		Viewer viewer = new Viewer(kind, nullableInt(v, "index"), nullableInt(v, "group"), side == null ? null : "PAIR".equalsIgnoreCase(side),
				nullableInt(v, "door_passed"), bool(v, "side_voted"), Vote.of(v.get("own_vote")), bool(v, "can_open_final_door"),
				bool(v, "can_roam"), bool(v, "is_game_master"));
		Results results = null;
		if (d.get("results") instanceof Map<?, ?> rm) {
			@SuppressWarnings("unchecked")
			Map<String, Object> r = (Map<String, Object>) rm;
			List<VoteResult> votes = new ArrayList<>();
			for (Map<String, Object> vr : maps(r, "votes")) {
				votes.add(new VoteResult(integer(vr, "door", 0), color(str(vr, "pair_color")), color(str(vr, "solo_color")), ints(vr, "pair"),
						integer(vr, "solo", -1), vote(vr, "pair_vote"), bool(vr, "pair_cast"), nullableInt(vr, "pair_voter"), vote(vr, "solo_vote"),
						bool(vr, "solo_cast")));
			}
			results = new Results(integer(r, "round", 0), bool(r, "final_loop"), votes, ints(r, "deltas"), ints(r, "newly_dead"));
		}
		List<Integer> winners = d.get("winners") instanceof List<?> ? ints(d, "winners") : null;
		Gm gm = null;
		if (d.get("gm") instanceof Map<?, ?> gmRaw) {
			@SuppressWarnings("unchecked")
			Map<String, Object> g = (Map<String, Object>) gmRaw;
			List<GmVote> votes = new ArrayList<>();
			for (Map<String, Object> gv : maps(g, "votes")) {
				votes.add(new GmVote(integer(gv, "door", 0), vote(gv, "pair_vote"), nullableInt(gv, "pair_voter"), vote(gv, "solo_vote")));
			}
			Map<String, Object> settings = map(g, "settings");
			Map<String, Integer> durations = new java.util.LinkedHashMap<>();
			map(settings, "durations").forEach((k, val) -> durations.put(k, val instanceof Number n ? n.intValue() : 0));
			List<GmZone> zones = new ArrayList<>();
			for (Map<String, Object> gz : maps(g, "zones")) {
				zones.add(new GmZone(integer(gz, "index", zones.size()), str(gz, "zone"),
						gz.get("x") instanceof Number n ? n.doubleValue() : null, gz.get("y") instanceof Number n ? n.doubleValue() : null,
						gz.get("z") instanceof Number n ? n.doubleValue() : null));
			}
			gm = new Gm(votes, str(settings, "mode") == null ? "AUTO" : str(settings, "mode"), durations, integer(settings, "cabin_partner_seconds", 30),
					str(g, "bot_policy") == null ? "RANDOM" : str(g, "bot_policy"), lng(g, "seed", 0), Collections.unmodifiableList(zones), draw(g));
		}
		Map<String, Boolean> doorsOpen = new java.util.LinkedHashMap<>();
		map(d, "doors_state").forEach((id, state) -> doorsOpen.put(id, "OPEN".equalsIgnoreCase(String.valueOf(state))));
		return new View(str(d, "hub") == null ? "" : str(d, "hub"), str(d, "game_id") == null ? "" : str(d, "game_id"),
				integer(d, "layout_version", 0), Collections.unmodifiableMap(doorsOpen), doorHints(d), phase, integer(d, "round", 0),
				bool(d, "final_loop"), integer(d, "final_loops", 0), str(d, "mode") == null ? "AUTO" : str(d, "mode"),
				d.get("deadline") instanceof Number n ? n.longValue() : null, bool(d, "paused"), bool(d, "auto_paused"),
				d.get("remaining_ms") instanceof Number n2 ? n2.longValue() : null, lng(d, "server_time", 0), Collections.unmodifiableList(players),
				Collections.unmodifiableList(bracelets), Collections.unmodifiableList(doors), groups, passed, viewer, results, winners, gm);
	}

	private static List<Away> parseAway(Map<String, Object> d) {
		List<Away> out = new ArrayList<>();
		for (Map<String, Object> a : maps(d, "disconnected")) {
			if (a.get("x") instanceof Number x && a.get("z") instanceof Number z) {
				out.add(new Away(integer(a, "player", -1), x.doubleValue(), a.get("y") instanceof Number y ? y.doubleValue() : 0, z.doubleValue()));
			}
		}
		return Collections.unmodifiableList(out);
	}

	private static List<Body> parseBodies(Map<String, Object> d) {
		List<Body> out = new ArrayList<>();
		for (Map<String, Object> b : maps(d, "bodies")) {
			if (b.get("x") instanceof Number x && b.get("z") instanceof Number z) {
				out.add(new Body(integer(b, "player", -1), str(b, "zone") == null ? "" : str(b, "zone"), x.doubleValue(),
						b.get("y") instanceof Number y ? y.doubleValue() : 0, z.doubleValue(), bool(b, "bracelet_on_body")));
			}
		}
		return Collections.unmodifiableList(out);
	}

	private static Cabins parseCabins(Map<String, Object> d) {
		List<Cabin> list = new ArrayList<>();
		for (Map<String, Object> c : maps(d, "cabins")) {
			String id = str(c, "id");
			if (id != null) {
				list.add(new Cabin(id, str(c, "state") == null ? "FREE" : str(c, "state"), c.get("window_until") instanceof Number n ? n.longValue() : null));
			}
		}
		return new Cabins(bool(d, "cabins_required"), Collections.unmodifiableList(list), str(map(d, "viewer"), "my_cabin"));
	}

	private static Lobby parseLobby(Map<String, Object> d) {
		List<Member> masters = new ArrayList<>();
		for (Map<String, Object> m : maps(d, "game_masters")) {
			masters.add(new Member(uuid(str(m, "uuid")), str(m, "name") == null ? "?" : str(m, "name"), true));
		}
		List<Member> members = new ArrayList<>();
		for (Map<String, Object> m : maps(d, "lobby_members")) {
			members.add(new Member(uuid(str(m, "uuid")), str(m, "name") == null ? "?" : str(m, "name"), bool(m, "game_master")));
		}
		return new Lobby(Collections.unmodifiableList(masters), Collections.unmodifiableList(members));
	}

	private static List<DoorHint> doorHints(Map<String, Object> d) {
		List<DoorHint> out = new ArrayList<>();
		for (Map<String, Object> h : maps(d, "door_hints")) {
			List<GameColor> colors = new ArrayList<>();
			if (h.get("colors") instanceof List<?> list) {
				list.forEach(c -> {
					GameColor color = color(String.valueOf(c));
					if (color != null) {
						colors.add(color);
					}
				});
			}
			String id = str(h, "door");
			if (id != null) {
				out.add(new DoorHint(id, integer(h, "index", -1), colors, integer(h, "present", 0), h.get("open_until") instanceof Number n ? n.longValue() : null,
						bool(h, "ready"), bool(h, "incompatible"), needs(h.get("missing")), h.get("alt_missing") == null ? null : needs(h.get("alt_missing"))));
			}
		}
		return Collections.unmodifiableList(out);
	}

	private static List<Need> needs(Object raw) {
		List<Need> out = new ArrayList<>();
		if (raw instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof Map<?, ?> m) {
					GameColor color = color(String.valueOf(m.get("color")));
					if (color != null) {
						out.add(new Need(color, "PAIR".equalsIgnoreCase(String.valueOf(m.get("status")))));
					}
				}
			}
		}
		return List.copyOf(out);
	}

	// ---------------------------------------------------------------- small typed accessors

	/** The draw block of the game master view, or null. */
	private static Draw draw(Map<String, Object> g) {
		if (!(g.get("draw") instanceof Map<?, ?> raw)) {
			return null;
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> d = (Map<String, Object>) raw;
		List<DrawPick> picks = new ArrayList<>();
		for (Map<String, Object> p : maps(d, "picks")) {
			picks.add(new DrawPick(integer(p, "round", 1), integer(p, "group", 0), str(p, "room") == null ? "" : str(p, "room"),
					str(p, "name") == null ? "" : str(p, "name"), integer(p, "difficulty", 0), integer(p, "expected_seconds", 0),
					str(p, "mechanism") == null ? "" : str(p, "mechanism"), bool(p, "manual"), str(p, "place"), str(p, "orientation")));
		}
		List<String> relaxed = new ArrayList<>();
		if (d.get("relaxed") instanceof List<?> l) {
			l.forEach(x -> relaxed.add(String.valueOf(x)));
		}
		return new Draw(lng(d, "seed", 0), Collections.unmodifiableList(picks), Collections.unmodifiableList(relaxed));
	}

	private static String str(Map<String, Object> m, String k) {
		Object o = m.get(k);
		return o == null ? null : o.toString();
	}

	private static int integer(Map<String, Object> m, String k, int def) {
		return m.get(k) instanceof Number n ? n.intValue() : def;
	}

	private static Integer nullableInt(Map<String, Object> m, String k) {
		return m.get(k) instanceof Number n ? Integer.valueOf(n.intValue()) : null;
	}

	private static long lng(Map<String, Object> m, String k, long def) {
		return m.get(k) instanceof Number n ? n.longValue() : def;
	}

	private static boolean bool(Map<String, Object> m, String k) {
		return Boolean.TRUE.equals(m.get(k));
	}

	private static Vote vote(Map<String, Object> m, String k) {
		return Vote.of(m.get(k));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Map<String, Object> m, String k) {
		return m.get(k) instanceof Map<?, ?> r ? (Map<String, Object>) r : Map.of();
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> maps(Map<String, Object> m, String k) {
		List<Map<String, Object>> out = new ArrayList<>();
		if (m.get(k) instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof Map<?, ?> e) {
					out.add((Map<String, Object>) e);
				}
			}
		}
		return out;
	}

	private static List<Integer> ints(Map<String, Object> m, String k) {
		List<Integer> out = new ArrayList<>();
		if (m.get(k) instanceof List<?> list) {
			for (Object o : list) {
				out.add(o instanceof Number n ? n.intValue() : -1);
			}
		}
		return out;
	}

	private static UUID uuid(String s) {
		try {
			return s == null ? null : UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static GameColor color(String s) {
		try {
			return s == null ? null : GameColor.valueOf(s.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
