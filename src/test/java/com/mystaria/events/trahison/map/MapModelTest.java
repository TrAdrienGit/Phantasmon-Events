package com.mystaria.events.trahison.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mystaria.events.trahison.edit.LayoutDto;
import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.map.MapModel.FloorInfo;
import com.mystaria.events.trahison.map.MapModel.FloorMap;
import com.mystaria.events.trahison.map.MapModel.Stroke;
import com.mystaria.events.trahison.net.MapData;

class MapModelTest {

	private static LayoutDto.Zone zone(String id, String type, Box... boxes) {
		LayoutDto.Zone z = new LayoutDto.Zone();
		z.id = id;
		z.type = type;
		z.boxes = new ArrayList<>(List.of(boxes));
		return z;
	}

	private static LayoutDto.Floor floor(String id, int yMin, int yMax) {
		LayoutDto.Floor f = new LayoutDto.Floor();
		f.id = id;
		f.name = id.toUpperCase();
		f.yMin = yMin;
		f.yMax = yMax;
		return f;
	}

	private static LayoutDto plan() {
		LayoutDto plan = new LayoutDto();
		plan.zones.add(zone("hall", "hall", new Box(0, 64, 0, 9, 68, 9)));
		plan.zones.add(zone("room_a", "room_slot", new Box(100, 64, 0, 109, 68, 9)));
		plan.zones.add(zone("stairs", "corridor", new Box(10, 66, 0, 12, 74, 3)));
		plan.zones.add(zone("up", "room_slot", new Box(100, 72, 0, 109, 76, 9)));
		LayoutDto.Slot slot = new LayoutDto.Slot();
		slot.id = "place_1_0";
		slot.origin = new LayoutDto.Pos(96, 60, -10);
		plan.roomSlots.add(slot);
		LayoutDto.Door door = new LayoutDto.Door();
		door.id = "d1";
		door.type = "color";
		door.block = new Box(9, 64, 4, 10, 66, 4);
		door.colors = List.of("red", "green");
		plan.doors.add(door);
		return plan;
	}

	@Test
	void aPlanWithoutFloorsHasOneThatHoldsEverything() {
		List<FloorInfo> floors = MapModel.floors(plan());
		assertEquals(1, floors.size());
		assertEquals(0, MapModel.floorAt(floors, 70));
		assertEquals(0, MapModel.floorAt(floors, -50));
		assertEquals(4, MapModel.build(plan(), floors.get(0), Map.of()).rooms().size());
	}

	@Test
	void floorsAreSortedAndAnElementStraddlingTwoShowsOnBoth() {
		LayoutDto plan = plan();
		plan.floors.add(floor("upper", 70, 79));
		plan.floors.add(floor("ground", 60, 69));
		List<FloorInfo> floors = MapModel.floors(plan);
		assertEquals("ground", floors.get(0).id());
		FloorMap ground = MapModel.build(plan, floors.get(0), Map.of());
		FloorMap upper = MapModel.build(plan, floors.get(1), Map.of());
		assertEquals(List.of("hall", "room_a", "stairs"), ground.rooms().stream().map(MapModel.RoomShape::zone).toList());
		assertEquals(List.of("stairs", "up"), upper.rooms().stream().map(MapModel.RoomShape::zone).toList());
		assertEquals(1, ground.doors().size());
		assertTrue(upper.doors().isEmpty());
		assertEquals(0, MapModel.floorAt(floors, 65.5));
		assertEquals(1, MapModel.floorAt(floors, 70.0));
		assertEquals(-1, MapModel.floorAt(floors, 120));
	}

	@Test
	void aRoomIsNamedAfterTheRoomDrawnInItsPlaceElseAfterThePlace() {
		LayoutDto plan = plan();
		FloorInfo floor = MapModel.floors(plan).get(0);
		FloorMap unnamed = MapModel.build(plan, floor, Map.of());
		assertEquals("place_1_0", unnamed.rooms().stream().filter(r -> r.zone().equals("room_a")).findFirst().orElseThrow().label());
		assertNull(unnamed.rooms().stream().filter(r -> r.zone().equals("hall")).findFirst().orElseThrow().label());
		FloorMap named = MapModel.build(plan, floor, Map.of("place_1_0", new MapData.Room("Cold room", 1, 0)));
		assertEquals("Cold room", named.rooms().stream().filter(r -> r.zone().equals("room_a")).findFirst().orElseThrow().label());
		assertTrue(named.rooms().stream().filter(r -> r.zone().equals("room_a")).findFirst().orElseThrow().isRoom());
	}

	@Test
	void theBoundsCoverEveryRoomAndDoor() {
		FloorMap map = MapModel.build(plan(), MapModel.floors(plan()).get(0), Map.of());
		assertEquals(0, map.bounds().minX());
		assertEquals(109, map.bounds().maxX());
		assertEquals(9, map.bounds().maxZ());
		assertFalse(map.isEmpty());
	}

	@Test
	void theEraserRemovesTheStrokesThatPassNearAndOnlyThose() {
		Stroke a = new Stroke(0);
		a.add(0, 0);
		a.add(10, 0);
		Stroke b = new Stroke(1);
		b.add(0, 20);
		b.add(10, 20);
		Stroke dot = new Stroke(2);
		dot.add(50, 50);
		List<Stroke> all = new ArrayList<>(List.of(a, b, dot));
		assertEquals(0, MapModel.erase(all, 5, 10, 2));
		assertEquals(1, MapModel.erase(all, 5, 1, 2));
		assertEquals(1, MapModel.erase(all, 51, 50, 2));
		assertEquals(List.of(b), all);
		assertEquals(2, MapModel.pointCount(all));
	}
}
