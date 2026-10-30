package com.mystaria.events.tournament;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * Tournaments by command (plan-tournois.md, phase T1; the screens come in T3).
 *
 * <ul>
 * <li>Every player: {@code /phantasmon tournament list | info <t> | register <t> | unregister <t> | checkin <t>}.</li>
 * <li>Admins: {@code /events tournament create <name> <swiss|bracket> <bo> <format>},
 * {@code schedule <t> <start|opens|closes> <yyyy-MM-dd HH:mm>}, {@code add | remove <t> <player>},
 * {@code checkin <t>} (opens the check-in), {@code cancel <t>}.</li>
 * </ul>
 */
public final class TournamentCommands {

	/** Smogon formats a tournament may use: the lobby's, National Dex first (the backend checks them). */
	static final List<String> FORMATS = List.of("gen9nationaldex", "gen9nationaldexubers", "gen9nationaldexuu",
			"gen9nationaldexru", "gen9nationaldexmonotype", "gen9nationaldexlc", "gen9nationaldex1v1", "gen9nationaldexag",
			"gen9ou", "gen9ubers", "gen9uu", "gen9ru", "gen9nu", "gen9pu", "gen9zu", "gen9lc", "gen9monotype", "gen91v1",
			"gen9anythinggoes");
	private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);

	private static volatile List<String> names = List.of();

	private TournamentCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			// Under /phantasmon: the server (Cobblemon Academy) has its own /tournament, which hides a client one.
			dispatcher.register(ClientCommandManager.literal("phantasmon").then(ClientCommandManager.literal("tournament")
					.then(ClientCommandManager.literal("list").executes(context -> {
						list();
						return 1;
					}))
					.then(ClientCommandManager.literal("info").then(tournament().executes(context -> {
						info(arg(context, "tournament"));
						return 1;
					})))
					.then(ClientCommandManager.literal("register").then(tournament().executes(context -> {
						call(PhantasmonApi.post("/tournaments/" + arg(context, "tournament") + "/register", null, TournamentDto.class),
								"phantasmon_events.tournament.registered");
						return 1;
					})))
					.then(ClientCommandManager.literal("unregister").then(tournament().executes(context -> {
						call(PhantasmonApi.delete("/tournaments/" + arg(context, "tournament") + "/register", TournamentDto.class),
								"phantasmon_events.tournament.unregistered");
						return 1;
					})))
					.then(ClientCommandManager.literal("checkin").then(tournament().executes(context -> {
						call(PhantasmonApi.post("/tournaments/" + arg(context, "tournament") + "/check-in", null, TournamentDto.class),
								"phantasmon_events.tournament.checked_in");
						return 1;
					})))));

			dispatcher.register(ClientCommandManager.literal("events")
					.requires(source -> PhantasmonApi.isAdmin())
					.then(ClientCommandManager.literal("tournament")
							.then(ClientCommandManager.literal("create")
									.then(ClientCommandManager.argument("name", StringArgumentType.word())
											.then(ClientCommandManager.argument("kind", StringArgumentType.word())
													.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("swiss", "bracket"), b))
													.then(ClientCommandManager.argument("bo", IntegerArgumentType.integer(1, 5))
															.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("1", "3", "5"), b))
															.then(ClientCommandManager.argument("format", StringArgumentType.word())
																	.suggests((c, b) -> SharedSuggestionProvider.suggest(FORMATS, b))
																	.executes(context -> {
																		Map<String, Object> body = new HashMap<>();
																		body.put("name", arg(context, "name"));
																		body.put("kind", arg(context, "kind"));
																		body.put("best_of", IntegerArgumentType.getInteger(context, "bo"));
																		body.put("format_id", arg(context, "format"));
																		call(PhantasmonApi.post("/admin/tournaments", body, TournamentDto.class),
																				"phantasmon_events.tournament.created");
																		return 1;
																	}))))))
							.then(ClientCommandManager.literal("schedule").then(tournament()
									.then(ClientCommandManager.argument("what", StringArgumentType.word())
											.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("start", "opens", "closes"), b))
											.then(ClientCommandManager.argument("date", StringArgumentType.greedyString())
													.executes(context -> {
														schedule(arg(context, "tournament"), arg(context, "what"), arg(context, "date"));
														return 1;
													})))))
							.then(ClientCommandManager.literal("add").then(tournament()
									.then(ClientCommandManager.argument("player", StringArgumentType.word()).executes(context -> {
										call(PhantasmonApi.post("/admin/tournaments/" + arg(context, "tournament") + "/players",
												Map.of("username", arg(context, "player")), TournamentDto.class),
												"phantasmon_events.tournament.admin.added", arg(context, "player"));
										return 1;
									}))))
							.then(ClientCommandManager.literal("remove").then(tournament()
									.then(ClientCommandManager.argument("player", StringArgumentType.word()).executes(context -> {
										call(PhantasmonApi.delete("/admin/tournaments/" + arg(context, "tournament") + "/players/"
												+ arg(context, "player"), TournamentDto.class),
												"phantasmon_events.tournament.admin.removed", arg(context, "player"));
										return 1;
									}))))
							.then(ClientCommandManager.literal("checkin").then(tournament().executes(context -> {
								call(PhantasmonApi.post("/admin/tournaments/" + arg(context, "tournament") + "/check-in", null,
										TournamentDto.class), "phantasmon_events.tournament.admin.check_in_open");
								return 1;
							})))
							.then(ClientCommandManager.literal("cancel").then(tournament().executes(context -> {
								call(PhantasmonApi.post("/admin/tournaments/" + arg(context, "tournament") + "/cancel", null,
										TournamentDto.class), "phantasmon_events.tournament.admin.cancelled");
								return 1;
							})))));
		});
	}

	private static RequiredArgumentBuilder<FabricClientCommandSource, String> tournament() {
		return ClientCommandManager.argument("tournament", StringArgumentType.word()).suggests((context, builder) -> {
			if (PhantasmonApi.isAuthenticated()) {
				PhantasmonApi.get("/tournaments", TournamentDto[].class).thenAccept(all ->
						names = java.util.Arrays.stream(all).map(TournamentDto::name).toList());
			}
			return SharedSuggestionProvider.suggest(names, builder);
		});
	}

	private static String arg(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, String name) {
		return StringArgumentType.getString(context, name);
	}

	private static void list() {
		PhantasmonApi.get("/tournaments", TournamentDto[].class).thenAccept(all -> onClient(() -> {
			names = java.util.Arrays.stream(all).map(TournamentDto::name).toList();
			if (all.length == 0) {
				chat(Component.translatable("phantasmon_events.tournament.list.none"));
			}
			for (TournamentDto t : all) {
				chat(Component.translatable("phantasmon_events.tournament.list.entry", t.name(), kind(t), t.bestOf(), t.formatName(),
						status(t), t.players().size(), date(t.startsAt())));
			}
		})).exceptionally(TournamentCommands::failed);
	}

	private static void info(String name) {
		PhantasmonApi.get("/tournaments/" + name, TournamentDto.class).thenAccept(t -> onClient(() -> {
			chat(Component.translatable("phantasmon_events.tournament.info.title", t.name(), kind(t), t.bestOf(), t.formatName())
					.withStyle(ChatFormatting.GOLD));
			chat(Component.translatable("phantasmon_events.tournament.info.status", status(t), date(t.startsAt()),
					date(t.registrationOpensAt()), date(t.registrationClosesAt()),
					Component.translatable(t.registrationOpen() ? "phantasmon_events.tournament.open" : "phantasmon_events.tournament.closed")));
			MutableComponent players = Component.translatable("phantasmon_events.tournament.info.players", t.players().size());
			for (TournamentDto.Player p : t.players()) {
				players.append(" ").append(Component.literal(p.username())
						.withStyle(p.dropped() ? ChatFormatting.STRIKETHROUGH : p.checkedIn() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			}
			chat(players);
		})).exceptionally(TournamentCommands::failed);
	}

	private static void schedule(String tournament, String what, String date) {
		Instant instant;
		try {
			instant = LocalDateTime.parse(date.strip(), INPUT).atZone(ZoneId.systemDefault()).toInstant();
		} catch (DateTimeParseException ex) {
			chat(Component.translatable("phantasmon_events.tournament.admin.bad_date").withStyle(ChatFormatting.RED));
			return;
		}
		String field = switch (what) {
			case "start" -> "starts_at";
			case "opens" -> "registration_opens_at";
			case "closes" -> "registration_closes_at";
			default -> null;
		};
		if (field == null) {
			chat(Component.translatable("phantasmon_events.tournament.admin.bad_date").withStyle(ChatFormatting.RED));
			return;
		}
		call(PhantasmonApi.post("/admin/tournaments/" + tournament + "/schedule", Map.of(field, instant.toString()), TournamentDto.class),
				"phantasmon_events.tournament.admin.scheduled");
	}

	private static void call(CompletableFuture<TournamentDto> request, String doneKey, Object... extra) {
		request.thenAccept(t -> onClient(() -> {
			Object[] args = new Object[extra.length + 1];
			args[0] = t.name();
			System.arraycopy(extra, 0, args, 1, extra.length);
			chat(Component.translatable(doneKey, args).withStyle(ChatFormatting.LIGHT_PURPLE));
		})).exceptionally(TournamentCommands::failed);
	}

	private static Void failed(Throwable failure) {
		String code = PhantasmonApi.errorCode(failure);
		onClient(() -> chat((code == null ? Component.translatable("phantasmon_events.error.network")
				: Component.translatableWithFallback("phantasmon_events.error." + code.toLowerCase(java.util.Locale.ROOT),
						"[Tournoi] " + code)).withStyle(ChatFormatting.RED)));
		return null;
	}

	private static Component kind(TournamentDto t) {
		return Component.translatable(t.swiss() ? "phantasmon_events.tournament.kind.swiss" : "phantasmon_events.tournament.kind.bracket");
	}

	private static Component status(TournamentDto t) {
		return Component.translatable("phantasmon_events.tournament.status." + t.status().toLowerCase(java.util.Locale.ROOT));
	}

	private static Component date(String iso) {
		if (iso == null) {
			return Component.translatable("phantasmon_events.tournament.no_date");
		}
		return Component.literal(SHOWN.format(Instant.parse(iso).atZone(ZoneId.systemDefault())));
	}

	private static void onClient(Runnable action) {
		Minecraft.getInstance().execute(action);
	}

	private static void chat(Component message) {
		if (Minecraft.getInstance().player != null) {
			Minecraft.getInstance().player.displayClientMessage(message, false);
		}
	}
}
