package com.mystaria.events.intro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import com.mystaria.showtime.api.CustomIntro;
import com.mystaria.showtime.api.IntroContext;

/**
 * The League's intros (TODO-37, 2026-11-11), all reserved and given as rewards: the gym leader's (one per type), the
 * Elite Four's and the Champion's. One staging, validated first as the gym leader's: the emblem rises out of the dark
 * in a fan of light, the star stands up against it as a silhouette, then lights up under their title and name, their
 * line is typed; the challenger's side cuts in from below, a "VS" stamps between them and both teams are counted out.
 * The Elite Four's adds its doors opening on the hall, four seals lighting round the emblem and a mist on the floor;
 * the Champion's, the most prestigious, a golden iris bursting open, beams of light, comets circling a crowned emblem,
 * a fanfare and a shake at the reveal, gold leaf falling and a gleam sweeping across their name.
 * Each one has its own colours, emblem frame and logo: the type's (Cobblemon's own type icon, or
 * {@code textures/intro/types/<type>.png} in this mod when one is supplied) or the League's. Showtime adds the
 * closing white flash; the star is whoever picked the intro in the lobby, on the right on both screens.
 */
public final class LeagueIntro implements CustomIntro {

	/** Cobblemon's 18 types, in the order of its {@code textures/gui/types.png} sheet (36 px each). */
	public static final List<String> TYPES = List.of("normal", "fire", "water", "grass", "electric", "ice", "fighting",
			"poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy");
	private static final ResourceLocation TYPE_SHEET = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/types.png");
	private static final ResourceLocation LEAGUE_LOGO = ResourceLocation.fromNamespaceAndPath("phantasmon_events", "textures/intro/league.png");

	private enum Rank {
		/** The first one, kept for the rewards already given: gold, a star at the heart of the badge. */
		GYM,
		GYM_TYPE,
		ELITE_FOUR,
		CHAMPION
	}

	/** A palette: the emblem's colour, its dark and light tones, the challenger band's colour. */
	private record Palette(int main, int deep, int light, int band) {
	}

	private static final int CRIMSON = 0xFF9E1F2A;
	private static final int NAVY = 0xFF1F3A8A;

	private static Palette palette(String type) {
		return switch (type) {
			case "fire" -> new Palette(0xFFFF7A2A, 0xFF7E2406, 0xFFFFD9A0, NAVY);
			case "water" -> new Palette(0xFF3E9BFF, 0xFF0F3A86, 0xFFBFE2FF, CRIMSON);
			case "grass" -> new Palette(0xFF5FD35A, 0xFF1C5E22, 0xFFD2F7C2, CRIMSON);
			case "electric" -> new Palette(0xFFFFD83A, 0xFF7A5C00, 0xFFFFF4B0, NAVY);
			case "ice" -> new Palette(0xFF8FE6F0, 0xFF246A7A, 0xFFE6FCFF, CRIMSON);
			case "fighting" -> new Palette(0xFFE0533A, 0xFF62180E, 0xFFFFC6B6, NAVY);
			case "poison" -> new Palette(0xFFB65AE0, 0xFF44165F, 0xFFEBC6FF, CRIMSON);
			case "ground" -> new Palette(0xFFD9A85A, 0xFF5E3E12, 0xFFF5DEB0, NAVY);
			case "flying" -> new Palette(0xFF9EB6FF, 0xFF33468E, 0xFFE2EAFF, CRIMSON);
			case "psychic" -> new Palette(0xFFFF5A9E, 0xFF7A1640, 0xFFFFC6DE, NAVY);
			case "bug" -> new Palette(0xFFA8C82A, 0xFF404E08, 0xFFE8F5A8, CRIMSON);
			case "rock" -> new Palette(0xFFC2A85E, 0xFF52441C, 0xFFEDE0B8, NAVY);
			case "ghost" -> new Palette(0xFF8A70E0, 0xFF241A52, 0xFFD6CCFF, CRIMSON);
			case "dragon" -> new Palette(0xFF6A5AFF, 0xFF1E1070, 0xFFCFC8FF, CRIMSON);
			case "dark" -> new Palette(0xFF9A7E70, 0xFF1C1512, 0xFFD8CCC2, CRIMSON);
			case "steel" -> new Palette(0xFFB8C8D8, 0xFF3E4C5A, 0xFFEEF4FA, CRIMSON);
			case "fairy" -> new Palette(0xFFFF9EE0, 0xFF7A3264, 0xFFFFE0F5, NAVY);
			case "elite_four" -> new Palette(0xFFB9A6FF, 0xFF2A1C60, 0xFFEDE6FF, CRIMSON);
			case "champion" -> new Palette(0xFFFFD45A, 0xFF6E4A00, 0xFFFFFBE6, CRIMSON);
			default -> new Palette(0xFFFFC94A, 0xFF4A2E08, 0xFFF3E6C4, CRIMSON); // normal, the first gym leader's
		};
	}

