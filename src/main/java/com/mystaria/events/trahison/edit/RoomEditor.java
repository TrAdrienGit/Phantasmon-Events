package com.mystaria.events.trahison.edit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.RoomDto.GiveDto;
import com.mystaria.events.trahison.edit.RoomDto.P;
import com.mystaria.events.trahison.edit.RoomDto.SlotDto;
import com.mystaria.events.trahison.edit.RoomDto.StepDto;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.network.BackendApiException;

/**
 * The room editor (Documentation/trahison/etapes/T3-salles.md, T3.2): a room is built by hand in a singleplayer creative
 * world inside the template ({@link RoomTemplate}), its slots and its chain of steps are described here, and the whole is
 * saved to the backend (definition and blocks). It holds no game rule: {@link RoomChecks} only spares a round trip, the
 * backend validates again.
 *
 * <p>The builder needs normal clicks to build, so the selection tool (corner A on left click, B on right click, as in the
 * plan editor) is only active while a <b>stick</b> is held in the main hand. {@link TrahisonEditor#OPEN_KEY} opens the
 * panel ({@link RoomScreen}). Client thread; the world is read and written on the integrated server's thread
 * ({@link RoomSchematic}, solution S12).
 */
public final class RoomEditor {

	private static final String T = "phantasmon_events.trahison.room_edit.";

	private static boolean active;
	/** Where the template stands in this world. */
	static RoomTemplate.Frame frame;
	static RoomDto room = new RoomDto();
	static boolean dirty;
	static boolean busy;
	static Component status;
	static boolean statusError;
	static final Selection selection = new Selection();
	static String selectedSlot;
	static String selectedStep;
	/** "Create from a sheet": the hint of each slot of the template, and the slots whose proposed position is not confirmed yet. */
	static final Map<String, String> hints = new HashMap<>();
	static final Set<String> unplaced = new LinkedHashSet<>();
	/** The problems found by the last save attempt (local or from the backend). */
	static List<RoomChecks.Issue> saveIssues = new ArrayList<>();

	private static int revision;
	private static int liveRevision = -1;
	private static List<RoomChecks.Issue> live = List.of();

	private RoomEditor() {
	}

	// ---------------------------------------------------------------- registration

