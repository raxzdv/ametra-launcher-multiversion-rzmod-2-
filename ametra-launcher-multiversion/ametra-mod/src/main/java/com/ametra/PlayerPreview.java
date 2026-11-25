package com.ametra;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Dibuja tu personaje (con los cosméticos puestos) dentro del menú de Ametra, girado a voluntad.
 * Es la misma técnica que usa el inventario de supervivencia. Va en una clase aparte para que, si una versión de
 * Minecraft cambia esta llamada, solo haya que tocar este archivo.
 */
final class PlayerPreview {
    private PlayerPreview() {}

    /** cx, cy = centro (coordenadas del menú), size = píxeles por bloque, spinDeg = giro (0 = de frente, 180 = de espaldas). */
    static void draw(DrawContext ctx, float cx, float cy, int size, float spinDeg) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        float ob = p.bodyYaw, oy = p.getYaw(), op = p.getPitch(), oh = p.headYaw, oph = p.prevHeadYaw;
        float yaw = 180f + spinDeg;
        p.bodyYaw = yaw;
        p.setYaw(yaw);
        p.setPitch(0f);
        p.headYaw = yaw;
        p.prevHeadYaw = yaw;
        try {
            Compat.drawEntity(ctx, cx, cy, size, p);
        } finally {
            p.bodyYaw = ob;
            p.setYaw(oy);
            p.setPitch(op);
            p.headYaw = oh;
            p.prevHeadYaw = oph;
        }
    }
}