	/** Every League intro: the first gym leader's, one per type, the Elite Four's and the Champion's. */
	public static List<LeagueIntro> all() {
		List<LeagueIntro> intros = new ArrayList<>();
		intros.add(new LeagueIntro("phantasmon_events:gym_leader", Rank.GYM, null));
		for (String type : TYPES) {
			intros.add(new LeagueIntro("phantasmon_events:gym_" + type, Rank.GYM_TYPE, type));
		}
		intros.add(new LeagueIntro("phantasmon_events:elite_four", Rank.ELITE_FOUR, null));
		intros.add(new LeagueIntro("phantasmon_events:champion", Rank.CHAMPION, null));
		return intros;
	}

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
	/** The Elite Four: its doors open, its four seals light one after the other. */
	private static final long DOORS_END = CLOSE_END + 850;
	private static final long SEALS = 1500;
	private static final long SEAL_STEP = 220;

	private static final int CREAM = 0xFFF3E6C4;

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

	private final String id;
	private final Rank rank;
	/** The gym's type, or null. */
	private final String type;
	private final Palette colours;
	/** The Elite Four's doors and seals, the Champion's burst, fanfare and bells, on top of the common cues. */
	private static Cue[] cues(Rank rank) {
		List<Cue> all = new ArrayList<>(List.of(CUES));
		if (rank == Rank.ELITE_FOUR) {
			all.add(new Cue(CLOSE_END + 60, SoundEvents.IRON_DOOR_OPEN, 0.5f, 0.9f));
			all.add(new Cue(CLOSE_END + 140, SoundEvents.IRON_DOOR_OPEN, 0.45f, 0.7f));
			for (int i = 0; i < 4; i++) {
				all.add(new Cue(SEALS + i * SEAL_STEP, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f + 0.15f * i, 0.9f));
			}
			all.add(new Cue(REVEAL, SoundEvents.BELL_BLOCK, 0.6f, 0.6f));
		} else if (rank == Rank.CHAMPION) {
			all.add(new Cue(CLOSE_END, SoundEvents.END_PORTAL_SPAWN, 1.4f, 0.3f));
			all.add(new Cue(CLOSE_END, SoundEvents.BELL_BLOCK, 0.8f, 0.8f));
			all.add(new Cue(REVEAL, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.7f));
			all.add(new Cue(FLARE + 100, SoundEvents.BELL_BLOCK, 1.0f, 0.7f));
			all.add(new Cue(FLARE + 100, SoundEvents.BELL_RESONATE, 1.0f, 0.5f));
		}
		return all.toArray(Cue[]::new);
	}

	private final Cue[] cues;
	private final boolean[] played;
	private long lastTick = -1;
	/** The type's own logo when this mod supplies one (checked once): else Cobblemon's icon. */
	private Boolean ownLogo;

