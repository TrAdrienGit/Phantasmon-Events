package com.mystaria.events.ai;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;

/** What the NPC AIs share. */
final class PhantasmonAiSupport {

	private PhantasmonAiSupport() {
	}

	/**
	 * The foe the AIs think against: the first of the opposite side's active Pokémon still standing (singles: the
	 * only one; doubles: slot a, or slot b once a is down with nobody left to replace it), else null.
	 */
	static BattlePokemon firstFoe(BattleSide side) {
		BattlePokemon fallback = null;
		for (BattleActor actor : side.getOppositeSide().getActors()) {
			for (ActiveBattlePokemon slot : actor.getActivePokemon()) {
				BattlePokemon pokemon = slot.getBattlePokemon();
				if (pokemon == null) {
					continue;
				}
				if (pokemon.getHealth() > 0) {
					return pokemon;
				}
				if (fallback == null) {
					fallback = pokemon;
				}
			}
		}
		return fallback;
	}
}
