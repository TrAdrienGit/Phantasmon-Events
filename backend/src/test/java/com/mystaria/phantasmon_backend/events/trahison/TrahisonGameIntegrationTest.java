package com.mystaria.phantasmon_backend.events.trahison;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import com.mystaria.phantasmon_backend.TestcontainersConfiguration;
import com.mystaria.phantasmon_backend.events.trahison.game.Game;
import com.mystaria.phantasmon_backend.events.trahison.game.GameService;
import com.mystaria.phantasmon_backend.events.trahison.game.Phase;
import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;
import com.mystaria.phantasmon_backend.events.trahison.room.Give;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomCatalog;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomDefinition;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomService;
import com.mystaria.phantasmon_backend.events.trahison.room.Slot;
import com.mystaria.phantasmon_backend.events.trahison.room.Step;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;
import com.mystaria.phantasmon_backend.player.PlayerService;
import com.mystaria.phantasmon_backend.websocket.SessionRegistry;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A whole game through the WebSocket handler (T1.3): two clients, seven bots, a game master, a spectator. The game master
 * drives the stages ({@code GAME_MASTER} mode, short stages so the bots act within a second or two). Checks the views
 * received and that nobody gets a vote of the round in progress before its results.
 */
@Import(TestcontainersConfiguration.class)
// Same properties as the other integration tests: one shared Spring context.
@SpringBootTest(properties = { "phantasmon.logging.enabled=false", "phantasmon.admin.file=build/test-admins.txt" })
class TrahisonGameIntegrationTest {

	private static final Path ADMIN_FILE = Path.of("build/test-admins.txt");

	@Autowired
	private TrahisonWsHandler handler;
	@Autowired
	private SessionRegistry sessionRegistry;
	@Autowired
	private GameService games;
	@Autowired
	private PlayerService playerService;
	@Autowired
	private HubGate realGate;
	@Autowired
	private RoomService rooms;
	@Autowired
	private RoomCatalog catalog;
	@Autowired
	private ObjectMapper mapper;
	@Autowired
	private com.mystaria.phantasmon_backend.events.trahison.layout.LayoutService layouts;

	private String hub;
	private Client a;
	private Client b;
	private Client admin;
	private Client spectator;
	private Client nobody;

	/** A mock connection that keeps what it was sent. */
	private final class Client {
		final UUID uuid = UUID.randomUUID();
		final List<String> messages = Collections.synchronizedList(new ArrayList<>());
		final WebSocketSession session = mock(WebSocketSession.class);

		Client(String name) throws Exception {
			playerService.recordConnection(uuid, name);
			when(session.isOpen()).thenReturn(true);
			doAnswer(call -> {
				messages.add(((TextMessage) call.<WebSocketMessage<?>>getArgument(0)).getPayload());
				return null;
			}).when(session).sendMessage(any());
			sessionRegistry.register(uuid, session);
		}

		void send(String type, String json) {
			Map<String, Object> data = mapper.readValue(json, Map.class);
			handler.handle(uuid, type, data);
		}

		void act(String json) {
			send("EventsTrahisonAction", json);
		}

		void gm(String json) {
			send("EventsTrahisonGm", json);
		}

		List<JsonNode> ofType(String type) {
			List<JsonNode> out = new ArrayList<>();
			synchronized (messages) {
				for (String m : messages) {
					JsonNode n = mapper.readTree(m);
					if (type.equals(n.get("type").asString())) {
						out.add(n.get("data"));
					}
				}
			}
			return out;
		}

		List<JsonNode> views() {
			return ofType("EventsTrahisonView");
		}

		JsonNode view() {
			List<JsonNode> v = views();
			return v.isEmpty() ? null : v.get(v.size() - 1);
		}

		/** Codes of the errors received. */
		List<String> errors() {
			List<String> out = new ArrayList<>();
			for (JsonNode say : ofType("EventsTrahisonSay")) {
				if ("error".equals(say.get("kind").asString())) {
					out.add(say.get("code").asString());
				}
			}
			return out;
		}

		int index() {
			for (JsonNode p : view().get("players")) {
				if (p.get("uuid").asString().equals(uuid.toString())) {
					return p.get("index").asInt();
				}
			}
			return -1;
		}
	}