	private LeagueIntro(String id, Rank rank, String type) {
		this.id = id;
		this.rank = rank;
		this.type = type;
		this.cues = cues(rank);
		this.played = new boolean[cues.length];
		this.colours = palette(switch (rank) {
			case GYM -> "gym";
			case GYM_TYPE -> type;
			case ELITE_FOUR -> "elite_four";
			case CHAMPION -> "champion";
		});
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public Component name() {
		return switch (rank) {
			case GYM -> Component.translatable("phantasmon_events.intro.gym_leader");
			case GYM_TYPE -> Component.translatable("phantasmon_events.intro.gym_type", typeName());
			case ELITE_FOUR -> Component.translatable("phantasmon_events.intro.elite_four");
			case CHAMPION -> Component.translatable("phantasmon_events.intro.champion");
		};
	}

	private Component typeName() {
		return Component.translatable("cobblemon.type." + type);
	}

	/** The star's title: the rank, the gym's type after it. */
	private String title() {
		return switch (rank) {
			case GYM -> Component.translatable("phantasmon_events.intro.gym_leader.title").getString();
			case GYM_TYPE -> Component.translatable("phantasmon_events.intro.gym_leader.title").getString() + "  ·  "
					+ typeName().getString().toUpperCase(Locale.ROOT);
			case ELITE_FOUR -> Component.translatable("phantasmon_events.intro.elite_four.title").getString();
			case CHAMPION -> Component.translatable("phantasmon_events.intro.champion.title").getString();
		};
	}

	private String line() {
		return Component.translatable(switch (rank) {
			case GYM, GYM_TYPE -> "phantasmon_events.intro.gym_leader.line";
			case ELITE_FOUR -> "phantasmon_events.intro.elite_four.line";
			case CHAMPION -> "phantasmon_events.intro.champion.line";
		}).getString();
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
		for (int i = 0; i < cues.length; i++) {
			if (!played[i] && t >= cues[i].at()) {
				played[i] = true;
				context.sound(cues[i].sound(), cues[i].pitch(), cues[i].volume());
			}
		}
	}

	@Override
	public void render(GuiGraphics g, IntroContext c, long t, float partialTick) {
		int w = c.width();
		int h = c.height();
		float u = h / 270f;
		if (t < CLOSE_END) {
			if (rank == Rank.CHAMPION) {
				iris(g, w, h, u, ease(t / (float) CLOSE_END));
			} else {
				closing(g, w, h, ease(t / (float) CLOSE_END));
			}
			return;
		}
		boolean starIsSelf = c.starIsSelf();
		float reveal = clamp((t - REVEAL) / 320f);
		float flare = ease(clamp((t - FLARE) / 600f));
		// A slow push in all along, a faster one as the emblem flares at the end.
		float push = 1f + 0.035f * smooth(clamp((t - REVEAL) / 4700f)) + 0.07f * flare;
		g.pose().pushPose();
		g.pose().translate(w / 2f, h / 2f, 0);
		if (rank == Rank.CHAMPION && t >= REVEAL && t < REVEAL + 380) {
			// The Champion's reveal shakes the hall.
			float k = 1f - (t - REVEAL) / 380f;
			g.pose().translate(Mth.sin(t * 0.09f) * 4 * u * k, Mth.cos(t * 0.11f) * 3 * u * k, 0);
		}
		g.pose().scale(push, push, 1f);
		g.pose().translate(-w / 2f, -h / 2f, 0);

		// The dark hall, tinted by the emblem's colour, a glow on the floor line.
		g.fillGradient(-w, -h, 2 * w, 2 * h, rank == Rank.GYM ? 0xFF121A33 : mix(0xFF121A33, colours.deep(), 0.45f), 0xFF03050C);
		int glow = alpha(colours.main(), 0.10f + 0.10f * reveal);
		g.fillGradient(-w, Math.round(h * 0.70f), 2 * w, Math.round(h * 0.80f), colours.main() & 0xFFFFFF, glow);
		g.fillGradient(-w, Math.round(h * 0.80f), 2 * w, Math.round(h * 0.90f), glow, colours.main() & 0xFFFFFF);

		float cx = w * 0.66f;
		float cy = h * 0.40f;
		if (rank == Rank.CHAMPION) {
			pillars(g, w, h, u, cx, t, clamp((t - BADGE) / 900f) * (0.7f + 0.3f * reveal + 0.4f * flare));
		}
		float raysIn = clamp((t - CLOSE_END) / 650f);
		float rayStrength = 0.20f * raysIn + 0.12f * reveal + 0.35f * flare;
		rays(g, cx, cy, rank == Rank.CHAMPION ? 34 : 22, w * 1.1f, t * 0.00011f, rayStrength, colours.main());
		if (rank == Rank.CHAMPION) {
			// The Champion's crown of light: a second, finer fan turning the other way, in white.
			rays(g, cx, cy, 26, w * 0.8f, -t * 0.00018f, rayStrength * 0.6f, colours.light());
		}
		float emblem = 0;
		float turn = 0;
		float radius = 0;
		float light = 0.55f + 0.45f * reveal + 0.3f * flare;
		if (t >= BADGE) {
			emblem = clamp((t - BADGE) / 800f);
			radius = 72 * u * easeOutBack(emblem) * (1f + 0.06f * flare);
			turn = (1f - emblem) * -2.6f;
			badge(g, cx, cy, radius, turn, light);
		}
		float settled = 72 * u * (1f + 0.06f * flare);
		if (rank == Rank.ELITE_FOUR) {
			seals(g, cx, cy, settled, u, t, light);
		} else if (rank == Rank.CHAMPION) {
			comets(g, cx, cy, settled, u, t, clamp((t - BADGE - 600) / 600f));
			crown(g, cx, cy, settled, u, t);
		}
		sparks(g, w, h, u, t, clamp((t - REVEAL) / 600f), rank == Rank.CHAMPION ? 70 : 46);
		if (rank == Rank.ELITE_FOUR) {
			mist(g, w, h, u, t, clamp((t - CLOSE_END) / 900f));
		}
		g.flush();
		if (radius > 1) {
			logo(g, cx, cy, radius, turn, light);
		}

		// The star: rises as a silhouette against the emblem, then lights up, turning to face the challenger.
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
		if (rank == Rank.CHAMPION && t >= REVEAL) {
			goldLeaf(g, w, h, u, t, clamp((t - REVEAL) / 500f));
		}
		// The reveal: a burst of white over the star.
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
		if (rank == Rank.ELITE_FOUR && t < DOORS_END) {
			doors(g, w, h, u, t);
		}
		if (rank == Rank.CHAMPION && t < CLOSE_END + 450) {
			burst(g, w, h, u, (t - CLOSE_END) / 450f);
		}
		g.pose().popPose();
		g.pose().popPose();
	}

	// ---------------------------------------------------------------- pieces

	/** The world disappearing between two black bars meeting in the middle, edged in the emblem's colour. */
	private void closing(GuiGraphics g, int w, int h, float k) {
		int bar = Math.round(h / 2f * k);
		g.fill(0, 0, w, bar, 0xFF000000);
		g.fill(0, h - bar, w, h, 0xFF000000);
		g.fill(0, bar - 1, w, bar + 1, colours.main());
		g.fill(0, h - bar - 1, w, h - bar + 1, colours.main());
	}

	/** Fans of light from the emblem: wedges fading outward, turning slowly. */
	private static void rays(GuiGraphics g, float cx, float cy, int count, float length, float spin, float strength, int colour) {
		if (strength <= 0) {
			return;
		}
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		int outer = colour & 0xFFFFFF;
		for (int i = 0; i < count; i++) {
			float angle = hash(i * 7 + 3) * Mth.TWO_PI + spin * (i % 2 == 0 ? 1 : -0.6f);
			float half = 0.02f + 0.05f * hash(i * 7 + 4);
			float reach = length * (0.6f + 0.4f * hash(i * 7 + 5));
			int inner = alpha(colour, strength * (0.5f + 0.5f * hash(i * 7 + 6)));
			vertex(buffer, m, cx, cy, inner);
			vertex(buffer, m, cx + Mth.cos(angle - half) * reach, cy + Mth.sin(angle - half) * reach, outer);
			vertex(buffer, m, cx + Mth.cos(angle + half) * reach, cy + Mth.sin(angle + half) * reach, outer);
			vertex(buffer, m, cx + Mth.cos(angle + half) * reach, cy + Mth.sin(angle + half) * reach, outer);
		}
	}

	/** Sides of the emblem's frame: a gym badge's octagon, the Elite Four's hexagon, the Champion's twelve. */
	private int sides() {
		return rank == Rank.ELITE_FOUR ? 6 : rank == Rank.CHAMPION ? 12 : 8;
	}

	/** The frame's resting angle, a flat side on top. */
	private float rest() {
		return Mth.PI / sides();
	}

	/**
	 * The emblem: a soft glow, a rim in the emblem's colour round a dark ring, a bevelled face; the first gym badge has
	 * a four-pointed star at its heart, the others a logo ({@link #logo}). The Champion's has a second rim.
	 */
	private void badge(GuiGraphics g, float cx, float cy, float r, float turn, float light) {
		if (r <= 0.5f) {
			return;
		}
		int sides = sides();
		float a0 = turn + rest();
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		for (int i = 6; i >= 1; i--) {
			float k = 1f + i * 0.14f;
			ring(buffer, m, cx, cy, r * k, r * (k - 0.14f), 32, 0f, alpha(colours.main(), 0.05f * light * (7 - i) / 6f));
		}
		if (rank == Rank.CHAMPION) {
			ring(buffer, m, cx, cy, r * 1.12f, r * 1.06f, sides, a0, colours.light());
		}
		ring(buffer, m, cx, cy, r, r * 0.86f, sides, a0, colours.main());
		ring(buffer, m, cx, cy, r * 0.86f, r * 0.78f, sides, a0, colours.deep());
		// The first gym badge keeps its validated gold face; the others take their colours.
		int faceEdge = rank == Rank.GYM ? 0xFFD8962A : mix(colours.deep(), colours.main(), 0.55f);
		int faceCentre = rank == Rank.GYM ? colours.main() : mix(colours.main(), colours.light(), 0.25f);
		polygon(buffer, m, cx, cy, r * 0.78f, sides, a0, faceEdge, faceCentre);
		ring(buffer, m, cx, cy, r * 0.70f, r * 0.67f, sides, a0, alpha(colours.deep(), 0.8f));
		if (rank != Rank.GYM) {
			return;
		}
		for (int arm = 0; arm < 4; arm++) {
			float a = a0 + arm * Mth.HALF_PI;
			float tipX = cx + Mth.cos(a) * r * 0.58f;
			float tipY = cy + Mth.sin(a) * r * 0.58f;
			float sideA = a + Mth.HALF_PI;
			float wide = r * 0.11f;
			int core = alpha(0xFFFFF6DA, Math.min(1f, 0.75f + 0.25f * light));
			vertex(buffer, m, cx, cy, core);
			vertex(buffer, m, cx + Mth.cos(sideA) * wide, cy + Mth.sin(sideA) * wide, colours.main());
			vertex(buffer, m, tipX, tipY, core);
			vertex(buffer, m, cx - Mth.cos(sideA) * wide, cy - Mth.sin(sideA) * wide, colours.main());
		}
	}

	/**
	 * The logo at the emblem's heart, turning with it as it settles, dimmed until the reveal: the type's icon, or the
	 * League's logo for the Elite Four and the Champion. Pixel art, drawn sharp.
	 */
	private void logo(GuiGraphics g, float cx, float cy, float r, float turn, float light) {
		if (rank == Rank.GYM) {
			return;
		}
		float bright = Math.min(1f, 0.6f + 0.4f * light);
		g.pose().pushPose();
		g.pose().translate(cx, cy, 1);
		g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(turn));
		RenderSystem.setShaderColor(bright, bright, bright, 1f);
		if (rank == Rank.GYM_TYPE) {
			int size = Math.round(r * 1.12f);
			if (ownLogo()) {
				g.blit(ownLogoTexture(), -size / 2, -size / 2, size, size, 0, 0, 1, 1, 1, 1);
			} else {
				int index = TYPES.indexOf(type);
				g.blit(TYPE_SHEET, -size / 2, -size / 2, size, size, index * 36, 0, 36, 36, 648, 36);
			}
		} else {
			int height = Math.round(r * 1.18f);
			int width = Math.round(height * 56f / 59f);
			g.blit(LEAGUE_LOGO, -width / 2, -height / 2, width, height, 0, 0, 56, 59, 56, 59);
		}
		g.flush();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		g.pose().popPose();
	}

