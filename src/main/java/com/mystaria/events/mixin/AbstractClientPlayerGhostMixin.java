package com.mystaria.events.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;

import com.mystaria.events.trahison.world.GhostLook;

/**
 * T6.3 (S6): the skin of a player known to be dead is a black and white copy of his own.
 *
 * <p>Wraps the whole method (2026-11-19) rather than hooking its return: LeagueWay's dressing replaces skins at the
 * method's start and returns early, so a RETURN hook never ran and ghosts kept their colours. A wrap sees whatever skin
 * any mod ended on.
 */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerGhostMixin {

	@WrapMethod(method = "getSkin", require = 0)
	private PlayerSkin phantasmonEvents$gray(Operation<PlayerSkin> original) {
		PlayerSkin skin = original.call();
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		if (skin != null && (GhostLook.isGhost(self) || GhostLook.isSpectator(self))) {
			return GhostLook.gray(skin);
		}
		return skin;
	}
}
