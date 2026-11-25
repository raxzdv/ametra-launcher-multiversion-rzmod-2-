package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ametra.Features;

import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;

/** Zoom con la rueda: con la tecla de zoom mantenida, la rueda cambia el nivel en vez de la ranura (require = 0). */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void ametra$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null && Features.scrollZoom(vertical)) ci.cancel();
    }
}