	private ResourceLocation ownLogoTexture() {
		return ResourceLocation.fromNamespaceAndPath("phantasmon_events", "textures/intro/types/" + type + ".png");
	}

	private boolean ownLogo() {
		if (ownLogo == null) {
			ownLogo = Minecraft.getInstance().getResourceManager().getResource(ownLogoTexture()).isPresent();
		}
		return ownLogo;
	}

	/** Sparks drifting up through the hall, in the emblem's colours. */
	private void sparks(GuiGraphics g, int w, int h, float u, long t, float strength, int count) {
		if (strength <= 0) {
			return;
		}
		for (int i = 0; i < count; i++) {
			float speed = 0.02f + 0.05f * hash(i * 3 + 1);
			float x = hash(i * 3 + 2) * w + Mth.sin(t * 0.002f + i) * 6 * u;
			float travel = (t * speed + hash(i * 3 + 3) * h * 1.3f) % (h * 1.3f);
			float y = h * 1.1f - travel;
			int size = Math.max(1, Math.round((1 + hash(i * 3 + 4) * 1.5f) * u));
			float fade = strength * clamp(1f - travel / (h * 1.3f)) * (0.5f + 0.5f * Mth.sin(t * 0.01f + i * 1.7f));
			g.fill(Math.round(x), Math.round(y), Math.round(x) + size, Math.round(y) + size,
					alpha(i % 4 == 0 ? colours.light() : colours.main(), fade));
		}
	}

