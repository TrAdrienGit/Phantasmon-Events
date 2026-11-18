package com.mystaria.phantasmon_backend.events.field;

import java.util.List;

import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.addon.BattleFieldProvider;

/**
 * League Reborn's battle fields (2026-11-18): Pokémon Reborn's field effects, run in the battle engine by the Events
 * client mod ({@code assets/phantasmon_events/league_reborn/engine.js}). The ids are the keys of its {@code fields.json},
 * minus {@code indoor} (no field). Keep them in step when the converter adds fields.
 */
@Component
public class LeagueRebornFields implements BattleFieldProvider {

	public static final List<String> IDS = List.of("electerrain", "grassy", "misty", "psyterrain", "darkcrystalcavern", "chess",
			"bigtop", "burning", "swamp", "rainbow", "corrosive", "corrosivemist", "desert", "icy", "rocky", "forest",
			"superheated", "factory", "shortcircuit", "wasteland", "ashenbeach", "watersurface", "underwater", "cave",
			"glitch", "crystalcavern", "murkwatersurface", "mountain", "snowymountain", "holy", "mirror", "fairytale",
			"dragonsden", "flowergarden1", "flowergarden2", "flowergarden3", "flowergarden4", "flowergarden5", "starlight",
			"newworld", "inverse");

	@Override
	public List<String> fieldIds() {
		return IDS;
	}
}
