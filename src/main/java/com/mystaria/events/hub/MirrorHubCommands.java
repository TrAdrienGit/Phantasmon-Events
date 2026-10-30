package com.mystaria.events.hub;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAvatarEntity;

/**
 * {@code /phantasmon event hub ...} (D-38), for Phantasmon's admins only: mirror hubs, whose terrain is the real ground of a zone
 * of a multiplayer server, copied into single-player worlds. A mirror hub is a square around its anchor, the whole
 * height of the world, sized by its radius.
 *
 * <ul>
 * <li>{@code create <name> <radius>}: a new mirror hub (radius from 1, no upper limit: admins only);</li>
 * <li>{@code anchor <hub>}: its anchor where the player stands; on a multiplayer server it sends the blocks (one per
 * hub), in a single-player world it receives them (one per admin);</li>
 * <li>{@code resize <hub> <delta>}: the radius grows (or shrinks, negative) around the same centre;</li>
 * <li>{@code debug <hub>}: shows or hides its limits (world border look), on this client;</li>
 * <li>{@code delete <hub>}: the hub and all its anchors;</li>
 * <li>{@code clean <hub>}: removes its ghost blocks (placed by hand in a copy), the real blocks come back;</li>
 * <li>{@code tp <player>}: in a single-player world only, to a player's avatar in the hub (never on a multiplayer
 * server: no rights there).</li>
 * </ul>
 */
public final class MirrorHubCommands {

	private MirrorHubCommands() {
	}

	/** The {@code hub} node of {@code /phantasmon event}, shown to Phantasmon's admins only. */
	public static LiteralArgumentBuilder<FabricClientCommandSource> node() {
		return ClientCommandManager.literal("hub")
				.requires(source -> PhantasmonApi.isAdmin())
				.then(ClientCommandManager.literal("create")
						.then(ClientCommandManager.argument("name", StringArgumentType.word())
								.then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1))
										.executes(context -> {
											PhantasmonApi.hubs().adminCreateMirrorHub(
													StringArgumentType.getString(context, "name"),
													IntegerArgumentType.getInteger(context, "radius"));
											return 1;
										}))))
				.then(ClientCommandManager.literal("anchor").then(hubArgument().executes(context -> {
					PhantasmonApi.hubs().createMirrorAnchor(StringArgumentType.getString(context, "hub"));
					return 1;
				})))
				.then(ClientCommandManager.literal("resize").then(hubArgument()
						.then(ClientCommandManager.argument("delta", IntegerArgumentType.integer())
								.executes(context -> {
									PhantasmonApi.hubs().adminResizeHub(StringArgumentType.getString(context, "hub"),
											IntegerArgumentType.getInteger(context, "delta"));
									return 1;
								}))))
				.then(ClientCommandManager.literal("debug").then(hubArgument().executes(context -> {
					PhantasmonApi.hubs().adminToggleDebug(StringArgumentType.getString(context, "hub"));
					return 1;
				})))
				.then(ClientCommandManager.literal("delete").then(hubArgument().executes(context -> {
					PhantasmonApi.hubs().adminDeleteHub(StringArgumentType.getString(context, "hub"));
					return 1;
				})))
				.then(ClientCommandManager.literal("clean").then(hubArgument().executes(context -> {
					clean(StringArgumentType.getString(context, "hub"));
					return 1;
				})))
				.then(ClientCommandManager.literal("tp")
						.then(ClientCommandManager.argument("player", StringArgumentType.word())
								.suggests((context, builder) -> {
									avatars().forEach(avatar -> builder.suggest(avatar.getGameProfile().getName()));
									return builder.buildFuture();
								})
								.executes(context -> {
									teleport(StringArgumentType.getString(context, "player"));
									return 1;
								})));
	}

	private static void clean(String hub) {
		PhantasmonApi.delete("/admin/events/mirror/" + hub + "/ghosts", Map.class)
				.thenAccept(result -> tell(Component.translatable("phantasmon_events.hub.cleaned", hub,
						result != null && result.get("removed") instanceof Number n ? n.intValue() : 0)))
				.exceptionally(ex -> {
					tell(Component.translatable("phantasmon_events.hub.clean_failed", hub));
					return null;
				});
	}

	/** To a hub avatar of this world, by the integrated server. */
	private static void teleport(String name) {
		Minecraft mc = Minecraft.getInstance();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null || mc.player == null) {
			tell(Component.translatable("phantasmon_events.hub.tp_solo_only"));
			return;
		}
		HubAvatarEntity target = avatars().stream().filter(avatar -> avatar.getGameProfile().getName().equalsIgnoreCase(name))
				.findFirst().orElse(null);
		if (target == null) {
			tell(Component.translatable("phantasmon_events.hub.tp_unknown", name));
			return;
		}
		double x = target.getX();
		double y = target.getY();
		double z = target.getZ();
		var me = mc.player.getUUID();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayer(me);
			if (player != null) {
				player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
			}
		});
		tell(Component.translatable("phantasmon_events.hub.tp_done", target.getGameProfile().getName()));
	}

	private static List<HubAvatarEntity> avatars() {
		ClientLevel level = Minecraft.getInstance().level;
		List<HubAvatarEntity> list = new ArrayList<>();
		if (level != null) {
			for (var entity : level.entitiesForRendering()) {
				if (entity instanceof HubAvatarEntity avatar) {
					list.add(avatar);
				}
			}
		}
		return list;
	}

	private static void tell(Component message) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.player != null) {
				mc.player.displayClientMessage(message, false);
			}
		});
	}

	private static RequiredArgumentBuilder<FabricClientCommandSource, String> hubArgument() {
		return ClientCommandManager.argument("hub", StringArgumentType.word())
				.suggests((context, builder) -> PhantasmonApi.hubs().suggestHubs(builder));
	}
}
