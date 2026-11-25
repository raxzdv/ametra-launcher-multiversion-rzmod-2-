package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ametra.DawnOptions;

import net.minecraft.client.render.GameRenderer;

/** Ajustes Dawn > Graphics: Minimal View Bobbing. Quita el balanceo de la cámara al caminar (require = 0). */
@Mixin(GameRenderer.class)
public abstract class DawnBobMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
    private void ametra$minimalBob(CallbackInfo ci) {
        if (DawnOptions.get("minimalViewBobbing")) ci.cancel();
    }
}