	/** The challenger's side: a band rising from the lower left, edged in the emblem's colour. */
	private void challengerBand(GuiGraphics g, int w, int h, float u, float in) {
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		float right = w * 0.56f * in;
		int top = alpha(colours.band(), 0.92f);
		int bottom = alpha(mix(colours.band(), 0xFF000000, 0.75f), 0.95f);
		vertex(buffer, m, -w * 0.1f, h * 0.66f, top);
		vertex(buffer, m, -w * 0.1f, h * 1.1f, bottom);
		vertex(buffer, m, right - w * 0.08f, h * 1.1f, bottom);
		vertex(buffer, m, right, h * 0.60f, top);
		float edge = 2 * u;
		vertex(buffer, m, -w * 0.1f, h * 0.66f - edge, colours.main());
		vertex(buffer, m, -w * 0.1f, h * 0.66f, colours.main());
		vertex(buffer, m, right, h * 0.60f, colours.main());
		vertex(buffer, m, right, h * 0.60f - edge, colours.main());
		g.flush();
	}

	/**
	 * The star's plate, sliding in from the left: their title in small capitals, their name large, an underline
	 * growing, then their line typed below. Returns the y under the name, for the team's balls.
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
		String title = title();
		float nameWidth = font.width(name) * nameScale;
		float plateWidth = Math.max(Math.max(nameWidth, font.width(title) * 1.1f * u), 120 * u);
		g.fill(Math.round(x - 6 * u), Math.round(y0 - 6 * u), Math.round(x + plateWidth + 10 * u),
				Math.round(nameBottom + 6 * u), alpha(0xFF05070F, 0.55f * in));
		g.fill(Math.round(x - 6 * u), Math.round(y0 - 6 * u), Math.round(x - 3 * u), Math.round(nameBottom + 6 * u), colours.main());
		text(g, title, x, y0, 1.1f * u, colours.main(), true);
		if (rank == Rank.ELITE_FOUR) {
			// Four marks after the title: the four of the Council.
			float px = x + font.width(title) * 1.1f * u + 8 * u;
			for (int i = 0; i < 4; i++) {
				diamond(g, px + i * 9 * u, y0 + 4.5f * u, 3 * u, i % 2 == 0 ? colours.main() : colours.light());
			}
		}
		if (rank == Rank.CHAMPION) {
			shimmer(g, name, x, y0 + 14 * u, nameScale, t);
		} else {
			text(g, name, x, y0 + 14 * u, nameScale, 0xFFFFFFFF, true);
		}
		float line = ease(clamp((t - PLATE - 250) / 500f));
		g.fill(Math.round(x), Math.round(nameBottom + 2 * u), Math.round(x + nameWidth * line), Math.round(nameBottom + 4 * u), colours.main());
		if (rank == Rank.CHAMPION) {
			float second = ease(clamp((t - PLATE - 450) / 500f));
			g.fill(Math.round(x), Math.round(nameBottom + 5.5f * u), Math.round(x + nameWidth * 0.7f * second),
					Math.round(nameBottom + 6.5f * u), colours.light());
		}
		if (t >= LINE) {
			String quote = line();
			int shown = Math.min(quote.length(), (int) ((t - LINE) / 32));
			text(g, quote.substring(0, shown), x, nameBottom + 10 * u + (t >= BALLS ? 16 * u : 0), 1.05f * u, colours.light(), true);
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
		int outline = mix(colours.deep(), 0xFF000000, 0.6f) & 0xFFFFFF;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					g.drawString(font, vs, dx, dy, (a << 24) | outline, false);
				}
			}
		}
		g.drawString(font, vs, 0, 0, (a << 24) | (colours.main() & 0xFFFFFF), false);
		g.pose().popPose();
	}

	// ---------------------------------------------------------------- the Elite Four's

	/**
	 * The Council's doors: two heavy violet panels shut over the hall, a line of light along their seam, then sliding
	 * apart onto it.
	 */
	private void doors(GuiGraphics g, int w, int h, float u, long t) {
		float open = ease(clamp((t - CLOSE_END - 160) / 690f));
		float half = w / 2f * (1f - open);
		if (half <= 0.5f) {
			return;
		}
		int top = mix(colours.deep(), 0xFF000000, 0.35f);
		int bottom = mix(colours.deep(), 0xFF000000, 0.8f);
		int left = Math.round(half);
		g.fillGradient(-w, -h, left, 2 * h, top, bottom);
		g.fillGradient(w - left, -h, 2 * w, 2 * h, top, bottom);
		// Silver bands across the panels, and their inner edges.
		for (float band : new float[] { 0.18f, 0.82f }) {
			int y = Math.round(h * band);
			g.fill(-w, y, left, y + Math.round(3 * u), alpha(colours.light(), 0.55f));
			g.fill(w - left, y, 2 * w, y + Math.round(3 * u), alpha(colours.light(), 0.55f));
		}
		g.fill(left - Math.round(3 * u), -h, left, 2 * h, colours.light());
		g.fill(w - left, -h, w - left + Math.round(3 * u), 2 * h, colours.light());
		// The seam glowing just before they part.
		float seam = clamp((t - CLOSE_END) / 160f) * (1f - open);
		if (seam > 0) {
			g.fill(left - Math.round(1 * u), -h, w - left + Math.round(1 * u), 2 * h, alpha(0xFFFFFFFF, seam));
			for (int i = 1; i <= 4; i++) {
				g.fill(left - Math.round(i * 3 * u), -h, w - left + Math.round(i * 3 * u), 2 * h, alpha(colours.main(), 0.12f * seam));
			}
		}
		// Each panel bears half of a large hexagon, whole while they are shut.
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		float r = 70 * u;
		float hx = w / 2f;
		float hy = h / 2f;
		float shift = w / 2f - half;
		for (int side = 0; side < 6; side++) {
			float a = side * Mth.TWO_PI / 6;
			float b = (side + 1) * Mth.TWO_PI / 6;
			float ax = Mth.cos(a) * r;
			float bx = Mth.cos(b) * r;
			// A side belongs to the panel its middle is on.
			float dx = (ax + bx) / 2f < 0 ? -shift : shift;
			float ay = Mth.sin(a) * r;
			float by = Mth.sin(b) * r;
			vertex(buffer, m, hx + ax + dx, hy + ay, alpha(colours.light(), 0.8f));
			vertex(buffer, m, hx + ax * 0.9f + dx, hy + ay * 0.9f, alpha(colours.light(), 0.8f));
			vertex(buffer, m, hx + bx * 0.9f + dx, hy + by * 0.9f, alpha(colours.light(), 0.8f));
			vertex(buffer, m, hx + bx + dx, hy + by, alpha(colours.light(), 0.8f));
		}
		g.flush();
	}

