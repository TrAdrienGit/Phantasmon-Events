package com.mystaria.events.trahison.minigame;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.PokemonGuiRendering;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * What the Pokemon screens share: a species' name and model, a type's name and symbol, the texts the player has read. Types are never
 * shown by colour alone: each has a symbol of {@link Glyphs} beside its name. Client thread.
 */
public final class PokemonUi {

	/** The 18 types, in the backend catalogue's order; the index picks the symbol. */
	public static final List<String> TYPES = List.of("bug", "dark", "dragon", "electric", "fairy", "fighting", "fire", "flying", "ghost", "grass",
			"ground", "ice", "normal", "poison", "psychic", "rock", "steel", "water");
	/** Where the visual centre of a Cobblemon GUI model sits under its anchor, per unit of outer scale (measured by Phantasmon). */
	private static final float MODEL_CENTER_K = 7.9f;
	private static final Map<String, String> READ = new HashMap<>();
	private static boolean modelFailed;

	private PokemonUi() {
	}

	/** The species name: Cobblemon's own translation when it has one, else the id capitalised. */
	public static Component speciesName(String id) {
		String fallback = id.isEmpty() ? "?" : Character.toUpperCase(id.charAt(0)) + id.substring(1);
		return Component.translatableWithFallback("cobblemon.species." + id + ".name", fallback);
	}

	public static Component typeName(String type) {
		return Component.translatable("phantasmon_events.trahison.pokemon.type." + type.toLowerCase(Locale.ROOT));
	}

	/** The symbol of a type (an id of {@link Glyphs}). */
	public static String typeGlyph(String type) {
		int i = TYPES.indexOf(type.toLowerCase(Locale.ROOT));
		return Glyphs.IDS.get(i < 0 ? 0 : i);
	}

	/** A type as a small symbol and its name; returns the width used. */
	public static int drawType(GuiGraphics g, String type, int x, int y, int color) {
		Glyphs.draw(g, typeGlyph(type), x + 5, y + 5, 10, 0, color);
		Component name = typeName(type);
		MenuUi.text(g, name, x + 13, y + 1, 90, color);
		return 14 + TrahisonUi.width(name);
	}

	/**
	 * Draws the model of a species centred on ({@code cx}, {@code cy}), about {@code size} pixels high, clipped to that box. When the
	 * model cannot be drawn (Cobblemon's API changed, unknown id) a plain box with a question mark stands in, once and for all.
	 */
	public static void model(GuiGraphics g, String species, int cx, int cy, int size) {
		if (!modelFailed && species != null && !species.isEmpty()) {
			float scale = size / 11f;
			try {
				g.flush();
				g.enableScissor(cx - size / 2 - 2, cy - size / 2 - 2, cx + size / 2 + 2, cy + size / 2 + 2);
				PokemonGuiRendering.renderModel(g, species, null, false, null, cx, cy - MODEL_CENTER_K * scale, scale);
				g.flush();
				g.disableScissor();
				return;
			} catch (RuntimeException | LinkageError e) {
				g.disableScissor();
				modelFailed = true;
			}
		}
		g.fill(cx - size / 3, cy - size / 3, cx + size / 3, cy + size / 3, TrahisonPalette.PANEL_SUNKEN);
		MenuUi.centered(g, Component.literal("?"), cx, cy - 4, size, TrahisonPalette.TEXT_MUTED);
	}

	/** A text of the room the player read (a wall text...), kept so a screen can show it again; {@code role} like {@code wall_text}. */
	public static void noteRead(String role, String text) {
		READ.put(role, text);
	}

	public static String read(String role) {
		return READ.get(role);
	}

	public static void clearRead() {
		READ.clear();
	}

	/** {@code 6.0 kg} from hectograms. */
	public static Component weight(int hectograms) {
		return Component.literal("%.1f kg".formatted(hectograms / 10.0));
	}

	/** {@code 0.4 m} from decimetres. */
	public static Component height(int decimetres) {
		return Component.literal("%.1f m".formatted(decimetres / 10.0));
	}
}
