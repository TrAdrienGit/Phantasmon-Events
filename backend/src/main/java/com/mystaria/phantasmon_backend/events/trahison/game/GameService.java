package com.mystaria.phantasmon_backend.events.trahison.game;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mystaria.phantasmon_backend.events.trahison.room.ActionResult;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomAction;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomException;
import com.mystaria.phantasmon_backend.events.trahison.rules.Assignment;
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.DoorGroup;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Outcome;
import com.mystaria.phantasmon_backend.events.trahison.rules.Placement;
import com.mystaria.phantasmon_backend.events.trahison.rules.Scoring;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;
import com.mystaria.phantasmon_backend.events.trahison.rules.Vote;

/**
 * The state machine of the betrayal game: one game per hub, every operation synchronized on the game, time from an
 * injected {@link Clock} and {@link GameScheduler}. Rules (colours, doors, points, deaths) are all delegated to the
 * {@code rules} package. A refused operation throws {@link TrahisonException}.
 *
 * <pre>
 * LOBBY -> PROLOGUE -> DOORS -> ROOMS -> FREE_TIME -> VOTE -> RESULTS -> DOORS (rounds 1, 2) | FINAL (after round 3)
 * FINAL -> VOTE -> RESULTS -> FINAL ... until the final door opens or the game master ends it -> ENDED
 * </pre>
 *
 * Every mutating operation writes the game to the {@link GameStore} before returning. Games returned or passed to
 * listeners are the live objects: read them under {@code synchronized (game)} (see {@link #read}).
 */
public class GameService {

	private static final Logger log = LoggerFactory.getLogger(GameService.class);
	public static final int PLAYERS = 9;
	/** Longest automatic pause after a disconnection (AUTO mode). */
	public static final Duration AUTO_PAUSE_MAX = Duration.ofMinutes(5);
	private static final long GOLDEN = 0x9E3779B97F4A7C15L;
	private static final long SPLIT_SALT = 0x5DEECE66DL;

	private final GameStore store;
	private final Clock clock;
	private final GameScheduler scheduler;
	private final Map<String, Game> byHub = new ConcurrentHashMap<>();
	private final Map<UUID, Game> byId = new ConcurrentHashMap<>();
	private final Map<UUID, GameScheduler.Handle> timers = new ConcurrentHashMap<>();
	private final Map<UUID, GameScheduler.Handle> autoTimers = new ConcurrentHashMap<>();
	private final List<GameListener> listeners = new CopyOnWriteArrayList<>();
	/** The room engine link; null in tests that play the game alone (then {@code debug_solve} is the only way). */
	private volatile GameRooms rooms;

	public GameService(GameStore store, Clock clock, GameScheduler scheduler) {
		this.store = store;
		this.clock = clock;
		this.scheduler = scheduler;
	}

	public void addListener(GameListener listener) {
		listeners.add(listener);
	}

	/** Plugs the room engine in (before games are reloaded). */
	public void useRooms(GameRooms gameRooms) {
		this.rooms = gameRooms;
	}

	// ------------------------------------------------------------------ lookup

	/** The current game of a hub (possibly ended), or null. */
	public Game find(String hub) {
		return byHub.get(hub);
	}

	/** Every game known (current game of each hub), for lookups by player. */
	public List<Game> games() {
		return List.copyOf(byHub.values());
	}

	/** The unfinished game this account plays in, or null. */
	public Game findByPlayer(UUID uuid) {
		for (Game g : byHub.values()) {
			synchronized (g) {
				if (g.phase != Phase.ENDED && g.indexOf(uuid) >= 0) {
					return g;
				}
			}
		}
		return null;
	}

	/** Runs {@code reader} on the hub's game while it is locked. */
	public <T> T read(String hub, Function<Game, T> reader) {
		Game g = require(hub);
		synchronized (g) {
			return reader.apply(g);
		}
	}

	private Game require(String hub) {
		Game g = byHub.get(hub);
		if (g == null) {
			throw new TrahisonException(TrahisonException.NO_GAME, "no game in hub " + hub);
		}
		return g;
	}

	// ------------------------------------------------------------------ lobby

