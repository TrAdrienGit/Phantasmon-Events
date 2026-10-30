package com.mystaria.events.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.mystaria.events.hub.MirrorEdits;

/** Every block set; {@link MirrorEdits} keeps those set during a player's action on the integrated server. */
@Mixin(Level.class)
abstract class LevelGhostEditMixin {

	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
			at = @At("RETURN"))
	private void phantasmonEvents$edit(BlockPos pos, BlockState state, int flags, int recursion,
			CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			MirrorEdits.onSetBlock((Level) (Object) this, pos);
		}
	}
}
