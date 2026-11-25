package com.ametra;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** Mira y cursor propios: PNG sueltos en .minecraft/ametra/miras y .minecraft/ametra/cursores. */
public final class Custom {
    private Custom() {}

    private static final Identifier CROSS_ID = Compat.id("ametra", "dyn/mira_propia");
    private static String crossLoaded = null;
    private static boolean crossOk = false;
    private static int cw = 1, ch = 1;
    private static long cursor = 0L;
    private static boolean inited = false;

    public static Path dir(String sub) {
        return FabricLoader.getInstance().getGameDir().resolve("ametra").resolve(sub);
    }

    public static List<String> list(String sub) {
        List<String> out = new ArrayList<>();
        try {
            Files.createDirectories(dir(sub));
            try (var st = Files.list(dir(sub))) {
                st.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .forEach(p -> out.add(p.getFileName().toString()));
            }
        } catch (Exception ignored) { /* carpeta no accesible */ }
        java.util.Collections.sort(out);
        return out;
    }

    public static void openFolder(String sub) {
        try {
            Files.createDirectories(dir(sub));
            net.minecraft.util.Util.getOperatingSystem().open(dir(sub).toFile());
        } catch (Exception ignored) { /* el usuario la encuentra en .minecraft/ametra */ }
    }

    // ---------- mira ----------
    private static void loadCross(String name) {
        crossLoaded = name;
        crossOk = false;
        if (name == null || name.isEmpty()) return;
        try (InputStream in = Files.newInputStream(dir("miras").resolve(name))) {
            NativeImage img = NativeImage.read(in);
            cw = img.getWidth();
            ch = img.getHeight();
            MinecraftClient.getInstance().getTextureManager().registerTexture(CROSS_ID, new NativeImageBackedTexture(img));
            crossOk = true;
        } catch (Exception ignored) { /* imagen rara: se usa la cruz normal */ }
    }

    /** ¿Hay una imagen de mira cargada y lista? */
    public static boolean hasCrosshair() {
        String n = AmetraConfig.customCrosshair;
        if (n == null || n.isEmpty()) return false;
        if (!n.equals(crossLoaded)) loadCross(n);
        return crossOk;
    }

    public static void drawCrosshair(DrawContext ctx, int cx, int cy, int size) {
        int w = size, h = Math.max(1, Math.round(size * (ch / (float) cw)));
        Compat.drawTex(ctx, CROSS_ID, cx - w / 2, cy - h / 2, w, h, 0f, 0f, cw, ch, cw, ch);
    }

    public static void reloadCross() {
        crossLoaded = null;
    }

    // ---------- cursor ----------
    public static void init(MinecraftClient mc) {
        if (inited || mc.getWindow() == null) return;
        inited = true;
        applyCursor();
    }

    public static void applyCursor() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;
        long win = mc.getWindow().getHandle();
        String name = AmetraConfig.customCursor;
        long made = 0L;
        if (name != null && !name.isEmpty()) {
            try {
                BufferedImage src = ImageIO.read(dir("cursores").resolve(name).toFile());
                if (src != null) {
                    float k = Math.min(1f, 32f / Math.max(src.getWidth(), src.getHeight()));
                    int w = Math.max(1, Math.round(src.getWidth() * k)), h = Math.max(1, Math.round(src.getHeight() * k));
                    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = img.createGraphics();
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(src, 0, 0, w, h, null);
                    g.dispose();
                    ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int p = img.getRGB(x, y);
                            buf.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
                        }
                    }
                    buf.flip();
                    GLFWImage gi = GLFWImage.malloc();
                    gi.set(w, h, buf);
                    made = GLFW.glfwCreateCursor(gi, 0, 0);
                    gi.free();
                    MemoryUtil.memFree(buf);
                }
            } catch (Exception ignored) { /* imagen rara: cursor normal */ }
        }
        GLFW.glfwSetCursor(win, made); // 0 = cursor normal
        if (cursor != 0L) GLFW.glfwDestroyCursor(cursor);
        cursor = made;
    }
}
