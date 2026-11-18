package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;
import com.mystaria.phantasmon_backend.events.trahison.layout.RoomSlot;
import com.mystaria.phantasmon_backend.events.trahison.room.ActionResult;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomAction;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomDefinition;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomDraw;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomInstance;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomPlace;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomSecrets;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomService;
import com.mystaria.phantasmon_backend.events.trahison.room.Slot;
import com.mystaria.phantasmon_backend.events.trahison.room.Step;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.DebugButton;
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;

import tools.jackson.databind.JsonNode;

/**
 * The link between a game and the room engine ({@link RoomService}): starts the three rooms of a round, forwards the
 * players' room actions, keeps each room's saved state in the round ({@link RoundState#rooms}) and puts the rooms back
 * after a restart. Every method is called by {@link GameService} <b>with the game locked</b> (lock order: game, then
 * room), which also makes the saved state consistent with the game's.
 */
public final class GameRooms {

	private static final Logger log = LoggerFactory.getLogger(GameRooms.class);
	private static final long GOLDEN = 0x9E3779B97F4A7C15L;
	private static final long SALT = 0x524F4F4DL;
	/** The id of the built-in room used when none is chosen. */
	public static final String FALLBACK_ID = "fallback";

	private final RoomService rooms;
	private final Function<String, Optional<RoomDefinition>> definitions;

	/** @param definitions looks a room up in the bank by id (empty when unknown) */
	public GameRooms(RoomService rooms, Function<String, Optional<RoomDefinition>> definitions) {
		this.rooms = rooms;
		this.definitions = definitions;
	}

	/** One button to press, and the exit: the room used when none is chosen, so a game always works. */
	public static RoomDefinition fallbackDefinition() {
		return new RoomDefinition(FALLBACK_ID, "Fallback room", null, 1, 1, 10,
				List.of(new Slot("btn", "terminal", new Pos(2, 1, 2), "north", null),
						new Slot("exit", "exit", new Pos(40, 0, 20), "north", null)),
				List.of(new Step("press", DebugButton.ID, null, Map.of("exit", "exit"), List.of(), List.of(), List.of())),
				null);
	}

	/** The seed of the room of group {@code group} in a round: stable, different for each group. */
	static long roomSeed(long roundSeed, int group) {
		return new SplittableRandom(roundSeed ^ ((group + 1) * GOLDEN) ^ SALT).nextLong();
	}

	/**
	 * The room of group {@code group} in round {@code round}: the game's draw (T4.1; the rounds past the third replay the
	 * cycle), else the settings' test room, else the fallback.
	 */
	private RoomDefinition definitionFor(Game g, int round, int group) {
		if (g.draw != null && (g.settings.testRoom == null || g.settings.testRoom.isBlank())) {
			int cycle = (Math.max(1, round) - 1) % 3 + 1;
			RoomDraw.Pick pick = g.draw.pick(cycle, group);
			if (pick != null) {
				try {
					Optional<RoomDefinition> found = definitions.apply(pick.room());
					if (found.isPresent()) {
						return found.get();
					}
					log.warn("Trahison: drawn room {} no longer in the bank, using the fallback room", pick.room());
				} catch (RuntimeException ex) {
					log.warn("Trahison: could not load drawn room {}, using the fallback room", pick.room(), ex);
				}
				return fallbackDefinition();
			}
		}
		return definitionFor(g.settings);
	}

	private RoomDefinition definitionFor(Settings settings) {
		String id = settings.testRoom;
		if (id != null && !id.isBlank()) {
			try {
				Optional<RoomDefinition> found = definitions.apply(id);
				if (found.isPresent()) {
					return found.get();
				}
				log.warn("Trahison: test room {} not found, using the fallback room", id);
			} catch (RuntimeException ex) {
				log.warn("Trahison: could not load test room {}, using the fallback room", id, ex);
			}
		}
		return fallbackDefinition();
	}

	/** The doors just closed: starts the room of each group of the round (the split is known). */
	void startRound(Game g, RoundState r) {
		startRound(g, r, LayoutContent.empty());
	}

	/**
	 * As {@link #startRound(Game, RoundState)}, putting each room in a place of the plan: the places of the round, in plan
	 * order ({@code roomSlots} whose {@code round} is the round's, a final-loop round counting as the cycle of three), the
	 * k-th for group k; a group without a place has none.
	 */
	void startRound(Game g, RoundState r, LayoutContent plan) {
		if (g.draw != null) {
			g.draw = GameDraws.assignPlaces(g.draw, plan); // records the place and the turn each room really got
		}
		r.rooms = new ArrayList<>();
		List<RoomSlot> places = placesOf(plan, r.number);
		for (int k = 0; k < 3; k++) {
			List<Integer> members = List.copyOf(r.split().group(Door.ALL.get(k)).members());
			RoomSlot place = k < places.size() ? places.get(k) : null;
			rooms.start(g.hub, r.number, k, members, definitionFor(g, r.number, k), roomSeed(r.seed, k),
					place == null || place.origin() == null ? null
							: new RoomPlace(place.id(), place.origin(), place.orientation() == null ? "north" : place.orientation()));
			r.rooms.add(rooms.snapshot(g.hub, r.number, k));
		}
	}

