package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

/** T7.3 (the automatic temptations) and the game master gestures of T7.4, played through the service. */
class TemptationsTest {

	private static final String HUB = "hub-t";
	private static final Box BOX = new Box(0, 64, 0, 0, 66, 2);
	private static final String[] NAMES = { "rg", "rb", "gb" };
	private static final String[][] COLORS = { { "red", "green" }, { "red", "blue" }, { "green", "blue" } };

	private ManualClock clock;
	private GameService service;
	private final UUID[] ids = new UUID[9];
	private final List<String> whispers = new ArrayList<>();
	private final List<String> announces = new ArrayList<>();

	private static LayoutContent plan() {
		List<LayoutZone> zones = new ArrayList<>(List.of(new LayoutZone("hall", "hall", List.of(BOX))));
		List<LayoutDoor> doors = new ArrayList<>();
		for (int k = 0; k < 3; k++) {
			zones.add(new LayoutZone("thr_" + NAMES[k], "doors", List.of(BOX)));
			zones.add(new LayoutZone("slot_" + k, "room_slot", List.of(BOX)));
			doors.add(new LayoutDoor("c_" + NAMES[k], "color", BOX, "hall", "slot_" + k, 1, List.of(COLORS[k]), "thr_" + NAMES[k]));
		}
		return new LayoutContent(zones, doors, null, null, List.of());
	}

