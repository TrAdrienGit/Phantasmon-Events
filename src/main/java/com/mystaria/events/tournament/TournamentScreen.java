package com.mystaria.events.tournament;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * The tournaments screen (plan-tournois.md, screens): the tournaments on the left, the one picked on the right with
 * its dates and players (green: checked in; struck: dropped) and what the player can do — register, unregister,
 * check in. Admins also create a tournament, change its dates, open the check-in, add or remove a player, cancel.
 * Kept up to date while open ({@code EventsTournamentChanged}).
 */
public final class TournamentScreen extends Screen {

	private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);
	private static final int LIST_W = 150;
	private static final int ROWS = 9;

	private static TournamentScreen open;

	private List<TournamentDto> tournaments = List.of();
	private String selected;
	private Component message;
	private EditBox playerName;

	public TournamentScreen() {
		super(Component.translatable("phantasmon_events.screen.title"));
	}

	/** Every {@code EventsTournamentChanged}: an open screen reads the tournaments again. */
	public static void register() {
		PhantasmonApi.onEventsMessage((type, data) -> {
			if ("EventsTournamentChanged".equals(type) && open != null) {
				open.refresh();
			}
		});
	}

	@Override
	protected void init() {
		open = this;
		refresh();
		rebuild();
	}

	@Override
	public void removed() {
		if (open == this) {
			open = null;
		}
	}

	private void refresh() {
		PhantasmonApi.get("/tournaments", TournamentDto[].class).thenAccept(all -> Minecraft.getInstance().execute(() -> {
			tournaments = Arrays.asList(all);
			if (selected == null && !tournaments.isEmpty()) {
				selected = tournaments.get(0).name();
			}
			rebuild();
		})).exceptionally(this::failed);
	}

	private TournamentDto current() {
		return tournaments.stream().filter(t -> t.name().equals(selected)).findFirst().orElse(null);
	}

	private void rebuild() {
		String typed = playerName == null ? "" : playerName.getValue();
		clearWidgets();
		playerName = null;
		int left = left();
		int top = 34;
		for (int i = 0; i < Math.min(ROWS, tournaments.size()); i++) {
			TournamentDto t = tournaments.get(i);
			Component label = Component.literal(t.name()).withStyle(t.name().equals(selected) ? ChatFormatting.YELLOW
					: t.status().equals("FINISHED") || t.status().equals("CANCELLED") ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE);
			addRenderableWidget(Button.builder(label, b -> {
				selected = t.name();
				message = null;
				rebuild();
			}).bounds(left, top + i * 22, LIST_W, 20).build());
		}
		boolean admin = PhantasmonApi.isAdmin();
		if (admin) {
			addRenderableWidget(Button.builder(Component.translatable("phantasmon_events.screen.create"),
					b -> minecraft.setScreen(new TournamentFormScreen(this, null))).bounds(left, top + ROWS * 22 + 4, LIST_W, 20).build());
		}
		TournamentDto t = current();
		int x = left + LIST_W + 10;
		int w = panelWidth();
		int y = height - 78;
		if (t != null && !over(t)) {
			boolean registered = mine(t) != null;
			if (!registered && t.registrationOpen()) {
				addRenderableWidget(action("phantasmon_events.screen.register", x, y, "/tournaments/" + t.name() + "/register", false));
			}
			if (registered && !t.status().equals("RUNNING")) {
				addRenderableWidget(action("phantasmon_events.screen.unregister", x, y, "/tournaments/" + t.name() + "/register", true));
			}
			if (registered && t.status().equals("CHECK_IN") && !mine(t).checkedIn()) {
				addRenderableWidget(Button.builder(Component.translatable("phantasmon_events.screen.check_in"), b ->
						send(PhantasmonApi.post("/tournaments/" + t.name() + "/check-in", PhantasmonApi.cobblemonParty(), TournamentDto.class)))
						.bounds(x + 104, y, 100, 20).build());
			}
			if (admin) {
				adminButtons(t, x, w, typed);
			}
		}
		if (t != null && admin && (t.status().equals("CANCELLED") || t.status().equals("FINISHED"))) {
			addRenderableWidget(Button.builder(Component.translatable("phantasmon_events.screen.delete"), b ->
					PhantasmonApi.delete("/admin/tournaments/" + t.name(), Void.class).thenAccept(ignored -> Minecraft.getInstance().execute(() -> {
						selected = null;
						message = null;
						refresh();
					})).exceptionally(this::failed)).bounds(x, height - 54, 100, 20).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(width / 2 - 50, height - 26, 100, 20).build());
	}

	/**
	 * Admin rows: the tournament's steps (dates, check-in, start, next round, rounds ±1, finish, cancel) and, for the
	 * player named in the box, register / remove and, once running, win / forfeit / invite again.
	 */
	private void adminButtons(TournamentDto t, int x, int w, String typed) {
		String base = "/admin/tournaments/" + t.name();
		boolean running = t.status().equals("RUNNING");
		int ay = height - 54;
		int bx = x;
		if (!running) {
			bx = button("phantasmon_events.screen.dates", bx, ay, 70, () -> minecraft.setScreen(new TournamentFormScreen(this, t)));
			if (t.status().equals("REGISTRATION")) {
				bx = button("phantasmon_events.screen.open_check_in", bx, ay, 90, () -> send(PhantasmonApi.post(base + "/check-in", null, TournamentDto.class)));
			}
			bx = button("phantasmon_events.screen.start", bx, ay, 70, () -> send(PhantasmonApi.post(base + "/start", null, TournamentDto.class)));
		} else {
			bx = button("phantasmon_events.screen.next_round", bx, ay, 96, () -> send(PhantasmonApi.post(base + "/next-round", null, TournamentDto.class)));
			bx = button("phantasmon_events.screen.round_plus", bx, ay, 26, () -> send(PhantasmonApi.post(base + "/rounds", Map.of("delta", 1), TournamentDto.class)));
			bx = button("phantasmon_events.screen.round_minus", bx, ay, 26, () -> send(PhantasmonApi.post(base + "/rounds", Map.of("delta", -1), TournamentDto.class)));
			bx = button("phantasmon_events.screen.finish", bx, ay, 70, () -> send(PhantasmonApi.post(base + "/finish", null, TournamentDto.class)));
		}
		button("phantasmon_events.screen.cancel", bx, ay, 70, () -> send(PhantasmonApi.post(base + "/cancel", null, TournamentDto.class)));

		int py = height - 102;
		int buttons = running ? 4 : 2;
		int boxW = Math.max(70, w - buttons * 64);
		playerName = addRenderableWidget(new EditBox(font, x, py, boxW, 20, Component.empty()));
		playerName.setMaxLength(16);
		playerName.setValue(typed);
		playerName.setHint(Component.translatable("phantasmon_events.screen.player_hint").withStyle(ChatFormatting.DARK_GRAY));
		int px = x + boxW + 4;
		if (!running) {
			px = button("phantasmon_events.screen.add", px, py, 60, () -> named(name ->
					send(PhantasmonApi.post(base + "/players", Map.of("username", name), TournamentDto.class))));
		}
		px = button("phantasmon_events.screen.remove", px, py, 60, () -> named(name ->
				send(PhantasmonApi.delete(base + "/players/" + name, TournamentDto.class))));
		if (running) {
			px = button("phantasmon_events.screen.win", px, py, 60, () -> named(name ->
					send(PhantasmonApi.post(base + "/result", Map.of("username", name, "forfeit", false), TournamentDto.class))));
			px = button("phantasmon_events.screen.forfeit", px, py, 60, () -> named(name ->
					send(PhantasmonApi.post(base + "/result", Map.of("username", name, "forfeit", true), TournamentDto.class))));
			button("phantasmon_events.screen.reinvite", px, py, 60, () -> named(name ->
					send(PhantasmonApi.post(base + "/reinvite", Map.of("username", name), TournamentDto.class))));
		}
	}

	/** A button of that width; returns where the next one goes. */
	private int button(String key, int x, int y, int width, Runnable action) {
		addRenderableWidget(Button.builder(Component.translatable(key), b -> action.run()).bounds(x, y, width, 20).build());
		return x + width + 3;
	}

	private void named(java.util.function.Consumer<String> action) {
		if (playerName != null && !playerName.getValue().isBlank()) {
			action.accept(playerName.getValue().strip());
		}
	}

	private Button action(String key, int x, int y, String path, boolean delete) {
		return Button.builder(Component.translatable(key), b -> send(delete ? PhantasmonApi.delete(path, TournamentDto.class)
				: PhantasmonApi.post(path, null, TournamentDto.class))).bounds(x, y, 100, 20).build();
	}

	private void send(java.util.concurrent.CompletableFuture<TournamentDto> request) {
		request.thenAccept(t -> Minecraft.getInstance().execute(() -> {
			message = null;
			refresh();
		})).exceptionally(this::failed);
	}

	private Void failed(Throwable failure) {
		Minecraft.getInstance().execute(() -> message = errorMessage(failure));
		return null;
	}

	static Component errorMessage(Throwable failure) {
		String code = PhantasmonApi.errorCode(failure);
		return code == null ? Component.translatable("phantasmon_events.error.network")
				: Component.translatableWithFallback("phantasmon_events.error." + code.toLowerCase(Locale.ROOT), code);
	}

	private TournamentDto.Player mine(TournamentDto t) {
		UUID me = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
		return t.players().stream().filter(p -> p.uuid().equals(me)).findFirst().orElse(null);
	}

	private static boolean over(TournamentDto t) {
		return t.status().equals("FINISHED") || t.status().equals("CANCELLED");
	}

	private int left() {
		return Math.max(8, width / 2 - 230);
	}

	private int panelWidth() {
		return Math.min(310, width - left() - LIST_W - 18);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
		int x = left() + LIST_W + 10;
		int w = panelWidth();
		int y = 34;
		if (tournaments.isEmpty()) {
			g.drawString(font, Component.translatable("phantasmon_events.tournament.list.none"), left(), y, 0xFFC0C0C0, false);
		}
		TournamentDto t = current();
		if (t != null) {
			g.drawString(font, Component.literal(t.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x, y, 0xFFFFFFFF, true);
			y += 14;
			for (Component line : List.of(
					Component.translatable(t.swiss() ? "phantasmon_events.tournament.kind.swiss" : "phantasmon_events.tournament.kind.bracket")
							.append(" — Bo" + t.bestOf() + " — " + t.formatName()),
					Component.translatable("phantasmon_events.screen.status",
							Component.translatable("phantasmon_events.tournament.status." + t.status().toLowerCase(Locale.ROOT))),
					Component.translatable("phantasmon_events.screen.starts", date(t.startsAt())),
					Component.translatable("phantasmon_events.screen.registration", date(t.registrationOpensAt()),
							date(t.registrationClosesAt()), Component.translatable(t.registrationOpen()
									? "phantasmon_events.tournament.open" : "phantasmon_events.tournament.closed")))) {
				g.drawWordWrap(font, line, x, y, w, 0xFFE0E0E0);
				y += font.wordWrapHeight(line, w) + 2;
			}
			y += 6;
			if (t.currentRound() > 0) {
				renderRounds(g, t, x, y, w);
				return;
			}
			long present = t.players().stream().filter(TournamentDto.Player::checkedIn).count();
			g.drawString(font, Component.translatable("phantasmon_events.screen.players", t.players().size(), present), x, y, 0xFFFFFFFF, false);
			y += 12;
			List<Component> names = new ArrayList<>();
			for (TournamentDto.Player p : t.players()) {
				names.add(Component.literal(p.username()).withStyle(p.dropped() ? ChatFormatting.STRIKETHROUGH
						: p.checkedIn() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			}
			int col = 0;
			int colW = 100;
			int limitY = height - 114;
			for (Component name : names) {
				if (y > limitY) {
					g.drawString(font, "…", x + col * colW, y, 0xFFC0C0C0, false);
					break;
				}
				g.drawString(font, name, x + col * colW, y, 0xFFFFFFFF, false);
				col++;
				if ((col + 1) * colW > w) {
					col = 0;
					y += 10;
				}
			}
		}
		if (message != null) {
			g.drawCenteredString(font, message, width / 2, 23, 0xFFFF5555);
		}
	}

	/** The current round's matches, then the standings, as far as the room goes. */
	private void renderRounds(GuiGraphics g, TournamentDto t, int x, int y, int w) {
		int limitY = height - 114;
		g.drawString(font, Component.translatable(t.swiss() ? "phantasmon_events.screen.round" : "phantasmon_events.screen.wave",
				t.currentRound(), t.plannedRounds() == null ? "?" : t.plannedRounds()).withStyle(ChatFormatting.YELLOW), x, y, 0xFFFFFFFF, false);
		y += 11;
		if (!t.swiss()) {
			g.drawString(font, Component.translatable("phantasmon_events.screen.web", "server-linux.tail192969.ts.net/t/" + t.name())
					.withStyle(ChatFormatting.DARK_AQUA), x, y, 0xFFFFFFFF, false);
			y += 11;
		}
		for (TournamentDto.Match m : t.currentMatches()) {
			if (y > limitY - 40) {
				g.drawString(font, "…", x, y, 0xFFC0C0C0, false);
				y += 10;
				break;
			}
			Component line;
			if (m.player2() == null) {
				line = Component.translatable("phantasmon_events.screen.match.bye", m.player1()).withStyle(ChatFormatting.GRAY);
			} else {
				ChatFormatting state = m.status().equals("DONE") ? ChatFormatting.GREEN
						: m.status().equals("PLAYING") ? ChatFormatting.AQUA : ChatFormatting.YELLOW;
				Component where = t.swiss() ? Component.empty() : Component.translatable("phantasmon_events.screen.bracket."
						+ m.bracket().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.DARK_GRAY);
				line = where.copy().append(Component.literal(m.player1())).withStyle(m.player1().equals(m.winner()) ? ChatFormatting.BOLD : ChatFormatting.RESET)
						.append(Component.literal(" " + m.wins1() + " - " + m.wins2() + " ").withStyle(state))
						.append(Component.literal(m.player2()).withStyle(m.player2().equals(m.winner()) ? ChatFormatting.BOLD : ChatFormatting.RESET))
						.append(Component.translatable("phantasmon_events.screen.match." + m.status().toLowerCase(Locale.ROOT)).withStyle(state))
						.append(m.forfeit() ? Component.translatable("phantasmon_events.screen.match.forfeit").withStyle(ChatFormatting.RED)
								: Component.empty());
			}
			g.drawString(font, line, x, y, 0xFFFFFFFF, false);
			y += 10;
		}
		y += 6;
		g.drawString(font, Component.translatable("phantasmon_events.screen.standings").withStyle(ChatFormatting.YELLOW), x, y, 0xFFFFFFFF, false);
		y += 11;
		for (TournamentDto.Standing s : t.standings() == null ? List.<TournamentDto.Standing>of() : t.standings()) {
			if (y > limitY) {
				g.drawString(font, "…", x, y, 0xFFC0C0C0, false);
				break;
			}
			g.drawString(font, Component.literal(s.rank() + ". ").append(Component.literal(s.username())
					.withStyle(s.dropped() ? ChatFormatting.STRIKETHROUGH : ChatFormatting.WHITE)), x, y, 0xFFFFFFFF, false);
			g.drawString(font, Component.translatable("phantasmon_events.screen.standing", s.points(), s.wins(), s.losses(), s.buchholz()),
					x + Math.min(150, w / 2), y, 0xFFC0C0C0, false);
			y += 10;
		}
	}

	private static Component date(String iso) {
		return iso == null ? Component.translatable("phantasmon_events.tournament.no_date")
				: Component.literal(SHOWN.format(Instant.parse(iso).atZone(ZoneId.systemDefault())));
	}
}
