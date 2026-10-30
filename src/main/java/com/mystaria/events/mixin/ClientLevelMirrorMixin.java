package com.mystaria.events.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.mystaria.events.hub.MirrorCapture;
import com.mystaria.events.hub.MirrorGhosts;

/**
 * Every block the server sets (block and section updates): mirror hubs' source zones send it on (D-38); under a ghost
 * block shown, it is kept aside instead ({@link MirrorGhosts}).
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMirrorMixin {

	@Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"), cancellable = true)
	private void phantasmonEvents$mirror(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
		ClientLevel level = (ClientLevel) (Object) this;
		BlockPos at = pos.immutable();
		MirrorCapture.onBlock(at, MirrorGhosts.realState(level, at), state);
		if (MirrorGhosts.interceptServerBlock(level, at, state)) {
			ci.cancel();
		}
	}
}
