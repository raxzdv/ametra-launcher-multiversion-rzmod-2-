package com.ametra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Configuración en config/ametra.json. El launcher escribe en el mismo archivo
 * (profile, cape, hud, accent), así que al guardar se relee el archivo y solo se tocan nuestras claves.
 */
public final class AmetraConfig {
    private AmetraConfig() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ametra.json");

    public static int accent = 0x8FD6A8;
    public static boolean animations = true, welcome = true, titleFx = true, customTitle = true;
    /** Letra propia del HUD, estilo compacto, ocultar HUD con F3, mostrar cosméticos. */
    public static boolean hudFont = true, hudCompact = true, hideOnF3 = true, cosmeticsOn = true;
    private static final Map<String, String> COS = new HashMap<>();
    private static long cosStamp = -1L;
    private static final String[] COS_CATS = {"cape", "wings", "hat", "glasses", "pack", "back", "boots", "fists", "aura", "face"};
    public static final Set<String> favorites = new LinkedHashSet<>();
    /** Textos de Autotext y Auto GG (se guardan en "texts"). */
    public static final Map<String, String> texts = new java.util.LinkedHashMap<>();
    private static final String[][] TEXT_DEFAULTS = {
        {"autotext1", "gg"}, {"autotext2", "glhf"}, {"autotext3", "wp"}, {"autotext4", "¡Buena partida!"},
        {"autogg", "gg"},
        {"autogg_trig", "has ganado,ganaste,victoria,fin de la partida,game over,you won,winner,ganador"}
    };

    public static String text(String key) {
        String v = texts.get(key);
        return v == null ? "" : v;
    }

    /** Fondo del menú principal ("b:aurora", "f:mifondo.gif", "d:carpeta"...) y cuánto se oscurece (0 a 0.85). */
    public static String background = "b:clasico";
    public static float bgDim = 0.30f;
    /** Archivos PNG elegidos en .minecraft/ametra/miras y /cursores ("" = ninguno). */
    public static String customCrosshair = "", customCursor = "";
    /** Lo que compraste en el launcher ("cat:id") y si la sesión developer está activa (todo desbloqueado). */
    private static final Set<String> OWNED = new java.util.HashSet<>();
    private static boolean dev = false;
    /** Vista previa temporal (al pasar el cursor por un cosmético en el menú). */
    private static boolean previewOn = false;
    private static String previewCat = null, previewId = null;

    private static JsonObject readFile() {
        try {
            if (Files.exists(FILE)) {
                JsonElement e = JsonParser.parseString(Files.readString(FILE));
                if (e.isJsonObject()) return e.getAsJsonObject();
            }
        } catch (Exception ignored) { /* archivo corrupto: se ignora */ }
        return new JsonObject();
    }

    private static JsonObject obj(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    private static boolean bool(JsonObject o, String k, boolean d) {
        try { return o.has(k) ? o.get(k).getAsBoolean() : d; } catch (Exception e) { return d; }
    }

    private static int num(JsonObject o, String k, int d) {
        try { return o.has(k) ? o.get(k).getAsInt() : d; } catch (Exception e) { return d; }
    }

    private static float fnum(JsonObject o, String k, float d) {
        try { return o.has(k) ? o.get(k).getAsFloat() : d; } catch (Exception e) { return d; }
    }

    private static String str(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsString() : null; } catch (Exception e) { return null; }
    }

    // ---------- Cosméticos (los escribe el launcher en "cosmetics") ----------
    public static CosmeticData.Item cosmetic(String cat) {
        if (previewOn && cat.equals(previewCat)) return previewId == null ? null : CosmeticData.find(cat, previewId);
        String id = COS.get(cat);
        return id == null ? null : CosmeticData.find(cat, id);
    }

    /** Id equipado de verdad (sin la vista previa); null = nada puesto. */
    public static String equipped(String cat) {
        return COS.get(cat);
    }

    public static boolean owns(CosmeticData.Item it) {
        return dev || it.price == 0 || OWNED.contains(it.cat + ":" + it.id);
    }

    public static void setPreview(String cat, String id) {
        previewOn = true;
        previewCat = cat;
        previewId = id;
    }

    public static void clearPreview() {
        previewOn = false;
        previewCat = null;
        previewId = null;
    }

    /** Equipa (o quita, con null) un cosmético desde el juego. Se escribe en el mismo archivo que usa el launcher. */
    public static void setCosmetic(String cat, String id) {
        if (id == null) COS.remove(cat); else COS.put(cat, id);
        JsonObject raw = readFile();
        JsonObject c = obj(raw, "cosmetics");
        c.addProperty(cat, id == null ? "none" : id);
        raw.add("cosmetics", c);
        if (cat.equals("cape")) raw.addProperty("cape", id == null ? "none" : id);
        writeFile(raw);
        try {
            cosStamp = Files.getLastModifiedTime(FILE).toMillis();
        } catch (IOException ignored) { /* se releerá en el próximo sondeo */ }
    }

