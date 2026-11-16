package com.mystaria.events.trahison.world;

import java.util.LinkedHashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import com.mystaria.events.trahison.edit.LayoutDto;
import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.LayoutDto.Zone;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;

/**
 * Which zone of the plan the local player stands in (solution S2): computed every tick from the plan and the player's
 * position (hub coordinates through {@link HubBlocks}), and sent to the backend as {@code EventsTrahisonZone {zone, x, y,
 * z}} when it changes and at least every {@value #REFRESH_TICKS} ticks (2 s) during a game, so the backend knows where
 * the player (or his body) is. An empty zone means "in none". Only the players of the game send it. Zones never overlap,
 * so the first box that holds the player's block gives the zone. Client thread.
 */
public final class Zones {

	public static final String ZONE = "EventsTrahisonZone";
	private static final int REFRESH_TICKS = 40;

	private static String current = "";
	private static boolean sentOnce;
	private static int sinceSent;

	private Zones() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Zones::tick);
		GameView.addListener(Zones::onView);
	}

	/** The zone the player is in, "" for none, as last computed. */
	public static String current() {
		return current;
	}

	/** A view came: fetch the plan it uses if we do not hold it. */
	private static void onView() {
		View v = GameView.current();
		if (v == null || !v.hasGame()) {
			Plan.clear();
			sentOnce = false;
			return;
		}
		Plan.ensure(v.hub(), v.layoutVersion());
	}

	/** The id of the zone holding the block, or "". {@code hubX}, {@code hubZ}: hub block coordinates, {@code y}: the world's. */
	public static String zoneAt(LayoutDto plan, int hubX, int y, int hubZ) {
		for (Zone zone : plan.zones) {
			for (Box box : zone.boxes) {
				if (hubX >= box.minX && hubX <= box.maxX && y >= box.minY && y <= box.maxY && hubZ >= box.minZ && hubZ <= box.maxZ) {
					return zone.id;
				}
			}
		}
		return "";
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		View v = GameView.current();
		HubAnchorDto anchor = PhantasmonApi.hubs() == null ? null : PhantasmonApi.hubs().joinedAnchor();
		if (player == null || v == null || anchor == null || !playing(v) || !anchor.hub().equals(Link.hub())) {
			sentOnce = false;
			current = "";
			return;
		}
		LayoutDto plan = Plan.get(v.hub(), v.layoutVersion());
		if (plan == null) {
			return;
		}
		HubBlocks blocks = HubBlocks.of(anchor);
		double x = blocks.hubX(player.getX());
		double z = blocks.hubZ(player.getZ());
		String zone = zoneAt(plan, (int) Math.floor(x), (int) Math.floor(player.getY()), (int) Math.floor(z));
		sinceSent++;
		if (sentOnce && zone.equals(current) && sinceSent < REFRESH_TICKS) {
			return;
		}
		current = zone;
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("zone", zone);
		data.put("x", Math.round(x * 100) / 100.0);
		data.put("y", Math.round(player.getY() * 100) / 100.0);
		data.put("z", Math.round(z * 100) / 100.0);
		if (PhantasmonApi.send(ZONE, data)) {
			sentOnce = true;
			sinceSent = 0;
		}
	}

	/** Only a player of a game that is on reports his zone. */
	private static boolean playing(View v) {
		return v.hasGame() && v.phase() != Phase.ENDED && v.viewer().index() != null
				&& (v.viewer().kind() == GameView.ViewerKind.PLAYER || v.viewer().kind() == GameView.ViewerKind.GHOST);
	}
}
