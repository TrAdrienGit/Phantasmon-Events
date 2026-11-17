package com.mystaria.events.trahison.room;

import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.ui.TrahisonPalette;

/**
 * The four field powers of the partner Pokemon (T8.3), as the backend names them ({@code FLASH}, {@code STRENGTH}, {@code CUT},
 * {@code SURF}): their name, tone and symbol. A power is never told by colour alone: it always has its name, and its symbol is the one of
 * a Pokemon type of the same spirit (the symbols of {@code Glyphs}). Pure client display: no rule here.
 */
public final class Powers {

	public static final List<String> ALL = List.of("FLASH", "STRENGTH", "CUT", "SURF");

	private Powers() {
	}

	public static Component name(String power) {
		return Component.translatable("phantasmon_events.trahison.power." + power.toLowerCase(Locale.ROOT));
	}

	/** The Pokemon type whose symbol stands for the power. */
	public static String type(String power) {
		return switch (power) {
			case "FLASH" -> "electric";
			case "STRENGTH" -> "fighting";
			case "CUT" -> "grass";
			case "SURF" -> "water";
			default -> "normal";
		};
	}

	/** A palette token of the power's tone. */
	public static int color(String power) {
		return switch (power) {
			case "FLASH" -> TrahisonPalette.YELLOW;
			case "STRENGTH" -> TrahisonPalette.RED_LIGHT;
			case "CUT" -> TrahisonPalette.GREEN_LIGHT;
			case "SURF" -> TrahisonPalette.BLUE_LIGHT;
			default -> TrahisonPalette.TEXT_MUTED;
		};
	}
}
