package com.mystaria.phantasmon_backend.events.trahison;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.game.Game;
import com.mystaria.phantasmon_backend.events.trahison.game.PlayerState;
import com.mystaria.phantasmon_backend.events.trahison.game.Phase;
import com.mystaria.phantasmon_backend.events.trahison.rules.Variants;

import static org.assertj.core.api.Assertions.assertThat;

/** T9.4: every hostess line carries a stable variant, and the ending line follows the winners. */
class BroadcasterSayTest {

	private static Game game(long seed, int winners) {
		Game g = new Game();
		g.seed = seed;
		g.phase = Phase.ENDED;
		g.round = 2;
		for (int i = 0; i < 9; i++) {
			PlayerState p = new PlayerState();
			p.uuid = new UUID(0, i + 1);
			p.name = "p" + i;
			g.players.add(p);
		}
		g.winners = new ArrayList<>(java.util.stream.IntStream.range(0, winners).boxed().toList());
		return g;
	}

	@Test
	void everySayKindCarriesAStableVariant() {
		Game g = game(42L, 0);
		for (String kind : List.of("announce", "whisper")) {
			Map<String, Object> say = Broadcaster.sayOf(g, kind, Broadcaster.SAY_KEY + "phase.vote", List.of());
			assertThat(say.get("variant")).isInstanceOf(Integer.class);
			int variant = (Integer) say.get("variant");
			assertThat(variant).isBetween(0, Variants.RANGE - 1);
			assertThat(Broadcaster.sayOf(g, kind, Broadcaster.SAY_KEY + "phase.vote", List.of()).get("variant")).isEqualTo(variant);
		}
	}

	@Test
	void theVariantDependsOnTheGameTheLineAndTheRound() {
		Game a = game(1L, 0);
		Game b = game(2L, 0);
		List<Integer> bySeed = new ArrayList<>();
		for (String key : List.of("phase.vote", "phase.rooms", "death", "betrayal", "alliance", "ending.all")) {
			bySeed.add((Integer) Broadcaster.sayOf(a, "announce", Broadcaster.SAY_KEY + key, null).get("variant"));
			bySeed.add((Integer) Broadcaster.sayOf(b, "announce", Broadcaster.SAY_KEY + key, null).get("variant"));
		}
		assertThat(bySeed.stream().distinct().count()).isGreaterThan(6);
		a.round = 3;
		assertThat(Variants.pick(1L, "phase.vote", 2)).isNotEqualTo(Variants.pick(1L, "phase.vote", 3));
	}

	@Test
	void theEndingLineFollowsTheWinners() {
		assertThat(Broadcaster.endingKey(game(1L, 0))).isEqualTo("phantasmon_events.trahison.say.ending.nobody");
		assertThat(Broadcaster.endingKey(game(1L, 1))).isEqualTo("phantasmon_events.trahison.say.ending.solo");
		assertThat(Broadcaster.endingKey(game(1L, 5))).isEqualTo("phantasmon_events.trahison.say.ending.some");
		assertThat(Broadcaster.endingKey(game(1L, 9))).isEqualTo("phantasmon_events.trahison.say.ending.all");
	}
}
