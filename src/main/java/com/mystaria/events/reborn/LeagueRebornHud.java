package com.mystaria.events.reborn;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The League Reborn field in play, top centre of the battle screen (Adrien 2026-11-18), in the style of Cobblemon
 * Battle Extras' weather and terrain lines and right under them: the field's name, and the turns a temporary field has
 * left ("Electric Terrain (4)"). Read from the battle messages every client gets ({@link LeagueReborn}'s texts carry
 * the field id and its turns), so the host, the guest and spectators all see it.
 */
public final class LeagueRebornHud {

	private static final String TURN = "cobblemon.battle.turn";
	private static final String FIELD_KEY = "league_reborn.field.";
	private static final int TOP = 20;
	private static final int LINE = 14;

	/** A colour per field, for its name. */
	private static final Map<String, Integer> COLORS = Map.ofEntries(Map.entry("electerrain", 0xF8D030),
			Map.entry("grassy", 0x78C850), Map.entry("misty", 0xF0B6F0), Map.entry("psyterrain", 0xF85888),
			Map.entry("darkcrystalcavern", 0x9A7FD1), Map.entry("chess", 0xE0E0E0), Map.entry("bigtop", 0xFF7F50),
			Map.entry("burning", 0xFF6A1F), Map.entry("swamp", 0x7A8B4A), Map.entry("rainbow", 0xFFB3FF),
			Map.entry("corrosive", 0xA040A0), Map.entry("corrosivemist", 0xB86CC8), Map.entry("desert", 0xE0C068),
			Map.entry("icy", 0x98D8D8), Map.entry("rocky", 0xB8A038), Map.entry("forest", 0x4E9A3A),
			Map.entry("superheated", 0xFF4500), Map.entry("factory", 0xB8B8D0), Map.entry("shortcircuit", 0xFFE34D),
			Map.entry("wasteland", 0x8E7A4F), Map.entry("ashenbeach", 0xC8B89A), Map.entry("watersurface", 0x6890F0),
			Map.entry("underwater", 0x3E6FD8), Map.entry("cave", 0xA08A6A), Map.entry("glitch", 0x00FF9C),
			Map.entry("crystalcavern", 0x8FE3FF), Map.entry("murkwatersurface", 0x5A6E5A), Map.entry("mountain", 0xA89070),
			Map.entry("snowymountain", 0xE8F4FF), Map.entry("holy", 0xFFF2A8), Map.entry("mirror", 0xD8F0FF),
			Map.entry("fairytale", 0xFFA6D9), Map.entry("dragonsden", 0x9C6BFF), Map.entry("starlight", 0xC8B8FF),
			Map.entry("newworld", 0x7F7FFF), Map.entry("inverse", 0xFF8080));

	private static UUID battleId;
	private static int turn;
	private static String field;
	/** Turns left when the field was laid down (0: permanent), and the turn it was. */
	private static int turns;
	private static int setTurn;

	private LeagueRebornHud() {
	}

	public static void register() {
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	/** Every battle message packet, before Cobblemon shows it. */
	public static void onMessages(List<Component> messages) {
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		if (battle == null) {
			return;
		}
		if (!battle.getBattleId().equals(battleId)) {
			battleId = battle.getBattleId();
			turn = 0;
			field = null;
		}
		for (Component message : messages) {
			if (!(message.getContents() instanceof TranslatableContents text)) {
				continue;
			}
			Object[] args = text.getArgs();
			switch (text.getKey()) {
				case TURN -> turn = number(args, 0, turn);
				case "league_reborn.start" -> laid(fieldId(args), 0);
				case "league_reborn.now" -> laid(fieldId(args), number(args, 1, 0));
				case "league_reborn.cleared" -> field = null;
				default -> {
				}
			}
		}
	}

	private static void laid(String id, int left) {
		field = id;
		turns = left;
		setTurn = turn;
	}

	/** The field id, from its name's lang key ({@code league_reborn.field.<id>}). */
	private static String fieldId(Object[] args) {
		if (args.length > 0 && args[0] instanceof Component name && name.getContents() instanceof TranslatableContents key
				&& key.getKey().startsWith(FIELD_KEY)) {
			return key.getKey().substring(FIELD_KEY.length());
		}
		return null;
	}

	private static int number(Object[] args, int index, int fallback) {
		if (index >= args.length) {
			return fallback;
		}
		Object arg = args[index];
		if (arg instanceof Component component) {
			arg = component.getString();
		}
		try {
			return arg instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(arg).strip());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	/**
	 * Turns a temporary field has left now: the engine counts one down at the end of every turn, the one it was laid
	 * in included (none before turn 1, when leads come out).
	 */
	private static int left() {
		return turns - Math.max(0, turn - Math.max(setTurn, 1));
	}

	private static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		if (battle == null || field == null || mc.player == null || !battle.getBattleId().equals(battleId)
				|| mc.options.hideGui) {
			return;
		}
		Component name = LeagueReborn.fieldName(field);
		String label = name.getString();
		int left = left();
		if (turns > 0 && left > 0) {
			label += " (" + left + ")";
		}
		Font font = mc.font;
		int width = font.width(label);
		int x = g.guiWidth() / 2 - width / 2;
		int y = TOP + LINE * BattleExtras.lines();
		g.fill(x - 4, y - 2, x + width + 4, y + 10, 0x80000000);
		g.drawString(font, label, x, y, 0xFF000000 | COLORS.getOrDefault(field.startsWith("flowergarden") ? "grassy" : field, 0x9FD8FF), true);
	}

	/** How many lines Cobblemon Battle Extras shows up there (weather, terrain, rooms...), if it is installed. */
	private static final class BattleExtras {

		private static final String[] COUNTERS = { "getPerishSongTurns", "getTrickRoomTurns", "getWaterSportTurns",
				"getMudSportTurns", "getGravityTurns", "getWonderRoomTurns", "getMagicRoomTurns" };
		private static boolean looked;
		private static Method enabled;
		private static Method weather;
		private static Method terrain;
		private static Method[] counters;

		static int lines() {
			if (!looked) {
				looked = true;
				try {
					Class<?> config = Class.forName("name.modid.client.BattleExtrasConfig");
					Class<?> subscriber = Class.forName("name.modid.client.BattleMessageSubscriber");
					enabled = config.getMethod("isWeatherTerrainEnabled");
					weather = subscriber.getMethod("getCurrentWeather");
					terrain = subscriber.getMethod("getCurrentTerrain");
					counters = new Method[COUNTERS.length];
					for (int i = 0; i < COUNTERS.length; i++) {
						counters[i] = subscriber.getMethod(COUNTERS[i]);
					}
				} catch (ReflectiveOperationException | LinkageError ex) {
					enabled = null; // not installed, or another version: we take the top line
				}
			}
			if (enabled == null) {
				return 0;
			}
			try {
				if (!Boolean.TRUE.equals(enabled.invoke(null))) {
					return 0;
				}
				int lines = 0;
				for (Method text : new Method[] { weather, terrain }) {
					if (text.invoke(null) instanceof String value && !value.isEmpty()) {
						lines++;
					}
				}
				for (Method counter : counters) {
					if (counter.invoke(null) instanceof Integer value && value > 0) {
						lines++;
					}
				}
				return lines;
			} catch (ReflectiveOperationException | RuntimeException ex) {
				enabled = null;
				return 0;
			}
		}
	}
}
