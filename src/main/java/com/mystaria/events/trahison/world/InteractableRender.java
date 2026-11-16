package com.mystaria.events.trahison.world;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.mystaria.events.trahison.minigame.Glyphs;
import com.mystaria.events.trahison.room.Inventory;
import com.mystaria.events.trahison.room.RoomView;
import com.mystaria.events.trahison.room.RoomView.Clue;
import com.mystaria.events.trahison.room.RoomView.Show;
import com.mystaria.events.trahison.room.RoomView.SlotView;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.WireColor;
import com.mystaria.events.trahison.world.Interactables.Item;

/**
 * Draws the objects of {@link Interactables} (T3.3, solution S14): no external model, everything is quads and text in the
 * charter's palette. A terminal is a dark block with a glowing screen, a keypad a slab with a small display and twelve keys,
 * an item to pick up floats and turns, a clue panel is a wall panel (text, digits, symbols or colours), a lever has a
 * handle that falls when its puzzle is solved, a pedestal a column with a glowing top. The object the player aims at gets a
 * light outline and a label with the action's name.
 *
 * <p>Like {@code DoorPanels}: shapes first (one buffer, ended before anything asks for another: solution S14), then the
 * symbols of the clue panels (drawn through a {@link GuiGraphics} in the panel's plane, depth tested), then all text.
 * Everything is in camera-relative coordinates and unlit; faces are shaded by hand so a block reads as a block. Client thread.
 */
final class InteractableRender {

	private static final double RANGE = 28.0;
	/** World size of one panel pixel: 50 pixels to a block. */
	private static final float PX = 0.02f;
	private static final float LAYER = 0.35f;
	private static final int LIGHT = 0xF000F0;
	private static final String T = "phantasmon_events.trahison.";

	private InteractableRender() {
	}

	/** A face's brightness: the sun is up and a little to the north-west. */
	private static final float TOP = 1.0f;
	private static final float SIDE_Z = 0.82f;
	private static final float SIDE_X = 0.68f;
	private static final float BOTTOM = 0.5f;

	/** A panel on a surface: its front-centre point, the side it faces, in world coordinates. */
	private record Frame(double x, double y, double z, double fx, double fz) {

		/** Panel pixels, x to the viewer's right, y down; local {@code -z} is toward the viewer. */
		PoseStack pose(Vec3 camera) {
			PoseStack pose = new PoseStack();
			pose.translate(x - camera.x, y - camera.y, z - camera.z);
			pose.mulPose(Axis.YP.rotation((float) Math.atan2(-fx, -fz)));
			pose.scale(-PX, -PX, PX);
			return pose;
		}
	}

	/** What one object puts into the text pass. */
	private record TextJob(Frame frame, float x, float y, Component text, int color, float scale, boolean billboard, Vec3 at) {
	}

	/** A symbol row for the glyph pass: ids drawn at panel pixels. */
	private record GlyphJob(Frame frame, float cx, float cy, int size, List<String> ids, float gap, int color) {
	}

