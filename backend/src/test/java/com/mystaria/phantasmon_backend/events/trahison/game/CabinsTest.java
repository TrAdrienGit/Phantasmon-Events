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

	/** A game in VOTE (round 1), 9 humans unless {@code bots} of them are fake players. */
	private void gameInVote(Settings settings, int bots) {
		service.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan()));
		service.create(HUB, settings, 5L);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
			service.join(HUB, ids[i], "p" + i, i >= 9 - bots);
		}
		service.start(HUB);
		for (int i = 0; i < 4; i++) {
			clock.advanceSeconds(10); // DOORS, ROOMS, FREE_TIME, VOTE
		}
		assertThat(service.find(HUB).phase).isEqualTo(Phase.VOTE);
	}

	private void gameInVote() {
		gameInVote(Settings.fast(10), 0);
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
		assertThat(service.find(HUB).closedCabins).isEmpty();
		stand(p, "cab_red_green_solo");
		service.closeCabin(HUB, ids[p]);
		assertThat(service.find(HUB).closedCabins).containsEntry("cab_red_green_solo", p);
	}

	@Test
	void aClosedCabinIsClosedForEveryoneAndTheViewSaysOnlyThat() {
		gameInVote();
		int p = group(0).solo();
		int other = group(1).solo();
		assertThat(doorState(other, "door_cab_red_green_solo")).isEqualTo("OPEN");
		stand(p, "cab_red_green_solo");
		service.closeCabin(HUB, ids[p]);
		assertThat(doorState(other, "door_cab_red_green_solo")).isEqualTo("CLOSED");
		assertThat(doorState(p, "door_cab_red_green_solo")).isEqualTo("CLOSED");
		assertThat(doorState(other, "door_cab_red_blue_solo")).isEqualTo("OPEN");
		assertThat(cabinView(other, "cab_red_green_solo")).containsEntry("closed", true).containsEntry("closed_by_me", false);
		assertThat(cabinView(p, "cab_red_green_solo")).containsEntry("closed", true).containsEntry("closed_by_me", true);
		assertThat(cabinView(other, "cab_red_blue_solo")).containsEntry("closed", false);
		assertThat(view(other).toString()).doesNotContain("closed_by=");
		// closing his own cabin again does nothing
		service.closeCabin(HUB, ids[p]);
		assertThat(service.find(HUB).closedCabins).hasSize(1);
	}

	@Test
	void aVoteNeedsHisOwnCabinClosedByHim() {
		gameInVote();
		int p = group(0).solo();
		assertCode(() -> service.vote(HUB, ids[p], Vote.BETRAY), TrahisonException.NOT_IN_CABIN);
		stand(p, "cab_red_blue_solo");
		assertCode(() -> service.vote(HUB, ids[p], Vote.BETRAY), TrahisonException.WRONG_CABIN);
		stand(p, "cab_red_green_solo");
		assertCode(() -> service.vote(HUB, ids[p], Vote.BETRAY), TrahisonException.CABIN_NOT_CLOSED);
		service.closeCabin(HUB, ids[p]);
		service.vote(HUB, ids[p], Vote.BETRAY);
		assertThat(((Map<?, ?>) view(p).get("viewer")).get("own_vote")).isEqualTo("BETRAY");
		assertCode(() -> service.vote(HUB, ids[p], Vote.ALLY), TrahisonException.ALREADY_VOTED);
	}

	@Test
	void thePartnerWhoDidNotCloseIsLockedOut() {
		gameInVote();
		DoorGroup g = group(1);
		int closer = g.pair().get(0);
		int partner = g.pair().get(1);
		stand(closer, "cab_red_blue_pair");
		service.closeCabin(HUB, ids[closer]);
		// outside, he cannot be in the cabin (its door is shut for everyone)
		stand(partner, "vote");
		assertCode(() -> service.closeCabin(HUB, ids[partner]), TrahisonException.NOT_IN_CABIN);
		assertCode(() -> service.vote(HUB, ids[partner], Vote.ALLY), TrahisonException.NOT_IN_CABIN);
		// even if he already was inside when the other closed it
		stand(partner, "cab_red_blue_pair");
		assertCode(() -> service.closeCabin(HUB, ids[partner]), TrahisonException.CABIN_TAKEN);
		assertCode(() -> service.vote(HUB, ids[partner], Vote.ALLY), TrahisonException.CABIN_TAKEN);
		service.vote(HUB, ids[closer], Vote.BETRAY);
		assertThat(service.find(HUB).currentRound().pairVote[1]).isEqualTo(Vote.BETRAY);
		assertThat(service.find(HUB).currentRound().pairVoter[1]).isEqualTo(closer);
	}

	@Test
	void ghostsCannotCloseCabins() {
		gameInVote();
		int p = group(0).solo();
		service.find(HUB).players.get(p).alive = false;
		stand(p, "cab_red_green_solo");
		assertCode(() -> service.closeCabin(HUB, ids[p]), TrahisonException.DEAD);
	}

	@Test
	void nobodyVotingMeansAllyAndEveryCabinReopensWhenTheVoteEnds() {
		gameInVote();
		int p = group(2).pair().get(0);
		stand(p, "cab_green_blue_pair");
		service.closeCabin(HUB, ids[p]);
		service.setConnected(HUB, ids[p], false); // he drops inside, the cabin stays closed until the stage ends
		assertThat(doorState(0, "door_cab_green_blue_pair")).isEqualTo("CLOSED");
		service.next(HUB); // VOTE -> RESULTS (also while paused)
		assertThat(service.find(HUB).phase).isEqualTo(Phase.RESULTS);
		assertThat(service.find(HUB).closedCabins).isEmpty();
		assertThat(doorState(0, "door_cab_green_blue_pair")).isEqualTo("OPEN");
		assertThat(cabinView(0, "cab_green_blue_pair")).containsEntry("closed", false);
		// nobody voted: the default ALLY gave everybody +2
		assertThat(service.find(HUB).currentRound().deltas).containsOnly(2);
	}

	@Test
	void botsVoteWithoutCabinsSoTwoHumansAndSevenBotsStayPlayable() {
		gameInVote(Settings.fast(10), 7);
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
		assertThat(service.find(HUB).closedCabins).isEmpty();
	}

	@Test
	void withCabinsNotRequiredTheVoteIsFreeAnywhere() {
		gameInVote(Settings.fast(10).withoutCabins(), 0);
		int p = group(0).solo();
		stand(p, "vote");
		service.vote(HUB, ids[p], Vote.BETRAY);
		int q = group(0).pair().get(0);
		service.vote(HUB, ids[q], Vote.ALLY); // no zone reported at all
		assertThat(service.find(HUB).currentRound().soloVote[0]).isEqualTo(Vote.BETRAY);
		assertThat(view(0).get("cabins_required")).isEqualTo(false);
	}

	@Test
	void closedCabinsSurviveARestart() {
		gameInVote();
		int p = group(0).solo();
		stand(p, "cab_red_green_solo");
		service.closeCabin(HUB, ids[p]);
		GameService reborn = new GameService(store, clock, clock);
		reborn.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan()));
		reborn.loadActiveGames();
		assertThat(reborn.find(HUB).closedCabins).containsEntry("cab_red_green_solo", p);
	}
}
