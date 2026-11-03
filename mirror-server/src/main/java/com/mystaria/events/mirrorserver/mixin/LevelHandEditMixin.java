package com.mystaria.events.mirrorserver.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.mystaria.events.mirrorserver.HandEdits;

/** Every block set; {@link HandEdits} keeps those set during an operator's action. */
@Mixin(Level.class)
abstract class LevelHandEditMixin {

	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
			at = @At("RETURN"))
	private void mirrorServer$edit(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			HandEdits.onSetBlock((Level) (Object) this, pos);
		}
	}
}