	/**
	 * The four seals of the Council round the emblem, lighting one after the other, bound by lines of light into a
	 * square.
	 */
	private void seals(GuiGraphics g, float cx, float cy, float r, float u, long t, float light) {
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		float[] xs = new float[4];
		float[] ys = new float[4];
		float[] lit = new float[4];
		for (int i = 0; i < 4; i++) {
			float a = -Mth.HALF_PI / 2 - Mth.HALF_PI + i * Mth.HALF_PI; // top left, top right, bottom right, bottom left
			xs[i] = cx + Mth.cos(a) * r * 1.62f;
			ys[i] = cy + Mth.sin(a) * r * 1.62f;
			lit[i] = clamp((t - SEALS - i * SEAL_STEP) / 260f);
		}
		for (int i = 0; i < 4; i++) {
			int next = (i + 1) % 4;
			float bound = Math.min(lit[i], lit[next]);
			if (bound > 0) {
				line(buffer, m, xs[i], ys[i], Mth.lerp(bound, xs[i], xs[next]), Mth.lerp(bound, ys[i], ys[next]), 1.2f * u,
						alpha(colours.light(), 0.45f * light));
			}
		}
		for (int i = 0; i < 4; i++) {
			if (lit[i] <= 0) {
				continue;
			}
			float size = r * 0.2f * easeOutBack(lit[i]);
			float pulse = 0.85f + 0.15f * Mth.sin(t * 0.006f + i);
			for (int k = 3; k >= 1; k--) {
				ring(buffer, m, xs[i], ys[i], size * (1.2f + k * 0.25f), size * (1.2f + (k - 1) * 0.25f), 24, 0f,
						alpha(colours.main(), 0.07f * pulse * (4 - k)));
			}
			ring(buffer, m, xs[i], ys[i], size, size * 0.78f, 6, Mth.PI / 6, colours.light());
			polygon(buffer, m, xs[i], ys[i], size * 0.78f, 6, Mth.PI / 6, colours.deep(), mix(colours.deep(), colours.main(), 0.6f));
			polygon(buffer, m, xs[i], ys[i], size * 0.3f, 6, Mth.PI / 6, colours.main(), alpha(0xFFFFFFFF, pulse));
			// The flash of a seal lighting.
			float flash = 1f - clamp((t - SEALS - i * SEAL_STEP) / 400f);
			if (flash > 0) {
				float ringR = size * (1f + 2.5f * (1f - flash));
				ring(buffer, m, xs[i], ys[i], ringR, ringR - 2 * u, 24, 0f, alpha(0xFFFFFFFF, flash));
			}
		}
	}

	/** Violet mist drifting along the floor. */
	private void mist(GuiGraphics g, int w, int h, float u, long t, float strength) {
		if (strength <= 0) {
			return;
		}
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		for (int i = 0; i < 9; i++) {
			float speed = 0.004f + 0.006f * hash(i * 5 + 40);
			float x = ((hash(i * 5 + 41) * w * 1.6f + t * speed * u * 10) % (w * 1.6f)) - w * 0.3f;
			float y = h * (0.80f + 0.12f * hash(i * 5 + 42));
			blob(buffer, m, x, y, w * (0.18f + 0.12f * hash(i * 5 + 43)), 12 * u, alpha(colours.light(), 0.16f * strength));
		}
	}

