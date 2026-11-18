package com.mystaria.events.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.battle.BattleMessageHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket;

import net.minecraft.client.Minecraft;

import com.mystaria.events.reborn.LeagueRebornHud;

/**
 * Battle messages, as every client receives them (host, guest, spectators): League Reborn's field display follows the
 * field changes and the turns in them.
 */
@Mixin(value = BattleMessageHandler.class, remap = false)
abstract class BattleMessageFieldMixin {

	@Inject(method = "handle", at = @At("HEAD"), remap = false)
	private void phantasmonEvents$field(BattleMessagePacket packet, Minecraft client, CallbackInfo ci) {
		LeagueRebornHud.onMessages(packet.getMessages());
	}
}
