package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;
import com.mystaria.phantasmon_backend.events.trahison.room.ActionResult;
import com.mystaria.phantasmon_backend.events.trahison.room.Give;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomAction;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomDefinition;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomException;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomInstance;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomFixtures;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomService;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomValidator;
import com.mystaria.phantasmon_backend.events.trahison.room.Slot;
import com.mystaria.phantasmon_backend.events.trahison.room.Step;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.DebugButton;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ThreeDials;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;
import com.mystaria.phantasmon_backend.events.trahison.rules.Assignment;
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The game and the room engine together: rooms started per group, played through the game, saved and reloaded. */
class GameRoomsTest {

	private static final String HUB = "hub-r";
	private static final int SECONDS = 10;

	private ManualClock clock;
	private MemoryStore store;
	private RoomService roomService;
	private GameService service;
	private final UUID[] ids = new UUID[9];

	private static MechanismRegistry registry() {
		return new MechanismRegistry(List.of(new DebugButton(), new ThreeDials(), new RoomFixtures.KeyBox()));
	}

	/** An item to pick up in "box", then the exit: for the inventory's actions through the game. */
	private static RoomDefinition keys() {
		return new RoomDefinition("keys", "Keys", "t", 1, 2, 30,
			List.of(new Slot("box", "pickup", new Pos(1, 1, 1), "north", null),
				new Slot("exit", "exit", new Pos(5, 0, 5), "north", null)),
			List.of(new Step("k", "key_box", null, Map.of(), List.of(), List.of(new Give("key", "box", null)), List.of()),
				new Step("e", "debug_button", null, Map.of("exit", "exit"), List.of("k"), List.of(), List.of())),
			null);
	}

	/** press (debug_button, slot btn) -> dials (three_dials, clue on the wall) -> exit button. */
	private static RoomDefinition chain() {
		return new RoomDefinition("chain", "Chain", "t", 1, 3, 100,
			List.of(new Slot("btn", "terminal", new Pos(2, 1, 2), "north", null),
				new Slot("dials", "terminal", new Pos(10, 1, 4), "north", null),
				new Slot("wall", "wall_clue", new Pos(20, 2, 0), "north", null),
				new Slot("exit", "exit", new Pos(40, 0, 20), "north", null)),
			List.of(new Step("s1", "debug_button", null, Map.of("press", "btn"), List.of(), List.of(), List.of()),
				new Step("s2", "three_dials", null, Map.of("dials", "dials"), List.of("s1"),
					List.of(new Give("code", "wall", null)), List.of()),
				new Step("s3", "debug_button", null, Map.of("exit", "exit"), List.of("s2"), List.of(), List.of())),
			null);
	}

	private GameService newService(ManualClock c, MemoryStore s, RoomService rs) {
		GameService gs = new GameService(s, c, c);
		gs.useRooms(new GameRooms(rs, id -> "chain".equals(id) ? Optional.of(chain()) : "keys".equals(id) ? Optional.of(keys()) : Optional.empty()));
		return gs;
	}