	// ---------------------------------------------------------------- the Champion's

	/** The world closing in a golden iris onto a point of light. */
	private void iris(GuiGraphics g, int w, int h, float u, float k) {
		float diagonal = (float) Math.hypot(w, h) / 2f + 4;
		float radius = Mth.lerp(k, diagonal, 2 * u);
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		ring(buffer, m, w / 2f, h / 2f, diagonal * 2, radius, 48, 0f, 0xFF000000);
		ring(buffer, m, w / 2f, h / 2f, radius, Math.max(0, radius - 2 * u), 48, 0f, colours.main());
		g.flush();
	}

	/** The point of light bursting: a gold and white flash, a ring sweeping out over the hall. */
	private void burst(GuiGraphics g, int w, int h, float u, float k) {
		if (k < 0 || k >= 1) {
			return;
		}
		g.fill(-w, -h, 2 * w, 2 * h, alpha(mix(colours.light(), 0xFFFFFFFF, 0.5f), 0.9f * (1f - k) * (1f - k)));
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		float radius = ease(k) * w * 0.9f;
		ring(buffer, m, w / 2f, h / 2f, radius, Math.max(0, radius - 6 * u * (1f - k)), 64, 0f, alpha(colours.main(), 1f - k));
		g.flush();
	}

	/** Beams of light rising from the floor behind the Champion, swaying slowly. */
	private void pillars(GuiGraphics g, int w, int h, float u, float cx, long t, float strength) {
		if (strength <= 0) {
			return;
		}
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		for (int i = 0; i < 6; i++) {
			float x = cx + (i - 2.5f) * w * 0.12f + Mth.sin(t * 0.0007f + i * 1.3f) * w * 0.03f;
			float lean = Mth.sin(t * 0.0005f + i) * w * 0.04f;
			float floor = h * 0.86f;
			float bottomHalf = 6 * u;
			float topHalf = 22 * u;
			int base = alpha(colours.light(), 0.16f * strength);
			int end = colours.light() & 0xFFFFFF;
			vertex(buffer, m, x - bottomHalf, floor, base);
			vertex(buffer, m, x + bottomHalf, floor, base);
			vertex(buffer, m, x + lean + topHalf, -h * 0.1f, end);
			vertex(buffer, m, x + lean - topHalf, -h * 0.1f, end);
		}
	}

	/** Three comets circling the emblem, each with a tail of fading sparks. */
	private void comets(GuiGraphics g, float cx, float cy, float r, float u, long t, float strength) {
		if (strength <= 0) {
			return;
		}
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		for (int i = 0; i < 3; i++) {
			float head = t * 0.0022f + i * Mth.TWO_PI / 3;
			for (int k = 14; k >= 0; k--) {
				float a = head - k * 0.055f;
				float x = cx + Mth.cos(a) * r * 1.3f;
				float y = cy + Mth.sin(a) * r * 1.3f * 0.92f;
				float size = (k == 0 ? 3.2f : 2.4f * (1f - k / 15f)) * u;
				int colour = k == 0 ? 0xFFFFFFFF : k < 4 ? colours.light() : colours.main();
				diamondQuad(buffer, m, x, y, size, alpha(colour, strength * (1f - k / 15f)));
			}
		}
	}

	/** A crown of light over the emblem, set down at the reveal: five spikes, a gem at each tip. */
	private void crown(GuiGraphics g, float cx, float cy, float r, float u, long t) {
		float k = clamp((t - REVEAL - 80) / 450f);
		if (k <= 0) {
			return;
		}
		float s = easeOutBack(k);
		float bob = Mth.sin(t * 0.003f) * 1.5f * u;
		// Set on the emblem's top edge (higher, it would leave a ×4 screen).
		float baseY = cy - r * 0.84f + bob;
		float halfW = r * 0.5f * s;
		float bandH = r * 0.1f * s;
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		int top = colours.light();
		int bottom = colours.main();
		vertex(buffer, m, cx - halfW, baseY - bandH, top);
		vertex(buffer, m, cx - halfW, baseY, bottom);
		vertex(buffer, m, cx + halfW, baseY, bottom);
		vertex(buffer, m, cx + halfW, baseY - bandH, top);
		float[] heights = { 0.20f, 0.28f, 0.38f, 0.28f, 0.20f };
		for (int i = 0; i < 5; i++) {
			float x = cx - halfW + (i + 0.5f) * (2 * halfW / 5);
			float half = halfW / 5;
			float tipY = baseY - bandH - r * heights[i] * s;
			vertex(buffer, m, x - half, baseY - bandH, bottom);
			vertex(buffer, m, x + half, baseY - bandH, bottom);
			vertex(buffer, m, x, tipY, top);
			vertex(buffer, m, x, tipY, top);
			float twinkle = 0.7f + 0.3f * Mth.sin(t * 0.008f + i * 1.9f);
			diamondQuad(buffer, m, x, tipY - 2 * u, 3.2f * u * s, alpha(0xFFFFFFFF, twinkle));
		}
		// Gems along the band.
		for (int i = 0; i < 3; i++) {
			float x = cx + (i - 1) * halfW * 0.6f;
			diamondQuad(buffer, m, x, baseY - bandH / 2, 2.6f * u * s, i == 1 ? 0xFFE8364A : 0xFF3A8CFF);
		}
	}

