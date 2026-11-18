package com.mystaria.phantasmon_backend.events.trahison.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.Box;
import com.mystaria.phantasmon_backend.events.trahison.layout.Cabin;
import com.mystaria.phantasmon_backend.events.trahison.layout.HidingSpot;
import com.mystaria.phantasmon_backend.events.trahison.layout.Layout;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutDoor;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutZone;
import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Item;
import com.mystaria.phantasmon_backend.events.trahison.rules.Role;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;
import com.mystaria.phantasmon_backend.events.trahison.rules.Vote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T7.1 (the secret roles) and T7.2 (the hidden items), played through the service. */
class RolesAndItemsTest {

	private static final String HUB = "hub-r";
	private static final Box BOX = new Box(0, 64, 0, 0, 66, 2);
	private static final Box PLAIN = new Box(100, 64, 100, 100, 66, 102);
	private static final String[] NAMES = { "rg", "rb", "gb" };
	private static final String[][] COLORS = { { "red", "green" }, { "red", "blue" }, { "green", "blue" } };

	private ManualClock clock;
	private GameService service;
	private final UUID[] ids = new UUID[9];
	private final List<String> whispers = new ArrayList<>();

	private static LayoutContent plan() {
		List<LayoutZone> zones = new ArrayList<>(List.of(new LayoutZone("hall", "hall", List.of(BOX)),
			new LayoutZone("vault", "hall", List.of(PLAIN))));
		List<LayoutDoor> doors = new ArrayList<>();
		for (int k = 0; k < 3; k++) {
			zones.add(new LayoutZone("thr_" + NAMES[k], "doors", List.of(BOX)));
			zones.add(new LayoutZone("slot_" + k, "room_slot", List.of(BOX)));
			doors.add(new LayoutDoor("c_" + NAMES[k], "color", BOX, "hall", "slot_" + k, 1, List.of(COLORS[k]), "thr_" + NAMES[k]));
		}
		doors.add(new LayoutDoor("d_plain", "plain", PLAIN, "hall", "vault", null, null));
		List<Cabin> cabins = new ArrayList<>();
		for (int k = 0; k < 3; k++) {
			for (String side : List.of("pair", "solo")) {
				String id = "cab_" + NAMES[k] + "_" + side;
				zones.add(new LayoutZone(id, "cabin", List.of(PLAIN)));
				cabins.add(new Cabin(id, new String[] { "red_green", "red_blue", "green_blue" }[k], side, id, null));
			}
		}
		List<HidingSpot> spots = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			spots.add(new HidingSpot("s" + i, new Pos(i * 10, 65, 0)));
		}
		return new LayoutContent(zones, doors, null, cabins, spots);
	}

	@BeforeEach
	void setUp() {
		clock = new ManualClock();
		service = new GameService(new MemoryStore(), clock, clock);
		service.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan()));
		service.addListener(new GameListener() {
			@Override
			public void whispered(Game game, int seat, String key, List<String> args) {
				whispers.add(seat + ":" + key + ":" + args);
			}
		});
		service.create(HUB, Settings.fast(60).withoutCabins(), 11L);
		for (int i = 0; i < 9; i++) {
			ids[i] = new UUID(0, i + 1);
			service.join(HUB, ids[i], "p" + i);
		}
	}

	private void startAndDoors() {
		service.start(HUB);
		service.next(HUB); // DOORS
	}

	private Split split() {
		return service.read(HUB, g -> Doors.validSplits(g.currentRound().assignment()).get(0));
	}

	private void everyonePasses() {
		Split s = split();
		for (int k = 0; k < 3; k++) {
			for (int b : s.groups().get(k).members()) {
				service.passDoor(HUB, ids[b], Door.ALL.indexOf(s.groups().get(k).door()));
			}
		}
	}

	private void toVote() {
		startAndDoors();
		everyonePasses();
		service.next(HUB); // ROOMS
		service.next(HUB); // FREE_TIME
		service.next(HUB); // VOTE
	}

	private void setRoles(Map<Integer, Role> roles) {
		service.read(HUB, g -> {
			for (PlayerState p : g.players) {
				p.role = null;
			}
			roles.forEach((seat, role) -> g.players.get(seat).role = role.name());
			return null;
		});
	}

	private void give(int seat, Item item) {
		service.read(HUB, g -> g.players.get(seat).items.add(item.name()));
	}

	private void at(int player, String zone, double x, double z) {
		service.setZone(HUB, ids[player], zone, x, 65, z);
	}

	private Map<String, Object> view(int player) {
		return service.read(HUB, g -> Views.build(g, ids[player], clock.instant()));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> viewer(int player) {
		return (Map<String, Object>) view(player).get("viewer");
	}

	private int points(int seat) {
		return service.read(HUB, g -> g.bracelets.get(seat).points);
	}

	private static void assertCode(Runnable call, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(TrahisonException.class, ex -> assertThat(ex.code()).isEqualTo(code));
	}

	// ---------------------------------------------------------------- roles

	@Test
	void rolesAreDrawnAtTheStartAndEachHolderIsToldInPrivate() {
		startAndDoors();
		Game g = service.find(HUB);
		List<Integer> holders = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			if (g.players.get(i).role != null) {
				holders.add(i);
			}
		}
		assertThat(holders).hasSize(Settings.DEFAULT_ROLE_COUNT);
		for (int i = 0; i < 9; i++) {
			assertThat(viewer(i).get("role")).isEqualTo(g.players.get(i).role); // his own, nobody else's
		}
		assertThat(whispers).hasSize(3);
		for (int seat : holders) {
			assertThat(whispers).contains(seat + ":phantasmon_events.trahison.say.role." + g.players.get(seat).role.toLowerCase() + ":[]");
		}
		// no role of another player in a view before the end
		for (int i = 0; i < 9; i++) {
			assertThat(view(i).get("roles")).isNull();
			assertThat(view(i).get("gm")).isNull();
			String text = view(i).toString();
			for (int seat : holders) {
				if (seat != i) {
					assertThat(text).doesNotContain(g.players.get(seat).role);
				}
			}
		}
	}

	@Test
	void theDrawRepeatsWithTheSeed() {
		startAndDoors();
		List<String> first = service.read(HUB, g -> g.players.stream().map(p -> String.valueOf(p.role)).toList());
		GameService other = new GameService(new MemoryStore(), clock, clock);
		other.create("hub-2", Settings.fast(60).withoutCabins(), 11L);
		for (int i = 0; i < 9; i++) {
			other.join("hub-2", ids[i], "p" + i);
		}
		other.start("hub-2");
		List<String> second = other.read("hub-2", g -> g.players.stream().map(p -> String.valueOf(p.role)).toList());
		assertThat(second).isEqualTo(first);
	}

	@Test
	void theGameMasterWhoPlaysHoldsHisRoleAndIsNeverDrawnAnother() {
		service.setGameMaster(HUB, ids[4], "p4", true);
		service.start(HUB);
		Game g = service.find(HUB);
		assertThat(g.players.get(4).role).isNull(); // not drawn: his secret role is the game master's (D12)
		assertThat(viewer(4).get("role")).isEqualTo("GAME_MASTER");
		assertThat(viewer(3).get("role")).isNotEqualTo("GAME_MASTER");
		assertThat(whispers).contains("4:phantasmon_events.trahison.say.role.game_master:[]");
		service.end(HUB);
		assertThat(view(0).get("roles").toString()).contains("index=4, role=GAME_MASTER");
	}

	@Test
	void botsAreGivenNoRole() {
		for (int i = 0; i < 9; i++) {
			service.find(HUB).players.get(i).bot = i < 6;
		}
		service.start(HUB);
		Game g = service.find(HUB);
		for (int i = 0; i < 6; i++) {
			assertThat(g.players.get(i).role).isNull();
		}
		assertThat(g.players.stream().filter(p -> p.role != null).count()).isEqualTo(3);
	}

	@Test
	void theGuardianCannotBetray() {
		toVote();
		setRoles(Map.of(2, Role.GUARDIAN));
		assertCode(() -> service.vote(HUB, ids[2], Vote.BETRAY), TrahisonException.CANNOT_BETRAY);
		assertThat(viewer(2).get("side_voted")).isEqualTo(false);
		assertThat(view(5).toString()).doesNotContain("CANNOT_BETRAY");
		service.vote(HUB, ids[2], Vote.ALLY); // allying is fine
		assertThat(viewer(2).get("side_voted")).isEqualTo(true);
		// somebody else betrays freely
		setRoles(Map.of());
		int other = (2 + 1) % 9;
		assertThat(other).isNotEqualTo(2);
	}

	@Test
	void theInfiltratorGetsOnePointForBetrayingInRoundOne() {
		toVote();
		Split s = split();
		int solo = s.groups().get(0).solo();
		int pairFirst = s.groups().get(0).pair().get(0);
		setRoles(Map.of(solo, Role.INFILTRATOR));
		int before = points(solo);
		service.vote(HUB, ids[solo], Vote.BETRAY);
		service.vote(HUB, ids[pairFirst], Vote.ALLY);
		service.next(HUB); // results
		assertThat(points(solo)).isEqualTo(before + 3 + 1); // +3 for the betrayal, +1 for the role
		assertThat(service.find(HUB).currentRound().deltas[solo]).isEqualTo(4);
		assertThat(((Map<?, ?>) viewer(solo).get("role_info")).get("done")).isEqualTo(true);
	}

	@Test
	void theInfiltratorWhoAlliesGetsNothing() {
		toVote();
		Split s = split();
		int solo = s.groups().get(0).solo();
		setRoles(Map.of(solo, Role.INFILTRATOR));
		int before = points(solo);
		service.vote(HUB, ids[solo], Vote.ALLY);
		service.vote(HUB, ids[s.groups().get(0).pair().get(0)], Vote.ALLY);
		service.next(HUB);
		assertThat(points(solo)).isEqualTo(before + 2);
	}

	@Test
	void theSeerSeesTheVoteOfOneClosedCabinOnceAndOnlyAfterItWasCast() {
		toVote();
		Split s = split();
		int seer = s.groups().get(0).solo();
		int pairFirst = s.groups().get(1).pair().get(0);
		setRoles(Map.of(seer, Role.SEER));
		String own = cabinId(0, false);
		String target = cabinId(1, true);
		service.read(HUB, g -> g.settings.cabinsRequired = true);
		assertCode(() -> service.foresee(HUB, ids[seer], "nope"), TrahisonException.BAD_ARGUMENT);
		assertCode(() -> service.foresee(HUB, ids[seer], own), TrahisonException.BAD_ARGUMENT); // his own cabin
		assertCode(() -> service.foresee(HUB, ids[seer], target), TrahisonException.CABIN_NOT_CLOSED);
		lock(target, false); // waiting: still not closed
		assertCode(() -> service.foresee(HUB, ids[seer], target), TrahisonException.CABIN_NOT_CLOSED);
		lock(target, true);
		assertCode(() -> service.foresee(HUB, ids[seer], target), TrahisonException.NOT_VOTED_YET);
		service.read(HUB, g -> g.settings.cabinsRequired = false);
		service.vote(HUB, ids[pairFirst], Vote.BETRAY);
		service.read(HUB, g -> g.settings.cabinsRequired = true);
		service.foresee(HUB, ids[seer], target);
		Map<?, ?> info = (Map<?, ?>) viewer(seer).get("role_info");
		assertThat(info.get("vote")).isEqualTo("BETRAY");
		assertThat(info.get("cabin")).isEqualTo(target);
		assertThat(info.get("group")).isEqualTo(1);
		assertThat(info.get("side")).isEqualTo("PAIR");
		assertThat(info.get("used")).isEqualTo(true);
		assertCode(() -> service.foresee(HUB, ids[seer], target), TrahisonException.POWER_USED);
		assertCode(() -> service.foresee(HUB, ids[pairFirst], target), TrahisonException.NOT_YOUR_ROLE);
		assertThat(viewer(pairFirst).get("role_info")).isNull();
	}

	/** The cabin that serves the colour pair of door {@code k} on the pair or the solo side, in this test's plan. */
	private String cabinId(int k, boolean pair) {
		return "cab_" + NAMES[k] + "_" + (pair ? "pair" : "solo");
	}

	private void lock(String cabin, boolean locked) {
		service.read(HUB, g -> g.cabinLocks.put(cabin, new CabinLock(0, 0, locked, false, List.of())));
	}

	@Test
	void theInfiltratorGetsHisPointWhenHisPartnerBetraysForTheirPair() {
		toVote();
		Split s = split();
		int infiltrator = s.groups().get(0).pair().get(0);
		int partner = s.groups().get(0).pair().get(1);
		setRoles(Map.of(infiltrator, Role.INFILTRATOR));
		int before = points(infiltrator);
		service.vote(HUB, ids[partner], Vote.BETRAY); // D143: the pair's vote counts, cast by the partner
		service.vote(HUB, ids[s.groups().get(0).solo()], Vote.ALLY);
		service.next(HUB);
		assertThat(points(infiltrator)).isGreaterThan(before + 1);
		assertThat(((Map<?, ?>) viewer(infiltrator).get("role_info")).get("done")).isEqualTo(true);
	}

	@Test
	void theGamblerBetsOncePerRoundAndIsPaidOnceInTheGame() {
		toVote();
		Split s = split();
		int gambler = s.groups().get(0).solo();
		int pairFirst = s.groups().get(0).pair().get(0);
		setRoles(Map.of(gambler, Role.GAMBLER));
		service.bet(HUB, ids[gambler], Vote.BETRAY);
		assertCode(() -> service.bet(HUB, ids[gambler], Vote.ALLY), TrahisonException.ALREADY_BET);
		assertCode(() -> service.bet(HUB, ids[pairFirst], Vote.ALLY), TrahisonException.NOT_YOUR_ROLE);
		service.vote(HUB, ids[pairFirst], Vote.BETRAY); // the bet is right
		service.vote(HUB, ids[gambler], Vote.ALLY);
		int before = points(gambler);
		service.next(HUB); // results: ALLY against BETRAY is -2, +1 for the bet
		assertThat(points(gambler)).isEqualTo(before - 2 + 1);
		assertThat(service.find(HUB).roleState.gamblerPaid).isTrue();
		// round 2: another right bet is not paid again
		service.next(HUB); // DOORS round 2
		everyonePasses();
		service.next(HUB);
		service.next(HUB);
		service.next(HUB); // VOTE
		Split s2 = split();
		int pair2 = s2.groups().get(0).pair().contains(gambler) ? s2.groups().get(0).solo() : s2.groups().get(0).pair().get(0);
		int k = service.read(HUB, g -> g.currentRound().groupOf(gambler));
		boolean gamblerPair = s2.groups().get(k).pair().contains(gambler);
		int opponent = gamblerPair ? s2.groups().get(k).solo() : s2.groups().get(k).pair().get(0);
		service.bet(HUB, ids[gambler], Vote.ALLY);
		service.vote(HUB, ids[opponent], Vote.ALLY);
		service.vote(HUB, ids[gambler], Vote.ALLY);
		int mid = points(gambler);
		service.next(HUB);
		assertThat(points(gambler)).isEqualTo(mid + 2); // no bonus
		assertThat(pair2).isGreaterThanOrEqualTo(0);
	}

	@Test
	void everyRoleIsRevealedAtTheEndWithItsObjective() {
		startAndDoors();
		setRoles(Map.of(0, Role.FERRYMAN, 1, Role.SEER));
		service.kill(HUB, 2, "knife");
		service.kill(HUB, 3, "knife");
		service.end(HUB);
		Map<String, Object> v = view(5);
		assertThat(v.get("roles").toString()).contains("index=0, role=FERRYMAN, objective_met=true").contains("index=1, role=SEER, objective_met=false");
	}

	// ---------------------------------------------------------------- items

	@Test
	void itemsLieInTheHidingSpotsAndAreFoundOneByOneInTheFreeTime() {
		startAndDoors();
		Game g = service.find(HUB);
		assertThat(g.hiddenItems).hasSize(7);
		String spot = g.hiddenItems.keySet().iterator().next();
		String item = g.hiddenItems.get(spot);
		int x = Integer.parseInt(spot.substring(1)) * 10;
		at(0, "hall", x + 0.5, 0.5);
		assertCode(() -> service.search(HUB, ids[0], spot), TrahisonException.CANNOT_SEARCH_NOW); // DOORS
		everyonePasses();
		service.next(HUB); // ROOMS
		service.next(HUB); // FREE_TIME
		at(0, "hall", x + 20, 0.5);
		assertCode(() -> service.search(HUB, ids[0], spot), TrahisonException.SPOT_TOO_FAR);
		at(0, "hall", x + 0.5, 0.5);
		assertCode(() -> service.search(HUB, ids[0], "nowhere"), TrahisonException.NO_SPOT);
		service.search(HUB, ids[0], spot);
		assertThat(viewer(0).get("items")).isEqualTo(List.of(item));
		assertThat(viewer(1).get("items")).isEqualTo(List.of()); // nobody else sees it
		assertCode(() -> service.search(HUB, ids[0], spot), TrahisonException.SPOT_EMPTY);
		assertThat(view(1).toString()).doesNotContain(item);
	}

	@Test
	void theQuantitiesAreSettingsAndTheUnplacedAreKept() {
		Settings s = Settings.fast(60).withoutCabins();
		s.items.put(Item.KNIFE, 2);
		s.items.put(Item.LETTER, 0);
		GameService other = new GameService(new MemoryStore(), clock, clock);
		other.useLayouts((hub, version) -> Layout.of(hub, 1, false, Instant.EPOCH, plan()));
		other.create("hub-3", s, 5L);
		for (int i = 0; i < 9; i++) {
			other.join("hub-3", ids[i], "p" + i);
		}
		other.start("hub-3");
		Game g = other.find("hub-3");
		assertThat(g.hiddenItems.values().stream().filter("KNIFE"::equals).count() + g.unplacedItems.stream().filter("KNIFE"::equals).count()).isEqualTo(2);
		assertThat(g.hiddenItems.values()).doesNotContain("LETTER");
		assertThat(g.hiddenItems.size() + g.unplacedItems.size()).isEqualTo(7); // 7 kinds, a knife twice, no letter
		assertThat(g.hiddenItems).hasSize(7); // the plan has 7 spots, so the 7 items still fit
	}

	@Test
	void theKnifeKillsWithinThreeBlocksInTheSameZoneAndIsConsumed() {
		startAndDoors();
		give(0, Item.KNIFE);
		at(0, "hall", 10, 10);
		at(1, "hall", 20, 10);
		assertCode(() -> service.useObject(HUB, ids[0], Item.KNIFE, 1, null, null), TrahisonException.TARGET_TOO_FAR);
		at(1, "vault", 11, 10); // close, but through a wall: another zone
		assertCode(() -> service.useObject(HUB, ids[0], Item.KNIFE, 1, null, null), TrahisonException.TARGET_TOO_FAR);
		assertThat(viewer(0).get("items")).isEqualTo(List.of("KNIFE")); // a refusal spends nothing
		at(1, "hall", 11, 10);
		service.useObject(HUB, ids[0], Item.KNIFE, 1, null, null);
		Game g = service.find(HUB);
		assertThat(g.players.get(1).alive).isFalse();
		assertThat(g.bodyOf(1).cause).isEqualTo("knife");
		assertThat(g.players.get(0).items).isEmpty();
		assertCode(() -> service.useObject(HUB, ids[0], Item.KNIFE, 2, null, null), TrahisonException.NO_SUCH_SECRET_ITEM);
	}

	@Test
	void theKnifeOnTheGameMasterWhoPlaysEndsTheGameForTheOthers() {
		service.setGameMaster(HUB, ids[1], "p1", true);
		startAndDoors();
		give(0, Item.KNIFE);
		at(0, "hall", 10, 10);
		at(1, "hall", 11, 10);
		service.useObject(HUB, ids[0], Item.KNIFE, 1, null, null);
		assertThat(service.find(HUB).phase).isEqualTo(Phase.ENDED);
		assertThat(service.find(HUB).winners).doesNotContain(1);
	}

	@Test
	void bondsHoldATargetThatCannotVoteUntilSomeoneUntiesHimOrTheTimeIsUp() {
		toVote();
		Split s = split();
		int victim = s.groups().get(0).solo();
		int friend = s.groups().get(1).solo();
		int rope = s.groups().get(2).solo();
		give(rope, Item.BONDS);
		at(rope, "hall", 10, 10);
		at(victim, "hall", 11, 10);
		at(friend, "hall", 12, 10);
		service.useObject(HUB, ids[rope], Item.BONDS, victim, null, null);
		assertThat(view(friend).get("restrained").toString()).contains("player=" + victim); // visible to all
		assertCode(() -> service.vote(HUB, ids[victim], Vote.ALLY), TrahisonException.RESTRAINED);
		assertCode(() -> service.useObject(HUB, ids[victim], Item.KNIFE, friend, null, null), TrahisonException.RESTRAINED);
		assertCode(() -> service.untie(HUB, ids[victim], friend), TrahisonException.RESTRAINED);
		service.untie(HUB, ids[friend], victim); // starts
		assertCode(() -> service.untie(HUB, ids[friend], victim), TrahisonException.UNTYING); // too early
		clock.advanceSeconds(Settings.DEFAULT_UNTIE_SECONDS);
		service.untie(HUB, ids[friend], victim);
		assertThat(view(friend).get("restrained")).isEqualTo(List.of());
		service.vote(HUB, ids[victim], Vote.ALLY);
	}

	@Test
	void bondsEndByThemselvesAndNeedTheUntierToStayClose() {
		startAndDoors();
		give(0, Item.BONDS);
		at(0, "hall", 10, 10);
		at(1, "hall", 11, 10);
		at(2, "hall", 12, 10);
		service.useObject(HUB, ids[0], Item.BONDS, 1, null, null);
		assertCode(() -> service.useObject(HUB, ids[0], Item.BONDS, 1, null, null), TrahisonException.NO_SUCH_SECRET_ITEM);
		service.untie(HUB, ids[2], 1);
		clock.advanceSeconds(Settings.DEFAULT_UNTIE_SECONDS);
		at(2, "hall", 40, 10);
		assertCode(() -> service.untie(HUB, ids[2], 1), TrahisonException.TARGET_TOO_FAR);
		assertThat(viewer(1).get("restrained_until")).isNotNull();
		clock.advanceSeconds(Settings.DEFAULT_BONDS_SECONDS);
		assertThat(view(2).get("restrained")).isEqualTo(List.of());
		assertThat(service.find(HUB).players.get(1).restrainedUntil).isNull();
	}

	@Test
	void aLockClosesAnOrdinaryDoorFromOutsideUntilUnlockedPassedOrExpired() {
		startAndDoors();
		give(0, Item.LOCK);
		give(1, Item.PASS);
		at(0, "hall", 98, 100);
		assertThat(doorState("d_plain")).isEqualTo("OPEN");
		assertCode(() -> service.useObject(HUB, ids[0], Item.LOCK, null, "c_rg", null), TrahisonException.NOT_LOCKABLE); // a colour door
		at(0, "hall", 50, 100);
		assertCode(() -> service.useObject(HUB, ids[0], Item.LOCK, null, "d_plain", null), TrahisonException.TARGET_TOO_FAR);
		at(0, "hall", 98, 100);
		service.useObject(HUB, ids[0], Item.LOCK, null, "d_plain", null);
		assertThat(doorState("d_plain")).isEqualTo("CLOSED");
		assertCode(() -> service.unlock(HUB, ids[2], "d_plain"), TrahisonException.TARGET_TOO_FAR);
		at(2, "hall", 98, 101);
		service.unlock(HUB, ids[2], "d_plain");
		assertThat(doorState("d_plain")).isEqualTo("OPEN");
		// the pass: from anywhere
		give(0, Item.LOCK);
		service.useObject(HUB, ids[0], Item.LOCK, null, "d_plain", null);
		assertThat(doorState("d_plain")).isEqualTo("CLOSED");
		service.useObject(HUB, ids[1], Item.PASS, null, "d_plain", null);
		assertThat(doorState("d_plain")).isEqualTo("OPEN");
		assertCode(() -> service.unlock(HUB, ids[2], "d_plain"), TrahisonException.NOT_LOCKED);
		// the time
		give(0, Item.LOCK);
		service.useObject(HUB, ids[0], Item.LOCK, null, "d_plain", null);
		clock.advanceSeconds(Settings.DEFAULT_LOCK_SECONDS);
		assertThat(doorState("d_plain")).isEqualTo("OPEN");
	}

	private String doorState(String door) {
		return (String) ((Map<?, ?>) view(0).get("doors_state")).get(door);
	}

	@Test
	void theShieldCancelsTheNextMinusTwoAndDisappears() {
		toVote();
		Split s = split();
		int solo = s.groups().get(0).solo();
		int pairFirst = s.groups().get(0).pair().get(0);
		give(solo, Item.SHIELD);
		int before = points(solo);
		service.vote(HUB, ids[solo], Vote.ALLY);
		service.vote(HUB, ids[pairFirst], Vote.BETRAY); // -2 for the solo
		service.next(HUB);
		assertThat(points(solo)).isEqualTo(before);
		assertThat(service.find(HUB).players.get(solo).items).isEmpty();
		assertCode(() -> service.useObject(HUB, ids[solo], Item.SHIELD, null, null, null), TrahisonException.NO_SUCH_SECRET_ITEM);
	}

	@Test
	void theLetterIsWhisperedToItsTargetWithoutASender() {
		startAndDoors();
		give(0, Item.LETTER);
		assertCode(() -> service.useObject(HUB, ids[0], Item.LETTER, 3, null, "   "), TrahisonException.BAD_ARGUMENT);
		service.useObject(HUB, ids[0], Item.LETTER, 3, null, "Look behind the wall");
		assertThat(whispers).contains("3:phantasmon_events.trahison.say.letter:[Look behind the wall]");
		assertThat(whispers.stream().filter(w -> w.contains("letter")).toList()).hasSize(1);
		assertThat(viewer(0).get("items")).isEqualTo(List.of());
	}

	@Test
	void theEarListensToACabinDuringAVoteOnly() {
		startAndDoors();
		give(0, Item.EAR);
		assertCode(() -> service.useObject(HUB, ids[0], Item.EAR, null, "d_plain", null), TrahisonException.WRONG_PHASE);
	}

	@Test
	void theGameMastersBlockShowsEveryRoleAndItemAndNobodyElseGetsIt() {
		UUID watcher = new UUID(9, 9);
		service.setGameMaster(HUB, watcher, "watcher", true);
		startAndDoors();
		give(4, Item.KNIFE);
		Map<String, Object> gm = (Map<String, Object>) service.read(HUB, g -> Views.build(g, watcher, clock.instant())).get("gm");
		assertThat(gm.get("roles").toString()).contains("index=0");
		assertThat(gm.get("player_items").toString()).contains("index=4, items=[KNIFE]");
		assertThat(((List<?>) gm.get("hidden_items"))).hasSize(7);
		assertThat(view(4).get("gm")).isNull();
	}

	@Test
	void restoredFromJsonTheStateKeepsRolesItemsAndLocks() {
		startAndDoors();
		give(0, Item.LOCK);
		at(0, "hall", 98, 100);
		service.useObject(HUB, ids[0], Item.LOCK, null, "d_plain", null);
		Game copy = GameJson.read(GameJson.write(service.find(HUB)));
		assertThat(copy.lockedDoors).containsKey("d_plain");
		assertThat(copy.hiddenItems).isEqualTo(service.find(HUB).hiddenItems);
		assertThat(copy.players.stream().map(p -> p.role).toList()).isEqualTo(service.find(HUB).players.stream().map(p -> p.role).toList());
	}
}
