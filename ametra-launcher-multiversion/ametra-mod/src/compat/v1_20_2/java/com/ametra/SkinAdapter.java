package com.ametra;

import net.minecraft.client.util.SkinTextures;

/** Esta versión no cambia la skin (la API de SkinTextures no coincide). Devuelve la original sin tocarla. */
public final class SkinAdapter {
    private SkinAdapter() {}

    public static SkinTextures apply(SkinTextures base) {
        return base;
    }
}
