#!/usr/bin/env python3
"""Hoja de contacto de los cosméticos (proyección 2D, frente o espalda) para revisarlos sin abrir el juego.
Uso: python tools/preview_cosmetics.py [cat ...]   ->  tools/preview.png"""
import math, os, sys
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_cosmetics as g

FRONT = ("hat", "glasses", "face", "boots", "fists", "aura")
CELL = 160


def icon(it):
    tex = Image.open(os.path.join(g.TEX_DIR, "%s_%s.png" % (it["cat"], it["id"]))).convert("RGBA")
    cat, front = it["cat"], it["cat"] in FRONT
    bs = []
    for b in it["boxes"]:
        if cat == "wings":
            a = math.radians(24)
            for sd in (-1, 1):
                bs.append(dict(x=sd * (3 + b["x"] * math.cos(a)), y=b["y"] + 0.6, w=b["w"] * math.cos(a), h=b["h"], z=b["z"], uv=b["uv"], flip=sd > 0))
        elif cat == "boots":
            for sd in (-1, 1):
                bs.append(dict(x=sd * (2 + b["x"]), y=b["y"] + 12, w=b["w"], h=b["h"], z=b["z"], uv=b["uv"], flip=sd > 0))
        elif cat == "fists":
            for sd in (-1, 1):
                bs.append(dict(x=sd * (6 + b["x"]), y=b["y"] + 2, w=b["w"], h=b["h"], z=b["z"], uv=b["uv"], flip=sd > 0))
        elif cat == "aura":
            a = math.radians(b["aph"]) if b["an"] & g.ORBIT else 0
            r = b["x"] if b["an"] & g.ORBIT else 0
            bs.append(dict(x=r * math.cos(a), y=b["y"] - b["ar"] * 0.5, w=b["w"], h=b["h"], z=r * math.sin(a), uv=b["uv"], flip=False))
        else:
            bs.append(dict(x=b["x"] if front else -b["x"], y=b["y"], w=b["w"], h=b["h"], z=b["z"], uv=b["uv"], flip=False))
    x0 = min(b["x"] - b["w"] / 2 for b in bs); x1 = max(b["x"] + b["w"] / 2 for b in bs)
    y0 = min(b["y"] - b["h"] / 2 for b in bs); y1 = max(b["y"] + b["h"] / 2 for b in bs)
    s = min(CELL * 0.86 / (x1 - x0), CELL * 0.86 / (y1 - y0))
    im = Image.new("RGBA", (CELL, CELL), (24, 24, 30, 255))
    for b in sorted(bs, key=lambda b: (-b["z"] if front else b["z"])):
        u0, v0, u1, v1 = b["uv"]
        w, h = max(1, round(b["w"] * s)), max(1, round(b["h"] * s))
        part = tex.crop((u0, v0, u1, v1)).resize((w, h), Image.NEAREST)
        if b["flip"]:
            part = part.transpose(Image.FLIP_LEFT_RIGHT)
        px = round(CELL / 2 + (b["x"] - (x0 + x1) / 2) * s - w / 2)
        py = round(CELL / 2 + (b["y"] - (y0 + y1) / 2) * s - h / 2)
        im.alpha_composite(part, (px, py)) if 0 <= px and 0 <= py and px + w <= CELL and py + h <= CELL else im.paste(part, (px, py), part)
    return im


def main():
    cats = sys.argv[1:]
    its = [i for i in g.ITEMS if not cats or i["cat"] in cats]
    cols = 6
    rows = (len(its) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * CELL, rows * CELL), (24, 24, 30, 255))
    for k, it in enumerate(its):
        sheet.paste(icon(it), ((k % cols) * CELL, (k // cols) * CELL))
    out = os.path.join(g.HERE, "preview.png")
    sheet.save(out)
    print(out, len(its))


if __name__ == "__main__":
    main()
