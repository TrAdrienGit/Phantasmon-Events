package com.mystaria.events.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;

import com.mystaria.events.trahison.net.Secrets;

/**
 * T7.2 (solution S7): a player tied up by the bonds item cannot move: once the keyboard has been read, the movement impulses,
 * the jump and the sneak are put back to zero. He can still turn his head and talk. The backend decides who is tied and for
 * how long; this only follows its view (the mod of the tied player holds him, as between players of good faith).
 */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputRestrainMixin {

	@Inject(method = "tick", at = @At("RETURN"))
	private void phantasmonEvents$restrain(boolean slowDown, float sneakingSpeedBonus, CallbackInfo ci) {
		if (Secrets.restrained()) {
			Input input = (Input) (Object) this;
			input.up = false;
			input.down = false;
			input.left = false;
			input.right = false;
			input.forwardImpulse = 0;
			input.leftImpulse = 0;
			input.jumping = false;
			input.shiftKeyDown = false;
		}
	}
}
