package com.mystaria.events.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.world.GhostLook;

/** T6.3: the name of a ghost carries a "ghost" mark, so a living player cannot take him for a player. */
@Mixin(PlayerRenderer.class)
abstract class PlayerRendererGhostNameMixin {

	@ModifyVariable(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private Component phantasmonEvents$ghostMark(Component name, AbstractClientPlayer player) {
		return GhostLook.isGhost(player) ? Component.empty().append(name).append(" ").append(Component.translatable("phantasmon_events.trahison.ghost.mark")) : name;
	}
}
