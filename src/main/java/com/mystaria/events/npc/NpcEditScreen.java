package com.mystaria.events.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.events.tournament.TournamentFormScreen;
import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * Admin: an event's trainer NPC (Adrien 2026-11-02) — its name, skin (a Minecraft account's), event, AI level and
 * battle format; its team copied from the admin's Ghost team or Cobblemon party; moved where the admin stands; its
 * event opened or closed; tried out (a battle against it, even closed); deleted.
 */
public final class NpcEditScreen extends Screen {

	static final List<String> AIS = List.of("random", "easy", "normal", "hard", "veryhard", "expert");

	private EventNpcDto npc;
	private EditBox name;
	private EditBox skin;
	private EditBox event;
	private String ai;
	private String[] format;
	private boolean aiDebug;
	private Component message;

	/** The NPC last opened in the editor: {@code /phantasmon event npc move} moves it. */
	static java.util.UUID lastEdited;

	public NpcEditScreen(EventNpcDto npc) {
		super(Component.translatable("phantasmon_events.npc.edit_title"));
		this.npc = npc;
		lastEdited = npc.uuid();
		this.ai = AIS.contains(npc.ai()) ? npc.ai() : "normal";
		this.aiDebug = npc.aiDebug();
		this.format = formats().stream().filter(f -> f[0].equals(npc.formatId())).findFirst().orElse(formats().get(0));
	}

	static List<String[]> formats() {
		List<String[]> all = new ArrayList<>();
		all.add(new String[] { "free", Component.translatable("phantasmon_events.npc.format_free").getString() });
		all.addAll(TournamentFormScreen.FORMATS);
		return all;
	}

	static Component aiName(String ai) {
		return Component.translatable("phantasmon_events.npc.ai." + ai);
	}

	@Override
	protected void init() {
		int left = width / 2 - 155;
		int y = 36;
		name = box(left, y, 152, 32, npc.name(), "phantasmon_events.npc.name");
		skin = box(left + 158, y, 152, 16, npc.skin() == null ? "" : npc.skin(), "phantasmon_events.npc.skin");
		y += 34;
		event = box(left, y, 152, 32, npc.event(), "phantasmon_events.npc.event");
		addRenderableWidget(CycleButton.<String>builder(NpcEditScreen::aiName).withValues(AIS).withInitialValue(ai)
				.create(left + 158, y + 10, 152, 20, Component.translatable("phantasmon_events.npc.ai"), (b, value) -> ai = value));
		y += 34;
		addRenderableWidget(CycleButton.<String[]>builder(value -> Component.literal(value[1])).withValues(formats()).withInitialValue(format)
				.create(left, y, 196, 20, Component.translatable("phantasmon_events.npc.format"), (b, value) -> format = value));
		// AI debug: its reasoning in the chat of the player battling it, turn by turn (the Expert AI).
		addRenderableWidget(CycleButton.onOffBuilder(aiDebug)
				.create(left + 202, y, 108, 20, Component.translatable("phantasmon_events.npc.ai_debug"), (b, value) -> aiDebug = value));
		y += 26;
		addRenderableWidget(Button.builder(Component.translatable("phantasmon_events.npc.save"), b -> save()).bounds(left, y, 152, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("phantasmon_events.npc.move_here"), b -> moveHere()).bounds(left + 158, y, 152, 20).build());
		y += 34;
		addRenderableWidget(Button.builder(Component.translatable("phantasmon_events.npc.team_ghost"), b -> team(Map.of("team", "GHOST"))).bounds(left, y, 152, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("phantasmon_events.npc.team_cobblemon"), b -> {
			Map<String, Object> party = PhantasmonApi.cobblemonParty();
			if (party == null) {
				message = Component.translatable("phantasmon_events.npc.no_party").withStyle(ChatFormatting.RED);
			} else {
				team(party);
			}
		}).bounds(left + 158, y, 152, 20).build());
		y += 34;
		addRenderableWidget(Button.builder(Component.translatable(npc.open() ? "phantasmon_events.npc.close_event" : "phantasmon_events.npc.open_event",
				npc.event()), b -> setOpen(!npc.open())).bounds(left, y, 152, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("phantasmon_events.npc.try"), b -> {
			onClose();
			Minecraft.getInstance().setScreen(new NpcChallengeScreen(npc));
		}).bounds(left + 158, y, 152, 20).build());
		y += 26;
		addRenderableWidget(Button.builder(Component.translatable("phantasmon_events.npc.delete").withStyle(ChatFormatting.RED), b ->
				PhantasmonApi.delete("/admin/events/npcs/" + npc.uuid(), Map.class)
						.thenAccept(ignored -> Minecraft.getInstance().execute(this::onClose))
						.exceptionally(this::failed)).bounds(left, y, 152, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 158, y, 152, 20).build());
	}

