package com.ametra;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Helpers de dibujo compartidos por el HUD, el menú y los efectos. */
public final class Gfx {
    private Gfx() {}

    private static final Identifier L16 = Compat.id("ametra", "textures/gui/logo16.png");
    private static final Identifier L32 = Compat.id("ametra", "textures/gui/logo32.png");
    private static final Identifier L64 = Compat.id("ametra", "textures/gui/logo64.png");

    /** Fuente propia del HUD (assets/ametra/font/hud.json, Poppins Medium). */
    private static final Identifier HUD_FONT = Compat.id("ametra", "hud");

    // Caché de textos y anchos: el HUD pide lo mismo cada fotograma y así no se crean objetos nuevos todo el rato.
    private static final java.util.HashMap<String, MutableText> TXT = new java.util.HashMap<>();
    private static final java.util.HashMap<String, MutableText> TXT_B = new java.util.HashMap<>();
    private static final java.util.HashMap<String, Integer> WID = new java.util.HashMap<>();
    private static final java.util.HashMap<String, Integer> WID_B = new java.util.HashMap<>();
    private static boolean cachedFont = true;

    private static void checkCache() {
        if (cachedFont != AmetraConfig.hudFont) {
            cachedFont = AmetraConfig.hudFont;
            clearCache();
        }
        if (TXT.size() > 400 || TXT_B.size() > 400 || WID.size() > 400 || WID_B.size() > 400) clearCache();
    }

    public static void clearCache() {
        TXT.clear();
        TXT_B.clear();
        WID.clear();
        WID_B.clear();
    }

    private static MutableText styled(String s, boolean bold) {
        checkCache();
        java.util.HashMap<String, MutableText> m = bold ? TXT_B : TXT;
        MutableText t = m.get(s);
        if (t == null) {
            Style st = Style.EMPTY;
            if (AmetraConfig.hudFont) st = st.withFont(HUD_FONT);
            if (bold) st = st.withBold(true);
            t = Text.literal(s).setStyle(st);
            m.put(s, t);
        }
        return t;
    }

    /** Rectángulo con esquinas redondeadas de 1 px. */
    public static void rbox(DrawContext ctx, float x, float y, float w, float h, int rgb, float alpha) {
        if (alpha <= 0.004f) return;
        int c = Colors.argb(rgb, alpha);
        int X = Math.round(x), Y = Math.round(y), W = Math.round(w), H = Math.round(h);
        if (W < 3 || H < 3) { ctx.fill(X, Y, X + W, Y + H, c); return; }
        ctx.fill(X + 1, Y, X + W - 1, Y + H, c);
        ctx.fill(X, Y + 1, X + 1, Y + H - 1, c);
        ctx.fill(X + W - 1, Y + 1, X + W, Y + H - 1, c);
    }

    public static void rect(DrawContext ctx, float x, float y, float w, float h, int rgb, float alpha) {
        if (alpha <= 0.004f) return;
        ctx.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), Colors.argb(rgb, alpha));
    }

    /** Borde de 1 px. */
    public static void outline(DrawContext ctx, float x, float y, float w, float h, int rgb, float alpha) {
        if (alpha <= 0.004f) return;
        rect(ctx, x, y, w, 1, rgb, alpha);
        rect(ctx, x, y + h - 1, w, 1, rgb, alpha);
        rect(ctx, x, y + 1, 1, h - 2, rgb, alpha);
        rect(ctx, x + w - 1, y + 1, 1, h - 2, rgb, alpha);
    }

    public static void text(DrawContext ctx, String s, float x, float y, int rgb, float alpha, boolean shadow) {
        if (alpha < 0.04f) return; // con alpha < 4/255 Minecraft pinta el texto opaco
        ctx.drawText(MinecraftClient.getInstance().textRenderer, styled(s, false), Math.round(x), Math.round(y), Colors.argb(rgb, alpha), shadow);
    }

    public static void bold(DrawContext ctx, String s, float x, float y, int rgb, float alpha, boolean shadow) {
        if (alpha < 0.04f) return;
        ctx.drawText(MinecraftClient.getInstance().textRenderer, styled(s, true),
                Math.round(x), Math.round(y), Colors.argb(rgb, alpha), shadow);
    }

    public static int width(String s) {
        checkCache();
        Integer w = WID.get(s);
        if (w == null) {
            w = MinecraftClient.getInstance().textRenderer.getWidth(styled(s, false));
            WID.put(s, w);
        }
        return w;
    }

    public static int boldWidth(String s) {
        checkCache();
        Integer w = WID_B.get(s);
        if (w == null) {
            w = MinecraftClient.getInstance().textRenderer.getWidth(styled(s, true));
            WID_B.put(s, w);
        }
        return w;
    }

    /** Texto escalado (para títulos). */
    public static void textScaled(DrawContext ctx, String s, float x, float y, float scale, int rgb, float alpha, boolean bold) {
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(x, y, 0f);
        ms.scale(scale, scale, 1f);
        if (bold) bold(ctx, s, 0, 0, rgb, alpha, true); else text(ctx, s, 0, 0, rgb, alpha, true);
        ms.pop();
    }

    /** Logo de Ametra (gema). */
    public static void logo(DrawContext ctx, float x, float y, float size, float alpha) {
        if (alpha <= 0.01f) return;
        int t = size <= 20 ? 16 : size <= 40 ? 32 : 64;
        Identifier id = t == 16 ? L16 : t == 32 ? L32 : L64;
        // Se dibuja la textura ENTERA (región t×t) estirada al tamaño pedido, sin escalar la matriz,
        // para que el logo no salga cortado a la mitad con ningún tamaño de GUI.
        int px = Math.round(size);
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(x, y, 0f);
        Compat.drawTexTint(ctx, id, 0, 0, px, px, 0f, 0f, t, t, t, t, Colors.clamp01(alpha));
        ms.pop();
    }

    /** Brillo suave alrededor de un punto (capas de cajas translúcidas). */
    public static void glow(DrawContext ctx, float cx, float cy, float r, int rgb, float alpha) {
        for (int i = 4; i >= 1; i--) {
            float rr = r * i / 4f;
            rbox(ctx, cx - rr, cy - rr, rr * 2, rr * 2, rgb, alpha * (5 - i) * 0.12f);
        }
    }
}
