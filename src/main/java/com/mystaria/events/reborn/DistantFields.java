package com.mystaria.events.reborn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket;
import com.mystaria.phantasmon.client.battle.BattleFieldScenes;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The League Reborn field of each battle seen from afar (2026-11-22): those standing around a battle, who neither fight
 * nor watch it, see its field's ambiance too, not only the one the battle came with. Read from the same stream that
 * shows them its Pokémon: the rules at the start, then the engine's messages. Client thread.
 */
final class DistantFields {

	private static final String RULE = "League Reborn =";
	private static final Map<UUID, String> FIELDS = new HashMap<>();

	private DistantFields() {
	}

	static void register() {
		BattleFieldScenes.addWatcher(new BattleFieldScenes.Watcher() {
			@Override
			public void started(UUID battleUuid, BattleInitializePacket init) {
				FIELDS.remove(battleUuid);
				for (String rule : init.getBattleFormat().getRuleSet()) {
					if (rule.startsWith(RULE)) {
						String id = rule.substring(RULE.length()).trim();
						if (!id.isEmpty() && !"indoor".equals(id)) { // "indoor": the engine's base layer, no field
							FIELDS.put(battleUuid, id);
						}
					}
				}
			}

			@Override
			public void packet(UUID battleUuid, NetworkPacket<?> packet) {
				if (packet instanceof BattleMessagePacket messages) {
					read(battleUuid, messages.getMessages());
				}
			}

			@Override
			public void ended(UUID battleUuid) {
				FIELDS.remove(battleUuid);
			}
		});
	}

	private static void read(UUID battleUuid, List<Component> messages) {
		// Its moments play only where its ambiance does: the battle seen from afar shown here, with none of our own.
		boolean shown = battleUuid.equals(BattleFieldScenes.ambianceBattle()) && CobblemonClient.INSTANCE.getBattle() == null;
		for (Component message : messages) {
			if (!(message.getContents() instanceof TranslatableContents text)) {
				continue;
			}
			switch (text.getKey()) {
				case "league_reborn.start", "league_reborn.now" -> {
					String id = LeagueRebornHud.fieldId(text.getArgs());
					if (id != null) {
						FIELDS.put(battleUuid, id);
					}
				}
				case "league_reborn.cleared" -> FIELDS.remove(battleUuid);
				case "league_reborn.text.cave_crumbling" -> moment(shown, "cave_rumble");
				case "league_reborn.text.cave_collapsed" -> moment(shown, "cave_collapse");
				default -> {
					if (text.getKey().startsWith(EVENT)) {
						moment(shown, text.getKey().substring(EVENT.length()));
					}
				}
			}
		}
	}

	private static final String EVENT = "league_reborn.text.";

	private static void moment(boolean shown, String name) {
		if (shown) {
			RebornAmbiance.moment(name);
		}
	}

	/** The field of the battle seen from afar whose ambiance plays here, or null. */
	static String current() {
		UUID battle = BattleFieldScenes.ambianceBattle();
		return battle == null ? null : FIELDS.get(battle);
	}
}
