package com.mystaria.events.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.events.tournament.TournamentForm;
import com.mystaria.events.tournament.ui.Ui;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Admin: an event's trainer NPC (Adrien 2026-11-02), redone in Phantasmon's menu look with drop-down lists
 * (2026-11-18). Its identity (name, skin, event), its battles (AI level, format, AI debug), what they impose from
 * Cobblemon Battle Showtime (intro, the NPC starring in it, and ambiance), its team copied from the admin's; then
 * moved where the admin stands, its event opened or closed, tried out, deleted.
 */
public final class NpcEditScreen extends Screen {

	static final List<String> AIS = List.of("random", "easy", "normal", "hard", "veryhard", "expert", "reborn", "reborn_tweak");

	private static final int W = 420;

	private EventNpcDto npc;
	private final Ui ui = new Ui();
	private final Ui.Field name = new Ui.Field(Ui.Kind.TEXT, 32);
	private final Ui.Field skin = new Ui.Field(Ui.Kind.TEXT, 16);
	private final Ui.Field event = new Ui.Field(Ui.Kind.NAME, 32);
	private final Ui.Dropdown<String> ai;
	private final Ui.Dropdown<String[]> format;
	private final Ui.Dropdown<String> intro;
	private final Ui.Dropdown<String> ambiance;
	private boolean aiDebug;
	private boolean busy;
	private Component message;

	/** The NPC last opened in the editor: {@code /phantasmon event npc move} moves it. */
	static java.util.UUID lastEdited;

	public NpcEditScreen(EventNpcDto npc) {
		super(Component.translatable("phantasmon_events.npc.edit_title"));
		this.npc = npc;
		lastEdited = npc.uuid();
		name.value = npc.name();
		skin.value = npc.skin() == null ? "" : npc.skin();
		event.value = npc.event();
		aiDebug = npc.aiDebug();
		ai = new Ui.Dropdown<>(AIS, NpcEditScreen::aiName, AIS.contains(npc.ai()) ? npc.ai() : "normal");
		List<String[]> formats = formats();
		format = new Ui.Dropdown<>(formats, f -> Component.literal(f[1]),
				formats.stream().filter(f -> f[0].equals(npc.formatId())).findFirst().orElse(formats.get(0)));
		String currentIntro = npc.intro() == null ? "" : npc.intro();
		String currentAmbiance = npc.ambiance() == null ? "" : npc.ambiance();
		intro = new Ui.Dropdown<>(intros(currentIntro), NpcEditScreen::introName, currentIntro);
		ambiance = new Ui.Dropdown<>(ambiances(currentAmbiance), NpcEditScreen::ambianceName, currentAmbiance);
	}

	static List<String[]> formats() {
		List<String[]> all = new ArrayList<>();
		all.add(new String[] { "free", Component.translatable("phantasmon_events.npc.format_free").getString() });
		all.addAll(TournamentForm.FORMATS);
		return all;
	}

	/**
	 * "" (the player's pick), then every intro: Showtime's own, then the add-ons' (the League's), reserved ones
	 * included — an imposed intro plays even for a player who never won it.
	 */
	static List<String> intros(String current) {
		List<String> all = new ArrayList<>();
		all.add("");
		for (com.mystaria.showtime.battle.BattleCinematic.Intro builtIn : com.mystaria.showtime.battle.BattleCinematic.Intro.values()) {
			all.add(builtIn.name());
		}
		for (com.mystaria.showtime.api.CustomIntro custom : com.mystaria.showtime.api.ShowtimeHooks.customIntros()) {
			if (!all.contains(custom.id())) {
				all.add(custom.id());
			}
		}
		if (!all.contains(current)) {
			all.add(current); // one this client doesn't have (an add-on missing): kept as is
		}
		return all;
	}

	/** "" (the player's pick), then every Showtime ambiance, reserved ones and resource packs' included. */
	static List<String> ambiances(String current) {
		List<String> all = new ArrayList<>();
		all.add("");
		all.addAll(com.mystaria.showtime.ambiance.Ambiances.ids());
		if (!all.contains(current)) {
			all.add(current);
		}
		return all;
	}

