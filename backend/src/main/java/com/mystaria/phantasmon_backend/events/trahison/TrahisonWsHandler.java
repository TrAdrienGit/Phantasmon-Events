package com.mystaria.phantasmon_backend.events.trahison;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.addon.WsMessageHandler;
import com.mystaria.phantasmon_backend.admin.AdminService;
import com.mystaria.phantasmon_backend.events.trahison.game.Bots;
import com.mystaria.phantasmon_backend.events.trahison.game.BotPolicy;
import com.mystaria.phantasmon_backend.events.trahison.game.Game;
import com.mystaria.phantasmon_backend.events.trahison.game.GameService;
import com.mystaria.phantasmon_backend.events.trahison.game.Mode;
import com.mystaria.phantasmon_backend.events.trahison.game.Phase;
import com.mystaria.phantasmon_backend.events.trahison.game.Settings;
import com.mystaria.phantasmon_backend.events.trahison.game.TrahisonException;
import com.mystaria.phantasmon_backend.events.trahison.game.Views;
import com.mystaria.phantasmon_backend.events.trahison.map.MapNotesService;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomAction;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomService;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomValidator;
import com.mystaria.phantasmon_backend.events.trahison.rules.Item;
import com.mystaria.phantasmon_backend.events.trahison.rules.Role;
import com.mystaria.phantasmon_backend.events.trahison.rules.Vote;
import com.mystaria.phantasmon_backend.player.Player;
import com.mystaria.phantasmon_backend.player.PlayerService;
import com.mystaria.phantasmon_backend.websocket.SessionRegistry;
import com.mystaria.phantasmon_backend.websocket.WsMessage;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The betrayal game's WebSocket messages (Documentation/trahison/protocole.md), a second {@link WsMessageHandler} next
 * to {@code EventsWsHandler}. It takes every type starting with {@code EventsTrahison}:
 * <ul>
 *   <li>{@code EventsTrahisonPing}: answered by a Pong;</li>
 *   <li>{@code EventsTrahisonAction}: what a player (or a spectator) does: join, leave, pass_door, close_cabin, vote, open_final_door,
 *       debug_solve, view, and the room actions use_slot, puzzle_input, pick_up and the inventory's give_item, use_item, combine;</li>
 *   <li>{@code EventsTrahisonGm}: what the game masters do (D7: the accounts assigned in the lobby), and what an admin does
 *       in the lobby of a game (create it, add bots, assign game masters, start);</li>
 *   <li>{@code EventsTrahisonZone}: where a player stands (zone and position), sent by his client.</li>
 * </ul>
 * A refused request is answered to its sender only, by an {@code EventsTrahisonSay} of kind {@code error}. Views go out
 * from {@link Broadcaster}. The hub of a request is the hub of the game its sender plays in, else the {@code hub} field.
 * There is no connect callback for add-ons: any message from a player of a game tells the game he is connected again
 * (the client sends a {@code view} action when it connects).
 */
@Component
public class TrahisonWsHandler implements WsMessageHandler {

	private static final Logger log = LoggerFactory.getLogger(TrahisonWsHandler.class);

	static final String PREFIX = "EventsTrahison";
	static final String PING = "EventsTrahisonPing";
	static final String PONG = "EventsTrahisonPong";
	static final String ACTION = "EventsTrahisonAction";
	static final String GM = "EventsTrahisonGm";
	static final String ZONE = "EventsTrahisonZone";
	static final String MAP = "EventsTrahisonMap";

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final SessionRegistry sessionRegistry;
	private final GameService games;
	private final AdminService admins;
	private final PlayerService players;
	private final Broadcaster broadcaster;
	private volatile HubGate hubGate;
	private final MapNotesService mapNotes;

