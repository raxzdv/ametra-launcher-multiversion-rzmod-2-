package com.ametra;

/** Ajustes de un módulo (se guardan en config/ametra.json). */
public final class ModuleCfg {
    public boolean enabled;
    public int x, y;
    public float scale = 1f;
    public int color = 0xFFFFFF;
    public boolean bg = true, shadow = true, chroma = false;
    /** Valor extra (p. ej. nivel de zoom). */
    public float value;
    /** Valores adicionales con nombre (p. ej. posición del viewmodel). */
    public final java.util.Map<String, Float> ex = new java.util.HashMap<>();

    public float ex(String key, float def) {
        Float v = ex.get(key);
        return v == null ? def : v;
    }
}
