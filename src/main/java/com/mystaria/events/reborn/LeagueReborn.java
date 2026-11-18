package com.mystaria.events.reborn;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mystaria.phantasmon.client.battle.ShowdownExtensions;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * League Reborn (2026-11-18): Pokémon Reborn's field effects, as a modifier on any battle format. The engine
 * ({@code league_reborn/engine.js}, data {@code fields.json}) goes into Cobblemon's Showdown through Phantasmon's
 * {@link ShowdownExtensions}; a lobby's field reaches it as the rule {@code League Reborn = <field>}. The engine
 * reports in {@code |-leaguereborn|...} lines, told here in the battle chat (relayed to the guest like any message).
 * See Documentation/terrains/conception.md.
 */
public final class LeagueReborn {

	private static final Logger LOG = LoggerFactory.getLogger(LeagueReborn.class);
	public static final String EXTENSION = "league_reborn";
	private static final String ROOT = "/assets/phantasmon_events/league_reborn/";
	/** No field: the engine's base layer, never offered. */
	private static final String NONE = "indoor";
	private static final int TEXT = 0x9FD8FF;

	private static List<String> fieldIds;

	private LeagueReborn() {
	}

	public static void register() {
		ShowdownExtensions.register(EXTENSION,
				"(function(){try{return !!(typeof Dex!=='undefined'?Dex:require('./sim/dex').Dex).data.Rulesets.leaguereborn}catch(e){return false}})()",
				LeagueReborn::script);
		ShowdownInterpreter.registerUpdateInstructionParser("-leaguereborn",
				(battle, instructionSet, message, messages) -> new Line(message));
		LeagueRebornHud.register();
	}

	/** The engine, called with Showdown's Dex (index.js's global) and the field data; kept as globalThis.LeagueReborn. */
	private static String script() {
		return "(function(){const D=(typeof Dex!=='undefined')?Dex:require('./sim/dex').Dex;const make=" + read("engine.js").strip()
				+ "\nglobalThis.LeagueReborn=make(D," + read("fields.json") + ");})();";
	}

	/** The fields one may lay down, in fields.json's order. */
	public static synchronized List<String> fieldIds() {
		if (fieldIds == null) {
			List<String> ids = new ArrayList<>();
			try {
				JsonObject fields = JsonParser.parseString(read("fields.json")).getAsJsonObject();
				for (String id : fields.keySet()) {
					if (!NONE.equals(id)) {
						ids.add(id);
					}
				}
			} catch (RuntimeException ex) {
				LOG.error("League Reborn: fields.json unreadable", ex);
			}
			fieldIds = List.copyOf(ids);
		}
		return fieldIds;
	}

	public static Component fieldName(String id) {
		return Component.translatableWithFallback("league_reborn.field." + id, id);
	}

	private static String read(String name) {
		try (InputStream in = LeagueReborn.class.getResourceAsStream(ROOT + name)) {
			if (in == null) {
				throw new IllegalStateException("missing " + ROOT + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** One {@code -leaguereborn} line, told in the chat in its turn. */
	private record Line(BattleMessage message) implements InterpreterInstruction {

		@Override
		public void invoke(PokemonBattle battle) {
			// "|-leaguereborn|kind|args..." — split by hand: the arguments are plain words, Pokémon names at most.
			String[] parts = message.getRawMessage().split("\\|", -1);
			if (parts.length < 3) {
				return;
			}
			String kind = parts[2];
			List<String> args = new ArrayList<>();
			for (int i = 3; i < parts.length; i++) {
				args.add(parts[i]);
			}
			List<Component> lines = lines(kind, args);
			if (lines.isEmpty()) {
				return;
			}
			battle.dispatchWaiting(1.2F, () -> {
				lines.forEach(battle::broadcastChatMessage);
				return kotlin.Unit.INSTANCE;
			});
		}
	}

	private static int turns(String text) {
		try {
			return Integer.parseInt(text.strip());
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private static String arg(List<String> args, int index) {
		return index < args.size() ? args.get(index) : "";
	}

	/** A field's own text when its lang file has one ({@code league_reborn.<field>.<what>}), else the generic one. */
	private static MutableComponent text(String specific, String generic, Object... with) {
		String key = Language.getInstance().has(specific) ? specific : generic;
		return Component.translatable(key, with);
	}

	static List<Component> lines(String kind, List<String> args) {
		List<Component> lines = new ArrayList<>();
		String field = arg(args, 0);
		switch (kind) {
			case "start" -> {
				lines.add(Component.translatable("league_reborn.start", fieldName(field)).withStyle(ChatFormatting.BOLD)
						.withColor(TEXT));
				if (Language.getInstance().has("league_reborn." + field + ".intro")) {
					lines.add(Component.translatable("league_reborn." + field + ".intro").withStyle(ChatFormatting.ITALIC)
							.withColor(TEXT));
				}
			}
			// The turns left ride along unshown (the text has no %2$s): LeagueRebornHud reads them on every client.
			case "field" -> lines.add((NONE.equals(field) ? Component.translatable("league_reborn.cleared")
					: Component.translatable("league_reborn.now", fieldName(field), turns(arg(args, 1)))).withColor(TEXT));
			case "end" -> lines.add(Component.translatable("league_reborn.end", fieldName(field)).withColor(TEXT));
			case "change" -> {
				String key = "league_reborn." + field + ".change." + arg(args, 2);
				if (Language.getInstance().has(key)) {
					lines.add(Component.translatable(key).withColor(TEXT));
				}
			}
			case "move" -> lines.add(text("league_reborn." + field + ".move." + arg(args, 1), "league_reborn.move",
					arg(args, 2), arg(args, 3)).withColor(TEXT));
			case "type" -> lines.add(text("league_reborn." + field + ".type." + arg(args, 1), "league_reborn.type").withColor(TEXT));
			case "text" -> lines.add(Component.translatable("league_reborn.text." + field, arg(args, 1)).withColor(TEXT));
			default -> {
			}
		}
		return lines;
	}
}
