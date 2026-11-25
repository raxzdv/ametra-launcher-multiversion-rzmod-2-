package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ametra.DawnOptions;

import net.minecraft.client.gui.screen.TitleScreen;

/** Ajustes Dawn > Graphics: Disable Panorama (require = 0: si cambia el nombre del método, no rompe). */
@Mixin(TitleScreen.class)
public abstract class DawnTitleMixin {
    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void ametra$noPanorama(CallbackInfo ci) {
        if (DawnOptions.get("disablePanoramaIngame")) ci.cancel();
    }
}
