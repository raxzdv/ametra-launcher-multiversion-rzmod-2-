package com.ametra;

import org.joml.Quaternionf;
import org.joml.Vector3f;

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
 * Capa de compatibilidad para Minecraft 1.21 y 1.21.1.
 * El resto del mod llama solo a estos métodos para todo lo que cambia entre versiones de Minecraft.
 */
public final class Compat {
    private Compat() {}

    public static Identifier id(String ns, String path) {
        return Identifier.of(ns, path);
    }

    /** Registra el dibujado del HUD de Ametra (el callback recibe un RenderTickCounter). */
    public static void registerHud() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> Hud.render(ctx));
    }

    public static float tickDelta(MinecraftClient mc) {
        return mc.getRenderTickCounter().getTickDelta(false);
    }

    /** Cierra un vértice: desde 1.21 ya no hace falta next(). */
    public static void end(VertexConsumer vc) {
    }

    public static String effectName(StatusEffectInstance e) {
        return e.getEffectType().value().getName().getString();
    }

    public static int effectColor(StatusEffectInstance e) {
        return e.getEffectType().value().getColor();
    }

    public static ServerInfo newServerInfo(String name, String host) {
        return new ServerInfo(name, host, ServerInfo.ServerType.OTHER);
    }

    public static void joinServer(MinecraftClient mc, String name, String host) {
        ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), mc, ServerAddress.parse(host), newServerInfo(name, host), false, null);
    }

    /** Dibuja al jugador centrado en (cx, cy), de frente. */
    public static void drawEntity(DrawContext ctx, float cx, float cy, int size, ClientPlayerEntity p) {
        Quaternionf flip = new Quaternionf().rotateZ((float) Math.PI);
        Vector3f off = new Vector3f(0f, p.getHeight() / 2f + 0.0625f, 0f);
        InventoryScreen.drawEntity(ctx, cx, cy, size, off, flip, null, p);
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

    // ---- añadido para RzClient multi-versión ----
    public static void drawTex(DrawContext ctx, Identifier id, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw, int th) {
        ctx.drawTexture(id, x, y, w, h, u, v, rw, rh, tw, th);
    }

    public static void drawTexTint(DrawContext ctx, Identifier id, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw, int th, float alpha) {
        ctx.setShaderColor(1f, 1f, 1f, alpha);
        ctx.drawTexture(id, x, y, w, h, u, v, rw, rh, tw, th);
        ctx.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Píxel en formato ABGR (el de NativeImage antes de 1.21.2). */
    public static int getPixel(net.minecraft.client.texture.NativeImage img, int x, int y) { return img.getColor(x, y); }
    public static void setPixel(net.minecraft.client.texture.NativeImage img, int x, int y, int abgr) { img.setColor(x, y, abgr); }

    public static void drawBox(net.minecraft.client.util.math.MatrixStack ms, VertexConsumer vc, net.minecraft.util.math.Box box, float r, float g, float b, float a) {
        net.minecraft.client.render.WorldRenderer.drawBox(ms, vc, box, r, g, b, a);
    }

    /** Cosméticos 3D sobre el jugador local (FeatureRenderer). */
    public static void registerCosmetics() {
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityType == net.minecraft.entity.EntityType.PLAYER && entityRenderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer pr) {
                registrationHelper.register(new CosmeticsFeature(pr));
            }
        });
    }
}
