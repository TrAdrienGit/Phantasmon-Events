package com.mystaria.events.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

import com.mystaria.events.trahison.world.GhostLook;

/** T6.3 (S6): with the "hidden" setting, a living player does not render the ghosts at all (body, name tag, shadow). */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherGhostMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private <E extends Entity> void phantasmonEvents$hideGhosts(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (GhostLook.hidden(entity)) {
			cir.setReturnValue(false);
		}
	}
}
