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
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutDoor;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutZone;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Door states in a running game (views, overrides, plan version) and the zones players report. */
class DoorsAndZonesTest {

	private static final String HUB = "hub-d";
	private static final Box BOX = new Box(0, 64, 0, 0, 66, 2);

	private ManualClock clock;
	private MemoryStore store;
	private GameService service;
	private final UUID[] ids = new UUID[9];
	private final UUID gm = new UUID(1, 1);
	private final List<String> zoneViews = new ArrayList<>();

	private static LayoutContent plan() {
		List<LayoutZone> zones = List.of(new LayoutZone("hall", "hall", List.of(BOX)), new LayoutZone("slot_0", "room_slot", List.of(BOX)),
			new LayoutZone("slot_1", "room_slot", List.of(BOX)), new LayoutZone("slot_2", "room_slot", List.of(BOX)));
		List<LayoutDoor> doors = List.of(
			new LayoutDoor("c_rg", "color", BOX, "hall", "slot_0", 1, List.of("red", "green")),
			new LayoutDoor("c_rb", "color", BOX, "hall", "slot_1", 1, List.of("red", "blue")),
			new LayoutDoor("c_gb", "color", BOX, "hall", "slot_2", 1, List.of("green", "blue")),
			new LayoutDoor("exit_0", "room_exit", BOX, "slot_0", "hall", null, null),
			new LayoutDoor("exit_1", "room_exit", BOX, "slot_1", "hall", null, null),
			new LayoutDoor("exit_2", "room_exit", BOX, "slot_2", "hall", null, null),
			new LayoutDoor("plain", "plain", BOX, "hall", "hall", null, null));
		return new LayoutContent(zones, doors, null, null, null);
	}

