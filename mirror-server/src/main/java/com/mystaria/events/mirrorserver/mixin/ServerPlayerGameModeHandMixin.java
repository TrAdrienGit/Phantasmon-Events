package com.mystaria.events.mirrorserver.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

import com.mystaria.events.mirrorserver.HandEdits;

/** A player's own actions on this server (place, use, break): an operator's may be ghost blocks of a mirror copy. */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeHandMixin {

	@Shadow
	@Final
	protected ServerPlayer player;

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void mirrorServer$beginUseOn(ServerPlayer actor, Level level, ItemStack stack, InteractionHand hand,
			BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		HandEdits.begin(actor);
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void mirrorServer$endUseOn(ServerPlayer actor, Level level, ItemStack stack, InteractionHand hand,
			BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		HandEdits.end();
	}

	@Inject(method = "useItem", at = @At("HEAD"))
	private void mirrorServer$beginUse(ServerPlayer actor, Level level, ItemStack stack, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		HandEdits.begin(actor);
	}

	@Inject(method = "useItem", at = @At("RETURN"))
	private void mirrorServer$endUse(ServerPlayer actor, Level level, ItemStack stack, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		HandEdits.end();
	}

	@Inject(method = "destroyBlock", at = @At("HEAD"))
	private void mirrorServer$beginDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		HandEdits.begin(player);
	}

	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void mirrorServer$endDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		HandEdits.end();
	}
}
