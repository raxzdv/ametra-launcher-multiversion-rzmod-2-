package com.ametra;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Un módulo del cliente: puede ser un elemento del HUD o una función (zoom, fullbright). */
public abstract class HudModule {
    public final String id, name, desc, cat;
    public final int defX, defY;
    public final boolean defEnabled;
    public ModuleCfg cfg = new ModuleCfg();
    /** false = función sin dibujo en pantalla (no se puede mover). */
    public boolean hud = true;
    /** true = función sin dibujo en el HUD pero con color propio (crosshair, hitbox). */
    public boolean colorable = false;
    public String valueLabel;
    public float vMin, vMax, vDef;
    /** Animación de aparición (0 = oculto, 1 = visible). */
    public float vis;
    /** Tamaño medido en el último dibujo (sin escalar). */
    public int w = 60, h = 16;

    /** Ajuste numérico adicional (slider en el panel del módulo). */
    public static final class Extra {
        public final String key, label, fmt;
        public final float min, max, def;
        /** Si no es null, el slider va por pasos enteros y muestra estos nombres. */
        public String[] names;

        Extra(String key, String label, float min, float max, float def, String fmt) {
            this.key = key;
            this.label = label;
            this.min = min;
            this.max = max;
            this.def = def;
            this.fmt = fmt;
        }
    }

    public final List<Extra> extras = new ArrayList<>();
    /** Campos de texto editables en el panel (clave en config -> etiqueta). */
    public final java.util.Map<String, String> textFields = new java.util.LinkedHashMap<>();

    public HudModule text(String key, String label) {
        textFields.put(key, label);
        return this;
    }

    public HudModule extra(String key, String label, float min, float max, float def, String fmt) {
        extras.add(new Extra(key, label, min, max, def, fmt));
        return this;
    }

    protected HudModule(String id, String name, String desc, String cat, int defX, int defY, boolean defEnabled) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.cat = cat;
        this.defX = defX;
        this.defY = defY;
        this.defEnabled = defEnabled;
    }

    /** Pone nombres al último ajuste añadido (slider por pasos). */
    public HudModule names(String... n) {
        if (!extras.isEmpty()) extras.get(extras.size() - 1).names = n;
        return this;
    }

    public HudModule colorable() {
        this.colorable = true;
        return this;
    }

    public HudModule value(String label, float min, float max, float def) {
        this.valueLabel = label;
        this.vMin = min;
        this.vMax = max;
        this.vDef = def;
        return this;
    }

    public boolean hasValue() {
        return valueLabel != null;
    }

    public void resetPos() {
        cfg.x = defX;
        cfg.y = defY;
    }

    /** Dibuja en (0,0) local (posición y escala ya aplicadas) y actualiza w/h. */
    public abstract void draw(DrawContext ctx, MinecraftClient mc, float alpha, int color);
}
