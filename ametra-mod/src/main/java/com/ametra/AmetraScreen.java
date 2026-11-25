package com.ametra;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/**
 * Menú de Ametra (estilo Feather/Lunar): barra lateral, buscador, categorías, rejilla de tarjetas,
 * favoritos y panel lateral de ajustes. Todo se dibuja a mano con "hit regions" que se recrean cada frame.
 */
public class AmetraScreen extends ScreenBase {
    static final String VERSION = "v1.6.0";

    // ---- Layout (coordenadas locales del panel) ----
    static final int PW = 620, PH = 344, SIDE = 146, MX = SIDE + 1, PAD = 14;
    static final int GX = MX + PAD, GW = PW - MX - 2 * PAD;
    static final int COLS = 4, CARD_W = 105, CARD_H = 94, GAP = 8, ROWGAP = 8;
    static final int TOP0 = 68, TOP1 = 44, BOTTOM = 10, DW = 206;

    // ---- Colores ----
    static final int BG = 0x0C0C12, BG2 = 0x151520, BG3 = 0x1E1E2C, LINE = 0x2A2A3A, TEXT = 0xF2F2F6, MUTED = 0x8E8E9E;
    static final int[] MOD_PRESETS = {0xFFFFFF, 0x8FD6A8, 0x85C1E9, 0xD7A8F0, 0xF1948A, 0xF0B27A, 0xFFD93D, 0xFF5C8A};
    static final int[] ACC_PRESETS = {
        0x8FD6A8, 0x6FCF97, 0x85C1E9, 0x56CCF2, 0xD7A8F0, 0xBB6BD9, 0xF1948A, 0xEB5757,
        0xF0B27A, 0xF2994A, 0xFFD93D, 0xF2C94C, 0xFF5C8A, 0x00E5FF, 0xFFFFFF, 0x9B8CFF};
    static final String[] TABS = {"MÓDULOS", "TEMA", "FONDOS", "COSMÉTICOS", "PERSONALIZAR", "SERVIDORES", "INSTALADOS", "EDITOR HUD"};
    static final String[] COS_IDS = {"cape", "wings", "hat", "glasses", "pack", "back", "boots", "fists", "aura", "face"};
    static final String[] COS_NAMES = {"Capas", "Alas", "Gorros", "Gafas", "Mochilas", "Espalda", "Botas", "Puños", "Auras", "Caras"};
    static final String[] CATS = {"Todos", "Activos", "HUD", "Info", "Combate", "Visual"};
    static final int PN = 60;

    interface DragFn { void on(float lx, float ly); }

    static final class Hit {
        float x, y, w, h;
        float[] clip;
        Runnable click;
        DragFn drag;
    }

    static final class ModInfo {
        String id, name, ver;
        int color;
    }

    private static final float[] NONE = {-1e9f, -1e9f, 1e9f, 1e9f};

    private final Screen parent;
    private int tab = 0, cat = 0;
    private boolean edit = false, closing = false, closed = false, armed = false;
    private float open = 0f, editFade = 0f, time = 0f, tabAge = 0f, dt = 0.016f;
    private long lastNs = System.nanoTime();
    private float ls = 1f, pa = 1f, lmx, lmy;

    private String search = "";
    private boolean searchFocus = false, favOnly = false;
    // Perfiles: renombrar, confirmar borrado y avisos
    private boolean renaming = false;
    // campo de texto del panel (Autotext / Auto GG) que se está editando
    private String textEdit = null;
    private String renameOld = "", renameBuf = "", confirmDel = null, toast = "";
    private float confirmT = 0f, toastT = 0f;
    private HudModule drawerMod = null, dragging = null;
    private boolean drawerOpen = false;
    private float dragOffX, dragOffY;
    private final float[] scroll = new float[TABS.length], scrollT = new float[TABS.length];
    private float contentH = 0f;
    private boolean noHit = false;
    // pestaña Fondos: fondo sobre el que está el cursor (se muestra tras 0,3 s)
    private String bgHover = null;
    private float bgHoverT = 0f;
    // pestaña Cosméticos
    private String cosCat = "cape", lastCosHover = "";
    private float spin = 0f, spinTarget = 0f, gridAge = 0f;

    private final Map<String, Float> anims = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private Hit active;
    private final ArrayDeque<float[]> clipStack = new ArrayDeque<>();
    private float[] clipNow = NONE;

    private float aH, aS, aV; // acento en HSV (conserva el tono aunque S o V sean 0)
    private List<ModInfo> mods;
    private final float[] pnx = new float[PN], pny = new float[PN], psp = new float[PN], pph = new float[PN];

    public AmetraScreen(Screen parent) {
        super(Text.literal("RzClient"));
        this.parent = parent;
        Random r = new Random(7);
        for (int i = 0; i < PN; i++) {
            pnx[i] = r.nextFloat();
            pny[i] = r.nextFloat();
            psp[i] = 0.02f + r.nextFloat() * 0.05f;
            pph[i] = r.nextFloat() * 6.28f;
        }
        armed = parent != null; // abierto desde un botón: la tecla no está pulsada
    }

    @Override
    protected void init() {
        if (mods == null) mods = loadMods();
        syncAccent();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        AmetraConfig.clearPreview();
        AmetraConfig.save();
    }

    @Override
    public void close() {
        if (closing || closed) return;
        if (!AmetraConfig.animations) {
            closed = true;
            AmetraConfig.save();
            client.setScreen(parent);
            return;
        }
        closing = true;
    }

