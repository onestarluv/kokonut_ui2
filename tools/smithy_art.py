"""Blacksmith screen art (menu/smithy/SmithyMenu): dark forge panel, tabs, slots, anvil, upgrade pips, odds digits,
artisan gauge, buttons, breakthrough orbs and beams, glows. Writes PNG glyphs to
src/main/resources/bundled-glyphs/smithy/ and the 48 px gear icons (client textures, no PNG) to
src/main/resources/bundled-smithy-glyphs.yml, and adds the new PNGs to bundled-glyphs.yml (ascent 8).

The mockup it follows: docs/smithy-mock-*.png. Coordinates in SmithyMenu are GUI pixels of a 420 x 252 screen.
Glyphs must be at most 256 px each way, so the background is two 210 x 252 tiles. Pixels under 10 % alpha are
discarded by the text shader, so glows stop at that step.

usage (from Kokonut_UI): py -I tools/smithy_art.py <client 26.3 jar> [<hammer icon png>]
"""
import math
import os
import re
import sys
import zipfile
from io import BytesIO

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
OUT = os.path.join(RES, "bundled-glyphs")
FONT = os.path.join(RES, "resource-pack", "assets", "coreui", "font", "galmuri11.ttf")
BOLD = os.path.join(ROOT, "tools", "fonts", "galmuri11_bold.ttf")  # stamps (copy of the Nexo pack's font)
JAR = sys.argv[1]
HAMMER = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "..", "..", "resource", "smithy.png")

W, H = 420, 252
BG = (24, 20, 18)
PANEL = (40, 34, 30)
PANEL_LIGHT = (58, 50, 44)
INSET = (32, 27, 24)
IRON = (78, 72, 68)
IRON_LIGHT = (120, 112, 104)
IRON_DARK = (34, 30, 28)
EMBER = (255, 138, 42)
EMBER_DARK = (176, 74, 16)
TIERS = {"d": (110, 220, 230), "o": (232, 160, 96), "m": (143, 227, 232), "s": (255, 210, 74)}
DIGITS = {"green": (126, 217, 87), "red": (255, 90, 74), "violet": (196, 160, 255)}
written = []


def save(img, gid):
    if img.width > 256 or img.height > 256:
        raise ValueError(f"{gid}: {img.size} exceeds 256 px")
    if img.height < 8:  # shorter glyphs cannot hang from their line (see dex_glyphs.py)
        pad = Image.new("RGBA", (img.width, 8), (0, 0, 0, 0))
        pad.paste(img, (0, 0))
        img = pad
    path = os.path.join(OUT, gid + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    written.append(gid)


def new(w, h):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def bevel(d, x, y, w, h, fill, light, dark):
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=fill)
    d.line([x, y, x + w - 1, y], fill=light)
    d.line([x, y, x, y + h - 1], fill=light)
    d.line([x, y + h - 1, x + w - 1, y + h - 1], fill=dark)
    d.line([x + w - 1, y, x + w - 1, y + h - 1], fill=dark)


# Background: outer iron frame, title plate, gear strip, right panel. Tabs, slots, glows are separate glyphs.
def background():
    img = Image.new("RGBA", (W, H), BG + (255,))
    d = ImageDraw.Draw(img)
    bevel(d, 0, 0, W, H, IRON, IRON_LIGHT, IRON_DARK)
    bevel(d, 3, 3, W - 6, H - 6, PANEL, IRON_DARK, PANEL_LIGHT)
    for cx, cy in [(1, 1), (W - 3, 1), (1, H - 3), (W - 3, H - 3)]:
        d.rectangle([cx, cy, cx + 1, cy + 1], fill=IRON_LIGHT)
    bevel(d, 8, 8, 120, 18, (50, 42, 36), PANEL_LIGHT, IRON_DARK)   # title plate
    bevel(d, 8, 32, 64, 212, INSET, IRON_DARK, PANEL_LIGHT)          # gear strip
    bevel(d, 262, 32, 150, 212, INSET, IRON_DARK, PANEL_LIGHT)       # right panel
    save(img.crop((0, 0, 210, H)), "smithy/ui/bg_0")
    save(img.crop((210, 0, W, H)), "smithy/ui/bg_1")


