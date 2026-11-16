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
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.DoorGroup;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;
import com.mystaria.phantasmon_backend.events.trahison.rules.Vote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T2.4: closing a cabin, voting only from one's own closed cabin, the partner locked out, reopening, bots, the free vote. */
class CabinsTest {

	private static final String HUB = "hub-c";
	private static final Box BOX = new Box(0, 64, 0, 0, 66, 2);
	private static final String[] PAIRS = { "red_green", "red_blue", "green_blue" };

	private final ManualClock clock = new ManualClock();
	private final MemoryStore store = new MemoryStore();
	private final GameService service = new GameService(store, clock, clock);
	private final UUID[] ids = new UUID[9];

	private static LayoutContent plan() {
		List<LayoutZone> zones = new ArrayList<>(List.of(new LayoutZone("vote", "vote", List.of(BOX))));
		List<LayoutDoor> doors = new ArrayList<>();
		List<Cabin> cabins = new ArrayList<>();
		for (String pair : PAIRS) {
			for (String side : List.of("pair", "solo")) {
				String id = "cab_" + pair + "_" + side;
				zones.add(new LayoutZone(id, "cabin", List.of(BOX)));
				doors.add(new LayoutDoor("door_" + id, "cabin", BOX, "vote", id, null, null));
				cabins.add(new Cabin(id, pair, side, id, "door_" + id));
			}
		}
		return new LayoutContent(zones, doors, null, cabins, null);
	}

