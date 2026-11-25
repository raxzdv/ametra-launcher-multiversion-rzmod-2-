package com.ametra;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.math.MatrixStack;

/** Lista de módulos del HUD y su render. */
public final class Hud {
    private Hud() {}

    // ---------- Contador de clics ----------
    static final class Cps {
        static final ArrayDeque<Long> L = new ArrayDeque<>(), R = new ArrayDeque<>();
        static boolean lp, rp, leftDown, rightDown;

        static void poll(MinecraftClient mc) {
            long win = mc.getWindow().getHandle();
            boolean l = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean r = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            long now = System.currentTimeMillis();
            boolean free = mc.currentScreen == null;
            if (free && l && !lp) L.addLast(now);
            if (free && r && !rp) R.addLast(now);
            lp = l;
            rp = r;
            leftDown = l && free;
            rightDown = r && free;
            while (!L.isEmpty() && now - L.peekFirst() > 1000) L.pollFirst();
            while (!R.isEmpty() && now - R.peekFirst() > 1000) R.pollFirst();
        }
    }

    // ---------- Módulo de texto: [barra] ETIQUETA valor ----------
    abstract static class TextModule extends HudModule {
        TextModule(String id, String name, String desc, String cat, int x, int y, boolean en) {
            super(id, name, desc, cat, x, y, en);
        }

        abstract String label();

        abstract String value(MinecraftClient mc);

        private String cachedVal = "";
        private long nextRefresh = 0L;

        /** Valor cacheado: evita String.format / consultas en cada fotograma. */
        private String cached(MinecraftClient mc) {
            long now = System.currentTimeMillis();
            if (now >= nextRefresh) {
                cachedVal = value(mc);
                nextRefresh = now + 50L;
            }
            return cachedVal;
        }

        @Override
        public void draw(DrawContext ctx, MinecraftClient mc, float alpha, int color) {
            String l = label(), v = cached(mc);
            int lw = Gfx.width(l), vw = Gfx.width(v);
            if (AmetraConfig.hudCompact) {
                // Estilo Feather: una pastilla oscura sin barra de color, etiqueta suave + valor.
                w = 6 + lw + 4 + vw + 6;
                h = 14;
                if (cfg.bg) Gfx.rbox(ctx, 0, 0, w, h, 0x000000, 0.42f * alpha);
                Gfx.text(ctx, l, 6, 3, 0xB4B4C4, alpha, cfg.shadow);
                Gfx.text(ctx, v, 6 + lw + 4, 3, color, alpha, cfg.shadow);
                return;
            }
            w = 2 + 5 + lw + 5 + vw + 5;
            h = 15;
            if (cfg.bg) Gfx.rbox(ctx, 0, 0, w, h, 0x000000, 0.48f * alpha);
            Gfx.rect(ctx, 0, 1, 2, h - 2, cfg.chroma ? color : AmetraConfig.accent, alpha);
            Gfx.text(ctx, l, 7, 3, 0xA6A6B8, alpha, cfg.shadow);
            Gfx.text(ctx, v, 7 + lw + 5, 3, color, alpha, cfg.shadow);
        }
    }

    // ---------- Logo ----------
    static final class LogoModule extends HudModule {
        LogoModule() {
            super("logo", "Logo", "Logo de RzClient en pantalla.", "Visual", 6, 6, false);
        }

        @Override
        public void draw(DrawContext ctx, MinecraftClient mc, float alpha, int color) {
            int tw = Gfx.width("RZCLIENT") + 1;
            w = 4 + 16 + 5 + tw + 5;
            h = 22;
            if (cfg.bg) Gfx.rbox(ctx, 0, 0, w, h, 0x000000, 0.48f * alpha);
            float bob = (float) Math.sin(System.currentTimeMillis() / 600.0) * 0.8f;
            Gfx.logo(ctx, 4, 3 + bob, 16, alpha);
            Gfx.bold(ctx, "RZCLIENT", 25, 7, cfg.chroma ? color : AmetraConfig.accent, alpha, cfg.shadow);
        }
    }

    // ---------- Teclas (WASD, ratón, espacio) ----------
    static final class KeysModule extends HudModule {
        final float[] press = new float[8];

