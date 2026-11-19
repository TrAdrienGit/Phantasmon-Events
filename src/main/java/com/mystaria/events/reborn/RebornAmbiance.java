package com.mystaria.events.reborn;

import java.util.Map;

import com.mystaria.showtime.api.ShowtimeHooks;

import net.minecraft.client.Minecraft;

/**
 * The battle ambiance of the League Reborn field in play (2026-11-18): Showtime's own ambiances, public ones anyone
 * may pick, imposed here over the battle's own while a field that has one is in play. The field changing changes the
 * ambiance (Showtime asks twice a second); no field, or one without an ambiance, gives the battle's own back.
 */
public final class RebornAmbiance {

	private static final String SHOWTIME = "cobblemon_battle_showtime:";
	/** Field id -> Showtime ambiance, filled family by family (the ones a move turns into one another first). */
	private static final Map<String, String> BY_FIELD = Map.ofEntries(
			// Caves.
			Map.entry("cave", "cave"),
			Map.entry("crystalcavern", "crystal_cavern"),
			Map.entry("darkcrystalcavern", "dark_crystal_cavern"),
			Map.entry("dragonsden", "dragons_den"),
			// Waters.
			Map.entry("watersurface", "water_surface"),
			Map.entry("underwater", "underwater"),
			Map.entry("murkwatersurface", "murkwater"),
			Map.entry("icy", "icy"),
			// Nature, fire and poison.
			Map.entry("grassy", "grassy"),
			Map.entry("forest", "forest"),
			Map.entry("burning", "burning"),
			Map.entry("superheated", "superheated"),
			Map.entry("corrosive", "corrosive"),
			Map.entry("corrosivemist", "corrosive_mist"),
			Map.entry("misty", "misty"),
			// Machines.
			Map.entry("factory", "factory"),
			Map.entry("shortcircuit", "short_circuit"),
			// Mountains.
			Map.entry("mountain", "mountain"),
			Map.entry("snowymountain", "snowy_mountain"));

	/**
	 * Field id -> the Showtime stadium it suggests (2026-11-19): shown only when the battle's scene asks for the
	 * field's stadium ("field", set on an NPC or in the lobby); else the field brings its ambiance alone, over the
	 * place the players stand in (a gym leader's own arena stays visible).
	 */
	private static final Map<String, String> STADIUM_BY_FIELD = Map.ofEntries(
			Map.entry("cave", "cave"),
			Map.entry("crystalcavern", "cave"),
			Map.entry("darkcrystalcavern", "cave"),
			Map.entry("dragonsden", "volcanic_den"));

	private RebornAmbiance() {
	}

	static void register() {
		ShowtimeHooks.addAmbianceOverride(RebornAmbiance::current);
		ShowtimeHooks.setFieldStadium(RebornAmbiance::stadium);
	}

	private static String current() {
		String field = LeagueRebornHud.currentField();
		String id = field == null ? null : BY_FIELD.get(field);
		return id == null ? null : SHOWTIME + id;
	}

	private static String stadium() {
		String field = LeagueRebornHud.currentField();
		String id = field == null ? null : STADIUM_BY_FIELD.get(field);
		return id == null ? null : SHOWTIME + id;
	}

	/** A field event the ambiance plays (the cave crumbling, then coming down). */
	static void moment(String name) {
		Minecraft.getInstance().execute(() -> ShowtimeHooks.ambianceMoment(name));
	}
}
