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
			Map.entry("snowymountain", "snowy_mountain"),
			// The rest (2026-11-19): every other field with its own ambiance; New World and Glitch borrow Cosmos and
			// Corrupted, which already are those places.
			Map.entry("electerrain", "electric_terrain"),
			Map.entry("psyterrain", "psychic_terrain"),
			Map.entry("chess", "chess_board"),
			Map.entry("bigtop", "big_top"),
			Map.entry("swamp", "swamp"),
			Map.entry("rainbow", "rainbow"),
			Map.entry("desert", "desert"),
			Map.entry("rocky", "rocky"),
			Map.entry("wasteland", "wasteland"),
			Map.entry("ashenbeach", "ashen_beach"),
			Map.entry("glitch", "corrupted"),
			Map.entry("holy", "holy"),
			Map.entry("mirror", "mirror"),
			Map.entry("fairytale", "fairy_tale"),
			Map.entry("flowergarden1", "flower_garden_1"),
			Map.entry("flowergarden2", "flower_garden_2"),
			Map.entry("flowergarden3", "flower_garden_3"),
			Map.entry("flowergarden4", "flower_garden_4"),
			Map.entry("flowergarden5", "flower_garden_5"),
			Map.entry("starlight", "starlight"),
			Map.entry("newworld", "cosmos"),
			Map.entry("inverse", "inverse"));

	/**
	 * Field id -> the Showtime stadium it suggests (2026-11-19): shown only when the battle's scene asks for the
	 * field's stadium ("field", set on an NPC or in the lobby); else the field brings its ambiance alone, over the
	 * place the players stand in (a gym leader's own arena stays visible).
	 */
	private static final Map<String, String> STADIUM_BY_FIELD = Map.ofEntries(
			Map.entry("cave", "cave"),
			Map.entry("crystalcavern", "cave"),
			Map.entry("darkcrystalcavern", "cave"),
			Map.entry("dragonsden", "volcanic_den"),
			Map.entry("watersurface", "sea"),
			Map.entry("murkwatersurface", "sea"),
			Map.entry("icy", "sea"),
			Map.entry("underwater", "seabed"),
			Map.entry("grassy", "meadow"),
			Map.entry("misty", "meadow"),
			Map.entry("rainbow", "meadow"),
			Map.entry("starlight", "meadow"),
			Map.entry("flowergarden1", "meadow"),
			Map.entry("flowergarden2", "meadow"),
			Map.entry("flowergarden3", "meadow"),
			Map.entry("flowergarden4", "meadow"),
			Map.entry("flowergarden5", "meadow"),
			Map.entry("forest", "forest"),
			Map.entry("burning", "forest"),
			Map.entry("superheated", "badlands"),
			Map.entry("swamp", "swamp"),
			Map.entry("corrosive", "swamp"),
			Map.entry("corrosivemist", "swamp"),
			Map.entry("desert", "desert"),
			Map.entry("rocky", "canyon"),
			Map.entry("wasteland", "scrapyard"),
			Map.entry("ashenbeach", "ashen_shore"),
			Map.entry("mountain", "summit"),
			Map.entry("snowymountain", "summit"),
			Map.entry("holy", "temple"),
			Map.entry("fairytale", "temple"),
			Map.entry("factory", "factory_hall"),
			Map.entry("shortcircuit", "factory_hall"),
			Map.entry("chess", "chessboard"),
			Map.entry("bigtop", "big_top"),
			Map.entry("mirror", "mirror_hall"),
			Map.entry("newworld", "void"),
			// The four fields with no place of their own got one (2026-11-20).
			Map.entry("electerrain", "power_station"),
			Map.entry("psyterrain", "psychic_sanctum"),
			Map.entry("glitch", "glitch_room"),
			Map.entry("inverse", "inverse_hall"));

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