	@BeforeEach
	void setUp() {
		clock = new ManualClock();
		store = new MemoryStore();
		service = new GameService(store, clock, clock);
		service.useLayouts((hub, version) -> Layout.of(hub, 7, false, Instant.EPOCH, plan()));
		service.addListener(new GameListener() {
			@Override
			public void zonesChanged(Game game) {
				zoneViews.add(game.players.get(0).zone);
			}
		});
		service.create(HUB, Settings.fast(10), 5L);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
			service.join(HUB, ids[i], "p" + i);
		}
	}

	private Map<String, Object> view(UUID who) {
		return service.read(HUB, g -> Views.build(g, who, clock.instant()));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> doorsOf(UUID who) {
		return (Map<String, Object>) view(who).get("doors_state");
	}

	@Test
	void theGameKeepsTheVersionOfThePlanItStartedWith() {
		assertThat(service.find(HUB).layoutVersion).isZero();
		service.start(HUB);
		assertThat(service.find(HUB).layoutVersion).isEqualTo(7);
		assertThat(view(ids[0]).get("layout_version")).isEqualTo(7);
	}

	@Test
	void theViewListsEveryDoorAsOpenOrClosed() {
		service.start(HUB);
		assertThat(doorsOf(ids[0])).containsEntry("c_rg", "CLOSED").containsEntry("plain", "OPEN").containsEntry("exit_0", "OPEN");
		clock.advanceSeconds(10); // DOORS
		for (int p = 0; p < 9; p++) { // everybody takes the door of a split
			Split s = Doors.validSplits(service.read(HUB, g -> g.currentRound().assignment())).get(0);
			for (int k = 0; k < 3; k++) {
				for (int b : s.groups().get(k).members()) {
					int door = com.mystaria.phantasmon_backend.events.trahison.rules.Door.ALL.indexOf(s.groups().get(k).door());
					if (service.read(HUB, g -> g.currentRound().passedDoor[b]) < 0) {
						service.passDoor(HUB, ids[b], door);
					}
				}
			}
		}
		clock.advanceSeconds(10); // ROOMS
		assertThat(doorsOf(ids[0])).containsEntry("exit_0", "CLOSED").containsEntry("exit_1", "CLOSED").containsEntry("exit_2", "CLOSED");
		service.debugSolve(HUB, ids[0]);
		int group = service.read(HUB, g -> g.currentRound().groupOf(0));
		assertThat(doorsOf(ids[3])).containsEntry("exit_" + group, "OPEN");
		for (int other = 0; other < 3; other++) {
			if (other != group) {
				assertThat(doorsOf(ids[3])).containsEntry("exit_" + other, "CLOSED");
			}
		}
		// Back to the defaults when the stage is over.
		clock.advanceSeconds(10); // FREE_TIME
		assertThat(doorsOf(ids[0])).containsEntry("exit_0", "OPEN").containsEntry("exit_1", "OPEN").containsEntry("exit_2", "OPEN");
	}

	@Test
	void theGameMasterOpensAndClosesDoorsUntilTheStageChanges() {
		service.addGameMaster(HUB, gm);
		service.start(HUB);
		service.setDoor(HUB, "c_rg", true);
		service.setDoor(HUB, "plain", false);
		assertThat(doorsOf(ids[0])).containsEntry("c_rg", "OPEN").containsEntry("plain", "CLOSED");
		assertThat(doorsOf(gm)).containsEntry("c_rg", "OPEN");
		clock.advanceSeconds(10);
		assertThat(doorsOf(ids[0])).containsEntry("c_rg", "CLOSED").containsEntry("plain", "OPEN");
	}

	@Test
	void anOverrideSurvivesARestart() {
		service.start(HUB);
		service.setDoor(HUB, "c_rb", true);
		GameService reborn = new GameService(store, clock, clock);
		reborn.useLayouts((hub, version) -> Layout.of(hub, 7, false, Instant.EPOCH, plan()));
		reborn.loadActiveGames();
		assertThat(reborn.<Boolean>read(HUB, g -> g.doors.get("c_rb").open())).isTrue();
		assertThat(reborn.<Boolean>read(HUB, g -> g.doors.get("c_rg").open())).isFalse();
	}

	@Test
	void aDoorOfThePlanIsRequired() {
		service.start(HUB);
		assertThatThrownBy(() -> service.setDoor(HUB, "nope", true)).isInstanceOf(TrahisonException.class)
			.extracting(e -> ((TrahisonException) e).code()).isEqualTo(TrahisonException.BAD_ARGUMENT);
	}

	@Test
	void aGameWithoutAPlanHasNoDoor() {
		GameService bare = new GameService(new MemoryStore(), clock, clock);
		bare.create("bare", Settings.fast(10), 1L);
		bare.addGameMaster("bare", gm);
		assertThat(bare.<Map<String, DoorState>>read("bare", g -> g.doors)).isEmpty();
		assertThatThrownBy(() -> bare.setDoor("bare", "any", true)).isInstanceOf(TrahisonException.class);
	}

	// ------------------------------------------------------------------ zones

	@Test
	void aPlayersZoneAndPositionAreKeptButNotWritten() {
		service.start(HUB);
		service.setZone(HUB, ids[2], "hall", 1.5, 65, -3.25);
		service.setZone(HUB, ids[2], "slot_1", 2, 65, -3);
		int saves = store.saves; // a first visit is saved (T11.2), a position never is
		service.setZone(HUB, ids[2], "slot_1", 9, 65, 9);
		PlayerState p = service.read(HUB, g -> g.players.get(2));
		assertThat(p.zone).isEqualTo("slot_1");
		assertThat(p.x).isEqualTo(9.0);
		assertThat(p.z).isEqualTo(9.0);
		assertThat(store.saves).isEqualTo(saves);
	}

	@Test
	void aBlankZoneMeansNone() {
		service.start(HUB);
		service.setZone(HUB, ids[0], "hall", 0, 64, 0);
		service.setZone(HUB, ids[0], "", 0, 64, 0);
		assertThat(service.<String>read(HUB, g -> g.players.get(0).zone)).isNull();
	}

	@Test
	void nonPlayersAndUnknownHubsAreIgnored() {
		service.start(HUB);
		service.setZone(HUB, new UUID(9, 9), "hall", 0, 64, 0);
		service.setZone("elsewhere", ids[0], "hall", 0, 64, 0);
		assertThat(zoneViews).isEmpty();
	}

	@Test
	void zoneChangesReachTheListenersAtMostOncePerSecond() {
		service.start(HUB);
		service.setZone(HUB, ids[0], "a", 0, 64, 0); // first: at once
		assertThat(zoneViews).containsExactly("a");
		service.setZone(HUB, ids[0], "b", 0, 64, 0); // within the second: held
		service.setZone(HUB, ids[0], "c", 0, 64, 0);
		assertThat(zoneViews).containsExactly("a");
		clock.advance(java.time.Duration.ofMillis(500));
		assertThat(zoneViews).containsExactly("a");
		clock.advance(java.time.Duration.ofMillis(600)); // the second is over: one update with the latest zone
		assertThat(zoneViews).containsExactly("a", "c");
		service.setZone(HUB, ids[0], "c", 0.5, 64, 0); // same zone, hardly moved: nothing to say
		clock.advanceSeconds(5);
		assertThat(zoneViews).containsExactly("a", "c");
		service.setZone(HUB, ids[0], "c", 5, 64, 5); // same zone, walked on: the cursor moves for those who watch (T11.2)
		clock.advanceSeconds(5);
		assertThat(zoneViews).containsExactly("a", "c", "c");
		service.setZone(HUB, ids[0], "d", 0, 64, 0); // long after: at once again
		assertThat(zoneViews).containsExactly("a", "c", "c", "d");
	}

	@Test
	void onlyTheGameMasterViewShowsZones() {
		service.addGameMaster(HUB, gm);
		service.start(HUB);
		service.setZone(HUB, ids[1], "hall", 1, 65, 2);
		@SuppressWarnings("unchecked")
		Map<String, Object> gmBlock = (Map<String, Object>) view(gm).get("gm");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> zones = (List<Map<String, Object>>) gmBlock.get("zones");
		assertThat(zones).hasSize(9);
		assertThat(zones.get(1)).containsEntry("zone", "hall").containsEntry("x", 1.0).containsEntry("z", 2.0);
		assertThat(view(ids[0]).get("gm")).isNull();
		assertThat(view(ids[0]).toString()).doesNotContain("hall\", x=");
	}

	@Test
	void theViewSaysWhoMayOpenTheFinalDoor() {
		service.start(HUB);
		@SuppressWarnings("unchecked")
		Map<String, Object> before = (Map<String, Object>) view(ids[4]).get("viewer");
		assertThat(before).containsEntry("can_open_final_door", false);
		service.setPoints(HUB, 4, 9);
		@SuppressWarnings("unchecked")
		Map<String, Object> after = (Map<String, Object>) view(ids[4]).get("viewer");
		assertThat(after).containsEntry("can_open_final_door", true);
		@SuppressWarnings("unchecked")
		Map<String, Object> other = (Map<String, Object>) view(ids[5]).get("viewer");
		assertThat(other).containsEntry("can_open_final_door", false);
		@SuppressWarnings("unchecked")
		Map<String, Object> spectator = (Map<String, Object>) view(new UUID(7, 7)).get("viewer");
		assertThat(spectator).containsEntry("can_open_final_door", false);
	}
}
