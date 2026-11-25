package com.ametra;

import org.joml.Quaternionf;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;

/**
 * Capa de compatibilidad para Minecraft 1.20.2, 1.20.3 y 1.20.4.
 * El resto del mod llama solo a estos métodos para todo lo que cambia entre versiones de Minecraft.
 */
public final class Compat {
    private Compat() {}

    public static Identifier id(String ns, String path) {
        return new Identifier(ns, path);
    }

    public static void registerHud() {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> Hud.render(ctx));
    }

    public static float tickDelta(MinecraftClient mc) {
        return mc.getTickDelta();
    }

    public static void end(VertexConsumer vc) {
        vc.next();
    }

    // Antes de 1.20.5 el efecto es directamente un StatusEffect
    public static String effectName(StatusEffectInstance e) {
        return e.getEffectType().getName().getString();
    }

    public static int effectColor(StatusEffectInstance e) {
        return e.getEffectType().getColor();
    }

    public static ServerInfo newServerInfo(String name, String host) {
        return new ServerInfo(name, host, ServerInfo.ServerType.OTHER);
    }

    public static void joinServer(MinecraftClient mc, String name, String host) {
        ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), mc, ServerAddress.parse(host), newServerInfo(name, host), false);
    }

    /** Esta versión dibuja desde los pies: se baja el punto para que (cx, cy) sea el centro del jugador. */
    public static void drawEntity(DrawContext ctx, float cx, float cy, int size, ClientPlayerEntity p) {
        Quaternionf flip = new Quaternionf().rotateZ((float) Math.PI);
        int y = Math.round(cy + size * (p.getHeight() / 2f + 0.0625f));
        InventoryScreen.drawEntity(ctx, Math.round(cx), y, size, flip, null, p);
    }

    /** Skin del jugador del launcher para dibujarla en el menú principal (null si no se puede). */
    public static SkinInfo localSkin(MinecraftClient mc) {
        try {
            SkinTextures st = mc.getSkinProvider().getSkinTextures(mc.getGameProfile());
            return new SkinInfo(st.texture(), st.model() == SkinTextures.Model.SLIM);
        } catch (Throwable t) {
            return null;
        }
    }
}
