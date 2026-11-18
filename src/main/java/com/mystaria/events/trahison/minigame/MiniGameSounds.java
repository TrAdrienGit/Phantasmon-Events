package com.mystaria.events.trahison.minigame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The mini-game sounds, all vanilla sound events (no audio file to ship). Custom sounds can replace them later by
 * changing this class only. Client thread.
 */
public final class MiniGameSounds {

	private MiniGameSounds() {
	}

	private static void play(SoundEvent event, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
	}

	/** A button or piece is pressed: Cobblemon's PC click, like every other menu of the mod. */
	public static void click() {
		MenuUi.click();
	}

	/** A detent of a dial or a ring; {@code pitch} 0.8..1.6 lets a rising value sound higher. */
	public static void detent(float pitch) {
		play(SoundEvents.COMPARATOR_CLICK, Math.max(0.5f, Math.min(2f, pitch)), 0.55f);
	}

	/** A piece slides into place or a latch drops: a short, heavy metallic clunk. */
	public static void clunk() {
		play(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.7f, 0.8f);
	}

	/**
	 * The safe dial's cue for one tick ({@code far}, {@code mid}, {@code near}, {@code hit}), from vanilla sounds: a dull
	 * click far away, a higher click closer, a bell near the number, a clunk and a bell on it.
	 */
	public static void cue(String cue) {
		switch (cue == null ? "far" : cue) {
			case "hit" -> {
				play(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.6f, 0.8f);
				play(SoundEvents.NOTE_BLOCK_BELL.value(), 2.0f, 0.8f);
			}
			case "near" -> play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.5f, 0.7f);
			case "mid" -> play(SoundEvents.COMPARATOR_CLICK, 1.3f, 0.6f);
			default -> play(SoundEvents.COMPARATOR_CLICK, 0.6f, 0.4f);
		}
	}

	/** A refused gesture. */
	public static void error() {
		play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.55f, 0.9f);
	}

	/** The mechanism unlocks: a clunk followed by a soft chime. */
	public static void success() {
		play(SoundEvents.IRON_DOOR_OPEN, 0.9f, 0.6f);
		play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f, 0.9f);
	}

	/** A screen opens. */
	public static void open() {
		play(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.6f);
	}

	/**
	 * The cry of a Cobblemon species (its own sound event {@code cobblemon:pokemon.<species>.cry}); silent when the id has none.
	 * Returns whether a sound was asked for.
	 */
	public static boolean cry(String species) {
		if (species == null || !species.matches("[a-z0-9_]+")) {
			return false;
		}
		try {
			SoundEvent event = SoundEvent.createVariableRangeEvent(
					net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "pokemon." + species + ".cry"));
			play(event, 1f, 1f);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}
}
