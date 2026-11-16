package com.mystaria.events.trahison.edit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.mystaria.events.trahison.edit.RoomDto.GiveDto;
import com.mystaria.events.trahison.edit.RoomDto.P;
import com.mystaria.events.trahison.edit.RoomDto.SlotDto;
import com.mystaria.events.trahison.edit.RoomDto.StepDto;

/**
 * The checks the room editor runs before saving (the backend's {@code RoomValidator} runs again on the server and stays the
 * authority). Same codes ({@code ERROR_TRAHISON_ROOM_*}) as the backend for the same problems, plus a few that only
 * the editor can know ({@code LOCAL_*}: it sees the world). A <i>warning</i> never blocks a save. Pure: no Minecraft class.
 */
final class RoomChecks {

	/** One problem: the elements concerned, a machine-readable complement, and whether it only warns. */
	record Issue(String code, List<String> ids, String info, boolean warning) {

		Issue(String code, List<String> ids, String info) {
			this(code, ids, info, false);
		}
	}

	static final List<String> KINDS = List.of("terminal", "keypad", "pickup", "wall_clue", "document", "lever", "plate", "pedestal",
			"inner_door", "exit", "secret");
	static final int MAX_STEPS = 24;
	static final int MAX_HINTS = 3;
	private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");

	private RoomChecks() {
	}

	static boolean validId(String id) {
		return id != null && ID.matcher(id).matches();
	}

	static List<Issue> check(RoomDto room) {
		List<Issue> out = new ArrayList<>();
		if (!validId(room.id)) {
			out.add(new Issue("ERROR_TRAHISON_ROOM_BAD_ID", List.of(String.valueOf(room.id)), "room"));
		}
		if (room.name == null || room.name.isBlank()) {
			out.add(new Issue("LOCAL_NO_NAME", List.of(), "", true));
		}
		Map<String, SlotDto> slots = checkSlots(room, out);
		Map<String, StepDto> steps = checkSteps(room, slots, out);
		if (room.secret != null && !slots.containsKey(room.secret)) {
			out.add(new Issue("ERROR_TRAHISON_ROOM_UNKNOWN_SLOT", List.of(room.secret), "secret"));
		}
		if (!steps.isEmpty()) {
			checkChain(room, slots, steps, out);
		}
		checkReuse(room, slots, out);
		return out;
	}

	private static Map<String, SlotDto> checkSlots(RoomDto room, List<Issue> out) {
		Map<String, SlotDto> slots = new HashMap<>();
		for (SlotDto s : room.slots) {
			if (!validId(s.id)) {
				out.add(new Issue("ERROR_TRAHISON_ROOM_BAD_ID", List.of(String.valueOf(s.id)), "slot"));
				continue;
			}
			if (slots.put(s.id, s) != null) {
				out.add(new Issue("ERROR_TRAHISON_ROOM_DUPLICATE_ID", List.of(s.id), "slot"));
			}
			if (!KINDS.contains(s.kind)) {
				out.add(new Issue("ERROR_TRAHISON_ROOM_BAD_KIND", List.of(s.id), "kind=" + s.kind));
			}
			if (!RoomTemplate.ORIENTATIONS.contains(s.orientation)) {
				out.add(new Issue("ERROR_TRAHISON_ROOM_BAD_ORIENTATION", List.of(s.id), "orientation=" + s.orientation));
			}
			P p = s.position;
			P z = s.size;
			if (z.x < 1 || z.y < 1 || z.z < 1 || !RoomTemplate.inside(p.x, p.y, p.z, p.x + z.x - 1, p.y + z.y - 1, p.z + z.z - 1)) {
				out.add(new Issue("ERROR_TRAHISON_ROOM_OUT_OF_BOUNDS", List.of(s.id),
						"position=" + p.x + "," + p.y + "," + p.z + ";size=" + z.x + "," + z.y + "," + z.z));
			}
		}
		return slots;
	}