	/** The plan's room places of a round, in plan order (rounds past the third reuse the cycle: 4 as 1...). */
	static List<RoomSlot> placesOf(LayoutContent plan, int roundNumber) {
		int cycle = (Math.max(1, roundNumber) - 1) % 3 + 1;
		List<RoomSlot> out = new ArrayList<>();
		for (RoomSlot s : plan.roomSlots()) {
			if (s.round() != null && s.round() == cycle) {
				out.add(s);
			}
		}
		return out;
	}

	/**
	 * The hiding places the rooms of round {@code r} offer the item draw: the live secret of each room that stands in a place of the
	 * plan (spot ids, see {@link RoomSecrets#spotId}), in group order. The inert candidates are not listed.
	 */
	List<String> secretSpots(Game g, RoundState r) {
		List<String> out = new ArrayList<>();
		for (int k = 0; k < 3; k++) {
			Optional<RoomInstance> room = rooms.find(g.hub, r.number, k);
			if (room.isPresent() && room.get().place != null && room.get().activeSecret != null) {
				out.add(RoomSecrets.spotId(r.number, k, room.get().activeSecret));
			}
		}
		return out;
	}

	/** The room of (round, group) when it stands in the plan and the slot is one of its secret candidates (live or inert), else empty. */
	Optional<RoomInstance> roomWithCandidate(Game g, int round, int group, String slot) {
		return rooms.find(g.hub, round, group)
				.filter(room -> room.place != null && RoomSecrets.candidates(room.definition).contains(slot));
	}

	/**
	 * The box of a slot in hub coordinates, {@code {minX, minY, minZ, maxX, maxY, maxZ}} (max exclusive): the slot's own box turned and
	 * moved by the room's place, exactly as the client puts it in the world.
	 */
	static double[] hubBox(RoomInstance room, String slotId) {
		var slot = room.definition.slot(slotId).orElseThrow();
		var p = slot.position();
		var s = slot.size();
		var a = room.place.toHub(p);
		var b = room.place.toHub(new com.mystaria.phantasmon_backend.events.trahison.layout.Pos(p.x() + s.x() - 1, p.y() + s.y() - 1, p.z() + s.z() - 1));
		return new double[] { Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
			Math.max(a.x(), b.x()) + 1, Math.max(a.y(), b.y()) + 1, Math.max(a.z(), b.z()) + 1 };
	}

	/** Forgets all the hub's rooms, those of earlier rounds too (the game ended). A new round keeps them (D8). */
	void clear(String hub) {
		rooms.removeAll(hub);
	}

	/** Applies a room action of player {@code player} in the room of his group, and saves the room in the round. */
	ActionResult apply(Game g, RoundState r, int group, int player, RoomAction action) {
		return apply(g, r, group, player, action, false);
	}

	/**
	 * As above, in the room of group {@code group} of round {@code r} (not necessarily the current one); {@code free}: the free
	 * time (D8), any player in any room opened so far, replays allowed.
	 */
	ActionResult apply(Game g, RoundState r, int group, int player, RoomAction action, boolean free) {
		ActionResult result = rooms.apply(g.hub, r.number, group, player, action, free);
		save(g, r, group);
		return result;
	}

	/**
	 * End of {@code ROOMS} (T3.6): opens the exit of every room of the round that is not finished and saves it. Returns the
	 * groups that were not finished (their members pay the timeout penalty).
	 */
	List<Integer> openUnsolved(Game g, RoundState r) {
		List<Integer> opened = new ArrayList<>();
		for (int k = 0; k < 3; k++) {
			if (!r.roomSolved[k] && rooms.find(g.hub, r.number, k).isPresent() && !rooms.isFinished(g.hub, k)) {
				forceSolve(g, r, k);
				opened.add(k);
			}
		}
		return opened;
	}

	/** The game was paused: the rooms' hint countdown stops (D66). */
	void pauseHints(Game g) {
		rooms.pauseHints(g.hub);
	}

	/** The game resumed: the pause time is added to the rooms' last-progress times (D66). */
	void resumeHints(Game g) {
		rooms.resumeHints(g.hub);
	}

	/** Gives the next help text to the group's room by hand; false when there is none left. */
	boolean giveHint(Game g, RoundState r, int group) {
		if (rooms.find(g.hub, r.number, group).isEmpty()) {
			return false;
		}
		boolean given = rooms.giveHint(g.hub, group).isPresent();
		if (given) {
			save(g, r, group);
		}
		return given;
	}

	/** Solves the group's room at once (the provisional {@code debug_solve}, bots); no room: nothing happens. */
	void forceSolve(Game g, RoundState r, int group) {
		if (rooms.find(g.hub, r.number, group).isEmpty()) {
			return;
		}
		rooms.forceSolve(g.hub, r.number, group);
		save(g, r, group);
	}

	private void save(Game g, RoundState r, int group) {
		while (r.rooms.size() <= group) {
			r.rooms.add(null);
		}
		r.rooms.set(group, rooms.snapshot(g.hub, r.number, group));
	}

	/**
	 * After a restart: brings back the rooms saved in every round so far, the earlier rounds' too (D8: the free time visits
	 * them). Nobody is told; clients ask for a view.
	 */
	void restore(Game g) {
		for (RoundState r : g.rounds) {
			for (JsonNode saved : r.rooms) {
				if (saved == null || saved.isNull()) {
					continue;
				}
				try {
					rooms.restore(saved, r.number);
				} catch (RuntimeException ex) {
					log.error("Trahison game {}: could not restore a room of round {}", g.id, r.number, ex);
				}
			}
		}
	}
}