	static Component introName(String id) {
		return id.isEmpty() ? Component.translatable("phantasmon_events.npc.player_pick")
				: com.mystaria.showtime.battle.BattleCinematic.introDisplayName(id);
	}

	static Component ambianceName(String id) {
		return id.isEmpty() ? Component.translatable("phantasmon_events.npc.player_pick")
				: com.mystaria.showtime.ambiance.Ambiances.displayName(id);
	}

	static Component aiName(String ai) {
		return Component.translatable("phantasmon_events.npc.ai." + ai);
	}

	// ---------------------------------------------------------------- drawing

	private int height() {
		return 290;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		g.fill(0, 0, width, height, MenuUi.VEIL);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		MenuUi.Palette c = MenuUi.p();
		ui.begin(mouseX, mouseY);
		int left = (width - W) / 2;
		int top = Math.max(6, (height - height()) / 2);
		int right = left + W;
		int bottom = top + height();
		g.fill(left, top, right, bottom, c.panel());
		MenuUi.frame(g, left, top, right, bottom);
		MenuUi.header(g, left, top, right, 22);
		MenuUi.text(g, title.copy().append(" · " + npc.name()).withStyle(s -> s.withBold(true)), left + 22, top + 7, W - 140, c.white());
		// The event's state, top right.
		Component state = Component.translatable(npc.open() ? "phantasmon_events.npc.state_open" : "phantasmon_events.npc.state_closed", npc.event());
		int sw = MenuUi.width(state) + 12;
		g.fill(right - sw - 8, top + 5, right - 8, top + 17, npc.open() ? c.accentDim() : c.dark());
		MenuUi.centered(g, state, right - 8 - sw / 2, top + 7, sw, npc.open() ? c.white() : c.muted());

		int x = left + 12;
		int w = W - 24;
		int row = top + 30;
		// Identity
		section(g, "phantasmon_events.npc.section.identity", x, row, w);
		row += 24;
		ui.field(g, name, x, row, 160, 15, Component.translatable("phantasmon_events.npc.name"), null, name.value.isBlank());
		ui.field(g, skin, x + 168, row, 110, 15, Component.translatable("phantasmon_events.npc.skin"),
				Component.translatable("phantasmon_events.npc.skin_hint").getString(), false);
		ui.field(g, event, x + 286, row, w - 286, 15, Component.translatable("phantasmon_events.npc.event"), null, event.value.isBlank());
		row += 24;
		// Battle
		section(g, "phantasmon_events.npc.section.battle", x, row, w);
		row += 24;
		ui.dropdown(g, ai, x, row, 130, 15, Component.translatable("phantasmon_events.npc.ai"));
		ui.dropdown(g, format, x + 138, row, 140, 15, Component.translatable("phantasmon_events.npc.format"));
		MenuUi.text(g, Component.translatable("phantasmon_events.npc.ai_debug"), x + 286, row - 10, w - 286, c.muted());
		MenuUi.toggle(g, x + 286, row + 2, aiDebug);
		MenuUi.small(g, Component.translatable(aiDebug ? "phantasmon_events.npc.ai_debug_on" : "phantasmon_events.npc.ai_debug_off"),
				x + 312, row + 4, w - 312, c.muted());
		ui.zone(x + 286, row, w - 286, 15, () -> aiDebug = !aiDebug);
		row += 24;
		// Showtime staging
		section(g, "phantasmon_events.npc.section.show", x, row, w);
		row += 24;
		ui.dropdown(g, intro, x, row, (w - 8) / 2, 15, Component.translatable("phantasmon_events.npc.intro"));
		ui.dropdown(g, ambiance, x + (w - 8) / 2 + 8, row, (w - 8) / 2, 15, Component.translatable("phantasmon_events.npc.ambiance"));
		row += 19;
		MenuUi.small(g, Component.translatable("phantasmon_events.npc.show_note"), x, row, w, c.edge());
		row += 14;
		// Team
		section(g, "phantasmon_events.npc.section.team", x, row, w);
		row += 14;
		List<String> species = npc.teamSpecies() == null ? List.of() : npc.teamSpecies();
		Component team = species.isEmpty() ? Component.translatable("phantasmon_events.npc.no_team")
				: Component.literal(String.join(", ", species));
		MenuUi.text(g, team, x, row, w, species.isEmpty() ? c.star() : c.white());
		row += 13;
		ui.button(g, x, row, (w - 8) / 2, 16, Component.translatable("phantasmon_events.npc.team_ghost"), busy ? null : () -> team(Map.of("team", "GHOST")));
		ui.button(g, x + (w - 8) / 2 + 8, row, (w - 8) / 2, 16, Component.translatable("phantasmon_events.npc.team_cobblemon"), busy ? null : () -> {
			Map<String, Object> party = PhantasmonApi.cobblemonParty();
			if (party == null) {
				message = Component.translatable("phantasmon_events.npc.no_party").withStyle(ChatFormatting.RED);
			} else {
				team(party);
			}
		});
		row += 24;
		// Place and event
		g.fill(x, row, x + w, row + 1, c.black());
		g.fill(x, row + 1, x + w, row + 2, c.edge());
		row += 7;
		int third = (w - 16) / 3;
		ui.button(g, x, row, third, 16, Component.translatable("phantasmon_events.npc.move_here"), busy ? null : this::moveHere);
		ui.button(g, x + third + 8, row, third, 16, Component.translatable(npc.open() ? "phantasmon_events.npc.close_event"
				: "phantasmon_events.npc.open_event", npc.event()), busy ? null : () -> setOpen(!npc.open()));
		ui.button(g, x + 2 * (third + 8), row, third, 16, Component.translatable("phantasmon_events.npc.try"), () -> {
			onClose();
			Minecraft.getInstance().setScreen(new NpcChallengeScreen(npc));
		});
		// Footer
		int fy = bottom - 24;
		ui.danger(g, x, fy, 80, 16, Component.translatable("phantasmon_events.npc.delete"), busy ? null : this::delete);
		if (message != null) {
			MenuUi.centered(g, message, x + 88 + (w - 88 - 196) / 2, fy + 4, w - 88 - 196, c.white());
		}
		ui.button(g, right - 12 - 196, fy, 90, 16, Component.translatable("gui.done"), this::onClose);
		ui.primary(g, right - 12 - 100, fy, 100, 16, Component.translatable("phantasmon_events.npc.save"), busy ? null : this::save);
		ui.overlay(g, height);
	}

