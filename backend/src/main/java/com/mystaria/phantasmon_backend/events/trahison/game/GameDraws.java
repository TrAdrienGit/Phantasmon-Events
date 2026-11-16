package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.RoomSlot;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomDefinition;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomDraw;

/**
 * The link between a game and the room draw (T4.1, T4.3): draws the nine rooms of a game from the bank and the games
 * played lately, lets the game master replace one, and records which place of the plan each room goes to and how it is
 * turned there. Called by {@link GameService} <b>with the game locked</b>.
 */
public final class GameDraws {

	private static final Logger log = LoggerFactory.getLogger(GameDraws.class);

	private final Supplier<List<RoomDefinition>> bank;
	private final Function<String, Optional<RoomDefinition>> lookup;
	private final HistoryLookup history;

	/** Rooms played in the last {@code games} games other than game {@code exclude}: room id to how many games ago. */
	@FunctionalInterface
	public interface HistoryLookup {
		Map<String, Integer> recent(java.util.UUID exclude, int games);
	}

	/**
	 * @param bank    the rooms that can be drawn (those with their blocks, so that they can be pasted)
	 * @param lookup  a room of the bank by id, for the game master's replacement
	 * @param history the recently played rooms
	 */
	public GameDraws(Supplier<List<RoomDefinition>> bank, Function<String, Optional<RoomDefinition>> lookup, HistoryLookup history) {
		this.bank = bank;
		this.lookup = lookup;
		this.history = history;
	}

	/** How many rooms the bank can offer to the draw (D73: a game needs nine). */
	int bankSize() {
		return bank.get().size();
	}

	/**
	 * Draws the game's nine rooms and stores them in {@link Game#draw}. Nothing is drawn (the field is left null) when the
	 * settings name a test room, and an empty bank leaves it null too, so that a game with no bank still runs on the
	 * fallback room.
	 *
	 * @param seed the seed of the draw; null: derived from the game's seed
	 */
	void draw(Game g, LayoutContent plan, Long seed) {
		g.draw = null;
		if (g.settings.testRoom != null && !g.settings.testRoom.isBlank()) {
			return;
		}
		long s = seed != null ? seed : g.seed ^ 0x4452415745L;
		try {
			List<RoomDefinition> rooms = bank.get();
			RoomDraw.Result result = RoomDraw.draw(rooms, s, history.recent(g.id, g.settings.drawAvoidGames), g.settings.drawParams());
			g.draw = assignPlaces(result, plan);
		} catch (RoomDraw.DrawException ex) {
			log.warn("Trahison game {}: no draw ({})", g.id, ex.getMessage());
		} catch (RuntimeException ex) {
			log.warn("Trahison game {}: the draw failed", g.id, ex);
		}
	}

	/**
	 * Replaces the room of {@code (round, group)} by {@code roomId}. Refused when the room is unknown or needs a bigger group;
	 * the caller checks that the round has not started.
	 */
	void replace(Game g, int round, int group, String roomId) {
		if (g.draw == null) {
			throw new TrahisonException(TrahisonException.NO_DRAW, "the game has no draw");
		}
		if (round < 1 || round > RoomDraw.ROUNDS || group < 0 || group >= RoomDraw.PER_ROUND) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "round 1 to 3, group 0 to 2");
		}
		RoomDefinition room = lookup.apply(roomId).orElseThrow(
			() -> new TrahisonException(TrahisonException.BAD_ARGUMENT, "unknown room " + roomId));
		try {
			g.draw = RoomDraw.replace(g.draw, round, group, room, null);
		} catch (RoomDraw.DrawException ex) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, ex.getMessage());
		}
	}

	/**
	 * Gives each pick the place of the plan it goes to: the places of its round in plan order, the k-th for group k (the same
	 * rule {@link GameRooms#startRound} follows), with the orientation the place turns the room by. A pick without a place
	 * (the plan has fewer places) keeps none.
	 */
	static RoomDraw.Result assignPlaces(RoomDraw.Result result, LayoutContent plan) {
		List<RoomDraw.Pick> picks = new ArrayList<>();
		for (RoomDraw.Pick p : result.picks()) {
			List<RoomSlot> places = GameRooms.placesOf(plan, p.round());
			RoomSlot place = p.group() < places.size() ? places.get(p.group()) : null;
			picks.add(place == null ? p.withPlace(null, null) : p.withPlace(place.id(), place.orientation() == null ? "north" : place.orientation()));
		}
		return new RoomDraw.Result(result.seed(), picks, result.relaxed());
	}
}
