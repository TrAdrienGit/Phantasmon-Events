package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.Box;
import com.mystaria.phantasmon_backend.events.trahison.layout.Cabin;
import com.mystaria.phantasmon_backend.events.trahison.layout.HidingSpot;
import com.mystaria.phantasmon_backend.events.trahison.layout.Layout;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutDoor;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutZone;
import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;
import com.mystaria.phantasmon_backend.events.trahison.layout.RoomSlot;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomDefinition;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomInstance;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomSecrets;
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

/** D228 + D141: the live secret of each placed room is one more hiding place for the item draw, searched in hub coordinates. */
class RoomSecretItemsTest {

	private static final String HUB = "hub-s";
	private static final Box BOX = new Box(0, 64, 0, 0, 66, 2);
	private static final Box PLAIN = new Box(100, 64, 100, 100, 66, 102);
	private static final String[] NAMES = { "rg", "rb", "gb" };
	private static final String[][] COLORS = { { "red", "green" }, { "red", "blue" }, { "green", "blue" } };

	private ManualClock clock;
	private RoomService roomService;
	private GameService service;
	private final UUID[] ids = new UUID[9];

	/** A room of three secret candidates, far apart, and an exit button. */
	private static RoomDefinition secretRoom() {
		return new RoomDefinition("sec", "Sec", "t", 1, 1, 30,
			List.of(new Slot("a", "secret", new Pos(5, 0, 5), "north", null),
				new Slot("b", "secret", new Pos(20, 0, 5), "north", null),
				new Slot("c", "secret", new Pos(35, 0, 5), "north", null),
				new Slot("exit", "exit", new Pos(40, 0, 30), "north", null)),
			List.of(new Step("e", DebugButton.ID, null, Map.of("exit", "exit"), List.of(), List.of(), List.of())),
			null);
	}

	/** Two plan hiding spots only, and three places for every round: the 5 other items need the rooms. */
	private static LayoutContent plan() {
		List<LayoutZone> zones = new ArrayList<>(List.of(new LayoutZone("hall", "hall", List.of(BOX)), new LayoutZone("vault", "hall", List.of(PLAIN))));
		List<LayoutDoor> doors = new ArrayList<>();
		List<Cabin> cabins = new ArrayList<>();
		for (int k = 0; k < 3; k++) {
			zones.add(new LayoutZone("thr_" + NAMES[k], "doors", List.of(BOX)));
			zones.add(new LayoutZone("slot_" + k, "room_slot", List.of(BOX)));
			doors.add(new LayoutDoor("c_" + NAMES[k], "color", BOX, "hall", "slot_" + k, 1, List.of(COLORS[k]), "thr_" + NAMES[k]));
			for (String side : List.of("pair", "solo")) {
				String id = "cab_" + NAMES[k] + "_" + side;
				zones.add(new LayoutZone(id, "cabin", List.of(PLAIN)));
				cabins.add(new Cabin(id, new String[] { "red_green", "red_blue", "green_blue" }[k], side, id, null));
			}
		}
		doors.add(new LayoutDoor("d_plain", "plain", PLAIN, "hall", "vault", null, null));
		List<HidingSpot> spots = List.of(new HidingSpot("s0", new Pos(0, 65, 0)), new HidingSpot("s1", new Pos(10, 65, 0)));
		List<RoomSlot> places = new ArrayList<>();
		String[] sides = { "north", "east", "south" };
		for (int round = 1; round <= 3; round++) {
			for (int p = 0; p < 3; p++) {
				places.add(new RoomSlot("place_" + round + p, round, new Pos(round * 100, 64, p * 100), sides[p]));
			}
		}
		return new LayoutContent(zones, doors, places, cabins, spots);
	}

