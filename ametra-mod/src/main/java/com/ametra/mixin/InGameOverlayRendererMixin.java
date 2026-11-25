package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ametra.Features;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;

/** Fuego bajo: baja el fuego que se dibuja en pantalla cuando te quemas (require = 0). */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), require = 0)
    private static void ametra$fireBegin(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        matrices.push();
        matrices.translate(0f, -Features.lowFire(), 0f);
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"), require = 0)
    private static void ametra$fireEnd(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        matrices.pop();
    }
}
