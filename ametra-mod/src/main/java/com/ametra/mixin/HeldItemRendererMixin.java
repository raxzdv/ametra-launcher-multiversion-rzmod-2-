package com.ametra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ametra.Features;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

/**
 * Viewmodel: mueve y cambia el tamaño de la mano / objeto en primera persona.
 * Se hace push() al empezar y pop() al terminar renderFirstPersonItem, así no se acumula entre las dos manos.
 * Si el método no existe en tu versión, simplemente no hace nada (require = 0).
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), require = 0)
    private void ametra$vmBegin(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                                ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                int light, CallbackInfo ci) {
        matrices.push();
        if (!Features.viewmodelOn()) return;
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == Arm.RIGHT ? 1f : -1f;
        matrices.translate(Features.vm("x", 0f) * side, Features.vm("y", 0f), Features.vm("z", 0f));
        float s = Features.vm("s", 1f);
        matrices.scale(s, s, s);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"), require = 0)
    private void ametra$vmEnd(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                              ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                              int light, CallbackInfo ci) {
        matrices.pop();
    }
}
