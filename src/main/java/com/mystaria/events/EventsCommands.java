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
 * <li>{@code trahison}: the betrayal game: bare or {@code ping} checks the link with its backend module, {@code edit} (admins) toggles the plan editor, {@code door <id> open|close} (admins) opens or closes a door of the plan, {@code room new|load|list|off} (admins) the room editor, {@code room try [reset|skip|stop]} (admins) plays the editor's saved room alone, {@code export [room ids]} (admins) writes the nine rooms' Axiom files, {@code check} (admins) lists the places whose blocks differ from the drawn room, {@code minigame <mechanism>} (admins) opens a puzzle screen offline, {@code voice <player> <gain>|reset} (admins) forces the gain of a player's voice to try the filter;</li>
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
							// D102: watch the game instead of playing it ("spectate off" stops).
							.then(ClientCommandManager.literal("spectate").executes(context -> {
								com.mystaria.events.trahison.net.Messages.spectate(true);
								return 1;
							}).then(ClientCommandManager.literal("off").executes(context -> {
								com.mystaria.events.trahison.net.Messages.spectate(false);
								return 1;
							})))
							// T7.2: an anonymous letter to a seat (the words cannot be typed in the screen).
							.then(ClientCommandManager.literal("letter")
								.then(ClientCommandManager.argument("seat", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 8))
									.then(ClientCommandManager.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(context -> {
										com.mystaria.events.trahison.net.Messages.useObject("LETTER",
											com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seat"), null,
											com.mojang.brigadier.arguments.StringArgumentType.getString(context, "text"));
										return 1;
									}))))
							// T7.4: the game master's words (they cannot be typed in the screen).
							.then(ClientCommandManager.literal("whisper").requires(source -> com.mystaria.phantasmon.client.api.PhantasmonApi.isAdmin())
								.then(ClientCommandManager.argument("seat", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 8))
									.then(ClientCommandManager.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(context -> {
										com.mystaria.events.trahison.net.Messages.whisper(
											com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seat"),
											com.mojang.brigadier.arguments.StringArgumentType.getString(context, "text"));
										return 1;
									}))))
							.then(ClientCommandManager.literal("say").requires(source -> com.mystaria.phantasmon.client.api.PhantasmonApi.isAdmin())
								.then(ClientCommandManager.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(context -> {
									com.mystaria.events.trahison.net.Messages.say(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "text"));
									return 1;
								})))
							// T5.1: forces what this client hears of a player (0 mute, 0.5, 1), to try the voice filter; admins only.
							.then(ClientCommandManager.literal("voice").requires(source -> com.mystaria.phantasmon.client.api.PhantasmonApi.isAdmin())
								.then(ClientCommandManager.literal("reset").executes(context -> {
									com.mystaria.events.trahison.voice.TrahisonVoice.resetForced();
									return 1;
								}))
								.then(ClientCommandManager.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
									.then(ClientCommandManager.argument("gain", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 1)).executes(context -> {
										com.mystaria.events.trahison.voice.TrahisonVoice.force(
											com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player"),
											com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "gain"));
										return 1;
									}))))
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
							// Opens or closes a door of the plan by hand (T2.2): the game master's set_door, the backend refuses non-admins.
							.then(ClientCommandManager.literal("door").requires(source -> com.mystaria.phantasmon.client.api.PhantasmonApi.isAdmin())
								.then(ClientCommandManager.argument("door", com.mojang.brigadier.arguments.StringArgumentType.word())
									.then(ClientCommandManager.literal("open").executes(context -> {
										com.mystaria.events.trahison.net.Messages.setDoor(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "door"), true);
										return 1;
									}))
									.then(ClientCommandManager.literal("close").executes(context -> {
										com.mystaria.events.trahison.net.Messages.setDoor(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "door"), false);
										return 1;
									}))))
							// The plan editor (T2.1): shown to Phantasmon's admins only, which the editor checks again.
							.then(ClientCommandManager.literal("edit").requires(source -> com.mystaria.phantasmon.client.api.PhantasmonApi.isAdmin())
								.executes(context -> {
									com.mystaria.events.trahison.edit.TrahisonEditor.toggle();
									return 1;
								}))
							// The room editor (T3.2): admins, in a singleplayer creative world (checked again by the editor).
							.then(ClientCommandManager.literal("room").requires(source -> com.mystaria.phantasmon.client.api.PhantasmonApi.isAdmin())
								.then(ClientCommandManager.literal("new")
									.then(ClientCommandManager.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> {
										com.mystaria.events.trahison.edit.RoomEditor.start(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id"));
										return 1;
									})
										// "Create from a sheet": the nine templates of Documentation/salles, tab-completed.
										.then(ClientCommandManager.argument("template", com.mojang.brigadier.arguments.StringArgumentType.word())
											.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(com.mystaria.events.trahison.edit.RoomEditor.templateIds(), builder))
											.executes(context -> {
												com.mystaria.events.trahison.edit.RoomEditor.start(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id"),
														com.mojang.brigadier.arguments.StringArgumentType.getString(context, "template"));
												return 1;
											}))))
								.then(ClientCommandManager.literal("templates").executes(context -> {
									com.mystaria.events.trahison.edit.RoomEditor.templates();
									return 1;
								}))
								.then(ClientCommandManager.literal("load")
									.then(ClientCommandManager.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> {
										com.mystaria.events.trahison.edit.RoomEditor.load(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id"));
										return 1;
									})))
								.then(ClientCommandManager.literal("list").executes(context -> {
									com.mystaria.events.trahison.edit.RoomEditor.list();
									return 1;
								}))
								.then(ClientCommandManager.literal("off").executes(context -> {
									com.mystaria.events.trahison.edit.RoomEditor.stop();
									return 1;
								}))
								// The try (D280): plays the saved room of the editor, alone, where it stands.
								.then(ClientCommandManager.literal("try")
									.executes(context -> {
										com.mystaria.events.trahison.edit.RoomTry.start();
										return 1;
									})
									.then(ClientCommandManager.literal("reset").executes(context -> {
										com.mystaria.events.trahison.edit.RoomTry.reset();
										return 1;
									}))
									.then(ClientCommandManager.literal("skip").executes(context -> {
										com.mystaria.events.trahison.edit.RoomTry.skip();
										return 1;
									}))
									.then(ClientCommandManager.literal("stop").executes(context -> {
										com.mystaria.events.trahison.edit.RoomTry.stop();
										return 1;
									}))))
							// The files of the nine drawn rooms for Axiom (T4.2): bare, the draw's rooms; with room ids, those, in the places' order.
							.then(ClientCommandManager.literal("export").requires(source -> com.mystaria.phantasmon.client.api.PhantasmonApi.isAdmin())
								.executes(context -> {
									com.mystaria.events.trahison.edit.ExportFlow.exportDrawn();
									return 1;
								})
								.then(ClientCommandManager.argument("rooms", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(context -> {
									com.mystaria.events.trahison.edit.ExportFlow.exportManual(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "rooms"));
									return 1;
								})))
							// D80: the paste files kept on the backend, to download (admins).
							.then(ClientCommandManager.literal("exports").requires(source -> com.mystaria.phantasmon.client.api.PhantasmonApi.isAdmin())
								.executes(context -> {
									net.minecraft.client.Minecraft.getInstance().tell(com.mystaria.events.trahison.screen.ExportsScreen::open);
									return 1;
								}))
							// T4.3: compares a sample of the world's blocks at each place with the drawn room.
							.then(ClientCommandManager.literal("check").requires(source -> com.mystaria.phantasmon.client.api.PhantasmonApi.isAdmin())
								.executes(context -> {
									com.mystaria.events.trahison.edit.PlaceCheck.run();
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