	private GameService newService(RoomService rs) {
		GameService gs = new GameService(new MemoryStore(), clock, clock);
		gs.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan()));
		gs.useRooms(new GameRooms(rs, id -> "sec".equals(id) ? Optional.of(secretRoom()) : Optional.empty()));
		return gs;
	}

	private void create(GameService gs, String hub, long seed) {
		Settings s = Settings.fast(60).withoutCabins();
		s.testRoom = "sec";
		s.roomTimeout = false;
		gs.create(hub, s, seed);
		for (int i = 0; i < 9; i++) {
			gs.join(hub, ids[i], "p" + i);
		}
	}

	@BeforeEach
	void setUp() {
		clock = new ManualClock();
		roomService = new RoomService(new MechanismRegistry(List.of(new DebugButton())), clock);
		service = newService(roomService);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
		}
		create(service, HUB, 11L);
	}

	private void toRooms(GameService gs, String hub) {
		gs.start(hub);
		toRoomsAfterStart(gs, hub);
	}

	private void toRoomsAfterStart(GameService gs, String hub) {
		gs.next(hub); // DOORS
		Split s = gs.read(hub, g -> Doors.validSplits(g.currentRound().assignment()).get(0));
		for (int k = 0; k < 3; k++) {
			for (int b : s.groups().get(k).members()) {
				gs.passDoor(hub, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
			}
		}
		gs.next(hub); // ROOMS
	}

	private static void assertCode(Runnable call, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(TrahisonException.class, ex -> assertThat(ex.code()).isEqualTo(code));
	}

	/** Stands the player next to the centre of a slot of room (1, group), in hub coordinates. */
	private void standAt(int player, int group, String slot, double dx) {
		RoomInstance room = roomService.find(HUB, 1, group).orElseThrow();
		double[] b = GameRooms.hubBox(room, slot);
		service.setZone(HUB, ids[player], "hall", (b[0] + b[3]) / 2 + dx, b[1], (b[2] + b[5]) / 2);
	}

	@Test
	void theLiveSecretOfEachPlacedRoomTakesTheItemsThePlanHadNoSpotFor() {
		service.start(HUB);
		Game g = service.find(HUB);
		assertThat(g.hiddenItems).hasSize(2);
		assertThat(g.unplacedItems).hasSize(5);
		toRoomsAfterStart(service, HUB); // the rooms only start with the round
		g = service.find(HUB);
		assertThat(g.hiddenItems).hasSize(5); // 2 plan spots + the 3 live secrets
		assertThat(g.unplacedItems).hasSize(2);
		for (int k = 0; k < 3; k++) {
			String live = roomService.find(HUB, 1, k).orElseThrow().activeSecret;
			assertThat(g.hiddenItems).containsKey(RoomSecrets.spotId(1, k, live));
			for (String other : List.of("a", "b", "c")) {
				if (!other.equals(live)) {
					assertThat(g.hiddenItems).doesNotContainKey(RoomSecrets.spotId(1, k, other));
				}
			}
		}
	}

	@Test
	void theDrawIsStableForOneSeed() {
		GameService other = newService(new RoomService(new MechanismRegistry(List.of(new DebugButton())), clock));
		create(other, "hub-t", 11L);
		toRooms(service, HUB);
		Game a = service.find(HUB);
		toRooms(other, "hub-t");
		Game b = other.find("hub-t");
		assertThat(b.hiddenItems).isEqualTo(a.hiddenItems);
		assertThat(b.unplacedItems).isEqualTo(a.unplacedItems);
	}

	@Test
	void onlyTheLiveCandidateYieldsAndTheInertOnesAnswerLikeAnEmptySpot() {
		toRooms(service, HUB);
		String live = roomService.find(HUB, 1, 0).orElseThrow().activeSecret;
		String inert = List.of("a", "b", "c").stream().filter(s -> !s.equals(live)).findFirst().orElseThrow();
		int p = service.read(HUB, g -> g.currentRound().split().group(Door.ALL.get(0)).members().iterator().next());
		standAt(p, 0, live, 0);
		assertCode(() -> service.search(HUB, ids[p], RoomSecrets.spotId(1, 0, live)), TrahisonException.CANNOT_SEARCH_NOW); // ROOMS: his room runs
		service.next(HUB); // FREE_TIME
		standAt(p, 0, live, 20);
		assertCode(() -> service.search(HUB, ids[p], RoomSecrets.spotId(1, 0, live)), TrahisonException.SPOT_TOO_FAR);
		standAt(p, 0, inert, 0);
		assertCode(() -> service.search(HUB, ids[p], RoomSecrets.spotId(1, 0, inert)), TrahisonException.SPOT_EMPTY);
		assertCode(() -> service.search(HUB, ids[p], RoomSecrets.spotId(1, 0, "exit")), TrahisonException.NO_SPOT); // not a candidate
		standAt(p, 0, live, 1);
		String item = service.find(HUB).hiddenItems.get(RoomSecrets.spotId(1, 0, live));
		assertThat(item).isNotNull();
		service.search(HUB, ids[p], RoomSecrets.spotId(1, 0, live));
		assertThat(service.find(HUB).players.get(p).items).contains(item);
		assertCode(() -> service.search(HUB, ids[p], RoomSecrets.spotId(1, 0, live)), TrahisonException.SPOT_EMPTY);
		assertThat(service.find(HUB).hiddenItems).hasSize(4);
	}

	@Test
	void aReplayKeepsTheHidingPlace() {
		toRooms(service, HUB);
		String live = roomService.find(HUB, 1, 1).orElseThrow().activeSecret;
		roomService.reset(HUB, 1, 987654321L);
		assertThat(roomService.find(HUB, 1, 1).orElseThrow().activeSecret).isEqualTo(live);
	}
}