	@BeforeEach
	void setUp() throws Exception {
		hub = "t_" + UUID.randomUUID().toString().substring(0, 8);
		String adminName = "Op" + UUID.randomUUID().toString().substring(0, 8);
		admin = new Client(adminName);
		a = new Client("Pa" + UUID.randomUUID().toString().substring(0, 8));
		b = new Client("Pb" + UUID.randomUUID().toString().substring(0, 8));
		spectator = new Client("Sp" + UUID.randomUUID().toString().substring(0, 8));
		nobody = new Client("No" + UUID.randomUUID().toString().substring(0, 8)); // in no hub
		Files.createDirectories(ADMIN_FILE.getParent());
		Files.writeString(ADMIN_FILE, adminName + "\n");
		Files.setLastModifiedTime(ADMIN_FILE, FileTime.from(Instant.now().plusSeconds((long) (Math.random() * 1000) + 1)));
		Set<UUID> inHub = Set.of(a.uuid, b.uuid, spectator.uuid);
		handler.useHubGate(new HubGate() {
			@Override
			public Optional<String> resolve(String name) {
				return hub.equalsIgnoreCase(name) ? Optional.of(hub) : Optional.empty();
			}

			@Override
			public boolean isMember(UUID account) {
				return inHub.contains(account);
			}

			@Override
			public Set<UUID> membersWith(UUID account) {
				return inHub.contains(account) ? inHub : Set.of();
			}
		});
	}

	@AfterEach
	void tearDown() {
		handler.useHubGate(realGate);
		for (Client c : List.of(admin, a, b, spectator, nobody)) {
			sessionRegistry.unregister(c.uuid, c.session);
		}
	}

	// ------------------------------------------------------------------ helpers

	private void await(String what, BooleanSupplier condition) throws InterruptedException {
		long end = System.currentTimeMillis() + 20_000;
		while (!condition.getAsBoolean()) {
			assertThat(System.currentTimeMillis()).as("waiting for " + what).isLessThan(end);
			Thread.sleep(10);
		}
	}

	private <T> T game(java.util.function.Function<Game, T> reader) {
		return games.read(hub, reader);
	}

	private Phase phase() {
		return game(g -> g.phase);
	}

	private void gm(String json) {
		admin.gm("{\"hub\":\"" + hub + "\"," + json.substring(1));
	}

	/** The admin creates the game and, as most tests need a game master, assigns himself in the lobby (D7). */
	private void createGame(String json) {
		gm(json);
		gm("{\"action\":\"set_game_master\",\"account\":\"" + admin.uuid + "\",\"on\":true}");
	}

	private void act(Client c, String json) {
		c.act("{\"hub\":\"" + hub + "\"," + json.substring(1));
	}

	/** The human takes a door with his colour; one of the two such doors agrees with what the bots chose. */
	private void passADoor(Client c) {
		int me = c.index();
		String colour = c.view().get("bracelets").get(me).get("color").asString();
		for (JsonNode door : c.view().get("doors")) {
			boolean has = false;
			for (JsonNode col : door.get("colors")) {
				has |= col.asString().equals(colour);
			}
			if (has) {
				act(c, "{\"action\":\"pass_door\",\"door\":" + door.get("index").asInt() + "}");
				if (game(g -> g.currentRound().passedDoor[me]) >= 0) {
					return;
				}
			}
		}
		throw new AssertionError("no door accepted for " + c.uuid);
	}

	private void toNextStage(Phase expected) throws InterruptedException {
		gm("{\"action\":\"next\"}");
		assertThat(phase()).isEqualTo(expected);
		await("the view of " + expected, () -> expected.name().equals(a.view().get("phase").asString())
			&& expected.name().equals(admin.view().get("phase").asString()));
	}

