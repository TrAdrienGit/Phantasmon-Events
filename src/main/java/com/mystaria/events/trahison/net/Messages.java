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

	private static final java.util.Set<String> ROOM_ACTIONS = java.util.Set.of("use_slot", "pick_up", "give_item", "use_item", "combine", "replay");

	/** The room the next room actions are aimed at, when it is not the one the player stands in (see {@link #inRoom}). */
	private static Integer forcedGroup;
	/** The round of the forced room (0: the current one, nothing to say). */
	private static int forcedRound;

	/** Runs {@code actions}, every room action of which goes to room {@code group} (the object clicked, not the room the player stands in). */
	public static void inRoom(int group, Runnable actions) {
		inRoom(0, group, actions);
	}

	/** As above, for the room of group {@code group} in round {@code round} (D8: rooms of earlier rounds; 0: the current round). */
	public static void inRoom(int round, int group, Runnable actions) {
		Integer before = forcedGroup;
		int roundBefore = forcedRound;
		forcedGroup = group;
		forcedRound = round;
		try {
			actions.run();
		} finally {
			forcedGroup = before;
			forcedRound = roundBefore;
		}
	}

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

	/** VOTE: locks the cabin the player stands in (his own) now, without waiting for his partner; the backend refuses otherwise. */
	public static void closeCabin() {
		send(ACTION, "close_cabin", null, true);
	}

	public static void vote(boolean ally) {
		send(ACTION, "vote", Map.of("vote", ally ? "ally" : "betray"), true);
	}

	/** D102: registers (or, with false, unregisters) this account as a spectator of the hub's game. */
	public static void spectate(boolean on) {
		send(ACTION, "spectate", Map.of("on", on), true);
	}

	/** T7.2: searches the hiding spot {@code spot} of the plan (the backend checks the 3 blocks and the stage). */
	public static void search(String spot) {
		send(ACTION, "search", Map.of("spot", spot), true);
	}

	/** T7.2: uses a secret item. {@code target}: a seat (knife, bonds, letter); {@code where}: a door or cabin id (lock, pass, ear); {@code text}: the letter. */
	public static void useObject(String item, Integer target, String where, String text) {
		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("item", item);
		if (target != null) {
			extra.put("target", target);
		}
		if (where != null) {
			extra.put("door", where);
		}
		if (text != null) {
			extra.put("text", text);
		}
		send(ACTION, "use_object", extra, true);
	}

	/** T7.2: reopens a door locked with the lock, from the outside. */
	public static void unlock(String door) {
		send(ACTION, "unlock", Map.of("door", door), true);
	}

	/** T7.2: starts untying a tied player, then finishes it after the waiting time (the second call frees him). */
	public static void untie(int target) {
		send(ACTION, "untie", Map.of("target", target), true);
	}

	/** T7.1, the seer (D144): learns the vote already cast from one closed cabin. */
	public static void foresee(String cabin) {
		send(ACTION, "foresee", Map.of("cabin", cabin), true);
	}

	/** T7.1, the gambler: bets on the vote of the opposite side of his group. */
	public static void bet(boolean ally) {
		send(ACTION, "bet", Map.of("vote", ally ? "ally" : "betray"), true);
	}

	/** T6: takes the bracelet off the body of player {@code body}; the backend checks the zone and the 3 blocks. */
	public static void takeBracelet(int body) {
		send(ACTION, "take_bracelet", Map.of("body", body), true);
	}

	/** T6: this client saw the body of player {@code body} (zone, under 8 blocks, line of sight); the backend then tells us of the death. */
	public static void bodySeen(int body) {
		send(ACTION, "body_seen", Map.of("body", body), false);
	}

	public static void openFinalDoor() {
		send(ACTION, "open_final_door", null, true);
	}

	/** Provisional action of step T1 (the simulated room). */
	public static void debugSolve() {
		send(ACTION, "debug_solve", null, true);
	}

	// ---------------------------------------------------------------- room actions (T3.3)

	/** {@code replay}: free time only (D8), puts the room the player stands in back to a fresh puzzle state, new solutions, no points. */
	public static void replay() {
		send(ACTION, "replay", null, true);
	}

	/** {@code use_slot}: uses an active slot; a puzzle bound to it opens its screen ({@code EventsTrahisonPuzzle}, {@code open}). */
	public static void useSlot(String slot) {
		send(ACTION, "use_slot", Map.of("slot", slot), true);
	}

	/** {@code pick_up}: takes the items shown in a slot into the player's bag. */
	public static void pickUp(String slot) {
		send(ACTION, "pick_up", Map.of("slot", slot), true);
	}

	/** {@code give_item}: hands an item the player carries to the group member {@code to} (a player index). */
	public static void giveItem(String item, int to) {
		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("item", item);
		extra.put("to", to);
		send(ACTION, "give_item", extra, true);
	}

	/** {@code use_item}: uses an item the player carries on an active slot. */
	public static void useItem(String item, String slot) {
		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("item", item);
		extra.put("slot", slot);
		send(ACTION, "use_item", extra, true);
	}

	/** {@code combine}: combines two items the player carries, following a recipe of the room. */
	public static void combine(String a, String b) {
		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("a", a);
		extra.put("b", b);
		send(ACTION, "combine", extra, true);
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

	/** Draws the nine rooms again (T4.1); before round 1. */
	public static void redraw() {
		send(GM, "redraw", null, true);
	}

	/** Puts another room of the bank in the place of a drawn one (T4.1). */
	public static void replaceRoom(int round, int group, String room) {
		send(GM, "replace_room", Map.of("round", round, "group", group, "room", room), true);
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

	/** Opens or closes a door of the plan by hand; it holds until the stage changes. */
	public static void setDoor(String door, boolean open) {
		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("door", door);
		extra.put("open", open);
		send(GM, "set_door", extra, true);
	}

	/** D7: an admin gives ({@code on}) or takes back the game master role of an account, in the lobby. */
	public static void setGameMaster(java.util.UUID account, boolean on) {
		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("account", account.toString());
		extra.put("on", on);
		send(GM, "set_game_master", extra, true);
	}

	/** T7.4: a private line to one seat, from the hostess. */
	public static void whisper(int seat, String text) {
		send(GM, "whisper", Map.of("seat", seat, "text", text), true);
	}

	/** T7.4: a line to everybody, from the hostess. */
	public static void say(String text) {
		send(GM, "say", Map.of("text", text), true);
	}

	/** T7.3: hands the cabin offer to a seat now (during a vote). */
	public static void offer(int seat) {
		send(GM, "offer", Map.of("seat", seat), true);
	}

	/** T7.4: the next help text of the room of a group. */
	public static void hint(int group) {
		send(GM, "hint", Map.of("group", group), true);
	}

	/** T7.4: opens the exit of a stuck room (no point lost). */
	public static void solveRoom(int group) {
		send(GM, "solve_room", Map.of("group", group), true);
	}

	/** T7.4: repairs a mistake: a death. */
	public static void kill(int seat) {
		send(GM, "kill", Map.of("seat", seat), true);
	}

	/** T7.4: repairs a mistake: brings a dead player back. */
	public static void revive(int seat) {
		send(GM, "revive", Map.of("seat", seat), true);
	}

	/** T7.4: changes some settings of the lobby ({@code offers}, {@code leaks}, {@code race}, {@code announce_deaths}, {@code role_count}, {@code mode}...). */
	public static void setSettings(Map<String, Object> changes) {
		send(GM, "set_settings", changes, true);
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
		if (ROOM_ACTIONS.contains(action)) {
			// Free time (D8): the room acted on may be another trio's; the backend refuses it outside the free time.
			com.mystaria.events.trahison.room.RoomView room = com.mystaria.events.trahison.room.Rooms.current();
			if (forcedGroup != null) {
				data.put("group", forcedGroup);
				if (forcedRound > 0) {
					data.put("round", forcedRound);
				}
			} else if (room != null) {
				data.put("group", room.group());
				if (room.round() > 0) {
					data.put("round", room.round());
				}
			}
		}
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
