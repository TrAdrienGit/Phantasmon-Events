package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Instant;
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
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;

import static org.assertj.core.api.Assertions.assertThat;

/** The colour doors open by themselves for the bracelets standing at their threshold (T2.3). */
class DoorThresholdsTest {

	private static final String HUB = "hub-t";
	private static final Box BOX = new Box(0, 64, 0, 0, 66, 2);
	private static final String[] NAMES = { "rg", "rb", "gb" };
	private static final String[][] COLORS = { { "red", "green" }, { "red", "blue" }, { "green", "blue" } };

	private ManualClock clock;
	private GameService service;
	private final UUID[] ids = new UUID[9];

	private static LayoutContent plan(boolean withThresholds) {
		List<LayoutZone> zones = new java.util.ArrayList<>(List.of(new LayoutZone("hall", "hall", List.of(BOX))));
		List<LayoutDoor> doors = new java.util.ArrayList<>();
		for (int k = 0; k < 3; k++) {
			zones.add(new LayoutZone("thr_" + NAMES[k], "doors", List.of(BOX)));
			zones.add(new LayoutZone("slot_" + k, "room_slot", List.of(BOX)));
			doors.add(new LayoutDoor("c_" + NAMES[k], "color", BOX, withThresholds ? "hall" : "thr_" + NAMES[k], "slot_" + k, 1,
				List.of(COLORS[k]), withThresholds ? "thr_" + NAMES[k] : null));
		}
		return new LayoutContent(zones, doors, null, null, null);
	}

