"""Generates the PLACEHOLDER textures for Traveler's Ring.

These are intentionally rough stand-ins until the client (Applecar) supplies final art.
They are plain 16x16 sprites. Replace the PNGs with the real art when it arrives.

Usage:  python -I tools/make_placeholder_textures.py
Writes into src/main/resources/assets/travelersring/textures/item/
"""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/travelersring/textures/item"
OUT.mkdir(parents=True, exist_ok=True)

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
    return img


def save(name, img):
    img.save(OUT / name)
    print("wrote", name)


save("travelers_ring.png", ring((214, 170, 60, 255), (150, 108, 30, 255), (60, 170, 220, 255), (190, 240, 255, 255)))
save("travelers_ring_netherite.png", ring((84, 70, 74, 255), (48, 40, 44, 255), (230, 140, 40, 255), (255, 220, 140, 255)))
save("travelers_ring_nether_star.png", ring((72, 60, 90, 255), (40, 30, 60, 255), (250, 250, 210, 255), (255, 255, 255, 255)))

# Smithing template: same silhouette and shading layout as the vanilla Netherite Upgrade template
# (dark outline, light top-left edge, four-shade mottled body, icon in the middle), recoloured violet,
# with a Nether Star glyph where vanilla has its icon.
CARD = [
    "................",
    "....222222222...",
    "...23444444342..",
    "...2444343333o..",
    "...o333312333o..",
    "...o332131332o..",
    "...o321333121o..",
    "...o213333312o..",
    "...o344333443o..",
    "...o434333433o..",
    "...o333333342o..",
    "...o233444331o..",
    "...o123322221o..",
    "....o11221ooo...",
    ".....ooooo......",
    "................",
]
STAR = [
    "................",
    "................",
    "................",
    "................",
    "........s.......",
    "........s.......",
    ".......sSs......",
    ".....ddsSssd....",
    ".......sss......",
    "........d.......",
    "........d.......",
    "................",
    "................",
    "................",
    "................",
    "................",
]
PAL = {
    "o": (27, 16, 48, 255), "1": (47, 31, 88, 255), "2": (62, 42, 110, 255),
    "3": (78, 55, 138, 255), "4": (97, 72, 166, 255),
    "S": (255, 251, 208, 255), "s": (240, 208, 96, 255), "d": (192, 144, 42, 255),
}
tpl = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for y in range(16):
    for x in range(16):
        ch = STAR[y][x] if STAR[y][x] != "." else CARD[y][x]
        if ch != ".":
            tpl.putpixel((x, y), PAL[ch])
save("nether_star_upgrade_smithing_template.png", tpl)

# empty smithing-table slot icon for the base (ring) slot: dark grey silhouette
slot = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
g = (120, 120, 120, 255)
for p in [(6, 5), (7, 5), (8, 5), (9, 5), (5, 6), (10, 6), (4, 7), (11, 7), (4, 8), (11, 8),
          (4, 9), (11, 9), (4, 10), (11, 10), (5, 11), (10, 11), (6, 12), (7, 12), (8, 12), (9, 12),
          (7, 3), (8, 3), (6, 4), (7, 4), (8, 4), (9, 4)]:
    slot.putpixel(p, g)
save("empty_slot_ring.png", slot)
