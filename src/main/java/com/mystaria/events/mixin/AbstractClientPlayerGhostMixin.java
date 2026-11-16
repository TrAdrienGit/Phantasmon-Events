package com.mystaria.events.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;

import com.mystaria.events.trahison.world.GhostLook;

/** T6.3 (S6): the skin of a player known to be dead is a black and white copy of his own. */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerGhostMixin {

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
	private void phantasmonEvents$gray(CallbackInfoReturnable<PlayerSkin> cir) {
		if (GhostLook.isGhost((AbstractClientPlayer) (Object) this) || GhostLook.isSpectator((AbstractClientPlayer) (Object) this)) {
			cir.setReturnValue(GhostLook.gray(cir.getReturnValue()));
		}
	}
}
