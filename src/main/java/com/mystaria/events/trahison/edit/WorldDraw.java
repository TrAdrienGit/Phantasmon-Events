package com.mystaria.events.trahison.edit;

import java.util.List;

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

import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.ui.TrahisonPalette;

/**
 * The drawing parts the editors share (solution S14): boxes of this world's blocks as a translucent fill and an outline,
 * the current {@link Selection}, and ids floating over a box. Used by the plan editor ({@link EditWorld}) and the room
 * editor ({@link RoomWorld}). Colours are palette tokens passed by the callers.
 */
final class WorldDraw {

	static final double LABEL_DISTANCE = 96.0;

	private WorldDraw() {
	}

	/** One box to draw: of local blocks, an outline (twice, for a strong one) and a translucent fill. */
	record Cmd(Box box, int color, boolean strong, float fill) {
	}

	static void queue(List<Cmd> cmds, Box box, int color, boolean strong, float fill) {
		cmds.add(new Cmd(box, color, strong, fill));
	}

	/** The selection: the box between the corners, and each corner. */
	static void selection(List<Cmd> cmds, Selection selection) {
		int[] s = selection.local();
		if (s == null) {
			return;
		}
		queue(cmds, new Box(s[0], s[1], s[2], s[3], s[4], s[5]), EditKinds.SELECTION_COLOR, true, 0.15f);
		if (selection.a != null) {
			var a = selection.a;
			queue(cmds, new Box(a.getX(), a.getY(), a.getZ(), a.getX(), a.getY(), a.getZ()), TrahisonPalette.ACCENT, true, 0.5f);
		}
		if (selection.b != null) {
			var b = selection.b;
			queue(cmds, new Box(b.getX(), b.getY(), b.getZ(), b.getX(), b.getY(), b.getZ()), TrahisonPalette.ALLY, true, 0.5f);
		}
	}

	/**
	 * Draws the queued boxes: all the fills, then all the outlines. A buffer must never be kept while another render type is
	 * asked for (the buffer source ends the previous one: solution S14), so each pass takes its buffer once.
	 */
	static void flush(PoseStack pose, MultiBufferSource.BufferSource buffers, List<Cmd> cmds) {
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
	static AABB aabb(Box box) {
		return new AABB(box.minX, box.minY, box.minZ, box.maxX + 1, box.maxY + 1, box.maxZ + 1).inflate(0.003);
	}

	static float red(int color) {
		return (color >> 16 & 0xFF) / 255f;
	}

	static float green(int color) {
		return (color >> 8 & 0xFF) / 255f;
	}

	static float blue(int color) {
		return (color & 0xFF) / 255f;
	}

	/** An id floating above a box, turned to the camera, readable through walls. */
	static void label(WorldRenderContext context, MultiBufferSource.BufferSource buffers, Vec3 camera, Box box, String text,
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
