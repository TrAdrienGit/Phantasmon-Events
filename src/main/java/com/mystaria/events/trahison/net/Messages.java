package com.mystaria.events.trahison.net;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.hud.Announcer;
import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * Sends the player's actions ({@code EventsTrahisonAction}) and the game master's ({@code EventsTrahisonGm}) to the
 * backend (Documentation/trahison/protocole.md, section 3). Each helper only builds the message: the backend decides, and
 * answers a refusal with an error line the {@link Announcer} shows. The {@code hub} field is the current mirror hub when
 * known. Client thread.
 */
public final class Messages {

	public static final String ACTION = "EventsTrahisonAction";
	public static final String GM = "EventsTrahisonGm";

	private Messages() {
	}

	// ---------------------------------------------------------------- players

	/** Asks for our view (and registers us as a viewer of the hub's game). Silent when not connected. */
	public static boolean view() {
		return send(ACTION, "view", null, false);
	}

	public static void join() {
		send(ACTION, "join", null, true);
	}

	public static void leave() {
		send(ACTION, "leave", null, true);
	}

	public static void passDoor(int door) {
		send(ACTION, "pass_door", Map.of("door", door), true);
	}

	public static void vote(boolean ally) {
		send(ACTION, "vote", Map.of("vote", ally ? "ally" : "betray"), true);
	}

	public static void openFinalDoor() {
		send(ACTION, "open_final_door", null, true);
	}

	/** Provisional action of step T1 (the simulated room). */
	public static void debugSolve() {
		send(ACTION, "debug_solve", null, true);
	}

	// ---------------------------------------------------------------- game master

	/** {@code mode}: {@code AUTO} or {@code GAME_MASTER}; {@code fast}: every step lasts 10 seconds. */
	public static void create(String mode, boolean fast) {
		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("mode", mode);
		if (fast) {
			extra.put("fast", true);
		}
		send(GM, "create", extra, true);
	}

	/** {@code count} 0 or less fills every free place. {@code policy}: {@code ally}, {@code betray}, {@code random}. */
	public static void addBots(int count, String policy) {
		Map<String, Object> extra = new LinkedHashMap<>();
		if (count > 0) {
			extra.put("count", count);
		}
		extra.put("policy", policy);
		send(GM, "add_bots", extra, true);
	}

	public static void setBotPolicy(String policy) {
		send(GM, "set_bot_policy", Map.of("policy", policy), true);
	}

	public static void start() {
		send(GM, "start", null, true);
	}

	public static void pause() {
		send(GM, "pause", null, true);
	}

	public static void resume() {
		send(GM, "resume", null, true);
	}

	public static void next() {
		send(GM, "next", null, true);
	}

	public static void end() {
		send(GM, "end", null, true);
	}

	public static void extend(int seconds) {
		send(GM, "extend", Map.of("seconds", seconds), true);
	}

	public static void setPoints(int bracelet, int points) {
		send(GM, "set_points", Map.of("bracelet", bracelet, "points", Math.max(0, points)), true);
	}

	// ---------------------------------------------------------------- plumbing

	/**
	 * Sends {@code {action, hub?, ...extra}}. {@code report}: tell the player when we are not connected (the automatic
	 * {@code view} stays silent and retries later).
	 */
	private static boolean send(String type, String action, Map<String, Object> extra, boolean report) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("action", action);
		String hub = Link.hub();
		if (hub != null) {
			data.put("hub", hub);
		}
		if (extra != null) {
			data.putAll(extra);
		}
		boolean sent = PhantasmonApi.send(type, data);
		if (!sent && report) {
			Announcer.error(Component.translatable("phantasmon_events.trahison.not_connected"));
		}
		return sent;
	}
}
