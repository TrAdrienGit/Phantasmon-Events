package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Mechanism;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.SlotRole;

/**
 * Checks a room definition. Pure, no Spring; every problem is reported at once as an {@link Issue} with an
 * {@code ERROR_TRAHISON_ROOM_*} code.
 * <ul>
 * <li>ids valid and unique (slots, steps); known slot kinds and orientations; slots and their size inside the
 * 48 x 48 x 24 place;</li>
 * <li>steps: known mechanism with valid params, roles bound to existing slots, {@code needs} naming existing steps with
 * no cycle, {@code gives} naming a clue role of the mechanism and an existing slot, at most 3 hints;</li>
 * <li>the chain ends at the exit: a step bound to a slot with the role {@code exit} (that slot has the kind
 * {@code exit}), or else the last step with an {@code exit} slot in the room; every step must lead to it.</li>
 * </ul>
 */
public final class RoomValidator {

	public static final Set<String> KINDS = Set.of("terminal", "keypad", "pickup", "wall_clue", "document", "lever", "plate",
			"pedestal", "inner_door", "exit", "secret", "power_spot");
	public static final Set<String> ORIENTATIONS = Set.of("north", "east", "south", "west");
	public static final int WIDTH = 48;
	public static final int HEIGHT = 24;
	public static final int DEPTH = 48;
	public static final int MAX_STEPS = 24;

	public static final String BAD_ID = "ERROR_TRAHISON_ROOM_BAD_ID";
	public static final String DUPLICATE_ID = "ERROR_TRAHISON_ROOM_DUPLICATE_ID";
	public static final String BAD_KIND = "ERROR_TRAHISON_ROOM_BAD_KIND";
	public static final String BAD_ORIENTATION = "ERROR_TRAHISON_ROOM_BAD_ORIENTATION";
	public static final String OUT_OF_BOUNDS = "ERROR_TRAHISON_ROOM_OUT_OF_BOUNDS";
	public static final String NO_STEPS = "ERROR_TRAHISON_ROOM_NO_STEPS";
	public static final String UNKNOWN_MECHANISM = "ERROR_TRAHISON_ROOM_UNKNOWN_MECHANISM";
	public static final String BAD_PARAMS = "ERROR_TRAHISON_ROOM_BAD_PARAMS";
	public static final String UNKNOWN_SLOT = "ERROR_TRAHISON_ROOM_UNKNOWN_SLOT";
	public static final String UNKNOWN_STEP = "ERROR_TRAHISON_ROOM_UNKNOWN_STEP";
	public static final String BAD_GIVE = "ERROR_TRAHISON_ROOM_BAD_GIVE";
	public static final String BAD_HINTS = "ERROR_TRAHISON_ROOM_BAD_HINTS";
	public static final String CYCLE = "ERROR_TRAHISON_ROOM_CYCLE";
	public static final String UNREACHABLE = "ERROR_TRAHISON_ROOM_UNREACHABLE";
	public static final String NO_EXIT = "ERROR_TRAHISON_ROOM_NO_EXIT";
	public static final String BAD_COMBINE = "ERROR_TRAHISON_ROOM_BAD_COMBINE";
	public static final int MAX_COMBINES = 32;
	/** Distinct powers a room may ask for (T8.3). */
	public static final int MAX_POWERS = 3;

	private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");

	/** One problem: {@code ids} the elements concerned, {@code info} a short machine-readable complement or null. */
	public record Issue(String code, List<String> ids, String info) {
	}

	private RoomValidator() {
	}

	public static boolean validId(String id) {
		return id != null && ID.matcher(id).matches();
	}

