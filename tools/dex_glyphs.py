"""아이템 도감(ItemDexMenu)용 UI 글리프 PNG 생성기 (책·슬롯·버튼·탭·책갈피 등). 표준 라이브러리만 사용합니다.
아이템 아이콘·초상은 바닐라 텍스처를 그대로 쓰므로 여기서 만들지 않습니다 (tools/dex_vanilla.txt).

실행:  py -3 tools/dex_glyphs.py
결과:  src/main/resources/bundled-glyphs/dex/**.png
       플러그인이 시작할 때 plugins/CoreUi/Glyphs/dex/ 에 없으면 복사하고, 글리프 id는 "dex/ui/slot_normal" 형식입니다.
모든 크기는 GUI 픽셀 기준입니다. 이미지는 시안용 임시 아트이므로 실제 아트로 같은 이름·크기로 교체하면 됩니다.
"""
import os
import shutil
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# Every PNG under bundled-glyphs/ becomes glyph id "<folder>/<path>": dex/ui/... for the dex, menu/ui/... for the main menu.
OUT = os.path.join(ROOT, "src", "main", "resources", "bundled-glyphs")


def hexc(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


CLEAR = (0, 0, 0, 0)


def mix(c, t, k):
    return tuple(round(c[i] + (t[i] - c[i]) * k) for i in range(3)) + (c[3],)


def lighten(c, k):
    return mix(c, (255, 255, 255, 255), k)


def darken(c, k):
    return mix(c, (0, 0, 0, 255), k)


class Img:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h = w, h
        self.px = [[fill] * w for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, c)

    def paste(self, o, ox, oy):
        for y in range(o.h):
            for x in range(o.w):
                if o.px[y][x][3]:
                    self.set(ox + x, oy + y, o.px[y][x])

    def crop(self, x, y, w, h):
        out = Img(w, h)
        for yy in range(h):
            out.px[yy] = self.px[y + yy][x:x + w]
        return out

    def scaled(self, k):
        out = Img(self.w * k, self.h * k)
        for y in range(self.h):
            for x in range(self.w):
                out.rect(x * k, y * k, k, k, self.px[y][x])
        return out

    def save(self, rel):
        if self.w > 256 or self.h > 256:  # the client shows a missing-glyph box for larger bitmap glyphs
            raise ValueError(f"{rel}: {self.w}x{self.h} exceeds 256 px; split it into tiles")
        # Glyphs shorter than 8 px cannot get ascent 8 and would land on the previous text line (painted too early);
        # transparent rows below keep every image hanging from the line its top sits on.
        if self.h < 8:
            self.px += [[CLEAR] * self.w for _ in range(8 - self.h)]
            self.h = 8
        path = os.path.join(OUT, (rel if rel.startswith("menu/") else "dex/" + rel) + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in self.px)

        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

        with open(path, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
                    + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def bevel(w, h, fill, edge, hi, lo, top=True, right=True, bottom=True, left=True):
    img = Img(w, h, fill)
    if top: img.rect(0, 0, w, 1, edge)
    if bottom: img.rect(0, h - 1, w, 1, edge)
    if left: img.rect(0, 0, 1, h, edge)
    if right: img.rect(w - 1, 0, 1, h, edge)
    x0, y0 = int(left), int(top)
    x1, y1 = w - 1 - int(right), h - 1 - int(bottom)
    img.rect(x0, y0, x1 - x0 + 1, 1, hi)
    img.rect(x0, y0, 1, y1 - y0 + 1, hi)
    img.rect(x0, y1, x1 - x0 + 1, 1, lo)
    img.rect(x1, y0, 1, y1 - y0 + 1, lo)
    return img


# ---------------------------------------------------------------- 8x8 원본 아이콘
SHAPES = {
    "nugget": ["........", "..cccc..", ".cwcccC.", "ccccccCC", "cccccCCC", ".ccCCCC.", "..CCCC..", "........"],
    "gem": ["........", "..wccc..", ".wccccC.", "wccccccC", "cccccCCC", ".cccCCC.", "..cCCC..", "...CC..."],
    "crystal": ["...w....", "...cw..c", ".c.cc.cC", ".cccc.cC", "cccCcccC", "ccCCcCCC", ".CCCCCC.", "........"],
    "block": ["wwwwwwwc", "wccccccC", "wccwcccC", "wccccccC", "wcccccwC", "wccccccC", "wccccccC", "cCCCCCCC"],
    "shard": ["........", "....w...", "...wc...", "...cc...", "..ccC...", "..cCC...", "...C....", "........"],
}


def mineral(shape, w, c, C):
    return {"pal": {"w": w, "c": c, "C": C}, "rows": SHAPES[shape]}


ICONS = {
    "cat_ore": {"pal": {"s": "#8A8A8A", "S": "#6A6A6A", "c": "#55D6E0", "C": "#2A9AA8"}, "rows": ["sSsssSss", "ssccsssS", "sScCssss", "ssssSccs", "Sssssccs", "sccsssSs", "scCssss.", "sssSsssS"]},
    "cat_crop": {"pal": {"y": "#E8C050", "Y": "#B08A2E", "g": "#6A9A3A"}, "rows": ["...y....", "..yYy...", "...yYy..", "..yYy...", "...yYy..", "....g...", "...g....", "..g....."]},
    "cat_plant": {"pal": {"p": "#E05A8A", "y": "#F2D640", "g": "#4E8A2E"}, "rows": ["..p.p...", ".ppypp..", "..ppp...", "...g....", ".g.g....", "..gg....", "...g....", "........"]},
    "cat_fish": {"pal": {"b": "#4F8FD6", "B": "#2F5F9E", "l": "#9CC8F0", "k": "#111111"}, "rows": ["........", "...bb...", ".lbbbb.B", "lkbbbbBB", "lbbbbbBB", ".lbbbb.B", "...bb...", "........"]},
    "cat_relic": {"pal": {"y": "#E8C050", "Y": "#A8802A", "b": "#3EC8C8"}, "rows": ["..YYYY..", ".Y....Y.", ".Y....Y.", "..Y..Y..", "..yyyy..", ".yybbyy.", ".yybbyy.", "..yyyy.."]},
    "cat_misc": {"pal": {"b": "#8A5A2A", "B": "#5E3A18", "y": "#F2C640"}, "rows": ["...BB...", "..B..B..", ".bbbbbb.", "bbbyybbb", "bbbbbbbb", "bbbbbbbb", ".bbbbbb.", "........"]},
    "mark_dex": {"pal": {"b": "#F0E6CC", "B": "#C8B890", "k": "#3B2A1A"}, "rows": ["kkkkkkk.", "kbbbbbbk", "kbBBBBbk", "kbbbbbbk", "kbBBBbbk", "kbbbbbbk", "kkkkkkkk", ".kkkkkk."]},
    "mark_reward": {"pal": {"c": "#A8743A", "C": "#6E4A22", "y": "#E8C850"}, "rows": ["........", ".CCCCCC.", "CccccccC", "CccccccC", "CCCyyCCC", "CccyycCC", "CccccccC", "CCCCCCCC"]},
    "mark_stats": {"pal": {"b": "#F0F0F0"}, "rows": ["........", "......b.", "....b.b.", "..b.b.b.", "b.b.b.b.", "b.b.b.b.", "bbbbbbbb", "........"]},
    "coin": {"pal": {"y": "#F2C640", "Y": "#C8962A", "w": "#FFF0A0"}, "rows": ["..yyyy..", ".ywwyyY.", "yywyyyYY", "yyyyyyYY", "yyyyyyYY", "yyyyyYYY", ".yYYYYY.", "..YYYY.."]},
    "scroll": {"pal": {"p": "#F0E6CC", "b": "#8A6A3A", "k": "#5A4630"}, "rows": ["bb......", "bpppppp.", ".pkkkkp.", ".pppppp.", ".pkkkpp.", ".pppppp.", ".ppppppb", "......bb"]},
    "bag": {"pal": {"b": "#8A5A2A", "B": "#5E3A18", "y": "#F2C640"}, "rows": ["...BB...", "..B..B..", ".bbbbbb.", "bbbyybbb", "bbbbbbbb", "bbbbbbbb", ".bbbbbb.", "........"]},
    "pick": {"pal": {"s": "#B8B8C8", "b": "#8A5A2A"}, "rows": ["..ssss..", ".s..b.s.", "s...b..s", "....b...", "...b....", "...b....", "..b.....", "..b....."]},
    "book": {"pal": {"b": "#F0E6CC", "B": "#C8B890", "k": "#3B2A1A"}, "rows": ["kkkkkkk.", "kbbbbbbk", "kbBBBBbk", "kbbbbbbk", "kbBBBbbk", "kbbbbbbk", "kkkkkkkk", ".kkkkkk."]},
    "trophy": {"pal": {"y": "#E8C050", "Y": "#A8802A"}, "rows": ["yyyyyyyy", "Y.yyyy.Y", "Y.yyyy.Y", ".YyyyyY.", "..YyyY..", "...YY...", "..yyyy..", ".YYYYYY."]},
    "coal": mineral("nugget", "#6A6A6A", "#3A3A3A", "#222222"),
    "copper": mineral("nugget", "#F0A070", "#D8784A", "#A85030"),
    "iron": mineral("nugget", "#F4EEE8", "#D8D0C8", "#A89888"),
    "gold": mineral("nugget", "#FFF0A0", "#F2D640", "#C8A020"),
    "lapis": mineral("gem", "#7A9AF0", "#3050C0", "#1E3080"),
    "emerald": mineral("gem", "#A0F0B8", "#3EC86A", "#1E8A44"),
    "ruby": mineral("gem", "#FFA0B0", "#E0304A", "#9A1A2E"),
    "amethyst": mineral("crystal", "#E8D0FF", "#B07AE0", "#7A4AB0"),
    "sapphire": mineral("gem", "#A0D8FF", "#3A8AE0", "#1E5AA0"),
    "moonstone": mineral("gem", "#FFFFFF", "#D0E0EC", "#98AABC"),
    "obsidian_shard": mineral("shard", "#7A5A9A", "#3A2A50", "#1E1430"),
    "firestone": mineral("crystal", "#FFE0A0", "#F08A30", "#B04A1A"),
    "amethyst_shard": mineral("shard", "#E8D0FF", "#B07AE0", "#7A4AB0"),
    "amethyst_block": mineral("block", "#E8D0FF", "#B07AE0", "#7A4AB0"),
    "golden_wheat": {"pal": {"y": "#F2D24A", "Y": "#C89A2A", "g": "#6A9A3A"}, "rows": ["..y.y...", ".yYyYy..", "..yYyY..", ".yYyYy..", "..yYy...", "...g....", "..g.....", ".g......"]},
    "trout": {"pal": {"b": "#B8C8D8", "B": "#7A8EA4", "l": "#E8F0F8", "k": "#111111", "p": "#E89AA8"}, "rows": ["........", "...bb...", ".lbbbb.B", "lkbppbBB", "lbbbbbBB", ".lbbbb.B", "...bb...", "........"]},
}
ITEMS = ["coal", "copper", "iron", "gold", "lapis", "emerald", "ruby", "amethyst", "sapphire", "moonstone", "obsidian_shard", "firestone"]
EXTRA_ICONS = ["amethyst_shard", "amethyst_block", "coin", "scroll", "bag", "pick", "book", "trophy", "golden_wheat", "trout",
               "cat_ore", "cat_crop", "cat_plant", "cat_fish", "cat_relic", "cat_misc"]
SILHOUETTE = hexc("#5A4630")


def icon8(name, tint=None):
    ic = ICONS[name]
    img = Img(8, 8)
    for y in range(8):
        for x, ch in enumerate((ic["rows"][y] + "........")[:8]):
            if ch != ".":
                img.set(x, y, tint or hexc(ic["pal"][ch]))
    return img


# ---------------------------------------------------------------- 팔레트
OUTLINE = hexc("#1E0E06")
INK = hexc("#3B2A1A")
SLOT, SLOT_DARK, SLOT_LIGHT = hexc("#D8C79C"), hexc("#A8946A"), hexc("#F6EBCF")
RED = hexc("#B03A2E")
BUTTONS = {
    "paper": ("#C9B588", "#3B2A1A", "#E6D6AC", "#9C8658"),
    "dark": ("#6B3A24", "#1E0E06", "#8A5232", "#4A2414"),
    "green": ("#4E8A2E", "#1E0E06", "#7CB856", "#2E5A1A"),
    "off": ("#D8CBA8", "#8C7A5A", "#D8CBA8", "#D8CBA8"),
}
CATS = [("ore", "#6E6E78"), ("crop", "#A8842A"), ("plant", "#B0607A"), ("fish", "#3F6FA8"), ("relic", "#7A5A9A"), ("misc", "#5E6E5A")]
MARKS = [("dex", "#B03A2E"), ("reward", "#C9A23A"), ("stats", "#3F6FA8")]
FRAMES = {"cave": ("#4A3E5E", "#3A3046", "#55486A"), "deep": ("#2E3A4E", "#232C3C", "#3A4860"),
          "field": ("#7FA0C8", "#4E7A36", "#6CA04A"), "sea": ("#3E6A98", "#2A4E74", "#4A7AAA"),
          "lava": ("#5A2E22", "#3E1E16", "#7A3A26"), "locked": ("#3A4650", "#2E363E", "#46525C")}


def book():
    W, H = 456, 272
    img = bevel(W, H, hexc("#6B3A24"), OUTLINE, hexc("#9A5A36"), hexc("#3E1E10"))
    for x0, shade_x in ((7, 221), (232, 232)):
        img.rect(x0, 7, 217, 258, hexc("#EADBB5"))
        img.rect(x0, 263, 217, 2, hexc("#CDB98A"))
        img.rect(shade_x, 7, 3, 256, hexc("#DCCBA0"))
    img.rect(224, 7, 8, 258, hexc("#4A2414"))
    img.rect(227, 7, 1, 258, hexc("#2A1208"))
    return img


QMARK = [".###.", "#...#", "....#", "..##.", "..#..", ".....", "..#.."]


def slot(state):
    img = Img(20, 20, {"normal": SLOT, "selected": hexc("#F4E6B8"), "locked": hexc("#D2C091")}[state])
    img.rect(0, 0, 20, 1, SLOT_DARK)
    img.rect(0, 0, 1, 20, SLOT_DARK)
    img.rect(1, 19, 19, 1, SLOT_LIGHT)
    img.rect(19, 1, 1, 19, SLOT_LIGHT)
    if state == "selected":
        for (x, y, w, h) in ((0, 0, 20, 1), (0, 19, 20, 1), (0, 0, 1, 20), (19, 0, 1, 20)):
            img.rect(x, y, w, h, RED)
    if state == "locked":
        for y, row in enumerate(QMARK):
            for x, ch in enumerate(row):
                if ch == "#":
                    img.set(8 + x, 6 + y, SLOT_DARK)
    return img


def button(kind, w):
    fill, edge, hi, lo = (hexc(c) for c in BUTTONS[kind])
    return bevel(w, 20, fill, edge, hi, lo)


def tab(color, active):
    c = hexc(color) if active else darken(hexc(color), 0.28)
    h = 22 if active else 20
    img = bevel(56, h, c, OUTLINE, lighten(c, 0.35), darken(c, 0.25), bottom=not active)
    if active:
        img.rect(1, h - 2, 54, 2, c)
    return img  # the vanilla item icon is drawn on top by ItemDexMenu


def mark(name, color, active):
    c = hexc(color) if active else darken(hexc(color), 0.25)
    w = 26 if active else 22
    img = bevel(w, 20, c, OUTLINE, lighten(c, 0.35), darken(c, 0.3), left=False)
    return img  # the vanilla item icon is drawn on top by ItemDexMenu


def close():
    img = bevel(20, 20, hexc("#9A2E2E"), OUTLINE, hexc("#C25444"), hexc("#6A1A16"))
    for dx, colour in ((1, hexc("#3A0E0A")), (0, hexc("#FFFFFF"))):
        for i in range(8):
            img.set(6 + i + dx, 6 + i + dx, colour)
            img.set(13 - i + dx, 6 + i + dx, colour)
    return img


def frame(name):
    sky, ground, hi = (hexc(c) for c in FRAMES[name])
    img = Img(200, 88, sky)
    img.rect(0, 66, 200, 22, ground)
    img.rect(0, 66, 200, 1, hi)
    for (x, y, w, h) in ((0, 0, 200, 1), (0, 87, 200, 1), (0, 0, 1, 88), (199, 0, 1, 88)):
        img.rect(x, y, w, h, INK)
    return img


def bar_bg(w):
    img = Img(w, 5, hexc("#CDB98A"))
    for (x, y, ww, h) in ((0, 0, w, 1), (0, 4, w, 1), (0, 0, 1, 5), (w - 1, 0, 1, 5)):
        img.rect(x, y, ww, h, hexc("#5A4630"))
    return img


def bar_piece(w, kind):
    top, body = {"fill": ("#9CD46A", "#6BA83A"), "seen": ("#A8946A", "#A8946A")}[kind]
    img = Img(w, 3, hexc(body))
    img.rect(0, 0, w, 1, hexc(top))
    return img


def pip(on):
    c = hexc("#9A62E0") if on else hexc("#C8B890")
    img = Img(6, 6, c)
    img.rect(1, 5, 5, 1, darken(c, 0.3))
    img.rect(5, 1, 1, 5, darken(c, 0.3))
    return img


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    # The book is drawn at y=20; splitting at 133 puts the lower tiles' top on a 9px line (y=153), so nothing
    # above that line can be painted over by them (CoreUi paints line by line, each glyph on its top line).
    b = book()
    for r, (y0, h) in enumerate(((0, 133), (133, 139))):
        for c in range(2):
            b.crop(c * 228, y0, 228, h).save(f"ui/page_{r}_{c}")
    for key, color in CATS:  # covers the book's top edge under the active tab
        Img(54, 2, hexc(color)).save(f"ui/tab_{key}_join")
    for s in ("normal", "selected", "locked"):
        slot(s).save(f"ui/slot_{s}")
    close().save("ui/close")
    for kind, widths in {"paper": (20, 24, 36, 52, 100), "dark": (36,), "green": (46, 60, 100), "off": (46, 60, 100)}.items():
        for w in widths:
            button(kind, w).save(f"ui/btn_{kind}_{w}")
    for key, color in CATS:
        tab(color, True).save(f"ui/tab_{key}_on")
        tab(color, False).save(f"ui/tab_{key}_off")
    for name, color in MARKS:
        mark(name, color, True).save(f"ui/bookmark_{name}_on")
        mark(name, color, False).save(f"ui/bookmark_{name}_off")
    for name in FRAMES:
        frame(name).save(f"ui/frame_{name}")
    for w in (200, 100):
        bar_bg(w).save(f"ui/bar_{w}")
    n = 1
    while n <= 128:
        bar_piece(n, "fill").save(f"ui/fill_{n}")
        bar_piece(n, "seen").save(f"ui/seen_{n}")
        n *= 2
    pip(True).save("ui/pip_on")
    pip(False).save("ui/pip_off")
    # Item icons and portraits are vanilla textures (tools/dex_vanilla.txt + VanillaGlyphs.java), not PNGs here.
    menu_glyphs()
    icon_art()
    write_defaults()
    count = sum(len(f) for _, _, f in os.walk(OUT))
    print(f"{count} glyph PNGs -> {OUT}")


HEIGHTS = {}  # glyph id -> display height when it differs from the PNG (scaled by the client)


def icon_art():
    """Kokonut icons drawn by hand (art/icons/*.png, 16x16; the same files are Nexo items icon_<name>).
    Each becomes menu/icon/<name> (16 px) and menu/icon/<name>_32 (same image shown at 32 px)."""
    src = os.path.join(ROOT, "art", "icons")
    if not os.path.isdir(src):
        return
    dst = os.path.join(OUT, "menu", "icon")
    os.makedirs(dst, exist_ok=True)
    for fn in sorted(os.listdir(src)):
        if not fn.endswith(".png"):
            continue
        name = fn[:-4]
        shutil.copyfile(os.path.join(src, fn), os.path.join(dst, name + ".png"))
        shutil.copyfile(os.path.join(src, fn), os.path.join(dst, name + "_32.png"))
        HEIGHTS["menu/icon/" + name + "_32"] = 32


def menu_glyphs():
    """Main menu (MainMenu.java): same leather-and-parchment kit as the dex. Sizes in GUI px.
    New sizes get new file names: bundled glyphs are only copied to a server when missing, so reusing a name would
    keep the old image there."""
    W, H = 320, 116
    panel = bevel(W, H, hexc("#6B3A24"), OUTLINE, hexc("#9A5A36"), hexc("#3E1E10"))
    panel.rect(7, 7, W - 14, H - 14, hexc("#EADBB5"))
    panel.rect(7, H - 9, W - 14, 2, hexc("#CDB98A"))
    panel.rect(14, 40, W - 28, 1, hexc("#CDB98A"))  # divider under the profile row
    # The client cannot draw a bitmap glyph wider than 256 px (it shows a missing-glyph box), so wide art is tiled.
    panel.crop(0, 0, 160, H).save(f"menu/ui/panel_{H}_0")
    panel.crop(160, 0, 160, H).save(f"menu/ui/panel_{H}_1")
    c = hexc("#B03A2E")
    tab_img = bevel(88, 22, c, OUTLINE, lighten(c, 0.35), darken(c, 0.25), bottom=False)
    tab_img.rect(1, 20, 86, 2, c)
    tab_img.save("menu/ui/tab")
    Img(86, 2, c).save("menu/ui/tab_join")
    bevel(40, 54, SLOT, INK, SLOT_LIGHT, SLOT_DARK).save("menu/ui/card_40")  # icon + name only
    # 12x12 frame for the player's 8x8 skin face (client-drawn text object, cannot be scaled).
    bevel(12, 12, SLOT, INK, SLOT_DARK, SLOT_LIGHT).save("menu/ui/head_frame")
    well = Img(36, 36, hexc("#C9B588"))
    well.rect(0, 0, 36, 1, hexc("#A8946A"))
    well.rect(0, 0, 1, 36, hexc("#A8946A"))
    well.save("menu/ui/well")
    for w in (70,):
        chip = Img(w, 16, hexc("#E0CFA4"))
        chip.save(f"menu/ui/chip_{w}")
    unknown = Img(16, 16)  # fallback icon for items without a vanilla icon glyph
    for y, row in enumerate(QMARK):
        for x, ch in enumerate(row):
            if ch == "#":
                unknown.rect(3 + x * 2, 1 + y * 2, 2, 2, SLOT_DARK)
    unknown.save("menu/ui/unknown_item")
    for w in (12, 18):  # unread mail badge: 1-2 digits / "99+"
        bevel(w, 12, RED, OUTLINE, lighten(RED, 0.3), darken(RED, 0.3)).save(f"menu/ui/badge_{w}")
    title_glyphs()


def title_glyphs():
    """Title screen (TitleMenu.java), same kit as the dex: the dark search field of the dex concept and the
    vanilla-style name plates (black at 25 %) shown over the head in the detail frame."""
    field = Img(176, 18, INK)
    field.rect(0, 0, 176, 1, OUTLINE)
    field.rect(0, 17, 176, 1, OUTLINE)
    field.rect(0, 0, 1, 18, OUTLINE)
    field.rect(175, 0, 1, 18, OUTLINE)
    field.rect(1, 1, 174, 1, hexc("#2A1C10"))
    field.rect(1, 1, 1, 16, hexc("#2A1C10"))
    field.save("menu/ui/field_176")
    for w in range(24, 201, 8):
        Img(w, 11, (0, 0, 0, 64)).save(f"menu/ui/plate_{w}")
    # Dark backing behind title names (the search field's leather ink): titles use chat colours chosen for a dark
    # chat background, so yellow or pink titles vanish on parchment without it.
    for w in range(24, 201, 8):
        tag = Img(w, 11, INK)
        tag.rect(0, 10, w, 1, OUTLINE)
        tag.save(f"menu/ui/tag_{w}")
    # Main menu profile backing (head, name, title), the colour of the currency chips.
    Img(148, 22, hexc("#E0CFA4")).save("menu/ui/profile_148")
    # Player faces drawn from the skin (SkinFaces): one white 2x2 dot per face pixel, tinted with the pixel colour,
    # and a 20x20 frame for the 16 px face. The client's own head object is fixed at 8x8.
    Img(2, 2, (255, 255, 255, 255)).save("menu/ui/px_2")
    bevel(20, 20, SLOT, INK, SLOT_DARK, SLOT_LIGHT).save("menu/ui/head_frame_20")


def write_defaults():
    """bundled-glyphs.yml: ascent min(8, height) makes every glyph hang from the text line its top is on, so
    CoreUi's line-by-line painting layers elements top to bottom instead of letting tall images cover earlier lines."""
    lines = ["# Generated by tools/dex_glyphs.py. glyphs.yml entries override these.", "glyphs:"]
    for base, _, files in sorted(os.walk(OUT)):
        for fn in sorted(files):
            path = os.path.join(base, fn)
            with open(path, "rb") as f:
                head = f.read(24)
            height = struct.unpack(">I", head[20:24])[0]
            gid = os.path.relpath(path, OUT).replace(os.sep, "/")[:-4]
            height = HEIGHTS.get(gid, height)
            lines.append(f"  {gid}:")
            if gid in HEIGHTS:
                lines.append(f"    height: {height}")
            lines.append(f"    ascent: {min(8, height)}")
    with open(os.path.join(ROOT, "src", "main", "resources", "bundled-glyphs.yml"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
