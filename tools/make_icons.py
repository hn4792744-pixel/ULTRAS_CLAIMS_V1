#!/usr/bin/env python3
"""Generates the optional GUI icon resource pack (resourcepack/Ultras_Claims_Icons.zip). Needs Pillow."""
import json, math, os, zipfile
from PIL import Image, ImageDraw

S = 8                      # supersampling
W = 32 * S
FG = (226, 226, 226, 255)
DIM = (150, 150, 150, 255)
GREEN = (111, 207, 151, 255)
RED = (235, 87, 87, 255)
ACCENT = (255, 107, 90, 255)
BLUE = (122, 162, 247, 255)

def canvas():
    im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)

def p(v):  # 0..32 design units -> pixels
    return v * S

def line(d, pts, col=FG, w=2.4):
    d.line([(p(x), p(y)) for x, y in pts], fill=col, width=int(w * S), joint="curve")
    for x, y in (pts[0], pts[-1]):
        d.ellipse([p(x) - w * S / 2, p(y) - w * S / 2, p(x) + w * S / 2, p(y) + w * S / 2], fill=col)

def ring(d, cx, cy, r, col=FG, w=2.4):
    d.ellipse([p(cx - r), p(cy - r), p(cx + r), p(cy + r)], outline=col, width=int(w * S))

def disc(d, cx, cy, r, col=FG):
    d.ellipse([p(cx - r), p(cy - r), p(cx + r), p(cy + r)], fill=col)

def box(d, x0, y0, x1, y1, col=FG, w=2.4, r=2):
    d.rounded_rectangle([p(x0), p(y0), p(x1), p(y1)], radius=p(r), outline=col, width=int(w * S))

def fillbox(d, x0, y0, x1, y1, col=FG, r=2):
    d.rounded_rectangle([p(x0), p(y0), p(x1), p(y1)], radius=p(r), fill=col)

def arrow(d, dx, dy, col=FG):
    cx = cy = 16
    tip = (cx + dx * 9, cy + dy * 9)
    tail = (cx - dx * 9, cy - dy * 9)
    line(d, [tail, tip], col)
    if dx:
        line(d, [(tip[0] - dx * 6, cy - 6), tip, (tip[0] - dx * 6, cy + 6)], col)
    else:
        line(d, [(cx - 6, tip[1] - dy * 6), tip, (cx + 6, tip[1] - dy * 6)], col)

def chevron(d, dx, col=FG):
    line(d, [(16 - dx * 4, 7), (16 + dx * 5, 16), (16 - dx * 4, 25)], col, 3)

def person(d, cx, col=FG, s=1.0):
    ring(d, cx, 11, 4 * s, col, 2.2)
    d.pieslice([p(cx - 8 * s), p(18), p(cx + 8 * s), p(34)], 180, 360, outline=col, width=int(2.2 * S))

def magnifier(d, col=FG):
    ring(d, 14, 14, 8, col, 2.6)
    line(d, [(20, 20), (27, 27)], col, 3)

def circle_arrow(d, col=FG):
    d.arc([p(7), p(7), p(25), p(25)], 40, 330, fill=col, width=int(2.6 * S))
    a = math.radians(330)
    ex, ey = 16 + 9 * math.cos(a), 16 + 9 * math.sin(a)
    line(d, [(ex + 5, ey - 3), (ex, ey), (ex + 5, ey + 4)], col, 2.4)

ICONS = {}
def icon(name):
    def deco(fn):
        ICONS[name] = fn
        return fn
    return deco

@icon("border")
def _(d):
    for (x, y, sx, sy) in ((6, 6, 1, 1), (26, 6, -1, 1), (6, 26, 1, -1), (26, 26, -1, -1)):
        line(d, [(x + sx * 7, y), (x, y), (x, y + sy * 7)], FG, 2.6)
    line(d, [(16, 12), (16, 20)], GREEN, 2.6); line(d, [(12, 16), (20, 16)], GREEN, 2.6)

@icon("members")
def _(d):
    person(d, 12, FG, 0.9); person(d, 21, DIM, 0.8)

@icon("settings")
def _(d):
    ring(d, 16, 16, 6, FG, 2.6)
    for i in range(8):
        a = math.radians(i * 45)
        line(d, [(16 + 8.5 * math.cos(a), 16 + 8.5 * math.sin(a)), (16 + 11.5 * math.cos(a), 16 + 11.5 * math.sin(a))], FG, 2.8)

