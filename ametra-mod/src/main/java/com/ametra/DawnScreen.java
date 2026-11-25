package com.ametra;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Menú estilo Dawn: pestañas arriba (General, Performance, Graphics, Cosmetic) y una rejilla de
 * interruptores de dos columnas. Los valores viven en DawnOptions y se guardan en ametra.json.
 */
public class DawnScreen extends ScreenBase {

    // Layout (coordenadas locales del panel)
    static final int PW = 620, PH = 344;
    static final int TAB_H = 30, ROW_H = 26, PAD = 16, COL_GAP = 16;

    static final int BG = 0x0C0C12, BG2 = 0x151520, BG3 = 0x1E1E2C, LINE = 0x2A2A3A, TEXT = 0xF2F2F6, MUTED = 0x8E8E9E;

    private final Screen parent;
    private DawnOptions.Cat tab = DawnOptions.Cat.GENERAL;
    private float ox, oy, lmx, lmy;
    private final List<Hit> hits = new ArrayList<>();
    private final List<float[]> sliderTracks = new ArrayList<>(); // x, y, w, h por cada slider visible
    private final List<String> sliderKeys = new ArrayList<>();
    private final List<double[]> sliderRange = new ArrayList<>(); // min, max
    private int dragging = -1;

    private static final class Hit {
        float x, y, w, h;
        Runnable click;
    }

