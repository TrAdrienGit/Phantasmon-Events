package com.mystaria.events.tournament;

import java.util.Locale;

import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/** A failed tournament request as the player reads it: the backend's error code, translated. */
final class TournamentMessages {

	private TournamentMessages() {
	}

	static Component error(Throwable failure) {
		String code = PhantasmonApi.errorCode(failure);
		Component message = code == null ? Component.translatable("phantasmon_events.error.network")
				: Component.translatableWithFallback("phantasmon_events.error." + code.toLowerCase(Locale.ROOT), code);
		// The chat's "[Tournoi]" prefix says nothing inside the Tournaments page.
		return Component.literal(message.getString().replaceFirst("^\\[[^]]*]\\s*", ""));
	}
}
