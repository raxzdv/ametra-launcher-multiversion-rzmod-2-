#!/usr/bin/env python3
"""
Genera las texturas de los cosméticos (PNG 64x64, píxel-art con transparencia)
y el archivo CosmeticData.java que usa el mod para dibujarlos.

Uso (desde la carpeta del launcher):   python tools/gen_cosmetics.py
Necesita:  pip install pillow

Coordenadas de las cajas = píxeles de Minecraft en el espacio del modelo del jugador:
  - Cabeza: y de -8 a 0 (x ±4, z ±4). Torso: y de 0 a 12 (x ±4, z ±2).
  - El frente del jugador es z NEGATIVO, la espalda es z POSITIVO. y positivo = abajo.
  - Capa, alas y mochilas se anclan al torso; sombreros y gafas, a la cabeza.
"""
import math
import os
import random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
TEX_DIR = os.path.join(ROOT, "ametra-mod", "src", "main", "resources", "assets", "ametra", "textures", "cosmetics")
JAVA_OUT = os.path.join(ROOT, "ametra-mod", "src", "main", "java", "com", "ametra", "CosmeticData.java")


# ---------------------------------------------------------------- utilidades de color
def hx(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, f):
    """Sombra hacia azul/violeta y luz hacia cálido (cambio de tono, como en el pixel-art profesional)."""
    if f >= 1:
        w = min(0.32, (f - 1) * 0.38)
        tint = (255, 238, 200)
    else:
        w = (1 - f) * 0.30
        tint = (28, 20, 72)
    return tuple(clamp(c[i] * f * (1 - w) + tint[i] * w) for i in range(3))


BAYER = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))


def dither(c, k, x, y, steps=9, lo=0.52, hi=1.22):
    """Cuantiza el brillo k en niveles con dither ordenado: da textura de tela sin ruido."""
    v = max(0.0, min(steps - 1.0, (k - lo) / (hi - lo) * (steps - 1)))
    base = int(math.floor(v))
    if v - base > (BAYER[y % 4][x % 4] + 0.5) / 16.0:
        base += 1
    return shade(c, lo + min(base, steps - 1) * (hi - lo) / (steps - 1))


def mix(a, b, t):
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


WHITE = (255, 255, 255)


class Cv:
    """Lienzo cuadrado (64 por defecto; 128 para piezas grandes como las alas) con transparencia."""

    def __init__(self, n=64):
        self.n = n
        self.im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
        self.p = self.im.load()

    def set(self, x, y, c, a=255):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.p[x, y] = (c[0], c[1], c[2], a)

    def rect(self, x0, y0, x1, y1, c, a=255):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, c, a)

    def clear(self, x0, y0, x1, y1):
        for y in range(y0, y1):
            for x in range(x0, x1):
                if 0 <= x < self.n and 0 <= y < self.n:
                    self.p[x, y] = (0, 0, 0, 0)

    def vgrad(self, x0, y0, x1, y1, c1, c2):
        n = max(1, y1 - y0 - 1)
        for y in range(y0, y1):
            c = mix(c1, c2, (y - y0) / n)
            for x in range(x0, x1):
                self.set(x, y, c)

    def hgrad(self, x0, y0, x1, y1, c1, c2):
        n = max(1, x1 - x0 - 1)
        for x in range(x0, x1):
            c = mix(c1, c2, (x - x0) / n)
            for y in range(y0, y1):
                self.set(x, y, c)

    def frame(self, x0, y0, x1, y1, c):
        for x in range(x0, x1):
            self.set(x, y0, c)
            self.set(x, y1 - 1, c)
        for y in range(y0, y1):
            self.set(x0, y, c)
            self.set(x1 - 1, y, c)

    def jitter(self, x0, y0, x1, y1, amt, seed):
        r = random.Random(seed)
        for y in range(y0, y1):
            for x in range(x0, x1):
                px = self.p[x, y]
                if px[3] == 0:
                    continue
                f = 1 + r.uniform(-amt, amt)
                self.p[x, y] = (clamp(px[0] * f), clamp(px[1] * f), clamp(px[2] * f), px[3])

    def line(self, x0, y0, x1, y1, c, a=255):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx = 1 if x0 < x1 else -1
        sy = 1 if y0 < y1 else -1
        err = dx + dy
        while True:
            self.set(x0, y0, c, a)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def save(self, name):
        os.makedirs(TEX_DIR, exist_ok=True)
        self.im.save(os.path.join(TEX_DIR, name + ".png"))


def finish(cv, rects, outline=False, bevel=True):
    """Pasada final: contorno selectivo en los bordes recortados y bisel (luz arriba-izquierda)."""
    snap = cv.im.copy().load()
    for (x0, y0, x1, y1) in rects:
        big = (x1 - x0) >= 8 and (y1 - y0) >= 8
        for y in range(y0, y1):
            for x in range(x0, x1):
                px = snap[x, y]
                if px[3] == 0:
                    continue

                def clear(nx, ny):
                    if not (x0 <= nx < x1 and y0 <= ny < y1):
                        return False
                    return snap[nx, ny][3] == 0

                if outline and (clear(x + 1, y) or clear(x - 1, y) or clear(x, y + 1) or clear(x, y - 1)):
                    cv.set(x, y, shade(px[:3], 0.5), px[3])
                    continue
                if bevel and big:
                    if y == y0 or x == x0:
                        cv.set(x, y, shade(px[:3], 1.14), px[3])
                    elif y == y1 - 1 or x == x1 - 1:
                        cv.set(x, y, shade(px[:3], 0.8), px[3])
    return cv


# ---------------------------------------------------------------- definición de cosméticos
ITEMS = []


# Animación por caja (bits de "an"). Velocidades en ciclos por segundo, fase en grados.
#   ORBIT: gira alrededor del eje Y del ancla con radio = x.      BOB: sube y baja (amplitud ap).
#   PULSE: late de tamaño (amplitud ap).                          SWAY: se balancea en x (amplitud ap).
#   RISE: sube ar píxeles y se desvanece (partículas).            SPIN: gira sobre sí misma.
ORBIT, BOB, PULSE, SWAY, RISE, SPIN = 1, 2, 4, 8, 16, 32


def B(x, y, z, w, h, d, uv, uv2=None, ry=0.0, glow=False, flags=0, an=0, ap=0.0, asp=0.0, aph=0.0, ar=0.0):
    return dict(x=x, y=y, z=z, w=w, h=h, d=d, uv=uv, uv2=uv2 or uv, ry=ry, glow=glow, flags=flags,
                an=an, ap=ap, asp=asp, aph=aph, ar=ar)


PRICES = {"comun": 150, "raro": 450, "epico": 1000, "legendario": 2200}


def item(cat, ident, name, rar, boxes, paint, price=None, desc="", ts=64):
    ITEMS.append(dict(cat=cat, id=ident, name=name, rar=rar, boxes=boxes, paint=paint, ts=ts,
                      price=PRICES[rar] if price is None else price, desc=desc))


# ================================================================ CAPAS
CAPE_MAIN = (0, 0, 32, 48)
CAPE_EDGE = (36, 0, 40, 4)
CAPE_COLLAR = (34, 8, 64, 13)


