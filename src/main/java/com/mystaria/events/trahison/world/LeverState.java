package com.mystaria.events.trahison.world;

import java.util.List;
import java.util.Map;

import com.mystaria.events.trahison.minigame.MiniGameSounds;
import com.mystaria.events.trahison.minigame.PuzzleScreens;

/**
 * What the world knows of a {@code three_levers} step (T3.5): read from the latest {@code EventsTrahisonPuzzle} of the step,
 * which {@code PuzzleScreens} keeps even though no screen opens for this mechanism. A lever is engaged for a lease the
 * backend counts ({@code ms} left when the view was built), so the client counts it down from the time the message arrived
 * and needs no second message when it ends. Also plays the feedback of the other players' gestures. Client thread.
 */
public final class LeverState {

	/** One lever: engaged now, and the local time (ms) its lease ends; {@code solved} when the step is done. */
	public record State(boolean on, long until, boolean solved) {

		public static final State IDLE = new State(false, 0, false);

		/** Seconds-ish warning: the lease ends within two seconds. */
		public boolean fading(long now) {
			return on && !solved && until - now < 2000;
		}
	}

	private LeverState() {
	}

	/** The state of lever {@code lever} (1..3) of {@code step} in the room (round, group), at local time {@code now}. */
	public static State of(int round, int group, String step, int lever, long now) {
		Map.Entry<PuzzleScreens.Msg, Long> last = PuzzleScreens.lastState(round, group, step);
		if (last == null) {
			return State.IDLE;
		}
		PuzzleScreens.Msg msg = last.getKey();
		List<Object> levers = PuzzleScreens.asList(msg.view().get("levers"));
		if (lever < 1 || lever > levers.size()) {
			return State.IDLE;
		}
		Map<String, Object> l = PuzzleScreens.asMap(levers.get(lever - 1));
		boolean solved = msg.solved() || Boolean.TRUE.equals(msg.view().get("solved"));
		long until = last.getValue() + PuzzleScreens.asInt(l.get("ms"), 0);
		boolean on = solved || Boolean.TRUE.equals(l.get("on")) && until >= now;
		return new State(on, until, solved);
	}

	/** How many of the three levers are engaged now: the shared progress light. */
	public static int count(int round, int group, String step, long now) {
		int n = 0;
		for (int lever = 1; lever <= 3; lever++) {
			if (of(round, group, step, lever, now).on()) {
				n++;
			}
		}
		return n;
	}

	/** Sounds for the events of a message: a lever engages (higher with each one), a wrong order, too slow, the unlock. */
	public static void onMessage(PuzzleScreens.Msg m) {
		for (Map<String, Object> e : m.lastEvents()) {
			switch (PuzzleScreens.asString(e.get("type"), "")) {
				case "engage" -> MiniGameSounds.detent(0.8f + 0.3f * PuzzleScreens.asInt(e.get("count"), 1));
				case "wrong", "slow" -> MiniGameSounds.error();
				case "unlock" -> MiniGameSounds.success();
				default -> {
				}
			}
		}
	}
}