	private static Map<String, StepDto> checkSteps(RoomDto room, Map<String, SlotDto> slots, List<Issue> out) {
		Map<String, StepDto> steps = new HashMap<>();
		if (room.steps.isEmpty() || room.steps.size() > MAX_STEPS) {
			out.add(new Issue("ERROR_TRAHISON_ROOM_NO_STEPS", List.of(), "count=" + room.steps.size()));
		}
		for (StepDto st : room.steps) {
			if (!validId(st.id)) {
				out.add(new Issue("ERROR_TRAHISON_ROOM_BAD_ID", List.of(String.valueOf(st.id)), "step"));
				continue;
			}
			if (steps.put(st.id, st) != null) {
				out.add(new Issue("ERROR_TRAHISON_ROOM_DUPLICATE_ID", List.of(st.id), "step"));
			}
		}
		for (StepDto st : steps.values()) {
			if (st.mechanism == null || st.mechanism.isBlank()) {
				out.add(new Issue("ERROR_TRAHISON_ROOM_UNKNOWN_MECHANISM", List.of(st.id), "mechanism=" + st.mechanism));
			} else if (MechanismCatalog.find(st.mechanism).isEmpty()) {
				// Not in the editor's list: the backend decides (the list is hard-coded, see MechanismCatalog).
				out.add(new Issue("LOCAL_MECHANISM_NOT_LISTED", List.of(st.id), "mechanism=" + st.mechanism, true));
			} else if (!MechanismCatalog.find(st.mechanism).get().known()) {
				out.add(new Issue("LOCAL_MECHANISM_NOT_YET", List.of(st.id), "mechanism=" + st.mechanism, true));
			}
			for (Map.Entry<String, String> role : st.slots.entrySet()) {
				SlotDto slot = slots.get(role.getValue());
				if (slot == null) {
					out.add(new Issue("ERROR_TRAHISON_ROOM_UNKNOWN_SLOT", List.of(st.id, String.valueOf(role.getValue())), "role=" + role.getKey()));
				} else if (MechanismCatalog.EXIT_ROLE.equals(role.getKey()) && !"exit".equals(slot.kind)) {
					out.add(new Issue("ERROR_TRAHISON_ROOM_BAD_KIND", List.of(st.id, slot.id), "role=exit;needs=exit"));
				} else if (!MechanismCatalog.accepts(st.mechanism, role.getKey(), slot.kind)) {
					out.add(new Issue("ERROR_TRAHISON_ROOM_BAD_KIND", List.of(st.id, slot.id),
							"role=" + role.getKey() + ";needs=" + String.join("|", MechanismCatalog.kinds(st.mechanism, role.getKey()))));
				}
			}
			for (String need : st.needs) {
				if (!steps.containsKey(need) || need.equals(st.id)) {
					out.add(new Issue("ERROR_TRAHISON_ROOM_UNKNOWN_STEP", List.of(st.id, String.valueOf(need)), "needs"));
				}
			}
			for (GiveDto g : st.gives) {
				if (!slots.containsKey(g.slot)) {
					out.add(new Issue("ERROR_TRAHISON_ROOM_UNKNOWN_SLOT", List.of(st.id, String.valueOf(g.slot)), "gives"));
				}
				if (!"open".equals(g.when) && !"solved".equals(g.when)) {
					out.add(new Issue("ERROR_TRAHISON_ROOM_BAD_GIVE", List.of(st.id), "when=" + g.when));
				}
				if (MechanismCatalog.find(st.mechanism).filter(info -> info.known() && !MechanismCatalog.usingFallback()).isPresent()
						&& !MechanismCatalog.clues(st.mechanism).contains(g.clue)) {
					out.add(new Issue("ERROR_TRAHISON_ROOM_BAD_GIVE", List.of(st.id), "clue=" + g.clue));
				}
			}
			if (st.hints.size() > MAX_HINTS || st.hints.stream().anyMatch(h -> h == null || h.isBlank())) {
				out.add(new Issue("ERROR_TRAHISON_ROOM_BAD_HINTS", List.of(st.id), "count=" + st.hints.size()));
			}
		}
		return steps;
	}

	private static void checkChain(RoomDto room, Map<String, SlotDto> slots, Map<String, StepDto> steps, List<Issue> out) {
		Set<String> inCycle = new LinkedHashSet<>();
		Map<String, Integer> color = new HashMap<>();
		for (StepDto st : room.steps) {
			if (steps.containsKey(st.id)) {
				visit(st.id, steps, color, new ArrayDeque<>(), inCycle);
			}
		}
		if (!inCycle.isEmpty()) {
			out.add(new Issue("ERROR_TRAHISON_ROOM_CYCLE", List.copyOf(inCycle), null));
			return;
		}
		// The steps that open the exit: those bound to the exit role, else the last step (which then needs an exit slot).
		List<String> exits = new ArrayList<>();
		for (StepDto st : room.steps) {
			if (steps.containsKey(st.id) && st.slots.containsKey(MechanismCatalog.EXIT_ROLE)) {
				exits.add(st.id);
			}
		}
		if (exits.isEmpty()) {
			if (slots.values().stream().noneMatch(s -> "exit".equals(s.kind))) {
				out.add(new Issue("ERROR_TRAHISON_ROOM_NO_EXIT", List.of(), "no exit slot"));
			}
			StepDto last = room.steps.get(room.steps.size() - 1);
			if (steps.containsKey(last.id)) {
				exits.add(last.id);
			}
		}
		// Every step must be the exit step or one of its ancestors: the last step opens the exit and nothing leads nowhere.
		Set<String> useful = new HashSet<>();
		Deque<String> todo = new ArrayDeque<>(exits);
		while (!todo.isEmpty()) {
			String id = todo.pop();
			if (useful.add(id)) {
				for (String need : steps.get(id).needs) {
					if (steps.containsKey(need)) {
						todo.push(need);
					}
				}
			}
		}
		for (StepDto st : room.steps) {
			if (steps.containsKey(st.id) && !useful.contains(st.id)) {
				out.add(new Issue("ERROR_TRAHISON_ROOM_UNREACHABLE", List.of(st.id), "leads_nowhere"));
			}
		}
	}

