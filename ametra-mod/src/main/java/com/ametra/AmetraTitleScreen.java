package com.ametra;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.joml.Quaternionf;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Menú principal de Ametra con el mismo diseño que la pantalla de inicio del launcher:
 * saludo, tarjeta central con tu personaje animado (Idle / Caminar / Saludar / Bailar), botones de juego,
 * y columna de novedades. Reemplaza al menú de Minecraft (ver MinecraftClientMixin).
 */
public class AmetraTitleScreen extends Screen {
    private static final String[] LABELS = {"Un jugador", "Multijugador", "Opciones", "RzClient", "Salir"};
    private static final String[] ANIMS = {"Idle", "Caminar", "Saludar", "Bailar"};
    private static final int BH = 20, GAP = 5, PAD = 12;
    private static int anim = 0; // se recuerda mientras el juego esté abierto

    private final float[] hov = new float[LABELS.length];
    private final int[] bx = new int[LABELS.length], by = new int[LABELS.length], bw = new int[LABELS.length];
    private final int[] ax = new int[ANIMS.length], aw = new int[ANIMS.length];
    private int ay, ah = 14;
    private float time = 0f, appear = 0f;
    private long lastNs = System.nanoTime();
    private int heroX, heroY, heroW, heroH, newsX, newsW;

    public AmetraTitleScreen() {
        super(Text.literal("RzClient"));
    }

    @Override
    protected void init() {
        Servers.syncToMultiplayer();
        layout();
    }

