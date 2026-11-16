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
import com.mystaria.phantasmon_backend.events.trahison.rules.Vote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T6.1: death, the body it leaves, the bracelet taken from it, and who learns of the death. */
class BodiesTest {

	private static final String HUB = "hub-b";
	private static final Box BOX = new Box(0, 64, 0, 0, 66, 2);
	private static final String[] NAMES = { "rg", "rb", "gb" };
	private static final String[][] COLORS = { { "red", "green" }, { "red", "blue" }, { "green", "blue" } };

	private ManualClock clock;
	private GameService service;
	private final UUID[] ids = new UUID[9];

	private static LayoutContent plan() {
		List<LayoutZone> zones = new java.util.ArrayList<>(List.of(new LayoutZone("hall", "hall", List.of(BOX))));
		List<LayoutDoor> doors = new java.util.ArrayList<>();
		for (int k = 0; k < 3; k++) {
			zones.add(new LayoutZone("thr_" + NAMES[k], "doors", List.of(BOX)));
			zones.add(new LayoutZone("slot_" + k, "room_slot", List.of(BOX)));
			doors.add(new LayoutDoor("c_" + NAMES[k], "color", BOX, "hall", "slot_" + k, 1, List.of(COLORS[k]), "thr_" + NAMES[k]));
		}
		return new LayoutContent(zones, doors, null, null, null);
	}

