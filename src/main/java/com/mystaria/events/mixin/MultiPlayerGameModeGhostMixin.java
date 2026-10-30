package com.mystaria.events.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

import com.mystaria.events.hub.MirrorGhosts;

/**
 * Ghost blocks of the mirror hubs: the server doesn't know them, so they can't be broken — no mining, nothing sent —
 * and a right click on one never reaches the server (which would place a block in the air there).
 */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeGhostMixin {

	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
	private void phantasmonEvents$unbreakableStart(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
		if (MirrorGhosts.isGhost(minecraft.level, pos)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
	private void phantasmonEvents$unbreakableContinue(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
		if (MirrorGhosts.isGhost(minecraft.level, pos)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
	private void phantasmonEvents$unbreakable(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (MirrorGhosts.isGhost(minecraft.level, pos)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void phantasmonEvents$useGhost(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (MirrorGhosts.isGhost(minecraft.level, hit.getBlockPos())) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
}
