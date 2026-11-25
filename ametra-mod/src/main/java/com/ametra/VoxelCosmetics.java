package com.ametra;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Cosméticos 3D hechos con el Estudio del launcher (bloques de 1 píxel). Se leen de .minecraft/(instancia)/ametra/cosmeticos/*.json
 * y se dibujan sobre tu personaje. Coordenadas del editor: origen en los pies, Y arriba, X positivo = izquierda del jugador,
 * Z positivo = frente. Alas, botas y puños se construyen solo en el lado izquierdo (X >= 0) y se reflejan.
 */
public final class VoxelCosmetics {
    private VoxelCosmetics() {}

    private static final Identifier WHITE = Compat.id("ametra", "textures/cosmetics/white.png");
    private static final Set<String> SLOTS = Set.of("cape", "wings", "hat", "glasses", "pack", "back", "boots", "fists", "aura", "face");
    private static final Set<String> SIDE = Set.of("wings", "boots", "fists");

    private static final float[][][] FACES = {
        {{0, 1, 0}, {0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}},
        {{0, -1, 0}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
        {{0, 0, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
        {{0, 0, -1}, {0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}},
        {{1, 0, 0}, {1, 0, 0}, {1, 0, 1}, {1, 1, 1}, {1, 1, 0}},
        {{-1, 0, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}}
    };

    static final class Face {
        final float[] v = new float[12];
        float nx, ny, nz;
        int r, g, b;
    }

    public static final class Cos {
        public final String name, slot;
        final int[][] vox; // x, y, z, rgb
        Face[] left, right;

        Cos(String name, String slot, int[][] vox) {
            this.name = name;
            this.slot = slot;
            this.vox = vox;
        }
    }

    private static final Map<String, Cos> ALL = new LinkedHashMap<>();
    private static final Map<String, String> EQUIP = new HashMap<>();
    private static long scanned = 0L;

    public static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("ametra").resolve("cosmeticos");
    }

    public static synchronized List<Cos> list() {
        if (System.currentTimeMillis() - scanned > 5000L) rescan();
        return new ArrayList<>(ALL.values());
    }

    public static synchronized String equipped(String slot) {
        return EQUIP.get(slot);
    }

    public static synchronized void rescan() {
        scanned = System.currentTimeMillis();
        ALL.clear();
        EQUIP.clear();
        try {
            Files.createDirectories(dir());
            try (var st = Files.list(dir())) {
                List<Path> files = new ArrayList<>();
                st.forEach(files::add);
                java.util.Collections.sort(files);
                for (Path p : files) {
                    String fn = p.getFileName().toString();
                    if (!fn.endsWith(".json") || fn.startsWith("_")) continue;
                    try { load(p, fn.substring(0, fn.length() - 5)); } catch (Exception ignored) { /* archivo roto: se salta */ }
                }
            }
            Path eq = dir().resolve("_equipado.json");
            if (Files.exists(eq)) {
                JsonObject o = JsonParser.parseString(Files.readString(eq, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    Cos c = ALL.get(e.getValue().getAsString());
                    if (c != null && c.slot.equals(e.getKey())) EQUIP.put(e.getKey(), c.name);
                }
            }
        } catch (Exception ignored) { /* sin carpeta: no hay cosméticos propios */ }
    }

    private static void load(Path p, String name) throws Exception {
        JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!"ametra-voxel".equals(o.get("format").getAsString())) return;
        String slot = o.get("slot").getAsString();
        if (!SLOTS.contains(slot)) return;
        JsonArray a = o.getAsJsonArray("voxels");
        List<int[]> out = new ArrayList<>();
        for (JsonElement e : a) {
            if (out.size() >= 512) break;
            JsonArray v = e.getAsJsonArray();
            int x = v.get(0).getAsInt(), y = v.get(1).getAsInt(), z = v.get(2).getAsInt();
            if (x < -16 || x > 15 || y < 0 || y > 39 || z < -16 || z > 15) continue; // fuera de la cuadrícula
            out.add(new int[] {x, y, z, Integer.parseInt(v.get(3).getAsString(), 16) & 0xFFFFFF});
        }
        if (!out.isEmpty()) ALL.put(name, new Cos(name, slot, out.toArray(new int[0][])));
    }

    /** Equipa (o quita, con null) un cosmético propio en su ranura y lo guarda. */
    public static synchronized void equip(String slot, String name) {
        if (name == null) EQUIP.remove(slot);
        else EQUIP.put(slot, name);
        try {
            JsonObject o = new JsonObject();
            for (Map.Entry<String, String> e : EQUIP.entrySet()) o.addProperty(e.getKey(), e.getValue());
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("_equipado.json"), o.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) { /* no se pudo guardar: se pierde al cerrar */ }
    }

    // ---------- malla ----------
    private static Face[] mesh(Cos c, boolean right) {
        float ax = 0f, ay = 0f;
        switch (c.slot) {
            case "boots" -> { ax = 1.9f; ay = 12f; }
            case "fists" -> { ax = 5f; ay = 2f; }
            default -> { }
        }
        boolean side = SIDE.contains(c.slot);
        Set<Integer> has = new HashSet<>();
        for (int[] v : c.vox) if (!side || v[0] >= 0) has.add(key(v[0], v[1], v[2]));
        List<Face> out = new ArrayList<>();
        for (int[] v : c.vox) {
            if (side && v[0] < 0) continue;
            for (float[][] f : FACES) {
                if (has.contains(key(v[0] + (int) f[0][0], v[1] + (int) f[0][1], v[2] + (int) f[0][2]))) continue;
                Face q = new Face();
                for (int i = 0; i < 4; i++) {
                    float x = v[0] + f[i + 1][0], y = v[1] + f[i + 1][1], z = v[2] + f[i + 1][2];
                    q.v[i * 3] = right ? ax - x : x - ax;
                    q.v[i * 3 + 1] = 24f - y - ay;
                    q.v[i * 3 + 2] = -z;
                }
                q.nx = right ? -f[0][0] : f[0][0];
                q.ny = -f[0][1];
                q.nz = -f[0][2];
                q.r = (v[3] >> 16) & 255;
                q.g = (v[3] >> 8) & 255;
                q.b = v[3] & 255;
                out.add(q);
            }
        }
        return out.toArray(new Face[0]);
    }

    private static int key(int x, int y, int z) {
        return ((x + 64) << 16) | ((y + 64) << 8) | (z + 64);
    }

    private static void draw(MatrixStack ms, VertexConsumer vc, Face[] faces, int light) {
        MatrixStack.Entry e = ms.peek();
        Matrix4f m = e.getPositionMatrix();
        for (Face f : faces) {
            for (int i = 0; i < 4; i++) {
                Compat.end(vc.vertex(m, f.v[i * 3] / 16f, f.v[i * 3 + 1] / 16f, f.v[i * 3 + 2] / 16f)
                    .color(f.r, f.g, f.b, 255).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, f.nx, f.ny, f.nz));
            }
        }
    }

    /** Dibuja los cosméticos propios equipados sobre el jugador local. */
    public static void render(MatrixStack ms, VertexConsumerProvider vcp, int light, PlayerEntityModel<AbstractClientPlayerEntity> model,
                              boolean helmet, boolean chest) {
        list(); // refresca cada pocos segundos
        Map<String, Cos> now = new LinkedHashMap<>();
        synchronized (VoxelCosmetics.class) {
            for (Map.Entry<String, String> e : EQUIP.entrySet()) {
                Cos c = ALL.get(e.getValue());
                if (c != null) now.put(e.getKey(), c);
            }
        }
        if (now.isEmpty()) return;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE));
        float back = chest ? 1f / 16f : 0f;
        for (Map.Entry<String, Cos> en : now.entrySet()) {
            Cos c = en.getValue();
            if (c.left == null) c.left = mesh(c, false);
            switch (en.getKey()) {
                case "cape", "pack", "back", "wings" -> {
                    ms.push();
                    model.body.rotate(ms);
                    ms.translate(0f, 0f, back);
                    draw(ms, vc, c.left, light);
                    if (en.getKey().equals("wings")) {
                        if (c.right == null) c.right = mesh(c, true);
                        draw(ms, vc, c.right, light);
                    }
                    ms.pop();
                }
                case "aura" -> {
                    ms.push();
                    model.body.rotate(ms);
                    draw(ms, vc, c.left, light);
                    ms.pop();
                }
                case "hat", "glasses", "face" -> {
                    if (helmet && !en.getKey().equals("hat")) break;
                    ms.push();
                    model.head.rotate(ms);
                    if (helmet) ms.translate(0f, -1f / 16f, 0f);
                    draw(ms, vc, c.left, light);
                    ms.pop();
                }
                case "boots" -> {
                    if (c.right == null) c.right = mesh(c, true);
                    ms.push();
                    model.leftLeg.rotate(ms);
                    draw(ms, vc, c.left, light);
                    ms.pop();
                    ms.push();
                    model.rightLeg.rotate(ms);
                    draw(ms, vc, c.right, light);
                    ms.pop();
                }
                case "fists" -> {
                    if (c.right == null) c.right = mesh(c, true);
                    ms.push();
                    model.leftArm.rotate(ms);
                    draw(ms, vc, c.left, light);
                    ms.pop();
                    ms.push();
                    model.rightArm.rotate(ms);
                    draw(ms, vc, c.right, light);
                    ms.pop();
                }
                default -> { }
            }
        }
    }
}
