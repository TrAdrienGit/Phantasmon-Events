package com.mystaria.events.trahison.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * The mechanisms the room editor offers: id, the roles a step binds to slots, the clue roles it gives, a default
 * difficulty and duration, and the parameters it reads.
 *
 * <p>The list comes from the backend ({@code GET /events/trahison/mechanisms}, fetched by {@link #refresh()} when the
 * editor opens and kept until the next refresh). The backend does not say which slot roles a step binds, so those come
 * from the built-in table below for the ids it knows; a mechanism the table does not know offers only the exit role.
 * If the call fails the built-in list is used as is and {@link #usingFallback()} is true (the editor warns).
 */
final class MechanismCatalog {

	/**
	 * One parameter a mechanism reads: {@code type} is {@code int} (between {@code min} and {@code max}), {@code bool} or
	 * {@code text}; {@code def} its default as text. The editor's parameter rows are generic (any key and value), these only
	 * add a range check and a default to offer.
	 */
	record Param(String name, String type, int min, int max, String def) {
	}

	/** One mechanism. {@code known}: the backend has it today. {@code difficulty}/{@code seconds}: an estimate for the totals. */
	record Info(String id, boolean known, List<String> roles, List<String> clues, int difficulty, int seconds, List<Param> params) {

		Info(String id, boolean known, List<String> roles, List<String> clues, int difficulty, int seconds) {
			this(id, known, roles, clues, difficulty, seconds, List.of());
		}
	}

	/** The role that opens the room's exit; its slot must be of kind {@code exit} (the backend checks it too). */
	static final String EXIT_ROLE = "exit";

	private static final List<Info> BUILT_IN = List.of(
			new Info("debug_button", true, List.of("press"), List.of(), 1, 10),
			new Info("three_dials", true, List.of("dials"), List.of("code"), 1, 60),
			// Planned (T3.5); roles and clues are the editor's best guess for those the backend does not list.
			new Info("keypad", false, List.of("keypad"), List.of(), 1, 60, List.of(new Param("digits", "int", 3, 6, "4"))),
			new Info("pickup_set", false, List.of("pickups"), List.of("item", "document"), 1, 90),
			new Info("ring_dial", false, List.of("terminal"), List.of("symbols"), 2, 240),
			new Info("wiring", false, List.of("terminal"), List.of("colors", "document"), 2, 180),
			new Info("code_breaker", false, List.of("terminal"), List.of(), 3, 300),
			new Info("three_levers", false, List.of("lever_a", "lever_b", "lever_c"), List.of(), 2, 120));

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

	private static final class RemoteInfo {
		String id;
		List<String> clueRoles;
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
				Info known = BUILT_IN.stream().filter(i -> i.id().equals(r.id)).findFirst().orElse(null);
				List<Param> params = new ArrayList<>();
				if (r.params != null) {
					for (RemoteParam p : r.params) {
						params.add(new Param(p.name, p.type, p.min, p.max, p.def));
					}
				}
				list.add(new Info(r.id, true, known == null ? List.of() : known.roles(),
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
		List<String> roles = new java.util.ArrayList<>(find(id).map(Info::roles).orElse(List.of()));
		roles.add(EXIT_ROLE);
		return roles;
	}

	static List<Param> params(String id) {
		return find(id).map(Info::params).orElse(List.of());
	}

	static List<String> clues(String id) {
		return find(id).map(Info::clues).orElse(List.of());
	}
}
