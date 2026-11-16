package com.mystaria.phantasmon_backend.events.trahison.layout;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutValidator.Issue;

import static org.assertj.core.api.Assertions.assertThat;

class LayoutValidatorTest {

	private static List<String> codes(LayoutContent layout, boolean draft) {
		return LayoutValidator.validate(layout, draft).stream().map(Issue::code).toList();
	}

	@Test
	void aCompletePlanIsValid() {
		assertThat(LayoutValidator.validate(LayoutFixtures.complete(), false)).isEmpty();
	}

	@Test
	void anEmptyPlanFailsCompletenessButIsAValidDraft() {
		assertThat(codes(LayoutContent.empty(), true)).isEmpty();
		assertThat(codes(LayoutContent.empty(), false)).contains(LayoutValidator.ROUND_DOORS, LayoutValidator.CABINS,
				LayoutValidator.ROOM_SLOTS, LayoutValidator.FINAL_DOOR);
	}

	@Test
	void aColourDoorMissingFromARoundIsReportedWithItsPair() {
		LayoutContent base = LayoutFixtures.complete();
		List<LayoutDoor> doors = new ArrayList<>(base.doors());
		doors.removeIf(d -> d.id().equals("door_2_red_blue"));
		List<Issue> issues = LayoutValidator.validate(LayoutFixtures.with(base, null, doors, null, null), false);
		assertThat(issues).anyMatch(i -> i.code().equals(LayoutValidator.ROUND_DOORS) && i.info().contains("round=2")
				&& i.info().contains("missing=red_blue"));
		assertThat(issues).noneMatch(i -> i.code().equals(LayoutValidator.CABINS));
	}

	@Test
	void twoColourDoorsWithTheSamePairInARoundAreRefused() {
		LayoutContent base = LayoutFixtures.complete();
		List<LayoutDoor> doors = new ArrayList<>(base.doors());
		LayoutDoor extra = new LayoutDoor("door_extra", "color", LayoutFixtures.cell(90), "doors_1", "slot_1_0", 1, List.of("green", "red"));
		doors.add(extra);
		assertThat(codes(LayoutFixtures.with(base, null, doors, null, null), false)).contains(LayoutValidator.ROUND_DOORS);
	}

	@Test
	void sixCabinsAreNeededOnePerPairAndSide() {
		LayoutContent base = LayoutFixtures.complete();
		List<Cabin> cabins = new ArrayList<>(base.cabins());
		cabins.remove(5);
		List<Issue> issues = LayoutValidator.validate(LayoutFixtures.with(base, null, null, null, cabins), false);
		assertThat(issues).anyMatch(i -> i.code().equals(LayoutValidator.CABINS) && i.info().contains("missing=green_blue/solo"));
		// Two cabins on the same pair and side, the right count: still refused.
		List<Cabin> twice = new ArrayList<>(base.cabins());
		twice.set(5, new Cabin("c_dup", "red_green", "pair", twice.get(5).zone(), twice.get(5).door()));
		assertThat(codes(LayoutFixtures.with(base, null, null, null, twice), false)).contains(LayoutValidator.CABINS);
	}

	@Test
	void aCabinMustPointAtACabinZoneAndACabinDoor() {
		LayoutContent base = LayoutFixtures.complete();
		List<Cabin> cabins = new ArrayList<>(base.cabins());
		cabins.set(0, new Cabin("cabin_red_green_pair", "red_green", "pair", "hall", "door_final"));
		List<Issue> issues = LayoutValidator.validate(LayoutFixtures.with(base, null, null, null, cabins), false);
		assertThat(issues.stream().filter(i -> i.code().equals(LayoutValidator.BAD_REFERENCE))).hasSize(2);
	}

	@Test
	void nineRoomSlotsAreNeededThreePerRound() {
		LayoutContent base = LayoutFixtures.complete();
		List<RoomSlot> slots = new ArrayList<>(base.roomSlots());
		slots.set(0, new RoomSlot("place_1_0", 2, new Pos(0, 64, 0), "north"));
		List<Issue> issues = LayoutValidator.validate(LayoutFixtures.with(base, null, null, slots, null), false);
		assertThat(issues.stream().filter(i -> i.code().equals(LayoutValidator.ROOM_SLOTS)).map(Issue::info))
				.containsExactlyInAnyOrder("round=1;count=2", "round=2;count=4");
	}

	@Test
	void exactlyOneFinalDoor() {
		LayoutContent base = LayoutFixtures.complete();
		List<LayoutDoor> none = new ArrayList<>(base.doors());
		none.removeIf(d -> d.type().equals("final"));
		assertThat(LayoutValidator.validate(LayoutFixtures.with(base, null, none, null, null), false))
				.anyMatch(i -> i.code().equals(LayoutValidator.FINAL_DOOR) && i.info().equals("count=0"));
		List<LayoutDoor> two = new ArrayList<>(base.doors());
		two.add(new LayoutDoor("door_final_2", "final", LayoutFixtures.cell(95), "hall", "final", null, null));
		assertThat(LayoutValidator.validate(LayoutFixtures.with(base, null, two, null, null), false))
				.anyMatch(i -> i.code().equals(LayoutValidator.FINAL_DOOR) && i.info().equals("count=2"));
	}