	private void section(GuiGraphics g, String key, int x, int y, int w) {
		MenuUi.Palette c = MenuUi.p();
		Component text = Component.translatable(key).withStyle(s -> s.withBold(true));
		MenuUi.text(g, text, x, y, w, c.accent());
		int tw = MenuUi.width(text) + 6;
		g.fill(x + tw, y + 4, x + w, y + 5, c.edge());
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && ui.click(mouseX, mouseY)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return ui.scrollList(scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		return ui.charTyped(chr) || super.charTyped(chr, modifiers);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && ui.listOpen()) {
			ui.closeList();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && ui.focused() != null) {
			ui.focus(null);
			return true;
		}
		if (ui.key(keyCode, hasControlDown())) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- actions

	private void save() {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name.value.strip());
		body.put("skin", skin.value.strip());
		body.put("event", event.value.strip().toLowerCase(Locale.ROOT));
		body.put("ai", ai.value);
		body.put("format_id", format.value[0]);
		body.put("ai_debug", aiDebug);
		body.put("intro", intro.value);
		body.put("ambiance", ambiance.value);
		post("/admin/events/npcs/" + npc.uuid(), body, "phantasmon_events.npc.saved");
	}

	private void moveHere() {
		Map<String, Object> body = here();
		if (body != null) {
			post("/admin/events/npcs/" + npc.uuid(), body, "phantasmon_events.npc.moved");
		}
	}

