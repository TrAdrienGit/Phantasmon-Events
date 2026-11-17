package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.Box;
import com.mystaria.phantasmon_backend.events.trahison.layout.Layout;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutZone;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomDraw;

import static org.assertj.core.api.Assertions.assertThat;

/** T11.2: the rooms a player explored, kept with the game, private to him; what those who watch see instead. */
class MapExplorationTest {

	private static final String HUB = "hub-m";
	private static final Box BOX = new Box(0, 64, 0, 0, 66, 2);

	private ManualClock clock;
	private MemoryStore store;
	private GameService service;
	private final UUID[] ids = new UUID[9];
	private final UUID gm = new UUID(1, 1);
	private final UUID watcher = new UUID(7, 7);
	private final List<Integer> exploredEvents = new ArrayList<>();

	private static LayoutContent plan() {
		return new LayoutContent(List.of(new LayoutZone("hall", "hall", List.of(BOX)), new LayoutZone("slot_a", "room_slot", List.of(BOX)),
			new LayoutZone("slot_b", "room_slot", List.of(BOX))), List.of(), null, null, null);
	}

	@BeforeEach
	void setUp() {
		clock = new ManualClock();
		store = new MemoryStore();
		service = new GameService(store, clock, clock);
		service.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan()));
		service.addListener(new GameListener() {
			@Override
			public void exploredChanged(Game game, int seat) {
				exploredEvents.add(seat);
			}
		});
		service.create(HUB, Settings.fast(10), 5L);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
			service.join(HUB, ids[i], "p" + i);
		}
		service.addGameMaster(HUB, gm);
	}

	private Map<String, Object> view(UUID who) {
		return service.read(HUB, g -> Views.build(g, who, clock.instant()));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> viewer(UUID who) {
		return (Map<String, Object>) view(who).get("viewer");
	}

	@Test
	void aVisitedZoneIsRememberedOncePerPlayerAndOnlyOnceTheGameStarted() {
		service.setZone(HUB, ids[0], "hall", 0, 64, 0); // the lobby: nothing yet
		assertThat(viewer(ids[0]).get("explored")).isEqualTo(List.of());
		service.start(HUB);
		service.setZone(HUB, ids[0], "hall", 0, 64, 0);
		service.setZone(HUB, ids[0], "slot_a", 0, 64, 0);
		service.setZone(HUB, ids[0], "hall", 0, 64, 0);
		service.setZone(HUB, ids[0], "slot_a", 0, 64, 0);
		service.setZone(HUB, ids[0], "unknown_zone", 0, 64, 0); // not in the plan: ignored
		assertThat(viewer(ids[0]).get("explored")).isEqualTo(List.of("hall", "slot_a"));
		assertThat(exploredEvents).containsExactly(0, 0);
		assertThat(viewer(ids[0])).containsEntry("explored_all", false);
	}

	@Test
	void aPlayerNeverReceivesAnotherPlayersExploration() {
		service.start(HUB);
		service.setZone(HUB, ids[1], "slot_b", 0, 64, 0);
		assertThat(viewer(ids[0]).get("explored")).isEqualTo(List.of());
		assertThat(viewer(ids[1]).get("explored")).isEqualTo(List.of("slot_b"));
		assertThat(view(ids[0]).toString()).doesNotContain("slot_b");
		assertThat(view(ids[0]).get("map_cursors")).isEqualTo(List.of());
	}

	@Test
	void theExplorationSurvivesARestart() {
		service.start(HUB);
		service.setZone(HUB, ids[3], "slot_a", 0, 64, 0);
		GameService reborn = new GameService(store, clock, clock);
		reborn.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan()));
		reborn.loadActiveGames();
		@SuppressWarnings("unchecked")
		Map<String, Object> mine = (Map<String, Object>) reborn.read(HUB, g -> Views.build(g, ids[3], clock.instant())).get("viewer");
		assertThat(mine.get("explored")).isEqualTo(List.of("slot_a"));
	}

	@Test
	void gameMastersSpectatorsAndGhostsSeeEveryRoomAndTheCursors() {
		service.spectate(HUB, watcher, "w", true);
		service.start(HUB);
		service.setZone(HUB, ids[2], "slot_a", 3.5, 65, -2);
		service.kill(HUB, 5, "test");
		for (UUID who : new UUID[] { gm, watcher, ids[5] }) {
			assertThat(viewer(who)).containsEntry("explored_all", true);
			assertThat(viewer(who).get("explored")).isNull();
		}
		for (UUID who : new UUID[] { gm, watcher, ids[5] }) {
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> cursors = (List<Map<String, Object>>) view(who).get("map_cursors");
			assertThat(cursors).hasSize(1);
			assertThat(cursors.get(0)).containsEntry("index", 2).containsEntry("zone", "slot_a").containsEntry("x", 3.5);
		}
		assertThat(view(ids[0]).get("map_cursors")).isEqualTo(List.of());
	}

	private void drawRooms() {
		service.read(HUB, g -> {
			g.draw = new RoomDraw.Result(1, List.of(
				new RoomDraw.Pick(1, 0, "r1", "First room", 1, 60, "keypad", false, "place_1_0", "north"),
				new RoomDraw.Pick(2, 0, "r2", "Second room", 1, 60, "keypad", false, "place_2_0", "north")), List.of());
			return null;
		});
	}

	@Test
	void theNamesOfThePlacesComeWithTheDrawAndAPlayerOnlyLearnsTheRoundsThatBegan() {
		drawRooms();
		assertThat(rooms(ids[0])).isEmpty(); // lobby
		assertThat(rooms(gm)).containsKeys("place_1_0", "place_2_0");
		service.start(HUB);
		drawRooms(); // the start draws its own rooms
		assertThat(rooms(ids[0])).containsOnlyKeys("place_1_0");
		assertThat(rooms(ids[0]).get("place_1_0")).isEqualTo(Map.of("name", "First room", "round", 1, "group", 0));
		assertThat(rooms(watcher)).containsOnlyKeys("place_1_0", "place_2_0");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> rooms(UUID who) {
		return (Map<String, Object>) view(who).get("map_rooms");
	}
}