	private static void render(WorldRenderContext context, Minecraft mc) {
		Vec3 camera = context.camera().getPosition();
		Item aimed = Interactables.pick(mc);
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		List<TextJob> texts = new ArrayList<>();
		List<GlyphJob> glyphs = new ArrayList<>();
		VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
		long now = System.currentTimeMillis();
		for (Item item : Interactables.items().values()) {
			if (item.box().getCenter().distanceTo(camera) > RANGE) {
				continue;
			}
			draw(vc, camera, item, now, texts, glyphs);
		}
		if (aimed != null) {
			outline(vc, camera, aimed.box().inflate(0.04), TrahisonPalette.ACCENT_HOT);
			label(vc, context, camera, aimed, texts, mc.font);
		}
		buffers.endBatch(RenderType.debugQuads());
		if (!glyphs.isEmpty()) {
			drawGlyphs(mc, buffers, camera, glyphs);
		}
		for (TextJob job : texts) {
			drawText(context, buffers, mc.font, camera, job);
		}
		buffers.endBatch();
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || Interactables.items().isEmpty()) {
			return;
		}
		render(context, mc);
	}

	// ---------------------------------------------------------------- one object

	private static void draw(VertexConsumer vc, Vec3 camera, Item item, long now, List<TextJob> texts, List<GlyphJob> glyphs) {
		SlotView slot = item.slot();
		boolean puzzle = slot.openPuzzle() != null;
		boolean solved = slot.solvedPuzzle();
		switch (slot.kind()) {
			case "terminal" -> terminal(vc, camera, item, puzzle, solved, now);
			case "keypad" -> keypad(vc, camera, item, puzzle, solved, now);
			case "wall_clue" -> clues(vc, camera, item, texts, glyphs, true);
			case "lever" -> {
				Show coop = slot.shows().stream().filter(sh -> sh.isPuzzle() && "three_levers".equals(sh.mechanism())).findFirst().orElse(null);
				if (coop != null) {
					coopLever(vc, camera, item, coop, now);
				} else {
					lever(vc, camera, item, solved, now);
				}
			}
			case "plate" -> plate(vc, camera, item, puzzle, solved);
			case "pedestal" -> pedestal(vc, camera, item, puzzle, solved, now);
			case "document" -> document(vc, camera, item, now);
			case "pickup" -> {
				// the floating item below is the object
			}
			default -> {
				if (puzzle || solved) {
					terminal(vc, camera, item, puzzle, solved, now);
				} else if (item.slot().item() == null && item.slot().readable().isEmpty()) {
					floating(vc, camera, item.box().getCenter(), 0.14f, TrahisonPalette.ACCENT, now);
				}
			}
		}
		if (!"wall_clue".equals(slot.kind()) && !slot.readable().isEmpty() && !"document".equals(slot.kind())) {
			clues(vc, camera, item, texts, glyphs, false); // a clue shown elsewhere than on a wall panel: a small card in front
		}
		if (slot.item() != null) {
			AABB b = item.box();
			double top = "pedestal".equals(slot.kind()) ? b.minY + Math.min(1.2, b.getYsize()) + 0.35 : b.getCenter().y;
			floating(vc, camera, new Vec3(b.getCenter().x, top, b.getCenter().z), 0.2f, Inventory.tint(slot.item().text()), now);
		}
	}

	// ---- terminal

	private static void terminal(VertexConsumer vc, Vec3 camera, Item item, boolean puzzle, boolean solved, long now) {
		AABB b = item.box();
		double w = Math.min(1.0, Math.max(b.getXsize(), b.getZsize()));
		double h = Math.min(1.1, Math.max(0.8, b.getYsize()));
		Matrix4f m = rel(camera, b.getCenter().x, b.minY, b.getCenter().z);
		float hw = (float) (b.getXsize() >= 0.9 ? Math.min(0.46, b.getXsize() / 2 - 0.04) : b.getXsize() / 2);
		float hd = (float) (b.getZsize() >= 0.9 ? Math.min(0.40, b.getZsize() / 2 - 0.04) : b.getZsize() / 2);
		// the cabinet, then a sloped-looking head on top
		box(vc, m, -hw, 0, -hd, hw, (float) h * 0.62f, hd, TrahisonPalette.PANEL_RAISED);
		box(vc, m, -hw - 0.02f, (float) h * 0.62f, -hd - 0.02f, hw + 0.02f, (float) h, hd + 0.02f, TrahisonPalette.EDGE);
		// the screen, on the face toward the players
		double depth = item.faceX() != 0 ? hw : hd;
		Frame f = new Frame(b.getCenter().x + item.faceX() * (depth + 0.045), b.minY + h * 0.81, b.getCenter().z + item.faceZ() * (depth + 0.045),
				item.faceX(), item.faceZ());
		float sw = (float) ((item.faceX() != 0 ? hd : hw) * 2 - 0.12) / PX;
		float sh = (float) (h * 0.30) / PX;
		screen(vc, camera, f, sw, sh, puzzle, solved, now);
	}

	/** A glowing screen of {@code w x h} panel pixels centred on the frame. */
	private static void screen(VertexConsumer vc, Vec3 camera, Frame f, float w, float h, boolean puzzle, boolean solved, long now) {
		Matrix4f m = f.pose(camera).last().pose();
		int glow = solved ? TrahisonPalette.SUCCESS : puzzle ? TrahisonUi.lerp(TrahisonUi.pulse(2400), TrahisonPalette.ACCENT_DIM, TrahisonPalette.ACCENT)
				: TrahisonPalette.TEXT_DISABLED;
		rect(vc, m, -w / 2 - 2, -h / 2 - 2, w / 2 + 2, h / 2 + 2, 0, TrahisonPalette.BORDER);
		rect(vc, m, -w / 2, -h / 2, w / 2, h / 2, -1, TrahisonUi.lerp(0.18f, TrahisonPalette.BG_DEEP, glow));
		// text-like lines, and a blinking cursor while the puzzle is open
		float y = -h / 2 + 5;
		int line = 0;
		while (y + 3 < h / 2 - 3) {
			float len = (w - 14) * (0.35f + 0.55f * (((line * 37 + 11) % 10) / 10f));
			rect(vc, m, -w / 2 + 6, y, -w / 2 + 6 + len, y + 2, -2, TrahisonUi.withAlpha(glow, 0xB0));
			y += 6;
			line++;
		}
		if (puzzle && now / 600 % 2 == 0) {
			rect(vc, m, -w / 2 + 6, h / 2 - 8, -w / 2 + 12, h / 2 - 4, -3, TrahisonPalette.ACCENT_HOT);
		}
	}

	// ---- keypad

	private static void keypad(VertexConsumer vc, Vec3 camera, Item item, boolean puzzle, boolean solved, long now) {
		AABB b = item.box();
		double wallBack = item.faceX() != 0 ? (item.faceX() > 0 ? b.minX : b.maxX) : (item.faceZ() > 0 ? b.minZ : b.maxZ);
		double cx = b.getCenter().x;
		double cz = b.getCenter().z;
		double cy = b.minY + Math.max(0.5, Math.min(b.getYsize(), 1.2)) / 2 + 0.2;
		float hw = 0.28f;
		float hh = 0.40f;
		// the slab against the wall
		double sx = item.faceX() != 0 ? wallBack + item.faceX() * 0.05 : cx;
		double sz = item.faceZ() != 0 ? wallBack + item.faceZ() * 0.05 : cz;
		Matrix4f m = rel(camera, sx, cy, sz);
		float ax = item.faceX() != 0 ? 0.05f : hw;
		float az = item.faceZ() != 0 ? 0.05f : hw;
		box(vc, m, -ax, -hh, -az, ax, hh, az, TrahisonPalette.PANEL_RAISED);
		Frame f = new Frame(sx + item.faceX() * 0.052, cy, sz + item.faceZ() * 0.052, item.faceX(), item.faceZ());
		Matrix4f fm = f.pose(camera).last().pose();
		float w = hw * 2 / PX;
		float h = hh * 2 / PX;
		int glow = solved ? TrahisonPalette.SUCCESS : puzzle ? TrahisonPalette.ACCENT : TrahisonPalette.TEXT_DISABLED;
		rect(vc, fm, -w / 2 + 3, -h / 2 + 3, w / 2 - 3, -h / 2 + 15, -1, TrahisonPalette.BORDER);
		rect(vc, fm, -w / 2 + 4, -h / 2 + 4, w / 2 - 4, -h / 2 + 14, -2, TrahisonUi.lerp(0.3f, TrahisonPalette.BG_DEEP, glow));
		float keyW = (w - 6 - 8) / 3f;
		float keyH = 9;
		for (int row = 0; row < 4; row++) {
			for (int col = 0; col < 3; col++) {
				float x = -w / 2 + 3 + col * (keyW + 2);
				float y = -h / 2 + 19 + row * (keyH + 2);
				rect(vc, fm, x, y, x + keyW, y + keyH, -1, TrahisonPalette.BORDER);
				rect(vc, fm, x + 1, y + 1, x + keyW - 1, y + keyH - 1, -2, row == 3 && col == 2 ? TrahisonUi.lerp(0.5f, TrahisonPalette.EDGE, glow) : TrahisonPalette.EDGE);
			}
		}
		if (puzzle && now / 700 % 2 == 0) {
			rect(vc, fm, -w / 2 + 6, -h / 2 + 6, -w / 2 + 11, -h / 2 + 12, -3, TrahisonPalette.ACCENT_HOT);
		}
	}

	// ---- lever, plate, pedestal, document

	private static void lever(VertexConsumer vc, Vec3 camera, Item item, boolean solved, long now) {
		AABB b = item.box();
		double wallBack = item.faceX() != 0 ? (item.faceX() > 0 ? b.minX : b.maxX) : (item.faceZ() > 0 ? b.minZ : b.maxZ);
		double sx = item.faceX() != 0 ? wallBack + item.faceX() * 0.04 : b.getCenter().x;
		double sz = item.faceZ() != 0 ? wallBack + item.faceZ() * 0.04 : b.getCenter().z;
		double cy = b.minY + Math.min(0.7, Math.max(0.45, b.getYsize() * 0.5));
		Matrix4f m = rel(camera, sx, cy, sz);
		float ax = item.faceX() != 0 ? 0.04f : 0.2f;
		float az = item.faceZ() != 0 ? 0.04f : 0.2f;
		box(vc, m, -ax, -0.28f, -az, ax, 0.28f, az, TrahisonPalette.PANEL_RAISED);
		Frame f = new Frame(sx + item.faceX() * 0.045, cy, sz + item.faceZ() * 0.045, item.faceX(), item.faceZ());
		Matrix4f fm = f.pose(camera).last().pose();
		rect(vc, fm, -4, -10, 4, 10, 0, TrahisonPalette.BORDER);
		rect(vc, fm, -3, -9, 3, 9, -1, TrahisonPalette.PANEL_SUNKEN);
		// the handle, up while the puzzle waits, down once solved
		float angle = (float) Math.toRadians(solved ? 150 : 25);
		float len = 22;
		float tx = (float) Math.sin(angle) * len;
		float ty = (float) -Math.cos(angle) * len;
		float nx = (float) Math.cos(angle) * 2.2f;
		float ny = (float) Math.sin(angle) * 2.2f;
		int color = solved ? TrahisonPalette.SUCCESS : TrahisonPalette.EDGE_LIGHT;
		quad(vc, fm, -nx, -ny, nx, ny, tx + nx, ty + ny, tx - nx, ty - ny, -2, color);
		rect(vc, fm, tx - 4, ty - 4, tx + 4, ty + 4, -3, solved ? TrahisonPalette.SUCCESS : TrahisonPalette.DANGER);
	}

	/**
	 * A lever of {@code three_levers}: the handle is down and lit while this lever is engaged, up and dark while it waits
	 * (it blinks in the last two seconds of the lease), and three small lights under the plate show how many of the three
	 * levers are engaged together: the progress the group shares. All three green and the handle down once solved.
	 */
	private static void coopLever(VertexConsumer vc, Vec3 camera, Item item, Show show, long now) {
		int number = 0;
		if (show.role().startsWith("lever_")) {
			try {
				number = Integer.parseInt(show.role().substring("lever_".length()));
			} catch (NumberFormatException ignored) {
				// a malformed role: drawn as waiting
			}
		}
		LeverState.State state = LeverState.of(item.round(), item.group(), show.step(), number, System.currentTimeMillis());
		int count = LeverState.count(item.round(), item.group(), show.step(), System.currentTimeMillis());
		AABB b = item.box();
		double wallBack = item.faceX() != 0 ? (item.faceX() > 0 ? b.minX : b.maxX) : (item.faceZ() > 0 ? b.minZ : b.maxZ);
		double sx = item.faceX() != 0 ? wallBack + item.faceX() * 0.04 : b.getCenter().x;
		double sz = item.faceZ() != 0 ? wallBack + item.faceZ() * 0.04 : b.getCenter().z;
		double cy = b.minY + Math.min(0.7, Math.max(0.45, b.getYsize() * 0.5));
		Matrix4f m = rel(camera, sx, cy, sz);
		float ax = item.faceX() != 0 ? 0.04f : 0.2f;
		float az = item.faceZ() != 0 ? 0.04f : 0.2f;
		box(vc, m, -ax, -0.28f, -az, ax, 0.28f, az, TrahisonPalette.PANEL_RAISED);
		Frame f = new Frame(sx + item.faceX() * 0.045, cy, sz + item.faceZ() * 0.045, item.faceX(), item.faceZ());
		Matrix4f fm = f.pose(camera).last().pose();
		rect(vc, fm, -4, -10, 4, 10, 0, TrahisonPalette.BORDER);
		rect(vc, fm, -3, -9, 3, 9, -1, TrahisonPalette.PANEL_SUNKEN);
		float angle = (float) Math.toRadians(state.on() ? 150 : 25);
		float len = 22;
		float tx = (float) Math.sin(angle) * len;
		float ty = (float) -Math.cos(angle) * len;
		float nx = (float) Math.cos(angle) * 2.2f;
		float ny = (float) Math.sin(angle) * 2.2f;
		boolean blink = state.fading(now) && now / 150 % 2 == 0;
		int lit = state.solved() ? TrahisonPalette.SUCCESS : blink ? TrahisonPalette.ACCENT_DIM : TrahisonPalette.ACCENT_HOT;
		quad(vc, fm, -nx, -ny, nx, ny, tx + nx, ty + ny, tx - nx, ty - ny, -2, state.on() ? lit : TrahisonPalette.EDGE_LIGHT);
		rect(vc, fm, tx - 4, ty - 4, tx + 4, ty + 4, -3, state.on() ? lit : TrahisonPalette.DANGER);
		// the shared progress: one light per lever engaged, all green when done
		for (int i = 0; i < 3; i++) {
			int x = -11 + i * 8;
			rect(vc, fm, x, 14, x + 6, 20, -1, TrahisonPalette.BORDER);
			rect(vc, fm, x + 1, 15, x + 5, 19, -2, state.solved() ? TrahisonPalette.SUCCESS : i < count ? TrahisonPalette.ACCENT_HOT : TrahisonPalette.PANEL_SUNKEN);
		}
	}

	private static void plate(VertexConsumer vc, Vec3 camera, Item item, boolean puzzle, boolean solved) {
		AABB b = item.box();
		Matrix4f m = rel(camera, b.getCenter().x, b.minY, b.getCenter().z);
		float hw = (float) Math.min(0.5, b.getXsize() / 2);
		float hd = (float) Math.min(0.5, b.getZsize() / 2);
		float height = solved ? 0.02f : 0.07f;
		box(vc, m, -hw, 0, -hd, hw, height, hd, TrahisonPalette.PANEL_RAISED);
		int glow = solved ? TrahisonPalette.SUCCESS : puzzle ? TrahisonPalette.ACCENT : TrahisonPalette.TEXT_DISABLED;
		box(vc, m, -hw + 0.12f, height, -hd + 0.12f, hw - 0.12f, height + 0.005f, hd - 0.12f, glow);
	}

	private static void pedestal(VertexConsumer vc, Vec3 camera, Item item, boolean puzzle, boolean solved, long now) {
		AABB b = item.box();
		double h = Math.min(1.2, Math.max(0.7, b.getYsize()));
		Matrix4f m = rel(camera, b.getCenter().x, b.minY, b.getCenter().z);
		float hw = (float) Math.min(0.42, b.getXsize() / 2);
		float hd = (float) Math.min(0.42, b.getZsize() / 2);
		float hh = (float) h;
		box(vc, m, -hw, 0, -hd, hw, 0.14f * hh, hd, TrahisonPalette.EDGE);
		box(vc, m, -hw * 0.6f, 0.14f * hh, -hd * 0.6f, hw * 0.6f, 0.84f * hh, hd * 0.6f, TrahisonPalette.PANEL_RAISED);
		box(vc, m, -hw, 0.84f * hh, -hd, hw, hh, hd, TrahisonPalette.EDGE);
		int glow = solved ? TrahisonPalette.SUCCESS : puzzle || item.slot().item() != null ? TrahisonPalette.ACCENT : TrahisonPalette.TEXT_DISABLED;
		box(vc, m, -hw + 0.07f, hh, -hd + 0.07f, hw - 0.07f, hh + 0.012f, hd - 0.07f, glow);
	}

	private static void document(VertexConsumer vc, Vec3 camera, Item item, long now) {
		AABB b = item.box();
		float bob = (float) Math.sin(now / 900.0) * 0.03f;
		Matrix4f m = rel(camera, b.getCenter().x, b.getCenter().y + bob, b.getCenter().z);
		float spin = (float) Math.toRadians(now % 9000 / 9000.0 * 360);
		float c = (float) Math.cos(spin);
		float s = (float) Math.sin(spin);
		// a sheet standing on its edge, turning slowly: two faces so it reads from both sides
		float hw = 0.22f;
		float hh = 0.3f;
		for (float off : new float[] { 0.004f, -0.004f }) {
			quadWorld(vc, m, onSheet(-hw, -hh, off, c, s), onSheet(hw, -hh, off, c, s), onSheet(hw, hh, off, c, s), onSheet(-hw, hh, off, c, s), 0xFFE8E0C8);
		}
		for (int i = 0; i < 5; i++) {
			float y = -hh + 0.1f + i * 0.1f;
			float x1 = -hw * 0.7f;
			float x2 = hw * (i == 4 ? 0.2f : 0.7f);
			quadWorld(vc, m, onSheet(x1, y, 0.006f, c, s), onSheet(x2, y, 0.006f, c, s), onSheet(x2, y + 0.03f, 0.006f, c, s),
					onSheet(x1, y + 0.03f, 0.006f, c, s), 0xFF6A6455);
			quadWorld(vc, m, onSheet(x1, y, -0.006f, c, s), onSheet(x1, y + 0.03f, -0.006f, c, s), onSheet(x2, y + 0.03f, -0.006f, c, s),
					onSheet(x2, y, -0.006f, c, s), 0xFF6A6455);
		}
	}

	/** A point of the turning sheet: {@code x} along it, {@code y} up, {@code off} off its plane. */
	private static float[] onSheet(float x, float y, float off, float c, float s) {
		return new float[] { x * c - off * s, y, x * s + off * c };
	}

	// ---- floating item

	/** A small octahedron that bobs and turns (an item to pick up, or a bare marker). */
	private static void floating(VertexConsumer vc, Vec3 camera, Vec3 at, float r, int color, long now) {
		float bob = (float) Math.sin(now / 700.0) * 0.06f;
		Matrix4f m = rel(camera, at.x, at.y + bob, at.z);
		double angle = now % 3600 / 3600.0 * Math.PI * 2;
		float c = (float) Math.cos(angle);
		float s = (float) Math.sin(angle);
		float[] px = { r * c, 0, -r * s };
		float[] nx = { -r * c, 0, r * s };
		float[] pz = { r * s, 0, r * c };
		float[] nz = { -r * s, 0, -r * c };
		float[] up = { 0, r * 1.4f, 0 };
		float[] down = { 0, -r * 1.4f, 0 };
		float[][] ring = { px, pz, nx, nz };
		for (int i = 0; i < 4; i++) {
			float[] a = ring[i];
			float[] b = ring[(i + 1) % 4];
			quadWorld(vc, m, up, a, b, b, shade(color, 1.0f - 0.12f * i));
			quadWorld(vc, m, down, b, a, a, shade(color, 0.7f - 0.08f * i));
		}
	}

	// ---- clues

	/** A readable clue (text, digits, symbols, colours, a document's cover) on a wall panel, or as a small card in front of the slot. */
	private static void clues(VertexConsumer vc, Vec3 camera, Item item, List<TextJob> texts, List<GlyphJob> glyphs, boolean wall) {
		List<Clue> clues = item.slot().readable();
		if (clues.isEmpty()) {
			return;
		}
		AABB b = item.box();
		double along = item.faceX() != 0 ? b.getZsize() : b.getXsize();
		double width = wall ? Math.max(0.8, Math.min(4.0, along)) : 0.9;
		double height = wall ? Math.max(0.6, Math.min(2.4, b.getYsize())) : 0.55;
		double wallBack = item.faceX() != 0 ? (item.faceX() > 0 ? b.minX : b.maxX) : (item.faceZ() > 0 ? b.minZ : b.maxZ);
		double cx = item.faceX() != 0 ? wallBack + item.faceX() * (wall ? 0.03 : 0.3) : b.getCenter().x;
		double cz = item.faceZ() != 0 ? wallBack + item.faceZ() * (wall ? 0.03 : 0.3) : b.getCenter().z;
		double cy = wall ? b.minY + height / 2 : b.minY + 1.55;
		float w = (float) (width / PX);
		float h = (float) (height / PX);
		// the board itself (a thin slab), then its face
		Matrix4f m = rel(camera, cx, cy, cz);
		float ax = item.faceX() != 0 ? 0.03f : (float) width / 2;
		float az = item.faceZ() != 0 ? 0.03f : (float) width / 2;
		box(vc, m, -ax, (float) -height / 2, -az, ax, (float) height / 2, az, TrahisonPalette.BORDER);
		Frame f = new Frame(cx + item.faceX() * 0.032, cy, cz + item.faceZ() * 0.032, item.faceX(), item.faceZ());
		Matrix4f fm = f.pose(camera).last().pose();
		rect(vc, fm, -w / 2 + 1, -h / 2 + 1, w / 2 - 1, h / 2 - 1, -1, TrahisonPalette.PANEL_SUNKEN);
		rect(vc, fm, -w / 2 + 1, -h / 2 + 1, w / 2 - 1, -h / 2 + 2, -2, TrahisonPalette.ACCENT_DIM);
		float band = (h - 8) / clues.size();
		for (int i = 0; i < clues.size(); i++) {
			clueFace(vc, fm, f, clues.get(i), -h / 2 + 4 + i * band, band, w, texts, glyphs);
		}
	}

	private static void clueFace(VertexConsumer vc, Matrix4f fm, Frame f, Clue clue, float top, float band, float w, List<TextJob> texts, List<GlyphJob> glyphs) {
		float mid = top + band / 2;
		switch (clue.type()) {
			case "digits" -> {
				String d = clue.text();
				float scale = Math.max(1, Math.min(4, Math.min(band / 12f, (w - 16) / Math.max(1, d.length() * 9f))));
				texts.add(new TextJob(f, -d.length() * 4.5f * scale, mid - 4 * scale, Component.literal(spaced(d)), TrahisonPalette.ACCENT_HOT, scale, false, null));
			}
			case "symbols" -> {
				List<String> ids = clue.list();
				int size = (int) Math.max(9, Math.min(Math.min(band - 6, 22), (w - 12) / Math.max(1, ids.size()) - 4));
				glyphs.add(new GlyphJob(f, 0, mid, size, ids, 4, TrahisonPalette.ACCENT_HOT));
			}
			case "colors" -> {
				List<String> names = clue.list();
				float size = Math.max(9, Math.min(Math.min(band - 6, 20), (w - 12) / Math.max(1, names.size()) - 4));
				float x = -(names.size() * (size + 4) - 4) / 2f;
				for (String name : names) {
					WireColor c = colorOf(name);
					int tone = c == null ? TrahisonPalette.TEXT_MUTED : c.base;
					rect(vc, fm, x - 1, mid - size / 2 - 1, x + size + 1, mid + size / 2 + 1, -2, TrahisonPalette.BORDER);
					rect(vc, fm, x, mid - size / 2, x + size, mid + size / 2, -3, tone);
					x += size + 4;
				}
				glyphColors(texts, f, names, mid + 1, size);
			}
			case "document" -> {
				rect(vc, fm, -9, mid - 10, 9, mid + 10, -2, 0xFFE8E0C8);
				for (int i = 0; i < 4; i++) {
					rect(vc, fm, -6, mid - 7 + i * 4, i == 3 ? 1 : 6, mid - 6 + i * 4, -3, 0xFF6A6455);
				}
			}
			case "image" -> {
				rect(vc, fm, -14, mid - 10, 14, mid + 10, -2, TrahisonPalette.EDGE);
				rect(vc, fm, -12, mid - 8, 12, mid + 8, -3, TrahisonPalette.PANEL);
				rect(vc, fm, -8, mid + 2, -2, mid + 6, -4, TrahisonPalette.GREEN_DARK);
				rect(vc, fm, 1, mid - 5, 8, mid + 6, -4, TrahisonPalette.BLUE_DARK);
			}
			default -> {
				Minecraft mc = Minecraft.getInstance();
				List<FormattedCharSequence> lines = mc.font.split(Component.literal(clue.text()), (int) (w - 16));
				float y = mid - Math.min(lines.size(), Math.max(1, (int) (band / 10))) * 5f;
				int shown = 0;
				for (FormattedCharSequence line : lines) {
					if (shown++ >= Math.max(1, (int) (band / 10))) {
						break;
					}
					texts.add(new TextJob(f, -w / 2 + 8, y, Component.literal(plain(line)), TrahisonPalette.TEXT, 1f, false, null));
					y += 10;
				}
			}
		}
	}

	private static void glyphColors(List<TextJob> texts, Frame f, List<String> names, float y, float size) {
		// the colour's symbol goes with its square (never the colour alone, charte-ui.md section 9)
		float x = -(names.size() * (size + 4) - 4) / 2f;
		for (String name : names) {
			WireColor c = colorOf(name);
			String mark = c == null ? "?" : switch (c.glyph) {
				case TRIANGLE -> "\u25B2";
				case CIRCLE -> "\u25CF";
				case DIAMOND -> "\u25C6";
				case RING -> "\u25CB";
				default -> "\u25A0";
			};
			texts.add(new TextJob(f, x + size / 2f - 3, y - 4, Component.literal(mark), TrahisonPalette.BG_DEEP, 1f, false, null));
			x += size + 4;
		}
	}

	private static WireColor colorOf(String name) {
		return WireColor.of(name);
	}

	private static String spaced(String digits) {
		StringBuilder sb = new StringBuilder();
		for (char c : digits.toCharArray()) {
			sb.append(c);
		}
		return sb.toString();
	}

	private static String plain(FormattedCharSequence seq) {
		StringBuilder sb = new StringBuilder();
		seq.accept((i, style, cp) -> {
			sb.appendCodePoint(cp);
			return true;
		});
		return sb.toString();
	}

	// ---------------------------------------------------------------- aim: outline and label

	private static void outline(VertexConsumer vc, Vec3 camera, AABB b, int color) {
		Matrix4f m = rel(camera, 0, 0, 0);
		float t = 0.018f;
		float x1 = (float) b.minX;
		float y1 = (float) b.minY;
		float z1 = (float) b.minZ;
		float x2 = (float) b.maxX;
		float y2 = (float) b.maxY;
		float z2 = (float) b.maxZ;
		for (float y : new float[] { y1, y2 }) {
			boxFlat(vc, m, x1, y - t, z1 - t, x2, y + t, z1 + t, color);
			boxFlat(vc, m, x1, y - t, z2 - t, x2, y + t, z2 + t, color);
			boxFlat(vc, m, x1 - t, y - t, z1, x1 + t, y + t, z2, color);
			boxFlat(vc, m, x2 - t, y - t, z1, x2 + t, y + t, z2, color);
		}
		for (float x : new float[] { x1, x2 }) {
			for (float z : new float[] { z1, z2 }) {
				boxFlat(vc, m, x - t, y1, z - t, x + t, y2, z + t, color);
			}
		}
	}

	/** The name of the action over the aimed object (a pill, billboarded to the camera). */
	private static void label(VertexConsumer vc, WorldRenderContext context, Vec3 camera, Item item, List<TextJob> texts, Font font) {
		String action = item.action(Inventory.held());
		if (action == null) {
			return;
		}
		Component name = Component.translatable(T + "interact." + action);
		Component what = what(item);
		Vec3 at = new Vec3(item.box().getCenter().x, item.box().maxY + 0.45, item.box().getCenter().z);
		float w = Math.max(font.width(name), what == null ? 0 : font.width(what)) + 16;
		float h = what == null ? 18 : 28;
		PoseStack pose = new PoseStack();
		pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
		pose.mulPose(context.camera().rotation());
		pose.scale(-0.025f, -0.025f, 0.025f);
		Matrix4f m = pose.last().pose();
		rect(vc, m, -w / 2 - 1, -h - 1, w / 2 + 1, 1, 0, TrahisonPalette.BORDER);
		rect(vc, m, -w / 2, -h, w / 2, 0, -1, TrahisonPalette.BG_DEEP);
		rect(vc, m, -w / 2, -h, w / 2, -h + 1, -2, TrahisonPalette.ACCENT);
		texts.add(new TextJob(null, -font.width(name) / 2f, -h + 5, name, TrahisonPalette.ACCENT_HOT, 1f, true, at));
		if (what != null) {
			texts.add(new TextJob(null, -font.width(what) / 2f, -h + 16, what, TrahisonPalette.TEXT_SECONDARY, 1f, true, at));
		}
	}

	private static Component what(Item item) {
		SlotView slot = item.slot();
		if (slot.item() != null && slot.openPuzzle() == null) {
			return Inventory.name(slot.item().text());
		}
		if ("use_item".equals(item.action(Inventory.held())) && Inventory.held() != null) {
			return Inventory.name(Inventory.held());
		}
		String key = T + "slot." + slot.kind();
		return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : null;
	}

	// ---------------------------------------------------------------- text and symbol passes

	private static void drawText(WorldRenderContext context, MultiBufferSource.BufferSource buffers, Font font, Vec3 camera, TextJob job) {
		PoseStack pose;
		if (job.billboard()) {
			pose = new PoseStack();
			pose.translate(job.at().x - camera.x, job.at().y - camera.y, job.at().z - camera.z);
			pose.mulPose(context.camera().rotation());
			pose.scale(-0.025f, -0.025f, 0.025f);
			pose.translate(0, 0, -3 * LAYER);
		} else {
			pose = job.frame().pose(camera);
			pose.translate(0, 0, -6 * LAYER);
			pose.scale(job.scale(), job.scale(), 1f);
		}
		font.drawInBatch(job.text(), job.x() / (job.billboard() ? 1f : job.scale()), job.y() / (job.billboard() ? 1f : job.scale()), job.color(), false,
				pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LIGHT);
	}

	/** Symbols of the minigames' set on a clue panel, drawn in the panel's plane with the same {@link Glyphs} the puzzle screens use. */
	private static void drawGlyphs(Minecraft mc, MultiBufferSource.BufferSource buffers, Vec3 camera, List<GlyphJob> jobs) {
		GuiGraphics g = new GuiGraphics(mc, buffers);
		for (GlyphJob job : jobs) {
			PoseStack frame = job.frame().pose(camera);
			g.pose().pushPose();
			g.pose().mulPose(frame.last().pose());
			g.pose().translate(0, 0, -6 * LAYER);
			float x = -(job.ids().size() * (job.size() + job.gap()) - job.gap()) / 2f + job.size() / 2f;
			for (String id : job.ids()) {
				Glyphs.draw(g, id, x, job.cy(), job.size(), 0, job.color());
				x += job.size() + job.gap();
			}
			g.pose().popPose();
		}
		buffers.endBatch(RenderType.gui()); // not g.flush(): that one turns the depth test off, and a wall must hide its panel
	}

	// ---------------------------------------------------------------- primitives

	/** A matrix that puts local (0, 0, 0) at a world point, camera-relative. */
	private static Matrix4f rel(Vec3 camera, double x, double y, double z) {
		return new Matrix4f().translation((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z));
	}

	private static int shade(int color, float f) {
		int r = Math.min(255, Math.round((color >> 16 & 0xFF) * f));
		int g = Math.min(255, Math.round((color >> 8 & 0xFF) * f));
		int b = Math.min(255, Math.round((color & 0xFF) * f));
		return (color & 0xFF000000) | r << 16 | g << 8 | b;
	}

	/** A shaded axis-aligned box in local coordinates. */
	private static void box(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
		quadWorld(vc, m, new float[] { x1, y2, z1 }, new float[] { x2, y2, z1 }, new float[] { x2, y2, z2 }, new float[] { x1, y2, z2 }, shade(color, TOP));
		quadWorld(vc, m, new float[] { x1, y1, z1 }, new float[] { x1, y1, z2 }, new float[] { x2, y1, z2 }, new float[] { x2, y1, z1 }, shade(color, BOTTOM));
		quadWorld(vc, m, new float[] { x1, y1, z1 }, new float[] { x2, y1, z1 }, new float[] { x2, y2, z1 }, new float[] { x1, y2, z1 }, shade(color, SIDE_Z));
		quadWorld(vc, m, new float[] { x1, y1, z2 }, new float[] { x1, y2, z2 }, new float[] { x2, y2, z2 }, new float[] { x2, y1, z2 }, shade(color, SIDE_Z));
		quadWorld(vc, m, new float[] { x1, y1, z1 }, new float[] { x1, y2, z1 }, new float[] { x1, y2, z2 }, new float[] { x1, y1, z2 }, shade(color, SIDE_X));
		quadWorld(vc, m, new float[] { x2, y1, z1 }, new float[] { x2, y1, z2 }, new float[] { x2, y2, z2 }, new float[] { x2, y2, z1 }, shade(color, SIDE_X));
	}

	/** A box without shading (the aim outline). */
	private static void boxFlat(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
		quadWorld(vc, m, new float[] { x1, y2, z1 }, new float[] { x2, y2, z1 }, new float[] { x2, y2, z2 }, new float[] { x1, y2, z2 }, color);
		quadWorld(vc, m, new float[] { x1, y1, z1 }, new float[] { x1, y1, z2 }, new float[] { x2, y1, z2 }, new float[] { x2, y1, z1 }, color);
		quadWorld(vc, m, new float[] { x1, y1, z1 }, new float[] { x2, y1, z1 }, new float[] { x2, y2, z1 }, new float[] { x1, y2, z1 }, color);
		quadWorld(vc, m, new float[] { x1, y1, z2 }, new float[] { x1, y2, z2 }, new float[] { x2, y2, z2 }, new float[] { x2, y1, z2 }, color);
		quadWorld(vc, m, new float[] { x1, y1, z1 }, new float[] { x1, y2, z1 }, new float[] { x1, y2, z2 }, new float[] { x1, y1, z2 }, color);
		quadWorld(vc, m, new float[] { x2, y1, z1 }, new float[] { x2, y1, z2 }, new float[] { x2, y2, z2 }, new float[] { x2, y2, z1 }, color);
	}

	private static void quadWorld(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d, int color) {
		vc.addVertex(m, a[0], a[1], a[2]).setColor(color);
		vc.addVertex(m, b[0], b[1], b[2]).setColor(color);
		vc.addVertex(m, c[0], c[1], c[2]).setColor(color);
		vc.addVertex(m, d[0], d[1], d[2]).setColor(color);
	}

	/** A flat rectangle in panel pixels; {@code layer} negative is toward the viewer. */
	private static void rect(VertexConsumer vc, Matrix4f m, float x1, float y1, float x2, float y2, int layer, int color) {
		float z = layer * LAYER;
		vc.addVertex(m, x1, y1, z).setColor(color);
		vc.addVertex(m, x2, y1, z).setColor(color);
		vc.addVertex(m, x2, y2, z).setColor(color);
		vc.addVertex(m, x1, y2, z).setColor(color);
	}

	/** A flat quad in panel pixels from four corners. */
	private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int layer, int color) {
		float z = layer * LAYER;
		vc.addVertex(m, ax, ay, z).setColor(color);
		vc.addVertex(m, bx, by, z).setColor(color);
		vc.addVertex(m, cx, cy, z).setColor(color);
		vc.addVertex(m, dx, dy, z).setColor(color);
	}
}
