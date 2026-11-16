package com.mystaria.phantasmon_backend.events.trahison.room;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Clue;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ClueType;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Mechanism;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The rooms being played: one {@link RoomInstance} per (hub, round, group). A game keeps the rooms of its earlier rounds
 * (D8: the free time visits every room opened so far); the methods without a round address the <b>latest</b> room of the
 * group (the current round's, or the only one outside a game). Started with a definition and a seed (same seed,
 * same solutions), it applies the players' actions, opens the steps whose {@code needs} are done, detects the end and
 * tells the {@link RoomListener}s. Memory only (the game keeps a {@link #snapshot} of each room in its own state and
 * {@link #restore}s them after a restart); independent of {@code GameService} (it does not know the game: the caller
 * gives the group's player indices and checks the stage). Thread-safe: each instance is guarded by its own lock.
 * <p>
 * Lock order: {@code GameService} (the game) first, then the room. This service never calls the game, and its listeners
 * must not take the game's lock (they work from a snapshot of the audience).
 */
public class RoomService {

	private static final Logger LOG = LoggerFactory.getLogger(RoomService.class);

	private final MechanismRegistry registry;
	private final Clock clock;
	private final Map<String, RoomInstance> rooms = new ConcurrentHashMap<>();
	private final List<RoomListener> listeners = new CopyOnWriteArrayList<>();

	public RoomService(MechanismRegistry registry, Clock clock) {
		this.registry = registry;
		this.clock = clock;
	}

	public void addListener(RoomListener listener) {
		listeners.add(listener);
	}

	private static String key(String hub, int round, int group) {
		return hub + "#" + round + "#" + group;
	}

	/** The generator of one step: stable for (seed, step id), independent of the other steps. */
	static SplittableRandom rngFor(long seed, String stepId) {
		long h = 0xcbf29ce484222325L;
		for (int i = 0; i < stepId.length(); i++) {
			h = (h ^ stepId.charAt(i)) * 0x100000001b3L;
		}
		return new SplittableRandom(seed * 0x9E3779B97F4A7C15L + h);
	}

	// ---------------------------------------------------------------- life

	/** Starts (or replaces) the room of a group. {@code members}: the player indices of the group. */
	public RoomInstance start(String hub, int group, Collection<Integer> members, RoomDefinition definition, long seed) {
		return start(hub, group, members, definition, seed, null);
	}

	/** As {@link #start(String, int, Collection, RoomDefinition, long)}, with the place of the hub the room stands in. */
	public RoomInstance start(String hub, int group, Collection<Integer> members, RoomDefinition definition, long seed,
			RoomPlace place) {
		return start(hub, 0, group, members, definition, seed, place);
	}

	/** Starts (or replaces) the room of {@code group} in round {@code round} of the hub's game. */
	public RoomInstance start(String hub, int round, int group, Collection<Integer> members, RoomDefinition definition,
			long seed, RoomPlace place) {
		RoomInstance room = build(hub, round, group, members, definition, seed);
		room.place = place;
		rooms.put(key(hub, round, group), room);
		synchronized (room) {
			notifyRoom(room);
		}
		return room;
	}

	/** Back to zero with another seed: new solutions, same room and group, times restarted. */
	public RoomInstance reset(String hub, int group, long newSeed) {
		RoomInstance old = require(hub, group);
		RoomInstance room;
		synchronized (old) {
			room = build(hub, old.round, group, old.members, old.definition, newSeed);
			room.place = old.place;
			rooms.put(key(hub, old.round, group), room);
		}
		synchronized (room) {
			for (StepRuntime s : room.steps.values()) {
				notifyPuzzle(room, new PuzzleUpdate(s.step.id(), "reset", null, List.of(), null));
			}
			notifyRoom(room);
		}
		return room;
	}

	public void remove(String hub, int group) {
		RoomInstance latest = latest(hub, group);
		if (latest != null) {
			rooms.remove(key(hub, latest.round, group));
		}
	}

	/** Drops every room of a hub (a new round starts, or the game ended). Nobody is told. */
	public void removeAll(String hub) {
		rooms.values().removeIf(r -> r.hub.equals(hub));
	}

	/** True when the group's room exists and its exit is open. */
	public boolean isFinished(String hub, int group) {
		RoomInstance room = latest(hub, group);
		if (room == null) {
			return false;
		}
		synchronized (room) {
			return room.exitOpen;
		}
	}

	/**
	 * Solves every remaining step and opens the exit at once (the provisional {@code debug_solve}, bots). Does nothing
	 * when the room is already finished. Tells the listeners like a real finish would.
	 */
	public RoomInstance forceSolve(String hub, int group) {
		return forceSolve(require(hub, group));
	}

	/** As {@link #forceSolve(String, int)}, for the room of an explicit round. */
	public RoomInstance forceSolve(String hub, int round, int group) {
		return forceSolve(require(hub, round, group));
	}

	private RoomInstance forceSolve(RoomInstance room) {
		synchronized (room) {
			if (room.exitOpen) {
				return room;
			}
			Instant now = clock.instant();
			for (StepRuntime s : room.steps.values()) {
				if (s.solved) {
					continue;
				}
				if (!s.open) {
					s.open = true;
					s.openedAt = now;
				}
				s.solved = true;
				s.solvedAt = now;
				notifyPuzzle(room, new PuzzleUpdate(s.step.id(), "solved", null, List.of(), null));
			}
			room.lastProgressAt = now;
			room.finishedAt = now;
			room.exitOpen = true;
			notifyRoom(room);
			return room;
		}
	}

	// ---------------------------------------------------------------- persistence

	/** The JSON form of a room (everything needed to resume it, solution included: it never leaves the backend). */
	public ObjectNode snapshot(String hub, int group) {
		return snapshot(require(hub, group));
	}

	/** As {@link #snapshot(String, int)}, for the room of an explicit round. */
	public ObjectNode snapshot(String hub, int round, int group) {
		return snapshot(require(hub, round, group));
	}

	private static ObjectNode snapshot(RoomInstance room) {
		synchronized (room) {
			return RoomSnapshot.write(room);
		}
	}

	/** Puts back a room saved by {@link #snapshot} (replacing the group's room). Nobody is told. */
	public RoomInstance restore(JsonNode snapshot) {
		return restore(snapshot, 0);
	}

	/** As {@link #restore(JsonNode)}; {@code defaultRound}: the round of a snapshot saved before rooms knew theirs. */
	public RoomInstance restore(JsonNode snapshot, int defaultRound) {
		RoomInstance room = RoomSnapshot.read(snapshot, registry, defaultRound);
		rooms.put(key(room.hub, room.round, room.group), room);
		return room;
	}

	/** The latest room of the group (highest round), if any. */
	public Optional<RoomInstance> find(String hub, int group) {
		return Optional.ofNullable(latest(hub, group));
	}

	public Optional<RoomInstance> find(String hub, int round, int group) {
		return Optional.ofNullable(rooms.get(key(hub, round, group)));
	}

	/** Every room of the hub, rounds in order, then groups. */
	public List<RoomInstance> rooms(String hub) {
		List<RoomInstance> out = new ArrayList<>();
		rooms.forEach((k, r) -> {
			if (r.hub.equals(hub)) {
				out.add(r);
			}
		});
		out.sort(java.util.Comparator.<RoomInstance>comparingInt(r -> r.round).thenComparingInt(r -> r.group));
		return out;
	}

	/** The room of this group in the latest round that has one, or null. */
	private RoomInstance latest(String hub, int group) {
		RoomInstance best = null;
		for (RoomInstance r : rooms.values()) {
			if (r.hub.equals(hub) && r.group == group && (best == null || r.round > best.round)) {
				best = r;
			}
		}
		return best;
	}

	private RoomInstance build(String hub, int round, int group, Collection<Integer> members, RoomDefinition definition, long seed) {
		Instant now = clock.instant();
		RoomInstance room = new RoomInstance(hub, round, group, definition, seed, List.copyOf(members));
		for (Step step : definition.steps()) {
			Mechanism m = registry.get(step.mechanism());
			room.steps.put(step.id(), new StepRuntime(step, m, m.generate(rngFor(seed, step.id()), step.params())));
		}
		room.startedAt = now;
		room.lastProgressAt = now;
		openReady(room, now);
		return room;
	}

	/** Opens every unsolved step whose needs are all solved. */
	private static void openReady(RoomInstance room, Instant now) {
		for (StepRuntime s : room.steps.values()) {
			if (!s.open && !s.solved && s.step.needs().stream().allMatch(n -> room.steps.get(n).solved)) {
				s.open = true;
				s.openedAt = now;
			}
		}
	}

	// ---------------------------------------------------------------- views

	public ObjectNode roomView(String hub, int group, ViewerKind viewer) {
		return roomView(require(hub, group), viewer);
	}

	/** As {@link #roomView(String, int, ViewerKind)}, for the room of an explicit round. */
	public ObjectNode roomView(String hub, int round, int group, ViewerKind viewer) {
		return roomView(require(hub, round, group), viewer);
	}

	private static ObjectNode roomView(RoomInstance room, ViewerKind viewer) {
		synchronized (room) {
			return RoomViews.room(room, viewer);
		}
	}

	public ObjectNode puzzleView(String hub, int group, String stepId, ViewerKind viewer) {
		return puzzleView(require(hub, group), stepId, viewer);
	}

	/** As {@link #puzzleView(String, int, String, ViewerKind)}, for the room of an explicit round. */
	public ObjectNode puzzleView(String hub, int round, int group, String stepId, ViewerKind viewer) {
		return puzzleView(require(hub, round, group), stepId, viewer);
	}

	private static ObjectNode puzzleView(RoomInstance room, String stepId, ViewerKind viewer) {
		synchronized (room) {
			if (!room.steps.containsKey(stepId)) {
				throw new RoomException(RoomException.BAD_ACTION, "unknown step " + stepId);
			}
			return RoomViews.puzzle(room, new PuzzleUpdate(stepId, "open", null, List.of(), null), viewer);
		}
	}

	// ---------------------------------------------------------------- actions

	/** Applies an action of a member of the group in the round's play; refused ones raise a {@link RoomException}. */
	public ActionResult apply(String hub, int group, int player, RoomAction action) {
		return apply(hub, group, player, action, false);
	}

	/**
	 * Applies an action of the player with this index. {@code free}: the free time (D8): any player may act in any room, not
	 * only the group's members, a finished room keeps taking actions, and {@link RoomAction.Replay} puts the room back to a
	 * fresh state. The caller (the game) checks the stage and that the player lives.
	 */
	public ActionResult apply(String hub, int group, int player, RoomAction action, boolean free) {
		return apply(require(hub, group), group, player, action, free);
	}

	/** As {@link #apply(String, int, int, RoomAction, boolean)}, in the room of an explicit round. */
	public ActionResult apply(String hub, int round, int group, int player, RoomAction action, boolean free) {
		return apply(require(hub, round, group), group, player, action, free);
	}

	private ActionResult apply(RoomInstance room, int group, int player, RoomAction action, boolean free) {
		synchronized (room) {
			if (!free && !room.members.contains(player)) {
				throw new RoomException(RoomException.NOT_IN_ROOM, "player " + player + " is not in group " + group);
			}
			if (room.exitOpen && !free) {
				throw new RoomException(RoomException.ROOM_FINISHED, "the room is finished");
			}
			return switch (action) {
				case RoomAction.UseSlot a -> useSlot(room, player, a.slot());
				case RoomAction.PuzzleInput a -> puzzleInput(room, player, a.step(), a.input());
				case RoomAction.PickUp a -> pickUp(room, player, a.slot());
				case RoomAction.GiveItem a -> giveItem(room, player, a.item(), a.to(), free);
				case RoomAction.UseItem a -> useItem(room, player, a.item(), a.slot());
				case RoomAction.CombineItems a -> combine(room, player, a.a(), a.b());
				case RoomAction.Replay a -> {
					if (!free) {
						throw new RoomException(RoomException.BAD_ACTION, "a room is replayed in the free time only");
					}
					replay(room);
					yield new ActionResult(null, false, false, List.of());
				}
			};
		}
	}

	/** The seed of replay number {@code n} of a room started with {@code seed}: stable, and never the one before. */
	static long replaySeed(long seed, int n) {
		long h = seed ^ (n * 0x9E3779B97F4A7C15L);
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		return h ^ (h >>> 31);
	}

	/**
	 * D8: puts the room back to a fresh puzzle state with a <b>new seed</b> derived from the first one and the replay count
	 * (new solutions, so a replay is a real puzzle again and not a recital of what the group remembers; the same replay
	 * number always gives the same seed). Same members and place, empty bags, times restarted. It gives no points and opens
	 * nothing: that is the game's business and it does not look at a replayed room. Called with the room locked.
	 */
	private void replay(RoomInstance old) {
		RoomInstance room = build(old.hub, old.round, old.group, old.members, old.definition, replaySeed(old.seed, old.replays + 1));
		room.place = old.place;
		room.replays = old.replays + 1;
		rooms.put(key(old.hub, old.round, old.group), room);
		synchronized (room) {
			for (StepRuntime s : room.steps.values()) {
				notifyPuzzle(room, new PuzzleUpdate(s.step.id(), "reset", null, List.of(), null));
			}
			notifyRoom(room);
		}
	}

	private ActionResult useSlot(RoomInstance room, int player, String slotId) {
		Slot slot = room.definition.slot(slotId).orElseThrow(
				() -> new RoomException(RoomException.SLOT_INACTIVE, "unknown slot " + slotId));
		if (RoomViews.shows(room, slot).isEmpty()) {
			throw new RoomException(RoomException.SLOT_INACTIVE, "slot " + slotId + " shows nothing");
		}
		for (StepRuntime s : room.steps.values()) {
			if (!s.open || s.solved) {
				continue;
			}
			for (var role : s.step.slots().entrySet()) {
				if (role.getValue().equals(slotId)) {
					notifyPuzzle(room, new PuzzleUpdate(s.step.id(), "open", null, List.of(), player));
					InputResult r = run(() -> s.mechanism.onUse(s.state, player, role.getKey()));
					boolean solved = commit(room, s, player, r);
					return new ActionResult(s.step.id(), solved, room.exitOpen, List.of());
				}
			}
		}
		return new ActionResult(null, false, false, List.of());
	}

	private ActionResult puzzleInput(RoomInstance room, int player, String stepId, tools.jackson.databind.JsonNode input) {
		StepRuntime s = room.steps.get(stepId);
		if (s == null) {
			throw new RoomException(RoomException.BAD_ACTION, "unknown step " + stepId);
		}
		if (s.solved) {
			throw new RoomException(RoomException.STEP_SOLVED, "step " + stepId + " is solved");
		}
		if (!s.open) {
			throw new RoomException(RoomException.STEP_NOT_OPEN, "step " + stepId + " is not open");
		}
		InputResult r = run(() -> s.mechanism.onInput(s.state, player, input));
		boolean solved = commit(room, s, player, r);
		return new ActionResult(stepId, solved, room.exitOpen, List.of());
	}

	private static InputResult run(java.util.function.Supplier<InputResult> gesture) {
		try {
			return gesture.get();
		} catch (IllegalArgumentException ex) {
			throw new RoomException(RoomException.BAD_INPUT, String.valueOf(ex.getMessage()));
		}
	}

	/** Stores a mechanism's result, tells the watchers, and moves the chain forward when the step is solved. */
	private boolean commit(RoomInstance room, StepRuntime s, int player, InputResult r) {
		s.state = r.state();
		boolean changed = !r.events().isEmpty() || r.solved();
		if (r.solved()) {
			Instant now = clock.instant();
			s.solved = true;
			s.solvedAt = now;
			room.lastProgressAt = now;
			notifyPuzzle(room, new PuzzleUpdate(s.step.id(), "solved", player, r.events(), null));
			openReady(room, now);
			if (room.steps.values().stream().allMatch(x -> x.solved)) {
				room.exitOpen = true;
				room.finishedAt = now;
			}
			notifyRoom(room);
		} else if (changed) {
			notifyPuzzle(room, new PuzzleUpdate(s.step.id(), "input", player, r.events(), null));
		}
		return r.solved();
	}

	private ActionResult pickUp(RoomInstance room, int player, String slotId) {
		Slot slot = room.definition.slot(slotId).orElseThrow(
				() -> new RoomException(RoomException.NOTHING_TO_PICK, "unknown slot " + slotId));
		List<String> taken = new ArrayList<>();
		for (StepRuntime s : room.steps.values()) {
			for (Give g : s.step.gives()) {
				if (!g.slot().equals(slot.id()) || !(g.whenSolved() ? s.solved : s.open || s.solved)) {
					continue;
				}
				for (Clue clue : s.state.clues()) {
					String k = RoomViews.pickKey(s.step.id(), clue.role());
					if (clue.role().equals(g.clue()) && clue.type() == ClueType.ITEM && !room.picked.contains(k)) {
						room.picked.add(k);
						String item = clue.value().asString("");
						room.addItem(item, player);
						taken.add(item);
					}
				}
			}
		}
		if (taken.isEmpty()) {
			throw new RoomException(RoomException.NOTHING_TO_PICK, "nothing to pick up in " + slotId);
		}
		room.lastProgressAt = clock.instant();
		notifyRoom(room);
		return new ActionResult(null, false, false, taken);
	}

	/** Hands an item over to another member of the group: only the holder gives. (Distance is the game's to check.) */
	private ActionResult giveItem(RoomInstance room, int player, String item, int to, boolean free) {
		if (!free && !room.members.contains(to) || to == player) {
			throw new RoomException(RoomException.BAD_ACTION, "player " + to + " cannot receive the item");
		}
		int at = room.indexOfHeld(item, player);
		if (at < 0) {
			throw new RoomException(RoomException.NO_SUCH_ITEM, "player " + player + " does not carry " + item);
		}
		room.holders.set(at, to);
		notifyRoom(room);
		return new ActionResult(null, false, false, List.of());
	}

	/** Uses an item on an active slot: the open step bound to it must take it, and then the item is consumed. */
	private ActionResult useItem(RoomInstance room, int player, String item, String slotId) {
		Slot slot = room.definition.slot(slotId).orElseThrow(
				() -> new RoomException(RoomException.SLOT_INACTIVE, "unknown slot " + slotId));
		if (RoomViews.shows(room, slot).isEmpty()) {
			throw new RoomException(RoomException.SLOT_INACTIVE, "slot " + slotId + " shows nothing");
		}
		int at = room.indexOfHeld(item, player);
		if (at < 0) {
			throw new RoomException(RoomException.NO_SUCH_ITEM, "player " + player + " does not carry " + item);
		}
		for (StepRuntime s : room.steps.values()) {
			if (!s.open || s.solved) {
				continue;
			}
			for (var role : s.step.slots().entrySet()) {
				if (!role.getValue().equals(slotId)) {
					continue;
				}
				Optional<InputResult> handled = runItem(() -> s.mechanism.onItem(s.state, player, role.getKey(), item));
				if (handled.isPresent()) {
					room.removeItemAt(at);
					room.lastProgressAt = clock.instant();
					boolean solved = commit(room, s, player, handled.get());
					if (!solved) {
						notifyRoom(room); // the bag changed
					}
					return new ActionResult(s.step.id(), solved, room.exitOpen, List.of());
				}
			}
		}
		throw new RoomException(RoomException.ITEM_REFUSED, item + " does nothing at " + slotId);
	}

	private static Optional<InputResult> runItem(java.util.function.Supplier<Optional<InputResult>> gesture) {
		try {
			return gesture.get();
		} catch (IllegalArgumentException ex) {
			throw new RoomException(RoomException.BAD_INPUT, String.valueOf(ex.getMessage()));
		}
	}

	/** Combines two items the actor carries (a recipe of the room): both are consumed, the result is his. */
	private ActionResult combine(RoomInstance room, int player, String a, String b) {
		Combine recipe = room.definition.combine(a, b).orElseThrow(
				() -> new RoomException(RoomException.NO_COMBINATION, a + " and " + b + " do not combine"));
		int first = room.indexOfHeld(a, player);
		if (first < 0) {
			throw new RoomException(RoomException.NO_SUCH_ITEM, "player " + player + " does not carry " + a);
		}
		int second = room.indexOfHeld(b, player, first); // another copy when both items are the same
		if (second < 0) {
			throw new RoomException(RoomException.NO_SUCH_ITEM, "player " + player + " does not carry " + b);
		}
		room.removeItemAt(Math.max(first, second));
		room.removeItemAt(Math.min(first, second));
		room.addItem(recipe.result(), player);
		room.lastProgressAt = clock.instant();
		notifyRoom(room);
		return new ActionResult(null, false, false, List.of(recipe.result()));
	}

	/**
	 * The next help text for the group: the first open, unsolved step (in definition order) that still has one. Empty
	 * when there is none. The caller decides when (10, 18, 25 minutes without progress: {@link RoomInstance#lastProgressAt}).
	 */
	public Optional<Hint> giveHint(String hub, int group) {
		RoomInstance room = require(hub, group);
		synchronized (room) {
			for (StepRuntime s : room.steps.values()) {
				if (s.open && !s.solved && s.hintsGiven < s.step.hints().size()) {
					s.hintsGiven++;
					return Optional.of(new Hint(s.step.id(), s.hintsGiven, s.step.hints().get(s.hintsGiven - 1)));
				}
			}
			return Optional.empty();
		}
	}

	/** The latest room of the group. */
	private RoomInstance require(String hub, int group) {
		RoomInstance room = latest(hub, group);
		if (room == null) {
			throw new RoomException(RoomException.NO_ROOM, "no room for group " + group + " in hub " + hub);
		}
		return room;
	}

	private RoomInstance require(String hub, int round, int group) {
		RoomInstance room = rooms.get(key(hub, round, group));
		if (room == null) {
			throw new RoomException(RoomException.NO_ROOM, "no room for group " + group + " of round " + round + " in hub " + hub);
		}
		return room;
	}

	private void notifyRoom(RoomInstance room) {
		for (RoomListener l : listeners) {
			try {
				l.roomChanged(room);
			} catch (RuntimeException ex) {
				LOG.warn("room listener failed", ex);
			}
		}
	}

	private void notifyPuzzle(RoomInstance room, PuzzleUpdate update) {
		for (RoomListener l : listeners) {
			try {
				l.puzzleChanged(room, update);
			} catch (RuntimeException ex) {
				LOG.warn("room listener failed", ex);
			}
		}
	}
}
