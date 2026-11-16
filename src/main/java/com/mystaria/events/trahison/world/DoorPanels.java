package com.mystaria.events.trahison.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

import com.mystaria.events.trahison.edit.LayoutDto;
import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.LayoutDto.Door;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.DoorHint;
import com.mystaria.events.trahison.net.GameView.Need;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi.GameColor;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;

/**
 * The floating panel above each colour door during DOORS (solution S14, {@code WorldRenderEvents.AFTER_TRANSLUCENT}): the
 * door's two colours as glyph pills (the colour never stands alone: its symbol and its name come with it), then either
 * what its threshold lacks ({@code door_hints}, computed by the backend: this class decides nothing) or "open" with a
 * countdown. Drawn as a billboard facing the camera, in the look of the charter (dark panel, hairline, palette tokens), with
 * depth testing: it cannot be seen through a wall. Visible to every viewer of the game; within {@value #RANGE} blocks.
 *
 * <p>Layers are separated by a fraction of a pixel toward the viewer (local {@code -z}: the camera's {@code +z} is what it
 * looks at) so the panel, the pills and the text never fight for the same depth. Client thread.
 */
public final class DoorPanels {

	private static final double RANGE = 16.0;
	/** World size of one panel pixel. */
	private static final float SCALE = 0.03f;
	private static final int TEXT_W = 128;
	private static final int PAD = 8;
	private static final int LINE = 10;
	private static final int PILL_H = 12;
	private static final float LAYER = 0.4f;
	private static final int LIGHT = 0xF000F0;

	/** Per door: the longest open time seen (ms), so the bar can empty from full. */
	private static final Map<String, Long> OPEN_TOTAL = new HashMap<>();

