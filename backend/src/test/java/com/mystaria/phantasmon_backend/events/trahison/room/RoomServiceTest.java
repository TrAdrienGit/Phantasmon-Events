package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.RoomFixtures.TestClock;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomServiceTest {

	static final String HUB = "hub";
	static final List<Integer> MEMBERS = List.of(0, 3, 5);

	TestClock clock;
	RoomService rooms;
	List<String> rooms_ = new ArrayList<>();
	List<PuzzleUpdate> puzzles = new ArrayList<>();
	int roomNotifications;

	@BeforeEach
	void setUp() {
		clock = new TestClock();
		rooms = new RoomService(RoomFixtures.registry(), clock);
		rooms.addListener(new RoomListener() {
			@Override
			public void roomChanged(RoomInstance room) {
				roomNotifications++;
			}

			@Override
			public void puzzleChanged(RoomInstance room, PuzzleUpdate update) {
				puzzles.add(update);
			}
		});
	}

	static RoomAction input(String step, ObjectNode input) {
		return new RoomAction.PuzzleInput(step, input);
	}

	static ObjectNode turn(int dial, int delta) {
		ObjectNode n = Json.object();
		n.put("dial", dial);
		n.put("delta", delta);
		return n;
	}

	/** The digits the room shows on the wall, as a player would read them. */
	String codeOnWall() {
		for (JsonNode slot : rooms.roomView(HUB, 0, ViewerKind.PLAYER).get("slots")) {
			if (slot.get("id").asString().equals("wall")) {
				for (JsonNode show : slot.get("shows")) {
					if (show.get("type").asString().equals("clue")) {
						return show.get("clue").get("value").asString();
					}
				}
			}
		}
		return null;
	}

	/** Turns the dials to the code, using only what a player sees. */
	void solveDials(int player, String code) {
		JsonNode view = rooms.puzzleView(HUB, 0, "s2", ViewerKind.PLAYER).get("view");
		for (int d = 0; d < 3; d++) {
			int want = code.charAt(d) - '0';
			int have = view.get("dials").get(d).asInt();
			if (want != have) {
				rooms.apply(HUB, 0, player, input("s2", turn(d, Math.floorMod(want - have, 10))));
			}
		}
	}

	@Test
	void threeStepChainEndToEnd() {
		rooms.start(HUB, 0, MEMBERS, RoomFixtures.chain(), 42L);
		ObjectNode view = rooms.roomView(HUB, 0, ViewerKind.PLAYER);
		assertEquals(0, view.get("steps_done").asInt());
		assertEquals(3, view.get("steps_total").asInt());
		assertFalse(view.get("exit_open").asBoolean());
		assertNull(codeOnWall());

		// s2 is not open yet, outsiders are refused.
		assertEquals(RoomException.STEP_NOT_OPEN,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 0, input("s2", turn(0, 1)))).code());
		assertEquals(RoomException.NOT_IN_ROOM,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 7, input("s1", Json.object()))).code());

		// Press the button by using its slot: opens s1's screen for that player only, then solves it.
		ActionResult use = rooms.apply(HUB, 0, 3, new RoomAction.UseSlot("btn"));
		assertEquals("s1", use.step());
		assertTrue(use.stepSolved());
		assertEquals(PuzzleUpdateKinds.OPEN, puzzles.get(0).reason());
		assertEquals(3, puzzles.get(0).onlyTo());
		assertEquals("solved", puzzles.get(1).reason());

		// s2 is open and its clue shows on the wall.
		String code = codeOnWall();
		assertEquals(3, code.length());
		clock.now = clock.now.plusSeconds(90);
		puzzles.clear();
		solveDials(5, code);
		assertTrue(puzzles.stream().anyMatch(p -> p.reason().equals("input") && p.player() == 5 && !p.events().isEmpty()));
		assertTrue(rooms.find(HUB, 0).get().steps.get("s2").solved);
		assertEquals(clock.now, rooms.find(HUB, 0).get().lastProgressAt);

		// The exit step.
		assertFalse(rooms.find(HUB, 0).get().exitOpen);
		ActionResult last = rooms.apply(HUB, 0, 0, new RoomAction.UseSlot("exit"));
		assertTrue(last.roomFinished());
		view = rooms.roomView(HUB, 0, ViewerKind.SPECTATOR);
		assertTrue(view.get("exit_open").asBoolean());
		assertEquals(3, view.get("steps_done").asInt());
		assertFalse(view.get("finished_at").isNull());
		assertEquals(RoomException.ROOM_FINISHED,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 0, new RoomAction.UseSlot("exit"))).code());
		assertTrue(roomNotifications >= 3);
	}

	@Test
	void sameSeedSameSolutionsOtherSeedOther() {
		String a = gmTarget(7L);
		assertEquals(a, gmTarget(7L));
		Set<String> distinct = new HashSet<>();
		for (long seed = 0; seed < 30; seed++) {
			distinct.add(gmTarget(seed));
		}
		assertTrue(distinct.size() > 10, "other seeds draw other solutions");
	}

	private String gmTarget(long seed) {
		RoomService s = new RoomService(RoomFixtures.registry(), clock);
		s.start(HUB, 1, MEMBERS, RoomFixtures.chain(), seed);
		return s.puzzleView(HUB, 1, "s2", ViewerKind.GAME_MASTER).get("view").get("target").toString();
	}

	@Test
	void viewsNeverContainTheSolution() {
		for (long seed = 0; seed < 200; seed++) {
			rooms.start(HUB, 0, MEMBERS, RoomFixtures.chain(), seed);
			String target = rooms.puzzleView(HUB, 0, "s2", ViewerKind.GAME_MASTER).get("view").get("target").toString();
			String code = target.replaceAll("[^0-9]", "");
			for (ViewerKind kind : new ViewerKind[] { ViewerKind.PLAYER, ViewerKind.SPECTATOR }) {
				String puzzle = rooms.puzzleView(HUB, 0, "s2", kind).toString();
				assertFalse(puzzle.contains("target"), puzzle);
				assertFalse(puzzle.contains(target), puzzle);
				// The clue is not on the wall before s2 opens.
				String room = rooms.roomView(HUB, 0, kind).toString();
				assertFalse(room.contains("\"value\":\"" + code + "\""), room);
				assertFalse(room.contains("target"), room);
			}
			// The game master may see it.
			assertTrue(rooms.puzzleView(HUB, 0, "s2", ViewerKind.GAME_MASTER).toString().contains("target"));
		}
	}

	@Test
	void resetGivesNewSolutionsAndZeroState() {
		rooms.start(HUB, 0, MEMBERS, RoomFixtures.chain(), 1L);
		rooms.apply(HUB, 0, 0, new RoomAction.UseSlot("btn"));
		String first = codeOnWall();
		puzzles.clear();
		long seed = 2;
		while (true) {
			rooms.reset(HUB, 0, seed);
			rooms.apply(HUB, 0, 0, new RoomAction.UseSlot("btn"));
			if (!first.equals(codeOnWall())) {
				break;
			}
			seed++;
		}
		assertTrue(puzzles.stream().anyMatch(p -> p.reason().equals("reset")));
		rooms.reset(HUB, 0, seed);
		ObjectNode view = rooms.roomView(HUB, 0, ViewerKind.PLAYER);
		assertEquals(0, view.get("steps_done").asInt());
		assertNull(codeOnWall());
	}

	@Test
	void badInputsAreRefused() {
		rooms.start(HUB, 0, MEMBERS, RoomFixtures.chain(), 5L);
		rooms.apply(HUB, 0, 0, new RoomAction.UseSlot("btn"));
		assertEquals(RoomException.BAD_INPUT,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 0, input("s2", turn(9, 1)))).code());
		assertEquals(RoomException.BAD_INPUT,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 0, input("s2", Json.object()))).code());
		assertEquals(RoomException.STEP_SOLVED,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 0, input("s1", Json.object()))).code());
		assertEquals(RoomException.SLOT_INACTIVE,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 0, new RoomAction.UseSlot("wall_missing"))).code());
		assertEquals(RoomException.NO_ROOM,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 2, 0, new RoomAction.UseSlot("btn"))).code());
	}

	@Test
	void hintsComeVagueToDirectOnTheOpenStep() {
		rooms.start(HUB, 0, MEMBERS, RoomFixtures.chain(), 5L);
		assertEquals("vague", rooms.giveHint(HUB, 0).get().text());
		assertEquals("less vague", rooms.giveHint(HUB, 0).get().text());
		Hint third = rooms.giveHint(HUB, 0).get();
		assertEquals(3, third.rank());
		assertEquals("s1", third.step());
		assertTrue(rooms.giveHint(HUB, 0).isEmpty(), "only s1 is open and it has no hint left");
	}

	@Test
	void itemsArePickedUpIntoTheSharedBag() {
		var def = new RoomDefinition("keys", "Keys", "t", 1, 2, 30,
				List.of(RoomFixtures.slot("box", "pickup", 1, 1, 1), RoomFixtures.slot("door", "exit", 5, 0, 5)),
				List.of(RoomFixtures.step("k", "key_box", Map.of(), List.of(), List.of(new Give("key", "box", null))),
						RoomFixtures.step("e", "debug_button", Map.of("exit", "door"), List.of("k"), List.of())),
				null);
		rooms.start(HUB, 0, MEMBERS, def, 1L);
		assertTrue(rooms.roomView(HUB, 0, ViewerKind.PLAYER).toString().contains("brass_key"));
		ActionResult r = rooms.apply(HUB, 0, 5, new RoomAction.PickUp("box"));
		assertEquals(List.of("brass_key"), r.pickedItems());
		ObjectNode view = rooms.roomView(HUB, 0, ViewerKind.PLAYER);
		assertEquals("brass_key", view.get("inventory").get(0).asString());
		assertFalse(view.get("slots").toString().contains("brass_key"), "a picked item leaves its slot");
		assertEquals(RoomException.NOTHING_TO_PICK,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 5, new RoomAction.PickUp("box"))).code());
	}

	/** key box (item brass_key in "box") -> lock (use the key on "lock") -> exit; recipe brass_key + rope = rope_key. */
	private RoomDefinition keyRoom() {
		return new RoomDefinition("keys", "Keys", "t", 1, 2, 30,
				List.of(RoomFixtures.slot("box", "pickup", 1, 1, 1), RoomFixtures.slot("lock", "terminal", 3, 1, 3),
						RoomFixtures.slot("door", "exit", 5, 0, 5)),
				List.of(RoomFixtures.step("k", "key_box", Map.of(), List.of(), List.of(new Give("key", "box", null))),
						RoomFixtures.step("l", "key_lock", Map.of("lock", "lock"), List.of(), List.of()),
						RoomFixtures.step("e", "debug_button", Map.of("exit", "door"), List.of("l"), List.of())),
				null, List.of(new Combine("brass_key", "rope", "rope_key")));
	}

	private List<String> carried(int player) {
		List<String> out = new ArrayList<>();
		for (JsonNode i : rooms.roomView(HUB, 0, ViewerKind.PLAYER).get("items")) {
			if (i.get("holder").asInt() == player) {
				out.add(i.get("id").asString());
			}
		}
		return out;
	}

	@Test
	void pickedItemBelongsToThePickerAndOnlyHeGivesIt() {
		rooms.start(HUB, 0, MEMBERS, keyRoom(), 1L);
		rooms.apply(HUB, 0, 3, new RoomAction.PickUp("box"));
		assertEquals(List.of("brass_key"), carried(3));
		assertEquals(RoomException.NO_SUCH_ITEM,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 5, new RoomAction.GiveItem("brass_key", 0))).code());
		assertEquals(RoomException.BAD_ACTION,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 3, new RoomAction.GiveItem("brass_key", 3))).code());
		assertEquals(RoomException.BAD_ACTION,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 3, new RoomAction.GiveItem("brass_key", 9))).code());
		rooms.apply(HUB, 0, 3, new RoomAction.GiveItem("brass_key", 5));
		assertEquals(List.of(), carried(3));
		assertEquals(List.of("brass_key"), carried(5));
		assertEquals(RoomException.NO_SUCH_ITEM,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 3, new RoomAction.UseItem("brass_key", "lock"))).code());
	}

	@Test
	void useItemOnASlotSolvesTheStepAndConsumesTheItem() {
		rooms.start(HUB, 0, MEMBERS, keyRoom(), 1L);
		// Not carried, then carried but at a slot that takes nothing, then at the wrong item.
		assertEquals(RoomException.NO_SUCH_ITEM,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 3, new RoomAction.UseItem("brass_key", "lock"))).code());
		rooms.apply(HUB, 0, 3, new RoomAction.PickUp("box"));
		assertEquals(RoomException.ITEM_REFUSED,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 3, new RoomAction.UseItem("brass_key", "door"))).code());
		assertEquals(List.of("brass_key"), carried(3), "a refused use keeps the item");
		assertEquals(RoomException.SLOT_INACTIVE,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 3, new RoomAction.UseItem("brass_key", "nowhere"))).code());
		ActionResult r = rooms.apply(HUB, 0, 3, new RoomAction.UseItem("brass_key", "lock"));
		assertTrue(r.stepSolved());
		assertEquals("l", r.step());
		assertEquals(List.of(), carried(3), "the item is consumed");
		assertTrue(rooms.roomView(HUB, 0, ViewerKind.PLAYER).get("steps").toString().contains("\"solved\":true"));
	}

	@Test
	void combineFollowsTheRoomsRecipes() {
		// Two boxes give two brass keys; the recipe turns two keys into a twin key.
		var def = new RoomDefinition("keys", "Keys", "t", 1, 2, 30,
				List.of(RoomFixtures.slot("box1", "pickup", 1, 1, 1), RoomFixtures.slot("box2", "pickup", 2, 1, 1),
						RoomFixtures.slot("door", "exit", 5, 0, 5)),
				List.of(RoomFixtures.step("k1", "key_box", Map.of(), List.of(), List.of(new Give("key", "box1", null))),
						RoomFixtures.step("k2", "key_box", Map.of(), List.of(), List.of(new Give("key", "box2", null))),
						RoomFixtures.step("e", "debug_button", Map.of("exit", "door"), List.of("k1", "k2"), List.of())),
				null, List.of(new Combine("brass_key", "brass_key", "twin_key"), new Combine("twin_key", "oil", "oiled_key")));
		rooms.start(HUB, 0, MEMBERS, def, 1L);
		rooms.apply(HUB, 0, 3, new RoomAction.PickUp("box1"));
		// One key is not two: the same item needs two copies.
		assertEquals(RoomException.NO_SUCH_ITEM,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 3, new RoomAction.CombineItems("brass_key", "brass_key"))).code());
		assertEquals(RoomException.NO_COMBINATION,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 3, new RoomAction.CombineItems("brass_key", "oil"))).code());
		assertEquals(List.of("brass_key"), carried(3), "a failed combine changes nothing");
		rooms.apply(HUB, 0, 5, new RoomAction.PickUp("box2"));
		// The second key is player 5's: player 3 cannot combine it, 5 cannot either (one key each).
		assertEquals(RoomException.NO_SUCH_ITEM,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 3, new RoomAction.CombineItems("brass_key", "brass_key"))).code());
		rooms.apply(HUB, 0, 5, new RoomAction.GiveItem("brass_key", 3));
		ActionResult r = rooms.apply(HUB, 0, 3, new RoomAction.CombineItems("brass_key", "brass_key"));
		assertEquals(List.of("twin_key"), r.pickedItems());
		assertEquals(List.of("twin_key"), carried(3));
		assertEquals(List.of(), carried(5));
		assertEquals(1, rooms.roomView(HUB, 0, ViewerKind.PLAYER).get("inventory").size());
	}

	@Test
	void combineIsDeterministicAndRecipeOrderDoesNotMatter() {
		var recipe = new Combine("a", "b", "c");
		assertTrue(recipe.matches("a", "b"));
		assertTrue(recipe.matches("b", "a"));
		assertFalse(recipe.matches("a", "a"));
		var def = keyRoom();
		assertEquals(def.combine("brass_key", "rope"), def.combine("rope", "brass_key"));
		assertTrue(def.combine("rope", "rope").isEmpty());
	}

	@Test
	void roomViewCarriesItemsHoldersAndPlaceButNeverASolution() {
		rooms.start(HUB, 0, MEMBERS, keyRoom(), 1L, new RoomPlace("p1", new com.mystaria.phantasmon_backend.events.trahison.layout.Pos(10, 64, 20), "east"));
		rooms.apply(HUB, 0, 3, new RoomAction.PickUp("box"));
		ObjectNode view = rooms.roomView(HUB, 0, ViewerKind.PLAYER);
		assertEquals("east", view.get("place").get("orientation").asString());
		assertEquals(10, view.get("place").get("origin").get("x").asInt());
		assertEquals(3, view.get("items").get(0).get("holder").asInt());
	}

	// ---------------------------------------------------------------- D8: free time

	private JsonNode target(RoomService service) {
		return service.puzzleView(HUB, 0, "s2", ViewerKind.GAME_MASTER).get("view").get("target");
	}

	@Test
	void freeTimeLetsNonMembersActAndReceiveButTheRoundDoesNot() {
		rooms.start(HUB, 0, MEMBERS, keyRoom(), 1L);
		assertEquals(RoomException.NOT_IN_ROOM,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 7, new RoomAction.PickUp("box"))).code());
		rooms.apply(HUB, 0, 3, new RoomAction.PickUp("box"));
		assertEquals(RoomException.BAD_ACTION,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 3, new RoomAction.GiveItem("brass_key", 7))).code());
		rooms.apply(HUB, 0, 3, new RoomAction.GiveItem("brass_key", 7), true);
		assertEquals(List.of("brass_key"), carried(7));
		// The visitor uses it on the lock, in the free time: the step is solved, nobody else is asked.
		assertTrue(rooms.apply(HUB, 0, 7, new RoomAction.UseItem("brass_key", "lock"), true).stepSolved());
		assertEquals(List.of(), carried(7));
	}

	@Test
	void aFinishedRoomRefusesTheRoundsActionsButTakesFreeTimeOnesAndReplays() {
		rooms.start(HUB, 0, MEMBERS, RoomFixtures.chain(), 5L);
		rooms.forceSolve(HUB, 0);
		assertEquals(RoomException.ROOM_FINISHED,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 0, new RoomAction.UseSlot("btn"))).code());
		rooms.apply(HUB, 0, 8, new RoomAction.UseSlot("btn"), true); // a visitor looks around: nothing to refuse
		assertEquals(RoomException.ROOM_FINISHED,
				assertThrows(RoomException.class, () -> rooms.apply(HUB, 0, 0, new RoomAction.Replay())).code(),
				"a replay is for the free time (and a finished room refuses the round's actions)");
		int notifications = roomNotifications;
		rooms.apply(HUB, 0, 8, new RoomAction.Replay(), true);
		RoomInstance again = rooms.find(HUB, 0).orElseThrow();
		assertFalse(again.exitOpen);
		assertEquals(0, again.stepsDone());
		assertEquals(1, again.replays);
		assertNotEquals(5L, again.seed);
		assertEquals(MEMBERS, again.members);
		assertTrue(roomNotifications > notifications, "the watchers are told");
		assertTrue(puzzles.stream().anyMatch(p -> p.reason().equals("reset")));
		// It plays again from the start.
		assertTrue(rooms.apply(HUB, 0, 8, new RoomAction.UseSlot("btn"), true).stepSolved());
	}

	@Test
	void replaysGiveNewSolutionsEachTimeButTheSameOnesForTheSameHistory() {
		RoomService other = new RoomService(RoomFixtures.registry(), clock);
		rooms.start(HUB, 0, MEMBERS, RoomFixtures.chain(), 5L);
		other.start(HUB, 0, MEMBERS, RoomFixtures.chain(), 5L);
		rooms.apply(HUB, 0, 0, new RoomAction.UseSlot("btn")); // s2 opens
		other.apply(HUB, 0, 0, new RoomAction.UseSlot("btn"));
		JsonNode first = target(rooms).deepCopy();
		assertEquals(first, target(other));
		rooms.apply(HUB, 0, 1, new RoomAction.Replay(), true);
		other.apply(HUB, 0, 1, new RoomAction.Replay(), true);
		rooms.apply(HUB, 0, 0, new RoomAction.UseSlot("btn"), true);
		other.apply(HUB, 0, 0, new RoomAction.UseSlot("btn"), true);
		assertEquals(target(rooms), target(other), "same replay number, same solutions");
		assertEquals(RoomService.replaySeed(5L, 1), rooms.find(HUB, 0).orElseThrow().seed);
		assertNotEquals(RoomService.replaySeed(5L, 1), RoomService.replaySeed(5L, 2));
		rooms.apply(HUB, 0, 1, new RoomAction.Replay(), true);
		assertEquals(2, rooms.find(HUB, 0).orElseThrow().replays);
		assertEquals(RoomService.replaySeed(RoomService.replaySeed(5L, 1), 2), rooms.find(HUB, 0).orElseThrow().seed,
				"each replay's seed comes from the previous seed and the count");
		// The replay count survives a save and a reload.
		RoomInstance restored = rooms.restore(rooms.snapshot(HUB, 0));
		assertEquals(2, restored.replays);
	}

	@Test
	void documentCluesShowTheirPagesAndNeverTheSolution() {
		var clue = com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Clue.document("memo", List.of("page one", "@image:plan\nsecond"));
		assertEquals("document", clue.toJson().get("type").asString());
		assertEquals(2, clue.toJson().get("value").size());
	}

	@Test
	void actionsParse() {
		var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
		assertEquals(new RoomAction.UseSlot("a"), RoomAction.parse(mapper.readTree("{\"action\":\"use_slot\",\"slot\":\"a\"}")));
		assertEquals(new RoomAction.PickUp("a"), RoomAction.parse(mapper.readTree("{\"action\":\"pick_up\",\"slot\":\"a\"}")));
		var in = (RoomAction.PuzzleInput) RoomAction.parse(
				mapper.readTree("{\"action\":\"puzzle_input\",\"step\":\"s\",\"input\":{\"dial\":1,\"delta\":2}}"));
		assertEquals("s", in.step());
		assertEquals(2, in.input().get("delta").asInt());
		assertThrows(RoomException.class, () -> RoomAction.parse(mapper.readTree("{\"action\":\"use_slot\"}")));
		assertThrows(RoomException.class, () -> RoomAction.parse(mapper.readTree("{\"action\":\"vote\"}")));
		assertNotEquals(0, RoomAction.NAMES.size());
		assertEquals(new RoomAction.Replay(), RoomAction.parse(mapper.readTree("{\"action\":\"replay\"}")));
		assertEquals(new RoomAction.GiveItem("k", 4), RoomAction.parse(mapper.readTree("{\"action\":\"give_item\",\"item\":\"k\",\"to\":4}")));
		assertEquals(new RoomAction.UseItem("k", "s"), RoomAction.parse(mapper.readTree("{\"action\":\"use_item\",\"item\":\"k\",\"slot\":\"s\"}")));
		assertEquals(new RoomAction.CombineItems("a", "b"), RoomAction.parse(mapper.readTree("{\"action\":\"combine\",\"a\":\"a\",\"b\":\"b\"}")));
		assertEquals(2, RoomAction.targetGroup(mapper.readTree("{\"group\":2}")));
		assertEquals(null, RoomAction.targetGroup(mapper.readTree("{\"x\":1}")));
		assertThrows(RoomException.class, () -> RoomAction.targetGroup(mapper.readTree("{\"group\":\"a\"}")));
	}

	private static void assertNull(Object o) {
		org.junit.jupiter.api.Assertions.assertNull(o);
	}

	/** Just names the reason strings used above. */
	private static final class PuzzleUpdateKinds {
		static final String OPEN = "open";
	}
}
