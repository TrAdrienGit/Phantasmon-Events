package com.mystaria.phantasmon_backend.events.trahison;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
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
import com.mystaria.phantasmon_backend.events.trahison.room.RoomAction;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomService;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomValidator;
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
 *   <li>{@code EventsTrahisonAction}: what a player (or a spectator) does: join, leave, pass_door, vote, open_final_door,
 *       debug_solve, view, and the room actions use_slot, puzzle_input, pick_up;</li>
 *   <li>{@code EventsTrahisonGm}: what the game master does, admins only.</li>
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

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final SessionRegistry sessionRegistry;
	private final GameService games;
	private final AdminService admins;
	private final PlayerService players;
	private final Broadcaster broadcaster;
	private volatile HubGate hubGate;

	public TrahisonWsHandler(SessionRegistry sessionRegistry, GameService games, AdminService admins, PlayerService players,
			HubGate hubGate, RoomService rooms) {
		this.sessionRegistry = sessionRegistry;
		this.games = games;
		this.admins = admins;
		this.players = players;
		this.hubGate = hubGate;
		this.broadcaster = new Broadcaster(sessionRegistry, Clock.systemUTC(), () -> this.hubGate, rooms);
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
			case "open_final_door" -> games.openFinalDoor(hubOf(who, in, false), who);
			case "debug_solve" -> games.debugSolve(hubOf(who, in, false), who);
			case "use_slot", "puzzle_input", "pick_up" -> {
				ObjectNode data = MAPPER.valueToTree(in);
				data.put("action", action);
				games.roomAction(hubOf(who, in, false), who, RoomAction.parse(data));
			}
			default -> throw new TrahisonException(TrahisonException.UNKNOWN_ACTION, String.valueOf(action));
		}
	}

	// ------------------------------------------------------------------ game master

	private void gm(UUID who, Map<String, Object> in) {
		if (!admins.isAdmin(who)) {
			throw new TrahisonException(TrahisonException.ADMIN_REQUIRED, "game master actions are for admins");
		}
		String action = text(in, "action");
		String hub = gmHub(who, in);
		if ("create".equals(action)) {
			broadcaster.watch(hub, who);
			Long seed = in.get("seed") instanceof Number n ? n.longValue() : null;
			games.create(hub, settingsOf(in), seed);
			games.addGameMaster(hub, who);
			return;
		}
		broadcaster.watch(hub, who);
		games.addGameMaster(hub, who);
		switch (action) {
			case "start" -> games.start(hub);
			case "pause" -> games.pause(hub);
			case "resume" -> games.resume(hub);
			case "next" -> games.next(hub);
			case "end" -> games.end(hub);
			case "extend" -> games.extend(hub, intArg(in, "seconds"));
			case "set_points" -> games.setPoints(hub, intArg(in, "bracelet"), intArg(in, "points"));
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
			case "set_bot_policy" -> games.setBotPolicy(hub, BotPolicy.parse(in.get("policy")));
			default -> throw new TrahisonException(TrahisonException.UNKNOWN_ACTION, String.valueOf(action));
		}
	}

	/** The hub a game master message is about: the {@code hub} field, else the hub of the game he already runs. */
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

	/** {@code {settings?: {mode, durations: {PHASE: seconds}, no_door_penalty, test_room}, mode?, test_room?, fast?: seconds}}. */
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
			if (m.get("no_door_penalty") instanceof Number n) {
				s.noDoorPenalty = Math.min(0, n.intValue());
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
		Object v = in.get(key);
		if (!(v instanceof String s) || s.isBlank()) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, key + " required");
		}
		return s.trim().toLowerCase(Locale.ROOT);
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
