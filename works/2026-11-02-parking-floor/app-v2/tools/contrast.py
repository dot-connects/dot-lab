"""Text contrast measured on the rendered widget PNGs (spec 11: 4.5:1).

    python tools/contrast.py [shots/widget]      -> one line per shot (worst text), exit 1 if any text is under 4.5

WidgetShots writes <name>.json next to each PNG with every drawn text box (px). Per box:
  background = median colour of a 2-px ring just outside the box (what the text actually sits on, halo included),
  ink        = median of the 10 % box pixels that differ most from that background (glyph cores, not anti-aliased edges),
  ratio      = WCAG contrast of ink vs background.
"""
import json
import sys
from pathlib import Path
from statistics import median

from PIL import Image


def lum(c) -> float:
    def ch(v):
        s = v / 255
        return s / 12.92 if s <= .03928 else ((s + .055) / 1.055) ** 2.4
    return .2126 * ch(c[0]) + .7152 * ch(c[1]) + .0722 * ch(c[2])


def ratio(a, b) -> float:
    x, y = lum(a), lum(b)
    return (max(x, y) + .05) / (min(x, y) + .05)


def med(px):
    return tuple(median(p[i] for p in px) for i in range(3))


def measure(im: Image.Image, box) -> float:
    x0, y0, x1, y1 = (round(v) for v in box)
    x0, y0, x1, y1 = max(0, x0), max(0, y0), min(im.width - 1, x1), min(im.height - 1, y1)
    if x1 - x0 < 2 or y1 - y0 < 2:
        return 99.0
    ring = [im.getpixel((x, y)) for x in range(max(0, x0 - 3), min(im.width, x1 + 3)) for y in (max(0, y0 - 3), max(0, y0 - 2), min(im.height - 1, y1 + 2), min(im.height - 1, y1 + 3))]
    ring += [im.getpixel((x, y)) for y in range(y0, y1) for x in (max(0, x0 - 3), min(im.width - 1, x1 + 3))]
    bg = med(ring)
    inside = sorted((im.getpixel((x, y)) for x in range(x0, x1) for y in range(y0, y1)), key=lambda p: -ratio(p, bg))
    ink = med(inside[: max(1, len(inside) // 10)])
    return ratio(ink, bg)


def main(root: Path) -> int:
    worst_all, bad = 99.0, 0
    for js in sorted(root.glob("*.json")):
        png = js.with_suffix(".png")
        if not png.exists():
            continue
        im = Image.open(png).convert("RGB")
        res = [(measure(im, t["box"]), t["text"]) for t in json.loads(js.read_text(encoding="utf-8"))["texts"] if t["text"].strip()]
        if not res:
            continue
        r, t = min(res)
        worst_all = min(worst_all, r)
        flag = "OK " if r >= 4.5 else "LOW"
        bad += r < 4.5
        print(f"{flag} {js.stem:28} worst {r:5.2f}  '{t.strip()}'  ({len(res)} texts)")
    print(f"worst overall {worst_all:.2f}, {bad} shot(s) under 4.5")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "shots" / "widget"))
