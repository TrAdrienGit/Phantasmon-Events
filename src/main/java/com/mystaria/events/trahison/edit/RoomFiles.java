package com.mystaria.events.trahison.edit;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * Transport of a room's schematic through the JSON routes {@code /events/trahison/rooms/{id}/schematic.json}
 * (protocole.md section 4.3): the compressed bytes travel as base64 in a JSON body, so {@link PhantasmonApi#get} and
 * {@link PhantasmonApi#post} are enough. The backend applies the binary route's limit (16 MiB decoded).
 */
final class RoomFiles {

	/** The JSON answer of {@code GET .../schematic.json}. */
	private static final class Download {
		String id;
		int version;
		String data;
	}

	/** The JSON body of {@code POST .../schematic.json}. */
	private static final class Upload {
		final String data;

		Upload(String data) {
			this.data = data;
		}
	}

	private RoomFiles() {
	}

	private static String path(String roomId) {
		return "/events/trahison/rooms/" + roomId + "/schematic.json";
	}

	/** The room's saved blocks; fails with the backend's error (404 when there are none). */
	static CompletableFuture<byte[]> download(String roomId) {
		return PhantasmonApi.get(path(roomId), Download.class).thenApply(answer -> {
			if (answer == null || answer.data == null) {
				throw new IllegalStateException("empty schematic answer");
			}
			return Base64.getDecoder().decode(answer.data);
		});
	}

	/** Replaces the room's saved blocks. */
	static CompletableFuture<Map> upload(String roomId, byte[] bytes) {
		return PhantasmonApi.post(path(roomId), new Upload(Base64.getEncoder().encodeToString(bytes)), Map.class);
	}
}
