package com.mystaria.events.trahison.edit;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.RoomDto.SlotDto;
import com.mystaria.events.trahison.ui.TrahisonPalette;

/**
 * The room editor's view in the world (solution S14, {@code WorldRenderEvents.AFTER_TRANSLUCENT}): the 48 x 48 x 24 template
 * box, the imposed entry and exit, every slot coloured by kind with its id floating over it, the selected slot highlighted,
 * and the current selection. Slots are stored relative to the template's canonical frame and turned into this world's blocks
 * each frame by {@link RoomTemplate.Frame}. Colours are palette tokens.
 */
final class RoomWorld {

	private static final int TEMPLATE_COLOR = TrahisonPalette.ACCENT_DIM;
	private static final int ENTRY_COLOR = TrahisonPalette.GREEN_LIGHT;
	private static final int EXIT_COLOR = TrahisonPalette.GAME_MASTER;

	private RoomWorld() {
	}

	/** The colour a slot kind is drawn in (also the chip colour in the panel). */
	static int kindColor(String kind) {
		return switch (kind == null ? "" : kind) {
			case "terminal" -> TrahisonPalette.BLUE_LIGHT;
			case "keypad" -> TrahisonPalette.BLUE;
			case "pickup" -> TrahisonPalette.ALLY;
			case "wall_clue" -> TrahisonPalette.ACCENT;
			case "document" -> TrahisonPalette.TEXT_SECONDARY;
			case "lever" -> TrahisonPalette.RED_LIGHT;
			case "plate" -> TrahisonPalette.GREEN;
			case "pedestal" -> TrahisonPalette.BETRAY;
			case "inner_door" -> TrahisonPalette.GAME_MASTER;
			case "exit" -> TrahisonPalette.SUCCESS;
			case "secret" -> TrahisonPalette.GHOST;
			case "power_spot" -> TrahisonPalette.YELLOW;
			default -> TrahisonPalette.TEXT_MUTED;
		};
	}

	static Box slotBox(RoomDto.SlotDto slot) {
		return new Box(slot.position.x, slot.position.y, slot.position.z, slot.position.x + slot.size.x - 1,
				slot.position.y + slot.size.y - 1, slot.position.z + slot.size.z - 1);
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		RoomTemplate.Frame frame = RoomEditor.frame;
		if (!RoomEditor.isActive() || mc.player == null || frame == null) {
			return;
		}
		Vec3 camera = context.camera().getPosition();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		PoseStack pose = new PoseStack();
		pose.translate(-camera.x, -camera.y, -camera.z);
		List<WorldDraw.Cmd> cmds = new ArrayList<>();

		WorldDraw.queue(cmds, frame.toWorld(RoomTemplate.BOX), TEMPLATE_COLOR, false, 0f);
		Box entry = frame.toWorld(RoomTemplate.ENTRY);
		Box exit = frame.toWorld(RoomTemplate.EXIT);
		WorldDraw.queue(cmds, entry, ENTRY_COLOR, true, 0.25f);
		WorldDraw.queue(cmds, exit, EXIT_COLOR, true, 0.25f);
		for (SlotDto slot : RoomEditor.room.slots) {
			boolean selected = slot.id.equals(RoomEditor.selectedSlot);
			WorldDraw.queue(cmds, frame.toWorld(slotBox(slot)), kindColor(slot.kind), selected, selected ? 0.45f : 0.25f);
		}
		WorldDraw.selection(cmds, RoomEditor.selection);
		WorldDraw.flush(pose, buffers, cmds);

		WorldDraw.label(context, buffers, camera, entry, Component.translatable("phantasmon_events.trahison.room_edit.world.entry").getString(), ENTRY_COLOR, false);
		WorldDraw.label(context, buffers, camera, exit, Component.translatable("phantasmon_events.trahison.room_edit.world.exit").getString(), EXIT_COLOR, false);
		for (SlotDto slot : RoomEditor.room.slots) {
			// The orientation shown is the room's own (canonical): the entry is on its south side, whichever way the builder faced.
			String text = slot.id + " [" + slot.orientation + "]";
			WorldDraw.label(context, buffers, camera, frame.toWorld(slotBox(slot)), text, kindColor(slot.kind), slot.id.equals(RoomEditor.selectedSlot));
		}
		buffers.endBatch();
	}
}