	private static void visit(String id, Map<String, StepDto> steps, Map<String, Integer> color, Deque<String> stack, Set<String> inCycle) {
		Integer c = color.get(id);
		if (c != null && c == 2) {
			return;
		}
		if (c != null && c == 1) {
			boolean on = false;
			for (String s : stack.reversed()) {
				on |= s.equals(id);
				if (on) {
					inCycle.add(s);
				}
			}
			return;
		}
		color.put(id, 1);
		stack.push(id);
		for (String need : steps.get(id).needs) {
			if (steps.containsKey(need)) {
				visit(need, steps, color, stack, inCycle);
			}
		}
		stack.pop();
		color.put(id, 2);
	}

	/** Editor-only checks on how slots are used: one puzzle per slot, slots that overlap or cover an opening. */
	private static void checkReuse(RoomDto room, Map<String, SlotDto> slots, List<Issue> out) {
		// A slot is the seat of one puzzle at most (two steps bound to the same slot would open one screen for two).
		Map<String, List<String>> bound = new HashMap<>();
		for (StepDto st : room.steps) {
			for (String slot : st.slots.values()) {
				bound.computeIfAbsent(slot, k -> new ArrayList<>()).add(st.id);
			}
		}
		for (Map.Entry<String, List<String>> e : bound.entrySet()) {
			if (e.getValue().size() > 1 && slots.containsKey(e.getKey()) && !"exit".equals(slots.get(e.getKey()).kind)) {
				List<String> ids = new ArrayList<>();
				ids.add(e.getKey());
				ids.addAll(e.getValue());
				out.add(new Issue("LOCAL_SLOT_REUSED", ids, "slot=" + e.getKey()));
			}
		}
		// Overlaps: two slots occupying the same block are almost surely a mistake (warning), and so is one on an opening.
		List<SlotDto> list = new ArrayList<>(slots.values());
		list.sort((a, b) -> a.id.compareTo(b.id));
		for (int i = 0; i < list.size(); i++) {
			SlotDto a = list.get(i);
			if (touches(a, RoomTemplate.ENTRY) || touches(a, RoomTemplate.EXIT)) {
				out.add(new Issue("LOCAL_SLOT_ON_OPENING", List.of(a.id), "", true));
			}
			for (int j = i + 1; j < list.size(); j++) {
				if (overlap(a, list.get(j))) {
					out.add(new Issue("LOCAL_SLOT_OVERLAP", List.of(a.id, list.get(j).id), "", true));
				}
			}
		}
	}

	private static boolean overlap(SlotDto a, SlotDto b) {
		return a.position.x < b.position.x + b.size.x && b.position.x < a.position.x + a.size.x
				&& a.position.y < b.position.y + b.size.y && b.position.y < a.position.y + a.size.y
				&& a.position.z < b.position.z + b.size.z && b.position.z < a.position.z + a.size.z;
	}

	private static boolean touches(SlotDto a, LayoutDto.Box box) {
		return a.position.x <= box.maxX && a.position.x + a.size.x - 1 >= box.minX
				&& a.position.y <= box.maxY && a.position.y + a.size.y - 1 >= box.minY
				&& a.position.z <= box.maxZ && a.position.z + a.size.z - 1 >= box.minZ;
	}

	/** Difficulty and time of the steps the catalog knows, for the totals shown before the server computes them. */
	static int[] totals(RoomDto room) {
		int difficulty = 0;
		int seconds = 0;
		for (StepDto st : room.steps) {
			var info = MechanismCatalog.find(st.mechanism);
			if (info.isPresent()) {
				difficulty += info.get().difficulty();
				seconds += info.get().seconds();
			}
		}
		return new int[] { difficulty, seconds };
	}

	/** True when no issue blocks a save. */
	static boolean passes(List<Issue> issues) {
		return issues.stream().allMatch(Issue::warning);
	}
}
