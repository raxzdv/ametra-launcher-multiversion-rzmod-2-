package com.ametra;

import java.util.ArrayList;
import java.util.List;

/** GENERADO por tools/gen_cosmetics.py — no editar a mano. Geometría y UV de los cosméticos. */
public final class CosmeticData {
    private CosmeticData() {}

    /** Caja en píxeles de modelo. uv = cara frontal/trasera, uv2 = resto de caras (en texels de una hoja de ts x ts). */
    public static final class B {
        public final float x, y, z, w, h, d, ry;
        public final float u0, v0, u1, v1, s0, t0, s1, t1;
        public final boolean glow;
        public final int flags, an;
        /** Animación (ver ORBIT/BOB/... en gen_cosmetics.py): amplitud, ciclos por segundo, fase en grados, altura de ascenso. */
        public final float ap, asp, aph, ar;

        B(float x, float y, float z, float w, float h, float d, float ry,
          int u0, int v0, int u1, int v1, int s0, int t0, int s1, int t1, int ts, boolean glow, int flags,
          int an, float ap, float asp, float aph, float ar) {
            this.x = x; this.y = y; this.z = z; this.w = w; this.h = h; this.d = d; this.ry = ry;
            this.u0 = u0 / (float) ts; this.v0 = v0 / (float) ts; this.u1 = u1 / (float) ts; this.v1 = v1 / (float) ts;
            this.s0 = s0 / (float) ts; this.t0 = t0 / (float) ts; this.s1 = s1 / (float) ts; this.t1 = t1 / (float) ts;
            this.glow = glow; this.flags = flags; this.an = an; this.ap = ap; this.asp = asp; this.aph = aph; this.ar = ar;
        }
    }

    public static final class Item {
        public final String cat, id, name, rar, tex;
        public final int price;
        public final B[] boxes;

        Item(String cat, String id, String name, String rar, int price, B[] boxes) {
            this.cat = cat; this.id = id; this.name = name; this.rar = rar; this.price = price; this.boxes = boxes;
            this.tex = cat + "_" + id;
        }
    }

    public static final List<Item> ALL = new ArrayList<>();

    public static Item find(String cat, String id) {
        if (id == null) return null;
        for (Item it : ALL) if (it.cat.equals(cat) && it.id.equals(id)) return it;
        return null;
    }