	private void delete() {
		busy = true;
		PhantasmonApi.delete("/admin/events/npcs/" + npc.uuid(), Map.class)
				.thenAccept(ignored -> Minecraft.getInstance().execute(this::onClose))
				.exceptionally(this::failed);
	}

	/**
	 * Where the admin stands, facing where they look: the place of an NPC posed or moved there. Inside a hub's zone, the
	 * place is given in that hub's coordinates ({@code hub}): the NPC then belongs to the hub and stands in all its
	 * zones (Adrien 2026-11-03).
	 */
	static Map<String, Object> here() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return null;
		}
		Map<String, Object> body = new HashMap<>();
		body.put("server_fingerprint", PhantasmonApi.serverFingerprint());
		body.put("dimension", mc.level.dimension().location().toString());
		body.put("x", mc.player.getX());
		body.put("y", mc.player.getY());
		body.put("z", mc.player.getZ());
		body.put("yaw", (double) mc.player.getYRot());
		body.put("hub", null);
		List<com.mystaria.phantasmon.client.hub.HubAnchorDto> anchors = PhantasmonApi.anchorsHere();
		for (com.mystaria.phantasmon.client.hub.HubAnchorDto anchor : anchors == null ? List.<com.mystaria.phantasmon.client.hub.HubAnchorDto>of() : anchors) {
			com.mystaria.phantasmon.client.hub.HubCoordinates hub = anchor.coordinates();
			if (hub.contains(mc.player.getX(), mc.player.getY(), mc.player.getZ())) {
				double[] inHub = hub.toHub(mc.player.getX(), mc.player.getZ());
				body.put("hub", anchor.hub());
				body.put("x", inHub[0]);
				body.put("y", mc.player.getY() - hub.originY());
				body.put("z", inHub[1]);
				body.put("yaw", (double) hub.toHubYaw(mc.player.getYRot()));
				break;
			}
		}
		return body;
	}

	private void team(Map<String, Object> body) {
		post("/admin/events/npcs/" + npc.uuid() + "/team", body, "phantasmon_events.npc.team_set");
	}

	private void setOpen(boolean open) {
		busy = true;
		PhantasmonApi.post("/admin/events/" + npc.event() + "/open", Map.of("open", open), Map.class)
				.thenAccept(ignored -> Minecraft.getInstance().execute(() -> {
					busy = false;
					npc = new EventNpcDto(npc.uuid(), npc.event(), open, npc.name(), npc.skin(), npc.x(), npc.y(), npc.z(), npc.yaw(),
							npc.ai(), npc.formatId(), npc.teamSize(), npc.teamSpecies(), npc.hub(), npc.aiDebug(), npc.intro(), npc.ambiance());
					message = Component.translatable(open ? "phantasmon_events.npc.event_opened_short" : "phantasmon_events.npc.event_closed_short")
							.withStyle(ChatFormatting.GREEN);
				}))
				.exceptionally(this::failed);
	}

	private void post(String path, Map<String, Object> body, String done) {
		busy = true;
		PhantasmonApi.post(path, body, EventNpcDto.class)
				.thenAccept(updated -> Minecraft.getInstance().execute(() -> {
					busy = false;
					npc = updated;
					message = Component.translatable(done).withStyle(ChatFormatting.GREEN);
				}))
				.exceptionally(this::failed);
	}

	private Void failed(Throwable failure) {
		Minecraft.getInstance().execute(() -> {
			busy = false;
			message = errorMessage(failure);
		});
		return null;
	}

	static Component errorMessage(Throwable failure) {
		String code = PhantasmonApi.errorCode(failure);
		return (code == null ? Component.translatable("phantasmon_events.error.network")
				: Component.translatableWithFallback("phantasmon_events.error." + code.toLowerCase(Locale.ROOT), code)).withStyle(ChatFormatting.RED);
	}
}
