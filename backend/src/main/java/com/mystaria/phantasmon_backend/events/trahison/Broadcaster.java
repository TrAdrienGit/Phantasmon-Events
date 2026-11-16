package com.mystaria.phantasmon_backend.events.trahison;

import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.mystaria.phantasmon_backend.events.trahison.game.Game;
import com.mystaria.phantasmon_backend.events.trahison.game.GameListener;
import com.mystaria.phantasmon_backend.events.trahison.game.PlayerState;
import com.mystaria.phantasmon_backend.events.trahison.game.Views;
import com.mystaria.phantasmon_backend.events.trahison.room.PuzzleUpdate;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomException;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomInstance;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomListener;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomService;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomViews;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;
import com.mystaria.phantasmon_backend.websocket.SessionRegistry;
import com.mystaria.phantasmon_backend.websocket.WsMessage;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Sends the views: on every change of a game, a fresh {@code EventsTrahisonView} to each recipient (the players, the game
 * masters, whoever asked for a view, and the members of the hub as spectators), each built for that recipient by
 * {@link Views}. Called by {@link com.mystaria.phantasmon_backend.events.trahison.game.GameService} while the game is
 * locked, so every view is consistent. {@code EventsTrahisonPatch} is reserved: the state is small, so views are always
 * complete. A phase (or round) change also announces it by language key.
 * <p>
 * It is also the {@link RoomListener}: {@code EventsTrahisonRoom} and {@code EventsTrahisonPuzzle} go to the members of
 * the group playing the room (player view), to the game masters (game master view) and to ghosts and spectators
 * (spectator view, of every room); a living player of another group gets nothing. Rooms call it with the room locked, so
 * it never takes the game's lock (lock order: game, then room): it works from an {@link Audience}, a snapshot of who is
 * who that it refreshes on every game change.
 */
class Broadcaster implements GameListener, RoomListener {

	static final String VIEW = "EventsTrahisonView";
	static final String SAY = "EventsTrahisonSay";
	static final String ROOM = "EventsTrahisonRoom";
	static final String PUZZLE = "EventsTrahisonPuzzle";
	static final String SAY_KEY_PREFIX = "phantasmon_events.trahison.say.phase.";

	private final SessionRegistry sessions;
	private final Clock clock;
	private final Supplier<HubGate> hubs;
	private final RoomService rooms;
	private final Map<String, Audience> audiences = new ConcurrentHashMap<>();
	/** Hub name (as in the game) to the accounts that asked for views of it, besides players and game masters. */
	private final Map<String, Set<UUID>> watchers = new ConcurrentHashMap<>();
	/** Game id to the last announced stage, so a pause or a point correction does not announce again. */
	private final Map<UUID, String> announced = new ConcurrentHashMap<>();

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	/**
	 * Who is who in a game, copied while the game was locked: the account of each player index, who is dead, which
	 * accounts are bots, the game masters.
	 */
	private record Audience(List<UUID> accounts, boolean[] alive, Set<UUID> bots, Set<UUID> gameMasters) {

		static Audience of(Game g) {
			List<UUID> accounts = new java.util.ArrayList<>();
			boolean[] alive = new boolean[g.players.size()];
			Set<UUID> bots = new LinkedHashSet<>();
			for (int i = 0; i < alive.length; i++) {
				PlayerState p = g.players.get(i);
				accounts.add(p.uuid);
				alive[i] = p.alive;
				if (p.bot) {
					bots.add(p.uuid);
				}
			}
			return new Audience(List.copyOf(accounts), alive, Set.copyOf(bots), Set.copyOf(g.gameMasters));
		}

		/**
		 * What this account sees of a room: a living member of the group, the player view; a living player of another
		 * group, nothing; a ghost or anyone not playing, the spectator view; a game master who does not play, the game
		 * master view (as {@link Views#kindOf}, a game master who plays is a player).
		 */
		ViewerKind viewerOf(UUID account, RoomInstance room) {
			int i = accounts.indexOf(account);
			if (i >= 0) {
				if (!alive[i]) {
					return ViewerKind.SPECTATOR;
				}
				return room.members.contains(i) ? ViewerKind.PLAYER : null;
			}
			return gameMasters.contains(account) ? ViewerKind.GAME_MASTER : ViewerKind.SPECTATOR;
		}
	}

