package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.DoorGroup;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;
import com.mystaria.phantasmon_backend.events.trahison.rules.Vote;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** The views: who sees what, in particular that no vote of the round in progress leaves the game master's view. */
class ViewsTest {

	private static final String HUB = "hub-v";
	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private ManualClock clock;
	private GameService service;
	private final UUID[] ids = new UUID[9];
	private final UUID gm = new UUID(1, 1);
	private final UUID spectator = new UUID(1, 2);

	@BeforeEach
	void setUp() {
		clock = new ManualClock();
		service = new GameService(new MemoryStore(), clock, clock);
		service.create(HUB, Settings.fast(10).withoutCabins().withoutRoles(), 99L);
		service.addGameMaster(HUB, gm);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
			service.join(HUB, ids[i], "p" + i, i >= 2);
		}
		service.start(HUB);
		clock.advanceSeconds(10); // DOORS of round 1
	}

	private String json(UUID who) {
		return service.read(HUB, g -> MAPPER.writeValueAsString(Views.build(g, who, clock.instant())));
	}

	private JsonNode view(UUID who) {
		return MAPPER.readTree(json(who));
	}

	private Split split() {
		return service.read(HUB, g -> g.currentRound().split());
	}

	private void passAllDoors() {
		Split s = service.read(HUB, g -> Doors.validSplits(g.currentRound().assignment()).get(0));
		for (DoorGroup dg : s.groups()) {
			for (int b : dg.members()) {
				service.passDoor(HUB, ids[b], Door.ALL.indexOf(dg.door()));
			}
		}
	}

	private void toVote() {
		passAllDoors();
		clock.advanceSeconds(10 * 3); // ROOMS, FREE_TIME -> VOTE
	}

	@Test
	void theGroupsAreKnownOnlyOnceTheDoorsClosed() {
		JsonNode doors = view(ids[0]);
		assertThat(doors.get("phase").asString()).isEqualTo("DOORS");
		assertThat(doors.get("groups").isNull()).isTrue();
		assertThat(doors.get("bracelets").get(0).get("color").isNull()).isFalse(); // colours are public from the start
		assertThat(doors.get("doors")).hasSize(3);
		passAllDoors();
		assertThat(view(ids[0]).get("doors_passed").get(0).asInt()).isGreaterThanOrEqualTo(0);
		clock.advanceSeconds(10);
		JsonNode rooms = view(spectator);
		assertThat(rooms.get("phase").asString()).isEqualTo("ROOMS");
		assertThat(rooms.get("groups")).hasSize(3);
		assertThat(rooms.get("groups").get(0).get("room_solved").asBoolean()).isFalse();
	}

	@Test
	void noVoteOfTheRoundInProgressReachesAnyoneButTheGameMaster() {
		toVote();
		DoorGroup first = split().groups().get(0);
		int pairVoter = first.pair().get(0);
		service.vote(HUB, ids[pairVoter], Vote.BETRAY);
		for (DoorGroup dg : split().groups().subList(1, 3)) {
			service.vote(HUB, ids[dg.pair().get(0)], Vote.BETRAY);
		}
		for (DoorGroup dg : split().groups()) {
			service.vote(HUB, ids[dg.solo()], Vote.BETRAY);
		}
		assertThat(service.<Phase>read(HUB, g -> g.phase)).isEqualTo(Phase.VOTE);

		UUID partner = ids[first.pair().get(1)];
		for (int i = 0; i < 9; i++) {
			String text = json(ids[i]);
			JsonNode v = MAPPER.readTree(text);
			assertThat(v.get("viewer").get("kind").asString()).isEqualTo("PLAYER");
			assertThat(v.get("results").isNull()).isTrue();
			assertThat(v.get("gm").isNull()).isTrue();
			assertThat(text).doesNotContain("\"log\"").doesNotContain("\"seed\"").doesNotContain("pair_vote");
			boolean voted = v.get("viewer").get("side_voted").asBoolean();
			assertThat(voted).isTrue();
			// BETRAY can only be written as the viewer's own vote, never anywhere else.
			int occurrences = text.split("BETRAY", -1).length - 1;
			assertThat(occurrences).isEqualTo(v.get("viewer").get("own_vote").isNull() ? 0 : 1);
		}
		assertThat(view(ids[pairVoter]).get("viewer").get("own_vote").asString()).isEqualTo("BETRAY");
		// the pair's other member knows his side voted, not what it voted
		assertThat(view(partner).get("viewer").get("side_voted").asBoolean()).isTrue();
		assertThat(view(partner).get("viewer").get("own_vote").isNull()).isTrue();

		assertThat(json(spectator)).doesNotContain("BETRAY").doesNotContain("\"gm\":{");
		assertThat(view(spectator).get("viewer").get("kind").asString()).isEqualTo("SPECTATOR");

		JsonNode master = view(gm);
		assertThat(master.get("viewer").get("kind").asString()).isEqualTo("GM");
		JsonNode votes = master.get("gm").get("votes");
		assertThat(votes).hasSize(3);
		assertThat(votes.get(0).get("pair_vote").asString()).isEqualTo("BETRAY");
		assertThat(votes.get(0).get("solo_vote").asString()).isEqualTo("BETRAY");
		assertThat(master.get("gm").get("seed").asLong()).isEqualTo(99L);
		assertThat(master.get("gm").get("bot_policy").asString()).isEqualTo("RANDOM");
		// T7.4: the game master reads the log of the game; no other view ever carries it
		assertThat(master.get("gm").has("log")).isTrue();
		assertThat(json(spectator)).doesNotContain("\"log\"");
	}

	@Test
	void ownVoteIsVisibleToTheVoterOnlyAndNotBeforeVoting() {
		toVote();
		DoorGroup g0 = split().groups().get(0);
		int solo = g0.solo();
		assertThat(view(ids[solo]).get("viewer").get("side_voted").asBoolean()).isFalse();
		service.vote(HUB, ids[solo], Vote.ALLY);
		assertThat(view(ids[solo]).get("viewer").get("own_vote").asString()).isEqualTo("ALLY");
		for (int b : g0.pair()) {
			assertThat(view(ids[b]).get("viewer").get("side_voted").asBoolean()).isFalse();
			assertThat(json(ids[b])).doesNotContain("ALLY");
		}
	}

	@Test
	void resultsAndDeathsBecomePublicAtResults() {
		toVote();
		for (DoorGroup dg : split().groups()) {
			service.vote(HUB, ids[dg.pair().get(0)], Vote.BETRAY);
			service.vote(HUB, ids[dg.solo()], Vote.ALLY);
		}
		clock.advanceSeconds(10); // RESULTS
		JsonNode v = view(spectator);
		assertThat(v.get("phase").asString()).isEqualTo("RESULTS");
		JsonNode results = v.get("results");
		assertThat(results.get("round").asInt()).isEqualTo(1);
		assertThat(results.get("votes")).hasSize(3);
		assertThat(results.get("votes").get(0).get("pair_vote").asString()).isEqualTo("BETRAY");
		assertThat(results.get("votes").get(0).get("solo_vote").asString()).isEqualTo("ALLY");
		assertThat(results.get("deltas")).hasSize(9);
		assertThat(v.get("gm").isNull()).isTrue();
		// The next round starts: the last results stay visible, the new votes do not exist yet.
		clock.advanceSeconds(10);
		JsonNode next = view(spectator);
		assertThat(next.get("phase").asString()).isEqualTo("DOORS");
		assertThat(next.get("round").asInt()).isEqualTo(2);
		assertThat(next.get("results").get("round").asInt()).isEqualTo(1);
	}

	@Test
	void aDeadPlayerGetsAGhostViewAndTheEndShowsWinners() {
		service.setPoints(HUB, 3, 1);
		toVote();
		for (DoorGroup dg : split().groups()) {
			if (dg.pair().contains(3)) {
				service.vote(HUB, ids[dg.solo()], Vote.BETRAY); // the pair of 3 loses 2 points
			} else if (dg.solo() == 3) {
				service.vote(HUB, ids[dg.pair().get(0)], Vote.BETRAY);
			}
		}
		clock.advanceSeconds(10);
		assertThat(service.<Phase>read(HUB, g -> g.phase)).isEqualTo(Phase.RESULTS);
		JsonNode v = view(ids[3]);
		assertThat(v.get("viewer").get("kind").asString()).isEqualTo("GHOST");
		assertThat(v.get("players").get(3).get("alive").asBoolean()).isFalse();
		assertThat(v.get("winners").isNull()).isTrue();
		service.end(HUB);
		assertThat(view(ids[0]).get("winners")).hasSize(0);
		assertThat(view(ids[0]).get("deadline").isNull()).isTrue();
	}

	@Test
	void aPausedGameShowsItsRemainingTime() {
		clock.advanceSeconds(4);
		service.pause(HUB);
		JsonNode v = view(ids[0]);
		assertThat(v.get("paused").asBoolean()).isTrue();
		assertThat(v.get("deadline").isNull()).isTrue();
		assertThat(v.get("remaining_ms").asLong()).isEqualTo(6000L);
	}

	@Test
	void aBotIsMarkedAndAGameMasterWhoPlaysSeesAPlayerView() {
		assertThat(view(ids[0]).get("players").get(2).get("is_bot").asBoolean()).isTrue();
		assertThat(view(ids[0]).get("players").get(0).get("is_bot").asBoolean()).isFalse();
		service.read(HUB, g -> g.gameMasters.add(ids[0]));
		assertThat(view(ids[0]).get("viewer").get("kind").asString()).isEqualTo("PLAYER");
		// D12: the game master who plays keeps his player view and gets the game master block on top (nobody else does)
		assertThat(view(ids[0]).get("gm").isNull()).isFalse();
		assertThat(view(ids[0]).get("viewer").get("is_game_master").asBoolean()).isTrue();
		assertThat(view(ids[1]).get("gm").isNull()).isTrue();
		assertThat(view(ids[1]).get("viewer").get("is_game_master").asBoolean()).isFalse();
	}

	/** T9.4: the ending and the recap are in the view only once the game is over, and the ending follows the winners. */
	@Test
	void theEndingAndTheRecapComeWithTheEndOfTheGame() {
		assertThat(view(ids[0]).get("ending").isNull()).isTrue();
		assertThat(view(ids[0]).get("recap").isNull()).isTrue();
		service.end(HUB);
		String[] expected = { "nobody", "solo", "some", "all" };
		int[] winners = { 0, 1, 4, 9 };
		for (int k = 0; k < expected.length; k++) {
			int count = winners[k];
			service.read(HUB, g -> {
				g.winners = new java.util.ArrayList<>(java.util.stream.IntStream.range(0, count).boxed().toList());
				return null;
			});
			assertThat(view(ids[0]).get("ending").asString()).isEqualTo(expected[k]);
			assertThat(view(spectator).get("ending").asString()).isEqualTo(expected[k]);
		}
		assertThat(view(ids[0]).get("recap").isArray()).isTrue();
	}

	@Test
	void theRecapListsEveryResolvedRound() {
		toVote();
		clock.advanceSeconds(10); // VOTE -> RESULTS of round 1
		assertThat(view(ids[0]).get("phase").asString()).isEqualTo("RESULTS");
		service.end(HUB);
		JsonNode recap = view(ids[0]).get("recap");
		assertThat(recap).hasSize(1);
		assertThat(recap.get(0).get("round").asInt()).isEqualTo(1);
		assertThat(recap.get(0).get("votes")).hasSize(3);
	}
}
