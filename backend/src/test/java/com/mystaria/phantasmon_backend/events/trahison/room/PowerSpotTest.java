package com.mystaria.phantasmon_backend.events.trahison.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.pokemon.Partner;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.Partners;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.Power;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomFixtures.TestClock;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.DebugButton;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.PowerSpot;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** T8.3: the partner draw and the {@code power_spot} mechanism, alone and played through {@link RoomService}. */
class PowerSpotTest {

	static final MechanismRegistry REGISTRY = new MechanismRegistry(List.of(new DebugButton(), new PowerSpot()));
	final PokemonCatalog catalog = new PokemonCatalog();

	static ObjectNode params(String power, Integer pushes) {
		ObjectNode p = Json.object();
		p.put("power", power);
		if (pushes != null) {
			p.put("pushes", pushes);
		}
		return p;
	}

	/** A flash spot, then a strength spot (2 pushes) that needs it, then the exit. */
	static RoomDefinition room() {
		return new RoomDefinition("power_room", "Powers", "tester", 1, 3, 100,
				List.of(RoomFixtures.slot("lamp", "power_spot", 5, 1, 2), RoomFixtures.slot("rock", "power_spot", 8, 1, 2),
						RoomFixtures.slot("exit", "exit", 40, 0, 20)),
				List.of(new Step("flash", "power_spot", params("FLASH", null), Map.of("spot", "lamp"), List.of(), List.of(), List.of()),
						new Step("push", "power_spot", params("strength", 2), Map.of("spot", "rock"), List.of("flash"), List.of(), List.of()),
						new Step("out", "debug_button", null, Map.of("exit", "exit"), List.of("push"), List.of(), List.of())),
				null);
	}

	@Test
	void everyDrawGivesEachMemberAPartnerAndSpreadsTheRequiredPowers() {
		for (long seed = 0; seed < 1000; seed++) {
			List<Integer> members = seed % 2 == 0 ? List.of(3, 4, 5) : List.of(0, 1);
			List<Partner> partners = Partners.draw(seed, members, List.of(Power.SURF, Power.CUT));
			assertEquals(members.size(), partners.size());
			assertEquals(new HashSet<>(members), partners.stream().map(Partner::player).collect(java.util.stream.Collectors.toSet()));
			for (Power p : List.of(Power.SURF, Power.CUT)) {
				assertEquals(1, partners.stream().filter(x -> x.power() == p).count(), "seed " + seed + " " + p);
			}
			for (Partner p : partners) {
				assertTrue(catalog.byId(p.species()).isPresent(), p.species());
			}
			assertEquals(partners, Partners.draw(seed, members, List.of(Power.SURF, Power.CUT)));
		}
		assertTrue(catalog.byId(Partners.GHOST_SPECIES).isPresent());
		// the draw is not always the same one
		Set<List<Partner>> distinct = new HashSet<>();
		for (long seed = 0; seed < 50; seed++) {
			distinct.add(Partners.draw(seed, List.of(0, 1, 2), List.of(Power.FLASH, Power.STRENGTH)));
		}
		assertTrue(distinct.size() > 10);
	}

	@Test
	void aRoomAsksForTwoPowersHeldByTwoDifferentPlayers() {
		for (long seed = 0; seed < 200; seed++) {
			RoomService rooms = new RoomService(REGISTRY, new TestClock());
			RoomInstance r = rooms.start("hub", 0, List.of(0, 1, 2), room(), seed);
			int flash = r.partners.stream().filter(p -> p.power() == Power.FLASH).findFirst().orElseThrow().player();
			int strength = r.partners.stream().filter(p -> p.power() == Power.STRENGTH).findFirst().orElseThrow().player();
			assertNotEquals(flash, strength);
			int other = List.of(0, 1, 2).stream().filter(i -> i != flash).findFirst().orElseThrow();

			// somebody else cannot light the lamp: nothing moves, the event says which power is missing
			rooms.apply("hub", 0, other, new RoomAction.UseSlot("lamp"));
			assertFalse(r.steps.get("flash").solved);
			JsonNode view = rooms.puzzleView("hub", 0, "flash", ViewerKind.PLAYER).get("view");
			assertEquals(1, view.get("denied").asInt());
			assertEquals("FLASH", view.get("power").asString());
			JsonNode world = rooms.roomView("hub", 0, ViewerKind.PLAYER).get("steps").get(1).get("world"); // present before the step opens
			assertEquals("rock", world.get("bound").get("spot").get("id").asString());
			assertEquals("STRENGTH", world.get("power").asString());

			// the bearer lights it, which opens the strength step, which takes two pushes by the other bearer
			assertTrue(rooms.apply("hub", 0, flash, new RoomAction.UseSlot("lamp")).stepSolved());
			assertTrue(r.steps.get("push").open);
			rooms.apply("hub", 0, flash, new RoomAction.UseSlot("rock")); // not his power (unless he holds both: never, one power each)
			assertEquals(0, r.steps.get("push").state.data().get("done").asInt());
			assertFalse(rooms.apply("hub", 0, strength, new RoomAction.UseSlot("rock")).stepSolved());
			assertEquals(1, rooms.roomView("hub", 0, ViewerKind.PLAYER).get("steps").get(1).get("progress").get("done").asInt());
			assertTrue(rooms.apply("hub", 0, strength, new RoomAction.UseSlot("rock")).stepSolved());
			assertTrue(r.steps.get("push").solved);
		}
	}

