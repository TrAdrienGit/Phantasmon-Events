package com.mystaria.events.trahison.edit;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import com.mystaria.events.trahison.edit.TrahisonExport.PlaceRoom;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.network.BackendApiException;

/**
 * The game master's way to the files (T4.2): the plan's places, the nine drawn rooms (their definition and blocks from the
 * backend), then {@link TrahisonExport} writes them into {@code .minecraft/phantasmon_events/trahison/export/<game>/} and the
 * chat says where, with a click that opens the folder. The nine rooms come from a {@link DrawSource}: the T4.1 draw once the
 * backend serves it, until then {@link #manual} (the room ids typed after the command, in the order of the places: by round,
 * then by id). Client thread to start; the work runs on the API's threads and the answer comes back to the client thread.
 */
public final class ExportFlow {

	private static final String T = "phantasmon_events.trahison.export.";

	/**
	 * The drawn rooms keyed by the id of the place they go to, in the backend's order (round, then group = plan order). The
	 * place id is what pairs a room with its place: no order of the plan is assumed here.
	 */
	@FunctionalInterface
	public interface DrawSource {
		CompletableFuture<java.util.Map<String, String>> roomsByPlace(String gameId, LayoutDto layout);
	}

	/**
	 * Where the draw comes from. T4.1's draw is not served to the client yet: no source, so the button asks for the command.
	 * Set it to the draw's reader when the backend exposes it.
	 */
	public static volatile DrawSource drawn = null;

	private static boolean busy;

	private ExportFlow() {
	}

	/** The GM screen's button: the draw, if there is a source for it. */
	public static void exportDrawn() {
		DrawSource source = drawn;
		if (source == null) {
			chat(Component.translatable(T + "no_draw"));
			return;
		}
		run(game(), source);
	}

	/** The command: room ids given by hand, in the order of the places. */
	public static void exportManual(String ids) {
		List<String> list = new ArrayList<>();
		for (String id : ids.trim().split("[\\s,]+")) {
			if (!id.isEmpty()) {
				list.add(id);
			}
		}
		run(game(), (game, layout) -> {
			List<LayoutDto.Slot> places = TrahisonExport.orderedPlaces(layout);
			if (places.size() != list.size()) {
				return CompletableFuture.failedFuture(new IllegalArgumentException("count:" + list.size() + ":" + places.size()));
			}
			java.util.Map<String, String> byPlace = new java.util.LinkedHashMap<>();
			for (int i = 0; i < places.size(); i++) {
				byPlace.put(places.get(i).id, list.get(i));
			}
			return CompletableFuture.completedFuture(byPlace);
		});
	}

	private static String game() {
		GameView.View view = GameView.orEmpty();
		return view.hasGame() && view.gameId() != null ? view.gameId() : "manual";
	}

	private static void run(String gameId, DrawSource source) {
		if (!PhantasmonApi.isAdmin() && !GameView.orEmpty().viewer().isGameMaster()) {
			chat(Component.translatable(T + "not_allowed"));
			return;
		}
		String hub = Link.hub();
		if (hub == null) {
			chat(Component.translatable("phantasmon_events.trahison.gm.no_hub"));
			return;
		}
		if (busy) {
			return;
		}
		busy = true;
		chat(Component.translatable(T + "working"));
		PhantasmonApi.get("/events/trahison/layout/" + URLEncoder.encode(hub, StandardCharsets.UTF_8), LayoutDto.class)
				.thenCompose(layout -> source.roomsByPlace(gameId, layout.normalized()).thenCompose(byPlace -> fetch(layout.normalized(), byPlace)))
				.thenApply(places -> {
					try {
						Path folder = FabricLoader.getInstance().getGameDir().resolve("phantasmon_events").resolve("trahison").resolve("export")
								.resolve(safe(gameId));
						return TrahisonExport.write(folder, gameId, places, SharedConstants.getCurrentVersion().getDataVersion().getVersion(),
								"Phantasmon Events", RoomTurn::minecraftStates);
					} catch (java.io.IOException e) {
						throw new CompletionException(e);
					}
				})
				.whenComplete((written, failure) -> Minecraft.getInstance().execute(() -> {
					busy = false;
					if (failure != null || written == null) {
						chat(failureMessage(failure));
						return;
					}
					Component folder = Component.literal(written.folder().toString())
							.withStyle(Style.EMPTY.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, written.folder().toString())));
					chat(Component.translatable(T + "done", written.files().size(), folder));
					written.notes().forEach(n -> chat(Component.literal(n)));
				}));
	}

	/** Pairs the plan's places with the room ids by place id and fetches each room's definition and blocks. */
	private static CompletableFuture<List<PlaceRoom>> fetch(LayoutDto layout, java.util.Map<String, String> byPlace) {
		List<TrahisonExport.Pairing> pairs;
		try {
			pairs = TrahisonExport.pair(layout, byPlace);
		} catch (IllegalArgumentException e) {
			return CompletableFuture.failedFuture(e);
		}
		List<CompletableFuture<PlaceRoom>> jobs = new ArrayList<>();
		for (TrahisonExport.Pairing pair : pairs) {
			LayoutDto.Slot place = pair.place();
			String roomId = pair.roomId();
			if (place.origin == null) {
				return CompletableFuture.failedFuture(new IllegalArgumentException("no_origin:" + place.id));
			}
			CompletableFuture<RoomDto> definition = PhantasmonApi.get("/events/trahison/rooms/" + roomId, RoomDto.class);
			CompletableFuture<byte[]> blocks = RoomFiles.download(roomId);
			jobs.add(definition.thenCombine(blocks, (room, file) -> new PlaceRoom(place.id, place.round == null ? 0 : place.round, place.origin.x,
					place.origin.y, place.origin.z, place.orientation == null ? "north" : place.orientation, roomId, room.name, file, room.normalized())));
		}
		return CompletableFuture.allOf(jobs.toArray(new CompletableFuture[0])).thenApply(v -> jobs.stream().map(CompletableFuture::join).toList());
	}

	private static String safe(String id) {
		return id.replaceAll("[^A-Za-z0-9_.-]", "_").toLowerCase(Locale.ROOT);
	}

	private static Component failureMessage(Throwable failure) {
		Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
		if (cause instanceof BackendApiException api) {
			String code = api.errorCode();
			return Component.translatableWithFallback(T + "error." + code.toLowerCase(Locale.ROOT), code);
		}
		if (cause instanceof IllegalArgumentException bad && bad.getMessage() != null) {
			String[] parts = bad.getMessage().split(":");
			return switch (parts[0]) {
				case "count" -> Component.translatable(T + "error.count", parts[1], parts[2]);
				case "unknown_place" -> Component.translatable(T + "error.unknown_place", parts.length > 1 ? parts[1] : "?");
				case "no_origin" -> Component.translatable(T + "error.no_origin", parts.length > 1 ? parts[1] : "?");
				default -> Component.translatable(T + "error.no_places");
			};
		}
		if (cause instanceof java.io.IOException io) {
			return Component.translatable(T + "error.write", String.valueOf(io.getMessage()));
		}
		return Component.translatable(T + "error.network");
	}

	private static void chat(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(message, false);
		}
	}
}
