package com.ametra.mixin;

import com.ametra.SkinAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.ametra.OfflineSkin;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;

/** Cuentas no premium: tu jugador usa la skin elegida en el launcher (require = 0: si cambia el nombre del método no rompe). */
@Mixin(PlayerListEntry.class)
public abstract class SkinMixin {
    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true, require = 0)
    private void ametra$offlineSkin(CallbackInfoReturnable<SkinTextures> cir) {
        PlayerListEntry self = (PlayerListEntry) (Object) this;
        if (!OfflineSkin.isMe(self.getProfile().getId(), self.getProfile().getName())) return;
        SkinTextures t = SkinAdapter.apply(cir.getReturnValue());
        if (t != null) cir.setReturnValue(t);
    }
}