	/** Round {@code n} up to its results. */
	private void playRound(int n) throws InterruptedException {
		toNextStage(Phase.DOORS);
		assertThat(a.view().get("round").asInt()).isEqualTo(n);
		assertThat(a.view().get("groups").isNull()).isTrue();
		passADoor(a);
		passADoor(b);
		await("every door taken", () -> game(g -> {
			for (int d : g.currentRound().passedDoor) {
				if (d < 0) {
					return false;
				}
			}
			return true;
		}));
		toNextStage(Phase.ROOMS);
		assertThat(a.view().get("groups")).hasSize(3);
		act(a, "{\"action\":\"debug_solve\"}");
		act(b, "{\"action\":\"debug_solve\"}");
		await("every room solved", () -> game(g -> {
			for (boolean s : g.currentRound().roomSolved) {
				if (!s) {
					return false;
				}
			}
			return true;
		}));
		toNextStage(Phase.FREE_TIME);
		toNextStage(Phase.VOTE);
		assertThat(a.view().get("results") == null || a.view().get("results").isNull()
			|| a.view().get("results").get("round").asInt() < n).isTrue();
		// A betrays, B allies; the bots ally (a pair has one vote: the first of its members to vote counts).
		act(a, "{\"action\":\"vote\",\"vote\":\"betray\"}");
		act(b, "{\"action\":\"vote\",\"vote\":\"ally\"}");
		await("every side voted", () -> game(g -> {
			var r = g.currentRound();
			for (int k = 0; k < 3; k++) {
				if (r.pairVote[k] == null || r.soloVote[k] == null) {
					return false;
				}
			}
			return true;
		}));
		await("the game master sees the votes", () -> {
			JsonNode votes = admin.view().get("gm").get("votes");
			return votes.size() == 3 && !votes.get(0).get("pair_vote").isNull() && !votes.get(2).get("solo_vote").isNull();
		});
		toNextStage(Phase.RESULTS);
		JsonNode results = a.view().get("results");
		assertThat(results.get("round").asInt()).isEqualTo(n);
		assertThat(results.get("votes")).hasSize(3);
		assertThat(results.get("deltas")).hasSize(9);
		assertThat(a.view().get("gm").isNull()).isTrue();
	}

	// ------------------------------------------------------------------ tests

