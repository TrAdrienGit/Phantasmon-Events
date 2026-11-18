package com.mystaria.events.trahison.edit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nine room templates ("create from a sheet"): they parse, every slot is declared with a hint and is still to place, the
 * proposed positions are inside the box and do not collide, and the editor's own checks find nothing wrong once the slots
 * count as placed. The backend validates the same files again in its own {@code RoomTemplatesTest}.
 */
class RoomTemplatesTest {

	@BeforeAll
	static void withoutBackendCatalog() throws Exception {
		// The catalog is read from the backend; here it is the built-in list (no clue roles), so the clue check is skipped.
		Field failed = MechanismCatalog.class.getDeclaredField("failed");
		failed.setAccessible(true);
		failed.set(null, true);
	}

	@Test
	void everyTemplateParsesWithItsChainAndItsGhosts() throws Exception {
		assertEquals(9, RoomTemplates.IDS.size());
		for (String id : RoomTemplates.IDS) {
			RoomTemplates.Loaded t = RoomTemplates.load(id);
			RoomDto room = t.room();
			assertEquals(id, room.id);
			assertFalse(room.name == null || room.name.isBlank(), id);
			assertTrue(room.steps.size() >= 5 && room.steps.size() <= 6, id);
			Set<String> ids = new HashSet<>();
			for (RoomDto.SlotDto slot : room.slots) {
				assertTrue(ids.add(slot.id), id + " duplicate " + slot.id);
				assertFalse(t.hints().getOrDefault(slot.id, "").isBlank(), id + "/" + slot.id + " has no hint");
			}
			assertEquals(ids, t.unplaced(), id + ": every slot is a proposal");
			for (RoomDto.StepDto step : room.steps) {
				assertEquals(3, step.hints.size(), id + "/" + step.id);
			}
			assertEquals(ids.size(), RoomTemplates.order(room, t.unplaced()).size(), id);
			assertEquals(ids, new HashSet<>(RoomTemplates.order(room, t.unplaced())), id);
		}
	}

	@Test
	void everySlotIsUsedOrIsAHidingPlaceAndTheyDoNotCollide() throws Exception {
		for (String id : RoomTemplates.IDS) {
			RoomDto room = RoomTemplates.load(id).room();
			Set<String> used = new HashSet<>();
			for (RoomDto.StepDto step : room.steps) {
				used.addAll(step.slots.values());
				step.gives.forEach(give -> used.add(give.slot));
			}
			int candidates = 0;
			for (RoomDto.SlotDto slot : room.slots) {
				if ("secret".equals(slot.kind)) {
					candidates++;
				} else {
					assertTrue(used.contains(slot.id), id + "/" + slot.id + " serves no step");
				}
				assertTrue(RoomTemplate.inside(slot.position.x, slot.position.y, slot.position.z, slot.position.x + slot.size.x - 1,
						slot.position.y + slot.size.y - 1, slot.position.z + slot.size.z - 1), id + "/" + slot.id + " is outside the box");
			}
			assertTrue(candidates == 2 || candidates == 3, id + " declares 2 or 3 hiding places, has " + candidates);
			List<RoomChecks.Issue> issues = RoomChecks.check(room);
			assertEquals(List.of(), issues, id + ": no error and no warning (overlap, opening, hiding places...)");
		}
	}

	@Test
	void unplacedSlotsComeInTheOrderOfTheChain() throws Exception {
		RoomTemplates.Loaded t = RoomTemplates.load("archives");
		List<String> order = RoomTemplates.order(t.room(), t.unplaced());
		assertEquals("fiche_1", order.get(0));
		assertTrue(order.indexOf("bureau_registre") < order.indexOf("pad_casier"));
		assertTrue(order.indexOf("pad_rayons") < order.indexOf("levier_1"));
		assertEquals("sortie", order.get(order.size() - 1 - 3), "the exit comes after the levers of its step, before the hiding places");
		Set<String> rest = new HashSet<>(t.unplaced());
		rest.remove("fiche_1");
		assertEquals(order.size() - 1, RoomTemplates.order(t.room(), rest).size());
		assertEquals(List.of(), RoomTemplates.order(t.room(), new ArrayList<String>().stream().collect(java.util.stream.Collectors.toSet())));
	}
}
