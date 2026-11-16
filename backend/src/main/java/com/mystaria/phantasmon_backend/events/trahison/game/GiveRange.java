package com.mystaria.phantasmon_backend.events.trahison.game;

/**
 * Whether two players are close enough to hand an item over. The client reports where its player stands every couple of
 * seconds ({@code EventsTrahisonZone}), so the limit is 4 blocks plus {@link #TOLERANCE} for the time a position is old.
 * When a position is missing, standing in the same known zone is enough; with neither, they are not near. Pure.
 */
public final class GiveRange {

	/** The reach of a hand-over, in blocks. */
	public static final double REACH = 4.0;
	/** Slack for the age of the reported positions, in blocks. */
	public static final double TOLERANCE = 1.0;

	private GiveRange() {
	}

	public static boolean near(PlayerState a, PlayerState b) {
		if (a.x != null && a.y != null && a.z != null && b.x != null && b.y != null && b.z != null) {
			double dx = a.x - b.x;
			double dy = a.y - b.y;
			double dz = a.z - b.z;
			double limit = REACH + TOLERANCE;
			return dx * dx + dy * dy + dz * dz <= limit * limit;
		}
		return a.zone != null && a.zone.equals(b.zone);
	}
}