	@BeforeEach
	void setUp() {
		clock = new ManualClock();
		service = new GameService(new MemoryStore(), clock, clock);
		service.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan()));
		service.addListener(new GameListener() {
			@Override
			public void whispered(Game game, int seat, String key, List<String> args) {
				whispers.add(seat + ":" + key + ":" + args);
			}

			@Override
			public void announced(Game game, String key, List<String> args) {
				announces.add(key + ":" + args);
			}
		});
		service.create(HUB, Settings.fast(60).withoutCabins().withoutRoles(), 11L);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
			service.join(HUB, ids[i], "p" + i);
		}
	}

	private Split split() {
		return service.read(HUB, g -> Doors.validSplits(g.currentRound().assignment()).get(0));
	}

	private void toVote() {
		service.start(HUB);
		service.next(HUB); // DOORS
		Split s = split();
		for (int k = 0; k < 3; k++) {
			for (int b : s.groups().get(k).members()) {
				service.passDoor(HUB, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
			}
		}
		service.next(HUB); // ROOMS
		service.next(HUB); // FREE_TIME
		service.next(HUB); // VOTE
	}

	private <T> T read(java.util.function.Function<Game, T> f) {
		return service.read(HUB, f);
	}

	private long count(String prefix) {
		return whispers.stream().filter(w -> w.contains(prefix)).count();
	}

	private int offeree() {
		return service.read(HUB, g -> g.temptations.offerSeat);
	}

	@Test
	void anOfferIsWhisperedToOneSeatAndAnnouncedWithoutAName() {
		toVote();
		assertThat(count("say.offer")).isEqualTo(1);
		int seat = offeree();
		assertThat(whispers).anyMatch(w -> w.startsWith(seat + ":phantasmon_events.trahison.say.offer"));
		assertThat(announces).contains("offer_made:[]");
	}

	@Test
	void theOfferIsDrawnFromTheSeedAndSurvivesNothingElse() {
		toVote();
		int first = offeree();
		setUp();
		toVote();
		assertThat(offeree()).isEqualTo(first);
	}

	@Test
	void betrayingAfterTheOfferIsWorthOnePointMore() {
		toVote();
		int seat = offeree();
		service.vote(HUB, ids[seat], Vote.BETRAY);
		service.next(HUB); // results
		assertThat(this.<Boolean>read(g -> g.log.stream().anyMatch(e -> e.type.equals("offer_taken")))).isTrue();
	}

	@Test
	void allyingLeavesTheOfferUnpaid() {
		toVote();
		int seat = offeree();
		service.vote(HUB, ids[seat], Vote.ALLY);
		service.next(HUB);
		assertThat(this.<Boolean>read(g -> g.log.stream().anyMatch(e -> e.type.equals("offer_taken")))).isFalse();
	}

	@Test
	void offersCanBeSwitchedOff() {
		service.read(HUB, g -> g.settings.offers = false);
		toVote();
		assertThat(count("say.offer")).isZero();
		assertThat(announces).doesNotContain("offer_made:[]");
	}

	@Test
	void theGameMasterCanForceAnOfferDuringAVoteOnly() {
		assertThatThrownBy(() -> service.offer(HUB, 3)).isInstanceOf(TrahisonException.class);
		toVote();
		long before = count("say.offer");
		int other = (offeree() + 1) % 9;
		service.offer(HUB, other);
		assertThat(count("say.offer")).isEqualTo(before + 1);
		assertThat(this.<Boolean>read(g -> g.temptations.offerForced)).isTrue();
	}

	@Test
	void aLeakFollowsTheResultsAndIsTrue() {
		toVote();
		for (int i = 0; i < 9; i++) {
			try {
				service.vote(HUB, ids[i], Vote.ALLY);
			} catch (TrahisonException ignored) {
				// the second member of a pair
			}
		}
		service.next(HUB); // RESULTS
		assertThat(count("say.leak.")).isEqualTo(1);
		String leak = whispers.stream().filter(w -> w.contains("say.leak.")).findFirst().orElseThrow();
		int receiver = Integer.parseInt(leak.substring(0, leak.indexOf(':')));
		assertThat(leak).doesNotContain("p" + receiver + "]").doesNotContain("[p" + receiver + ",");
	}

	@Test
	void leaksCanBeSwitchedOff() {
		service.read(HUB, g -> g.settings.leaks = false);
		toVote();
		service.vote(HUB, ids[0], Vote.ALLY);
		service.next(HUB);
		assertThat(count("say.leak.")).isZero();
	}

	@Test
	void theFirstRoomOutIsAnnouncedOnce() {
		service.start(HUB);
		service.next(HUB);
		Split s = split();
		for (int k = 0; k < 3; k++) {
			for (int b : s.groups().get(k).members()) {
				service.passDoor(HUB, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
			}
		}
		service.next(HUB); // ROOMS
		service.debugSolve(HUB, ids[0]);
		service.debugSolve(HUB, ids[s.groups().get(1).members().iterator().next()]);
		assertThat(announces.stream().filter(a -> a.startsWith("race_first")).count()).isEqualTo(1);
	}

	@Test
	void gameMasterWhisperSayAndRevive() {
		toVote();
		service.gmWhisper(HUB, 2, "psst");
		assertThat(whispers).contains("2:phantasmon_events.trahison.say.gm_whisper:[psst]");
		service.gmSay(HUB, "bonsoir");
		assertThat(announces).contains("gm:[bonsoir]");
		assertThatThrownBy(() -> service.gmSay(HUB, "  ")).isInstanceOf(TrahisonException.class);
		service.kill(HUB, 4, "gm");
		assertThat(this.<Boolean>read(g -> g.players.get(4).alive)).isFalse();
		service.revive(HUB, 4);
		assertThat(this.<Boolean>read(g -> g.players.get(4).alive)).isTrue();
		assertThat(this.<Body>read(g -> g.bodyOf(4))).isNull();
		assertThat(this.<Boolean>read(g -> g.log.stream().anyMatch(e -> e.type.equals("revived")))).isTrue();
	}

	@Test
	void settingsChangeOnlyInTheLobby() {
		service.configure(HUB, s -> s.race = false);
		assertThat(this.<Boolean>read(g -> g.settings.race)).isFalse();
		toVote();
		assertThatThrownBy(() -> service.configure(HUB, s -> s.race = true)).isInstanceOf(TrahisonException.class);
	}

	@Test
	void pureDrawsFollowTheSeed() {
		assertThat(Temptations.pickOfferee(5, 1, List.of())).isEqualTo(-1);
		List<Integer> c = List.of(0, 1, 2, 3, 4, 5);
		assertThat(Temptations.pickOfferee(5, 1, c)).isEqualTo(Temptations.pickOfferee(5, 1, c));
		var facts = List.of(new Temptations.Fact("vote_time", 1, List.of("a", "3")));
		assertThat(Temptations.pickLeak(5, 1, List.of(1), facts)).isNull(); // the only fact is about the only receiver
		assertThat(Temptations.pickLeak(5, 1, List.of(1, 2), facts).receiver()).isEqualTo(2);
	}
}
