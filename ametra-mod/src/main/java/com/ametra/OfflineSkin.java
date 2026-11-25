package com.ametra;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Skin para cuentas NO premium. El launcher copia la skin elegida a  (instancia)/ametra/skin.png
 * (y "slim" en ametra/skin.json); aquí se registra como textura y SkinMixin la usa para TU jugador.
 * Si no hay archivo, o la cuenta es premium (el launcher lo borra), no hace nada.
 */
public final class OfflineSkin {
    private OfflineSkin() {}

    private static final Identifier ID = Compat.id("ametra", "offline_skin");
    private static boolean loaded, ok, slim;
    private static long stamp = -1;

    private static Path dir() { return FabricLoader.getInstance().getGameDir().resolve("ametra"); }

    /** true si hay skin local cargada (se recarga sola si el archivo cambió). */
    public static boolean ready() {
        try {
            Path f = dir().resolve("skin.png");
            if (!Files.isRegularFile(f)) { ok = false; loaded = true; stamp = -1; return false; }
            long t = Files.getLastModifiedTime(f).toMillis();
            if (loaded && t == stamp) return ok;
            loaded = true; stamp = t; ok = false;
            NativeImage src;
            try (InputStream in = Files.newInputStream(f)) { src = NativeImage.read(in); }
            NativeImage img = src;
            if (src.getWidth() == 64 && src.getHeight() == 32) { img = upgrade(src); src.close(); }
            else if (src.getWidth() != 64 || src.getHeight() != 64) { src.close(); return false; }
            // Las skins antiguas no traen alfa en el cuerpo: se fuerza opaco como hace el juego (cabeza/cuerpo/piernas).
            opaque(img, 0, 0, 32, 16); opaque(img, 0, 16, 64, 16); opaque(img, 16, 48, 32, 16);
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.getTextureManager().registerTexture(ID, new NativeImageBackedTexture(img));
            Path j = dir().resolve("skin.json");
            slim = Files.isRegularFile(j) && Files.readString(j).contains("\"slim\":true");
            ok = true;
        } catch (Throwable e) { ok = false; }
        return ok;
    }

    /** Skin 64x32 (formato antiguo) -> 64x64: copia espejo de brazo y pierna izquierdos. */
    private static NativeImage upgrade(NativeImage s) {
        NativeImage d = new NativeImage(64, 64, true);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) Compat.setPixel(d, x, y, Compat.getPixel(s, x, y));
        mirror(s, d, 0, 16, 16, 48);   // pierna
        mirror(s, d, 40, 16, 32, 48);  // brazo
        return d;
    }
    // Copia el bloque 16x16 de (sx,sy) a (dx,dy) cambiando izquierda<->derecha de cada cara.
    private static void mirror(NativeImage s, NativeImage d, int sx, int sy, int dx, int dy) {
        // caras de un cubo 4x12x4 dentro de 16x16: top/bottom (4 de alto), y fila de 12: der, frente, izq, atrás
        int[][] faces = { {4, 0, 4, 4}, {8, 0, 4, 4}, {0, 4, 4, 12}, {4, 4, 4, 12}, {8, 4, 4, 12}, {12, 4, 4, 12} };
        // destino intercambia right<->left (0<->8) y se voltea cada cara en horizontal
        int[] swap = { 0, 1, 4, 3, 2, 5 };
        for (int i = 0; i < faces.length; i++) {
            int[] f = faces[i], g = faces[swap[i]];
            for (int yy = 0; yy < f[3]; yy++) for (int xx = 0; xx < f[2]; xx++)
                Compat.setPixel(d, dx + g[0] + (f[2] - 1 - xx), dy + g[1] + yy, Compat.getPixel(s, sx + f[0] + xx, sy + f[1] + yy));
        }
    }
    private static void opaque(NativeImage img, int x0, int y0, int w, int h) {
        for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) Compat.setPixel(img, x, y, Compat.getPixel(img, x, y) | 0xFF000000);
    }

    /** Textura de la skin local (llamar a ready() antes). */
    public static Identifier textureId() { return ID; }

    /** true si la skin local es de brazos finos. */
    public static boolean isSlim() { return slim; }

    public static boolean isMe(UUID id, String name) {
        var s = MinecraftClient.getInstance().getSession();
        if (s == null) return false;
        if (id != null && id.equals(s.getUuidOrNull())) return true;
        return name != null && name.equals(s.getUsername());
    }
}
