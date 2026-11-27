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
	/** The stadium imposed with it (Showtime 1.3): "" = the preset's own, else the player's. */
	private final Ui.Dropdown<String> stadium;
	/** League Reborn's field its battles impose; "" for none. */
	private final Ui.Dropdown<String> field;
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
				formats.stream().filter(f -> f[0].equals(npc.formatId())).findFirst()
						.orElse(formats.stream().filter(f -> "free".equals(f[0])).findFirst().orElseThrow()))
				.headers(f -> f[0].startsWith("#"));
		String currentIntro = npc.intro() == null ? "" : npc.intro();
		String currentAmbiance = npc.ambiance() == null ? "" : npc.ambiance();
		intro = new Ui.Dropdown<>(intros(currentIntro), NpcEditScreen::introName, currentIntro);
		// Saved as one Showtime scene, "<ambiance>|<stadium>" (2026-11-19).
		String sceneAmbiance = com.mystaria.showtime.ambiance.Ambiances.sceneAmbiance(currentAmbiance);
		String sceneStadium = com.mystaria.showtime.ambiance.Ambiances.sceneStadium(currentAmbiance);
		ambiance = new Ui.Dropdown<>(ambiances(sceneAmbiance), NpcEditScreen::ambianceName, sceneAmbiance);
		String currentStadium = sceneStadium == null ? "" : sceneStadium;
		stadium = new Ui.Dropdown<>(stadiums(currentStadium), NpcEditScreen::stadiumName, currentStadium);
		String currentField = npc.field() == null ? "" : npc.field();
		List<String> fields = new ArrayList<>();
		fields.add("");
		fields.addAll(com.mystaria.events.reborn.LeagueReborn.fieldIds());
		if (!fields.contains(currentField)) {
			fields.add(currentField);
		}
		field = new Ui.Dropdown<>(fields, id -> id.isEmpty() ? Component.translatable("phantasmon_events.npc.field_none")
				: com.mystaria.events.reborn.LeagueReborn.fieldName(id), currentField);
	}

	/**
	 * The battle formats, as the lobby's format picker lists them (2026-11-27): one header ("#...") per column and
	 * sub-group — Singles (National Dex, Gen 9, Champions), Doubles, Random, Others — the backend's catalogue order.
	 */
	static List<String[]> formats() {
		List<String[]> all = new ArrayList<>();
		for (String[] entry : CATALOGUE) {
			if (entry[0].startsWith("#")) {
				String[] keys = entry[1].split("\\|");
				all.add(new String[] { entry[0], Component.translatable("phantasmon_events.npc.format_group_" + keys[0]).getString()
						+ " · " + Component.translatable("phantasmon_events.npc.format_sub_" + keys[1]).getString() });
			} else if ("free".equals(entry[0])) {
				all.add(new String[] { "free", Component.translatable("phantasmon_events.npc.format_free").getString() });
			} else {
				all.add(entry);
			}
		}
		return all;
	}

	/** Headers: "#id", "column|sub-group" (lang keys); formats: Showdown id, short name. */
	private static final List<String[]> CATALOGUE = List.of(
			new String[] { "#singles_natdex", "singles|natdex" },
			new String[] { "gen9nationaldex", "NatDex OU" },
			new String[] { "gen9nationaldexubers", "NatDex Ubers" },
			new String[] { "gen9nationaldexmonotype", "NatDex Monotype" },
			new String[] { "gen9nationaldexuu", "NatDex UU" },
			new String[] { "gen9nationaldexru", "NatDex RU" },
			new String[] { "gen9nationaldexlc", "NatDex LC" },
			new String[] { "gen9nationaldex1v1", "NatDex 1v1" },
			new String[] { "gen9nationaldexag", "NatDex AG" },
			new String[] { "gen9nationaldexmonotypeubers", "NatDex Monotype Ubers" },
			new String[] { "gen9nationaldexubersuu", "NatDex Ubers UU" },
			new String[] { "#singles_gen9", "singles|gen9" },
			new String[] { "gen9ou", "OU" },
			new String[] { "gen9anythinggoes", "Anything Goes" },
			new String[] { "gen9ubers", "Ubers" },
			new String[] { "gen9monotype", "Monotype" },
			new String[] { "gen9uu", "UU" },
			new String[] { "gen91v1", "1v1" },
			new String[] { "gen9ru", "RU" },
			new String[] { "gen9pu", "PU" },
			new String[] { "gen9nu", "NU" },
			new String[] { "gen9lc", "LC" },
			new String[] { "gen9zu", "ZU" },
			new String[] { "gen9bssregi", "BSS Reg I (3 of 6)" },
			new String[] { "gen9ubersuu", "Ubers UU" },
			new String[] { "gen9lcuu", "LC UU" },
			new String[] { "#singles_champions", "singles|champions" },
			new String[] { "gen9championsnationaldex", "Champions NatDex" },
			new String[] { "gen9championsou", "Champions OU" },
			new String[] { "gen9championsbssregmc", "Champions BSS (3 of 6)" },
			new String[] { "gen9championsuu", "Champions UU" },
			new String[] { "#doubles_natdex", "doubles|natdex" },
			new String[] { "gen9nationaldexdoubles", "NatDex Doubles" },
			new String[] { "gen9nationaldexdoublesubers", "NatDex Doubles Ubers" },
			new String[] { "#doubles_gen9", "doubles|gen9" },
			new String[] { "gen9doublesou", "Doubles OU" },
			new String[] { "gen9doublesubers", "Doubles Ubers" },
			new String[] { "gen9doublesuu", "Doubles UU" },
			new String[] { "gen92v2doubles", "2v2 Doubles" },
			new String[] { "gen9vgc2024regg", "VGC 2024 Reg G (4 of 6)" },
			new String[] { "gen9vgc2025regi", "VGC 2025 Reg I (4 of 6)" },
			new String[] { "gen9doubleslc", "Doubles LC" },
			new String[] { "#doubles_champions", "doubles|champions" },
			new String[] { "gen9championsvgc2026regmc", "Champions VGC (4 of 6)" },
			new String[] { "gen9championsdoubles", "Champions Doubles" },
			new String[] { "#random_singles", "random|singles" },
			new String[] { "gen9randombattle", "Random Battle" },
			new String[] { "gen9monotyperandombattle", "Monotype Random Battle" },
			new String[] { "gen9hackmonscup", "Hackmons Cup" },
			new String[] { "#random_doubles", "random|doubles" },
			new String[] { "gen9randomdoublesbattle", "Random Doubles Battle" },
			new String[] { "gen9doubleshackmonscup", "Doubles Hackmons Cup" },
			new String[] { "#other_free", "other|free" },
			new String[] { "free", "" },
			new String[] { "#other_natdex", "other|natdex" },
			new String[] { "gen9nationaldexaaa", "NatDex AAA" },
			new String[] { "gen9nationaldexstabmons", "NatDex STABmons" },
			new String[] { "gen9nationaldexmonoletter", "NatDex Monoletter" },
			new String[] { "#other_gen9", "other|gen9" },
			new String[] { "gen9balancedhackmons", "Balanced Hackmons" },
			new String[] { "gen9almostanyability", "Almost Any Ability" },
			new String[] { "gen9stabmons", "STABmons" },
			new String[] { "gen9monocolor", "Monocolor" },
			new String[] { "gen9nfe", "NFE" });

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

	/** "" (the player's pick), then Showtime's presets and ambiances, reserved ones and resource packs' included. */
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

	/** "" (the preset's own, else the player's), "none" (the world as it is), then every Showtime stadium. */
	static List<String> stadiums(String current) {
		List<String> all = new ArrayList<>();
		all.add("");
		all.add(com.mystaria.showtime.ambiance.Ambiances.FIELD_STADIUM);
		all.add(com.mystaria.showtime.ambiance.Ambiances.NONE);
		all.addAll(com.mystaria.showtime.ambiance.Ambiances.stadiumIds());
		if (!all.contains(current)) {
			all.add(current);
		}
		return all;
	}

	static Component stadiumName(String id) {
		if (com.mystaria.showtime.ambiance.Ambiances.FIELD_STADIUM.equals(id)) {
			return Component.translatable("phantasmon_events.npc.stadium_field");
		}
		return id.isEmpty() ? Component.translatable("phantasmon_events.npc.stadium_auto")
				: com.mystaria.showtime.ambiance.Ambiances.stadiumName(id);
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
		return 314;
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
		ui.dropdown(g, field, x, row, 278, 15, Component.translatable("phantasmon_events.npc.field"));
		row += 24;
		// Showtime staging
		section(g, "phantasmon_events.npc.section.show", x, row, w);
		row += 24;
		int showThird = (w - 16) / 3;
		ui.dropdown(g, intro, x, row, showThird, 15, Component.translatable("phantasmon_events.npc.intro"));
		ui.dropdown(g, stadium, x + showThird + 8, row, showThird, 15, Component.translatable("phantasmon_events.npc.stadium"));
		ui.dropdown(g, ambiance, x + 2 * (showThird + 8), row, w - 2 * (showThird + 8), 15, Component.translatable("phantasmon_events.npc.ambiance"));
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

	/** A drop-down list's scrollbar taken by {@link Ui#click} follows the mouse (2026-11-20: only the wheel moved it). */
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		return button == 0 && ui.drag(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		ui.release();
		return super.mouseReleased(mouseX, mouseY, button);
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
		body.put("ambiance", com.mystaria.showtime.ambiance.Ambiances.scene(ambiance.value,
				stadium.value.isEmpty() ? null : stadium.value));
		body.put("field", field.value);
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
							npc.ai(), npc.formatId(), npc.teamSize(), npc.teamSpecies(), npc.hub(), npc.aiDebug(), npc.intro(), npc.ambiance(),
							npc.field());
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
