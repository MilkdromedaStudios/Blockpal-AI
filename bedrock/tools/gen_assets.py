#!/usr/bin/env python3
"""Generate the Blockpal Bedrock pack textures (skins + pack icons).

Pure-stdlib PNG writer — no Pillow needed. Skins use the classic 64x64
player-skin layout, so any standard skin PNG dropped over these files works.
Run from anywhere: paths are resolved relative to this file.
"""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "packs"
SKIN_DIR = ROOT / "resource" / "textures" / "entity" / "companion"


def write_png(path: Path, width: int, height: int, pixels):
    def chunk(tag: bytes, data: bytes) -> bytes:
        out = struct.pack(">I", len(data)) + tag + data
        return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = b"".join(
        b"\x00" + b"".join(struct.pack("4B", *px) for px in row) for row in pixels
    )
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"wrote {path} ({width}x{height})")


def blank(width: int, height: int):
    return [[(0, 0, 0, 0)] * width for _ in range(height)]


def fill(px, x0, y0, w, h, color):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px[y][x] = color


def box_faces(u, v, w, h, d):
    """Face rects (x, y, w, h) of a standard box-UV unwrap at (u, v)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def fill_box(px, faces, color):
    for x, y, w, h in faces.values():
        fill(px, x, y, w, h, color)


def make_skin(path: Path, p):
    """p: palette dict — skin, hair, eye, pupil, shirt, sleeve, pants, shoes."""
    px = blank(64, 64)

    head = box_faces(0, 0, 8, 8, 8)
    fill_box(px, head, p["skin"])
    # Hair: top of head, upper rows of the sides/back, fringe on the face.
    fill(px, *head["top"], p["hair"])
    for face in ("right", "left", "back"):
        x, y, w, h = head[face]
        fill(px, x, y, w, 3, p["hair"])
    fx, fy, fw, fh = head["front"]
    fill(px, fx, fy, fw, 2, p["hair"])
    # Eyes (white + pupil) and a hint of a mouth.
    px[fy + 4][fx + 1] = p["eye"]
    px[fy + 4][fx + 2] = p["pupil"]
    px[fy + 4][fx + 5] = p["pupil"]
    px[fy + 4][fx + 6] = p["eye"]
    px[fy + 6][fx + 3] = p["mouth"]
    px[fy + 6][fx + 4] = p["mouth"]

    body = box_faces(16, 16, 8, 12, 4)
    fill_box(px, body, p["shirt"])
    bx, by, bw, bh = body["front"]
    fill(px, bx + 3, by + 2, 2, 2, p["accent"])  # chest emblem

    for u, v in ((40, 16), (32, 48)):  # right arm, left arm
        arm = box_faces(u, v, 4, 12, 4)
        fill_box(px, arm, p["skin"])
        for face in ("right", "front", "left", "back"):
            x, y, w, h = arm[face]
            fill(px, x, y, w, 5, p["sleeve"])
        fill(px, *arm["top"], p["sleeve"])

    for u, v in ((0, 16), (16, 48)):  # right leg, left leg
        leg = box_faces(u, v, 4, 12, 4)
        fill_box(px, leg, p["pants"])
        for face in ("right", "front", "left", "back"):
            x, y, w, h = leg[face]
            fill(px, x, y + h - 2, w, 2, p["shoes"])
        fill(px, *leg["bottom"], p["shoes"])

    write_png(path, 64, 64, px)


PALETTES = {
    "default": {  # Ethan — teal shirt, navy trousers (Blockpal colours)
        "skin": (224, 180, 154, 255), "hair": (79, 50, 34, 255),
        "eye": (255, 255, 255, 255), "pupil": (59, 102, 217, 255),
        "mouth": (188, 132, 106, 255), "shirt": (42, 166, 160, 255),
        "sleeve": (34, 132, 128, 255), "accent": (18, 50, 90, 255),
        "pants": (39, 50, 75, 255), "shoes": (58, 58, 58, 255),
    },
    "robot": {
        "skin": (185, 196, 206, 255), "hair": (90, 103, 114, 255),
        "eye": (53, 224, 255, 255), "pupil": (53, 224, 255, 255),
        "mouth": (90, 103, 114, 255), "shirt": (140, 152, 163, 255),
        "sleeve": (110, 122, 133, 255), "accent": (53, 224, 255, 255),
        "pants": (90, 103, 114, 255), "shoes": (60, 70, 78, 255),
    },
    "ember": {
        "skin": (233, 173, 128, 255), "hair": (112, 34, 20, 255),
        "eye": (255, 236, 170, 255), "pupil": (198, 70, 46, 255),
        "mouth": (176, 110, 76, 255), "shirt": (198, 70, 46, 255),
        "sleeve": (158, 52, 34, 255), "accent": (255, 176, 46, 255),
        "pants": (74, 38, 34, 255), "shoes": (40, 24, 22, 255),
    },
    "void": {
        "skin": (60, 46, 92, 255), "hair": (18, 13, 34, 255),
        "eye": (179, 124, 255, 255), "pupil": (232, 214, 255, 255),
        "mouth": (36, 27, 58, 255), "shirt": (36, 27, 58, 255),
        "sleeve": (26, 19, 44, 255), "accent": (179, 124, 255, 255),
        "pants": (26, 19, 44, 255), "shoes": (14, 10, 26, 255),
    },
}


def make_icon(path: Path, border):
    """256x256 pack icon: navy gradient, cyan frame, companion face."""
    size = 256
    px = blank(size, size)
    top, bottom = (10, 20, 40), (20, 50, 90)
    for y in range(size):
        t = y / (size - 1)
        color = tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)) + (255,)
        for x in range(size):
            px[y][x] = color
    for i in range(4):  # frame
        for x in range(i, size - i):
            px[i][x] = border
            px[size - 1 - i][x] = border
        for y in range(i, size - i):
            px[y][i] = border
            px[y][size - 1 - i] = border
    # Big companion face: skin square, hair band, eyes, mouth.
    p = PALETTES["default"]
    f0, fs = 64, 128
    fill(px, f0, f0, fs, fs, p["skin"])
    fill(px, f0, f0, fs, 32, p["hair"])
    for ex in (f0 + 24, f0 + 80):
        fill(px, ex, f0 + 56, 24, 16, p["eye"])
        fill(px, ex + 12 if ex == f0 + 24 else ex, f0 + 56, 12, 16, p["pupil"])
    fill(px, f0 + 48, f0 + 96, 32, 10, p["mouth"])
    write_png(path, size, size, px)


def main():
    for name, palette in PALETTES.items():
        make_skin(SKIN_DIR / f"{name}.png", palette)
    make_icon(ROOT / "behavior" / "pack_icon.png", (42, 166, 160, 255))
    make_icon(ROOT / "resource" / "pack_icon.png", (53, 224, 255, 255))


if __name__ == "__main__":
    main()