@icon("cabin")
def _(d):
    line(d, [(9, 5), (23, 5)], FG, 2.6); line(d, [(9, 27), (23, 27)], FG, 2.6)
    line(d, [(10, 5), (10, 10), (16, 16), (10, 22), (10, 27)], FG, 2.2)
    line(d, [(22, 5), (22, 10), (16, 16), (22, 22), (22, 27)], FG, 2.2)
    line(d, [(13, 24), (19, 24)], ACCENT, 2.2)

@icon("cabin-disabled")
def _(d):
    line(d, [(9, 5), (23, 5)], DIM, 2.6); line(d, [(9, 27), (23, 27)], DIM, 2.6)
    line(d, [(10, 5), (10, 10), (16, 16), (10, 22), (10, 27)], DIM, 2.2)
    line(d, [(22, 5), (22, 10), (16, 16), (22, 22), (22, 27)], DIM, 2.2)
    line(d, [(6, 26), (26, 6)], RED, 2.4)

@icon("map")
def _(d):
    for i in range(3):
        for j in range(3):
            fillbox(d, 5 + i * 8, 5 + j * 8, 11 + i * 8, 11 + j * 8, GREEN if (i, j) == (1, 1) else DIM, 1)

@icon("notifications")
def _(d):
    d.pieslice([p(8), p(6), p(24), p(22)], 180, 360, outline=FG, width=int(2.4 * S))
    line(d, [(8, 14), (8, 22), (5, 25), (27, 25), (24, 22), (24, 14)], FG, 2.4)
    disc(d, 16, 28, 2)

@icon("info")
def _(d):
    ring(d, 16, 16, 11, FG, 2.4); disc(d, 16, 10.5, 1.8); line(d, [(16, 14.5), (16, 22)], FG, 2.6)

@icon("close")
def _(d):
    line(d, [(8, 8), (24, 24)], FG, 3); line(d, [(24, 8), (8, 24)], FG, 3)

@icon("cancel")
def _(d):
    line(d, [(8, 8), (24, 24)], RED, 3.4); line(d, [(24, 8), (8, 24)], RED, 3.4)

@icon("confirm")
def _(d):
    line(d, [(7, 17), (13, 23), (25, 9)], GREEN, 3.6)

@icon("back")
def _(d):
    arrow(d, -1, 0)

@icon("next")
def _(d):
    chevron(d, 1)

@icon("previous")
def _(d):
    chevron(d, -1)

@icon("left")
def _(d):
    arrow(d, -1, 0)

@icon("right")
def _(d):
    arrow(d, 1, 0)

@icon("up")
def _(d):
    arrow(d, 0, -1)

@icon("down")
def _(d):
    arrow(d, 0, 1)

@icon("center")
def _(d):
    ring(d, 16, 16, 9, FG, 2.4); disc(d, 16, 16, 3, ACCENT)

@icon("add-member")
def _(d):
    person(d, 12, FG, 0.9); line(d, [(24, 8), (24, 18)], GREEN, 3); line(d, [(19, 13), (29, 13)], GREEN, 3)

@icon("search")
def _(d):
    magnifier(d)

@icon("filter")
def _(d):
    line(d, [(5, 7), (27, 7), (18, 17), (18, 26), (14, 23), (14, 17), (5, 7)], FG, 2.4)

@icon("page")
def _(d):
    box(d, 8, 5, 24, 27, FG, 2.2); line(d, [(12, 12), (20, 12)], DIM, 2); line(d, [(12, 17), (20, 17)], DIM, 2); line(d, [(12, 22), (17, 22)], DIM, 2)

@icon("empty")
def _(d):
    line(d, [(10, 16), (22, 16)], DIM, 2.6)

@icon("toggle-on")
def _(d):
    fillbox(d, 4, 10, 28, 22, GREEN, 6); disc(d, 22, 16, 4.2, (20, 20, 20, 255))

@icon("toggle-off")
def _(d):
    fillbox(d, 4, 10, 28, 22, (90, 90, 90, 255), 6); disc(d, 10, 16, 4.2, (225, 225, 225, 255))

@icon("option")
def _(d):
    ring(d, 16, 16, 9, FG, 2.4); disc(d, 16, 16, 3.4, FG)

@icon("zoom-in")
def _(d):
    magnifier(d); line(d, [(10, 14), (18, 14)], GREEN, 2.2); line(d, [(14, 10), (14, 18)], GREEN, 2.2)

@icon("zoom-out")
def _(d):
    magnifier(d); line(d, [(10, 14), (18, 14)], RED, 2.2)

