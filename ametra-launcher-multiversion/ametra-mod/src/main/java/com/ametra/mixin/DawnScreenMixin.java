package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ametra.DawnOptions;

import net.minecraft.client.gui.screen.Screen;

/** Ajustes Dawn > Graphics: Disable Dirt Background (require = 0: si cambia el nombre del método, no rompe). */
@Mixin(Screen.class)
public abstract class DawnScreenMixin {
    @Inject(method = "renderBackgroundTexture", at = @At("HEAD"), cancellable = true, require = 0)
    private void ametra$noDirt(CallbackInfo ci) {
        if (DawnOptions.get("disableDirtBackground")) ci.cancel();
    }
}
