package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.Layout;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The production wiring ({@code requireCompletePlan}): a game starts only on a complete, valid plan of its hub. */
class CompletePlanRequiredTest {

	private static final String HUB = "hub-a";

	private GameService service;

	private void game(LayoutContent plan, boolean strict) {
		game(plan, strict, Settings.fast(10));
	}

	private void game(LayoutContent plan, boolean strict, Settings settings) {
		ManualClock clock = new ManualClock();
		service = new GameService(new MemoryStore(), clock, clock);
		service.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan));
		if (strict) {
			service.requireCompletePlan();
		}
		service.create(HUB, settings, 5L);
		for (int i = 0; i < 9; i++) {
			service.join(HUB, new UUID(0, i + 1), "p" + i);
		}
	}

	@Test
	void aCompletePlanStarts() {
		game(LayoutFixtures.complete(), true);
		service.start(HUB);
		assertThat(service.find(HUB).phase).isEqualTo(Phase.PROLOGUE);
	}

	@Test
	void anIncompleteOrEmptyPlanRefusesTheStartAndKeepsTheLobby() {
		for (LayoutContent plan : new LayoutContent[] { LayoutContent.empty(),
			new LayoutContent(LayoutFixtures.complete().zones(), null, null, null, null) }) {
			game(plan, true);
			assertThatThrownBy(() -> service.start(HUB)).isInstanceOf(TrahisonException.class)
				.extracting(e -> ((TrahisonException) e).code()).isEqualTo(TrahisonException.PLAN_INVALID);
			assertThat(service.find(HUB).phase).isEqualTo(Phase.LOBBY);
		}
	}

	@Test
	void withoutTheRequirementAPartialPlanStillStarts() {
		game(LayoutContent.empty(), false);
		service.start(HUB);
		assertThat(service.find(HUB).phase).isEqualTo(Phase.PROLOGUE);
	}

	@Test
	void tryingOneRoomStartsWithoutTheComplex() {
		Settings settings = Settings.fast(10);
		settings.testRoom = "archives";
		game(LayoutContent.empty(), true, settings);
		service.start(HUB);
		assertThat(service.find(HUB).phase).isEqualTo(Phase.PROLOGUE);
	}
}
