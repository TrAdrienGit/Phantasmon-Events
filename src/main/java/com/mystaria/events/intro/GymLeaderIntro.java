package com.mystaria.events.intro;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import com.mystaria.showtime.api.CustomIntro;
import com.mystaria.showtime.api.IntroContext;

/**
 * The gym leader's intro (TODO-37, 2026-11-11), reserved: Phantasmon gives it to a gym leader as a reward, and when
 * they pick it in the battle lobby both players watch it, starring the gym leader. Their badge rises out of the dark
 * in a fan of golden light, the leader stands up against it as a silhouette, then lights up under their title and
 * name; the challenger's side cuts in from below, a "VS" stamps between them and both teams are counted out.
 * Everything is drawn here, on Showtime's intro screen; Showtime adds the closing white flash.
 */
public final class GymLeaderIntro implements CustomIntro {

	public static final String ID = "phantasmon_events:gym_leader";
	private static final long LENGTH = 8200;

	// ---- Timeline (ms, the intro's own clock) ----
	private static final long CLOSE_END = 450;
	private static final long BADGE = 700;
	private static final long RISE = 1300;
	private static final long REVEAL = 2600;
	private static final long PLATE = 2700;
	private static final long LINE = 3300;
	private static final long CHALLENGER = 5000;
	private static final long VS = 5400;
	private static final long BALLS = 6300;
	private static final long FLARE = 7300;

	private static final int GOLD = 0xFFFFC94A;
	private static final int GOLD_DEEP = 0xFFD8962A;
	private static final int BRONZE = 0xFF4A2E08;
	private static final int CREAM = 0xFFF3E6C4;
	private static final int CRIMSON = 0xFF9E1F2A;

	private record Cue(long at, SoundEvent sound, float pitch, float volume) {
	}

	private static final Cue[] CUES = {
			new Cue(0, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 0.7f),
			new Cue(BADGE, SoundEvents.BEACON_ACTIVATE, 0.8f, 0.8f),
			new Cue(BADGE + 800, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1f),
			new Cue(REVEAL, SoundEvents.FIREWORK_ROCKET_BLAST, 0.8f, 0.9f),
			new Cue(REVEAL, SoundEvents.PLAYER_LEVELUP, 0.7f, 0.45f),
			new Cue(PLATE + 50, SoundEvents.PLAYER_ATTACK_SWEEP, 1.1f, 0.6f),
			new Cue(CHALLENGER, SoundEvents.ELYTRA_FLYING, 1.6f, 0.35f),
			new Cue(VS, SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 1f),
			new Cue(VS, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.9f, 0.7f),
			new Cue(BALLS, SoundEvents.ITEM_PICKUP, 0.8f, 0.6f),
			new Cue(BALLS + 110, SoundEvents.ITEM_PICKUP, 0.9f, 0.6f),
			new Cue(BALLS + 220, SoundEvents.ITEM_PICKUP, 1.0f, 0.6f),
			new Cue(BALLS + 330, SoundEvents.ITEM_PICKUP, 1.1f, 0.6f),
			new Cue(BALLS + 440, SoundEvents.ITEM_PICKUP, 1.2f, 0.6f),
			new Cue(BALLS + 550, SoundEvents.ITEM_PICKUP, 1.3f, 0.6f),
			new Cue(FLARE + 100, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.7f),
	};

	private final boolean[] played = new boolean[CUES.length];
	private long lastTick = -1;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component name() {
		return Component.translatable("phantasmon_events.intro.gym_leader");
	}

	@Override
	public long length() {
		return LENGTH;
	}

	@Override
	public boolean reserved() {
		return true;
	}

	@Override
	public void tick(IntroContext context, long t) {
		if (t < lastTick) {
			java.util.Arrays.fill(played, false); // a new play (the instance is shared)
		}
		lastTick = t;
		for (int i = 0; i < CUES.length; i++) {
			if (!played[i] && t >= CUES[i].at()) {
				played[i] = true;
				context.sound(CUES[i].sound(), CUES[i].pitch(), CUES[i].volume());
			}
		}
	}