    private void layout() {
        heroY = 50;
        newsW = Math.max(110, Math.min(150, (int) (width * 0.28f)));
        heroX = PAD;
        heroW = width - PAD * 2 - newsW - 10;
        heroH = height - heroY - PAD;
        newsX = heroX + heroW + 10;
        // botones en fila, abajo de la tarjeta
        int inner = 10;
        int n = LABELS.length;
        int bwEach = (heroW - inner * 2 - GAP * (n - 1)) / n;
        int y = heroY + heroH - inner - BH;
        for (int i = 0; i < n; i++) {
            bx[i] = heroX + inner + i * (bwEach + GAP);
            by[i] = y;
            bw[i] = bwEach;
        }
        // píldoras de animación, arriba a la derecha de la tarjeta
        int total = 0;
        for (String a : ANIMS) total += Gfx.width(a) + 14;
        int x = heroX + heroW - inner - total - GAP * (ANIMS.length - 1) / 2;
        ay = heroY + 8;
        for (int i = 0; i < ANIMS.length; i++) {
            aw[i] = Gfx.width(ANIMS[i]) + 14;
            ax[i] = x;
            x += aw[i] + 3;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private int hit(double mx, double my) {
        for (int i = 0; i < LABELS.length; i++) {
            if (mx >= bx[i] && mx < bx[i] + bw[i] && my >= by[i] && my < by[i] + BH) return i;
        }
        return -1;
    }

    private int hitAnim(double mx, double my) {
        if (my < ay || my >= ay + ah) return -1;
        for (int i = 0; i < ANIMS.length; i++) if (mx >= ax[i] && mx < ax[i] + aw[i]) return i;
        return -1;
    }

    private static String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h < 6 ? "Buenas noches" : h < 12 ? "Buenos días" : h < 20 ? "Buenas tardes" : "Buenas noches";
    }

    private static List<String> wrap(String s, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String t = line.length() == 0 ? word : line + " " + word;
            if (Gfx.width(t) > maxW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(word); }
            else line = new StringBuilder(t);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        layout();
        long n = System.nanoTime();
        float dt = Math.min(0.1f, (n - lastNs) / 1e9f);
        lastNs = n;
        time += dt;
        appear = Math.min(1f, appear + dt * (AmetraConfig.animations ? 2.2f : 20f));
        float ap = Colors.ease(appear);
        int ac = AmetraConfig.accent;
        int w = width, h = height;
        MinecraftClient mc = MinecraftClient.getInstance();

        Backgrounds.drawFull(ctx, w, h, time);
        Gfx.rect(ctx, 0, 0, w, h, 0x000000, 0.45f * ap); // oscurece el fondo para que se lea todo

        // ----- cabecera: saludo + datos -----
        String user = mc.getSession().getUsername();
        float slide = (1f - ap) * -10f;
        Gfx.logo(ctx, PAD, 12 + slide, 28, ap);
        String g1 = greeting() + ", ";
        float tx = PAD + 34;
        Gfx.textScaled(ctx, g1, tx, 11 + slide, 1.6f, 0xFFFFFF, ap, true);
        Gfx.textScaled(ctx, user, tx + Gfx.boldWidth(g1) * 1.6f, 11 + slide, 1.6f, ac, ap, true);
        Gfx.text(ctx, "Todo listo para jugar.", tx, 29 + slide, 0x8E8E9E, ap, true);

        String[][] chips = {{"1.21.1", "Minecraft"}, {AmetraScreen.VERSION, "RzClient"}};
        int cxr = w - PAD;
        for (int i = chips.length - 1; i >= 0; i--) {
            int cw = Math.max(Gfx.width(chips[i][0]), Gfx.width(chips[i][1])) + 16;
            cxr -= cw;
            Gfx.rbox(ctx, cxr, 10 + slide, cw, 28, 0x1B1B28, 0.85f * ap);
            Gfx.outline(ctx, cxr, 10 + slide, cw, 28, 0xFFFFFF, 0.08f * ap);
            Gfx.bold(ctx, chips[i][0], cxr + 8, 14 + slide, 0xFFFFFF, ap, true);
            Gfx.text(ctx, chips[i][1], cxr + 8, 26 + slide, 0x8E8E9E, ap, true);
            cxr -= 6;
        }

        // ----- tarjeta central -----
        Gfx.rbox(ctx, heroX, heroY, heroW, heroH, 0x14141E, 0.82f * ap);
        Gfx.outline(ctx, heroX, heroY, heroW, heroH, ac, 0.18f * ap);
        Gfx.glow(ctx, heroX + heroW / 2f, heroY + heroH * 0.55f, heroH * 0.45f, ac, 0.22f * ap);

        // píldoras de animación
        int hoveredAnim = hitAnim(mouseX, mouseY);
        for (int i = 0; i < ANIMS.length; i++) {
            boolean on = i == anim;
            Gfx.rbox(ctx, ax[i], ay, aw[i], ah, on ? ac : 0x1B1B28, (on ? 0.95f : 0.8f) * ap);
            Gfx.text(ctx, ANIMS[i], ax[i] + 7, ay + 3, on ? 0xFFFFFF : (i == hoveredAnim ? 0xFFFFFF : 0xB0B0C0), ap, true);
        }

        // personaje
        float s = Math.max(1.5f, (heroH - 90) / 32f);
        drawFigure(ctx, heroX + heroW / 2f, heroY + 38, s, ap, mc);
        // etiqueta con el nombre
        int nw = Gfx.boldWidth(user) + 18;
        Gfx.rbox(ctx, heroX + (heroW - nw) / 2f, heroY + 8, nw, 16, 0x0C0C14, 0.85f * ap);
        Gfx.bold(ctx, user, heroX + (heroW - nw) / 2f + 9, heroY + 12, 0xFFFFFF, ap, true);

        // botones
        int hovered = hit(mouseX, mouseY);
        for (int i = 0; i < LABELS.length; i++) {
            hov[i] += ((i == hovered ? 1f : 0f) - hov[i]) * Math.min(1f, dt * 16f);
            float k = hov[i];
            float a = Colors.clamp01(ap * 1.4f - i * 0.08f);
            float y = by[i] + (1f - a) * 8f;
            boolean primary = i == 0;
            int base = primary ? ac : 0x1B1B28;
            Gfx.rbox(ctx, bx[i], y, bw[i], BH, Colors.lerp(base, ac, primary ? 0.25f * k : 0.28f * k), 0.92f * a);
            Gfx.outline(ctx, bx[i], y, bw[i], BH, ac, (0.12f + 0.55f * k) * a);
            String l = LABELS[i];
            Gfx.text(ctx, l, bx[i] + (bw[i] - Gfx.width(l)) / 2f, y + (BH - 8) / 2f, Colors.lerp(0xE6E6F0, 0xFFFFFF, k), a, true);
        }

        // ----- novedades -----
        Gfx.text(ctx, "NOVEDADES", newsX, heroY - 1, 0x8E8E9E, ap, true);
        String[][] news = {
            {"NUEVO", "Tienda y cosméticos", "Sets Ángel, Demonio y Vacío. Gana gemas jugando y pruébalos antes de comprar."},
            {"MODS", "Biblioteca de mods", "Instala con un clic Sodium, Iris, Xaero y miles más de Modrinth."},
            {"CLIENTE", "Perfiles y códigos", "Comparte tu configuración con un código AMETRA1."}
        };
        int top = heroY + 12, gapN = 6;
        int ch = (heroY + heroH - top - gapN * 2) / 3;
        for (int i = 0; i < 3; i++) {
            float a = Colors.clamp01(ap * 1.4f - 0.15f - i * 0.1f);
            float y = top + i * (ch + gapN) + (1f - a) * 8f;
            Gfx.rbox(ctx, newsX, y, newsW, ch, 0x1B1B28, 0.85f * a);
            Gfx.outline(ctx, newsX, y, newsW, ch, 0xFFFFFF, 0.07f * a);
            int tagW = Gfx.boldWidth(news[i][0]) + 10;
            Gfx.rbox(ctx, newsX + 8, y + 7, tagW, 12, 0x7A0F1A, a);
            Gfx.bold(ctx, news[i][0], newsX + 13, y + 9, 0xFFB3BA, a, false);
            Gfx.bold(ctx, news[i][1], newsX + 8, y + 24, 0xFFFFFF, a, true);
            int ty = (int) y + 36;
            for (String line : wrap(news[i][2], newsW - 16)) {
                if (ty + 9 > y + ch - 3) break;
                Gfx.text(ctx, line, newsX + 8, ty, 0x8E8E9E, a, true);
                ty += 10;
            }
        }

        // pie
        String key = AmetraClient.MENU_KEY == null ? "Shift derecho" : AmetraClient.MENU_KEY.getBoundKeyLocalizedText().getString();
        Gfx.text(ctx, "En el juego: " + key + " abre el menú de RzClient", PAD, h - 9, 0x6E6E7E, ap, true);
    }

    // ---------- personaje en 2D con tu skin (cabeza, cuerpo, brazos y piernas) ----------
    private void drawFigure(DrawContext ctx, float cx, float top, float s, float ap, MinecraftClient mc) {
        SkinInfo si = Compat.localSkin(mc);
        if (si == null) return; // si una versión cambia esta API, el menú sigue funcionando sin el muñeco
        Identifier tex = si.tex;
        boolean slim = si.slim;
        int aw2 = slim ? 3 : 4;
        float t = time;
        float bob = 0, sway = 0, lArm = 0, rArm = 0, lLeg = 0, rLeg = 0;
        switch (anim) {
            case 0 -> { bob = (float) Math.sin(t * 2f) * 0.6f; lArm = 0.04f * (float) Math.sin(t * 2f); rArm = -lArm; }
            case 1 -> {
                float sw = (float) Math.sin(t * 6f) * 0.6f;
                lLeg = sw; rLeg = -sw; lArm = -sw; rArm = sw;
                bob = Math.abs((float) Math.sin(t * 6f)) * -1.2f;
            }
            case 2 -> { rArm = -(2.5f + 0.35f * (float) Math.sin(t * 9f)); lArm = 0.04f; bob = (float) Math.sin(t * 2f) * 0.5f; }
            default -> {
                sway = 0.08f * (float) Math.sin(t * 6f);
                lArm = 2.4f + 0.3f * (float) Math.sin(t * 8f);
                rArm = -(2.4f + 0.3f * (float) Math.sin(t * 8f + 1.2f));
                lLeg = 0.35f * (float) Math.sin(t * 6f); rLeg = -lLeg;
                bob = Math.abs((float) Math.sin(t * 6f)) * -2f;
            }
        }
        var m = ctx.getMatrices();
        m.push();
        m.translate(cx, top, 0);
        m.scale(s * ap, s * ap, 1f);
        m.translate(0, bob + (1f - ap) * 6f, 0);
        m.multiply(new Quaternionf().rotateZ(sway));
        // piernas (detrás del cuerpo)
        limb(ctx, tex, -2, 20, rLeg, 4, 20, 4, 36, 4, 12);
        limb(ctx, tex, 2, 20, lLeg, 20, 52, 4, 52, 4, 12);
        // brazos
        limb(ctx, tex, -4 - aw2 / 2f, 8, lArm, 44, 20, 44, 36, aw2, 12);
        limb(ctx, tex, 4 + aw2 / 2f, 8, rArm, 36, 52, 52, 52, aw2, 12);
        // cuerpo + chaqueta
        Compat.drawTex(ctx, tex, -4, 8, 8, 12, 20, 20, 8, 12, 64, 64);
        Compat.drawTex(ctx, tex, -4, 8, 8, 12, 20, 36, 8, 12, 64, 64);
        // cabeza + gorro
        Compat.drawTex(ctx, tex, -4, 0, 8, 8, 8, 8, 8, 8, 64, 64);
        Compat.drawTex(ctx, tex, -4, 0, 8, 8, 40, 8, 8, 8, 64, 64);
        m.pop();
    }

    /** Una extremidad que gira desde su parte de arriba. (u,v) = capa base, (u2,v2) = capa exterior. */
    private static void limb(DrawContext ctx, Identifier tex, float px, float py, float ang, int u, int v, int u2, int v2, int w, int h) {
        var m = ctx.getMatrices();
        m.push();
        m.translate(px, py, 0);
        m.multiply(new Quaternionf().rotateZ(ang));
        int x = -w / 2;
        Compat.drawTex(ctx, tex, x, 0, w, h, u, v, w, h, 64, 64);
        Compat.drawTex(ctx, tex, x, 0, w, h, u2, v2, w, h, 64, 64);
        m.pop();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int a = hitAnim(mouseX, mouseY);
            if (a >= 0) {
                anim = a;
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
            int i = hit(mouseX, mouseY);
            if (i >= 0) {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                switch (i) {
                    case 0 -> mc.setScreen(new SelectWorldScreen(this));
                    case 1 -> mc.setScreen(new MultiplayerScreen(this));
                    case 2 -> mc.setScreen(new OptionsScreen(this, mc.options));
                    case 3 -> mc.setScreen(new AmetraScreen(this));
                    default -> mc.scheduleStop();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
