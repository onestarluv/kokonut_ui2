"""가구 제작대(FurnitureMenu)용 가구 아이콘 렌더러. 표준 라이브러리만 사용합니다.

TooManyFurniture 모델(JSON)을 마인크래프트 인벤토리와 같은 각도(모델의 display.gui 회전, 기본 [30, 225, 0])로
그려서 PNG로 저장합니다. 다이얼로그 글자에는 3D 모델을 넣을 수 없어서 미리 그린 그림을 글리프로 씁니다.

실행:  py -I tools/furniture_icons.py [Nexo 폴더 [나무]]   (기본 D:/egoverse/home1/plugins/Nexo, 나무를 주면 그 나무만)
입력:  <Nexo>/pack/assets/toomanyfurniture/models/block/<나무>_furniture/<나무>_<종류>.json
       <Nexo>/pack/assets/toomanyfurniture/textures/block/<나무>_furniture.png
결과:  art/furniture/icon/<나무>_<종류>.png     32x32 (화면에서는 16px 칸)
       art/furniture/preview/<나무>_<종류>.png  64x64 (화면에서는 32px 미리보기)
이후 py -I tools/dex_glyphs.py 를 돌리면 bundled-glyphs/furniture/ 로 복사됩니다.
"""
import json
import math
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NEXO = sys.argv[1] if len(sys.argv) > 1 else "D:/egoverse/home1/plugins/Nexo"
ASSETS = os.path.join(NEXO, "pack", "assets", "toomanyfurniture")
OUT = os.path.join(ROOT, "art", "furniture")

PREVIEW = 64          # 미리보기 한 변(px). 아이콘은 그 절반.
SS = 3                # 미리보기 픽셀 하나를 SS x SS 번 샘플링
MAX_SCALE = 0.8       # 바닐라 블록 아이콘은 0.625. 작은 가구는 조금 키워서 알아보기 쉽게 한다.
SHADE = {"x": 0.6, "z": 0.8, "up": 1.0, "down": 0.5}   # 바닐라 블록 면 밝기


def read_png(path):
    """8비트 RGBA / RGB / 팔레트 PNG -> (w, h, [[(r, g, b, a)]])."""
    data = open(path, "rb").read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path}: PNG가 아닙니다")
    pos, idat, palette, trns = 8, b"", None, None
    while pos < len(data):
        size, tag = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + size]
        pos += 12 + size
        if tag == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif tag == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
    if depth != 8 or interlace or ctype not in (2, 3, 6):
        raise ValueError(f"{path}: 지원하지 않는 PNG 형식 (depth {depth}, type {ctype}, interlace {interlace})")
    bpp = {2: 3, 3: 1, 6: 4}[ctype]
    raw, stride = zlib.decompress(idat), w * bpp
    prev, rows, pos = bytearray(stride), [], 0
    for _ in range(h):
        kind = raw[pos]
        line = bytearray(raw[pos + 1:pos + 1 + stride])
        pos += 1 + stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if kind == 1:
                line[i] = (line[i] + a) & 255
            elif kind == 2:
                line[i] = (line[i] + b) & 255
            elif kind == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif kind == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        if ctype == 6:
            rows.append([tuple(line[x * 4:x * 4 + 4]) for x in range(w)])
        elif ctype == 2:
            rows.append([tuple(line[x * 3:x * 3 + 3]) + (255,) for x in range(w)])
        else:
            rows.append([palette[v] + ((trns[v] if trns and v < len(trns) else 255),) for v in line])
    return w, h, rows


def write_png(path, w, h, rows):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in rows)

    def chunk(tag, body):
        return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def rot(axis, deg):
    """오른손 좌표계 회전 행렬 (3x3, 행 우선)."""
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    if axis == "x":
        return ((1, 0, 0), (0, c, -s), (0, s, c))
    if axis == "y":
        return ((c, 0, s), (0, 1, 0), (-s, 0, c))
    return ((c, -s, 0), (s, c, 0), (0, 0, 1))


def mul(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def apply(m, v):
    return tuple(m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2] for i in range(3))


# 면마다 꼭짓점 4개 (마인크래프트 FaceInfo 순서): uv 왼쪽 위, 왼쪽 아래, 오른쪽 아래, 오른쪽 위.
# 값은 (x, y, z) 각각 0 = from 쪽, 1 = to 쪽.
FACES = {
    "down": (((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)), (0, -1, 0)),
    "up": (((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)), (0, 1, 0)),
    "north": (((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)), (0, 0, -1)),
    "south": (((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)), (0, 0, 1)),
    "west": (((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)), (-1, 0, 0)),
    "east": (((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)), (1, 0, 0)),
}


