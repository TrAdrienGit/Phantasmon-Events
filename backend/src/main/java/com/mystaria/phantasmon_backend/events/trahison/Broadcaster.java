package com.mystaria.phantasmon_backend.events.trahison;

import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
	/** The hostess's one-off lines (not tied to a stage): {@code say.<name>}. */
	static final String SAY_KEY = "phantasmon_events.trahison.say.";

	private final SessionRegistry sessions;
	private final Clock clock;
	private final Supplier<HubGate> hubs;
	private final RoomService rooms;
	private final Map<String, Audience> audiences = new ConcurrentHashMap<>();
	/** Hub name (as in the game) to the accounts that asked for views of it, besides players and game masters. */
	private final Map<String, Set<UUID>> watchers = new ConcurrentHashMap<>();
	/** Game id to the last announced stage, so a pause or a point correction does not announce again. */
	private final Map<UUID, String> announced = new ConcurrentHashMap<>();
	/** Game id to the final door window already announced, so a later change does not announce it again. */
	private final Map<UUID, com.mystaria.phantasmon_backend.events.trahison.game.FinalDoorWindow> openDoors = new ConcurrentHashMap<>();

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	/**
	 * Who is who in a game, copied while the game was locked: the account of each player index, who is dead, which
	 * accounts are bots, the game masters.
	 */
	private record Audience(List<UUID> accounts, boolean[] alive, Set<UUID> bots, Set<UUID> gameMasters, boolean freeTime,
			int round, boolean[] roam) {

		static Audience of(Game g) {
			List<UUID> accounts = new java.util.ArrayList<>();
			boolean[] alive = new boolean[g.players.size()];
			boolean[] roam = new boolean[alive.length];
			Set<UUID> bots = new LinkedHashSet<>();
			for (int i = 0; i < alive.length; i++) {
				PlayerState p = g.players.get(i);
				accounts.add(p.uuid);
				alive[i] = p.alive;
				roam[i] = com.mystaria.phantasmon_backend.events.trahison.game.GameService.canRoam(g, i);
				if (p.bot) {
					bots.add(p.uuid);
				}
			}
			var current = g.currentRound();
			return new Audience(List.copyOf(accounts), alive, Set.copyOf(bots), Set.copyOf(g.gameMasters),
					g.phase == com.mystaria.phantasmon_backend.events.trahison.game.Phase.FREE_TIME,
					current == null ? 0 : current.number, roam);
		}

		/**
		 * What this account sees of a room: a living member of the group, the player view; a living player of another
		 * group, nothing (except in the free time, D8: every living player visits and plays every room opened so far, those
		 * of earlier rounds too, so he gets the player view of all of them; outside it, the rooms of earlier rounds are closed
		 * to everybody who plays, except, D22, in ROOMS and VOTE for a player whose group has finished its own room of the
		 * round: he roams like in the free time); a ghost or anyone not playing, the spectator view; a game master who does not play, the game
		 * master view (as {@link Views#kindOf}, a game master who plays is a player).
		 */
		ViewerKind viewerOf(UUID account, RoomInstance room) {
			int i = accounts.indexOf(account);
			if (i >= 0) {
				if (!alive[i]) {
					return ViewerKind.SPECTATOR;
				}
				return freeTime || roam[i] || room.round == round && room.members.contains(i) ? ViewerKind.PLAYER : null;
			}
			return gameMasters.contains(account) ? ViewerKind.GAME_MASTER : ViewerKind.SPECTATOR;
		}
	}

	/** The name of an account, for the lobby's list of who may be given the game master role (D7). */
	private final Function<UUID, String> names;

	Broadcaster(SessionRegistry sessions, Clock clock, Supplier<HubGate> hubs, RoomService rooms) {
		this(sessions, clock, hubs, rooms, UUID::toString);
	}

	Broadcaster(SessionRegistry sessions, Clock clock, Supplier<HubGate> hubs, RoomService rooms, Function<UUID, String> names) {
		this.names = names;
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
		if (g.phase == com.mystaria.phantasmon_backend.events.trahison.game.Phase.FREE_TIME) {
			// D8: every living player may now visit every room, so each one gets the rooms he did not see until now.
			for (UUID account : recipients(g)) {
				sendRooms(g, account);
			}
		}
		String stage = g.phase + ":" + g.round + ":" + g.finalLoops;
		if (!stage.equals(announced.put(g.id, stage))) {
			announce(g);
			announceDoorDeaths(g);
			if (g.phase == com.mystaria.phantasmon_backend.events.trahison.game.Phase.ENDED
					&& g.log.stream().anyMatch(e -> e.type.equals("final_door"))) {
				announceEscape(g);
			}
			if (g.phase == com.mystaria.phantasmon_backend.events.trahison.game.Phase.ENDED
					&& g.log.stream().anyMatch(e -> e.type.equals("game_master_killed"))) {
				announceGameMasterKilled(g);
			}
		}
	}

	@Override
	public void changed(Game g, String patch) {
		audiences.put(g.hub, Audience.of(g));
		sendViews(g);
		if ("room".equals(patch)) {
			// D22: a group that finished its room is free to roam, so its members get the rooms they did not see until now.
			Audience audience = audiences.get(g.hub);
			for (int i = 0; audience != null && i < audience.roam().length; i++) {
				if (audience.roam()[i] && !audience.bots().contains(audience.accounts().get(i))) {
					sendRooms(g, audience.accounts().get(i));
				}
			}
		}
		if ("final_door".equals(patch) && g.finalDoor != null && !g.finalDoor.equals(openDoors.put(g.id, g.finalDoor))) {
			announceFinalDoor(g);
		}
	}

	/** Zones only show in the game master's view: nobody else gets anything when a player changes zone. */
	@Override
	public void zonesChanged(Game g) {
		Audience audience = Audience.of(g);
		audiences.put(g.hub, audience);
		for (UUID account : audience.gameMasters()) {
			if (Views.kindOf(g, account) == Views.Kind.GM || g.indexOf(account) >= 0) { // D12: a playing game master too
				sendView(g, account);
			}
		}
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
				sessions.send(account, WsMessage.of(ROOM, data(rooms.roomView(g.hub, room.round, room.group, kind))));
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
		sendView(g, account, g.phase == com.mystaria.phantasmon_backend.events.trahison.game.Phase.LOBBY ? lobbyMembers(g) : null);
	}

	/** D75: who is an admin, to show the draw to the admins who run the lobby without being game masters. */
	private volatile java.util.function.Predicate<UUID> admins = a -> false;

	void useAdmins(java.util.function.Predicate<UUID> admins) {
		this.admins = admins;
	}

	private void sendView(Game g, UUID account, List<Object> lobbyMembers) {
		Map<String, Object> view = Views.build(g, account, clock.instant());
		if (g.phase == com.mystaria.phantasmon_backend.events.trahison.game.Phase.LOBBY && view.get("gm") == null && admins.test(account)) {
			view.put("lobby_draw", Views.drawBlock(g)); // D75: the admins who set the lobby up see the draw too
		}
		if (lobbyMembers != null) {
			view.put("lobby_members", lobbyMembers);
		}
		sessions.send(account, WsMessage.of(VIEW, view));
	}

	private void sendViews(Game g) {
		List<Object> members = g.phase == com.mystaria.phantasmon_backend.events.trahison.game.Phase.LOBBY ? lobbyMembers(g) : null;
		for (UUID account : recipients(g)) {
			sendView(g, account, members);
		}
	}

	/**
	 * The accounts around the lobby who are not in the game (D7): the people an admin may give the game master role to,
	 * with whether they already have it. Sent to everyone while the game is in its lobby.
	 */
	private List<Object> lobbyMembers(Game g) {
		List<Object> out = new java.util.ArrayList<>();
		for (UUID account : recipients(g)) {
			if (g.indexOf(account) >= 0) {
				continue;
			}
			Map<String, Object> m = new java.util.LinkedHashMap<>();
			m.put("uuid", account.toString());
			m.put("name", g.gameMasterNames.getOrDefault(account, names.apply(account)));
			m.put("game_master", g.gameMasters.contains(account));
			out.add(m);
		}
		return out;
	}

	/** D2: the hostess says who did not pass any colour door and died of it (once, when the doors have closed). */
	private void announceDoorDeaths(Game g) {
		var r = g.currentRound();
		if (g.phase != com.mystaria.phantasmon_backend.events.trahison.game.Phase.ROOMS || r == null || r.diedAtDoors.isEmpty()) {
			return;
		}
		List<String> who = new java.util.ArrayList<>();
		for (int i : r.diedAtDoors) {
			who.add(g.players.get(i).name);
		}
		Map<String, Object> say = new java.util.LinkedHashMap<>();
		say.put("kind", "announce");
		say.put("key", SAY_KEY + "doors_dead");
		say.put("args", List.of(String.join(", ", who)));
		say.put("phase", g.phase.name());
		say.put("round", g.round);
		for (UUID account : recipients(g)) {
			sessions.send(account, WsMessage.of(SAY, say));
		}
	}

	/** T2.5: the hostess says that the final door is open, who opened it, and for how long. */
	private void announceFinalDoor(Game g) {
		Map<String, Object> say = new java.util.LinkedHashMap<>();
		say.put("kind", "announce");
		say.put("key", SAY_KEY + "final_door_open");
		say.put("args", List.of(g.players.get(g.finalDoor.openedBy()).name, String.valueOf(g.settings.finalDoorSeconds)));
		say.put("phase", g.phase.name());
		say.put("round", g.round);
		for (UUID account : recipients(g)) {
			sessions.send(account, WsMessage.of(SAY, say));
		}
	}

	/** D12: the game ended because the others killed the game master who played; his identity is now public. */
	private void announceGameMasterKilled(Game g) {
		List<String> who = new java.util.ArrayList<>();
		for (int i : g.playingGameMasters()) {
			who.add(g.players.get(i).name);
		}
		Map<String, Object> say = new java.util.LinkedHashMap<>();
		say.put("kind", "announce");
		say.put("key", SAY_KEY + "game_master_killed");
		say.put("args", List.of(String.join(", ", who)));
		say.put("phase", g.phase.name());
		say.put("round", g.round);
		for (UUID account : recipients(g)) {
			sessions.send(account, WsMessage.of(SAY, say));
		}
	}

	/** T2.5: once the game has ended through the final door, who walked out (or that nobody did). */
	private void announceEscape(Game g) {
		boolean any = !g.winners.isEmpty();
		List<String> who = new java.util.ArrayList<>();
		for (int i : g.winners) {
			who.add(g.players.get(i).name);
		}
		Map<String, Object> say = new java.util.LinkedHashMap<>();
		say.put("kind", "announce");
		say.put("key", SAY_KEY + (any ? "final_door_escaped" : "final_door_nobody"));
		say.put("args", any ? List.of(String.join(", ", who)) : List.of());
		say.put("phase", g.phase.name());
		say.put("round", g.round);
		for (UUID account : recipients(g)) {
			sessions.send(account, WsMessage.of(SAY, say));
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
