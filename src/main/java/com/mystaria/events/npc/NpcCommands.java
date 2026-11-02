package com.mystaria.events.npc;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * Admins' event commands (Adrien 2026-11-02), the rest is in the NPC's editor (a click on it):
 * <ul>
 * <li>{@code npc create <event> <name>}: poses a trainer NPC where the admin stands, in that event (created closed if
 * new), then opens its editor;</li>
 * <li>{@code npc move}: the NPC last edited (else the nearest) comes exactly where the admin stands;</li>
 * <li>{@code npc edit}: the editor of the nearest NPC;</li>
 * <li>{@code open <event>}, {@code close <event>}: its NPCs appear to players, or disappear;</li>
 * <li>{@code list}: the events, open or not, and how many NPCs each has.</li>
 * </ul>
 */
public final class NpcCommands {

	private NpcCommands() {
	}

	public static List<LiteralArgumentBuilder<FabricClientCommandSource>> nodes() {
		return List.of(
				ClientCommandManager.literal("npc").requires(source -> PhantasmonApi.isAdmin())
						.then(ClientCommandManager.literal("create")
								.then(ClientCommandManager.argument("event", StringArgumentType.word())
										.then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes(context -> {
											create(StringArgumentType.getString(context, "event"), StringArgumentType.getString(context, "name"));
											return 1;
										}))))
						.then(ClientCommandManager.literal("move").executes(context -> {
							move();
							return 1;
						}))
						.then(ClientCommandManager.literal("edit").executes(context -> {
							EventNpcEntity nearest = EventNpcs.nearest(12);
							if (nearest == null) {
								chat(Component.translatable("phantasmon_events.npc.none_near").withStyle(ChatFormatting.RED));
							} else {
								Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new NpcEditScreen(nearest.npc())));
							}
							return 1;
						})),
				openClose("open", true),
				openClose("close", false),
				ClientCommandManager.literal("list").requires(source -> PhantasmonApi.isAdmin()).executes(context -> {
					PhantasmonApi.get("/admin/events", Map[].class).thenAccept(events -> {
						if (events.length == 0) {
							chat(Component.translatable("phantasmon_events.npc.no_events"));
						}
						for (Map<?, ?> event : events) {
							boolean open = Boolean.TRUE.equals(event.get("open"));
							chat(Component.translatable(open ? "phantasmon_events.npc.event_line_open" : "phantasmon_events.npc.event_line_closed",
									event.get("name"), ((Number) event.get("npcs")).intValue()).withStyle(open ? ChatFormatting.GREEN : ChatFormatting.GRAY));
						}
					}).exceptionally(NpcCommands::failed);
					return 1;
				}));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> openClose(String literal, boolean open) {
		return ClientCommandManager.literal(literal).requires(source -> PhantasmonApi.isAdmin())
				.then(ClientCommandManager.argument("event", StringArgumentType.word()).executes(context -> {
					String event = StringArgumentType.getString(context, "event").toLowerCase(Locale.ROOT);
					PhantasmonApi.post("/admin/events/" + event + "/open", Map.of("open", open), Map.class)
							.thenAccept(ignored -> chat(Component.translatable(open ? "phantasmon_events.npc.event_opened"
									: "phantasmon_events.npc.event_closed", event).withStyle(ChatFormatting.GREEN)))
							.exceptionally(NpcCommands::failed);
					return 1;
				}));
	}

	/**
	 * {@code npc move}: the NPC last opened in the editor (else the nearest, 32 blocks) comes exactly where the admin
	 * stands, facing where they look.
	 */
	private static void move() {
		java.util.UUID uuid = NpcEditScreen.lastEdited;
		if (uuid == null) {
			EventNpcEntity nearest = EventNpcs.nearest(32);
			uuid = nearest == null ? null : nearest.npc().uuid();
		}
		Map<String, Object> body = NpcEditScreen.here();
		if (uuid == null || body == null) {
			chat(Component.translatable("phantasmon_events.npc.none_near").withStyle(ChatFormatting.RED));
			return;
		}
		PhantasmonApi.post("/admin/events/npcs/" + uuid, body, EventNpcDto.class)
				.thenAccept(npc -> chat(Component.translatable("phantasmon_events.npc.moved_named", npc.name()).withStyle(ChatFormatting.GREEN)))
				.exceptionally(NpcCommands::failed);
	}

	private static void create(String event, String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		Map<String, Object> body = new HashMap<>();
		body.put("event", event.toLowerCase(Locale.ROOT));
		body.put("name", name.strip());
		body.put("server_fingerprint", PhantasmonApi.serverFingerprint());
		body.put("dimension", mc.level.dimension().location().toString());
		body.put("x", mc.player.getX());
		body.put("y", mc.player.getY());
		body.put("z", mc.player.getZ());
		body.put("yaw", (double) mc.player.getYRot());
		PhantasmonApi.post("/admin/events/npcs", body, EventNpcDto.class)
				.thenAccept(npc -> mc.execute(() -> {
					chat(Component.translatable("phantasmon_events.npc.created", npc.name(), npc.event()).withStyle(ChatFormatting.GREEN));
					mc.setScreen(new NpcEditScreen(npc));
				}))
				.exceptionally(NpcCommands::failed);
	}

	private static Void failed(Throwable failure) {
		chat(NpcEditScreen.errorMessage(failure));
		return null;
	}

	private static void chat(Component message) {
		Minecraft.getInstance().execute(() -> {
			if (Minecraft.getInstance().player != null) {
				Minecraft.getInstance().player.displayClientMessage(message, false);
			}
		});
	}
}
