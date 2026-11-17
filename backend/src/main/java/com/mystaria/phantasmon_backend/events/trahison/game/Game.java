package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mystaria.phantasmon_backend.events.trahison.room.RoomDraw;
import com.mystaria.phantasmon_backend.events.trahison.rules.History;

/**
 * The whole state of one game (architecture.md section 4). Mutable, guarded by {@code synchronized (game)} in
 * {@link GameService}; serialized as is to JSON ({@link GameJson}) and nothing else is kept elsewhere, so a restart
 * resumes it. Fields are public for Jackson: only {@link GameService} may write them.
 */
public class Game {
	public UUID id;
	public String hub;
	public long seed;
	public Settings settings = new Settings();
	/** Version of the hub's plan this game uses, fixed when it starts (0: the hub had no plan). */
	public int layoutVersion;
	/**
	 * State of each door of the plan (door id), derived by {@link GameService} from the stage and the room progress
	 * ({@link DoorStates}) and refreshed before every notification; sent in the views as {@code doors_state}.
	 */
	public java.util.Map<String, DoorState> doors = new java.util.LinkedHashMap<>();
	/** The game master's {@code set_door} decisions (door id to open); they win over the defaults until the stage changes. */
	public java.util.Map<String, Boolean> doorOverrides = new java.util.LinkedHashMap<>();
	/**
	 * The colour doors open right now because the bracelets at their threshold satisfy them (door id to its window), during
	 * DOORS only (T2.3). Kept with the game so a restart brings them back.
	 */
	public java.util.Map<String, DoorWindow> doorWindows = new java.util.LinkedHashMap<>();
	/**
	 * The vote cabins held right now (cabin id to its lock), VOTE only (D4, D5). Cleared when a stage starts, so every cabin
	 * reopens when VOTE ends. Kept with the game so a restart brings them back.
	 */
	public java.util.Map<String, CabinLock> cabinLocks = new java.util.LinkedHashMap<>();
	/**
	 * The final door while it is open (FINAL only, T2.5): who may pass and until when; null the rest of the time. Kept with
	 * the game so a restart brings it back (and closes it if its time has passed).
	 */
	public FinalDoorWindow finalDoor;
	/** The ids of the plan's cabins, derived like {@link #doors} and sent as {@code cabins}; not stored. */
	@com.fasterxml.jackson.annotation.JsonIgnore
	public java.util.List<String> cabinIds = new java.util.ArrayList<>();
	/** The cabin each player may close and vote in (player index to cabin id), derived; not stored. */
	@com.fasterxml.jackson.annotation.JsonIgnore
	public java.util.Map<Integer, String> myCabins = new java.util.LinkedHashMap<>();
	/** The threshold hints of the colour doors, derived like {@link #doors} and sent as {@code door_hints}; not stored. */
	@com.fasterxml.jackson.annotation.JsonIgnore
	public java.util.List<DoorHint> doorHints = new java.util.ArrayList<>();

	/**
	 * The nine rooms drawn for this game and the place each one is played in (T4.1, T4.3); null when no draw was made (a test
	 * room is set, the bank is empty, or the room engine is not wired), the rooms then come from the settings.
	 */
	public RoomDraw.Result draw;

	/** T11.2: the zones of the plan each seat has been in (seat to zone ids, in order of first visit). Private to the seat. */
	public java.util.Map<Integer, List<String>> explored = new java.util.LinkedHashMap<>();

	public Phase phase = Phase.LOBBY;
	/** 1..3, stays 3 during the final. */
	public int round;
	/** Number of final-door vote loops started. */
	public int finalLoops;
	/** End of the current stage; null in the lobby, at the end, and while paused. */
	public Instant deadline;
	public boolean paused;
	/** Time left in the stage; meaningful while paused. */
	public long remainingMs;
	/** Bumped each time a deadline is (re)scheduled, so a stale timer does nothing. */
	public int timerVersion;