	Broadcaster(SessionRegistry sessions, Clock clock, Supplier<HubGate> hubs, RoomService rooms) {
		this.sessions = sessions;
		this.clock = clock;
		this.hubs = hubs;
		this.rooms = rooms;
	}

	void watch(String hub, UUID account) {
		watchers.computeIfAbsent(hub, h -> ConcurrentHashMap.newKeySet()).add(account);
	}

	void forget(UUID account) {
		watchers.values().forEach(set -> set.remove(account));
	}

	@Override
	public void phaseChanged(Game g) {
		audiences.put(g.hub, Audience.of(g));
		sendViews(g);
		String stage = g.phase + ":" + g.round + ":" + g.finalLoops;
		if (!stage.equals(announced.put(g.id, stage))) {
			announce(g);
		}
	}

	@Override
	public void changed(Game g, String patch) {
		audiences.put(g.hub, Audience.of(g));
		sendViews(g);
	}

	// ------------------------------------------------------------------ rooms

	@Override
	public void roomChanged(RoomInstance room) {
		Audience audience = audiences.get(room.hub);
		if (audience == null) {
			return;
		}
		EnumMap<ViewerKind, Map<String, Object>> built = new EnumMap<>(ViewerKind.class);
		for (UUID account : recipients(room.hub, audience)) {
			ViewerKind kind = audience.viewerOf(account, room);
			if (kind != null) {
				sessions.send(account, WsMessage.of(ROOM, built.computeIfAbsent(kind, k -> data(RoomViews.room(room, k)))));
			}
		}
	}

	@Override
	public void puzzleChanged(RoomInstance room, PuzzleUpdate update) {
		Audience audience = audiences.get(room.hub);
		if (audience == null) {
			return;
		}
		if (update.onlyTo() != null) { // a player opened the screen: only he needs the state
			int i = update.onlyTo();
			if (i >= 0 && i < audience.accounts().size() && !audience.bots().contains(audience.accounts().get(i))) {
				sessions.send(audience.accounts().get(i),
						WsMessage.of(PUZZLE, data(RoomViews.puzzle(room, update, ViewerKind.PLAYER))));
			}
			return;
		}
		EnumMap<ViewerKind, Map<String, Object>> built = new EnumMap<>(ViewerKind.class);
		for (UUID account : recipients(room.hub, audience)) {
			ViewerKind kind = audience.viewerOf(account, room);
			if (kind != null) {
				sessions.send(account, WsMessage.of(PUZZLE, built.computeIfAbsent(kind, k -> data(RoomViews.puzzle(room, update, k)))));
			}
		}
	}

	/** The rooms of the game's hub as this account may see them (the game must be locked by the caller). */
	void sendRooms(Game g, UUID account) {
		Audience audience = Audience.of(g);
		for (RoomInstance room : rooms.rooms(g.hub)) {
			ViewerKind kind = audience.viewerOf(account, room);
			if (kind == null) {
				continue;
			}
			try {
				sessions.send(account, WsMessage.of(ROOM, data(rooms.roomView(g.hub, room.group, kind))));
			} catch (RoomException ex) {
				// the room went away meanwhile: nothing to show
			}
		}
	}

	private static Map<String, Object> data(ObjectNode node) {
		return MAPPER.convertValue(node, new TypeReference<java.util.LinkedHashMap<String, Object>>() {
		});
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

	private Set<UUID> recipients(Game g) {
		return recipients(g.hub, Audience.of(g));
	}

	/** Players, game masters, watchers, and the hub members around any of them who is in a hub. Never a bot. */
	private Set<UUID> recipients(String hub, Audience audience) {
		Set<UUID> known = new LinkedHashSet<>();
		for (UUID account : audience.accounts()) {
			if (!audience.bots().contains(account)) {
				known.add(account);
			}
		}
		known.addAll(audience.gameMasters());
		known.addAll(watchers.getOrDefault(hub, Set.of()));
		Set<UUID> all = new LinkedHashSet<>(known);
		HubGate gate = hubs.get();
		for (UUID account : known) {
			if (gate.isMember(account)) {
				all.addAll(gate.membersWith(account));
			}
		}
		all.removeAll(audience.bots());
		return all;
	}
}
