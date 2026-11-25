#!/usr/bin/env python3
"""
Entrena el modelo supervisado del plugin con los casos que el staff confirmó (/guard label).

Uso:
    python3 train_model.py plugins/AmetraGuard/dataset.csv plugins/AmetraGuard/model.json

Después, en el servidor:  /guard reload
Necesita:  pip install scikit-learn numpy
"""
import csv
import json
import sys

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import precision_score, recall_score
from sklearn.model_selection import StratifiedKFold, cross_val_predict

MIN_PER_CLASS = 30


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    src, dst = sys.argv[1], sys.argv[2]

    with open(src, newline="", encoding="utf-8") as f:
        rd = csv.reader(f)
        header = next(rd)
        rows = [r for r in rd if r]
    names = header[1:]
    y = np.array([int(r[0]) for r in rows])
    X = np.array([[float(v) if v != "" else np.nan for v in r[1:]] for r in rows])

    n1, n0 = int(y.sum()), int((y == 0).sum())
    print(f"ejemplos: {len(y)}  (trampa={n1}, legítimos={n0})")
    if min(n0, n1) < MIN_PER_CLASS:
        sys.exit(f"Hacen falta al menos {MIN_PER_CLASS} ventanas de cada clase (/guard label <jugador> cheat|legit).")

    # los huecos (NaN) se rellenan con la media: en el servidor un hueco también cuenta como "valor medio"
    mean = np.nanmean(X, axis=0)
    mean = np.where(np.isnan(mean), 0.0, mean)
    X = np.where(np.isnan(X), mean, X)
    std = X.std(axis=0)
    std = np.where(std < 1e-9, 1.0, std)
    Z = (X - mean) / std

    clf = LogisticRegression(C=1.0, class_weight="balanced", max_iter=2000)

    folds = min(5, n0, n1)
    if folds >= 2:
        pred = cross_val_predict(clf, Z, y, cv=StratifiedKFold(folds, shuffle=True, random_state=0))
        print(f"validación cruzada ({folds} pliegues): precisión={precision_score(y, pred):.3f}  recall={recall_score(y, pred):.3f}")

    clf.fit(Z, y)
    out = {
        "features": names,
        "mean": [float(v) for v in mean],
        "std": [float(v) for v in std],
        "w": [float(v) for v in clf.coef_[0]],
        "b": float(clf.intercept_[0]),
    }
    with open(dst, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2)
    print(f"modelo guardado en {dst}")
    top = sorted(zip(names, out["w"]), key=lambda t: -abs(t[1]))[:5]
    print("características con más peso:", ", ".join(f"{n} ({w:+.2f})" for n, w in top))


if __name__ == "__main__":
    main()
