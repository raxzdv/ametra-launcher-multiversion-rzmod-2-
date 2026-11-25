package com.ametra;

import net.minecraft.util.Identifier;

/** Textura de skin + tipo de brazos, igual en todas las versiones de Minecraft (lo rellena Compat.localSkin). */
final class SkinInfo {
    final Identifier tex;
    final boolean slim;

    SkinInfo(Identifier tex, boolean slim) {
        this.tex = tex;
        this.slim = slim;
    }
}
