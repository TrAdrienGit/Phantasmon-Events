package com.mystaria.events.trahison.edit;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.events.trahison.world.HubBlocks;
import com.mystaria.events.trahison.world.RoomFrame;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubSchematic;

/**
 * T4.3: before a game starts, the game master's client compares a sample of the world's blocks at each place with what the
 * drawn room should look like there (the room's blocks turned by the place's orientation, the same turn the export writes), and
 * lists the places that were not pasted, or pasted wrongly. A place is {@link Status#OK} when every sampled block matches,
 * {@link Status#MISSING} when almost none does (nothing pasted, or pasted elsewhere), {@link Status#WRONG} between the two, and
 * {@link Status#UNLOADED} when its chunks are not loaded (go there first). Read only: nothing in the world is changed. The
 * positions are the plan's hub coordinates through {@link HubBlocks}, the turn is {@link RoomTurn}, the same rule the backend's
 * {@code RoomPlace.toHub} applies to one block.
 */
public final class PlaceCheck {

	private static final String T = "phantasmon_events.trahison.check.";
	/** Blocks compared per place. */
	static final int SAMPLE = 600;

	public enum Status {
		OK, WRONG, MISSING, UNLOADED
	}

	private static boolean busy;

	private PlaceCheck() {
	}

	/** Which blocks of a turned room to look at: up to {@code max}, evenly spread among the non-air blocks. Pure. */
	static List<HubSchematic.Block> sample(List<HubSchematic.Block> blocks, int max) {
		// D78: blocks with data (signs, heads) come first, up to half the sample, then the rest evenly spread
		List<HubSchematic.Block> withData = new ArrayList<>();
		List<HubSchematic.Block> solid = new ArrayList<>();
		for (HubSchematic.Block b : blocks) {
			if (b.name().endsWith("air")) {
				continue;
			}
			(b.blockEntity() != null ? withData : solid).add(b);
		}
		List<HubSchematic.Block> out = new ArrayList<>(Math.min(max, withData.size() + solid.size()));
		int dataKept = Math.min(withData.size(), max / 2);
		for (int i = 0; i < dataKept; i++) {
			out.add(withData.get((int) ((long) i * withData.size() / dataKept)));
		}
		int rest = Math.min(solid.size(), max - dataKept);
		for (int i = 0; i < rest; i++) {
			out.add(solid.get((int) ((long) i * solid.size() / rest)));
		}
		return out;
	}

	/** What a count of matches says about a place. Pure. */
	static Status classify(int checked, int matched, int unloaded) {
		if (checked == 0) {
			return Status.OK;
		}
		if (unloaded * 2 > checked) {
			return Status.UNLOADED;
		}
		int considered = checked - unloaded;
		if (matched == considered) {
			return Status.OK;
		}
		return matched * 20 < considered ? Status.MISSING : Status.WRONG;
	}

	/** The command and the GM screen's button: checks the nine places of the draw. */
	public static void run() {
		Minecraft mc = Minecraft.getInstance();
		if (!PhantasmonApi.isAdmin() && !GameView.orEmpty().viewer().isGameMaster()) {
			chat(Component.translatable(T + "not_allowed"));
			return;
		}
		var anchor = PhantasmonApi.hubs() == null ? null : PhantasmonApi.hubs().joinedAnchor();
		String hub = Link.hub();
		GameView.Gm gm = GameView.orEmpty().gm();
		if (hub == null || anchor == null || !hub.equals(anchor.hub()) || mc.level == null) {
			chat(Component.translatable(T + "not_in_hub"));
			return;
		}
		if (gm == null || gm.draw() == null || gm.draw().picks().stream().anyMatch(p -> p.place() == null)) {
			chat(Component.translatable(T + "no_draw"));
			return;
		}
		if (busy) {
			return;
		}
		busy = true;
		HubBlocks blocks = HubBlocks.of(anchor);
		Map<String, String> byPlace = new java.util.LinkedHashMap<>();
		gm.draw().picks().forEach(p -> byPlace.put(p.place(), p.room()));
		chat(Component.translatable(T + "working"));
		PhantasmonApi.get("/events/trahison/layout/" + URLEncoder.encode(hub, StandardCharsets.UTF_8), LayoutDto.class)
				.thenCompose(layout -> {
					List<TrahisonExport.Pairing> pairs = TrahisonExport.pair(layout.normalized(), byPlace);
					List<CompletableFuture<byte[]>> files = new ArrayList<>();
					pairs.forEach(p -> files.add(RoomFiles.download(p.roomId())));
					return CompletableFuture.allOf(files.toArray(new CompletableFuture[0])).thenApply(v -> {
						List<Object[]> out = new ArrayList<>();
						for (int i = 0; i < pairs.size(); i++) {
							out.add(new Object[] { pairs.get(i), files.get(i).join() });
						}
						return out;
					});
				})
				.whenComplete((jobs, failure) -> Minecraft.getInstance().execute(() -> {
					busy = false;
					if (failure != null || jobs == null) {
						Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
						chat(Component.translatable(T + "error", String.valueOf(cause == null ? "?" : cause.getMessage())));
						return;
					}
					report(mc.level, blocks, jobs);
				}));
	}

