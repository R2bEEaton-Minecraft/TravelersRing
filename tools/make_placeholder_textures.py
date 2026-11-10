"""Generates the PLACEHOLDER textures for Traveler's Ring.

These are intentionally rough stand-ins until the client (Applecar) supplies final art.
Every texture carries a 2x2 magenta marker in the top-left corner so placeholders are obvious in-game.

Usage:  python -I tools/make_placeholder_textures.py
Writes into src/main/resources/assets/travelersring/textures/item/
"""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/travelersring/textures/item"
OUT.mkdir(parents=True, exist_ok=True)

MAGENTA = (255, 0, 255, 255)


def marker(img):
    for x in range(2):
        for y in range(2):
            img.putpixel((x, y), MAGENTA)


def ring(band, shade, gem, gem_hi):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # band: ellipse-ish ring centered at (8, 9)
    pts = [
        (6, 5), (7, 5), (8, 5), (9, 5),
        (5, 6), (10, 6),
        (4, 7), (11, 7),
        (4, 8), (11, 8),
        (4, 9), (11, 9),
        (4, 10), (11, 10),
        (5, 11), (10, 11),
        (6, 12), (7, 12), (8, 12), (9, 12),
    ]
    for p in pts:
        img.putpixel(p, band)
    # shading on the lower/right side
    for p in [(10, 6), (11, 7), (11, 8), (11, 9), (11, 10), (10, 11), (9, 12), (8, 12)]:
        img.putpixel(p, shade)
    # gem on top
    for p in [(7, 3), (8, 3), (6, 4), (7, 4), (8, 4), (9, 4)]:
        img.putpixel(p, gem)
    img.putpixel((7, 3), gem_hi)
    img.putpixel((7, 4), gem_hi)
    marker(img)
    return img


def save(name, img):
    img.save(OUT / name)
    print("wrote", name)


save("travelers_ring.png", ring((214, 170, 60, 255), (150, 108, 30, 255), (60, 170, 220, 255), (190, 240, 255, 255)))
save("travelers_ring_netherite.png", ring((84, 70, 74, 255), (48, 40, 44, 255), (230, 140, 40, 255), (255, 220, 140, 255)))
save("travelers_ring_nether_star.png", ring((72, 60, 90, 255), (40, 30, 60, 255), (250, 250, 210, 255), (255, 255, 255, 255)))

# smithing template: a card with a star glyph
tpl = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
card = (96, 70, 140, 255)
edge = (50, 34, 80, 255)
for x in range(3, 13):
    for y in range(1, 15):
        tpl.putpixel((x, y), card)
for x in range(3, 13):
    tpl.putpixel((x, 1), edge)
    tpl.putpixel((x, 14), edge)
for y in range(1, 15):
    tpl.putpixel((3, y), edge)
    tpl.putpixel((12, y), edge)
star = (255, 250, 200, 255)
for p in [(7, 4), (8, 4), (7, 5), (8, 5), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7),
          (6, 8), (7, 8), (8, 8), (9, 8), (6, 9), (7, 9), (8, 9), (9, 9), (5, 10), (6, 10), (9, 10), (10, 10)]:
    tpl.putpixel(p, star)
marker(tpl)
save("nether_star_upgrade_smithing_template.png", tpl)

# empty smithing-table slot icon for the base (ring) slot: dark grey silhouette
slot = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
g = (120, 120, 120, 255)
for p in [(6, 5), (7, 5), (8, 5), (9, 5), (5, 6), (10, 6), (4, 7), (11, 7), (4, 8), (11, 8),
          (4, 9), (11, 9), (4, 10), (11, 10), (5, 11), (10, 11), (6, 12), (7, 12), (8, 12), (9, 12),
          (7, 3), (8, 3), (6, 4), (7, 4), (8, 4), (9, 4)]:
    slot.putpixel(p, g)
save("empty_slot_ring.png", slot)
