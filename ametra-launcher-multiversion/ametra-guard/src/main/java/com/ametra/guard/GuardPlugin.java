package com.ametra.guard;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Exige el cliente Ametra para entrar, comprueba su integridad y los mods cargados, y analiza el comportamiento
 * de combate con un detector de anomalías + modelo opcional. Por defecto solo avisa al staff (no banea).
 */
public final class GuardPlugin extends JavaPlugin implements Listener, PluginMessageListener, TabExecutor {

    static final class Session {
        final UUID id;
        final String name;
        byte[] nonce, sessionKey;
        boolean verified;
        long deadline, lastChallenge, lastSeq = -1, lastTele, lastAlert;
        int challenges, strikes, windows;
        Features.ServerSide acc = new Features.ServerSide();
        UUID lastTarget;
        double vl, lastP;
        String lastReasons = "-";
        final ArrayDeque<double[]> recent = new ArrayDeque<>();

        Session(Player p) {
            id = p.getUniqueId();
            name = p.getName();
        }
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();
    private final SecureRandom rng = new SecureRandom();
    private final Detector detector = new Detector();
    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newHttpClient();

    // ajustes
    private byte[] secret;
    private int timeoutSec, minBaseline, vlAlert, vlAction;
    private double aiThreshold;
    private boolean aiEnabled, whitelistMode;
    private String kickMsg, exemptPerm, agentPolicy, aiAction, webhookUrl;
    private List<String> exemptPrefixes, probes, agentsOk;
    private Set<String> okHashes, blockedIds, blockedWords, allowedIds, allowedPrefixes;

    // ---------------------------------------------------------------- ciclo de vida
    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        loadBaseline();
        loadModel();
        getServer().getMessenger().registerOutgoingPluginChannel(this, Protocol.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("guard") != null) getCommand("guard").setExecutor(this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, this::saveBaseline, 6000L, 6000L);
        for (Player p : getServer().getOnlinePlayers()) begin(p);
    }

    @Override
    public void onDisable() {
        saveBaseline();
    }

    private Set<String> lower(List<String> l) {
        Set<String> s = new HashSet<>();
        for (String x : l) s.add(x.toLowerCase(Locale.ROOT));
        return s;
    }

    private void loadSettings() {
        reloadConfig();
        String hex = getConfig().getString("secret", "");
        if (hex == null || hex.isBlank()) {
            byte[] b = new byte[32];
            rng.nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            hex = sb.toString();
            getConfig().set("secret", hex);
            saveConfig();
            getLogger().warning("Se generó un secreto nuevo en config.yml. Pon EL MISMO valor en el cliente (guard_secret) al compilarlo.");
        }
        secret = new byte[hex.length() / 2];
        for (int i = 0; i < secret.length; i++) secret[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);

        timeoutSec = getConfig().getInt("verify-timeout-seconds", 10);
        kickMsg = getConfig().getString("kick-message", "Necesitas el cliente oficial para entrar.");
        exemptPerm = getConfig().getString("exempt-permission", "ametraguard.bypass");
        exemptPrefixes = getConfig().getStringList("exempt-name-prefixes");
        okHashes = lower(getConfig().getStringList("allowed-jar-hashes"));
        whitelistMode = "whitelist".equalsIgnoreCase(getConfig().getString("mod-policy", "blocklist"));
        blockedIds = lower(getConfig().getStringList("blocked-mod-ids"));
        blockedWords = lower(getConfig().getStringList("blocked-mod-keywords"));
        allowedIds = lower(getConfig().getStringList("allowed-mod-ids"));
        allowedPrefixes = lower(getConfig().getStringList("allowed-mod-id-prefixes"));
        probes = getConfig().getStringList("probe-classes");
        agentPolicy = getConfig().getString("javaagent-policy", "alert").toLowerCase(Locale.ROOT);
        agentsOk = getConfig().getStringList("allowed-agents");
        webhookUrl = getConfig().getString("discord-webhook", "");
        aiEnabled = getConfig().getBoolean("ai.enabled", true);
        aiThreshold = getConfig().getDouble("ai.alert-threshold", 0.80);
        vlAlert = getConfig().getInt("ai.vl-alert", 4);
        vlAction = getConfig().getInt("ai.vl-action", 12);
        aiAction = getConfig().getString("ai.action", "none").toLowerCase(Locale.ROOT);
        minBaseline = getConfig().getInt("ai.min-windows-baseline", 150);
        detector.minBaseline = minBaseline;
    }

