"""Self-hosted Korean fonts for the mockups.

Each theme fragment declares what it uses on a line `<!-- kfonts: Pretendard:300;500;800 | SUIT:400;700 -->`.
preview.py calls faces_css(); each requested weight becomes one base64 @font-face, subset to the glyphs the
mockups use (woff2 sources are embedded whole: no brotli in this venv).
"""
import base64
import hashlib
import io
import re
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

R5 = Path(__file__).resolve().parent
SRC = R5.parent.parent / "fontsrc"
CACHE = R5 / ".fontcache"

_W9 = {100: "Thin", 200: "ExtraLight", 300: "Light", 400: "Regular", 500: "Medium", 600: "SemiBold", 700: "Bold", 800: "ExtraBold", 900: "Black"}
FAMILIES: dict[str, dict[int, str]] = {
    "Pretendard": {w: f"Pretendard-{n}.otf" for w, n in _W9.items()},
    "SUIT": {**{w: f"SUIT-{n}.otf" for w, n in _W9.items() if w < 900}, 900: "SUIT-Heavy.otf"},
    "Wanted Sans": {400: "WantedSans-Regular.otf", 500: "WantedSans-Medium.otf", 600: "WantedSans-SemiBold.otf",
                    700: "WantedSans-Bold.otf", 800: "WantedSans-ExtraBold.otf", 900: "WantedSans-Black.otf",
                    950: "WantedSans-ExtraBlack.otf"},
    "Paperlogy": {w: f"Paperlogy-{w // 100}{n}.woff2" for w, n in _W9.items()},
    "LINE Seed KR": {100: "LINESeedKR-Th.woff2", 400: "LINESeedKR-Rg.woff2", 700: "LINESeedKR-Bd.woff2"},
    "Gmarket Sans": {300: "GmarketSansLight.woff", 500: "GmarketSansMedium.woff", 700: "GmarketSansBold.woff"},
    "Spoqa Han Sans Neo": {100: "SpoqaHanSansNeo-Thin.woff", 300: "SpoqaHanSansNeo-Light.woff", 400: "SpoqaHanSansNeo-Regular.woff",
                           500: "SpoqaHanSansNeo-Medium.woff", 700: "SpoqaHanSansNeo-Bold.woff"},
}
# one-face aliases that answer every weight (no faux bold)
ALIASES = {"Wanted Sans Display": "WantedSans-Black.otf", "Gmarket Display": "GmarketSansBold.woff"}


def parse(fragment: str) -> dict[str, set[int]]:
    m = re.search(r"<!--\s*kfonts:(.*?)-->", fragment)
    out: dict[str, set[int]] = {}
    for part in (m.group(1).split("|") if m else []):
        fam, _, ws = part.strip().partition(":")
        if fam:
            out.setdefault(fam.strip(), set()).update(int(w) for w in ws.split(";") if w.strip())
    return out


def _data(fn: str, text: str) -> tuple[bytes, str]:
    p = SRC / fn
    if fn.endswith(".woff2"):
        return p.read_bytes(), "woff2"
    CACHE.mkdir(exist_ok=True)
    c = CACHE / f"{fn}.{hashlib.md5(text.encode()).hexdigest()[:10]}.woff"
    if not c.exists():
        f = TTFont(p)
        opt = subset.Options()
        opt.flavor = "woff"
        opt.layout_features = ["*"]
        s = subset.Subsetter(opt)
        s.populate(text=text)
        s.subset(f)
        buf = io.BytesIO()
        f.flavor = "woff"
        f.save(buf)
        c.write_bytes(buf.getvalue())
    return c.read_bytes(), "woff"


def _face(fam: str, weight: str, fn: str, text: str) -> str:
    data, fmt = _data(fn, text)
    return (f'@font-face{{font-family:"{fam}";font-weight:{weight};font-display:block;'
            f'src:url(data:font/{fmt};base64,{base64.b64encode(data).decode()}) format("{fmt}")}}')


def faces_css(want: dict[str, set[int]], text: str) -> str:
    text = "".join(sorted(set(text) | set(map(chr, range(32, 127)))))
    css = []
    for fam, ws in want.items():
        if fam in ALIASES:
            css.append(_face(fam, "1 1000", ALIASES[fam], text))
            continue
        table = FAMILIES[fam]  # KeyError = unknown family: fix the kfonts line
        for w in sorted(ws or {400}):
            near = min(table, key=lambda k: abs(k - w))
            css.append(_face(fam, str(w), table[near], text))
    return "\n".join(css)