	public TrahisonWsHandler(SessionRegistry sessionRegistry, GameService games, AdminService admins, PlayerService players,
			HubGate hubGate, RoomService rooms, MapNotesService mapNotes) {
		this.sessionRegistry = sessionRegistry;
		this.games = games;
		this.admins = admins;
		this.players = players;
		this.hubGate = hubGate;
		this.mapNotes = mapNotes;
		this.broadcaster = new Broadcaster(sessionRegistry, Clock.systemUTC(), () -> this.hubGate, rooms, this::nameOf);
		broadcaster.useAdmins(admins::isAdmin);
		games.addListener(broadcaster);
		rooms.addListener(broadcaster);
	}

	/** Test seam: replaces how hubs and their members are looked up. */
	void useHubGate(HubGate gate) {
		this.hubGate = gate;
	}

	@Override
	public boolean handles(String type) {
		return type != null && type.startsWith(PREFIX);
	}

	@Override
	public void handle(UUID playerUuid, String type, Map<String, Object> data) {
		Map<String, Object> in = data == null ? Map.of() : data;
		if (PING.equals(type)) {
			Object nonce = in.get("nonce");
			Map<String, Object> reply = nonce == null ? Map.of() : Map.of("nonce", nonce);
			sessionRegistry.send(playerUuid, WsMessage.of(PONG, reply));
			return;
		}
		try {
			if (ACTION.equals(type)) {
				action(playerUuid, in);
			} else if (GM.equals(type)) {
				gm(playerUuid, in);
			} else if (ZONE.equals(type)) {
				zone(playerUuid, in);
			} else if (MAP.equals(type)) {
				map(playerUuid, in);
			}
			// Later steps add their messages here; an unknown one is ignored.
		} catch (TrahisonException ex) {
			error(playerUuid, ex.code());
		} catch (RuntimeException ex) {
			log.warn("Trahison message {} from {} failed", type, playerUuid, ex);
			error(playerUuid, TrahisonException.BAD_ARGUMENT);
		}
	}

	@Override
	public void onDisconnect(UUID playerUuid) {
		broadcaster.forget(playerUuid);
		Game g = games.findByPlayer(playerUuid);
		if (g != null) {
			games.setConnected(g.hub, playerUuid, false);
		}
	}

	// ------------------------------------------------------------------ players