	@Test
	void twoClientsAndSevenBotsPlayAFullGameThroughTheHandler() throws Exception {
		spectator.act("{\"action\":\"view\",\"hub\":\"" + hub + "\"}");
		assertThat(spectator.view().get("phase").asString()).isEqualTo("NONE"); // no game yet

		createGame("{\"action\":\"create\",\"mode\":\"GAME_MASTER\",\"fast\":2,\"seed\":7,\"settings\":{\"cabins_required\":false}}");
		assertThat(admin.view().get("phase").asString()).isEqualTo("LOBBY");
		assertThat(admin.view().get("viewer").get("kind").asString()).isEqualTo("GM");
		assertThat(admin.view().get("gm").get("seed").asLong()).isEqualTo(7L);
		assertThat(spectator.view().get("viewer").get("kind").asString()).isEqualTo("SPECTATOR");
		assertThat(spectator.view().get("gm").isNull()).isTrue();

		act(a, "{\"action\":\"join\"}");
		act(b, "{\"action\":\"join\"}");
		assertThat(a.view().get("players")).hasSize(2);
		assertThat(a.view().get("viewer").get("kind").asString()).isEqualTo("PLAYER");
		assertThat(spectator.view().get("players")).hasSize(2);

		// Refusals: not an admin, nobody's hub, wrong stage, unknown action.
		a.gm("{\"action\":\"start\"}");
		assertThat(a.errors()).containsExactly("ERROR_TRAHISON_NOT_GAME_MASTER");
		nobody.act("{\"action\":\"join\",\"hub\":\"" + hub + "\"}");
		assertThat(nobody.errors()).containsExactly("ERROR_TRAHISON_NOT_IN_HUB");
		a.act("{\"action\":\"vote\",\"vote\":\"ally\"}");
		assertThat(a.errors()).contains("ERROR_TRAHISON_WRONG_PHASE");
		a.act("{\"action\":\"dance\"}");
		assertThat(a.errors()).contains("ERROR_TRAHISON_UNKNOWN_ACTION");
		gm("{\"action\":\"start\"}");
		assertThat(admin.errors()).containsExactly("ERROR_TRAHISON_NOT_ENOUGH_PLAYERS");

		gm("{\"action\":\"add_bots\",\"count\":7,\"policy\":\"ally\"}");
		assertThat(admin.view().get("players")).hasSize(9);
		assertThat(admin.view().get("players").get(2).get("is_bot").asBoolean()).isTrue();
		assertThat(admin.view().get("gm").get("bot_policy").asString()).isEqualTo("ALLY");
		gm("{\"action\":\"start\"}");
		assertThat(a.view().get("phase").asString()).isEqualTo("PROLOGUE");
		act(b, "{\"action\":\"join\"}");
		assertThat(b.errors()).containsExactly("ERROR_TRAHISON_NOT_IN_LOBBY");
		assertThat(a.ofType("EventsTrahisonSay").stream().filter(s -> "announce".equals(s.get("kind").asString()))
			.map(s -> s.get("key").asString())).containsExactly("phantasmon_events.trahison.say.phase.lobby",
				"phantasmon_events.trahison.say.phase.prologue");
		assertThat(admin.ofType("EventsTrahisonSay").stream().filter(s -> "announce".equals(s.get("kind").asString()))
			.map(s -> s.get("key").asString())).contains("phantasmon_events.trahison.say.phase.lobby");

		// A player drops and comes back: the game goes on (game master mode), the others see it.
		handler.onDisconnect(a.uuid);
		assertThat(b.view().get("players").get(a.index()).get("connected").asBoolean()).isFalse();
		assertThat(b.view().get("paused").asBoolean()).isFalse();
		a.act("{\"action\":\"view\"}");
		assertThat(b.view().get("players").get(a.index()).get("connected").asBoolean()).isTrue();

		for (int round = 1; round <= 3; round++) {
			playRound(round);
			if (round < 3) {
				continue;
			}
		}
		toNextStage(Phase.FINAL);
		assertThat(a.view().get("round").asInt()).isEqualTo(3);

		// A bot reaches 9 points: it opens the final door by itself and the game ends.
		int rich = a.view().get("players").get(2).get("alive").asBoolean() ? 2 : 3;
		gm("{\"action\":\"set_points\",\"bracelet\":" + rich + ",\"points\":9}");
		await("the final door", () -> phase() == Phase.ENDED);
		await("the end view", () -> "ENDED".equals(b.view().get("phase").asString()));
		JsonNode end = b.view();
		List<Integer> winners = new ArrayList<>();
		end.get("winners").forEach(w -> winners.add(w.asInt()));
		assertThat(winners).contains(rich); // and anyone else who also read 9 (allied bots do)
		for (int w : winners) {
			assertThat(end.get("bracelets").get(w).get("points").asInt()).isGreaterThanOrEqualTo(9);
			assertThat(end.get("players").get(w).get("alive").asBoolean()).isTrue();
		}
		assertThat(end.get("deadline").isNull()).isTrue();

		// Nobody ever saw a vote of the round in progress, and only the game master ever saw the game master block.
		for (Client c : List.of(a, b, spectator)) {
			for (JsonNode v : c.views()) {
				if ("NONE".equals(v.get("phase").asString())) {
					continue;
				}
				assertThat(v.get("gm").isNull()).as("gm block in the view of " + c.uuid).isTrue();
				if (!"VOTE".equals(v.get("phase").asString())) {
					continue;
				}
				int current = v.get("final_loop").asBoolean() ? 3 + v.get("final_loops").asInt() : v.get("round").asInt();
				JsonNode results = v.get("results");
				assertThat(results.isNull() || results.get("round").asInt() < current)
					.as("results of the current round during its vote").isTrue();
				// the results of earlier rounds are public; everything else must hold no vote
				tools.jackson.databind.node.ObjectNode rest = (tools.jackson.databind.node.ObjectNode) v.deepCopy();
				rest.remove("results");
				String text = rest.toString();
				if (c != a) {
					assertThat(text).as("a vote in the view of " + c.uuid).doesNotContain("BETRAY");
				}
			}
		}
		assertThat(admin.views().stream().anyMatch(v -> v.get("gm") != null && !v.get("gm").isNull())).isTrue();
		// Bots never get any message and the log never leaves the backend.
		assertThat(admin.messages.stream().noneMatch(m -> m.contains("\"log\""))).isTrue();
		assertThat(a.views().get(0).get("hub").asString()).isEqualTo(hub);
	}

