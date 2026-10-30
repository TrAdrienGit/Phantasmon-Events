package com.mystaria.events.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import com.mystaria.events.hub.MirrorEdits;

/** A player's own actions on the integrated server (place, use, break): what they change may be ghost blocks. */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeGhostMixin {

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void phantasmonEvents$beginUseOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
			BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		MirrorEdits.begin();
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void phantasmonEvents$endUseOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
			BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		MirrorEdits.end();
	}

	@Inject(method = "useItem", at = @At("HEAD"))
	private void phantasmonEvents$beginUse(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		MirrorEdits.begin();
	}

	@Inject(method = "useItem", at = @At("RETURN"))
	private void phantasmonEvents$endUse(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		MirrorEdits.end();
	}

	@Inject(method = "destroyBlock", at = @At("HEAD"))
	private void phantasmonEvents$beginDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		MirrorEdits.begin();
	}

	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void phantasmonEvents$endDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		MirrorEdits.end();
	}
}
