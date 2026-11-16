package com.mystaria.events.trahison.world;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;

import com.mystaria.events.trahison.edit.LayoutDto;
import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * The fixed plan of the hub the player is in, at the version the running game uses ({@code layout_version} of the view):
 * fetched once when a view brings a version we do not hold ({@code GET /events/trahison/layout/{hub}?version=n}), and
 * shared by {@link Zones} and {@link FakeBlocks}. Hub version 0 means the hub had no plan when the game started: an empty
 * one. The plan has no secret. Client thread.
 */
public final class Plan {

	private static final Logger LOG = LoggerFactory.getLogger(Plan.class);

	private static String hub;
	private static int version = -1;
	private static LayoutDto layout;
	/** The (hub, version) being fetched, so a view per second does not start a fetch per second. */
	private static String fetching;
	private static long failedAt;
	private static final long RETRY_MS = 5000;

	private Plan() {
	}

	/** The plan held for this hub and version, or null while it is not here (yet). */
	public static LayoutDto get(String forHub, int forVersion) {
		return layout != null && forVersion == version && forHub != null && forHub.equals(hub) ? layout : null;
	}

	/** Makes sure the plan of this hub at this version is held or on its way. */
	public static void ensure(String forHub, int forVersion) {
		if (forHub == null || forHub.isEmpty() || forVersion < 0 || get(forHub, forVersion) != null) {
			return;
		}
		if (forVersion == 0) {
			hub = forHub;
			version = 0;
			layout = new LayoutDto().normalized();
			return;
		}
		String key = forHub + "#" + forVersion;
		if (key.equals(fetching) || System.currentTimeMillis() - failedAt < RETRY_MS) {
			return;
		}
		fetching = key;
		PhantasmonApi.get("/events/trahison/layout/" + URLEncoder.encode(forHub, StandardCharsets.UTF_8) + "?version=" + forVersion,
				LayoutDto.class).whenComplete((loaded, failure) -> Minecraft.getInstance().execute(() -> {
					if (!key.equals(fetching)) {
						return; // another one was asked for meanwhile
					}
					fetching = null;
					if (failure != null || loaded == null) {
						failedAt = System.currentTimeMillis();
						LOG.warn("Could not fetch the plan of hub {} (version {})", forHub, forVersion, failure);
						return;
					}
					hub = forHub;
					version = forVersion;
					layout = loaded.normalized();
				}));
	}

	/** Left the hub or the game: forgets the plan. */
	public static void clear() {
		hub = null;
		version = -1;
		layout = null;
		fetching = null;
	}
}
