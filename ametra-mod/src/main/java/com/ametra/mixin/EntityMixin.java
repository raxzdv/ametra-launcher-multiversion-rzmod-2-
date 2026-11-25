package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ametra.Features;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/** Freelook: con la tecla mantenida el ratón mueve la cámara y no al personaje (require = 0). */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
    private void ametra$freelook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!Features.freelook) return;
        if ((Object) this != MinecraftClient.getInstance().player) return;
        Features.flYaw += (float) cursorDeltaX * 0.15f;
        Features.flPitch = MathHelper.clamp(Features.flPitch + (float) cursorDeltaY * 0.15f, -90f, 90f);
        ci.cancel();
    }
}
