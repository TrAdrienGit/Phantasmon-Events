"""Writes the PLACEHOLDER Zarbi (Unown) alphabet of the Trahison game:
src/main/resources/assets/phantasmon_events/textures/trahison/zarbi/glyphs.png

A grid of 13 columns x 2 rows of square cells, white strokes on transparent (the client tints them): row 1 holds A..M, row 2
N..Z. The cell size is free (this script draws 16 px cells; a hand drawn sheet may use 32 or 64 px) as long as the grid stays
13 x 2. Replace the PNG by a hand drawn one, nothing else to change. Run: python tools/zarbi_placeholder.py
"""
import os
import random

from PIL import Image, ImageDraw

CELL = 16
COLS = 13
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "phantasmon_events",
                   "textures", "trahison", "zarbi", "glyphs.png")
POINTS = [(3 + 5 * x, 3 + 5 * y) for y in range(3) for x in range(3)]


def glyph(index, seen):
    rng = random.Random(1000 + index)
    while True:
        length = rng.randint(3, 5)
        path = [rng.randrange(9)]
        while len(path) < length:
            nxt = rng.randrange(9)
            if nxt != path[-1]:
                path.append(nxt)
        key = frozenset(frozenset((a, b)) for a, b in zip(path, path[1:]))
        if key not in seen:
            seen.add(key)
            return path, rng.choice([None, rng.randrange(9)])


def main():
    image = Image.new("RGBA", (CELL * COLS, CELL * 2), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    seen = set()
    for i in range(26):
        path, dot = glyph(i, seen)
        ox = (i % COLS) * CELL
        oy = (i // COLS) * CELL
        pts = [(ox + POINTS[p][0], oy + POINTS[p][1]) for p in path]
        draw.line(pts, fill=(255, 255, 255, 255), width=2)
        for px, py in pts:
            draw.rectangle([px - 1, py - 1, px, py], fill=(255, 255, 255, 255))
        if dot is not None:
            dx, dy = ox + POINTS[dot][0], oy + POINTS[dot][1]
            draw.rectangle([dx - 1, dy - 1, dx + 1, dy + 1], fill=(255, 255, 255, 255))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image.save(OUT)
    print("written", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
