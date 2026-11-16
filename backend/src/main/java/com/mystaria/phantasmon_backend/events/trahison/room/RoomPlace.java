package com.mystaria.phantasmon_backend.events.trahison.room;

import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;

/**
 * Where a room was put in the hub: the plan's room place it uses ({@code id}), the {@code origin} (its minimum corner, hub
 * coordinates) and the {@code orientation} (the world side the room's own north, its exit side, points to). A slot's
 * position is relative to the room's canonical frame; the client turns it by {@code orientation} inside the 48 x 48 square
 * at {@code origin} (protocole.md, "Place d'une salle"). Sent in {@code EventsTrahisonRoom} so no client has to guess which
 * place a group plays in.
 */
public record RoomPlace(String id, Pos origin, String orientation) {

	/** Side of the square a room is built in. */
	public static final int SQUARE = 48;

	/** Quarter turns clockwise (seen from above) of the room's own frame: north 0, east 1, south 2, west 3; unknown counts as north. */
	public int quarterTurns() {
		return switch (orientation == null ? "north" : orientation) {
			case "east" -> 1;
			case "south" -> 2;
			case "west" -> 3;
			default -> 0;
		};
	}

	/**
	 * The block of the hub a block of the room lands on: {@code relative} (a block position in the room's own frame, x and z in
	 * 0..47) turned by {@link #quarterTurns()} inside the 48 x 48 square, then moved to {@link #origin}. The same rule as the
	 * client's {@code RoomFrame} (protocole.md, "Place d'une salle").
	 */
	public Pos toHub(Pos relative) {
		int x = relative.x();
		int z = relative.z();
		int last = SQUARE - 1;
		int[] turned = switch (quarterTurns()) {
			case 1 -> new int[] { last - z, x };
			case 2 -> new int[] { last - x, last - z };
			case 3 -> new int[] { z, last - x };
			default -> new int[] { x, z };
		};
		return new Pos(origin.x() + turned[0], origin.y() + relative.y(), origin.z() + turned[1]);
	}
}