	private void action(UUID who, Map<String, Object> in) {
		String action = text(in, "action");
		Game mine = games.findByPlayer(who);
		if (mine != null) {
			games.setConnected(mine.hub, who, true); // a message from a dropped player: he is back
		}
		switch (action) {
			case "view" -> {
				String hub = hubOf(who, in, true);
				broadcaster.watch(hub, who);
				if (games.find(hub) == null) {
					sessionRegistry.send(who, WsMessage.of(Broadcaster.VIEW, Views.none(hub)));
					return;
				}
				games.read(hub, g -> {
					broadcaster.sendView(g, who);
					broadcaster.sendRooms(g, who);
					return null;
				});
			}
			case "join" -> {
				String hub = hubOf(who, in, true);
				broadcaster.watch(hub, who);
				games.join(hub, who, nameOf(who));
			}
			case "leave" -> games.leave(hubOf(who, in, false), who);
			case "pass_door" -> games.passDoor(hubOf(who, in, false), who, intArg(in, "door"));
			case "vote" -> {
				Vote vote = parseVote(in.get("vote"));
				games.vote(hubOf(who, in, false), who, vote);
			}
			case "close_cabin" -> games.closeCabin(hubOf(who, in, false), who);
			case "open_final_door" -> games.openFinalDoor(hubOf(who, in, false), who);
			case "take_bracelet" -> games.takeBracelet(hubOf(who, in, false), who, intArg(in, "body"),
				in.get("bracelet") instanceof Number n ? n.intValue() : null);
			case "spectate" -> {
				String hub = hubOf(who, in, true);
				broadcaster.watch(hub, who);
				games.spectate(hub, who, nameOf(who), !Boolean.FALSE.equals(in.get("on")));
			}
			case "body_seen" -> games.bodySeen(hubOf(who, in, false), who, intArg(in, "body"));
			// T7: hidden items and secret roles. The room inventory's own "use_item" stays as it was: the secret ones are "use_object".
			case "search" -> games.search(hubOf(who, in, false), who, text(in, "spot", false));
			case "use_object" -> {
				Item item = Item.parse(in.get("item"));
				if (item == null) {
					throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "item required");
				}
				String where = in.get("door") instanceof String d ? d : in.get("cabin") instanceof String c ? c : null;
				games.useObject(hubOf(who, in, false), who, item, in.get("target") instanceof Number n ? n.intValue() : null, where,
					in.get("text") instanceof String t ? t : null);
			}
			case "unlock" -> games.unlock(hubOf(who, in, false), who, in.get("door") instanceof String d ? d : in.get("cabin") instanceof String c ? c : null);
			case "untie" -> games.untie(hubOf(who, in, false), who, intArg(in, "target"));
			case "foresee" -> {
				String side = text(in, "side", true);
				if (!side.equals("pair") && !side.equals("solo")) {
					throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "side: pair or solo");
				}
				games.foresee(hubOf(who, in, false), who, intArg(in, "group"), side.equals("pair"));
			}
			case "bet" -> games.bet(hubOf(who, in, false), who, parseVote(in.get("vote")));
			case "debug_solve" -> games.debugSolve(hubOf(who, in, false), who);
			case "use_slot", "puzzle_input", "pick_up", "give_item", "use_item", "combine", "replay" -> {
				ObjectNode data = MAPPER.valueToTree(in);
				data.put("action", action);
				games.roomAction(hubOf(who, in, false), who, RoomAction.parse(data), RoomAction.targetGroup(data), RoomAction.targetRound(data));
			}
			default -> throw new TrahisonException(TrahisonException.UNKNOWN_ACTION, String.valueOf(action));
		}
	}

	/**
	 * {@code EventsTrahisonZone {zone, x, y, z}}: where a player stands (zone id of the plan or empty, hub coordinates).
	 * Not a request: nothing is answered, a message from a non-player or with bad numbers is dropped, and the database is
	 * not written. A message from a dropped player tells the game he is back, as any other message.
	 */
	private void zone(UUID who, Map<String, Object> in) {
		Game mine = games.findByPlayer(who);
		if (mine == null || !(in.get("x") instanceof Number x) || !(in.get("y") instanceof Number y)
				|| !(in.get("z") instanceof Number z) || !finite(x) || !finite(y) || !finite(z)) {
			return;
		}
		String zone = in.get("zone") instanceof String s && s.length() <= 64 ? s : null;
		games.setConnected(mine.hub, who, true);
		games.setZone(mine.hub, who, zone, x.doubleValue(), y.doubleValue(), z.doubleValue());
	}

	/**
	 * {@code EventsTrahisonMap {op, hub?, ...}} (T11.4): the sender's own strokes and notes on the map. {@code get} answers
	 * {@code state}; {@code set {strokes, notes}} saves (answers {@code saved}); {@code all}, once the game has ended, answers
	 * everybody's ({@code all}, D15). Anyone around the game may annotate: a player, a ghost, a game master, a spectator.
	 */
	private void map(UUID who, Map<String, Object> in) {
		String op = text(in, "op");
		String hub = hubOf(who, in, true);
		if (games.find(hub) == null) {
			throw new TrahisonException(TrahisonException.NO_GAME, "no game");
		}
		UUID gameId = games.read(hub, g -> g.id);
		Phase phase = games.read(hub, g -> g.phase);
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("hub", hub);
		switch (op) {
			case "get" -> {
				MapNotesService.Notes mine = mapNotes.get(gameId, who);
				out.put("op", "state");
				out.put("strokes", mine.strokes());
				out.put("notes", mine.notes());
			}
			case "set" -> {
				if (phase == Phase.ENDED) {
					throw new TrahisonException(TrahisonException.GAME_ENDED, "the game is over");
				}
				mapNotes.save(gameId, who, in.get("strokes"), in.get("notes"));
				out.put("op", "saved");
			}
			case "all" -> {
				if (phase != Phase.ENDED) {
					throw new TrahisonException(TrahisonException.WRONG_PHASE, "the game is not over");
				}
				List<Object> people = new java.util.ArrayList<>();
				for (MapNotesService.Row row : mapNotes.all(gameId)) {
					Map<String, Object> p = new LinkedHashMap<>();
					p.put("account", row.account().toString());
					p.put("name", nameOf(row.account()));
					p.put("index", games.read(hub, g -> g.indexOf(row.account())));
					p.put("strokes", row.notes().strokes());
					p.put("notes", row.notes().notes());
					people.add(p);
				}
				out.put("op", "all");
				out.put("players", people);
			}
			default -> throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "op: get, set or all");
		}
		sessionRegistry.send(who, WsMessage.of(MAP, out));
	}

	private static boolean finite(Number n) {
		return Double.isFinite(n.doubleValue());
	}

	// ------------------------------------------------------------------ game master

	private void gm(UUID who, Map<String, Object> in) {
		String action = text(in, "action");
		boolean admin = admins.isAdmin(who);
		String hub;
		try {
			hub = gmHub(who, in);
		} catch (TrahisonException ex) {
			// someone who is no admin and runs no game gets the same answer whatever the hub: he is not a game master
			throw !admin && TrahisonException.NO_HUB.equals(ex.code())
				? new TrahisonException(TrahisonException.NOT_GAME_MASTER, "not a game master") : ex;
		}
		if ("create".equals(action)) {
			if (!admin) {
				throw new TrahisonException(TrahisonException.ADMIN_REQUIRED, "only admins create a game");
			}
			broadcaster.watch(hub, who);
			Long seed = in.get("seed") instanceof Number n ? n.longValue() : null;
			games.create(hub, settingsOf(in), seed);
			return; // nobody is game master by default (D7): the admin assigns them in the lobby
		}
		// D7: the game's assigned game masters, and in the lobby any admin (who sets the game up: bots, start, game masters)
		if (games.find(hub) == null) {
			throw new TrahisonException(TrahisonException.NO_GAME, "no game in hub " + hub);
		}
		boolean allowed = games.read(hub, g -> g.gameMasters.contains(who) || admin && g.phase == Phase.LOBBY);
		if (!allowed) {
			throw new TrahisonException(TrahisonException.NOT_GAME_MASTER, "not a game master of this game");
		}
		broadcaster.watch(hub, who);
		switch (action) {
			case "start" -> games.start(hub);
			case "pause" -> games.pause(hub);
			case "resume" -> games.resume(hub);
			case "next" -> games.next(hub);
			case "end" -> games.end(hub);
			case "extend" -> games.extend(hub, intArg(in, "seconds"));
			case "set_door" -> {
				if (!(in.get("open") instanceof Boolean open)) {
					throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "open required (true or false)");
				}
				games.setDoor(hub, doorId(in), open);
			}
			case "set_points" -> games.setPoints(hub, intArg(in, "bracelet"), intArg(in, "points"), who);
			case "set_game_master" -> {
				if (!admin) {
					throw new TrahisonException(TrahisonException.ADMIN_REQUIRED, "only admins assign game masters");
				}
				if (!(in.get("on") instanceof Boolean on)) {
					throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "on required (true or false)");
				}
				UUID account;
				try {
					account = UUID.fromString(String.valueOf(in.get("account")));
				} catch (IllegalArgumentException ex) {
					throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "account required (a player id)");
				}
				games.setGameMaster(hub, account, nameOf(account), on);
			}
			case "add_bots" -> {
				BotPolicy policy = null;
				if (in.get("policy") != null) {
					policy = BotPolicy.parse(in.get("policy"));
					if (policy == null) {
						throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "policy: ally, betray or random");
					}
				}
				int count = in.get("count") instanceof Number n ? n.intValue() : 0;
				Bots.addBots(games, hub, count);
				if (policy != null) {
					games.setBotPolicy(hub, policy);
				}
			}
			case "redraw" -> games.redraw(hub, in.get("seed") instanceof Number n ? n.longValue() : null);
			case "replace_room" -> {
				if (!(in.get("room") instanceof String id) || !RoomValidator.validId(id)) {
					throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "room required (a room id)");
				}
				games.replaceRoom(hub, intArg(in, "round"), intArg(in, "group"), id);
			}
			case "set_bot_policy" -> games.setBotPolicy(hub, BotPolicy.parse(in.get("policy")));
			default -> throw new TrahisonException(TrahisonException.UNKNOWN_ACTION, String.valueOf(action));
		}
	}

	/** The hub a game master message is about: the {@code hub} field, else the hub of the game he is assigned to. */
	private String gmHub(UUID who, Map<String, Object> in) {
		if (in.get("hub") instanceof String name && !name.isBlank()) {
			return hubGate.resolve(name).orElseThrow(
				() -> new TrahisonException(TrahisonException.NO_HUB, "no such mirror hub: " + name));
		}
		for (Game g : games.games()) {
			if (g.phase != Phase.ENDED && g.gameMasters.contains(who)) {
				return g.hub;
			}
		}
		throw new TrahisonException(TrahisonException.NO_HUB, "hub required");
	}

	/** {@code {settings?: {mode, durations: {PHASE: seconds}, door_open_seconds, final_door_seconds, cabin_partner_seconds, cabins_required, test_room, draw_max_spread, draw_rise, draw_avoid_games}, mode?, test_room?, fast?: seconds}}. */
	private static Settings settingsOf(Map<String, Object> in) {
		Settings s = new Settings();
		Object fast = in.get("fast");
		if (fast instanceof Number n && n.intValue() > 0) {
			s.allDurations(n.intValue());
		} else if (Boolean.TRUE.equals(fast)) {
			s.allDurations(10);
		}
		Object raw = in.get("settings");
		if (raw != null) {
			if (!(raw instanceof Map<?, ?> m)) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "settings must be an object");
			}
			if (m.get("mode") != null) {
				s.mode = parseMode(m.get("mode"));
			}
			if (m.get("cabin_partner_seconds") instanceof Number n) {
				s.cabinPartnerSeconds = Math.max(1, Math.min(300, n.intValue()));
			}
			if (m.get("cabins_required") instanceof Boolean b) {
				s.cabinsRequired = b;
			}
			if (m.get("door_open_seconds") instanceof Number n) {
				s.doorOpenSeconds = Math.max(1, Math.min(60, n.intValue()));
			}
			if (m.get("final_door_seconds") instanceof Number n) {
				s.finalDoorSeconds = Math.max(1, Math.min(300, n.intValue()));
			}
			if (m.get("announce_deaths") instanceof Boolean b) {
				s.announceDeaths = b;
			}
			if ("HIDDEN".equals(m.get("ghost_display"))) {
				s.ghostDisplay = Settings.GhostDisplay.HIDDEN;
			}
			if (m.get("roles") instanceof java.util.List<?> names) {
				s.roles = new java.util.ArrayList<>();
				for (Object name : names) {
					Role role = Role.parse(name);
					if (role == null || role == Role.GAME_MASTER) {
						throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "unknown role " + name);
					}
					s.roles.add(role.name());
				}
			}
			if (m.get("role_count") instanceof Number n) {
				s.roleCount = Math.max(0, Math.min(6, n.intValue()));
			}
			if (m.get("items") instanceof Map<?, ?> counts) {
				for (Map.Entry<?, ?> e : counts.entrySet()) {
					Item item = Item.parse(e.getKey());
					if (item == null || !(e.getValue() instanceof Number n)) {
						throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "bad item count for " + e.getKey());
					}
					s.items.put(item, Math.max(0, Math.min(2, n.intValue())));
				}
			}
			if (m.get("bonds_seconds") instanceof Number n) {
				s.bondsSeconds = Math.max(5, Math.min(1800, n.intValue()));
			}
			if (m.get("lock_seconds") instanceof Number n) {
				s.lockSeconds = Math.max(5, Math.min(1800, n.intValue()));
			}
			if (m.get("auto_pause_seconds") instanceof Number n) {
				s.autoPauseSeconds = Math.max(1, Math.min(1800, n.intValue()));
			}
			if (m.get("draw_max_spread") instanceof Number n) {
				s.drawMaxSpread = Math.max(0, Math.min(20, n.intValue()));
			}
			if (m.get("draw_rise") instanceof Number n) {
				s.drawRise = Math.max(0, Math.min(20, n.intValue()));
			}
			if (m.get("draw_avoid_games") instanceof Number n) {
				s.drawAvoidGames = Math.max(0, Math.min(20, n.intValue()));
			}
			if (m.get("durations") instanceof Map<?, ?> d) {
				for (Map.Entry<?, ?> e : d.entrySet()) {
					Phase phase;
					try {
						phase = Phase.valueOf(String.valueOf(e.getKey()).toUpperCase(Locale.ROOT));
					} catch (IllegalArgumentException ex) {
						throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "unknown stage " + e.getKey());
					}
					if (!phase.timed() || !(e.getValue() instanceof Number sec) || sec.intValue() < 1) {
						throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "bad duration for " + e.getKey());
					}
					s.durations.put(phase, sec.intValue());
				}
			}
		}
		if (in.get("mode") != null) {
			s.mode = parseMode(in.get("mode"));
		}
		Object testRoom = in.get("test_room") != null ? in.get("test_room")
			: in.get("settings") instanceof Map<?, ?> m ? m.get("test_room") : null;
		if (testRoom != null) {
			if (!(testRoom instanceof String id) || !RoomValidator.validId(id)) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "test_room: a room id");
			}
			s.testRoom = id;
		}
		return s;
	}

	private static Mode parseMode(Object value) {
		try {
			return Mode.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "mode: AUTO or GAME_MASTER");
		}
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * The hub of a request: the game the sender plays in; else the {@code hub} field (an existing mirror hub). With
	 * {@code mustBeMember} the sender must be in a hub (join, or asking for a view as a spectator).
	 */
	private String hubOf(UUID who, Map<String, Object> in, boolean mustBeMember) {
		Game mine = games.findByPlayer(who);
		if (mine != null) {
			return mine.hub;
		}
		if (!(in.get("hub") instanceof String name) || name.isBlank()) {
			throw new TrahisonException(TrahisonException.NO_HUB, "hub required");
		}
		String hub = hubGate.resolve(name).orElseThrow(
			() -> new TrahisonException(TrahisonException.NO_HUB, "no such mirror hub: " + name));
		if (mustBeMember && !hubGate.isMember(who)) {
			throw new TrahisonException(TrahisonException.NOT_IN_HUB, "join the hub first");
		}
		return hub;
	}

	private String nameOf(UUID who) {
		return players.findById(who).map(Player::getLastUsername).orElse("Player " + who.toString().substring(0, 8));
	}

	private static String text(Map<String, Object> in, String key) {
		return text(in, key, true);
	}

	private static String text(Map<String, Object> in, String key, boolean lower) {
		Object v = in.get(key);
		if (!(v instanceof String s) || s.isBlank()) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, key + " required");
		}
		return lower ? s.trim().toLowerCase(Locale.ROOT) : s.trim();
	}

	private static String doorId(Map<String, Object> in) {
		if (!(in.get("door") instanceof String id) || id.isBlank()) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "door required");
		}
		return id.trim();
	}

	private static int intArg(Map<String, Object> in, String key) {
		if (!(in.get(key) instanceof Number n)) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, key + " required");
		}
		return n.intValue();
	}

	private static Vote parseVote(Object value) {
		try {
			return Vote.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "vote: ally or betray");
		}
	}

	private void error(UUID to, String code) {
		Map<String, Object> say = new LinkedHashMap<>();
		say.put("kind", "error");
		say.put("code", code);
		sessionRegistry.send(to, WsMessage.of(Broadcaster.SAY, say));
	}
}
