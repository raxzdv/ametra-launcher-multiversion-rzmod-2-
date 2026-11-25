package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ametra.Crosshair;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;

/** Quita la mira de Minecraft cuando el módulo Crosshair de Ametra está activo (require = 0: si cambia el método, solo no se oculta). */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void ametra$crosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Crosshair.replacesVanilla()) ci.cancel();
    }
}
