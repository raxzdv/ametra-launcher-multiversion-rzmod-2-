package com.ametra.guard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Puntúa una ventana de comportamiento (0 = limpio, 1 = casi seguro trampa) combinando tres cosas:
 *  1. Un detector de anomalías sin etiquetas: compara cada característica con la línea base de TODOS los jugadores
 *     (se va aprendiendo sola) y solo castiga en la dirección sospechosa (p. ej. clics demasiado regulares).
 *  2. Un modelo supervisado opcional (regresión logística entrenada con tus casos confirmados, ver tools/train_model.py).
 *  3. Reglas duras para evidencias muy claras.
 */
final class Detector {
    // dirección sospechosa: +1 = valores altos, -1 = valores bajos, 0 = no se usa para anomalías
    static final double[] DIR = {1, 0, -1, 1, 1, 0, 0, 1, 1, -1, 0, 1, 1, 1, 1, -1, -1};
    // valores de partida aproximados (heurísticos) hasta que la línea base aprenda de tus jugadores
    static final double[] PRIOR_MEAN = {6.0, 160, 0.35, 0.03, 0.25, 3.0, 6.0, 0.3, 0.02, 0.95, 4, 0.45, 2.6, 2.3, 0.3, 12, 8};
    static final double[] PRIOR_STD = {2.5, 60, 0.12, 0.04, 0.10, 2.5, 4.0, 0.4, 0.04, 0.08, 3, 0.20, 0.35, 0.30, 0.4, 6, 4};

    final double[] mean = PRIOR_MEAN.clone();
    final double[] var = new double[Features.N];
    final long[] cnt = new long[Features.N];
    static final double CAP = 6.0;
    int minBaseline = 150;

    // modelo supervisado opcional
    double[] mMean, mStd, mW;
    double mB;

    static final class Verdict {
        double p, pUnsup = 0, pModel = Double.NaN;
        final List<String> reasons = new ArrayList<>();
    }

    Detector() {
        for (int i = 0; i < Features.N; i++) var[i] = PRIOR_STD[i] * PRIOR_STD[i];
    }

    void setModel(double[] m, double[] s, double[] w, double b) {
        if (m == null || m.length != Features.N || s.length != Features.N || w.length != Features.N) {
            mMean = mStd = mW = null;
            return;
        }
        mMean = m; mStd = s; mW = w; mB = b;
    }

    boolean hasModel() { return mW != null; }

    private static double sigmoid(double x) { return 1.0 / (1.0 + Math.exp(-x)); }

    private double effMean(int i) {
        double w = Math.min(1.0, cnt[i] / (double) minBaseline);
        return (1 - w) * PRIOR_MEAN[i] + w * mean[i];
    }

    private double effStd(int i) {
        double w = Math.min(1.0, cnt[i] / (double) minBaseline);
        double learned = Math.sqrt(Math.max(var[i], 1e-9));
        // nunca menos del 40 % de la desviación de partida: evita que una característica poco frecuente
        // aprenda una variación casi nula y luego marque como anomalía cualquier valor normal
        return Math.max(0.4 * PRIOR_STD[i], (1 - w) * PRIOR_STD[i] + w * learned);
    }

    /** Actualiza la línea base con una ventana (se usa solo con ventanas que no parecen trampa). */
    void learn(double[] x) {
        for (int i = 0; i < Features.N; i++) {
            if (Double.isNaN(x[i])) continue;
            cnt[i]++;
            if (cnt[i] == 1) { mean[i] = x[i]; var[i] = PRIOR_STD[i] * PRIOR_STD[i] * 0.25; continue; }
            double a = Math.max(1.0 / (cnt[i] + 1), 0.002);
            double d = x[i] - mean[i];
            mean[i] += a * d;
            var[i] = (1 - a) * (var[i] + a * d * d);
        }
    }

    Verdict score(Features.Computed c) {
        double[] x = c.f;
        Verdict v = new Verdict();

        // 1) anomalías
        double[] s = new double[Features.N];
        int used = 0;
        for (int i = 0; i < Features.N; i++) {
            if (DIR[i] == 0 || Double.isNaN(x[i])) continue;
            // tope por característica: una sola no basta para marcar a alguien, hacen falta varias a la vez
            s[i] = Math.min(CAP, Math.max(0, DIR[i] * (x[i] - effMean(i)) / effStd(i)));
            used++;
        }
        double[] sorted = s.clone();
        Arrays.sort(sorted);
        double top = 0;
        for (int k = 0; k < 3 && used > 0; k++) top += sorted[Features.N - 1 - k] * sorted[Features.N - 1 - k];
        v.pUnsup = used == 0 ? 0 : sigmoid(Math.sqrt(top) - 6.0);
        for (int i = 0; i < Features.N; i++) {
            if (s[i] >= 3.0) v.reasons.add(String.format("%s=%.2f (%.1f desv.)", Features.NAMES[i], x[i], s[i]));
        }

        // 2) modelo entrenado
        v.p = v.pUnsup;
        if (hasModel()) {
            double z = mB;
            for (int i = 0; i < Features.N; i++) {
                double xi = Double.isNaN(x[i]) ? mMean[i] : x[i];
                z += mW[i] * (xi - mMean[i]) / Math.max(1e-9, mStd[i]);
            }
            v.pModel = sigmoid(z);
            v.p = 0.7 * v.pModel + 0.3 * v.pUnsup;
        }

        // 3) reglas duras (evidencia muy clara)
        double rule = 0;
        if (!Double.isNaN(x[Features.REPEAT]) && x[Features.REPEAT] >= 0.6 && x[Features.CPS] >= 8 && c.intervals >= 12) {
            rule = Math.max(rule, 0.92); v.reasons.add("intervalos de clic idénticos");
        }
        if (!Double.isNaN(x[Features.CV]) && x[Features.CV] < 0.06 && x[Features.CPS] >= 10 && c.intervals >= 15) {
            rule = Math.max(rule, 0.88); v.reasons.add("ritmo de clics constante");
        }
        if (!Double.isNaN(x[Features.NO_MOUSE]) && x[Features.NO_MOUSE] >= 0.35 && c.rotTicks >= 25) {
            rule = Math.max(rule, 0.90); v.reasons.add("rotación sin movimiento de ratón");
        }
        if (!Double.isNaN(x[Features.REACH_MAX]) && x[Features.REACH_MAX] > 4.5 && c.hits >= 3) {
            rule = Math.max(rule, 0.90); v.reasons.add(String.format("alcance %.2f", x[Features.REACH_MAX]));
        }
        if (!Double.isNaN(x[Features.AIM_STD]) && x[Features.AIM_STD] < 0.7 && x[Features.AIM_MEAN] < 4 && c.hits >= 10) {
            rule = Math.max(rule, 0.85); v.reasons.add("puntería inhumana");
        }
        v.p = Math.max(v.p, rule);
        return v;
    }
}