def gem(cv, cx, cy, r, c, hi=WHITE):
    """Diamante con facetas (luz arriba-izquierda, sombra abajo-derecha)."""
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            d = abs(dx) + abs(dy)
            if d > r:
                continue
            if d == r:
                col = shade(c, 0.45)
            elif dx + dy < -r // 2:
                col = mix(c, hi, 0.55)
            elif dx + dy > r // 2:
                col = shade(c, 0.7)
            else:
                col = c
            cv.set(cx + dx, cy + dy, col)
    cv.set(cx - r // 3, cy - r // 3, hi)


def crystal(cv, cx, cy, c, s=1.0, ring=True):
    """Cristal de Ametra: prisma hexagonal con puntas y caras iluminadas, aro dorado."""
    gold = hx("#f0c040")
    hh = int(7 * s)
    hw = max(2, int(4 * s))
    if ring:
        for dy in range(-hh - 3, hh + 4):
            for dx in range(-hw - 4, hw + 5):
                d = math.hypot(dx / (hw + 4.0), dy / (hh + 3.5))
                if 0.9 <= d <= 1.0:
                    cv.set(cx + dx, cy + dy, shade(gold, 0.85 if dx + dy > 0 else 1.15))
    for dy in range(-hh, hh + 1):
        w = hw if abs(dy) <= hh - hw else max(0, hw - (abs(dy) - (hh - hw)))
        for dx in range(-w, w + 1):
            top_face = dy < -(hh - hw) + 1 and abs(dx) <= w
            if dx < -w // 3:
                col = mix(c, WHITE, 0.42)
            elif dx > w // 3:
                col = shade(c, 0.62)
            else:
                col = shade(c, 1.12)
            if top_face and dy < -(hh - hw):
                col = mix(col, WHITE, 0.18)
            if abs(dx) == w or abs(dy) == hh:
                col = shade(c, 0.42)
            cv.set(cx + dx, cy + dy, col)
    cv.set(cx - hw // 2, cy - hh // 2, WHITE)
    cv.set(cx - hw // 2, cy - hh // 2 + 1, mix(c, WHITE, 0.8))


def crown_icon(cv, cx, cy, c):
    pts = [(-5, 2), (-5, 1), (-5, 0), (-4, 1), (-3, 2), (-2, 1), (-1, 0), (0, -1), (1, 0), (2, 1), (3, 2), (4, 1), (5, 0), (5, 1), (5, 2)]
    for dx, dy in pts:
        cv.set(cx + dx, cy + dy, shade(c, 1.2))
    for dx in range(-5, 6):
        cv.set(cx + dx, cy + 3, shade(c, 0.95))
        cv.set(cx + dx, cy + 4, shade(c, 0.6))
    for dx in (-3, 0, 3):
        cv.set(cx + dx, cy + 3, hx("#e03060"))


def flame_icon(cv, cx, cy):
    rows = ["....#....", "...##....", "..###.#..", "..#####..", ".#######.", ".###.###.", ".##...##.", "..#####.."]
    cols = [hx("#fff3b0"), hx("#ffb02e"), hx("#ff6a1a")]
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#":
                cv.set(cx - 4 + i, cy - 4 + j, cols[min(2, max(0, (j + abs(i - 4)) // 3))] if j > 1 else cols[2])


def paint_cape(c1, c2, style, stripe=False, emblem="gem", seed=1):
    def go(cv):
        a, b = hx(c1), hx(c2)
        rnd = random.Random(seed)
        X1, Y1 = 32, 48
        # tela con degradado vertical y pliegues con dither
        for y in range(Y1):
            t = y / (Y1 - 1)
            for x in range(X1):
                fold = math.sin(x * 0.66 + 0.8) + 0.45 * math.sin(x * 1.45 + 2.1 + math.sin(y * 0.09) * 1.2)
                cv.set(x, y, dither(a, 1.13 - 0.42 * t + 0.10 * fold, x, y))

        def lit(x, y, f):
            if 0 <= x < X1 and 0 <= y < Y1:
                cv.set(x, y, shade(cv.p[x, y][:3], f))

        if style == "lattice":
            for y in range(Y1):
                for x in range(X1):
                    if (x + y) % 8 == 0 or (x - y) % 8 == 0:
                        lit(x, y, 1.2)
                    if (x + y) % 8 == 0 and (x - y) % 8 == 0:
                        cv.set(x, y, mix(cv.p[x, y][:3], WHITE, 0.5))
                    if (x + y) % 8 == 1 and (x - y) % 8 != 0:
                        lit(x, y, 0.88)
        elif style == "damask":
            for oy, ox in ((0, 0), (8, 8)):
                for cy in range(4 + oy, 44, 16):
                    for cx in range(4 + ox, 32, 16):
                        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (0, 2), (0, -2), (2, 0), (-2, 0)):
                            lit(cx + dx, cy + dy, 1.28)
                        for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1), (2, 2), (-2, 2), (2, -2), (-2, -2)):
                            lit(cx + dx, cy + dy, 0.8)
        elif style == "scales":
            for y in range(Y1):
                for x in range(X1):
                    cx = (x + (4 if (y // 4) % 2 else 0)) % 8
                    cy = y % 4
                    if cy == 3 - (abs(cx - 4) // 2) and abs(cx - 4) <= 3:
                        lit(x, y, 1.3)
                    elif cy == 0 and cx == 4:
                        lit(x, y, 0.78)
                    elif cy in (1, 2) and abs(cx - 4) <= 1:
                        lit(x, y, 1.08)
        elif style == "metal":
            for y in range(Y1):
                for x in range(X1):
                    k = (x * 2 + y) % 26
                    if k < 3:
                        cv.set(x, y, mix(cv.p[x, y][:3], WHITE, 0.34 if k == 1 else 0.16))
                    elif k > 22:
                        lit(x, y, 0.84)
            cv.jitter(0, 0, X1, Y1, 0.04, seed)
        elif style == "stars":
            for y in range(24, 44):  # nebulosa azul abajo
                for x in range(X1):
                    if (x * 3 + y * 5 + int(math.sin(x * 0.4) * 4)) % 9 < 2 and y > 24 + (x % 5):
                        cv.set(x, y, mix(cv.p[x, y][:3], hx("#2d3f7a"), 0.55))
            for _ in range(46):
                x, y = rnd.randrange(1, X1 - 1), rnd.randrange(6, 40)
                cv.set(x, y, rnd.choice([WHITE, hx("#bfe3ff"), hx("#85c1e9")]))
            for x, y in ((6, 12), (25, 20), (10, 31), (22, 33)):
                cv.set(x, y, WHITE)
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    cv.set(x + dx, y + dy, hx("#85c1e9"))
            for dy in range(-6, 7):
                for dx in range(-6, 7):
                    if dx * dx + dy * dy <= 30 and (dx - 3) ** 2 + (dy + 1) ** 2 > 26:
                        cv.set(16 + dx, 9 + dy, hx("#e8f2ff"))
        elif style == "aurora":
            for y in range(Y1):
                for x in range(X1):
                    w = (math.sin(y * 0.27 + x * 0.14) + math.sin(y * 0.11 - x * 0.2) * 0.6 + 1.6) / 3.2
                    col = mix(a, b, w)
                    if (x + y * 2) % 19 < 2:
                        col = mix(col, WHITE, 0.35)
                    cv.set(x, y, dither(col, 1.0 + 0.06 * math.sin(x * 0.9), x, y, 9, 0.9, 1.1))
            for _ in range(36):
                cv.set(rnd.randrange(1, X1 - 1), rnd.randrange(6, 42), WHITE)
        elif style == "flame":
            ramp = [hx("#fff3b0"), hx("#ffc23a"), hx("#ff7a1a"), hx("#d8321a"), hx("#7a1208")]
            for x in range(X1):
                h = 17 + 9 * abs(math.sin(x * 0.55)) + 5 * math.sin(x * 0.23 + 1.0) + (x % 3)
                for y in range(Y1):
                    t = (41 - y) / h
                    if 0 <= t < 1.0:
                        k = min(4, int(t * 5.0 + ((x + y) % 2) * 0.35))
                        cv.set(x, y, ramp[k])
            for _ in range(26):
                cv.set(rnd.randrange(1, X1 - 1), rnd.randrange(8, 30), rnd.choice([hx("#ffc23a"), hx("#ff7a1a")]))
        elif style == "frost":
            for _ in range(60):
                x, y = rnd.randrange(1, X1 - 1), rnd.randrange(5, 41)
                cv.set(x, y, rnd.choice([WHITE, hx("#d8f1ff")]))
            for ang in range(0, 360, 60):  # copo central
                r = math.radians(ang)
                ex, ey = int(16 + 11 * math.cos(r)), int(20 + 11 * math.sin(r))
                cv.line(16, 20, ex, ey, hx("#e8f7ff"))
                mx_, my_ = int(16 + 6 * math.cos(r)), int(20 + 6 * math.sin(r))
                for da in (-50, 50):
                    rr = math.radians(ang + da)
                    cv.line(mx_, my_, int(mx_ + 4 * math.cos(rr)), int(my_ + 4 * math.sin(rr)), hx("#bfe3ff"))
            for x in range(1, X1 - 1, 5):  # carámbanos en el borde inferior
                for k in range(rnd.randrange(2, 6)):
                    cv.set(x, 40 - k, hx("#e8f7ff"))
        elif style == "facets":
            for y in range(Y1):
                for x in range(X1):
                    cx_, cy_ = x % 8, y % 8
                    if (cx_ + cy_) < 8:
                        lit(x, y, 1.12 if (x // 8 + y // 8) % 2 else 0.94)
                    else:
                        lit(x, y, 0.82 if (x // 8 + y // 8) % 2 else 1.06)
                    if cx_ == 0 or cy_ == 0:
                        lit(x, y, 1.15)
        cv.jitter(0, 0, X1, Y1, 0.03, seed + 7)

        if stripe:
            for y in range(4, 43):
                for x in range(13, 19):
                    k = x - 13
                    col = shade(b, 0.7) if k in (0, 5) else (mix(b, WHITE, 0.2) if k in (1, 2) else b)
                    cv.set(x, y, col)
        # banda superior con bordado y dobladillo inferior con puntadas
        for y in range(0, 3):
            for x in range(X1):
                cv.set(x, y, shade(b, 1.1 - 0.12 * y))
        for x in range(1, X1, 4):
            cv.set(x, 1, shade(b, 1.5))
            cv.set(x + 1, 1, shade(b, 0.7))
        for x in range(X1):
            cv.set(x, 3, shade(b, 0.5))
        for y in range(41, 48):
            for x in range(X1):
                cv.set(x, y, b if y not in (41, 47) else shade(b, 0.6))
        for x in range(X1):
            cv.set(x, 42, mix(b, WHITE, 0.32))
            cv.set(x, 43, shade(b, 1.08))
        for x in range(0, X1, 4):
            cv.set(x, 45, shade(b, 0.6))
            cv.set(x + 1, 45, shade(b, 0.6))
        # bordes laterales más oscuros (da grosor)
        for y in range(Y1):
            for x, f in ((0, 0.6), (31, 0.6), (1, 0.84), (30, 0.84)):
                cv.set(x, y, shade(cv.p[x, y][:3], f))
        # emblema
        if emblem == "crystal":
            crystal(cv, 16, 22, b if style != "facets" else hx("#a47af0"), 1.0)
        elif emblem == "crystal_s":
            crystal(cv, 16, 21, b, 0.75, ring=False)
        elif emblem == "crown":
            crown_icon(cv, 16, 19, hx("#fff0c0") if style == "metal" else b)
        elif emblem == "flame":
            flame_icon(cv, 16, 21)
        elif emblem == "gem":
            gem(cv, 16, 19, 6, b, mix(b, WHITE, 0.6))
        # borde inferior festoneado (recorta la tela)
        for x in range(X1):
            m = x % 6
            depth = 2 if m in (0, 5) else (1 if m in (1, 4) else 0)
            for y in range(47 - depth + 1, 48):
                cv.p[x, y] = (0, 0, 0, 0)
        # arista y collar
        cv.rect(36, 0, 40, 4, shade(a, 0.5))
        cv.jitter(36, 0, 40, 4, 0.1, seed)
        cv.vgrad(34, 8, 64, 13, shade(b, 1.25), shade(b, 0.8))
        for x in range(35, 63, 3):
            cv.set(x, 10, shade(b, 1.5))
        cv.frame(34, 8, 64, 13, shade(b, 0.5))
        gem(cv, 38, 10, 2, a, mix(a, WHITE, 0.6))
        gem(cv, 60, 10, 2, a, mix(a, WHITE, 0.6))
        return finish(cv, [CAPE_MAIN], outline=True, bevel=False)

    return go


CAPE_BOXES = [
    B(0, 7.5, 2.7, 9.6, 15, 0.7, CAPE_MAIN, CAPE_EDGE),
    B(0, 0.9, 2.45, 10.2, 1.7, 1.3, CAPE_COLLAR, CAPE_EDGE),
]
for cid, nm, rar, c1, c2, st, stripe, emb, desc, price in [
    ("amatista", "Amatista", "comun", "#6f48c9", "#c8a8ff", "lattice", False, "crystal_s", "Tela morada con celosía y el cristal de Ametra.", 0),
    ("esmeralda", "Esmeralda", "comun", "#1f7a4d", "#8fd6a8", "scales", False, "gem", "Escamas verdes con borde bordado.", None),
    ("hielo", "Escarcha", "raro", "#4f9ccc", "#e8f7ff", "frost", False, "none", "Copo de hielo y carámbanos que brillan.", None),
    ("rubi", "Rubí", "raro", "#9b1c31", "#f1948a", "damask", False, "gem", "Damasco rojo con motivos de flor.", None),
    ("oro", "Oro", "raro", "#b8862e", "#ffd27a", "metal", True, "crown", "Metal cepillado con corona en relieve.", None),
    ("noche", "Noche", "epico", "#14161d", "#85c1e9", "stars", True, "none", "Cielo con luna, estrellas y nebulosa.", None),
    ("ametra", "Ametra", "epico", "#2a1658", "#c8a8ff", "facets", False, "crystal", "La capa oficial: facetas de amatista y gran cristal.", None),
    ("aurora", "Aurora", "legendario", "#19e3ff", "#b061e0", "aurora", True, "crystal_s", "Ondas de aurora boreal que brillan en la oscuridad.", None),
    ("fuego", "Fénix", "legendario", "#8a1a0d", "#ffb02e", "flame", False, "flame", "Llamas vivas que suben por la tela.", None),
]:
    boxes = [dict(b) for b in CAPE_BOXES]
    if cid in ("aurora", "fuego"):
        boxes[0]["glow"] = True
    item("cape", cid, nm, rar, boxes, paint_cape(c1, c2, st, stripe, emb, seed=len(cid) * 13 + ord(cid[0])), price=price, desc=desc)

# ================================================================ ALAS
WING_MAIN = (0, 0, 32, 40)
WING_EDGE = (36, 0, 40, 4)
WW, WH = 32, 40


def wing_mask(kind):
    m = [[False] * WW for _ in range(WH)]
    tops = [0.0] * WW
    if kind in ("angel", "neon", "phoenix"):
        for x in range(WW):
            t = x / (WW - 1)
            top = 11 - 10 * math.sin(math.pi * min(1, t * 1.18) * 0.85)
            bot = top + 28 * (1 - t ** 1.5) - abs((x % 5) - 2) * 0.8
            tops[x] = top
            for y in range(WH):
                m[y][x] = top <= y <= bot
    elif kind == "dragon":
        tips = [(31, 7), (23, 27), (11, 36), (0, 39)]
        for x in range(WW):
            t = x / (WW - 1)
            top = 14 - 13 * math.sin(math.pi * min(1, t * 1.1) * 0.8)
            tops[x] = top
            bot = 39
            for i in range(len(tips) - 1):
                (xa, ya), (xb, yb) = tips[i], tips[i + 1]
                if xb <= x <= xa:
                    u = (x - xb) / max(1, (xa - xb))
                    bot = yb + (ya - yb) * u - 6 * math.sin(math.pi * u)
                    break
            for y in range(WH):
                m[y][x] = top <= y <= bot
    elif kind == "butterfly":
        for y in range(WH):
            for x in range(WW):
                up = ((x - 14) / 15.5) ** 2 + ((y - 12) / 12.0) ** 2 <= 1
                lo = ((x - 10) / 10.5) ** 2 + ((y - 30) / 9.0) ** 2 <= 1
                m[y][x] = up or lo
        for x in range(WW):
            tops[x] = next((y for y in range(WH) if m[y][x]), 0)
    return m, tops


def paint_wing(kind, c1, c2, seed=3):
    def go(cv):
        a, b = hx(c1), hx(c2)
        rnd = random.Random(seed)
        m, tops = wing_mask(kind)

        def inside(x, y):
            if x < 0:
                return True
            return 0 <= x < WW and 0 <= y < WH and m[y][x]

        edge = {}
        for y in range(WH):
            for x in range(WW):
                if not m[y][x]:
                    continue
                if not (inside(x + 1, y) and inside(x - 1, y) and inside(x, y + 1) and inside(x, y - 1)):
                    edge[(x, y)] = True

        for y in range(WH):
            for x in range(WW):
                if not m[y][x]:
                    continue
                t = x / (WW - 1)
                if kind == "phoenix":
                    col = mix(hx("#b81e10"), a, min(1.0, t * 1.5))
                    col = mix(col, b, max(0.0, (t - 0.55) * 1.8) * (0.6 + 0.4 * ((x + y) % 2)))
                    if x % 4 == 0 and y > tops[x] + 6:
                        col = shade(col, 0.8)
                    elif x % 4 == 1 and y > tops[x] + 6:
                        col = shade(col, 1.12)
                    if y < tops[x] + 8 and x < 22:
                        col = mix(col, hx("#7a1208"), 0.3)
                elif kind == "angel":
                    col = mix(shade(a, 1.0), shade(b, 0.92), t * 0.8 + (y / WH) * 0.2)
                    if x % 5 == 0 and y > tops[x] + 9:
                        col = shade(col, 0.84)
                    elif x % 5 == 1 and y > tops[x] + 9:
                        col = shade(col, 1.05)
                    if y > tops[x] + 9 and (y - int(tops[x]) - 9 + (x // 5) * 3) % 7 == 6:
                        col = shade(col, 0.8)  # festón de cada fila de plumas
                    if y < tops[x] + 9 and x < 24:  # plumas cobertoras
                        col = mix(col, b, 0.35)
                        if (x + (y // 3) * 2) % 5 == 0:
                            col = shade(col, 0.88)
                elif kind == "neon":
                    col = hx("#07131c")
                    if x % 5 == 0 and y > tops[x] + 6:
                        col = shade(a, 0.45)
                    if (y - int(tops[x])) % 7 == 3 and x % 2 == 0:
                        col = shade(a, 0.35)
                elif kind == "dragon":
                    col = mix(shade(a, 1.15), shade(a, 0.7), (y / WH) * 0.7 + t * 0.2)
                    col = shade(col, 1 + 0.05 * math.sin(x * 1.3 + y * 0.9))
                elif kind == "butterfly":
                    d = math.hypot(x - 8, y - 18) / 24.0
                    col = mix(a, b, min(1.0, d * 1.15))
                    if ((x * 7 + y * 13) % 29) == 0 and d > 0.45:
                        col = WHITE
                cv.set(x, y, col)

        # huesos y detalles
        if kind == "dragon":
            bone = b
            for (tx, ty) in [(31, 7), (23, 27), (11, 36)]:
                cv.line(0, 15, tx, ty, bone)
            for x in range(0, 31):
                top = int(round(tops[x]))
                cv.set(x, top, shade(bone, 1.4))
                cv.set(x, top + 1, bone)
            for (tx, ty) in [(31, 7), (23, 27), (11, 36)]:
                cv.set(tx, ty, shade(bone, 1.6))
        if kind == "butterfly":
            for k in range(5):
                cv.line(0, 20, 28 - k * 3, 2 + k * 7, shade(a, 0.55), 160)
            for cx, cy in ((22, 9), (14, 6), (13, 32), (6, 27)):
                for dy in range(-2, 3):
                    for dx in range(-2, 3):
                        if dx * dx + dy * dy <= 4:
                            cv.set(cx + dx, cy + dy, WHITE if dx * dx + dy * dy <= 1 else b)

        # contorno
        for (x, y) in edge:
            if x == 0:
                continue
            if kind == "neon":
                cv.set(x, y, mix(a, WHITE, 0.45))
                for nx, ny in ((x - 1, y), (x, y - 1), (x + 1, y), (x, y + 1)):
                    if 0 <= nx < WW and 0 <= ny < WH and m[ny][nx] and (nx, ny) not in edge:
                        cv.set(nx, ny, a)
            elif kind == "butterfly":
                cv.set(x, y, shade(a, 0.18))
                for nx, ny in ((x - 1, y), (x, y - 1), (x + 1, y), (x, y + 1)):
                    if 0 <= nx < WW and 0 <= ny < WH and m[ny][nx] and (nx, ny) not in edge:
                        cv.set(nx, ny, shade(a, 0.4))
            elif kind == "dragon":
                cv.set(x, y, shade(b, 0.8))
            elif kind == "phoenix":
                cv.set(x, y, hx("#6a0f06"))
            else:
                cv.set(x, y, shade(b, 0.62))
        # brillo en el borde superior
        if kind in ("angel", "butterfly", "phoenix"):
            for x in range(1, WW - 1):
                y = int(round(tops[x])) + 1
                if 0 <= y < WH and m[y][x]:
                    cv.set(x, y, mix(cv.p[x, y][:3], WHITE, 0.4))
        cv.jitter(0, 0, WW, WH, 0.03, seed + 5)
        cv.rect(36, 0, 40, 4, shade(b, 0.55))
        return cv

    return go


# Ala derecha en coordenadas locales de la bisagra (el mod la refleja para la izquierda).
# x = mitad del largo (el ala se extiende hacia fuera), y centrado en el hombro.
def wing_box(glow=False):
    return [B(8.0, 6.5, 0.0, 16, 18, 0.3, WING_MAIN, WING_EDGE, glow=glow)]


item("wings", "angel", "Ángel", "raro", wing_box(), paint_wing("angel", "#f4f1ff", "#cfc7f2"), desc="Plumas blancas en filas con brillo en el borde.")
item("wings", "dragon", "Dragón", "epico", wing_box(), paint_wing("dragon", "#7a1530", "#2e0612"), desc="Membrana de cuero con huesos y garras.")
item("wings", "mariposa", "Mariposa", "epico", wing_box(), paint_wing("butterfly", "#b061e0", "#f08ad0"), desc="Alas con venas y manchas de luz.")
item("wings", "neon", "Neón", "legendario", wing_box(True), paint_wing("neon", "#19e3ff", "#0a7f93"), desc="Circuito de neón que brilla en la oscuridad.")
item("wings", "fenix", "Alas de fénix", "legendario", wing_box(True), paint_wing("phoenix", "#ff7a1a", "#ffd23c"), desc="Plumas de fuego del naranja al amarillo.")

# ================================================================ SOMBREROS (anclados a la cabeza)


def weave(cv, r, c, amt=0.06, seed=1):
    cv.rect(r[0], r[1], r[2], r[3], c)
    cv.jitter(r[0], r[1], r[2], r[3], amt, seed)


def stitch(cv, x0, x1, y, c, step=3):
    for x in range(x0, x1, step):
        cv.set(x, y, c)
        cv.set(x + 1, y, c)


# --- Gorra
CAP_CROWN, CAP_TOP, CAP_VISOR, CAP_STRAP = (0, 0, 24, 8), (26, 0, 34, 8), (0, 10, 24, 18), (26, 10, 34, 14)


def paint_gorra(cv):
    red, dark = hx("#d94141"), hx("#a82e2e")
    cv.vgrad(*CAP_CROWN, shade(red, 1.15), shade(red, 0.85))
    cv.jitter(*CAP_CROWN, 0.05, 2)
    for x in range(0, 24):
        cv.set(x, 5, WHITE)
        cv.set(x, 6, shade(WHITE, 0.82))
    gem(cv, 12, 2, 2, hx("#ffd27a"), WHITE)
    weave(cv, CAP_TOP, red, 0.06, 3)
    for k in range(0, 8):
        cv.set(k, 0, shade(red, 0.7))  # costuras (se ven en la cara de arriba)
    cv.rect(29, 3, 31, 5, shade(red, 0.6))
    cv.vgrad(*CAP_VISOR, shade(dark, 1.2), shade(dark, 0.8))
    cv.jitter(*CAP_VISOR, 0.05, 4)
    stitch(cv, 1, 23, 12, shade(dark, 1.5))
    stitch(cv, 1, 23, 16, shade(dark, 1.5))
    cv.frame(*CAP_VISOR, shade(dark, 0.55))
    weave(cv, CAP_STRAP, dark, 0.05, 5)
    return finish(cv, [CAP_CROWN, CAP_TOP, CAP_VISOR])


item("hat", "gorra", "Gorra", "comun", [
    B(0, -9.0, 0, 9.2, 2.4, 9.2, CAP_CROWN, CAP_TOP),
    B(0, -8.2, -5.5, 8.8, 0.5, 3.0, CAP_VISOR, CAP_VISOR),
    B(0, -8.4, 4.8, 6.0, 1.0, 0.6, CAP_STRAP, CAP_STRAP),
], paint_gorra, desc="Gorra roja con costuras y visera.", price=0)

# --- Sombrero de copa
TOP_BRIM, TOP_BODY, TOP_BTOP, TOP_BAND = (0, 0, 24, 24), (26, 0, 50, 24), (0, 26, 12, 38), (26, 26, 54, 32)


def paint_copa(cv):
    blk = hx("#15151d")
    cv.rect(*TOP_BRIM, blk)
    for y in range(24):
        for x in range(24):
            d = math.hypot(x - 11.5, y - 11.5)
            if 4 < d < 6:
                cv.set(x, y, shade(blk, 0.6))
    cv.jitter(*TOP_BRIM, 0.18, 5)
    cv.hgrad(*TOP_BODY, shade(blk, 1.7), shade(blk, 0.75))
    for y in range(0, 24):
        cv.set(29, y, shade(blk, 2.3))
        cv.set(30, y, shade(blk, 2.0))
    cv.jitter(*TOP_BODY, 0.05, 6)
    cv.rect(*TOP_BTOP, shade(blk, 1.1))
    cv.frame(*TOP_BTOP, shade(blk, 1.9))
    cv.rect(*TOP_BAND, hx("#8a5be0"))
    cv.vgrad(*TOP_BAND, hx("#a47af0"), hx("#6a43b8"))
    cv.rect(38, 26, 44, 32, hx("#f0c040"))
    cv.rect(40, 28, 42, 30, shade(hx("#f0c040"), 0.45))
    cv.frame(38, 26, 44, 32, hx("#9a7a18"))
    return finish(cv, [TOP_BRIM, TOP_BODY, TOP_BAND])


item("hat", "copa", "Sombrero de copa", "raro", [
    B(0, -8.3, 0, 12, 0.6, 12, TOP_BRIM, TOP_BRIM),
    B(0, -11.6, 0, 7, 6.6, 7, TOP_BODY, TOP_BTOP),
    B(0, -9.4, 0, 7.25, 1.1, 7.25, TOP_BAND, TOP_BAND),
], paint_copa, desc="Seda negra con cinta de amatista y hebilla.")

# --- Orejas de gato
CAT_FUR, CAT_PINK, CAT_BAND = (0, 0, 16, 16), (18, 0, 26, 8), (0, 18, 24, 24)


def paint_orejas(cv):
    fur = hx("#2a2a36")
    cv.rect(*CAT_FUR, fur)
    r = random.Random(11)
    for _ in range(40):
        cv.set(r.randrange(0, 16), r.randrange(0, 16), shade(fur, r.choice([1.5, 0.7])))
    cv.rect(*CAT_PINK, hx("#f08ad0"))
    for y in range(0, 8):
        for x in range(18, 26):
            if math.hypot(x - 21.5, y - 3.5) < 3:
                cv.set(x, y, hx("#ffc2ea"))
    cv.rect(*CAT_BAND, shade(fur, 0.9))
    cv.jitter(*CAT_BAND, 0.1, 12)
    return finish(cv, [CAT_FUR, CAT_BAND])


item("hat", "orejas", "Orejas de gato", "raro", [
    B(0, -8.2, 0, 8.6, 0.7, 1.6, CAT_BAND, CAT_BAND),
    B(3, -9.5, 0, 2.8, 1.6, 1.3, CAT_FUR, CAT_FUR),
    B(-3, -9.5, 0, 2.8, 1.6, 1.3, CAT_FUR, CAT_FUR),
    B(3, -10.8, 0, 1.6, 1.2, 1.1, CAT_FUR, CAT_FUR),
    B(-3, -10.8, 0, 1.6, 1.2, 1.1, CAT_FUR, CAT_FUR),
    B(3, -9.7, -0.7, 1.6, 1.6, 0.1, CAT_PINK, CAT_PINK),
    B(-3, -9.7, -0.7, 1.6, 1.6, 0.1, CAT_PINK, CAT_PINK),
], paint_orejas, desc="Pelaje oscuro con interior rosa.")

# --- Gorro de mago
WZ_BRIM, WZ_TIER, WZ_BAND, WZ_TIP = (0, 0, 24, 24), (26, 0, 50, 24), (0, 26, 28, 32), (52, 0, 60, 8)


def star(cv, cx, cy, c):
    cv.set(cx, cy, c)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        cv.set(cx + dx, cy + dy, mix(c, (90, 56, 176), 0.4))


def paint_mago(cv):
    pur, gold = hx("#5a38b0"), hx("#ffd27a")
    cv.rect(*WZ_BRIM, hx("#3b2a7a"))
    for y in range(24):
        for x in range(24):
            d = math.hypot(x - 11.5, y - 11.5)
            if 9.5 < d < 10.8:
                cv.set(x, y, gold)
    cv.jitter(*WZ_BRIM, 0.08, 21)
    cv.vgrad(*WZ_TIER, shade(pur, 1.2), shade(pur, 0.78))
    r = random.Random(22)
    for _ in range(7):
        star(cv, r.randrange(28, 48), r.randrange(2, 22), gold if r.random() < 0.6 else WHITE)
    cv.jitter(*WZ_TIER, 0.05, 23)
    cv.rect(*WZ_BAND, hx("#2a1d5c"))
    cv.rect(11, 26, 17, 32, gold)
    cv.rect(13, 28, 15, 30, shade(gold, 0.4))
    cv.frame(11, 26, 17, 32, shade(gold, 0.6))
    cv.rect(*WZ_TIP, gold)
    cv.jitter(*WZ_TIP, 0.1, 24)
    cv.set(53, 1, WHITE)
    return finish(cv, [WZ_BRIM, WZ_TIER, WZ_BAND])


item("hat", "mago", "Gorro de mago", "epico", [
    B(0, -8.3, 0, 11.4, 0.5, 11.4, WZ_BRIM, WZ_BRIM),
    B(0, -9.1, 0, 7.9, 1.2, 7.9, WZ_BAND, WZ_BAND),
    B(0, -11.0, 0, 7.4, 3.0, 7.4, WZ_TIER, WZ_TIER),
    B(0.4, -13.4, 0, 5.4, 2.4, 5.4, WZ_TIER, WZ_TIER),
    B(1.0, -15.5, 0, 3.6, 2.0, 3.6, WZ_TIER, WZ_TIER),
    B(1.8, -17.2, 0, 2.0, 1.6, 2.0, WZ_TIER, WZ_TIER),
    B(2.4, -18.5, 0, 1.4, 1.2, 1.4, WZ_TIP, WZ_TIP),
], paint_mago, desc="Gorro con estrellas doradas y borde bordado.")

# --- Corona
CR_RING, CR_SIDE, CR_SPIKE, CR_GEM = (0, 0, 28, 8), (30, 0, 38, 8), (0, 10, 8, 18), (10, 10, 14, 14)


def paint_corona(cv):
    gold = hx("#f0c040")
    cv.vgrad(*CR_RING, shade(gold, 1.25), shade(gold, 0.78))
    for x in range(28):
        cv.set(x, 0, shade(gold, 1.5))
        cv.set(x, 7, shade(gold, 0.55))
        if x % 4 < 2:
            cv.set(x, 3, shade(gold, 0.7))
    for cx in (5, 14, 23):
        gem(cv, cx, 4, 2, hx("#e03060") if cx != 14 else hx("#19b8e0"), WHITE)
    cv.jitter(*CR_RING, 0.05, 31)
    cv.rect(*CR_SIDE, gold)
    cv.jitter(*CR_SIDE, 0.12, 32)
    cv.vgrad(*CR_SPIKE, shade(gold, 1.4), shade(gold, 0.8))
    cv.jitter(*CR_SPIKE, 0.06, 33)
    cv.frame(*CR_SPIKE, shade(gold, 0.6))
    cv.rect(*CR_GEM, hx("#e03060"))
    cv.rect(11, 11, 13, 13, hx("#ff9ab8"))
    return finish(cv, [CR_RING, CR_SPIKE])


crown_spikes = [B(x, -10.5, z, 1.6, 1.8, 1.6, CR_SPIKE, CR_SPIKE) for x in (-3.3, 0.0, 3.3) for z in (-3.3, 3.3) if not (x == 0.0 and z == -3.3)]
item("hat", "corona", "Corona", "legendario", [
    B(0, -8.9, 0, 8.8, 1.8, 8.8, CR_RING, CR_SIDE),
    *crown_spikes,
    B(0, -10.8, -3.5, 2.0, 2.6, 1.2, CR_SPIKE, CR_SPIKE),
    B(0, -9.0, -4.5, 1.6, 1.4, 0.2, CR_GEM, CR_GEM, glow=True),
], paint_corona, desc="Oro con gemas y puntas brillantes.")

# --- Halo
HL_BAR = (0, 0, 16, 4)


def paint_halo(cv):
    for x in range(16):
        t = abs(x - 7.5) / 8
        for y in range(4):
            c = mix(hx("#fff6c8"), hx("#ffd966"), t * 0.8 + (y / 3) * 0.2)
            cv.set(x, y, c)
    for x in range(1, 16, 4):
        cv.set(x, 1, WHITE)
    return cv


item("hat", "halo", "Halo", "legendario", [
    B(0, -12.0, -3.6, 8.2, 0.9, 1.1, HL_BAR, HL_BAR, glow=True),
    B(0, -12.0, 3.6, 8.2, 0.9, 1.1, HL_BAR, HL_BAR, glow=True),
    B(-3.6, -12.0, 0, 1.1, 0.9, 6.1, HL_BAR, HL_BAR, glow=True),
    B(3.6, -12.0, 0, 1.1, 0.9, 6.1, HL_BAR, HL_BAR, glow=True),
], paint_halo, desc="Aro de luz dorada flotando sobre tu cabeza.")

# --- Casco vikingo
VK_DOME, VK_TOP, VK_HORN, VK_BAND = (0, 0, 24, 10), (26, 0, 34, 8), (0, 12, 8, 20), (10, 12, 34, 16)


def paint_vikingo(cv):
    steel = hx("#8e95a8")
    cv.vgrad(*VK_DOME, shade(steel, 1.25), shade(steel, 0.75))
    for x in range(1, 24, 4):  # remaches
        cv.set(x, 6, shade(steel, 1.7))
        cv.set(x, 7, shade(steel, 0.5))
    for x in range(24):
        cv.set(x, 9, hx("#5a3a1e"))
    cv.jitter(*VK_DOME, 0.06, 61)
    cv.rect(*VK_TOP, shade(steel, 1.05))
    cv.frame(*VK_TOP, shade(steel, 0.6))
    ivory = hx("#f1e8cf")
    cv.vgrad(*VK_HORN, ivory, shade(ivory, 0.78))
    for y in (14, 17):
        for x in range(8):
            cv.set(x, y, shade(ivory, 0.62))
    cv.hgrad(*VK_BAND, hx("#6a4424"), hx("#4a2e16"))
    for x in range(11, 33, 4):
        cv.set(x, 14, hx("#e0b040"))
    return finish(cv, [VK_DOME, VK_TOP, VK_BAND])


vk = [
    B(0, -9.6, 0, 9.0, 3.2, 9.0, VK_DOME, VK_TOP),
    B(0, -8.3, 0, 9.3, 0.8, 9.3, VK_BAND, VK_BAND),
]
for sx in (-1, 1):
    vk += [
        B(sx * 5.2, -9.4, 0, 1.8, 1.8, 1.8, VK_HORN, VK_HORN),
        B(sx * 6.4, -10.8, 0, 1.6, 1.8, 1.6, VK_HORN, VK_HORN),
        B(sx * 7.2, -12.4, 0, 1.4, 1.8, 1.4, VK_HORN, VK_HORN),
        B(sx * 7.6, -14.0, 0, 1.0, 1.6, 1.0, VK_HORN, VK_HORN),
    ]
item("hat", "vikingo", "Casco vikingo", "epico", vk, paint_vikingo, desc="Casco de acero con remaches y cuernos de marfil.")

# ================================================================ GAFAS (cabeza)
GL_LENS, GL_FRAME, GL_BAR = (0, 0, 16, 10), (18, 0, 22, 4), (0, 12, 32, 18)


def paint_glasses(kind, lens, frame):
    def go(cv):
        l, f = hx(lens), hx(frame)
        if kind == "visor":
            cv.hgrad(*GL_BAR, hx("#5a38b0"), hx("#c8a8ff"))
            for x in range(0, 32, 4):
                cv.set(x, 14, mix(hx("#c8a8ff"), WHITE, 0.6))
                cv.set(x + 1, 15, mix(hx("#c8a8ff"), WHITE, 0.4))
            cv.frame(*GL_BAR, hx("#2a1d5c"))
            cv.rect(*GL_FRAME, hx("#2a1d5c"))
            return cv
        for y in range(10):
            for x in range(16):
                if kind == "heart":
                    # corazón
                    fx, fy = (x - 7.5) / 6.4, (y - 4.2) / 4.6
                    inside = (fx * fx + (fy - math.sqrt(abs(fx))) ** 2) <= 1.0
                    if not inside:
                        continue
                cv.set(x, y, mix(shade(l, 1.1), shade(l, 0.7), y / 9))
        if kind == "sun":
            for k in range(0, 9):
                cv.set(2 + k, 7 - k // 2, mix(hx("#3a3a50"), WHITE, 0.3))
        elif kind == "neon":
            for k in range(0, 8):
                cv.set(2 + k, 6 - k // 2, mix(l, WHITE, 0.7))
            cv.frame(0, 0, 16, 10, mix(l, WHITE, 0.5))
        elif kind == "heart":
            cv.set(4, 2, WHITE)
            cv.set(5, 2, WHITE)
            cv.set(4, 3, WHITE)
        cv.rect(*GL_FRAME, f)
        return cv

    return go


GL_BOXES = [
    B(2.2, -4.6, -4.4, 3.4, 2.2, 0.5, GL_LENS, GL_FRAME),
    B(-2.2, -4.6, -4.4, 3.4, 2.2, 0.5, GL_LENS, GL_FRAME),
    B(0, -4.9, -4.4, 1.2, 0.6, 0.5, GL_FRAME, GL_FRAME),
    B(4.15, -4.9, -2.2, 0.4, 0.5, 4.3, GL_FRAME, GL_FRAME),
    B(-4.15, -4.9, -2.2, 0.4, 0.5, 4.3, GL_FRAME, GL_FRAME),
]
item("glasses", "sol", "Gafas de sol", "comun", [dict(b) for b in GL_BOXES], paint_glasses("sun", "#0d0d12", "#0d0d12"), desc="Lentes oscuros con un reflejo.", price=0)
neon_boxes = [dict(b) for b in GL_BOXES]
neon_boxes[0]["glow"] = neon_boxes[1]["glow"] = True
item("glasses", "neon", "Gafas neón", "raro", neon_boxes, paint_glasses("neon", "#19e3ff", "#0a7f93"), desc="Lentes de neón que brillan.")
heart_boxes = [dict(b) for b in GL_BOXES]
heart_boxes[0].update(w=3.8, h=2.8, y=-4.4)
heart_boxes[1].update(w=3.8, h=2.8, y=-4.4)
item("glasses", "corazon", "Corazón", "epico", heart_boxes, paint_glasses("heart", "#ff4f8b", "#ffd0e0"), desc="Lentes en forma de corazón.")
item("glasses", "visor", "Visor", "legendario", [
    B(0, -4.6, -4.5, 8.8, 2.2, 0.7, GL_BAR, GL_FRAME, glow=True),
    B(4.15, -4.9, -2.2, 0.4, 0.5, 4.3, GL_FRAME, GL_FRAME),
    B(-4.15, -4.9, -2.2, 0.4, 0.5, 4.3, GL_FRAME, GL_FRAME),
], paint_glasses("visor", "#8a5be0", "#5a38b0"), desc="Visor de energía que brilla.")

# --- Gafas pixel (estilo "deal with it")
GL_PIX = (0, 20, 32, 26)


def paint_pixshades(cv):
    for y in range(6):
        for x in range(32):
            cv.set(x, y + 20, hx("#0b0b10"))
    for x in range(32):  # muescas escalonadas abajo y en el puente
        cut = 0
        if x in (0, 1, 30, 31):
            cut = 2
        elif x in (2, 3, 28, 29):
            cut = 1
        elif 13 <= x <= 18:
            cut = 3
        elif x in (12, 19):
            cut = 2
        for k in range(cut):
            cv.p[x, 25 - k] = (0, 0, 0, 0)
    for x in range(2, 12):  # brillo en los pixeles
        cv.set(x, 21, hx("#2a2a36"))
    for x in range(20, 30):
        cv.set(x, 21, hx("#2a2a36"))
    cv.set(4, 22, WHITE); cv.set(5, 22, WHITE); cv.set(22, 22, WHITE); cv.set(23, 22, WHITE)
    cv.rect(*GL_FRAME, hx("#0b0b10"))
    return cv


item("glasses", "pixel", "Gafas pixel", "epico", [
    B(0, -4.6, -4.5, 9.0, 2.2, 0.7, GL_PIX, GL_FRAME),
    B(4.15, -4.9, -2.2, 0.4, 0.5, 4.3, GL_FRAME, GL_FRAME),
    B(-4.15, -4.9, -2.2, 0.4, 0.5, 4.3, GL_FRAME, GL_FRAME),
], paint_pixshades, desc="Gafas de 8 bits: el clásico meme en tu cara.")

# ================================================================ MOCHILAS (torso)
PK_MAIN, PK_SIDE, PK_LID, PK_POCKET = (0, 0, 24, 32), (26, 0, 34, 8), (0, 34, 26, 44), (28, 34, 44, 46)


def paint_pack(c1, c2, dots=False):
    def go(cv):
        a, b = hx(c1), hx(c2)
        cv.vgrad(*PK_MAIN, shade(a, 1.12), shade(a, 0.82))
        cv.jitter(*PK_MAIN, 0.07, 41)
        for x in range(2, 22, 3):  # costura punteada
            cv.set(x, 2, shade(a, 1.5))
            cv.set(x, 29, shade(a, 1.5))
        for y in range(2, 30, 3):
            cv.set(2, y, shade(a, 1.5))
            cv.set(21, y, shade(a, 1.5))
        for y in range(0, 32):
            cv.set(11, y, shade(a, 0.7))
            cv.set(12, y, shade(a, 1.2))
        if dots:
            r = random.Random(44)
            for _ in range(14):
                cx, cy = r.randrange(3, 21), r.randrange(4, 28)
                cv.set(cx, cy, WHITE)
                cv.set(cx + 1, cy, shade(WHITE, 0.85))
        cv.frame(*PK_MAIN, shade(a, 0.5))
        cv.rect(*PK_SIDE, b)
        cv.jitter(*PK_SIDE, 0.1, 42)
        cv.vgrad(*PK_LID, shade(b, 1.15), shade(b, 0.8))
        cv.jitter(*PK_LID, 0.06, 43)
        cv.frame(*PK_LID, shade(b, 0.5))
        gold = hx("#e0b040")
        cv.rect(11, 38, 15, 43, gold)
        cv.rect(12, 39, 14, 42, shade(gold, 0.45))
        cv.vgrad(*PK_POCKET, shade(a, 0.95), shade(a, 0.72))
        cv.jitter(*PK_POCKET, 0.06, 45)
        for x in range(29, 43, 2):
            cv.set(x, 36, shade(a, 1.6))
        cv.frame(*PK_POCKET, shade(a, 0.5))
        return finish(cv, [PK_MAIN, PK_LID, PK_POCKET])

    return go


PACK_BOXES = [
    B(0, 6.0, 3.7, 6, 8, 3, PK_MAIN, PK_SIDE),
    B(0, 3.3, 3.95, 6.3, 2.8, 3.4, PK_LID, PK_SIDE),
    B(0, 8.8, 5.45, 4, 3, 0.6, PK_POCKET, PK_SIDE),
]
item("pack", "mochila", "Mochila", "comun", [dict(b) for b in PACK_BOXES], paint_pack("#8a5a2b", "#6b4120"), desc="Mochila de cuero con hebilla y bolsillo.", price=0)
item("pack", "rosa", "Mochila rosa", "raro", [dict(b) for b in PACK_BOXES], paint_pack("#e86aa6", "#b84e82", dots=True), desc="Mochila rosa con lunares de brillo.")

# --- Jetpack
JT_TANK, JT_EDGE, JT_CAP, JT_NOZ, JT_FLAME, JT_PIPE = (0, 0, 12, 36), (14, 0, 18, 4), (20, 0, 32, 8), (20, 10, 32, 18), (34, 0, 42, 12), (34, 14, 44, 18)


def paint_jet(cv):
    steel, red = hx("#8b8fa3"), hx("#c23b3b")
    for x in range(12):
        k = 0.78 + 0.5 * math.cos((x - 5.5) / 5.5 * 1.4)
        for y in range(36):
            cv.set(x, y, shade(steel, k))
    for x in range(12):  # banda roja y franjas de aviso
        for y in range(3, 7):
            cv.set(x, y, shade(red, 0.75 + 0.5 * math.cos((x - 5.5) / 5.5 * 1.4) * 0.5))
        for y in range(28, 32):
            cv.set(x, y, hx("#f0c040") if (x + y) % 4 < 2 else hx("#1a1a22"))
    for y in range(36):
        cv.set(0, y, shade(steel, 0.5))
        cv.set(11, y, shade(steel, 0.5))
    for y in (12, 20):
        for x in range(12):
            cv.set(x, y, shade(steel, 0.6))
    for x, y in ((3, 15), (8, 15), (3, 23), (8, 23)):
        cv.set(x, y, shade(steel, 1.5))
    cv.jitter(*JT_TANK, 0.04, 51)
    cv.rect(*JT_EDGE, hx("#4a4d5c"))
    cv.vgrad(*JT_CAP, hx("#d94a4a"), hx("#8a2020"))
    cv.frame(*JT_CAP, hx("#5a1414"))
    cv.vgrad(*JT_NOZ, hx("#5a5d70"), hx("#2a2c38"))
    for x in range(20, 32, 3):
        cv.line(x, 10, x, 17, hx("#15151d"))
    for y in range(12):
        t = y / 11
        c = mix(WHITE, hx("#19e3ff"), min(1, t * 1.6))
        for x in range(34, 42):
            w = 3.6 * (1 - t * 0.8)
            if abs(x - 37.5) <= w:
                cv.set(x, y, c, int(255 * (1 - t * 0.55)))
    cv.vgrad(*JT_PIPE, hx("#6a6d80"), hx("#3a3c4a"))
    return finish(cv, [JT_TANK, JT_CAP, JT_NOZ])


jet_boxes = []
for s in (1, -1):
    jet_boxes += [
        B(s * 2.4, 6.2, 4.4, 3.4, 9, 3.4, JT_TANK, JT_EDGE),
        B(s * 2.4, 1.2, 4.4, 3.6, 1.2, 3.6, JT_CAP, JT_CAP),
        B(s * 2.4, 11.4, 4.4, 2.6, 1.4, 2.6, JT_NOZ, JT_NOZ),
        B(s * 2.4, 13.6, 4.4, 1.8, 2.6, 1.8, JT_FLAME, JT_FLAME, glow=True, flags=1),
    ]
jet_boxes.append(B(0, 3.4, 4.4, 2.6, 1.2, 1.2, JT_PIPE, JT_PIPE))
item("pack", "jetpack", "Jetpack", "epico", jet_boxes, paint_jet, desc="Dos tanques con llama que se enciende al saltar.")


# ================================================================ GENERACIÓN NUEVA: ÁNGEL / DEMONIO / VACÍO + PAYASO
# Cada set tiene alas, sombrero, espalda, botas, puños y aura. Más la cara de payaso.
# Coordenadas por categoría:
#   wings/pack/back/aura -> torso (y=0 es la línea del hombro)      hat/glasses/face -> cabeza
#   boots -> pierna (y=0 cadera, 12 = suelo; x=0 centro de la pierna; se refleja solo)
#   fists -> brazo  (y=-2 hombro, 10 = fondo de la mano; x=0 centro del brazo; se refleja solo)
from PIL import ImageDraw

ANG = dict(w=hx("#f7f5ff"), l=hx("#e4defa"), s=hx("#b6aedf"), out=hx("#8d84c4"), gold=hx("#f0c040"), gl=hx("#ffe58a"), gd=hx("#a8741c"))
HEL = dict(k=hx("#14070c"), d=hx("#3a0b14"), r=hx("#a3121e"), b=hx("#ff3a1c"), o=hx("#ff8a1a"), y=hx("#ffd23c"), bone=hx("#2d1a20"), out=hx("#0d0306"))
VOI = dict(k=hx("#0b0916"), d=hx("#17122e"), m=hx("#2b2058"), p=hx("#6a43b8"), g=hx("#a47af0"), l=hx("#dcc2ff"), out=hx("#06040d"))


def ramp_v(cv, r, cols, jit=0.04, seed=1):
    """Degradado vertical con varias paradas."""
    x0, y0, x1, y1 = r
    n = len(cols) - 1
    for y in range(y0, y1):
        t = (y - y0) / max(1, y1 - y0 - 1) * n
        i = min(n - 1, int(t))
        c = mix(cols[i], cols[i + 1], t - i)
        for x in range(x0, x1):
            cv.set(x, y, c)
    if jit:
        cv.jitter(x0, y0, x1, y1, jit, seed)


def blob(cv, x0, y0, w, h, inside, colfn, out):
    """Sprite: inside(u,v) con u,v en [-1,1]; colfn(x,y,u,v) -> color; contorno de 1 px en `out`."""
    m = [[inside((x + 0.5) / w * 2 - 1, (y + 0.5) / h * 2 - 1) for x in range(w)] for y in range(h)]

    def on(x, y):
        return 0 <= x < w and 0 <= y < h and m[y][x]

    for y in range(h):
        for x in range(w):
            if not m[y][x]:
                continue
            if not (on(x + 1, y) and on(x - 1, y) and on(x, y + 1) and on(x, y - 1)):
                cv.set(x0 + x, y0 + y, out)
            else:
                cv.set(x0 + x, y0 + y, colfn(x, y, (x + 0.5) / w * 2 - 1, (y + 0.5) / h * 2 - 1))


def spr_feather(cv, r, body, vein, out):
    x0, y0, x1, y1 = r
    blob(cv, x0, y0, x1 - x0, y1 - y0,
         lambda u, v: abs(u) <= (max(0.0, 1 - v * v) ** 0.5) * (1 - 0.4 * (v + 1) / 2),
         lambda x, y, u, v: vein if abs(u) < 0.12 else shade(body, 1.08 - 0.22 * (v + 1) / 2 + (0.05 if (x + y) % 3 == 0 else 0)), out)


def spr_crystal(cv, r, body, edge, glow):
    x0, y0, x1, y1 = r
    blob(cv, x0, y0, x1 - x0, y1 - y0,
         lambda u, v: abs(u) <= (1 - abs(v) ** 1.5) * 0.95 if abs(v) > 0.45 else abs(u) <= 0.95,
         lambda x, y, u, v: glow if abs(u) < 0.14 else (mix(body, edge, 0.55) if u < -0.25 else (shade(body, 0.7) if u > 0.25 else body)), edge)


def spr_flame(cv, r, cols):
    x0, y0, x1, y1 = r
    blob(cv, x0, y0, x1 - x0, y1 - y0,
         lambda u, v: abs(u) <= max(0.0, ((v + 1) / 2)) ** 0.75 * (0.95 + 0.25 * math.sin(v * 6)) * 0.95,
         lambda x, y, u, v: cols[min(len(cols) - 1, max(0, int(((1 - (v + 1) / 2) * 0.7 + abs(u) * 0.5 + ((x + y) % 2) * 0.07) * len(cols))))],
         cols[-1])


def cube_tex(cv, r, base, lit, dark, edge):
    """Cubito/gema: cara con brillo arriba-izquierda y borde."""
    x0, y0, x1, y1 = r
    for y in range(y0, y1):
        for x in range(x0, x1):
            k = ((x - x0) + (y - y0)) / max(1, (x1 - x0) + (y1 - y0) - 2)
            cv.set(x, y, mix(lit, dark, k) if (x > x0 and y > y0 and x < x1 - 1 and y < y1 - 1) else edge)
    cv.set(x0 + 1, y0 + 1, WHITE)


# ---------------------------------------------------------------- ALAS: plumas (ángel y vacío) y membrana (demonio)
def feather_layer(cv, ox, oy, W, H, n, kind, pal, bright, seed):
    S = (0.02 * W, 0.21 * H)
    Wr = (0.54 * W, 0.09 * H)
    fs = []
    for k in range(n):
        u = k / (n - 1)
        tip = (0.17 * W + 0.80 * W * u, 0.93 * H - 0.42 * H * u ** 1.2)
        base = (S[0] + (Wr[0] - S[0]) * u ** 0.9, S[1] + (Wr[1] - S[1]) * u ** 0.9)
        dx, dy = tip[0] - base[0], tip[1] - base[1]
        fs.append(dict(bx=base[0], by=base[1], ang=math.atan2(dy, dx), L=math.hypot(dx, dy),
                       wd=W * (0.085 if kind == "angel" else 0.098), br=1.0))
    fs.reverse()  # lo más externo primero; lo interno tapa
    if kind == "angel":  # coberteras: dos filas de plumas cortas sobre el borde de ataque
        for row, (ln, off) in enumerate(((0.34, 0.03), (0.25, 0.15))):
            m = 8 - row
            for j in range(m):
                u = j / (m - 1)
                bx = S[0] + (Wr[0] - S[0]) * u
                by = S[1] + (Wr[1] - S[1]) * u + off * H
                fs.append(dict(bx=bx, by=by, ang=math.radians(84 - 46 * u), L=ln * H, wd=W * 0.075, br=1.05 + 0.04 * row))
    lab = [[-1] * W for _ in range(H)]
    par = {}
    for i, f in enumerate(fs):
        ca, sa = math.cos(f["ang"]), math.sin(f["ang"])
        for y in range(H):
            for x in range(W):
                dx, dy = x + 0.5 - f["bx"], y + 0.5 - f["by"]
                t = (dx * ca + dy * sa) / f["L"]
                s = -dx * sa + dy * ca
                if t < -0.02 or t > 1.0:
                    continue
                t = max(0.0, t)
                if kind == "angel":
                    hw = f["wd"] * max(0.0, 1 - t ** 2.6) ** 0.55 * (0.5 + 0.5 * min(1.0, t * 5))
                else:  # cuchilla de cristal: ancha al inicio y afilada
                    hw = f["wd"] * (1 - t) ** 0.8 * (0.4 + 0.6 * min(1.0, t * 3.2))
                if abs(s) <= hw:
                    lab[y][x] = i
                    par[(x, y)] = (t, s, hw)

    def L(x, y):
        if x < 0:
            return -2  # el borde del hombro no lleva contorno
        if x >= W or y < 0 or y >= H:
            return -1
        return lab[y][x]

    for (x, y), (t, s, hw) in par.items():
        i = lab[y][x]
        f = fs[i]
        nb = [L(x + 1, y), L(x - 1, y), L(x, y + 1), L(x, y - 1)]
        if any(v == -1 for v in nb):
            col = pal["out"]
        else:
            if kind == "angel":
                col = mix(shade(pal["c0"], f["br"] * bright), pal["c1"], t * 0.55)
                if abs(s) < 0.7:
                    col = shade(col, 0.9)
                elif (int(t * f["L"] * 0.8) + int(abs(s) * 1.3)) % 4 == 0:
                    col = shade(col, 0.94)
            else:
                col = mix(pal["c0"], pal["c1"], min(1.0, t * 0.9 + abs(s) / max(0.5, hw) * 0.35))
                col = shade(col, bright)
                if abs(s) < 0.6:
                    col = pal["vein"] if t > 0.12 else shade(col, 1.3)
                elif abs(s) / max(0.5, hw) > 0.78:
                    col = mix(col, pal["edge"], 0.7)
            if any(v > i for v in nb):
                col = shade(col, 0.74)
        cv.set(ox + x, oy + y, col)
    if kind == "shard":  # esquirlas sueltas cerca de las puntas
        r = random.Random(seed)
        for _ in range(4):
            cx, cy = int(W * r.uniform(0.74, 0.97)), int(H * r.uniform(0.04, 0.5))
            if lab[min(H - 1, cy)][min(W - 1, cx)] != -1:
                continue
            for dy in range(-3, 4):
                for dx in range(-1, 2):
                    if abs(dx) * 3 + abs(dy) <= 3:
                        cv.set(ox + cx + dx, oy + cy + dy, pal["edge"] if abs(dx) * 3 + abs(dy) == 3 else pal["vein"])


def bat_layer(cv, ox, oy, W, H, pal, warm, seed):
    S = (1.0, 0.22 * H)
    Wr = (0.58 * W, 0.07 * H)
    T = [(0.97 * W, 0.30 * H), (0.84 * W, 0.62 * H), (0.57 * W, 0.90 * H), (0.24 * W, 0.97 * H)]
    Ab = (1.0, 0.80 * H)

    def scal(P, Q):
        M = ((P[0] + Q[0]) / 2, (P[1] + Q[1]) / 2)
        C = (M[0] + (Wr[0] - M[0]) * 0.42, M[1] + (Wr[1] - M[1]) * 0.42)
        o = []
        for i in range(1, 13):
            t = i / 12
            o.append(((1 - t) ** 2 * P[0] + 2 * (1 - t) * t * C[0] + t * t * Q[0], (1 - t) ** 2 * P[1] + 2 * (1 - t) * t * C[1] + t * t * Q[1]))
        return o

    pts = [S, Wr, T[0]]
    seq = T + [Ab]
    for i in range(len(seq) - 1):
        pts += scal(seq[i], seq[i + 1])
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).polygon([(int(round(p[0])), int(round(p[1]))) for p in pts], fill=255)
    m = mask.load()
    far = math.hypot(W, H) * 0.62

    def on(x, y):
        return 0 <= x < W and 0 <= y < H and m[x, y] > 0

    for y in range(H):
        for x in range(W):
            if not on(x, y):
                continue
            d = min(1.0, math.hypot(x - Wr[0], y - Wr[1]) / far)
            col = dither(mix(pal["r"], pal["d"], d), 1.1 * warm - 0.45 * d, x, y, 9, 0.5, 1.25)
            cv.set(ox + x, oy + y, col)
    vein = mix(pal["r"], pal["b"], 0.75)
    for i in range(3):  # venas hacia el centro de cada festón
        a, b = T[i], T[i + 1]
        mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        e = (Wr[0] + (mid[0] - Wr[0]) * 0.86, Wr[1] + (mid[1] - Wr[1]) * 0.86)
        for (px, py) in _line_pts(int(Wr[0]), int(Wr[1]), int(e[0]), int(e[1])):
            if on(px, py):
                cv.set(ox + px, oy + py, vein)
    bone, hi = pal["bone"], mix(pal["bone"], pal["r"], 0.55)
    for t in T:
        for dy, c in ((1, shade(bone, 0.8)), (0, bone), (-1, hi)):
            cv.line(ox + int(Wr[0]), oy + int(Wr[1]) + dy, ox + int(t[0]), oy + int(t[1]) + dy, c)
    for dy, c in ((2, shade(bone, 0.8)), (1, bone), (0, bone), (-1, hi)):
        cv.line(ox + int(S[0]), oy + int(S[1]) + dy, ox + int(Wr[0]), oy + int(Wr[1]) + dy, c)
    for t in T:  # garras
        cv.set(ox + int(t[0]), oy + int(t[1]), pal["y"])
        cv.set(ox + int(t[0]) - 1, oy + int(t[1]), pal["o"])
    for y in range(H):  # contorno
        for x in range(W):
            if on(x, y) and x > 0 and not (on(x + 1, y) and on(x - 1, y) and on(x, y + 1) and on(x, y - 1)):
                cv.set(ox + x, oy + y, pal["out"])
    r = random.Random(seed)
    for _ in range(18):  # brasas en el borde inferior
        x, y = r.randrange(2, W - 2), r.randrange(int(H * 0.3), H - 2)
        if on(x, y) and on(x, y - 1) and on(x + 1, y) and on(x - 1, y) and on(x, y + 1):
            cv.set(ox + x, oy + y, r.choice([pal["o"], pal["y"], pal["b"]]))


def _line_pts(x0, y0, x1, y1):
    out = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        out.append((x0, y0))
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy
    return out


WL1, WL2, WL3, WEDGE = (0, 0, 48, 56), (50, 0, 90, 48), (92, 0, 120, 32), (96, 40, 100, 44)


def paint_set_wings(kind):
    def go(cv):
        if kind == "angel":
            pal = dict(c0=ANG["w"], c1=ANG["l"], out=ANG["out"])
            for (r, n, br, sd) in ((WL1, 10, 0.88, 1), (WL2, 8, 0.97, 2), (WL3, 6, 1.06, 3)):
                feather_layer(cv, r[0], r[1], r[2] - r[0], r[3] - r[1], n, "angel", pal, br, sd)
            cv.rect(*WEDGE, ANG["out"])
        elif kind == "hell":
            pal = dict(r=hx("#8c1420"), d=hx("#240609"), b=HEL["b"], o=HEL["o"], y=HEL["y"], bone=hx("#6a323a"), out=HEL["out"])
            bat_layer(cv, WL1[0], WL1[1], 48, 56, pal, 0.95, 5)
            pal2 = dict(pal, r=hx("#b3202a"), d=hx("#4a0b12"))
            bat_layer(cv, WL2[0], WL2[1], 40, 48, pal2, 1.05, 9)
            cv.rect(*WEDGE, HEL["out"])
        else:
            pal = dict(c0=hx("#2a2058"), c1=hx("#5a43a8"), vein=VOI["g"], edge=VOI["p"], out=VOI["out"])
            for (r, n, br, sd) in ((WL1, 7, 0.85, 1), (WL2, 6, 1.0, 2), (WL3, 5, 1.2, 3)):
                feather_layer(cv, r[0], r[1], r[2] - r[0], r[3] - r[1], n, "shard", pal, br, sd)
            cv.rect(*WEDGE, VOI["out"])
        return cv
    return go


def wing_layers(three=True, glow_last=False):
    b = [B(12, 11, 0.0, 24, 28, 0.3, WL1, WEDGE), B(10, 11, 0.45, 20, 24, 0.3, WL2, WEDGE)]
    if three:
        b.append(B(7, 8, 0.9, 14, 16, 0.3, WL3, WEDGE, glow=glow_last))
    return b


item("wings", "angel_wings", "Alas de ángel", "legendario", wing_layers(), paint_set_wings("angel"),
     price=2400, desc="Tres capas de plumas blancas con coberteras. Gran envergadura.", ts=128)
item("wings", "hell_wings", "Alas del Soberano", "legendario", wing_layers(False), paint_set_wings("hell"),
     price=2400, desc="Membrana de murciélago infernal con venas de lava y brasas.", ts=128)
item("wings", "void_wings", "Alas del Vacío", "legendario", wing_layers(True, True), paint_set_wings("void"),
     price=2400, desc="Cuchillas de cristal oscuro con venas violeta y esquirlas flotando.", ts=128)


# ---------------------------------------------------------------- SOMBREROS
# Halo celestial
H_RING, H_FEATHER, H_MOTE = (0, 0, 16, 4), (0, 6, 8, 14), (10, 6, 14, 10)


def paint_angel_halo(cv):
    for x in range(16):
        t = abs(x - 7.5) / 8
        for y in range(4):
            cv.set(x, y, mix(hx("#fffbe0"), ANG["gold"], t * 0.7 + (y / 3) * 0.3))
    for x in range(1, 16, 4):
        cv.set(x, 1, WHITE)
    spr_feather(cv, H_FEATHER, ANG["w"], ANG["gl"], ANG["gd"])
    cube_tex(cv, H_MOTE, hx("#fff3a0"), WHITE, ANG["gold"], ANG["gd"])
    return cv


item("hat", "angel_halo", "Halo celestial", "epico", [
    B(0, -13.0, -4.2, 9.2, 0.8, 1.1, H_RING, H_RING, glow=True, an=BOB, ap=0.5, asp=0.5),
    B(0, -13.0, 4.2, 9.2, 0.8, 1.1, H_RING, H_RING, glow=True, an=BOB, ap=0.5, asp=0.5),
    B(-4.2, -13.0, 0, 1.1, 0.8, 7.3, H_RING, H_RING, glow=True, an=BOB, ap=0.5, asp=0.5),
    B(4.2, -13.0, 0, 1.1, 0.8, 7.3, H_RING, H_RING, glow=True, an=BOB, ap=0.5, asp=0.5),
    B(5.2, -9.2, 0.6, 0.3, 4.4, 3.6, H_FEATHER, H_FEATHER, ry=-22),
    B(-5.2, -9.2, 0.6, 0.3, 4.4, 3.6, H_FEATHER, H_FEATHER, ry=22),
    B(2.6, -15.6, 0, 1.1, 1.1, 1.1, H_MOTE, H_MOTE, glow=True, an=BOB, ap=0.9, asp=0.7, aph=0),
    B(-2.4, -16.0, 1.0, 0.9, 0.9, 0.9, H_MOTE, H_MOTE, glow=True, an=BOB, ap=0.9, asp=0.7, aph=180),
], paint_angel_halo, price=1100, desc="Aro de luz dorada con plumas y destellos que flotan.")

# Cuernos infernales
HN = [(0, 0, 8, 8), (0, 8, 8, 16), (0, 16, 8, 24), (0, 24, 8, 32), (0, 32, 8, 40)]
HN_FLAME = (10, 0, 18, 10)


def paint_hell_horns(cv):
    ramps = [(HEL["k"], HEL["d"]), (HEL["d"], hx("#5a0f18")), (hx("#5a0f18"), HEL["r"]), (HEL["r"], hx("#d02a1c")), (HEL["b"], HEL["o"])]
    for r, (a, b) in zip(HN, ramps):
        ramp_v(cv, r, [a, b], 0.05, r[1])
        for y in range(r[1] + 2, r[3], 3):  # anillos de crecimiento
            for x in range(r[0], r[2]):
                cv.set(x, y, shade(cv.p[x, y][:3], 0.7))
    spr_flame(cv, HN_FLAME, [hx("#fff3b0"), HEL["y"], HEL["o"], HEL["b"], hx("#a3121e")])
    return cv


def horn_boxes():
    out = []
    spec = [((3.7, -8.7, 0.0), (2.4, 1.8, 2.4)), ((4.7, -10.3, -0.1), (2.1, 1.9, 2.1)), ((5.9, -11.7, -0.4), (1.9, 1.8, 1.9)),
            ((7.2, -12.9, -0.9), (1.6, 1.7, 1.6)), ((8.5, -13.8, -1.6), (1.3, 1.5, 1.3))]
    for sx in (-1, 1):
        for i, ((x, y, z), (w, h, d)) in enumerate(spec):
            out.append(B(sx * x, y, z, w * 1.3, h * 1.25, d * 1.3, HN[i], HN[i], glow=(i == 4), ry=sx * (-6 * i)))
        out.append(B(sx * 8.9, -15.2, -1.9, 1.4, 2.0, 1.4, HN_FLAME, HN_FLAME, glow=True, an=PULSE, ap=0.3, asp=2.2, aph=0 if sx > 0 else 140))
    return out


item("hat", "hell_horns", "Cuernos infernales", "epico", horn_boxes(), paint_hell_horns, price=1100,
     desc="Cuernos del Soberano: negros en la raíz y al rojo vivo en la punta, con llama.")

# Corona del Vacío
VC_SHARD, VC_BASE, VC_CRY = (0, 0, 8, 16), (10, 0, 26, 4), (28, 0, 38, 16)


def paint_void_crown(cv):
    ramp_v(cv, VC_SHARD, [VOI["g"], VOI["p"], VOI["m"], VOI["k"]], 0.05, 3)
    for y in range(0, 16, 4):
        for x in range(0, 8):
            if (x + y) % 5 == 0:
                cv.set(x, y, VOI["l"])
    cv.frame(*VC_SHARD, VOI["out"])
    for x in range(16):
        for y in range(4):
            cv.set(10 + x, y, mix(VOI["m"], VOI["g"], 0.8) if y == 0 else (VOI["k"] if y == 3 else VOI["d"]))
    spr_crystal(cv, VC_CRY, VOI["m"], VOI["g"], VOI["l"])
    return cv


def void_crown_boxes():
    b = [B(0, -8.7, -4.5, 9.4, 0.7, 0.8, VC_BASE, VC_BASE, glow=True), B(0, -8.7, 4.5, 9.4, 0.7, 0.8, VC_BASE, VC_BASE, glow=True),
         B(-4.5, -8.7, 0, 0.8, 0.7, 8.2, VC_BASE, VC_BASE, glow=True), B(4.5, -8.7, 0, 0.8, 0.7, 8.2, VC_BASE, VC_BASE, glow=True)]
    hs = [4.2, 2.6, 3.8, 2.4, 4.6, 2.8, 3.6, 2.5]
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        h = hs[k]
        b.append(B(4.5 * math.cos(a), -8.6 - h / 2, 4.5 * math.sin(a), 1.4, h, 1.4, VC_SHARD, VC_SHARD, ry=-math.degrees(a),
                   an=BOB, ap=0.45, asp=0.4, aph=k * 47))
    b.append(B(0, -18.0, 0, 2.6, 4.6, 2.6, VC_CRY, VC_CRY, glow=True, an=BOB | SPIN, ap=0.9, asp=0.35, aph=0))
    return b


item("hat", "void_crown", "Corona del Vacío", "epico", void_crown_boxes(), paint_void_crown, price=1100,
     desc="Esquirlas de obsidiana flotando y un cristal que gira sobre tu cabeza.")

# ---------------------------------------------------------------- ESPALDA
# Ángel
AB_PLATE, AB_SIDE, AB_DISC, AB_EPA, AB_FEATHER = (0, 0, 16, 20), (18, 0, 26, 8), (0, 24, 32, 56), (28, 0, 40, 10), (42, 0, 50, 8)


def paint_angel_back(cv):
    for y in range(20):  # peto dorado con rejilla blanca (como el de la tienda)
        for x in range(16):
            cv.set(x, y, mix(ANG["gl"], ANG["gold"], y / 19) if (x < 3 or x > 12 or y < 3 or y > 16) else hx("#d9dbe8"))
    for x in range(4, 12, 2):
        for y in range(4, 16):
            cv.set(x, y, hx("#8e92ab"))
    cv.frame(0, 0, 16, 20, ANG["gd"])
    cv.rect(*AB_SIDE, ANG["gold"])
    cv.jitter(*AB_SIDE, 0.08, 4)
    for y in range(10):
        for x in range(12):
            cv.set(28 + x, y, mix(ANG["gl"], ANG["gd"], y / 9))
    cv.frame(*AB_EPA, ANG["gd"])
    spr_feather(cv, AB_FEATHER, ANG["w"], ANG["gl"], ANG["out"])
    # disco solar detrás de la cabeza
    cx, cy = 15.5, 39.5
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5)
            a = math.atan2(y - 15.5, x - 15.5)
            if 9.5 <= d <= 14.5:
                t = (d - 9.5) / 5
                c = mix(hx("#fff6c8"), ANG["gold"], abs(t - 0.4) * 1.3)
                if d < 10.5 or d > 13.7:
                    c = ANG["gd"]
                cv.set(x, 24 + y, c)
            elif 14.5 < d <= 16 and (int((a + math.pi) / (2 * math.pi) * 24) % 2 == 0):
                cv.set(x, 24 + y, ANG["gl"])  # rayos
    for k in range(8):  # perlas en el aro
        a = k * math.pi / 4
        cv.set(int(15.5 + 12 * math.cos(a)), int(24 + 15.5 + 12 * math.sin(a)), WHITE)
    return cv


item("back", "angel_back", "Manto celestial", "epico", [
    B(0, 5.5, 2.7, 8.4, 10.4, 0.9, AB_PLATE, AB_SIDE),
    B(6.0, -0.5, 0, 4.8, 1.2, 4.8, AB_EPA, AB_EPA),
    B(-6.0, -0.5, 0, 4.8, 1.2, 4.8, AB_EPA, AB_EPA),
    B(6.4, -2.4, 0.8, 0.3, 3.4, 3.0, AB_FEATHER, AB_FEATHER, ry=-18),
    B(-6.4, -2.4, 0.8, 0.3, 3.4, 3.0, AB_FEATHER, AB_FEATHER, ry=18),
    B(0, -1.0, 4.6, 16, 16, 0.3, AB_DISC, AB_DISC, glow=True, an=PULSE, ap=0.03, asp=0.4),
], paint_angel_back, price=1000, desc="Peto dorado con rejilla, hombreras con plumas y un disco solar a tu espalda.")

# Demonio
HB_PLATE, HB_SIDE, HB_SPIKE, HB_TAIL, HB_SPADE = (0, 0, 16, 20), (18, 0, 26, 8), (28, 0, 34, 10), (36, 0, 44, 8), (46, 0, 58, 12)


def paint_hell_back(cv):
    ramp_v(cv, HB_PLATE, [hx("#2a0e14"), HEL["d"], HEL["k"]], 0.07, 8)
    r = random.Random(81)
    for _ in range(5):  # grietas de lava
        x, y = r.randrange(2, 14), r.randrange(2, 16)
        for k in range(r.randrange(4, 8)):
            cv.set(x, y, HEL["o"] if k % 3 else HEL["y"])
            x += r.choice([-1, 0, 1])
            y += 1
    for y in range(0, 20, 5):
        for x in range(16):
            cv.set(x, y, shade(cv.p[x, y][:3], 0.6))
    cv.frame(*HB_PLATE, HEL["out"])
    ramp_v(cv, HB_SIDE, [HEL["d"], HEL["k"]], 0.1, 9)
    ramp_v(cv, HB_SPIKE, [HEL["b"], HEL["r"], HEL["d"], HEL["k"]], 0.05, 10)
    ramp_v(cv, HB_TAIL, [hx("#5a0f18"), HEL["d"]], 0.08, 11)
    for y in range(0, 8, 3):
        for x in range(8):
            cv.set(36 + x, y, shade(cv.p[36 + x, y][:3], 0.65))
    blob(cv, 46, 0, 12, 12, lambda u, v: (abs(u) + abs(v) <= 1.0) or (abs(u) <= 0.35 and v > 0.5),
         lambda x, y, u, v: mix(HEL["y"], HEL["b"], min(1.0, (abs(u) + abs(v)) * 1.1)), HEL["r"])
    return cv


def hell_back_boxes():
    b = [B(0, 5.5, 2.7, 8.4, 10.4, 1.0, HB_PLATE, HB_SIDE)]
    for y, h in ((2.0, 1.8), (5.0, 2.4), (8.3, 1.8)):
        b.append(B(0, y, 3.7, 1.2, h, 1.2, HB_SPIKE, HB_SPIKE, glow=True))
    for sx in (-1, 1):
        b.append(B(sx * 5.8, -1.6, 0.4, 1.8, 3.2, 1.8, HB_SPIKE, HB_SPIKE))
        b.append(B(sx * 5.9, -4.0, 0.2, 1.1, 2.2, 1.1, HB_SPIKE, HB_SPIKE, glow=True))
    n = 8
    for i in range(n):
        s = 2.1 - 1.0 * i / (n - 1)
        b.append(B(0, 11.2 + 1.7 * i, 3.4 + 0.6 * i, s, 2.0, s, HB_TAIL, HB_TAIL, an=SWAY, ap=0.25 + 0.17 * i, asp=0.55, aph=-i * 38))
    b.append(B(0, 11.2 + 1.7 * n + 0.9, 3.4 + 0.6 * n + 0.2, 3.8, 3.8, 0.6, HB_SPADE, HB_SPADE, glow=True, an=SWAY, ap=1.5, asp=0.55, aph=-n * 38))
    return b


item("back", "hell_back", "Espalda del Soberano", "epico", hell_back_boxes(), paint_hell_back, price=1000,
     desc="Placa con grietas de lava, púas y una cola con punta de flecha que se balancea.")

# Vacío
VB_BLOCK, VB_SPIKE, VB_CRY, VB_GEM = (0, 0, 16, 16), (18, 0, 26, 12), (28, 0, 38, 20), (40, 0, 44, 4)


def paint_void_back(cv):
    ramp_v(cv, VB_BLOCK, [VOI["m"], VOI["d"], VOI["k"]], 0.06, 20)
    for x, y in ((3, 3), (4, 3), (4, 4), (11, 5), (12, 5), (12, 6), (6, 11), (7, 11), (8, 11), (8, 10)):
        cv.set(x, y, VOI["g"])  # runas
    cv.frame(*VB_BLOCK, VOI["p"])
    ramp_v(cv, VB_SPIKE, [VOI["g"], VOI["p"], VOI["d"], VOI["k"]], 0.05, 21)
    spr_crystal(cv, VB_CRY, VOI["m"], VOI["g"], VOI["l"])
    cube_tex(cv, VB_GEM, VOI["g"], VOI["l"], VOI["p"], VOI["m"])
    return cv


def void_back_boxes():
    b = []
    for sx, ph in ((1, 0), (-1, 180)):
        b.append(B(sx * 8.8, 1.6, 0.4, 4.0, 3.4, 5.2, VB_BLOCK, VB_BLOCK, an=BOB, ap=0.8, asp=0.42, aph=ph))
        b.append(B(sx * 8.8, -1.4, 0.4, 1.4, 3.2, 1.4, VB_SPIKE, VB_SPIKE, glow=True, an=BOB, ap=0.8, asp=0.42, aph=ph))
        b.append(B(sx * 10.9, 3.4, 0.4, 1.1, 1.1, 1.1, VB_GEM, VB_GEM, glow=True, an=BOB, ap=1.2, asp=0.42, aph=ph + 90))
        b.append(B(sx * 3.2, 7.4, 4.3, 2.0, 6.0, 2.0, VB_CRY, VB_CRY, glow=True, ry=sx * 40, an=BOB, ap=0.6, asp=0.35, aph=ph + 45))
    b.append(B(0, 4.6, 4.7, 3.2, 9.0, 3.2, VB_CRY, VB_CRY, glow=True, an=BOB, ap=0.5, asp=0.3))
    return b


item("back", "void_back", "Fragmentos del Vacío", "epico", void_back_boxes(), paint_void_back, price=1000,
     desc="Dos hombreras de obsidiana flotando y un cristal violeta a tu espalda.")

# ---------------------------------------------------------------- BOTAS (coordenadas de pierna)
BT_F, BT_S, BT_CUFF, BT_TOE, BT_ACC = (0, 0, 10, 9), (12, 0, 22, 9), (0, 10, 10, 13), (12, 10, 18, 13), (20, 10, 28, 18)


def paint_boots(kind):
    def go(cv):
        if kind == "angel":
            ramp_v(cv, BT_F, [ANG["w"], ANG["l"], ANG["s"]], 0.04, 31)
            ramp_v(cv, BT_S, [ANG["l"], ANG["s"], hx("#8d84c4")], 0.04, 32)
            for x in range(10):
                cv.set(x, 7, ANG["gold"]); cv.set(x, 8, ANG["gd"])
                cv.set(12 + x, 7, ANG["gold"]); cv.set(12 + x, 8, ANG["gd"])
            ramp_v(cv, BT_CUFF, [ANG["gl"], ANG["gold"], ANG["gd"]], 0.05, 33)
            for x in range(1, 10, 3):
                cv.set(x, 11, WHITE)
            ramp_v(cv, BT_TOE, [ANG["gl"], ANG["gold"]], 0.05, 34)
            spr_feather(cv, BT_ACC, ANG["w"], ANG["gl"], ANG["out"])
        elif kind == "hell":
            ramp_v(cv, BT_F, [hx("#3a0b14"), HEL["k"]], 0.06, 35)
            ramp_v(cv, BT_S, [hx("#3a0b14"), HEL["k"]], 0.06, 36)
            for r_ in (BT_F, BT_S):
                for x in range(r_[0], r_[2]):
                    cv.set(x, r_[3] - 2, HEL["b"]); cv.set(x, r_[3] - 1, HEL["o"])
                for (dx, dy) in ((2, 1), (3, 2), (3, 3), (4, 4), (7, 2), (6, 3), (6, 4)):
                    cv.set(r_[0] + dx, r_[1] + dy, HEL["o"] if dy % 2 else HEL["y"])
            ramp_v(cv, BT_CUFF, [HEL["r"], HEL["d"], HEL["k"]], 0.05, 37)
            ramp_v(cv, BT_TOE, [HEL["bone"], HEL["k"]], 0.08, 38)
            spr_flame(cv, BT_ACC, [hx("#fff3b0"), HEL["y"], HEL["o"], HEL["b"], HEL["r"]])
        else:
            ramp_v(cv, BT_F, [VOI["m"], VOI["d"], VOI["k"]], 0.05, 39)
            ramp_v(cv, BT_S, [VOI["m"], VOI["d"], VOI["k"]], 0.05, 40)
            for r_ in (BT_F, BT_S):
                for (dx, dy) in ((1, 1), (2, 2), (3, 3), (3, 4), (4, 5), (7, 1), (6, 2), (6, 3), (5, 4)):
                    cv.set(r_[0] + dx, r_[1] + dy, VOI["g"])
            ramp_v(cv, BT_CUFF, [VOI["p"], VOI["m"], VOI["d"]], 0.05, 41)
            ramp_v(cv, BT_TOE, [VOI["g"], VOI["p"]], 0.05, 42)
            spr_crystal(cv, BT_ACC, VOI["m"], VOI["g"], VOI["l"])
        return cv
    return go


def boot_boxes(kind):
    b = [B(0, 9.8, 0, 4.7, 4.5, 4.7, BT_F, BT_S), B(0, 7.7, 0, 5.1, 1.1, 5.1, BT_CUFF, BT_CUFF),
         B(0, 11.2, -2.6, 3.2, 1.4, 1.2, BT_TOE, BT_TOE)]
    if kind == "angel":
        b += [B(2.7, 8.8, 0.9, 0.3, 3.6, 3.4, BT_ACC, BT_ACC, an=SWAY, ap=0.25, asp=0.7)]
    elif kind == "hell":
        b += [B(0, 11.5, -3.4, 1.4, 1.4, 2.0, BT_TOE, BT_TOE, glow=True), B(0, 10.9, 2.9, 1.2, 2.2, 1.6, BT_TOE, BT_TOE)]
        for i, (x, z, ph) in enumerate(((-1.6, -1.6, 0), (1.6, -1.6, 90), (0, 1.4, 200), (-1.7, 1.2, 300))):
            b.append(B(x, 6.5, z, 1.3, 2.0, 1.3, BT_ACC, BT_ACC, glow=True, an=PULSE, ap=0.3, asp=2.0, aph=ph))
    else:
        b += [B(0, 12.0, 0, 4.9, 0.3, 4.9, BT_TOE, BT_TOE, glow=True)]
        for ph in (0, 180):
            b.append(B(3.9, 8.0, 0, 1.0, 2.0, 1.0, BT_ACC, BT_ACC, glow=True, an=ORBIT | BOB, ap=0.5, asp=0.55, aph=ph))
    return b


for kd, nm, ds in (("angel", "Botas celestiales", "Botas blancas con ribete dorado y una plumilla en cada tobillo."),
                   ("hell", "Botas del Soberano", "Cuero negro con grietas de lava, púas y llamas en la caña."),
                   ("void", "Botas del Vacío", "Obsidiana con venas violeta, suela luminosa y cristales en órbita.")):
    item("boots", kd + "_boots", nm, "epico", boot_boxes(kd), paint_boots(kd), price=900, desc=ds)

# ---------------------------------------------------------------- PUÑOS (coordenadas de brazo)


def fist_boxes(kind):
    b = [B(0, 8.0, 0, 4.7, 4.2, 4.7, BT_F, BT_S), B(0, 5.8, 0, 5.1, 1.0, 5.1, BT_CUFF, BT_CUFF),
         B(0, 8.0, -2.55, 3.6, 2.4, 0.5, BT_TOE, BT_TOE)]
    if kind == "angel":
        b += [B(2.7, 7.2, 0.6, 0.3, 4.0, 3.2, BT_ACC, BT_ACC, an=SWAY, ap=0.2, asp=0.7)]
    elif kind == "hell":
        for x in (-1.5, 0, 1.5):
            b.append(B(x, 7.5, -3.5, 0.9, 0.9, 1.8, BT_TOE, BT_TOE, glow=True))
        b.append(B(0, 4.7, 0, 1.5, 2.2, 1.5, BT_ACC, BT_ACC, glow=True, an=PULSE, ap=0.3, asp=2.0))
    else:
        for ph in (0, 180):
            b.append(B(3.7, 6.2, 0, 1.0, 1.9, 1.0, BT_ACC, BT_ACC, glow=True, an=ORBIT | BOB, ap=0.4, asp=0.7, aph=ph))
    return b


for kd, nm, ds in (("angel", "Guanteletes celestiales", "Puños blancos con nudillera dorada y una pluma que se mece."),
                   ("hell", "Puños del Soberano", "Puños negros con lava, púas en los nudillos y una llama sobre la muñeca."),
                   ("void", "Puños del Vacío", "Guanteletes de obsidiana con dos esquirlas girando en la muñeca.")):
    item("fists", kd + "_fists", nm, "epico", fist_boxes(kd), paint_boots(kd), price=900, desc=ds)

# ---------------------------------------------------------------- AURAS (partículas alrededor del cuerpo)
AU_A, AU_B, AU_C, AU_D = (0, 0, 8, 8), (10, 0, 18, 8), (0, 10, 8, 18), (10, 10, 18, 18)


def paint_aura(kind):
    def go(cv):
        if kind == "angel":
            cube_tex(cv, AU_A, hx("#fff3a0"), WHITE, ANG["gold"], ANG["gd"])
            spr_feather(cv, AU_B, ANG["w"], ANG["gl"], ANG["out"])
            cube_tex(cv, AU_C, WHITE, WHITE, ANG["l"], ANG["gl"])
            cube_tex(cv, AU_D, ANG["gl"], WHITE, ANG["gold"], ANG["gd"])
        elif kind == "hell":
            cube_tex(cv, AU_A, HEL["o"], HEL["y"], HEL["b"], HEL["r"])
            cube_tex(cv, AU_B, HEL["b"], HEL["o"], HEL["r"], HEL["d"])
            cube_tex(cv, AU_C, HEL["y"], WHITE, HEL["o"], HEL["b"])
            spr_flame(cv, AU_D, [hx("#fff3b0"), HEL["y"], HEL["o"], HEL["b"], HEL["r"]])
        else:
            cube_tex(cv, AU_A, VOI["p"], VOI["g"], VOI["m"], VOI["d"])
            cube_tex(cv, AU_B, VOI["m"], VOI["p"], VOI["d"], VOI["k"])
            cube_tex(cv, AU_C, VOI["g"], VOI["l"], VOI["p"], VOI["m"])
            spr_crystal(cv, AU_D, VOI["m"], VOI["g"], VOI["l"])
        return cv
    return go


def aura_boxes(kind):
    b = []
    if kind == "angel":
        for k in range(12):
            r = 8.5 + (k % 3) * 1.7
            if k % 4 == 3:
                b.append(B(r, 24, 0, 1.4, 2.6, 0.3, AU_B, AU_B, glow=True, an=ORBIT | RISE | SPIN, asp=0.2 + 0.03 * (k % 3), aph=k * 30, ar=26))
            else:
                b.append(B(r, 24, 0, 1.0, 1.0, 1.0, AU_A if k % 2 else AU_C, AU_A if k % 2 else AU_C, glow=True,
                           an=ORBIT | RISE, asp=0.26 + 0.04 * (k % 3), aph=k * 30, ar=26))
    elif kind == "hell":
        for k in range(14):
            r = 7.5 + (k % 4) * 1.4
            b.append(B(r, 25, 0, 0.9 + (k % 2) * 0.4, 0.9 + (k % 2) * 0.4, 0.9 + (k % 2) * 0.4, AU_A if k % 3 else AU_C, AU_A if k % 3 else AU_C,
                       glow=True, an=ORBIT | RISE, asp=0.4 + 0.05 * (k % 4), aph=k * 26, ar=28))
        for k in range(3):
            b.append(B(7.4, 22.0, 0, 2.4, 2.8, 2.4, AU_D, AU_D, glow=True, an=ORBIT | PULSE, ap=0.25, asp=0.5, aph=k * 120))
    else:
        for k in range(7):
            r = 8.0 + (k % 3) * 2.0
            b.append(B(r, 3 + k * 3.2, 0, 1.6, 1.6, 1.6, AU_A if k % 2 else AU_C, AU_A if k % 2 else AU_C, glow=True,
                       an=ORBIT | PULSE, ap=0.3, asp=0.22 + 0.03 * (k % 3), aph=k * 51))
        for k in range(8):
            b.append(B(6.6, 25.4, 0, 1.2, 2.0, 1.2, AU_D, AU_D, glow=True, an=ORBIT | SPIN, asp=0.18, aph=k * 45))
    return b


for kd, nm, ds in (("angel", "Aura celestial", "Destellos dorados y plumas que suben en espiral a tu alrededor."),
                   ("hell", "Aura infernal", "Brasas y llamas que ascienden girando alrededor de tu cuerpo."),
                   ("void", "Aura del Vacío", "Cubos y esquirlas de energía violeta orbitando a tu alrededor.")):
    item("aura", kd + "_aura", nm, "legendario", aura_boxes(kd), paint_aura(kd), price=1900, desc=ds)

# ---------------------------------------------------------------- CARA: PAYASO
CL_MASK, CL_WHITE, CL_NOSE, CL_BOW, CL_KNOT = (0, 0, 16, 16), (18, 0, 22, 4), (24, 0, 32, 8), (34, 0, 44, 8), (46, 0, 50, 4)
CL_HAIR = [(0, 20, 8, 28), (10, 20, 18, 28), (20, 20, 28, 28), (30, 20, 38, 28), (40, 20, 48, 28)]  # rojo, naranja, amarillo, verde, azul
CLOWN_HAIR = [hx("#e8321f"), hx("#ff8a1a"), hx("#ffd23c"), hx("#35c24a"), hx("#2f7bff")]


def paint_clown(cv):
    ink, red = hx("#2a0c12"), hx("#e0182d")
    cv.rect(*CL_MASK, hx("#fffaf2"))
    for y in range(16):  # leve sombra azulada abajo
        for x in range(16):
            if y > 12:
                cv.set(x, y, hx("#ece4ee"))
    # rombos azules sobre los ojos (los ojos quedan al descubierto: agujeros transparentes)
    for (cx, cy) in ((3.5, 5.0), (11.5, 5.0)):
        for y in range(16):
            for x in range(16):
                d = abs(x + 0.5 - cx - 0.5) + abs(y + 0.5 - cy - 0.5)
                if d <= 3.6:
                    cv.set(x, y, hx("#123c8c") if d > 2.6 else (hx("#2b6fe0") if x + y < cx + cy + 2 else hx("#1f55b8")))
        cv.set(int(cx), int(cy) - 1, hx("#7fb4ff"))
    # sonrisa roja enorme con remate hacia arriba
    band = set()
    for x in range(0, 16):
        yc = 14.0 - ((x - 7.5) / 7.5) ** 2 * 3.4
        for dy in (-1, 0, 1):
            band.add((x, int(round(yc)) + dy))
    for (x, y) in band:
        if 0 <= y < 16:
            cv.set(x, y, red)
    for (x, y) in list(band):
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if (nx, ny) not in band and 0 <= nx < 16 and 0 <= ny < 16:
                cv.set(nx, ny, ink)
    for x in range(3, 13, 3):
        cv.set(x, 13, hx("#ff7a86"))  # brillo del labio
    # estrella amarilla en la frente y mejillas
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        cv.set(8 + dx, 1 + dy, hx("#ffd23c"))
    for (cx, cy) in ((1, 10), (14, 10)):
        cv.set(cx, cy, hx("#ff6fa0"))
        cv.set(cx, cy + 1, hx("#ff6fa0"))
    # agujeros de los ojos (donde quedan los ojos de tu skin)
    for (ex, ey) in ((2, 8), (10, 8)):
        cv.clear(ex, ey, ex + 4, ey + 2)
    # cejas negras
    for x in range(1, 6):
        cv.set(x, 1 + (1 if x in (1, 5) else 0), ink)
    for x in range(10, 15):
        cv.set(x, 1 + (1 if x in (10, 14) else 0), ink)
    cv.rect(*CL_WHITE, hx("#fffaf2"))
    blob(cv, 24, 0, 8, 8, lambda u, v: u * u + v * v <= 1.0,
         lambda x, y, u, v: WHITE if (x, y) in ((2, 2), (3, 2)) else mix(hx("#ff4a5e"), hx("#a80f22"), min(1.0, (u + v + 2) / 3.2)), hx("#6a0612"))
    cv.rect(*CL_BOW, hx("#e0182d"))
    for x in range(34, 44):
        for y in range(8):
            if (x + y * 2) % 5 == 0:
                cv.set(x, y, WHITE)
    cv.frame(*CL_BOW, hx("#7a0f1c"))
    cv.rect(*CL_KNOT, hx("#2b6fe0"))
    cv.frame(*CL_KNOT, hx("#123c8c"))
    for i, r_ in enumerate(CL_HAIR):  # peluca rizada
        base = CLOWN_HAIR[i]
        rr = random.Random(100 + i)
        ramp_v(cv, r_, [shade(base, 1.2), base, shade(base, 0.72)], 0.0)
        for _ in range(9):
            cx, cy = r_[0] + rr.randrange(1, 7), r_[1] + rr.randrange(1, 7)
            for dx, dy, f in ((0, 0, 1.0), (1, 0, 1.28), (-1, 0, 0.7), (0, 1, 0.7), (0, -1, 1.28)):
                px = cv.p[cx + dx, cy + dy]
                if px[3]:
                    cv.set(cx + dx, cy + dy, shade(base, f))
        cv.frame(*r_, shade(base, 0.6))
    return cv


def clown_boxes():
    b = [B(0, -4.0, -4.45, 8.4, 8.4, 0.25, CL_MASK, CL_WHITE)]
    b += [B(0, -2.5, -5.2, 2.0, 2.0, 1.6, CL_NOSE, CL_NOSE), B(0, -2.5, -5.4, 1.4, 2.6, 1.4, CL_NOSE, CL_NOSE),
          B(0, -2.5, -5.4, 2.6, 1.4, 1.4, CL_NOSE, CL_NOSE)]
    for sx in (-1, 1):
        b += [B(sx * 5.4, -7.0, 0.2, 3.1, 3.1, 3.1, CL_HAIR[0], CL_HAIR[0]),
              B(sx * 5.9, -4.5, 0.2, 3.5, 3.1, 3.1, CL_HAIR[2], CL_HAIR[2]),
              B(sx * 5.4, -2.2, 0.2, 2.8, 2.6, 2.8, CL_HAIR[3], CL_HAIR[3])]
    b += [B(0, -4.4, 4.8, 6.4, 5.4, 1.6, CL_HAIR[4], CL_HAIR[4]),
          B(0.0, -9.6, -0.4, 3.6, 3.0, 3.2, CL_HAIR[1], CL_HAIR[1]),
          B(0.0, -11.6, -0.4, 1.8, 1.4, 1.8, CL_HAIR[0], CL_HAIR[0])]
    b += [B(1.9, 1.7, -2.6, 2.8, 2.0, 1.0, CL_BOW, CL_BOW), B(-1.9, 1.7, -2.6, 2.8, 2.0, 1.0, CL_BOW, CL_BOW),
          B(0, 1.7, -2.65, 1.3, 1.3, 1.3, CL_KNOT, CL_KNOT)]
    return b


item("face", "payaso", "Payaso", "epico", clown_boxes(), paint_clown, price=1200,
     desc="Maquillaje blanco con rombos azules, sonrisa roja, nariz, peluca arcoíris y pajarita.")



# ================================================================ SALIDA
def fl(v):
    return ("%.3f" % v).rstrip("0").rstrip(".") + "f" if "." in ("%.3f" % v) else "%.1ff" % v


def jstr(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def emit_java():
    out = []
    out.append("package com.ametra;")
    out.append("")
    out.append("import java.util.ArrayList;")
    out.append("import java.util.List;")
    out.append("")
    out.append("/** GENERADO por tools/gen_cosmetics.py — no editar a mano. Geometría y UV de los cosméticos. */")
    out.append("public final class CosmeticData {")
    out.append("    private CosmeticData() {}")
    out.append("")
    out.append("    /** Caja en píxeles de modelo. uv = cara frontal/trasera, uv2 = resto de caras (en texels de una hoja de ts x ts). */")
    out.append("    public static final class B {")
    out.append("        public final float x, y, z, w, h, d, ry;")
    out.append("        public final float u0, v0, u1, v1, s0, t0, s1, t1;")
    out.append("        public final boolean glow;")
    out.append("        public final int flags, an;")
    out.append("        /** Animación (ver ORBIT/BOB/... en gen_cosmetics.py): amplitud, ciclos por segundo, fase en grados, altura de ascenso. */")
    out.append("        public final float ap, asp, aph, ar;")
    out.append("")
    out.append("        B(float x, float y, float z, float w, float h, float d, float ry,")
    out.append("          int u0, int v0, int u1, int v1, int s0, int t0, int s1, int t1, int ts, boolean glow, int flags,")
    out.append("          int an, float ap, float asp, float aph, float ar) {")
    out.append("            this.x = x; this.y = y; this.z = z; this.w = w; this.h = h; this.d = d; this.ry = ry;")
    out.append("            this.u0 = u0 / (float) ts; this.v0 = v0 / (float) ts; this.u1 = u1 / (float) ts; this.v1 = v1 / (float) ts;")
    out.append("            this.s0 = s0 / (float) ts; this.t0 = t0 / (float) ts; this.s1 = s1 / (float) ts; this.t1 = t1 / (float) ts;")
    out.append("            this.glow = glow; this.flags = flags; this.an = an; this.ap = ap; this.asp = asp; this.aph = aph; this.ar = ar;")
    out.append("        }")
    out.append("    }")
    out.append("")
    out.append("    public static final class Item {")
    out.append("        public final String cat, id, name, rar, tex;")
    out.append("        public final int price;")
    out.append("        public final B[] boxes;")
    out.append("")
    out.append("        Item(String cat, String id, String name, String rar, int price, B[] boxes) {")
    out.append("            this.cat = cat; this.id = id; this.name = name; this.rar = rar; this.price = price; this.boxes = boxes;")
    out.append('            this.tex = cat + "_" + id;')
    out.append("        }")
    out.append("    }")
    out.append("")
    out.append("    public static final List<Item> ALL = new ArrayList<>();")
    out.append("")
    out.append("    public static Item find(String cat, String id) {")
    out.append("        if (id == null) return null;")
    out.append("        for (Item it : ALL) if (it.cat.equals(cat) && it.id.equals(id)) return it;")
    out.append("        return null;")
    out.append("    }")
    out.append("")
    out.append("    static {")
    for it in ITEMS:
        out.append("        ALL.add(new Item(%s, %s, %s, %s, %d, new B[] {" % (jstr(it["cat"]), jstr(it["id"]), jstr(it["name"]), jstr(it["rar"]), it["price"]))
        for b in it["boxes"]:
            u, s = b["uv"], b["uv2"]
            out.append("            new B(%s, %s, %s, %s, %s, %s, %s, %d, %d, %d, %d, %d, %d, %d, %d, %d, %s, %d, %d, %s, %s, %s, %s),"
                       % (fl(b["x"]), fl(b["y"]), fl(b["z"]), fl(b["w"]), fl(b["h"]), fl(b["d"]), fl(b["ry"]),
                          u[0], u[1], u[2], u[3], s[0], s[1], s[2], s[3], it["ts"], "true" if b["glow"] else "false", b["flags"],
                          b["an"], fl(b["ap"]), fl(b["asp"]), fl(b["aph"]), fl(b["ar"])))
        out.append("        }));")
    out.append("    }")
    out.append("}")
    with open(JAVA_OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")


LAUNCHER_DIR = os.path.join(ROOT, "assets", "cosmetics")


def emit_launcher():
    """Copia los PNG al launcher y genera assets/cosmetics/data.js (geometría + UV + precios)."""
    import json
    import shutil
    os.makedirs(LAUNCHER_DIR, exist_ok=True)
    out = []
    for it in ITEMS:
        name = "%s_%s.png" % (it["cat"], it["id"])
        shutil.copyfile(os.path.join(TEX_DIR, name), os.path.join(LAUNCHER_DIR, name))
        out.append(dict(cat=it["cat"], id=it["id"], name=it["name"], rar=it["rar"], price=it["price"], desc=it["desc"], tex=name, ts=it["ts"],
                        boxes=[dict(x=b["x"], y=b["y"], z=b["z"], w=b["w"], h=b["h"], d=b["d"], ry=b["ry"], uv=list(b["uv"]), uv2=list(b["uv2"]),
                                    glow=b["glow"], flags=b["flags"], an=b["an"], ap=b["ap"], asp=b["asp"], aph=b["aph"], ar=b["ar"]) for b in it["boxes"]]))
    with open(os.path.join(LAUNCHER_DIR, "data.js"), "w", encoding="utf-8", newline="\n") as f:
        f.write("// GENERADO por tools/gen_cosmetics.py: no editar a mano.\nconst COS_DATA = " + json.dumps(out, ensure_ascii=False) + ";\n")
    cat = [dict(cat=i["cat"], id=i["id"], name=i["name"], price=i["price"]) for i in out]
    with open(os.path.join(ROOT, "server", "catalog.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(cat, f, ensure_ascii=False)


def main():
    for it in ITEMS:
        cv = Cv(it["ts"])
        it["paint"](cv)
        cv.save("%s_%s" % (it["cat"], it["id"]))
    emit_java()
    emit_launcher()
    print("OK: %d cosméticos -> %s" % (len(ITEMS), TEX_DIR))


if __name__ == "__main__":
    main()
