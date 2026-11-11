"""Review sheets for the r9 shot matrix: one PNG per (group, subject), rows = themes, columns = variants.

    python tools/sheets.py standby/build/shots          -> standby/build/shots/_sheets/<group>/<subject>.png

A subject is the shot name without its device suffix; card shots (`card-<type>-<class>[-night]`) are grouped per card
type so a row shows every size class. Images are scaled to dp (xhdpi shots / 2, xxhdpi / 3) so 13 dp text stays
readable; a missing cell is a red cross, never a silent shift. Labels: theme on the left, variant on top.
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

DEVICES = ["fold-cover-land", "fold-cover-port", "fold-inner", "bar-land", "bar-port", "flip-cover"]
DENSITY = {"bar-land": 3, "bar-port": 3}           # xxhdpi; the rest are xhdpi (2)
LABEL_W, LABEL_H, GAP = 120, 28, 8


def split(stem: str) -> tuple[str, str]:
    """(subject, variant): device goes to the variant; card shots also move their size class there."""
    dev = next((d for d in DEVICES if stem.endswith("-" + d)), "")
    base = stem[: -len(dev) - 1] if dev else stem
    m = re.match(r"(card-[a-z_]+)-(L|M|Sw|St|XS)(-night)?$", base)
    if m:
        return m.group(1), f"{m.group(2)}{m.group(3) or ''}"
    return base, dev or "-"


def main(root: Path) -> int:
    known = ["iphone", "paper", "moodlight", "candle", "flip", "sky", "daypaper", "radio", "neon", "terminal"]
    themes = [t for t in known if (root / t).is_dir()]          # other folders are non-theme shot groups
    cells: dict[tuple[str, str], dict[str, dict[str, Path]]] = defaultdict(lambda: defaultdict(dict))
    for t in themes:
        for f in (root / t).rglob("*.png"):
            subject, variant = split(f.stem)
            cells[(f.parent.name, subject)][variant][t] = f
    font = ImageFont.load_default()
    n = 0
    for (group, subject), by_variant in sorted(cells.items()):
        variants = sorted(by_variant)
        imgs = {}
        for v in variants:
            for t, f in by_variant[v].items():
                dev = next((d for d in DEVICES if f.stem.endswith(d)), "")
                im = Image.open(f).convert("RGB")
                k = DENSITY.get(dev, 2)
                imgs[(v, t)] = im.resize((im.width // k, im.height // k))
        col_w = {v: max((imgs[(v, t)].width for t in themes if (v, t) in imgs), default=100) for v in variants}
        row_h = {t: max((imgs[(v, t)].height for v in variants if (v, t) in imgs), default=100) for t in themes}
        W = LABEL_W + sum(col_w[v] + GAP for v in variants)
        H = LABEL_H + sum(row_h[t] + GAP for t in themes)
        sheet = Image.new("RGB", (W, H), (32, 32, 34))
        d = ImageDraw.Draw(sheet)
        x = LABEL_W
        for v in variants:
            d.text((x + 4, 8), v, fill=(230, 230, 230), font=font)
            x += col_w[v] + GAP
        y = LABEL_H
        for t in themes:
            d.text((6, y + 6), t, fill=(230, 230, 230), font=font)
            x = LABEL_W
            for v in variants:
                im = imgs.get((v, t))
                if im is None:
                    d.line((x, y, x + col_w[v], y + row_h[t]), fill=(220, 40, 40), width=3)
                    d.line((x + col_w[v], y, x, y + row_h[t]), fill=(220, 40, 40), width=3)
                else:
                    sheet.paste(im, (x, y))
                x += col_w[v] + GAP
            y += row_h[t] + GAP
        out = root / "_sheets" / group / f"{subject}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        sheet.save(out)
        n += 1
    print(f"{n} sheets, {len(themes)} themes")
    return 0


if __name__ == "__main__":
    sys.exit(main(Path(sys.argv[1] if len(sys.argv) > 1 else "standby/build/shots")))
