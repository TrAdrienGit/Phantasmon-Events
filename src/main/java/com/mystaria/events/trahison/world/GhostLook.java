package com.mystaria.events.trahison.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.ViewerKind;

/**
 * How the dead look on this client (T6.3, solution S6). The backend never lists ghosts to a living player beyond the deaths
 * he may know ({@code players[].alive}); this only reads that list. Used by the render mixins:
 * <ul>
 *   <li>a ghost's skin is a grey copy of his real one (made once per skin, on the render thread);</li>
 *   <li>with the setting {@code HIDDEN}, a living viewer does not render a ghost at all (body, name and shadow);</li>
 *   <li>a ghost's name carries a "ghost" mark.</li>
 * </ul>
 * A body ({@link BodyEntity}) keeps its colours: it is the dead player's corpse, not his ghost. Client thread.
 */
public final class GhostLook {

	private static final Map<ResourceLocation, ResourceLocation> GRAY = new HashMap<>();
	private static final java.util.Set<ResourceLocation> FAILED = new java.util.HashSet<>();

	private GhostLook() {
	}

	/** The account of an entity that stands for a player (a real player or a hub avatar), or null. */
	private static UUID accountOf(Entity entity) {
		return entity instanceof Player p && !(entity instanceof BodyEntity) ? p.getGameProfile().getId() : null;
	}

	/** Whether this entity is a player this viewer knows to be dead. */
	public static boolean isGhost(Entity entity) {
		UUID account = accountOf(entity);
		return account != null && GameView.hasGame() && GameView.ghosts().contains(account);
	}

	/** Whether this entity must not be drawn at all: a ghost, for a living viewer, under the {@code HIDDEN} setting. */
	public static boolean hidden(Entity entity) {
		return GameView.ghostsHidden() && GameView.current().viewer().kind() == ViewerKind.PLAYER && isGhost(entity);
	}

	/** The skin with its texture replaced by a grey copy; the same skin when the copy cannot be made. */
	public static PlayerSkin gray(PlayerSkin skin) {
		ResourceLocation copy = grayOf(skin.texture());
		if (copy == null) {
			return skin;
		}
		return new PlayerSkin(copy, skin.textureUrl(), skin.capeTexture(), skin.elytraTexture(), skin.model(), skin.secure());
	}

	private static ResourceLocation grayOf(ResourceLocation original) {
		ResourceLocation known = GRAY.get(original);
		if (known != null) {
			return known;
		}
		if (FAILED.contains(original)) {
			return null;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			AbstractTexture source = mc.getTextureManager().getTexture(original);
			GlStateManager._bindTexture(source.getId());
			NativeImage image = new NativeImage(64, 64, false);
			image.downloadTexture(0, false);
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int abgr = image.getPixelRGBA(x, y);
					int a = abgr >>> 24 & 0xFF;
					int b = abgr >>> 16 & 0xFF;
					int g = abgr >>> 8 & 0xFF;
					int r = abgr & 0xFF;
					int luma = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
					image.setPixelRGBA(x, y, a << 24 | luma << 16 | luma << 8 | luma);
				}
			}
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("phantasmon_events", "ghost_skin/" + Integer.toHexString(original.hashCode()));
			mc.getTextureManager().register(id, new DynamicTexture(image));
			GRAY.put(original, id);
			return id;
		} catch (RuntimeException e) {
			FAILED.add(original);
			return null;
		}
	}
}
