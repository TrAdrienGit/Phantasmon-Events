package com.mystaria.phantasmon_backend.events;

import com.mystaria.phantasmon_backend.hub.Hub;

/** What a mirror hub is (D-38), now that the core only knows hub kinds (D-41). */
public final class MirrorHubs {

	/** {@code hubs.kind} of a mirror hub. */
	public static final String KIND = "MIRROR";
	/** The anchor on the server whose zone is copied: one per hub. */
	public static final String ROLE_SOURCE = "SOURCE";
	/** An admin's copy in their single-player world: one per admin and hub. */
	public static final String ROLE_COPY = "COPY";
	/** A mirror hub spans the whole height of the world: y {@value #MIN_Y} to 319. */
	public static final int MIN_Y = Hub.WORLD_MIN_Y;
	public static final int HEIGHT = Hub.WORLD_HEIGHT;

	private MirrorHubs() {
	}

	public static boolean isMirror(Hub hub) {
		return KIND.equals(hub.getKind());
	}

	/** Its radius: a square of side {@code 2 * radius + 1} (the core keeps only the side, V25). */
	public static int radius(Hub hub) {
		return (hub.getSizeX() - 1) / 2;
	}
}
