package com.mystaria.events.trahison.world;

import net.minecraft.client.Minecraft;

import com.mystaria.events.trahison.edit.LayoutDto;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.screen.FinalDoorScreen;

/**
 * The player's side of the final door (T2.5): what to suggest to him while the game is in its FINAL stage, from the plan's
 * final door (its {@code threshold} zone is the pupitre), the zone {@link Zones} computed and the view ({@code viewer.
 * can_open_final_door}, {@code final_door}, {@code doors_state}). The backend decides everything: this only chooses a prompt.
 * Where the door is open for this viewer, his client removes its fake wall ({@link FakeBlocks}, from the same
 * {@code doors_state}), so he simply walks through. Client thread.
 */
public final class FinalDoorFlow {

	/** What to suggest to the player right now. */
	public enum Prompt {
		/** Nothing: not a living player of a FINAL stage, or the plan has no pupitre and the door is shut. */
		NONE,
		/** His bracelet reads enough: the pupitre is the way (when the plan has one). */
		GO,
		/** Standing at the pupitre, his bracelet reads enough: the key opens the pupitre screen. */
		AT_PUPITRE,
		/** Standing at the pupitre, his bracelet does not read enough. */
		CANNOT,
		/** The door is open and it is open for him: go through. */
		OPEN_FOR_ME,
		/** The door is open, not for him. */
		OPEN_NOT_ME
	}

	private FinalDoorFlow() {
	}

	/** The plan's final door, or null. */
	public static LayoutDto.Door finalDoor() {
		View v = GameView.current();
		LayoutDto plan = v == null || !v.hasGame() ? null : Plan.get(v.hub(), v.layoutVersion());
		if (plan == null) {
			return null;
		}
		for (LayoutDto.Door door : plan.doors) {
			if ("final".equals(door.type)) {
				return door;
			}
		}
		return null;
	}

	/** The zone of the pupitre (the final door's threshold), or null when the plan names none. */
	public static String pupitreZone() {
		LayoutDto.Door door = finalDoor();
		return door == null || door.threshold == null || door.threshold.isBlank() ? null : door.threshold;
	}

	private static boolean finalStage(View v) {
		return v != null && v.hasGame() && v.phase() == Phase.FINAL && v.viewer().kind() == ViewerKind.PLAYER;
	}

	public static Prompt prompt() {
		View v = GameView.current();
		if (!finalStage(v)) {
			return Prompt.NONE;
		}
		LayoutDto.Door door = finalDoor();
		if (door == null) {
			return Prompt.NONE;
		}
		if (GameView.finalDoor() != null) {
			return Boolean.TRUE.equals(v.doorsOpen().get(door.id)) ? Prompt.OPEN_FOR_ME : Prompt.OPEN_NOT_ME;
		}
		String pupitre = pupitreZone();
		if (pupitre == null) {
			return Prompt.NONE;
		}
		boolean can = v.viewer().canOpenFinalDoor();
		if (pupitre.equals(Zones.current())) {
			return can ? Prompt.AT_PUPITRE : Prompt.CANNOT;
		}
		return can ? Prompt.GO : Prompt.NONE;
	}

	/** He stands at the pupitre (the screen works, and still shows while the door is open). */
	public static boolean atPupitre() {
		View v = GameView.current();
		String pupitre = pupitreZone();
		return finalStage(v) && pupitre != null && pupitre.equals(Zones.current());
	}

	/** The cabin key was pressed: at the pupitre it opens the screen. True when it was used. */
	public static boolean onKey(Minecraft mc) {
		if (!atPupitre()) {
			return false;
		}
		FinalDoorScreen.open();
		return true;
	}
}
