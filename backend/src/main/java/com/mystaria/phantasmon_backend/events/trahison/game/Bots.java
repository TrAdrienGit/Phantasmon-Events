package com.mystaria.phantasmon_backend.events.trahison.game;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Outcome;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;
import com.mystaria.phantasmon_backend.events.trahison.rules.Vote;

/**
 * The fake players (admin-only, to play with fewer than nine people). A bot acts through {@link GameService} like a
 * player, after a delay drawn from the game seed, with time from the injected {@link GameScheduler} (so tests with a
 * manual clock stay deterministic):
 * <ul>
 *   <li>DOORS: takes the door its split leaves it, consistent with the doors already taken (with none taken, the seed
 *       picks one of the two splits, the same for every bot);</li>
 *   <li>ROOMS: solves its group's simulated room;</li>
 *   <li>VOTE: votes by the game's {@link BotPolicy} (a pair has one vote: the first of its members to vote);</li>
 *   <li>FINAL: opens the final door if its bracelet reads 9 and it is alive.</li>
 * </ul>
 * It is a {@link GameListener}: each stage entry (and each resume from a pause) arms the bots that have nothing
 * scheduled for that stage, so a reloaded game keeps its bots playing. A task that fires while the game is paused is
 * dropped and re-armed on resume. Bots never receive any message.
 */
public class Bots implements GameListener {

	private static final Logger log = LoggerFactory.getLogger(Bots.class);
	private static final long GOLDEN = 0x9E3779B97F4A7C15L;
	private static final long SALT = 0xB0B0B0B0L;

	private final GameService service;
	private final Clock clock;
	private final GameScheduler scheduler;
	/** Game id to what is armed for its current stage. */
	private final Map<UUID, Plan> plans = new ConcurrentHashMap<>();

	private static final class Plan {
		final String key;
		final Set<Integer> armed = ConcurrentHashMap.newKeySet();

		Plan(String key) {
			this.key = key;
		}
	}

	public Bots(GameService service, Clock clock, GameScheduler scheduler) {
		this.service = service;
		this.clock = clock;
		this.scheduler = scheduler;
	}

	/**
	 * Adds up to {@code count} bots ("Bot 1", "Bot 2"...) to the hub's lobby, never above nine players; with
	 * {@code count <= 0} fills every free place. Returns the number added.
	 */
	public static int addBots(GameService service, String hub, int count) {
		int[] state = service.read(hub, g -> new int[] { g.players.size(), (int) g.players.stream().filter(p -> p.bot).count() });
		int free = GameService.PLAYERS - state[0];
		int n = count <= 0 ? free : count;
		if (n > free) {
			throw new TrahisonException(TrahisonException.GAME_FULL, "only " + free + " places left");
		}
		for (int k = 1; k <= n; k++) {
			int number = state[1] + k;
			service.join(hub, botId(hub, number), "Bot " + number, true);
		}
		return n;
	}

	/** The fake account of the {@code number}th bot of a hub. */
	static UUID botId(String hub, int number) {
		return UUID.nameUUIDFromBytes(("trahison-bot:" + hub + ":" + number).getBytes(StandardCharsets.UTF_8));
	}

	// ------------------------------------------------------------------ arming (called under the game lock)

	@Override
	public void phaseChanged(Game g) {
		boolean acts = g.phase == Phase.DOORS || g.phase == Phase.ROOMS || g.phase == Phase.VOTE || g.phase == Phase.FINAL;
		if (!acts) {
			plans.remove(g.id);
			return;
		}
		if (g.paused || g.players.stream().noneMatch(p -> p.bot)) {
			return;
		}
		String key = g.round + ":" + g.phase + ":" + g.finalLoops;
		Plan plan = plans.compute(g.id, (id, old) -> old != null && old.key.equals(key) ? old : new Plan(key));
		long span = g.deadline != null ? Math.max(0, g.deadline.toEpochMilli() - clock.millis()) : g.remainingMs;
		for (int i = 0; i < g.players.size(); i++) {
			PlayerState p = g.players.get(i);
			if (!p.bot || !p.alive || !plan.armed.add(i)) {
				continue;
			}
			// Seeded fraction of the time left, so bots do not all act at once nor at the last second.
			double fraction = 0.1 + 0.5 * new SplittableRandom(g.seed ^ (g.round * GOLDEN) ^ (g.phase.ordinal() * SALT)
				^ ((i + 1) * GOLDEN) ^ g.finalLoops).nextDouble();
			long delay = Math.max(1, (long) (span * fraction));
			UUID id = g.id;
			String hub = g.hub;
			int bot = i;
			scheduler.schedule(clock.instant().plusMillis(delay), () -> act(hub, id, plan, bot));
		}
	}

