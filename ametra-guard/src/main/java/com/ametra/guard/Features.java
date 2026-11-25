package com.ametra.guard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Convierte una ventana de telemetría + eventos del servidor en un vector de características. */
final class Features {
    private Features() {}

    static final int N = 17;
    static final String[] NAMES = {
        "cps", "click_mean_ms", "click_cv", "click_repeat", "click_mode_share",
        "yaw_abs_mean", "yaw_std", "snap_per_s", "rot_without_mouse", "mouse_yaw_corr",
        "hits", "accuracy", "reach_max", "reach_mean", "target_switch_per_s",
        "aim_err_mean", "aim_err_std"
    };
    static final int CPS = 0, CLICK_MEAN = 1, CV = 2, REPEAT = 3, MODE = 4, YAW_ABS = 5, YAW_STD = 6, SNAP = 7,
        NO_MOUSE = 8, CORR = 9, HITS = 10, ACC = 11, REACH_MAX = 12, REACH_MEAN = 13, SWITCH = 14, AIM_MEAN = 15, AIM_STD = 16;

    /** Eventos que el servidor ve por su cuenta durante la ventana. */
    static final class ServerSide {
        int hits, swings, switches;
        double reachMax, reachSum, aimSum, aimSq;
    }

    static final class Computed {
        final double[] f = new double[N];
        int intervals, rotTicks, hits;
    }

    private static double mean(List<Double> v) {
        double s = 0;
        for (double x : v) s += x;
        return s / v.size();
    }

    private static double std(List<Double> v, double m) {
        double s = 0;
        for (double x : v) s += (x - m) * (x - m);
        return Math.sqrt(s / v.size());
    }

    /** null = ventana sin actividad suficiente para puntuar. */
    static Computed compute(Protocol.Telemetry t, ServerSide s) {
        Computed c = new Computed();
        Arrays.fill(c.f, Double.NaN);
        double secs = Math.max(1.0, t.n / 20.0);

        // ----- clics -----
        List<Double> iv = new ArrayList<>();
        for (int i = 0; i < t.nClicks; i++) {
            double ms = t.clickDtMicros[i] / 1000.0;
            if (ms > 0 && ms <= 600) iv.add(ms);
        }
        c.f[CPS] = t.nClicks / secs;
        c.intervals = iv.size();
        if (iv.size() >= 6) {
            double m = mean(iv), sd = std(iv, m);
            c.f[CLICK_MEAN] = m;
            c.f[CV] = m > 0 ? sd / m : Double.NaN;
            int rep = 0;
            for (int i = 1; i < iv.size(); i++) if (Math.abs(iv.get(i) - iv.get(i - 1)) < 2.0) rep++;
            c.f[REPEAT] = rep / (double) (iv.size() - 1);
            List<Double> sorted = new ArrayList<>(iv);
            sorted.sort(null);
            double med = sorted.get(sorted.size() / 2);
            int near = 0;
            for (double x : iv) if (Math.abs(x - med) <= med * 0.03) near++;
            c.f[MODE] = near / (double) iv.size();
        }

        // ----- rotación -----
        List<Integer> valid = new ArrayList<>();
        for (int i = 0; i < t.n; i++) if ((t.flags[i] & 1) != 0) valid.add(i);
        if (valid.size() >= 20) {
            List<Double> ya = new ArrayList<>();
            for (int i : valid) ya.add((double) Math.abs(t.yaw[i]));
            double m = mean(ya);
            c.f[YAW_ABS] = m;
            c.f[YAW_STD] = std(ya, m);

            int snaps = 0;
            for (int k = 0; k + 1 < valid.size(); k++) {
                int i = valid.get(k), j = valid.get(k + 1);
                if (j == i + 1 && Math.abs(t.yaw[i]) > 20 && Math.abs(t.yaw[j]) < 1.5) snaps++;
            }
            c.f[SNAP] = snaps / (valid.size() / 20.0);

            int rot = 0, noMouse = 0;
            for (int i : valid) {
                if (Math.abs(t.yaw[i]) > 0.8 || Math.abs(t.pitch[i]) > 0.8) {
                    rot++;
                    if (t.mdx[i] == 0 && t.mdy[i] == 0) noMouse++;
                }
            }
            c.rotTicks = rot;
            if (rot >= 15) c.f[NO_MOUSE] = noMouse / (double) rot;

            // correlación ratón-yaw
            double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
            int n = 0;
            for (int i : valid) {
                if (t.mdx[i] != 0 || Math.abs(t.yaw[i]) > 0.01) {
                    double x = t.mdx[i], y = t.yaw[i];
                    sx += x; sy += y; sxx += x * x; syy += y * y; sxy += x * y;
                    n++;
                }
            }
            if (n >= 15) {
                double cov = sxy - sx * sy / n;
                double vx = sxx - sx * sx / n, vy = syy - sy * sy / n;
                if (vx > 1e-9 && vy > 1e-9) c.f[CORR] = cov / Math.sqrt(vx * vy);
            }
        }

        // ----- lo que ve el servidor -----
        c.hits = s.hits;
        c.f[HITS] = s.hits;
        if (s.swings > 0) c.f[ACC] = Math.min(1.0, s.hits / (double) s.swings);
        if (s.hits > 0) {
            c.f[REACH_MAX] = s.reachMax;
            c.f[REACH_MEAN] = s.reachSum / s.hits;
            c.f[SWITCH] = s.switches / secs;
        }
        if (s.hits >= 5) {
            double m = s.aimSum / s.hits;
            c.f[AIM_MEAN] = m;
            c.f[AIM_STD] = Math.sqrt(Math.max(0, s.aimSq / s.hits - m * m));
        }

        boolean active = t.nClicks >= 5 || s.hits >= 3 || c.rotTicks >= 15;
        return active ? c : null;
    }
}
