package com.ametra;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Opciones tipo Dawn (pestañas General, Performance, Graphics y Cosmetic del menú Shift).
 *
 * Este archivo solo guarda y lee los valores. Los efectos reales (por ejemplo, ocultar el panorama
 * o desactivar los avances) se conectan desde los mixins o desde la UI, consultando DawnOptions.get("clave").
 *
 * Uso en AmetraConfig:
 *   load:  DawnOptions.load(json.getAsJsonObject("dawn"));  (si existe)
 *   save:  json.add("dawn", DawnOptions.save());
 */
public final class DawnOptions {
    private DawnOptions() {}

    public enum Cat {
        GENERAL("General"),
        PERFORMANCE("Performance"),
        GRAPHICS("Graphics"),
        COSMETIC("Cosmetic");

        public final String title;

        Cat(String title) {
            this.title = title;
        }
    }

    /** Una opción de tipo interruptor. */
    public static final class Opt {
        public final Cat cat;
        public final String key;
        public final String label;
        public final boolean def;

        Opt(Cat cat, String key, String label, boolean def) {
            this.cat = cat;
            this.key = key;
            this.label = label;
            this.def = def;
        }
    }

    private static final List<Opt> ALL = List.of(
        // General
        new Opt(Cat.GENERAL, "borderlessFullscreen", "Borderless Fullscreen", false),
        new Opt(Cat.GENERAL, "favoriteWorlds", "Favorite Worlds", true),
        new Opt(Cat.GENERAL, "deleteWorldToTrash", "Delete World To Trash", false),
        new Opt(Cat.GENERAL, "disableHotbarLooping", "Disable Hotbar Looping", false),
        new Opt(Cat.GENERAL, "translateFormattingCodes", "Translate Formatting Codes", true),
        new Opt(Cat.GENERAL, "sendWarningToast", "Send Warning Toast in Chat", true),
        new Opt(Cat.GENERAL, "serverSearchBar", "Server Search Bar", true),
        new Opt(Cat.GENERAL, "serverDrag", "Server Drag", true),
        new Opt(Cat.GENERAL, "serverListFolders", "Server List Folders", true),
        new Opt(Cat.GENERAL, "serverFolderClickJoin", "Server Folder Click Join", true),
        new Opt(Cat.GENERAL, "directJoinPreview", "Direct Join Preview", true),
        new Opt(Cat.GENERAL, "serverBanners", "Server Banners", true),
        new Opt(Cat.GENERAL, "hideServerEvents", "Hide Server Events", false),
        new Opt(Cat.GENERAL, "serverGeolocation", "Server Geolocation", true),
        new Opt(Cat.GENERAL, "serverLocationTooltip", "Server Location Tooltip", true),
        new Opt(Cat.GENERAL, "serverLocationTooltipPlace", "Server Location Tooltip Place", true),

        // Performance
        new Opt(Cat.PERFORMANCE, "bufferImprovements", "Buffer Improvements", true),
        new Opt(Cat.PERFORMANCE, "lightTextureImprovements", "Light Texture Improvements", true),
        new Opt(Cat.PERFORMANCE, "limitHudFps", "Limit Dawn HUD FPS", true),
        new Opt(Cat.PERFORMANCE, "nametagOptimization", "Nametag Optimization", true),

        // Graphics
        new Opt(Cat.GRAPHICS, "disableAdvancements", "Disable Advancements", false),
        new Opt(Cat.GRAPHICS, "disablePanoramaIngame", "Disable Panorama Ingame", true),
        new Opt(Cat.GRAPHICS, "disableDirtBackground", "Disable Dirt Background", true),
        new Opt(Cat.GRAPHICS, "disableItemStitchFix", "Disable Item Stitch Fix", false),
        new Opt(Cat.GRAPHICS, "minimalViewBobbing", "Minimal View Bobbing", true),
        new Opt(Cat.GRAPHICS, "clearGlass", "Clear Glass", false),
        new Opt(Cat.GRAPHICS, "blurMenus", "Blur Menus", true),
        new Opt(Cat.GRAPHICS, "blurMods", "Blur Mods", false),
        new Opt(Cat.GRAPHICS, "blurDawnMenusOnly", "Blur Dawn Menus Only", false),

        // Cosmetic
        new Opt(Cat.COSMETIC, "disableBackendNotification", "Disable Backend Notification", false),
        new Opt(Cat.COSMETIC, "realisticCloaks", "Realistic Cloaks", true),
        new Opt(Cat.COSMETIC, "showWings", "Show Wings", true),
        new Opt(Cat.COSMETIC, "showBack", "Show Back", true),
        new Opt(Cat.COSMETIC, "showHats", "Show Hats", true),
        new Opt(Cat.COSMETIC, "showArm", "Show Arm", true),
        new Opt(Cat.COSMETIC, "showBoots", "Show Boots", true),
        new Opt(Cat.COSMETIC, "showShields", "Show Shields", true),
        new Opt(Cat.COSMETIC, "onlyShowOwnCosmetics", "Only Show Own Cosmetics", false),
        new Opt(Cat.COSMETIC, "cloakTextureOnElytra", "Cloak Texture on Elytra", true),
        new Opt(Cat.COSMETIC, "showFace", "Show Face", true),
        new Opt(Cat.COSMETIC, "showPets", "Show Pets", true),
        new Opt(Cat.COSMETIC, "showAuras", "Show Auras", true),
        new Opt(Cat.COSMETIC, "showCosmeticsInFirstPerson", "Show Cosmetics in First Person", true)
    );