	/** A game in VOTE (round 1), 9 humans unless {@code bots} of them are fake players. Everybody passed a colour door. */
	private void gameInVote(Settings settings, int bots) {
		service.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan()));
		service.create(HUB, settings, 5L);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
			service.join(HUB, ids[i], "p" + i, i >= 9 - bots);
		}
		service.start(HUB);
		clock.advanceSeconds(100); // PROLOGUE -> DOORS
		Split s = Doors.validSplits(service.read(HUB, g -> g.currentRound().assignment())).get(0);
		for (int k = 0; k < 3; k++) {
			for (int b : s.groups().get(k).members()) {
				service.passDoor(HUB, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
			}
		}
		for (int i = 0; i < 3; i++) {
			clock.advanceSeconds(100); // ROOMS, FREE_TIME, VOTE
		}
		assertThat(service.find(HUB).phase).isEqualTo(Phase.VOTE);
	}

	private void gameInVote() {
		gameInVote(Settings.fast(100), 0);
	}

	private String cabinOf(int p) {
		return service.read(HUB, g -> g.myCabins.get(p));
	}

	private void stand(int p, String zone) {
		service.setZone(HUB, ids[p], zone, 0, 64, 0);
	}

	private DoorGroup group(int k) {
		return service.read(HUB, g -> g.currentRound().split().group(Door.ALL.get(k)));
	}

	private Map<String, Object> view(int p) {
		return service.read(HUB, g -> Views.build(g, ids[p], clock.instant()));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> cabinView(int p, String cabin) {
		return ((List<Map<String, Object>>) view(p).get("cabins")).stream().filter(c -> cabin.equals(c.get("id"))).findFirst().orElseThrow();
	}

	@SuppressWarnings("unchecked")
	private String doorState(int p, String doorId) {
		return (String) ((Map<String, Object>) view(p).get("doors_state")).get(doorId);
	}

	private static void assertCode(Runnable call, String code) {
		assertThatThrownBy(call::run).isInstanceOf(TrahisonException.class).extracting(e -> ((TrahisonException) e).code()).isEqualTo(code);
	}

	@Test
	void everyPlayerHasTheCabinOfHisDoorColoursAndSide() {
		gameInVote();
		for (int k = 0; k < 3; k++) {
			DoorGroup g = group(k);
			String pair = PAIRS[k];
			assertThat(cabinOf(g.pair().get(0))).isEqualTo("cab_" + pair + "_pair");
			assertThat(cabinOf(g.pair().get(1))).isEqualTo("cab_" + pair + "_pair");
			assertThat(cabinOf(g.solo())).isEqualTo("cab_" + pair + "_solo");
		}
		int p = group(0).solo();
		assertThat(((Map<?, ?>) view(p).get("viewer")).get("my_cabin")).isEqualTo("cab_red_green_solo");
	}

	@Test
	void closingNeedsTheOwnCabinFromInside() {
		gameInVote();
		int p = group(0).solo();
		assertCode(() -> service.closeCabin(HUB, ids[p]), TrahisonException.NOT_IN_CABIN); // no zone known
		stand(p, "vote");
		assertCode(() -> service.closeCabin(HUB, ids[p]), TrahisonException.NOT_IN_CABIN); // the hall of the cabins
		stand(p, "cab_red_green_pair");
		assertCode(() -> service.closeCabin(HUB, ids[p]), TrahisonException.WRONG_CABIN);
		stand(p, "cab_red_blue_solo");
		assertCode(() -> service.closeCabin(HUB, ids[p]), TrahisonException.WRONG_CABIN);
		assertThat(service.find(HUB).cabinLocks).isEmpty(); // somebody else's cabin is not held by walking in
	}

	@Test
	void aSoloWhoEntersLocksHisCabinAtOnceAndNobodyElseGetsIn() {
		gameInVote();
		int p = group(0).solo();
		int other = group(1).solo();
		assertThat(doorState(other, "door_cab_red_green_solo")).isEqualTo("OPEN");
		stand(p, "cab_red_green_solo");
		assertThat(service.find(HUB).cabinLocks.get("cab_red_green_solo").locked()).isTrue();
		assertThat(doorState(other, "door_cab_red_green_solo")).isEqualTo("CLOSED");
		assertThat(doorState(p, "door_cab_red_green_solo")).isEqualTo("CLOSED"); // locked in until he has voted
		assertThat(doorState(other, "door_cab_red_blue_solo")).isEqualTo("OPEN");
		assertThat(cabinView(other, "cab_red_green_solo")).containsEntry("closed", true).containsEntry("state", "LOCKED");
		assertThat(cabinView(other, "cab_red_blue_solo")).containsEntry("closed", false).containsEntry("state", "FREE");
		assertThat(view(other).toString()).doesNotContain("closed_by");
		// the key does nothing more once it is locked
		service.closeCabin(HUB, ids[p]);
		assertThat(service.find(HUB).cabinLocks).hasSize(1);
	}

	@Test
	void aVoteNeedsHisOwnLockedCabinAndOpensItsDoorForHim() {
		gameInVote();
		int p = group(0).solo();
		assertCode(() -> service.vote(HUB, ids[p], Vote.BETRAY), TrahisonException.NOT_IN_CABIN);
		stand(p, "cab_red_blue_solo");
		assertCode(() -> service.vote(HUB, ids[p], Vote.BETRAY), TrahisonException.WRONG_CABIN);
		stand(p, "cab_red_green_solo");
		service.vote(HUB, ids[p], Vote.BETRAY);
		assertThat(((Map<?, ?>) view(p).get("viewer")).get("own_vote")).isEqualTo("BETRAY");
		assertCode(() -> service.vote(HUB, ids[p], Vote.ALLY), TrahisonException.ALREADY_VOTED);
		// D5: his group voted, the door opens for him (and only him) at once
		assertThat(cabinView(p, "cab_red_green_solo")).containsEntry("state", "RELEASED");
		assertThat(doorState(p, "door_cab_red_green_solo")).isEqualTo("OPEN");
		assertThat(doorState(group(1).solo(), "door_cab_red_green_solo")).isEqualTo("CLOSED");
		// he walks out: the cabin is free again for everybody
		stand(p, "vote");
		assertThat(service.find(HUB).cabinLocks).isEmpty();
		assertThat(doorState(group(1).solo(), "door_cab_red_green_solo")).isEqualTo("OPEN");
	}

	@Test
	void aVoteIsRefusedWhileThePartnerIsStillExpected() {
		gameInVote();
		DoorGroup g = group(1);
		int first = g.pair().get(0);
		stand(first, "cab_red_blue_pair");
		assertThat(cabinView(0, "cab_red_blue_pair")).containsEntry("state", "WAITING");
		assertCode(() -> service.vote(HUB, ids[first], Vote.ALLY), TrahisonException.CABIN_NOT_CLOSED);
	}

	@Test
	void aPairEntersTogetherThePartnerHasThirtySecondsAndTheCabinLocksWhenBothAreIn() {
		gameInVote();
		DoorGroup g = group(1);
		int first = g.pair().get(0);
		int partner = g.pair().get(1);
		int stranger = group(0).solo();
		stand(first, "cab_red_blue_pair");
		Map<String, Object> cab = cabinView(stranger, "cab_red_blue_pair");
		assertThat(cab).containsEntry("state", "WAITING").containsEntry("closed", true);
		assertThat((Long) cab.get("window_until")).isEqualTo(clock.millis() + 30_000);
		// the door is shut to the others and open to the partner (and the one inside)
		assertThat(doorState(stranger, "door_cab_red_blue_pair")).isEqualTo("CLOSED");
		assertThat(doorState(partner, "door_cab_red_blue_pair")).isEqualTo("OPEN");
		assertThat(doorState(first, "door_cab_red_blue_pair")).isEqualTo("OPEN");
		clock.advanceSeconds(20);
		stand(partner, "cab_red_blue_pair");
		assertThat(cabinView(stranger, "cab_red_blue_pair")).containsEntry("state", "LOCKED").containsEntry("window_until", null);
		assertThat(doorState(partner, "door_cab_red_blue_pair")).isEqualTo("CLOSED");
		clock.advanceSeconds(20); // the old window would have ended: nothing changes
		assertThat(service.find(HUB).cabinLocks.get("cab_red_blue_pair").locked()).isTrue();
		// either of them votes for the pair, once
		service.vote(HUB, ids[partner], Vote.BETRAY);
		assertCode(() -> service.vote(HUB, ids[first], Vote.ALLY), TrahisonException.ALREADY_VOTED);
		assertThat(service.find(HUB).currentRound().pairVoter[1]).isEqualTo(partner);
		// D5: both may leave now
		assertThat(doorState(first, "door_cab_red_blue_pair")).isEqualTo("OPEN");
		assertThat(doorState(partner, "door_cab_red_blue_pair")).isEqualTo("OPEN");
		assertThat(doorState(stranger, "door_cab_red_blue_pair")).isEqualTo("CLOSED");
		stand(first, "vote");
		assertThat(doorState(stranger, "door_cab_red_blue_pair")).isEqualTo("CLOSED"); // the partner is still in
		stand(partner, "vote");
		assertThat(doorState(stranger, "door_cab_red_blue_pair")).isEqualTo("OPEN");
	}

	@Test
	void ifThePartnerDoesNotComeThePairCabinLocksWithTheFirstAfterTheWindow() {
		gameInVote();
		DoorGroup g = group(1);
		int first = g.pair().get(0);
		int partner = g.pair().get(1);
		stand(first, "cab_red_blue_pair");
		stand(partner, "vote");
		clock.advanceSeconds(29);
		assertThat(service.find(HUB).cabinLocks.get("cab_red_blue_pair").waiting()).isTrue();
		clock.advanceSeconds(2);
		assertThat(cabinView(0, "cab_red_blue_pair")).containsEntry("state", "LOCKED");
		assertThat(doorState(partner, "door_cab_red_blue_pair")).isEqualTo("CLOSED");
		assertThat(doorState(first, "door_cab_red_blue_pair")).isEqualTo("CLOSED"); // locked in
		// he votes alone for the pair; the partner outside cannot
		assertCode(() -> service.vote(HUB, ids[partner], Vote.ALLY), TrahisonException.NOT_IN_CABIN);
		service.vote(HUB, ids[first], Vote.BETRAY);
		assertThat(service.find(HUB).currentRound().pairVote[1]).isEqualTo(Vote.BETRAY);
		assertThat(doorState(first, "door_cab_red_blue_pair")).isEqualTo("OPEN");
	}

	@Test
	void theKeyClosesAPairCabinNowWithoutWaitingForThePartner() {
		gameInVote();
		DoorGroup g = group(1);
		int first = g.pair().get(0);
		int partner = g.pair().get(1);
		stand(first, "cab_red_blue_pair");
		service.closeCabin(HUB, ids[first]);
		assertThat(cabinView(0, "cab_red_blue_pair")).containsEntry("state", "LOCKED").containsEntry("window_until", null);
		assertThat(doorState(partner, "door_cab_red_blue_pair")).isEqualTo("CLOSED");
		service.vote(HUB, ids[first], Vote.ALLY);
		assertThat(service.find(HUB).currentRound().pairVoter[1]).isEqualTo(first);
	}

	@Test
	void aWaitingCabinFreesItselfIfTheFirstOneLeavesBeforeItLocks() {
		gameInVote();
		int first = group(1).pair().get(0);
		stand(first, "cab_red_blue_pair");
		assertThat(service.find(HUB).cabinLocks).hasSize(1);
		stand(first, "vote");
		assertThat(service.find(HUB).cabinLocks).isEmpty();
		assertThat(doorState(group(0).solo(), "door_cab_red_blue_pair")).isEqualTo("OPEN");
		clock.advanceSeconds(40); // the old window ends: nothing locks an empty cabin
		assertThat(service.find(HUB).cabinLocks).isEmpty();
	}

	@Test
	void aStrangerInsideWhenItLocksMayWalkOutButNeverVotesThere() {
		gameInVote();
		int stranger = group(0).solo();
		int owner = group(1).solo();
		stand(stranger, "cab_red_blue_solo"); // nobody's lock: it is not his cabin
		stand(owner, "cab_red_blue_solo");
		assertThat(service.find(HUB).cabinLocks.get("cab_red_blue_solo").locked()).isTrue();
		assertThat(doorState(stranger, "door_cab_red_blue_solo")).isEqualTo("OPEN");
		assertThat(doorState(owner, "door_cab_red_blue_solo")).isEqualTo("CLOSED");
		assertCode(() -> service.vote(HUB, ids[stranger], Vote.ALLY), TrahisonException.WRONG_CABIN);
		stand(stranger, "vote");
		assertThat(doorState(stranger, "door_cab_red_blue_solo")).isEqualTo("CLOSED");
	}

	@Test
	void ghostsCannotCloseCabinsAndAreNotWaitedFor() {
		gameInVote();
		DoorGroup g = group(1);
		int dead = g.pair().get(1);
		int alive = g.pair().get(0);
		service.find(HUB).players.get(dead).alive = false;
		stand(dead, "cab_red_blue_pair");
		assertCode(() -> service.closeCabin(HUB, ids[dead]), TrahisonException.DEAD);
		assertThat(service.find(HUB).cabinLocks).isEmpty();
		stand(alive, "cab_red_blue_pair"); // alone with a ghost: nobody else to wait for
		assertThat(service.find(HUB).cabinLocks.get("cab_red_blue_pair").locked()).isTrue();
	}

	@Test
	void nobodyVotingMeansAllyAndEveryCabinReopensWhenTheVoteEnds() {
		gameInVote();
		int p = group(2).pair().get(0);
		stand(p, "cab_green_blue_pair");
		service.setConnected(HUB, ids[p], false); // he drops inside, the cabin stays held until the stage ends
		assertThat(doorState(0, "door_cab_green_blue_pair")).isEqualTo("CLOSED");
		service.next(HUB); // VOTE -> RESULTS (also while paused)
		assertThat(service.find(HUB).phase).isEqualTo(Phase.RESULTS);
		assertThat(service.find(HUB).cabinLocks).isEmpty();
		assertThat(doorState(0, "door_cab_green_blue_pair")).isEqualTo("OPEN");
		assertThat(doorState(p, "door_cab_green_blue_pair")).isEqualTo("OPEN");
		assertThat(cabinView(0, "cab_green_blue_pair")).containsEntry("closed", false);
		// nobody voted: the default ALLY gave everybody +2
		assertThat(service.find(HUB).currentRound().deltas).containsOnly(2);
	}

	@Test
	void anAbsentVoteIsAllyForASoloAndTheOtherMemberVotesAloneForAPair() {
		gameInVote(Settings.fast(100).withoutCabins(), 0);
		DoorGroup g0 = group(0);
		DoorGroup g1 = group(1);
		// group 0: the solo does not vote (ALLY), one member of the pair votes alone for the pair
		service.vote(HUB, ids[g0.pair().get(1)], Vote.BETRAY);
		// group 1: nobody of the pair votes (ALLY), the solo betrays
		service.vote(HUB, ids[g1.solo()], Vote.BETRAY);
		service.next(HUB);
		Round0 r = new Round0(service.find(HUB).currentRound());
		assertThat(r.pair(0)).isEqualTo(Vote.BETRAY);
		assertThat(r.solo(0)).isEqualTo(Vote.ALLY);
		assertThat(r.pair(1)).isEqualTo(Vote.ALLY);
		assertThat(r.solo(1)).isEqualTo(Vote.BETRAY);
		assertThat(r.pair(2)).isEqualTo(Vote.ALLY);
		assertThat(r.solo(2)).isEqualTo(Vote.ALLY);
	}

	/** The effective votes of a resolved round. */
	private record Round0(RoundState round) {

		Vote pair(int k) {
			return com.mystaria.phantasmon_backend.events.trahison.rules.Scoring.effective(round.pairVote[k]);
		}

		Vote solo(int k) {
			return com.mystaria.phantasmon_backend.events.trahison.rules.Scoring.effective(round.soloVote[k]);
		}
	}

	@Test
	void botsVoteWithoutCabinsSoTwoHumansAndSevenBotsStayPlayable() {
		gameInVote(Settings.fast(100), 7);
		Game g = service.find(HUB);
		for (int i = 0; i < 9; i++) {
			if (g.players.get(i).bot) {
				try {
					service.vote(HUB, ids[i], Vote.BETRAY);
				} catch (TrahisonException ex) {
					assertThat(ex.code()).isEqualTo(TrahisonException.ALREADY_VOTED); // the partner's vote engaged the pair
				}
			}
		}
		int human = 0;
		assertThat(g.players.get(human).bot).isFalse();
		assertCode(() -> service.vote(HUB, ids[0], Vote.ALLY), TrahisonException.NOT_IN_CABIN);
		assertThat(service.find(HUB).cabinLocks).isEmpty();
	}

	@Test
	void withCabinsNotRequiredTheVoteIsFreeAnywhereAndNothingLocks() {
		gameInVote(Settings.fast(100).withoutCabins(), 0);
		int p = group(0).solo();
		stand(p, "vote");
		service.vote(HUB, ids[p], Vote.BETRAY);
		int q = group(0).pair().get(0);
		stand(q, "cab_red_green_pair");
		assertThat(service.find(HUB).cabinLocks).isEmpty();
		service.vote(HUB, ids[q], Vote.ALLY);
		assertThat(service.find(HUB).currentRound().soloVote[0]).isEqualTo(Vote.BETRAY);
		assertThat(view(0).get("cabins_required")).isEqualTo(false);
	}

	@Test
	void aHubWithoutAPlanKeepsTheVoteFreeEvenWhenCabinsAreRequired() {
		service.useLayouts((hub, version) -> Layout.of(hub, 0, true, Instant.EPOCH, LayoutContent.empty()));
		service.create(HUB, Settings.fast(100), 5L);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
			service.join(HUB, ids[i], "p" + i, false);
		}
		service.start(HUB);
		clock.advanceSeconds(100);
		Split s = Doors.validSplits(service.read(HUB, g -> g.currentRound().assignment())).get(0);
		for (int k = 0; k < 3; k++) {
			for (int b : s.groups().get(k).members()) {
				service.passDoor(HUB, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
			}
		}
		for (int i = 0; i < 3; i++) {
			clock.advanceSeconds(100);
		}
		assertThat(service.find(HUB).phase).isEqualTo(Phase.VOTE);
		int p = group(0).solo();
		service.vote(HUB, ids[p], Vote.BETRAY); // no cabin in the plan: nothing to close
		assertThat(service.find(HUB).currentRound().soloVote[0]).isEqualTo(Vote.BETRAY);
		assertThat(view(0).get("cabins_required")).isEqualTo(false);
	}

	@Test
	void heldCabinsSurviveARestartAndTheWaitingOneStillLocksAfterItsWindow() {
		gameInVote();
		int solo = group(0).solo();
		int first = group(1).pair().get(0);
		stand(solo, "cab_red_green_solo");
		stand(first, "cab_red_blue_pair");
		GameService reborn = new GameService(store, clock, clock);
		reborn.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan()));
		reborn.loadActiveGames();
		assertThat(reborn.find(HUB).cabinLocks.get("cab_red_green_solo").locked()).isTrue();
		assertThat(reborn.find(HUB).cabinLocks.get("cab_red_blue_pair").waiting()).isTrue();
		clock.advanceSeconds(31);
		assertThat(reborn.find(HUB).cabinLocks.get("cab_red_blue_pair").locked()).isTrue();
	}
}
