package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.events.trahison.room.Give;
import com.mystaria.phantasmon_backend.events.trahison.room.Step;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Objects hidden in the room, to find and bring back. The room has up to {@value #MAX_SPOTS} <em>spots</em>: the clue
 * roles {@code spot_1}..{@code spot_n}, each given ({@code Step.gives}) to a slot of kind {@code pickup} (or any other).
 * The seed picks which {@code filled} spots really hold an object and which object each holds; a spot left empty shows
 * nothing, so its slot is not even active: the players do not know where the objects are (the counter only counts them). An object is picked up with the room's {@code pick_up} (it goes to the inventory); a {@code document} may be
 * given too, and is read where it lies.
 *
 * <h2>Solved</h2>
 * The step is solved when <b>every object the seed placed has been picked up</b> (decisions-tranchees.md, D32): picking an
 * object up is enough, nothing is brought anywhere. {@code RoomService} tells the mechanism on every {@code pick_up}
 * ({@link Mechanism#onPickUp}). Giving an object away or using it elsewhere afterwards changes nothing. The step binds no
 * slot and holds no screen gesture (a slot of role {@value #DEPOSIT_ROLE} may still be bound: it only shows the counter).
 *
 * <h2>Parameters</h2>
 * <ul>
 * <li>{@code spots} 2..6 (default 6): how many {@code spot_n} roles exist. Give each of them to a slot: a spot with no
 * give would hide an object nobody can reach, and the validator refuses it;</li>
 * <li>{@code filled} 1..spots (default 3): how many spots hold an object;</li>
 * <li>{@code items} (text, default {@value #DEFAULT_ITEMS}): the pool of item ids, comma separated; at least
 * {@code filled} of them. Ids must not clash with items other steps give;</li>
 * <li>{@code document} (text, default none): when not blank, the step also produces a {@code document} clue (role
 * {@value #DOCUMENT_ROLE}, always listed among the clue roles; giving it with no text is refused): its pages, separated by a line {@code ---} (a line {@code @image:<id>} shows a picture).
 * It is a clue like the others: give it to a {@code document} slot, from the start or when solved.</li>
 * </ul>
 *
 * <h2>Generation</h2>
 * Shuffled with the step's generator: the same seed always fills the same spots with the same objects. Always solvable:
 * every object placed is a clue the players can pick up, and picking them all up solves the step.
 */
@Component
public class PickupSet implements Mechanism {

	public static final String ID = "pickup_set";
	public static final String DEPOSIT_ROLE = "deposit";
	public static final String DOCUMENT_ROLE = "document";
	public static final String SPOT_PREFIX = "spot_";

	static final int MIN_SPOTS = 2;
	static final int MAX_SPOTS = 6;
	static final int DEFAULT_SPOTS = 6;
	static final int DEFAULT_FILLED = 3;
	static final String DEFAULT_ITEMS = "fuse,gear,lens,valve,badge,crystal";
	static final int MAX_DOCUMENT_CHARS = 4000;
	private static final Pattern ITEM_ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");

	record Config(int spots, int filled, List<String> items, String document) {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int difficulty(JsonNode params) {
		int filled = config(params).filled();
		return filled <= 2 ? 1 : filled <= 4 ? 2 : 3;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		Config c = config(params);
		return 45 + 25 * c.filled() + 5 * c.spots();
	}

	@Override
	public List<String> clueRoles(JsonNode params) {
		Config c = config(params);
		List<String> roles = new ArrayList<>();
		for (int i = 1; i <= c.spots(); i++) {
			roles.add(SPOT_PREFIX + i);
		}
		roles.add(DOCUMENT_ROLE); // always listed (the editor offers the defaults); it exists only when the document param has a text
		return roles;
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(
				new MechanismParam("spots", "int", MIN_SPOTS, MAX_SPOTS, String.valueOf(DEFAULT_SPOTS),
						"Number of spot_n clue roles; give each of them to a slot"),
				new MechanismParam("filled", "int", 1, MAX_SPOTS, String.valueOf(DEFAULT_FILLED),
						"How many spots hold an object (the seed picks which)"),
				new MechanismParam("items", "text", 0, 0, DEFAULT_ITEMS,
						"Pool of item ids, comma separated, at least 'filled' of them"),
				new MechanismParam("document", "text", 0, 0, "",
						"Optional document given as the document clue: pages separated by a line ---"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		checkInt(params, "spots", MIN_SPOTS, MAX_SPOTS, problems);
		checkInt(params, "filled", 1, MAX_SPOTS, problems);
		if (params != null && params.has("items") && !params.get("items").isString()) {
			problems.add("items must be a text of ids separated by commas");
		}
		if (params != null && params.has("document") && !params.get("document").isString()) {
			problems.add("document must be a text");
		}
		if (!problems.isEmpty()) {
			return problems;
		}
		Config c = config(params);
		if (intParam(params, "filled", DEFAULT_FILLED) > c.spots()) {
			problems.add("filled: at most spots");
		}
		for (String id : rawItems(params)) {
			if (!ITEM_ID.matcher(id).matches()) {
				problems.add("items: bad id '" + id + "'");
			}
		}
		if (new LinkedHashSet<>(rawItems(params)).size() < c.filled()) {
			problems.add("items: at least filled distinct ids");
		}
		if (c.document().length() > MAX_DOCUMENT_CHARS) {
			problems.add("document: at most " + MAX_DOCUMENT_CHARS + " characters");
		}
		return problems;
	}

	/** Every spot must be given to a slot from the start, or its object would be out of reach. No slot is needed to bring objects to. */
	@Override
	public List<String> validateStep(Step step) {
		List<String> problems = new ArrayList<>();
		Config c = config(step.params());
		for (int i = 1; i <= c.spots(); i++) {
			String role = SPOT_PREFIX + i;
			if (step.gives().stream().noneMatch(g -> role.equals(g.clue()) && Give.OPEN.equals(g.when()))) {
				problems.add("missing=" + role + ";when=open");
			}
		}
		if (c.document().isBlank() && step.gives().stream().anyMatch(g -> DOCUMENT_ROLE.equals(g.clue()))) {
			problems.add("missing=param:" + DOCUMENT_ROLE);
		}
		return problems;
	}

	private static void checkInt(JsonNode params, String name, int min, int max, List<String> problems) {
		if (params != null && params.has(name)) {
			JsonNode v = params.get(name);
			if (!v.isInt() || v.asInt() < min || v.asInt() > max) {
				problems.add(name + " must be an integer " + min + ".." + max);
			}
		}
	}

	private static List<String> rawItems(JsonNode params) {
		String text = params != null && params.path("items").isString() ? params.get("items").asString() : DEFAULT_ITEMS;
		List<String> ids = new ArrayList<>();
		for (String part : text.split(",")) {
			if (!part.isBlank()) {
				ids.add(part.trim());
			}
		}
		return ids;
	}

	/** The parameters with their defaults; out-of-range values are clamped (validateParams reports them). */
	static Config config(JsonNode params) {
		int spots = clamp(intParam(params, "spots", DEFAULT_SPOTS), MIN_SPOTS, MAX_SPOTS);
		int filled = clamp(intParam(params, "filled", DEFAULT_FILLED), 1, spots);
		List<String> items = new ArrayList<>(new LinkedHashSet<>(rawItems(params)));
		String document = params != null && params.path("document").isString() ? params.get("document").asString() : "";
		return new Config(spots, filled, items, document);
	}

	private static int intParam(JsonNode params, String name, int fallback) {
		return params != null && params.path(name).isNumber() ? params.get(name).asInt() : fallback;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	// ---------------------------------------------------------------- generation

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		Config c = config(params);
		List<Integer> spots = new ArrayList<>();
		for (int i = 1; i <= c.spots(); i++) {
			spots.add(i);
		}
		shuffle(spots, rng);
		List<Integer> filled = new ArrayList<>(spots.subList(0, c.filled()));
		Collections.sort(filled);
		List<String> pool = new ArrayList<>(c.items());
		shuffle(pool, rng);
		List<Clue> clues = new ArrayList<>();
		ArrayNode filledSpots = Json.array();
		ArrayNode required = Json.array();
		for (int k = 0; k < filled.size(); k++) {
			String item = pool.get(k % pool.size());
			clues.add(Clue.item(SPOT_PREFIX + filled.get(k), item));
			filledSpots.add(SPOT_PREFIX + filled.get(k));
			required.add(item);
		}
		if (!c.document().isBlank()) {
			clues.add(Clue.document(DOCUMENT_ROLE, pages(c.document())));
		}
		ObjectNode data = Json.object();
		data.set("filled", filledSpots);
		data.set("required", required);
		data.set("delivered", Json.array());
		data.put("solved", false);
		return new MechanismState(data, clues);
	}

	/** The pages of a document text: separated by a line holding only {@code ---}. */
	static List<String> pages(String text) {
		List<String> pages = new ArrayList<>();
		StringBuilder page = new StringBuilder();
		for (String line : text.split("\n", -1)) {
			if (line.strip().equals("---")) {
				pages.add(page.toString().strip());
				page.setLength(0);
			} else {
				page.append(line).append('\n');
			}
		}
		pages.add(page.toString().strip());
		pages.removeIf(String::isEmpty);
		return pages.isEmpty() ? List.of(text.strip()) : pages;
	}

	// ---------------------------------------------------------------- gestures

	/** The step has no screen: nothing to do (a gesture is ignored). */
	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		return InputResult.unchanged(state);
	}

	/** An object of the step picked up: counted when the seed placed it and it is not counted yet. */
	@Override
	public Optional<InputResult> onPickUp(MechanismState state, int player, String role, String item) {
		ObjectNode old = state.data();
		if (old.path("solved").asBoolean(false) || !contains(old.get("required"), item) || contains(old.get("delivered"), item)) {
			return Optional.empty();
		}
		ObjectNode data = state.copyData();
		ArrayNode delivered = (ArrayNode) data.get("delivered");
		delivered.add(item);
		boolean solved = delivered.size() == data.get("required").size();
		data.put("solved", solved);
		ObjectNode event = Json.object();
		event.put("type", "collect");
		event.put("item", item);
		return Optional.of(new InputResult(state.withData(data), List.of(event), solved));
	}

	private static boolean contains(JsonNode array, String value) {
		for (JsonNode n : array) {
			if (n.asString("").equals(value)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- view

	/** How many objects there are and which are picked up (field {@code delivered}); never the ones still hidden (the game master sees them). */
	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		ObjectNode view = Json.object();
		view.put("total", d.get("required").size());
		view.set("delivered", d.get("delivered").deepCopy());
		view.put("solved", d.path("solved").asBoolean(false));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.set("required", d.get("required").deepCopy());
			view.set("filled", d.get("filled").deepCopy());
		}
		return view;
	}

	private static void shuffle(List<?> list, RandomGenerator rng) {
		for (int i = list.size() - 1; i > 0; i--) {
			Collections.swap(list, i, rng.nextInt(i + 1));
		}
	}
}