	@BeforeEach
	void setUp() {
		clock = new ManualClock();
		service = new GameService(new MemoryStore(), clock, clock);
		service.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan()));
		service.create(HUB, Settings.fast(60).withoutCabins(), 11L);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
			service.join(HUB, ids[i], "p" + i);
		}
		service.start(HUB);
		service.next(HUB); // DOORS
	}

	private Split split() {
		return service.read(HUB, g -> Doors.validSplits(g.currentRound().assignment()).get(0));
	}

	private void everyonePasses() {
		Split s = split();
		for (int k = 0; k < 3; k++) {
			for (int b : s.groups().get(k).members()) {
				service.passDoor(HUB, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
			}
		}
	}

	private void at(int player, String zone, double x, double z) {
		service.setZone(HUB, ids[player], zone, x, 65, z);
	}

	private Map<String, Object> view(int player) {
		return service.read(HUB, g -> Views.build(g, ids[player], clock.instant()));
	}

	@SuppressWarnings("unchecked")
	private static boolean aliveIn(Map<String, Object> v, int player) {
		return (boolean) ((Map<String, Object>) ((List<Object>) v.get("players")).get(player)).get("alive");
	}

	private static void assertCode(Runnable call, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(TrahisonException.class, ex -> assertThat(ex.code()).isEqualTo(code));
	}

	@Test
	void aKillLeavesABodyWhereTheLastPositionWasAndKeepsPointsAndGroup() {
		everyonePasses();
		service.next(HUB); // ROOMS
		at(3, "slot_1", 5, 7);
		service.setPoints(HUB, 3, 6);
		int group = service.read(HUB, g -> g.currentRound().groupOf(3));
		service.kill(HUB, 3, "knife");
		Game g = service.find(HUB);
		assertThat(g.players.get(3).alive).isFalse();
		Body b = g.bodyOf(3);
		assertThat(b.zone).isEqualTo("slot_1");
		assertThat(b.x).isEqualTo(5.0);
		assertThat(b.z).isEqualTo(7.0);
		assertThat(b.braceletOnBody).isTrue();
		assertThat(g.bracelets.get(3).points).isEqualTo(6); // D13: points kept
		assertThat(g.bracelets.get(3).holder).isNull();
		assertThat(g.currentRound().groupOf(3)).isEqualTo(group); // D13: his place in the group is kept
		assertCode(() -> service.kill(HUB, 3, "knife"), TrahisonException.DEAD);
	}

	@Test
	void theDeathStaysSecretUntilTheBodyIsSeenUnlessTheSettingAnnouncesIt() {
		everyonePasses();
		service.next(HUB);
		at(3, "slot_1", 5, 7);
		service.kill(HUB, 3, "knife");
		assertThat(aliveIn(view(0), 3)).isTrue(); // nothing told
		assertThat(view(0).get("bodies")).isEqualTo(List.of());
		assertThat(view(0).get("bracelets").toString()).contains("owner_alive=true").doesNotContain("owner_alive=false");
		assertThat(aliveIn(view(3), 3)).isFalse(); // the ghost knows
		assertThat(((List<?>) view(3).get("bodies"))).hasSize(1);
		// someone far away did not see it
		at(0, "slot_1", 50, 50);
		assertCode(() -> service.bodySeen(HUB, ids[0], 3), TrahisonException.BODY_TOO_FAR);
		assertThat(aliveIn(view(0), 3)).isTrue();
		// someone near saw it
		at(1, "slot_1", 9, 7);
		service.bodySeen(HUB, ids[1], 3);
		assertThat(aliveIn(view(1), 3)).isFalse();
		assertThat(((List<?>) view(1).get("bodies"))).hasSize(1);
		assertThat(aliveIn(view(2), 3)).isTrue(); // the others still do not know
		// the setting announces to everybody
		service.find(HUB).settings.announceDeaths = true;
		at(4, "slot_1", 1, 1);
		service.kill(HUB, 4, "knife");
		assertThat(aliveIn(view(2), 4)).isFalse();
	}

	@Test
	void deathsAtTheDoorsAreAnnouncedAndTheBracelet0VotesAllyByDefault() {
		Split s = split();
		int late = s.groups().get(0).solo();
		for (int k = 0; k < 3; k++) {
			for (int b : s.groups().get(k).members()) {
				if (b != late) {
					service.passDoor(HUB, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
				}
			}
		}
		service.next(HUB); // the late one dies
		Game g = service.find(HUB);
		int before = g.bracelets.get(late).points;
		assertThat(g.players.get(late).alive).isFalse();
		assertThat(g.bodyOf(late).announced).isTrue();
		assertThat(aliveIn(view((late + 1) % 9), late)).isFalse();
		assertThat(g.bracelets.get(late).ownerAlive).isFalse();
		assertThat(g.currentRound().groupOf(late)).isEqualTo(0);
		// nobody votes: the dead one's side defaults to ALLY, like every missing vote
		for (int i = 0; i < 3; i++) {
			service.next(HUB); // FREE_TIME, VOTE (cabins not required), resolved
		}
		assertThat(g.phase).isEqualTo(Phase.RESULTS);
		assertThat(g.bracelets.get(late).points).isGreaterThan(before) // all ally: he gains like the others
			.isEqualTo(g.bracelets.get((late + 1) % 9).points);
	}

	@Test
	void aTakenBraceletOpensADoorForItsCarrier() {
		Split s = split();
		int solo = s.groups().get(0).solo();
		int pair0 = s.groups().get(0).pair().get(0);
		int stranger = -1;
		for (int i = 0; i < 9; i++) {
			if (!s.groups().get(0).members().contains(i)) {
				stranger = i;
				break;
			}
		}
		at(solo, "thr_rg", 0, 0);
		service.kill(HUB, solo, "knife"); // body at the threshold, he no longer counts
		at(pair0, "thr_rg", 0, 1);
		at(stranger, "thr_rg", 0, 1);
		assertThat(doorRg()).isEqualTo("CLOSED");
		service.takeBracelet(HUB, ids[stranger], solo);
		Game g = service.find(HUB);
		assertThat(g.players.get(stranger).carried).containsExactly(solo);
		assertThat(g.bracelets.get(solo).holder).isEqualTo(stranger);
		assertThat(g.bodyOf(solo).braceletOnBody).isFalse();
		assertThat(view(stranger).get("viewer").toString()).contains("carried=[" + solo + "]");
		assertThat(doorRg()).isEqualTo("OPEN");
		assertCode(() -> service.takeBracelet(HUB, ids[pair0], solo), TrahisonException.NO_BODY);
	}

	private String doorRg() {
		return (String) ((Map<?, ?>) view(0).get("doors_state")).get("c_rg");
	}

	@Test
	void takingNeedsTheZoneAndThreeBlocksAndALivingTaker() {
		everyonePasses();
		service.next(HUB);
		at(3, "slot_1", 5, 7);
		service.kill(HUB, 3, "knife");
		at(0, "slot_2", 5, 7); // same spot, other zone
		assertCode(() -> service.takeBracelet(HUB, ids[0], 3), TrahisonException.BODY_TOO_FAR);
		at(0, "slot_1", 5, 10.5); // 3.5 blocks
		assertCode(() -> service.takeBracelet(HUB, ids[0], 3), TrahisonException.BODY_TOO_FAR);
		assertCode(() -> service.takeBracelet(HUB, ids[0], 4), TrahisonException.NO_BODY);
		service.kill(HUB, 5, "knife");
		assertCode(() -> service.takeBracelet(HUB, ids[5], 3), TrahisonException.DEAD);
		at(0, "slot_1", 5, 9.9); // 2.9 blocks
		service.takeBracelet(HUB, ids[0], 3);
		assertThat(service.find(HUB).players.get(0).carried).containsExactly(3);
	}

	@Test
	void aDeadBraceletStillMovesWithTheVotesAndItsSideVotesAllyByDefault() {
		everyonePasses();
		service.next(HUB); // ROOMS
		at(3, "slot_1", 5, 7);
		service.kill(HUB, 3, "knife");
		service.next(HUB);
		service.next(HUB); // VOTE
		Split s = service.read(HUB, g -> g.currentRound().split());
		int k = service.read(HUB, g -> g.currentRound().groupOf(3));
		var group = s.group(Door.ALL.get(k));
		boolean pair = group.pair().contains(3);
		int before = service.find(HUB).bracelets.get(3).points;
		// his side does not vote (a dead one cannot): ALLY by default; the other side betrays
		if (pair) {
			service.vote(HUB, ids[group.solo()], Vote.BETRAY);
		} else {
			service.vote(HUB, ids[group.pair().get(0)], Vote.BETRAY);
		}
		service.next(HUB);
		assertThat(service.find(HUB).bracelets.get(3).points).isLessThan(before);
		assertThat(service.find(HUB).bracelets.get(3).ownerAlive).isFalse();
	}

	@Test
	void killingAPlayingGameMasterEndsTheGameAndTheOthersWin() {
		service.addGameMaster(HUB, ids[4]);
		service.kill(HUB, 4, "knife");
		Game g = service.find(HUB);
		assertThat(g.phase).isEqualTo(Phase.ENDED);
		assertThat(g.winners).containsExactly(0, 1, 2, 3, 5, 6, 7, 8);
		assertThat(g.log).extracting(e -> e.type).contains("killed", "game_master_killed");
	}

	@Test
	void aBodySurvivesARestart() {
		everyonePasses();
		service.next(HUB);
		at(3, "slot_1", 5, 7);
		service.kill(HUB, 3, "knife");
		at(1, "slot_1", 5, 7);
		service.bodySeen(HUB, ids[1], 3);
		Game copy = GameJson.read(GameJson.write(service.find(HUB)));
		assertThat(copy.bodyOf(3).seenBy).containsExactly(1);
		assertThat(copy.bodyOf(3).zone).isEqualTo("slot_1");
	}

	@Test
	void aDisconnectedLivingPlayerLeavesAMannequinAndComesBack() {
		everyonePasses();
		service.next(HUB); // ROOMS
		at(2, "slot_1", 5, 7);
		service.setConnected(HUB, ids[2], false);
		assertThat(view(0).get("disconnected").toString()).contains("player=2", "x=5.0", "z=7.0");
		assertThat(view(2).get("disconnected")).isEqualTo(List.of()); // not shown to himself
		service.setConnected(HUB, ids[2], true);
		assertThat(view(0).get("disconnected")).isEqualTo(List.of());
		// a ghost leaves a body, never a mannequin
		at(3, "slot_1", 1, 1);
		service.kill(HUB, 3, "knife");
		service.setConnected(HUB, ids[3], false);
		assertThat(view(0).get("disconnected")).isEqualTo(List.of());
	}

	@Test
	void aGhostSeesLikeAnOutsiderAndNeverLearnsWhoPlaysAsGameMaster() {
		service.addGameMaster(HUB, ids[6]);
		at(3, "hall", 1, 1);
		service.kill(HUB, 3, "knife");
		Map<String, Object> ghost = view(3);
		assertThat(((Map<?, ?>) ghost.get("viewer")).get("kind")).isEqualTo("GHOST");
		assertThat(ghost.get("gm")).isNull(); // D12: the block is the game masters'
		assertThat(((Map<?, ?>) ghost.get("viewer")).get("is_game_master")).isEqualTo(false);
		assertThat(ghost.get("game_masters")).isEqualTo(List.of());
		assertThat(ghost.get("ghost_display")).isEqualTo("SILHOUETTE");
		service.find(HUB).settings.ghostDisplay = Settings.GhostDisplay.HIDDEN;
		assertThat(view(0).get("ghost_display")).isEqualTo("HIDDEN");
	}
}
