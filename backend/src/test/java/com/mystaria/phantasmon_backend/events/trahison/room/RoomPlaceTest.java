package com.mystaria.phantasmon_backend.events.trahison.room;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;

import static org.assertj.core.api.Assertions.assertThat;

class RoomPlaceTest {

	private static RoomPlace at(String orientation) {
		return new RoomPlace("1A", new Pos(100, 64, 200), orientation);
	}

	@Test
	void northKeepsTheRoomAsBuilt() {
		assertThat(at("north").toHub(new Pos(3, 5, 7))).isEqualTo(new Pos(103, 69, 207));
	}

	@Test
	void eastTurnsAQuarterClockwiseInsideTheSquare() {
		// the room's north edge (z = 0) ends up on the east edge (x = 47)
		assertThat(at("east").toHub(new Pos(10, 0, 0))).isEqualTo(new Pos(147, 64, 210));
		assertThat(at("east").toHub(new Pos(0, 0, 0))).isEqualTo(new Pos(147, 64, 200));
	}

	@Test
	void southAndWest() {
		assertThat(at("south").toHub(new Pos(10, 0, 0))).isEqualTo(new Pos(137, 64, 247));
		assertThat(at("west").toHub(new Pos(10, 0, 0))).isEqualTo(new Pos(100, 64, 237));
	}

	@Test
	void fourTurnsAreTheIdentityAndEveryOrientationStaysInTheSquare() {
		for (String o : new String[] { "north", "east", "south", "west" }) {
			for (int x : new int[] { 0, 17, 47 }) {
				for (int z : new int[] { 0, 5, 47 }) {
					Pos p = at(o).toHub(new Pos(x, 2, z));
					assertThat(p.x()).isBetween(100, 147);
					assertThat(p.z()).isBetween(200, 247);
				}
			}
		}
	}
}
