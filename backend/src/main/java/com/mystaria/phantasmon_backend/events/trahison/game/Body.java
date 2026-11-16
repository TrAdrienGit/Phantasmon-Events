package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.ArrayList;
import java.util.List;

/**
 * The body a dead player leaves (T6.1): where he fell (the last position his client sent, hub coordinates) and whether his
 * wrist bracelet is still on it. Kept with the game so a restart brings it back. Who knows about the death is decided here:
 * {@code announced} (everybody, D2 for the doors and the points, or the setting for a kill) or {@code seenBy} (the players
 * whose client reported seeing the body).
 */
public class Body {
	/** The dead player (his seat; his bracelet has the same index). */
	public int player;
	public String zone;
	public Double x;
	public Double y;
	public Double z;
	/** Why he died: {@code doors}, {@code points}, {@code timeout} or a kill cause of a later step. */
	public String cause;
	/** True when the bracelet still lies on the body (nobody took it). */
	public boolean braceletOnBody = true;
	/** True when every living player knows about the death without finding the body. */
	public boolean announced;
	/** The seats of the living players who saw the body. */
	public List<Integer> seenBy = new ArrayList<>();

	public Body() {
	}

	/** Whether this living player may know about the death. */
	public boolean knownTo(int viewer) {
		return announced || seenBy.contains(viewer);
	}
}