	@Override
	public void render(GuiGraphics g, IntroContext c, long t, float partialTick) {
		int w = c.width();
		int h = c.height();
		float u = h / 270f;
		if (t < CLOSE_END) {
			closing(g, w, h, ease(t / (float) CLOSE_END));
			return;
		}
		boolean starIsSelf = c.starIsSelf();
		float reveal = clamp((t - REVEAL) / 320f);
		float flare = ease(clamp((t - FLARE) / 600f));
		// A slow push in all along, a faster one as the badge flares at the end.
		float push = 1f + 0.035f * smooth(clamp((t - REVEAL) / 4700f)) + 0.07f * flare;
		g.pose().pushPose();
		g.pose().translate(w / 2f, h / 2f, 0);
		g.pose().scale(push, push, 1f);
		g.pose().translate(-w / 2f, -h / 2f, 0);

		// The dark hall, a gold glow on the floor line.
		g.fillGradient(-w, -h, 2 * w, 2 * h, 0xFF121A33, 0xFF03050C);
		g.fillGradient(-w, Math.round(h * 0.70f), 2 * w, Math.round(h * 0.80f), 0x00FFC94A, alpha(GOLD, 0.10f + 0.10f * reveal));
		g.fillGradient(-w, Math.round(h * 0.80f), 2 * w, Math.round(h * 0.90f), alpha(GOLD, 0.10f + 0.10f * reveal), 0x00FFC94A);

		float cx = w * 0.66f;
		float cy = h * 0.40f;
		float raysIn = clamp((t - CLOSE_END) / 650f);
		rays(g, cx, cy, 22, w * 1.1f, t * 0.00011f, 0.20f * raysIn + 0.12f * reveal + 0.35f * flare);
		if (t >= BADGE) {
			float k = easeOutBack(clamp((t - BADGE) / 800f));
			badge(g, cx, cy, 72 * u * k * (1f + 0.06f * flare), (1f - clamp((t - BADGE) / 800f)) * -2.6f + Mth.PI / 8f,
					0.55f + 0.45f * reveal + 0.3f * flare);
		}
		sparks(g, w, h, u, t, clamp((t - REVEAL) / 600f));
		g.flush();

		// The gym leader: rises as a silhouette against the badge, then lights up, turning to face the challenger.
		if (t >= RISE) {
			float rise = 1f - ease(clamp((t - RISE) / 1100f));
			// From three-quarters to almost facing us, turned a little towards the challenger on the left.
			float yaw = Mth.lerp(ease(reveal), 235f, 200f);
			c.drawTrainer(g, !starIsSelf, cx, h * 0.97f + rise * h * 0.45f, h * 0.30f, yaw, Mth.lerp(reveal, 0.04f, 1f));
		}

		// The challenger's band cuts in from the lower left.
		float band = ease(clamp((t - CHALLENGER) / 500f));
		if (band > 0) {
			challengerBand(g, w, h, u, band);
			float lit = clamp((t - CHALLENGER - 300) / 400f);
			c.drawTrainer(g, starIsSelf, w * 0.13f - (1f - band) * w * 0.4f, h * 1.02f, h * 0.20f, 150f, Mth.lerp(lit, 0.08f, 1f));
		}

		front(g, 400);
		// The reveal: a white burst over the leader.
		if (t >= REVEAL && t < REVEAL + 360) {
			float k = (t - REVEAL) / 360f;
			g.fill(-w, -h, 2 * w, 2 * h, alpha(0xFFFFFFFF, 0.55f * (1f - k) * (1f - k)));
		}
		String starName = starIsSelf ? c.selfName() : c.opponentName();
		String challengerName = starIsSelf ? c.opponentName() : c.selfName();
		float nameBottom = plate(g, w, h, u, t, starName);
		if (t >= BALLS) {
			int team = starIsSelf ? c.ownTeamSize() : c.opponentTeamSize();
			balls(g, c, w * 0.06f + 6 * u, nameBottom + 14 * u, u, t, team);
		}
		if (band > 0) {
			float in = ease(clamp((t - CHALLENGER - 150) / 450f));
			float x = w * 0.27f - (1f - in) * w * 0.3f;
			float y = h * 0.70f;
			text(g, Component.translatable("phantasmon_events.intro.gym_leader.challenger").getString(), x, y, u * 0.9f,
					alpha(CREAM, in), false);
			text(g, challengerName, x, y + 10 * u, u * 1.7f, alpha(0xFFFFFFFF, in), true);
			if (t >= BALLS) {
				int team = starIsSelf ? c.opponentTeamSize() : c.ownTeamSize();
				balls(g, c, x + 6 * u, y + 34 * u, u, t, team);
			}
		}
		if (t >= VS) {
			versus(g, w, h, u, t);
		}
		g.pose().popPose();
		g.pose().popPose();
	}

