package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.ametra.OfflineSkin;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;

/** 1.20 / 1.20.1 (antes de SkinTextures): cuentas no premium usan la skin elegida en el launcher (require = 0). */
@Mixin(PlayerListEntry.class)
public abstract class SkinMixin {
    @Inject(method = "getSkinTexture", at = @At("RETURN"), cancellable = true, require = 0)
    private void ametra$offlineSkin(CallbackInfoReturnable<Identifier> cir) {
        PlayerListEntry self = (PlayerListEntry) (Object) this;
        if (!OfflineSkin.isMe(self.getProfile().getId(), self.getProfile().getName())) return;
        if (OfflineSkin.ready()) cir.setReturnValue(OfflineSkin.textureId());
    }

    @Inject(method = "getModel", at = @At("RETURN"), cancellable = true, require = 0)
    private void ametra$offlineModel(CallbackInfoReturnable<String> cir) {
        PlayerListEntry self = (PlayerListEntry) (Object) this;
        if (!OfflineSkin.isMe(self.getProfile().getId(), self.getProfile().getName())) return;
        if (OfflineSkin.ready()) cir.setReturnValue(OfflineSkin.isSlim() ? "slim" : "default");
    }
}