def tabs():
    for name, active in [("tab_on", True), ("tab_off", False)]:
        img, d = new(50, 16)
        if active:
            bevel(d, 0, 0, 50, 16, EMBER_DARK, EMBER, (96, 36, 8))
        else:
            bevel(d, 0, 0, 50, 16, PANEL, PANEL_LIGHT, IRON_DARK)
        save(img, "smithy/ui/" + name)


def slots():
    img, d = new(24, 24)
    bevel(d, 0, 0, 24, 24, (30, 26, 23), IRON_DARK, IRON)
    save(img, "smithy/ui/slot")
    img, d = new(28, 28)
    bevel(d, 2, 2, 24, 24, (30, 26, 23), IRON_DARK, IRON)
    d.rectangle([1, 1, 26, 26], outline=EMBER)
    d.rectangle([0, 0, 27, 27], outline=EMBER_DARK)
    save(img, "smithy/ui/slot_on")


def glow(name, color, r, strength, under=PANEL):
    """A soft glow that works with the shader's 10 % alpha cut: every pixel is half opaque and its colour is solved so
    that, over {@code under} (the centre column's panel colour), it shows the wanted mix. The edge therefore matches
    the panel exactly instead of stopping at a visible ring."""
    size = r * 2 + 1
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    a = 0.5
    for y in range(size):
        for x in range(size):
            dist = math.hypot(x - r, y - r) / r
            if dist >= 1:
                continue
            k = strength / 255 * (1 - dist) ** 2
            target = [under[i] + (color[i] - under[i]) * k for i in range(3)]
            c = tuple(max(0, min(255, round((target[i] - under[i] * (1 - a)) / a))) for i in range(3))
            px[x, y] = c + (round(255 * a),)
    save(img, "smithy/ui/" + name)


def anvil():
    img, d = new(66, 26)
    cx, top = 33, 0
    d.polygon([(cx - 32, top), (cx + 26, top), (cx + 32, top + 3), (cx + 22, top + 7), (cx - 22, top + 7),
               (cx - 32, top + 4)], fill=(70, 70, 76))
    d.line([cx - 30, top, cx + 25, top], fill=(140, 140, 150))
    d.rectangle([cx - 12, top + 7, cx + 12, top + 15], fill=(52, 52, 58))
    d.rectangle([cx - 20, top + 15, cx + 20, top + 21], fill=(62, 62, 68))
    d.line([cx - 20, top + 15, cx + 20, top + 15], fill=(100, 100, 108))
    d.rectangle([cx - 24, top + 21, cx + 24, top + 24], fill=(44, 44, 50))
    save(img, "smithy/ui/anvil")


def pips():
    for tier, color in TIERS.items():
        for state in ("on", "open", "lock"):
            img, d = new(7, 7)
            pts = [(3, 0), (6, 3), (3, 6), (0, 3)]
            if state == "on":
                d.polygon(pts, fill=color)
                d.point((3, 1), fill=(255, 255, 255))
            elif state == "open":
                d.polygon(pts, outline=color, fill=(30, 26, 23))
            else:
                d.polygon(pts, outline=(70, 62, 56), fill=(30, 26, 23))
            save(img, f"smithy/ui/pip_{tier}_{state}")


def plates():
    img, d = new(48, 13)
    bevel(d, 0, 0, 48, 13, (60, 40, 26), (120, 76, 40), (30, 20, 12))
    save(img, "smithy/ui/level_plate")
    img, d = new(54, 14)
    bevel(d, 0, 0, 54, 14, (50, 44, 40), (80, 72, 66), IRON_DARK)
    save(img, "smithy/ui/badge_from")
    img, d = new(54, 14)
    bevel(d, 0, 0, 54, 14, (90, 50, 24), (232, 160, 96), (50, 26, 10))
    save(img, "smithy/ui/badge_to")
    img, d = new(1, 28)
    d.line([0, 0, 0, 27], fill=IRON)
    save(img, "smithy/ui/vline")
    img, d = new(134, 1)
    d.line([0, 0, 133, 0], fill=IRON_DARK)
    save(img, "smithy/ui/hline")