        KeysModule() {
            super("keys", "Teclas", "WASD, espacio y clics animados.", "Combate", 6, 124, true);
            w = 58;
            h = 72;
        }

        private void key(DrawContext ctx, int i, String label, int x, int y, int kw, int kh, boolean down, float alpha, int color) {
            press[i] += ((down ? 1f : 0f) - press[i]) * 0.3f;
            float p = press[i];
            float bgA = (cfg.bg ? 0.48f : 0.12f) * (1 - p) + 0.9f * p;
            Gfx.rbox(ctx, x, y, kw, kh, Colors.lerp(0x000000, color, p), bgA * alpha);
            int tc = Colors.lerp(0xE6E6F0, 0x0A0A12, p);
            if (!label.isEmpty()) {
                Gfx.text(ctx, label, x + (kw - Gfx.width(label)) / 2f, y + (kh - 8) / 2f, tc, alpha, false);
            } else {
                Gfx.rect(ctx, x + 8, y + kh / 2f - 0.5f, kw - 16, 1, tc, alpha);
            }
        }

        @Override
        public void draw(DrawContext ctx, MinecraftClient mc, float alpha, int color) {
            w = 58;
            h = 72;
            KeyBinding f = mc.options.forwardKey, l = mc.options.leftKey, b = mc.options.backKey, r = mc.options.rightKey;
            key(ctx, 0, "W", 20, 0, 18, 18, f.isPressed(), alpha, color);
            key(ctx, 1, "A", 0, 20, 18, 18, l.isPressed(), alpha, color);
            key(ctx, 2, "S", 20, 20, 18, 18, b.isPressed(), alpha, color);
            key(ctx, 3, "D", 40, 20, 18, 18, r.isPressed(), alpha, color);
            key(ctx, 4, "LMB", 0, 40, 28, 18, Cps.leftDown, alpha, color);
            key(ctx, 5, "RMB", 30, 40, 28, 18, Cps.rightDown, alpha, color);
            key(ctx, 6, "", 0, 60, 58, 12, mc.options.jumpKey.isPressed(), alpha, color);
        }
    }

    // ---------- Armadura y objeto en mano ----------
    static final class ArmorModule extends HudModule {
        private final ItemStack[] st = new ItemStack[5];

        ArmorModule() {
            super("armor", "Armadura", "Casco, peto, pantalón, botas y mano.", "Combate", 96, 124, false);
        }

        @Override
        public void draw(DrawContext ctx, MinecraftClient mc, float alpha, int color) {
            for (int i = 0; i < 4; i++) st[i] = mc.player.getInventory().getArmorStack(3 - i);
            st[4] = mc.player.getMainHandStack();
            int rows = 0, tw = 0;
            for (ItemStack it : st) {
                if (it.isEmpty()) continue;
                rows++;
                tw = Math.max(tw, Gfx.width(label(it)));
            }
            if (rows == 0) {
                w = 74;
                h = 15;
                if (cfg.bg) Gfx.rbox(ctx, 0, 0, w, h, 0x000000, 0.48f * alpha);
                Gfx.rect(ctx, 0, 1, 2, h - 2, AmetraConfig.accent, alpha);
                Gfx.text(ctx, "Sin armadura", 7, 3, 0xA6A6B8, alpha, cfg.shadow);
                return;
            }
            w = 4 + 16 + 4 + tw + 5;
            h = rows * 18 + 2;
            if (cfg.bg) Gfx.rbox(ctx, 0, 0, w, h, 0x000000, 0.48f * alpha);
            int y = 2;
            for (ItemStack it : st) {
                if (it.isEmpty()) continue;
                if (alpha > 0.6f) ctx.drawItem(it, 4, y);
                Gfx.text(ctx, label(it), 24, y + 4, color, alpha, cfg.shadow);
                y += 18;
            }
        }

        private static String label(ItemStack it) {
            if (it.getMaxDamage() > 0) return String.valueOf(it.getMaxDamage() - it.getDamage());
            return "";
        }
    }

    /** Función sin dibujo (zoom, fullbright): solo interruptor y ajustes. */
    static final class Feature extends HudModule {
        Feature(String id, String name, String desc, String cat, boolean en) {
            super(id, name, desc, cat, 0, 0, en);
            hud = false;
        }