	/**
	 * @param mechanisms resolves a mechanism id; {@code null} skips the checks that need the mechanisms (known id,
	 *                   params, clue roles)
	 */
	public static List<Issue> validate(RoomDefinition room, Function<String, Optional<Mechanism>> mechanisms) {
		List<Issue> out = new ArrayList<>();
		if (!validId(room.id())) {
			out.add(new Issue(BAD_ID, List.of(String.valueOf(room.id())), "room"));
		}
		Map<String, Slot> slots = checkSlots(room, out);
		Map<String, Step> steps = checkSteps(room, slots, mechanisms, out);
		if (room.secret() != null && !slots.containsKey(room.secret())) {
			out.add(new Issue(UNKNOWN_SLOT, List.of(room.secret()), "secret"));
		}
		if (!steps.isEmpty()) {
			checkChain(room, slots, steps, out);
		}
		checkCombines(room, out);
		checkPowers(room, out);
		return out;
	}

	/** T8.3: a group has at most three members and every power goes to a different one, so a room asks for at most three powers. */
	private static void checkPowers(RoomDefinition room, List<Issue> out) {
		Set<com.mystaria.phantasmon_backend.events.trahison.pokemon.Power> asked = new LinkedHashSet<>();
		List<String> ids = new ArrayList<>();
		for (Step st : room.steps()) {
			if (st != null && com.mystaria.phantasmon_backend.events.trahison.room.mechanism.PowerSpot.ID.equals(st.mechanism())) {
				com.mystaria.phantasmon_backend.events.trahison.room.mechanism.PowerSpot.power(st.params()).ifPresent(p -> {
					asked.add(p);
					ids.add(st.id());
				});
			}
		}
		if (asked.size() > MAX_POWERS) {
			out.add(new Issue(BAD_PARAMS, ids, "powers=" + asked.size() + ";max=" + MAX_POWERS));
		}
	}

	/** Recipes: valid item ids, a result that is neither ingredient, no pair declared twice (in either order). */
	private static void checkCombines(RoomDefinition room, List<Issue> out) {
		Set<String> seen = new HashSet<>();
		if (room.combines().size() > MAX_COMBINES) {
			out.add(new Issue(BAD_COMBINE, List.of(), "count=" + room.combines().size()));
		}
		for (Combine c : room.combines()) {
			if (c == null || !validId(c.a()) || !validId(c.b()) || !validId(c.result())) {
				out.add(new Issue(BAD_COMBINE, List.of(c == null ? "null" : c.a() + "+" + c.b()), "id"));
				continue;
			}
			String pair = c.a().compareTo(c.b()) <= 0 ? c.a() + "+" + c.b() : c.b() + "+" + c.a();
			if (c.result().equals(c.a()) || c.result().equals(c.b())) {
				out.add(new Issue(BAD_COMBINE, List.of(pair), "result_is_ingredient"));
			} else if (!seen.add(pair)) {
				out.add(new Issue(BAD_COMBINE, List.of(pair), "duplicate"));
			}
		}
	}

	private static Map<String, Slot> checkSlots(RoomDefinition room, List<Issue> out) {
		Map<String, Slot> slots = new HashMap<>();
		for (Slot s : room.slots()) {
			if (s == null || !validId(s.id())) {
				out.add(new Issue(BAD_ID, List.of(s == null ? "null" : String.valueOf(s.id())), "slot"));
				continue;
			}
			if (slots.put(s.id(), s) != null) {
				out.add(new Issue(DUPLICATE_ID, List.of(s.id()), "slot"));
			}
			if (s.kind() == null || !KINDS.contains(s.kind())) {
				out.add(new Issue(BAD_KIND, List.of(s.id()), "kind=" + s.kind()));
			}
			if (!ORIENTATIONS.contains(s.orientation())) {
				out.add(new Issue(BAD_ORIENTATION, List.of(s.id()), "orientation=" + s.orientation()));
			}
			Pos p = s.position();
			Size z = s.size();
			if (p == null) {
				out.add(new Issue(OUT_OF_BOUNDS, List.of(s.id()), "position=missing"));
			} else if (z.x() < 1 || z.y() < 1 || z.z() < 1 || p.x() < 0 || p.y() < 0 || p.z() < 0
					|| (long) p.x() + z.x() > WIDTH || (long) p.y() + z.y() > HEIGHT || (long) p.z() + z.z() > DEPTH) {
				out.add(new Issue(OUT_OF_BOUNDS, List.of(s.id()), "position=" + p.x() + "," + p.y() + "," + p.z()
						+ ";size=" + z.x() + "," + z.y() + "," + z.z()));
			}
		}
		return slots;
	}

