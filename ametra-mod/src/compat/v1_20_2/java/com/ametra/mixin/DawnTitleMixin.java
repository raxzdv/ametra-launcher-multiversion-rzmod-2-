package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.gui.screen.TitleScreen;

/** En esta familia TitleScreen no tiene renderPanoramaBackground: "Disable Panorama" no se aplica (mixin vacío). */
@Mixin(TitleScreen.class)
public abstract class DawnTitleMixin {
}
