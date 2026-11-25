package com.ametra;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Mod Menu estilo Dawn: filtros arriba (ALL, NEW, HUD, HYPIXEL, PVP), botón de favoritos,
 * cuadrícula de módulos en tres columnas con interruptor y desplazamiento con la rueda.
 */
public class ModMenuScreen extends ScreenBase {

    static final int PW = 620, PH = 344;
    static final int PAD = 16, GAP = 10, CARD_H = 46, ROW_GAP = 8, COLS = 3;
    static final int BG = 0x0C0C12, BG2 = 0x151520, BG3 = 0x1E1E2C, LINE = 0x2A2A3A, TEXT = 0xF2F2F6, MUTED = 0x8E8E9E;

    private final Screen parent;
    private String filter = "ALL";
    private boolean favOnly = false;
    private int scrollRow = 0;
    private float ox, oy;
    private final List<Hit> hits = new ArrayList<>();

    private static final class Hit {
        float x, y, w, h;
        Runnable click;
    }

    public ModMenuScreen(Screen parent) {
        super(Text.literal("Mod Menu"));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected boolean onScroll(double mx, double my, double hAmount, double vAmount) {
        if (vAmount > 0) scrollRow = Math.max(0, scrollRow - 1);
        else if (vAmount < 0) scrollRow++;
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        ox = (width - PW) / 2f;
        oy = (height - PH) / 2f;
        hits.clear();

        int ac = AmetraConfig.accent;
        float a = 1f;

        Gfx.rbox(ctx, ox, oy, PW, PH, BG2, a);
        Gfx.outline(ctx, ox, oy, PW, PH, LINE, a);

        // Barra superior: filtros + favoritos + cerrar
        float fx = ox + PAD, fy = oy + 12, fh = 22;
        for (String f : DawnModules.FILTERS) {
            int w = Gfx.width(f) + 20;
            boolean sel = !favOnly && f.equals(filter);
            Gfx.rbox(ctx, fx, fy, w, fh, sel ? BG3 : BG, a);
            if (sel) Gfx.outline(ctx, fx, fy, w, fh, ac, a);
            Gfx.text(ctx, f, fx + 10, fy + (fh - 8) / 2f, sel ? ac : MUTED, a, false);
            final String target = f;
            addHit(fx, fy, w, fh, () -> { filter = target; favOnly = false; scrollRow = 0; });
            fx += w + 6;
        }

        // Favoritos: un cuadro de color cuando está activo
        int fvW = Gfx.width("FAVORITOS") + 28;
        Gfx.rbox(ctx, fx, fy, fvW, fh, favOnly ? BG3 : BG, a);
        if (favOnly) Gfx.outline(ctx, fx, fy, fvW, fh, ac, a);
        Gfx.rbox(ctx, fx + 9, fy + 7, 8, 8, favOnly ? ac : 0x3A3A4A, a);
        Gfx.text(ctx, "FAVORITOS", fx + 22, fy + (fh - 8) / 2f, favOnly ? TEXT : MUTED, a, false);
        addHit(fx, fy, fvW, fh, () -> { favOnly = !favOnly; scrollRow = 0; });

        // Ir a Ajustes
        float axW = Gfx.width("AJUSTES") + 22;
        float axX = ox + PW - PAD - 22 - 8 - axW;
        Gfx.rbox(ctx, axX, fy, axW, fh, BG, a);
        Gfx.outline(ctx, axX, fy, axW, fh, LINE, a);
        Gfx.text(ctx, "AJUSTES", axX + 11, fy + (fh - 8) / 2f, MUTED, a, false);
        addHit(axX, fy, axW, fh, () -> client.setScreen(new DawnScreen(parent)));

        // Cerrar
        float cx = ox + PW - PAD - 22;
        Gfx.rbox(ctx, cx, fy, 22, fh, BG, a);
        Gfx.text(ctx, "X", cx + 8, fy + (fh - 8) / 2f, 0xFF5C5C, a, false);
        addHit(cx, fy, 22, fh, () -> client.setScreen(parent));

        // Cuadrícula
        List<DawnModules.Mod> mods = DawnModules.filtered(filter, favOnly);
        float gx = ox + PAD, gy = oy + 56;
        float colW = (PW - 2 * PAD - (COLS - 1) * GAP) / (float) COLS;
        float areaH = PH - 56 - 14;
        int rowsVisible = Math.max(1, (int) ((areaH + ROW_GAP) / (CARD_H + ROW_GAP)));
        int totalRows = (mods.size() + COLS - 1) / COLS;
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, totalRows - rowsVisible)));

        if (mods.isEmpty()) {
            Gfx.text(ctx, favOnly ? "No tienes favoritos en este filtro" : "No hay módulos en este filtro", gx, gy + 20, MUTED, a, false);
        }

        for (int i = 0; i < mods.size(); i++) {
            int row = i / COLS - scrollRow, col = i % COLS;
            if (row < 0 || row >= rowsVisible) continue;
            DawnModules.Mod m = mods.get(i);
            float cardX = gx + col * (colW + GAP);
            float cardY = gy + row * (CARD_H + ROW_GAP);
            boolean on = DawnModules.isOn(m.key);

            Gfx.rbox(ctx, cardX, cardY, colW, CARD_H, BG, a);
            Gfx.outline(ctx, cardX, cardY, colW, CARD_H, on ? 0x33FFFFFF : LINE, a);

            // Nombre (recortado si es largo)
            String name = m.name.length() > 17 ? m.name.substring(0, 16) + "…" : m.name;
            Gfx.text(ctx, name, cardX + 8, cardY + 7, on ? TEXT : MUTED, a, false);

            final String key = m.key;
            addHit(cardX, cardY, colW, CARD_H, () -> { DawnModules.toggle(key); AmetraConfig.save(); });
            // Pie de la tarjeta: favorito, ajustes y interruptor
            float footY = cardY + CARD_H - 15;
            boolean fav = DawnModules.isFav(m.key);
            Gfx.rbox(ctx, cardX + 8, footY + 1, 8, 8, fav ? ac : 0x3A3A4A, a);
            addHit(cardX + 4, footY - 3, 16, 16, () -> DawnModules.toggleFav(key));

            Gfx.text(ctx, "ajustes", cardX + 20, footY, MUTED, a, false);

            float sx = cardX + colW - 32, sy = footY;
            Gfx.rbox(ctx, sx, sy, 24, 11, on ? ac : 0x2E2E3E, a);
            Gfx.rbox(ctx, on ? sx + 14 : sx + 2, sy + 2, 7, 7, 0xFFFFFF, a);
        }

        // Indicador de desplazamiento
        if (totalRows > rowsVisible) {
            Gfx.text(ctx, (scrollRow + 1) + "/" + (totalRows - rowsVisible + 1), ox + PW - PAD - 30, oy + PH - 12, MUTED, a, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        float x = (float) mx, y = (float) my;
        // Los puntos de favorito están encima de la tarjeta: se revisan primero
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (x >= h.x && x < h.x + h.w && y >= h.y && y < h.y + h.h) {
                h.click.run();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void addHit(float x, float y, float w, float h, Runnable click) {
        Hit t = new Hit();
        t.x = x; t.y = y; t.w = w; t.h = h; t.click = click;
        hits.add(t);
    }
}