	// ---------------------------------------------------------------- pieces

	/** The world disappearing between two black bars meeting in the middle, gold-edged. */
	private static void closing(GuiGraphics g, int w, int h, float k) {
		int bar = Math.round(h / 2f * k);
		g.fill(0, 0, w, bar, 0xFF000000);
		g.fill(0, h - bar, w, h, 0xFF000000);
		g.fill(0, bar - 1, w, bar + 1, GOLD);
		g.fill(0, h - bar - 1, w, h - bar + 1, GOLD);
	}

	/** Fans of light from the badge: wedges fading outward, turning slowly. */
	private static void rays(GuiGraphics g, float cx, float cy, int count, float length, float spin, float strength) {
		if (strength <= 0) {
			return;
		}
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		for (int i = 0; i < count; i++) {
			float angle = hash(i * 7 + 3) * Mth.TWO_PI + spin * (i % 2 == 0 ? 1 : -0.6f);
			float half = 0.02f + 0.05f * hash(i * 7 + 4);
			float reach = length * (0.6f + 0.4f * hash(i * 7 + 5));
			int inner = alpha(GOLD, strength * (0.5f + 0.5f * hash(i * 7 + 6)));
			int outer = 0x00FFC94A;
			vertex(buffer, m, cx, cy, inner);
			vertex(buffer, m, cx + Mth.cos(angle - half) * reach, cy + Mth.sin(angle - half) * reach, outer);
			vertex(buffer, m, cx + Mth.cos(angle + half) * reach, cy + Mth.sin(angle + half) * reach, outer);
			vertex(buffer, m, cx + Mth.cos(angle + half) * reach, cy + Mth.sin(angle + half) * reach, outer);
		}
	}

	/**
	 * A gym badge: a soft glow, an octagonal gold rim round a bronze ring, a gold face with a bevel, and a four-pointed
	 * star at its heart.
	 */
	private static void badge(GuiGraphics g, float cx, float cy, float r, float turn, float light) {
		if (r <= 0.5f) {
			return;
		}
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		for (int i = 6; i >= 1; i--) {
			float k = 1f + i * 0.14f;
			ring(buffer, m, cx, cy, r * k, r * (k - 0.14f), 32, 0f, alpha(GOLD, 0.05f * light * (7 - i) / 6f));
		}
		ring(buffer, m, cx, cy, r, r * 0.86f, 8, turn, GOLD);
		ring(buffer, m, cx, cy, r * 0.86f, r * 0.78f, 8, turn, BRONZE);
		polygon(buffer, m, cx, cy, r * 0.78f, 8, turn, GOLD_DEEP, GOLD);
		ring(buffer, m, cx, cy, r * 0.70f, r * 0.67f, 8, turn, alpha(0xFF7A4E10, 0.8f));
		for (int arm = 0; arm < 4; arm++) {
			float a = turn + arm * Mth.HALF_PI;
			float tipX = cx + Mth.cos(a) * r * 0.58f;
			float tipY = cy + Mth.sin(a) * r * 0.58f;
			float sideA = a + Mth.HALF_PI;
			float wide = r * 0.11f;
			int core = alpha(0xFFFFF6DA, Math.min(1f, 0.75f + 0.25f * light));
			vertex(buffer, m, cx, cy, core);
			vertex(buffer, m, cx + Mth.cos(sideA) * wide, cy + Mth.sin(sideA) * wide, GOLD);
			vertex(buffer, m, tipX, tipY, core);
			vertex(buffer, m, cx - Mth.cos(sideA) * wide, cy - Mth.sin(sideA) * wide, GOLD);
		}
	}

