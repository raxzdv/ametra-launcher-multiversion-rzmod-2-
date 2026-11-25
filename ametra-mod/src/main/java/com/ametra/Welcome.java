package com.ametra;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Animación de bienvenida: una tarjeta con el logo baja desde arriba al entrar a un mundo o servidor. */
public final class Welcome {
    private Welcome() {}

    private static long start = -1;
    private static final float DURATION = 4.6f;

    public static void start() {
        start = AmetraConfig.welcome ? System.currentTimeMillis() : -1;
    }

    /** Vista previa desde el menú (aunque la bienvenida esté desactivada). */
    public static void preview() {
        start = System.currentTimeMillis();
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        if (start < 0) return;
        float t = (System.currentTimeMillis() - start) / 1000f;
        if (t > DURATION) { start = -1; return; }

        float in = Colors.clamp01(t / 0.7f);
        float out = Colors.clamp01((DURATION - t) / 0.6f);
        float k = in < 1f ? Colors.easeOutBack(in) : Colors.ease(out);
        float a = Colors.clamp01(Math.min(in * 1.6f, out * 1.6f));

        int pw = 226, ph = 44;
        float x = (mc.getWindow().getScaledWidth() - pw) / 2f;
        float y = -ph + (10 + ph) * k;
        int ac = AmetraConfig.accent;

        // brillo + tarjeta
        Gfx.rbox(ctx, x - 3, y - 3, pw + 6, ph + 6, ac, 0.10f * a);
        Gfx.rbox(ctx, x - 1, y - 1, pw + 2, ph + 2, ac, 0.55f * a);
        Gfx.rbox(ctx, x, y, pw, ph, 0x0B0B11, 0.95f * a);

        // logo con pulso y halo
        float pulse = 1f + 0.06f * (float) Math.sin(t * 5f);
        float size = 32f * pulse;
        Gfx.glow(ctx, x + 24, y + 22, 24 + 4 * (float) Math.sin(t * 3f), ac, 0.5f * a);
        Gfx.logo(ctx, x + 24 - size / 2f, y + 22 - size / 2f, size, a);

        Gfx.bold(ctx, "RzClient", x + 48, y + 9, ac, a, true);
        String key = AmetraClient.MENU_KEY == null ? "SHIFT DER." : AmetraClient.MENU_KEY.getBoundKeyLocalizedText().getString();
        Gfx.text(ctx, "Pulsa " + key + " para abrir el menú", x + 48, y + 22, 0xA6A6B8, a, false);

        // barra de tiempo + destello que la recorre
        float prog = 1f - t / DURATION;
        Gfx.rect(ctx, x + 4, y + ph - 4, (pw - 8) * prog, 1, ac, 0.9f * a);
        float sh = ((t * 160f) % (pw + 40)) - 20;
        Gfx.rect(ctx, x + Math.max(4, sh), y + ph - 4, Math.max(0, Math.min(18, pw - 4 - sh)), 1, 0xFFFFFF, 0.5f * a);
    }
}
