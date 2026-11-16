package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;
import com.mystaria.phantasmon_backend.events.trahison.layout.RoomSlot;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomDefinition;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomDraw;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomFixtures;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomService;
import com.mystaria.phantasmon_backend.events.trahison.room.Slot;
import com.mystaria.phantasmon_backend.events.trahison.room.Step;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.DebugButton;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T4.1 and T4.3 through the game: the draw at creation, the room of each place, the game master's replacement. */
class GameDrawsTest {

	private static final String HUB = "hub-d";
	private static final int SECONDS = 10;

	private ManualClock clock;
	private MemoryStore store;
	private GameService service;
	private final UUID[] ids = new UUID[9];
	private final List<RoomDefinition> bank = new ArrayList<>();

	private static RoomDefinition room(String id, int difficulty) {
		return new RoomDefinition(id, id, "t", 1, difficulty, 30,
				List.of(new Slot("btn", "terminal", new Pos(2, 1, 2), "north", null), new Slot("exit", "exit", new Pos(40, 0, 20), "north", null)),
				List.of(new Step("press", DebugButton.ID, null, Map.of("exit", "exit"), List.of(), List.of(), List.of())), null);
	}

	@BeforeEach
	void setUp() {
		clock = new ManualClock();
		store = new MemoryStore();
		for (int i = 0; i < 12; i++) {
			bank.add(room("room" + i, 2 + i % 2));
		}
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
		}
		service = newService();
	}

	private GameService newService() {
		GameService gs = new GameService(store, clock, clock);
		gs.useRooms(new GameRooms(new RoomService(new MechanismRegistry(List.of(new DebugButton())), clock),
				id -> bank.stream().filter(r -> r.id().equals(id)).findFirst()));
		gs.useDraws(new GameDraws(() -> bank, id -> bank.stream().filter(r -> r.id().equals(id)).findFirst(), (exclude, games) -> Map.of()));
		return gs;
	}

	private static RoomDraw.Result drawOf(GameService gs) {
		return gs.read(HUB, g -> g.draw);
	}

	private Settings settings() {
		Settings s = Settings.fast(SECONDS);
		s.roomTimeout = false;
		return s;
	}

	@Test
	void aGameIsCreatedWithItsNineRoomsDrawn() {
		service.create(HUB, settings(), 7L);
		RoomDraw.Result draw = service.read(HUB, g -> g.draw);
		assertThat(draw.picks()).hasSize(9);
		assertThat(draw.picks().stream().map(RoomDraw.Pick::room).distinct()).hasSize(9);
	}

	@Test
	void aTestRoomMeansNoDraw() {
		Settings s = settings();
		s.testRoom = "room0";
		service.create(HUB, s, 7L);
		assertThat(drawOf(service)).isNull();
	}

	@Test
	void aBankUnderNineRoomsRefusesTheGameUnlessATestRoomIsSet() {
		bank.clear();
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(HUB, settings(), 7L))
			.isInstanceOfSatisfying(TrahisonException.class, ex -> assertThat(ex.code()).isEqualTo(TrahisonException.BANK_TOO_SMALL)); // D73
		assertThat(service.find(HUB)).isNull();
	}

	@Test
	void theRoomsOfARoundAreTheDrawnOnesAndTheDrawSurvivesARestart() {
		service.create(HUB, settings(), 7L);
		for (int i = 0; i < 9; i++) {
			service.join(HUB, ids[i], "p" + i);
		}
		service.start(HUB);
		clock.advanceSeconds(SECONDS);
		Split s = Doors.validSplits(service.read(HUB, g -> g.currentRound().assignment())).get(0);
		for (int k = 0; k < 3; k++) {
			for (int b : s.groups().get(k).members()) {
				service.passDoor(HUB, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
			}
		}
		clock.advanceSeconds(SECONDS);
		RoomDraw.Result draw = service.read(HUB, g -> g.draw);
		List<String> played = service.read(HUB, g -> g.currentRound().rooms.stream().map(n -> n.get("definition").get("id").asString()).toList());
		assertThat(played).containsExactly(draw.pick(1, 0).room(), draw.pick(1, 1).room(), draw.pick(1, 2).room());

		GameService again = newService();
		again.loadActiveGames();
		assertThat(drawOf(again)).isEqualTo(draw);
	}

	@Test
	void theGameMasterReplacesARoomUntilItsRoundStarts() {
		service.create(HUB, settings(), 7L);
		service.replaceRoom(HUB, 2, 1, "room11");
		RoomDraw.Pick pick = service.read(HUB, g -> g.draw.pick(2, 1));
		assertThat(pick.room()).isEqualTo("room11");
		assertThat(pick.manual()).isTrue();
		assertThatThrownBy(() -> service.replaceRoom(HUB, 2, 1, "nope")).isInstanceOf(TrahisonException.class);
		assertThatThrownBy(() -> service.replaceRoom(HUB, 4, 0, "room1")).isInstanceOf(TrahisonException.class);
	}

	@Test
	void aRedrawIsAnotherDrawFromAnotherSeed() {
		service.create(HUB, settings(), 7L);
		RoomDraw.Result first = service.read(HUB, g -> g.draw);
		service.redraw(HUB, null);
		RoomDraw.Result second = service.read(HUB, g -> g.draw);
		assertThat(second.seed()).isNotEqualTo(first.seed());
		service.redraw(HUB, first.seed());
		assertThat(drawOf(service)).isEqualTo(first);
	}

	@Test
	void eachPickGetsThePlaceOfItsGroupAndTheOrientationItIsTurnedBy() {
		RoomDraw.Result drawn = RoomDraw.draw(bank, 3L, Map.of(), RoomDraw.Params.DEFAULT);
		List<RoomSlot> slots = new ArrayList<>();
		String[] sides = { "north", "east", "south" };
		for (int round = 1; round <= 3; round++) {
			for (int p = 0; p < 3; p++) {
				slots.add(new RoomSlot("place_" + round + p, round, new Pos(round * 100, 64, p * 100), sides[p]));
			}
		}
		LayoutContent plan = new LayoutContent(List.of(), List.of(), slots, List.of(), List.of());
		RoomDraw.Result placed = GameDraws.assignPlaces(drawn, plan);
		assertThat(placed.pick(2, 1).place()).isEqualTo("place_21");
		assertThat(placed.pick(2, 1).orientation()).isEqualTo("east");
		assertThat(placed.pick(3, 2).orientation()).isEqualTo("south");
		assertThat(GameDraws.assignPlaces(drawn, LayoutContent.empty()).pick(1, 0).place()).isNull();
	}
}
