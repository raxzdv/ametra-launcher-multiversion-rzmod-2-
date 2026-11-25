package com.ametra;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Perfiles de AmetraClient: cada perfil guarda qué módulos están activos, su posición, escala, color y el
 * color de acento. Se pueden copiar como un "código" de texto (AMETRA1:...) y pegar en otro PC.
 *
 * Se guardan en config/ametra.json bajo "mprofiles" / "mactive" (la clave "profile" es del launcher).
 */
public final class AmetraProfiles {
    private AmetraProfiles() {}

    public static final int MAX = 6, NAME_MAX = 14;
    static final String PREFIX = "AMETRA1:";
    private static final int MAX_JSON = 64 * 1024;

    private static final Map<String, JsonObject> STORE = new LinkedHashMap<>();
    public static String active = "Principal";

    public static List<String> names() {
        return new ArrayList<>(STORE.keySet());
    }

    public static boolean canAdd() {
        return STORE.size() < MAX;
    }

    // ---------- Instantánea de la configuración actual ----------
    static JsonObject snapshot() {
        JsonObject s = new JsonObject();
        s.addProperty("a", Colors.hex(AmetraConfig.accent));
        JsonObject mods = new JsonObject();
        for (HudModule m : Hud.MODULES) {
            ModuleCfg c = m.cfg;
            JsonObject o = new JsonObject();
            o.addProperty("e", c.enabled);
            o.addProperty("x", c.x);
            o.addProperty("y", c.y);
            o.addProperty("s", Math.round(c.scale * 100f) / 100f);
            o.addProperty("c", Colors.hex(c.color));
            o.addProperty("b", c.bg);
            o.addProperty("h", c.shadow);
            o.addProperty("r", c.chroma);
            if (m.hasValue()) o.addProperty("v", Math.round(c.value * 100f) / 100f);
            for (HudModule.Extra x : m.extras) o.addProperty("x_" + x.key, Math.round(c.ex(x.key, x.def) * 100f) / 100f);
            mods.add(m.id, o);
        }
        s.add("m", mods);
        return s;
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

    /** Aplica una instantánea a la configuración en vivo (los módulos que falten se dejan como están). */
    static void apply(JsonObject s) {
        AmetraConfig.accent = Colors.parse(str(s, "a"), AmetraConfig.accent);
        JsonObject mods = s.has("m") && s.get("m").isJsonObject() ? s.getAsJsonObject("m") : new JsonObject();
        for (HudModule m : Hud.MODULES) {
            if (!mods.has(m.id) || !mods.get(m.id).isJsonObject()) continue;
            JsonObject o = mods.getAsJsonObject(m.id);
            ModuleCfg c = m.cfg;
            c.enabled = bool(o, "e", c.enabled);
            c.x = Math.max(0, Math.min(8000, num(o, "x", c.x)));
            c.y = Math.max(0, Math.min(8000, num(o, "y", c.y)));
            c.scale = Math.max(0.5f, Math.min(2f, fnum(o, "s", c.scale)));
            c.color = Colors.parse(str(o, "c"), c.color);
            c.bg = bool(o, "b", c.bg);
            c.shadow = bool(o, "h", c.shadow);
            c.chroma = bool(o, "r", c.chroma);
            if (m.hasValue()) c.value = Math.max(m.vMin, Math.min(m.vMax, fnum(o, "v", c.value)));
            for (HudModule.Extra x : m.extras) {
                c.ex.put(x.key, Math.max(x.min, Math.min(x.max, fnum(o, "x_" + x.key, c.ex(x.key, x.def)))));
            }
        }
    }

    // ---------- Carga / guardado (los llama AmetraConfig) ----------
    static void load(JsonObject raw) {
        STORE.clear();
        try {
            if (raw.has("mprofiles") && raw.get("mprofiles").isJsonObject()) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : raw.getAsJsonObject("mprofiles").entrySet()) {
                    if (e.getValue().isJsonObject() && STORE.size() < MAX) STORE.put(clean(e.getKey()), e.getValue().getAsJsonObject());
                }
            }
        } catch (Exception ignored) { /* archivo raro: se resiembra abajo */ }

