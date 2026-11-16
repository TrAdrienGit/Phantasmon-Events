package com.mystaria.phantasmon_backend.events.trahison;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.mystaria.phantasmon_backend.events.trahison.game.Game;
import com.mystaria.phantasmon_backend.events.trahison.game.GameListener;
import com.mystaria.phantasmon_backend.events.trahison.game.PlayerState;
import com.mystaria.phantasmon_backend.events.trahison.game.Views;
import com.mystaria.phantasmon_backend.websocket.SessionRegistry;
import com.mystaria.phantasmon_backend.websocket.WsMessage;

/**
 * Sends the views: on every change of a game, a fresh {@code EventsTrahisonView} to each recipient (the players, the game
 * masters, whoever asked for a view, and the members of the hub as spectators), each built for that recipient by
 * {@link Views}. Called by {@link com.mystaria.phantasmon_backend.events.trahison.game.GameService} while the game is
 * locked, so every view is consistent. {@code EventsTrahisonPatch} is reserved: the state is small, so views are always
 * complete. A phase (or round) change also announces it by language key.
 */
class Broadcaster implements GameListener {

	static final String VIEW = "EventsTrahisonView";
	static final String SAY = "EventsTrahisonSay";
	static final String SAY_KEY_PREFIX = "phantasmon_events.trahison.say.phase.";

	private final SessionRegistry sessions;
	private final Clock clock;
	private final Supplier<HubGate> hubs;
	/** Hub name (as in the game) to the accounts that asked for views of it, besides players and game masters. */
	private final Map<String, Set<UUID>> watchers = new ConcurrentHashMap<>();
	/** Game id to the last announced stage, so a pause or a point correction does not announce again. */
	private final Map<UUID, String> announced = new ConcurrentHashMap<>();

	Broadcaster(SessionRegistry sessions, Clock clock, Supplier<HubGate> hubs) {
		this.sessions = sessions;
		this.clock = clock;
		this.hubs = hubs;
	}

	void watch(String hub, UUID account) {
		watchers.computeIfAbsent(hub, h -> ConcurrentHashMap.newKeySet()).add(account);
	}

	void forget(UUID account) {
		watchers.values().forEach(set -> set.remove(account));
	}

	@Override
	public void phaseChanged(Game g) {
		sendViews(g);
		String stage = g.phase + ":" + g.round + ":" + g.finalLoops;
		if (!stage.equals(announced.put(g.id, stage))) {
			announce(g);
		}
	}

	@Override
	public void changed(Game g, String patch) {
		sendViews(g);
	}

	/** One view for one account (the game must be locked by the caller). */
	void sendView(Game g, UUID account) {
		sessions.send(account, WsMessage.of(VIEW, Views.build(g, account, clock.instant())));
	}

	private void sendViews(Game g) {
		for (UUID account : recipients(g)) {
			sendView(g, account);
		}
	}

	private void announce(Game g) {
		Map<String, Object> say = new java.util.LinkedHashMap<>();
		say.put("kind", "announce");
		say.put("key", SAY_KEY_PREFIX + g.phase.name().toLowerCase(java.util.Locale.ROOT));
		say.put("phase", g.phase.name());
		say.put("round", g.round);
		for (UUID account : recipients(g)) {
			sessions.send(account, WsMessage.of(SAY, say));
		}
	}

	/** Players, game masters, watchers, and the hub members around any of them who is in a hub. Never a bot. */
	private Set<UUID> recipients(Game g) {
		Set<UUID> known = new LinkedHashSet<>();
		for (PlayerState p : g.players) {
			if (!p.bot) {
				known.add(p.uuid);
			}
		}
		known.addAll(g.gameMasters);
		known.addAll(watchers.getOrDefault(g.hub, Set.of()));
		Set<UUID> all = new LinkedHashSet<>(known);
		HubGate gate = hubs.get();
		for (UUID account : known) {
			if (gate.isMember(account)) {
				all.addAll(gate.membersWith(account));
			}
		}
		for (PlayerState p : g.players) {
			if (p.bot) {
				all.remove(p.uuid);
			}
		}
		return all;
	}
}