	/** Gold leaf falling over everything after the reveal, turning and catching the light. */
	private void goldLeaf(GuiGraphics g, int w, int h, float u, long t, float strength) {
		if (strength <= 0) {
			return;
		}
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		long since = t - REVEAL;
		for (int i = 0; i < 56; i++) {
			float speed = 0.03f + 0.04f * hash(i * 9 + 60);
			float fall = (since * speed + hash(i * 9 + 61) * h * 1.2f) % (h * 1.2f);
			float x = hash(i * 9 + 62) * w + Mth.sin(since * 0.002f + i) * 14 * u;
			float y = fall - h * 0.1f;
			float angle = since * (0.002f + 0.004f * hash(i * 9 + 63)) + i;
			float flip = Math.abs(Mth.cos(since * 0.006f + i * 0.7f));
			float lw = 3.2f * u * (0.3f + 0.7f * flip);
			float lh = 1.8f * u;
			float ca = Mth.cos(angle);
			float sa = Mth.sin(angle);
			int colour = alpha(flip > 0.85f ? 0xFFFFFFFF : i % 3 == 0 ? colours.light() : colours.main(), strength * 0.9f);
			vertex(buffer, m, x + (-lw * ca - -lh * sa), y + (-lw * sa + -lh * ca), colour);
			vertex(buffer, m, x + (-lw * ca - lh * sa), y + (-lw * sa + lh * ca), colour);
			vertex(buffer, m, x + (lw * ca - lh * sa), y + (lw * sa + lh * ca), colour);
			vertex(buffer, m, x + (lw * ca - -lh * sa), y + (lw * sa + -lh * ca), colour);
		}
		g.flush();
	}

	/** The Champion's name, a band of golden light sweeping across it every second and a half. */
	private void shimmer(GuiGraphics g, String name, float x, float y, float scale, long t) {
		Font font = Minecraft.getInstance().font;
		float width = font.width(name);
		float sweep = ((t - PLATE - 600) % 1600) / 1600f * (width + 30) - 15;
		boolean sweeping = t >= PLATE + 600;
		g.pose().pushPose();
		g.pose().translate(x, y, 0);
		g.pose().scale(scale, scale, 1f);
		float cursor = 0;
		for (int i = 0; i < name.length(); i++) {
			String letter = name.substring(i, i + 1);
			float letterW = font.width(letter);
			float centre = cursor + letterW / 2f;
			float near = sweeping ? clamp(1f - Math.abs(centre - sweep) / 9f) : 0;
			g.drawString(font, letter, Math.round(cursor), 0, mix(0xFFFFFFFF, colours.main(), near), true);
			cursor += letterW;
		}
		g.pose().popPose();
	}

	// ---------------------------------------------------------------- helpers

	/** A soft blob: brightest at its centre, fading to nothing at its edges. */
	private static void blob(VertexConsumer buffer, Matrix4f m, float cx, float cy, float rx, float ry, int colour) {
		int clear = colour & 0xFFFFFF;
		float[][] corners = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
		for (float[] c : corners) {
			vertex(buffer, m, cx, cy, colour);
			vertex(buffer, m, cx + c[0] * rx, cy, clear);
			vertex(buffer, m, cx + c[0] * rx, cy + c[1] * ry, clear);
			vertex(buffer, m, cx, cy + c[1] * ry, clear);
		}
	}

	/** A line of {@code thickness} between two points, as a quad. */
	private static void line(VertexConsumer buffer, Matrix4f m, float x1, float y1, float x2, float y2, float thickness, int colour) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float length = Math.max(0.001f, (float) Math.hypot(dx, dy));
		float nx = -dy / length * thickness / 2f;
		float ny = dx / length * thickness / 2f;
		vertex(buffer, m, x1 + nx, y1 + ny, colour);
		vertex(buffer, m, x1 - nx, y1 - ny, colour);
		vertex(buffer, m, x2 - nx, y2 - ny, colour);
		vertex(buffer, m, x2 + nx, y2 + ny, colour);
	}

	private static void diamondQuad(VertexConsumer buffer, Matrix4f m, float cx, float cy, float r, int colour) {
		vertex(buffer, m, cx, cy - r, colour);
		vertex(buffer, m, cx - r, cy, colour);
		vertex(buffer, m, cx, cy + r, colour);
		vertex(buffer, m, cx + r, cy, colour);
	}

	private static void diamond(GuiGraphics g, float cx, float cy, float r, int colour) {
		VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
		diamondQuad(buffer, g.pose().last().pose(), cx, cy, r, colour);
	}

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

	/** {@code a} blended towards {@code b} by {@code k}, opaque. */
	private static int mix(int a, int b, float k) {
		int r = Math.round(Mth.lerp(k, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
		int gr = Math.round(Mth.lerp(k, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
		int bl = Math.round(Mth.lerp(k, a & 0xFF, b & 0xFF));
		return 0xFF000000 | (r << 16) | (gr << 8) | bl;
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