	@Test
	void theGameMasterCanPauseExtendAndEndAndOnlyAnAdminMay() throws Exception {
		createGame("{\"action\":\"create\",\"mode\":\"GAME_MASTER\",\"fast\":60}");
		gm("{\"action\":\"add_bots\",\"count\":0}");
		assertThat(admin.view().get("players")).hasSize(9);
		gm("{\"action\":\"create\"}");
		assertThat(admin.errors()).containsExactly("ERROR_TRAHISON_GAME_EXISTS");
		gm("{\"action\":\"start\"}");
		gm("{\"action\":\"pause\"}");
		assertThat(admin.view().get("paused").asBoolean()).isTrue();
		assertThat(admin.view().get("remaining_ms").asLong()).isPositive();
		gm("{\"action\":\"extend\",\"seconds\":30}");
		gm("{\"action\":\"resume\"}");
		assertThat(admin.view().get("paused").asBoolean()).isFalse();
		gm("{\"action\":\"set_bot_policy\",\"policy\":\"betray\"}");
		assertThat(admin.view().get("gm").get("bot_policy").asString()).isEqualTo("BETRAY");
		gm("{\"action\":\"set_bot_policy\",\"policy\":\"sometimes\"}");
		assertThat(admin.errors()).contains("ERROR_TRAHISON_BAD_ARGUMENT");
		b.gm("{\"action\":\"end\"}");
		assertThat(b.errors()).containsExactly("ERROR_TRAHISON_NOT_GAME_MASTER");
		gm("{\"action\":\"end\"}");
		assertThat(admin.view().get("phase").asString()).isEqualTo("ENDED");
		assertThat(admin.errors()).doesNotContain("ERROR_TRAHISON_NO_GAME");
		// The game is persisted: the row exists.
		assertThat(this.<Integer>game(g -> g.settings.durations.size())).isGreaterThan(0);
	}

	@Test
	void gameMastersAreAssignedInTheLobbyAndOnlyTheyRunTheGame() throws Exception {
		gm("{\"action\":\"create\",\"mode\":\"GAME_MASTER\",\"fast\":60,\"settings\":{\"cabins_required\":false}}");
		// nobody is game master by default, not even the admin who created the game: he only sees the lobby
		assertThat(admin.view().get("viewer").get("kind").asString()).isEqualTo("SPECTATOR");
		assertThat(admin.view().get("game_masters")).isEmpty();
		spectator.act("{\"action\":\"view\",\"hub\":\"" + hub + "\"}");
		assertThat(spectator.view().get("lobby_members").toString()).contains(spectator.uuid.toString(), a.uuid.toString());
		gm("{\"action\":\"add_bots\",\"count\":0}");
		// game master mode needs one
		gm("{\"action\":\"start\"}");
		assertThat(admin.errors()).containsExactly("ERROR_TRAHISON_NO_GAME_MASTER");
		// only an admin assigns
		b.gm("{\"action\":\"set_game_master\",\"account\":\"" + b.uuid + "\",\"on\":true}");
		assertThat(b.errors()).containsExactly("ERROR_TRAHISON_NOT_GAME_MASTER");
		gm("{\"action\":\"set_game_master\",\"account\":\"" + b.uuid + "\",\"on\":true}");
		gm("{\"action\":\"set_game_master\",\"account\":\"" + spectator.uuid + "\",\"on\":true}");
		await("two game masters", () -> admin.view().get("game_masters").size() == 2 && b.view().get("viewer").get("kind").asString().equals("GM"));
		assertThat(a.view().get("game_masters").toString()).contains(b.uuid.toString());
		assertThat(spectator.view().get("viewer").get("kind").asString()).isEqualTo("GM");
		assertThat(spectator.view().get("gm").isNull()).isFalse();
		// a game master does not play
		act(b, "{\"action\":\"join\"}");
		assertThat(b.errors()).containsExactly("ERROR_TRAHISON_NOT_GAME_MASTER", "ERROR_TRAHISON_GAME_MASTER_PLAYS");
		gm("{\"action\":\"start\"}"); // an admin sets the lobby up and starts
		assertThat(phase()).isEqualTo(Phase.PROLOGUE);
		// the stage moves on for an assigned game master, not for the admin who is none
		gm("{\"action\":\"next\"}");
		assertThat(admin.errors()).containsExactly("ERROR_TRAHISON_NO_GAME_MASTER", "ERROR_TRAHISON_NOT_GAME_MASTER");
		assertThat(phase()).isEqualTo(Phase.PROLOGUE);
		b.gm("{\"action\":\"next\"}");
		assertThat(phase()).isEqualTo(Phase.DOORS);
		spectator.gm("{\"action\":\"pause\"}"); // the second game master, not an admin
		assertThat(this.<Boolean>game(g -> g.paused)).isTrue();
		// roles cannot change once the game runs
		gm("{\"action\":\"set_game_master\",\"account\":\"" + b.uuid + "\",\"on\":false}");
		assertThat(admin.errors()).hasSize(3);
	}

