package com.mystaria.events.hub;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.HubManagerTab;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAdminDto;

/**
 * The "Hubs Event" tab of Phantasmon's hub manager (admins, Adrien 2026-11-10): Phantasmon Events' mirror hubs (D-38),
 * with what {@code /phantasmon event hub} does — create one (name, radius), grow or shrink its radius, pose its source
 * or a copy where the admin stands, show its limits, clean its ghost blocks. Rename, delete and the anchors are the
 * manager's own.
 */
public final class EventHubsTab implements HubManagerTab {

	private EventHubsTab() {
	}

	public static void register() {
		PhantasmonApi.registerHubManagerTab(new EventHubsTab());
	}

	@Override
	public Component title() {
		return Component.translatable("phantasmon_events.hub_tab.title");
	}

	@Override
	public boolean shows(HubAdminDto hub) {
		return "MIRROR".equals(hub.kind());
	}

	@Override
	public List<Component> details(HubAdminDto hub) {
		return List.of(Component.translatable("phantasmon_events.hub_tab.radius", hub.radius() == null ? "?" : hub.radius()));
	}

	@Override
	public List<Action> actions() {
		return List.of(
				new Action(Component.translatable("phantasmon_events.hub_tab.resize"), false,
						List.of(new Field(Component.translatable("phantasmon_events.hub_tab.field.delta"), Field.Kind.SIGNED_NUMBER)),
						(hub, values, done, status) -> {
							PhantasmonApi.hubs().adminResizeHub(hub.name(), Integer.parseInt(values.get(0)));
							later(done);
						}),
				new Action(Component.translatable("phantasmon_events.hub_tab.source"), false, (hub, done, status) -> {
					PhantasmonApi.hubs().createMirrorAnchor(hub.name(), false);
					later(done);
				}),
				new Action(Component.translatable("phantasmon_events.hub_tab.copy"), false, (hub, done, status) -> {
					PhantasmonApi.hubs().createMirrorAnchor(hub.name(), true);
					later(done);
				}),
				new Action(Component.translatable("phantasmon_events.hub_tab.limits"), false, (hub, done, status) ->
						PhantasmonApi.hubs().adminToggleDebug(hub.name())),
				new Action(Component.translatable("phantasmon_events.hub_tab.clean"), true, (hub, done, status) ->
						PhantasmonApi.delete("/admin/events/mirror/" + hub.name() + "/ghosts", Map.class)
								.thenAccept(result -> status.accept(Component.translatable("phantasmon_events.hub.cleaned", hub.name(),
										result != null && result.get("removed") instanceof Number n ? n.intValue() : 0)))
								.exceptionally(ex -> {
									status.accept(Component.translatable("phantasmon_events.hub.clean_failed", hub.name()));
									return null;
								})));
	}

	@Override
	public Action createAction() {
		return new Action(Component.translatable("phantasmon_events.hub_tab.create"), false,
				List.of(new Field(Component.translatable("phantasmon_events.hub_tab.field.name"), Field.Kind.NAME),
						new Field(Component.translatable("phantasmon_events.hub_tab.field.radius"), Field.Kind.NUMBER)),
				(hub, values, done, status) -> {
					PhantasmonApi.hubs().adminCreateMirrorHub(values.get(0), Integer.parseInt(values.get(1)));
					later(done);
				});
	}

	/** The commands answer in the chat, without telling when: the lists are read again a moment later. */
	private static void later(Runnable done) {
		CompletableFuture.delayedExecutor(800, TimeUnit.MILLISECONDS).execute(() -> Minecraft.getInstance().execute(done));
	}
}