	// ------------------------------------------------------------------ acting

	private void act(String hub, UUID gameId, Plan plan, int bot) {
		try {
			Runnable action = service.read(hub, g -> decide(g, gameId, plan, bot));
			if (action != null) {
				action.run();
			}
		} catch (TrahisonException ex) {
			log.debug("Trahison bot {} in {}: {}", bot, hub, ex.getMessage());
		} catch (RuntimeException ex) {
			log.warn("Trahison bot {} in {} failed", bot, hub, ex);
		}
	}

	/** Called under the game lock: what the bot does now, or null. The action itself runs after the lock is released. */
	private Runnable decide(Game g, UUID gameId, Plan plan, int bot) {
		if (!g.id.equals(gameId) || plans.get(gameId) != plan) {
			return null; // the stage moved on
		}
		if (g.paused) {
			plan.armed.remove(bot); // armed again by the resume
			return null;
		}
		PlayerState p = g.players.get(bot);
		if (!p.alive) {
			return null;
		}
		UUID uuid = p.uuid;
		String hub = g.hub;
		RoundState r = g.currentRound();
		switch (g.phase) {
			case DOORS -> {
				if (r.passedDoor[bot] >= 0) {
					return null;
				}
				Split s = chosenSplit(g, r);
				if (s == null) {
					return null;
				}
				for (int k = 0; k < Door.ALL.size(); k++) {
					if (s.group(Door.ALL.get(k)).members().contains(bot)) {
						int door = k;
						return () -> service.passDoor(hub, uuid, door);
					}
				}
				return null;
			}
			case ROOMS -> {
				return r.splitIndex == null ? null : () -> service.debugSolve(hub, uuid);
			}
			case VOTE -> {
				if (r.splitIndex == null) {
					return null;
				}
				int k = r.groupOf(bot);
				boolean pair = r.split().group(Door.ALL.get(k)).pair().contains(bot);
				if ((pair ? r.pairVote[k] : r.soloVote[k]) != null) {
					return null;
				}
				Vote v = switch (g.botPolicy) {
					case ALLY -> Vote.ALLY;
					case BETRAY -> Vote.BETRAY;
					case RANDOM -> new SplittableRandom(g.seed ^ (r.number * GOLDEN) ^ ((bot + 1) * SALT)).nextBoolean()
						? Vote.BETRAY : Vote.ALLY;
				};
				return () -> service.vote(hub, uuid, v);
			}
			case FINAL -> {
				return Outcome.canOpenFinalDoor(g.bracelets.get(bot).points, p.alive)
					? () -> service.openFinalDoor(hub, uuid) : null;
			}
			default -> {
				return null;
			}
		}
	}

	/**
	 * The split the doors taken so far agree with; with no door taken, the one the seed picks (the same for all bots).
	 */
	private static Split chosenSplit(Game g, RoundState r) {
		List<Split> consistent = new ArrayList<>();
		for (Split s : Doors.validSplits(r.assignment())) {
			boolean ok = true;
			for (int b = 0; b < r.passedDoor.length && ok; b++) {
				int d = r.passedDoor[b];
				ok = d < 0 || s.group(Door.ALL.get(d)).members().contains(b);
			}
			if (ok) {
				consistent.add(s);
			}
		}
		if (consistent.isEmpty()) {
			return null;
		}
		if (consistent.size() == 1) {
			return consistent.get(0);
		}
		return consistent.get(new SplittableRandom(g.seed ^ (r.number * GOLDEN) ^ SALT).nextInt(consistent.size()));
	}
}
