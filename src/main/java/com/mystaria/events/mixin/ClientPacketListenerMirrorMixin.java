package com.mystaria.events.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;

import com.mystaria.events.hub.MirrorCapture;

/** A block entity's data from the server (a Templates block's look, a sign's text): mirror hubs send it on (D-38). */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMirrorMixin {

	@Inject(method = "handleBlockEntityData", at = @At("RETURN"))
	private void phantasmonEvents$mirror(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
		MirrorCapture.onBlockEntity(packet.getPos());
	}
}
