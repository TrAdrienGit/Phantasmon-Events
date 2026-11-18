package com.mystaria.events.trahison.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import com.mystaria.events.trahison.edit.LayoutDto;
import com.mystaria.events.trahison.edit.LayoutDto.ResultsBoard;
import com.mystaria.events.trahison.hud.ResultsReveal;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Results;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.VoteResult;
import com.mystaria.events.trahison.net.GameView.Vote;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi.GameColor;
import com.mystaria.events.trahison.ui.TrahisonUi.Glyph;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;

/**
 * The big results board of the hall (T2.4, solution S14, {@code WorldRenderEvents.AFTER_TRANSLUCENT}): a flat panel fixed to
 * the wall the plan's {@code results_board} names ({@link BoardGeometry}), facing the readers, in the look of the charter.
 * During RESULTS it reveals the votes door by door and then the new points ({@link ResultsReveal}); everything it shows
 * comes from the view's {@code results} (no rule here). Drawn for every viewer, with depth testing, within
 * {@value #RANGE} blocks. Without a board in the plan, {@code ResultsHud} shows the results instead.
 *
 * <p>Orientation: after {@code translate} to the board's top-left corner and a rotation of {@code -yaw} about Y (the way a
 * sign's text is placed: yaw is {@code Direction.toYRot} of the facing), local +x is the readers' right hand, local +y is
 * up and local +z points at the readers; the final {@code scale(s, -s, 1)} makes y run downward like a GUI, so text reads
 * left to right, top to bottom, not mirrored. Layers are in blocks along +z. Client thread.
 */
public final class ResultsBoardWorld {

	private static final double RANGE = 64.0;
	private static final int LIGHT = 0xF000F0;
	private static final int PAD = 8;
	private static final int LINE = 11;
	private static final int DOOR_H = 2 * LINE + 6;
	/** Design height in board pixels; the width follows the board's aspect. */
	private static final int DESIGN_H = 176;
	private static final float PLANE = 0.02f;
	private static final float LAYER = 0.012f;
	private static final String T = "phantasmon_events.trahison.";