	@Test
	void theRoomViewShowsThePartnersAndTheGhostPartnerToEveryone() {
		RoomService rooms = new RoomService(REGISTRY, new TestClock());
		rooms.start("hub", 0, List.of(4, 5), room(), 7L);
		for (ViewerKind kind : ViewerKind.values()) {
			JsonNode view = rooms.roomView("hub", 0, kind);
			assertEquals(2, view.get("partners").size());
			assertEquals(Partners.GHOST_SPECIES, view.get("ghost_partner").asString());
			assertTrue(view.get("partners").get(0).has("species") && view.get("partners").get(0).has("power"));
		}
	}

	@Test
	void aSmallGroupNeverGetsAStepNobodyCanDo() {
		// one member, two powers asked: whoever lacks one of them may act on it
		RoomService rooms = new RoomService(REGISTRY, new TestClock());
		RoomInstance r = rooms.start("hub", 0, List.of(0), room(), 3L);
		boolean flashHeld = r.partners.get(0).power() == Power.FLASH;
		RoomAction.UseSlot lamp = new RoomAction.UseSlot("lamp");
		assertTrue(rooms.apply("hub", 0, 0, lamp).stepSolved() || !flashHeld);
		if (!flashHeld) {
			assertTrue(r.steps.get("flash").solved, "no holder in the group: anyone acts");
		}
	}

	@Test
	void partnersSurviveASnapshotAndAReplayDrawsAgain() {
		RoomService rooms = new RoomService(REGISTRY, new TestClock());
		RoomInstance r = rooms.start("hub", 0, List.of(0, 1, 2), room(), 11L);
		List<Partner> before = List.copyOf(r.partners);
		RoomInstance back = rooms.restore(rooms.snapshot("hub", 0));
		assertEquals(before, back.partners);
		assertEquals(r.steps.get("flash").state.data(), back.steps.get("flash").state.data());
		// a snapshot saved before partners existed gets the same draw from its seed
		var old = rooms.snapshot("hub", 0);
		old.remove("partners");
		assertEquals(before, rooms.restore(old).partners);
	}

	@Test
	void validatorChecksThePowerAndTheCount() {
		assertEquals(List.of(), RoomValidator.validate(room(), REGISTRY::find));
		RoomDefinition bad = new RoomDefinition("bad", "Bad", "t", 1, 1, 1,
				List.of(RoomFixtures.slot("p", "power_spot", 1, 1, 1), RoomFixtures.slot("exit", "exit", 40, 0, 20)),
				List.of(new Step("a", "power_spot", params("TELEPORT", null), Map.of("spot", "p"), List.of(), List.of(), List.of()),
						new Step("out", "debug_button", null, Map.of("exit", "exit"), List.of("a"), List.of(), List.of())),
				null);
		assertTrue(RoomValidator.validate(bad, REGISTRY::find).stream().anyMatch(i -> i.code().equals(RoomValidator.BAD_PARAMS)));
		List<Step> steps = new java.util.ArrayList<>();
		String need = null;
		for (Power p : Power.values()) {
			steps.add(new Step(p.name().toLowerCase(), "power_spot", params(p.name(), null), Map.of("spot", "p"), List.of(), List.of(), List.of()));
		}
		steps.add(new Step("out", "debug_button", null, Map.of("exit", "exit"), List.of("flash", "cut"), List.of(), List.of()));
		RoomDefinition four = new RoomDefinition("four", "Four", "t", 1, 1, 1,
				List.of(RoomFixtures.slot("p", "power_spot", 1, 1, 1), RoomFixtures.slot("exit", "exit", 40, 0, 20)), steps, null);
		assertTrue(RoomValidator.validate(four, REGISTRY::find).stream().anyMatch(i -> i.code().equals(RoomValidator.BAD_PARAMS)
				&& i.info().startsWith("powers=4")));
		// a step without its slot
		RoomDefinition unbound = new RoomDefinition("u", "U", "t", 1, 1, 1, List.of(RoomFixtures.slot("exit", "exit", 40, 0, 20)),
				List.of(new Step("a", "power_spot", params("CUT", null), Map.of(), List.of(), List.of(), List.of()),
						new Step("out", "debug_button", null, Map.of("exit", "exit"), List.of("a"), List.of(), List.of())), null);
		assertTrue(RoomValidator.validate(unbound, REGISTRY::find).stream().anyMatch(i -> i.info() != null && i.info().contains("slot:spot")));
	}
}