	/** Creates the hub's game in its lobby. Refused while the hub has a game that has not ended. */
	public Game create(String hub, Settings settings, Long seed) {
		if (hub == null || hub.isBlank()) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "hub required");
		}
		Game g = new Game();
		synchronized (byHub) {
			Game existing = byHub.get(hub);
			if (existing != null && existing.phase != Phase.ENDED) {
				throw new TrahisonException(TrahisonException.GAME_EXISTS, "hub " + hub + " already has a game");
			}
			g.id = UUID.randomUUID();
			g.hub = hub;
			g.seed = seed != null ? seed : new SecureRandom().nextLong();
			g.settings = settings != null ? settings : new Settings();
			g.createdAt = clock.instant();
			byHub.put(hub, g);
			byId.put(g.id, g);
		}
		synchronized (g) {
			event(g, "created", Map.of("seed", g.seed, "mode", g.settings.mode.name()));
			persist(g);
			notifyPhase(g);
		}
		return g;
	}

	public Game join(String hub, UUID uuid, String name) {
		return join(hub, uuid, name, false);
	}

	/** Same, for a bot when {@code bot} is true. */
	public Game join(String hub, UUID uuid, String name, boolean bot) {
		Game g = require(hub);
		synchronized (g) {
			if (g.phase != Phase.LOBBY) {
				throw new TrahisonException(TrahisonException.NOT_IN_LOBBY, "the game already started");
			}
			if (g.indexOf(uuid) >= 0) {
				throw new TrahisonException(TrahisonException.ALREADY_JOINED, "already in the game");
			}
			if (g.players.size() >= PLAYERS) {
				throw new TrahisonException(TrahisonException.GAME_FULL, "nine players already");
			}
			PlayerState added = new PlayerState(uuid, name);
			added.bot = bot;
			g.players.add(added);
			event(g, "join", Map.of("player", uuid.toString(), "name", String.valueOf(name), "bot", bot));
			persist(g);
			notifyChanged(g, "join");
			return g;
		}
	}

	public Game leave(String hub, UUID uuid) {
		Game g = require(hub);
		synchronized (g) {
			if (g.phase != Phase.LOBBY) {
				throw new TrahisonException(TrahisonException.NOT_IN_LOBBY, "the game already started");
			}
			int i = g.indexOf(uuid);
			if (i < 0) {
				throw new TrahisonException(TrahisonException.NOT_A_PLAYER, "not in the game");
			}
			g.players.remove(i);
			event(g, "leave", Map.of("player", uuid.toString()));
			persist(g);
			notifyChanged(g, "leave");
			return g;
		}
	}

	/** Needs exactly nine players. Player {@code i} (join order) gets bracelet {@code i} at 3 points. */
	public Game start(String hub) {
		Game g = require(hub);
		synchronized (g) {
			if (g.phase != Phase.LOBBY) {
				throw new TrahisonException(TrahisonException.NOT_IN_LOBBY, "the game already started");
			}
			if (g.players.size() != PLAYERS) {
				throw new TrahisonException(TrahisonException.NOT_ENOUGH_PLAYERS,
					"need exactly " + PLAYERS + " players, have " + g.players.size());
			}
			g.bracelets = new ArrayList<>();
			for (int i = 0; i < PLAYERS; i++) {
				g.bracelets.add(new BraceletState(i, Outcome.START_POINTS));
			}
			event(g, "start", Map.of());
			enterPhase(g, Phase.PROLOGUE);
			persist(g);
			return g;
		}
	}

	// ------------------------------------------------------------------ game master

	/** Forces the next stage now (also while paused: the new stage then starts paused). */
	public Game next(String hub) {
		Game g = require(hub);
		synchronized (g) {
			requireRunning(g);
			advance(g, "gm");
			persist(g);
			return g;
		}
	}

	public Game pause(String hub) {
		Game g = require(hub);
		synchronized (g) {
			requireRunning(g);
			if (g.paused) {
				if (g.autoPaused) { // the game master takes over an automatic pause: it now lasts until he resumes
					clearAutoPause(g);
					event(g, "pause", Map.of("remainingMs", g.remainingMs, "takeover", true));
					persist(g);
					notifyPhase(g);
					return g;
				}
				throw new TrahisonException(TrahisonException.PAUSED, "already paused");
			}
			doPause(g);
			persist(g);
			notifyPhase(g);
			return g;
		}
	}

	public Game resume(String hub) {
		Game g = require(hub);
		synchronized (g) {
			requireRunning(g);
			if (!g.paused) {
				throw new TrahisonException(TrahisonException.NOT_PAUSED, "not paused");
			}
			clearAutoPause(g);
			doResume(g, "gm");
			persist(g);
			notifyPhase(g);
			return g;
		}
	}

	/** Adds time to the current stage (negative to cut it). */
	public Game extend(String hub, int seconds) {
		Game g = require(hub);
		synchronized (g) {
			requireRunning(g);
			if (g.paused) {
				g.remainingMs = Math.max(0, g.remainingMs + seconds * 1000L);
			} else {
				g.deadline = g.deadline.plusSeconds(seconds);
				scheduleTimer(g);
			}
			event(g, "extend", Map.of("seconds", seconds));
			persist(g);
			notifyPhase(g);
			return g;
		}
	}

	/** Ends the game now (any stage). No winners are recorded. */
	public Game end(String hub) {
		Game g = require(hub);
		synchronized (g) {
			if (g.phase == Phase.ENDED) {
				throw new TrahisonException(TrahisonException.GAME_ENDED, "already ended");
			}
			finish(g, "gm");
			persist(g);
			return g;
		}
	}

	/** The game master corrects a bracelet's points (no death check until the next results). */
	public Game setPoints(String hub, int bracelet, int points) {
		Game g = require(hub);
		synchronized (g) {
			if (g.phase == Phase.LOBBY || g.phase == Phase.ENDED) {
				throw new TrahisonException(TrahisonException.WRONG_PHASE, "no running game");
			}
			if (bracelet < 0 || bracelet >= PLAYERS || points < 0) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "bad bracelet or points");
			}
			g.bracelets.get(bracelet).points = points;
			event(g, "set_points", Map.of("bracelet", bracelet, "points", points));
			persist(g);
			notifyChanged(g, "points");
			return g;
		}
	}

	public Game setConnected(String hub, UUID uuid, boolean connected) {
		Game g = require(hub);
		synchronized (g) {
			int i = g.indexOf(uuid);
			if (i < 0) {
				throw new TrahisonException(TrahisonException.NOT_A_PLAYER, "not in the game");
			}
			PlayerState p = g.players.get(i);
			if (p.connected == connected) {
				return g;
			}
			p.connected = connected;
			event(g, connected ? "connected" : "disconnected", Map.of("player", i));
			boolean pauseChanged = false;
			if (g.settings.mode == Mode.AUTO && g.phase.timed()) {
				if (!connected && p.alive && !g.paused) {
					// A living player dropped: the clock stops for at most AUTO_PAUSE_MAX (plan, section 3).
					doPause(g);
					g.autoPaused = true;
					g.autoPauseUntil = clock.instant().plus(AUTO_PAUSE_MAX);
					scheduleAutoResume(g);
					event(g, "auto_pause", Map.of("player", i, "until", g.autoPauseUntil.toString()));
					pauseChanged = true;
				}
			}
			if (connected && g.autoPaused && g.paused && !anyLivingDisconnected(g)) {
				clearAutoPause(g);
				doResume(g, "reconnected");
				pauseChanged = true;
			}
			persist(g);
			if (pauseChanged) {
				notifyPhase(g);
			} else {
				notifyChanged(g, "connection");
			}
			return g;
		}
	}

	/** Adds an admin to the game masters (they get the game master view). */
	public Game addGameMaster(String hub, UUID uuid) {
		Game g = require(hub);
		synchronized (g) {
			if (g.gameMasters.add(uuid)) {
				event(g, "game_master", Map.of("account", uuid.toString()));
				persist(g);
				notifyChanged(g, "gm");
			}
			return g;
		}
	}

	/** How the bots vote from now on. */
	public Game setBotPolicy(String hub, BotPolicy policy) {
		Game g = require(hub);
		synchronized (g) {
			if (policy == null) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "policy: ally, betray or random");
			}
			g.botPolicy = policy;
			event(g, "bot_policy", Map.of("policy", policy.name()));
			persist(g);
			notifyChanged(g, "bots");
			return g;
		}
	}

	private static boolean anyLivingDisconnected(Game g) {
		for (PlayerState p : g.players) {
			if (p.alive && !p.connected) {
				return true;
			}
		}
		return false;
	}

	/** Freezes the stage's clock (the caller persists and notifies). */
	private void doPause(Game g) {
		g.remainingMs = Math.max(0, Duration.between(clock.instant(), g.deadline).toMillis());
		g.paused = true;
		g.deadline = null;
		cancelTimer(g);
		event(g, "pause", Map.of("remainingMs", g.remainingMs));
	}

	/** Restarts the stage's clock with the time that was left (the caller persists and notifies). */
	private void doResume(Game g, String cause) {
		g.paused = false;
		g.deadline = clock.instant().plusMillis(g.remainingMs);
		scheduleTimer(g);
		event(g, "resume", Map.of("remainingMs", g.remainingMs, "cause", cause));
	}

	private void clearAutoPause(Game g) {
		g.autoPaused = false;
		g.autoPauseUntil = null;
		GameScheduler.Handle h = autoTimers.remove(g.id);
		if (h != null) {
			h.cancel();
		}
	}

	private void scheduleAutoResume(Game g) {
		GameScheduler.Handle old = autoTimers.remove(g.id);
		if (old != null) {
			old.cancel();
		}
		UUID id = g.id;
		Instant until = g.autoPauseUntil;
		autoTimers.put(id, scheduler.schedule(until, () -> onAutoResume(id, until)));
	}

	private void onAutoResume(UUID id, Instant until) {
		Game g = byId.get(id);
		if (g == null) {
			return;
		}
		try {
			synchronized (g) {
				if (!g.autoPaused || !g.paused || !until.equals(g.autoPauseUntil)) {
					return; // stale: someone resumed or took over meanwhile
				}
				autoTimers.remove(id);
				clearAutoPause(g);
				doResume(g, "auto_pause_over");
				persist(g);
				notifyPhase(g);
			}
		} catch (RuntimeException ex) {
			log.error("Trahison game {}: automatic resume failed", id, ex);
		}
	}

	// ------------------------------------------------------------------ player actions

	/** The player takes door {@code door} (an index in {@link Door#ALL}) with his wrist bracelet. */
	public Game passDoor(String hub, UUID uuid, int door) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerAction(g, uuid, Phase.DOORS);
			if (door < 0 || door >= 3) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "door index 0..2");
			}
			RoundState r = g.currentRound();
			if (r.passedDoor[p] >= 0) {
				throw new TrahisonException(TrahisonException.ALREADY_PASSED, "already passed a door");
			}
			Assignment a = r.assignment();
			Door d = Door.ALL.get(door);
			if (!d.contains(a.color(p))) {
				throw new TrahisonException(TrahisonException.WRONG_DOOR, "the bracelet colour is not on that door");
			}
			boolean compatible = false;
			for (Split s : Doors.validSplits(a)) {
				if (s.group(d).members().contains(p) && consistent(r, s)) {
					compatible = true;
					break;
				}
			}
			if (!compatible) {
				throw new TrahisonException(TrahisonException.DOOR_INCOMPATIBLE, "that door contradicts the doors already taken");
			}
			r.passedDoor[p] = door;
			event(g, "pass_door", Map.of("player", p, "door", door));
			persist(g);
			notifyChanged(g, "door");
			return g;
		}
	}

	/**
	 * Test/provisional (and the bots): the player's group solves its room at once: every step is solved and the exit
	 * opens, exactly as if the players had done it.
	 */
	public Game debugSolve(String hub, UUID uuid) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerAction(g, uuid, Phase.ROOMS);
			RoundState r = g.currentRound();
			int k = r.groupOf(p);
			if (rooms != null) {
				rooms.forceSolve(g, r, k);
			}
			markRoomSolved(g, r, k, p);
			persist(g);
			return g;
		}
	}

	/**
	 * A room action of a player ({@code use_slot}, {@code puzzle_input}, {@code pick_up}): in {@code ROOMS} only, not
	 * paused, by a living player, in the room of his group. When it finishes the room, the group's room is solved (as with
	 * {@link #debugSolve}). The room engine is called with the game locked (game, then room), so the room's saved state
	 * stays consistent with the game's, and a room failure leaves both untouched.
	 */
	public ActionResult roomAction(String hub, UUID uuid, RoomAction action) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerAction(g, uuid, Phase.ROOMS);
			if (rooms == null) {
				throw new TrahisonException(RoomException.NO_ROOM, "no room engine");
			}
			RoundState r = g.currentRound();
			int k = r.groupOf(p);
			ActionResult result = rooms.apply(g, r, k, p, action);
			if (result.roomFinished()) {
				markRoomSolved(g, r, k, p);
			}
			persist(g);
			return result;
		}
	}

	/** The group's room is done: remembers it (once), logs it and tells the listeners. The caller persists. */
	private void markRoomSolved(Game g, RoundState r, int group, int by) {
		if (r.roomSolved[group]) {
			return;
		}
		r.roomSolved[group] = true;
		event(g, "room_solved", Map.of("group", group, "by", by));
		notifyChanged(g, "room");
	}

	/** The player votes for his wrist bracelet's side of his group. A pair has one vote: its first voter's. */
	public Game vote(String hub, UUID uuid, Vote side) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerAction(g, uuid, Phase.VOTE);
			if (side == null) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "vote required");
			}
			RoundState r = g.currentRound();
			int k = r.groupOf(p);
			DoorGroup group = r.split().group(Door.ALL.get(k));
			if (group.pair().contains(p)) {
				if (r.pairVote[k] != null) {
					throw new TrahisonException(TrahisonException.ALREADY_VOTED, "the pair already voted");
				}
				r.pairVote[k] = side;
				r.pairVoter[k] = p;
			} else {
				if (r.soloVote[k] != null) {
					throw new TrahisonException(TrahisonException.ALREADY_VOTED, "already voted");
				}
				r.soloVote[k] = side;
			}
			event(g, "vote", Map.of("player", p, "group", k)); // the side stays secret until the results
			persist(g);
			notifyChanged(g, "vote");
			return g;
		}
	}

	/** In FINAL only; the wrist bracelet must read 9 or more and the player be alive. The game ends. */
	public Game openFinalDoor(String hub, UUID uuid) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerAction(g, uuid, Phase.FINAL);
			if (!Outcome.canOpenFinalDoor(g.bracelets.get(p).points, g.players.get(p).alive)) {
				throw new TrahisonException(TrahisonException.CANNOT_OPEN_FINAL, "the bracelet does not read 9");
			}
			g.winners = new ArrayList<>(Outcome.finalDoorOpeners(g.points(), g.aliveFlags()));
			event(g, "final_door", Map.of("opener", p, "winners", g.winners));
			finish(g, "final_door");
			persist(g);
			return g;
		}
	}

	// ------------------------------------------------------------------ startup

	/** Reloads the unfinished games and schedules their deadlines again (a deadline already past fires at once). */
	public void loadActiveGames() {
		for (Game g : store.loadActive()) {
			synchronized (byHub) {
				if (byHub.containsKey(g.hub)) {
					continue;
				}
				byHub.put(g.hub, g);
				byId.put(g.id, g);
			}
			synchronized (g) {
				if (rooms != null) {
					rooms.restore(g);
				}
				if (g.phase.timed() && !g.paused && g.deadline != null) {
					scheduleTimer(g);
				}
				if (g.autoPaused && g.paused && g.autoPauseUntil != null) {
					scheduleAutoResume(g);
				}
				log.info("Trahison game {} reloaded in {} (hub {})", g.id, g.phase, g.hub);
				notifyPhase(g);
			}
		}
	}

	// ------------------------------------------------------------------ the machine

	private int playerAction(Game g, UUID uuid, Phase expected) {
		if (g.phase == Phase.ENDED) {
			throw new TrahisonException(TrahisonException.GAME_ENDED, "the game ended");
		}
		int p = g.indexOf(uuid);
		if (p < 0) {
			throw new TrahisonException(TrahisonException.NOT_A_PLAYER, "not in the game");
		}
		if (g.phase != expected) {
			throw new TrahisonException(TrahisonException.WRONG_PHASE, "now " + g.phase + ", needs " + expected);
		}
		if (g.paused) {
			throw new TrahisonException(TrahisonException.PAUSED, "the game is paused");
		}
		if (!g.players.get(p).alive) {
			throw new TrahisonException(TrahisonException.DEAD, "ghosts do not act");
		}
		return p;
	}

	private void requireRunning(Game g) {
		if (!g.phase.timed()) {
			throw new TrahisonException(TrahisonException.WRONG_PHASE, "no running stage (" + g.phase + ")");
		}
	}

	/** Moves to the stage after the current one. */
	private void advance(Game g, String cause) {
		switch (g.phase) {
			case PROLOGUE -> {
				g.round = 1;
				startRound(g, false);
				enterPhase(g, Phase.DOORS);
			}
			case DOORS -> {
				closeDoors(g);
				enterPhase(g, Phase.ROOMS);
				if (rooms != null) {
					// After the stage view went out, so a client gets ROOMS first, then its room.
					rooms.startRound(g, g.currentRound());
				}
			}
			// T3 adds the timeout penalty of an unsolved room here.
			case ROOMS -> enterPhase(g, Phase.FREE_TIME);
			case FREE_TIME -> enterPhase(g, Phase.VOTE);
			case VOTE -> {
				resolveVotes(g);
				enterPhase(g, Phase.RESULTS);
			}
			case RESULTS -> {
				if (!g.currentRound().finalLoop && g.round < 3) {
					g.round++;
					startRound(g, false);
					enterPhase(g, Phase.DOORS);
				} else {
					enterPhase(g, Phase.FINAL);
				}
			}
			case FINAL -> {
				// DESIGN DEFAULT (the owner may change it): each final loop starts a fresh assignment, and there is
				// no doors stage, so the split is picked by the seed instead of by the players.
				g.finalLoops++;
				startRound(g, true);
				enterPhase(g, Phase.VOTE);
			}
			default -> throw new TrahisonException(TrahisonException.WRONG_PHASE, "cannot advance from " + g.phase);
		}
		event(g, "advanced", Map.of("cause", cause, "phase", g.phase.name()));
	}

	/** Draws the assignment of a new round (or final loop) from the game seed and the history. */
	private void startRound(Game g, boolean finalLoop) {
		if (rooms != null) {
			rooms.clear(g.hub); // the rooms of the previous round are gone with it
		}
		RoundState r = new RoundState();
		r.finalLoop = finalLoop;
		r.number = finalLoop ? 3 + g.finalLoops : g.round;
		r.seed = new SplittableRandom(g.seed + r.number * GOLDEN).nextLong();
		Assignment a = Assignment.generate(r.seed, g.history());
		r.placements = new ArrayList<>(a.placements());
		if (finalLoop) {
			r.splitIndex = seedSplit(r.seed);
		}
		g.rounds.add(r);
		for (int i = 0; i < PLAYERS; i++) {
			g.bracelets.get(i).placement = a.placement(i);
		}
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("round", r.number);
		d.put("seed", r.seed);
		d.put("placements", placementNames(a));
		event(g, "round_start", d);
	}

	private static int seedSplit(long roundSeed) {
		return new SplittableRandom(roundSeed ^ SPLIT_SALT).nextInt(2);
	}

	private static List<String> placementNames(Assignment a) {
		List<String> out = new ArrayList<>();
		for (Placement p : a.placements()) {
			out.add(p.color() + "/" + p.status());
		}
		return out;
	}

	/** True when every pass so far is a member of its door's group in split {@code s}. */
	private static boolean consistent(RoundState r, Split s) {
		for (int b = 0; b < PLAYERS; b++) {
			int d = r.passedDoor[b];
			if (d >= 0 && !s.group(Door.ALL.get(d)).members().contains(b)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The doors close: the round's split is the one the passes agree with (one pass is enough to decide, since a
	 * bracelet goes through a different door in each split); with no pass at all the seed picks. Living players who
	 * passed nothing lose the penalty. The split then defines the groups of rooms and votes, absentees included.
	 */
	private void closeDoors(Game g) {
		RoundState r = g.currentRound();
		List<Split> splits = Doors.validSplits(r.assignment());
		int chosen = -1;
		for (int i = 0; i < splits.size(); i++) {
			if (consistent(r, splits.get(i))) {
				chosen = i;
				break;
			}
		}
		boolean anyPass = false;
		for (int d : r.passedDoor) {
			anyPass |= d >= 0;
		}
		r.splitIndex = anyPass && chosen >= 0 ? chosen : seedSplit(r.seed);
		for (int i = 0; i < PLAYERS; i++) {
			if (g.players.get(i).alive && r.passedDoor[i] < 0) {
				BraceletState b = g.bracelets.get(i);
				b.points = Scoring.apply(b.points, g.settings.noDoorPenalty);
				r.penalized.add(i);
			}
		}
		event(g, "doors_closed", Map.of("split", r.splitIndex, "penalized", r.penalized));
	}

	/**
	 * Scores the round: the deltas of each group, applied to every bracelet (dead ones included, they vote ALLY and
	 * can be betrayed), then the history, then the newly dead become ghosts.
	 */
	private void resolveVotes(Game g) {
		RoundState r = g.currentRound();
		Split split = r.split();
		int[] total = new int[PLAYERS];
		for (int k = 0; k < 3; k++) {
			int[] d = Scoring.deltas(split.group(Door.ALL.get(k)), r.pairVote[k], r.soloVote[k]);
			for (int i = 0; i < PLAYERS; i++) {
				total[i] += d[i];
			}
		}
		int[] after = Scoring.applyAll(g.points(), total);
		for (int i = 0; i < PLAYERS; i++) {
			g.bracelets.get(i).points = after[i];
		}
		r.deltas = total;
		g.storeHistory(g.history().after(r.assignment(), split));
		List<Integer> dead = Outcome.newlyDead(after, g.aliveFlags());
		for (int i : dead) {
			g.players.get(i).alive = false;
			g.bracelets.get(i).ownerAlive = false;
		}
		r.newlyDead = new ArrayList<>(dead);
		List<String> votes = new ArrayList<>();
		for (int k = 0; k < 3; k++) {
			votes.add(k + ": pair " + Scoring.effective(r.pairVote[k]) + ", solo " + Scoring.effective(r.soloVote[k]));
		}
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("round", r.number);
		d.put("votes", votes);
		d.put("deltas", toList(total));
		d.put("points", toList(after));
		d.put("newlyDead", dead);
		event(g, "results", d);
	}

	private static List<Integer> toList(int[] a) {
		List<Integer> out = new ArrayList<>();
		for (int v : a) {
			out.add(v);
		}
		return out;
	}

	private void finish(Game g, String cause) {
		if (rooms != null) {
			rooms.clear(g.hub);
		}
		g.phase = Phase.ENDED;
		g.endedAt = clock.instant();
		g.deadline = null;
		g.paused = false;
		clearAutoPause(g);
		cancelTimer(g);
		event(g, "ended", Map.of("cause", cause));
		notifyPhase(g);
	}

	/** Enters a timed stage: full duration, deadline and timer (frozen if the game is paused). */
	private void enterPhase(Game g, Phase phase) {
		g.phase = phase;
		g.remainingMs = g.settings.durationSeconds(phase) * 1000L;
		if (g.paused) {
			g.deadline = null;
			cancelTimer(g);
		} else {
			g.deadline = clock.instant().plusMillis(g.remainingMs);
			scheduleTimer(g);
		}
		event(g, "phase", Map.of("phase", phase.name(), "round", g.round));
		notifyPhase(g);
	}

	private void scheduleTimer(Game g) {
		cancelTimer(g);
		if (g.settings.mode != Mode.AUTO || g.deadline == null || !g.phase.timed()) {
			return; // game master mode: the countdown is informational, he moves the game on
		}
		int version = ++g.timerVersion;
		UUID id = g.id;
		timers.put(id, scheduler.schedule(g.deadline, () -> onTimer(id, version)));
	}

	private void cancelTimer(Game g) {
		GameScheduler.Handle h = timers.remove(g.id);
		if (h != null) {
			h.cancel();
		}
	}

	private void onTimer(UUID id, int version) {
		Game g = byId.get(id);
		if (g == null) {
			return;
		}
		try {
			synchronized (g) {
				if (g.timerVersion != version || g.paused || !g.phase.timed()) {
					return; // stale timer
				}
				timers.remove(id);
				advance(g, "timer");
				persist(g);
			}
		} catch (RuntimeException ex) {
			log.error("Trahison game {}: timer failed", id, ex);
		}
	}

	// ------------------------------------------------------------------ log, persistence, listeners

	private void event(Game g, String type, Map<String, Object> data) {
		g.log.add(new LogEntry(clock.instant(), type, data));
	}

	private void persist(Game g) {
		try {
			store.save(g);
		} catch (RuntimeException ex) {
			log.error("Trahison game {}: could not save", g.id, ex);
		}
	}

	private void notifyPhase(Game g) {
		for (GameListener l : listeners) {
			try {
				l.phaseChanged(g);
			} catch (RuntimeException ex) {
				log.warn("Trahison listener failed on phaseChanged", ex);
			}
		}
	}

	private void notifyChanged(Game g, String patch) {
		for (GameListener l : listeners) {
			try {
				l.changed(g, patch);
			} catch (RuntimeException ex) {
				log.warn("Trahison listener failed on {}", patch, ex);
			}
		}
	}
}
