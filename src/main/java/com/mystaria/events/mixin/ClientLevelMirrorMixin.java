package com.mystaria.events.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.mystaria.events.hub.MirrorCapture;

/** Every block the server sets (block and section updates): mirror hubs' source zones send it on (D-38). */
@Mixin(ClientLevel.class)
abstract class ClientLevelMirrorMixin {

	@Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
	private void phantasmonEvents$mirror(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
		MirrorCapture.onBlock(pos.immutable(), ((ClientLevel) (Object) this).getBlockState(pos), state);
	}
}
