package com.ametra;

import net.minecraft.client.util.SkinTextures;

/** 1.21.x: aplica la skin elegida en el launcher a tu jugador (cuentas no premium). */
public final class SkinAdapter {
    private SkinAdapter() {}

    public static SkinTextures apply(SkinTextures base) {
        if (!OfflineSkin.ready()) return null;
        return new SkinTextures(OfflineSkin.textureId(), null, base.capeTexture(), base.elytraTexture(),
            OfflineSkin.isSlim() ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE, false);
    }
}