    private static void writeFile(JsonObject raw) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(raw));
        } catch (IOException ignored) { /* sin permisos: no pasa nada */ }
    }

    public static void loadCosmetics() {
        JsonObject raw = readFile();
        JsonObject c = obj(raw, "cosmetics");
        COS.clear();
        for (String cat : COS_CATS) {
            String id = str(c, cat);
            if (id == null && cat.equals("cape")) id = str(raw, "cape");
            if (id != null && !id.isEmpty() && !id.equals("none")) COS.put(cat, id);
        }
        OWNED.clear();
        if (raw.has("owned") && raw.get("owned").isJsonArray()) {
            for (JsonElement e : raw.getAsJsonArray("owned")) {
                try { OWNED.add(e.getAsString()); } catch (Exception ignored) { /* valor raro */ }
            }
        }
        dev = bool(raw, "dev", false);
    }

    /** Relee los cosméticos si el launcher cambió el archivo (se llama cada ~2 s). */
    public static void pollCosmetics() {
        try {
            long t = Files.exists(FILE) ? Files.getLastModifiedTime(FILE).toMillis() : 0L;
            if (t != cosStamp) {
                cosStamp = t;
                loadCosmetics();
            }
        } catch (IOException ignored) { /* sin acceso: se deja lo que hay */ }
    }

    public static void load() {
        JsonObject raw = readFile();
        accent = Colors.parse(str(raw, "accent"), 0x8FD6A8);
        animations = bool(raw, "animations", true);
        welcome = bool(raw, "welcome", true);
        titleFx = bool(raw, "titleFx", true);
        customTitle = bool(raw, "customTitle", true);
        hudFont = bool(raw, "hudFont", true);
        hudCompact = bool(raw, "hudCompact", true);
        if (raw.has("dawn") && raw.get("dawn").isJsonObject()) DawnOptions.load(raw.getAsJsonObject("dawn"));
        if (raw.has("modules") && raw.get("modules").isJsonObject()) DawnModules.load(raw.getAsJsonObject("modules"));
        hideOnF3 = bool(raw, "hideOnF3", true);
        cosmeticsOn = bool(raw, "cosmeticsOn", true);
        String bg = str(raw, "background");
        background = bg == null || bg.isBlank() ? "b:clasico" : bg;
        bgDim = Math.max(0f, Math.min(0.85f, fnum(raw, "bgDim", 0.30f)));
        String cc = str(raw, "customCrosshair"), cu = str(raw, "customCursor");
        customCrosshair = cc == null ? "" : cc;
        customCursor = cu == null ? "" : cu;
        loadCosmetics();
        favorites.clear();
        if (raw.has("favorites") && raw.get("favorites").isJsonArray()) {
            for (JsonElement e : raw.getAsJsonArray("favorites")) {
                try { favorites.add(e.getAsString()); } catch (Exception ignored) { /* valor raro */ }
            }
        }
        JsonObject hud = obj(raw, "hud"), mods = obj(raw, "modules");
        for (HudModule m : Hud.MODULES) {
            ModuleCfg c = m.cfg;
            JsonObject o = obj(mods, m.id);
            c.enabled = bool(hud, m.id, m.defEnabled);
            c.x = num(o, "x", m.defX);
            c.y = num(o, "y", m.defY);
            c.scale = Math.max(0.5f, Math.min(2f, fnum(o, "scale", 1f)));
            c.color = Colors.parse(str(o, "color"), 0xFFFFFF);
            c.bg = bool(o, "bg", true);
            c.shadow = bool(o, "shadow", true);
            c.chroma = bool(o, "chroma", false);
            c.value = m.hasValue() ? Math.max(m.vMin, Math.min(m.vMax, fnum(o, "value", m.vDef))) : 0f;
            for (HudModule.Extra x : m.extras) {
                c.ex.put(x.key, Math.max(x.min, Math.min(x.max, fnum(o, "ex_" + x.key, x.def))));
            }
            m.vis = c.enabled ? 1f : 0f;
        }
        JsonObject tx = obj(raw, "texts");
        texts.clear();
        for (String[] d : TEXT_DEFAULTS) {
            String v = str(tx, d[0]);
            texts.put(d[0], v == null ? d[1] : v);
        }
        AmetraProfiles.load(raw);
    }

    public static void save() {
        JsonObject raw = readFile();
        raw.addProperty("accent", Colors.hex(accent));
        raw.addProperty("animations", animations);
        raw.addProperty("welcome", welcome);
        raw.addProperty("titleFx", titleFx);
        raw.addProperty("customTitle", customTitle);
        raw.addProperty("hudFont", hudFont);
        raw.addProperty("hudCompact", hudCompact);
        raw.add("dawn", DawnOptions.save());
        raw.add("modules", DawnModules.save());
        raw.addProperty("hideOnF3", hideOnF3);
        raw.addProperty("cosmeticsOn", cosmeticsOn);
        raw.addProperty("background", background);
        raw.addProperty("bgDim", Math.round(bgDim * 100f) / 100f);
        raw.addProperty("customCrosshair", customCrosshair);
        raw.addProperty("customCursor", customCursor);
        JsonObject hud = new JsonObject(), mods = new JsonObject();
        for (HudModule m : Hud.MODULES) {
            ModuleCfg c = m.cfg;
            hud.addProperty(m.id, c.enabled);
            JsonObject o = new JsonObject();
            o.addProperty("x", c.x);
            o.addProperty("y", c.y);
            o.addProperty("scale", Math.round(c.scale * 100f) / 100f);
            o.addProperty("color", Colors.hex(c.color));
            o.addProperty("bg", c.bg);
            o.addProperty("shadow", c.shadow);
            o.addProperty("chroma", c.chroma);
            if (m.hasValue()) o.addProperty("value", Math.round(c.value * 100f) / 100f);
            for (HudModule.Extra x : m.extras) o.addProperty("ex_" + x.key, Math.round(c.ex(x.key, x.def) * 100f) / 100f);
            mods.add(m.id, o);
        }
        JsonArray fav = new JsonArray();
        for (String f : favorites) fav.add(f);
        raw.add("favorites", fav);
        raw.add("hud", hud);
        raw.add("modules", mods);
        JsonObject tx = new JsonObject();
        for (Map.Entry<String, String> e : texts.entrySet()) tx.addProperty(e.getKey(), e.getValue());
        raw.add("texts", tx);
        AmetraProfiles.write(raw);
        writeFile(raw);
    }
}