def quads(model):
    """모델의 보이는 면 -> [(꼭짓점 3개의 화면 좌표·깊이, uv 3개, 밝기)] (모델 단위, 화면 y는 아래로)."""
    gui = (model.get("display") or {}).get("gui") or {}
    rx, ry, rz = gui.get("rotation", [30, 225, 0])
    view = mul(rot("x", rx), mul(rot("y", ry), rot("z", rz)))
    out = []
    for e in model["elements"]:
        lo = [min(a, b) for a, b in zip(e["from"], e["to"])]
        hi = [max(a, b) for a, b in zip(e["from"], e["to"])]
        r = e.get("rotation") or {}
        local = rot(r["axis"], r.get("angle", 0)) if r.get("angle") else None
        origin = r.get("origin", [8, 8, 8])

        def place(p):
            if local:
                q = apply(local, tuple(p[i] - origin[i] for i in range(3)))
                p = tuple(q[i] + origin[i] for i in range(3))
            return p

        for name, face in (e.get("faces") or {}).items():
            corners, normal = FACES[name]
            n = apply(local, normal) if local else normal
            if apply(view, n)[2] <= 1e-6:      # 뒤를 보는 면
                continue
            u1, v1, u2, v2 = face.get("uv", [0, 0, 16, 16])
            turn = (face.get("rotation", 0) // 90) % 4
            pts, uvs = [], []
            for i in (0, 1, 3):                # 원점, 아래 방향 끝, 오른쪽 방향 끝
                c = corners[i]
                p = place(tuple(hi[k] if c[k] else lo[k] for k in range(3)))
                x, y, z = apply(view, tuple(p[k] - 8 for k in range(3)))
                pts.append((x, -y, z))
                j = (i + turn) % 4
                uvs.append((u1 if j in (0, 1) else u2, v1 if j in (0, 3) else v2))
            shade = n[0] * n[0] * SHADE["x"] + n[2] * n[2] * SHADE["z"] + n[1] * n[1] * SHADE["up" if n[1] > 0 else "down"]
            out.append((pts, uvs, shade))
    return out


def render(model, texture):
    """PREVIEW*SS 크기의 색 버퍼(없으면 None)를 돌려준다."""
    tw, th, tex = texture
    faces = quads(model)
    corners = [c for pts, _, _ in faces for c in (pts[0], pts[1], pts[2],
                                                  (pts[1][0] + pts[2][0] - pts[0][0], pts[1][1] + pts[2][1] - pts[0][1]))]
    xs, ys = [c[0] for c in corners], [c[1] for c in corners]
    width, height = max(xs) - min(xs), max(ys) - min(ys)
    size = PREVIEW * SS
    scale = min(MAX_SCALE, 16 / max(width, height)) * size / 16        # 모델 단위 -> 샘플 픽셀
    ox = size / 2 - (min(xs) + max(xs)) / 2 * scale
    oy = size / 2 - (min(ys) + max(ys)) / 2 * scale
    color = [[None] * size for _ in range(size)]
    depth = [[-1e9] * size for _ in range(size)]
    for pts, uvs, shade in faces:
        (x0, y0, z0), (x1, y1, z1), (x3, y3, z3) = [(p[0] * scale + ox, p[1] * scale + oy, p[2]) for p in pts]
        ax, ay, bx, by = x3 - x0, y3 - y0, x1 - x0, y1 - y0       # s축(오른쪽), t축(아래)
        det = ax * by - ay * bx
        if abs(det) < 1e-9:
            continue
        cx = (x0, x1, x3, x1 + x3 - x0)
        cy = (y0, y1, y3, y1 + y3 - y0)
        for py in range(max(0, math.floor(min(cy))), min(size, math.ceil(max(cy)))):
            dy = py + 0.5 - y0
            for px in range(max(0, math.floor(min(cx))), min(size, math.ceil(max(cx)))):
                dx = px + 0.5 - x0
                s = (dx * by - dy * bx) / det
                t = (ax * dy - ay * dx) / det
                if s < 0 or s > 1 or t < 0 or t > 1:
                    continue
                z = z0 + s * (z3 - z0) + t * (z1 - z0)
                if z <= depth[py][px]:
                    continue
                u = uvs[0][0] + s * (uvs[2][0] - uvs[0][0]) + t * (uvs[1][0] - uvs[0][0])
                v = uvs[0][1] + s * (uvs[2][1] - uvs[0][1]) + t * (uvs[1][1] - uvs[0][1])
                r, g, b, a = tex[min(th - 1, max(0, int(v * th / 16)))][min(tw - 1, max(0, int(u * tw / 16)))]
                if a < 128:
                    continue
                depth[py][px] = z
                color[py][px] = (r * shade, g * shade, b * shade)
    return color


def shrink(color, block):
    """block x block 샘플을 한 픽셀로. 절반 이상 덮인 픽셀만 불투명하게 남겨 윤곽을 또렷하게 한다."""
    size = len(color) // block
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            hit = [c for yy in range(block) for c in color[y * block + yy][x * block:x * block + block] if c]
            if len(hit) * 2 < block * block:
                row.append((0, 0, 0, 0))
            else:
                row.append(tuple(min(255, round(sum(c[i] for c in hit) / len(hit))) for i in range(3)) + (255,))
        rows.append(row)
    return size, rows


def main():
    models = os.path.join(ASSETS, "models", "block")
    count = 0
    for folder in sorted(os.listdir(models)):
        if not folder.endswith("_furniture") or (len(sys.argv) > 2 and not folder.startswith(sys.argv[2])):
            continue
        texture = read_png(os.path.join(ASSETS, "textures", "block", folder + ".png"))
        for name in sorted(os.listdir(os.path.join(models, folder))):
            if not name.endswith(".json"):
                continue
            with open(os.path.join(models, folder, name), encoding="utf-8") as f:
                color = render(json.load(f), texture)
            for kind, block in (("preview", SS), ("icon", SS * 2)):
                size, rows = shrink(color, block)
                write_png(os.path.join(OUT, kind, name[:-5] + ".png"), size, size, rows)
            count += 1
        print(folder, flush=True)
    print(f"{count} furniture -> {OUT}")


if __name__ == "__main__":
    main()