	/** Gold sparks drifting up through the hall. */
	private static void sparks(GuiGraphics g, int w, int h, float u, long t, float strength) {
		if (strength <= 0) {
			return;
		}
		for (int i = 0; i < 46; i++) {
			float speed = 0.02f + 0.05f * hash(i * 3 + 1);
			float x = hash(i * 3 + 2) * w + Mth.sin(t * 0.002f + i) * 6 * u;
			float travel = (t * speed + hash(i * 3 + 3) * h * 1.3f) % (h * 1.3f);
			float y = h * 1.1f - travel;
			int size = Math.max(1, Math.round((1 + hash(i * 3 + 4) * 1.5f) * u));
			float fade = strength * clamp(1f - travel / (h * 1.3f)) * (0.5f + 0.5f * Mth.sin(t * 0.01f + i * 1.7f));
			g.fill(Math.round(x), Math.round(y), Math.round(x) + size, Math.round(y) + size, alpha(i % 4 == 0 ? CREAM : GOLD, fade));
		}
	}

	/** The challenger's side: a crimson band rising from the lower left, gold-edged. */
	private static void challengerBand(GuiGraphics g, int w, int h, float u, float in) {
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		float right = w * 0.56f * in;
		vertex(buffer, m, -w * 0.1f, h * 0.66f, alpha(CRIMSON, 0.92f));
		vertex(buffer, m, -w * 0.1f, h * 1.1f, alpha(0xFF2A0508, 0.95f));
		vertex(buffer, m, right - w * 0.08f, h * 1.1f, alpha(0xFF2A0508, 0.95f));
		vertex(buffer, m, right, h * 0.60f, alpha(CRIMSON, 0.92f));
		float edge = 2 * u;
		vertex(buffer, m, -w * 0.1f, h * 0.66f - edge, GOLD);
		vertex(buffer, m, -w * 0.1f, h * 0.66f, GOLD);
		vertex(buffer, m, right, h * 0.60f, GOLD);
		vertex(buffer, m, right, h * 0.60f - edge, GOLD);
		g.flush();
	}

	/**
	 * The leader's plate, sliding in from the left: their title in gold small capitals, their name large, a gold
	 * underline growing, then their line typed below. Returns the y under the name, for the team's balls.
	 */
	private float plate(GuiGraphics g, int w, int h, float u, long t, String name) {
		float x0 = w * 0.06f;
		float y0 = h * 0.22f;
		float nameScale = 2.6f * u;
		Font font = Minecraft.getInstance().font;
		float nameBottom = y0 + 14 * u + 9 * nameScale;
		if (t < PLATE) {
			return nameBottom;
		}
		float in = ease(clamp((t - PLATE) / 450f));
		float x = x0 - (1f - in) * w * 0.5f;
		float nameWidth = font.width(name) * nameScale;
		g.fill(Math.round(x - 6 * u), Math.round(y0 - 6 * u), Math.round(x + Math.max(nameWidth, 120 * u) + 10 * u),
				Math.round(nameBottom + 6 * u), alpha(0xFF05070F, 0.55f * in));
		g.fill(Math.round(x - 6 * u), Math.round(y0 - 6 * u), Math.round(x - 3 * u), Math.round(nameBottom + 6 * u), GOLD);
		text(g, Component.translatable("phantasmon_events.intro.gym_leader.title").getString(), x, y0, 1.1f * u, GOLD, true);
		text(g, name, x, y0 + 14 * u, nameScale, 0xFFFFFFFF, true);
		float line = ease(clamp((t - PLATE - 250) / 500f));
		g.fill(Math.round(x), Math.round(nameBottom + 2 * u), Math.round(x + nameWidth * line), Math.round(nameBottom + 4 * u), GOLD);
		if (t >= LINE) {
			String quote = Component.translatable("phantasmon_events.intro.gym_leader.line").getString();
			int shown = Math.min(quote.length(), (int) ((t - LINE) / 32));
			text(g, quote.substring(0, shown), x, nameBottom + 10 * u + (t >= BALLS ? 16 * u : 0), 1.05f * u, CREAM, true);
		}
		return nameBottom;
	}

	/** A team counted out: one Poké Ball after the other, popping in. Unknown size (-1): none. */
	private static void balls(GuiGraphics g, IntroContext c, float x, float y, float u, long t, int count) {
		for (int i = 0; i < Math.min(6, count); i++) {
			float pop = clamp((t - BALLS - i * 110) / 180f);
			if (pop <= 0) {
				continue;
			}
			float size = 11 * u * easeOutBack(pop);
			c.drawBall(g, x + i * 14 * u, y, size, (1f - pop) * 180f);
		}
	}

