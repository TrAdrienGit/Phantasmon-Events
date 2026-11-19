package com.mystaria.events.hub;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.HubManagerTab;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;
import com.mystaria.phantasmon.client.hub.HubCoordinates;
import com.mystaria.showtime.ambiance.Ambiances;
import com.mystaria.showtime.api.AmbianceArea;
import com.mystaria.showtime.api.ShowtimeHooks;

/**
 * Hub ambiances (2026-11-19): one Showtime ambiance per hub, laid on the hub itself — everyone standing in one of its
 * zones here sees it, battle or not, the same wherever they stand in it. Admins pick it in the hub manager's Events tab
 * ({@code POST /admin/events/hubs/{name}/ambiance}); the list ({@code GET /events/hubs/ambiances}) is read on joining
 * and again on each change ({@code EventsHubAmbiancesChanged}). Showtime asks {@link #area} twice a second and keeps
 * the ambiance fixed to the zone (on each of its floors), never to the player.
 */
public final class HubAmbiances {

	private static final Logger LOG = LoggerFactory.getLogger(HubAmbiances.class);
	/** Read again now and then (a missed broadcast): every 5 minutes. */
	private static final int REFRESH_TICKS = 6000;

	/** Hub name (lower case) -> ambiance id. */
	private static volatile Map<String, String> byHub = Map.of();
	private static ClientLevel level;
	private static int ticks;
	private static boolean refreshing;

	private HubAmbiances() {
	}

	public static void register() {
		PhantasmonApi.onEventsMessage((type, data) -> {
			if (type.equals("EventsHubAmbiancesChanged")) {
				Minecraft.getInstance().execute(HubAmbiances::refresh);
			}
		});
		ShowtimeHooks.setAreaAmbiance(HubAmbiances::area);
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != level) {
			level = mc.level;
			ticks = REFRESH_TICKS - 40; // soon after joining
		}
		if (level == null || !PhantasmonApi.isAuthenticated()) {
			return;
		}
		if (++ticks >= REFRESH_TICKS) {
			refresh();
		}
	}

	public static void refresh() {
		if (refreshing || !PhantasmonApi.isAuthenticated()) {
			return;
		}
		ticks = 0;
		refreshing = true;
		PhantasmonApi.get("/events/hubs/ambiances", Entry[].class).whenComplete((entries, error) -> {
			refreshing = false;
			if (error != null) {
				LOG.debug("Hub ambiances not listed", error);
				return;
			}
			Map<String, String> read = new HashMap<>();
			for (Entry entry : entries == null ? new Entry[0] : entries) {
				if (entry.hub() != null && entry.ambiance() != null && !entry.ambiance().isBlank()) {
					read.put(entry.hub().toLowerCase(Locale.ROOT), entry.ambiance());
				}
			}
			byHub = Map.copyOf(read);
		});
	}

	/** One hub's ambiance as the backend lists it. */
	public record Entry(String hub, String ambiance) {
	}

	/** A hub's ambiance id, or null. */
	public static String of(String hub) {
		return hub == null ? null : byHub.get(hub.toLowerCase(Locale.ROOT));
	}

	/** Told by the hub manager once an admin changed it: shown at once, before the broadcast comes back. */
	static void changed(String hub, String ambiance) {
		Map<String, String> next = new HashMap<>(byHub);
		if (ambiance == null || ambiance.isBlank()) {
			next.remove(hub.toLowerCase(Locale.ROOT));
		} else {
			next.put(hub.toLowerCase(Locale.ROOT), ambiance);
		}
		byHub = Map.copyOf(next);
	}

	/** The zone of a hub with an ambiance the local player stands in, here and now; null: none. */
	static AmbianceArea area() {
		Map<String, String> ambiances = byHub;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (ambiances.isEmpty() || player == null || mc.level == null) {
			return null;
		}
		List<HubAnchorDto> anchors = PhantasmonApi.anchorsHere();
		if (anchors == null) {
			return null;
		}
		String dimension = mc.level.dimension().location().toString();
		for (HubAnchorDto anchor : anchors) {
			String ambiance = anchor.hub() == null ? null : ambiances.get(anchor.hub().toLowerCase(Locale.ROOT));
			if (ambiance == null || anchor.dimension() != null && !anchor.dimension().equals(dimension)) {
				continue;
			}
			HubCoordinates zone = anchor.coordinates();
			if (zone.contains(player.getX(), player.getY(), player.getZ())) {
				return new AmbianceArea(ambiance, zone.originX(), zone.originZ(), zone.halfX(), zone.halfZ(), zone.yaw(),
						zone.originY(), zone.originY() + zone.sizeY());
			}
		}
		return null;
	}

	/**
	 * The choices the hub manager offers: none, then every ambiance without a place of its own (a hub is the place),
	 * Showtime's and the add-ons' (Phantasmon Events' own), reserved ones included: a hub's is everyone's.
	 */
	static List<HubManagerTab.Option> options() {
		List<HubManagerTab.Option> options = new java.util.ArrayList<>();
		options.add(new HubManagerTab.Option("", Component.translatable("phantasmon_events.hub_tab.ambiance_none")));
		List<String> ids = new java.util.ArrayList<>();
		for (String id : Ambiances.ids()) {
			if (Ambiances.isAmbiance(id) && !Ambiances.hasPlace(Ambiances.get(id))) {
				ids.add(id);
			}
		}
		ids.sort((a, b) -> Ambiances.displayName(a).getString().compareToIgnoreCase(Ambiances.displayName(b).getString()));
		for (String id : ids) {
			options.add(new HubManagerTab.Option(id, label(id)));
		}
		return options;
	}

	/** An ambiance's name; Phantasmon Events' own marked as such. */
	static Component label(String id) {
		Component name = Ambiances.displayName(id);
		return id.startsWith("phantasmon_events:")
				? Component.translatable("phantasmon_events.hub_tab.ambiance_events", name)
				: name;
	}
}
