package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutDoor;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutValidator;
import com.mystaria.phantasmon_backend.events.trahison.rules.Assignment;
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.DoorGroup;
import com.mystaria.phantasmon_backend.events.trahison.rules.Doors;
import com.mystaria.phantasmon_backend.events.trahison.rules.Split;
import com.mystaria.phantasmon_backend.events.trahison.rules.Status;

/**
 * The colour doors of the current round and what stands at their threshold (T2.3). Pure: it reads a game and its plan and
 * never changes them. The bracelets a player presents are his wrist bracelet and the ones he carries ({@link #presented}).
 */
final class DoorThresholds {

	private DoorThresholds() {
	}

	/** A colour door of the round with the {@link Door} it stands for. */
	record Entry(LayoutDoor door, Door pair, int index) {
	}

	/** The colour doors of this round that have a valid colour pair, in the plan's order. */
	static List<Entry> colorDoors(LayoutContent plan, int round) {
		List<Entry> out = new ArrayList<>();
		for (LayoutDoor d : plan.doors()) {
			if ("color".equals(d.type()) && d.round() != null && d.round() == round) {
				Optional<Door> pair = LayoutValidator.doorPair(d);
				if (pair.isPresent()) {
					out.add(new Entry(d, pair.get(), Door.ALL.indexOf(pair.get())));
				}
			}
		}
		return out;
	}

	/** The bracelets a living player holds out: his wrist bracelet, and the ones picked up from bodies (T6). */
	static Set<Integer> presented(Game g, int player) {
		Set<Integer> out = new LinkedHashSet<>();
		PlayerState p = g.players.get(player);
		out.add(player);
		if (p.carried != null) {
			out.addAll(p.carried);
		}
		return out;
	}

	/** The players standing in this zone who may still present a bracelet: alive, and who have not passed a door yet. */
	static List<Integer> standingAt(Game g, RoundState r, String zone) {
		List<Integer> out = new ArrayList<>();
		if (zone == null) {
			return out;
		}
		for (int i = 0; i < g.players.size(); i++) {
			PlayerState p = g.players.get(i);
			if (p.alive && zone.equals(p.zone) && r.passedDoor[i] < 0) {
				out.add(i);
			}
		}
		return out;
	}

	/** The splits that agree with every pass so far. */
	static List<Split> consistentSplits(RoundState r) {
		return Doors.validSplits(r.assignment()).stream().filter(s -> GameService.consistent(r, s)).toList();
	}

	/** The hints of every colour door of the current round; empty outside DOORS. */
	static List<DoorHint> hints(Game g, LayoutContent plan) {
		RoundState r = g.currentRound();
		if (g.phase != Phase.DOORS || r == null) {
			return List.of();
		}
		List<DoorHint> out = new ArrayList<>();
		List<Split> splits = consistentSplits(r);
		for (Entry e : colorDoors(plan, g.round)) {
			out.add(hint(g, r, splits, e));
		}
		return out;
	}

	private static DoorHint hint(Game g, RoundState r, List<Split> splits, Entry e) {
		Assignment a = r.assignment();
		List<Integer> standing = standingAt(g, r, e.door().thresholdZone());
		Set<Integer> bracelets = new LinkedHashSet<>();
		standing.forEach(p -> bracelets.addAll(presented(g, p)));

		boolean ready = false;
		List<List<DoorHint.Need>> options = new ArrayList<>();
		for (Split s : splits) {
			List<DoorHint.Need> lacking = lacking(a, s.group(e.pair()), bracelets);
			ready |= lacking.isEmpty();
			options.add(lacking);
		}
		boolean incompatible = !ready && Doors.canOpen(e.pair(), a, bracelets);
		List<DoorHint.Need> missing = List.of();
		List<DoorHint.Need> alt = null;
		if (!ready && !options.isEmpty()) {
			options.sort(Comparator.comparingInt(List::size)); // stable: the first split wins a tie
			missing = options.get(0);
			if (options.size() > 1 && options.get(1).size() == missing.size()) {
				alt = options.get(1);
			}
		}
		return new DoorHint(e.door().id(), e.index(),
			List.of(e.pair().first().name(), e.pair().second().name()), standing.size(), ready, incompatible, missing, alt);
	}

	/** What the presented bracelets lack to match this group: its pair colour as PAIR, its solo colour as SOLO. */
	private static List<DoorHint.Need> lacking(Assignment a, DoorGroup group, Set<Integer> bracelets) {
		boolean pair = false;
		boolean solo = false;
		for (int b : bracelets) {
			pair |= a.color(b) == group.pairColor() && a.status(b) == Status.PAIR;
			solo |= a.color(b) == group.soloColor() && a.status(b) == Status.SOLO;
		}
		List<DoorHint.Need> out = new ArrayList<>();
		if (!pair) {
			out.add(new DoorHint.Need(group.pairColor().name(), Status.PAIR.name()));
		}
		if (!solo) {
			out.add(new DoorHint.Need(group.soloColor().name(), Status.SOLO.name()));
		}
		return out;
	}

	/**
	 * The living players who have not passed yet and whose own bracelet belongs to a group the bracelets at the threshold
	 * satisfy: the door waits for all of them (one pair bracelet and the solo are enough to open it, the other member of the
	 * pair may still be on his way). Empty when the door is not ready.
	 */
	static List<Integer> expected(Game g, RoundState r, Entry e) {
		Assignment a = r.assignment();
		List<Integer> standing = standingAt(g, r, e.door().thresholdZone());
		Set<Integer> bracelets = new LinkedHashSet<>();
		standing.forEach(p -> bracelets.addAll(presented(g, p)));
		Set<Integer> members = new LinkedHashSet<>();
		for (Split s : consistentSplits(r)) {
			DoorGroup group = s.group(e.pair());
			if (lacking(a, group, bracelets).isEmpty()) {
				members.addAll(group.members());
			}
		}
		List<Integer> out = new ArrayList<>();
		if (standing.stream().noneMatch(members::contains)) {
			return out;
		}
		for (int p : members) {
			if (g.players.get(p).alive && r.passedDoor[p] < 0) {
				out.add(p);
			}
		}
		return out;
	}
}
