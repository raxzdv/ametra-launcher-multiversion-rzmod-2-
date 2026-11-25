package com.ametra.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.ametra.net.Wire;

/** Prueba de la lógica pura: protocolo cliente<->servidor y detector con jugadores simulados. */
public final class SelfTest {
    static int fails = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  FALLA ") + what);
        if (!ok) fails++;
    }

    // ------------------------------------------------------------ simulación
    static final Random R = new Random(42);

    static double gauss(double m, double s) { return m + s * R.nextGaussian(); }

    static Protocol.Telemetry window(double[] ivMs, boolean mouseLinked, double rotRate, double rotSize) {
        Protocol.Telemetry t = new Protocol.Telemetry();
        t.n = 100;
        t.yaw = new float[100]; t.pitch = new float[100];
        t.mdx = new short[100]; t.mdy = new short[100]; t.flags = new byte[100];
        for (int i = 0; i < 100; i++) {
            t.flags[i] = 1;
            if (R.nextDouble() < rotRate) {
                double dx = gauss(0, rotSize);
                if (Math.abs(dx) < 1) dx = dx < 0 ? -1 : 1;
                t.yaw[i] = (float) (dx * 0.15 + gauss(0, 0.01));
                t.pitch[i] = (float) (gauss(0, rotSize * 0.2) * 0.15);
                if (mouseLinked) { t.mdx[i] = (short) Math.round(dx); t.mdy[i] = (short) Math.round(t.pitch[i] / 0.15); }
            }
        }
        t.nClicks = ivMs.length;
        t.clickDtMicros = new int[ivMs.length];
        for (int i = 0; i < ivMs.length; i++) t.clickDtMicros[i] = (int) Math.round(ivMs[i] * 1000);
        return t;
    }

    static double[] intervals(double cps, double cv) {
        int n = (int) (cps * 5);
        double mean = 1000.0 / cps;
        double sigma = Math.sqrt(Math.log(1 + cv * cv)), mu = Math.log(mean) - sigma * sigma / 2;
        double[] iv = new double[n];
        for (int i = 0; i < n; i++) iv[i] = Math.exp(mu + sigma * R.nextGaussian());
        return iv;
    }

    static Features.ServerSide server(int swings, double acc, double reachMean, double reachSd, double aimMean, double aimSd) {
        Features.ServerSide s = new Features.ServerSide();
        s.swings = swings;
        s.hits = (int) Math.round(swings * acc);
        for (int i = 0; i < s.hits; i++) {
            double r = Math.max(0.5, gauss(reachMean, reachSd));
            double a = Math.abs(gauss(aimMean, aimSd));
            s.reachSum += r; s.reachMax = Math.max(s.reachMax, r);
            s.aimSum += a; s.aimSq += a * a;
            if (R.nextDouble() < 0.15) s.switches++;
        }
        return s;
    }

    static Detector.Verdict run(Detector d, Protocol.Telemetry t, Features.ServerSide s) {
        Features.Computed c = Features.compute(t, s);
        if (c == null) return null;
        return d.score(c);
    }

    public static void main(String[] a) throws Exception {
        byte[] secret = new byte[32];
        R.nextBytes(secret);

        System.out.println("== Protocolo ==");
        byte[] nonce = new byte[16];
        R.nextBytes(nonce);
        byte[] chal = Protocol.challenge(nonce, List.of("net.wurstclient.WurstClient", "a.B"));
        Wire.Challenge pc = Wire.parseChallenge(chal);
        check(java.util.Arrays.equals(pc.nonce, nonce) && pc.probes.size() == 2 && pc.probes.get(0).equals("net.wurstclient.WurstClient"), "reto: el cliente lo lee igual que lo escribió el servidor");

        byte[] jar = new byte[32];
        R.nextBytes(jar);
        List<Wire.ModInfo> mods = new ArrayList<>();
        mods.add(new Wire.ModInfo("ametra", "1.6.1", new byte[16]));
        mods.add(new Wire.ModInfo("sodium", "0.6.0", new byte[16]));
        byte[] hello = Wire.hello(secret, nonce, jar, 1, List.of("authlib-injector.jar"), mods, List.of("x.Y"));
        Protocol.Hello h = Protocol.parseHello(hello, secret);
        check(h.mods.size() == 2 && h.mods.get(1).id.equals("sodium") && h.classHits.get(0).equals("x.Y")
            && h.agents.get(0).equals("authlib-injector.jar") && h.flags == 1 && h.protocol == 1, "hello: el servidor lee mods, clases y agentes");
        check(java.util.Arrays.equals(h.nonce, nonce), "hello: nonce correcto");

        byte[] bad = hello.clone();
        bad[20] ^= 1;
        boolean rejected = false;
        try { Protocol.parseHello(bad, secret); } catch (Protocol.Bad e) { rejected = true; }
        check(rejected, "hello alterado -> rechazado");
        rejected = false;
        try { Protocol.parseHello(hello, new byte[32]); } catch (Protocol.Bad e) { rejected = true; }
        check(rejected, "hello firmado con otro secreto -> rechazado");

        byte[] sk = Protocol.sessionKey(secret, nonce);
        check(java.util.Arrays.equals(sk, Wire.sessionKey(secret, nonce)), "clave de sesión idéntica en cliente y servidor");
        Protocol.Telemetry w = window(intervals(7, 0.3), true, 0.6, 20);
        byte[] tel = Wire.telemetry(sk, 5, w.n, w.yaw, w.pitch, w.mdx, w.mdy, w.flags, w.nClicks, w.clickDtMicros);
        Protocol.Telemetry pt = Protocol.parseTelemetry(tel, sk);
        check(pt.seq == 5 && pt.n == 100 && pt.nClicks == w.nClicks && pt.yaw[10] == w.yaw[10] && pt.mdx[10] == w.mdx[10], "telemetría ida y vuelta");
        byte[] tbad = tel.clone();
        tbad[30] ^= 1;
        rejected = false;
        try { Protocol.parseTelemetry(tbad, sk); } catch (Protocol.Bad e) { rejected = true; }
        check(rejected, "telemetría alterada -> rechazada");
        rejected = false;
        try { Protocol.parseTelemetry(tel, Protocol.sessionKey(secret, new byte[16])); } catch (Protocol.Bad e) { rejected = true; }
        check(rejected, "telemetría de otra sesión -> rechazada");

        System.out.println("== Detector: jugadores legítimos (línea base se aprende sola) ==");
        Detector d = new Detector();
        int total = 0, flagged = 0;
        double maxP = 0;
        for (int i = 0; i < 1500; i++) {
            double cps = 3 + R.nextDouble() * 8.5;
            double cv = 0.2 + R.nextDouble() * 0.3;
            double rate = 0.4 + R.nextDouble() * 0.5;
            Protocol.Telemetry t = window(intervals(cps, cv), true, rate, 8 + R.nextDouble() * 30);
            Features.ServerSide s = server(t.nClicks, 0.3 + R.nextDouble() * 0.4, 2.3, 0.3, 10, 7);
            Detector.Verdict v = run(d, t, s);
            if (v == null) continue;
            total++;
            maxP = Math.max(maxP, v.p);
            if (v.p >= 0.8) { flagged++; if (flagged <= 3) System.out.println("     falso positivo: p=" + v.p + " " + v.reasons); }
            else if (v.p < 0.5) d.learn(Features.compute(t, s).f);
        }
        System.out.printf("  ventanas legítimas: %d, marcadas (p>=0.8): %d, p máximo: %.2f%n", total, flagged, maxP);
        check(flagged <= total * 0.005, "falsos positivos <= 0.5%");

        System.out.println("== Detector: trampas simuladas ==");
        // autoclicker de intervalo fijo, 15 cps
        double[] fixed = new double[75];
        for (int i = 0; i < fixed.length; i++) fixed[i] = gauss(66.7, 0.3);
        Detector.Verdict v = run(d, window(fixed, true, 0.6, 15), server(75, 0.6, 2.4, 0.3, 10, 7));
        check(v != null && v.p >= 0.8, "autoclicker de intervalo fijo -> p=" + (v == null ? "-" : String.format("%.2f", v.p)) + " " + (v == null ? "" : v.reasons));

        // aimbot: la cámara gira sin movimiento de ratón y apunta casi perfecto
        v = run(d, window(intervals(8, 0.3), false, 0.8, 25), server(40, 0.9, 2.5, 0.2, 1.0, 0.4));
        check(v != null && v.p >= 0.8, "aimbot (giro sin ratón + puntería perfecta) -> p=" + (v == null ? "-" : String.format("%.2f", v.p)) + " " + (v == null ? "" : v.reasons));

        // reach
        v = run(d, window(intervals(7, 0.3), true, 0.6, 15), server(35, 0.6, 4.8, 0.2, 10, 6));
        check(v != null && v.p >= 0.8, "reach ~4.8 -> p=" + (v == null ? "-" : String.format("%.2f", v.p)) + " " + (v == null ? "" : v.reasons));

        // killaura clásico: gira sin ratón, apunta bien, cambia de objetivo muy rápido
        Features.ServerSide ka = server(60, 0.95, 3.0, 0.15, 2.0, 0.8);
        ka.switches = 40;
        v = run(d, window(intervals(11, 0.25), false, 0.9, 30), ka);
        check(v != null && v.p >= 0.8, "killaura -> p=" + (v == null ? "-" : String.format("%.2f", v.p)) + " " + (v == null ? "" : v.reasons));

        // clicker con aleatoriedad "humana": se espera que NO lo vea por intervalos (límite conocido)
        v = run(d, window(intervals(9, 0.3), true, 0.6, 15), server(45, 0.5, 2.4, 0.3, 10, 7));
        System.out.printf("  (informativo) clicker aleatorizado: p=%.2f -> los clickers que imitan a un humano son el límite del método%n", v == null ? 0 : v.p);

        System.out.println("== Modelo supervisado ==");
        Detector d2 = new Detector();
        double[] mean = Detector.PRIOR_MEAN.clone(), std = Detector.PRIOR_STD.clone(), wts = new double[Features.N];
        wts[Features.NO_MOUSE] = 3.0;
        d2.setModel(mean, std, wts, -2.0);
        Features.Computed cc = Features.compute(window(intervals(7, 0.3), false, 0.7, 20), server(30, 0.5, 2.4, 0.3, 10, 7));
        Detector.Verdict vm = d2.score(cc);
        check(d2.hasModel() && !Double.isNaN(vm.pModel) && vm.pModel > 0.5, "el modelo cargado se usa (pModel=" + String.format("%.2f", vm.pModel) + ")");

        System.out.println(fails == 0 ? "\nTODO OK" : "\nFALLAS: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
