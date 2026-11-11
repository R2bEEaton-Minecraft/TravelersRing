"""Generates the PLACEHOLDER textures for Traveler's Ring.

Rough stand-ins until the client (Applecar) supplies final art. They are plain 16x16 sprites;
replace the PNGs with the real art when it arrives.

Usage:  python -I tools/make_placeholder_textures.py
Writes into src/main/resources/assets/travelersring/textures/item/
"""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/travelersring/textures/item"
OUT.mkdir(parents=True, exist_ok=True)


def save(name, img):
    img.save(OUT / name)
    print("wrote", name)


def paint(grid, pal):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), pal[ch])
    return img


# ---- Rings: upright band with a faceted gem on top, dark outline (the usual Curios-ring look) ----
# o outline   L band highlight   B band   D band shadow   h gem highlight   g gem   G gem shadow
RING = [
    "................",
    "................",
    "................",
    "......oooo......",
    ".....ohhggo.....",
    ".....ohggGo.....",
    "....ooLGGBoo....",
    "...oLBooooBDo...",
    "..oLBo....oBDo..",
    "..oLBo....oBDo..",
    "..oBBo....oDDo..",
    "..oBDo....oDDo..",
    "...oBDooooDDo...",
    "....ooDDDDoo....",
    ".....oooooo.....",
    "................",
]


def rgb(r, g, b):
    return (r, g, b, 255)


def ring(outline, light, band, shadow, gem_hi, gem, gem_dark):
    return paint(RING, {"o": rgb(*outline), "L": rgb(*light), "B": rgb(*band), "D": rgb(*shadow),
                        "h": rgb(*gem_hi), "g": rgb(*gem), "G": rgb(*gem_dark)})


# Base: gold band, blue gem
save("travelers_ring.png", ring((60, 36, 10), (255, 225, 120), (222, 176, 60), (160, 110, 30),
                                (190, 240, 255), (70, 170, 225), (30, 100, 170)))
# Netherite: dark band, ember gem
save("travelers_ring_netherite.png", ring((22, 18, 22), (140, 124, 130), (90, 76, 82), (52, 44, 50),
                                           (255, 225, 150), (240, 140, 40), (170, 70, 20)))
# Nether Star: violet band, star-white gem
save("travelers_ring_nether_star.png", ring((22, 14, 38), (140, 116, 180), (86, 66, 120), (48, 36, 78),
                                             (255, 255, 255), (250, 240, 170), (200, 170, 70)))

# ---- Smithing template: same silhouette and shading layout as the vanilla Netherite Upgrade template
# (dark outline, light top-left edge, four-shade mottled body, icon in the middle), recoloured violet,
# with a Nether Star glyph where vanilla has its icon. ----
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

# ---- Empty smithing-table slot icon for the ring slot: grey silhouette of the ring ----
slot = paint(RING, {"o": (110, 110, 110, 255), "L": (160, 160, 160, 255), "B": (140, 140, 140, 255),
                    "D": (120, 120, 120, 255), "h": (170, 170, 170, 255), "g": (145, 145, 145, 255),
                    "G": (125, 125, 125, 255)})
save("empty_slot_ring.png", slot)