def gauge():
    img, d = new(134, 7)
    bevel(d, 0, 0, 134, 7, (22, 18, 16), IRON_DARK, IRON)
    save(img, "smithy/ui/bar")
    for w in (64, 32, 16, 8, 4, 2, 1):  # fill pieces, 5 px tall, laid left to right like dex bars
        img, d = new(w, 5)
        d.rectangle([0, 0, w - 1, 4], fill=EMBER)
        d.line([0, 0, w - 1, 0], fill=(255, 198, 102))
        save(img, f"smithy/ui/fill_{w}")


def buttons():
    for w, h in [(134, 26), (62, 24)]:
        for state in ("on", "off"):
            img, d = new(w, h)
            if state == "on":
                bevel(d, 0, 0, w, h, EMBER_DARK, EMBER, (96, 36, 8))
                d.rectangle([2, 2, w - 3, h // 2 - 2], fill=(214, 102, 26))
            else:
                bevel(d, 0, 0, w, h, (60, 54, 50), (84, 76, 70), IRON_DARK)
            save(img, f"smithy/ui/btn_{state}_{w}")


def orbs():
    img, d = new(15, 15)
    d.ellipse([0, 0, 14, 14], fill=(30, 26, 23), outline=IRON_LIGHT)
    d.ellipse([3, 3, 11, 11], fill=(50, 44, 40))
    save(img, "smithy/ui/orb_off")
    img, d = new(15, 15)
    d.ellipse([0, 0, 14, 14], fill=(30, 26, 23), outline=IRON_LIGHT)
    d.ellipse([3, 3, 11, 11], fill=(170, 235, 255))
    d.point((6, 5), fill=(255, 255, 255))
    save(img, "smithy/ui/orb_on")


# Breakthrough ring: five sockets on a pentagon (centre (R+8, R+8) in the image), the ring lines, and one beam image
# per socket from the socket to the gear's edge. SmithyMenu places the ring and beams with the same centre.
RING_R = 48
GEAR_EDGE = 16


def ring_points():
    c = RING_R + 8
    return [(round(c + RING_R * math.cos(-math.pi / 2 + i * 2 * math.pi / 5)),
             round(c + RING_R * math.sin(-math.pi / 2 + i * 2 * math.pi / 5))) for i in range(5)]


def ring():
    size = (RING_R + 8) * 2 + 1
    img, d = new(size, size)
    pts = ring_points()
    for i in range(5):
        d.line([pts[i], pts[(i + 1) % 5]], fill=(70, 62, 56))
    save(img, "smithy/ui/ring")
    c = RING_R + 8
    for i, (x, y) in enumerate(pts):
        img, d = new(size, size)
        dx, dy = c - x, c - y
        length = math.hypot(dx, dy)
        ex, ey = x + dx * (length - GEAR_EDGE) / length, y + dy * (length - GEAR_EDGE) / length
        d.line([(x, y), (ex, ey)], fill=(150, 220, 255), width=1)
        box = img.getbbox()
        # Crop to the beam so its top line is its own (layering goes by top y); SmithyMenu reads the offsets below.
        save(img.crop(box), f"smithy/ui/beam_{i}")
        BEAMS.append((box[0] - c, box[1] - c))


BEAMS = []


def digits():
    """Big odds digits: one hand-drawn 8-row bitmap font (digits 5 cells wide, % 7), every cell doubled to 2x2 px,
    so all characters share the same height, stroke and pixel grain."""
    glyphs = {
        "0": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
        "1": ["..#..", ".##..", "#.#..", "..#..", "..#..", "..#..", "..#..", "#####"],
        "2": [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
        "3": [".###.", "#...#", "....#", "..##.", "....#", "....#", "#...#", ".###."],
        "4": ["...#.", "..##.", ".#.#.", "#..#.", "#..#.", "#####", "...#.", "...#."],
        "5": ["#####", "#....", "#....", "####.", "....#", "....#", "#...#", ".###."],
        "6": [".###.", "#....", "#....", "####.", "#...#", "#...#", "#...#", ".###."],
        "7": ["#####", "#...#", "....#", "...#.", "..#..", "..#..", "..#..", "..#.."],
        "8": [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", "#...#", ".###."],
        "9": [".###.", "#...#", "#...#", "#...#", ".####", "....#", "....#", ".###."],
        "pct": [".#...#.", "#.#..#.", ".#..#..", "...#...", "...#...", "..#..#.", ".#..#.#", ".#...#."],
        "plus": [".....", ".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."],
    }
    for name, color in DIGITS.items():
        for gname, rows in glyphs.items():
            img = Image.new("RGBA", (len(rows[0]) * 2, len(rows) * 2), (0, 0, 0, 0))
            for yy, row in enumerate(rows):
                for xx, cell in enumerate(row):
                    if cell == "#":
                        for dx in (0, 1):
                            for dy in (0, 1):
                                img.putpixel((xx * 2 + dx, yy * 2 + dy), color + (255,))
            save(img, f"smithy/num/{name}_{gname}")


def close():
    img, d = new(16, 16)
    bevel(d, 0, 0, 16, 16, PANEL, PANEL_LIGHT, IRON_DARK)
    for i in range(4, 12):
        d.point((i, i), fill=(237, 227, 209))
        d.point((15 - i, i), fill=(237, 227, 209))
    save(img, "smithy/ui/close")


def burst(name, color, r, strength, rays=16, smoke=False):
    """Result scene backdrop: a glow with rays (or smoke puffs), half opaque with colours solved over the panel like
    glow(), so the edge disappears into it."""
    size = r * 2 + 1
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    a = 0.5
    puffs = [(r + int(r * 0.45 * math.cos(t)), r + int(r * 0.35 * math.sin(t)), r * (0.32 + 0.1 * (i % 3)))
             for i, t in enumerate([0.3 + i * 0.9 for i in range(7)])]
    for y in range(size):
        for x in range(size):
            dist = math.hypot(x - r, y - r) / r
            if dist >= 1:
                continue
            if smoke:
                k = 0.0
                for cx, cy, pr in puffs:
                    pd = math.hypot(x - cx, y - cy) / pr
                    if pd < 1:
                        k = max(k, (1 - pd) ** 1.5)
                k *= strength / 255 * (1 - dist)
            else:
                angle = math.atan2(y - r, x - r)
                ray = max(0.0, math.cos(angle * rays / 2)) ** 6
                long_ray = 1.0 if int((angle + math.pi) / (2 * math.pi) * rays) % 2 == 0 else 0.65
                core = (1 - dist) ** 2
                reach = max(0.0, 1 - dist / long_ray)
                k = strength / 255 * min(1.0, core * 0.9 + ray * reach * 0.8)
            if k < 0.02:
                continue
            target = [PANEL[i] + (color[i] - PANEL[i]) * k for i in range(3)]
            c = tuple(max(0, min(255, round((target[i] - PANEL[i] * (1 - a)) / a))) for i in range(3))
            px[x, y] = c + (round(255 * a),)
    save(img, "smithy/fx/" + name)


def cracks():
    """Cracks over the 48 px gear of destroyed gear."""
    img, d = new(48, 48)
    dark, red = (20, 8, 6), (200, 50, 30)
    for path in ([(24, 22), (18, 14), (20, 8), (14, 2)], [(24, 22), (31, 17), (36, 19), (44, 12)],
                 [(24, 22), (22, 31), (28, 37), (25, 46)], [(24, 22), (14, 27), (6, 26)], [(28, 37), (35, 41)]):
        d.line(path, fill=dark, width=2)
        d.line(path, fill=red, width=1)
    save(img, "smithy/fx/cracks")


STAMPS = {"success": ("성공!", (242, 193, 78), (120, 80, 20)), "failure": ("실패", (170, 160, 150), (60, 54, 50)),
          "destroyed": ("파괴!", (255, 90, 74), (110, 24, 16)), "break": ("돌파!", (150, 220, 255), (30, 70, 100)),
          "core": ("완성!", (196, 160, 255), (70, 40, 110))}


def stamps():
    """Result stamps: the word baked in at galmuri 11 bold's native 11 px (other sizes flatten ㅇ into bars),
    doubled, on a bevelled plate in the result's colour."""
    font = ImageFont.truetype(BOLD, 11)
    for name, (word, color, edge) in STAMPS.items():
        text, td = new(80, 16)
        td.fontmode = "1"
        td.text((0, 1), word, font=font, fill=color)
        box = text.getbbox()
        text = text.crop(box)
        big = text.resize((text.width * 2, text.height * 2), Image.NEAREST)
        w, h = max(96, big.width + 28), big.height + 12
        img, d = new(w, h)
        bevel(d, 0, 0, w, h, (30, 26, 23), color, edge)
        d.rectangle([2, 2, w - 3, h - 3], outline=edge)
        img.alpha_composite(big, ((w - big.width) // 2, 6))
        save(img, "smithy/fx/stamp_" + name)


def shock():
    """The shock ring drawn with the white flash before a result scene."""
    r = 60
    img, d = new(r * 2 + 3, r * 2 + 3)
    d.ellipse([1, 1, r * 2 + 1, r * 2 + 1], outline=(255, 245, 220), width=2)
    d.ellipse([5, 5, r * 2 - 3, r * 2 - 3], outline=(255, 200, 110), width=1)
    save(img, "smithy/fx/shock")


def hammer():
    img = Image.open(HAMMER).convert("RGBA")
    save(img, "smithy/ui/hammer")


def gear_icons():
    """48 px gear icons drawn from the client's own textures (no PNG shipped)."""
    z = zipfile.ZipFile(JAR)
    names = set(z.namelist())
    lines = ["# Generated by tools/smithy_art.py: 48 px gear icons for the blacksmith (menu/smithy/SmithyMenu).",
             "# Textures are the client's own; only fonts are written (see ResourcePackGenerator).", "vanilla:"]
    mats = ["wooden", "stone", "copper", "iron", "golden", "diamond", "netherite", "leather", "chainmail"]
    kinds = ["pickaxe", "shovel", "axe", "hoe", "sword", "spear", "helmet", "chestplate", "leggings", "boots"]
    keys = [f"{m}_{k}" for m in mats for k in kinds] + ["fishing_rod", "trident", "mace", "crossbow", "bow",
                                                         "heart_of_the_sea"]
    for key in keys:
        texture = "crossbow_standby" if key == "crossbow" else key
        if f"assets/minecraft/textures/item/{texture}.png" not in names:
            continue
        # The client's bitmap font measures a glyph up to its rightmost non-transparent column; CoreUi steps back by
        # the advance written here, so it must be that same measure or later elements on the line shift.
        img = Image.open(BytesIO(z.read(f"assets/minecraft/textures/item/{texture}.png"))).convert("RGBA")
        frame = img.crop((0, 0, img.width, img.width))  # first frame of animated strips
        right = 0
        for x in range(frame.width - 1, -1, -1):
            if any(frame.getpixel((x, y))[3] for y in range(frame.height)):
                right = x + 1
                break
        advance = int(right * 48 / frame.height + 0.5) + 1
        lines += [f"  smithy/item/{key}:", f"    texture: minecraft:item/{texture}.png", "    width: 48",
                  "    height: 48", "    ascent: 8", f"    advance: {advance}"]
    with open(os.path.join(RES, "bundled-smithy-glyphs.yml"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def register():
    """Adds the PNG glyphs to bundled-glyphs.yml with ascent 8 (dex_glyphs.py write_defaults does the same)."""
    path = os.path.join(RES, "bundled-glyphs.yml")
    with open(path, encoding="utf-8") as f:
        text = f.read()
    text = re.sub(r"(?m)^  smithy/.*\n(    .*\n)*", "", text)
    text = text.rstrip("\n") + "\n" + "".join(f"  {gid}:\n    ascent: 8\n" for gid in sorted(written))
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


background()
tabs()
slots()
glow("glow_ember", (255, 120, 30), 70, 150)
glow("glow_blue", (120, 200, 255), 62, 110)
glow("glow_violet", (176, 124, 255), 60, 140)
glow("glow_orb", (150, 220, 255), 14, 190)
anvil()
pips()
plates()
gauge()
buttons()
orbs()
ring()
digits()
hammer()
close()
burst("burst_gold", (255, 196, 70), 70, 230)
burst("burst_red", (255, 70, 50), 70, 220)
burst("burst_blue", (140, 215, 255), 70, 220)
burst("burst_violet", (190, 140, 255), 70, 220)
burst("smoke", (150, 140, 132), 70, 200, smoke=True)
burst("flash", (255, 250, 235), 70, 255, rays=24)
cracks()
stamps()
shock()
gear_icons()
register()
print(f"{len(written)} glyphs; beam offsets {BEAMS}")