    private static final Map<String, Opt> BY_KEY = new LinkedHashMap<>();
    private static final Map<String, Boolean> VALUES = new LinkedHashMap<>();

    static {
        for (Opt o : ALL) {
            BY_KEY.put(o.key, o);
            VALUES.put(o.key, o.def);
        }
    }


    /** Una opción numérica con deslizador. */
    public static final class Num {
        public final Cat cat;
        public final String key;
        public final String label;
        public final double min, max, def;

        Num(Cat cat, String key, String label, double min, double max, double def) {
            this.cat = cat;
            this.key = key;
            this.label = label;
            this.min = min;
            this.max = max;
            this.def = def;
        }
    }

    private static final List<Num> NUMS = List.of(
        new Num(Cat.GRAPHICS, "fireHeight", "Fire Height", 0.0, 1.0, 0.3),
        new Num(Cat.GRAPHICS, "fadeInDuration", "Fade-In Duration", 0.0, 2.0, 0.2),
        new Num(Cat.GRAPHICS, "fadeOutDuration", "Fade-Out Duration", 0.0, 2.0, 0.0),
        new Num(Cat.GRAPHICS, "blurStrength", "Blur Strength", 0.0, 10.0, 3.0),
        new Num(Cat.COSMETIC, "gravity", "Gravity", 0.0, 5.0, 1.0)
    );

    private static final Map<String, Double> NV = new LinkedHashMap<>();

    static {
        for (Num n : NUMS) NV.put(n.key, n.def);
    }

    public static List<Num> numsByCat(Cat cat) {
        List<Num> out = new ArrayList<>();
        for (Num n : NUMS) if (n.cat == cat) out.add(n);
        return Collections.unmodifiableList(out);
    }

    public static double getNum(String key) {
        Double v = NV.get(key);
        return v == null ? 0 : v;
    }

    public static void setNum(String key, double value) {
        for (Num n : NUMS) {
            if (n.key.equals(key)) {
                NV.put(key, Math.max(n.min, Math.min(n.max, value)));
                return;
            }
        }
    }

    /** Todas las opciones de una pestaña, en el orden en que se muestran. */
    public static List<Opt> byCat(Cat cat) {
        List<Opt> out = new ArrayList<>();
        for (Opt o : ALL) if (o.cat == cat) out.add(o);
        return Collections.unmodifiableList(out);
    }

    /** Valor actual. Si la clave no existe, devuelve false. */
    public static boolean get(String key) {
        Boolean v = VALUES.get(key);
        return v != null && v;
    }

    public static void set(String key, boolean value) {
        if (BY_KEY.containsKey(key)) VALUES.put(key, value);
    }

    public static void toggle(String key) {
        set(key, !get(key));
    }

    /** Vuelve todas las opciones de una pestaña a su valor por defecto. */
    public static void resetCat(Cat cat) {
        for (Opt o : byCat(cat)) VALUES.put(o.key, o.def);
        for (Num n : numsByCat(cat)) NV.put(n.key, n.def);
    }

    /** Vuelve todo a los valores por defecto. */
    public static void resetAll() {
        for (Opt o : ALL) VALUES.put(o.key, o.def);
        for (Num n : NUMS) NV.put(n.key, n.def);
    }

    /** Carga valores desde el JSON guardado. Las claves que no existan se dejan con su valor por defecto. */
    public static void load(JsonObject json) {
        if (json == null) return;
        if (json.has("nums") && json.get("nums").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("nums").entrySet()) {
                JsonElement v = e.getValue();
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) setNum(e.getKey(), v.getAsDouble());
            }
        }
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            JsonElement v = e.getValue();
            if (BY_KEY.containsKey(e.getKey()) && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
                VALUES.put(e.getKey(), v.getAsBoolean());
            }
        }
    }

    /** Devuelve los valores para guardar en ametra.json. */
    public static JsonObject save() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, Boolean> e : VALUES.entrySet()) o.addProperty(e.getKey(), e.getValue());
        JsonObject nums = new JsonObject();
        for (Map.Entry<String, Double> e : NV.entrySet()) nums.addProperty(e.getKey(), e.getValue());
        o.add("nums", nums);
        return o;
    }
}
