package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.events.trahison.room.Step;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Three levers in three corners of a room that must be held at the same time by three different players. Played in the
 * world: a click on a lever slot is a {@code onUse} with the role {@code lever_1}..{@code lever_3} (no screen).
 *
 * <h2>Holding is a lease</h2>
 * A click engages the lever for {@code hold} seconds; clicking it again while it is engaged (by the same player) renews the
 * lease. There is no "release" message: the client never has to keep a key down nor send a heartbeat, so a slow or jittery
 * WebSocket cannot drop a lever (the window is several seconds wide) and a lost message costs one click at most
 * (decisions.md, D40). The expiry is evaluated lazily, with the clock this bean holds, at the next gesture or view; the
 * client counts the remaining time down from the {@code ms} it receives.
 *
 * <h2>Rules</h2>
 * <ul>
 * <li>A player holds one lever at a time: engaging another one lets go of the first. So the three levers are held by three
 * distinct players, and a lone player can never solve it (unless {@code solo}, the editor's trial).</li>
 * <li>The seed draws the <em>order</em> in which the levers must be engaged (when {@code ordered}). Engaging a lever out of
 * order lets all of them go: event {@code wrong}, counted in {@code resets}. The order is never said: the players find it
 * by trying, and the progress light says how far they got.</li>
 * <li>The seed draws the <em>delay</em>: between two consecutive engagements at most {@code gap} seconds (a value in
 * {@code gap-2..gap}, at least 2). Too slow: all the levers let go, event {@code slow}, and the engagement counts as the
 * first of a new attempt.</li>
 * <li>The step is solved the moment the third lever is engaged while the two others still are.</li>
 * </ul>
 *
 * <h2>Parameters</h2>
 * {@code ordered} (default true), {@code gap} 2..8 s (default 5), {@code hold} 5..30 s (default 12, at least twice the gap so
 * the first lever is still engaged when the third is), {@code solo} (default false: one player may engage the three levers,
 * the delay is ignored; for the editor's trial room, never for a real game).
 *
 * <h2>Generation</h2>
 * Always solvable: three players engaging the levers in the drawn order, each within the delay, solve it. The tests do it
 * for 1,000 draws through the engine, and check that a single player is refused.
 */
@Component
public class ThreeLevers implements Mechanism {

	public static final String ID = "three_levers";
	public static final List<String> LEVER_ROLES = List.of("lever_1", "lever_2", "lever_3");

	static final int LEVERS = 3;
	static final int MIN_GAP = 2;
	static final int MAX_GAP = 8;
	static final int DEFAULT_GAP = 5;
	static final int MIN_HOLD = 5;
	static final int MAX_HOLD = 30;
	static final int DEFAULT_HOLD = 12;

	record Config(boolean ordered, int gap, int hold, boolean solo) {
	}

	private final Clock clock;

	@Autowired
	public ThreeLevers() {
		this(Clock.systemUTC());
	}

	/** With the clock the lease is measured on (tests move it). */
	public ThreeLevers(Clock clock) {
		this.clock = clock;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int difficulty(JsonNode params) {
		Config c = config(params);
		return c.ordered() ? (c.gap() <= 3 ? 3 : 2) : 1;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		Config c = config(params);
		return 60 + (c.ordered() ? 90 : 0) + (c.gap() <= 3 ? 30 : 0);
	}

	@Override
	public List<SlotRole> slotRoles() {
		return LEVER_ROLES.stream().map(r -> SlotRole.of(r, "lever")).toList();
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(
				new MechanismParam("ordered", "bool", 0, 0, "true", "The seed draws the order the levers must be engaged in; false: any order"),
				new MechanismParam("gap", "int", MIN_GAP, MAX_GAP, String.valueOf(DEFAULT_GAP),
						"Longest delay in seconds between two engagements (the seed draws a value in gap-2..gap)"),
				new MechanismParam("hold", "int", MIN_HOLD, MAX_HOLD, String.valueOf(DEFAULT_HOLD),
						"Seconds a lever stays engaged after a click; at least twice the gap"),
				new MechanismParam("solo", "bool", 0, 0, "false", "Trial only: one player may engage the three levers and the delay is ignored"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		check(params, "gap", MIN_GAP, MAX_GAP, problems);
		check(params, "hold", MIN_HOLD, MAX_HOLD, problems);
		for (String flag : List.of("ordered", "solo")) {
			if (params != null && params.has(flag) && !params.get(flag).isBoolean()) {
				problems.add(flag + " must be true or false");
			}
		}
		if (problems.isEmpty()
				&& intParam(params, "hold", DEFAULT_HOLD) < 2 * intParam(params, "gap", DEFAULT_GAP)) {
			problems.add("hold must be at least twice gap");
		}
		return problems;
	}

	/** The three levers must be bound, each to its own slot (three corners). */
	@Override
	public List<String> validateStep(Step step) {
		List<String> problems = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String role : LEVER_ROLES) {
			String slot = step.slots().get(role);
			if (slot == null) {
				problems.add("missing=slot:" + role);
			} else if (!seen.add(slot)) {
				problems.add("duplicate=slot:" + role);
			}
		}
		return problems;
	}

	private static void check(JsonNode params, String name, int min, int max, List<String> problems) {
		if (params == null || !params.has(name)) {
			return;
		}
		JsonNode v = params.get(name);
		if (!v.isInt() || v.asInt() < min || v.asInt() > max) {
			problems.add(name + " must be an integer " + min + ".." + max);
		}
	}

	/** Reads the parameters with their defaults; out-of-range values are clamped (validateParams reports them). */
	static Config config(JsonNode params) {
		boolean ordered = params == null || params.path("ordered").asBoolean(true);
		boolean solo = params != null && params.path("solo").asBoolean(false);
		int gap = clamp(intParam(params, "gap", DEFAULT_GAP), MIN_GAP, MAX_GAP);
		int hold = clamp(intParam(params, "hold", DEFAULT_HOLD), MIN_HOLD, MAX_HOLD);
		return new Config(ordered, gap, Math.max(hold, 2 * gap), solo);
	}

	private static int intParam(JsonNode params, String name, int fallback) {
		return params != null && params.path(name).isNumber() ? params.get(name).asInt() : fallback;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	// ---------------------------------------------------------------- generation

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		Config c = config(params);
		List<Integer> order = new ArrayList<>(List.of(0, 1, 2));
		if (c.ordered()) {
			for (int i = order.size() - 1; i > 0; i--) {
				Collections.swap(order, i, rng.nextInt(i + 1));
			}
		}
		int gap = Math.max(MIN_GAP, c.gap() - rng.nextInt(3));
		ObjectNode data = Json.object();
		data.put("ordered", c.ordered());
		data.put("solo", c.solo());
		ArrayNode orderNode = data.putArray("order");
		order.forEach(orderNode::add);
		data.put("gapMs", gap * 1000L);
		data.put("holdMs", c.hold() * 1000L);
		ArrayNode levers = data.putArray("levers");
		for (int i = 0; i < LEVERS; i++) {
			ObjectNode l = levers.addObject();
			l.put("until", 0L);
			l.put("by", -1);
		}
		data.put("lastAt", 0L);
		data.put("resets", 0);
		data.put("solved", false);
		return new MechanismState(data, List.of());
	}

	// ---------------------------------------------------------------- gestures

	@Override
	public InputResult onUse(MechanismState state, int player, String role) {
		int lever = LEVER_ROLES.indexOf(role);
		if (lever < 0) {
			return InputResult.unchanged(state);
		}
		return engage(state, player, lever, clock.millis());
	}

	/** {@code {"lever": 1..3}}: the same as using the lever in the world (tools and tests; the game uses slots). */
	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		JsonNode v = input == null ? null : input.get("lever");
		if (v == null || !v.isInt() || v.asInt() < 1 || v.asInt() > LEVERS) {
			throw new IllegalArgumentException("lever: 1.." + LEVERS);
		}
		return engage(state, player, v.asInt() - 1, clock.millis());
	}

	static boolean active(ObjectNode lever, long now) {
		return lever.path("until").asLong(0) >= now && lever.path("until").asLong(0) > 0;
	}

	private InputResult engage(MechanismState state, int player, int lever, long now) {
		ObjectNode old = state.data();
		if (old.path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		ObjectNode data = state.copyData();
		ArrayNode levers = (ArrayNode) data.get("levers");
		long hold = data.path("holdMs").asLong();
		boolean solo = data.path("solo").asBoolean(false);
		for (int i = 0; i < LEVERS; i++) {
			if (!active((ObjectNode) levers.get(i), now)) {
				free((ObjectNode) levers.get(i));
			}
		}
		ObjectNode mine = (ObjectNode) levers.get(lever);
		if (active(mine, now)) {
			if (mine.path("by").asInt(-1) != player) {
				return InputResult.unchanged(state); // someone else holds it: the click does nothing
			}
			mine.put("until", now + hold);
			return new InputResult(state.withData(data), List.of(event("hold", lever, count(levers, now))), false);
		}
		if (!solo) {
			for (int i = 0; i < LEVERS; i++) {
				ObjectNode l = (ObjectNode) levers.get(i);
				if (i != lever && active(l, now) && l.path("by").asInt(-1) == player) {
					free(l); // one lever per player
				}
			}
		}
		List<ObjectNode> events = new ArrayList<>();
		if (!solo && count(levers, now) > 0 && now - data.path("lastAt").asLong() > data.path("gapMs").asLong()) {
			freeAll(levers);
			events.add(type("slow"));
		}
		if (data.path("ordered").asBoolean(true) && !inOrder(data, levers, lever, now)) {
			freeAll(levers);
			data.put("resets", data.path("resets").asInt() + 1);
			data.put("lastAt", 0L);
			events.add(event("wrong", lever, 0));
			return new InputResult(state.withData(data), events, false);
		}
		mine.put("until", now + hold);
		mine.put("by", player);
		data.put("lastAt", now);
		int engaged = count(levers, now);
		if (engaged == LEVERS) {
			data.put("solved", true);
			events.add(event("engage", lever, engaged));
			events.add(type("unlock"));
			return new InputResult(state.withData(data), events, true);
		}
		events.add(event("engage", lever, engaged));
		return new InputResult(state.withData(data), events, false);
	}

	/** Whether {@code lever} is the next one of the order: the engaged ones are exactly the first k of it and it is the (k+1)th. */
	private static boolean inOrder(ObjectNode data, ArrayNode levers, int lever, long now) {
		ArrayNode order = (ArrayNode) data.get("order");
		int k = count(levers, now);
		if (order.get(k).asInt() != lever) {
			return false;
		}
		for (int i = 0; i < k; i++) {
			if (!active((ObjectNode) levers.get(order.get(i).asInt()), now)) {
				return false;
			}
		}
		return true;
	}

	private static int count(ArrayNode levers, long now) {
		int n = 0;
		for (JsonNode l : levers) {
			if (active((ObjectNode) l, now)) {
				n++;
			}
		}
		return n;
	}

	private static void free(ObjectNode lever) {
		lever.put("until", 0L);
		lever.put("by", -1);
	}

	private static void freeAll(ArrayNode levers) {
		for (JsonNode l : levers) {
			free((ObjectNode) l);
		}
	}

	private static ObjectNode type(String type) {
		ObjectNode e = Json.object();
		e.put("type", type);
		return e;
	}

	private static ObjectNode event(String type, int lever, int count) {
		ObjectNode e = type(type);
		e.put("lever", lever + 1);
		e.put("count", count);
		return e;
	}

	// ---------------------------------------------------------------- views

	/**
	 * {@code levers[i] = {on, ms, by}}: engaged now or not, milliseconds of lease left (the client counts down from the moment
	 * it receives the view), who holds it; {@code count} engaged now. Never the order or the delay, but for the game master.
	 */
	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		long now = clock.millis();
		boolean solved = d.path("solved").asBoolean(false);
		ObjectNode view = Json.object();
		ArrayNode levers = view.putArray("levers");
		int count = 0;
		for (JsonNode node : d.get("levers")) {
			boolean on = solved || active((ObjectNode) node, now);
			ObjectNode l = levers.addObject();
			l.put("on", on);
			l.put("ms", solved ? 0L : on ? node.path("until").asLong() - now : 0L);
			l.put("by", on ? node.path("by").asInt(-1) : -1);
			if (on) {
				count++;
			}
		}
		view.put("count", count);
		view.put("resets", d.path("resets").asInt());
		view.put("solved", solved);
		if (viewer == ViewerKind.GAME_MASTER) {
			ArrayNode order = view.putArray("order");
			d.get("order").forEach(o -> order.add(o.asInt() + 1));
			view.put("gapMs", d.path("gapMs").asLong());
		}
		return view;
	}
}