    // ---------------------------------------------------------------- modelo y línea base
    private void loadModel() {
        File f = new File(getDataFolder(), "model.json");
        if (!f.exists()) { detector.setModel(null, null, null, 0); return; }
        try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            detector.setModel(arr(o, "mean"), arr(o, "std"), arr(o, "w"), o.get("b").getAsDouble());
            getLogger().info("Modelo supervisado cargado: " + (detector.hasModel() ? "ok" : "formato inválido"));
        } catch (Exception e) {
            getLogger().warning("No se pudo leer model.json: " + e.getMessage());
            detector.setModel(null, null, null, 0);
        }
    }

    private static double[] arr(JsonObject o, String k) {
        JsonArray a = o.getAsJsonArray(k);
        double[] d = new double[a.size()];
        for (int i = 0; i < d.length; i++) d[i] = a.get(i).getAsDouble();
        return d;
    }

    private void loadBaseline() {
        File f = new File(getDataFolder(), "baseline.json");
        if (!f.exists()) return;
        try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            double[] m = arr(o, "mean"), v = arr(o, "var"), c = arr(o, "cnt");
            if (m.length == Features.N && v.length == Features.N && c.length == Features.N) {
                for (int i = 0; i < Features.N; i++) {
                    detector.mean[i] = m[i];
                    detector.var[i] = v[i];
                    detector.cnt[i] = (long) c[i];
                }
            }
        } catch (Exception e) {
            getLogger().warning("No se pudo leer baseline.json: " + e.getMessage());
        }
    }

    private void saveBaseline() {
        try {
            getDataFolder().mkdirs();
            JsonObject o = new JsonObject();
            o.add("mean", gson.toJsonTree(detector.mean));
            o.add("var", gson.toJsonTree(detector.var));
            double[] c = new double[Features.N];
            for (int i = 0; i < c.length; i++) c[i] = detector.cnt[i];
            o.add("cnt", gson.toJsonTree(c));
            Files.writeString(new File(getDataFolder(), "baseline.json").toPath(), gson.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().warning("No se pudo guardar baseline.json: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- entrada de jugadores
    private boolean exempt(Player p) {
        if (p.hasPermission(exemptPerm)) return true;
        for (String pre : exemptPrefixes) if (!pre.isEmpty() && p.getName().startsWith(pre)) return true;
        return false;
    }

    private void begin(Player p) {
        if (exempt(p)) return;
        Session s = new Session(p);
        s.deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        sessions.put(s.id, s);
        if (p.getListeningPluginChannels().contains(Protocol.CHANNEL)) sendChallenge(p, s);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        begin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sessions.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRegister(PlayerRegisterChannelEvent e) {
        if (!Protocol.CHANNEL.equals(e.getChannel())) return;
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null && !s.verified && s.challenges == 0) sendChallenge(e.getPlayer(), s);
    }

    private void sendChallenge(Player p, Session s) {
        if (s.nonce == null) {
            s.nonce = new byte[16];
            rng.nextBytes(s.nonce);
            s.sessionKey = Protocol.sessionKey(secret, s.nonce);
        }
        s.lastChallenge = System.currentTimeMillis();
        s.challenges++;
        p.sendPluginMessage(this, Protocol.CHANNEL, Protocol.challenge(s.nonce, probes));
    }

    /** Cada segundo: reenvía el reto si hace falta y expulsa a quien no se verificó a tiempo. */
    private void tick() {
        long now = System.currentTimeMillis();
        for (Session s : sessions.values()) {
            Player p = getServer().getPlayer(s.id);
            if (p == null || s.verified) continue;
            if (s.challenges > 0 && s.challenges < 4 && now - s.lastChallenge > 3000
                && p.getListeningPluginChannels().contains(Protocol.CHANNEL)) {
                sendChallenge(p, s);
            }
            if (now > s.deadline) {
                getLogger().info(p.getName() + " expulsado: no respondió al cliente oficial.");
                p.kick(Component.text(kickMsg));
            }
        }
    }

    // ---------------------------------------------------------------- mensajes del cliente
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] msg) {
        if (!Protocol.CHANNEL.equals(channel) || msg.length == 0) return;
        Session s = sessions.get(player.getUniqueId());
        if (s == null) return;
        if (msg[0] == Protocol.T_HELLO) hello(player, s, msg);
        else if (msg[0] == Protocol.T_TELEMETRY) telemetry(player, s, msg);
    }

    private void reject(Player p, String detail) {
        alert(p.getName() + " rechazado: " + detail);
        p.kick(Component.text(kickMsg));
    }

    private void hello(Player p, Session s, byte[] msg) {
        if (s.verified || s.nonce == null) return;
        Protocol.Hello h;
        try {
            h = Protocol.parseHello(msg, secret);
        } catch (Protocol.Bad e) {
            reject(p, "respuesta inválida (" + e.getMessage() + ")");
            return;
        }
        if (!java.security.MessageDigest.isEqual(h.nonce, s.nonce)) { reject(p, "reto distinto"); return; }
        if (h.protocol != Protocol.VERSION) { reject(p, "protocolo " + h.protocol); return; }

        List<String> problems = new ArrayList<>();
        if (!okHashes.isEmpty()) {
            if (!okHashes.contains(h.jarHash)) problems.add("jar no autorizado " + h.jarHash.substring(0, 12));
        } else {
            getLogger().info(p.getName() + " cliente sha256=" + h.jarHash + " (agrégalo a allowed-jar-hashes para exigir esta versión)");
        }
        for (Protocol.Mod m : h.mods) {
            String id = m.id.toLowerCase(Locale.ROOT);
            if (blockedIds.contains(id)) { problems.add("mod bloqueado: " + id); continue; }
            boolean word = false;
            for (String w : blockedWords) if (!w.isEmpty() && id.contains(w)) { problems.add("mod sospechoso: " + id); word = true; break; }
            if (word) continue;
            if (whitelistMode && !allowedIds.contains(id)) {
                boolean pre = false;
                for (String a : allowedPrefixes) if (id.startsWith(a)) { pre = true; break; }
                if (!pre) problems.add("mod no permitido: " + id);
            }
        }
        for (String c : h.classHits) problems.add("clase detectada: " + c);
        List<String> agents = new ArrayList<>();
        for (String a : h.agents) if (!agentsOk.contains(a)) agents.add(a);
        if (!agents.isEmpty()) {
            if (agentPolicy.equals("kick")) problems.add("javaagent: " + String.join(",", agents));
            else if (agentPolicy.equals("alert")) alert(p.getName() + " usa javaagent: " + String.join(",", agents));
        }

        if (!problems.isEmpty()) {
            reject(p, String.join("; ", problems));
            return;
        }
        s.verified = true;
        getLogger().info(p.getName() + " verificado (" + h.mods.size() + " mods).");
    }

    private void telemetry(Player p, Session s, byte[] msg) {
        if (!s.verified || !aiEnabled) return;
        long now = System.currentTimeMillis();
        if (now - s.lastTele < 800) return;
        Protocol.Telemetry t;
        try {
            t = Protocol.parseTelemetry(msg, s.sessionKey);
        } catch (Protocol.Bad e) {
            if (++s.strikes > 5) reject(p, "telemetría inválida");
            return;
        }
        if (t.seq <= s.lastSeq) {
            if (++s.strikes > 5) reject(p, "telemetría repetida");
            return;
        }
        s.lastSeq = t.seq;
        s.lastTele = now;

        Features.ServerSide acc = s.acc;
        s.acc = new Features.ServerSide();
        Features.Computed c = Features.compute(t, acc);
        if (c == null) return;

        Detector.Verdict v = detector.score(c);
        s.windows++;
        s.lastP = v.p;
        s.lastReasons = v.reasons.isEmpty() ? "-" : String.join("; ", v.reasons);
        s.recent.addLast(c.f.clone());
        if (s.recent.size() > 30) s.recent.removeFirst();

        if (v.p >= aiThreshold) {
            s.vl += 1.0 + (v.p - aiThreshold) * 10;
        } else {
            s.vl = Math.max(0, s.vl - 0.5);
            if (s.vl == 0 && v.p < 0.5) detector.learn(c.f);
        }
        if (s.vl >= vlAlert && now - s.lastAlert > 60_000) {
            s.lastAlert = now;
            alert(String.format(Locale.ROOT, "%s sospechoso: p=%.2f VL=%.1f %s", p.getName(), v.p, s.vl, s.lastReasons));
        }
        if (s.vl >= vlAction && aiAction.equals("kick")) {
            alert(p.getName() + " expulsado por el análisis (VL " + String.format(Locale.ROOT, "%.1f", s.vl) + ")");
            p.kick(Component.text(kickMsg));
        }
    }

    // ---------------------------------------------------------------- lo que ve el servidor
    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null && s.verified) s.acc.swings++;
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || e.getCause() != DamageCause.ENTITY_ATTACK) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null || !s.verified) return;
        Entity target = e.getEntity();
        org.bukkit.Location eye = p.getEyeLocation();
        BoundingBox b = target.getBoundingBox();
        double dx = Math.max(Math.max(b.getMinX() - eye.getX(), 0.0), eye.getX() - b.getMaxX());
        double dy = Math.max(Math.max(b.getMinY() - eye.getY(), 0.0), eye.getY() - b.getMaxY());
        double dz = Math.max(Math.max(b.getMinZ() - eye.getZ(), 0.0), eye.getZ() - b.getMaxZ());
        double reach = Math.sqrt(dx * dx + dy * dy + dz * dz);

        Vector to = b.getCenter().subtract(eye.toVector());
        double len = to.length(), ang = 0;
        if (len > 1e-6) {
            double dot = eye.getDirection().dot(to) / len;
            ang = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        }
        Features.ServerSide a = s.acc;
        a.hits++;
        a.reachSum += reach;
        a.reachMax = Math.max(a.reachMax, reach);
        a.aimSum += ang;
        a.aimSq += ang * ang;
        if (s.lastTarget != null && !s.lastTarget.equals(target.getUniqueId())) a.switches++;
        s.lastTarget = target.getUniqueId();
    }

    // ---------------------------------------------------------------- avisos
    private void alert(String msg) {
        String line = "[Guard] " + msg;
        getLogger().warning(line);
        for (Player p : getServer().getOnlinePlayers()) {
            if (p.hasPermission("ametraguard.alerts") && !muted.contains(p.getUniqueId())) {
                p.sendMessage(Component.text(line, NamedTextColor.RED));
            }
        }
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            try {
                JsonObject o = new JsonObject();
                o.addProperty("content", line);
                HttpRequest rq = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(o))).build();
                http.sendAsync(rq, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // el aviso por webhook es opcional
            }
        }
    }

    // ---------------------------------------------------------------- comandos
    @Override
    public boolean onCommand(CommandSender who, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            who.sendMessage("/guard status <jugador> | label <jugador> <cheat|legit> | alerts | reload");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                loadSettings();
                loadModel();
                who.sendMessage("Configuración y modelo recargados.");
            }
            case "alerts" -> {
                if (who instanceof Player p) {
                    boolean off = muted.add(p.getUniqueId());
                    if (!off) muted.remove(p.getUniqueId());
                    who.sendMessage("Avisos " + (off ? "desactivados" : "activados") + ".");
                }
            }
            case "status" -> {
                Session s = a.length > 1 ? byName(a[1]) : null;
                if (s == null) { who.sendMessage("Jugador no encontrado o exento."); break; }
                who.sendMessage(String.format(Locale.ROOT, "%s verificado=%s ventanas=%d p=%.2f VL=%.1f razones=%s",
                    s.name, s.verified, s.windows, s.lastP, s.vl, s.lastReasons));
                who.sendMessage("línea base: " + detector.cnt[Features.CPS] + " ventanas, modelo=" + detector.hasModel());
            }
            case "label" -> label(who, a);
            default -> who.sendMessage("Subcomando desconocido.");
        }
        return true;
    }

    private Session byName(String n) {
        Player p = Bukkit.getPlayerExact(n);
        return p == null ? null : sessions.get(p.getUniqueId());
    }

    /** Guarda las últimas ventanas del jugador como ejemplo etiquetado para entrenar el modelo. */
    private void label(CommandSender who, String[] a) {
        if (a.length < 3 || !(a[2].equalsIgnoreCase("cheat") || a[2].equalsIgnoreCase("legit"))) {
            who.sendMessage("Uso: /guard label <jugador> <cheat|legit>");
            return;
        }
        Session s = byName(a[1]);
        if (s == null || s.recent.isEmpty()) { who.sendMessage("Sin datos recientes de ese jugador."); return; }
        String lab = a[2].equalsIgnoreCase("cheat") ? "1" : "0";
        File f = new File(getDataFolder(), "dataset.csv");
        try {
            getDataFolder().mkdirs();
            StringBuilder sb = new StringBuilder();
            if (!f.exists()) sb.append("label,").append(String.join(",", Features.NAMES)).append('\n');
            for (double[] row : s.recent) {
                sb.append(lab);
                for (double x : row) sb.append(',').append(Double.isNaN(x) ? "" : String.format(Locale.ROOT, "%.5f", x));
                sb.append('\n');
            }
            Files.writeString(f.toPath(), sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            who.sendMessage("Guardadas " + s.recent.size() + " ventanas como " + a[2] + " en dataset.csv");
        } catch (IOException e) {
            who.sendMessage("No se pudo escribir dataset.csv: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender who, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : List.of("status", "label", "alerts", "reload")) if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("status") || a[0].equalsIgnoreCase("label"))) {
            for (Player p : getServer().getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("label")) {
            out.add("cheat");
            out.add("legit");
        }
        return out;
    }
}