	/** "VS", stamped from very large, shaking the screen a moment. */
	private void versus(GuiGraphics g, int w, int h, float u, long t) {
		float k = clamp((t - VS) / 220f);
		float scale = u * Mth.lerp(ease(k), 7f, 3.2f);
		float shake = t - VS < 420 ? Mth.sin((t - VS) * 0.12f) * 3 * u * (1f - (t - VS) / 420f) : 0;
		Font font = Minecraft.getInstance().font;
		Component vs = Component.literal("VS").withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC);
		float cx = w * 0.47f + shake;
		float cy = h * 0.58f;
		float textW = font.width(vs) * scale;
		g.pose().pushPose();
		g.pose().translate(cx - textW / 2f, cy - 4.5f * scale, 0);
		g.pose().scale(scale, scale, 1f);
		int a = Math.round(255 * Math.min(1f, k * 2f));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					g.drawString(font, vs, dx, dy, (a << 24) | 0x1A0A00, false);
				}
			}
		}
		g.drawString(font, vs, 0, 0, (a << 24) | (GOLD & 0xFFFFFF), false);
		g.pose().popPose();
	}

	// ---------------------------------------------------------------- helpers

	private static void front(GuiGraphics g, float z) {
		g.flush();
		g.pose().pushPose();
		g.pose().translate(0, 0, z);
	}

	private static void text(GuiGraphics g, String text, float x, float y, float scale, int color, boolean shadow) {
		g.pose().pushPose();
		g.pose().translate(x, y, 0);
		g.pose().scale(scale, scale, 1f);
		g.drawString(Minecraft.getInstance().font, text, 0, 0, color, shadow);
		g.pose().popPose();
	}

	/** A ring of {@code sides} straight sides between two radii, as quads. */
	private static void ring(VertexConsumer buffer, Matrix4f m, float cx, float cy, float outer, float inner, int sides, float turn, int color) {
		for (int i = 0; i < sides; i++) {
			float a = turn + i * Mth.TWO_PI / sides;
			float b = turn + (i + 1) * Mth.TWO_PI / sides;
			vertex(buffer, m, cx + Mth.cos(a) * outer, cy + Mth.sin(a) * outer, color);
			vertex(buffer, m, cx + Mth.cos(a) * inner, cy + Mth.sin(a) * inner, color);
			vertex(buffer, m, cx + Mth.cos(b) * inner, cy + Mth.sin(b) * inner, color);
			vertex(buffer, m, cx + Mth.cos(b) * outer, cy + Mth.sin(b) * outer, color);
		}
	}

	/** A filled polygon, {@code centre} colour in the middle fading to {@code edge}: a bevelled face. */
	private static void polygon(VertexConsumer buffer, Matrix4f m, float cx, float cy, float r, int sides, float turn, int edge, int centre) {
		for (int i = 0; i < sides; i++) {
			float a = turn + i * Mth.TWO_PI / sides;
			float b = turn + (i + 1) * Mth.TWO_PI / sides;
			vertex(buffer, m, cx, cy, centre);
			vertex(buffer, m, cx + Mth.cos(a) * r, cy + Mth.sin(a) * r, edge);
			vertex(buffer, m, cx + Mth.cos(b) * r, cy + Mth.sin(b) * r, edge);
			vertex(buffer, m, cx + Mth.cos(b) * r, cy + Mth.sin(b) * r, edge);
		}
	}

	private static void vertex(VertexConsumer buffer, Matrix4f m, float x, float y, int argb) {
		buffer.addVertex(m, x, y, 0f).setColor(argb);
	}

	private static int alpha(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * clamp(alpha));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static float clamp(float value) {
		return Math.max(0f, Math.min(1f, value));
	}

	private static float ease(float k) {
		float x = clamp(k);
		return 1f - (1f - x) * (1f - x) * (1f - x);
	}

	private static float smooth(float k) {
		float x = clamp(k);
		return x * x * (3 - 2 * x);
	}

	private static float easeOutBack(float k) {
		float x = clamp(k);
		float c1 = 1.70158f;
		float c3 = c1 + 1;
		return 1 + c3 * (x - 1) * (x - 1) * (x - 1) + c1 * (x - 1) * (x - 1);
	}

	private static float hash(int seed) {
		int x = seed * 374761393 + 668265263;
		x = (x ^ (x >>> 13)) * 1274126177;
		return ((x ^ (x >>> 16)) & 0xFFFF) / 65535f;
	}
}
