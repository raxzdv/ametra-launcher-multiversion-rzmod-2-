package com.ametra;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Hitboxes de colores (jugadores, mobs u objetos). Más ligero y con color propio que el F3+B de Minecraft. */
public final class Hitboxes {
    private Hitboxes() {}

    public static final String[] MODES = {"Jugadores", "Jugadores y mobs", "Todo"};

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(Hitboxes::render);
    }

    private static void render(WorldRenderContext ctx) {
        HudModule m = Hud.HITBOX;
        if (m == null || !m.cfg.enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || ctx.consumers() == null || ctx.matrixStack() == null) return;

        int mode = Math.max(0, Math.min(2, Math.round(m.cfg.ex("mode", 0f))));
        double range = Math.max(8f, m.cfg.ex("range", 32f));
        double range2 = range * range;
        int rgb = m.cfg.chroma ? Colors.hsv((System.currentTimeMillis() % 4000L) / 4000f, 0.6f, 1f) : m.cfg.color;
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;

        float delta = Compat.tickDelta(mc);
        Vec3d cam = ctx.camera().getPos();
        boolean third = !mc.options.getPerspective().isFirstPerson();

        MatrixStack ms = ctx.matrixStack();
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getLines());

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player && !third) continue;
            if (e.squaredDistanceTo(cam) > range2) continue;
            if (mode == 0 && !(e instanceof PlayerEntity)) continue;
            if (mode == 1 && !(e instanceof LivingEntity)) continue;
            if (e.isSpectator()) continue;

            Box box = e.getBoundingBox().offset(e.getLerpedPos(delta).subtract(e.getPos()));
            if (e == mc.targetedEntity) {
                // el que tienes en la mira se pinta en rojo para ver cuándo estás apuntando bien
                WorldRenderer.drawBox(ms, vc, box, 1f, 0.3f, 0.3f, 1f);
            } else {
                WorldRenderer.drawBox(ms, vc, box, r, g, b, 1f);
            }
        }
        ms.pop();
    }
}
