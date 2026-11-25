package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ametra.net.Probe;

import net.minecraft.client.Mouse;

/** Lectura de entrada del ratón (require = 0: si una versión cambia la firma, simplemente no hace nada). */
@Mixin(Mouse.class)
public abstract class MouseInputMixin {
    @Inject(method = "onCursorPos", at = @At("HEAD"), require = 0)
    private void ametra$cursor(long window, double x, double y, CallbackInfo ci) {
        Probe.cursor(x, y);
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), require = 0)
    private void ametra$button(long window, int button, int action, int mods, CallbackInfo ci) {
        Probe.button(button, action);
    }
}
