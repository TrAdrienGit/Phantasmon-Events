package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.Box;
import com.mystaria.phantasmon_backend.events.trahison.layout.Cabin;
import com.mystaria.phantasmon_backend.events.trahison.layout.Layout;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutDoor;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutZone;

import static org.assertj.core.api.Assertions.assertThat;

/** T5.2 to T5.4: the sound zone of each player, the announce flag and the target of the ear, in the view. */
class VoiceZonesTest {

	private static final String HUB = "hub-v";
	private static final Box BOX = new Box(0, 64, 0, 0, 66, 2);
	private static final String CAB = "cab_red_green_pair";

	private final ManualClock clock = new ManualClock();
	private final GameService service = new GameService(new MemoryStore(), clock, clock);
	private final UUID[] ids = new UUID[9];
	private final UUID gm = new UUID(7, 7);

	private static LayoutContent plan() {
		List<LayoutZone> zones = new ArrayList<>(List.of(new LayoutZone("hall", "hall", List.of(BOX)), new LayoutZone("vote", "vote", List.of(BOX)),
			new LayoutZone("corridor", "corridor", List.of(BOX)), new LayoutZone(CAB, "cabin", List.of(BOX))));
		List<LayoutDoor> doors = List.of(new LayoutDoor("door_" + CAB, "cabin", BOX, "vote", CAB, null, null));
		return new LayoutContent(zones, doors, null, List.of(new Cabin(CAB, "red_green", "pair", CAB, "door_" + CAB)), null);
	}

	private void game() {
		service.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan()));
		service.create(HUB, Settings.fast(100).withoutRoles(), 5L);
		service.setGameMaster(HUB, gm, "gm", true);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
			service.join(HUB, ids[i], "p" + i);
		}
		service.start(HUB);
	}

	private void stand(int p, String zone) {
		service.setZone(HUB, ids[p], zone, 0, 64, 0);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> voice(UUID who) {
		return (Map<String, Object>) service.read(HUB, g -> Views.build(g, who, clock.instant())).get("voice");
	}

	private void closeCabinOf(int p) {
		service.read(HUB, g -> g.cabinLocks.put(CAB, new CabinLock(p, 0, true, false, List.of(p))));
	}

	@Test
	void theSoundZoneIsTheZoneOfThePlan() {
		Map<String, String> cabins = Map.of(CAB, CAB);
		assertThat(VoiceZones.soundZone("hall", cabins, Map.of())).isEqualTo("hall");
		assertThat(VoiceZones.soundZone("corridor", cabins, Map.of())).isEqualTo("corridor");
		assertThat(VoiceZones.soundZone(null, cabins, Map.of())).isNull();
		assertThat(VoiceZones.soundZone(" ", cabins, Map.of())).isNull();
	}

	@Test
	void anOpenCabinIsJustItsZoneAndAClosedOneIsAZoneOfItsOwn() {
		Map<String, String> cabins = Map.of(CAB, CAB);
		CabinLock held = new CabinLock(0, 0, true, false, List.of(0));
		assertThat(VoiceZones.soundZone(CAB, cabins, Map.of())).isEqualTo(CAB);
		assertThat(VoiceZones.soundZone(CAB, cabins, Map.of(CAB, held))).isEqualTo(VoiceZones.CABIN_PREFIX + CAB);
		assertThat(VoiceZones.soundZone("vote", cabins, Map.of(CAB, held))).isEqualTo("vote"); // the others' zones do not change
		// the cabin zone can never be confused with a zone of the plan
		assertThat(VoiceZones.soundZone(CAB, cabins, Map.of(CAB, held))).isNotEqualTo(CAB);
	}

	@Test
	void aLivingPlayerLearnsOnlyWhoSharesHisSoundZone() {
		game();
		stand(0, "hall");
		stand(1, "hall");
		stand(2, "corridor"); // the next zone: an open door does not join them
		Map<String, Object> v = voice(ids[0]);
		assertThat(v.get("zone")).isEqualTo("hall");
		assertThat(v.get("same")).isEqualTo(List.of(1));
		assertThat(v.get("sound_zones")).isNull(); // where the others are stays secret
		assertThat(voice(ids[2]).get("same")).isEqualTo(List.of());
	}

	@Test
	void aClosedCabinHearsNothingOutsideAndHearsNobodyInTheSameZone() {
		game();
		stand(0, CAB);
		stand(1, CAB); // reported in the cabin's zone too
		stand(2, "hall");
		assertThat(voice(ids[0]).get("same")).isEqualTo(List.of(1)); // open: the zone is shared
		closeCabinOf(0);
		stand(3, "hall");
		assertThat(voice(ids[0]).get("zone")).isEqualTo(VoiceZones.CABIN_PREFIX + CAB);
		assertThat(voice(ids[2]).get("same")).isEqualTo(List.of(3)); // the hall does not hear the cabin
		assertThat(voice(ids[2]).get("zone")).isEqualTo("hall");
	}

	@Test
	void everyoneWhoSeesAllGetsTheSoundZoneOfEachSeat() {
		game();
		stand(0, CAB);
		closeCabinOf(0);
		stand(1, "hall");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> zones = (List<Map<String, Object>>) voice(gm).get("sound_zones");
		assertThat(zones).hasSize(9);
		assertThat(zones.get(0)).containsEntry("sound_zone", VoiceZones.CABIN_PREFIX + CAB);
		assertThat(zones.get(1)).containsEntry("sound_zone", "hall");
		assertThat(zones.get(2)).containsEntry("sound_zone", null);
	}

	@Test
	void theAnnounceFlagIsPublicOnlyForAGameMasterAndEndsWithTheKey() {
		game();
		service.setAnnounce(HUB, ids[0], true); // a player is no game master: refused silently
		assertThat(voice(ids[1]).get("announcing")).isEqualTo(List.of());
		service.setAnnounce(HUB, gm, true);
		assertThat(voice(ids[1]).get("announcing")).isEqualTo(List.of(gm.toString()));
		assertThat(voice(ids[1]).get("announcing")).isEqualTo(voice(gm).get("announcing"));
		service.setAnnounce(HUB, gm, false);
		assertThat(voice(ids[1]).get("announcing")).isEqualTo(List.of());
	}

	@Test
	void theEarTargetsTheSeatsInsideTheListenedCabinDuringAVoteOnly() {
		game();
		stand(1, CAB);
		stand(2, CAB);
		stand(3, "hall");
		service.read(HUB, g -> g.listening.put(0, CAB));
		assertThat(voice(ids[0]).get("ear")).isNull(); // not a vote yet
		service.read(HUB, g -> {
			g.phase = Phase.VOTE;
			return null;
		});
		assertThat(voice(ids[0]).get("ear")).isEqualTo(List.of(1, 2));
		closeCabinOf(1);
		assertThat(voice(ids[0]).get("ear")).isEqualTo(List.of(1, 2)); // closed: its own sound zone, same people
		stand(2, "hall"); // 2 steps out of the (closed, in a test) cabin zone
		assertThat(voice(ids[0]).get("ear")).isEqualTo(List.of(1));
		assertThat(voice(ids[4]).get("ear")).isNull(); // nobody else gets the target
		assertThat(voice(gm).get("ear")).isNull();
	}
}