	public List<PlayerState> players = new ArrayList<>();
	public List<BraceletState> bracelets = new ArrayList<>();
	public List<RoundState> rounds = new ArrayList<>();
	/** D102: the accounts that registered as spectators (account to name); shown only to ghosts, spectators and game masters. */
	public java.util.Map<UUID, String> spectators = new java.util.LinkedHashMap<>();
	/** D50: the seats of the playing game masters whose own powers set their bracelet to 0; if that kills them, nobody wins by it. */
	public List<Integer> selfDestined = new ArrayList<>();
	/** T7.1: what the secret roles remember (the roles themselves are {@code players[i].role}). */
	public RoleState roleState = new RoleState();
	/** T7.1: the seats that fulfilled their role's objective, set when the game ends. */
	public List<Integer> objectivesMet = new ArrayList<>();
	/** T7.2: the items still hidden in the plan's hiding spots (spot id to {@code Item} name). Secret. */
	public java.util.Map<String, String> hiddenItems = new java.util.LinkedHashMap<>();
	/** T7.2: items the draw found no hiding spot for (the plan has too few). Secret, game masters only. */
	public List<String> unplacedItems = new ArrayList<>();
	/** T7.2: the doors locked by the lock item (door id to its lock). */
	public java.util.Map<String, LockedDoor> lockedDoors = new java.util.LinkedHashMap<>();
	/** T7.2: the cabin each player listens to with the ear this VOTE (seat to cabin id); T5.4 plays the sound. */
	public java.util.Map<Integer, String> listening = new java.util.LinkedHashMap<>();
	/** T7.2: the untying in progress (target seat to who is untying him). */
	public java.util.Map<Integer, Untying> untying = new java.util.LinkedHashMap<>();
	/** The bodies of the dead players (T6.1), in order of death. */
	public List<Body> bodies = new ArrayList<>();
	/** Couples of bracelet indices {lo, hi} (see {@link History}). */
	public List<int[]> pairedHistory = new ArrayList<>();
	public List<int[]> facedHistory = new ArrayList<>();
	/** Player indices that went through the final door; set when it opens. */
	public List<Integer> winners = new ArrayList<>();
	public List<LogEntry> log = new ArrayList<>();
	/** The accounts the admins assigned in the lobby to run this game (D7); they get the game master view. Possibly none. */
	public java.util.Set<UUID> gameMasters = new java.util.LinkedHashSet<>();
	/** The names of the game masters (account to name), for the views of the lobby. */
	public java.util.Map<UUID, String> gameMasterNames = new java.util.LinkedHashMap<>();
	/** How the bots vote. */
	public BotPolicy botPolicy = BotPolicy.RANDOM;
	/** True while the game is paused by itself because a living player disconnected (AUTO mode only). */
	public boolean autoPaused;
	/** When an automatic pause ends whatever happens; null when not auto-paused. */
	public Instant autoPauseUntil;

	public Instant createdAt;
	public Instant endedAt;

	public RoundState currentRound() {
		return rounds.isEmpty() ? null : rounds.get(rounds.size() - 1);
	}

	/** Indices of the players who are also game masters (D12: a secret role), in seat order. */
	public List<Integer> playingGameMasters() {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < players.size(); i++) {
			if (gameMasters.contains(players.get(i).uuid)) {
				out.add(i);
			}
		}
		return out;
	}

	/** The role of this seat, or null (the game master who plays has {@code GAME_MASTER}, D12). */
	public com.mystaria.phantasmon_backend.events.trahison.rules.Role roleOf(int seat) {
		if (seat < 0 || seat >= players.size()) {
			return null;
		}
		if (gameMasters.contains(players.get(seat).uuid)) {
			return com.mystaria.phantasmon_backend.events.trahison.rules.Role.GAME_MASTER;
		}
		return com.mystaria.phantasmon_backend.events.trahison.rules.Role.parse(players.get(seat).role);
	}

	/** The body of this dead player, or null. */
	public Body bodyOf(int player) {
		for (Body b : bodies) {
			if (b.player == player) {
				return b;
			}
		}
		return null;
	}

	/** Index of the player with this account, or -1. */
	public int indexOf(UUID uuid) {
		for (int i = 0; i < players.size(); i++) {
			if (players.get(i).uuid.equals(uuid)) {
				return i;
			}
		}
		return -1;
	}

	public int[] points() {
		int[] out = new int[bracelets.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = bracelets.get(i).points;
		}
		return out;
	}

	public boolean[] aliveFlags() {
		boolean[] out = new boolean[players.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = players.get(i).alive;
		}
		return out;
	}

	public History history() {
		var paired = new java.util.HashSet<History.Link>();
		var faced = new java.util.HashSet<History.Link>();
		pairedHistory.forEach(p -> paired.add(History.Link.of(p[0], p[1])));
		facedHistory.forEach(p -> faced.add(History.Link.of(p[0], p[1])));
		return new History(paired, faced);
	}

	/** Replaces the stored history, sorted so the JSON is stable. */
	public void storeHistory(History h) {
		pairedHistory = sorted(h.paired());
		facedHistory = sorted(h.faced());
	}

	private static List<int[]> sorted(java.util.Set<History.Link> links) {
		return links.stream()
			.sorted(java.util.Comparator.comparingInt(History.Link::lo).thenComparingInt(History.Link::hi))
			.map(l -> new int[] { l.lo(), l.hi() })
			.toList();
	}
}
