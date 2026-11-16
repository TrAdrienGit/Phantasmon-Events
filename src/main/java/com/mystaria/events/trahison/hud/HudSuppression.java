package com.mystaria.events.trahison.hud;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Lets a full screen hide the game's HUD (the bracelet widget) while it is open, so the bracelet is not drawn twice
 * (charte-ui.md section 8). A screen calls {@link #hide} with itself when it opens and {@link #show} when it closes;
 * owners are held weakly, so a screen that forgets to release does not leave the HUD hidden for good. The mini-game
 * screens use it too. Client thread.
 */
public final class HudSuppression {

	private static final Set<Object> OWNERS = Collections.newSetFromMap(new WeakHashMap<>());

	private HudSuppression() {
	}

	public static void hide(Object owner) {
		OWNERS.add(owner);
	}

	public static void show(Object owner) {
		OWNERS.remove(owner);
	}

	/** Whether any screen asks for the HUD to be hidden. */
	public static boolean hidden() {
		return !OWNERS.isEmpty();
	}

	/** Forgets every owner (disconnect). */
	public static void clear() {
		OWNERS.clear();
	}
}
