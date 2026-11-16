package com.mystaria.events;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;

import com.mystaria.events.hub.MirrorHubCommands;

/**
 * Phantasmon Events' commands, all under {@code /phantasmon event} (Adrien 2026-10-30: as few commands as possible,
 * the rest in menus):
 * <ul>
 * <li>{@code tournament}: opens the tournaments menu, where players register and check in, and admins create and run
 * the tournaments;</li>
 * <li>{@code trahison}: the betrayal game: bare or {@code ping} checks the link with its backend module, {@code edit} (admins) toggles the plan editor, {@code minigame <mechanism>} (admins) opens a puzzle screen offline;</li>
 * <li>{@code hub ...}: the mirror hubs, admins only ({@link MirrorHubCommands});</li>
 * <li>{@code npc ...}, {@code open}, {@code close}, {@code list}: events and their trainer NPCs, admins only
 * ({@link com.mystaria.events.npc.NpcCommands}).</li>
 * </ul>
 */
public final class EventsCommands {

	private EventsCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("phantasmon").then(ClientCommandManager.literal("event")
						.then(ClientCommandManager.literal("tournament").executes(context -> {
							// The chat closes after the command: the screen opens on the next tick.
							Minecraft.getInstance().tell(() -> com.mystaria.phantasmon.client.api.PhantasmonApi.openMenu(com.mystaria.events.tournament.TournamentsPage.ID));
							return 1;
						}))
						.then(ClientCommandManager.literal("trahison").executes(context -> {
								com.mystaria.events.trahison.Trahison.ping();
								return 1;
							})
							.then(ClientCommandManager.literal("ping").executes(context -> {
								com.mystaria.events.trahison.Trahison.ping();
								return 1;
							}))
							// The provisional player screen (T1.4); the chat closes after the command: the screen opens on the next tick.
							.then(ClientCommandManager.literal("menu").executes(context -> {
								Minecraft.getInstance().tell(com.mystaria.events.trahison.screen.GameScreen::open);
								return 1;
							}))
							// The provisional game master screen (T1.4): admins only, checked again when it opens.
							.then(ClientCommandManager.literal("gm").requires(source -> com.mystaria.phantasmon.client.api.PhantasmonApi.isAdmin())
								.executes(context -> {
									Minecraft.getInstance().tell(com.mystaria.events.trahison.screen.GmScreen::open);
									return 1;
								}))
							// The plan editor (T2.1): shown to Phantasmon's admins only, which the editor checks again.
							.then(ClientCommandManager.literal("edit").requires(source -> com.mystaria.phantasmon.client.api.PhantasmonApi.isAdmin())
								.executes(context -> {
									com.mystaria.events.trahison.edit.TrahisonEditor.toggle();
									return 1;
								}))
							// A puzzle screen opened locally with no backend (T3.4), to judge its look and feel.
							.then(ClientCommandManager.literal("minigame").requires(source -> com.mystaria.phantasmon.client.api.PhantasmonApi.isAdmin())
								.then(ClientCommandManager.argument("mechanism", com.mojang.brigadier.arguments.StringArgumentType.word())
									.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
											com.mystaria.events.trahison.minigame.PuzzleScreens.mechanisms(), builder))
									.executes(context -> {
										String mechanism = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "mechanism");
										// The chat closes after the command: the screen opens on the next tick.
										Minecraft.getInstance().tell(() -> {
											if (!com.mystaria.events.trahison.minigame.PuzzleScreens.openOffline(mechanism)
													&& Minecraft.getInstance().player != null) {
												Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component
														.translatable("phantasmon_events.trahison.minigame.unknown", mechanism), false);
											}
										});
										return 1;
									}))))
							.then(MirrorHubCommands.node())
						.then(com.mystaria.events.npc.NpcCommands.nodes().get(0))
						.then(com.mystaria.events.npc.NpcCommands.nodes().get(1))
						.then(com.mystaria.events.npc.NpcCommands.nodes().get(2))
						.then(com.mystaria.events.npc.NpcCommands.nodes().get(3)))));
	}
}