	/**
	 * Roles bound to slots against what the mechanism declares ({@link Mechanism#slotRoles}): a role it does not know, or a
	 * slot of a kind the role does not accept. Skipped when the mechanism declares none. The {@code exit} role is checked
	 * with the slots (kind {@code exit}).
	 */
	private static void checkRoles(Step st, Map<String, Slot> slots, Mechanism mechanism, List<Issue> out) {
		List<SlotRole> declared = mechanism.slotRoles();
		if (declared.isEmpty()) {
			return;
		}
		for (Map.Entry<String, String> bound : st.slots().entrySet()) {
			String role = bound.getKey();
			Slot slot = slots.get(bound.getValue());
			if (Step.EXIT_ROLE.equals(role) || slot == null) {
				continue;
			}
			SlotRole known = declared.stream().filter(r -> r.role().equals(role)).findFirst().orElse(null);
			if (known == null) {
				out.add(new Issue(BAD_KIND, List.of(st.id(), slot.id()), "role=" + role + ";unknown"));
			} else if (!known.kinds().contains(slot.kind())) {
				out.add(new Issue(BAD_KIND, List.of(st.id(), slot.id()),
						"role=" + role + ";needs=" + String.join("|", known.kinds())));
			}
		}
	}

	private static Map<String, Step> checkSteps(RoomDefinition room, Map<String, Slot> slots,
			Function<String, Optional<Mechanism>> mechanisms, List<Issue> out) {
		Map<String, Step> steps = new HashMap<>();
		if (room.steps().isEmpty() || room.steps().size() > MAX_STEPS) {
			out.add(new Issue(NO_STEPS, List.of(), "count=" + room.steps().size()));
		}
		for (Step st : room.steps()) {
			if (st == null || !validId(st.id())) {
				out.add(new Issue(BAD_ID, List.of(st == null ? "null" : String.valueOf(st.id())), "step"));
				continue;
			}
			if (steps.put(st.id(), st) != null) {
				out.add(new Issue(DUPLICATE_ID, List.of(st.id()), "step"));
			}
		}
		for (Step st : steps.values()) {
			Mechanism mechanism = null;
			if (mechanisms != null) {
				mechanism = st.mechanism() == null ? null : mechanisms.apply(st.mechanism()).orElse(null);
				if (mechanism == null) {
					out.add(new Issue(UNKNOWN_MECHANISM, List.of(st.id()), "mechanism=" + st.mechanism()));
				} else {
					for (String problem : mechanism.validateParams(st.params())) {
						out.add(new Issue(BAD_PARAMS, List.of(st.id()), problem));
					}
				}
			}
			for (Map.Entry<String, String> role : st.slots().entrySet()) {
				Slot slot = slots.get(role.getValue());
				if (slot == null) {
					out.add(new Issue(UNKNOWN_SLOT, List.of(st.id(), String.valueOf(role.getValue())), "role=" + role.getKey()));
				} else if (Step.EXIT_ROLE.equals(role.getKey()) && !"exit".equals(slot.kind())) {
					out.add(new Issue(BAD_KIND, List.of(st.id(), slot.id()), "role=exit;needs=exit"));
				}
			}
			if (mechanism != null) {
				checkRoles(st, slots, mechanism, out);
			}
			for (String need : st.needs()) {
				if (!steps.containsKey(need) || need.equals(st.id())) {
					out.add(new Issue(UNKNOWN_STEP, List.of(st.id(), String.valueOf(need)), "needs"));
				}
			}
			for (Give g : st.gives()) {
				if (!slots.containsKey(g.slot())) {
					out.add(new Issue(UNKNOWN_SLOT, List.of(st.id(), String.valueOf(g.slot())), "gives"));
				}
				if (!Give.OPEN.equals(g.when()) && !Give.SOLVED.equals(g.when())) {
					out.add(new Issue(BAD_GIVE, List.of(st.id()), "when=" + g.when()));
				}
				if (mechanism != null && !mechanism.clueRoles(st.params()).contains(g.clue())) {
					out.add(new Issue(BAD_GIVE, List.of(st.id()), "clue=" + g.clue()));
				}
			}
			if (mechanism != null && mechanism.validateParams(st.params()).isEmpty()) {
				for (String problem : mechanism.validateStep(st)) {
					out.add(new Issue(BAD_GIVE, List.of(st.id()), problem));
				}
			}
			if (st.hints().size() > Step.MAX_HINTS || st.hints().stream().anyMatch(h -> h == null || h.isBlank())) {
				out.add(new Issue(BAD_HINTS, List.of(st.id()), "count=" + st.hints().size()));
			}
		}
		return steps;
	}

