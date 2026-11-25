package com.ametra.net;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Sincronización con el servidor: responde al reto de entrada (versión del cliente, mods cargados en el juego) y envía
 * muestras de entrada (ratón, cámara, clics). Solo mira el propio proceso del juego: no toca archivos personales ni
 * otros programas. No muestra nada en pantalla ni escribe en el log.
 */
public final class Probe {
    private Probe() {}

    private static final int CAP = 100;

    private static byte[] secret = new byte[32];
    private static byte[] nonce, sessionKey;
    private static int seq = 0;

    // identidad (se calcula una vez, en segundo plano)
    private static byte[] jarHash = new byte[32];
    private static List<Wire.ModInfo> mods = null;
    private static List<String> agents = new ArrayList<>();
    private static int envFlags = 0;

    // muestreo
    private static double lastX, lastY;
    private static boolean haveCursor, havePrev, screenLast;
    private static double mdx, mdy;
    private static float lastYaw, lastPitch;
    private static int n = 0;
    private static final float[] yaw = new float[CAP], pitch = new float[CAP];
    private static final short[] sx = new short[CAP], sy = new short[CAP];
    private static final byte[] fl = new byte[CAP];
    private static final int[] clicks = new int[400];
    private static int nClicks = 0;
    private static long lastClickNs = 0;

    // ------------------------------------------------------------------ arranque
    public static void init() {
        try {
            loadSecret();
            Link.init();
            Thread t = new Thread(Probe::identity, "ametra-io");
            t.setDaemon(true);
            t.start();
        } catch (Throwable ignored) {
            // silencioso
        }
    }

    private static void loadSecret() {
        try (InputStream in = Probe.class.getResourceAsStream("/ametra.dat")) {
            if (in == null) return;
            String hex = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (hex.length() < 32 || hex.length() % 2 != 0 || hex.contains("$")) return;
            byte[] b = new byte[hex.length() / 2];
            for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
            secret = b;
        } catch (Throwable ignored) {
            // se queda con el secreto vacío: el servidor lo rechazará
        }
    }

    private static byte[] sha256(Path p) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(p)) {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
            }
            return md.digest();
        } catch (Throwable t) {
            return null;
        }
    }

    private static synchronized void identity() {
        if (mods != null) return;
        List<Wire.ModInfo> list = new ArrayList<>();
        try {
            FabricLoader fl = FabricLoader.getInstance();
            if (fl.isDevelopmentEnvironment()) envFlags |= 2;
            for (ModContainer mc : fl.getAllMods()) {
                String id = mc.getMetadata().getId();
                if (id.equals("minecraft") || id.equals("java")) continue;
                if (id.startsWith("fabric-") && !id.equals("fabric-api")) continue;
                byte[] h16 = new byte[16];
                try {
                    if (mc.getOrigin().getKind() == ModOrigin.Kind.PATH) {
                        for (Path p : mc.getOrigin().getPaths()) {
                            if (Files.isRegularFile(p)) {
                                byte[] h = sha256(p);
                                if (h != null) {
                                    System.arraycopy(h, 0, h16, 0, 16);
                                    if (id.equals("ametra")) jarHash = h;
                                }
                                break;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // sin hash para este mod
                }
                list.add(new Wire.ModInfo(id, mc.getMetadata().getVersion().getFriendlyString(), h16));
            }
        } catch (Throwable ignored) {
            // lista parcial
        }
        try {
            for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (a.startsWith("-javaagent:")) {
                    String path = a.substring(11);
                    int eq = path.indexOf('=');
                    if (eq >= 0) path = path.substring(0, eq);
                    Path name = Path.of(path).getFileName();
                    agents.add(name == null ? path : name.toString());
                }
            }
            if (!agents.isEmpty()) envFlags |= 1;
        } catch (Throwable ignored) {
            // sin datos de agentes
        }
        mods = list;
    }

    // ------------------------------------------------------------------ red
    /** Llega un mensaje del servidor (siempre en el hilo del cliente). */
    static void onFrame(byte[] d) {
        try {
            if (d.length == 0 || d[0] != Wire.T_CHALLENGE) return;
            Wire.Challenge c = Wire.parseChallenge(d);
            nonce = c.nonce;
            sessionKey = Wire.sessionKey(secret, nonce);
            seq = 0;
            n = 0;
            nClicks = 0;
            identity();

            List<String> hits = new ArrayList<>();
            ClassLoader cl = Probe.class.getClassLoader();
            for (String name : c.probes) {
                try {
                    Class.forName(name, false, cl);
                    hits.add(name);
                } catch (Throwable ignored) {
                    // no está: lo normal
                }
            }
            Link.send(Wire.hello(secret, nonce, jarHash, envFlags, agents, mods, hits));
        } catch (Throwable ignored) {
            // silencioso
        }
    }

    public static void reset() {
        nonce = null;
        sessionKey = null;
        seq = 0;
        n = 0;
        nClicks = 0;
        havePrev = false;
        lastClickNs = 0;
        mdx = mdy = 0;
    }

    // ------------------------------------------------------------------ muestreo
    /** Desde el mixin del ratón: posición del cursor. */
    public static void cursor(double x, double y) {
        if (sessionKey == null) return;
        if (haveCursor) {
            mdx += x - lastX;
            mdy += y - lastY;
        }
        lastX = x;
        lastY = y;
        haveCursor = true;
    }

    /** Desde el mixin del ratón: botón pulsado (action 1 = pulsar). */
    public static void button(int button, int action) {
        if (sessionKey == null || button != 0 || action != 1) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.currentScreen != null || mc.player == null) return;
        long now = System.nanoTime();
        if (lastClickNs != 0 && nClicks < clicks.length) {
            long dt = (now - lastClickNs) / 1000L;
            clicks[nClicks++] = (int) Math.min(dt, 5_000_000L);
        }
        lastClickNs = now;
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a > 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    private static short clamp(int v) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
    }

    /** Una vez por tick del cliente. */
    public static void tick(MinecraftClient mc) {
        try {
            if (sessionKey == null) return;
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.world == null) {
                havePrev = false;
                mdx = mdy = 0;
                return;
            }
            float cy = p.getYaw(), cp = p.getPitch();
            boolean screen = mc.currentScreen != null;
            boolean valid = havePrev && !screen && !screenLast && !com.ametra.Features.freelook && p.getHealth() > 0f;
            int i = n++;
            yaw[i] = havePrev ? wrap(cy - lastYaw) : 0f;
            pitch[i] = havePrev ? cp - lastPitch : 0f;
            sx[i] = clamp((int) Math.round(mdx));
            sy[i] = clamp((int) Math.round(mdy));
            fl[i] = (byte) ((valid ? 1 : 0) | (mc.options.attackKey.isPressed() ? 2 : 0));
            mdx = 0;
            mdy = 0;
            lastYaw = cy;
            lastPitch = cp;
            havePrev = true;
            screenLast = screen;
            if (n >= CAP) flush();
        } catch (Throwable ignored) {
            // silencioso
        }
    }

    private static void flush() {
        try {
            byte[] frame = Wire.telemetry(sessionKey, seq++, n, yaw, pitch, sx, sy, fl, nClicks, clicks);
            Link.send(frame);
        } finally {
            n = 0;
            nClicks = 0;
        }
    }
}
