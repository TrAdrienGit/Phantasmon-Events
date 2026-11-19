"""
Icons for League Reborn's own held items (the four seeds, Amplifield Rock), drawn here from shapes and palettes: nothing
is extracted from a game. Writes 32x32 PNGs to assets/phantasmon_events/textures/gui/held_items/<showdown id>.png,
the size and style of Phantasmon's own virtual item icons.

    python tools/reborn_item_icons.py [contact_sheet.png]
"""
import math
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/phantasmon_events/textures/gui/held_items"
S = 32


def rgb(value):
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def noise(x, y):
    n = math.sin(x * 12.9898 + y * 78.233) * 43758.5453
    return n - math.floor(n)


def seed(dark, mid, light, mark):
    """A teardrop seed lying slanted, shaded per pixel, with a mark of its kind and a dark outline."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    dark, mid, light, mark = rgb(dark), rgb(mid), rgb(light), rgb(mark)
    inside = [[False] * S for _ in range(S)]
    angle = math.radians(-35)
    ca, sa = math.cos(angle), math.sin(angle)
    for y in range(S):
        for x in range(S):
            u = (x + 0.5 - 16) * ca - (y + 0.5 - 16) * sa
            v = (x + 0.5 - 16) * sa + (y + 0.5 - 16) * ca
            # Teardrop: round at the bottom, pointed at the top.
            t = (v + 12) / 24
            if not 0 <= t <= 1:
                continue
            half = 8.5 * math.sin(math.pi * t ** 0.8) * (1 - 0.25 * t)
            if abs(u) <= half:
                inside[y][x] = True
    for y in range(S):
        for x in range(S):
            if not inside[y][x]:
                continue
            edge = any(not (0 <= x + dx < S and 0 <= y + dy < S and inside[y + dy][x + dx])
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                px[x, y] = mix(dark, (0, 0, 0), 0.45) + (255,)
                continue
            # Light from the top left, grain on top.
            shade = 1 - ((x - 10) ** 2 + (y - 9) ** 2) ** 0.5 / 22
            c = mix(dark, mid, shade * 1.6)
            c = mix(c, light, (shade - 0.55) * 2.2)
            c = mix(c, (0, 0, 0), (noise(x, y) - 0.5) * 0.18)
            px[x, y] = c + (255,)
    # The mark: a groove down the middle in the kind's colour.
    for i in range(-8, 9):
        x = int(round(16 + i * sa * 0.9))
        y = int(round(16 + i * ca * 0.9))
        if 0 <= x < S and 0 <= y < S and inside[y][x]:
            px[x, y] = mix(mark, light, 0.25 if i < -3 else 0) + (255,)
    # A highlight.
    for x, y in ((11, 10), (12, 10), (11, 11)):
        if inside[y][x]:
            px[x, y] = mix(light, (255, 255, 255), 0.6) + (255,)
    return img


def rock():
    """A faceted grey rock with glowing rainbow veins: the field, amplified."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    pts = [(6, 20), (9, 9), (17, 5), (26, 10), (28, 21), (20, 28), (10, 27)]

    def inside(x, y):
        n = len(pts)
        c = False
        for i in range(n):
            x1, y1 = pts[i]
            x2, y2 = pts[(i + 1) % n]
            if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                c = not c
        return c

    mask = [[inside(x + 0.5, y + 0.5) for x in range(S)] for y in range(S)]
    veins = [rgb("F05050"), rgb("F0C040"), rgb("50D070"), rgb("50A0F0"), rgb("B070F0")]
    for y in range(S):
        for x in range(S):
            if not mask[y][x]:
                continue
            edge = any(not (0 <= x + dx < S and 0 <= y + dy < S and mask[y + dy][x + dx])
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                px[x, y] = (40, 40, 48, 255)
                continue
            # Facets: the light falls by side of two ridges.
            facet = 0.75 if x + y < 30 else 0.5
            facet += 0.12 if x > 17 else 0
            c = mix(rgb("4A4E58"), rgb("B8BCC8"), facet + (noise(x, y) - 0.5) * 0.2)
            # Veins: two wavy lines, coloured along their length.
            for k, off in enumerate((13, 21)):
                vy = off + 3 * math.sin(x * 0.55 + k)
                d = abs(y - vy)
                if d < 1.1:
                    col = veins[int((x / S) * len(veins) + k) % len(veins)]
                    c = mix(c, col, 1 - d * 0.5)
                elif d < 2.2:
                    col = veins[int((x / S) * len(veins) + k) % len(veins)]
                    c = mix(c, col, 0.3)
            px[x, y] = c + (255,)
    return img


ICONS = {
    "elementalseed": seed("8A2A18", "E06030", "F8C060", "3080E0"),
    "magicalseed": seed("4A2A78", "A060D0", "F0B0F0", "F0E070"),
    "telluricseed": seed("4A3218", "9A7040", "D8B878", "4E9A3A"),
    "syntheticseed": seed("2E3848", "7890A8", "D0E0F0", "40E0D0"),
    "amplifieldrock": rock(),
}

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, icon in ICONS.items():
        icon.save(OUT / f"{name}.png")
    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (len(ICONS) * 132, 132), (58, 62, 72, 255))
        for i, icon in enumerate(ICONS.values()):
            big = icon.resize((128, 128), Image.NEAREST)
            sheet.paste(big, (i * 132 + 2, 2), big)
        sheet.save(sys.argv[1])
    print(f"{len(ICONS)} icons -> {OUT}")