    // ---------- Mods instalados ----------
    private static List<ModInfo> loadMods() {
        List<ModInfo> out = new ArrayList<>();
        int[] pal = {0x8FD6A8, 0x85C1E9, 0xD7A8F0, 0xF1948A, 0xF0B27A, 0xFFD93D, 0xFF5C8A, 0x56CCF2};
        for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
            ModMetadata md = c.getMetadata();
            String id = md.getId();
            if (c.getContainingMod().isPresent()) continue;
            if ("builtin".equals(md.getType())) continue;
            if (id.equals("fabricloader") || id.equals("mixinextras") || (id.startsWith("fabric-") && !id.equals("fabric-api"))) continue;
            ModInfo mi = new ModInfo();
            mi.id = id;
            mi.name = md.getName() == null || md.getName().isBlank() ? id : md.getName();
            mi.ver = md.getVersion().getFriendlyString();
            mi.color = pal[Math.abs(id.hashCode()) % pal.length];
            out.add(mi);
        }
        out.sort(Comparator.comparing(m -> m.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    // ---------- Helpers ----------
    private float an(String k, float target, float speed) {
        float v = anims.getOrDefault(k, target);
        v += (target - v) * Math.min(1f, dt * speed);
        if (Math.abs(target - v) < 0.002f) v = target;
        anims.put(k, v);
        return v;
    }

    private static float approach(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    private float tx() { return width / 2f; }
    private float ty() { return height / 2f + (1f - Colors.ease(open)) * 10f; }
    private float toLX(double mx) { return (float) ((mx - tx()) / ls + PW / 2f); }
    private float toLY(double my) { return (float) ((my - ty()) / ls + PH / 2f); }
    private float toSX(float lx) { return (lx - PW / 2f) * ls + tx(); }
    private float toSY(float ly) { return (ly - PH / 2f) * ls + ty(); }

    private boolean over(float x, float y, float w, float h) {
        return lmx >= x && lmx < x + w && lmy >= y && lmy < y + h
            && lmx >= clipNow[0] && lmx < clipNow[2] && lmy >= clipNow[1] && lmy < clipNow[3];
    }

    private void hit(float x, float y, float w, float h, Runnable click, DragFn drag) {
        if (noHit) return;
        Hit t = new Hit();
        t.x = x; t.y = y; t.w = w; t.h = h; t.clip = clipNow; t.click = click; t.drag = drag;
        hits.add(t);
    }

    private void pushClip(DrawContext ctx, float x1, float y1, float x2, float y2) {
        float[] n = {Math.max(x1, clipNow[0]), Math.max(y1, clipNow[1]), Math.min(x2, clipNow[2]), Math.min(y2, clipNow[3])};
        clipStack.push(clipNow);
        clipNow = n;
        ctx.enableScissor((int) Math.floor(toSX(x1)), (int) Math.floor(toSY(y1)), (int) Math.ceil(toSX(x2)), (int) Math.ceil(toSY(y2)));
    }

    private void popClip(DrawContext ctx) {
        ctx.disableScissor();
        clipNow = clipStack.pop();
    }

    // Wrappers con la transparencia global del panel
    private void box(DrawContext c, float x, float y, float w, float h, int rgb, float a) { Gfx.rbox(c, x, y, w, h, rgb, a * pa); }
    private void rect(DrawContext c, float x, float y, float w, float h, int rgb, float a) { Gfx.rect(c, x, y, w, h, rgb, a * pa); }
    private void ol(DrawContext c, float x, float y, float w, float h, int rgb, float a) { Gfx.outline(c, x, y, w, h, rgb, a * pa); }
    private void txt(DrawContext c, String s, float x, float y, int rgb, float a) { Gfx.text(c, s, x, y, rgb, a * pa, false); }

    private String trim(String s, int maxW) {
        if (Gfx.width(s) <= maxW) return s;
        while (s.length() > 1 && Gfx.width(s + "...") > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private List<String> wrap(String s, int maxW, int maxLines) {
        List<String> lines = new ArrayList<>();
        String cur = "";
        for (String word : s.split(" ")) {
            String t = cur.isEmpty() ? word : cur + " " + word;
            if (Gfx.width(t) <= maxW) { cur = t; continue; }
            lines.add(cur);
            cur = word;
            if (lines.size() == maxLines) break;
        }
        if (lines.size() < maxLines && !cur.isEmpty()) lines.add(cur);
        return lines;
    }

    private boolean menuKeyDown() {
        try {
            InputUtil.Key k = KeyBindingHelper.getBoundKeyOf(AmetraClient.MENU_KEY);
            if (k.getCategory() == InputUtil.Type.KEYSYM) return InputUtil.isKeyPressed(client.getWindow().getHandle(), k.getCode());
        } catch (Exception ignored) { /* sin tecla */ }
        return false;
    }

    private float rowA(int i) {
        return Colors.ease(tabAge * 6f - i * 0.25f);
    }

    private void setTab(int i) {
        if (tab == i) return;
        tab = i;
        tabAge = 0f;
        gridAge = 0f;
        searchFocus = false;
        AmetraConfig.clearPreview();
    }

    private void setCat(int i) {
        cat = i;
        scrollT[0] = 0f;
        tabAge = 0f;
    }

    private void openDrawer(HudModule m) {
        if (drawerOpen && drawerMod == m) { drawerOpen = false; return; }
        drawerMod = m;
        drawerOpen = true;
    }

    private void toggleFav(String id) {
        if (!AmetraConfig.favorites.remove(id)) AmetraConfig.favorites.add(id);
        AmetraConfig.save();
    }

    private List<HudModule> filtered() {
        String q = search.toLowerCase(Locale.ROOT).trim();
        List<HudModule> favs = new ArrayList<>(), rest = new ArrayList<>();
        for (HudModule m : Hud.MODULES) {
            boolean fav = AmetraConfig.favorites.contains(m.id);
            if (favOnly && !fav) continue;
            if (cat == 1 && !m.cfg.enabled) continue;
            if (cat == 2 && !m.hud) continue;
            if (cat >= 3 && !m.cat.equals(CATS[cat])) continue;
            if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q) && !m.desc.toLowerCase(Locale.ROOT).contains(q)) continue;
            (fav ? favs : rest).add(m);
        }
        favs.addAll(rest);
        return favs;
    }

    // ---------- Render ----------
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long nn = System.nanoTime();
        dt = Math.min(0.1f, (nn - lastNs) / 1e9f);
        lastNs = nn;
        time += dt;
        tabAge += dt;
        if (toastT > 0f) toastT -= dt;
        if (confirmT > 0f && (confirmT -= dt) <= 0f) confirmDel = null;
        boolean anim = AmetraConfig.animations;
        if (!armed && !menuKeyDown()) armed = true;

        if (closing) open = anim ? Math.max(0f, open - dt * 7f) : 0f;
        else open = anim ? Math.min(1f, open + dt * 4.5f) : 1f;
        if (closing && open <= 0f && !closed) {
            closed = true;
            AmetraConfig.save();
            client.execute(() -> client.setScreen(parent));
        }
        editFade = anim ? approach(editFade, edit ? 1f : 0f, dt * 7f) : (edit ? 1f : 0f);

        float oe = Colors.ease(open);
        float fit = Math.min(1f, Math.min(width / (PW + 24f), height / (PH + 24f)));
        ls = fit * (0.9f + 0.1f * oe);
        pa = oe * (1f - editFade);
        lmx = toLX(mouseX);
        lmy = toLY(mouseY);
        hits.clear();
        clipStack.clear();
        clipNow = NONE;
        noHit = false;

        int ac = AmetraConfig.accent;
        float dim = oe * (1f - 0.8f * editFade);
        // sin mundo detrás (abierto desde el menú principal) se ve el fondo elegido
        if (client != null && client.world == null) Backgrounds.drawFull(ctx, width, height, time);
        ctx.fillGradient(0, 0, width, height, Colors.argb(0x04040A, 0.60f * dim), Colors.argb(0x0A0A18, 0.82f * dim));
        ctx.fillGradient(0, height * 2 / 3, width, height, Colors.argb(ac, 0f), Colors.argb(ac, 0.12f * dim));
        for (int i = 0; i < PN; i++) {
            float y = (((pny[i] - time * psp[i]) % 1f) + 1f) % 1f;
            float x = pnx[i] * width + (float) Math.sin(time * 0.6f + pph[i]) * 10f;
            float tw = 0.5f + 0.5f * (float) Math.sin(time * 1.5f + pph[i] * 3f);
            Gfx.rect(ctx, x, y * height, (i % 5 == 0) ? 2 : 1, (i % 5 == 0) ? 2 : 1, ac, (0.10f + 0.35f * tw) * dim);
        }

        if (pa > 0.02f) drawPanel(ctx, ac);
        if (editFade > 0.02f) drawEdit(ctx, mouseX, mouseY, ac);
    }

    private void drawPanel(DrawContext ctx, int ac) {
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(tx(), ty(), 0f);
        ms.scale(ls, ls, 1f);
        ms.translate(-PW / 2f, -PH / 2f, 0f);

        box(ctx, -4, -4, PW + 8, PH + 8, ac, 0.07f);
        box(ctx, -1, -1, PW + 2, PH + 2, LINE, 1f);
        box(ctx, 0, 0, PW, PH, BG, 0.97f);
        rect(ctx, 1, 1, SIDE - 1, PH - 2, BG2, 0.85f);
        rect(ctx, SIDE, 1, 1, PH - 2, LINE, 1f);

        drawSidebar(ctx, ac);

        // Botón cerrar
        float cl = an("close", over(PW - 30, 8, 20, 20) ? 1f : 0f, 14f);
        box(ctx, PW - 30, 8, 20, 20, Colors.lerp(BG2, 0xC4384B, cl), 1f);
        txt(ctx, "×", PW - 30 + (20 - Gfx.width("×")) / 2f, 14, Colors.lerp(MUTED, 0xFFFFFF, cl), 1f);
        hit(PW - 30, 8, 20, 20, this::close, null);

        float top = tab == 0 ? TOP0 : TOP1;
        drawTabs(ctx, ac);
        if (tab == 0) drawTopBar(ctx, ac);

        float ch = PH - BOTTOM - top;
        pushClip(ctx, GX - 4, top, GX + GW + 4, PH - BOTTOM);
        float y0 = top - scroll[tab];
        if (tab == 0) tabGrid(ctx, y0, ac);
        else if (tab == 1) tabTheme(ctx, y0, ac);
        else if (tab == 2) tabBackgrounds(ctx, y0, ac);
        else if (tab == 3) tabCosmetics(ctx, y0, ac);
        else if (tab == 4) tabCustom(ctx, y0, ac);
        else if (tab == 5) tabServers(ctx, y0, ac);
        else tabMods(ctx, y0, ac);
        popClip(ctx);

        float maxS = Math.max(0f, contentH - ch);
        scrollT[tab] = Math.max(0f, Math.min(maxS, scrollT[tab]));
        scroll[tab] += (scrollT[tab] - scroll[tab]) * Math.min(1f, dt * 14f);
        if (maxS > 0f) {
            float th = Math.max(24f, ch * ch / contentH);
            float tyy = top + (ch - th) * (scroll[tab] / maxS);
            rect(ctx, GX + GW + 6, top, 2, ch, LINE, 0.8f);
            rect(ctx, GX + GW + 6, tyy, 2, th, ac, 0.9f);
        }

        drawDrawer(ctx, ac);
        drawToast(ctx, ac);
        ms.pop();
    }

    private void drawToast(DrawContext ctx, int ac) {
        if (toastT <= 0f || toast.isEmpty()) return;
        float a = Colors.clamp01(Math.min(toastT * 3f, (2.6f - toastT) * 8f));
        float w = Gfx.width(toast) + 24, x = MX + (PW - MX - w) / 2f, y = PH - 36 - (1f - a) * 6f;
        box(ctx, x - 1, y - 1, w + 2, 22, ac, 0.6f * a);
        box(ctx, x, y, w, 20, 0x0B0B11, 0.97f * a);
        txt(ctx, toast, x + 12, y + 6, TEXT, a);
    }

    // ---------- Barra lateral: marca + perfiles + códigos ----------
    private int enabledCount() {
        int n = 0;
        for (HudModule m : Hud.MODULES) if (m.cfg.enabled) n++;
        return n;
    }

    private void syncAccent() {
        float[] hsv = Colors.toHsv(AmetraConfig.accent);
        aH = hsv[0];
        aS = hsv[1];
        aV = hsv[2];
    }

    private void toast(String s) {
        toast = s;
        toastT = 2.6f;
    }

    private void newProfile() {
        String n = AmetraProfiles.create();
        if (n == null) toast("Máximo " + AmetraProfiles.MAX + " perfiles: borra uno primero");
        else toast("Perfil \"" + n + "\" creado (copia del actual)");
    }

    private void copyCode() {
        try {
            client.keyboard.setClipboard(AmetraProfiles.exportCode());
            toast("Código de \"" + AmetraProfiles.active + "\" copiado");
        } catch (Exception e) {
            toast("No se pudo copiar al portapapeles");
        }
    }

    private void pasteCode() {
        String err;
        try {
            err = AmetraProfiles.importCode(client.keyboard.getClipboard());
        } catch (Exception e) {
            err = "No se pudo leer el portapapeles";
        }
        if (err == null) {
            syncAccent();
            toast("Perfil importado: " + AmetraProfiles.active);
        } else {
            toast(err);
        }
    }

    private void pickProfile(String name) {
        if (renaming) finishRename(true);
        AmetraProfiles.switchTo(name);
        syncAccent();
    }

    private void startRename(String name) {
        renaming = true;
        renameOld = name;
        renameBuf = name;
        searchFocus = false;
    }

    private void endTextEdit() {
        if (textEdit == null) return;
        textEdit = null;
        AmetraConfig.save();
    }

    /** Última parte del texto que cabe (así se ve el cursor al escribir). */
    private String tail(String s, int maxW) {
        while (s.length() > 1 && Gfx.width(s) > maxW) s = s.substring(1);
        return s;
    }

    private void finishRename(boolean commit) {
        if (!renaming) return;
        renaming = false;
        if (commit && !renameBuf.trim().isEmpty() && !AmetraProfiles.rename(renameOld, renameBuf)) toast("Ya existe un perfil con ese nombre");
    }

    private void deleteProfile(String name) {
        if (name.equals(confirmDel)) {
            AmetraProfiles.delete(name);
            syncAccent();
            confirmDel = null;
            toast("Perfil borrado");
        } else {
            confirmDel = name;
            confirmT = 3f;
            toast("Pulsa la × otra vez para borrar \"" + name + "\"");
        }
    }

    private void drawSidebar(DrawContext ctx, int ac) {
        float pulse = 1f + 0.05f * (float) Math.sin(time * 2.2f);
        float size = 28f * pulse;
        Gfx.glow(ctx, 26, 27, 20 + 2 * (float) Math.sin(time * 2.2f), ac, 0.5f * pa);
        Gfx.logo(ctx, 26 - size / 2f, 27 - size / 2f, size, pa);
        float sc = 1.1f;
        Gfx.textScaled(ctx, "RzClient", 46, 12, sc, TEXT, pa, true);
        txt(ctx, trim(enabledCount() + "/" + Hud.MODULES.size() + " activos", SIDE - 52), 46, 28, MUTED, 0.9f);

        // línea con destello
        rect(ctx, 12, 46, SIDE - 24, 1, LINE, 1f);
        float shx = (time * 70f) % (SIDE + 40) - 20f;
        float xa = Math.max(12, shx), xb = Math.min(SIDE - 12, shx + 28f);
        if (xb > xa) rect(ctx, xa, 46, xb - xa, 1, ac, 0.9f);

        txt(ctx, "PERFILES", 12, 56, MUTED, 0.9f);
        String act = AmetraProfiles.active;
        java.util.List<String> names = AmetraProfiles.names();
        boolean many = names.size() > 1;
        for (int i = 0; i < names.size(); i++) {
            final String name = names.get(i);
            boolean isAct = name.equals(act);
            boolean ren = renaming && name.equals(renameOld);
            float ry = 70 + i * 22, rw = SIDE - 16;
            float ra = rowA(i);
            float hv = an("pr:" + name, over(8, ry, rw, 20) ? 1f : 0f, 14f);
            float sel = an("prs:" + name, isAct ? 1f : 0f, 14f);
            box(ctx, 8, ry, rw, 20, Colors.lerp(Colors.lerp(BG2, BG3, hv), ac, 0.20f * sel), ra);
            rect(ctx, 8, ry + 4, 2, 12, ac, sel * ra);
            box(ctx, 17, ry + 8, 4, 4, Colors.lerp(0x4A4A5E, ac, sel), ra);
            if (!ren) hit(8, ry, rw, 20, () -> pickProfile(name), null);

            if (ren) {
                box(ctx, 26, ry + 3, rw - 24, 14, BG, 1f);
                ol(ctx, 26, ry + 3, rw - 24, 14, ac, 1f);
                txt(ctx, trim(renameBuf, (int) rw - 34), 30, ry + 6, TEXT, 1f);
                if (((int) (time * 2f)) % 2 == 0) rect(ctx, 30 + Math.min(Gfx.width(renameBuf), rw - 34), ry + 5, 1, 10, ac, 1f);
            } else {
                int tw = (int) rw - (isAct ? 54 : many ? 38 : 28);
                txt(ctx, trim(name, tw), 28 + hv * 1f, ry + 6, Colors.lerp(Colors.lerp(MUTED, TEXT, hv), TEXT, sel), ra);
                if (isAct) {
                    float ph = an("pen:" + name, over(SIDE - 42, ry + 2, 16, 16) ? 1f : 0f, 14f);
                    Icons.pencil(ctx, SIDE - 36, ry + 7, Colors.lerp(MUTED, TEXT, ph), 1f * ra);
                    hit(SIDE - 42, ry + 2, 16, 16, () -> startRename(name), null);
                }
                if (many && (hv > 0.05f || name.equals(confirmDel))) {
                    boolean cf = name.equals(confirmDel);
                    float xh = an("del:" + name, over(SIDE - 26, ry + 2, 16, 16) ? 1f : 0f, 14f);
                    txt(ctx, "×", SIDE - 21, ry + 6, cf ? 0xFF5C5C : Colors.lerp(MUTED, 0xFF8A8A, xh), cf ? 1f : hv);
                    hit(SIDE - 26, ry + 2, 16, 16, () -> deleteProfile(name), null);
                }
            }
        }

        float bx = 10, bw = SIDE - 20;
        button(ctx, "newp", "+ Nuevo perfil", bx, PH - 112, bw, 22, AmetraProfiles.canAdd(), 1f, this::newProfile);
        button(ctx, "copy", "Copiar código", bx, PH - 86, bw, 22, true, 1f, this::copyCode);
        button(ctx, "paste", "Pegar código", bx, PH - 60, bw, 22, true, 1f, this::pasteCode);

        String key = AmetraClient.MENU_KEY.getBoundKeyLocalizedText().getString();
        txt(ctx, trim(key + " = cerrar", SIDE - 20), 12, PH - 30, MUTED, 0.9f);
        txt(ctx, "AmetraClient " + VERSION, 12, PH - 18, MUTED, 0.7f);
    }

    // ---------- Pestañas superiores (estilo Real Client) ----------
    private void drawTabs(DrawContext ctx, int ac) {
        boolean inWorld = client != null && client.player != null;
        float x = GX;
        // con 6 pestañas el espacio entre ellas se ajusta solo para no pisar el buscador (pestaña Módulos)
        float sumW = 0f;
        for (String t : TABS) sumW += Gfx.width(t);
        float avail = (tab == 0 ? PW - 36 - 100 - 4 - 8 : PW - 40) - GX;
        float gap = Math.max(8f, Math.min(20f, (avail - sumW) / (TABS.length - 1)));
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            boolean isEdit = i == TABS.length - 1;
            float w = Gfx.width(TABS[i]);
            float hv = an("tb" + i, over(x - 4, 6, w + 8, 24) ? 1f : 0f, 14f);
            float s = an("tbs" + i, !isEdit && tab == i ? 1f : 0f, 14f);
            int base = isEdit && !inWorld ? 0x5A5A6A : Colors.lerp(MUTED, TEXT, hv);
            txt(ctx, TABS[i], x, 14, Colors.lerp(base, ac, s), 1f);
            rect(ctx, x + w * (1f - s) / 2f, 27, w * s, 2, ac, 1f);
            if (isEdit) {
                hit(x - 4, 6, w + 8, 24, () -> {
                    if (inWorld) { edit = true; dragging = null; drawerOpen = false; }
                    else toast("Entra a un mundo para mover el HUD");
                }, null);
            } else {
                hit(x - 4, 6, w + 8, 24, () -> setTab(idx), null);
            }
            x += w + gap;
        }
        rect(ctx, MX, 33, PW - MX - 1, 1, LINE, 0.7f);
    }

    // ---------- Buscador + chips + filtro de favoritos ----------
    private void drawTopBar(DrawContext ctx, int ac) {
        float sw = 100, sx = PW - 36 - sw - 4;
        float fk = an("searchf", searchFocus ? 1f : 0f, 14f);
        box(ctx, sx, 8, sw, 22, BG2, 1f);
        ol(ctx, sx, 8, sw, 22, Colors.lerp(LINE, ac, fk), 1f);
        // lupa en píxeles
        ol(ctx, sx + 8, 14, 7, 7, Colors.lerp(MUTED, ac, fk), 1f);
        rect(ctx, sx + 15, 21, 3, 1, Colors.lerp(MUTED, ac, fk), 1f);
        if (search.isEmpty() && !searchFocus) txt(ctx, "Buscar...", sx + 24, 15, MUTED, 0.8f);
        else txt(ctx, trim(search, (int) sw - 34), sx + 24, 15, TEXT, 1f);
        if (searchFocus && ((int) (time * 2f)) % 2 == 0) {
            rect(ctx, sx + 24 + Math.min(Gfx.width(search), sw - 34), 13, 1, 11, ac, 1f);
        }
        hit(sx, 8, sw, 22, () -> { searchFocus = true; finishRename(true); }, null);

        float x = GX;
        for (int i = 0; i < CATS.length; i++) {
            final int idx = i;
            String label = CATS[i];
            float cw = Gfx.width(label) + 18;
            float hv = an("cat" + i, over(x, 40, cw, 18) ? 1f : 0f, 14f);
            float sel = an("catsel" + i, cat == i ? 1f : 0f, 14f);
            box(ctx, x, 40, cw, 18, Colors.lerp(Colors.lerp(BG2, BG3, hv), ac, sel * 0.22f), 1f);
            ol(ctx, x, 40, cw, 18, ac, 0.55f * sel);
            txt(ctx, label, x + 9, 45, Colors.lerp(Colors.lerp(MUTED, TEXT, hv), ac, sel), 1f);
            hit(x, 40, cw, 18, () -> setCat(idx), null);
            x += cw + 6;
        }

        // corazón: solo favoritos
        float hx = GX + GW - 24;
        float hv = an("favf", over(hx, 40, 24, 18) ? 1f : 0f, 14f);
        float sel = an("favfs", favOnly ? 1f : 0f, 14f);
        box(ctx, hx, 40, 24, 18, Colors.lerp(Colors.lerp(BG2, BG3, hv), ac, sel * 0.22f), 1f);
        ol(ctx, hx, 40, 24, 18, ac, 0.55f * sel);
        Icons.heart(ctx, hx + 8.5f, 46, Colors.lerp(Colors.lerp(0x6A6A7E, TEXT, hv), 0xFF6B8B, sel), pa);
        hit(hx, 40, 24, 18, () -> { favOnly = !favOnly; scrollT[0] = 0f; tabAge = 0f; }, null);
    }

    // ---------- Widgets ----------
    private void toggle(DrawContext ctx, String id, float x, float y, boolean on, float a, Runnable r) {
        float k = an("tg:" + id, on ? 1f : 0f, 16f);
        box(ctx, x, y, 22, 12, Colors.lerp(0x2E2E3E, AmetraConfig.accent, k), a);
        box(ctx, x + 2 + k * 10f, y + 2, 8, 8, Colors.lerp(0x9A9AAA, 0xFFFFFF, k), a);
        hit(x - 2, y - 2, 26, 16, r, null);
    }

    private void button(DrawContext ctx, String id, String label, float x, float y, float w, float h, boolean enabled, float a, Runnable r) {
        float hv = an("bt:" + id, enabled && over(x, y, w, h) ? 1f : 0f, 14f);
        box(ctx, x, y, w, h, Colors.lerp(BG3, AmetraConfig.accent, hv * 0.30f), a);
        ol(ctx, x, y, w, h, AmetraConfig.accent, a * (0.25f + 0.5f * hv));
        txt(ctx, label, x + (w - Gfx.width(label)) / 2f, y + (h - 8) / 2f, enabled ? TEXT : MUTED, a);
        if (enabled) hit(x, y, w, h, r, null);
    }

    private void slider(DrawContext ctx, String id, float x, float y, float w, float min, float max, float val, float a, Consumer<Float> set) {
        float f = Colors.clamp01((val - min) / (max - min));
        box(ctx, x, y + 5, w, 4, 0x2A2A3A, a);
        box(ctx, x, y + 5, Math.max(2f, w * f), 4, AmetraConfig.accent, a);
        float hv = an("sl:" + id, over(x - 4, y, w + 8, 14) ? 1f : 0f, 14f);
        box(ctx, x + w * f - 3, y + 1, 6, 12, Colors.lerp(0xD0D0DA, 0xFFFFFF, hv), a);
        hit(x - 4, y, w + 8, 14, null, (lx, ly) -> {
            float v = min + Colors.clamp01((lx - x) / w) * (max - min);
            set.accept(Math.round(v * 20f) / 20f);
        });
    }

    private void hueBar(DrawContext ctx, float x, float y, float w, float h, float hue, boolean marker, float a, Consumer<Float> set) {
        int seg = Math.max(1, Math.round(w / 2f));
        for (int i = 0; i < seg; i++) {
            float xa = x + w * i / seg, xb = x + w * (i + 1) / seg;
            rect(ctx, xa, y, xb - xa, h, Colors.hsv((i + 0.5f) / seg, 1f, 1f), a);
        }
        if (marker) {
            rect(ctx, x + w * hue - 1.5f, y - 2, 3, h + 4, 0x000000, a);
            rect(ctx, x + w * hue - 0.5f, y - 2, 1, h + 4, 0xFFFFFF, a);
        }
        hit(x - 2, y - 2, w + 4, h + 4, null, (lx, ly) -> set.accept(Colors.clamp01((lx - x) / w)));
    }

    private void svBox(DrawContext ctx, float x, float y, float w, float h, float hue, float s, float v, float a, BiConsumer<Float, Float> set) {
        int cols = Math.round(w / 2f);
        for (int i = 0; i < cols; i++) {
            float xa = x + w * i / cols, xb = x + w * (i + 1) / cols;
            float sat = (i + 0.5f) / cols;
            ctx.fillGradient(Math.round(xa), Math.round(y), Math.round(xb), Math.round(y + h),
                Colors.argb(Colors.hsv(hue, sat, 1f), a * pa), Colors.argb(0x000000, a * pa));
        }
        float mx = x + s * (w - 1), my = y + (1f - v) * (h - 1);
        ol(ctx, mx - 3, my - 3, 7, 7, 0x000000, a);
        ol(ctx, mx - 2, my - 2, 5, 5, 0xFFFFFF, a);
        hit(x, y, w, h, null, (lx, ly) -> set.accept(Colors.clamp01((lx - x) / w), 1f - Colors.clamp01((ly - y) / h)));
    }

    // ---------- Pestaña: rejilla de módulos (tarjetas estilo Lunar) ----------
    private void tabGrid(DrawContext ctx, float y0, int ac) {
        List<HudModule> list = filtered();
        if (list.isEmpty()) {
            String m = favOnly ? "Aún no tienes favoritos: toca el corazón de un módulo." : "No hay módulos que coincidan.";
            txt(ctx, m, GX + (GW - Gfx.width(m)) / 2f, y0 + 60, MUTED, 1f);
            contentH = 0f;
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            int col = i % COLS, row = i / COLS;
            card(ctx, list.get(i), GX + col * (CARD_W + GAP), y0 + row * (CARD_H + ROWGAP) + (1f - rowA(i)) * 10f, rowA(i), ac);
        }
        int rows = (list.size() + COLS - 1) / COLS;
        contentH = rows * (CARD_H + ROWGAP);
    }

    private void card(DrawContext ctx, HudModule m, float x, float y, float ra, int ac) {
        ModuleCfg c = m.cfg;
        float hv = an("ch:" + m.id, over(x, y, CARD_W, CARD_H) ? 1f : 0f, 14f);
        float en = an("cen:" + m.id, c.enabled ? 1f : 0f, 12f);
        float sl = an("csel:" + m.id, drawerOpen && drawerMod == m ? 1f : 0f, 14f);
        float yy = y - hv;

        hit(x, y, CARD_W, CARD_H, () -> openDrawer(m), null);
        box(ctx, x, yy, CARD_W, CARD_H, Colors.lerp(BG2, BG3, hv), ra);
        ol(ctx, x, yy, CARD_W, CARD_H, ac, ra * (0.06f + 0.28f * en + 0.5f * sl));
        rect(ctx, x + 6, yy, CARD_W - 12, 2, ac, ra * en);

        // nombre + corazón
        txt(ctx, trim(m.name, CARD_W - 34), x + 9, yy + 9, Colors.lerp(MUTED, TEXT, en), ra);
        float fk = an("fav:" + m.id, AmetraConfig.favorites.contains(m.id) ? 1f : 0f, 14f);
        float fh = an("favh:" + m.id, over(x + CARD_W - 21, yy + 5, 15, 13) ? 1f : 0f, 14f);
        Icons.heart(ctx, x + CARD_W - 17, yy + 9, Colors.lerp(Colors.lerp(0x4A4A5E, 0x9A9AAE, fh), 0xFF6B8B, fk), ra * pa);
        hit(x + CARD_W - 21, yy + 5, 15, 13, () -> toggleFav(m.id), null);

        // ícono grande
        int icol = Colors.lerp(0x5C5C70, Colors.lerp(TEXT, ac, 0.3f), en);
        float cx = x + CARD_W / 2f, cy = yy + 40;
        if (!Icons.module(ctx, m.id, cx, cy, 3f, icol, ra * pa)) {
            String l = m.name.substring(0, 1);
            Gfx.textScaled(ctx, l, cx - Gfx.width(l) * 2f, cy - 8, 4f, icol, ra * pa, true);
        }

        // engranaje + botón Activado / Desactivado
        float by = yy + CARD_H - 28;
        float gh = an("gear:" + m.id, over(x + 8, by, 20, 20) ? 1f : 0f, 14f);
        box(ctx, x + 8, by, 20, 20, Colors.lerp(BG3, 0x2C2C3C, gh), ra);
        Icons.gear(ctx, x + 13.5f, by + 5.5f, Colors.lerp(MUTED, TEXT, gh), ra * pa);
        hit(x + 8, by, 20, 20, () -> openDrawer(m), null);

        float bx = x + 34, bw = CARD_W - 42;
        float bh = an("bth:" + m.id, over(bx, by, bw, 20) ? 1f : 0f, 14f);
        box(ctx, bx, by, bw, 20, Colors.lerp(Colors.lerp(BG3, 0x2C2C3C, bh), Colors.lerp(BG3, ac, 0.5f + 0.15f * bh), en), ra);
        ol(ctx, bx, by, bw, 20, ac, ra * 0.4f * en);
        String lab = c.enabled ? "Activado" : "Desactivado";
        txt(ctx, lab, bx + (bw - Gfx.width(lab)) / 2f, by + 6, Colors.lerp(MUTED, 0xFFFFFF, en), ra);
        hit(bx, by, bw, 20, () -> { c.enabled = !c.enabled; AmetraConfig.save(); }, null);
    }

    // ---------- Panel lateral de ajustes ----------
    private void drawDrawer(DrawContext ctx, int ac) {
        float k = an("drawer", drawerOpen ? 1f : 0f, 12f);
        if (k <= 0.01f && !drawerOpen) { drawerMod = null; return; }
        if (drawerMod == null) return;
        HudModule m = drawerMod;
        ModuleCfg c = m.cfg;
        float e = Colors.ease(k);
        float x = PW - DW * e;
        box(ctx, x - 1, 1, DW + 1, PH - 2, LINE, k);
        box(ctx, x, 1, DW, PH - 2, 0x10101A, 0.99f * k);
        noHit = k < 0.85f;
        hit(x, 0, DW + 2, PH, () -> { }, null); // bloquea clics hacia la rejilla
        if (m.hud) drawPreview(ctx, m, x, k);

        float ox = x + 16, w = DW - 32;
        Gfx.bold(ctx, m.name, ox, 16, TEXT, k * pa, true);
        float cx = an("dx", over(x + DW - 30, 10, 18, 18) ? 1f : 0f, 14f);
        txt(ctx, "×", x + DW - 26, 14, Colors.lerp(MUTED, TEXT, cx), k);
        hit(x + DW - 30, 10, 18, 18, () -> drawerOpen = false, null);

        float y = 34;
        List<String> lines = wrap(m.desc, (int) w, 3);
        for (String ln : lines) { txt(ctx, ln, ox, y, MUTED, k); y += 10; }
        y += 6;

        txt(ctx, "Activado", ox, y + 2, TEXT, k);
        toggle(ctx, "dm:" + m.id, ox + w - 22, y, c.enabled, k, () -> { c.enabled = !c.enabled; AmetraConfig.save(); });
        y += 24;

        if (m.hasValue()) {
            txt(ctx, m.valueLabel, ox, y, MUTED, k);
            txt(ctx, String.format(Locale.ROOT, "%.1fx", c.value), ox + w - Gfx.width(String.format(Locale.ROOT, "%.1fx", c.value)), y, TEXT, k);
            slider(ctx, "val:" + m.id, ox, y + 10, w, m.vMin, m.vMax, c.value, k, v -> c.value = v);
            y += 32;
            if (m.id.equals("zoom") && Features.ZOOM_KEY != null) {
                txt(ctx, "Tecla: " + Features.ZOOM_KEY.getBoundKeyLocalizedText().getString() + " (cámbiala en Controles)", ox, y, MUTED, k * 0.9f);
                y += 12;
                txt(ctx, "Rueda del ratón con la tecla: cambia el nivel", ox, y, MUTED, k * 0.9f);
                y += 14;
            }
        }

        if (m.id.equals("autotext")) {
            txt(ctx, "Asigna las teclas en Controles → RzClient", ox, y, MUTED, k * 0.9f);
            y += 14;
        }

        for (HudModule.Extra xv : m.extras) {
            final String key = xv.key;
            float cur = c.ex(key, xv.def);
            String shown = xv.names != null
                ? xv.names[Math.max(0, Math.min(xv.names.length - 1, Math.round(cur - xv.min)))]
                : String.format(Locale.ROOT, xv.fmt, cur);
            txt(ctx, xv.label, ox, y, MUTED, k);
            txt(ctx, shown, ox + w - Gfx.width(shown), y, TEXT, k);
            final boolean stepped = xv.names != null;
            slider(ctx, "ex:" + m.id + ":" + key, ox, y + 10, w, xv.min, xv.max, cur, k,
                val -> c.ex.put(key, stepped ? (float) Math.round(val) : val));
            y += 32;
        }
        if (!m.extras.isEmpty()) {
            button(ctx, "dexr", "Restablecer valores", ox, y, w, 18, true, k, () -> {
                for (HudModule.Extra xr : m.extras) c.ex.put(xr.key, xr.def);
                AmetraConfig.save();
            });
            y += 24;
        }

        for (Map.Entry<String, String> tf : m.textFields.entrySet()) {
            final String tk = tf.getKey();
            boolean on = tk.equals(textEdit);
            float fh = an("tf:" + tk, on ? 1f : 0f, 14f);
            txt(ctx, tf.getValue(), ox, y, MUTED, k);
            box(ctx, ox - 1, y + 9, w + 2, 18, Colors.lerp(LINE, AmetraConfig.accent, fh), k);
            box(ctx, ox, y + 10, w, 16, BG2, k);
            String val = AmetraConfig.text(tk);
            String shown = on ? tail(val + (((int) (time * 2f) % 2 == 0) ? "_" : " "), (int) w - 8) : trim(val, (int) w - 8);
            txt(ctx, shown, ox + 4, y + 14, TEXT, k);
            hit(ox, y + 10, w, 16, () -> { textEdit = tk; searchFocus = false; }, null);
            y += 32;
        }

        if (m.hud || m.colorable) {
            txt(ctx, "Color", ox, y, MUTED, k);
            float sy = y + 12;
            for (int i = 0; i < MOD_PRESETS.length; i++) {
                final int col = MOD_PRESETS[i];
                float bx = ox + i * 22;
                boolean sel = !c.chroma && c.color == col;
                float hv = an("dsw:" + i, over(bx, sy, 16, 14) ? 1f : 0f, 14f);
                box(ctx, bx, sy, 16, 14, col, k);
                if (sel) ol(ctx, bx - 2, sy - 2, 20, 18, TEXT, k);
                else ol(ctx, bx - 1, sy - 1, 18, 16, TEXT, k * hv * 0.6f);
                hit(bx, sy, 16, 14, () -> { c.color = col; c.chroma = false; AmetraConfig.save(); }, null);
            }
            hueBar(ctx, ox, sy + 22, w, 12, Colors.toHsv(c.color)[0], !c.chroma, k, hue -> { c.color = Colors.hsv(hue, 1f, 1f); c.chroma = false; });
            y = sy + 44;

            txt(ctx, "Chroma", ox, y + 2, MUTED, k);
            toggle(ctx, "dch:" + m.id, ox + 44, y, c.chroma, k, () -> { c.chroma = !c.chroma; AmetraConfig.save(); });
            if (m.hud) {
                txt(ctx, "Fondo", ox + 92, y + 2, MUTED, k);
                toggle(ctx, "dbg:" + m.id, ox + 126, y, c.bg, k, () -> { c.bg = !c.bg; AmetraConfig.save(); });
            }
            y += 20;
        }

        if (m.hud) {
            txt(ctx, "Sombra", ox, y + 2, MUTED, k);
            toggle(ctx, "dsh:" + m.id, ox + 44, y, c.shadow, k, () -> { c.shadow = !c.shadow; AmetraConfig.save(); });
            y += 22;

            txt(ctx, "Escala", ox, y, MUTED, k);
            String sc = String.format(Locale.ROOT, "%.2fx", c.scale);
            txt(ctx, sc, ox + w - Gfx.width(sc), y, TEXT, k);
            slider(ctx, "dsc:" + m.id, ox, y + 10, w, 0.5f, 2f, c.scale, k, v -> c.scale = v);
            y += 32;

            button(ctx, "dmove", "Mover en pantalla", ox, y, w, 18, client != null && client.player != null, k, () -> { edit = true; drawerOpen = false; });
            button(ctx, "dreset", "Reiniciar posición", ox, y + 22, w, 18, true, k, () -> { m.resetPos(); AmetraConfig.save(); });
        }
        noHit = false;
    }

    /** Vista previa en vivo del módulo, al lado del panel de ajustes (con sus colores, fondo, sombra y escala). */
    private void drawPreview(DrawContext ctx, HudModule m, float drawerX, float k) {
        float pw = 150, ph = 92;
        float px = drawerX - pw - 10, py = TOP0 + 8;
        box(ctx, px - 1, py - 1, pw + 2, ph + 2, LINE, k);
        rect(ctx, px, py, pw, ph * 0.62f, 0x3C5A80, 0.95f * k); // cielo
        rect(ctx, px, py + ph * 0.62f, pw, ph * 0.38f, 0x2E4A2B, 0.95f * k); // suelo
        txt(ctx, "Vista previa", px, py + ph + 5, MUTED, k);
        hit(px - 1, py - 1, pw + 2, ph + 16, () -> { }, null); // no deja pasar clics a las tarjetas de atrás

        float fit = Math.min((pw - 16f) / Math.max(1, m.w), (ph - 16f) / Math.max(1, m.h));
        float sc = Math.min(m.cfg.scale, fit);
        float dw = m.w * sc, dh = m.h * sc;
        int color = m.cfg.chroma ? Colors.hsv((System.currentTimeMillis() % 4000L) / 4000f, 0.6f, 1f) : m.cfg.color;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(px + (pw - dw) / 2f, py + (ph - dh) / 2f, 0f);
        ms.scale(sc, sc, 1f);
        if (m instanceof Hud.ArmorModule && client.player == null) {
            Gfx.text(ctx, "Entra a un mundo", 0, 0, TEXT, k * pa, true);
        } else {
            m.draw(ctx, client, k * pa, color);
        }
        ms.pop();
    }

    // ---------- Pestaña: Tema ----------
    private void applyAccent() {
        AmetraConfig.accent = Colors.hsv(aH, aS, aV);
    }

    private void tabTheme(DrawContext ctx, float y0, int ac) {
        float a = Colors.ease(tabAge * 6f);
        txt(ctx, "COLOR DE ACENTO", GX, y0, MUTED, a);
        float by = y0 + 14;

        svBox(ctx, GX, by, 150, 70, aH, aS, aV, a, (s, v) -> { aS = s; aV = v; applyAccent(); });
        hueBar(ctx, GX, by + 78, 150, 10, aH, true, a, h -> { aH = h; applyAccent(); });

        float px = GX + 172;
        box(ctx, px, by, 44, 44, ac, a);
        txt(ctx, Colors.hex(ac), px + 54, by + 9, TEXT, a);
        txt(ctx, "Acento del menú y del HUD", px + 54, by + 23, MUTED, a);
        for (int i = 0; i < ACC_PRESETS.length; i++) {
            final int col = ACC_PRESETS[i];
            float bx = px + (i % 8) * 22, byy = by + 56 + (i / 8) * 20;
            float hv = an("ap" + i, over(bx, byy, 16, 16) ? 1f : 0f, 14f);
            box(ctx, bx, byy, 16, 16, col, a);
            if (col == ac) ol(ctx, bx - 2, byy - 2, 20, 20, TEXT, a);
            else ol(ctx, bx - 1, byy - 1, 18, 18, TEXT, a * hv * 0.6f);
            hit(bx, byy, 16, 16, () -> {
                float[] hsv = Colors.toHsv(col);
                aH = hsv[0]; aS = hsv[1]; aV = hsv[2];
                AmetraConfig.accent = col;
                AmetraConfig.save();
            }, null);
        }

        float t2 = by + 108;
        txt(ctx, "ANIMACIONES Y EFECTOS", GX, t2, MUTED, a);
        float tgx = GX + GW - 30;
        txt(ctx, "Animaciones del menú y del HUD", GX, t2 + 20, TEXT, a);
        toggle(ctx, "anim", tgx, t2 + 18, AmetraConfig.animations, a, () -> { AmetraConfig.animations = !AmetraConfig.animations; AmetraConfig.save(); });
        txt(ctx, "Bienvenida al entrar a un mundo o servidor", GX, t2 + 42, TEXT, a);
        toggle(ctx, "welcome", tgx, t2 + 40, AmetraConfig.welcome, a, () -> { AmetraConfig.welcome = !AmetraConfig.welcome; AmetraConfig.save(); });
        txt(ctx, "Efectos en el menú principal", GX, t2 + 64, TEXT, a);
        toggle(ctx, "titlefx", tgx, t2 + 62, AmetraConfig.titleFx, a, () -> { AmetraConfig.titleFx = !AmetraConfig.titleFx; AmetraConfig.save(); });

        txt(ctx, "Letra propia en el HUD y el menú", GX, t2 + 86, TEXT, a);
        toggle(ctx, "hfont", tgx, t2 + 84, AmetraConfig.hudFont, a, () -> { AmetraConfig.hudFont = !AmetraConfig.hudFont; AmetraConfig.save(); });
        txt(ctx, "HUD compacto (estilo Feather)", GX, t2 + 108, TEXT, a);
        toggle(ctx, "hcomp", tgx, t2 + 106, AmetraConfig.hudCompact, a, () -> { AmetraConfig.hudCompact = !AmetraConfig.hudCompact; AmetraConfig.save(); });
        txt(ctx, "Ocultar el HUD cuando abres F3", GX, t2 + 130, TEXT, a);
        toggle(ctx, "hf3", tgx, t2 + 128, AmetraConfig.hideOnF3, a, () -> { AmetraConfig.hideOnF3 = !AmetraConfig.hideOnF3; AmetraConfig.save(); });
        txt(ctx, "Mostrar mis cosméticos en el juego", GX, t2 + 152, TEXT, a);
        toggle(ctx, "hcos", tgx, t2 + 150, AmetraConfig.cosmeticsOn, a, () -> { AmetraConfig.cosmeticsOn = !AmetraConfig.cosmeticsOn; AmetraConfig.save(); });

        txt(ctx, "Menú principal de RzClient (en vez del de Minecraft)", GX, t2 + 174, TEXT, a);
        toggle(ctx, "ctitle", tgx, t2 + 172, AmetraConfig.customTitle, a, () -> { AmetraConfig.customTitle = !AmetraConfig.customTitle; AmetraConfig.save(); });

        button(ctx, "resetall", "Restablecer posiciones del HUD", GX, t2 + 200, 190, 20, true, a, () -> {
            for (HudModule m : Hud.MODULES) m.resetPos();
            AmetraConfig.save();
        });
        button(ctx, "prevwelcome", "Ver bienvenida", GX + 198, t2 + 200, 110, 20, client != null && client.player != null, a, Welcome::preview);
        contentH = (t2 + 200 + 20 + 6) - y0;
    }

    // ---------- Pestaña: Fondos del menú principal (con vista previa en vivo) ----------
    // ---------- Pestaña PERSONALIZAR: mira y cursor propios ----------
    private void tabCustom(DrawContext ctx, float y0, int ac) {
        float a = Colors.ease(tabAge * 6f);
        float cw = (GW - 16) / 2f;
        float e1 = customCol(ctx, "MIRA PROPIA (PNG)", "miras", AmetraConfig.customCrosshair, GX, y0, cw, a, ac, n -> {
            AmetraConfig.customCrosshair = n;
            Custom.reloadCross();
            if (!n.isEmpty() && Hud.CROSSHAIR != null) {
                Hud.CROSSHAIR.cfg.enabled = true;
                Hud.CROSSHAIR.cfg.ex.put("style", 5f);
            }
            AmetraConfig.save();
            toast(n.isEmpty() ? "Mira normal" : "Mira: " + n);
        }, "Se activa sola con el estilo «Imagen propia». El tamaño se cambia en el módulo Crosshair.");
        float e2 = customCol(ctx, "CURSOR PROPIO (PNG)", "cursores", AmetraConfig.customCursor, GX + cw + 16, y0, cw, a, ac, n -> {
            AmetraConfig.customCursor = n;
            AmetraConfig.save();
            Custom.applyCursor();
            toast(n.isEmpty() ? "Cursor normal" : "Cursor: " + n);
        }, "Se ve en los menús. Se reduce a 32 px como máximo; el punto de clic es la esquina de arriba a la izquierda.");
        // cosméticos 3D hechos en el Estudio del launcher
        float y = Math.max(e1, e2) + 14;
        txt(ctx, "COSMÉTICOS 3D PROPIOS (se crean en el launcher → Cosméticos → Crear cosmético 3D)", GX, y, MUTED, a);
        y += 14;
        button(ctx, "vcf", "Abrir carpeta", GX, y, 110, 20, true, a, () -> Custom.openFolder("cosmeticos"));
        button(ctx, "vcr", "Recargar", GX + 116, y, 80, 20, true, a, () -> { VoxelCosmetics.rescan(); toast("Recargado"); });
        y += 28;
        java.util.List<VoxelCosmetics.Cos> own = VoxelCosmetics.list();
        for (int i = 0; i < own.size(); i++) {
            final VoxelCosmetics.Cos c = own.get(i);
            boolean sel = c.name.equals(VoxelCosmetics.equipped(c.slot));
            String slotName = c.slot;
            for (int k = 0; k < COS_IDS.length; k++) if (COS_IDS[k].equals(c.slot)) slotName = COS_NAMES[k];
            float ra = rowA(i);
            boolean ov = over(GX, y, GW, 22);
            float hv = an("vc:" + c.name, ov ? 1f : 0f, 14f);
            box(ctx, GX, y, GW, 22, Colors.lerp(BG2, BG3, hv), ra);
            if (sel) rect(ctx, GX, y + 3, 2, 16, ac, ra);
            txt(ctx, trim(c.name, (int) (GW - 150)), GX + 9, y + 7, sel ? TEXT : Colors.lerp(MUTED, TEXT, 0.6f + 0.4f * hv), ra);
            String tag = sel ? slotName + " · puesto" : slotName;
            float tw = Gfx.width(tag);
            txt(ctx, tag, GX + GW - tw - 8, y + 7, sel ? ac : MUTED, ra);
            hit(GX, y, GW, 22, () -> {
                VoxelCosmetics.equip(c.slot, sel ? null : c.name);
                toast(sel ? "Quitado: " + c.name : "Puesto: " + c.name);
            }, null);
            y += 25;
        }
        if (own.isEmpty()) {
            txt(ctx, "Aún no tienes. Créalos en el launcher y pulsa Recargar.", GX, y + 2, MUTED, a);
            y += 16;
        }
        contentH = y - y0 + 6;
    }

    private float customCol(DrawContext ctx, String title, String sub, String selected, float x, float y0, float w, float a, int ac,
                            Consumer<String> pick, String hint) {
        txt(ctx, title, x, y0, MUTED, a);
        float y = y0 + 14;
        button(ctx, "cf" + sub, "Abrir carpeta", x, y, w / 2f - 2, 20, true, a, () -> Custom.openFolder(sub));
        button(ctx, "cr" + sub, "Recargar", x + w / 2f + 2, y, w / 2f - 2, 20, true, a, () -> { Custom.reloadCross(); toast("Recargado"); });
        y += 28;
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add("");
        names.addAll(Custom.list(sub));
        for (int i = 0; i < names.size(); i++) {
            final String n = names.get(i);
            boolean sel = n.equals(selected == null ? "" : selected);
            float ra = rowA(i);
            boolean ov = over(x, y, w, 22);
            float hv = an("cu:" + sub + n, ov ? 1f : 0f, 14f);
            box(ctx, x, y, w, 22, Colors.lerp(BG2, BG3, hv), ra);
            if (sel) rect(ctx, x, y + 3, 2, 16, ac, ra);
            txt(ctx, trim(n.isEmpty() ? "Ninguno (normal)" : n, (int) (w - 20)), x + 9, y + 7, sel ? TEXT : Colors.lerp(MUTED, TEXT, 0.6f + 0.4f * hv), ra);
            hit(x, y, w, 22, () -> pick.accept(n), null);
            y += 25;
        }
        y += 4;
        for (String s : wrap(hint, (int) w, 4)) {
            txt(ctx, s, x, y, MUTED, a);
            y += 10;
        }
        return y;
    }

    /** Servidores del launcher (partners de Ametra + los tuyos): un clic y entras. */
    private void tabServers(DrawContext ctx, float y0, int ac) {
        float a = Colors.ease(tabAge * 6f);
        List<Servers.Entry> list = Servers.list();
        txt(ctx, "SERVIDORES · haz clic en uno para entrar", GX, y0, MUTED, a);
        float y = y0 + 14;
        if (list.isEmpty()) {
            for (String s : wrap("Aún no hay servidores. Añádelos en el launcher (pestaña Servidores) y aparecerán aquí al instante.", GW, 3)) {
                txt(ctx, s, GX, y + 4, MUTED, a);
                y += 10;
            }
            contentH = y - y0 + 14;
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            final Servers.Entry e = list.get(i);
            float ra = rowA(i);
            boolean ov = over(GX, y, GW, 32);
            float hv = an("sv:" + i, ov ? 1f : 0f, 14f);
            box(ctx, GX, y, GW, 32, Colors.lerp(BG2, BG3, hv), ra);
            if (e.main) rect(ctx, GX, y + 4, 2, 24, ac, ra);
            box(ctx, GX + 7, y + 6, 20, 20, e.color, ra);
            String ini = e.name.isEmpty() ? "?" : e.name.substring(0, 1).toUpperCase(Locale.ROOT);
            txt(ctx, ini, GX + 7 + (20 - Gfx.width(ini)) / 2f, y + 12, 0xFFFFFF, ra);
            float right = GX + GW - 10;
            String go = "ENTRAR";
            float gw = Gfx.width(go) + 14;
            box(ctx, right - gw, y + 7, gw, 18, Colors.lerp(BG3, ac, 0.25f + 0.5f * hv), ra);
            txt(ctx, go, right - gw + 7, y + 12, TEXT, ra);
            float tx = right - gw - 8;
            if (e.partner) {
                String tag = "PARTNER";
                float tw = Gfx.width(tag) + 8;
                box(ctx, tx - tw, y + 10, tw, 12, ac, 0.22f * ra);
                txt(ctx, tag, tx - tw + 4, y + 12, ac, ra);
                tx -= tw + 8;
            }
            txt(ctx, trim(e.name, (int) (tx - (GX + 36))), GX + 36, y + 6, TEXT, ra);
            txt(ctx, trim(e.desc.isEmpty() ? e.host : e.host + "  ·  " + e.desc, (int) (tx - (GX + 36))), GX + 36, y + 18, MUTED, ra);
            hit(GX, y, GW, 32, () -> Servers.join(e), null);
            y += 36;
        }
        contentH = y - y0 + 6;
    }

    private void tabBackgrounds(DrawContext ctx, float y0, int ac) {
        float a = Colors.ease(tabAge * 6f);
        List<Backgrounds.Entry> list = Backgrounds.list();
        Backgrounds.Entry cur = Backgrounds.current();
        Backgrounds.Entry shown = cur;
        if (bgHover != null && bgHoverT > 0.3f) {
            Backgrounds.Entry hv = Backgrounds.find(bgHover);
            if (hv != null) shown = hv;
        }

        // columna izquierda: así se vería el menú principal (en pequeño, porque este menú es grande)
        float pw = 236, ph = 133, px = GX, py = y0 + 14;
        txt(ctx, shown == cur ? "VISTA PREVIA · EN USO" : "VISTA PREVIA · " + trim(shown.name, 150), GX, y0, MUTED, a);
        box(ctx, px - 1, py - 1, pw + 2, ph + 2, LINE, a);
        box(ctx, px, py, pw, ph, BG2, a);
        if (a * pa > 0.97f) {
            pushClip(ctx, px, py, px + pw, py + ph);
            Backgrounds.drawEntry(ctx, shown, Math.round(px), Math.round(py), Math.round(pw), Math.round(ph), time, true);
            miniTitle(ctx, px, py, pw, ph, ac);
            popClip(ctx);
        }

        float cy = py + ph + 12;
        txt(ctx, "Oscurecer imagen / GIF: " + Math.round(AmetraConfig.bgDim * 100f) + "%", px, cy, TEXT, a);
        slider(ctx, "bgdim", px, cy + 12, pw, 0f, 0.85f, AmetraConfig.bgDim, a, v -> AmetraConfig.bgDim = v);
        button(ctx, "bgfolder", "Abrir carpeta de fondos", px, cy + 34, 150, 20, true, a, Backgrounds::openFolder);
        button(ctx, "bgreload", "Recargar", px + 156, cy + 34, 80, 20, true, a, () -> { Backgrounds.reload(); toast("Fondos recargados"); });
        float iy = cy + 60;
        for (String s : wrap("Suelta en la carpeta imágenes PNG/JPG, GIFs animados o una carpeta con frames (img_001.png, img_002.png…; "
            + "pon @30 al final del nombre para 30 fps). Para un video, conviértelo a GIF.", (int) pw, 4)) {
            txt(ctx, s, px, iy, MUTED, a);
            iy += 10;
        }

        // columna derecha: lista de fondos
        float lx = GX + 248, lw = GW - 248;
        txt(ctx, "TUS FONDOS", lx, y0, MUTED, a);
        float y = y0 + 14;
        String newHover = null;
        for (int i = 0; i < list.size(); i++) {
            final Backgrounds.Entry e = list.get(i);
            boolean sel = e == cur;
            float ra = rowA(i);
            boolean ov = over(lx, y, lw, 22);
            float hv = an("bg:" + e.id, ov ? 1f : 0f, 14f);
            box(ctx, lx, y, lw, 22, Colors.lerp(BG2, BG3, hv), ra);
            if (sel) rect(ctx, lx, y + 3, 2, 16, ac, ra);
            String tag = e.tag();
            float tw = Gfx.width(tag) + 8;
            txt(ctx, trim(e.name, (int) (lw - tw - 24)), lx + 9, y + 7, sel ? TEXT : Colors.lerp(MUTED, TEXT, 0.6f + 0.4f * hv), ra);
            box(ctx, lx + lw - tw - 5, y + 5, tw, 12, sel ? ac : 0x2A2A3A, (sel ? 0.25f : 1f) * ra);
            txt(ctx, tag, lx + lw - tw - 1, y + 7, sel ? ac : MUTED, ra);
            if (ov) newHover = e.id;
            hit(lx, y, lw, 22, () -> { Backgrounds.select(e.id); toast("Fondo: " + e.name); }, null);
            y += 25;
        }
        if (newHover != null && newHover.equals(bgHover)) bgHoverT += dt;
        else { bgHover = newHover; bgHoverT = 0f; }
        if (list.size() <= 4) txt(ctx, "Aún no hay fondos tuyos.", lx, y + 2, MUTED, a);

        contentH = Math.max(iy, y + 14) - y0 + 6;
    }

    /** Menú principal en miniatura (para la vista previa): oscurecido, logo, título y botones. */
    private void miniTitle(DrawContext ctx, float x, float y, float w, float h, int ac) {
        float k = w / 320f;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(x, y, 0f);
        ms.scale(k, k, 1f);
        Gfx.logo(ctx, 142, 14, 36, 1f);
        String t = "RZCLIENT";
        Gfx.textScaled(ctx, t, 160 - Gfx.boldWidth(t) * 1.3f / 2f, 56, 1.3f, ac, 1f, true);
        String[] ls = {"Un jugador", "Multijugador", "Opciones"};
        for (int i = 0; i < ls.length; i++) {
            float by = 84 + i * 19;
            Gfx.rbox(ctx, 110, by, 100, 15, 0x1B1B28, 0.92f);
            Gfx.outline(ctx, 110, by, 100, 15, ac, 0.3f);
            Gfx.rect(ctx, 112, by + 3, 2, 9, ac, 0.8f);
            Gfx.text(ctx, ls[i], 160 - Gfx.width(ls[i]) / 2f, by + 4, 0xE6E6F0, 1f, true);
        }
        ms.pop();
    }

    // ---------- Pestaña: Cosméticos (equipar desde el juego, con vista previa) ----------
    private static int rarityColor(String r) {
        return switch (r) {
            case "raro" -> 0x56CCF2;
            case "epico" -> 0xBB6BD9;
            case "legendario" -> 0xFFD93D;
            default -> 0x9AA3AF;
        };
    }

    private static String rarityName(String r) {
        return switch (r) {
            case "raro" -> "Raro";
            case "epico" -> "Épico";
            case "legendario" -> "Legendario";
            default -> "Común";
        };
    }

    private static boolean backCat(String c) {
        return c.equals("cape") || c.equals("wings") || c.equals("pack") || c.equals("back");
    }

    private void tabCosmetics(DrawContext ctx, float y0, int ac) {
        float a = Colors.ease(tabAge * 6f);
        gridAge += dt;
        AmetraConfig.clearPreview();

        // ---- derecha: categorías + tarjetas (primero, porque al pasar el cursor se activa la vista previa)
        float cx0 = GX + 132, cw = GW - 132;
        float chipX = cx0, chipY = y0;
        for (int i = 0; i < COS_IDS.length; i++) {
            final String id = COS_IDS[i];
            String nm = COS_NAMES[i];
            float tw = Gfx.width(nm) + 16;
            if (chipX + tw > cx0 + cw && chipX > cx0) { chipX = cx0; chipY += 22; } // salta de fila si no cabe
            boolean on = id.equals(cosCat);
            float hv = an("cc" + i, over(chipX, chipY, tw, 18) ? 1f : 0f, 14f);
            box(ctx, chipX, chipY, tw, 18, on ? Colors.lerp(BG3, ac, 0.28f) : Colors.lerp(BG2, BG3, hv), a);
            if (on) ol(ctx, chipX, chipY, tw, 18, ac, a * 0.7f);
            txt(ctx, nm, chipX + 8, chipY + 5, on ? TEXT : Colors.lerp(MUTED, TEXT, hv), a);
            hit(chipX, chipY, tw, 18, () -> { cosCat = id; gridAge = 0f; spinTarget = backCat(id) ? 180f : 0f; }, null);
            chipX += tw + 6;
        }

        List<CosmeticData.Item> items = new ArrayList<>();
        for (CosmeticData.Item it : CosmeticData.ALL) if (it.cat.equals(cosCat)) items.add(it);
        String eq = AmetraConfig.equipped(cosCat);
        float gx = cx0, gy = chipY + 28, gw = (cw - 16) / 3f, gh = 50;
        for (int i = 0; i <= items.size(); i++) {
            final CosmeticData.Item it = i == 0 ? null : items.get(i - 1);
            final boolean owned = it == null || AmetraConfig.owns(it);
            boolean sel = it == null ? eq == null : it.id.equals(eq);
            float x = gx + (i % 3) * (gw + 8), y = gy + (i / 3) * (gh + 8);
            float ra = Colors.ease(gridAge * 6f - i * 0.1f);
            boolean ov = over(x, y, gw, gh);
            float hv = an("cs:" + cosCat + ":" + i, ov ? 1f : 0f, 14f);
            int rc = it == null ? 0x5A5A6A : rarityColor(it.rar);
            box(ctx, x, y, gw, gh, Colors.lerp(BG2, BG3, hv), a * ra);
            ol(ctx, x, y, gw, gh, sel ? ac : LINE, a * ra * (sel ? 0.9f : 0.6f));
            rect(ctx, x + 1, y + 5, 2, gh - 10, rc, a * ra);
            txt(ctx, trim(it == null ? "Ninguno" : it.name, (int) gw - 18), x + 9, y + 9, owned ? TEXT : MUTED, a * ra);
            txt(ctx, it == null ? "Quitar" : rarityName(it.rar), x + 9, y + 21, rc, a * ra * 0.9f);
            String st = sel ? "EQUIPADO" : owned ? (it == null ? "" : "Tuyo") : "Bloqueado · " + it.price;
            txt(ctx, st, x + 9, y + 34, sel ? ac : MUTED, a * ra * 0.9f);
            if (ov) {
                AmetraConfig.setPreview(cosCat, it == null ? null : it.id);
                String hk = cosCat + ":" + (it == null ? "-" : it.id);
                if (!hk.equals(lastCosHover)) {
                    lastCosHover = hk;
                    spinTarget = backCat(cosCat) ? 180f : 0f;
                }
            }
            hit(x, y, gw, gh, () -> {
                if (!owned) { toast("Bloqueado: se compra en la Tienda del launcher"); return; }
                AmetraConfig.setCosmetic(cosCat, it == null ? null : it.id);
                toast(it == null ? "Quitado" : "Equipado: " + it.name);
            }, null);
        }
        int rows = (items.size() + 3) / 3;
        float gridEnd = gy + rows * (gh + 8);
        txt(ctx, "Pasa el cursor para ver cómo queda · clic para equipar", cx0, gridEnd + 2, MUTED, a * 0.9f);

        // ---- izquierda: tu personaje
        float bx = GX, by = y0, bw = 118, bh = 190;
        box(ctx, bx - 1, by - 1, bw + 2, bh + 2, LINE, a);
        box(ctx, bx, by, bw, bh, BG2, a);
        boolean inWorld = client != null && client.player != null;
        if (inWorld) {
            spin += (spinTarget - spin) * Math.min(1f, dt * 8f);
            if (a * pa > 0.97f) {
                pushClip(ctx, bx, by, bx + bw, by + bh);
                PlayerPreview.draw(ctx, bx + bw / 2f, by + bh / 2f + 6f, Math.round(bh / 2.3f), spin);
                popClip(ctx);
            }
            txt(ctx, "Arrastra para girar", bx + 8, by + bh - 12, MUTED, a * 0.8f);
            hit(bx, by, bw, bh, null, (lx, ly) -> {
                float d = (lx - (bx + bw / 2f)) * 3.2f;
                spin = d;
                spinTarget = d;
            });
        } else {
            float ty = by + 70;
            for (String s : wrap("Entra a un mundo para ver tu personaje con los cosméticos puestos.", (int) bw - 16, 5)) {
                txt(ctx, s, bx + 8, ty, MUTED, a);
                ty += 11;
            }
        }
        button(ctx, "cfront", "Frente", bx, by + bh + 8, 56, 18, inWorld, a, () -> spinTarget = 0f);
        button(ctx, "cback", "Espalda", bx + 62, by + bh + 8, 56, 18, inWorld, a, () -> spinTarget = 180f);
        txt(ctx, "Mostrar", bx, by + bh + 34, TEXT, a);
        toggle(ctx, "cosOn", bx + bw - 24, by + bh + 32, AmetraConfig.cosmeticsOn, a, () -> { AmetraConfig.cosmeticsOn = !AmetraConfig.cosmeticsOn; AmetraConfig.save(); });
        button(ctx, "cosclear", "Quitar todo", bx, by + bh + 50, bw, 18, true, a, () -> {
            for (String c : COS_IDS) AmetraConfig.setCosmetic(c, null);
            toast("Cosméticos quitados");
        });

        contentH = Math.max(by + bh + 68, gridEnd + 14) - y0 + 6;
    }

    // ---------- Pestaña: Mods instalados ----------
    private void tabMods(DrawContext ctx, float y0, int ac) {
        float a0 = Colors.ease(tabAge * 6f);
        txt(ctx, mods.size() + " mods instalados en esta instancia", GX, y0, MUTED, a0);
        float y = y0 + 16;
        for (int i = 0; i < mods.size(); i++) {
            ModInfo mi = mods.get(i);
            float ra = rowA(i);
            float x = GX + (1f - ra) * 14f;
            float hv = an("mi:" + mi.id, over(GX, y, GW, 30) ? 1f : 0f, 14f);
            box(ctx, x, y, GW, 30, Colors.lerp(BG2, BG3, hv), ra);
            box(ctx, x + 8, y + 5, 20, 20, mi.color, ra);
            String letter = mi.name.substring(0, 1).toUpperCase(Locale.ROOT);
            txt(ctx, letter, x + 8 + (20 - Gfx.width(letter)) / 2f, y + 11, 0x0A0A12, ra);
            txt(ctx, trim(mi.name, GW - 120), x + 38, y + 6, TEXT, ra);
            txt(ctx, trim(mi.id + "  ·  v" + mi.ver, GW - 120), x + 38, y + 17, MUTED, ra * 0.9f);
            if (mi.id.equals("ametra")) {
                String tag = "CLIENTE";
                float tw = Gfx.width(tag) + 10;
                box(ctx, x + GW - tw - 8, y + 8, tw, 14, ac, 0.2f * ra);
                txt(ctx, tag, x + GW - tw - 3, y + 11, ac, ra);
            }
            y += 34;
        }
        contentH = y - y0;
    }

    // ---------- Editor de posición del HUD ----------
    private void drawEdit(DrawContext ctx, int mx, int my, int ac) {
        float a = editFade;
        String hint = "Arrastra los módulos  ·  Rueda: tamaño  ·  Clic derecho: reiniciar  ·  ESC: volver";
        float hw = Gfx.width(hint) + 24;
        float hx = (width - hw) / 2f;
        Gfx.rbox(ctx, hx - 1, 7, hw + 2, 22, ac, 0.5f * a);
        Gfx.rbox(ctx, hx, 8, hw, 20, 0x0B0B11, 0.94f * a);
        Gfx.text(ctx, hint, hx + 12, 14, TEXT, a, false);

        for (HudModule m : Hud.MODULES) {
            if (!m.hud || !m.cfg.enabled) continue;
            float x = m.cfg.x, y = m.cfg.y, w = m.w * m.cfg.scale, h = m.h * m.cfg.scale;
            boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
            boolean drag = dragging == m;
            float pulse = 0.5f + 0.25f * (float) Math.sin(time * 4f);
            Gfx.rect(ctx, x - 2, y - 2, w + 4, h + 4, ac, a * (drag ? 0.20f : hov ? 0.12f : 0.05f));
            Gfx.outline(ctx, x - 2, y - 2, w + 4, h + 4, ac, a * (drag ? 1f : hov ? 0.95f : pulse));
            if (hov || drag) {
                String lab = m.name + "  " + Math.round(m.cfg.scale * 100f) + "%";
                float ly = y >= 14 ? y - 14 : y + h + 4;
                Gfx.rbox(ctx, x - 2, ly - 2, Gfx.width(lab) + 8, 12, 0x0B0B11, 0.9f * a);
                Gfx.text(ctx, lab, x + 2, ly, TEXT, a, false);
            }
        }

        float bx = width / 2f - 44, by = height - 36;
        boolean hv = mx >= bx && mx < bx + 88 && my >= by && my < by + 22;
        float k = an("done", hv ? 1f : 0f, 14f);
        Gfx.rbox(ctx, bx, by, 88, 22, Colors.lerp(ac, 0xFFFFFF, k * 0.25f), a);
        Gfx.text(ctx, "Listo", bx + (88 - Gfx.width("Listo")) / 2f, by + 7, 0x0A0A12, a, false);
    }

    private boolean inRect(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------- Entrada ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closing) return true;
        if (edit) {
            if (button == 0 && inRect(mx, my, width / 2f - 44, height - 36, 88, 22)) { edit = false; return true; }
            for (int i = Hud.MODULES.size() - 1; i >= 0; i--) {
                HudModule m = Hud.MODULES.get(i);
                if (!m.hud || !m.cfg.enabled) continue;
                if (inRect(mx, my, m.cfg.x, m.cfg.y, m.w * m.cfg.scale, m.h * m.cfg.scale)) {
                    if (button == 1) { m.resetPos(); AmetraConfig.save(); }
                    else if (button == 0) { dragging = m; dragOffX = (float) mx - m.cfg.x; dragOffY = (float) my - m.cfg.y; }
                    return true;
                }
            }
            return true;
        }
        if (button != 0) return true;
        if (renaming) finishRename(true);
        searchFocus = false;
        endTextEdit(); // un clic fuera cierra el campo; si fue sobre un campo, su clic lo vuelve a abrir
        float lx = toLX(mx), ly = toLY(my);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (lx >= h.x && lx < h.x + h.w && ly >= h.y && ly < h.y + h.h
                && lx >= h.clip[0] && lx < h.clip[2] && ly >= h.clip[1] && ly < h.clip[3]) {
                if (h.drag != null) { active = h; h.drag.on(lx, ly); }
                else if (h.click != null) h.click.run();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (edit && dragging != null) {
            float w = dragging.w * dragging.cfg.scale, h = dragging.h * dragging.cfg.scale;
            dragging.cfg.x = Math.round(Math.max(0f, Math.min(width - w, (float) mx - dragOffX)));
            dragging.cfg.y = Math.round(Math.max(0f, Math.min(height - h, (float) my - dragOffY)));
            return true;
        }
        if (active != null && active.drag != null) {
            active.drag.on(toLX(mx), toLY(my));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean had = active != null || dragging != null;
        active = null;
        dragging = null;
        if (had) AmetraConfig.save();
        return true;
    }

    @Override
    protected boolean onScroll(double mx, double my, double hAmount, double vAmount) {
        if (edit) {
            for (int i = Hud.MODULES.size() - 1; i >= 0; i--) {
                HudModule m = Hud.MODULES.get(i);
                if (m.hud && m.cfg.enabled && inRect(mx, my, m.cfg.x, m.cfg.y, m.w * m.cfg.scale, m.h * m.cfg.scale)) {
                    m.cfg.scale = Math.max(0.5f, Math.min(2f, Math.round((m.cfg.scale + (float) vAmount * 0.05f) * 20f) / 20f));
                    AmetraConfig.save();
                    return true;
                }
            }
            return true;
        }
        scrollT[tab] -= (float) vAmount * 30f;
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (textEdit != null) {
            String cur = AmetraConfig.text(textEdit);
            if (chr >= 32 && chr != 127 && cur.length() < 100) AmetraConfig.texts.put(textEdit, cur + chr);
            return true;
        }
        if (renaming) {
            if (chr >= 32 && chr != 127 && chr != '"' && renameBuf.length() < AmetraProfiles.NAME_MAX) renameBuf += chr;
            return true;
        }
        if (searchFocus && !edit && chr >= 32 && chr != 127 && search.length() < 18) {
            search += chr;
            scrollT[0] = 0f;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (edit) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) edit = false;
            return true;
        }
        if (textEdit != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String cur = AmetraConfig.text(textEdit);
                if (!cur.isEmpty()) AmetraConfig.texts.put(textEdit, cur.substring(0, cur.length() - 1));
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                endTextEdit();
            }
            return true;
        }
        if (renaming) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!renameBuf.isEmpty()) renameBuf = renameBuf.substring(0, renameBuf.length() - 1);
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                finishRename(true);
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishRename(false);
            }
            return true;
        }
        if (searchFocus) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocus = false;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && drawerOpen) {
            drawerOpen = false;
            return true;
        }
        if (armed && AmetraClient.MENU_KEY.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
