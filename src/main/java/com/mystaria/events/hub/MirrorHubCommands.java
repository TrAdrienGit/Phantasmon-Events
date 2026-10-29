package com.mystaria.events.hub;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * {@code /events hub ...} (D-38), for Phantasmon's admins only: mirror hubs, whose terrain is the real ground of a zone
 * of a multiplayer server, copied into single-player worlds. A mirror hub is a square around its anchor, the whole
 * height of the world, sized by its radius.
 *
 * <ul>
 * <li>{@code create <name> <radius>}: a new mirror hub (radius from 1, no upper limit: admins only);</li>
 * <li>{@code anchor <hub>}: its anchor where the player stands; on a multiplayer server it sends the blocks (one per
 * hub), in a single-player world it receives them (one per admin);</li>
 * <li>{@code resize <hub> <delta>}: the radius grows (or shrinks, negative) around the same centre;</li>
 * <li>{@code debug <hub>}: shows or hides its limits (world border look), on this client;</li>
 * <li>{@code delete <hub>}: the hub and all its anchors.</li>
 * </ul>
 */
public final class MirrorHubCommands {

	private MirrorHubCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("events")
						.requires(source -> PhantasmonApi.isAdmin())
						.then(ClientCommandManager.literal("hub")
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
								}))))));
	}

	private static RequiredArgumentBuilder<FabricClientCommandSource, String> hubArgument() {
		return ClientCommandManager.argument("hub", StringArgumentType.word())
				.suggests((context, builder) -> PhantasmonApi.hubs().suggestHubs(builder));
	}
}