	@Test
	void aGameWithoutAnyGameMasterRunsOnItsOwn() throws Exception {
		gm("{\"action\":\"create\",\"mode\":\"AUTO\",\"fast\":1,\"settings\":{\"cabins_required\":false}}");
		gm("{\"action\":\"add_bots\",\"count\":0}");
		gm("{\"action\":\"start\"}");
		assertThat(this.<Boolean>game(g -> g.gameMasters.isEmpty())).isTrue();
		await("the clock moves the game on", () -> phase() == Phase.DOORS);
		games.end(hub);
	}

	@Test
	void doorsAndZonesGoThroughTheHandler() throws Exception {
		var box = new com.mystaria.phantasmon_backend.events.trahison.layout.Box(0, 64, 0, 0, 66, 2);
		var door = new com.mystaria.phantasmon_backend.events.trahison.layout.LayoutDoor("d_plain", "plain", box, "hall", "hall", null, null);
		var color = new com.mystaria.phantasmon_backend.events.trahison.layout.LayoutDoor("d_color", "color", box, "hall", "hall", 1,
			List.of("red", "green"));
		var zone = new com.mystaria.phantasmon_backend.events.trahison.layout.LayoutZone("hall", "hall", List.of(box));
		int version = layouts.save(admin.uuid, hub, new com.mystaria.phantasmon_backend.events.trahison.layout.LayoutRequest(null, true,
			List.of(zone), List.of(door, color), List.of(), List.of(), List.of())).version();
		createGame("{\"action\":\"create\",\"mode\":\"GAME_MASTER\",\"fast\":60}");
		act(a, "{\"action\":\"join\"}");
		act(b, "{\"action\":\"join\"}");
		gm("{\"action\":\"add_bots\",\"count\":0}");
		gm("{\"action\":\"start\"}");
		await("the started view", () -> a.view() != null && "PROLOGUE".equals(a.view().get("phase").asString()));
		assertThat(a.view().get("layout_version").asInt()).isEqualTo(version);
		assertThat(a.view().get("doors_state").get("d_plain").asString()).isEqualTo("OPEN");
		assertThat(a.view().get("doors_state").get("d_color").asString()).isEqualTo("CLOSED");
		assertThat(a.view().get("viewer").get("can_open_final_door").asBoolean()).isFalse();

		// The game master opens a colour door by hand; every player sees it.
		gm("{\"action\":\"set_door\",\"door\":\"d_color\",\"open\":true}");
		await("the door open", () -> "OPEN".equals(b.view().get("doors_state").get("d_color").asString()));
		gm("{\"action\":\"set_door\",\"door\":\"nope\",\"open\":true}");
		gm("{\"action\":\"set_door\",\"door\":\"d_color\"}");
		assertThat(admin.errors()).containsExactly("ERROR_TRAHISON_BAD_ARGUMENT", "ERROR_TRAHISON_BAD_ARGUMENT");
		b.gm("{\"action\":\"set_door\",\"door\":\"d_color\",\"open\":false}");
		assertThat(b.errors()).containsExactly("ERROR_TRAHISON_NOT_GAME_MASTER");

		// A player's zone: kept for the game master's view, nothing answered, nobody else told.
		int me = a.index();
		int others = b.views().size();
		a.send("EventsTrahisonZone", "{\"zone\":\"hall\",\"x\":1.5,\"y\":65,\"z\":-2}");
		await("the zone in the game master view", () -> {
			JsonNode gmBlock = admin.view().get("gm");
			return gmBlock != null && "hall".equals(gmBlock.get("zones").get(me).get("zone").asString());
		});
		assertThat(admin.view().get("gm").get("zones").get(me).get("x").asDouble()).isEqualTo(1.5);
		assertThat(a.errors()).isEmpty();
		assertThat(b.views().size()).isEqualTo(others);
		assertThat(a.view().has("zones")).isFalse();
		assertThat(a.view().get("gm").isNull()).isTrue();
		// Garbage and strangers are dropped without a word.
		nobody.send("EventsTrahisonZone", "{\"zone\":\"hall\",\"x\":0,\"y\":0,\"z\":0}");
		a.send("EventsTrahisonZone", "{\"zone\":\"hall\",\"x\":\"left\",\"y\":0,\"z\":0}");
		assertThat(nobody.errors()).isEmpty();
		assertThat(a.errors()).isEmpty();
		gm("{\"action\":\"end\"}");
	}

