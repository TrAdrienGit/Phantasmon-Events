package com.mystaria.events.trahison.voice;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

/**
 * Simple Voice Chat's plugin entrypoint of the betrayal game ({@code "voicechat"} in {@code fabric.mod.json}, T5.1, solution
 * S4): it is only ever loaded by Simple Voice Chat itself, so its API is touched nowhere else and Events runs the same without
 * the mod (D-36). It lowers or mutes what <em>this</em> client hears of each player, frame by frame, from the table
 * {@link TrahisonVoice} computes on the game thread.
 *
 * <p>The event arrives on the audio thread: nothing of the world or of the game is read here, only the immutable table.
 * The three variants are subscribed separately (the event bus dispatches by exact class). The proximity voice of a player on
 * the server is keyed by his account, the voice relayed from a hub copy by the entity id of his avatar (S5): the table holds both.
 */
public final class TrahisonVoicePlugin implements VoicechatPlugin {

	@Override
	public String getPluginId() {
		return "phantasmon_events_trahison";
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, event -> filter(event, event.isWhispering()));
		registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, event -> filter(event, false));
		registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, event -> filter(event, false));
	}

	private static void filter(ClientReceiveSoundEvent event, boolean whispering) {
		short[] frame = event.getRawAudio();
		if (frame == null || frame.length == 0) {
			return; // an empty frame ends the speech: left alone
		}
		double gain = TrahisonVoice.table().gain(event.getId(), whispering);
		if (gain < 1) {
			event.setRawAudio(VoiceGains.apply(frame, gain));
		}
	}
}
