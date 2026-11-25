package com.ametra;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Módulos del Mod Menu estilo Dawn: estado activado/desactivado, favoritos y etiquetas para filtrar.
 * Se guarda en ametra.json bajo la clave "modules".
 */
public final class DawnModules {
    private DawnModules() {}

    /** Filtros de la barra superior. ALL muestra todo; el resto muestra solo los módulos con esa etiqueta. */
    public static final String[] FILTERS = { "ALL", "NEW", "HUD", "HYPIXEL", "PVP" };

    public static final class Mod {
        public final String key;
        public final String name;
        public final boolean def;
        public final Set<String> tags;

        Mod(String key, String name, boolean def, String... tags) {
            this.key = key;
            this.name = name;
            this.def = def;
            this.tags = new HashSet<>(List.of(tags));
        }
    }

    // Orden alfabético, igual que en las capturas. Los que están activados por defecto son los de la captura.
    private static final List<Mod> ALL = List.of(
        new Mod("animations", "Animations", true),
        new Mod("armorBar", "Armor Bar", false, "HUD"),
        new Mod("armorStatus", "Armor Status", false, "HUD", "PVP"),
        new Mod("attackIndicator", "Attack Indicator", false, "HUD", "PVP"),
        new Mod("autohideHud", "Autohide HUD", false, "HUD"),
        new Mod("autoPerspective", "Auto Perspective", false, "NEW"),
        new Mod("autoText", "Auto Text", true, "HYPIXEL"),
        new Mod("backups", "Backups", false),
        new Mod("blockIndicator", "Block Indicator", false, "HUD", "NEW"),
        new Mod("blockOverlay", "Block Overlay", true),
        new Mod("bossBar", "Boss Bar", true, "HUD"),
        new Mod("brightness", "Brightness", true),
        new Mod("camera", "Camera", true),
        new Mod("colorSaturation", "Color Saturation", true),
        new Mod("comboDisplay", "Combo Display", false, "HUD", "PVP"),
        new Mod("coordinates", "Coordinates", false, "HUD", "HYPIXEL"),
        new Mod("cps", "CPS", true, "HUD", "PVP"),
        new Mod("customCrosshair", "Custom Crosshair", true, "PVP"),
        new Mod("cullLogs", "Cull Logs", false, "HYPIXEL"),
        new Mod("customMovement", "Custom Advanced Movement", false, "NEW"),
        new Mod("customChat", "Custom Chat", false, "HYPIXEL"),
        new Mod("customF3", "Custom F3", false, "HUD"),
        new Mod("customFog", "Custom Fog", false, "NEW"),
        new Mod("damageIndicator", "Damage Indicator", false, "HUD", "PVP")
    );

    private static final Map<String, Boolean> ON = new LinkedHashMap<>();
    private static final Set<String> FAV = new HashSet<>();

    static {
        for (Mod m : ALL) ON.put(m.key, m.def);
    }

    public static List<Mod> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Módulos que pasan el filtro y, opcionalmente, solo favoritos. */
    public static List<Mod> filtered(String filter, boolean favOnly) {
        List<Mod> out = new ArrayList<>();
        for (Mod m : ALL) {
            if (favOnly && !FAV.contains(m.key)) continue;
            if (!"ALL".equals(filter) && !m.tags.contains(filter)) continue;
            out.add(m);
        }
        return out;
    }

    public static boolean isOn(String key) {
        Boolean v = ON.get(key);
        return v != null && v;
    }

    public static void toggle(String key) {
        if (ON.containsKey(key)) ON.put(key, !isOn(key));
    }

    public static boolean isFav(String key) {
        return FAV.contains(key);
    }

    public static void toggleFav(String key) {
        if (!FAV.remove(key)) FAV.add(key);
    }

    public static void load(JsonObject json) {
        if (json == null) return;
        if (json.has("enabled") && json.get("enabled").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("enabled").entrySet()) {
                JsonElement v = e.getValue();
                if (ON.containsKey(e.getKey()) && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
                    ON.put(e.getKey(), v.getAsBoolean());
                }
            }
        }
        if (json.has("fav") && json.get("fav").isJsonArray()) {
            FAV.clear();
            for (JsonElement e : json.getAsJsonArray("fav")) {
                if (e.isJsonPrimitive() && ON.containsKey(e.getAsString())) FAV.add(e.getAsString());
            }
        }
    }

    public static JsonObject save() {
        JsonObject o = new JsonObject();
        JsonObject en = new JsonObject();
        for (Map.Entry<String, Boolean> e : ON.entrySet()) en.addProperty(e.getKey(), e.getValue());
        o.add("enabled", en);
        JsonArray fav = new JsonArray();
        for (String k : FAV) fav.add(k);
        o.add("fav", fav);
        return o;
    }
}
