package com.ametra;

import java.util.Locale;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/** Zoom, Fullbright, Sprint automático, combo, reach, freelook y ajustes de cámara. */
public final class Features {
    private Features() {}

    public static KeyBinding ZOOM_KEY, FREELOOK_KEY;
    /** Teclas de Autotext (sin asignar por defecto; se eligen en Controles). */
    public static final KeyBinding[] TEXT_KEYS = new KeyBinding[4];
    private static float zoomK = 0f;
    /** Nivel de zoom que se está mostrando: sigue al valor elegido con suavidad (rueda del ratón). */
    private static float zoomCur = 0f;
    private static boolean zoomDirty = false;
    private static long lastSendMs = 0L, lastGgMs = 0L, ggAtMs = 0L;
    private static boolean nvAdded = false;

    /** Golpes seguidos sin recibir daño (módulo Combo). */
    public static int combo = 0;
    private static long lastHitMs = 0L;

    /** Divisor del FOV (1 = sin zoom). Lo usa el mixin de GameRenderer. */
    public static float zoomAmount() {
        HudModule z = Hud.ZOOM;
        if (z == null || !z.cfg.enabled || zoomK <= 0.001f) return 1f;
        return 1f + (Math.max(1f, zoomCur) - 1f) * Colors.ease(zoomK);
    }

    /** Rueda del ratón con la tecla de zoom mantenida: cambia el nivel (true = se usó, no cambia la ranura). */
    public static boolean scrollZoom(double vAmount) {
        HudModule z = Hud.ZOOM;
        if (z == null || !z.cfg.enabled || ZOOM_KEY == null || !ZOOM_KEY.isPressed() || vAmount == 0) return false;
        float f = vAmount > 0 ? 1.15f : 1f / 1.15f;
        z.cfg.value = Math.max(z.vMin, Math.min(z.vMax, z.cfg.value * f));
        zoomDirty = true;
        return true;
    }

    /** ¿Está activo el módulo Viewmodel? (lo usa el mixin de HeldItemRenderer). */
    public static boolean viewmodelOn() {
        HudModule m = Hud.VIEWMODEL;
        return m != null && m.cfg.enabled;
    }

    public static float vm(String key, float def) {
        HudModule m = Hud.VIEWMODEL;
        return m == null ? def : m.cfg.ex(key, def);
    }

    /** Se llama al pegarle a una entidad (ver AmetraClient). */
    public static void onAttack() {
        long now = System.currentTimeMillis();
        // un golpe cuenta una vez cada ~120 ms aunque el evento se repita
        if (now - lastHitMs < 120L) return;
        lastHitMs = now;
        combo++;
    }


    // ---------- Reach, Freelook y ajustes de cámara ----------
    /** Distancia del último golpe (-1 = todavía ninguno). */
    public static double lastReach = -1;

    /** Freelook activo: la cámara mira donde quiere el ratón y el personaje sigue mirando al frente. */
    public static boolean freelook = false;
    public static float flYaw, flPitch;
    private static Perspective flPrev = null;
    private static Boolean prevSneak = null, prevBob = null;

    private static boolean on(HudModule m) {
        return m != null && m.cfg.enabled;
    }

    public static boolean noHurtCam() {
        return on(Hud.NOHURT);
    }

    /** Cuánto baja el fuego en pantalla (0 si el módulo está apagado). */
    public static float lowFire() {
        HudModule m = Hud.LOWFIRE;
        return on(m) ? m.cfg.ex("y", 0.3f) : 0f;
    }

    /** Se llama al pegarle a una entidad: mide la distancia desde tus ojos hasta su caja. */
    public static void onAttack(Entity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && e != null) {
            net.minecraft.util.math.Vec3d eye = mc.player.getEyePos();
            net.minecraft.util.math.Box b = e.getBoundingBox();
            double dx = Math.max(Math.max(b.minX - eye.x, 0.0), eye.x - b.maxX);
            double dy = Math.max(Math.max(b.minY - eye.y, 0.0), eye.y - b.maxY);
            double dz = Math.max(Math.max(b.minZ - eye.z, 0.0), eye.z - b.maxZ);
            lastReach = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        onAttack();
    }