    static {
        ALL.add(new Item("cape", "amatista", "Amatista", "comun", 0, new B[] {
            new B(0f, 7.5f, 2.7f, 9.6f, 15f, 0.7f, 0f, 0, 0, 32, 48, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 0.9f, 2.45f, 10.2f, 1.7f, 1.3f, 0f, 34, 8, 64, 13, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("cape", "esmeralda", "Esmeralda", "comun", 150, new B[] {
            new B(0f, 7.5f, 2.7f, 9.6f, 15f, 0.7f, 0f, 0, 0, 32, 48, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 0.9f, 2.45f, 10.2f, 1.7f, 1.3f, 0f, 34, 8, 64, 13, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("cape", "hielo", "Escarcha", "raro", 450, new B[] {
            new B(0f, 7.5f, 2.7f, 9.6f, 15f, 0.7f, 0f, 0, 0, 32, 48, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 0.9f, 2.45f, 10.2f, 1.7f, 1.3f, 0f, 34, 8, 64, 13, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("cape", "rubi", "Rubí", "raro", 450, new B[] {
            new B(0f, 7.5f, 2.7f, 9.6f, 15f, 0.7f, 0f, 0, 0, 32, 48, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 0.9f, 2.45f, 10.2f, 1.7f, 1.3f, 0f, 34, 8, 64, 13, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("cape", "oro", "Oro", "raro", 450, new B[] {
            new B(0f, 7.5f, 2.7f, 9.6f, 15f, 0.7f, 0f, 0, 0, 32, 48, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 0.9f, 2.45f, 10.2f, 1.7f, 1.3f, 0f, 34, 8, 64, 13, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("cape", "noche", "Noche", "epico", 1000, new B[] {
            new B(0f, 7.5f, 2.7f, 9.6f, 15f, 0.7f, 0f, 0, 0, 32, 48, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 0.9f, 2.45f, 10.2f, 1.7f, 1.3f, 0f, 34, 8, 64, 13, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("cape", "ametra", "RzClient", "epico", 1000, new B[] {
            new B(0f, 7.5f, 2.7f, 9.6f, 15f, 0.7f, 0f, 0, 0, 32, 48, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 0.9f, 2.45f, 10.2f, 1.7f, 1.3f, 0f, 34, 8, 64, 13, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("cape", "aurora", "Aurora", "legendario", 2200, new B[] {
            new B(0f, 7.5f, 2.7f, 9.6f, 15f, 0.7f, 0f, 0, 0, 32, 48, 36, 0, 40, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 0.9f, 2.45f, 10.2f, 1.7f, 1.3f, 0f, 34, 8, 64, 13, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("cape", "fuego", "Fénix", "legendario", 2200, new B[] {
            new B(0f, 7.5f, 2.7f, 9.6f, 15f, 0.7f, 0f, 0, 0, 32, 48, 36, 0, 40, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 0.9f, 2.45f, 10.2f, 1.7f, 1.3f, 0f, 34, 8, 64, 13, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("wings", "angel", "Ángel", "raro", 450, new B[] {
            new B(8f, 6.5f, 0f, 16f, 18f, 0.3f, 0f, 0, 0, 32, 40, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("wings", "dragon", "Dragón", "epico", 1000, new B[] {
            new B(8f, 6.5f, 0f, 16f, 18f, 0.3f, 0f, 0, 0, 32, 40, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("wings", "mariposa", "Mariposa", "epico", 1000, new B[] {
            new B(8f, 6.5f, 0f, 16f, 18f, 0.3f, 0f, 0, 0, 32, 40, 36, 0, 40, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("wings", "neon", "Neón", "legendario", 2200, new B[] {
            new B(8f, 6.5f, 0f, 16f, 18f, 0.3f, 0f, 0, 0, 32, 40, 36, 0, 40, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("wings", "fenix", "Alas de fénix", "legendario", 2200, new B[] {
            new B(8f, 6.5f, 0f, 16f, 18f, 0.3f, 0f, 0, 0, 32, 40, 36, 0, 40, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("hat", "gorra", "Gorra", "comun", 0, new B[] {
            new B(0f, -9f, 0f, 9.2f, 2.4f, 9.2f, 0f, 0, 0, 24, 8, 26, 0, 34, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -8.2f, -5.5f, 8.8f, 0.5f, 3f, 0f, 0, 10, 24, 18, 0, 10, 24, 18, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -8.4f, 4.8f, 6f, 1f, 0.6f, 0f, 26, 10, 34, 14, 26, 10, 34, 14, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("hat", "copa", "Sombrero de copa", "raro", 450, new B[] {
            new B(0f, -8.3f, 0f, 12f, 0.6f, 12f, 0f, 0, 0, 24, 24, 0, 0, 24, 24, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -11.6f, 0f, 7f, 6.6f, 7f, 0f, 26, 0, 50, 24, 0, 26, 12, 38, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -9.4f, 0f, 7.25f, 1.1f, 7.25f, 0f, 26, 26, 54, 32, 26, 26, 54, 32, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("hat", "orejas", "Orejas de gato", "raro", 450, new B[] {
            new B(0f, -8.2f, 0f, 8.6f, 0.7f, 1.6f, 0f, 0, 18, 24, 24, 0, 18, 24, 24, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(3f, -9.5f, 0f, 2.8f, 1.6f, 1.3f, 0f, 0, 0, 16, 16, 0, 0, 16, 16, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-3f, -9.5f, 0f, 2.8f, 1.6f, 1.3f, 0f, 0, 0, 16, 16, 0, 0, 16, 16, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(3f, -10.8f, 0f, 1.6f, 1.2f, 1.1f, 0f, 0, 0, 16, 16, 0, 0, 16, 16, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-3f, -10.8f, 0f, 1.6f, 1.2f, 1.1f, 0f, 0, 0, 16, 16, 0, 0, 16, 16, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(3f, -9.7f, -0.7f, 1.6f, 1.6f, 0.1f, 0f, 18, 0, 26, 8, 18, 0, 26, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-3f, -9.7f, -0.7f, 1.6f, 1.6f, 0.1f, 0f, 18, 0, 26, 8, 18, 0, 26, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("hat", "mago", "Gorro de mago", "epico", 1000, new B[] {
            new B(0f, -8.3f, 0f, 11.4f, 0.5f, 11.4f, 0f, 0, 0, 24, 24, 0, 0, 24, 24, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -9.1f, 0f, 7.9f, 1.2f, 7.9f, 0f, 0, 26, 28, 32, 0, 26, 28, 32, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -11f, 0f, 7.4f, 3f, 7.4f, 0f, 26, 0, 50, 24, 26, 0, 50, 24, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0.4f, -13.4f, 0f, 5.4f, 2.4f, 5.4f, 0f, 26, 0, 50, 24, 26, 0, 50, 24, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(1f, -15.5f, 0f, 3.6f, 2f, 3.6f, 0f, 26, 0, 50, 24, 26, 0, 50, 24, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(1.8f, -17.2f, 0f, 2f, 1.6f, 2f, 0f, 26, 0, 50, 24, 26, 0, 50, 24, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(2.4f, -18.5f, 0f, 1.4f, 1.2f, 1.4f, 0f, 52, 0, 60, 8, 52, 0, 60, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("hat", "corona", "Corona", "legendario", 2200, new B[] {
            new B(0f, -8.9f, 0f, 8.8f, 1.8f, 8.8f, 0f, 0, 0, 28, 8, 30, 0, 38, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-3.3f, -10.5f, -3.3f, 1.6f, 1.8f, 1.6f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-3.3f, -10.5f, 3.3f, 1.6f, 1.8f, 1.6f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -10.5f, 3.3f, 1.6f, 1.8f, 1.6f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(3.3f, -10.5f, -3.3f, 1.6f, 1.8f, 1.6f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(3.3f, -10.5f, 3.3f, 1.6f, 1.8f, 1.6f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -10.8f, -3.5f, 2f, 2.6f, 1.2f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -9f, -4.5f, 1.6f, 1.4f, 0.2f, 0f, 10, 10, 14, 14, 10, 10, 14, 14, 64, true, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("hat", "halo", "Halo", "legendario", 2200, new B[] {
            new B(0f, -12f, -3.6f, 8.2f, 0.9f, 1.1f, 0f, 0, 0, 16, 4, 0, 0, 16, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -12f, 3.6f, 8.2f, 0.9f, 1.1f, 0f, 0, 0, 16, 4, 0, 0, 16, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(-3.6f, -12f, 0f, 1.1f, 0.9f, 6.1f, 0f, 0, 0, 16, 4, 0, 0, 16, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(3.6f, -12f, 0f, 1.1f, 0.9f, 6.1f, 0f, 0, 0, 16, 4, 0, 0, 16, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("hat", "vikingo", "Casco vikingo", "epico", 1000, new B[] {
            new B(0f, -9.6f, 0f, 9f, 3.2f, 9f, 0f, 0, 0, 24, 10, 26, 0, 34, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -8.3f, 0f, 9.3f, 0.8f, 9.3f, 0f, 10, 12, 34, 16, 10, 12, 34, 16, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-5.2f, -9.4f, 0f, 1.8f, 1.8f, 1.8f, 0f, 0, 12, 8, 20, 0, 12, 8, 20, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-6.4f, -10.8f, 0f, 1.6f, 1.8f, 1.6f, 0f, 0, 12, 8, 20, 0, 12, 8, 20, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-7.2f, -12.4f, 0f, 1.4f, 1.8f, 1.4f, 0f, 0, 12, 8, 20, 0, 12, 8, 20, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-7.6f, -14f, 0f, 1f, 1.6f, 1f, 0f, 0, 12, 8, 20, 0, 12, 8, 20, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(5.2f, -9.4f, 0f, 1.8f, 1.8f, 1.8f, 0f, 0, 12, 8, 20, 0, 12, 8, 20, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(6.4f, -10.8f, 0f, 1.6f, 1.8f, 1.6f, 0f, 0, 12, 8, 20, 0, 12, 8, 20, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(7.2f, -12.4f, 0f, 1.4f, 1.8f, 1.4f, 0f, 0, 12, 8, 20, 0, 12, 8, 20, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(7.6f, -14f, 0f, 1f, 1.6f, 1f, 0f, 0, 12, 8, 20, 0, 12, 8, 20, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("glasses", "sol", "Gafas de sol", "comun", 0, new B[] {
            new B(2.2f, -4.6f, -4.4f, 3.4f, 2.2f, 0.5f, 0f, 0, 0, 16, 10, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-2.2f, -4.6f, -4.4f, 3.4f, 2.2f, 0.5f, 0f, 0, 0, 16, 10, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -4.9f, -4.4f, 1.2f, 0.6f, 0.5f, 0f, 18, 0, 22, 4, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(4.15f, -4.9f, -2.2f, 0.4f, 0.5f, 4.3f, 0f, 18, 0, 22, 4, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-4.15f, -4.9f, -2.2f, 0.4f, 0.5f, 4.3f, 0f, 18, 0, 22, 4, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("glasses", "neon", "Gafas neón", "raro", 450, new B[] {
            new B(2.2f, -4.6f, -4.4f, 3.4f, 2.2f, 0.5f, 0f, 0, 0, 16, 10, 18, 0, 22, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(-2.2f, -4.6f, -4.4f, 3.4f, 2.2f, 0.5f, 0f, 0, 0, 16, 10, 18, 0, 22, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -4.9f, -4.4f, 1.2f, 0.6f, 0.5f, 0f, 18, 0, 22, 4, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(4.15f, -4.9f, -2.2f, 0.4f, 0.5f, 4.3f, 0f, 18, 0, 22, 4, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-4.15f, -4.9f, -2.2f, 0.4f, 0.5f, 4.3f, 0f, 18, 0, 22, 4, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("glasses", "corazon", "Corazón", "epico", 1000, new B[] {
            new B(2.2f, -4.4f, -4.4f, 3.8f, 2.8f, 0.5f, 0f, 0, 0, 16, 10, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-2.2f, -4.4f, -4.4f, 3.8f, 2.8f, 0.5f, 0f, 0, 0, 16, 10, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -4.9f, -4.4f, 1.2f, 0.6f, 0.5f, 0f, 18, 0, 22, 4, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(4.15f, -4.9f, -2.2f, 0.4f, 0.5f, 4.3f, 0f, 18, 0, 22, 4, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-4.15f, -4.9f, -2.2f, 0.4f, 0.5f, 4.3f, 0f, 18, 0, 22, 4, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("glasses", "visor", "Visor", "legendario", 2200, new B[] {
            new B(0f, -4.6f, -4.5f, 8.8f, 2.2f, 0.7f, 0f, 0, 12, 32, 18, 18, 0, 22, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(4.15f, -4.9f, -2.2f, 0.4f, 0.5f, 4.3f, 0f, 18, 0, 22, 4, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-4.15f, -4.9f, -2.2f, 0.4f, 0.5f, 4.3f, 0f, 18, 0, 22, 4, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("glasses", "pixel", "Gafas pixel", "epico", 1000, new B[] {
            new B(0f, -4.6f, -4.5f, 9f, 2.2f, 0.7f, 0f, 0, 20, 32, 26, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(4.15f, -4.9f, -2.2f, 0.4f, 0.5f, 4.3f, 0f, 18, 0, 22, 4, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-4.15f, -4.9f, -2.2f, 0.4f, 0.5f, 4.3f, 0f, 18, 0, 22, 4, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("pack", "mochila", "Mochila", "comun", 0, new B[] {
            new B(0f, 6f, 3.7f, 6f, 8f, 3f, 0f, 0, 0, 24, 32, 26, 0, 34, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 3.3f, 3.95f, 6.3f, 2.8f, 3.4f, 0f, 0, 34, 26, 44, 26, 0, 34, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 8.8f, 5.45f, 4f, 3f, 0.6f, 0f, 28, 34, 44, 46, 26, 0, 34, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("pack", "rosa", "Mochila rosa", "raro", 450, new B[] {
            new B(0f, 6f, 3.7f, 6f, 8f, 3f, 0f, 0, 0, 24, 32, 26, 0, 34, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 3.3f, 3.95f, 6.3f, 2.8f, 3.4f, 0f, 0, 34, 26, 44, 26, 0, 34, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 8.8f, 5.45f, 4f, 3f, 0.6f, 0f, 28, 34, 44, 46, 26, 0, 34, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("pack", "jetpack", "Jetpack", "epico", 1000, new B[] {
            new B(2.4f, 6.2f, 4.4f, 3.4f, 9f, 3.4f, 0f, 0, 0, 12, 36, 14, 0, 18, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(2.4f, 1.2f, 4.4f, 3.6f, 1.2f, 3.6f, 0f, 20, 0, 32, 8, 20, 0, 32, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(2.4f, 11.4f, 4.4f, 2.6f, 1.4f, 2.6f, 0f, 20, 10, 32, 18, 20, 10, 32, 18, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(2.4f, 13.6f, 4.4f, 1.8f, 2.6f, 1.8f, 0f, 34, 0, 42, 12, 34, 0, 42, 12, 64, true, 1, 0, 0f, 0f, 0f, 0f),
            new B(-2.4f, 6.2f, 4.4f, 3.4f, 9f, 3.4f, 0f, 0, 0, 12, 36, 14, 0, 18, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-2.4f, 1.2f, 4.4f, 3.6f, 1.2f, 3.6f, 0f, 20, 0, 32, 8, 20, 0, 32, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-2.4f, 11.4f, 4.4f, 2.6f, 1.4f, 2.6f, 0f, 20, 10, 32, 18, 20, 10, 32, 18, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-2.4f, 13.6f, 4.4f, 1.8f, 2.6f, 1.8f, 0f, 34, 0, 42, 12, 34, 0, 42, 12, 64, true, 1, 0, 0f, 0f, 0f, 0f),
            new B(0f, 3.4f, 4.4f, 2.6f, 1.2f, 1.2f, 0f, 34, 14, 44, 18, 34, 14, 44, 18, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("wings", "angel_wings", "Alas de ángel", "legendario", 2400, new B[] {
            new B(12f, 11f, 0f, 24f, 28f, 0.3f, 0f, 0, 0, 48, 56, 96, 40, 100, 44, 128, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(10f, 11f, 0.45f, 20f, 24f, 0.3f, 0f, 50, 0, 90, 48, 96, 40, 100, 44, 128, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(7f, 8f, 0.9f, 14f, 16f, 0.3f, 0f, 92, 0, 120, 32, 96, 40, 100, 44, 128, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("wings", "hell_wings", "Alas del Soberano", "legendario", 2400, new B[] {
            new B(12f, 11f, 0f, 24f, 28f, 0.3f, 0f, 0, 0, 48, 56, 96, 40, 100, 44, 128, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(10f, 11f, 0.45f, 20f, 24f, 0.3f, 0f, 50, 0, 90, 48, 96, 40, 100, 44, 128, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("wings", "void_wings", "Alas del Vacío", "legendario", 2400, new B[] {
            new B(12f, 11f, 0f, 24f, 28f, 0.3f, 0f, 0, 0, 48, 56, 96, 40, 100, 44, 128, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(10f, 11f, 0.45f, 20f, 24f, 0.3f, 0f, 50, 0, 90, 48, 96, 40, 100, 44, 128, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(7f, 8f, 0.9f, 14f, 16f, 0.3f, 0f, 92, 0, 120, 32, 96, 40, 100, 44, 128, true, 0, 0, 0f, 0f, 0f, 0f),
        }));
        ALL.add(new Item("hat", "angel_halo", "Halo celestial", "epico", 1100, new B[] {
            new B(0f, -13f, -4.2f, 9.2f, 0.8f, 1.1f, 0f, 0, 0, 16, 4, 0, 0, 16, 4, 64, true, 0, 2, 0.5f, 0.5f, 0f, 0f),
            new B(0f, -13f, 4.2f, 9.2f, 0.8f, 1.1f, 0f, 0, 0, 16, 4, 0, 0, 16, 4, 64, true, 0, 2, 0.5f, 0.5f, 0f, 0f),
            new B(-4.2f, -13f, 0f, 1.1f, 0.8f, 7.3f, 0f, 0, 0, 16, 4, 0, 0, 16, 4, 64, true, 0, 2, 0.5f, 0.5f, 0f, 0f),
            new B(4.2f, -13f, 0f, 1.1f, 0.8f, 7.3f, 0f, 0, 0, 16, 4, 0, 0, 16, 4, 64, true, 0, 2, 0.5f, 0.5f, 0f, 0f),
            new B(5.2f, -9.2f, 0.6f, 0.3f, 4.4f, 3.6f, -22f, 0, 6, 8, 14, 0, 6, 8, 14, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-5.2f, -9.2f, 0.6f, 0.3f, 4.4f, 3.6f, 22f, 0, 6, 8, 14, 0, 6, 8, 14, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(2.6f, -15.6f, 0f, 1.1f, 1.1f, 1.1f, 0f, 10, 6, 14, 10, 10, 6, 14, 10, 64, true, 0, 2, 0.9f, 0.7f, 0f, 0f),
            new B(-2.4f, -16f, 1f, 0.9f, 0.9f, 0.9f, 0f, 10, 6, 14, 10, 10, 6, 14, 10, 64, true, 0, 2, 0.9f, 0.7f, 180f, 0f),
        }));
        ALL.add(new Item("hat", "hell_horns", "Cuernos infernales", "epico", 1100, new B[] {
            new B(-3.7f, -8.7f, 0f, 3.12f, 2.25f, 3.12f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-4.7f, -10.3f, -0.1f, 2.73f, 2.375f, 2.73f, 6f, 0, 8, 8, 16, 0, 8, 8, 16, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-5.9f, -11.7f, -0.4f, 2.47f, 2.25f, 2.47f, 12f, 0, 16, 8, 24, 0, 16, 8, 24, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-7.2f, -12.9f, -0.9f, 2.08f, 2.125f, 2.08f, 18f, 0, 24, 8, 32, 0, 24, 8, 32, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-8.5f, -13.8f, -1.6f, 1.69f, 1.875f, 1.69f, 24f, 0, 32, 8, 40, 0, 32, 8, 40, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(-8.9f, -15.2f, -1.9f, 1.4f, 2f, 1.4f, 0f, 10, 0, 18, 10, 10, 0, 18, 10, 64, true, 0, 4, 0.3f, 2.2f, 140f, 0f),
            new B(3.7f, -8.7f, 0f, 3.12f, 2.25f, 3.12f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(4.7f, -10.3f, -0.1f, 2.73f, 2.375f, 2.73f, -6f, 0, 8, 8, 16, 0, 8, 8, 16, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(5.9f, -11.7f, -0.4f, 2.47f, 2.25f, 2.47f, -12f, 0, 16, 8, 24, 0, 16, 8, 24, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(7.2f, -12.9f, -0.9f, 2.08f, 2.125f, 2.08f, -18f, 0, 24, 8, 32, 0, 24, 8, 32, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(8.5f, -13.8f, -1.6f, 1.69f, 1.875f, 1.69f, -24f, 0, 32, 8, 40, 0, 32, 8, 40, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(8.9f, -15.2f, -1.9f, 1.4f, 2f, 1.4f, 0f, 10, 0, 18, 10, 10, 0, 18, 10, 64, true, 0, 4, 0.3f, 2.2f, 0f, 0f),
        }));
        ALL.add(new Item("hat", "void_crown", "Corona del Vacío", "epico", 1100, new B[] {
            new B(0f, -8.7f, -4.5f, 9.4f, 0.7f, 0.8f, 0f, 10, 0, 26, 4, 10, 0, 26, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -8.7f, 4.5f, 9.4f, 0.7f, 0.8f, 0f, 10, 0, 26, 4, 10, 0, 26, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(-4.5f, -8.7f, 0f, 0.8f, 0.7f, 8.2f, 0f, 10, 0, 26, 4, 10, 0, 26, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(4.5f, -8.7f, 0f, 0.8f, 0.7f, 8.2f, 0f, 10, 0, 26, 4, 10, 0, 26, 4, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(4.157f, -10.7f, 1.722f, 1.4f, 4.2f, 1.4f, -22.5f, 0, 0, 8, 16, 0, 0, 8, 16, 64, false, 0, 2, 0.45f, 0.4f, 0f, 0f),
            new B(1.722f, -9.9f, 4.157f, 1.4f, 2.6f, 1.4f, -67.5f, 0, 0, 8, 16, 0, 0, 8, 16, 64, false, 0, 2, 0.45f, 0.4f, 47f, 0f),
            new B(-1.722f, -10.5f, 4.157f, 1.4f, 3.8f, 1.4f, -112.5f, 0, 0, 8, 16, 0, 0, 8, 16, 64, false, 0, 2, 0.45f, 0.4f, 94f, 0f),
            new B(-4.157f, -9.8f, 1.722f, 1.4f, 2.4f, 1.4f, -157.5f, 0, 0, 8, 16, 0, 0, 8, 16, 64, false, 0, 2, 0.45f, 0.4f, 141f, 0f),
            new B(-4.157f, -10.9f, -1.722f, 1.4f, 4.6f, 1.4f, -202.5f, 0, 0, 8, 16, 0, 0, 8, 16, 64, false, 0, 2, 0.45f, 0.4f, 188f, 0f),
            new B(-1.722f, -10f, -4.157f, 1.4f, 2.8f, 1.4f, -247.5f, 0, 0, 8, 16, 0, 0, 8, 16, 64, false, 0, 2, 0.45f, 0.4f, 235f, 0f),
            new B(1.722f, -10.4f, -4.157f, 1.4f, 3.6f, 1.4f, -292.5f, 0, 0, 8, 16, 0, 0, 8, 16, 64, false, 0, 2, 0.45f, 0.4f, 282f, 0f),
            new B(4.157f, -9.85f, -1.722f, 1.4f, 2.5f, 1.4f, -337.5f, 0, 0, 8, 16, 0, 0, 8, 16, 64, false, 0, 2, 0.45f, 0.4f, 329f, 0f),
            new B(0f, -18f, 0f, 2.6f, 4.6f, 2.6f, 0f, 28, 0, 38, 16, 28, 0, 38, 16, 64, true, 0, 34, 0.9f, 0.35f, 0f, 0f),
        }));
        ALL.add(new Item("back", "angel_back", "Manto celestial", "epico", 1000, new B[] {
            new B(0f, 5.5f, 2.7f, 8.4f, 10.4f, 0.9f, 0f, 0, 0, 16, 20, 18, 0, 26, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(6f, -0.5f, 0f, 4.8f, 1.2f, 4.8f, 0f, 28, 0, 40, 10, 28, 0, 40, 10, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-6f, -0.5f, 0f, 4.8f, 1.2f, 4.8f, 0f, 28, 0, 40, 10, 28, 0, 40, 10, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(6.4f, -2.4f, 0.8f, 0.3f, 3.4f, 3f, -18f, 42, 0, 50, 8, 42, 0, 50, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-6.4f, -2.4f, 0.8f, 0.3f, 3.4f, 3f, 18f, 42, 0, 50, 8, 42, 0, 50, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -1f, 4.6f, 16f, 16f, 0.3f, 0f, 0, 24, 32, 56, 0, 24, 32, 56, 64, true, 0, 4, 0.03f, 0.4f, 0f, 0f),
        }));
        ALL.add(new Item("back", "hell_back", "Espalda del Soberano", "epico", 1000, new B[] {
            new B(0f, 5.5f, 2.7f, 8.4f, 10.4f, 1f, 0f, 0, 0, 16, 20, 18, 0, 26, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 2f, 3.7f, 1.2f, 1.8f, 1.2f, 0f, 28, 0, 34, 10, 28, 0, 34, 10, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 5f, 3.7f, 1.2f, 2.4f, 1.2f, 0f, 28, 0, 34, 10, 28, 0, 34, 10, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 8.3f, 3.7f, 1.2f, 1.8f, 1.2f, 0f, 28, 0, 34, 10, 28, 0, 34, 10, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(-5.8f, -1.6f, 0.4f, 1.8f, 3.2f, 1.8f, 0f, 28, 0, 34, 10, 28, 0, 34, 10, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-5.9f, -4f, 0.2f, 1.1f, 2.2f, 1.1f, 0f, 28, 0, 34, 10, 28, 0, 34, 10, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(5.8f, -1.6f, 0.4f, 1.8f, 3.2f, 1.8f, 0f, 28, 0, 34, 10, 28, 0, 34, 10, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(5.9f, -4f, 0.2f, 1.1f, 2.2f, 1.1f, 0f, 28, 0, 34, 10, 28, 0, 34, 10, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 11.2f, 3.4f, 2.1f, 2f, 2.1f, 0f, 36, 0, 44, 8, 36, 0, 44, 8, 64, false, 0, 8, 0.25f, 0.55f, 0f, 0f),
            new B(0f, 12.9f, 4f, 1.957f, 2f, 1.957f, 0f, 36, 0, 44, 8, 36, 0, 44, 8, 64, false, 0, 8, 0.42f, 0.55f, -38f, 0f),
            new B(0f, 14.6f, 4.6f, 1.814f, 2f, 1.814f, 0f, 36, 0, 44, 8, 36, 0, 44, 8, 64, false, 0, 8, 0.59f, 0.55f, -76f, 0f),
            new B(0f, 16.3f, 5.2f, 1.671f, 2f, 1.671f, 0f, 36, 0, 44, 8, 36, 0, 44, 8, 64, false, 0, 8, 0.76f, 0.55f, -114f, 0f),
            new B(0f, 18f, 5.8f, 1.529f, 2f, 1.529f, 0f, 36, 0, 44, 8, 36, 0, 44, 8, 64, false, 0, 8, 0.93f, 0.55f, -152f, 0f),
            new B(0f, 19.7f, 6.4f, 1.386f, 2f, 1.386f, 0f, 36, 0, 44, 8, 36, 0, 44, 8, 64, false, 0, 8, 1.1f, 0.55f, -190f, 0f),
            new B(0f, 21.4f, 7f, 1.243f, 2f, 1.243f, 0f, 36, 0, 44, 8, 36, 0, 44, 8, 64, false, 0, 8, 1.27f, 0.55f, -228f, 0f),
            new B(0f, 23.1f, 7.6f, 1.1f, 2f, 1.1f, 0f, 36, 0, 44, 8, 36, 0, 44, 8, 64, false, 0, 8, 1.44f, 0.55f, -266f, 0f),
            new B(0f, 25.7f, 8.4f, 3.8f, 3.8f, 0.6f, 0f, 46, 0, 58, 12, 46, 0, 58, 12, 64, true, 0, 8, 1.5f, 0.55f, -304f, 0f),
        }));
        ALL.add(new Item("back", "void_back", "Fragmentos del Vacío", "epico", 1000, new B[] {
            new B(8.8f, 1.6f, 0.4f, 4f, 3.4f, 5.2f, 0f, 0, 0, 16, 16, 0, 0, 16, 16, 64, false, 0, 2, 0.8f, 0.42f, 0f, 0f),
            new B(8.8f, -1.4f, 0.4f, 1.4f, 3.2f, 1.4f, 0f, 18, 0, 26, 12, 18, 0, 26, 12, 64, true, 0, 2, 0.8f, 0.42f, 0f, 0f),
            new B(10.9f, 3.4f, 0.4f, 1.1f, 1.1f, 1.1f, 0f, 40, 0, 44, 4, 40, 0, 44, 4, 64, true, 0, 2, 1.2f, 0.42f, 90f, 0f),
            new B(3.2f, 7.4f, 4.3f, 2f, 6f, 2f, 40f, 28, 0, 38, 20, 28, 0, 38, 20, 64, true, 0, 2, 0.6f, 0.35f, 45f, 0f),
            new B(-8.8f, 1.6f, 0.4f, 4f, 3.4f, 5.2f, 0f, 0, 0, 16, 16, 0, 0, 16, 16, 64, false, 0, 2, 0.8f, 0.42f, 180f, 0f),
            new B(-8.8f, -1.4f, 0.4f, 1.4f, 3.2f, 1.4f, 0f, 18, 0, 26, 12, 18, 0, 26, 12, 64, true, 0, 2, 0.8f, 0.42f, 180f, 0f),
            new B(-10.9f, 3.4f, 0.4f, 1.1f, 1.1f, 1.1f, 0f, 40, 0, 44, 4, 40, 0, 44, 4, 64, true, 0, 2, 1.2f, 0.42f, 270f, 0f),
            new B(-3.2f, 7.4f, 4.3f, 2f, 6f, 2f, -40f, 28, 0, 38, 20, 28, 0, 38, 20, 64, true, 0, 2, 0.6f, 0.35f, 225f, 0f),
            new B(0f, 4.6f, 4.7f, 3.2f, 9f, 3.2f, 0f, 28, 0, 38, 20, 28, 0, 38, 20, 64, true, 0, 2, 0.5f, 0.3f, 0f, 0f),
        }));
        ALL.add(new Item("boots", "angel_boots", "Botas celestiales", "epico", 900, new B[] {
            new B(0f, 9.8f, 0f, 4.7f, 4.5f, 4.7f, 0f, 0, 0, 10, 9, 12, 0, 22, 9, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 7.7f, 0f, 5.1f, 1.1f, 5.1f, 0f, 0, 10, 10, 13, 0, 10, 10, 13, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 11.2f, -2.6f, 3.2f, 1.4f, 1.2f, 0f, 12, 10, 18, 13, 12, 10, 18, 13, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(2.7f, 8.8f, 0.9f, 0.3f, 3.6f, 3.4f, 0f, 20, 10, 28, 18, 20, 10, 28, 18, 64, false, 0, 8, 0.25f, 0.7f, 0f, 0f),
        }));
        ALL.add(new Item("boots", "hell_boots", "Botas del Soberano", "epico", 900, new B[] {
            new B(0f, 9.8f, 0f, 4.7f, 4.5f, 4.7f, 0f, 0, 0, 10, 9, 12, 0, 22, 9, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 7.7f, 0f, 5.1f, 1.1f, 5.1f, 0f, 0, 10, 10, 13, 0, 10, 10, 13, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 11.2f, -2.6f, 3.2f, 1.4f, 1.2f, 0f, 12, 10, 18, 13, 12, 10, 18, 13, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 11.5f, -3.4f, 1.4f, 1.4f, 2f, 0f, 12, 10, 18, 13, 12, 10, 18, 13, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 10.9f, 2.9f, 1.2f, 2.2f, 1.6f, 0f, 12, 10, 18, 13, 12, 10, 18, 13, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-1.6f, 6.5f, -1.6f, 1.3f, 2f, 1.3f, 0f, 20, 10, 28, 18, 20, 10, 28, 18, 64, true, 0, 4, 0.3f, 2f, 0f, 0f),
            new B(1.6f, 6.5f, -1.6f, 1.3f, 2f, 1.3f, 0f, 20, 10, 28, 18, 20, 10, 28, 18, 64, true, 0, 4, 0.3f, 2f, 90f, 0f),
            new B(0f, 6.5f, 1.4f, 1.3f, 2f, 1.3f, 0f, 20, 10, 28, 18, 20, 10, 28, 18, 64, true, 0, 4, 0.3f, 2f, 200f, 0f),
            new B(-1.7f, 6.5f, 1.2f, 1.3f, 2f, 1.3f, 0f, 20, 10, 28, 18, 20, 10, 28, 18, 64, true, 0, 4, 0.3f, 2f, 300f, 0f),
        }));
        ALL.add(new Item("boots", "void_boots", "Botas del Vacío", "epico", 900, new B[] {
            new B(0f, 9.8f, 0f, 4.7f, 4.5f, 4.7f, 0f, 0, 0, 10, 9, 12, 0, 22, 9, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 7.7f, 0f, 5.1f, 1.1f, 5.1f, 0f, 0, 10, 10, 13, 0, 10, 10, 13, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 11.2f, -2.6f, 3.2f, 1.4f, 1.2f, 0f, 12, 10, 18, 13, 12, 10, 18, 13, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 12f, 0f, 4.9f, 0.3f, 4.9f, 0f, 12, 10, 18, 13, 12, 10, 18, 13, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(3.9f, 8f, 0f, 1f, 2f, 1f, 0f, 20, 10, 28, 18, 20, 10, 28, 18, 64, true, 0, 3, 0.5f, 0.55f, 0f, 0f),
            new B(3.9f, 8f, 0f, 1f, 2f, 1f, 0f, 20, 10, 28, 18, 20, 10, 28, 18, 64, true, 0, 3, 0.5f, 0.55f, 180f, 0f),
        }));
        ALL.add(new Item("fists", "angel_fists", "Guanteletes celestiales", "epico", 900, new B[] {
            new B(0f, 8f, 0f, 4.7f, 4.2f, 4.7f, 0f, 0, 0, 10, 9, 12, 0, 22, 9, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 5.8f, 0f, 5.1f, 1f, 5.1f, 0f, 0, 10, 10, 13, 0, 10, 10, 13, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 8f, -2.55f, 3.6f, 2.4f, 0.5f, 0f, 12, 10, 18, 13, 12, 10, 18, 13, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(2.7f, 7.2f, 0.6f, 0.3f, 4f, 3.2f, 0f, 20, 10, 28, 18, 20, 10, 28, 18, 64, false, 0, 8, 0.2f, 0.7f, 0f, 0f),
        }));
        ALL.add(new Item("fists", "hell_fists", "Puños del Soberano", "epico", 900, new B[] {
            new B(0f, 8f, 0f, 4.7f, 4.2f, 4.7f, 0f, 0, 0, 10, 9, 12, 0, 22, 9, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 5.8f, 0f, 5.1f, 1f, 5.1f, 0f, 0, 10, 10, 13, 0, 10, 10, 13, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 8f, -2.55f, 3.6f, 2.4f, 0.5f, 0f, 12, 10, 18, 13, 12, 10, 18, 13, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-1.5f, 7.5f, -3.5f, 0.9f, 0.9f, 1.8f, 0f, 12, 10, 18, 13, 12, 10, 18, 13, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 7.5f, -3.5f, 0.9f, 0.9f, 1.8f, 0f, 12, 10, 18, 13, 12, 10, 18, 13, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(1.5f, 7.5f, -3.5f, 0.9f, 0.9f, 1.8f, 0f, 12, 10, 18, 13, 12, 10, 18, 13, 64, true, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 4.7f, 0f, 1.5f, 2.2f, 1.5f, 0f, 20, 10, 28, 18, 20, 10, 28, 18, 64, true, 0, 4, 0.3f, 2f, 0f, 0f),
        }));
        ALL.add(new Item("fists", "void_fists", "Puños del Vacío", "epico", 900, new B[] {
            new B(0f, 8f, 0f, 4.7f, 4.2f, 4.7f, 0f, 0, 0, 10, 9, 12, 0, 22, 9, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 5.8f, 0f, 5.1f, 1f, 5.1f, 0f, 0, 10, 10, 13, 0, 10, 10, 13, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 8f, -2.55f, 3.6f, 2.4f, 0.5f, 0f, 12, 10, 18, 13, 12, 10, 18, 13, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(3.7f, 6.2f, 0f, 1f, 1.9f, 1f, 0f, 20, 10, 28, 18, 20, 10, 28, 18, 64, true, 0, 3, 0.4f, 0.7f, 0f, 0f),
            new B(3.7f, 6.2f, 0f, 1f, 1.9f, 1f, 0f, 20, 10, 28, 18, 20, 10, 28, 18, 64, true, 0, 3, 0.4f, 0.7f, 180f, 0f),
        }));
        ALL.add(new Item("aura", "angel_aura", "Aura celestial", "legendario", 1900, new B[] {
            new B(8.5f, 24f, 0f, 1f, 1f, 1f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 17, 0f, 0.26f, 0f, 26f),
            new B(10.2f, 24f, 0f, 1f, 1f, 1f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 17, 0f, 0.3f, 30f, 26f),
            new B(11.9f, 24f, 0f, 1f, 1f, 1f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 17, 0f, 0.34f, 60f, 26f),
            new B(8.5f, 24f, 0f, 1.4f, 2.6f, 0.3f, 0f, 10, 0, 18, 8, 10, 0, 18, 8, 64, true, 0, 49, 0f, 0.2f, 90f, 26f),
            new B(10.2f, 24f, 0f, 1f, 1f, 1f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 17, 0f, 0.3f, 120f, 26f),
            new B(11.9f, 24f, 0f, 1f, 1f, 1f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 17, 0f, 0.34f, 150f, 26f),
            new B(8.5f, 24f, 0f, 1f, 1f, 1f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 17, 0f, 0.26f, 180f, 26f),
            new B(10.2f, 24f, 0f, 1.4f, 2.6f, 0.3f, 0f, 10, 0, 18, 8, 10, 0, 18, 8, 64, true, 0, 49, 0f, 0.23f, 210f, 26f),
            new B(11.9f, 24f, 0f, 1f, 1f, 1f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 17, 0f, 0.34f, 240f, 26f),
            new B(8.5f, 24f, 0f, 1f, 1f, 1f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 17, 0f, 0.26f, 270f, 26f),
            new B(10.2f, 24f, 0f, 1f, 1f, 1f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 17, 0f, 0.3f, 300f, 26f),
            new B(11.9f, 24f, 0f, 1.4f, 2.6f, 0.3f, 0f, 10, 0, 18, 8, 10, 0, 18, 8, 64, true, 0, 49, 0f, 0.26f, 330f, 26f),
        }));
        ALL.add(new Item("aura", "hell_aura", "Aura infernal", "legendario", 1900, new B[] {
            new B(7.5f, 25f, 0f, 0.9f, 0.9f, 0.9f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 17, 0f, 0.4f, 0f, 28f),
            new B(8.9f, 25f, 0f, 1.3f, 1.3f, 1.3f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 17, 0f, 0.45f, 26f, 28f),
            new B(10.3f, 25f, 0f, 0.9f, 0.9f, 0.9f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 17, 0f, 0.5f, 52f, 28f),
            new B(11.7f, 25f, 0f, 1.3f, 1.3f, 1.3f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 17, 0f, 0.55f, 78f, 28f),
            new B(7.5f, 25f, 0f, 0.9f, 0.9f, 0.9f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 17, 0f, 0.4f, 104f, 28f),
            new B(8.9f, 25f, 0f, 1.3f, 1.3f, 1.3f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 17, 0f, 0.45f, 130f, 28f),
            new B(10.3f, 25f, 0f, 0.9f, 0.9f, 0.9f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 17, 0f, 0.5f, 156f, 28f),
            new B(11.7f, 25f, 0f, 1.3f, 1.3f, 1.3f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 17, 0f, 0.55f, 182f, 28f),
            new B(7.5f, 25f, 0f, 0.9f, 0.9f, 0.9f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 17, 0f, 0.4f, 208f, 28f),
            new B(8.9f, 25f, 0f, 1.3f, 1.3f, 1.3f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 17, 0f, 0.45f, 234f, 28f),
            new B(10.3f, 25f, 0f, 0.9f, 0.9f, 0.9f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 17, 0f, 0.5f, 260f, 28f),
            new B(11.7f, 25f, 0f, 1.3f, 1.3f, 1.3f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 17, 0f, 0.55f, 286f, 28f),
            new B(7.5f, 25f, 0f, 0.9f, 0.9f, 0.9f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 17, 0f, 0.4f, 312f, 28f),
            new B(8.9f, 25f, 0f, 1.3f, 1.3f, 1.3f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 17, 0f, 0.45f, 338f, 28f),
            new B(7.4f, 22f, 0f, 2.4f, 2.8f, 2.4f, 0f, 10, 10, 18, 18, 10, 10, 18, 18, 64, true, 0, 5, 0.25f, 0.5f, 0f, 0f),
            new B(7.4f, 22f, 0f, 2.4f, 2.8f, 2.4f, 0f, 10, 10, 18, 18, 10, 10, 18, 18, 64, true, 0, 5, 0.25f, 0.5f, 120f, 0f),
            new B(7.4f, 22f, 0f, 2.4f, 2.8f, 2.4f, 0f, 10, 10, 18, 18, 10, 10, 18, 18, 64, true, 0, 5, 0.25f, 0.5f, 240f, 0f),
        }));
        ALL.add(new Item("aura", "void_aura", "Aura del Vacío", "legendario", 1900, new B[] {
            new B(8f, 3f, 0f, 1.6f, 1.6f, 1.6f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 5, 0.3f, 0.22f, 0f, 0f),
            new B(10f, 6.2f, 0f, 1.6f, 1.6f, 1.6f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 5, 0.3f, 0.25f, 51f, 0f),
            new B(12f, 9.4f, 0f, 1.6f, 1.6f, 1.6f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 5, 0.3f, 0.28f, 102f, 0f),
            new B(8f, 12.6f, 0f, 1.6f, 1.6f, 1.6f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 5, 0.3f, 0.22f, 153f, 0f),
            new B(10f, 15.8f, 0f, 1.6f, 1.6f, 1.6f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 5, 0.3f, 0.25f, 204f, 0f),
            new B(12f, 19f, 0f, 1.6f, 1.6f, 1.6f, 0f, 0, 0, 8, 8, 0, 0, 8, 8, 64, true, 0, 5, 0.3f, 0.28f, 255f, 0f),
            new B(8f, 22.2f, 0f, 1.6f, 1.6f, 1.6f, 0f, 0, 10, 8, 18, 0, 10, 8, 18, 64, true, 0, 5, 0.3f, 0.22f, 306f, 0f),
            new B(6.6f, 25.4f, 0f, 1.2f, 2f, 1.2f, 0f, 10, 10, 18, 18, 10, 10, 18, 18, 64, true, 0, 33, 0f, 0.18f, 0f, 0f),
            new B(6.6f, 25.4f, 0f, 1.2f, 2f, 1.2f, 0f, 10, 10, 18, 18, 10, 10, 18, 18, 64, true, 0, 33, 0f, 0.18f, 45f, 0f),
            new B(6.6f, 25.4f, 0f, 1.2f, 2f, 1.2f, 0f, 10, 10, 18, 18, 10, 10, 18, 18, 64, true, 0, 33, 0f, 0.18f, 90f, 0f),
            new B(6.6f, 25.4f, 0f, 1.2f, 2f, 1.2f, 0f, 10, 10, 18, 18, 10, 10, 18, 18, 64, true, 0, 33, 0f, 0.18f, 135f, 0f),
            new B(6.6f, 25.4f, 0f, 1.2f, 2f, 1.2f, 0f, 10, 10, 18, 18, 10, 10, 18, 18, 64, true, 0, 33, 0f, 0.18f, 180f, 0f),
            new B(6.6f, 25.4f, 0f, 1.2f, 2f, 1.2f, 0f, 10, 10, 18, 18, 10, 10, 18, 18, 64, true, 0, 33, 0f, 0.18f, 225f, 0f),
            new B(6.6f, 25.4f, 0f, 1.2f, 2f, 1.2f, 0f, 10, 10, 18, 18, 10, 10, 18, 18, 64, true, 0, 33, 0f, 0.18f, 270f, 0f),
            new B(6.6f, 25.4f, 0f, 1.2f, 2f, 1.2f, 0f, 10, 10, 18, 18, 10, 10, 18, 18, 64, true, 0, 33, 0f, 0.18f, 315f, 0f),
        }));
        ALL.add(new Item("face", "payaso", "Payaso", "epico", 1200, new B[] {
            new B(0f, -4f, -4.45f, 8.4f, 8.4f, 0.25f, 0f, 0, 0, 16, 16, 18, 0, 22, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -2.5f, -5.2f, 2f, 2f, 1.6f, 0f, 24, 0, 32, 8, 24, 0, 32, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -2.5f, -5.4f, 1.4f, 2.6f, 1.4f, 0f, 24, 0, 32, 8, 24, 0, 32, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -2.5f, -5.4f, 2.6f, 1.4f, 1.4f, 0f, 24, 0, 32, 8, 24, 0, 32, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-5.4f, -7f, 0.2f, 3.1f, 3.1f, 3.1f, 0f, 0, 20, 8, 28, 0, 20, 8, 28, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-5.9f, -4.5f, 0.2f, 3.5f, 3.1f, 3.1f, 0f, 20, 20, 28, 28, 20, 20, 28, 28, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-5.4f, -2.2f, 0.2f, 2.8f, 2.6f, 2.8f, 0f, 30, 20, 38, 28, 30, 20, 38, 28, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(5.4f, -7f, 0.2f, 3.1f, 3.1f, 3.1f, 0f, 0, 20, 8, 28, 0, 20, 8, 28, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(5.9f, -4.5f, 0.2f, 3.5f, 3.1f, 3.1f, 0f, 20, 20, 28, 28, 20, 20, 28, 28, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(5.4f, -2.2f, 0.2f, 2.8f, 2.6f, 2.8f, 0f, 30, 20, 38, 28, 30, 20, 38, 28, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -4.4f, 4.8f, 6.4f, 5.4f, 1.6f, 0f, 40, 20, 48, 28, 40, 20, 48, 28, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -9.6f, -0.4f, 3.6f, 3f, 3.2f, 0f, 10, 20, 18, 28, 10, 20, 18, 28, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, -11.6f, -0.4f, 1.8f, 1.4f, 1.8f, 0f, 0, 20, 8, 28, 0, 20, 8, 28, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(1.9f, 1.7f, -2.6f, 2.8f, 2f, 1f, 0f, 34, 0, 44, 8, 34, 0, 44, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(-1.9f, 1.7f, -2.6f, 2.8f, 2f, 1f, 0f, 34, 0, 44, 8, 34, 0, 44, 8, 64, false, 0, 0, 0f, 0f, 0f, 0f),
            new B(0f, 1.7f, -2.65f, 1.3f, 1.3f, 1.3f, 0f, 46, 0, 50, 4, 46, 0, 50, 4, 64, false, 0, 0, 0f, 0f, 0f, 0f),
        }));
    }
}
