package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.ametra.Features;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;

/** Zoom: divide el campo de visión. Si el método no existe en tu versión, simplemente no hace zoom (require = 0). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void ametra$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        float z = Features.zoomAmount();
        if (z > 1.001f) cir.setReturnValue(cir.getReturnValue() / z);
    }

    /** Sin sacudida al recibir daño (require = 0: si cambia el método, solo no se quita). */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void ametra$noHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (Features.noHurtCam()) ci.cancel();
    }
}