	private ResultsBoardWorld() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(ResultsBoardWorld::render);
	}

	/** Whether the plan has a usable board (then the HUD fallback stays quiet). */
	public static boolean hasBoard(LayoutDto plan) {
		ResultsBoard board = plan == null ? null : plan.resultsBoard;
		return board != null && BoardGeometry.blocks(board) != null;
	}

	// ---------------------------------------------------------------- display list

	private record Shape(float x1, float y1, float x2, float y2, int layer, int color) {
	}

	private record Label(Component text, float x, float y, int color) {
	}

	/** What one frame of the board draws, in design pixels with y down. */
	private static final class Sheet {
		final List<Shape> shapes = new ArrayList<>();
		final List<Label> labels = new ArrayList<>();

		void rect(float x1, float y1, float x2, float y2, int layer, int color) {
			shapes.add(new Shape(x1, y1, x2, y2, layer, color));
		}

		void text(Component text, float x, float y, int color) {
			labels.add(new Label(text, x, y, color));
		}

		/** The charter's symbols from rectangles, in a {@code size} box (as {@code TrahisonUi.glyph}). */
		void glyph(Glyph glyph, float x, float y, int size, int layer, int color) {
			switch (glyph) {
				case SQUARE -> rect(x + 1, y + 1, x + size - 1, y + size - 1, layer, color);
				case CIRCLE -> {
					int r = size / 2;
					for (int row = 0; row < size; row++) {
						double dy = row + 0.5 - r;
						int half = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
						rect(x + r - half, y + row, x + r + half, y + row + 1, layer, color);
					}
				}
				case TRIANGLE -> {
					for (int row = 0; row < size; row++) {
						int half = Math.min(size / 2, 1 + row * size / Math.max(1, 2 * size - 2));
						rect(x + size / 2f - half, y + row, x + size / 2f + half, y + row + 1, layer, color);
					}
				}
				case ALLY -> {
					int d = Math.max(3, size / 2 - 1);
					rect(x, y + (size - d) / 2f, x + d, y + (size + d) / 2f, layer, color);
					rect(x + size - d, y + (size - d) / 2f, x + size, y + (size + d) / 2f, layer, color);
					rect(x + d, y + size / 2f - 1, x + size - d, y + size / 2f + 1, layer, color);
				}
				case BETRAY -> {
					for (int i = 0; i < size; i++) {
						rect(x + i, y + i, x + i + 2, y + i + 1, layer, color);
						rect(x + size - 1 - i, y + i, x + size + 1 - i, y + i + 1, layer, color);
					}
				}
			}
		}

		/** A pill like the GUI's: hairline, fill, a glyph, then its text. Returns the width. */
		float pill(Font font, Glyph glyph, Component text, float xRight, float y, int fill, int glyphColor, int textColor) {
			float w = font.width(text) + 20;
			float x = xRight - w;
			rect(x, y, x + w, y + 12, 2, TrahisonPalette.BORDER);
			rect(x + 1, y + 1, x + w - 1, y + 11, 3, fill);
			glyph(glyph, x + 3, y + 2, 8, 4, glyphColor);
			text(text, x + 15, y + 2, textColor);
			return w;
		}
	}

	// ---------------------------------------------------------------- frame

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		View view = GameView.current();
		HubAnchorDto anchor = PhantasmonApi.hubs() == null ? null : PhantasmonApi.hubs().joinedAnchor();
		if (mc.player == null || view == null || anchor == null || !view.hasGame() || view.results() == null
				|| ResultsReveal.elapsed(view) < 0 || !anchor.hub().equals(Link.hub())) {
			return;
		}
		LayoutDto plan = Plan.get(view.hub(), view.layoutVersion());
		if (plan == null || !hasBoard(plan)) {
			return;
		}
		ResultsBoard board = plan.resultsBoard;
		HubBlocks blocks = HubBlocks.of(anchor);
		int bx = blocks.worldX(board.position.x);
		int bz = blocks.worldZ(board.position.z);
		double[] corner = BoardGeometry.faceCorner(board.facing, bx, bz);
		int[] right = BoardGeometry.right(board.facing);
		double cx = corner[0] + right[0] * board.width / 2.0;
		double cz = corner[1] + right[1] * board.width / 2.0;
		Vec3 camera = context.camera().getPosition();
		if (camera.distanceTo(new Vec3(cx, board.position.y + board.height / 2.0, cz)) > RANGE) {
			return;
		}
		Font font = mc.font;
		float aspect = board.width / (float) board.height;
		int designW = Math.max(220, Math.min(440, Math.round(DESIGN_H * aspect)));
		Sheet sheet = layout(font, view, view.results(), designW);
		float scale = Math.min(board.width / (float) designW, board.height / (float) DESIGN_H);
		float ox = (board.width - designW * scale) / 2f;
		float oy = (board.height - DESIGN_H * scale) / 2f;

		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		PoseStack pose = new PoseStack();
		pose.translate(corner[0] - camera.x, board.position.y + board.height - camera.y, corner[1] - camera.z);
		pose.mulPose(Axis.YP.rotationDegrees(-BoardGeometry.yaw(board.facing)));
		VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
		// The whole board first: a backing panel with a hairline, so it reads as an object on the wall.
		pose.pushPose();
		pose.scale(1f, -1f, 1f); // x in blocks to the right, y in blocks downward
		Matrix4f bm = pose.last().pose();
		rect(vc, bm, -0.06f, -0.06f, board.width + 0.06f, board.height + 0.06f, PLANE - LAYER / 2, TrahisonPalette.BORDER);
		rect(vc, bm, 0, 0, board.width, board.height, PLANE, TrahisonPalette.BG_DEEP);
		rect(vc, bm, 0, 0, board.width, 0.05f, PLANE + LAYER / 2, TrahisonPalette.ACCENT_DIM);
		pose.popPose();

		pose.translate(ox, -oy, 0);
		pose.scale(scale, -scale, 1f);
		Matrix4f m = pose.last().pose();
		for (Shape s : sheet.shapes) {
			rect(vc, m, s.x1, s.y1, s.x2, s.y2, PLANE + s.layer * LAYER, s.color);
		}
		buffers.endBatch(RenderType.debugQuads());
		pose.translate(0, 0, PLANE + 6 * LAYER); // the text in front of every shape
		Matrix4f tm = pose.last().pose();
		for (Label l : sheet.labels) {
			font.drawInBatch(l.text, l.x, l.y, l.color, false, tm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LIGHT);
		}
		buffers.endBatch();
	}

	private static void rect(VertexConsumer vc, Matrix4f m, float x1, float y1, float x2, float y2, float z, int color) {
		vc.addVertex(m, x1, y1, z).setColor(color);
		vc.addVertex(m, x2, y1, z).setColor(color);
		vc.addVertex(m, x2, y2, z).setColor(color);
		vc.addVertex(m, x1, y2, z).setColor(color);
	}

	// ---------------------------------------------------------------- the page

	/** Lays the results out in {@code width x DESIGN_H} board pixels, with only what the reveal has shown so far. */
	private static Sheet layout(Font font, View view, Results results, int width) {
		Sheet s = new Sheet();
		long elapsed = ResultsReveal.elapsed(view);
		int shown = ResultsReveal.doorsShown(elapsed, results.votes().size());
		boolean points = ResultsReveal.pointsShown(elapsed, results.votes().size());
		Component title = Component.translatable(T + (results.finalLoop() ? "results.final" : "results.round"), results.round()).copy()
				.withStyle(st -> st.withBold(true));
		s.text(title, PAD, PAD - 2, TrahisonPalette.TEXT);
		s.rect(PAD, PAD + 10, width - PAD, PAD + 11, 1, TrahisonPalette.ACCENT);
		float y = PAD + 16;
		int index = 0;
		for (VoteResult vr : results.votes()) {
			boolean open = index < shown;
			s.rect(PAD - 2, y - 1, width - PAD + 2, y + DOOR_H - 3, 1, TrahisonPalette.PANEL);
			side(s, font, view, vr.pairColor(), names(view, vr.pair()), open ? vr.pairVote() : null, vr.pairCast(), width, y);
			side(s, font, view, vr.soloColor(), view.nameOf(vr.solo()), open ? vr.soloVote() : null, vr.soloCast(), width, y + LINE);
			y += DOOR_H;
			index++;
		}
		y += 2;
		if (points) {
			s.text(Component.translatable(T + "board.points"), PAD, y, TrahisonPalette.TEXT_MUTED);
			y += LINE;
			int cols = width >= 300 ? 3 : 2;
			int colW = (width - 2 * PAD) / cols;
			for (int p = 0; p < view.players().size() && p < results.deltas().size(); p++) {
				int delta = results.deltas().get(p);
				float cx = PAD + (p % cols) * colW;
				float cy = y + (p / cols) * LINE;
				String chip = delta > 0 ? "+" + delta : Integer.toString(delta);
				int color = delta > 0 ? TrahisonPalette.SUCCESS : delta < 0 ? TrahisonPalette.DANGER : TrahisonPalette.TEXT_MUTED;
				s.text(Component.literal(fit(font, view.nameOf(p), colW - 8 - font.width(chip) - 4)), cx, cy, TrahisonPalette.TEXT_SECONDARY);
				s.text(Component.literal(chip), cx + colW - 8 - font.width(chip), cy, color);
			}
			y += ((view.players().size() + cols - 1) / cols) * LINE + 2;
			if (!results.newlyDead().isEmpty()) {
				String names = String.join(", ", results.newlyDead().stream().map(view::nameOf).toList());
				s.text(Component.literal(fit(font, Component.translatable(T + "results.dead", names).getString(), width - 2 * PAD)), PAD, y, TrahisonPalette.GHOST);
			}
		} else {
			s.text(Component.translatable(T + "board.waiting"), PAD, y, TrahisonPalette.TEXT_MUTED);
		}
		return s;
	}

	/** One line: the colour's symbol, the names, and on the right the vote's pill (a hidden "?" before the reveal). */
	private static void side(Sheet s, Font font, View view, GameColor color, String names, Vote vote, boolean cast, int width, float y) {
		float right = width - PAD;
		float pillW;
		if (vote != null) {
			Component name = Component.translatable(T + "vote." + vote.name().toLowerCase(Locale.ROOT));
			int tone = vote == Vote.ALLY ? TrahisonPalette.ALLY : TrahisonPalette.BETRAY;
			int dark = vote == Vote.ALLY ? TrahisonPalette.ALLY_DARK : TrahisonPalette.BETRAY_DARK;
			Glyph glyph = vote == Vote.ALLY ? Glyph.ALLY : Glyph.BETRAY;
			pillW = s.pill(font, glyph, name, right, y, cast ? dark : TrahisonPalette.PANEL_SUNKEN, cast ? tone : TrahisonPalette.TEXT_MUTED,
					cast ? TrahisonPalette.TEXT : TrahisonPalette.TEXT_MUTED);
		} else {
			Component hidden = Component.literal("?");
			pillW = font.width(hidden) + 20;
			s.rect(right - pillW, y, right, y + 12, 2, TrahisonPalette.BORDER);
			s.rect(right - pillW + 1, y + 1, right - 1, y + 11, 3, TrahisonPalette.PANEL_SUNKEN);
			s.text(hidden, right - pillW / 2f - font.width(hidden) / 2f, y + 2, TrahisonPalette.TEXT_MUTED);
		}
		float x = PAD + 2;
		if (color != null) {
			s.glyph(color.glyph, x, y + 2, 8, 4, color.light);
			x += 12;
		}
		s.text(Component.literal(fit(font, names, (int) (right - pillW - 6 - x))), x, y + 2, TrahisonPalette.TEXT);
	}

	private static String names(View view, List<Integer> players) {
		return String.join(" + ", players.stream().map(view::nameOf).toList());
	}

	/** The text cut with "..." so it fits {@code max} board pixels. */
	private static String fit(Font font, String text, int max) {
		if (font.width(text) <= max) {
			return text;
		}
		int end = text.length();
		while (end > 0 && font.width(text.substring(0, end) + "...") > max) {
			end--;
		}
		return text.substring(0, end) + "...";
	}
}