	/** press -> three dials (clue on the wall) -> exit; every step carries a hint text that must never leave the bank. */
	private static RoomDefinition chain(String id) {
		List<String> hints = List.of("secret hint");
		return new RoomDefinition(id, "Chain", "t", 0, 0, 0,
			List.of(new Slot("btn", "terminal", new Pos(2, 1, 2), "north", null),
				new Slot("dials", "terminal", new Pos(10, 1, 4), "north", null),
				new Slot("wall", "wall_clue", new Pos(20, 2, 0), "north", null),
				new Slot("exit", "exit", new Pos(40, 0, 20), "north", null)),
			List.of(new Step("s1", "debug_button", null, java.util.Map.of("press", "btn"), List.of(), List.of(), hints),
				new Step("s2", "three_dials", null, java.util.Map.of("dials", "dials"), List.of("s1"),
					List.of(new Give("code", "wall", null)), hints),
				new Step("s3", "debug_button", null, java.util.Map.of("exit", "exit"), List.of("s2"), List.of(), hints)),
			null);
	}

	@Test
	void aGroupSolvesItsRoomThroughTheHandlerAndNobodyButTheGameMasterSeesASolution() throws Exception {
		String roomId = "chain_" + UUID.randomUUID().toString().substring(0, 8);
		catalog.save(admin.uuid, roomId, chain(roomId));
		try {
			// A long ROOMS stage: the bots only "solve" after at least a tenth of it, long after this test is done.
			createGame("{\"action\":\"create\",\"mode\":\"GAME_MASTER\",\"fast\":2,\"seed\":11,\"test_room\":\"" + roomId
				+ "\",\"settings\":{\"durations\":{\"ROOMS\":600},\"cabins_required\":false}}");
			assertThat(this.<String>game(g -> g.settings.testRoom)).isEqualTo(roomId);
			act(a, "{\"action\":\"join\"}");
			act(b, "{\"action\":\"join\"}");
			gm("{\"action\":\"add_bots\",\"count\":7}");
			gm("{\"action\":\"start\"}");
			toNextStage(Phase.DOORS);
			passADoor(a);
			passADoor(b);
			await("every door taken", () -> game(g -> {
				for (int d : g.currentRound().passedDoor) {
					if (d < 0) {
						return false;
					}
				}
				return true;
			}));
			toNextStage(Phase.ROOMS);
			int me = a.index();
			int group = this.<Integer>game(g -> g.currentRound().groupOf(me));
			await("the rooms reach the spectator", () -> roomGroups(spectator).size() == 3);
			assertThat(roomGroups(a)).contains(group);

			// A group-mate of another room cannot act in it; the actions are refused outside the stage.
			act(a, "{\"action\":\"use_slot\",\"slot\":\"nowhere\"}");
			act(a, "{\"action\":\"puzzle_input\",\"step\":\"s2\",\"input\":{\"dial\":0,\"delta\":1}}");
			assertThat(a.errors()).contains("ERROR_TRAHISON_SLOT_INACTIVE", "ERROR_TRAHISON_STEP_NOT_OPEN");
			act(a, "{\"action\":\"pick_up\",\"slot\":\"wall\"}");
			assertThat(a.errors()).contains("ERROR_TRAHISON_NOTHING_TO_PICK");

			act(a, "{\"action\":\"use_slot\",\"slot\":\"btn\"}");
			assertThat(this.<Boolean>game(g -> g.currentRound().roomSolved[group])).isFalse();
			act(a, "{\"action\":\"use_slot\",\"slot\":\"dials\"}"); // opens the screen: to him alone
			assertThat(a.ofType("EventsTrahisonPuzzle").stream().anyMatch(p -> "open".equals(p.get("reason").asString()))).isTrue();
			assertThat(spectator.ofType("EventsTrahisonPuzzle").stream().noneMatch(p -> "open".equals(p.get("reason").asString())))
				.isTrue();
			JsonNode view = rooms.puzzleView(hub, group, "s2", ViewerKind.GAME_MASTER).get("view");
			for (int d = 0; d < 3; d++) {
				int delta = Math.floorMod(view.get("target").get(d).asInt() - view.get("dials").get(d).asInt(), 10);
				if (delta != 0) {
					act(a, "{\"action\":\"puzzle_input\",\"step\":\"s2\",\"input\":{\"dial\":" + d + ",\"delta\":" + delta + "}}");
				}
			}
			assertThat(this.<Boolean>game(g -> g.currentRound().roomSolved[group])).isFalse();
			act(a, "{\"action\":\"use_slot\",\"slot\":\"exit\"}");
			assertThat(this.<Boolean>game(g -> g.currentRound().roomSolved[group])).isTrue();
			act(a, "{\"action\":\"use_slot\",\"slot\":\"exit\"}");
			assertThat(a.errors()).contains("ERROR_TRAHISON_ROOM_FINISHED");
			assertThat(a.ofType("EventsTrahisonRoom").stream().anyMatch(r -> r.get("exit_open").asBoolean())).isTrue();

			// Reconnection: a view brings back the rooms the viewer may see.
			int before = spectator.ofType("EventsTrahisonRoom").size();
			spectator.act("{\"action\":\"view\",\"hub\":\"" + hub + "\"}");
			assertThat(spectator.ofType("EventsTrahisonRoom").size() - before).isEqualTo(3);
			int beforeA = a.ofType("EventsTrahisonRoom").size();
			a.act("{\"action\":\"view\"}");
			assertThat(a.ofType("EventsTrahisonRoom").size() - beforeA).isEqualTo(1);

			// Who saw what: only the game master ever got a solution, nobody got a hint text; players see their room only.
			assertThat(admin.ofType("EventsTrahisonPuzzle").stream().anyMatch(p -> p.toString().contains("\"target\""))).isTrue();
			for (Client c : List.of(a, b, spectator)) {
				for (String type : List.of("EventsTrahisonRoom", "EventsTrahisonPuzzle")) {
					for (JsonNode m : c.ofType(type)) {
						assertThat(m.toString()).as(type + " of " + c.uuid).doesNotContain("\"target\"").doesNotContain("secret hint");
					}
				}
			}
			assertThat(roomGroups(a).stream().distinct().toList()).containsExactly(group);
			assertThat(roomGroups(spectator).stream().distinct().toList()).containsExactlyInAnyOrder(0, 1, 2);
			assertThat(admin.ofType("EventsTrahisonRoom").stream().anyMatch(r -> r.get("steps").get(0).has("mechanism"))).isTrue();
			assertThat(spectator.ofType("EventsTrahisonRoom").stream().noneMatch(r -> r.get("steps").get(0).has("mechanism"))).isTrue();

			// The rooms stay readable after their stage, and go with the game.
			toNextStage(Phase.FREE_TIME);
			assertThat(rooms.rooms(hub)).hasSize(3);
			// D8: the free time takes room actions (any room); the stage after it does not.
			act(a, "{\"action\":\"use_slot\",\"slot\":\"exit\"}");
			assertThat(a.errors()).doesNotContain("ERROR_TRAHISON_WRONG_PHASE");
			toNextStage(Phase.VOTE);
			act(a, "{\"action\":\"use_slot\",\"slot\":\"exit\"}");
			assertThat(a.errors()).contains("ERROR_TRAHISON_WRONG_PHASE");
			gm("{\"action\":\"end\"}");
			assertThat(rooms.rooms(hub)).isEmpty();
		} finally {
			catalog.delete(roomId);
		}
	}

	/** The group of every {@code EventsTrahisonRoom} the client received. */
	private static List<Integer> roomGroups(Client c) {
		List<Integer> out = new ArrayList<>();
		for (JsonNode r : c.ofType("EventsTrahisonRoom")) {
			out.add(r.get("group").asInt());
		}
		return out;
	}
}