@icon("expand-here")
def _(d):
    box(d, 5, 5, 27, 27, FG, 2.2); line(d, [(16, 10), (16, 22)], GREEN, 3); line(d, [(10, 16), (22, 16)], GREEN, 3)

@icon("expand-unavailable")
def _(d):
    box(d, 5, 5, 27, 27, DIM, 2.2); line(d, [(11, 11), (21, 21)], RED, 2.6)

@icon("renew")
def _(d):
    circle_arrow(d, GREEN)

@icon("ps-reset")
def _(d):
    circle_arrow(d, FG)

@icon("deposit")
def _(d):
    line(d, [(16, 5), (16, 18)], FG, 2.6); line(d, [(10, 12), (16, 18), (22, 12)], FG, 2.6); line(d, [(6, 21), (6, 27), (26, 27), (26, 21)], FG, 2.6)

@icon("clock")
def _(d):
    ring(d, 16, 16, 11, FG, 2.4); line(d, [(16, 9), (16, 16), (21, 19)], ACCENT, 2.4)

@icon("membership")
def _(d):
    box(d, 4, 7, 28, 25, FG, 2.2); ring(d, 11, 15, 3, DIM, 2); line(d, [(17, 13), (24, 13)], DIM, 2); line(d, [(17, 18), (22, 18)], DIM, 2)

@icon("ps-membership")
def _(d):
    ICONS["membership"](d)

@icon("ps-sounds")
def _(d):
    line(d, [(5, 12), (10, 12), (16, 6), (16, 26), (10, 20), (5, 20), (5, 12)], FG, 2.2)
    d.arc([p(15), p(10), p(25), p(22)], -50, 50, fill=FG, width=int(2.2 * S))

@icon("ps-language")
def _(d):
    ring(d, 16, 16, 11, FG, 2.2); line(d, [(5, 16), (27, 16)], DIM, 2); d.ellipse([p(11), p(5), p(21), p(27)], outline=DIM, width=int(2 * S))

@icon("ps-messages")
def _(d):
    box(d, 4, 6, 28, 21, FG, 2.2); line(d, [(10, 21), (10, 27), (16, 21)], FG, 2.2); line(d, [(10, 12), (22, 12)], DIM, 2); line(d, [(10, 16), (18, 16)], DIM, 2)

@icon("ps-entry")
def _(d):
    box(d, 8, 4, 24, 28, FG, 2.2); disc(d, 20, 16, 1.6, ACCENT); line(d, [(2, 16), (7, 16)], GREEN, 2.2)

@icon("ps-notifications")
def _(d):
    ICONS["notifications"](d)

@icon("ps-invitations")
def _(d):
    box(d, 4, 8, 28, 24, FG, 2.2); line(d, [(5, 9), (16, 18), (27, 9)], FG, 2.2)

def render(fn):
    im, d = canvas()
    fn(d)
    return im.resize((32, 32), Image.LANCZOS)

def main():
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resourcepack")
    src = os.path.join(root, "Ultras_Claims_Icons")
    os.makedirs(os.path.join(src, "assets/ultrasclaims/textures/item"), exist_ok=True)
    os.makedirs(os.path.join(src, "assets/ultrasclaims/models/item"), exist_ok=True)
    os.makedirs(os.path.join(src, "assets/ultrasclaims/items"), exist_ok=True)
    for name, fn in ICONS.items():
        render(fn).save(os.path.join(src, f"assets/ultrasclaims/textures/item/{name}.png"))
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"ultrasclaims:item/{name}"}},
                  open(os.path.join(src, f"assets/ultrasclaims/models/item/{name}.json"), "w"), indent=1)
        json.dump({"model": {"type": "minecraft:model", "model": f"ultrasclaims:item/{name}"}},
                  open(os.path.join(src, f"assets/ultrasclaims/items/{name}.json"), "w"), indent=1)
    json.dump({"pack": {"pack_format": 46, "supported_formats": {"min_inclusive": 46, "max_inclusive": 999},
                        "description": "Ultras_Claims_v1 menu icons"}}, open(os.path.join(src, "pack.mcmeta"), "w"), indent=1)
    zpath = os.path.join(root, "Ultras_Claims_Icons.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for base, _, files in os.walk(src):
            for f in files:
                full = os.path.join(base, f)
                z.write(full, os.path.relpath(full, src))
    print(len(ICONS), "icons ->", zpath)

if __name__ == "__main__":
    main()