	private static void checkChain(RoomDefinition room, Map<String, Slot> slots, Map<String, Step> steps, List<Issue> out) {
		// Cycle: depth-first over valid needs.
		Set<String> inCycle = new LinkedHashSet<>();
		Map<String, Integer> color = new HashMap<>();
		for (Step st : room.steps()) {
			if (st != null && steps.containsKey(st.id())) {
				visit(st.id(), steps, color, new ArrayDeque<>(), inCycle);
			}
		}
		if (!inCycle.isEmpty()) {
			out.add(new Issue(CYCLE, List.copyOf(inCycle), null));
			return; // reachability is meaningless on a cycle
		}
		// Exit steps: those bound to an "exit" role, else the last step (which needs an exit slot to open).
		List<String> exits = new ArrayList<>();
		for (Step st : room.steps()) {
			if (st != null && st.slots().containsKey(Step.EXIT_ROLE) && steps.containsKey(st.id())) {
				exits.add(st.id());
			}
		}
		if (exits.isEmpty()) {
			boolean hasExitSlot = slots.values().stream().anyMatch(s -> "exit".equals(s.kind()));
			if (!hasExitSlot) {
				out.add(new Issue(NO_EXIT, List.of(), "no exit slot"));
			}
			Step last = room.steps().get(room.steps().size() - 1);
			if (last != null && steps.containsKey(last.id())) {
				exits.add(last.id());
			}
		}
		// Every step must be an ancestor of (or be) an exit step.
		Set<String> useful = new HashSet<>();
		Deque<String> todo = new ArrayDeque<>(exits);
		while (!todo.isEmpty()) {
			String id = todo.pop();
			if (useful.add(id)) {
				for (String need : steps.get(id).needs()) {
					if (steps.containsKey(need)) {
						todo.push(need);
					}
				}
			}
		}
		for (String id : steps.keySet()) {
			if (!useful.contains(id)) {
				out.add(new Issue(UNREACHABLE, List.of(id), "leads_nowhere"));
			}
		}
	}

	/** Colors: 1 on the stack, 2 done. */
	private static void visit(String id, Map<String, Step> steps, Map<String, Integer> color, Deque<String> stack,
			Set<String> inCycle) {
		Integer c = color.get(id);
		if (c != null && c == 2) {
			return;
		}
		if (c != null && c == 1) {
			boolean on = false;
			for (String s : stack.reversed()) {
				if (s.equals(id)) {
					on = true;
				}
				if (on) {
					inCycle.add(s);
				}
			}
			return;
		}
		color.put(id, 1);
		stack.push(id);
		for (String need : steps.get(id).needs()) {
			if (steps.containsKey(need)) {
				visit(need, steps, color, stack, inCycle);
			}
		}
		stack.pop();
		color.put(id, 2);
	}

	/** Sum of the steps' difficulty and expected time, for the mechanisms that are known. */
	public static int[] totals(RoomDefinition room, Function<String, Optional<Mechanism>> mechanisms) {
		int difficulty = 0;
		int seconds = 0;
		for (Step st : room.steps()) {
			Optional<Mechanism> m = mechanisms.apply(st.mechanism());
			if (m.isPresent()) {
				difficulty += m.get().difficulty(st.params());
				seconds += m.get().expectedSeconds(st.params());
			}
		}
		return new int[] { difficulty, seconds };
	}
}
