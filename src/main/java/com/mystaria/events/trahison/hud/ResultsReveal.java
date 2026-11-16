package com.mystaria.events.trahison.hud;

import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.View;

/**
 * The pace at which the results are revealed, shared by the in-world board and the HUD fallback: the votes of one door
 * after another, then the new points (T2.4). It is only a display clock, started when this client first sees the results
 * of a step: a client that joins late replays the reveal from its start. Nothing here is a game rule. Client thread.
 */
public final class ResultsReveal {

	/** Time before the first door, then between two doors, and between the last door and the points. */
	public static final long FIRST_MS = 1200;
	public static final long STEP_MS = 2500;

	private static String key = "";
	private static long startedAt;

	private ResultsReveal() {
	}

	/** Milliseconds since this client first saw the results of the current step, or -1 when there are none. */
	public static long elapsed(View view) {
		if (view == null || !view.hasGame() || view.phase() != Phase.RESULTS || view.results() == null) {
			key = "";
			return -1;
		}
		String now = view.gameId() + "/" + view.results().round() + "/" + view.results().finalLoop() + "/" + view.finalLoops();
		if (!now.equals(key)) {
			key = now;
			startedAt = System.currentTimeMillis();
		}
		return System.currentTimeMillis() - startedAt;
	}

	/** How many of {@code total} doors have their votes shown after {@code elapsed} ms. */
	public static int doorsShown(long elapsed, int total) {
		if (elapsed < FIRST_MS) {
			return 0;
		}
		return (int) Math.min(total, (elapsed - FIRST_MS) / STEP_MS + 1);
	}

	/** Whether the new points are shown, once every one of {@code total} doors is. */
	public static boolean pointsShown(long elapsed, int total) {
		return elapsed >= FIRST_MS + total * STEP_MS;
	}
}
