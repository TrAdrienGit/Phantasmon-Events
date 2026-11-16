package com.mystaria.events.trahison.edit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import com.mystaria.events.trahison.edit.EditKinds.Kind;
import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.LayoutDto.Cabin;
import com.mystaria.events.trahison.edit.LayoutDto.Door;
import com.mystaria.events.trahison.edit.LayoutDto.Pos;
import com.mystaria.events.trahison.edit.LayoutDto.ResultsBoard;
import com.mystaria.events.trahison.edit.LayoutDto.Slot;
import com.mystaria.events.trahison.edit.LayoutDto.Spot;
import com.mystaria.events.trahison.edit.LayoutDto.Zone;
import com.mystaria.events.trahison.world.BoardGeometry;

/**
 * The editor's view of the plan in the world (solution S14, {@code WorldRenderEvents.AFTER_TRANSLUCENT}): every
 * element's boxes coloured by type, its id floating over it, the selected element highlighted and the current selection
 * (corner A, corner B and the box between). Positions are stored in hub coordinates and converted back to this world's
 * blocks each frame. Colours are palette tokens ({@link EditKinds}).
 */
final class EditWorld {

	private static final double LABEL_DISTANCE = 96.0;

	private EditWorld() {
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		HubGrid grid = TrahisonEditor.grid();
		if (!TrahisonEditor.isActive() || mc.player == null || grid == null) {
			return;
		}
		Vec3 camera = context.camera().getPosition();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		PoseStack pose = new PoseStack();
		pose.translate(-camera.x, -camera.y, -camera.z);
		List<WorldDraw.Cmd> cmds = new ArrayList<>();

		Set<String> lit = highlighted();
		LayoutDto layout = TrahisonEditor.layout;
		for (Zone zone : layout.zones) {
			boolean selected = lit.contains("zone:" + zone.id);
			int color = EditKinds.zoneColor(zone.type);
			for (Box box : zone.boxes) {
				WorldDraw.queue(cmds, grid.toLocal(box), color, selected, selected ? 0.22f : 0.08f);
			}
		}
		for (Door door : layout.doors) {
			if (door.block != null) {
				boolean selected = lit.contains("door:" + door.id);
				WorldDraw.queue(cmds, grid.toLocal(door.block), EditKinds.doorColor(door.type), selected, selected ? 0.5f : 0.3f);
			}
		}
		for (Slot slot : layout.roomSlots) {
			if (slot.origin != null) {
				Box place = new Box(slot.origin.x, slot.origin.y, slot.origin.z, slot.origin.x + EditKinds.SLOT_SIDE - 1,
						slot.origin.y + EditKinds.SLOT_HEIGHT - 1, slot.origin.z + EditKinds.SLOT_SIDE - 1);
				boolean selected = lit.contains("slot:" + slot.id);
				WorldDraw.queue(cmds, grid.toLocal(place), EditKinds.SLOT_COLOR, selected, selected ? 0.1f : 0f);
			}
		}
		for (Spot spot : layout.hidingSpots) {
			if (spot.position != null) {
				Pos p = spot.position;
				boolean selected = lit.contains("spot:" + spot.id);
				WorldDraw.queue(cmds, grid.toLocal(new Box(p.x, p.y, p.z, p.x, p.y, p.z)), EditKinds.SPOT_COLOR, selected, 0.5f);
			}
		}
		Box board = BoardGeometry.blocks(layout.resultsBoard);
		if (board != null) {
			boolean selected = lit.contains("board:" + EditKinds.BOARD_ID);
			WorldDraw.queue(cmds, grid.toLocal(board), EditKinds.BOARD_COLOR, selected, selected ? 0.45f : 0.25f);
		}
		drawSelection(cmds);
		WorldDraw.flush(pose, buffers, cmds);

		labels(context, buffers, grid, camera, lit);
		buffers.endBatch();
	}

	/** Keys ({@code kind:id}) of what the selected element is, or points at. */
	private static Set<String> highlighted() {
		Set<String> lit = new HashSet<>();
		String id = TrahisonEditor.selectedId;
		if (id == null) {
			return lit;
		}
		Kind kind = TrahisonEditor.selectedKind;
		lit.add(kind.key + ":" + id);
		if (kind == Kind.DOOR) {
			Door door = TrahisonEditor.door(id);
			if (door != null && door.threshold != null) {
				lit.add("zone:" + door.threshold);
			}
		}
		if (kind == Kind.CABIN) {
			Cabin cabin = TrahisonEditor.cabin(id);
			if (cabin != null) {
				lit.add("zone:" + cabin.zone);
				lit.add("door:" + cabin.door);
			}
		}
		return lit;
	}

	private static void drawSelection(List<WorldDraw.Cmd> cmds) {
		WorldDraw.selection(cmds, TrahisonEditor.selection);
	}

	private static void labels(WorldRenderContext context, MultiBufferSource.BufferSource buffers, HubGrid grid, Vec3 camera, Set<String> lit) {
		for (Zone zone : TrahisonEditor.layout.zones) {
			if (!zone.boxes.isEmpty()) {
				WorldDraw.label(context, buffers, camera, grid.toLocal(zone.boxes.get(0)), zone.id, EditKinds.zoneColor(zone.type), lit.contains("zone:" + zone.id));
			}
		}
		for (Door door : TrahisonEditor.layout.doors) {
			if (door.block != null) {
				WorldDraw.label(context, buffers, camera, grid.toLocal(door.block), door.id, EditKinds.doorColor(door.type), lit.contains("door:" + door.id));
			}
		}
		for (Slot slot : TrahisonEditor.layout.roomSlots) {
			if (slot.origin != null) {
				Box place = new Box(slot.origin.x, slot.origin.y, slot.origin.z, slot.origin.x + EditKinds.SLOT_SIDE - 1,
						slot.origin.y + EditKinds.SLOT_HEIGHT - 1, slot.origin.z + EditKinds.SLOT_SIDE - 1);
				String text = slot.id + (slot.orientation == null ? "" : " [" + slot.orientation + "]");
				WorldDraw.label(context, buffers, camera, grid.toLocal(place), text, EditKinds.SLOT_COLOR, lit.contains("slot:" + slot.id));
			}
		}
		ResultsBoard results = TrahisonEditor.layout.resultsBoard;
		Box boardBox = BoardGeometry.blocks(results);
		if (boardBox != null) {
			WorldDraw.label(context, buffers, camera, grid.toLocal(boardBox), EditKinds.BOARD_ID + " [" + results.facing + "]", EditKinds.BOARD_COLOR,
					lit.contains("board:" + EditKinds.BOARD_ID));
		}
		for (Spot spot : TrahisonEditor.layout.hidingSpots) {
			if (spot.position != null) {
				Pos p = spot.position;
				WorldDraw.label(context, buffers, camera, grid.toLocal(new Box(p.x, p.y, p.z, p.x, p.y, p.z)), spot.id, EditKinds.SPOT_COLOR, lit.contains("spot:" + spot.id));
			}
		}
	}
}
