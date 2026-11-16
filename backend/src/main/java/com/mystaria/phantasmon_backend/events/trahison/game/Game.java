package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mystaria.phantasmon_backend.events.trahison.rules.History;

/**
 * The whole state of one game (architecture.md section 4). Mutable, guarded by {@code synchronized (game)} in
 * {@link GameService}; serialized as is to JSON ({@link GameJson}) and nothing else is kept elsewhere, so a restart
 * resumes it. Fields are public for Jackson: only {@link GameService} may write them.
 */
public class Game {
	public UUID id;
	public String hub;
	public int layoutVersion = 1;
	public long seed;
	public Settings settings = new Settings();

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
	/** Couples of bracelet indices {lo, hi} (see {@link History}). */
	public List<int[]> pairedHistory = new ArrayList<>();
	public List<int[]> facedHistory = new ArrayList<>();
	/** Player indices that went through the final door; set when it opens. */
	public List<Integer> winners = new ArrayList<>();
	public List<LogEntry> log = new ArrayList<>();
	/** Admins who run this game (they get the game master view). */
	public java.util.Set<UUID> gameMasters = new java.util.LinkedHashSet<>();
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
