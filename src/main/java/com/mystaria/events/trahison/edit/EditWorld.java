package com.mystaria.events.trahison.edit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.mystaria.events.trahison.edit.EditKinds.Kind;
import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.LayoutDto.Cabin;
import com.mystaria.events.trahison.edit.LayoutDto.Door;
import com.mystaria.events.trahison.edit.LayoutDto.Pos;
import com.mystaria.events.trahison.edit.LayoutDto.Slot;
import com.mystaria.events.trahison.edit.LayoutDto.Spot;
import com.mystaria.events.trahison.edit.LayoutDto.Zone;
import com.mystaria.events.trahison.ui.TrahisonPalette;

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
		List<Cmd> cmds = new ArrayList<>();

		Set<String> lit = highlighted();
		LayoutDto layout = TrahisonEditor.layout;
		for (Zone zone : layout.zones) {
			boolean selected = lit.contains("zone:" + zone.id);
			int color = EditKinds.zoneColor(zone.type);
			for (Box box : zone.boxes) {
				queue(cmds, grid.toLocal(box), color, selected, selected ? 0.22f : 0.08f);
			}
		}
		for (Door door : layout.doors) {
			if (door.block != null) {
				boolean selected = lit.contains("door:" + door.id);
				queue(cmds, grid.toLocal(door.block), EditKinds.doorColor(door.type), selected, selected ? 0.5f : 0.3f);
			}
		}
		for (Slot slot : layout.roomSlots) {
			if (slot.origin != null) {
				Box place = new Box(slot.origin.x, slot.origin.y, slot.origin.z, slot.origin.x + EditKinds.SLOT_SIDE - 1,
						slot.origin.y + EditKinds.SLOT_HEIGHT - 1, slot.origin.z + EditKinds.SLOT_SIDE - 1);
				boolean selected = lit.contains("slot:" + slot.id);
				queue(cmds, grid.toLocal(place), EditKinds.SLOT_COLOR, selected, selected ? 0.1f : 0f);
			}
		}
		for (Spot spot : layout.hidingSpots) {
			if (spot.position != null) {
				Pos p = spot.position;
				boolean selected = lit.contains("spot:" + spot.id);
				queue(cmds, grid.toLocal(new Box(p.x, p.y, p.z, p.x, p.y, p.z)), EditKinds.SPOT_COLOR, selected, 0.5f);
			}
		}
		drawSelection(cmds);
		flush(pose, buffers, cmds);

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
		if (kind == Kind.CABIN) {
			Cabin cabin = TrahisonEditor.cabin(id);
			if (cabin != null) {
				lit.add("zone:" + cabin.zone);
				lit.add("door:" + cabin.door);
			}
		}
		return lit;
	}

	private static void drawSelection(List<Cmd> cmds) {
		int[] s = TrahisonEditor.selectionLocal();
		if (s == null) {
			return;
		}
		queue(cmds, new Box(s[0], s[1], s[2], s[3], s[4], s[5]), EditKinds.SELECTION_COLOR, true, 0.15f);
		if (TrahisonEditor.cornerA != null) {
			var a = TrahisonEditor.cornerA;
			queue(cmds, new Box(a.getX(), a.getY(), a.getZ(), a.getX(), a.getY(), a.getZ()), TrahisonPalette.ACCENT, true, 0.5f);
		}
		if (TrahisonEditor.cornerB != null) {
			var b = TrahisonEditor.cornerB;
			queue(cmds, new Box(b.getX(), b.getY(), b.getZ(), b.getX(), b.getY(), b.getZ()), TrahisonPalette.ALLY, true, 0.5f);
		}
	}

	/** One box to draw: of local blocks, an outline (twice, for a strong one) and a translucent fill. */
	private record Cmd(Box box, int color, boolean strong, float fill) {
	}

	private static void queue(List<Cmd> cmds, Box box, int color, boolean strong, float fill) {
		cmds.add(new Cmd(box, color, strong, fill));
	}

	/**
	 * Draws the queued boxes: all the fills, then all the outlines. A buffer must never be kept while another render type is
	 * asked for (the buffer source ends the previous one: solution S14), so each pass takes its buffer once.
	 */
	private static void flush(PoseStack pose, MultiBufferSource.BufferSource buffers, List<Cmd> cmds) {
		for (Cmd cmd : cmds) {
			if (cmd.fill() > 0f) {
				DebugRenderer.renderFilledBox(pose, buffers, aabb(cmd.box()), red(cmd.color()), green(cmd.color()), blue(cmd.color()), cmd.fill());
			}
		}
		buffers.endBatch(RenderType.debugFilledBox());
		VertexConsumer lines = buffers.getBuffer(RenderType.lines());
		for (Cmd cmd : cmds) {
			AABB aabb = aabb(cmd.box());
			LevelRenderer.renderLineBox(pose, lines, aabb, red(cmd.color()), green(cmd.color()), blue(cmd.color()), cmd.strong() ? 1f : 0.8f);
			if (cmd.strong()) {
				LevelRenderer.renderLineBox(pose, lines, aabb.inflate(0.02), red(cmd.color()), green(cmd.color()), blue(cmd.color()), 1f);
			}
		}
		buffers.endBatch(RenderType.lines());
	}

	/** A small grow so the outline and the fill do not fight with the blocks' own faces. */
	private static AABB aabb(Box box) {
		return new AABB(box.minX, box.minY, box.minZ, box.maxX + 1, box.maxY + 1, box.maxZ + 1).inflate(0.003);
	}

	private static float red(int color) {
		return (color >> 16 & 0xFF) / 255f;
	}

	private static float green(int color) {
		return (color >> 8 & 0xFF) / 255f;
	}

	private static float blue(int color) {
		return (color & 0xFF) / 255f;
	}

	private static void labels(WorldRenderContext context, MultiBufferSource.BufferSource buffers, HubGrid grid, Vec3 camera, Set<String> lit) {
		for (Zone zone : TrahisonEditor.layout.zones) {
			if (!zone.boxes.isEmpty()) {
				label(context, buffers, camera, grid.toLocal(zone.boxes.get(0)), zone.id, EditKinds.zoneColor(zone.type), lit.contains("zone:" + zone.id));
			}
		}
		for (Door door : TrahisonEditor.layout.doors) {
			if (door.block != null) {
				label(context, buffers, camera, grid.toLocal(door.block), door.id, EditKinds.doorColor(door.type), lit.contains("door:" + door.id));
			}
		}
		for (Slot slot : TrahisonEditor.layout.roomSlots) {
			if (slot.origin != null) {
				Box place = new Box(slot.origin.x, slot.origin.y, slot.origin.z, slot.origin.x + EditKinds.SLOT_SIDE - 1,
						slot.origin.y + EditKinds.SLOT_HEIGHT - 1, slot.origin.z + EditKinds.SLOT_SIDE - 1);
				String text = slot.id + (slot.orientation == null ? "" : " [" + slot.orientation + "]");
				label(context, buffers, camera, grid.toLocal(place), text, EditKinds.SLOT_COLOR, lit.contains("slot:" + slot.id));
			}
		}
		for (Spot spot : TrahisonEditor.layout.hidingSpots) {
			if (spot.position != null) {
				Pos p = spot.position;
				label(context, buffers, camera, grid.toLocal(new Box(p.x, p.y, p.z, p.x, p.y, p.z)), spot.id, EditKinds.SPOT_COLOR, lit.contains("spot:" + spot.id));
			}
		}
	}

	/** An id floating above a box, turned to the camera, readable through walls. */
	private static void label(WorldRenderContext context, MultiBufferSource.BufferSource buffers, Vec3 camera, Box box, String text,
			int color, boolean selected) {
		Vec3 at = new Vec3((box.minX + box.maxX + 1) / 2.0, box.maxY + 1.4, (box.minZ + box.maxZ + 1) / 2.0);
		if (at.distanceTo(camera) > LABEL_DISTANCE) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		PoseStack pose = new PoseStack();
		pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
		pose.mulPose(context.camera().rotation());
		float scale = selected ? 0.045f : 0.035f;
		pose.scale(scale, -scale, scale);
		Matrix4f matrix = pose.last().pose();
		font.drawInBatch(text, -font.width(text) / 2f, 0, selected ? EditKinds.SELECTED_COLOR : color, false, matrix, buffers,
				Font.DisplayMode.SEE_THROUGH, 0x60000000, 0xF000F0);
	}
}
