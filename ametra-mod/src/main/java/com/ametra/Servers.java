package com.ametra;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;

/**
 * Servidores del launcher dentro del juego. El launcher escribe  config/ametra.json -> "servers"
 * (partners de Ametra primero, luego los tuyos). Aquí se leen para la pestaña SERVIDORES del menú
 * (Shift derecho) y se añaden a la lista de Multijugador de Minecraft.
 */
public final class Servers {
    private Servers() {}

    public static final class Entry {
        public String name = "", host = "", desc = "";
        public boolean partner, main;
        public int color = 0x8A5BE0;
    }

    private static List<Entry> list = new ArrayList<>();
    private static long stamp = -1;

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("ametra.json"); }

    /** Lista actual (se relee sola si el launcher cambió el archivo). */
    public static List<Entry> list() {
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) { list = new ArrayList<>(); stamp = -1; return list; }
            long t = Files.getLastModifiedTime(f).toMillis();
            if (t == stamp) return list;
            stamp = t;
            List<Entry> out = new ArrayList<>();
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            JsonArray arr = root.has("servers") && root.get("servers").isJsonArray() ? root.getAsJsonArray("servers") : new JsonArray();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                Entry e = new Entry();
                e.name = str(o, "name"); e.host = str(o, "host"); e.desc = str(o, "desc");
                e.partner = o.has("partner") && o.get("partner").getAsBoolean();
                e.main = o.has("main") && o.get("main").getAsBoolean();
                String c = str(o, "color");
                if (c.matches("#[0-9a-fA-F]{6}")) e.color = Integer.parseInt(c.substring(1), 16);
                if (!e.host.isEmpty()) out.add(e);
                if (out.size() >= 40) break;
            }
            list = out;
        } catch (Throwable ignored) { /* archivo a medio escribir: se reintenta en el siguiente fotograma */ }
        return list;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    /** Entra al servidor (si estás en un mundo, sale de él primero). */
    public static void join(Entry e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Compat.joinServer(mc, e.name, e.host);
    }

    /** Añade a la lista de Multijugador los servidores que aún no estén (no toca ni borra los tuyos). */
    public static void syncToMultiplayer() {
        try {
            List<Entry> l = list();
            if (l.isEmpty()) return;
            ServerList sl = new ServerList(MinecraftClient.getInstance());
            sl.loadFile();
            boolean changed = false;
            for (Entry e : l) {
                boolean has = false;
                for (int i = 0; i < sl.size(); i++) if (sl.get(i).address.equalsIgnoreCase(e.host)) { has = true; break; }
                if (!has) { sl.add(Compat.newServerInfo(e.name, e.host), false); changed = true; }
            }
            if (changed) sl.saveFile();
        } catch (Throwable ignored) { /* es opcional */ }
    }
}
