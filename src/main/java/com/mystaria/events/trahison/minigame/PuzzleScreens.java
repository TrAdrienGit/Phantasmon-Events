package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * Router of the mini-game screens. Maps a mechanism id to the factory of its screen, receives
 * {@code EventsTrahisonPuzzle} (and {@code EventsTrahisonView}, to know the group and its players) from
 * {@link PhantasmonApi#onEventsMessage}, opens the screen on {@code reason: open}, forwards the other messages to the
 * screen that is open on the same step, and keeps the latest state of every step so that reopening a screen restores
 * it. Everything runs on the client thread.
 */
public final class PuzzleScreens {

	/** Builds the screen of one mechanism; {@code offline} is the debug mode with no backend. */
	public interface Factory {
		MiniGameScreen create(String step, boolean offline);
	}

	/** A player of the game as the latest view lists it. {@code uuid} may be null (unknown). */
	public record Player(int index, String name, UUID uuid) {
	}

	/** A parsed {@code EventsTrahisonPuzzle}. {@code lastPlayer} is -1 when {@code last} is null. */
	public record Msg(String hub, int round, int group, String step, String mechanism, String reason, boolean solved,
			Map<String, Object> view, int lastPlayer, List<Map<String, Object>> lastEvents) {
	}

	public static final String PUZZLE = "EventsTrahisonPuzzle";
	public static final String VIEW = "EventsTrahisonView";

	private static final Map<String, Factory> FACTORIES = new LinkedHashMap<>();
	/** Screens that exist only for the offline debug command ({@code /phantasmon event trahison minigame}): the mechanism is played in the world. */
	private static final Map<String, Factory> PREVIEWS = new LinkedHashMap<>();
	private static final Map<String, Msg> STATES = new HashMap<>();
	/** When each state of {@link #STATES} arrived (local milliseconds): lets world objects count a lease down from it. */
	private static final Map<String, Long> STAMPS = new HashMap<>();
	private static final Map<Integer, Player> PLAYERS = new HashMap<>();
	private static final List<Integer> GROUP_MEMBERS = new ArrayList<>();
	private static String hub = "";
	private static int ownIndex = -1;
	private static int group = -1;
	/** Free time (D8): every room can be visited, so a puzzle message of another group is for us too. */
	private static boolean freeTime;

	static {
		register("three_dials", ThreeDialsScreen::new);
		register("debug_button", DebugButtonScreen::new);
		register("ring_dial", RingDialScreen::new);
		register("keypad", KeypadScreen::new);
		register("pickup_set", PickupSetScreen::new);
		register("code_breaker", CodeBreakerScreen::new);
		register("wiring", WiringScreen::new);
		// three_levers is played in the world (a click on a lever slot): no screen opens for it, only a preview offline.
		PREVIEWS.put("three_levers", ThreeLeversScreen::new);
	}

	private PuzzleScreens() {
	}

	/** Adds a mechanism's screen. Called from here for the built-in ones; later mechanisms add one line. */
	public static void register(String mechanism, Factory factory) {
		FACTORIES.put(mechanism, factory);
	}

	/** The mechanisms the offline debug command can show: those with a screen, and the previews of those played in the world. */
	public static Set<String> mechanisms() {
		Set<String> all = new java.util.TreeSet<>(FACTORIES.keySet());
		all.addAll(PREVIEWS.keySet());
		return all;
	}

	/** Hooks the router to the add-on message stream; called once by {@code Trahison.register()}. */
	public static void install() {
		PhantasmonApi.onEventsMessage((type, data) -> {
			if (PUZZLE.equals(type)) {
				Minecraft.getInstance().execute(() -> onPuzzle(data));
			} else if (VIEW.equals(type)) {
				Minecraft.getInstance().execute(() -> onView(data));
			}
		});
	}

	/** Opens a mechanism's screen with no backend: gestures apply locally only (debug command). */
	public static boolean openOffline(String mechanism) {
		Factory factory = FACTORIES.getOrDefault(mechanism, PREVIEWS.get(mechanism));
		if (factory == null) {
			return false;
		}
		Minecraft.getInstance().setScreen(factory.create("debug", true));
		return true;
	}

	/** Index of the local player in the game, or -1 when unknown. */
	public static int ownIndex() {
		return ownIndex;
	}

	public static Player player(int index) {
		return PLAYERS.get(index);
	}

	/** Indices of the local player's group, from the latest view (empty when unknown). */
	public static List<Integer> groupMembers() {
		return GROUP_MEMBERS;
	}

	// ---------------------------------------------------------------- messages

	private static void onView(Map<String, Object> data) {
		String phase = asString(data.get("phase"), "");
		freeTime = "FREE_TIME".equals(phase);
		hub = asString(data.get("hub"), hub);
		if (phase.isEmpty() || "NONE".equals(phase) || "ENDED".equals(phase)) {
			STATES.clear();
			STAMPS.clear();
			PLAYERS.clear();
			GROUP_MEMBERS.clear();
			ownIndex = -1;
			group = -1;
			return;
		}
		if ("DOORS".equals(phase)) {
			// A new round: the rooms of the last one are gone and nothing says so, so what we hold of them goes too.
			STATES.clear();
			STAMPS.clear();
			if (Minecraft.getInstance().screen instanceof MiniGameScreen) {
				Minecraft.getInstance().setScreen(null);
			}
		}
		PLAYERS.clear();
		for (Object o : asList(data.get("players"))) {
			Map<String, Object> p = asMap(o);
			int index = asInt(p.get("index"), -1);
			UUID uuid = null;
			try {
				uuid = UUID.fromString(asString(p.get("uuid"), ""));
			} catch (IllegalArgumentException ignored) {
				// bots and unknown players have no usable id: their head is the default skin
			}
			if (index >= 0) {
				PLAYERS.put(index, new Player(index, asString(p.get("name"), "#" + (index + 1)), uuid));
			}
		}
		Map<String, Object> viewer = asMap(data.get("viewer"));
		ownIndex = asInt(viewer.get("index"), -1);
		group = asInt(viewer.get("group"), -1);
		GROUP_MEMBERS.clear();
		// groups[] has one element per door; the group number is its position in the list.
		List<Object> groups = asList(data.get("groups"));
		if (group >= 0 && group < groups.size()) {
			Map<String, Object> mine = asMap(groups.get(group));
			for (Object member : asList(mine.get("pair"))) {
				GROUP_MEMBERS.add(asInt(member, -1));
			}
			int solo = asInt(mine.get("solo"), -1);
			if (solo >= 0) {
				GROUP_MEMBERS.add(solo);
			}
			GROUP_MEMBERS.removeIf(i -> i < 0);
		}
	}

	private static void onPuzzle(Map<String, Object> data) {
		Msg m = parse(data);
		if (m == null || (group >= 0 && m.group() != group && !freeTime) || (!hub.isEmpty() && !m.hub().isEmpty() && !hub.equals(m.hub()))) {
			return;
		}
		STATES.put(key(m.hub(), m.round(), m.group(), m.step()), m);
		STAMPS.put(key(m.hub(), m.round(), m.group(), m.step()), System.currentTimeMillis());
		if ("three_levers".equals(m.mechanism())) {
			com.mystaria.events.trahison.world.LeverState.onMessage(m);
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof MiniGameScreen open && open.step().equals(m.step()) && (open.group() < 0 || open.group() == m.group() && open.round() == m.round())) {
			open.onPuzzle(m);
			return;
		}
		if ("open".equals(m.reason())) {
			Factory factory = FACTORIES.get(m.mechanism());
			if (factory != null) {
				MiniGameScreen screen = factory.create(m.step(), false);
				screen.aimAt(m.round(), m.group());
				screen.onPuzzle(m);
				mc.setScreen(screen);
			}
		}
	}

	/** The latest state of a step, or null: lets a screen opened later start from it. */
	public static Msg lastState(String step) {
		for (Msg m : STATES.values()) {
			if (m.step().equals(step)) {
				return m;
			}
		}
		return null;
	}

	/** The latest state of a step of the room (round, group), with the local time it arrived, or null. World objects use it. */
	public static Map.Entry<Msg, Long> lastState(int round, int group, String step) {
		for (Map.Entry<String, Msg> e : STATES.entrySet()) {
			Msg m = e.getValue();
			if (m.step().equals(step) && m.group() == group && Math.max(0, m.round()) == Math.max(0, round)) {
				return Map.entry(m, STAMPS.getOrDefault(e.getKey(), 0L));
			}
		}
		return null;
	}

	private static String key(String hub, int round, int group, String step) {
		return hub + "|" + round + "|" + group + "|" + step;
	}

	private static Msg parse(Map<String, Object> data) {
		String step = asString(data.get("step"), null);
		String mechanism = asString(data.get("mechanism"), null);
		if (step == null || mechanism == null) {
			return null;
		}
		Map<String, Object> last = asMap(data.get("last"));
		List<Map<String, Object>> events = new ArrayList<>();
		for (Object e : asList(last.get("events"))) {
			events.add(asMap(e));
		}
		return new Msg(asString(data.get("hub"), ""), Math.max(0, asInt(data.get("round"), 0)), asInt(data.get("group"), -1), step, mechanism,
				asString(data.get("reason"), "input"), Boolean.TRUE.equals(data.get("solved")), asMap(data.get("view")),
				data.get("last") == null ? -1 : asInt(last.get("player"), -1), events);
	}

	// ---------------------------------------------------------------- JSON helpers (backend numbers arrive as Double)

	public static int asInt(Object o, int fallback) {
		return o instanceof Number n ? n.intValue() : fallback;
	}

	public static String asString(Object o, String fallback) {
		return o instanceof String s ? s : fallback;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> asMap(Object o) {
		return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
	}

	@SuppressWarnings("unchecked")
	public static List<Object> asList(Object o) {
		return o instanceof List<?> l ? (List<Object>) l : List.of();
	}
}