	private static void report(ClientLevel level, HubBlocks blocks, List<Object[]> jobs) {
		int bad = 0;
		for (Object[] job : jobs) {
			TrahisonExport.Pairing pair = (TrahisonExport.Pairing) job[0];
			LayoutDto.Slot place = pair.place();
			Status status;
			try {
				status = check(level, blocks, place, HubSchematic.read((byte[]) job[1], false));
			} catch (IOException | RuntimeException e) {
				chat(Component.translatable(T + "unreadable", place.id, pair.roomId()));
				bad++;
				continue;
			}
			bad += status == Status.OK ? 0 : 1;
			chat(Component.translatable(T + "place." + status.name().toLowerCase(java.util.Locale.ROOT), place.id, pair.roomId()));
		}
		chat(Component.translatable(T + (bad == 0 ? "all_ok" : "some_bad"), bad));
	}

	private static Status check(ClientLevel level, HubBlocks blocks, LayoutDto.Slot place, HubSchematic canonical) {
		int k = RoomFrame.quarterTurns(place.orientation);
		HubSchematic turned = RoomTurn.turn(canonical, k, RoomTurn.minecraftStates(k));
		int checked = 0;
		int matched = 0;
		int unloaded = 0;
		for (HubSchematic.Block b : sample(turned.blocks(), SAMPLE)) {
			ResourceLocation id = ResourceLocation.tryParse(b.state().getString("Name"));
			if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
				continue;
			}
			BlockPos at = new BlockPos(blocks.worldX(place.origin.x + b.x()), place.origin.y + b.y(), blocks.worldZ(place.origin.z + b.z()));
			checked++;
			if (!level.hasChunkAt(at)) {
				unloaded++;
				continue;
			}
			BlockState expected = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), b.state());
			if (level.getBlockState(at) == expected && dataMatches(level, at, b.blockEntity())) {
				matched++;
			}
		}
		return classify(checked, matched, unloaded);
	}

	/**
	 * D78: the block's data (the text of a sign, a head's owner...) is what the file says: every tag of the file's block entity
	 * (but its position and id) is found, equal, in the world's. A block without data always matches.
	 */
	private static boolean dataMatches(ClientLevel level, BlockPos at, net.minecraft.nbt.CompoundTag expected) {
		if (expected == null) {
			return true;
		}
		net.minecraft.world.level.block.entity.BlockEntity entity = level.getBlockEntity(at);
		if (entity == null) {
			return false;
		}
		net.minecraft.nbt.CompoundTag actual = entity.saveWithoutMetadata(level.registryAccess());
		return dataEquals(expected, actual);
	}

	/** Every key of {@code expected} except {@code x y z id Pos} is in {@code actual} with an equal value. Pure. */
	static boolean dataEquals(net.minecraft.nbt.CompoundTag expected, net.minecraft.nbt.CompoundTag actual) {
		for (String key : expected.getAllKeys()) {
			if (key.equals("x") || key.equals("y") || key.equals("z") || key.equals("id") || key.equals("Pos")) {
				continue;
			}
			if (!expected.get(key).equals(actual.get(key))) {
				return false;
			}
		}
		return true;
	}

	private static void chat(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(message, false);
		}
	}
}