	private void begin(boolean withThresholds) {
		clock = new ManualClock();
		service = new GameService(new MemoryStore(), clock, clock);
		service.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan(withThresholds)));
		service.create(HUB, Settings.fast(60), 11L);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
			service.join(HUB, ids[i], "p" + i);
		}
		service.start(HUB);
		clock.advanceSeconds(60); // PROLOGUE -> DOORS
		assertThat(service.find(HUB).phase).isEqualTo(Phase.DOORS);
	}

	@BeforeEach
	void setUp() {
		begin(true);
	}

	private Split split(int index) {
		return service.read(HUB, g -> Doors.validSplits(g.currentRound().assignment()).get(index));
	}

	private void goTo(int player, String zone) {
		service.setZone(HUB, ids[player], zone, 0, 65, 0);
	}

	private void gather(Split s, int door) {
		for (int b : s.groups().get(door).members()) {
			goTo(b, "thr_" + NAMES[door]);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> hint(int door) {
		Map<String, Object> v = service.read(HUB, g -> Views.build(g, ids[0], clock.instant()));
		return ((List<Map<String, Object>>) v.get("door_hints")).get(door);
	}

	private String state(int door) {
		Map<String, Object> v = service.read(HUB, g -> Views.build(g, ids[0], clock.instant()));
		return (String) ((Map<?, ?>) v.get("doors_state")).get("c_" + NAMES[door]);
	}

	private int passed(int player) {
		return service.read(HUB, g -> g.currentRound().passedDoor[player]);
	}

	private void bothSplitsWork(int splitIndex) {
		Split s = split(splitIndex);
		for (int door = 0; door < 3; door++) {
			assertThat(state(door)).isEqualTo("CLOSED");
			gather(s, door);
			assertThat(state(door)).isEqualTo("OPEN");
			assertThat(hint(door)).containsEntry("ready", true);
			// the group walks through: its room side becomes the player's zone
			for (int b : s.groups().get(door).members()) {
				goTo(b, "slot_" + door);
				assertThat(passed(b)).isEqualTo(door);
			}
			assertThat(state(door)).isEqualTo("CLOSED"); // everybody crossed: shut again at once
		}
		clock.advanceSeconds(60); // DOORS -> ROOMS, nobody is penalised and the split is the one the players took
		assertThat(service.<Integer>read(HUB, g -> g.rounds.get(0).splitIndex)).isEqualTo(splitIndex);
		assertThat(service.<List<Integer>>read(HUB, g -> g.rounds.get(0).diedAtDoors)).isEmpty();
	}

	@Test
	void theFirstSplitOpensAndPassesEveryDoor() {
		bothSplitsWork(0);
	}

	@Test
	void theSecondSplitOpensAndPassesEveryDoor() {
		bothSplitsWork(1);
	}

	@Test
	void aPlayerAloneOpensNothingAndTheHintSaysWhatIsMissing() {
		Split s = split(0);
		int solo = s.groups().get(0).solo();
		goTo(solo, "thr_rg");
		assertThat(state(0)).isEqualTo("CLOSED");
		Map<String, Object> h = hint(0);
		assertThat(h).containsEntry("ready", false).containsEntry("present", 1).containsEntry("incompatible", false);
		// the solo is on the door in split 0 (RED pair + GREEN solo): what lacks is that pair
		assertThat(h.get("missing")).isEqualTo(List.of(Map.of("color", s.groups().get(0).pairColor().name(), "status", "PAIR")));
		assertThat(h.get("open_until")).isNull();
	}

	@Test
	void nobodyThereLacksEveryPieceOfEitherSplit() {
		Map<String, Object> h = hint(0);
		assertThat(h).containsEntry("present", 0);
		assertThat((List<?>) h.get("missing")).hasSize(2);
		assertThat((List<?>) h.get("alt_missing")).hasSize(2);
		assertThat(h.get("missing")).isNotEqualTo(h.get("alt_missing"));
	}

	@Test
	void aCombinationOfTheOtherSplitIsRefusedOnceTheFirstPassFixedTheSplit() {
		Split s0 = split(0);
		Split s1 = split(1);
		gather(s0, 0);
		int first = s0.groups().get(0).solo();
		goTo(first, "slot_0"); // one pass: split 0 is now the only possible one
		assertThat(passed(first)).isEqualTo(0);
		// the group of split 1 for door 1 stands at its threshold: right colours, wrong split
		gather(s1, 1);
		assertThat(state(1)).isEqualTo("CLOSED");
		assertThat(hint(1)).containsEntry("ready", false).containsEntry("incompatible", true);
		// ... and the group of split 0 for door 1 opens it
		gather(s0, 1);
		assertThat(state(1)).isEqualTo("OPEN");
	}

	@Test
	void theDoorShutsAfterTheOpenSecondsAndALatePlayerIsNotRecorded() {
		Split s = split(0);
		gather(s, 2);
		assertThat(state(2)).isEqualTo("OPEN");
		long until = (Long) hint(2).get("open_until");
		assertThat(until).isEqualTo(clock.instant().toEpochMilli() + Settings.DEFAULT_DOOR_OPEN_SECONDS * 1000L);
		clock.advanceSeconds(5);
		assertThat(state(2)).isEqualTo("OPEN");
		int early = s.groups().get(2).solo();
		goTo(early, "slot_2");
		assertThat(passed(early)).isEqualTo(2);
		clock.advanceSeconds(2);
		assertThat(state(2)).isEqualTo("CLOSED");
		int late = s.groups().get(2).pair().get(0);
		goTo(late, "slot_2");
		assertThat(passed(late)).isEqualTo(-1);
	}

	@Test
	void theOpenSecondsAreConfigurable() {
		service.find(HUB).settings.doorOpenSeconds = 2;
		gather(split(0), 0);
		assertThat(state(0)).isEqualTo("OPEN");
		clock.advanceSeconds(3);
		assertThat(state(0)).isEqualTo("CLOSED");
	}

	@Test
	void deadPlayersAndGhostsDoNotCount() {
		Split s = split(0);
		int solo = s.groups().get(0).solo();
		service.find(HUB).players.get(solo).alive = false;
		gather(s, 0);
		assertThat(state(0)).isEqualTo("CLOSED");
		assertThat(hint(0)).containsEntry("present", 2).containsEntry("ready", false);
	}

	@Test
	void aCarriedBraceletCountsForItsCarrier() {
		Split s = split(0);
		DoorGroupFacts f = new DoorGroupFacts(s, 0);
		// a player outside the group carries the solo bracelet of the group, and stands there with one pair bracelet
		int stranger = -1;
		for (int i = 0; i < 9; i++) {
			if (!s.groups().get(0).members().contains(i) && !s.groups().get(0).pair().contains(i)) {
				stranger = i;
				break;
			}
		}
		service.find(HUB).players.get(stranger).carried.add(f.solo);
		goTo(f.pair0, "thr_rg");
		goTo(stranger, "thr_rg");
		assertThat(state(0)).isEqualTo("OPEN");
	}

	private record DoorGroupFacts(int solo, int pair0) {
		DoorGroupFacts(Split s, int door) {
			this(s.groups().get(door).solo(), s.groups().get(door).pair().get(0));
		}
	}

	@Test
	void theDoorsShutAtTheEndOfTheStageAndTheLateOnesDie() {
		Split s = split(0);
		gather(s, 0);
		assertThat(state(0)).isEqualTo("OPEN");
		clock.advanceSeconds(60); // DOORS -> ROOMS with nobody having crossed
		assertThat(service.find(HUB).phase).isEqualTo(Phase.ROOMS);
		assertThat(service.<Map<String, DoorWindow>>read(HUB, g -> g.doorWindows)).isEmpty();
		assertThat(state(0)).isEqualTo("CLOSED");
		assertThat(service.<List<Integer>>read(HUB, g -> g.currentRound().diedAtDoors)).hasSize(9);
	}

	@Test
	void withoutAThresholdFieldTheHallSideZoneIsTheThreshold() {
		begin(false);
		Split s = split(1);
		for (int b : s.groups().get(1).members()) {
			goTo(b, "thr_rb"); // zone_a of door c_rb
		}
		assertThat(state(1)).isEqualTo("OPEN");
	}

	@Test
	void aWindowSurvivesARestart() {
		gather(split(0), 0);
		assertThat(state(0)).isEqualTo("OPEN");
		assertThat(service.<Map<String, DoorWindow>>read(HUB, g -> GameJson.read(GameJson.write(g)).doorWindows)).containsKey("c_rg");
	}

	@Test
	void thePassOfAnotherDoorOfTheSameBraceletColourIsRefusedSilently() {
		Split s = split(0);
		gather(s, 0);
		// a member of door 0's group walks into the room side of door 1, which is closed: nothing happens
		int b = s.groups().get(0).solo();
		goTo(b, "slot_1");
		assertThat(passed(b)).isEqualTo(-1);
		assertThat(Door.ALL).hasSize(3);
	}
}
