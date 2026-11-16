package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.DoorGroup;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The bots play a whole game through the service, on the manual clock. */
class BotsTest {

	private static final String HUB = "hub-b";
	private static final int SECONDS = 20;

	private static final class Table {
		final ManualClock clock = new ManualClock();
		final GameService service = new GameService(new MemoryStore(), clock, clock);

		Table(long seed, BotPolicy policy, int humans) {
			service.addListener(new Bots(service, clock, clock));
			service.create(HUB, Settings.fast(SECONDS), seed);
			for (int i = 0; i < humans; i++) {
				service.join(HUB, new UUID(0, i + 1), "human" + i);
			}
			Bots.addBots(service, HUB, 0);
			service.setBotPolicy(HUB, policy);
			service.start(HUB);
		}

		UUID human(int i) {
			return new UUID(0, i + 1);
		}

		/** Runs the clock second by second until the game ends (or a safety limit). */
		void playToTheEnd() {
			for (int s = 0; s < 3000 && service.read(HUB, g -> g.phase) != Phase.ENDED; s++) {
				clock.advanceSeconds(1);
			}
		}
	}

	@Test
	void nineBotsThatAllyOpenTheFinalDoorTogether() {
		Table t = new Table(5L, BotPolicy.ALLY, 0);
		t.playToTheEnd();
		Game g = t.service.find(HUB);
		assertThat(g.phase).isEqualTo(Phase.ENDED);
		assertThat(g.winners).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8);
		assertThat(g.log).extracting(e -> e.type).contains("pass_door", "room_solved", "vote", "final_door");
		assertThat(g.rounds).hasSize(3);
		assertThat(g.rounds.get(0).roomSolved).containsOnly(true);
		assertThat(g.rounds.get(0).diedAtDoors).isEmpty();
		assertThat(g.rounds.get(0).pairVote).doesNotContainNull();
	}

	@Test
	void betrayingBotsScoreNothingAndNobodyReachesTheFinal() {
		Table t = new Table(6L, BotPolicy.BETRAY, 0);
		for (int s = 0; s < 3 * (SECONDS * 6) + SECONDS * 2; s++) {
			t.clock.advanceSeconds(1);
		}
		Game g = t.service.find(HUB);
		assertThat(g.phase).isIn(Phase.FINAL, Phase.VOTE, Phase.RESULTS);
		assertThat(g.points()).containsOnly(3);
		assertThat(g.winners).isEmpty();
	}

	@Test
	void theSameSeedPlaysTheSameGame() {
		List<String> first = run(11L);
		List<String> second = run(11L);
		assertThat(first).isEqualTo(second).isNotEmpty();
		assertThat(run(12L)).isNotEqualTo(first);
	}

	private static List<String> run(long seed) {
		Table t = new Table(seed, BotPolicy.RANDOM, 0);
		for (int s = 0; s < 6 * SECONDS * 3 + SECONDS; s++) {
			t.clock.advanceSeconds(1);
		}
		return t.service.read(HUB, g -> g.log.stream().filter(e -> !e.type.equals("created") && !e.type.equals("join"))
			.map(e -> e.at + " " + e.type + " " + e.data).toList());
	}

	@Test
	void botsFollowTheDoorAHumanAlreadyTook() {
		Table t = new Table(21L, BotPolicy.ALLY, 2);
		t.clock.advanceSeconds(SECONDS); // DOORS of round 1
		// The human 0 takes the door of split 1: every bot must then choose that split.
		Split other = t.service.read(HUB, g -> Doors.validSplits(g.currentRound().assignment()).get(1));
		int door = -1;
		for (DoorGroup dg : other.groups()) {
			if (dg.members().contains(0)) {
				door = Door.ALL.indexOf(dg.door());
			}
		}
		t.service.passDoor(HUB, t.human(0), door);
		t.clock.advanceSeconds(SECONDS); // the doors close
		Game g = t.service.find(HUB);
		RoundState r = g.rounds.get(0);
		assertThat(r.splitIndex).isEqualTo(1);
		for (int i = 2; i < 9; i++) {
			assertThat(r.passedDoor[i]).isGreaterThanOrEqualTo(0);
		}
		assertThat(r.diedAtDoors).containsExactly(1); // only the other human did not pass
	}

	@Test
	void botsCanOnlyFillTheLobbyAndDoNotSurviveAFullLobby() {
		ManualClock clock = new ManualClock();
		GameService service = new GameService(new MemoryStore(), clock, clock);
		service.create(HUB, Settings.fast(SECONDS), 1L);
		assertThat(Bots.addBots(service, HUB, 3)).isEqualTo(3);
		assertThat(service.<String>read(HUB, g -> g.players.get(0).name)).isEqualTo("Bot 1");
		assertThat(service.<Boolean>read(HUB, g -> g.players.get(2).bot)).isTrue();
		assertThatThrownBy(() -> Bots.addBots(service, HUB, 7)).isInstanceOf(TrahisonException.class);
		assertThat(Bots.addBots(service, HUB, 0)).isEqualTo(6);
		assertThat(service.<Integer>read(HUB, g -> g.players.size())).isEqualTo(9);
		assertThat(service.<Long>read(HUB, g -> g.players.stream().map(p -> p.uuid).distinct().count())).isEqualTo(9L);
	}

	@Test
	void aPausedGameStopsTheBotsAndResumeStartsThemAgain() {
		Table t = new Table(31L, BotPolicy.ALLY, 0);
		t.clock.advanceSeconds(SECONDS); // DOORS
		t.service.pause(HUB);
		t.clock.advanceSeconds(SECONDS * 2);
		assertThat(t.service.<int[]>read(HUB, g -> g.currentRound().passedDoor)).containsOnly(-1);
		t.service.resume(HUB);
		t.clock.advanceSeconds(SECONDS - 1);
		assertThat(t.service.<int[]>read(HUB, g -> g.currentRound().passedDoor)).doesNotContain(-1);
	}
}