	/** Called by {@link TrahisonEditor#register()}. */
	static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (active && client.screen == null && client.player != null && holdingWand(client.player)) {
				while (client.options.keyAttack.consumeClick()) {
					setCorner(true);
				}
				while (client.options.keyUse.consumeClick()) {
					setCorner(false);
				}
			}
		});
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> wandInHand(player, level.isClientSide)
				? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> wandInHand(player, level.isClientSide)
				? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> wandInHand(player, level.isClientSide)
				? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
		WorldRenderEvents.AFTER_TRANSLUCENT.register(RoomWorld::render);
		RoomHud.register();
		RoomTry.register();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(RoomEditor::reset));
	}

	private static boolean holdingWand(Player player) {
		return player.getMainHandItem().is(Items.STICK);
	}

	private static boolean wandInHand(Player player, boolean clientSide) {
		return active && clientSide && holdingWand(player);
	}

	private static void reset() {
		RoomTry.stop(false, null);
		active = false;
		frame = null;
		room = new RoomDto();
		dirty = false;
		busy = false;
		selection.clear();
		selectedSlot = null;
		selectedStep = null;
		saveIssues = new ArrayList<>();
		hints.clear();
		unplaced.clear();
		status = null;
		changedRevision();
	}

	// ---------------------------------------------------------------- state

	static boolean isActive() {
		return active;
	}

	static void openPanel() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen == null && mc.player != null) {
			mc.setScreen(new RoomScreen());
		}
	}

	static void setStatus(Component message, boolean error) {
		status = message;
		statusError = error;
	}

	private static Component t(String key, Object... args) {
		return Component.translatable(T + key, args);
	}

	private static void chat(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(message, false);
		}
	}

	private static void changedRevision() {
		revision++;
	}

	/** Marks the room as changed since the last save or load. */
	static void changed() {
		RoomTry.stop(true, t("try_stopped_edit")); // D281: editing ends the try
		dirty = true;
		changedRevision();
	}

	/** The problems the editor finds in the room as it is now (cached until the next change). */
	static List<RoomChecks.Issue> live() {
		if (liveRevision != revision) {
			List<RoomChecks.Issue> found = new ArrayList<>(RoomChecks.check(room));
			if (!unplaced.isEmpty()) {
				// Not tied to one slot (no id): every row of the list would turn red; the info lists them in chain order.
				found.add(new RoomChecks.Issue("LOCAL_SLOTS_UNPLACED", List.of(), String.join(", ", todo())));
			}
			live = found;
			liveRevision = revision;
		}
		return live;
	}

	/** Live problems and the ones of the last save attempt, without duplicates. */
	static List<RoomChecks.Issue> issues() {
		Set<RoomChecks.Issue> all = new LinkedHashSet<>(saveIssues);
		all.addAll(live());
		return new ArrayList<>(all);
	}

	static boolean hasIssue(String id) {
		return issues().stream().anyMatch(issue -> !issue.warning() && issue.ids().contains(id));
	}

	// ---------------------------------------------------------------- commands

	/** Why the editor cannot run here, or null when it can: a singleplayer creative world and a Phantasmon admin. */
	private static Component refusal() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return t("refuse.no_world");
		}
		if (!PhantasmonApi.isAdmin()) {
			return t("refuse.not_admin");
		}
		if (mc.getSingleplayerServer() == null) {
			return t("refuse.not_singleplayer");
		}
		if (!mc.player.isCreative()) {
			return t("refuse.not_creative");
		}
		return null;
	}

	/** The template standing where the player is, turned the way they face. */
	private static RoomTemplate.Frame frameHere() {
		Minecraft mc = Minecraft.getInstance();
		BlockPos under = mc.player.blockPosition().below();
		int k = switch (mc.player.getDirection()) {
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			default -> 0;
		};
		return new RoomTemplate.Frame(under.getX(), under.getY(), under.getZ(), k);
	}

	private static boolean heightFits(RoomTemplate.Frame f) {
		Minecraft mc = Minecraft.getInstance();
		return f.ay() >= mc.level.getMinBuildHeight() && f.ay() + RoomTemplate.SIZE_Y <= mc.level.getMaxBuildHeight();
	}

	/** {@code room new <id>}. */
	public static void start(String id) {
		start(id, null);
	}

	/** The template ids the command offers (tab completion). */
	public static List<String> templateIds() {
		return RoomTemplates.IDS;
	}

	/** {@code room templates}: the nine sheets the editor can start from. */
	public static void templates() {
		chat(t("templates.title", RoomTemplates.IDS.size()));
		for (String template : RoomTemplates.IDS) {
			try {
				RoomTemplates.Loaded loaded = RoomTemplates.load(template);
				chat(t("templates.line", template, loaded.room().name, loaded.room().difficulty, loaded.room().steps.size(), loaded.room().slots.size()));
			} catch (IOException | RuntimeException ex) {
				chat(t("templates.broken", template));
			}
		}
		chat(t("templates.usage"));
	}

	/**
	 * {@code room new <id> [template]}: with a template (one of {@link RoomTemplates#IDS}), the room starts with the chain of the
	 * sheet and all its slots at their proposed positions, shown as ghosts until the builder places them.
	 */
	public static void start(String id, String template) {
		Component refused = refusal();
		if (refused != null) {
			chat(refused);
			return;
		}
		if (!RoomChecks.validId(id)) {
			chat(t("refuse.bad_id", id));
			return;
		}
		if (active && dirty) {
			chat(t("refuse.unsaved"));
			return;
		}
		if (busy) {
			chat(t("refuse.busy"));
			return;
		}
		RoomTemplate.Frame f = frameHere();
		if (!heightFits(f)) {
			chat(t("refuse.height"));
			return;
		}
		RoomTemplates.Loaded loaded = null;
		if (template != null) {
			if (!RoomTemplates.known(template)) {
				chat(t("refuse.bad_template", template, String.join(", ", RoomTemplates.IDS)));
				return;
			}
			try {
				loaded = RoomTemplates.load(template);
			} catch (IOException | RuntimeException ex) {
				chat(t("refuse.template_unreadable", template, String.valueOf(ex.getMessage())));
				return;
			}
		}
		begin(f, loaded == null ? new RoomDto() : loaded.room());
		room.id = id;
		room.name = loaded == null || loaded.room().name == null ? id : loaded.room().name;
		room.author = Minecraft.getInstance().player.getGameProfile().getName();
		dirty = true;
		if (loaded != null) {
			hints.putAll(loaded.hints());
			unplaced.addAll(loaded.unplaced());
			selectedSlot = todo().isEmpty() ? null : todo().get(0);
			RoomScreen.showTodo();
			setStatus(t("status.new_template", template, unplaced.size()), false);
			chat(t("started_template", id, template, room.steps.size(), unplaced.size(), TrahisonEditor.OPEN_KEY.getTranslatedKeyMessage(),
					Items.STICK.getDescription()));
			return;
		}
		setStatus(t("status.new"), false);
		chat(t("started", id, TrahisonEditor.OPEN_KEY.getTranslatedKeyMessage(), Items.STICK.getDescription()));
	}

	private static void begin(RoomTemplate.Frame f, RoomDto loaded) {
		RoomTry.stop(true, null);
		TrahisonEditor.deactivate();
		MechanismCatalog.refresh();
		active = true;
		frame = f;
		room = loaded.normalized();
		dirty = false;
		selection.clear();
		selectedSlot = null;
		selectedStep = null;
		saveIssues = new ArrayList<>();
		hints.clear();
		unplaced.clear();
		changedRevision();
	}

	/** {@code room off}: leaves the editor; nothing is saved, the blocks stay in the world. */
	public static void stop() {
		if (!active) {
			chat(t("off.not_active"));
			return;
		}
		RoomTry.stop(true, null);
		reset();
		chat(t("off"));
	}

	/** {@code room list}. */
	public static void list() {
		if (!PhantasmonApi.isAdmin()) {
			chat(t("refuse.not_admin"));
			return;
		}
		PhantasmonApi.get("/events/trahison/rooms", RoomDto.Summary[].class).whenComplete((rooms, failure) -> Minecraft.getInstance().execute(() -> {
			if (failure != null || rooms == null) {
				chat(failureMessage(failure));
				return;
			}
			if (rooms.length == 0) {
				chat(t("list.empty"));
				return;
			}
			chat(t("list.title", rooms.length));
			for (RoomDto.Summary r : rooms) {
				chat(t("list.line", r.id, r.name == null ? "" : r.name, r.version, r.difficulty, r.stepCount, r.slotCount,
						t(r.hasSchematic ? "list.blocks_yes" : "list.blocks_no")));
			}
		}));
	}

	/**
	 * {@code room load <id>}: pastes the saved blocks where the player stands (clearing the template's box first) and brings back
	 * the slots and the chain.
	 */
	public static void load(String id) {
		Component refused = refusal();
		if (refused != null) {
			chat(refused);
			return;
		}
		if (!RoomChecks.validId(id)) {
			chat(t("refuse.bad_id", id));
			return;
		}
		if (active && dirty) {
			chat(t("refuse.unsaved"));
			return;
		}
		if (busy) {
			chat(t("refuse.busy"));
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		RoomTemplate.Frame f = frameHere();
		if (!heightFits(f)) {
			chat(t("refuse.height"));
			return;
		}
		busy = true;
		chat(t("loading", id));
		PhantasmonApi.get("/events/trahison/rooms/" + id, RoomDto.class).whenComplete((definition, failure) -> mc.execute(() -> {
			if (failure != null || definition == null) {
				busy = false;
				chat(failureMessage(failure));
				return;
			}
			RoomFiles.download(id).whenComplete((bytes, missing) -> mc.execute(() -> {
				if (missing != null || bytes == null) {
					boolean absent = cause(missing) instanceof BackendApiException api && api.status() == 404;
					if (!absent) {
						busy = false;
						chat(failureMessage(missing));
						return;
					}
					busy = false;
					begin(f, definition);
					chat(t("loaded_no_blocks", id));
					return;
				}
				paste(f, definition, bytes);
			}));
		}));
	}

	private static void paste(RoomTemplate.Frame f, RoomDto definition, byte[] bytes) {
		Minecraft mc = Minecraft.getInstance();
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null || mc.level == null) {
			busy = false;
			chat(t("refuse.not_singleplayer"));
			return;
		}
		var dimension = mc.level.dimension();
		server.execute(() -> {
			int placed = -1;
			Throwable error = null;
			try {
				ServerLevel level = server.getLevel(dimension);
				placed = RoomSchematic.paste(level, f, bytes);
			} catch (IOException | RuntimeException ex) {
				error = ex;
			}
			int count = placed;
			Throwable failure = error;
			mc.execute(() -> {
				busy = false;
				if (failure != null) {
					chat(t("paste_failed", String.valueOf(failure.getMessage())));
					return;
				}
				begin(f, definition);
				setStatus(t("status.loaded", definition.version), false);
				chat(t("loaded", definition.id, definition.version, count));
			});
		});
	}

	// ---------------------------------------------------------------- the selection

	private static void setCorner(boolean first) {
		BlockPos pos = Selection.pick(Minecraft.getInstance());
		if (pos == null) {
			chat(Component.translatable("phantasmon_events.trahison.edit.no_block"));
			return;
		}
		selection.set(first, pos);
		Minecraft.getInstance().player.displayClientMessage(Component.translatable(first
				? "phantasmon_events.trahison.edit.corner_a" : "phantasmon_events.trahison.edit.corner_b", pos.getX(), pos.getY(), pos.getZ()), true);
	}

	/** The selection as a canonical box (relative to the template), or null. */
	static Box selectionCanonical() {
		int[] s = selection.local();
		return s == null || frame == null ? null : frame.toCanonical(s[0], s[1], s[2], s[3], s[4], s[5]);
	}

	// ---------------------------------------------------------------- slots

	static SlotDto slot(String id) {
		return room.slots.stream().filter(s -> s.id.equals(id)).findFirst().orElse(null);
	}

	static List<String> slotIds() {
		return room.slots.stream().map(s -> s.id).toList();
	}

	static List<String> slotIdsOfKind(String kind) {
		return room.slots.stream().filter(s -> kind.equals(s.kind)).map(s -> s.id).toList();
	}

	/** A new slot of a kind, placed on the selection (or on the block the player stands in), selected. */
	static String createSlot(String kind) {
		String id = freeId(kind, slotIds());
		SlotDto slot = new SlotDto();
		slot.id = id;
		slot.kind = kind;
		slot.orientation = "north";
		slot.position = new P();
		slot.size = new P(1, 1, 1);
		room.slots.add(slot);
		Box box = selectionCanonical();
		if (box != null) {
			placeOn(slot, box);
		} else if (frame != null && Minecraft.getInstance().player != null) {
			BlockPos feet = Minecraft.getInstance().player.blockPosition();
			int[] c = frame.toCanonical(feet.getX(), feet.getY(), feet.getZ());
			slot.position = new P(c[0], c[1], c[2]);
		}
		selectedSlot = id;
		changed();
		return id;
	}

	private static void placeOn(SlotDto slot, Box box) {
		slot.position = new P(box.minX, box.minY, box.minZ);
		slot.size = new P(box.maxX - box.minX + 1, box.maxY - box.minY + 1, box.maxZ - box.minZ + 1);
	}

	/** The slot takes the selected box (position and size); a template slot counts as placed from then on. */
	static void setSlotBox(SlotDto slot) {
		Box box = selectionCanonical();
		if (box != null) {
			placeOn(slot, box);
			unplaced.remove(slot.id);
			changed();
		}
	}

	/** The slots a template proposed that are not confirmed yet, in the order of the chain. */
	static List<String> todo() {
		return RoomTemplates.order(room, unplaced);
	}

	/** "Keep the proposed position": the slot counts as placed where it is; the next one to place is selected. */
	static void keepPosition(String id) {
		if (unplaced.remove(id)) {
			selectedSlot = todo().isEmpty() ? id : todo().get(0);
			changed();
		}
	}

	/** "Place on the selection": the slot takes the selected box and counts as placed; the next one to place is selected. */
	static void placeOnSelection(String id) {
		SlotDto slot = slot(id);
		if (slot != null && selectionCanonical() != null) {
			setSlotBox(slot);
			selectedSlot = todo().isEmpty() ? id : todo().get(0);
		}
	}

	/** The slot faces the player: its orientation is the opposite of where the player looks, in the canonical frame. */
	static void faceMe(SlotDto slot) {
		Minecraft mc = Minecraft.getInstance();
		if (frame == null || mc.player == null) {
			return;
		}
		Direction toward = mc.player.getDirection().getOpposite();
		slot.orientation = frame.orientationToCanonical(toward.getName());
		changed();
	}

	static void deleteSlot(String id) {
		room.slots.removeIf(s -> s.id.equals(id));
		for (StepDto step : room.steps) {
			step.slots.values().removeIf(id::equals);
			step.gives.removeIf(g -> id.equals(g.slot));
		}
		if (id.equals(room.secret)) {
			room.secret = null;
		}
		if (id.equals(selectedSlot)) {
			selectedSlot = null;
		}
		unplaced.remove(id);
		hints.remove(id);
		changed();
	}

	/** Renames a slot and what points at it; refused (false) for a bad or used id. */
	static boolean renameSlot(String from, String to) {
		if (from.equals(to)) {
			return true;
		}
		if (!RoomChecks.validId(to) || slot(to) != null) {
			return false;
		}
		slot(from).id = to;
		for (StepDto step : room.steps) {
			step.slots.replaceAll((role, slotId) -> from.equals(slotId) ? to : slotId);
			step.gives.forEach(g -> g.slot = from.equals(g.slot) ? to : g.slot);
		}
		if (from.equals(room.secret)) {
			room.secret = to;
		}
		if (from.equals(selectedSlot)) {
			selectedSlot = to;
		}
		if (unplaced.remove(from)) {
			unplaced.add(to);
		}
		String hint = hints.remove(from);
		if (hint != null) {
			hints.put(to, hint);
		}
		changed();
		return true;
	}

	// ---------------------------------------------------------------- steps

	static StepDto step(String id) {
		return room.steps.stream().filter(s -> s.id.equals(id)).findFirst().orElse(null);
	}

	static List<String> stepIds() {
		return room.steps.stream().map(s -> s.id).toList();
	}

	/** A new last step that needs the previous last one, selected. */
	static String addStep() {
		String id = freeId("s", stepIds());
		StepDto step = new StepDto();
		step.id = id;
		step.mechanism = MechanismCatalog.ids().get(0);
		if (!room.steps.isEmpty()) {
			step.needs.add(room.steps.get(room.steps.size() - 1).id);
		}
		applyDefaults(step);
		room.steps.add(step);
		selectedStep = id;
		changed();
		return id;
	}

	static void removeStep(String id) {
		room.steps.removeIf(s -> s.id.equals(id));
		room.steps.forEach(s -> s.needs.removeIf(id::equals));
		if (id.equals(selectedStep)) {
			selectedStep = null;
		}
		changed();
	}

	static void moveStep(String id, int dir) {
		int at = stepIds().indexOf(id);
		int to = at + dir;
		if (at < 0 || to < 0 || to >= room.steps.size()) {
			return;
		}
		StepDto step = room.steps.remove(at);
		room.steps.add(to, step);
		changed();
	}

	static boolean renameStep(String from, String to) {
		if (from.equals(to)) {
			return true;
		}
		if (!RoomChecks.validId(to) || step(to) != null) {
			return false;
		}
		step(from).id = to;
		for (StepDto step : room.steps) {
			step.needs.replaceAll(need -> from.equals(need) ? to : need);
		}
		if (from.equals(selectedStep)) {
			selectedStep = to;
		}
		changed();
		return true;
	}

	/** Changes a step's mechanism: bindings and clues that no longer exist are dropped, the parameters restart at their defaults. */
	static void setMechanism(StepDto step, String mechanism) {
		step.mechanism = mechanism;
		List<String> roles = MechanismCatalog.roles(mechanism);
		step.slots.keySet().removeIf(role -> !roles.contains(role));
		List<String> clues = MechanismCatalog.clues(mechanism);
		step.gives.removeIf(g -> !clues.contains(g.clue));
		step.params = new com.google.gson.JsonObject();
		applyDefaults(step);
		changed();
	}

	private static void applyDefaults(StepDto step) {
		for (MechanismCatalog.Param param : MechanismCatalog.params(step.mechanism)) {
			setParam(step.params, param.name(), param.def());
		}
	}

	/** Writes a parameter typed from its text: a whole number, true or false, else a string. */
	static void setParam(com.google.gson.JsonObject params, String key, String text) {
		if (text.matches("-?\\d{1,9}")) {
			params.addProperty(key, Integer.parseInt(text));
		} else if (text.matches("-?\\d{1,9}\\.\\d{1,9}")) {
			params.addProperty(key, Double.parseDouble(text));
		} else if (text.equals("true") || text.equals("false")) {
			params.addProperty(key, Boolean.parseBoolean(text));
		} else {
			params.addProperty(key, text);
		}
	}

	static void bindRole(StepDto step, String role, String slotId) {
		if (slotId == null) {
			step.slots.remove(role);
		} else {
			step.slots.put(role, slotId);
		}
		changed();
	}

	static void toggleNeed(StepDto step, String other) {
		if (!step.needs.remove(other)) {
			step.needs.add(other);
		}
		changed();
	}

	static GiveDto addGive(StepDto step) {
		GiveDto give = new GiveDto();
		List<String> clues = MechanismCatalog.clues(step.mechanism);
		give.clue = clues.isEmpty() ? "" : clues.get(0);
		give.slot = room.slots.isEmpty() ? "" : room.slots.get(0).id;
		give.when = "open";
		step.gives.add(give);
		changed();
		return give;
	}

	private static String freeId(String prefix, List<String> used) {
		for (int n = 1;; n++) {
			String id = prefix + "_" + n;
			if (prefix.equals("s")) {
				id = "s" + n;
			}
			if (!used.contains(id)) {
				return id;
			}
		}
	}

	// ---------------------------------------------------------------- saving

	/** Captures the blocks, runs the local checks, then sends the definition and the blocks to the backend. */
	static void save() {
		if (!active || busy) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Component refused = refusal();
		if (refused != null) {
			setStatus(refused, true);
			return;
		}
		List<RoomChecks.Issue> early = new ArrayList<>(live());
		if (!unplaced.isEmpty()) {
			// A clear local refusal before anything else: the template's slots are only proposals until confirmed.
			saveIssues = early;
			setStatus(t("status.unplaced", unplaced.size()), true);
			return;
		}
		if (!RoomChecks.passes(early)) {
			// Do not even read the world: the definition is not fit to send.
			saveIssues = early;
			setStatus(t("status.blocked", early.stream().filter(i -> !i.warning()).count()), true);
			return;
		}
		MinecraftServer server = mc.getSingleplayerServer();
		var dimension = mc.level.dimension();
		RoomTemplate.Frame f = frame;
		String name = room.name;
		String author = room.author;
		busy = true;
		setStatus(t("status.capturing"), false);
		server.execute(() -> {
			RoomSchematic.Capture capture = null;
			Throwable error = null;
			try {
				capture = RoomSchematic.capture(server.getLevel(dimension), f, name, author);
			} catch (IOException | RuntimeException ex) {
				error = ex;
			}
			RoomSchematic.Capture done = capture;
			Throwable failure = error;
			mc.execute(() -> afterCapture(done, failure));
		});
	}

	private static void afterCapture(RoomSchematic.Capture capture, Throwable failure) {
		if (failure != null || capture == null) {
			busy = false;
			setStatus(t("status.capture_failed", failure == null ? "" : String.valueOf(failure.getMessage())), true);
			return;
		}
		List<RoomChecks.Issue> found = new ArrayList<>(live());
		if (capture.blocks() == 0) {
			found.add(new RoomChecks.Issue("LOCAL_NO_BLOCKS", List.of(), ""));
		}
		if (capture.entryBlocked()) {
			found.add(new RoomChecks.Issue("LOCAL_ENTRY_BLOCKED", List.of(), ""));
		}
		if (capture.exitBlocked()) {
			found.add(new RoomChecks.Issue("LOCAL_EXIT_BLOCKED", List.of(), ""));
		}
		if (!RoomChecks.passes(found)) {
			busy = false;
			saveIssues = found;
			setStatus(t("status.blocked", found.stream().filter(i -> !i.warning()).count()), true);
			return;
		}
		saveIssues = new ArrayList<>(found);
		setStatus(t("status.uploading"), false);
		String id = room.id;
		RoomDto body = room;
		PhantasmonApi.post("/events/trahison/rooms/" + id, body, RoomDto.class).whenComplete((saved, definitionFailure) -> Minecraft.getInstance().execute(() -> {
			if (definitionFailure != null || saved == null) {
				busy = false;
				saveIssues = issuesOf(definitionFailure);
				setStatus(failureMessage(definitionFailure), true);
				RoomScreen.onSaveFailed(!saveIssues.isEmpty());
				return;
			}
			room.version = saved.version;
			room.difficulty = saved.difficulty;
			room.expectedSeconds = saved.expectedSeconds;
			RoomFiles.upload(id, capture.file())
					.whenComplete((ok, fileFailure) -> Minecraft.getInstance().execute(() -> {
						busy = false;
						if (fileFailure != null) {
							// The definition is saved, the blocks are not: stay dirty so that the next save sends both again.
							setStatus(t("status.blocks_failed", saved.version, failureMessage(fileFailure)), true);
							return;
						}
						dirty = false;
						saveIssues = new ArrayList<>(found.stream().filter(RoomChecks.Issue::warning).toList());
						setStatus(t("status.saved", saved.version, capture.blocks(), capture.blockEntities()), false);
					}));
		}));
	}

	// ---------------------------------------------------------------- backend answers

	private static Throwable cause(Throwable failure) {
		return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
	}

	private static Component failureMessage(Throwable failure) {
		Throwable cause = cause(failure);
		if (cause instanceof BackendApiException api) {
			String code = api.errorCode();
			return Component.translatableWithFallback(T + "error." + code.toLowerCase(Locale.ROOT), code);
		}
		if (cause instanceof IllegalStateException) {
			return t("error.transport");
		}
		return t("error.network");
	}

	/** The backend's {@code details.errors} of a refused room. */
	private static List<RoomChecks.Issue> issuesOf(Throwable failure) {
		List<RoomChecks.Issue> found = new ArrayList<>();
		if (!(cause(failure) instanceof BackendApiException api) || api.details() == null || !(api.details().get("errors") instanceof List<?> errors)) {
			return found;
		}
		for (Object error : errors) {
			if (error instanceof Map<?, ?> map) {
				List<String> ids = new ArrayList<>();
				if (map.get("ids") instanceof List<?> list) {
					list.forEach(id -> ids.add(String.valueOf(id)));
				}
				found.add(new RoomChecks.Issue(String.valueOf(map.get("code")), ids, map.get("info") == null ? "" : String.valueOf(map.get("info"))));
			}
		}
		return found;
	}
}
