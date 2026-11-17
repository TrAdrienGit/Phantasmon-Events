package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.events.trahison.pokemon.Partner;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.Power;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A place of the room that only the trainer whose partner holds the right power can act on (T8.3). Played in the world: a click on
 * the slot, no screen. Each power does its own thing in the world (the client draws it; the rules below are all the backend decides):
 * <ul>
 * <li>{@code FLASH}: the zone is dark for everybody except within {@code radius} blocks of the bearer, until the bearer lights it;</li>
 * <li>{@code STRENGTH}: the bearer pushes a boulder one cell at a time, {@code pushes} times;</li>
 * <li>{@code CUT}: the bearer clears an obstacle;</li>
 * <li>{@code SURF}: the bearer crosses a pool the others cannot (for the others the edge is walled with invisible blocks).</li>
 * </ul>
 * Every use by the bearer counts one step of progress; the step is solved after {@code pushes} uses (one for the other powers).
 * A use by anybody else changes nothing and says which power is missing (event {@code denied}).
 *
 * <h2>Parameters</h2>
 * {@code power} (FLASH, STRENGTH, CUT or SURF; required), {@code pushes} 1..3 (STRENGTH only, default 2), {@code radius} 2..8 (FLASH
 * only, default 4).
 * <h2>Who is the bearer</h2>
 * {@link #bind} reads the group's partners: the members whose partner holds the power. When nobody does (a group too small for the
 * powers its room asks for), any member may act, so the step is never impossible.
 */
@Component
public class PowerSpot implements Mechanism {

	public static final String ID = "power_spot";
	public static final String SPOT_ROLE = "spot";
	static final int MIN_PUSHES = 1;
	static final int MAX_PUSHES = 3;
	static final int DEFAULT_PUSHES = 2;
	static final int MIN_RADIUS = 2;
	static final int MAX_RADIUS = 8;
	static final int DEFAULT_RADIUS = 4;

	@Override
	public String id() {
		return ID;
	}

	/** The power a step's params name, if valid. */
	public static Optional<Power> power(JsonNode params) {
		return params != null && params.path("power").isString() ? Power.parse(params.get("power").asString()) : Optional.empty();
	}

	static int total(JsonNode params) {
		return power(params).filter(p -> p == Power.STRENGTH).isPresent()
				? CodeEntry.intParam(params, "pushes", DEFAULT_PUSHES, MIN_PUSHES, MAX_PUSHES) : 1;
	}

	@Override
	public int difficulty(JsonNode params) {
		return total(params) >= 3 ? 2 : 1;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		return 40 + 15 * total(params);
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(SPOT_ROLE, "power_spot", "plate", "pedestal", "lever"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(
				new MechanismParam("power", "text", 0, 0, "FLASH", "Power the trainer's partner must hold: FLASH, STRENGTH, CUT or SURF"),
				new MechanismParam("pushes", "int", MIN_PUSHES, MAX_PUSHES, String.valueOf(DEFAULT_PUSHES), "STRENGTH: times the boulder is pushed"),
				new MechanismParam("radius", "int", MIN_RADIUS, MAX_RADIUS, String.valueOf(DEFAULT_RADIUS), "FLASH: lit radius around the bearer, in blocks"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		if (power(params).isEmpty()) {
			problems.add("power must be FLASH, STRENGTH, CUT or SURF");
		}
		CodeEntry.checkInt(params, "pushes", MIN_PUSHES, MAX_PUSHES, problems);
		CodeEntry.checkInt(params, "radius", MIN_RADIUS, MAX_RADIUS, problems);
		return problems;
	}

	@Override
	public List<String> validateStep(com.mystaria.phantasmon_backend.events.trahison.room.Step step) {
		return step.slots().containsKey(SPOT_ROLE) ? List.of() : List.of("missing=slot:" + SPOT_ROLE);
	}

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		Power power = power(params).orElse(Power.FLASH);
		ObjectNode data = Json.object();
		data.put("power", power.name());
		data.put("total", total(params));
		data.put("done", 0);
		data.put("radius", CodeEntry.intParam(params, "radius", DEFAULT_RADIUS, MIN_RADIUS, MAX_RADIUS));
		data.putArray("holders");
		data.put("anyone", true);
		data.put("denied", 0);
		data.put("solved", false);
		return new MechanismState(data, List.of());
	}

	@Override
	public MechanismState bind(MechanismState state, List<Partner> partners) {
		ObjectNode data = state.copyData();
		Power power = Power.parse(data.path("power").asString("")).orElse(Power.FLASH);
		ArrayNode holders = Json.array();
		for (Partner p : partners) {
			if (p.power() == power) {
				holders.add(p.player());
			}
		}
		data.set("holders", holders);
		data.put("anyone", holders.isEmpty());
		return state.withData(data);
	}

	private static boolean mayAct(ObjectNode data, int player) {
		if (data.path("anyone").asBoolean(false)) {
			return true;
		}
		for (JsonNode h : data.get("holders")) {
			if (h.asInt(-1) == player) {
				return true;
			}
		}
		return false;
	}

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		throw new IllegalArgumentException("power_spot is played in the world, it takes no gesture from a screen");
	}

	@Override
	public InputResult onUse(MechanismState state, int player, String role) {
		ObjectNode old = state.data();
		if (!SPOT_ROLE.equals(role) || old.path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		ObjectNode data = state.copyData();
		ObjectNode event = Json.object();
		if (!mayAct(old, player)) {
			data.put("denied", old.path("denied").asInt() + 1);
			event.put("type", "denied");
			event.put("power", old.path("power").asString());
			return new InputResult(state.withData(data), List.of(event), false);
		}
		int done = old.path("done").asInt() + 1;
		boolean solved = done >= old.path("total").asInt(1);
		data.put("done", done);
		data.put("solved", solved);
		event.put("type", solved ? "unlock" : "push");
		event.put("power", old.path("power").asString());
		event.put("done", done);
		return new InputResult(state.withData(data), List.of(event), solved);
	}

	@Override
	public Optional<int[]> progress(MechanismState state) {
		int total = state.data().path("total").asInt(1);
		return total > 1 ? Optional.of(new int[] { state.data().path("done").asInt(), total }) : Optional.empty();
	}

	@Override
	public Optional<JsonNode> world(MechanismState state) {
		return Optional.of(view(state, ViewerKind.PLAYER));
	}

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		ObjectNode view = Json.object();
		view.put("power", d.path("power").asString());
		view.put("total", d.path("total").asInt(1));
		view.put("done", d.path("done").asInt());
		view.put("radius", d.path("radius").asInt(DEFAULT_RADIUS));
		view.set("holders", d.get("holders").deepCopy());
		view.put("anyone", d.path("anyone").asBoolean(false));
		view.put("denied", d.path("denied").asInt());
		view.put("solved", d.path("solved").asBoolean(false));
		return view;
	}
}
