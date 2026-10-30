package com.mystaria.events.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;

import com.mystaria.events.tournament.TournamentOpponent;

/** The opponent of the current tournament match glows, through walls (Adrien 2026-10-30). */
@Mixin(Entity.class)
abstract class EntityGlowOpponentMixin {

	@Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
	private void phantasmonEvents$opponent(CallbackInfoReturnable<Boolean> cir) {
		if (TournamentOpponent.isOpponent((Entity) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