	private EditBox box(int x, int y, int w, int max, String value, String label) {
		EditBox box = addRenderableWidget(new EditBox(font, x, y + 10, w, 20, Component.translatable(label)));
		box.setMaxLength(max);
		box.setValue(value);
		return box;
	}

	private void save() {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name.getValue().strip());
		body.put("skin", skin.getValue().strip());
		body.put("event", event.getValue().strip().toLowerCase(Locale.ROOT));
		body.put("ai", ai);
		body.put("format_id", format[0]);
		body.put("ai_debug", aiDebug);
		post("/admin/events/npcs/" + npc.uuid(), body, "phantasmon_events.npc.saved");
	}

	private void moveHere() {
		Map<String, Object> body = here();
		if (body != null) {
			post("/admin/events/npcs/" + npc.uuid(), body, "phantasmon_events.npc.moved");
		}
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
		PhantasmonApi.post("/admin/events/" + npc.event() + "/open", Map.of("open", open), Map.class)
				.thenAccept(ignored -> Minecraft.getInstance().execute(() -> {
					npc = new EventNpcDto(npc.uuid(), npc.event(), open, npc.name(), npc.skin(), npc.x(), npc.y(), npc.z(), npc.yaw(),
							npc.ai(), npc.formatId(), npc.teamSize(), npc.teamSpecies(), npc.hub(), npc.aiDebug());
					message = Component.translatable(open ? "phantasmon_events.npc.event_opened" : "phantasmon_events.npc.event_closed", npc.event())
							.withStyle(ChatFormatting.GREEN);
					rebuildWidgets();
				}))
				.exceptionally(this::failed);
	}

	private void post(String path, Map<String, Object> body, String done) {
		PhantasmonApi.post(path, body, EventNpcDto.class)
				.thenAccept(updated -> Minecraft.getInstance().execute(() -> {
					npc = updated;
					message = Component.translatable(done).withStyle(ChatFormatting.GREEN);
				}))
				.exceptionally(this::failed);
	}

	private Void failed(Throwable failure) {
		Minecraft.getInstance().execute(() -> message = errorMessage(failure));
		return null;
	}

	static Component errorMessage(Throwable failure) {
		String code = PhantasmonApi.errorCode(failure);
		return (code == null ? Component.translatable("phantasmon_events.error.network")
				: Component.translatableWithFallback("phantasmon_events.error." + code.toLowerCase(Locale.ROOT), code)).withStyle(ChatFormatting.RED);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int left = width / 2 - 155;
		g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
		g.drawString(font, Component.translatable("phantasmon_events.npc.name"), left, 36, 0xFFA0A0A0);
		g.drawString(font, Component.translatable("phantasmon_events.npc.skin"), left + 158, 36, 0xFFA0A0A0);
		g.drawString(font, Component.translatable("phantasmon_events.npc.event"), left, 70, 0xFFA0A0A0);
		List<String> species = npc.teamSpecies() == null ? List.of() : npc.teamSpecies();
		Component team = species.isEmpty() ? Component.translatable("phantasmon_events.npc.no_team").withStyle(ChatFormatting.GOLD)
				: Component.translatable("phantasmon_events.npc.team", String.join(", ", species));
		g.drawString(font, team, left, 158, 0xFFFFFFFF);
		if (message != null) {
			g.drawCenteredString(font, message, width / 2, height - 24, 0xFFFFFFFF);
		}
	}
}