        String act = str(raw, "mactive");
        if (STORE.isEmpty()) {
            // Primera vez: "Principal" = lo que hay ahora, más dos perfiles de ejemplo.
            STORE.put("Principal", snapshot());
            STORE.put("PvP", preset(Set.of("logo", "fps", "cps", "keys", "ping", "armor", "zoom")));
            STORE.put("Survival", preset(Set.of("logo", "fps", "coords", "dir", "clock", "zoom", "fullbright")));
            act = "Principal";
        }
        active = act != null && STORE.containsKey(act) ? act : STORE.keySet().iterator().next();
    }

    private static JsonObject preset(Set<String> on) {
        JsonObject s = snapshot();
        JsonObject mods = s.getAsJsonObject("m");
        for (HudModule m : Hud.MODULES) mods.getAsJsonObject(m.id).addProperty("e", on.contains(m.id));
        return s;
    }

    static void write(JsonObject raw) {
        if (STORE.containsKey(active)) STORE.put(active, snapshot());
        JsonObject o = new JsonObject();
        for (Map.Entry<String, JsonObject> e : STORE.entrySet()) o.add(e.getKey(), e.getValue());
        raw.add("mprofiles", o);
        raw.addProperty("mactive", active);
    }

    // ---------- Acciones ----------
    private static String clean(String n) {
        StringBuilder b = new StringBuilder();
        for (char ch : (n == null ? "" : n).toCharArray()) if (ch >= 32 && ch != 127 && ch != '"') b.append(ch);
        String s = b.toString().trim();
        if (s.length() > NAME_MAX) s = s.substring(0, NAME_MAX).trim();
        return s.isEmpty() ? "Perfil" : s;
    }

    private static String unique(String base) {
        String b = clean(base);
        if (!STORE.containsKey(b)) return b;
        for (int i = 2; i < 100; i++) {
            String suffix = " " + i;
            String n = (b.length() + suffix.length() > NAME_MAX ? b.substring(0, NAME_MAX - suffix.length()) : b) + suffix;
            if (!STORE.containsKey(n)) return n;
        }
        return b;
    }

    public static void switchTo(String name) {
        if (!STORE.containsKey(name) || name.equals(active)) return;
        STORE.put(active, snapshot());
        apply(STORE.get(name));
        active = name;
        refreshVisibility();
        AmetraConfig.save();
    }

    /** Perfil nuevo = copia de lo que tienes ahora. Devuelve el nombre o null si ya hay el máximo. */
    public static String create() {
        if (!canAdd()) return null;
        STORE.put(active, snapshot());
        String n = unique("Perfil");
        STORE.put(n, snapshot());
        active = n;
        AmetraConfig.save();
        return n;
    }

    public static boolean rename(String old, String to) {
        if (!STORE.containsKey(old)) return false;
        String n = clean(to);
        if (n.equals(old)) return true;
        if (STORE.containsKey(n)) return false;
        Map<String, JsonObject> copy = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> e : STORE.entrySet()) copy.put(e.getKey().equals(old) ? n : e.getKey(), e.getValue());
        STORE.clear();
        STORE.putAll(copy);
        if (active.equals(old)) active = n;
        AmetraConfig.save();
        return true;
    }

    public static boolean delete(String name) {
        if (STORE.size() <= 1 || !STORE.containsKey(name)) return false;
        boolean wasActive = name.equals(active);
        STORE.remove(name);
        if (wasActive) {
            active = STORE.keySet().iterator().next();
            apply(STORE.get(active));
            refreshVisibility();
        }
        AmetraConfig.save();
        return true;
    }

    private static void refreshVisibility() {
        for (HudModule m : Hud.MODULES) m.vis = m.cfg.enabled ? 1f : 0f;
    }

    // ---------- Códigos para compartir ----------
    public static String exportCode() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
                gz.write(snapshot().toString().getBytes(StandardCharsets.UTF_8));
            }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            return PREFIX;
        }
    }

    /** Importa un código como perfil nuevo y lo activa. Devuelve null si salió bien, o el motivo del error. */
    public static String importCode(String code) {
        if (code == null) return "El portapapeles está vacío";
        String c = code.replaceAll("\\s+", "");
        if (!c.startsWith(PREFIX) || c.length() <= PREFIX.length()) return "Eso no es un código de RzClient";
        if (!canAdd()) return "Máximo " + MAX + " perfiles: borra uno primero";
        JsonObject snap;
        try {
            byte[] gz = Base64.getUrlDecoder().decode(c.substring(PREFIX.length()));
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
                byte[] raw = in.readNBytes(MAX_JSON + 1);
                if (raw.length > MAX_JSON) return "El código es demasiado grande";
                snap = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            if (!snap.has("m") || !snap.get("m").isJsonObject()) return "Código dañado o incompleto";
        } catch (Exception e) {
            return "Código dañado o incompleto";
        }
        STORE.put(active, snapshot());
        String n = unique("Importado");
        STORE.put(n, snap);
        apply(snap);
        active = n;
        refreshVisibility();
        AmetraConfig.save();
        return null;
    }
}