	@BeforeEach
	void setUp() {
		clock = new ManualClock();
		store = new MemoryStore();
		roomService = new RoomService(registry(), clock);
		service = newService(clock, store, roomService);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
		}
	}

	private void toRooms(String testRoom) {
		Settings settings = Settings.fast(SECONDS);
		settings.testRoom = testRoom;
		service.create(HUB, settings, 42L);
		for (int i = 0; i < 9; i++) {
			service.join(HUB, ids[i], "p" + i);
		}
		service.start(HUB);
		clock.advanceSeconds(SECONDS); // DOORS
		Split s = Doors.validSplits(service.read(HUB, g -> g.currentRound().assignment())).get(0);
		for (int k = 0; k < 3; k++) {
			for (int b : s.groups().get(k).members()) {
				service.passDoor(HUB, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
			}
		}
		clock.advanceSeconds(SECONDS); // ROOMS
		assertThat(phase(service)).isEqualTo(Phase.ROOMS);
	}

	private Phase phase(GameService gs) {
		return gs.read(HUB, g -> g.phase);
	}

	private boolean[] solved(GameService gs) {
		return gs.read(HUB, g -> g.currentRound().roomSolved.clone());
	}

	private List<JsonNode> savedRooms() {
		return service.read(HUB, g -> List.copyOf(g.currentRound().rooms));
	}

	private long roomSolvedEvents() {
		return service.read(HUB, g -> g.log.stream().filter(e -> e.type.equals("room_solved")).count());
	}

	private int groupOf(int player) {
		return service.read(HUB, g -> g.currentRound().groupOf(player));
	}

	private List<Integer> membersOf(int group) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			if (groupOf(i) == group) {
				out.add(i);
			}
		}
		return out;
	}

	private static RoomAction use(String slot) {
		return new RoomAction.UseSlot(slot);
	}

	private static RoomAction turn(int dial, int delta) {
		ObjectNode in = Json.object();
		in.put("dial", dial);
		in.put("delta", delta);
		return new RoomAction.PuzzleInput("s2", in);
	}

	/** Solves the dials of {@code group} for real, reading the target as a game master would. */
	private void solveDials(RoomService rs, GameService gs, int group, int player) {
		solveDials(rs, gs, group, player, null);
	}

	/** As above; {@code aim}: the group of the room when the player is a visitor of it (free time). */
	private void solveDials(RoomService rs, GameService gs, int group, int player, Integer aim) {
		JsonNode view = rs.puzzleView(HUB, group, "s2", ViewerKind.GAME_MASTER).get("view");
		for (int d = 0; d < 3; d++) {
			int delta = Math.floorMod(view.get("target").get(d).asInt() - view.get("dials").get(d).asInt(), 10);
			if (delta != 0) {
				gs.roomAction(HUB, ids[player], turn(d, delta), aim);
			}
		}
	}

	// ------------------------------------------------------------------ tests

	@Test
	void theBuiltInFallbackRoomIsAValidDefinition() {
		MechanismRegistry registry = registry();
		assertThat(RoomValidator.validate(GameRooms.fallbackDefinition(), registry::find)).isEmpty();
	}

	@Test
	void everyGroupGetsItsRoomWhenTheDoorsCloseAndDebugSolveFinishesIt() {
		toRooms(null);
		List<RoomInstance> rooms = roomService.rooms(HUB);
		assertThat(rooms).hasSize(3);
		for (int k = 0; k < 3; k++) {
			assertThat(rooms.get(k).group).isEqualTo(k);
			assertThat(rooms.get(k).members).containsExactlyInAnyOrderElementsOf(membersOf(k));
			assertThat(rooms.get(k).definition.id()).isEqualTo(GameRooms.FALLBACK_ID);
		}
		assertThat(rooms.get(0).seed).isNotEqualTo(rooms.get(1).seed);
		assertThat(savedRooms()).hasSize(3);

		int me = membersOf(1).get(0);
		service.debugSolve(HUB, ids[me]);
		assertThat(solved(service)).containsExactly(false, true, false);
		assertThat(roomService.find(HUB, 1).orElseThrow().exitOpen).isTrue();
		assertThat(roomService.find(HUB, 0).orElseThrow().exitOpen).isFalse();
		assertThat(roomService.find(HUB, 1).orElseThrow().stepsDone()).isEqualTo(1);
		assertThatThrownBy(() -> service.roomAction(HUB, ids[me], use("exit")))
			.isInstanceOfSatisfying(RoomException.class, ex -> assertThat(ex.code()).isEqualTo(RoomException.ROOM_FINISHED));
		// the saved state follows: a later reload sees the finished room
		assertThat(savedRooms().get(1).get("exitOpen").asBoolean()).isTrue();
	}

	@Test
	void aGroupReallySolvesItsRoomAndTheGameSeesIt() {
		toRooms("chain");
		int me = membersOf(2).get(1);
		assertThat(roomService.find(HUB, 2).orElseThrow().definition.id()).isEqualTo("chain");

		ActionResult first = service.roomAction(HUB, ids[me], use("btn"));
		assertThat(first.stepSolved()).isTrue();
		assertThat(solved(service)[2]).isFalse();
		solveDials(roomService, service, 2, me);
		assertThat(roomService.find(HUB, 2).orElseThrow().steps.get("s2").solved).isTrue();
		assertThat(solved(service)[2]).isFalse();
		ActionResult last = service.roomAction(HUB, ids[me], use("exit"));
		assertThat(last.roomFinished()).isTrue();
		assertThat(solved(service)).containsExactly(false, false, true);
		assertThat(roomSolvedEvents()).isEqualTo(1L);
		// the other groups are untouched
		assertThat(roomService.find(HUB, 0).orElseThrow().stepsDone()).isZero();
		assertThat(roomService.find(HUB, 1).orElseThrow().stepsDone()).isZero();
	}

	@Test
	void roomActionsAreRefusedOutsideTheRoomsStageOrWhilePaused() {
		Settings settings = Settings.fast(SECONDS);
		service.create(HUB, settings, 42L);
		for (int i = 0; i < 9; i++) {
			service.join(HUB, ids[i], "p" + i);
		}
		assertThatThrownBy(() -> service.roomAction(HUB, ids[0], use("btn")))
			.isInstanceOfSatisfying(TrahisonException.class, ex -> assertThat(ex.code()).isEqualTo(TrahisonException.WRONG_PHASE));
		service.start(HUB);
		clock.advanceSeconds(SECONDS);
		assertThatThrownBy(() -> service.roomAction(HUB, ids[0], use("btn")))
			.isInstanceOfSatisfying(TrahisonException.class, ex -> assertThat(ex.code()).isEqualTo(TrahisonException.WRONG_PHASE));
		Split s = Doors.validSplits(service.read(HUB, g -> g.currentRound().assignment())).get(0);
		for (int k = 0; k < 3; k++) {
			for (int b : s.groups().get(k).members()) {
				service.passDoor(HUB, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
			}
		}
		clock.advanceSeconds(SECONDS);
		service.pause(HUB);
		assertThatThrownBy(() -> service.roomAction(HUB, ids[0], use("exit")))
			.isInstanceOfSatisfying(TrahisonException.class, ex -> assertThat(ex.code()).isEqualTo(TrahisonException.PAUSED));
		service.resume(HUB);
		assertThat(service.roomAction(HUB, ids[0], use("exit")).roomFinished()).isTrue();
		clock.advanceSeconds(SECONDS); // FREE_TIME: the room stays readable and (D8) takes visitors
		assertThat(roomService.find(HUB, groupOf(0))).isPresent();
		clock.advanceSeconds(SECONDS); // VOTE: nobody acts in a room any more
		assertThat(phase(service)).isEqualTo(Phase.VOTE);
		assertThatThrownBy(() -> service.roomAction(HUB, ids[0], use("exit")))
			.isInstanceOfSatisfying(TrahisonException.class, ex -> assertThat(ex.code()).isEqualTo(TrahisonException.WRONG_PHASE));
	}

	private static void assertCode(Runnable call, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(TrahisonException.class, ex -> assertThat(ex.code()).isEqualTo(code));
	}

	/** D8: in the free time any living player plays any room of the round, and a room can be replayed for no points. */
	@Test
	void freeTimeLetsEveryPlayerVisitAndReplayAnyRoomForNoPoints() {
		toRooms("chain");
		int owner = membersOf(1).get(0);
		int visitor = membersOf(0).get(0);
		// During ROOMS: another group's room is closed to him, a replay is not allowed, a group of one's own is fine.
		assertCode(() -> service.roomAction(HUB, ids[visitor], use("btn"), 1), RoomException.NOT_IN_ROOM);
		assertCode(() -> service.roomAction(HUB, ids[owner], new RoomAction.Replay(), 1), RoomException.BAD_ACTION);
		service.roomAction(HUB, ids[owner], use("btn"));
		solveDials(roomService, service, 1, owner);
		assertThat(service.roomAction(HUB, ids[owner], use("exit")).roomFinished()).isTrue();
		assertThat(solved(service)).containsExactly(false, true, false);
		long events = roomSolvedEvents();
		long oldSeed = roomService.find(HUB, 1).orElseThrow().seed;

		clock.advanceSeconds(SECONDS);
		assertThat(phase(service)).isEqualTo(Phase.FREE_TIME);
		// The visitor replays the finished room of another trio: a fresh state, other solutions, same place.
		service.roomAction(HUB, ids[visitor], new RoomAction.Replay(), 1);
		RoomInstance again = roomService.find(HUB, 1).orElseThrow();
		assertThat(again.exitOpen).isFalse();
		assertThat(again.stepsDone()).isZero();
		assertThat(again.seed).isNotEqualTo(oldSeed);
		assertThat(again.replays).isEqualTo(1);
		assertThat(again.members).containsExactlyInAnyOrderElementsOf(membersOf(1));
		assertThat(savedRooms().get(1).get("replays").asInt()).isEqualTo(1);
		// He plays it to the end: no point, no new room solved, nothing opened for his own group.
		assertThat(service.roomAction(HUB, ids[visitor], use("btn"), 1).stepSolved()).isTrue();
		solveDials(roomService, service, 1, visitor, 1);
		assertThat(service.roomAction(HUB, ids[visitor], use("exit"), 1).roomFinished()).isTrue();
		assertThat(solved(service)).containsExactly(false, true, false);
		assertThat(roomSolvedEvents()).isEqualTo(events);
		assertThat(roomService.find(HUB, 0).orElseThrow().stepsDone()).isZero();
		// A room that does not exist is refused; without a group the action is for his own room.
		assertCode(() -> service.roomAction(HUB, ids[visitor], use("btn"), 7), RoomException.NO_ROOM);
		assertThat(service.roomAction(HUB, ids[visitor], use("btn")).stepSolved()).isTrue();
		assertThat(roomService.find(HUB, 0).orElseThrow().stepsDone()).isEqualTo(1);
		assertThat(solved(service)).containsExactly(false, true, false);
	}

	/** Hands over an item through the game: in range it goes, out of range it is refused, and the zone counts. */
	@Test
	void giveItemThroughTheGameChecksTheDistance() {
		toRooms("keys");
		List<Integer> team = membersOf(0);
		int a = team.get(0);
		int b = team.get(1);
		service.roomAction(HUB, ids[a], new RoomAction.PickUp("box"));
		RoomAction give = new RoomAction.GiveItem("brass_key", b);
		// No position and no zone known: not near.
		assertCode(() -> service.roomAction(HUB, ids[a], give), RoomException.TOO_FAR);
		service.setZone(HUB, ids[a], "x", 100, 64, 100);
		service.setZone(HUB, ids[b], "y", 140, 64, 100);
		assertCode(() -> service.roomAction(HUB, ids[a], give), RoomException.TOO_FAR);
		service.setZone(HUB, ids[b], "y", 102, 64, 101);
		service.roomAction(HUB, ids[a], give);
		assertCode(() -> service.roomAction(HUB, ids[a], give), RoomException.NO_SUCH_ITEM);
		// He holds it now and gives it back, out of range this time.
		service.setZone(HUB, ids[a], "x", 0, 64, 0);
		assertCode(() -> service.roomAction(HUB, ids[b], new RoomAction.GiveItem("brass_key", a)), RoomException.TOO_FAR);
	}

	@Test
	void roomsLiveThroughTheirRoundAndGoAtTheNextRoundAndAtTheEnd() {
		toRooms(null);
		clock.advanceSeconds(SECONDS);
		assertThat(phase(service)).isEqualTo(Phase.FREE_TIME);
		assertThat(roomService.rooms(HUB)).hasSize(3);
		clock.advanceSeconds(SECONDS);
		assertThat(phase(service)).isEqualTo(Phase.VOTE);
		assertThat(roomService.rooms(HUB)).hasSize(3);
		clock.advanceSeconds(SECONDS);
		assertThat(phase(service)).isEqualTo(Phase.RESULTS);
		assertThat(roomService.rooms(HUB)).hasSize(3);
		clock.advanceSeconds(SECONDS);
		assertThat(phase(service)).isEqualTo(Phase.DOORS);
		assertThat(roomService.rooms(HUB)).isEmpty();
		service.end(HUB);
		assertThat(roomService.rooms(HUB)).isEmpty();
	}

	@Test
	void roomsComeBackAfterARestartWithTheSameViewsAndCanBeFinished() {
		toRooms("chain");
		int me = membersOf(0).get(0);
		service.roomAction(HUB, ids[me], use("btn"));
		JsonNode before = roomService.puzzleView(HUB, 0, "s2", ViewerKind.GAME_MASTER).get("view");
		service.roomAction(HUB, ids[me], turn(0, 4));
		service.debugSolve(HUB, ids[membersOf(1).get(0)]); // another group finished: stays finished too
		Map<String, String> views = new java.util.TreeMap<>();
		for (int k = 0; k < 3; k++) {
			for (ViewerKind kind : ViewerKind.values()) {
				views.put(k + "/" + kind + "/room", roomService.roomView(HUB, k, kind).toString());
				views.put(k + "/" + kind + "/s2", roomService.puzzleView(HUB, k, "s2", kind).toString());
			}
		}
		assertThat(before.get("dials").get(0).asInt()).isNotEqualTo(roomService.puzzleView(HUB, 0, "s2", ViewerKind.GAME_MASTER)
			.get("view").get("dials").get(0).asInt()); // there is real progress to keep

		// The backend restarts: a new service, a new room engine, the same store.
		ManualClock clock2 = new ManualClock();
		RoomService rooms2 = new RoomService(registry(), clock2);
		GameService service2 = newService(clock2, store, rooms2);
		assertThat(rooms2.rooms(HUB)).isEmpty();
		service2.loadActiveGames();
		assertThat(rooms2.rooms(HUB)).hasSize(3);
		for (int k = 0; k < 3; k++) {
			for (ViewerKind kind : ViewerKind.values()) {
				assertThat(rooms2.roomView(HUB, k, kind).toString()).isEqualTo(views.get(k + "/" + kind + "/room"));
				assertThat(rooms2.puzzleView(HUB, k, "s2", kind).toString()).isEqualTo(views.get(k + "/" + kind + "/s2"));
			}
		}
		assertThat(rooms2.find(HUB, 0).orElseThrow().seed).isEqualTo(roomService.find(HUB, 0).orElseThrow().seed);
		assertThat(rooms2.find(HUB, 1).orElseThrow().exitOpen).isTrue();

		// ... and the restored room plays on to the end through the new game.
		solveDials(rooms2, service2, 0, me);
		assertThat(service2.roomAction(HUB, ids[me], use("exit")).roomFinished()).isTrue();
		assertThat(solved(service2)).containsExactly(true, true, false);
	}
}
