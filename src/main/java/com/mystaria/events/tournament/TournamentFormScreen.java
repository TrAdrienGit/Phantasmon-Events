package com.mystaria.events.tournament;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * Admin: a new tournament, or the dates of one (plan-tournois.md): name, kind (weekly Swiss or double elimination
 * bracket), best of 1/3/5, Smogon format, start, registration opening and closing (optional, {@code JJ-MM-AAAA HH:MM},
 * local time). Editing keeps the name, kind and formats (fixed once players registered) and changes the dates only.
 */
public final class TournamentFormScreen extends Screen {

	/** Formats a tournament may use (the backend checks them), National Dex first, with their Showdown names. */
	static final List<String[]> FORMATS = List.of(
			new String[] { "gen9nationaldex", "[Gen 9] National Dex" },
			new String[] { "gen9nationaldexubers", "[Gen 9] National Dex Ubers" },
			new String[] { "gen9nationaldexuu", "[Gen 9] National Dex UU" },
			new String[] { "gen9nationaldexru", "[Gen 9] National Dex RU" },
			new String[] { "gen9nationaldexmonotype", "[Gen 9] National Dex Monotype" },
			new String[] { "gen9nationaldexlc", "[Gen 9] National Dex LC" },
			new String[] { "gen9nationaldex1v1", "[Gen 9] National Dex 1v1" },
			new String[] { "gen9nationaldexag", "[Gen 9] National Dex AG" },
			new String[] { "gen9ou", "[Gen 9] OU" },
			new String[] { "gen9ubers", "[Gen 9] Ubers" },
			new String[] { "gen9uu", "[Gen 9] UU" },
			new String[] { "gen9ru", "[Gen 9] RU" },
			new String[] { "gen9nu", "[Gen 9] NU" },
			new String[] { "gen9pu", "[Gen 9] PU" },
			new String[] { "gen9zu", "[Gen 9] ZU" },
			new String[] { "gen9lc", "[Gen 9] LC" },
			new String[] { "gen9monotype", "[Gen 9] Monotype" },
			new String[] { "gen91v1", "[Gen 9] 1v1" },
			new String[] { "gen9anythinggoes", "[Gen 9] Anything Goes" });
	private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

	private final Screen parent;
	/** Null: a new tournament; else the one whose dates are edited. */
	private final TournamentDto editing;
	private EditBox name;
	private EditBox startsAt;
	private EditBox opensAt;
	private EditBox closesAt;
	private String kind = "SWISS";
	private int bestOf = 1;
	private String[] format = FORMATS.get(0);
	private Component error;

	public TournamentFormScreen(Screen parent, TournamentDto editing) {
		super(Component.translatable(editing == null ? "phantasmon_events.screen.form.create" : "phantasmon_events.screen.form.edit"));
		this.parent = parent;
		this.editing = editing;
	}

	@Override
	protected void init() {
		int left = width / 2 - 155;
		int y = 40;
		name = addRenderableWidget(new EditBox(font, left, y, 310, 20, Component.translatable("phantasmon_events.screen.form.name")));
		name.setMaxLength(32);
		name.setHint(Component.translatable("phantasmon_events.screen.form.name_hint").withStyle(ChatFormatting.DARK_GRAY));
		if (editing != null) {
			name.setValue(editing.name());
			name.setEditable(false);
		}
		y += 30;
		if (editing == null) {
			addRenderableWidget(CycleButton.<String>builder(value -> Component.translatable(
					"SWISS".equals(value) ? "phantasmon_events.tournament.kind.swiss" : "phantasmon_events.tournament.kind.bracket"))
					.withValues("SWISS", "BRACKET").withInitialValue(kind)
					.create(left, y, 152, 20, Component.translatable("phantasmon_events.screen.form.kind"), (b, value) -> kind = value));
			addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal("Bo" + value))
					.withValues(1, 3, 5).withInitialValue(bestOf)
					.create(left + 158, y, 152, 20, Component.translatable("phantasmon_events.screen.form.best_of"), (b, value) -> bestOf = value));
			y += 24;
			addRenderableWidget(CycleButton.<String[]>builder(value -> Component.literal(value[1]))
					.withValues(FORMATS).withInitialValue(format)
					.create(left, y, 310, 20, Component.translatable("phantasmon_events.screen.form.format"), (b, value) -> format = value));
			y += 30;
		}
		startsAt = dateBox(left, y, editing == null ? null : editing.startsAt());
		y += 34;
		opensAt = dateBox(left, y, editing == null ? null : editing.registrationOpensAt());
		closesAt = dateBox(left + 158, y, editing == null ? null : editing.registrationClosesAt());
		y += 34;
		addRenderableWidget(Button.builder(Component.translatable(editing == null ? "phantasmon_events.screen.form.create_button"
				: "phantasmon_events.screen.form.save"), b -> submit()).bounds(left, y, 152, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + 158, y, 152, 20).build());
	}

	private EditBox dateBox(int x, int y, String iso) {
		EditBox box = addRenderableWidget(new EditBox(font, x, y + 10, 152, 20, Component.empty()));
		box.setMaxLength(16);
		box.setHint(Component.literal("JJ-MM-AAAA HH:MM").withStyle(ChatFormatting.DARK_GRAY));
		if (iso != null) {
			box.setValue(INPUT.format(Instant.parse(iso).atZone(ZoneId.systemDefault())));
		}
		return box;
	}

	private void submit() {
		Map<String, Object> body = new HashMap<>();
		try {
			put(body, "starts_at", startsAt);
			put(body, "registration_opens_at", opensAt);
			put(body, "registration_closes_at", closesAt);
		} catch (DateTimeParseException ex) {
			error = Component.translatable("phantasmon_events.screen.form.bad_date");
			return;
		}
		if (editing != null) {
			PhantasmonApi.post("/admin/tournaments/" + editing.name() + "/schedule", body, TournamentDto.class)
					.thenAccept(t -> minecraft.execute(this::onClose))
					.exceptionally(this::failed);
			return;
		}
		body.put("name", name.getValue().strip());
		body.put("kind", kind);
		body.put("best_of", bestOf);
		body.put("format_id", format[0]);
		PhantasmonApi.post("/admin/tournaments", body, TournamentDto.class)
				.thenAccept(t -> minecraft.execute(this::onClose))
				.exceptionally(this::failed);
	}

	private static void put(Map<String, Object> body, String key, EditBox box) {
		String value = box.getValue().strip();
		if (!value.isEmpty()) {
			body.put(key, LocalDateTime.parse(value, INPUT).atZone(ZoneId.systemDefault()).toInstant().toString());
		}
	}

	private Void failed(Throwable failure) {
		minecraft.execute(() -> error = TournamentScreen.errorMessage(failure));
		return null;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(font, title, width / 2, 16, 0xFFFFFFFF);
		int left = width / 2 - 155;
		int y = editing == null ? 40 + 30 + 24 + 30 : 70;
		g.drawString(font, Component.translatable("phantasmon_events.screen.form.starts_at"), left, y, 0xFFC0C0C0, false);
		g.drawString(font, Component.translatable("phantasmon_events.screen.form.opens_at"), left, y + 34, 0xFFC0C0C0, false);
		g.drawString(font, Component.translatable("phantasmon_events.screen.form.closes_at"), left + 158, y + 34, 0xFFC0C0C0, false);
		if (error != null) {
			g.drawCenteredString(font, error, width / 2, y + 96, 0xFFFF5555);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
