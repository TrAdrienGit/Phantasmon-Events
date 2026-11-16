package com.mystaria.events.trahison.minigame;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.mojang.math.Axis;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Twenty abstract marks of our own (occult / technical, no borrowed art), drawn in code with rectangles only, so they
 * take any palette colour and turn without a texture. Mechanisms that show symbols (ring dial, symbol locks, wall clues
 * of type {@code symbols}) call {@link #draw}; the ids are the ones of the backend's {@code SymbolSet}, and the two
 * lists must stay equal (a symbol clue is an array of these ids).
 *
 * <p>
 * Every glyph is designed on a 32 x 32 grid centred on the origin (x to the right, y down, all strokes inside the
 * radius 14) and scaled to the requested size. Below 9 pixels the strokes get thinner than a screen pixel and some
 * marks start to vanish: do not draw them smaller. A glyph is drawn in a single colour so that the caller decides what
 * is lit (the symbol on the marked radius, a solved clue...).
 */
public final class Glyphs {

	/** The ids, in the order of the backend's {@code SymbolSet.IDS}. */
	public static final List<String> IDS = List.of(
			"eye", "spiral", "trident", "crescent", "hourglass", "key", "dot_triangle", "cross_circle", "wave", "fork",
			"star", "diamond", "gear", "bolt", "hexagon", "loop_arrow", "ladder", "trio", "arch", "chevrons");

	/** Stroke width on the 32-unit grid: about one and a half pixels at size 12. */
	private static final float STROKE = 3.4f;

	private static final Map<String, Consumer<Pen>> SHAPES = Map.ofEntries(
			Map.entry("eye", p -> {
				p.outline(-14, 0, 0, -9, 14, 0, 0, 9);
				p.disc(0, 0, 4.2f);
			}),
			Map.entry("spiral", p -> {
				float px = 0;
				float py = 0;
				for (int i = 0; i <= 28; i++) {
					double t = i / 28.0 * Math.PI * 4.2;
					float r = 1.5f + (float) t * 1.05f;
					float x = (float) (r * Math.sin(t));
					float y = (float) (-r * Math.cos(t));
					if (i > 0) {
						p.line(px, py, x, y, STROKE);
					}
					px = x;
					py = y;
				}
			}),
			Map.entry("trident", p -> {
				p.line(0, -14, 0, 14, STROKE);
				p.line(-9, -13, -9, -2, STROKE);
				p.line(9, -13, 9, -2, STROKE);
				p.line(-9, -2, 0, 5, STROKE);
				p.line(9, -2, 0, 5, STROKE);
			}),
			Map.entry("crescent", p -> {
				// An outer disc minus a smaller one pushed to the right, row by row: a moon opening to the right.
				for (float y = -13; y < 13; y += 0.5f) {
					float d = y + 0.25f;
					float ho = (float) Math.sqrt(Math.max(0, 13 * 13 - d * d));
					float left = -1 - ho;
					float right = -1 + ho;
					float yi = d + 1;
					float hi2 = 10 * 10 - yi * yi;
					if (hi2 > 0) {
						float hi = (float) Math.sqrt(hi2);
						if (4 - hi > left) {
							p.rect(left, y, Math.min(right, 4 - hi), y + 0.5f);
						}
						if (4 + hi < right) {
							p.rect(4 + hi, y, right, y + 0.5f);
						}
					} else {
						p.rect(left, y, right, y + 0.5f);
					}
				}
			}),
			Map.entry("hourglass", p -> {
				p.line(-10, -12, 10, -12, STROKE);
				p.line(-10, 12, 10, 12, STROKE);
				p.line(-8, -12, 8, 12, STROKE);
				p.line(8, -12, -8, 12, STROKE);
				p.fill(-6, 11, 6, 11, 0, 5);
			}),
			Map.entry("key", p -> {
				p.ring(0, -7, 7, 3.6f);
				p.line(0, 0, 0, 14, STROKE);
				p.line(0, 8, 6, 8, STROKE);
				p.line(0, 13, 5, 13, STROKE);
			}),
			Map.entry("dot_triangle", p -> {
				p.line(0, -13, 13, 11, STROKE);
				p.line(13, 11, -13, 11, STROKE);
				p.line(-13, 11, 0, -13, STROKE);
				p.disc(0, 3, 3.2f);
			}),
			Map.entry("cross_circle", p -> {
				p.ring(0, 0, 13, 9.6f);
				p.line(-8, 0, 8, 0, STROKE);
				p.line(0, -8, 0, 8, STROKE);
			}),
			Map.entry("wave", p -> {
				for (int band = 0; band < 2; band++) {
					float oy = band == 0 ? -6 : 6;
					float px = -14;
					float py = oy;
					for (int i = 1; i <= 14; i++) {
						float x = -14 + i * 2f;
						float y = oy + (float) Math.sin(i / 14.0 * Math.PI * 3) * 3.4f;
						p.line(px, py, x, y, STROKE);
						px = x;
						py = y;
					}
				}
			}),
			Map.entry("fork", p -> {
				p.line(0, 14, 0, 0, STROKE);
				p.line(0, 0, -10, -11, STROKE);
				p.line(0, 0, 10, -11, STROKE);
				p.disc(-10, -12, 2.6f);
				p.disc(10, -12, 2.6f);
			}),
			Map.entry("star", p -> {
				for (int i = 0; i < 5; i++) {
					double a = Math.toRadians(i * 72);
					double b = Math.toRadians(((i + 2) % 5) * 72);
					p.line((float) (14 * Math.sin(a)), (float) (-14 * Math.cos(a)), (float) (14 * Math.sin(b)),
							(float) (-14 * Math.cos(b)), STROKE * 0.85f);
				}
			}),
			Map.entry("diamond", p -> p.fill(0, -14, 10, 0, 0, 14, -10, 0)),
			Map.entry("gear", p -> {
				p.ring(0, 0, 9, 4.6f);
				for (int i = 0; i < 8; i++) {
					p.spoke(i * 45, 8, 14, 5f);
				}
			}),
			Map.entry("bolt", p -> {
				p.line(5, -14, -6, 1, STROKE * 1.2f);
				p.line(-6, 1, 5, 1, STROKE * 1.2f);
				p.line(5, 1, -5, 14, STROKE * 1.2f);
			}),
			Map.entry("hexagon", p -> {
				p.hexagon(13, STROKE);
				p.hexagon(6, STROKE * 0.8f);
			}),
			Map.entry("loop_arrow", p -> {
				p.arc(0, 0, 9, 30, 320, STROKE);
				// Arrow head at the end of the arc, pointing along the clockwise tangent.
				double a = Math.toRadians(320);
				float ex = (float) (9 * Math.sin(a));
				float ey = (float) (-9 * Math.cos(a));
				float tx = (float) Math.cos(a);
				float ty = (float) Math.sin(a);
				float nx = (float) Math.sin(a);
				float ny = (float) -Math.cos(a);
				p.fill(ex + tx * 7, ey + ty * 7, ex + nx * 5, ey + ny * 5, ex - nx * 5, ey - ny * 5);
			}),
			Map.entry("ladder", p -> {
				p.line(-6, -14, -6, 14, STROKE);
				p.line(6, -14, 6, 14, STROKE);
				p.line(-6, -8, 6, -8, STROKE);
				p.line(-6, 0, 6, 0, STROKE);
				p.line(-6, 8, 6, 8, STROKE);
			}),
			Map.entry("trio", p -> {
				p.disc(0, -8, 4.4f);
				p.disc(-9, 7, 4.4f);
				p.disc(9, 7, 4.4f);
			}),
			Map.entry("arch", p -> {
				p.line(-9, 14, -9, -1, STROKE);
				p.line(9, 14, 9, -1, STROKE);
				p.arc(0, -1, 9, -90, 90, STROKE);
				p.line(-14, 14, 14, 14, STROKE);
			}),
			Map.entry("chevrons", p -> {
				for (int i = 0; i < 3; i++) {
					float y = -10 + i * 7;
					p.line(-10, y, 0, y + 6, STROKE);
					p.line(0, y + 6, 10, y, STROKE);
				}
			}));

	private Glyphs() {
	}

	public static boolean has(String id) {
		return SHAPES.containsKey(id);
	}

	/**
	 * Draws glyph {@code id} centred on {@code cx, cy} in a box of {@code size} pixels (9 or more), turned clockwise by
	 * {@code degrees} (0 = the glyph's top at the top). An unknown id draws a hollow box with a dot, so a wrong id in a
	 * room definition is visible rather than silent.
	 */
	public static void draw(GuiGraphics g, String id, float cx, float cy, int size, float degrees, int color) {
		Consumer<Pen> shape = SHAPES.get(id);
		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		g.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
		float s = size / 32f;
		g.pose().scale(s, s, 1);
		Pen pen = new Pen(g, color);
		if (shape != null) {
			shape.accept(pen);
		} else {
			pen.line(-12, -12, 12, -12, STROKE);
			pen.line(12, -12, 12, 12, STROKE);
			pen.line(12, 12, -12, 12, STROKE);
			pen.line(-12, 12, -12, -12, STROKE);
			pen.disc(0, 0, 3);
		}
		g.pose().popPose();
	}

	/** The brush: every shape is built from filled rectangles, in the grid's units. */
	private static final class Pen {

		private final GuiGraphics g;
		private final int color;

		Pen(GuiGraphics g, int color) {
			this.g = g;
			this.color = color;
		}

		void rect(float x1, float y1, float x2, float y2) {
			// Sub-unit precision: the pose is scaled up by 4 around the integer fill.
			g.pose().pushPose();
			g.pose().scale(0.25f, 0.25f, 1);
			g.fill(Math.round(x1 * 4), Math.round(y1 * 4), Math.round(x2 * 4), Math.round(y2 * 4), color);
			g.pose().popPose();
		}

		/** A stroke of width {@code t} from one point to another (a rotated rectangle with square ends). */
		void line(float x1, float y1, float x2, float y2, float t) {
			float dx = x2 - x1;
			float dy = y2 - y1;
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			if (len < 0.01f) {
				return;
			}
			g.pose().pushPose();
			g.pose().translate((x1 + x2) / 2, (y1 + y2) / 2, 0);
			g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
			rect(-len / 2 - t / 2 * 0.5f, -t / 2, len / 2 + t / 2 * 0.5f, t / 2);
			g.pose().popPose();
		}

		void disc(float cx, float cy, float r) {
			ring(cx, cy, r, 0);
		}

		/** A ring (or a disc when {@code inner} is 0) one row of 0.5 units at a time. */
		void ring(float cx, float cy, float outer, float inner) {
			for (float y = -outer; y < outer; y += 0.5f) {
				float d = y + 0.25f;
				float ho = (float) Math.sqrt(Math.max(0, outer * outer - d * d));
				if (Math.abs(d) >= inner) {
					rect(cx - ho, cy + y, cx + ho, cy + y + 0.5f);
				} else {
					float hi = (float) Math.sqrt(inner * inner - d * d);
					rect(cx - ho, cy + y, cx - hi, cy + y + 0.5f);
					rect(cx + hi, cy + y, cx + ho, cy + y + 0.5f);
				}
			}
		}

		/** An arc of a circle from {@code from} to {@code to} degrees (0 = up, clockwise), as short strokes. */
		void arc(float cx, float cy, float r, float from, float to, float t) {
			int steps = Math.max(6, (int) (Math.abs(to - from) / 12));
			float px = 0;
			float py = 0;
			for (int i = 0; i <= steps; i++) {
				double a = Math.toRadians(from + (to - from) * i / steps);
				float x = cx + (float) (r * Math.sin(a));
				float y = cy - (float) (r * Math.cos(a));
				if (i > 0) {
					line(px, py, x, y, t);
				}
				px = x;
				py = y;
			}
		}

		/** A stroke along the direction {@code degrees} (0 = up, clockwise) between two radii. */
		void spoke(float degrees, float r1, float r2, float t) {
			double a = Math.toRadians(degrees);
			line((float) (r1 * Math.sin(a)), (float) (-r1 * Math.cos(a)), (float) (r2 * Math.sin(a)),
					(float) (-r2 * Math.cos(a)), t);
		}

		/** A regular hexagon outline of circumradius {@code r}, pointing up. */
		void hexagon(float r, float t) {
			for (int i = 0; i < 6; i++) {
				double a = Math.toRadians(i * 60);
				double b = Math.toRadians((i + 1) * 60);
				line((float) (r * Math.sin(a)), (float) (-r * Math.cos(a)), (float) (r * Math.sin(b)),
						(float) (-r * Math.cos(b)), t);
			}
		}

		/** A closed polygon outline through the points {@code x0, y0, x1, y1...}. */
		void outline(float... c) {
			int n = c.length / 2;
			for (int i = 0; i < n; i++) {
				int j = (i + 1) % n;
				line(c[2 * i], c[2 * i + 1], c[2 * j], c[2 * j + 1], STROKE);
			}
		}

		/** A filled convex polygon through the points {@code x0, y0, x1, y1...}, one row of 0.5 units at a time. */
		void fill(float... c) {
			int n = c.length / 2;
			float top = Float.MAX_VALUE;
			float bottom = -Float.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				top = Math.min(top, c[2 * i + 1]);
				bottom = Math.max(bottom, c[2 * i + 1]);
			}
			for (float y = top; y < bottom; y += 0.5f) {
				float yc = y + 0.25f;
				float xMin = Float.MAX_VALUE;
				float xMax = -Float.MAX_VALUE;
				for (int i = 0; i < n; i++) {
					int j = (i + 1) % n;
					float y1 = c[2 * i + 1];
					float y2 = c[2 * j + 1];
					if ((y1 <= yc && yc < y2) || (y2 <= yc && yc < y1)) {
						float x = c[2 * i] + (yc - y1) / (y2 - y1) * (c[2 * j] - c[2 * i]);
						xMin = Math.min(xMin, x);
						xMax = Math.max(xMax, x);
					}
				}
				if (xMax > xMin) {
					rect(xMin, y, xMax, y + 0.5f);
				}
			}
		}
	}
}
