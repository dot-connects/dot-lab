"""Gate (plan-r9 Task 6): light themes never show a black page.

    python tools/check_light.py [shots_dir]     (default standby/build/shots) -> exit 1 on a dark sample

For every DAYPAPER / RADIO day shot in the board, notif, clock and primitives groups, samples the four corners and
the four edge midpoints inside the board margin (where only the page can be). Three or more samples darker than luminance 0.5
fail: that is a black rectangle left by a face or a test harness; one or two are content reaching the edge (a side
column, big digits). Night shots and faces that own their background by design (Solar, photo time, r10 색 블록) are
skipped.
"""
import sys
from pathlib import Path

from PIL import Image

THEMES = ["daypaper", "radio"]
GROUPS = ["board", "notif", "clock", "primitives"]


def lum(rgb: tuple[int, int, int]) -> float:
    def ch(v: int) -> float:
        c = v / 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (ch(v) for v in rgb[:3])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def points(w: int, h: int) -> list[tuple[int, int]]:
    # 1/3 of the board margin (≥ 16 dp ≈ 32 px at xhdpi) from each edge
    m = max(4, round(min(w, h) * 0.06 / 3))
    return [(m, m), (w - m, m), (m, h - m), (w - m, h - m), (w // 2, m), (w // 2, h - m), (m, h // 2), (w - m, h // 2)]


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "standby/build/shots")
    bad, seen = [], 0
    for th in THEMES:
        for g in GROUPS:
            for f in sorted((root / th / g).glob("*.png")):
                if any(k in f.stem for k in ("night", "dusk", "solar", "photos_time", "color_block")):
                    continue
                seen += 1
                im = Image.open(f).convert("RGB")
                dark = [(x, y) for x, y in points(*im.size) if lum(im.getpixel((min(x, im.width - 1), min(y, im.height - 1)))) < 0.5]
                if len(dark) >= 3:
                    bad.append(f"{f.relative_to(root).as_posix()}: dark at {dark[:3]}")
    print("\n".join(bad) if bad else f"check_light: ok ({seen} shots)")
    return 1 if bad or not seen else 0


if __name__ == "__main__":
    sys.exit(main())
