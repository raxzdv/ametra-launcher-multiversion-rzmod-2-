package com.ametra;

import java.util.Random;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Efectos en pantallas de Minecraft: botón "RzClient" y partículas/logo animado en el menú principal. */
public final class Fx {
    private Fx() {}

    private static final int N = 70;
    private static final float[] RX = new float[N], RY = new float[N], RS = new float[N], RP = new float[N];

    static {
        Random r = new Random(1234);
        for (int i = 0; i < N; i++) {
            RX[i] = r.nextFloat();
            RY[i] = r.nextFloat();
            RS[i] = 0.015f + r.nextFloat() * 0.04f;
            RP[i] = r.nextFloat() * 6.28f;
        }
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen || screen instanceof GameMenuScreen) {
                Screens.getButtons(screen).add(
                    ButtonWidget.builder(Text.literal("RzClient"), b -> client.setScreen(new AmetraScreen(screen)))
                        .dimensions(6, 6, 58, 20).build());
            }
            if (screen instanceof TitleScreen) {
                ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> title(ctx, s.width, s.height));
            }
        });
    }

    /** Partículas flotantes (las usa también el menú principal de Ametra). */
    static void particles(DrawContext ctx, int w, int h, float t, int count) {
        int ac = AmetraConfig.accent;
        for (int i = 0; i < Math.min(N, count); i++) {
            float y = (((RY[i] - t * RS[i]) % 1f) + 1f) % 1f;
            float x = RX[i] * w + (float) Math.sin(t * 0.6f + RP[i]) * 10f;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.5f + RP[i] * 3f);
            int sz = (i % 5 == 0) ? 2 : 1;
            Gfx.rect(ctx, x, y * h, sz, sz, ac, 0.12f + 0.38f * tw);
        }
    }

    private static void title(DrawContext ctx, int w, int h) {
        if (!AmetraConfig.titleFx) return;
        float t = (System.currentTimeMillis() % 100000000L) / 1000f;
        int ac = AmetraConfig.accent;

        for (int i = 0; i < N; i++) {
            float y = (((RY[i] - t * RS[i]) % 1f) + 1f) % 1f;
            float x = RX[i] * w + (float) Math.sin(t * 0.6f + RP[i]) * 10f;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.5f + RP[i] * 3f);
            int sz = (i % 5 == 0) ? 2 : 1;
            Gfx.rect(ctx, x, y * h, sz, sz, ac, 0.12f + 0.38f * tw);
        }

        // marca en la esquina inferior izquierda
        float bob = (float) Math.sin(t * 2f) * 1.5f;
        Gfx.glow(ctx, 24, h - 26 + bob, 18, ac, 0.5f);
        Gfx.logo(ctx, 8, h - 42 + bob, 32, 1f);
        Gfx.bold(ctx, "RzClient", 46, h - 32, ac, 1f, true);
        Gfx.text(ctx, "Menú dentro del juego: Shift derecho", 46, h - 20, 0xA6A6B8, 1f, true);
        float sh = (t * 90f) % 160f;
        Gfx.rect(ctx, 46, h - 9, 120, 1, ac, 0.25f);
        Gfx.rect(ctx, 46 + Math.min(sh, 100), h - 9, 20, 1, 0xFFFFFF, 0.7f);
    }
}