        @Override
        public void draw(DrawContext ctx, MinecraftClient mc, float alpha, int color) { }
    }


    // ---------- Soup PvP: contador de sopas, barra de vida y destello al curarte ----------
    static final class SoupModule extends HudModule {
        private static final String[] HEART = {
            ".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..." };
        static final int GREEN = 0x4DFF88, RED = 0xFF4D5E;
        static final ItemStack STEW = new ItemStack(Items.MUSHROOM_STEW);

        static int count = 0, bowls = 0, lastCount = -1;
        static float lastHp = -1f, popAmt = 0f;
        static long lastDropAt = 0L, lastHealAt = 0L, popAt = 0L;
        static float lastHealAmt = 0f;
        static boolean fired = true;

        SoupModule() {
            super("soup", "Soup PvP", "Cuenta tus sopas, muestra tu vida y da un destello verde al curarte con una.", "Combate", 96, 142, false);
            extra("low", "Aviso con pocas sopas", 0f, 16f, 4f, "%.0f");
            extra("flash", "Destello al curarte", 0f, 1f, 0.6f, "%.2f");
        }

        /** Cuenta sopas/cuencos y detecta "me comí una sopa y subió mi vida". Se llama cada fotograma con el módulo activo. */
        static void overlay(DrawContext ctx, MinecraftClient mc, HudModule m) {
            if (mc.player == null) return;
            int c = 0, b = 0;
            net.minecraft.entity.player.PlayerInventory inv = mc.player.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack it = inv.getStack(i);
                if (it.isOf(Items.MUSHROOM_STEW)) c += it.getCount();
                else if (it.isOf(Items.BOWL)) b += it.getCount();
            }
            count = c;
            bowls = b;
            long now = System.currentTimeMillis();
            float hp = mc.player.getHealth();
            if (lastCount >= 0 && c < lastCount) { lastDropAt = now; fired = false; }
            if (lastHp >= 0f && hp > lastHp + 0.4f) { lastHealAt = now; lastHealAmt = hp - lastHp; }
            lastCount = c;
            lastHp = hp;
            // sopa gastada y vida subida con menos de 0,45 s de diferencia (da igual cuál llegue primero)
            if (!fired && lastDropAt != 0L && lastHealAt != 0L && Math.abs(lastDropAt - lastHealAt) < 450L) {
                fired = true;
                popAt = now;
                popAmt = lastHealAmt;
            }
            // destello verde en los bordes de la pantalla
            float flash = m.cfg.ex("flash", 0.6f);
            float k = 1f - (now - popAt) / 480f;
            if (flash > 0.01f && k > 0f && popAt != 0L) {
                int W = ctx.getScaledWindowWidth(), H = ctx.getScaledWindowHeight();
                for (int i = 0; i < 12; i++) {
                    float a = k * flash * 0.26f * (1f - i / 12f);
                    Gfx.rect(ctx, 0, i * 2, W, 2, GREEN, a);
                    Gfx.rect(ctx, 0, H - (i + 1) * 2, W, 2, GREEN, a);
                    Gfx.rect(ctx, i * 2, 0, 2, H, GREEN, a * 0.8f);
                    Gfx.rect(ctx, W - (i + 1) * 2, 0, 2, H, GREEN, a * 0.8f);
                }
            }
        }

        private static void heart(DrawContext ctx, float x, float y, int rgb, float a) {
            for (int r = 0; r < HEART.length; r++)
                for (int cc = 0; cc < HEART[r].length(); cc++)
                    if (HEART[r].charAt(cc) == 'X') Gfx.rect(ctx, x + cc, y + r, 1, 1, rgb, a);
        }

