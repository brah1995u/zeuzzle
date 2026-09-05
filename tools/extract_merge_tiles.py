"""Extract the approved Olympus tile artwork from the supplied 858x1920 reference."""

from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets" / "olympus_merge_reference.png"
OUTPUT = ROOT / "assets" / "tiles"

# Pixel boxes are deliberately tight to the seven authored tile faces.
TILES = {
    2: (52, 638, 231, 824),
    4: (244, 638, 422, 824),
    8: (52, 838, 231, 1034),
    16: (628, 838, 807, 1034),
    32: (244, 1062, 422, 1263),
    64: (244, 1286, 422, 1501),
    128: (628, 1286, 808, 1503),
}


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with Image.open(SOURCE) as source:
        for value, box in TILES.items():
            source.crop(box).save(OUTPUT / f"tile_{value}.png", optimize=True)


if __name__ == "__main__":
    main()
