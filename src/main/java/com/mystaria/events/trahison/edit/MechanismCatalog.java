package com.mystaria.events.trahison.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * The mechanisms the room editor offers: id, the roles a step binds to slots (with the kinds of slot each accepts), the
 * clue roles it gives, a default difficulty and duration, and the parameters it reads.
 *
 * <p>Everything comes from the backend ({@code GET /events/trahison/mechanisms}, fetched by {@link #refresh()} when the
 * editor opens and kept until the next refresh), slot roles included: there is no table of roles here. If the call fails
 * the built-in list, which only has the ids (no roles, no clues: a step can bind its exit role only), is used and
 * {@link #usingFallback()} is true (the editor warns; the backend decides on save).
 */
final class MechanismCatalog {

	/**
	 * One parameter a mechanism reads: {@code type} is {@code int} (between {@code min} and {@code max}), {@code bool} or
	 * {@code text}; {@code def} its default as text. The editor's parameter rows are generic (any key and value), these only
	 * add a range check and a default to offer.
	 */
	record Param(String name, String type, int min, int max, String def) {
	}

	/** A role a step binds to a slot, and the kinds of slot it accepts (empty: any kind). */
	record SlotRole(String role, List<String> kinds) {

		boolean accepts(String kind) {
			return kinds.isEmpty() || kinds.contains(kind);
		}
	}

	/** One mechanism. {@code known}: the backend has it today. {@code difficulty}/{@code seconds}: an estimate for the totals. */
	record Info(String id, boolean known, List<SlotRole> roles, List<String> clues, int difficulty, int seconds, List<Param> params) {

		Info(String id, boolean known, int difficulty, int seconds) {
			this(id, known, List.of(), List.of(), difficulty, seconds, List.of());
		}
	}

	/** The role that opens the room's exit; its slot must be of kind {@code exit} (the backend checks it too). */
	static final String EXIT_ROLE = "exit";

	/**
	 * Used only while the backend has not answered: the ids the editor knows of, so a step can still be added. Those marked
	 * {@code known = false} are planned (T3.5/T9) and not in the backend yet.
	 */
	private static final List<Info> BUILT_IN = List.of(
			new Info("debug_button", true, 1, 10),
			new Info("three_dials", true, 1, 60),
			new Info("keypad", true, 2, 78),
			new Info("pickup_set", true, 2, 135),
			new Info("ring_dial", true, 2, 240),
			new Info("wiring", true, 2, 140),
			new Info("code_breaker", true, 2, 220),
			new Info("three_levers", true, 2, 150),
			new Info("dex_code", true, 1, 140),
			new Info("unown_text", true, 2, 150),
			new Info("weigh_in", true, 2, 120),
			new Info("type_chain", true, 2, 150),
			new Info("battle_puzzle", true, 3, 300),
			new Info("cry_match", true, 2, 160),
			new Info("pc_box", true, 2, 110),
			new Info("power_spot", true, 1, 70),
			new Info("sliding_blocks", true, 2, 170),
			new Info("rotating_tiles", true, 2, 150),
			new Info("safe_dial", true, 2, 170),
			new Info("twin_screens", true, 2, 150));

	/** The backend's answer, null until a call succeeds. */
	private static volatile List<Info> remote;
	/** True when the last call failed (and no earlier one succeeded): the built-in list is in use. */
	private static volatile boolean failed;

	/** One entry of the backend's answer (snake_case JSON). */
	private static final class RemoteParam {
		String name;
		String type;
		int min;
		int max;
		String def;
	}

	private static final class RemoteRole {
		String role;
		List<String> kinds;
	}

	private static final class RemoteInfo {
		String id;
		List<String> clueRoles;
		List<RemoteRole> slotRoles;
		List<RemoteParam> params;
		int difficulty;
		int expectedSeconds;
	}

	private MechanismCatalog() {
	}

	/** Fetches the backend's list; on failure keeps what was known and tells the editor's status line. */
	static void refresh() {
		if (!PhantasmonApi.isAdmin()) {
			return;
		}
		PhantasmonApi.get("/events/trahison/mechanisms", RemoteInfo[].class).whenComplete((answer, failure) -> Minecraft.getInstance().execute(() -> {
			if (failure != null || answer == null) {
				failed = remote == null;
				if (failed) {
					RoomEditor.setStatus(Component.translatable("phantasmon_events.trahison.room_edit.mechanism.fallback"), true);
				}
				return;
			}
			List<Info> list = new ArrayList<>();
			for (RemoteInfo r : answer) {
				List<SlotRole> roles = new ArrayList<>();
				if (r.slotRoles != null) {
					for (RemoteRole role : r.slotRoles) {
						roles.add(new SlotRole(role.role, role.kinds == null ? List.of() : List.copyOf(role.kinds)));
					}
				}
				List<Param> params = new ArrayList<>();
				if (r.params != null) {
					for (RemoteParam p : r.params) {
						params.add(new Param(p.name, p.type, p.min, p.max, p.def));
					}
				}
				list.add(new Info(r.id, true, List.copyOf(roles),
						r.clueRoles == null ? List.of() : List.copyOf(r.clueRoles), r.difficulty, r.expectedSeconds, List.copyOf(params)));
			}
			remote = List.copyOf(list);
			failed = false;
		}));
	}

	/** True when the backend's list could not be read and the built-in one is shown. */
	static boolean usingFallback() {
		return remote == null && failed;
	}

	static List<Info> all() {
		List<Info> list = remote;
		return list != null ? list : BUILT_IN;
	}

	static List<String> ids() {
		return all().stream().map(Info::id).toList();
	}

	static Optional<Info> find(String id) {
		return all().stream().filter(info -> info.id().equals(id)).findFirst();
	}

	/** The roles to offer for a mechanism, the exit role last. A mechanism the catalog does not know offers only the exit. */
	static List<String> roles(String id) {
		List<String> roles = new java.util.ArrayList<>(find(id).map(Info::roles).orElse(List.of()).stream().map(SlotRole::role).toList());
		roles.add(EXIT_ROLE);
		return roles;
	}

	/** Whether a slot of kind {@code kind} may be bound to {@code role} of the mechanism (the exit role needs kind exit; unknown roles accept anything). */
	static boolean accepts(String id, String role, String kind) {
		if (EXIT_ROLE.equals(role)) {
			return "exit".equals(kind);
		}
		return find(id).flatMap(info -> info.roles().stream().filter(r -> r.role().equals(role)).findFirst())
				.map(r -> r.accepts(kind)).orElse(true);
	}

	/** The kinds a role accepts, for messages (empty: any). */
	static List<String> kinds(String id, String role) {
		return find(id).flatMap(info -> info.roles().stream().filter(r -> r.role().equals(role)).findFirst())
				.map(SlotRole::kinds).orElse(List.of());
	}

	static List<Param> params(String id) {
		return find(id).map(Info::params).orElse(List.of());
	}

	static List<String> clues(String id) {
		return find(id).map(Info::clues).orElse(List.of());
	}
}
