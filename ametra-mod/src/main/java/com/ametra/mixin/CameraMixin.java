package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.ametra.Features;

import net.minecraft.client.render.Camera;

/** Freelook: la cámara usa su propia rotación mientras la tecla está mantenida (require = 0). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"), require = 0)
    private void ametra$freelook(Camera self, float yaw, float pitch) {
        if (Features.freelook) this.setRotation(Features.flYaw, Features.flPitch);
        else this.setRotation(yaw, pitch);
    }
}
