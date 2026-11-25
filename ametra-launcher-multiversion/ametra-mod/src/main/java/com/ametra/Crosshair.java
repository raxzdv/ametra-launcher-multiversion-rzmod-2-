package com.ametra;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Crosshair propio. Cuando el módulo está activo, el mixin InGameHudMixin cancela la mira de Minecraft
 * y esta clase dibuja la nuestra (cruz, punto, círculo o cuadro) con color, tamaño, hueco y grosor ajustables.
 */
public final class Crosshair {
    private Crosshair() {}

    public static final String[] STYLES = {"Cruz", "Cruz + punto", "Punto", "Círculo", "Cuadro", "Imagen propia"};
    private static float dyn = 0f;

    /** ¿Debe ocultarse la mira de Minecraft? */
    public static boolean replacesVanilla() {
        HudModule m = Hud.CROSSHAIR;
        return m != null && m.cfg.enabled;
    }

    private static int ex(String k, float d) {
        return Math.round(Hud.CROSSHAIR.cfg.ex(k, d));
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        HudModule m = Hud.CROSSHAIR;
        if (m == null || !m.cfg.enabled || mc.player == null) return;
        if (mc.currentScreen != null || mc.options.hudHidden) return;
        if (!mc.options.getPerspective().isFirstPerson()) return;

        int rgb = m.cfg.chroma ? Colors.hsv((System.currentTimeMillis() % 4000L) / 4000f, 0.6f, 1f) : m.cfg.color;
        int col = 0xFF000000 | (rgb & 0xFFFFFF);
        int style = Math.max(0, Math.min(STYLES.length - 1, ex("style", 0f)));
        int size = Math.max(1, ex("size", 5f));
        if (style == 5 && !Custom.hasCrosshair()) style = 0; // sin imagen elegida: cruz normal
        int gap = Math.max(0, ex("gap", 3f));
        int th = Math.max(1, ex("thick", 1f));

        // hueco dinámico: se abre un poco al correr, saltar o agacharte
        boolean busy = !mc.player.isOnGround() || mc.player.isSprinting()
            || mc.player.getVelocity().horizontalLengthSquared() > 0.0004;
        dyn += ((busy ? 1f : 0f) - dyn) * 0.2f;
        int g = gap + Math.round(dyn * 3f);

        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;
        int hx = cx - th / 2, hy = cy - th / 2;

        switch (style) {
            case 5 -> Custom.drawCrosshair(ctx, cx, cy, size * 4);
            case 0, 1 -> {
                int[][] bars = {
                    {hx - g - size, hy, hx - g, hy + th},
                    {hx + th + g, hy, hx + th + g + size, hy + th},
                    {hx, hy - g - size, hx + th, hy - g},
                    {hx, hy + th + g, hx + th, hy + th + g + size}
                };
                for (int[] b : bars) ctx.fill(b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1, 0xB0000000);
                if (style == 1) ctx.fill(hx - 1, hy - 1, hx + th + 1, hy + th + 1, 0xB0000000);
                for (int[] b : bars) ctx.fill(b[0], b[1], b[2], b[3], col);
                if (style == 1) ctx.fill(hx, hy, hx + th, hy + th, col);
            }
            case 2 -> {
                int s = Math.max(2, th + 1);
                ctx.fill(cx - s / 2 - 1, cy - s / 2 - 1, cx - s / 2 + s + 1, cy - s / 2 + s + 1, 0xB0000000);
                ctx.fill(cx - s / 2, cy - s / 2, cx - s / 2 + s, cy - s / 2 + s, col);
            }
            case 3 -> {
                int r = size + g;
                int pts = Math.max(16, r * 4);
                for (int i = 0; i < pts; i++) {
                    double a = i * (Math.PI * 2.0 / pts);
                    int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
                    ctx.fill(px - th / 2, py - th / 2, px - th / 2 + th, py - th / 2 + th, col);
                }
            }
            default -> {
                int r = size + g;
                int x1 = cx - r, y1 = cy - r, x2 = cx + r + 1, y2 = cy + r + 1;
                ctx.fill(x1, y1, x2, y1 + th, col);
                ctx.fill(x1, y2 - th, x2, y2, col);
                ctx.fill(x1, y1 + th, x1 + th, y2 - th, col);
                ctx.fill(x2 - th, y1 + th, x2, y2 - th, col);
            }
        }

        // indicador de ataque (barrita bajo la mira mientras el golpe se recarga)
        float cd = mc.player.getAttackCooldownProgress(0f);
        if (cd < 0.999f) {
            int bw = 16, by = cy + g + size + th + 5;
            ctx.fill(cx - bw / 2 - 1, by - 1, cx + bw / 2 + 1, by + 3, 0xB0000000);
            ctx.fill(cx - bw / 2, by, cx - bw / 2 + Math.round(bw * cd), by + 2, col);
        }
    }
}
