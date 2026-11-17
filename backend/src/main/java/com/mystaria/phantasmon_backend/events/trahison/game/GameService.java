package com.mystaria.phantasmon_backend.events.trahison.game;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mystaria.phantasmon_backend.events.trahison.layout.Cabin;
import com.mystaria.phantasmon_backend.events.trahison.layout.Layout;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutDoor;
import com.mystaria.phantasmon_backend.events.trahison.room.ActionResult;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomAction;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomDraw;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomException;
import com.mystaria.phantasmon_backend.events.trahison.rules.Assignment;
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.DoorGroup;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Item;
import com.mystaria.phantasmon_backend.events.trahison.rules.ItemDraw;
import com.mystaria.phantasmon_backend.events.trahison.rules.Role;
import com.mystaria.phantasmon_backend.events.trahison.rules.Roles;
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
	/** D52: the longest final-door window a game may be set to (seconds). */
	public static final int MAX_FINAL_DOOR_SECONDS = 300;
	public static final Duration AUTO_PAUSE_MAX = Duration.ofMinutes(5);
	/** Zone changes reach the game masters at most this often per game. */
	public static final long ZONE_NOTIFY_MIN_MS = 1000;
	private static final long GOLDEN = 0x9E3779B97F4A7C15L;
	private static final long SPLIT_SALT = 0x5DEECE66DL;

	private final GameStore store;
	private final Clock clock;
	private final GameScheduler scheduler;
	private final Map<String, Game> byHub = new ConcurrentHashMap<>();
	private final Map<UUID, Game> byId = new ConcurrentHashMap<>();
	private final Map<UUID, GameScheduler.Handle> timers = new ConcurrentHashMap<>();
	private final Map<UUID, GameScheduler.Handle> autoTimers = new ConcurrentHashMap<>();
	/** The end of the final door's window (T2.5), by game. */
	private final Map<UUID, GameScheduler.Handle> finalTimers = new ConcurrentHashMap<>();
	private final List<GameListener> listeners = new CopyOnWriteArrayList<>();
	/** The room engine link; null in tests that play the game alone (then {@code debug_solve} is the only way). */
	private volatile GameRooms rooms;
	/** The draw of a game's rooms; null in tests that do not draw (the rooms then come from the settings). */
	private volatile GameDraws draws;
	/** Where the plan of a hub comes from; null in tests that play without a plan (then there is no door). */
	private volatile LayoutLookup layouts;
	/** The plan of each game, as of the version it started with (read once, then kept). */
	private final Map<UUID, LayoutContent> plans = new ConcurrentHashMap<>();
	/** Game id to when the game masters last got a zone update, and the games with one scheduled. */
	private final Map<UUID, Long> lastZoneNotify = new ConcurrentHashMap<>();
	private final java.util.Set<UUID> zonePending = ConcurrentHashMap.newKeySet();

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

	/** Plugs the room draw in: a game then draws its nine rooms when it is created (T4.1). */
	public void useDraws(GameDraws gameDraws) {
		this.draws = gameDraws;
	}

	/** Plugs the plan source in: a game then reads the plan of its hub when it starts and computes its doors from it. */
	public void useLayouts(LayoutLookup lookup) {
		this.layouts = lookup;
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
		Settings wanted = settings != null ? settings : new Settings();
		wanted.finalDoorSeconds = Math.max(1, Math.min(MAX_FINAL_DOOR_SECONDS, wanted.finalDoorSeconds)); // D52
		if (draws != null && (wanted.testRoom == null || wanted.testRoom.isBlank()) && draws.bankSize() < RoomDraw.ROUNDS * RoomDraw.PER_ROUND) {
			// D73: no game without nine rooms to draw (a test room, or no draw wired at all, are the trial paths)
			throw new TrahisonException(TrahisonException.BANK_TOO_SMALL, "the room bank holds " + draws.bankSize() + " rooms, 9 needed");
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
			g.settings = wanted;
			g.createdAt = clock.instant();
			byHub.put(hub, g);
			byId.put(g.id, g);
		}
		synchronized (g) {
			event(g, "created", Map.of("seed", g.seed, "mode", g.settings.mode.name()));
			if (draws != null) {
				draws.draw(g, planOf(g), null);
				plans.remove(g.id); // the plan is read again, at its version, when the game starts
			}
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
			if (g.gameMasters.contains(uuid) && !g.playingGameMasters().isEmpty() && g.indexOf(uuid) < 0) {
				throw new TrahisonException(TrahisonException.ONE_PLAYING_GAME_MASTER, "only one game master may play (D46)");
			}
			if (g.phase != Phase.LOBBY) {
				throw new TrahisonException(TrahisonException.NOT_IN_LOBBY, "the game already started");
			}
			if (g.indexOf(uuid) >= 0) {
				throw new TrahisonException(TrahisonException.ALREADY_JOINED, "already in the game");
			}
			// D12: a game master may join as a player (secret role): nothing tells the others who he is.
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

	/** D102: an account registers as a spectator (or stops being one); a player cannot, and the game masters already watch. */
	public Game spectate(String hub, UUID uuid, String name, boolean on) {
		Game g = require(hub);
		synchronized (g) {
			if (g.indexOf(uuid) >= 0) {
				throw new TrahisonException(TrahisonException.ALREADY_JOINED, "a player does not spectate");
			}
			boolean changed = on ? g.spectators.put(uuid, name == null ? uuid.toString() : name) == null : g.spectators.remove(uuid) != null;
			if (changed) {
				event(g, on ? "spectate" : "unspectate", Map.of("account", uuid.toString()));
				persist(g);
				notifyChanged(g, "spectators");
			}
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
			if (g.settings.mode == Mode.GAME_MASTER && g.gameMasters.isEmpty()) {
				throw new TrahisonException(TrahisonException.NO_GAME_MASTER, "game master mode needs a game master");
			}
			g.bracelets = new ArrayList<>();
			for (int i = 0; i < PLAYERS; i++) {
				g.bracelets.add(new BraceletState(i, Outcome.START_POINTS));
			}
			g.layoutVersion = 0;
			plans.remove(g.id);
			if (layouts != null) {
				try {
					g.layoutVersion = layouts.find(g.hub, 0).version();
				} catch (RuntimeException ex) {
					log.warn("Trahison game {}: could not read the plan of hub {}", g.id, g.hub, ex);
				}
			}
			if (g.draw != null) {
				g.draw = GameDraws.assignPlaces(g.draw, planOf(g)); // the places of the plan version the game plays on
			}
			event(g, "start", Map.of("layoutVersion", g.layoutVersion));
			drawRolesAndItems(g); // T7: roles and hidden items, from the seed
			enterPhase(g, Phase.PROLOGUE);
			persist(g);
			whisperRoles(g); // after the views: each holder learns his role in private
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
		return setPoints(hub, bracelet, points, null);
	}

	/**
	 * As above, with the account that did it: a playing game master who sets his own bracelet to 0 is marked (D50), so that
	 * dying of it later does not give the others the win.
	 */
	public Game setPoints(String hub, int bracelet, int points, UUID actor) {
		Game g = require(hub);
		synchronized (g) {
			if (g.phase == Phase.LOBBY || g.phase == Phase.ENDED) {
				throw new TrahisonException(TrahisonException.WRONG_PHASE, "no running game");
			}
			if (bracelet < 0 || bracelet >= PLAYERS || points < 0) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "bad bracelet or points");
			}
			g.bracelets.get(bracelet).points = points;
			if (points == 0 && actor != null && g.indexOf(actor) == bracelet && g.gameMasters.contains(actor)
					&& !g.selfDestined.contains(bracelet)) {
				g.selfDestined.add(bracelet);
			}
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
					g.autoPauseUntil = clock.instant().plusSeconds(Math.max(1, g.settings.autoPauseSeconds));
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

	/**
	 * D7: an admin gives or takes back the game master role of an account, in the lobby only. A game can have none, one or
	 * several. D12: a game master may also play (he is then one of the nine, and nobody else is told he is a game master).
	 */
	public Game setGameMaster(String hub, UUID account, String name, boolean on) {
		Game g = require(hub);
		synchronized (g) {
			if (g.phase != Phase.LOBBY) {
				throw new TrahisonException(TrahisonException.NOT_IN_LOBBY, "game masters are assigned in the lobby");
			}
			if (on) {
				if (g.indexOf(account) >= 0 && g.playingGameMasters().stream().anyMatch(i -> !g.players.get(i).uuid.equals(account))) {
					throw new TrahisonException(TrahisonException.ONE_PLAYING_GAME_MASTER, "only one game master may play (D46)");
				}
				if (!g.gameMasters.add(account)) {
					return g;
				}
				g.gameMasterNames.put(account, name == null ? account.toString() : name);
			} else {
				if (!g.gameMasters.remove(account)) {
					return g;
				}
				g.gameMasterNames.remove(account);
			}
			event(g, on ? "game_master" : "game_master_removed", Map.of("account", account.toString()));
			persist(g);
			notifyChanged(g, "gm");
			return g;
		}
	}

	/** Makes an account a game master whatever the stage (no lobby rule): for tests and for restoring a game. */
	public Game addGameMaster(String hub, UUID uuid) {
		Game g = require(hub);
		synchronized (g) {
			if (g.gameMasters.add(uuid)) {
				g.gameMasterNames.putIfAbsent(uuid, uuid.toString());
				event(g, "game_master", Map.of("account", uuid.toString()));
				persist(g);
				notifyChanged(g, "gm");
			}
			return g;
		}
	}

	/** How the bots vote from now on. */
	/** Draws the nine rooms again (T4.1), with a new seed (the given one, else the next one derived from the draw's). Before any room has started. */
	public Game redraw(String hub, Long seed) {
		Game g = require(hub);
		synchronized (g) {
			if (draws == null) {
				throw new TrahisonException(TrahisonException.NO_DRAW, "no room draw here");
			}
			if (g.phase == Phase.ENDED || g.phase != Phase.LOBBY && g.phase != Phase.PROLOGUE) {
				throw new TrahisonException(TrahisonException.WRONG_PHASE, "the rooms can only be drawn again before round 1");
			}
			long next = seed != null ? seed : (g.draw != null ? g.draw.seed() : g.seed) * 0x9E3779B97F4A7C15L + 1;
			draws.draw(g, planOf(g), next);
			event(g, "redraw", Map.of("seed", next));
			persist(g);
			notifyChanged(g, "draw");
			return g;
		}
	}

	/** The game master puts another room of the bank in the place of a drawn one, as long as that round's rooms have not started. */
	public Game replaceRoom(String hub, int round, int group, String roomId) {
		Game g = require(hub);
		synchronized (g) {
			if (draws == null) {
				throw new TrahisonException(TrahisonException.NO_DRAW, "no room draw here");
			}
			if (g.phase == Phase.ENDED) {
				throw new TrahisonException(TrahisonException.GAME_ENDED, "the game ended");
			}
			if (g.rounds.stream().anyMatch(r -> r.number == round && !r.rooms.isEmpty())) {
				throw new TrahisonException(TrahisonException.WRONG_PHASE, "the rooms of round " + round + " already started");
			}
			draws.replace(g, round, group, roomId);
			event(g, "replace_room", Map.of("round", round, "group", group, "room", String.valueOf(roomId)));
			persist(g);
			notifyChanged(g, "draw");
			return g;
		}
	}

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

	/**
	 * The game master opens or closes a door of the plan by hand ({@code set_door}). It holds until the stage changes (then
	 * the defaults apply again). Refused for a door the game's plan does not have.
	 */
	public Game setDoor(String hub, String door, boolean open) {
		Game g = require(hub);
		synchronized (g) {
			if (g.phase == Phase.ENDED) {
				throw new TrahisonException(TrahisonException.GAME_ENDED, "already ended");
			}
			if (door == null || !DoorStates.ids(planOf(g)).contains(door)) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "no such door in the plan: " + door);
			}
			g.doorOverrides.put(door, open);
			event(g, "set_door", Map.of("door", door, "open", open));
			persist(g);
			notifyChanged(g, "doors");
			return g;
		}
	}

	/**
	 * A player's client says which zone of the plan he stands in, and where (hub coordinates). Anyone who does not play
	 * is ignored. The database is not written (positions come every couple of seconds); a change of zone reaches the
	 * game masters' views, at most once per {@link #ZONE_NOTIFY_MIN_MS}.
	 */
	public void setZone(String hub, UUID uuid, String zone, double x, double y, double z) {
		Game g = byHub.get(hub);
		if (g == null) {
			return;
		}
		synchronized (g) {
			int i = g.indexOf(uuid);
			if (i < 0 || g.phase == Phase.ENDED) {
				return;
			}
			PlayerState p = g.players.get(i);
			String next = zone == null || zone.isBlank() ? null : zone;
			boolean changed = !java.util.Objects.equals(p.zone, next);
			boolean moved = p.x == null || Math.abs(p.x - x) + Math.abs(p.z == null ? 0 : p.z - z) >= MOVED_BLOCKS;
			p.zone = next;
			boolean explored = MapViews.visit(g, i, next, next != null && knownZone(g, next));
			p.x = x;
			p.y = y;
			p.z = z;
			boolean doorsMoved = false;
			if (g.phase == Phase.DOORS && !g.paused) {
				List<DoorHint> before = g.doorHints;
				boolean passed = changed && crossing(g, i, next);
				boolean opened = updateWindows(g);
				doorsMoved = passed || opened || !DoorThresholds.hints(g, planOf(g)).equals(before);
				if (passed || opened) {
					persist(g);
				}
			}
			if (g.phase == Phase.FINAL && g.finalDoor != null && changed && finalCrossing(g, i, next)) {
				persist(g);
			}
			boolean cabinsMoved = false;
			if (g.phase == Phase.VOTE && !g.paused) {
				cabinsMoved = updateCabins(g) || changed && !g.cabinLocks.isEmpty(); // who is inside moves a door's openFor
				if (cabinsMoved) {
					persist(g);
				}
			}
			if (doorsMoved) {
				notifyChanged(g, "door"); // everyone's door_hints (and maybe a door) changed
			} else if (cabinsMoved) {
				notifyChanged(g, "cabin");
			}
			if (explored) {
				persist(g);
				notifyExplored(g, i);
			}
			if (changed || moved) {
				zoneChanged(g); // the cursors of those who watch follow the players
			}
		}
	}

	/** A player moved this far since his last report: those who watch get his new cursor (at most once a second). */
	private static final double MOVED_BLOCKS = 2.0;

	private boolean knownZone(Game g, String zone) {
		return planOf(g).zones().stream().anyMatch(z -> zone.equals(z.id()));
	}

	private void notifyExplored(Game g, int seat) {
		for (GameListener l : listeners) {
			try {
				l.exploredChanged(g, seat);
			} catch (RuntimeException ex) {
				log.warn("Trahison listener failed on exploredChanged", ex);
			}
		}
	}

	/**
	 * A player entered a zone while the final door is open (T2.5): if it is the zone beyond the door and he is one of those it
	 * lets through, he has crossed. The door stays open its whole window even when all of them have crossed (D19). True if he
	 * was counted.
	 */
	private boolean finalCrossing(Game g, int player, String zone) {
		FinalDoorWindow window = g.finalDoor;
		LayoutDoor door = finalDoorOf(planOf(g));
		if (zone == null || door == null || !zone.equals(door.zoneB()) || !window.openers().contains(player)
				|| window.crossed().contains(player)) {
			return false;
		}
		window = window.withCrossed(player);
		g.finalDoor = window;
		event(g, "final_cross", Map.of("player", player));
		return true;
	}

	// ------------------------------------------------------------------ colour doors (T2.3)

	/** The ids of the colour doors whose window is open now (and drops the windows that ended). */
	private Set<String> openWindows(Game g, long now) {
		g.doorWindows.values().removeIf(w -> w.untilMs() <= now);
		return g.doorWindows.keySet();
	}

	/**
	 * A player entered a zone during DOORS: if it is the room side of a colour door that is open, he passed it (as with
	 * {@code pass_door}); a refusal (his bracelet does not fit, or the doors already taken) is silent. True if he passed.
	 */
	private boolean crossing(Game g, int player, String zone) {
		RoundState r = g.currentRound();
		if (zone == null || !g.players.get(player).alive || r.passedDoor[player] >= 0) {
			return false;
		}
		long now = clock.millis();
		for (DoorThresholds.Entry e : DoorThresholds.colorDoors(planOf(g), g.round)) {
			DoorWindow window = g.doorWindows.get(e.door().id());
			if (window == null || window.untilMs() <= now || !zone.equals(e.door().zoneB())) {
				continue;
			}
			try {
				recordPass(g, player, e.index());
			} catch (TrahisonException ex) {
				return false;
			}
			endWindowIfDone(g, e.door().id(), window);
			return true;
		}
		return false;
	}

	/** Closes a door's window once everybody it waited for has crossed (or can no longer). */
	private void endWindowIfDone(Game g, String doorId, DoorWindow window) {
		RoundState r = g.currentRound();
		for (int p : window.expected()) {
			if (g.players.get(p).alive && r.passedDoor[p] < 0) {
				return;
			}
		}
		g.doorWindows.remove(doorId);
	}

	/** Opens the colour doors whose threshold is satisfied now. True if one opened. */
	private boolean updateWindows(Game g) {
		RoundState r = g.currentRound();
		long now = clock.millis();
		Set<String> open = openWindows(g, now);
		boolean opened = false;
		for (DoorThresholds.Entry e : DoorThresholds.colorDoors(planOf(g), g.round)) {
			if (open.contains(e.door().id())) {
				continue;
			}
			List<Integer> expected = DoorThresholds.expected(g, r, e);
			if (expected.isEmpty()) {
				continue;
			}
			DoorWindow window = new DoorWindow(now + Math.max(1, g.settings.doorOpenSeconds) * 1000L, expected);
			g.doorWindows.put(e.door().id(), window);
			scheduleWindowEnd(g, e.door().id(), window);
			event(g, "door_open", Map.of("door", e.door().id(), "players", expected));
			opened = true;
		}
		return opened;
	}

	private void scheduleWindowEnd(Game g, String doorId, DoorWindow window) {
		UUID id = g.id;
		scheduler.schedule(Instant.ofEpochMilli(window.untilMs()), () -> onWindowEnd(id, doorId, window));
	}

	private void onWindowEnd(UUID id, String doorId, DoorWindow window) {
		Game g = byId.get(id);
		if (g == null) {
			return;
		}
		try {
			synchronized (g) {
				if (g.doorWindows.get(doorId) != window) {
					return; // already closed, or opened again since
				}
				g.doorWindows.remove(doorId);
				persist(g);
				notifyChanged(g, "door");
			}
		} catch (RuntimeException ex) {
			log.error("Trahison game {}: door window failed", id, ex);
		}
	}

	private void zoneChanged(Game g) {
		long now = clock.millis();
		Long last = lastZoneNotify.get(g.id);
		if (last == null || now - last >= ZONE_NOTIFY_MIN_MS) {
			lastZoneNotify.put(g.id, now);
			notifyZones(g);
		} else if (zonePending.add(g.id)) {
			UUID id = g.id;
			scheduler.schedule(Instant.ofEpochMilli(last + ZONE_NOTIFY_MIN_MS), () -> flushZones(id));
		}
	}

	private void flushZones(UUID id) {
		Game g = byId.get(id);
		if (g == null) {
			return;
		}
		synchronized (g) {
			zonePending.remove(id);
			lastZoneNotify.put(id, clock.millis());
			notifyZones(g);
		}
	}

	private void notifyZones(Game g) {
		for (GameListener l : listeners) {
			try {
				l.zonesChanged(g);
			} catch (RuntimeException ex) {
				log.warn("Trahison listener failed on zonesChanged", ex);
			}
		}
	}

	/** The plan this game uses (the version it started with); empty when there is none or it cannot be read. */
	private LayoutContent planOf(Game g) {
		LayoutContent plan = plans.get(g.id);
		if (plan != null) {
			return plan;
		}
		LayoutLookup lookup = layouts;
		if (lookup == null) {
			return LayoutContent.empty();
		}
		try {
			Layout layout = lookup.find(g.hub, g.layoutVersion);
			plan = layout.content();
			plans.put(g.id, plan);
			return plan;
		} catch (RuntimeException ex) {
			log.warn("Trahison game {}: could not read the plan of hub {}", g.id, g.hub, ex);
			return LayoutContent.empty();
		}
	}

	/** Recomputes {@link Game#doors} from the stage, the room progress and the game master's overrides. */
	private void refreshDoors(Game g) {
		RoundState r = g.currentRound();
		LayoutContent plan = planOf(g);
		long now = clock.millis();
		g.doors = new java.util.LinkedHashMap<>(DoorStates.compute(plan, g.phase, g.round, r == null ? null : r.roomSolved,
			g.doorOverrides, openWindows(g, now), heldCabins(g, plan, r),
			g.finalDoor == null ? java.util.Set.of() : new java.util.TreeSet<>(g.finalDoor.openers())));
		pruneItems(g);
		if (g.phase != Phase.ENDED) {
			for (String id : g.lockedDoors.keySet()) { // T7.2: a locked door is shut for everybody, whatever its other state
				if (g.doors.containsKey(id) && !g.doorOverrides.containsKey(id)) {
					g.doors.put(id, DoorState.CLOSED);
				}
			}
		}
		g.doorHints = DoorThresholds.hints(g, plan);
		g.cabinIds = plan.cabins().stream().map(Cabin::id).toList();
		g.myCabins = new java.util.LinkedHashMap<>();
		if (r != null && r.splitIndex != null) {
			for (int p = 0; p < g.players.size(); p++) {
				Cabin mine = myCabin(plan, r, p).orElse(null);
				if (mine != null) {
					g.myCabins.put(p, mine.id());
				}
			}
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
		if (rooms != null) {
			rooms.pauseHints(g); // D66: the hint countdown stops too
		}
	}

	/** Restarts the stage's clock with the time that was left (the caller persists and notifies). */
	private void doResume(Game g, String cause) {
		g.paused = false;
		g.deadline = clock.instant().plusMillis(g.remainingMs);
		scheduleTimer(g);
		if (rooms != null) {
			rooms.resumeHints(g);
		}
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

	// ------------------------------------------------------------------ death and bodies (T6.1)

	/** How close, in blocks, a living player must be to a body to take its bracelet. */
	public static final double TAKE_BRACELET_RANGE = 3.0;
	/** How close, in blocks, a living player must be to a body to have seen it. */
	public static final double SEE_BODY_RANGE = 8.0;

	/**
	 * THE ONE WAY TO KILL: the player becomes a ghost, keeps his points and his place in his group (D13), his bracelet votes
	 * ALLY from now on and lies on a body where his client last said he stood. {@code announced}: every living player learns
	 * of it now; otherwise only those who see the body. Does nothing for a player already dead. The caller must then call
	 * {@link #endIfGameMasterKilled} (D12), persist and notify.
	 */
	private void markDead(Game g, int i, String cause, boolean announced) {
		PlayerState p = g.players.get(i);
		if (!p.alive) {
			return;
		}
		p.alive = false;
		BraceletState b = g.bracelets.get(i);
		b.ownerAlive = false;
		b.holder = null; // on the body
		Body body = new Body();
		body.player = i;
		body.zone = p.zone;
		body.x = p.x;
		body.y = p.y;
		body.z = p.z;
		body.cause = cause;
		body.announced = announced;
		// D97: the bracelets he carried fall on his body and can be taken again.
		for (int c : p.carried) {
			body.dropped.add(c);
			g.bracelets.get(c).holder = null;
		}
		p.carried = new ArrayList<>();
		g.bodies.add(body);
	}

	/**
	 * A kill from outside the stage machine (the knife of T7, the game master): {@code victim} dies of {@code cause}, his
	 * body appears where he last stood. Announced to all when {@link Settings#announceDeaths}. A playing game master killed
	 * ends the game (D12). Refuses a ghost.
	 */
	public Game kill(String hub, int victim, String cause) {
		Game g = require(hub);
		synchronized (g) {
			if (g.phase == Phase.LOBBY || g.phase == Phase.ENDED) {
				throw new TrahisonException(TrahisonException.WRONG_PHASE, "no running game");
			}
			if (victim < 0 || victim >= g.players.size()) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "no such player");
			}
			if (!g.players.get(victim).alive) {
				throw new TrahisonException(TrahisonException.DEAD, "already dead");
			}
			markDead(g, victim, cause == null || cause.isBlank() ? "kill" : cause, g.settings.announceDeaths);
			event(g, "killed", Map.of("player", victim, "cause", cause == null ? "kill" : cause));
			endIfGameMasterKilled(g); // D12: the hook of every death path
			persist(g);
			notifyChanged(g, "death");
			return g;
		}
	}

	/**
	 * A living player takes the bracelet off a body: he must stand in the zone of the body, at most
	 * {@link #TAKE_BRACELET_RANGE} blocks from it. The bracelet then counts at the doors he presents it to ({@code carried});
	 * it keeps its colour, its points and its vote. Taking a body's bracelet shows him the body.
	 */
	public Game takeBracelet(String hub, UUID uuid, int dead) {
		return takeBracelet(hub, uuid, dead, null);
	}

	/** As above, for one bracelet of the body: {@code bracelet} null takes the dead one's own first, then a dropped one (D97). */
	public Game takeBracelet(String hub, UUID uuid, int dead, Integer bracelet) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerActionAnyPhase(g, uuid);
			Body body = g.bodyOf(dead);
			if (body == null || !body.hasBracelets()) {
				throw new TrahisonException(TrahisonException.NO_BODY, "no bracelet to take there");
			}
			int taken = bracelet != null ? bracelet : body.braceletOnBody ? dead : body.dropped.get(0);
			if (!(taken == dead && body.braceletOnBody) && !body.dropped.contains(taken)) {
				throw new TrahisonException(TrahisonException.NO_BODY, "that bracelet is not on the body");
			}
			if (!near(g.players.get(p), body, TAKE_BRACELET_RANGE)) {
				throw new TrahisonException(TrahisonException.BODY_TOO_FAR, "too far from the body");
			}
			if (taken == dead && body.braceletOnBody) {
				body.braceletOnBody = false;
			} else {
				body.dropped.remove(Integer.valueOf(taken));
			}
			if (!body.seenBy.contains(p)) {
				body.seenBy.add(p);
			}
			g.players.get(p).carried.add(taken);
			g.bracelets.get(taken).holder = p;
			event(g, "bracelet_taken", Map.of("by", p, "from", dead, "bracelet", taken));
			boolean opened = g.phase == Phase.DOORS && !g.paused && updateWindows(g); // one more bracelet at the threshold
			persist(g);
			notifyChanged(g, opened ? "door" : "body");
			return g;
		}
	}

	/**
	 * A living player's client saw a body (in its zone, within {@link #SEE_BODY_RANGE} blocks, with a line of sight that only
	 * the client can tell): the backend checks the distance and from then on this player knows about the death.
	 */
	public Game bodySeen(String hub, UUID uuid, int dead) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerActionAnyPhase(g, uuid);
			Body body = g.bodyOf(dead);
			if (body == null) {
				throw new TrahisonException(TrahisonException.NO_BODY, "no body there");
			}
			if (body.knownTo(p)) {
				return g;
			}
			if (!near(g.players.get(p), body, SEE_BODY_RANGE)) {
				throw new TrahisonException(TrahisonException.BODY_TOO_FAR, "too far from the body");
			}
			body.seenBy.add(p);
			event(g, "body_seen", Map.of("by", p, "of", dead));
			persist(g);
			notifyChanged(g, "body");
			return g;
		}
	}

	/** Same zone as the body and within {@code range} blocks; a player with no known position is never near. */
	private static boolean near(PlayerState p, Body body, double range) {
		if (p.zone == null || !p.zone.equals(body.zone) || p.x == null || body.x == null) {
			return false;
		}
		double dx = p.x - body.x;
		double dy = (p.y == null || body.y == null) ? 0 : p.y - body.y;
		double dz = p.z - body.z;
		return dx * dx + dy * dy + dz * dz <= range * range;
	}

	/** A living, connected-or-not player of a running (not paused) game, whatever the stage. */
	private int playerActionAnyPhase(Game g, UUID uuid) {
		if (g.phase == Phase.LOBBY || g.phase == Phase.ENDED) {
			throw new TrahisonException(TrahisonException.WRONG_PHASE, "no running game");
		}
		int p = g.indexOf(uuid);
		if (p < 0) {
			throw new TrahisonException(TrahisonException.NOT_A_PLAYER, "not in the game");
		}
		if (g.paused) {
			throw new TrahisonException(TrahisonException.PAUSED, "the game is paused");
		}
		if (!g.players.get(p).alive) {
			throw new TrahisonException(TrahisonException.DEAD, "ghosts do not act");
		}
		return p;
	}

	// ------------------------------------------------------------------ player actions

	/** The player takes door {@code door} (an index in {@link Door#ALL}) with his wrist bracelet. */
	public Game passDoor(String hub, UUID uuid, int door) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerAction(g, uuid, Phase.DOORS);
			requireFree(g, p);
			recordPass(g, p, door);
			persist(g);
			notifyChanged(g, "door");
			return g;
		}
	}

	/** Records that player {@code p} took door {@code door} (an index in {@link Door#ALL}); refuses what {@link #passDoor} refuses. */
	private void recordPass(Game g, int p, int door) {
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
	 * A room action of a player ({@code use_slot}, {@code puzzle_input}, {@code pick_up}, the inventory's actions): in
	 * {@code ROOMS}, not paused, by a living player, in the room of his group. When it finishes the room, the group's room is
	 * solved (as with {@link #debugSolve}). The room engine is called with the game locked (game, then room), so the room's
	 * saved state stays consistent with the game's, and a room failure leaves both untouched.
	 * <p>
	 * Free time (D8): {@code FREE_TIME} also takes room actions, from any living player, in any room of the round
	 * ({@code group}: which one, own group when null), and a finished room can be replayed. It gives no points and opens
	 * nothing: a room finished there is never marked solved.
	 */
	public ActionResult roomAction(String hub, UUID uuid, RoomAction action) {
		return roomAction(hub, uuid, action, null);
	}

	/** As {@link #roomAction(String, UUID, RoomAction)}, aimed at room {@code group} (null: the sender's own). */
	public ActionResult roomAction(String hub, UUID uuid, RoomAction action, Integer group) {
		return roomAction(hub, uuid, action, group, null);
	}

	/**
	 * As above, aimed at the room of group {@code group} in round {@code round} (null: the current round). A room of an
	 * earlier round is only reachable in the free time, which lets every living player visit every room opened so far (D8);
	 * without a {@code group} the sender's own group of that round is meant.
	 */
	public ActionResult roomAction(String hub, UUID uuid, RoomAction action, Integer group, Integer round) {
		Game g = require(hub);
		synchronized (g) {
			boolean freeTime = g.phase == Phase.FREE_TIME;
			// D22: ROOMS and VOTE also take actions, from a player whose group has finished its own room of the round
			Phase stage = freeTime || g.phase == Phase.VOTE ? g.phase : Phase.ROOMS;
			int p = playerAction(g, uuid, stage);
			requireFree(g, p);
			if (rooms == null) {
				throw new TrahisonException(RoomException.NO_ROOM, "no room engine");
			}
			RoundState current = g.currentRound();
			boolean roam = freeTime || canRoam(g, p);
			if (!roam && g.phase == Phase.VOTE) {
				throw new TrahisonException(TrahisonException.WRONG_PHASE, "rooms are closed during the vote, until his group's room is done");
			}
			boolean free = roam;
			RoundState r = current;
			if (round != null && round != current.number) {
				if (!roam) {
					throw new TrahisonException(RoomException.NOT_IN_ROOM, "the rooms of round " + round + " are closed now");
				}
				r = null;
				for (RoundState earlier : g.rounds) {
					if (earlier.number == round && !earlier.finalLoop && earlier.splitIndex != null) {
						r = earlier;
					}
				}
				if (r == null) {
					throw new TrahisonException(RoomException.NO_ROOM, "no room in round " + round);
				}
			}
			int k = r.groupOf(p);
			if (group != null && group != k) {
				if (!roam) {
					throw new TrahisonException(RoomException.NOT_IN_ROOM, "player " + p + " is not in group " + group);
				}
				if (group >= r.rooms.size()) {
					throw new TrahisonException(RoomException.NO_ROOM, "no room for group " + group);
				}
				k = group;
			}
			if (action instanceof RoomAction.GiveItem give) {
				// The room only knows indices; where the players stand is the game's (EventsTrahisonZone).
				if (give.to() < 0 || give.to() >= g.players.size() || !g.players.get(give.to()).alive) {
					throw new TrahisonException(RoomException.BAD_ACTION, "player " + give.to() + " cannot receive the item");
				}
				if (!GiveRange.near(g.players.get(p), g.players.get(give.to()))) {
					throw new TrahisonException(RoomException.TOO_FAR, "player " + give.to() + " is too far");
				}
			}
			// His own room of this round, in ROOMS, still plays as before (a room being solved gives its door); everywhere else
			// (the free time, another room, an earlier round, any room during VOTE) it is the visit: no point, nothing opened.
			boolean own = r == current && k == current.groupOf(p) && g.phase == Phase.ROOMS;
			free = !own;
			ActionResult result = rooms.apply(g, r, k, p, action, free);
			if (result.roomFinished() && !free) {
				markRoomSolved(g, r, k, p); // (not free: r is the current round)
			}
			persist(g);
			return result;
		}
	}

	/**
	 * D22: whether player {@code p} is free to roam the rooms (his own, of earlier rounds and of the other trios) outside the
	 * free time: the stage is {@code ROOMS} or {@code VOTE}, he is alive and the room of his group in the current round is
	 * finished. Decided here, sent to the client as {@code viewer.can_roam}.
	 */
	public static boolean canRoam(Game g, int p) {
		RoundState r = g.currentRound();
		if (r == null || r.splitIndex == null || p < 0 || p >= g.players.size() || !g.players.get(p).alive
				|| g.phase != Phase.ROOMS && g.phase != Phase.VOTE) {
			return false;
		}
		return r.roomSolved[r.groupOf(p)];
	}

	/**
	 * End of {@code ROOMS} (T3.6): the exit of each room still unsolved opens (so does its door) and every member of its group
	 * loses {@link Scoring#ROOM_TIMEOUT_PENALTY} point, but the point never takes a bracelet to 0 (it stays at 1, D67): this
	 * kills nobody. Returns true when that ended the game.
	 */
	private boolean penalizeUnsolvedRooms(Game g) {
		if (rooms == null || !g.settings.roomTimeout) {
			return false;
		}
		RoundState r = g.currentRound();
		List<Integer> opened = rooms.openUnsolved(g, r);
		if (opened.isEmpty()) {
			return false;
		}
		int[] delta = new int[PLAYERS];
		for (int k : opened) {
			markRoomSolved(g, r, k, -1);
			for (int p : r.split().group(Door.ALL.get(k)).members()) {
				delta[p] += Scoring.ROOM_TIMEOUT_PENALTY;
			}
		}
		int[] before = g.points();
		int[] after = Scoring.applyAll(before, delta);
		for (int i = 0; i < PLAYERS; i++) {
			if (before[i] >= 1 && after[i] < 1) {
				after[i] = 1; // D67: this point never kills
			}
			g.bracelets.get(i).points = after[i];
		}
		event(g, "rooms_timeout", Map.of("groups", opened, "points", toList(after)));
		return endIfGameMasterKilled(g);
	}

	/** The group's room is done: remembers it (once), logs it and tells the listeners. The caller persists. */
	private void markRoomSolved(Game g, RoundState r, int group, int by) {
		if (r.roomSolved[group]) {
			return;
		}
		boolean first = !r.roomSolved[0] && !r.roomSolved[1] && !r.roomSolved[2];
		r.roomSolved[group] = true;
		event(g, "room_solved", Map.of("group", group, "by", by));
		if (first && by >= 0 && g.phase == Phase.ROOMS && g.settings.race) {
			announceRace(g, r, group);
		}
		notifyChanged(g, "room");
	}

	/**
	 * The cabin of player {@code p} in the round: the one that serves the colour pair of his group's door, on his side
	 * (pair or solo). Empty when the plan has no such cabin or the round's split is not known.
	 */
	private static java.util.Optional<Cabin> myCabin(LayoutContent plan, RoundState r, int p) {
		if (r.splitIndex == null) {
			return java.util.Optional.empty();
		}
		int k = r.groupOf(p);
		boolean pair = r.split().group(Door.ALL.get(k)).pair().contains(p);
		return Cabins.of(plan, Door.ALL.get(k), pair);
	}

	// ------------------------------------------------------------------ vote cabins (D4, D5)

	/** The side of a group (pair or solo) a cabin serves in the round, and who is on it. */
	private record Side(int group, boolean pair, List<Integer> members) {
	}

	private static Side sideOf(LayoutContent plan, RoundState r, Cabin cabin) {
		if (r == null || r.splitIndex == null) {
			return null;
		}
		Split split = r.split();
		for (int k = 0; k < Door.ALL.size(); k++) {
			Door d = Door.ALL.get(k);
			DoorGroup group = split.group(d);
			for (boolean pair : new boolean[] { true, false }) {
				if (Cabins.of(plan, d, pair).map(c -> c.id().equals(cabin.id())).orElse(false)) {
					return new Side(k, pair, pair ? new ArrayList<>(group.pair()) : List.of(group.solo()));
				}
			}
		}
		return null;
	}

	private static boolean voted(RoundState r, Side side) {
		return (side.pair() ? r.pairVote[side.group()] : r.soloVote[side.group()]) != null;
	}

	private static boolean inside(Game g, int player, Cabin cabin) {
		return cabin.zone() != null && cabin.zone().equals(g.players.get(player).zone);
	}

	/** The living humans of a side: the ones who go through the cabin (bots vote without it). */
	private static List<Integer> humansOf(Game g, Side side) {
		List<Integer> out = new ArrayList<>();
		for (int p : side.members()) {
			PlayerState ps = g.players.get(p);
			if (ps.alive && !ps.bot) {
				out.add(p);
			}
		}
		return out;
	}

	/**
	 * The cabins a group holds, with who each door stays open for (D4, D5): while the group waits for the partner, its
	 * living humans; once it voted, the group and whoever is inside; locked, only the strangers who got in (they may walk out).
	 */
	private Map<String, Set<Integer>> heldCabins(Game g, LayoutContent plan, RoundState r) {
		Map<String, Set<Integer>> out = new LinkedHashMap<>();
		for (Cabin cabin : plan.cabins()) {
			CabinLock lock = g.cabinLocks.get(cabin.id());
			if (lock == null) {
				continue;
			}
			Side side = sideOf(plan, r, cabin);
			Set<Integer> openFor = new java.util.TreeSet<>();
			for (int p = 0; p < g.players.size(); p++) {
				boolean mine = side != null && side.members().contains(p);
				if (g.players.get(p).alive && (inside(g, p, cabin) && (!mine || lock.released())
						|| mine && (lock.released() || lock.waiting()))) {
					openFor.add(p);
				}
			}
			out.put(cabin.id(), openFor);
		}
		return out;
	}

	/**
	 * Brings the cabins up to date with where the players stand (VOTE, not paused; the caller persists and notifies when this
	 * returns true). A living human who enters the cabin of his group, before it voted, opens its lock: a solo locks at once,
	 * a pair waits {@code cabin_partner_seconds} for the partner (and locks at once when both are in); a waiting cabin whose
	 * occupants left disappears again; a released cabin frees itself when the group has left.
	 */
	private boolean updateCabins(Game g) {
		if (!g.settings.cabinsRequired) {
			return false;
		}
		LayoutContent plan = planOf(g);
		RoundState r = g.currentRound();
		boolean changed = false;
		long now = clock.millis();
		for (Cabin cabin : plan.cabins()) {
			Side side = sideOf(plan, r, cabin);
			if (side == null) {
				continue;
			}
			CabinLock lock = g.cabinLocks.get(cabin.id());
			List<Integer> expected = lock == null ? humansOf(g, side) : lock.expected();
			List<Integer> here = expected.stream().filter(p -> g.players.get(p).alive && inside(g, p, cabin)).toList();
			if (lock == null) {
				if (voted(r, side) || here.isEmpty()) {
					continue;
				}
				boolean all = here.size() == expected.size();
				CabinLock born = new CabinLock(here.get(0), all ? 0 : now + Math.max(1, g.settings.cabinPartnerSeconds) * 1000L, all,
					false, expected);
				g.cabinLocks.put(cabin.id(), born);
				event(g, "cabin_enter", Map.of("player", here.get(0), "cabin", cabin.id(), "locked", all));
				if (!all) {
					scheduleCabinWindow(g, cabin.id(), born);
				}
				changed = true;
			} else if (lock.waiting()) {
				if (here.isEmpty()) {
					g.cabinLocks.remove(cabin.id()); // everybody left before the lock: it is free again
					changed = true;
				} else if (here.size() == expected.stream().filter(p -> g.players.get(p).alive).count()) {
					g.cabinLocks.put(cabin.id(), lock.lockedNow());
					event(g, "cabin_locked", Map.of("cabin", cabin.id(), "cause", "all_in"));
					changed = true;
				}
			} else if (lock.released() && here.isEmpty()) {
				g.cabinLocks.remove(cabin.id());
				event(g, "cabin_free", Map.of("cabin", cabin.id()));
				changed = true;
			}
		}
		return changed;
	}

	private void scheduleCabinWindow(Game g, String cabinId, CabinLock lock) {
		UUID id = g.id;
		scheduler.schedule(Instant.ofEpochMilli(lock.windowUntilMs()), () -> onCabinWindowEnd(id, cabinId, lock));
	}

	/** The partner did not come in time: the cabin locks with the ones inside (or frees itself if nobody is). */
	private void onCabinWindowEnd(UUID id, String cabinId, CabinLock lock) {
		Game g = byId.get(id);
		if (g == null) {
			return;
		}
		try {
			synchronized (g) {
				if (!lock.equals(g.cabinLocks.get(cabinId)) || !lock.waiting()) {
					return; // locked, released or freed since
				}
				boolean anyone = false;
				for (Cabin cabin : planOf(g).cabins()) {
					if (cabin.id().equals(cabinId)) {
						for (int p : lock.expected()) {
							anyone |= g.players.get(p).alive && inside(g, p, cabin);
						}
					}
				}
				if (anyone) {
					g.cabinLocks.put(cabinId, lock.lockedNow());
					event(g, "cabin_locked", Map.of("cabin", cabinId, "cause", "window_over"));
				} else {
					g.cabinLocks.remove(cabinId);
				}
				persist(g);
				notifyChanged(g, "cabin");
			}
		} catch (RuntimeException ex) {
			log.error("Trahison game {}: cabin window failed", id, ex);
		}
	}

	/**
	 * VOTE: a player of the group inside his own cabin closes it at once ({@code close_cabin}), without waiting for the
	 * partner of a pair: the partner is then locked out. Nothing to do when it is already locked or his group voted.
	 */
	public Game closeCabin(String hub, UUID uuid) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerAction(g, uuid, Phase.VOTE);
			requireFree(g, p);
			LayoutContent plan = planOf(g);
			RoundState r = g.currentRound();
			Cabin inside = Cabins.ofZone(plan, g.players.get(p).zone).orElseThrow(
				() -> new TrahisonException(TrahisonException.NOT_IN_CABIN, "not inside a cabin"));
			Cabin mine = myCabin(plan, r, p).orElseThrow(
				() -> new TrahisonException(TrahisonException.WRONG_CABIN, "the plan has no cabin for him"));
			if (!inside.id().equals(mine.id())) {
				throw new TrahisonException(TrahisonException.WRONG_CABIN, "that is not his cabin");
			}
			Side side = sideOf(plan, r, mine);
			if (side == null || voted(r, side)) {
				return g;
			}
			CabinLock lock = g.cabinLocks.get(mine.id());
			if (lock != null && !lock.waiting()) {
				return g;
			}
			List<Integer> expected = lock == null ? humansOf(g, side) : lock.expected();
			g.cabinLocks.put(mine.id(), new CabinLock(lock == null ? p : lock.first(), 0, true, false, expected));
			event(g, "close_cabin", Map.of("player", p, "cabin", mine.id()));
			makeOfferIfDue(g, p);
			persist(g);
			notifyChanged(g, "cabin");
			return g;
		}
	}

	/** Refuses a vote from outside his own locked cabin (humans only; bots and {@code cabins_required=false} are exempt). */
	private void requireOwnClosedCabin(Game g, RoundState r, int p) {
		if (!g.settings.cabinsRequired || g.players.get(p).bot) {
			return;
		}
		LayoutContent plan = planOf(g);
		if (plan.cabins().isEmpty()) {
			return; // no map yet (hub without a plan): nothing to close, the vote stays free
		}
		Cabin inside = Cabins.ofZone(plan, g.players.get(p).zone).orElseThrow(
			() -> new TrahisonException(TrahisonException.NOT_IN_CABIN, "a vote is cast from inside a cabin"));
		Cabin mine = myCabin(plan, r, p).orElseThrow(
			() -> new TrahisonException(TrahisonException.WRONG_CABIN, "the plan has no cabin for him"));
		if (!inside.id().equals(mine.id())) {
			throw new TrahisonException(TrahisonException.WRONG_CABIN, "that is not his cabin");
		}
		CabinLock lock = g.cabinLocks.get(mine.id());
		if (lock == null || !lock.locked()) {
			throw new TrahisonException(TrahisonException.CABIN_NOT_CLOSED, "the cabin is not locked yet");
		}
		if (mine.door() != null && g.lockedDoors.containsKey(mine.door())) {
			throw new TrahisonException(TrahisonException.DOOR_LOCKED, "the cabin was locked from outside");
		}
	}

	/** The group of player {@code p} has voted: its cabin opens for those inside (D5), and frees itself when they left. */
	private void releaseCabin(Game g, LayoutContent plan, RoundState r, int p) {
		Cabin mine = myCabin(plan, r, p).orElse(null);
		CabinLock lock = mine == null ? null : g.cabinLocks.get(mine.id());
		if (lock == null || lock.released()) {
			return;
		}
		g.cabinLocks.put(mine.id(), lock.releasedNow());
		event(g, "cabin_released", Map.of("cabin", mine.id()));
		updateCabins(g); // nobody left inside: free at once
	}

	/** The player votes for his wrist bracelet's side of his group. A pair has one vote: its first voter's. */
	public Game vote(String hub, UUID uuid, Vote side) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerAction(g, uuid, Phase.VOTE);
			if (side == null) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "vote required");
			}
			requireFree(g, p); // T7.2: a tied player does not vote (ALLY by default)
			if (side == Vote.BETRAY && g.roleOf(p) == Role.GUARDIAN) {
				// T7.1: refused to him alone (errors go to the sender); nothing public tells that a guardian tried
				throw new TrahisonException(TrahisonException.CANNOT_BETRAY, "he cannot betray");
			}
			RoundState r = g.currentRound();
			requireOwnClosedCabin(g, r, p);
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
			releaseCabin(g, planOf(g), r, p);
			persist(g);
			notifyChanged(g, "vote");
			return g;
		}
	}

	/**
	 * In FINAL only; the wrist bracelet must read 9 or more and the player be alive (T2.5). When the plan has a final door
	 * with a pupitre zone (its {@code threshold}), he must stand in it (bots excepted; a real plan always has one, D17). The
	 * door then opens for {@code final_door_seconds} (60 by default, D18/D19) for every living player who reads 9 (it stays
	 * closed for the others); the window always runs its full length, even when all of them have crossed. When the time is up
	 * it closes for ever and the game ends, {@code winners} being the ones who walked through. A plan without a final door has
	 * the same window with no door to cross (D21): every opener wins at its end. Pressing the pupitre again while the door
	 * is open does nothing.
	 */
	public Game openFinalDoor(String hub, UUID uuid) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerAction(g, uuid, Phase.FINAL);
			if (!Outcome.canOpenFinalDoor(g.bracelets.get(p).points, g.players.get(p).alive)) {
				throw new TrahisonException(TrahisonException.CANNOT_OPEN_FINAL, "the bracelet does not read 9");
			}
			if (g.finalDoor != null) {
				return g; // already open
			}
			LayoutDoor door = finalDoorOf(planOf(g));
			String pupitre = door == null || door.threshold() == null || door.threshold().isBlank() ? null : door.threshold();
			if (pupitre != null && !g.players.get(p).bot && !pupitre.equals(g.players.get(p).zone)) {
				throw new TrahisonException(TrahisonException.NOT_AT_FINAL_DOOR, "the final door opens from its pupitre");
			}
			List<Integer> openers = Outcome.finalDoorOpeners(g.points(), g.aliveFlags());
			// D21: a plan without a final door has the same window, with no door to walk through: every opener wins at the end.
			long until = clock.millis() + Math.max(1, g.settings.finalDoorSeconds) * 1000L;
			List<Integer> crossed = new ArrayList<>();
			for (int o : openers) {
				if (g.players.get(o).bot) {
					crossed.add(o); // a bot has no body to walk: he goes through with the door
				}
			}
			FinalDoorWindow window = new FinalDoorWindow(until, openers, p, crossed);
			g.finalDoor = window;
			g.deadline = Instant.ofEpochMilli(until); // the clock of the stage now counts the door's time
			g.remainingMs = until - clock.millis();
			scheduleTimer(g);
			scheduleFinalEnd(g, window);
			event(g, "final_door", Map.of("opener", p, "openers", openers, "until", until));
			persist(g);
			notifyChanged(g, "final_door");
			return g;
		}
	}

	/** The plan's final door, or null. */
	private static LayoutDoor finalDoorOf(LayoutContent plan) {
		for (LayoutDoor d : plan.doors()) {
			if ("final".equals(d.type())) {
				return d;
			}
		}
		return null;
	}

	private void scheduleFinalEnd(Game g, FinalDoorWindow window) {
		GameScheduler.Handle old = finalTimers.remove(g.id);
		if (old != null) {
			old.cancel();
		}
		UUID id = g.id;
		finalTimers.put(id, scheduler.schedule(Instant.ofEpochMilli(window.untilMs()), () -> onFinalEnd(id, window)));
	}

	private void onFinalEnd(UUID id, FinalDoorWindow window) {
		Game g = byId.get(id);
		if (g == null) {
			return;
		}
		try {
			synchronized (g) {
				if (g.finalDoor == null || g.phase != Phase.FINAL) {
					return; // closed or ended since
				}
				closeFinalDoor(g, "timeout");
				persist(g);
			}
		} catch (RuntimeException ex) {
			log.error("Trahison game {}: final door end failed", id, ex);
		}
	}

	/**
	 * The final door closes for ever and the game ends. The winners are the openers who walked through it (standing in the
	 * zone beyond it, {@code zone_b} of the door) and the bots among them; with no door, or no zone beyond it in the plan,
	 * every opener (D21).
	 */
	private void closeFinalDoor(Game g, String cause) {
		FinalDoorWindow window = g.finalDoor;
		LayoutDoor door = finalDoorOf(planOf(g));
		java.util.TreeSet<Integer> through = new java.util.TreeSet<>();
		for (int o : window.openers()) {
			boolean beyond = door != null && door.zoneB() != null && door.zoneB().equals(g.players.get(o).zone);
			if (door == null || door.zoneB() == null || beyond || window.crossed().contains(o)) {
				through.add(o);
			}
		}
		g.winners = new ArrayList<>(through);
		event(g, "final_door_closed", Map.of("cause", cause, "winners", g.winners));
		finish(g, "final_door");
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
				g.doorWindows.forEach((door, window) -> scheduleWindowEnd(g, door, window));
				if (g.finalDoor != null) {
					scheduleFinalEnd(g, g.finalDoor);
				}
				g.cabinLocks.forEach((cabin, lock) -> {
					if (lock.waiting()) {
						scheduleCabinWindow(g, cabin, lock);
					}
				});
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
				if (endIfGameMasterKilled(g)) { // D12: the doors killed a playing game master
					break;
				}
				enterPhase(g, Phase.ROOMS);
				if (rooms != null) {
					// After the stage view went out, so a client gets ROOMS first, then its room.
					rooms.startRound(g, g.currentRound(), planOf(g));
				}
			}
			case ROOMS -> {
				if (penalizeUnsolvedRooms(g)) { // T3.6: a bracelet at 0 killed a playing game master
					break;
				}
				enterPhase(g, Phase.FREE_TIME);
			}
			case FREE_TIME -> enterPhase(g, Phase.VOTE);
			case VOTE -> {
				resolveVotes(g);
				if (endIfGameMasterKilled(g)) { // D12: a bracelet at 0 killed a playing game master
					break;
				}
				enterPhase(g, Phase.RESULTS);
				leakAfterResults(g);
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
				if (g.finalDoor != null) { // the door was open: its time is over (or the game master closes it)
					closeFinalDoor(g, cause);
					break;
				}
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

	/**
	 * D12: the game master who plays is a secret role; when the other participants kill him (any death of a playing game
	 * master), all the others win and the game ends. THE HOOK OF EVERY DEATH PATH: call it after any change of a player's
	 * {@code alive} to false. Today: the doors that close ({@link #closeDoors}) and the bracelets at 0 points
	 * ({@link #resolveVotes}), both reached from {@link #advance}. T6 (killing between players) must call it right after
	 * its kill. Returns true when the game ended by it. The caller persists.
	 */
	private boolean endIfGameMasterKilled(Game g) {
		if (g.phase == Phase.ENDED || g.phase == Phase.LOBBY) {
			return false;
		}
		List<Integer> masters = g.playingGameMasters();
		// D50: a game master who dies of his own powers (his bracelet set to 0 by himself) does not hand the others the win.
		masters = masters.stream().filter(m -> !g.selfDestined.contains(m)).toList();
		if (!Outcome.gameMasterKilled(masters, g.aliveFlags())) {
			return false;
		}
		g.winners = new ArrayList<>(Outcome.winnersWhenGameMasterKilled(masters, g.aliveFlags())); // D44: the living others
		event(g, "game_master_killed", Map.of("winners", g.winners));
		finish(g, "game_master_killed");
		return true;
	}

	/** Draws the assignment of a new round (or final loop) from the game seed and the history. */
	private void startRound(Game g, boolean finalLoop) {
		// The rooms of the previous rounds stay (D8: the free time visits every room opened so far).
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
	static boolean consistent(RoundState r, Split s) {
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
	 * bracelet goes through a different door in each split); with no pass at all the seed picks. A living player who passed
	 * no door dies (D2: he becomes a ghost, as at 0 points). The split then defines the groups of rooms and votes, the dead
	 * included.
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
				markDead(g, i, "doors", true);
				r.diedAtDoors.add(i);
			}
		}
		event(g, "doors_closed", Map.of("split", r.splitIndex, "died", r.diedAtDoors));
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
		applyRoleAndItemPoints(g, r, total); // T7: shield, infiltrator, gambler
		int[] after = Scoring.applyAll(g.points(), total);
		for (int i = 0; i < PLAYERS; i++) {
			g.bracelets.get(i).points = after[i];
		}
		r.deltas = total;
		g.storeHistory(g.history().after(r.assignment(), split));
		List<Integer> dead = Outcome.newlyDead(after, g.aliveFlags());
		for (int i : dead) {
			markDead(g, i, "points", true);
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
		g.objectivesMet = new ArrayList<>(Outcome.objectivesMet(drawnRoles(g), g.aliveFlags(), g.winners,
			new java.util.HashSet<>(g.roleState.infiltratorBetrayed))); // T7.1: the roles are revealed with the result
		g.finalDoor = null;
		GameScheduler.Handle finalEnd = finalTimers.remove(g.id);
		if (finalEnd != null) {
			finalEnd.cancel();
		}
		g.doorOverrides.clear();
		g.doorWindows.clear();
		g.cabinLocks.clear();
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
		g.doorOverrides.clear(); // a manual door holds for the stage only
		g.doorWindows.clear();
		g.cabinLocks.clear(); // every cabin reopens when VOTE ends (and starts free)
		g.listening.clear(); // the ear lasts for one vote
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
		if (phase == Phase.VOTE) {
			prepareOffer(g);
		}
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

	// ------------------------------------------------------------------ secret roles and hidden items (T7)

	/** Range of the knife, of the bonds and of untying (T7.2, "à moins de 3 blocs"). */
	public static final double ITEM_RANGE = 3.0;
	/** How near a hiding spot a player must stand to search it. */
	public static final double SEARCH_RANGE = 3.0;
	/** An untying that was started and not finished is forgotten after this long. */
	private static final long UNTYING_FORGET_MS = 60_000;
	/** Longest letter, in characters. */
	public static final int LETTER_MAX = 120;

	/** The roles of the drawable kind as seats (the game master who plays is not in it: D12 derives his from the settings). */
	private static Map<Integer, Role> drawnRoles(Game g) {
		Map<Integer, Role> out = new java.util.TreeMap<>();
		for (int i = 0; i < g.players.size(); i++) {
			Role r = Role.parse(g.players.get(i).role);
			if (r != null && r != Role.GAME_MASTER) {
				out.put(i, r);
			}
		}
		return out;
	}

	/**
	 * Start of the game: draws the roles (never for a bot nor for the game master who plays, who already holds his own secret
	 * role, D12) and hides the items in the plan's hiding spots. Both come from the game seed.
	 */
	private void drawRolesAndItems(Game g) {
		List<Integer> eligible = new ArrayList<>();
		for (int i = 0; i < g.players.size(); i++) {
			PlayerState p = g.players.get(i);
			p.role = null;
			p.items = new ArrayList<>();
			if (!p.bot && !g.gameMasters.contains(p.uuid)) {
				eligible.add(i);
			}
		}
		Map<Integer, Role> drawn = Roles.draw(g.seed, g.settings.activeRoles(), g.settings.roleCount, eligible);
		drawn.forEach((seat, role) -> g.players.get(seat).role = role.name());
		g.roleState = new RoleState();
		List<String> spots = planOf(g).hidingSpots().stream().map(s -> s.id()).toList();
		ItemDraw.Result items = ItemDraw.draw(g.seed, g.settings.items, spots);
		g.hiddenItems = new LinkedHashMap<>();
		items.placed().forEach((spot, item) -> g.hiddenItems.put(spot, item.name()));
		g.unplacedItems = new ArrayList<>(items.unplaced().stream().map(Item::name).toList());
		g.lockedDoors = new LinkedHashMap<>();
		g.listening = new LinkedHashMap<>();
		g.untying = new LinkedHashMap<>();
		g.objectivesMet = new ArrayList<>();
		event(g, "roles", Map.of("roles", g.players.stream().map(p -> String.valueOf(p.role)).toList(),
			"hidden", g.hiddenItems, "unplaced", g.unplacedItems));
	}

	/** T7.1: each holder of a role learns it, in private, as the prologue starts (the game master who plays included). */
	private void whisperRoles(Game g) {
		for (int i = 0; i < g.players.size(); i++) {
			Role r = g.roleOf(i);
			if (r != null) {
				whisper(g, i, "phantasmon_events.trahison.say.role." + r.name().toLowerCase(java.util.Locale.ROOT), List.of());
			}
		}
	}

	private void whisper(Game g, int seat, String key, List<String> args) {
		for (GameListener l : listeners) {
			try {
				l.whispered(g, seat, key, args);
			} catch (RuntimeException ex) {
				log.warn("Trahison listener failed on whispered", ex);
			}
		}
	}

	private static boolean restrained(PlayerState p, long now) {
		return p.restrainedUntil != null && p.restrainedUntil > now;
	}

	/** Refuses an action of a player who is tied up (he can look and talk, nothing else). */
	private void requireFree(Game g, int p) {
		if (restrained(g.players.get(p), clock.millis())) {
			throw new TrahisonException(TrahisonException.RESTRAINED, "he is tied up");
		}
	}

	/** Forgets the bonds and the locks whose time is over; true when something changed. */
	private boolean pruneItems(Game g) {
		long now = clock.millis();
		boolean changed = false;
		for (PlayerState p : g.players) {
			if (p.restrainedUntil != null && p.restrainedUntil <= now) {
				p.restrainedUntil = null;
				changed = true;
			}
		}
		changed |= g.lockedDoors.values().removeIf(l -> l.untilMs() <= now);
		return changed;
	}

	/** Tells the views when a bond or a lock ends by itself. */
	private void scheduleItemEnd(Game g, long atMs) {
		UUID id = g.id;
		scheduler.schedule(Instant.ofEpochMilli(atMs), () -> {
			Game game = byId.get(id);
			if (game == null) {
				return;
			}
			synchronized (game) {
				if (game.phase == Phase.ENDED || !pruneItems(game)) {
					return;
				}
				persist(game);
				notifyChanged(game, "door");
			}
		});
	}

	private static double distance(PlayerState a, PlayerState b) {
		if (a.x == null || b.x == null || a.z == null || b.z == null) {
			return Double.POSITIVE_INFINITY;
		}
		double dy = a.y == null || b.y == null ? 0 : a.y - b.y;
		return Math.sqrt((a.x - b.x) * (a.x - b.x) + dy * dy + (a.z - b.z) * (a.z - b.z));
	}

	/** Same zone and within {@link #ITEM_RANGE} blocks: the knife does not go through a closed door (zones differ). */
	private static boolean within(PlayerState a, PlayerState b) {
		return java.util.Objects.equals(a.zone, b.zone) && distance(a, b) <= ITEM_RANGE;
	}

	private static double distanceToBox(PlayerState p, com.mystaria.phantasmon_backend.events.trahison.layout.Box box) {
		if (p.x == null || p.z == null) {
			return Double.POSITIVE_INFINITY;
		}
		double y = p.y == null ? box.minY() : p.y;
		double dx = Math.max(Math.max(box.minX() - p.x, 0), p.x - (box.maxX() + 1));
		double dy = Math.max(Math.max(box.minY() - y, 0), y - (box.maxY() + 1));
		double dz = Math.max(Math.max(box.minZ() - p.z, 0), p.z - (box.maxZ() + 1));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** A door (ordinary or a cabin's) the lock and the pass work on: {@code where} is a door id or a cabin id. */
	private record Lockable(LayoutDoor door, Cabin cabin) {
	}

	private static Lockable lockableOf(LayoutContent plan, String where) {
		if (where == null || where.isBlank()) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "a door or a cabin is required");
		}
		for (Cabin c : plan.cabins()) {
			if (c.id().equals(where) && c.door() != null) {
				for (LayoutDoor d : plan.doors()) {
					if (d.id().equals(c.door())) {
						return new Lockable(d, c);
					}
				}
			}
		}
		for (LayoutDoor d : plan.doors()) {
			if (d.id().equals(where)) {
				if (!"plain".equals(d.type()) && !"cabin".equals(d.type())) {
					throw new TrahisonException(TrahisonException.NOT_LOCKABLE, "only an ordinary door or a cabin");
				}
				Cabin cabin = plan.cabins().stream().filter(c -> d.id().equals(c.door())).findFirst().orElse(null);
				return new Lockable(d, cabin);
			}
		}
		throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "no such door or cabin");
	}

	/** From the outside, next to the door: out of the cabin, and within range of the door's block. */
	private static void requireOutsideAndNear(PlayerState p, Lockable l) {
		if (l.cabin() != null && l.cabin().zone() != null && l.cabin().zone().equals(p.zone)) {
			throw new TrahisonException(TrahisonException.TARGET_TOO_FAR, "from the outside of the cabin");
		}
		if (distanceToBox(p, l.door().block()) > ITEM_RANGE + 1) {
			throw new TrahisonException(TrahisonException.TARGET_TOO_FAR, "too far from the door");
		}
	}

	private int targetOf(Game g, int p, Integer target, boolean mustBeAlive) {
		if (target == null || target < 0 || target >= g.players.size() || target == p) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "target: another player's seat");
		}
		if (mustBeAlive && !g.players.get(target).alive) {
			throw new TrahisonException(TrahisonException.DEAD, "he is already dead");
		}
		return target;
	}

	/**
	 * T7.2: a living player searches the hiding spot {@code spot} of the plan: within {@link #SEARCH_RANGE} blocks of it, in the
	 * free time (or once his own room is done). What lay there goes into his personal items, which only he sees.
	 */
	public Game search(String hub, UUID uuid, String spot) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerActionAnyPhase(g, uuid);
			requireFree(g, p);
			if (g.phase != Phase.FREE_TIME && !canRoam(g, p)) {
				throw new TrahisonException(TrahisonException.CANNOT_SEARCH_NOW, "not now");
			}
			var found = planOf(g).hidingSpots().stream().filter(s -> s.id().equals(spot)).findFirst().orElseThrow(
				() -> new TrahisonException(TrahisonException.NO_SPOT, "no such hiding spot"));
			PlayerState me = g.players.get(p);
			if (me.x == null || me.z == null || Math.hypot(me.x - (found.position().x() + 0.5), me.z - (found.position().z() + 0.5)) > SEARCH_RANGE
					|| me.y != null && Math.abs(me.y - found.position().y()) > SEARCH_RANGE) {
				throw new TrahisonException(TrahisonException.SPOT_TOO_FAR, "too far from the hiding spot");
			}
			String item = g.hiddenItems.remove(found.id());
			if (item == null) {
				throw new TrahisonException(TrahisonException.SPOT_EMPTY, "nothing there");
			}
			me.items.add(item);
			event(g, "item_found", Map.of("player", p, "spot", found.id(), "item", item));
			persist(g);
			notifyChanged(g, "items");
			return g;
		}
	}

	/**
	 * T7.2: a player uses one of his items. {@code target}: a seat (knife, bonds, letter); {@code where}: a door id or a cabin id
	 * (lock, pass, ear); {@code text}: the letter. The knife kills (T6), the bonds tie, the lock shuts a plain door or a cabin from
	 * the outside, the pass reopens a locked one (from anywhere, even from inside), the ear is noted for the vote's sound (T5.4), the
	 * letter is whispered to its target, anonymously. The shield works by itself and cannot be used. The item is consumed once
	 * its effect took place; a refusal keeps it.
	 */
	public Game useObject(String hub, UUID uuid, Item item, Integer target, String where, String text) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerActionAnyPhase(g, uuid);
			requireFree(g, p);
			pruneItems(g);
			PlayerState me = g.players.get(p);
			if (item == null) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "item required");
			}
			if (!me.items.contains(item.name())) {
				throw new TrahisonException(TrahisonException.NO_SUCH_SECRET_ITEM, "he does not carry that item");
			}
			long now = clock.millis();
			LayoutContent plan = planOf(g);
			switch (item) {
				case SHIELD -> throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "the shield works by itself");
				case KNIFE -> {
					int t = targetOf(g, p, target, true);
					if (!within(me, g.players.get(t))) {
						throw new TrahisonException(TrahisonException.TARGET_TOO_FAR, "too far, or behind a door");
					}
					me.items.remove(item.name());
					event(g, "item_used", Map.of("player", p, "item", item.name(), "target", t));
					kill(hub, t, "knife"); // persists and notifies; a playing game master killed ends the game (D12)
					return g;
				}
				case BONDS -> {
					int t = targetOf(g, p, target, true);
					PlayerState victim = g.players.get(t);
					if (restrained(victim, now)) {
						throw new TrahisonException(TrahisonException.ALREADY_RESTRAINED, "already tied up");
					}
					if (!within(me, victim)) {
						throw new TrahisonException(TrahisonException.TARGET_TOO_FAR, "too far, or behind a door");
					}
					victim.restrainedUntil = now + g.settings.bondsSeconds * 1000L;
					g.untying.remove(t);
					scheduleItemEnd(g, victim.restrainedUntil);
				}
				case LOCK -> {
					Lockable l = lockableOf(plan, where);
					requireOutsideAndNear(me, l);
					if (g.lockedDoors.containsKey(l.door().id())) {
						throw new TrahisonException(TrahisonException.ALREADY_LOCKED, "already locked");
					}
					long until = now + g.settings.lockSeconds * 1000L;
					g.lockedDoors.put(l.door().id(), new LockedDoor(l.door().id(), p, until));
					scheduleItemEnd(g, until);
				}
				case PASS -> {
					Lockable l = lockableOf(plan, where);
					if (g.lockedDoors.remove(l.door().id()) == null) {
						throw new TrahisonException(TrahisonException.NOT_LOCKED, "that door is not locked");
					}
				}
				case EAR -> {
					if (g.phase != Phase.VOTE) {
						throw new TrahisonException(TrahisonException.WRONG_PHASE, "the ear listens during a vote");
					}
					Lockable l = lockableOf(plan, where);
					if (l.cabin() == null) {
						throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "a cabin is required");
					}
					g.listening.put(p, l.cabin().id());
				}
				case LETTER -> {
					int t = targetOf(g, p, target, false);
					String clean = text == null ? "" : text.replaceAll("[\\p{Cntrl}]", " ").trim();
					if (clean.isEmpty() || clean.length() > LETTER_MAX) {
						throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "a letter holds 1 to " + LETTER_MAX + " characters");
					}
					if (!g.players.get(t).bot) {
						whisper(g, t, "phantasmon_events.trahison.say.letter", List.of(clean));
					}
					event(g, "letter", Map.of("from", p, "to", t, "text", clean));
				}
			}
			me.items.remove(item.name());
			event(g, "item_used", Map.of("player", p, "item", item.name()));
			persist(g);
			notifyChanged(g, "items");
			return g;
		}
	}

	/**
	 * T7.2: a player reopens a door locked by the lock item without a pass: from the outside, next to the door.
	 */
	public Game unlock(String hub, UUID uuid, String where) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerActionAnyPhase(g, uuid);
			requireFree(g, p);
			pruneItems(g);
			Lockable l = lockableOf(planOf(g), where);
			if (!g.lockedDoors.containsKey(l.door().id())) {
				throw new TrahisonException(TrahisonException.NOT_LOCKED, "that door is not locked");
			}
			requireOutsideAndNear(g.players.get(p), l);
			g.lockedDoors.remove(l.door().id());
			event(g, "unlock", Map.of("player", p, "door", l.door().id()));
			persist(g);
			notifyChanged(g, "door");
			return g;
		}
	}

	/**
	 * T7.2: a player unties another one. The first call starts it; he must stay next to him (same zone, 3 blocks) for
	 * {@link Settings#untieSeconds} and call again: the rope then comes off. Too early, the call is refused and nothing is lost.
	 */
	public Game untie(String hub, UUID uuid, int target) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerActionAnyPhase(g, uuid);
			requireFree(g, p);
			pruneItems(g);
			int t = targetOf(g, p, target, true);
			PlayerState victim = g.players.get(t);
			if (!restrained(victim, clock.millis())) {
				throw new TrahisonException(TrahisonException.NOT_RESTRAINED, "he is not tied up");
			}
			if (!within(g.players.get(p), victim)) {
				throw new TrahisonException(TrahisonException.TARGET_TOO_FAR, "too far, or behind a door");
			}
			long now = clock.millis();
			Untying u = g.untying.get(t);
			if (u == null || u.by() != p || now - u.startedMs() > UNTYING_FORGET_MS) {
				g.untying.put(t, new Untying(p, now));
				persist(g);
				return g;
			}
			if (now - u.startedMs() < g.settings.untieSeconds * 1000L) {
				throw new TrahisonException(TrahisonException.UNTYING, "still untying");
			}
			victim.restrainedUntil = null;
			g.untying.remove(t);
			event(g, "untied", Map.of("by", p, "player", t));
			persist(g);
			notifyChanged(g, "items");
			return g;
		}
	}

	/**
	 * T7.1, the seer: once in the game, during VOTE, he learns the vote already cast by one side of a group that is not his
	 * ({@code group} 0..2, {@code pair} true for the pair). Refused, and not spent, when that side has not voted yet.
	 */
	public Game foresee(String hub, UUID uuid, int group, boolean pair) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerAction(g, uuid, Phase.VOTE);
			requireFree(g, p);
			if (g.roleOf(p) != Role.SEER) {
				throw new TrahisonException(TrahisonException.NOT_YOUR_ROLE, "he is not the seer");
			}
			if (g.roleState.seerUsed) {
				throw new TrahisonException(TrahisonException.POWER_USED, "already used");
			}
			RoundState r = g.currentRound();
			if (group < 0 || group >= 3 || r == null || r.splitIndex == null) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "group 0..2");
			}
			boolean mine = r.groupOf(p) == group && r.split().group(Door.ALL.get(group)).pair().contains(p) == pair;
			if (mine) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "not his own side");
			}
			Vote cast = pair ? r.pairVote[group] : r.soloVote[group];
			if (cast == null) {
				throw new TrahisonException(TrahisonException.NOT_VOTED_YET, "that side has not voted yet");
			}
			g.roleState.seerUsed = true;
			g.roleState.seerGroup = group;
			g.roleState.seerSide = pair ? "PAIR" : "SOLO";
			g.roleState.seerVote = cast.name();
			event(g, "foresee", Map.of("player", p, "group", group, "pair", pair));
			persist(g);
			notifyChanged(g, "role");
			return g;
		}
	}

	/**
	 * T7.1, the gambler: before the vote of the opposite side of his group, he bets on what it will be. One bet per round.
	 * A right bet gives +1 point at the results, once in the game.
	 */
	public Game bet(String hub, UUID uuid, Vote vote) {
		Game g = require(hub);
		synchronized (g) {
			int p = playerAction(g, uuid, Phase.VOTE);
			requireFree(g, p);
			if (g.roleOf(p) != Role.GAMBLER) {
				throw new TrahisonException(TrahisonException.NOT_YOUR_ROLE, "he is not the gambler");
			}
			if (vote == null) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "vote required");
			}
			RoundState r = g.currentRound();
			if (r == null || r.splitIndex == null) {
				throw new TrahisonException(TrahisonException.WRONG_PHASE, "no groups yet");
			}
			if (g.roleState.bets.containsKey(r.number)) {
				throw new TrahisonException(TrahisonException.ALREADY_BET, "already bet this round");
			}
			int k = r.groupOf(p);
			boolean pair = r.split().group(Door.ALL.get(k)).pair().contains(p);
			if ((pair ? r.soloVote[k] : r.pairVote[k]) != null) {
				throw new TrahisonException(TrahisonException.ALREADY_VOTED, "the opponent already voted");
			}
			g.roleState.bets.put(r.number, vote.name());
			event(g, "bet", Map.of("player", p, "round", r.number));
			persist(g);
			notifyChanged(g, "role");
			return g;
		}
	}

	/**
	 * What the roles and the items add to the points of a resolved round (T7): the shield cancels a -2, the infiltrator gets his
	 * point for betraying in round 1, the gambler his for a right bet. {@code total} is changed in place.
	 */
	private void applyRoleAndItemPoints(Game g, RoundState r, int[] total) {
		for (int i = 0; i < PLAYERS; i++) {
			PlayerState p = g.players.get(i);
			if (total[i] == -2 && p.items.remove(Item.SHIELD.name())) {
				total[i] = 0;
				event(g, "shield", Map.of("player", i));
			}
		}
		Split split = r.split();
		applyOfferBonus(g, r, split, total);
		for (int i = 0; i < PLAYERS; i++) {
			Role role = g.roleOf(i);
			if (role == null) {
				continue;
			}
			int k = r.groupOf(i);
			boolean pair = split.group(Door.ALL.get(k)).pair().contains(i);
			if (role == Role.INFILTRATOR && r.number == 1 && !r.finalLoop) {
				boolean betrayed = pair ? r.pairVote[k] == Vote.BETRAY && r.pairVoter[k] == i : r.soloVote[k] == Vote.BETRAY;
				if (betrayed) {
					total[i] += Roles.INFILTRATOR_BONUS;
					g.roleState.infiltratorBetrayed.add(i);
				}
			} else if (role == Role.GAMBLER && !g.roleState.gamblerPaid && g.roleState.bets.containsKey(r.number)) {
				Vote opponent = Scoring.effective(pair ? r.soloVote[k] : r.pairVote[k]);
				if (opponent.name().equals(g.roleState.bets.get(r.number))) {
					total[i] += Roles.GAMBLER_BONUS;
					g.roleState.gamblerPaid = true;
				}
			}
		}
	}


	// ------------------------------------------------------------------ T7.3 automatic temptations

	/** Humans still in the game: the only ones who can be whispered to (a bot has nobody to tell). */
	private static List<Integer> livingHumans(Game g) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < g.players.size(); i++) {
			PlayerState p = g.players.get(i);
			if (p.alive && !p.bot && p.uuid != null) {
				out.add(i);
			}
		}
		return out;
	}

	/** A vote starts: draws who will be offered the extra point (a pair's offer goes to the one who closes the cabin). */
	private void prepareOffer(Game g) {
		RoundState r = g.currentRound();
		g.temptations = new Temptations.State();
		if (r == null || !g.settings.offers) {
			return;
		}
		g.temptations.offerRound = r.number;
		g.temptations.offerSeat = Temptations.pickOfferee(g.seed, r.number + (r.finalLoop ? 100 : 0), livingHumans(g));
		if (g.temptations.offerSeat < 0) {
			return;
		}
		LayoutContent plan = planOf(g);
		if (!g.settings.cabinsRequired || plan.cabins().isEmpty()) {
			makeOffer(g, g.temptations.offerSeat, false); // no cabin to close: the offer comes with the vote
		}
		persist(g);
	}

	/** The offeree has closed his cabin: the offer reaches him now. */
	private void makeOfferIfDue(Game g, int p) {
		Temptations.State t = g.temptations;
		if (g.settings.offers && !t.offerMade && t.offerSeat == p) {
			makeOffer(g, p, false);
		}
	}

	private void makeOffer(Game g, int seat, boolean forced) {
		Temptations.State t = g.temptations;
		RoundState r = g.currentRound();
		t.offerSeat = seat;
		t.offerRound = r == null ? 0 : r.number;
		t.offerMade = true;
		t.offerForced = forced;
		event(g, "offer", Map.of("player", seat, "round", t.offerRound, "forced", forced));
		whisper(g, seat, "phantasmon_events.trahison.say.offer", List.of(String.valueOf(Temptations.OFFER_BONUS)));
		announce(g, "offer_made", List.of());
	}

	/** The game master hands an offer to a seat now (a vote must be running). */
	public Game offer(String hub, int seat) {
		Game g = require(hub);
		synchronized (g) {
			if (g.phase != Phase.VOTE) {
				throw new TrahisonException(TrahisonException.WRONG_PHASE, "an offer is made during a vote");
			}
			PlayerState p = player(g, seat);
			if (!p.alive || p.bot) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "he cannot receive an offer");
			}
			makeOffer(g, seat, true);
			persist(g);
			notifyChanged(g, "offer");
			return g;
		}
	}

	/** An accepted offer (the offeree betrayed with his own vote) is worth one more point. */
	private void applyOfferBonus(Game g, RoundState r, Split split, int[] total) {
		Temptations.State t = g.temptations;
		if (!t.offerMade || t.offerSeat < 0 || t.offerRound != r.number) {
			return;
		}
		int i = t.offerSeat;
		int k = r.groupOf(i);
		boolean pair = split.group(Door.ALL.get(k)).pair().contains(i);
		boolean betrayed = pair ? r.pairVote[k] == Vote.BETRAY && r.pairVoter[k] == i : r.soloVote[k] == Vote.BETRAY;
		if (betrayed) {
			total[i] += Temptations.OFFER_BONUS;
			event(g, "offer_taken", Map.of("player", i, "round", r.number));
		}
		t.offerMade = false; // paid once
		t.offerSeat = -1;
	}

	/** After the results: one living human learns a true thing taken from the log of the round. */
	private void leakAfterResults(Game g) {
		RoundState r = g.currentRound();
		if (!g.settings.leaks || r == null || g.phase != Phase.RESULTS) {
			return;
		}
		int from = 0;
		long voteStart = 0;
		for (int i = 0; i < g.log.size(); i++) {
			LogEntry e = g.log.get(i);
			if ("phase".equals(e.type) && "VOTE".equals(e.data.get("phase"))) {
				from = i;
				voteStart = e.at.toEpochMilli();
			}
		}
		List<Temptations.Fact> facts = new ArrayList<>();
		for (int i = from; i < g.log.size(); i++) {
			LogEntry e = g.log.get(i);
			if (!(e.data.get("player") instanceof Number n) || n.intValue() < 0 || n.intValue() >= g.players.size()) {
				continue;
			}
			int who = n.intValue();
			String name = g.players.get(who).name;
			long seconds = Math.max(0, (e.at.toEpochMilli() - voteStart) / 1000);
			switch (e.type) {
				case "vote" -> facts.add(new Temptations.Fact("vote_time", who, List.of(name, String.valueOf(seconds))));
				case "close_cabin" -> facts.add(new Temptations.Fact("cabin_closed", who, List.of(name, String.valueOf(seconds))));
				case "cabin_enter" -> facts.add(new Temptations.Fact("cabin_try", who, List.of(name)));
				default -> {
				}
			}
		}
		for (LogEntry e : g.log) { // objects picked up during the whole game
			if ("item_found".equals(e.type) && e.data.get("player") instanceof Number n && n.intValue() >= 0 && n.intValue() < g.players.size()) {
				facts.add(new Temptations.Fact("item_found", n.intValue(), List.of(g.players.get(n.intValue()).name)));
			}
		}
		Temptations.Leak leak = Temptations.pickLeak(g.seed, r.number + (r.finalLoop ? 100 : 0), livingHumans(g), facts);
		if (leak == null) {
			return;
		}
		event(g, "leak", Map.of("player", leak.receiver(), "kind", leak.fact().kind(), "about", leak.fact().about(), "round", r.number));
		whisper(g, leak.receiver(), "phantasmon_events.trahison.say.leak." + leak.fact().kind(), leak.fact().args());
		persist(g);
	}

	/** The first group out of its room is announced to everybody. */
	private void announceRace(Game g, RoundState r, int group) {
		List<String> names = new ArrayList<>();
		DoorGroup dg = r.split().group(Door.ALL.get(group));
		for (int i : dg.pair()) {
			names.add(g.players.get(i).name);
		}
		names.add(g.players.get(dg.solo()).name);
		event(g, "race", Map.of("group", group, "round", r.number));
		announce(g, "race_first", List.of(String.join(", ", names)));
	}

	private void announce(Game g, String key, List<String> args) {
		for (GameListener l : listeners) {
			try {
				l.announced(g, key, args);
			} catch (RuntimeException ex) {
				log.warn("Trahison listener failed on announced", ex);
			}
		}
	}

	// ------------------------------------------------------------------ T7.4 game master gestures

	private static PlayerState player(Game g, int seat) {
		if (seat < 0 || seat >= g.players.size()) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "no such player");
		}
		return g.players.get(seat);
	}

	private static String cleanText(String text) {
		String t = text == null ? "" : text.trim();
		if (t.isEmpty() || t.length() > 200) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "text required (1 to 200 characters)");
		}
		return t;
	}

	/** The game master whispers a line to one seat; the log keeps it. */
	public Game gmWhisper(String hub, int seat, String text) {
		Game g = require(hub);
		synchronized (g) {
			String clean = cleanText(text);
			PlayerState p = player(g, seat);
			if (p.bot) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "a dummy player has nobody to tell");
			}
			event(g, "gm_whisper", Map.of("player", seat, "text", clean));
			whisper(g, seat, "phantasmon_events.trahison.say.gm_whisper", List.of(clean));
			persist(g);
			return g;
		}
	}

	/** The game master speaks to everybody, as the hostess. */
	public Game gmSay(String hub, String text) {
		Game g = require(hub);
		synchronized (g) {
			String clean = cleanText(text);
			event(g, "gm_say", Map.of("text", clean));
			announce(g, "gm", List.of(clean));
			persist(g);
			return g;
		}
	}

	/** The game master gives a help text to the room of a group of the running round. */
	public Game gmHint(String hub, int group) {
		Game g = require(hub);
		synchronized (g) {
			RoundState r = g.currentRound();
			if (g.phase != Phase.ROOMS || r == null || group < 0 || group > 2 || rooms == null) {
				throw new TrahisonException(TrahisonException.WRONG_PHASE, "no room is being played");
			}
			boolean given = rooms.giveHint(g, r, group);
			event(g, "gm_hint", Map.of("group", group, "given", given));
			persist(g);
			return g;
		}
	}

	/** The game master solves a stuck room: its exit opens and the group counts as solved (no point lost). */
	public Game gmSolveRoom(String hub, int group) {
		Game g = require(hub);
		synchronized (g) {
			RoundState r = g.currentRound();
			if (g.phase != Phase.ROOMS || r == null || group < 0 || group > 2) {
				throw new TrahisonException(TrahisonException.WRONG_PHASE, "no room is being played");
			}
			if (rooms != null) {
				rooms.forceSolve(g, r, group);
			}
			event(g, "gm_solve_room", Map.of("group", group));
			markRoomSolved(g, r, group, -1);
			persist(g);
			notifyChanged(g, "room");
			return g;
		}
	}

	/** To repair a mistake: a dead player lives again, his body is gone and his bracelet is back on his wrist. */
	public Game revive(String hub, int seat) {
		Game g = require(hub);
		synchronized (g) {
			if (g.phase == Phase.LOBBY || g.phase == Phase.ENDED) {
				throw new TrahisonException(TrahisonException.WRONG_PHASE, "no running game");
			}
			PlayerState p = player(g, seat);
			if (p.alive) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "he is alive");
			}
			p.alive = true;
			BraceletState b = g.bracelets.get(seat);
			b.ownerAlive = true;
			if (b.holder == null) {
				b.holder = seat;
			}
			if (b.points < 1) {
				b.points = 1;
			}
			g.bodies.removeIf(body -> body.player == seat);
			event(g, "revived", Map.of("player", seat));
			persist(g);
			notifyChanged(g, "death");
			return g;
		}
	}

	/** Settings that may change while the lobby is open (the rest is fixed at creation); returns the game. */
	public Game configure(String hub, java.util.function.Consumer<Settings> change) {
		Game g = require(hub);
		synchronized (g) {
			if (g.phase != Phase.LOBBY) {
				throw new TrahisonException(TrahisonException.NOT_IN_LOBBY, "the settings change in the lobby");
			}
			change.accept(g.settings);
			event(g, "settings", Map.of());
			persist(g);
			notifyChanged(g, "settings");
			return g;
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
		refreshDoors(g);
		for (GameListener l : listeners) {
			try {
				l.phaseChanged(g);
			} catch (RuntimeException ex) {
				log.warn("Trahison listener failed on phaseChanged", ex);
			}
		}
	}

	private void notifyChanged(Game g, String patch) {
		refreshDoors(g);
		for (GameListener l : listeners) {
			try {
				l.changed(g, patch);
			} catch (RuntimeException ex) {
				log.warn("Trahison listener failed on {}", patch, ex);
			}
		}
	}
}
