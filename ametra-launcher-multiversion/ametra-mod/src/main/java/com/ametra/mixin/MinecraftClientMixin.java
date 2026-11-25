package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.ametra.AmetraConfig;
import com.ametra.AmetraTitleScreen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;

/** Cambia el menú principal de Minecraft por el de Ametra (se puede apagar en Tema). */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen ametra$title(Screen screen) {
        if (screen != null && screen.getClass() == TitleScreen.class && AmetraConfig.customTitle) {
            return new AmetraTitleScreen();
        }
        return screen;
    }
}