	private DoorPanels() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(DoorPanels::render);
	}

	/** One line of the panel: text, or a need (a colour glyph, then its words). */
	private record Row(Component text, FormattedCharSequence line, int color, GameColor glyph, int height) {
	}

	/** Everything a panel shows, with its size in panel pixels. */
	private record Content(List<GameColor> colors, List<Component> names, List<Row> rows, float bar, int barColor, int width, int height) {
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		View view = GameView.current();
		HubAnchorDto anchor = PhantasmonApi.hubs() == null ? null : PhantasmonApi.hubs().joinedAnchor();
		if (mc.player == null || view == null || anchor == null || !view.hasGame() || view.phase() != Phase.DOORS
				|| view.doorHints().isEmpty() || !anchor.hub().equals(Link.hub())) {
			OPEN_TOTAL.clear();
			return;
		}
		LayoutDto plan = Plan.get(view.hub(), view.layoutVersion());
		if (plan == null) {
			return;
		}
		HubBlocks blocks = HubBlocks.of(anchor);
		Vec3 camera = context.camera().getPosition();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		Font font = mc.font;
		long now = GameView.serverNow();

		List<Object[]> panels = new ArrayList<>(); // { Vec3 anchor, Content }
		for (DoorHint hint : view.doorHints()) {
			Door door = doorOf(plan, hint.door());
			if (door == null || door.block == null) {
				continue;
			}
			Box b = door.block;
			Vec3 at = new Vec3((blocks.worldX(b.minX) + blocks.worldX(b.maxX) + 1) / 2.0, Math.max(b.minY, b.maxY) + 1.4,
					(blocks.worldZ(b.minZ) + blocks.worldZ(b.maxZ) + 1) / 2.0);
			if (at.distanceTo(camera) > RANGE) {
				continue;
			}
			panels.add(new Object[] { at, content(font, hint, now) });
		}
		if (panels.isEmpty()) {
			return;
		}
		// Shapes first (one buffer, ended before the text asks for another: solution S14), then the text.
		for (Object[] p : panels) {
			drawShapes(context, buffers, camera, (Vec3) p[0], (Content) p[1]);
		}
		buffers.endBatch(RenderType.debugQuads());
		for (Object[] p : panels) {
			drawText(context, buffers, font, camera, (Vec3) p[0], (Content) p[1]);
		}
		buffers.endBatch();
	}

	private static Door doorOf(LayoutDto plan, String id) {
		for (Door door : plan.doors) {
			if (door.id.equals(id)) {
				return door;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- content

	private static Content content(Font font, DoorHint hint, long now) {
		List<GameColor> colors = hint.colors();
		List<Component> names = new ArrayList<>();
		for (GameColor c : colors) {
			names.add(colorName(c));
		}
		List<Row> rows = new ArrayList<>();
		float bar = -1f;
		int barColor = TrahisonPalette.SUCCESS;
		if (hint.openUntil() != null && hint.openUntil() > now) {
			long left = hint.openUntil() - now;
			long total = OPEN_TOTAL.merge(hint.door(), left, Math::max);
			bar = Math.min(1f, left / (float) Math.max(1, total));
			rows.add(new Row(Component.translatable("phantasmon_events.trahison.door.open_for", (left + 999) / 1000), null, TrahisonPalette.SUCCESS, null, LINE));
		} else {
			OPEN_TOTAL.remove(hint.door());
			if (hint.incompatible()) {
				for (FormattedCharSequence line : font.split(Component.translatable("phantasmon_events.trahison.door.incompatible"), TEXT_W)) {
					rows.add(new Row(null, line, TrahisonPalette.DANGER, null, LINE));
				}
			}
			if (!hint.missing().isEmpty()) {
				rows.add(new Row(Component.translatable("phantasmon_events.trahison.door.missing"), null, TrahisonPalette.TEXT, null, LINE));
				needRows(hint.missing(), rows);
				if (hint.altMissing() != null && !hint.altMissing().isEmpty()) {
					rows.add(new Row(Component.translatable("phantasmon_events.trahison.door.or"), null, TrahisonPalette.TEXT_MUTED, null, LINE));
					needRows(hint.altMissing(), rows);
				}
			}
		}
		int pills = 0;
		for (Component n : names) {
			pills += pillWidth(font, n);
		}
		int width = Math.max(TEXT_W, pills + 4 * Math.max(0, names.size() - 1)) + 2 * PAD;
		int height = 2 * PAD + PILL_H + (rows.isEmpty() ? 0 : 4);
		for (Row r : rows) {
			height += r.height();
		}
		if (bar >= 0f) {
			height += 8;
		}
		return new Content(colors, names, rows, bar, barColor, width, height);
	}

	private static void needRows(List<Need> needs, List<Row> rows) {
		for (Need n : needs) {
			String key = "phantasmon_events.trahison.door.need." + (n.pair() ? "pair." : "solo.") + n.color().name().toLowerCase(java.util.Locale.ROOT);
			rows.add(new Row(Component.translatable(key), null, TrahisonPalette.TEXT, n.color(), PILL_H));
		}
	}

	private static Component colorName(GameColor c) {
		return Component.translatable("phantasmon_events.trahison.color." + c.name().toLowerCase(java.util.Locale.ROOT));
	}

	private static int pillWidth(Font font, Component name) {
		return 4 + 8 + 4 + font.width(name) + 4;
	}

	// ---------------------------------------------------------------- drawing

	/** The pose of a panel: its anchor (bottom centre), turned to the camera, in panel pixels with y down. */
	private static PoseStack pose(WorldRenderContext context, Vec3 camera, Vec3 at) {
		PoseStack pose = new PoseStack();
		pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
		pose.mulPose(context.camera().rotation());
		pose.scale(-SCALE, -SCALE, SCALE); // x flipped: the camera's local +x is its left
		return pose;
	}

	private static void drawShapes(WorldRenderContext context, MultiBufferSource.BufferSource buffers, Vec3 camera, Vec3 at, Content c) {
		PoseStack pose = pose(context, camera, at);
		Matrix4f m = pose.last().pose();
		VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
		float left = -c.width() / 2f;
		float top = -c.height();
		// Panel: hairline, fill, a thin accent edge on top.
		rect(vc, m, left - 1, top - 1, left + c.width() + 1, 1, 0, TrahisonPalette.BORDER);
		rect(vc, m, left, top, left + c.width(), 0, -1, TrahisonPalette.PANEL);
		rect(vc, m, left, top, left + c.width(), top + 1, -2, TrahisonPalette.EDGE);
		float y = top + PAD;
		Font font = Minecraft.getInstance().font;
		int total = 0;
		for (Component n : c.names()) {
			total += pillWidth(font, n);
		}
		total += 4 * Math.max(0, c.names().size() - 1);
		float x = -total / 2f;
		for (int i = 0; i < c.colors().size(); i++) {
			GameColor color = c.colors().get(i);
			int w = pillWidth(font, c.names().get(i));
			rect(vc, m, x, y, x + w, y + PILL_H, -2, TrahisonPalette.BORDER);
			rect(vc, m, x + 1, y + 1, x + w - 1, y + PILL_H - 1, -3, color.dark);
			glyph(vc, m, color, x + 4, y + 2, 8, -4, color.light);
			x += w + 4;
		}
		y += PILL_H + (c.rows().isEmpty() ? 0 : 4);
		for (Row r : c.rows()) {
			if (r.glyph() != null) {
				glyph(vc, m, r.glyph(), left + PAD, y + 2, 8, -2, r.glyph().light);
			}
			y += r.height();
		}
		if (c.bar() >= 0f) {
			float w = c.width() - 2 * PAD;
			rect(vc, m, left + PAD, y + 1, left + PAD + w, y + 5, -2, TrahisonPalette.PANEL_SUNKEN);
			rect(vc, m, left + PAD + 1, y + 2, left + PAD + 1 + (w - 2) * c.bar(), y + 4, -3, c.barColor());
		}
	}

	private static void drawText(WorldRenderContext context, MultiBufferSource.BufferSource buffers, Font font, Vec3 camera, Vec3 at, Content c) {
		PoseStack pose = pose(context, camera, at);
		pose.translate(0, 0, -6 * LAYER); // in front of every shape
		Matrix4f m = pose.last().pose();
		float left = -c.width() / 2f;
		float y = -c.height() + PAD;
		int total = 0;
		for (Component n : c.names()) {
			total += pillWidth(font, n);
		}
		total += 4 * Math.max(0, c.names().size() - 1);
		float x = -total / 2f;
		for (Component name : c.names()) {
			font.drawInBatch(name, x + 16, y + 2, TrahisonPalette.TEXT, false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LIGHT);
			x += pillWidth(font, name) + 4;
		}
		y += PILL_H + (c.rows().isEmpty() ? 0 : 4);
		for (Row r : c.rows()) {
			float tx = left + PAD + (r.glyph() != null ? 14 : 0);
			float ty = y + (r.glyph() != null ? 2 : 1);
			if (r.line() != null) {
				font.drawInBatch(r.line(), tx, ty, r.color(), false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LIGHT);
			} else {
				font.drawInBatch(r.text(), tx, ty, r.color(), false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LIGHT);
			}
			y += r.height();
		}
	}

	/** A flat rectangle in panel pixels; {@code layer} negative is toward the viewer. */
	private static void rect(VertexConsumer vc, Matrix4f m, float x1, float y1, float x2, float y2, int layer, int color) {
		float z = layer * LAYER;
		vc.addVertex(m, x1, y1, z).setColor(color);
		vc.addVertex(m, x2, y1, z).setColor(color);
		vc.addVertex(m, x2, y2, z).setColor(color);
		vc.addVertex(m, x1, y2, z).setColor(color);
	}

	private static void quad(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d, float z, int color) {
		vc.addVertex(m, a[0], a[1], z).setColor(color);
		vc.addVertex(m, b[0], b[1], z).setColor(color);
		vc.addVertex(m, c[0], c[1], z).setColor(color);
		vc.addVertex(m, d[0], d[1], z).setColor(color);
	}

	/** The colour's symbol (charter, section 3): triangle, disc, square, in a {@code size x size} box at ({@code x}, {@code y}). */
	private static void glyph(VertexConsumer vc, Matrix4f m, GameColor color, float x, float y, float size, int layer, int tone) {
		float z = layer * LAYER;
		switch (color.glyph) {
			case SQUARE -> rect(vc, m, x + 1, y + 1, x + size - 1, y + size - 1, layer, tone);
			case CIRCLE -> {
				// An octagon: four quads fanned from the centre.
				float cx = x + size / 2f;
				float cy = y + size / 2f;
				float r = size / 2f;
				float[][] p = new float[8][];
				for (int i = 0; i < 8; i++) {
					double a = Math.PI / 8 + i * Math.PI / 4;
					p[i] = new float[] { cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r };
				}
				float[] centre = { cx, cy };
				for (int i = 0; i < 8; i += 2) {
					quad(vc, m, centre, p[i], p[i + 1], p[(i + 2) % 8], z, tone);
				}
			}
			default -> { // triangle, point up
				float[] top = { x + size / 2f, y + 1 };
				float[] bl = { x, y + size - 1 };
				float[] br = { x + size, y + size - 1 };
				quad(vc, m, top, bl, br, br, z, tone);
			}
		}
	}
}
