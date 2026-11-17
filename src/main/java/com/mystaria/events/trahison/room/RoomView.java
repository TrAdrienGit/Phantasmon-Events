package com.mystaria.events.trahison.room;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A parsed {@code EventsTrahisonRoom} (Documentation/trahison/protocole.md): what the backend tells a client of one
 * room: the active slots with what each shows, and the bag. Nothing here is a rule; the backend never sends a solution.
 * Pure: no Minecraft class.
 */
public record RoomView(String hub, int round, int group, String room, String name, List<Integer> members, boolean exitOpen, boolean finished,
		List<SlotView> slots, List<ItemView> items, Place place, List<String> hints, List<Progress> progress, List<Partner> partners, String ghostPartner,
		List<PowerStep> powers) {

	/** The partner Pokemon of a member (T8.3): a Cobblemon species id and the power it holds ({@code FLASH}, {@code STRENGTH}, {@code CUT}, {@code SURF}). */
	public record Partner(int player, String species, String power) {
	}

	/** What the world shows of a {@code power_spot} step (T8.3): the power asked, how far it got, who may act. {@code anyone}: nobody holds it, every member may. */
	public record PowerStep(String step, String power, int done, int total, int radius, List<Integer> holders, boolean anyone, boolean solved, boolean open, Spot spot) {

		public boolean mayAct(int player) {
			return anyone || holders.contains(player);
		}
	}

	/** The slot a {@code power_spot} step is bound to, in the room's own frame: it is known before the step opens (an obstacle stands there from the start). */
	public record Spot(String id, int x, int y, int z, int sx, int sy, int sz, String orientation) {
	}

	/** The partner of {@code player}, or null. */
	public Partner partnerOf(int player) {
		for (Partner p : partners) {
			if (p.player() == player) {
				return p;
			}
		}
		return null;
	}

	/** The {@code power_spot} step whose {@code spot} role is bound to {@code slotId}, or null. */
	public PowerStep powerOf(String slotId) {
		SlotView slot = slot(slotId);
		if (slot == null) {
			return null;
		}
		for (Show show : slot.shows()) {
			if (show.isPuzzle() && "power_spot".equals(show.mechanism())) {
				for (PowerStep p : powers) {
					if (p.step().equals(show.step())) {
						return p;
					}
				}
			}
		}
		return null;
	}

	/** A counter an open step shows on the HUD (D61: "objects 2 / 3"). */
	public record Progress(String step, int done, int total) {
	}

	/**
	 * The key of the room among all those known: rooms of earlier rounds stay in the hub (D8: the free time visits every room
	 * opened so far), so a room is (round, group). A room whose message carried no round has round 0 and key = its group.
	 */
	public int key() {
		return round * 10 + group;
	}

	/** Where the room stands in the hub: the plan's place, its minimum corner (hub x and z, world y) and its orientation. */
	public record Place(String id, int x, int y, int z, String orientation) {
	}

	/** An active slot: position (minimum corner) and size in the room's own frame, the side it faces, what it shows. */
	public record SlotView(String id, String kind, int x, int y, int z, int sx, int sy, int sz, String orientation, List<Show> shows) {

		/** The first puzzle the slot shows that is not solved yet, or null. */
		public Show openPuzzle() {
			for (Show s : shows) {
				if (s.isPuzzle() && !s.solved()) {
					return s;
				}
			}
			return null;
		}

		/** Whether a puzzle of this slot is solved and none is left to do. */
		public boolean solvedPuzzle() {
			boolean any = false;
			for (Show s : shows) {
				if (s.isPuzzle()) {
					any = true;
					if (!s.solved()) {
						return false;
					}
				}
			}
			return any;
		}

		/** The first item to pick up here, or null. */
		public Clue item() {
			for (Show s : shows) {
				if (s.clue() != null && s.clue().isItem()) {
					return s.clue();
				}
			}
			return null;
		}

		/** The clues a player can read here (everything but items), in the order the backend lists them. */
		public List<Clue> readable() {
			List<Clue> out = new ArrayList<>();
			for (Show s : shows) {
				if (s.clue() != null && !s.clue().isItem()) {
					out.add(s.clue());
				}
			}
			return out;
		}

		/** The room's exit slot: it is drawn by the plan's blocks, not by an object of ours. */
		public boolean isExit() {
			return "exit".equals(kind);
		}
	}

	/** One thing a slot shows: a {@code puzzle}, a {@code clue} or the {@code exit}. */
	public record Show(String type, String step, String role, String mechanism, boolean solved, boolean open, Clue clue) {

		public boolean isPuzzle() {
			return "puzzle".equals(type);
		}
	}

	/** A clue: {@code type} is text, digits, symbols, colors, image or item; {@code value} a String or a List of Strings. */
	public record Clue(String role, String type, Object value) {

		public boolean isItem() {
			return "item".equals(type);
		}

		/** The value as one string (a list is joined by spaces). */
		public String text() {
			if (value instanceof List<?> list) {
				StringBuilder sb = new StringBuilder();
				for (Object o : list) {
					if (sb.length() > 0) {
						sb.append(' ');
					}
					sb.append(o);
				}
				return sb.toString();
			}
			return value == null ? "" : value.toString();
		}

		/** The value as a list of strings (a plain string is a list of one). */
		public List<String> list() {
			List<String> out = new ArrayList<>();
			if (value instanceof List<?> list) {
				for (Object o : list) {
					out.add(String.valueOf(o));
				}
			} else if (value != null) {
				out.add(value.toString());
			}
			return out;
		}
	}

	/** An item of the bag and the player index who carries it ({@code -1}: nobody in particular, any member). */
	public record ItemView(String id, int holder) {
	}

	private static final Set<String> READABLE = Set.of("text", "digits", "symbols", "colors", "image");

	public static boolean readable(String clueType) {
		return READABLE.contains(clueType);
	}

	/** Parses a message's data; null when it has no group or room (malformed). */
	public static RoomView parse(Map<String, Object> data) {
		if (!(data.get("group") instanceof Number group) || !(data.get("room") instanceof String room)) {
			return null;
		}
		List<Integer> members = new ArrayList<>();
		for (Object o : list(data.get("members"))) {
			if (o instanceof Number n) {
				members.add(n.intValue());
			}
		}
		List<SlotView> slots = new ArrayList<>();
		for (Object o : list(data.get("slots"))) {
			Map<String, Object> s = map(o);
			if (!(s.get("id") instanceof String id)) {
				continue;
			}
			Map<String, Object> pos = map(s.get("position"));
			Map<String, Object> size = map(s.get("size"));
			List<Show> shows = new ArrayList<>();
			for (Object so : list(s.get("shows"))) {
				Map<String, Object> show = map(so);
				Clue clue = null;
				if (show.get("clue") instanceof Map<?, ?>) {
					Map<String, Object> c = map(show.get("clue"));
					clue = new Clue(str(c.get("role"), ""), str(c.get("type"), "text"), c.get("value"));
				}
				shows.add(new Show(str(show.get("type"), ""), str(show.get("step"), ""), str(show.get("role"), ""), str(show.get("mechanism"), ""),
						Boolean.TRUE.equals(show.get("solved")), Boolean.TRUE.equals(show.get("open")), clue));
			}
			slots.add(new SlotView(id, str(s.get("kind"), ""), num(pos.get("x")), num(pos.get("y")), num(pos.get("z")),
					Math.max(1, num(size.get("x"))), Math.max(1, num(size.get("y"))), Math.max(1, num(size.get("z"))),
					str(s.get("orientation"), "north"), List.copyOf(shows)));
		}
		List<ItemView> items = new ArrayList<>();
		for (Object o : list(data.get("items"))) {
			Map<String, Object> i = map(o);
			if (i.get("id") instanceof String id) {
				items.add(new ItemView(id, i.get("holder") instanceof Number h ? h.intValue() : -1));
			}
		}
		Place place = null;
		if (data.get("place") instanceof Map<?, ?>) {
			Map<String, Object> p = map(data.get("place"));
			Map<String, Object> origin = map(p.get("origin"));
			place = new Place(str(p.get("id"), ""), num(origin.get("x")), num(origin.get("y")), num(origin.get("z")), str(p.get("orientation"), "north"));
		}
		List<String> hints = new ArrayList<>();
		List<Progress> progress = new ArrayList<>();
		List<PowerStep> powers = new ArrayList<>();
		for (Object so : list(data.get("steps"))) {
			Map<String, Object> step = map(so);
			if (step.get("world") instanceof Map<?, ?> wm && wm.containsKey("power")) {
				Map<String, Object> w = map(step.get("world"));
				List<Integer> holders = new ArrayList<>();
				for (Object h : list(w.get("holders"))) {
					if (h instanceof Number n) {
						holders.add(n.intValue());
					}
				}
				Map<String, Object> bound = map(map(w.get("bound")).get("spot"));
				Map<String, Object> bp = map(bound.get("position"));
				Map<String, Object> bs = map(bound.get("size"));
				Spot spot = bound.isEmpty() ? null : new Spot(str(bound.get("id"), ""), num(bp.get("x")), num(bp.get("y")), num(bp.get("z")),
						Math.max(1, num(bs.get("x"))), Math.max(1, num(bs.get("y"))), Math.max(1, num(bs.get("z"))), str(bound.get("orientation"), "north"));
				powers.add(new PowerStep(str(step.get("id"), ""), str(w.get("power"), ""), num(w.get("done")), Math.max(1, num(w.get("total"))),
						Math.max(1, num(w.get("radius"))), List.copyOf(holders), Boolean.TRUE.equals(w.get("anyone")), Boolean.TRUE.equals(step.get("solved")),
						Boolean.TRUE.equals(step.get("open")), spot));
			}
			for (Object h : list(step.get("hints"))) {
				if (h instanceof String text) {
					hints.add(text);
				}
			}
			if (step.get("progress") instanceof Map<?, ?>) {
				Map<String, Object> p = map(step.get("progress"));
				progress.add(new Progress(str(step.get("id"), ""), num(p.get("done")), num(p.get("total"))));
			}
		}
		List<Partner> partners = new ArrayList<>();
		for (Object o : list(data.get("partners"))) {
			Map<String, Object> p = map(o);
			if (p.get("player") instanceof Number n && p.get("species") instanceof String species) {
				partners.add(new Partner(n.intValue(), species, str(p.get("power"), "")));
			}
		}
		return new RoomView(str(data.get("hub"), ""), data.get("round") instanceof Number round ? Math.max(0, round.intValue()) : 0, group.intValue(), room, str(data.get("name"), room), List.copyOf(members),
				Boolean.TRUE.equals(data.get("exit_open")), data.get("finished_at") != null, List.copyOf(slots), List.copyOf(items), place, List.copyOf(hints), List.copyOf(progress), List.copyOf(partners), str(data.get("ghost_partner"), "gastly"), List.copyOf(powers));
	}

	/** Items the player carries (or that anybody may handle). */
	public List<ItemView> carriedBy(int player) {
		List<ItemView> out = new ArrayList<>();
		for (ItemView i : items) {
			if (i.holder() == player || i.holder() < 0) {
				out.add(i);
			}
		}
		return out;
	}

	/** Items carried by teammates of {@code player}. */
	public List<ItemView> carriedByOthers(int player) {
		List<ItemView> out = new ArrayList<>();
		for (ItemView i : items) {
			if (i.holder() >= 0 && i.holder() != player) {
				out.add(i);
			}
		}
		return out;
	}

	public SlotView slot(String id) {
		for (SlotView s : slots) {
			if (s.id().equals(id)) {
				return s;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- JSON helpers (numbers arrive as Double)

	private static int num(Object o) {
		return o instanceof Number n ? n.intValue() : 0;
	}

	private static String str(Object o, String fallback) {
		return o instanceof String s ? s : fallback;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object o) {
		return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
	}

	@SuppressWarnings("unchecked")
	private static List<Object> list(Object o) {
		return o instanceof List<?> l ? (List<Object>) l : List.of();
	}
}
