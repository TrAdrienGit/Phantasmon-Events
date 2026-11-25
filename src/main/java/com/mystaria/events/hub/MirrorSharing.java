package com.mystaria.events.hub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * Whether this player's game feeds the mirror hubs ({@link MirrorCapture}): on by default, and a switch on the
 * Phantasmon menu's Settings page to refuse it (2026-11-25, for publication: the player chooses whether the blocks
 * of a server's zone leave their game). Kept in {@code config/phantasmon-events.json} ({@code "share_mirror_blocks"});
 * a missing or broken file means on.
 */
public final class MirrorSharing {

	private static final Logger LOG = LoggerFactory.getLogger(MirrorSharing.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String KEY = "share_mirror_blocks";
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("phantasmon-events.json");

	private static boolean enabled = read();

	private MirrorSharing() {
	}

	/** Adds the switch to the Phantasmon menu's Settings page. */
	public static void register() {
		PhantasmonApi.registerSettingToggle(new PhantasmonApi.SettingToggle(
				Component.translatable("phantasmon_events.settings.share_mirror_blocks"),
				Component.translatable("phantasmon_events.settings.share_mirror_blocks.hint"),
				MirrorSharing::enabled, MirrorSharing::toggle));
	}

	public static boolean enabled() {
		return enabled;
	}

	private static void toggle() {
		enabled = !enabled;
		write();
		LOG.info("Mirror hub block sharing {}", enabled ? "on" : "off");
	}

	private static JsonObject file() {
		try {
			if (Files.exists(FILE)) {
				JsonObject json = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
				if (json != null) {
					return json;
				}
			}
		} catch (IOException | JsonParseException | IllegalStateException ex) {
			LOG.warn("Cannot read {}: defaults used", FILE, ex);
		}
		return new JsonObject();
	}

	private static boolean read() {
		JsonObject json = file();
		try {
			return !json.has(KEY) || json.get(KEY).isJsonNull() || json.get(KEY).getAsBoolean();
		} catch (IllegalStateException | UnsupportedOperationException | ClassCastException ex) {
			return true;
		}
	}

	private static void write() {
		JsonObject json = file(); // other keys kept
		json.addProperty(KEY, enabled);
		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(json) + System.lineSeparator(), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ex) {
			LOG.warn("Cannot write {}", FILE, ex);
		}
	}
}