    private static void freelookTick(MinecraftClient mc) {
        boolean want = on(Hud.FREELOOK) && FREELOOK_KEY != null && FREELOOK_KEY.isPressed()
            && mc.currentScreen == null && mc.player != null;
        if (want && !freelook) {
            freelook = true;
            flYaw = mc.player.getYaw();
            flPitch = mc.player.getPitch();
            flPrev = mc.options.getPerspective();
            if (flPrev == Perspective.FIRST_PERSON) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (!want && freelook) {
            freelook = false;
            if (flPrev != null) mc.options.setPerspective(flPrev);
            flPrev = null;
        }
    }

    /** Agacharse alternado y sin bamboleo usan las opciones del propio juego y las devuelven al apagarlos. */
    private static void optionsTick(MinecraftClient mc) {
        if (mc.options == null) return;
        var sn = mc.options.getSneakToggled();
        if (on(Hud.TOGGLESNEAK)) {
            if (prevSneak == null) prevSneak = sn.getValue();
            if (!sn.getValue()) sn.setValue(true);
        } else if (prevSneak != null) {
            sn.setValue(prevSneak);
            prevSneak = null;
        }
        var bob = mc.options.getBobView();
        if (on(Hud.NOBOB)) {
            if (prevBob == null) prevBob = bob.getValue();
            if (bob.getValue()) bob.setValue(false);
        } else if (prevBob != null) {
            bob.setValue(prevBob);
            prevBob = null;
        }
    }

    /** Se llama cada fotograma. */
    public static void frame(MinecraftClient mc, float dt) {
        HudModule z = Hud.ZOOM;
        boolean want = z != null && z.cfg.enabled && ZOOM_KEY != null && ZOOM_KEY.isPressed() && mc.currentScreen == null;
        float target = want ? 1f : 0f;
        if (!want && zoomDirty) { zoomDirty = false; AmetraConfig.save(); } // guarda el nivel al soltar la tecla
        if (!want && zoomK == 0f) return; // nada que animar
        float sp = AmetraConfig.animations ? 1f : 5f;
        if (zoomCur < 1f && z != null) zoomCur = z.cfg.value;
        if (z != null) zoomCur += (z.cfg.value - zoomCur) * Math.min(1f, dt * 14f * sp);
        zoomK += (target - zoomK) * Math.min(1f, dt * (AmetraConfig.animations ? 9f : 60f));
        if (!want && zoomK < 0.01f) zoomK = 0f;
    }

    // ---------- Autotext y Auto GG ----------
    private static void send(MinecraftClient mc, String msg) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        msg = msg.trim();
        if (msg.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastSendMs < 1000L) return; // sin spam: el servidor te expulsaría
        lastSendMs = now;
        if (msg.startsWith("/")) mc.player.networkHandler.sendChatCommand(msg.substring(1));
        else mc.player.networkHandler.sendChatMessage(msg);
    }

    /** Mensajes del servidor (no el chat de otros jugadores): si anuncian el final de la partida, programa el GG. */
    public static void onGameMessage(String text) {
        HudModule g = Hud.AUTOGG;
        if (g == null || !g.cfg.enabled || text == null || text.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastGgMs < 30000L || ggAtMs != 0L) return; // una sola vez por partida
        String low = text.toLowerCase(Locale.ROOT);
        for (String t : AmetraConfig.text("autogg_trig").toLowerCase(Locale.ROOT).split(",")) {
            t = t.trim();
            if (!t.isEmpty() && low.contains(t)) {
                ggAtMs = now + (long) (g.cfg.ex("delay", 1f) * 1000f);
                return;
            }
        }
    }

    /** Se llama cada tick. */
    public static void tick(MinecraftClient mc) {
        optionsTick(mc);
        freelookTick(mc);
        if (mc.player == null) { nvAdded = false; combo = 0; ggAtMs = 0L; return; }

        // Autotext: una tecla por mensaje
        HudModule at = Hud.AUTOTEXT;
        for (int i = 0; i < TEXT_KEYS.length; i++) {
            if (TEXT_KEYS[i] == null) continue;
            while (TEXT_KEYS[i].wasPressed()) {
                if (at != null && at.cfg.enabled && mc.currentScreen == null) send(mc, AmetraConfig.text("autotext" + (i + 1)));
            }
        }
        // Auto GG: manda el mensaje cuando pasa la espera
        if (ggAtMs != 0L && System.currentTimeMillis() >= ggAtMs) {
            ggAtMs = 0L;
            lastGgMs = System.currentTimeMillis();
            HudModule g = Hud.AUTOGG;
            if (g != null && g.cfg.enabled) send(mc, AmetraConfig.text("autogg"));
        }

        // Combo: se reinicia si te hacen daño o pasa mucho tiempo sin golpear
        if (combo > 0 && (mc.player.hurtTime > 0 || System.currentTimeMillis() - lastHitMs > 4000L)) combo = 0;

        // Fullbright: visión nocturna del lado cliente
        HudModule f = Hud.FULLBRIGHT;
        boolean want = f != null && f.cfg.enabled;
        if (want) {
            if (!mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 100000, 0, false, false, false));
                nvAdded = true;
            }
        } else if (nvAdded) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            nvAdded = false;
        }

        // Sprint automático: igual que el "alternar correr" de accesibilidad, pero sin pulsar nada
        HudModule sp = Hud.AUTOSPRINT;
        if (sp != null && sp.cfg.enabled && mc.currentScreen == null
            && mc.options.forwardKey.isPressed() && !mc.player.isSneaking() && !mc.player.isUsingItem()
            && !mc.player.horizontalCollision && mc.player.getHungerManager().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }
    }
}