	@Test
	void overlappingZonesAreRefusedEvenInADraft() {
		LayoutContent base = LayoutFixtures.complete();
		List<LayoutZone> zones = new ArrayList<>(base.zones());
		Box inHall = LayoutFixtures.cell(0);
		zones.add(new LayoutZone("intruder", "alcove", List.of(new Box(inHall.maxX(), inHall.minY(), inHall.minZ(), inHall.maxX() + 3, inHall.maxY(), inHall.maxZ()))));
		LayoutContent overlapping = LayoutFixtures.with(base, zones, null, null, null);
		for (boolean draft : new boolean[] { true, false }) {
			assertThat(LayoutValidator.validate(overlapping, draft))
					.anyMatch(i -> i.code().equals(LayoutValidator.ZONE_OVERLAP) && i.ids().equals(List.of("hall", "intruder")));
		}
	}

	@Test
	void zonesThatOnlyTouchDoNotOverlap() {
		LayoutContent base = LayoutContent.empty();
		LayoutContent touching = new LayoutContent(List.of(
				new LayoutZone("a", "hall", List.of(new Box(0, 0, 0, 4, 4, 4))),
				new LayoutZone("b", "corridor", List.of(new Box(5, 0, 0, 9, 4, 4)))), null, null, null, null);
		assertThat(codes(touching, true)).isEmpty();
		assertThat(base.zones()).isEmpty();
	}

	@Test
	void theBoxesOfOneZoneMayOverlapEachOther() {
		LayoutContent plan = new LayoutContent(List.of(new LayoutZone("l", "corridor",
				List.of(new Box(0, 0, 0, 4, 4, 4), new Box(3, 0, 0, 9, 4, 4)))), null, null, null, null);
		assertThat(codes(plan, true)).isEmpty();
	}

	@Test
	void structuralProblemsAreReportedEvenInADraft() {
		LayoutContent plan = new LayoutContent(
				List.of(new LayoutZone("a", "castle", List.of(new Box(5, 0, 0, 1, 4, 4))), new LayoutZone("a", "hall", null),
						new LayoutZone("bad id!", "hall", null)),
				List.of(new LayoutDoor("d", "color", null, null, null, 4, List.of("red", "red"))),
				List.of(new RoomSlot("s", 0, null, "up")), List.of(new Cabin("c", "red_red", "middle", null, null)), null);
		assertThat(codes(plan, true)).contains(LayoutValidator.BAD_TYPE, LayoutValidator.BAD_BOX, LayoutValidator.DUPLICATE_ID,
				LayoutValidator.BAD_ID, LayoutValidator.BAD_ROUND, LayoutValidator.BAD_COLORS);
	}

	@Test
	void aDraftWithMissingFieldsPassesButAFinalSaveReportsThem() {
		LayoutContent plan = new LayoutContent(List.of(new LayoutZone("a", "hall", null)),
				List.of(new LayoutDoor("d", "color", null, null, null, null, null)),
				List.of(new RoomSlot("s", null, null, null)), List.of(new Cabin("c", null, null, null, null)), null);
		assertThat(codes(plan, true)).isEmpty();
		List<Issue> issues = LayoutValidator.validate(plan, false);
		assertThat(issues.stream().filter(i -> i.code().equals(LayoutValidator.MISSING_FIELD)).map(Issue::info))
				.contains("field=boxes", "field=block", "field=zone_a", "field=round", "field=colors", "field=origin",
						"field=orientation", "field=serves", "field=side");
	}

	@Test
	void aDoorLinkingAnUnknownZoneIsABadReference() {
		LayoutContent base = LayoutFixtures.complete();
		List<LayoutDoor> doors = new ArrayList<>(base.doors());
		doors.set(0, new LayoutDoor("door_final", "final", LayoutFixtures.cell(3), "hall", "nowhere", null, null));
		assertThat(LayoutValidator.validate(LayoutFixtures.with(base, null, doors, null, null), false))
				.anyMatch(i -> i.code().equals(LayoutValidator.BAD_REFERENCE) && i.ids().equals(List.of("door_final", "nowhere")));
	}

	@Test
	void aColourDoorThresholdMustBeAKnownZoneAndIsOptional() {
		LayoutContent base = LayoutFixtures.complete();
		assertThat(LayoutValidator.validate(base, false)).isEmpty();
		List<LayoutDoor> doors = new ArrayList<>(base.doors());
		LayoutDoor door = doors.get(1);
		doors.set(1, new LayoutDoor(door.id(), door.type(), door.block(), door.zoneA(), door.zoneB(), door.round(), door.colors(), "hall"));
		assertThat(LayoutValidator.validate(LayoutFixtures.with(base, null, doors, null, null), false)).isEmpty();
		doors.set(1, new LayoutDoor(door.id(), door.type(), door.block(), door.zoneA(), door.zoneB(), door.round(), door.colors(), "nowhere"));
		assertThat(LayoutValidator.validate(LayoutFixtures.with(base, null, doors, null, null), false))
				.anyMatch(i -> i.code().equals(LayoutValidator.BAD_REFERENCE) && i.info().equals("field=threshold"));
	}
}