        @Override
        public void draw(DrawContext ctx, MinecraftClient mc, float alpha, int color) {
            long now = System.currentTimeMillis();
            int low = Math.round(cfg.ex("low", 4f));
            boolean empty = count == 0, warn = count <= low;
            float pulse = warn ? 0.5f + 0.5f * (float) Math.sin(now / (empty ? 140.0 : 260.0)) : 0f;
            int numCol = warn ? Colors.lerp(color, RED, 0.55f + 0.45f * pulse) : color;
            String num = String.valueOf(count);
            String lab = empty ? "SIN SOPAS" : warn ? "POCAS" : "SOPAS";
            int nw = Gfx.boldWidth(num), lw = Gfx.width(lab);
            w = 4 + 16 + 5 + nw + 5 + lw + 6;
            h = 24;
            if (cfg.bg) Gfx.rbox(ctx, 0, 0, w, h, 0x000000, 0.48f * alpha);
            Gfx.rect(ctx, 0, 1, 2, h - 2, cfg.chroma ? color : (warn ? Colors.lerp(AmetraConfig.accent, RED, pulse) : AmetraConfig.accent), alpha);

            // icono: el item real al rebotar un poco cuando te curas
            float since = (now - popAt) / 1000f;
            float bounce = popAt != 0L && since < 0.35f ? -(float) Math.sin(since / 0.35f * Math.PI) * 3f : 0f;
            if (alpha > 0.6f) ctx.drawItem(STEW, 4, 2 + Math.round(bounce));
            Gfx.bold(ctx, num, 25, 4, numCol, alpha, cfg.shadow);
            Gfx.text(ctx, lab, 25 + nw + 5, 4, warn ? Colors.lerp(0xA6A6B8, RED, pulse) : 0xA6A6B8, alpha, cfg.shadow);

            // barra de vida abajo: verde -> rojo según lo que te queda
            float maxHp = Math.max(1f, mc.player.getMaxHealth());
            float frac = Math.max(0f, Math.min(1f, mc.player.getHealth() / maxHp));
            int bx = 4, bw = w - 10, by = h - 6;
            Gfx.rect(ctx, bx, by, bw, 3, 0xFFFFFF, 0.14f * alpha);
            Gfx.rect(ctx, bx, by, Math.max(1f, bw * frac), 3, Colors.lerp(RED, GREEN, frac), alpha);
            if (popAt != 0L && since < 0.45f) Gfx.rect(ctx, bx, by, Math.max(1f, bw * frac), 3, 0xFFFFFF, (1f - since / 0.45f) * 0.55f * alpha);

            // corazón flotante con lo que curaste
            if (popAt != 0L && since < 1.0f) {
                float a = alpha * (1f - since);
                float fy = -9f - since * 14f;
                heart(ctx, w - 30, fy, RED, a);
                String t = "+" + (popAmt == Math.floor(popAmt) ? String.valueOf((int) popAmt) : String.format(Locale.ROOT, "%.1f", popAmt));
                Gfx.bold(ctx, t, w - 20, fy - 1, GREEN, a, true);
            }
        }
    }

    // ---------- Efectos de poción activos con su tiempo ----------
    static final class PotionModule extends HudModule {
        PotionModule() {
            super("potions", "Efectos", "Tus pociones activas con el tiempo que les queda.", "Info", 186, 86, false);
        }

        private static String time(net.minecraft.entity.effect.StatusEffectInstance e) {
            if (e.isInfinite()) return "--";
            int sec = Math.max(0, e.getDuration() / 20);
            return String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
        }

        @Override
        public void draw(DrawContext ctx, MinecraftClient mc, float alpha, int color) {
            java.util.List<String> names = new java.util.ArrayList<>(), times = new java.util.ArrayList<>();
            java.util.List<Integer> cols = new java.util.ArrayList<>();
            if (mc.player != null) {
                for (net.minecraft.entity.effect.StatusEffectInstance e : mc.player.getStatusEffects()) {
                    String n = Compat.effectName(e);
                    if (e.getAmplifier() > 0) n += " " + (e.getAmplifier() + 1);
                    names.add(n);
                    times.add(time(e));
                    cols.add(Compat.effectColor(e) & 0xFFFFFF);
                }
            }
            if (names.isEmpty()) {
                // sin efectos: se muestra una pastilla vacía para poder verla y moverla en el editor
                String t = "Sin efectos";
                w = 12 + Gfx.width(t);
                h = 14;
                if (cfg.bg) Gfx.rbox(ctx, 0, 0, w, h, 0x000000, 0.42f * alpha);
                Gfx.text(ctx, t, 6, 3, 0x8A8A9C, alpha, cfg.shadow);
                return;
            }
            int nw = 0, tw = 0;
            for (int i = 0; i < names.size(); i++) {
                nw = Math.max(nw, Gfx.width(names.get(i)));
                tw = Math.max(tw, Gfx.width(times.get(i)));
            }
            w = 6 + 4 + 4 + nw + 8 + tw + 6;
            h = 4 + names.size() * 12;
            if (cfg.bg) Gfx.rbox(ctx, 0, 0, w, h, 0x000000, 0.42f * alpha);
            for (int i = 0; i < names.size(); i++) {
                int y = 2 + i * 12;
                Gfx.rect(ctx, 6, y + 3, 4, 6, cols.get(i), alpha);
                Gfx.text(ctx, names.get(i), 14, y + 2, 0xB4B4C4, alpha, cfg.shadow);
                Gfx.text(ctx, times.get(i), 14 + nw + 8, y + 2, color, alpha, cfg.shadow);
            }
        }
    }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};

    public static final List<HudModule> MODULES = List.of(
        new LogoModule(),
        new TextModule("fps", "FPS", "Fotogramas por segundo.", "Info", 6, 32, true) {
            String label() { return "FPS"; }
            String value(MinecraftClient mc) { return String.valueOf(mc.getCurrentFps()); }
        },
        new TextModule("cps", "CPS", "Clics por segundo (izq. | der.).", "Combate", 6, 50, true) {
            String label() { return "CPS"; }
            String value(MinecraftClient mc) { return Cps.L.size() + " | " + Cps.R.size(); }
        },
        new KeysModule(),
        new TextModule("ping", "Ping", "Latencia con el servidor.", "Info", 6, 68, false) {
            String label() { return "PING"; }
            String value(MinecraftClient mc) {
                if (mc.isInSingleplayer()) return "local";
                ClientPlayNetworkHandler nh = mc.getNetworkHandler();
                PlayerListEntry e = (nh == null || mc.player == null) ? null : nh.getPlayerListEntry(mc.player.getUuid());
                return e == null ? "-" : e.getLatency() + " ms";
            }
        },
        new TextModule("coords", "Coordenadas", "Tu posición X Y Z.", "Info", 6, 86, false) {
            String label() { return "XYZ"; }
            String value(MinecraftClient mc) {
                if (mc.player == null) return "0 0 0";
                return (int) Math.floor(mc.player.getX()) + " " + (int) Math.floor(mc.player.getY()) + " " + (int) Math.floor(mc.player.getZ());
            }
        },
        new TextModule("clock", "Reloj", "La hora de tu equipo.", "Info", 6, 104, false) {
            String label() { return "HORA"; }
            String value(MinecraftClient mc) { return LocalTime.now().format(CLOCK); }
        },
        new TextModule("speed", "Velocidad", "Bloques por segundo.", "Info", 96, 32, false) {
            String label() { return "VEL"; }
            String value(MinecraftClient mc) {
                if (mc.player == null) return "0.0 b/s";
                double vx = mc.player.getVelocity().x, vz = mc.player.getVelocity().z;
                return String.format(Locale.ROOT, "%.1f b/s", Math.sqrt(vx * vx + vz * vz) * 20.0);
            }
        },
        new TextModule("dir", "Dirección", "Brújula: hacia dónde miras.", "Info", 96, 50, false) {
            String label() { return "DIR"; }
            String value(MinecraftClient mc) {
                if (mc.player == null) return "N";
                float yaw = ((mc.player.getYaw() % 360f) + 360f) % 360f;
                int idx = Math.round(((yaw + 180f) % 360f) / 45f) % 8;
                return DIRS[idx] + " " + Math.round(yaw);
            }
        },
        new TextModule("ram", "Memoria", "RAM que usa el juego.", "Info", 96, 68, false) {
            String label() { return "RAM"; }
            String value(MinecraftClient mc) {
                Runtime rt = Runtime.getRuntime();
                long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L, max = rt.maxMemory() / 1048576L;
                return used + "/" + max + " MB";
            }
        },
        new TextModule("server", "Servidor", "La IP del servidor actual.", "Info", 96, 86, false) {
            String label() { return "IP"; }
            String value(MinecraftClient mc) {
                ServerInfo si = mc.getCurrentServerEntry();
                return si == null ? "un jugador" : si.address;
            }
        },
        new ArmorModule(),
        new TextModule("reach", "Reach", "Distancia a la que le pegaste por última vez a algo.", "Combate", 186, 32, false) {
            String label() { return "REACH"; }
            String value(MinecraftClient mc) {
                return Features.lastReach < 0 ? "-" : String.format(Locale.ROOT, "%.2f b", Features.lastReach);
            }
        },
        new TextModule("sat", "Saturación", "Tu saturación de comida (lo que tarda en bajar el hambre).", "Info", 186, 50, false) {
            String label() { return "SAT"; }
            String value(MinecraftClient mc) {
                return mc.player == null ? "0.0" : String.format(Locale.ROOT, "%.1f", mc.player.getHungerManager().getSaturationLevel());
            }
        },
        new TextModule("itemcount", "Contador de objetos", "Cuántos del objeto que llevas en la mano tienes en total en el inventario.", "Info", 186, 68, false) {
            String label() { return "x"; }
            String value(MinecraftClient mc) {
                if (mc.player == null) return "-";
                ItemStack held = mc.player.getMainHandStack();
                if (held.isEmpty()) return "-";
                int n = 0;
                net.minecraft.entity.player.PlayerInventory inv = mc.player.getInventory();
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack it = inv.getStack(i);
                    if (it.isOf(held.getItem())) n += it.getCount();
                }
                return String.valueOf(n);
            }
        },
        new PotionModule(),
        new Feature("togglesneak", "Agacharse alternado", "Pulsas una vez para agacharte y otra para levantarte. Al apagarlo vuelve a como estaba.", "Combate", false),
        new Feature("freelook", "Freelook", "Mantén la tecla (Controles → RzClient) y mira alrededor sin girar tu personaje.", "Visual", false),
        new Feature("nohurt", "Sin sacudida al recibir daño", "La cámara no se inclina cuando te pegan.", "Visual", false),
        new Feature("lowfire", "Fuego bajo", "El fuego en pantalla baja para que no tape la vista cuando te quemas.", "Visual", false)
            .extra("y", "Cuánto baja", 0f, 0.6f, 0.3f, "%.2f"),
        new Feature("nobob", "Sin bamboleo", "Quita el balanceo de la cámara al caminar.", "Visual", false),
        new SoupModule(),
        new TextModule("combo", "Combo", "Golpes seguidos sin recibir daño.", "Combate", 96, 104, false) {
            String label() { return "COMBO"; }
            String value(MinecraftClient mc) { return String.valueOf(Features.combo); }
        },
        new TextModule("day", "Día", "El día del mundo y la hora del juego.", "Info", 96, 124, false) {
            String label() { return "DÍA"; }
            String value(MinecraftClient mc) {
                if (mc.world == null) return "-";
                long t = mc.world.getTimeOfDay();
                long tod = ((t % 24000L) + 24000L) % 24000L;
                int hh = (int) ((tod / 1000L + 6L) % 24L), mm = (int) ((tod % 1000L) * 60L / 1000L);
                return (t / 24000L + 1L) + " · " + String.format(Locale.ROOT, "%02d:%02d", hh, mm);
            }
        },
        new Feature("crosshair", "Crosshair", "Tu propia mira: cruz, punto, círculo o cuadro, con color y tamaño.", "Visual", false)
            .colorable()
            .extra("style", "Estilo", 0f, 5f, 0f, "%.0f").names(Crosshair.STYLES)
            .extra("size", "Largo", 1f, 14f, 5f, "%.0f px")
            .extra("gap", "Hueco", 0f, 10f, 3f, "%.0f px")
            .extra("thick", "Grosor", 1f, 4f, 1f, "%.0f px"),
        new Feature("hitbox", "Hitbox", "Muestra las cajas de colisión. El que apuntas se ve en rojo.", "Combate", false)
            .colorable()
            .extra("mode", "Qué mostrar", 0f, 2f, 0f, "%.0f").names(Hitboxes.MODES)
            .extra("range", "Distancia", 8f, 64f, 32f, "%.0f bloques"),
        new Feature("autosprint", "Sprint automático", "Corres solo al avanzar, sin mantener la tecla.", "Combate", false),
        new Feature("zoom", "Zoom", "Mantén la tecla de zoom para acercar.", "Visual", true).value("Nivel de zoom", 1.5f, 10f, 4f),
        new Feature("fullbright", "Fullbright", "Ve claro en cuevas y de noche.", "Visual", false),
        new Feature("autotext", "Autotext", "Manda mensajes guardados con una tecla. Asigna las teclas en Controles → RzClient.", "Combate", false)
            .text("autotext1", "Mensaje 1").text("autotext2", "Mensaje 2").text("autotext3", "Mensaje 3").text("autotext4", "Mensaje 4"),
        new Feature("autogg", "Auto GG", "Escribe un mensaje solo cuando el servidor anuncia el final de la partida.", "Combate", false)
            .extra("delay", "Espera", 0f, 5f, 1f, "%.1f s")
            .text("autogg", "Mensaje").text("autogg_trig", "Frases del final (separadas por coma)"),
        new Feature("viewmodel", "Viewmodel", "Mueve y achica tu mano y el objeto en primera persona.", "Visual", false)
            .extra("x", "Posición horizontal", -1f, 1f, 0f, "%+.2f")
            .extra("y", "Altura", -1f, 1f, 0f, "%+.2f")
            .extra("z", "Profundidad", -1.5f, 0.5f, 0f, "%+.2f")
            .extra("s", "Tamaño", 0.3f, 1.5f, 1f, "%.2fx")
    );

    public static HudModule byId(String id) {
        for (HudModule m : MODULES) if (m.id.equals(id)) return m;
        return null;
    }

    // Referencias directas a los módulos que se consultan en cada fotograma o tick.
    public static final HudModule ZOOM = byId("zoom"), FULLBRIGHT = byId("fullbright"), VIEWMODEL = byId("viewmodel"),
        CROSSHAIR = byId("crosshair"), HITBOX = byId("hitbox"), AUTOSPRINT = byId("autosprint"),
        CPS = byId("cps"), KEYS = byId("keys"), COMBO = byId("combo"),
        AUTOTEXT = byId("autotext"), AUTOGG = byId("autogg"), SOUP = byId("soup"),
        TOGGLESNEAK = byId("togglesneak"), FREELOOK = byId("freelook"), NOHURT = byId("nohurt"),
        LOWFIRE = byId("lowfire"), NOBOB = byId("nobob");

    // ---------- Render ----------
    private static long lastNs = System.nanoTime();

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if ((CPS != null && CPS.cfg.enabled) || (KEYS != null && KEYS.cfg.enabled)) Cps.poll(mc);
        long n = System.nanoTime();
        float dt = Math.min(0.1f, (n - lastNs) / 1e9f);
        lastNs = n;
        Features.frame(mc, dt);
        if (mc.options.hudHidden) {
            Welcome.render(ctx, mc);
            return;
        }
        // Con F3 abierto no se dibuja el HUD de Ametra para que no tape ni se mezcle con la pantalla de depuración.
        if (AmetraConfig.hideOnF3 && mc.inGameHud.getDebugHud().shouldShowDebugHud()) {
            Welcome.render(ctx, mc);
            return;
        }
        Crosshair.render(ctx, mc);
        if (SOUP != null && SOUP.cfg.enabled) SoupModule.overlay(ctx, mc, SOUP);
        for (HudModule m : MODULES) {
            if (!m.hud) continue;
            float target = m.cfg.enabled ? 1f : 0f;
            m.vis = AmetraConfig.animations ? m.vis + (target - m.vis) * Math.min(1f, dt * 10f) : target;
            if (Math.abs(target - m.vis) < 0.01f) m.vis = target;
            drawModule(ctx, mc, m);
        }
        Welcome.render(ctx, mc);
    }

    private static void drawModule(DrawContext ctx, MinecraftClient mc, HudModule m) {
        float a = AmetraConfig.animations ? Colors.ease(m.vis) : m.vis;
        if (a < 0.05f) return;
        int color = m.cfg.chroma ? Colors.hsv((System.currentTimeMillis() % 4000L) / 4000f, 0.6f, 1f) : m.cfg.color;
        float slide = AmetraConfig.animations ? (1f - a) * -10f : 0f;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(m.cfg.x + slide, m.cfg.y, 0f);
        ms.scale(m.cfg.scale, m.cfg.scale, 1f);
        m.draw(ctx, mc, a, color);
        ms.pop();
    }
}
