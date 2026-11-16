package com.mystaria.events.trahison.room;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Pictures of the game's rooms, looked up by id in the resource pack so that a placeholder is replaced by dropping a file of the
 * same name over it: {@code assets/phantasmon_events/textures/trahison/docs/<id>.png} (document images) and
 * {@code .../items/<id>.png} (item icons). Any size and ratio is drawn scaled to fit. Sizes are read once from the file. Client thread.
 */
public final class Pictures {

	public static final String NAMESPACE = "phantasmon_events";
	/** What a document shows for an image id that has no file yet. */
	public static final String PLACEHOLDER = "placeholder";

	private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();
	private static final int[] NONE = new int[0];

	private Pictures() {
	}

	/** The texture of a document image, or the placeholder's when the id has none; null only if even the placeholder is missing. */
	public static ResourceLocation doc(String id) {
		ResourceLocation own = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "textures/trahison/docs/" + clean(id) + ".png");
		if (size(own) != null) {
			return own;
		}
		ResourceLocation fallback = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "textures/trahison/docs/" + PLACEHOLDER + ".png");
		return size(fallback) != null ? fallback : null;
	}

	/** Whether the pack has a real picture for this document image id (not the placeholder). */
	public static boolean hasDoc(String id) {
		return size(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "textures/trahison/docs/" + clean(id) + ".png")) != null;
	}

	/** An id kept to {@code a-z 0-9 _ -} so a room definition can never name a stray path. */
	static String clean(String id) {
		return id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
	}

	/** {@code {width, height}} of a texture of the pack, or null when it is not there. */
	public static int[] size(ResourceLocation texture) {
		int[] known = SIZES.get(texture);
		if (known == null) {
			known = NONE;
			var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
			if (resource.isPresent()) {
				try (var in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
					known = new int[] { image.getWidth(), image.getHeight() };
				} catch (java.io.IOException | RuntimeException e) {
					known = NONE;
				}
			}
			SIZES.put(texture, known);
		}
		return known.length == 0 ? null : known;
	}

	/** Draws a texture inside a box, as large as fits, centred. */
	public static void draw(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h) {
		int[] s = size(texture);
		if (s == null) {
			return;
		}
		float scale = Math.min(w / (float) s[0], h / (float) s[1]);
		int dw = Math.max(1, Math.round(s[0] * scale));
		int dh = Math.max(1, Math.round(s[1] * scale));
		g.blit(texture, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, 0, 0, s[0], s[1], s[0], s[1]);
	}
}