    public DawnScreen(Screen parent) {
        super(Text.literal("Dawn"));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected boolean onScroll(double mx, double my, double hAmount, double vAmount) {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Fondo oscuro sobre lo que haya detrás
        ctx.fill(0, 0, width, height, 0xB0000000);

        ox = (width - PW) / 2f;
        oy = (height - PH) / 2f;
        lmx = mouseX - ox;
        lmy = mouseY - oy;
        hits.clear();

        int ac = AmetraConfig.accent;
        float a = 1f;

        // Panel
        Gfx.rbox(ctx, ox, oy, PW, PH, BG2, a);
        Gfx.outline(ctx, ox, oy, PW, PH, LINE, a);

        // Barra de pestañas
        float tx = ox + PAD;
        for (DawnOptions.Cat c : DawnOptions.Cat.values()) {
            String label = c.title.toUpperCase();
            int w = Gfx.width(label) + 22;
            boolean sel = c == tab;
            Gfx.rbox(ctx, tx, oy + 12, w, TAB_H - 6, sel ? BG3 : BG, a);
            if (sel) Gfx.outline(ctx, tx, oy + 12, w, TAB_H - 6, ac, a);
            Gfx.text(ctx, label, tx + 11, oy + 12 + (TAB_H - 6 - 8) / 2f, sel ? ac : MUTED, a, false);
            final DawnOptions.Cat target = c;
            addHit(tx, oy + 12, w, TAB_H - 6, () -> tab = target);
            tx += w + 8;
        }

        // Abrir el Mod Menu
        float mmW = Gfx.width("MOD MENU") + 22;
        float mmX = ox + PW - PAD - 22 - 8 - mmW;
        Gfx.rbox(ctx, mmX, oy + 12, mmW, TAB_H - 6, BG, a);
        Gfx.outline(ctx, mmX, oy + 12, mmW, TAB_H - 6, LINE, a);
        Gfx.text(ctx, "MOD MENU", mmX + 11, oy + 12 + (TAB_H - 6 - 8) / 2f, MUTED, a, false);
        addHit(mmX, oy + 12, mmW, TAB_H - 6, () -> client.setScreen(new ModMenuScreen(parent)));

        // Cerrar (X)
        float cx = ox + PW - PAD - 22;
        Gfx.rbox(ctx, cx, oy + 12, 22, TAB_H - 6, BG, a);
        Gfx.text(ctx, "X", cx + 8, oy + 12 + (TAB_H - 6 - 8) / 2f, 0xFF5C5C, a, false);
        addHit(cx, oy + 12, 22, TAB_H - 6, () -> client.setScreen(parent));

        // Título de la pestaña y botón de reinicio
        float top = oy + TAB_H + 22;
        Gfx.text(ctx, tab.title.toUpperCase() + " SETTINGS", ox + PAD, top, TEXT, a, false);

        float rbW = 130, rbH = 18;
        float rbX = ox + PW - PAD - rbW, rbY = top - 5;
        Gfx.rbox(ctx, rbX, rbY, rbW, rbH, BG3, a);
        Gfx.outline(ctx, rbX, rbY, rbW, rbH, LINE, a);
        String rb = "RESET SETTINGS";
        Gfx.text(ctx, rb, rbX + (rbW - Gfx.width(rb)) / 2f, rbY + 5, MUTED, a, false);
        final DawnOptions.Cat resetTarget = tab;
        addHit(rbX, rbY, rbW, rbH, () -> {
            DawnOptions.resetCat(resetTarget);
            AmetraConfig.save();
        });

        // Rejilla de interruptores (2 columnas)
        List<DawnOptions.Opt> opts = DawnOptions.byCat(tab);
        float gx = ox + PAD, gy = top + 22;
        float colW = (PW - 2 * PAD - COL_GAP) / 2f;
        int i = 0;
        for (DawnOptions.Opt o : opts) {
            int col = i % 2, row = i / 2;
            float rx = gx + col * (colW + COL_GAP);
            float ry = gy + row * ROW_H;
            i++;

            Gfx.rbox(ctx, rx, ry, colW, ROW_H - 4, BG, a);
            boolean on = DawnOptions.get(o.key);
            Gfx.text(ctx, o.label, rx + 8, ry + (ROW_H - 4 - 8) / 2f, on ? TEXT : MUTED, a, false);

            // Interruptor
            float sx = rx + colW - 34, sy = ry + (ROW_H - 4 - 12) / 2f;
            Gfx.rbox(ctx, sx, sy, 26, 12, on ? ac : 0x2E2E3E, a);
            float kx = on ? sx + 16 : sx + 2;
            Gfx.rbox(ctx, kx, sy + 2, 8, 8, 0xFFFFFF, a);

            final String key = o.key;
            addHit(rx, ry, colW, ROW_H - 4, () -> {
                DawnOptions.toggle(key);
                AmetraConfig.save();
            });
        }

        // Sliders (debajo de los interruptores)
        sliderTracks.clear(); sliderKeys.clear(); sliderRange.clear();
        int rowsUsed = (opts.size() + 1) / 2;
        float sy0 = gy + rowsUsed * ROW_H + 6;
        int k = 0;
        for (DawnOptions.Num n : DawnOptions.numsByCat(tab)) {
            float ry = sy0 + k * ROW_H;
            k++;
            Gfx.rbox(ctx, gx, ry, PW - 2 * PAD, ROW_H - 4, BG, a);
            Gfx.text(ctx, n.label, gx + 8, ry + (ROW_H - 4 - 8) / 2f, TEXT, a, false);

            double v = DawnOptions.getNum(n.key);
            String vs = String.format(java.util.Locale.ROOT, "%.1f", v);
            float trackX = gx + 150, trackW = PW - 2 * PAD - 150 - 50;
            float trackY = ry + (ROW_H - 4) / 2f - 2;
            Gfx.rbox(ctx, trackX, trackY, trackW, 4, 0x2E2E3E, a);
            float frac = (float) ((v - n.min) / (n.max - n.min));
            Gfx.rbox(ctx, trackX, trackY, trackW * frac, 4, ac, a);
            Gfx.rbox(ctx, trackX + trackW * frac - 4, trackY - 3, 8, 10, 0xFFFFFF, a);
            Gfx.text(ctx, vs, gx + PW - 2 * PAD - 36, ry + (ROW_H - 4 - 8) / 2f, TEXT, a, false);

            sliderTracks.add(new float[] { trackX, ry, trackW, ROW_H - 4 });
            sliderKeys.add(n.key);
            sliderRange.add(new double[] { n.min, n.max });
        }
    }

    private void setSliderFromMouse(int i, float mx) {
        float[] t = sliderTracks.get(i);
        double[] r = sliderRange.get(i);
        float frac = Math.max(0f, Math.min(1f, (mx - t[0]) / t[2]));
        DawnOptions.setNum(sliderKeys.get(i), r[0] + frac * (r[1] - r[0]));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging >= 0 && button == 0) {
            setSliderFromMouse(dragging, (float) mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging >= 0) {
            dragging = -1;
            AmetraConfig.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        float x = (float) mx, y = (float) my;
        for (int i = 0; i < sliderTracks.size(); i++) {
            float[] t = sliderTracks.get(i);
            if (x >= t[0] - 6 && x <= t[0] + t[2] + 6 && y >= t[1] && y < t[1] + t[3]) {
                dragging = i;
                setSliderFromMouse(i, x);
                return true;
            }
        }
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
