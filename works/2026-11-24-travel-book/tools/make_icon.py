"""Launcher icon + splash mark from the chosen render (icon round 4, 1-A shelf_a, operator 11-26; kept 11-28 over round 5).
Also writes a monochrome layer for Android 13+ themed icons.

Adaptive icon: the whole render fills the visible 72 dp of the 108 dp canvas; the outer ring is the render's
paper colour, and the render's edge fades into it so no seam shows. usage: python tools/make_icon.py
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "mockups/icon/r4/shelf_a.jpg"
RES = ROOT / "app/android/app/src/main/res"
PAPER = (238, 225, 212)  # median border colour of shelf_a (#EEE1D4)
DENS = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def foreground(px: int) -> Image.Image:
    """px = 108 dp canvas in pixels."""
    inner = round(px * 72 / 108)
    art = Image.open(SRC).convert("RGB").resize((inner, inner), Image.LANCZOS)
    canvas = Image.new("RGB", (px, px), PAPER)
    mask = Image.new("L", (inner, inner), 0)
    pad = max(2, inner // 18)
    ImageDraw.Draw(mask).rectangle((pad, pad, inner - pad, inner - pad), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(pad / 1.5))
    off = (px - inner) // 2
    canvas.paste(art, (off, off), mask)
    return canvas


def monochrome(px: int) -> Image.Image:
    """Android 13+ themed icon: the same render, recoloured. Alpha = ink: the book and shelf only (no wall, no shadow),
    darker parts of the photo more opaque, so the launcher's tint redraws the original picture in its colour."""
    src = Image.open(SRC).convert("RGB")
    lum = (np.asarray(src).astype(float) / 255) @ [0.299, 0.587, 0.114]
    obj = Image.new("L", src.size, 0)  # shelf_a coordinates: spine, cover, shelf
    g = ImageDraw.Draw(obj)
    g.rectangle((311, 238, 368, 1006), fill=255)
    g.polygon([(368, 238), (932, 262), (932, 996), (368, 1008)], fill=255)
    g.rectangle((204, 988, 1043, 1054), fill=255)
    obj = np.asarray(obj.filter(ImageFilter.GaussianBlur(1.2))).astype(float) / 255
    ink = obj * (0.30 + 0.70 * np.clip((0.93 - lum) / 0.85, 0, 1))
    inner = round(px * 72 / 108)
    m = Image.fromarray((ink * 255).astype("uint8")).resize((inner, inner), Image.LANCZOS)
    out = Image.new("LA", (px, px), (255, 0))
    out.paste(Image.new("LA", (inner, inner), (255, 255)), ((px - inner) // 2,) * 2, m)
    return out


def main() -> None:
    (RES / "mipmap-anydpi-v26").mkdir(exist_ok=True)
    for name, k in DENS.items():
        d = RES / f"mipmap-{name}"
        foreground(round(108 * k)).save(d / "ic_launcher_foreground.png", optimize=True)
        monochrome(round(108 * k)).save(d / "ic_launcher_monochrome.png", optimize=True)
        # legacy square icon (pre-adaptive launchers): the visible 72 dp part at 48 dp
        fg = foreground(round(108 * k * 2))
        m = fg.width * 18 // 108
        fg.crop((m, m, fg.width - m, fg.width - m)).resize((round(48 * k),) * 2, Image.LANCZOS).save(d / "ic_launcher.png", optimize=True)
    (RES / "mipmap-anydpi-v26/ic_launcher.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />\n'
        "</adaptive-icon>\n", encoding="utf-8")
    hexc = "#%02X%02X%02X" % PAPER
    (RES / "values/colors.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="ic_launcher_background">{hexc}</color>\n'
        '    <color name="splash_paper">#F4EFE7</color>\n</resources>\n', encoding="utf-8")
    print("ok", hexc, float(np.mean(np.asarray(foreground(432)))))


if __name__ == "__main__":
    main()
