"""Subset the bundled fonts to the glyphs the app draws (APK size). Reproducible:

    uv run --with fonttools==4.* python tools/subset_fonts.py      (from app/)

Reads the full originals from tools/fonts-original/ (gitignored; restore them with
`git show 006a3c8:works/2026-11-09-standby/app/standby/src/main/res/font/<name>.ttf`) and writes the subsets into
standby/src/main/res/font/. Every original is checked against the sha256 below first, so a subset is never made from
an already-subset file. Variable axes (fvar/gvar/...) and all OpenType features (tnum, lnum, ...) are kept.

- Pretendard (the UI font): KS X 1001's 2,350 Hangul syllables + every Hangul syllable/jamo in src/main + compatibility
  jamo + ASCII + Latin-1 + general punctuation, arrows, math and the other symbols the sources use. Text from outside
  the app (notifications, media titles, city names) with a rarer syllable falls back to the system font for that glyph.
- Clock-digit families (Latin only, no Hangul drawn with them): Basic Latin + Latin-1 + punctuation/symbols. That
  covers digits, ':', '.', '/', '%', '°', AM/PM and the English weekday/month names the faces print.
- Already subset or Hangul clock fonts (gowun, jua, nanum_pen, mplus, instrument, sofia) are left alone
  (see tools/kfont_chars.py).
"""
import hashlib
import pathlib
import re
import sys

from fontTools import subset
from fontTools.ttLib import TTFont

APP = pathlib.Path(__file__).resolve().parent.parent
SRC = APP / "tools" / "fonts-original"
OUT = APP / "standby" / "src" / "main" / "res" / "font"
MAIN = APP / "standby" / "src" / "main"

# sha256 of the full originals (commit 006a3c8)
ORIGINALS = {
    "pretendard": "3090ccde0442bb347aa7685d9ba8b17436a60682df6e8f92a9a670de14056e22",
    "bagel_fat_one": "9286944032c5a9b20ad70940105417beae14013e0b4f5fc292425afdc4330245",
    "barlow_condensed_600": "7b619d14bc2327509a9ef32b0890f709626f7ecc9ff61191c2a4314c5499d2d9",
    "big_shoulders": "60e208dc276a1c35fc5b62e94f9fb959c40c11783a9eb7548175c14b1fbeb720",
    "fredoka": "2ba02e68b152868aef9ba28e24b3648c7d457fe6f25c761f2c2c53fb61a73fc8",
    "inter": "29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031",
    "jost": "6343b70971000b04c5d401c96ae08ce371086135e999d5e1e1413039c0213076",
    "nunito": "bb55a5ca5c2042335b3991af27c4d0705d0ef41cac6164ac737fd8f2a1e85207",
    "oswald": "5b38c246e255a12f5712d640d56bcced0472466fc68983d2d0410ec0457c2817",
    "outfit": "fc7287273e66929776e2ba54f144fe699080bec29f61bf649d70d871468aeade",
    "playfair": "c40f2293766a503bc70cce9e512ef844a4ccb7cbcde792fe2ea31d191917d8d6",
    "plex_mono_500": "a9b4c49bb299e05b5f6c481e7fb5e78943d2793249a0c8874ab574a2d1ea6755",
    "roboto_slab": "786ae192477447d33c6672c3055fba7cbfe45184c9a79e77a14f15716ca05b16",
    "source_serif": "97b2d4da6e3cb494b5a1e66ae176914d852ccabef49e0c02c0df25f3e39aca0b",
    "titan_one": "563ff6de179bbd44bcd7d2a6c448d6dc3bf935834237cf1250e506e65cd86ff1",
}

LATIN_FAMILIES = [
    "bagel_fat_one", "barlow_condensed_600", "big_shoulders", "fredoka", "inter", "jost", "nunito", "oswald",
    "outfit", "playfair", "plex_mono_500", "roboto_slab", "source_serif", "titan_one",
]


def ranges(*rs: tuple[int, int]) -> set[int]:
    return {c for a, b in rs for c in range(a, b + 1)}


# Basic Latin, Latin-1, general punctuation, super/subscripts, currency, letterlike, arrows, math, misc technical,
# geometric shapes (bullets, triangles), dingbat check/cross
LATIN = ranges((0x20, 0x7E), (0xA0, 0xFF), (0x2000, 0x206F), (0x2070, 0x209F), (0x20A0, 0x20CF), (0x2100, 0x214F),
               (0x2190, 0x21FF), (0x2200, 0x22FF), (0x2300, 0x23FF), (0x25A0, 0x25FF), (0x2713, 0x2718))


def ksx1001() -> set[int]:
    """The 2,350 precomposed syllables of KS X 1001 (EUC-KR rows 0xB0..0xC8)."""
    out = set()
    for hi in range(0xB0, 0xC9):
        for lo in range(0xA1, 0xFF):
            try:
                out.add(ord(bytes([hi, lo]).decode("euc-kr")))
            except UnicodeDecodeError:
                pass
    return out


def source_chars() -> set[int]:
    """Every non-ASCII character in the app's sources and resources (Hangul words, symbols like · → × °), including
    the ones written as \\uXXXX escapes."""
    out = set()
    for p in MAIN.rglob("*"):
        if p.suffix in (".kt", ".xml", ".json", ".txt") and p.is_file():
            text = p.read_text(encoding="utf-8", errors="ignore")
            out |= {ord(c) for c in text if ord(c) > 0x7E}
            out |= {int(h, 16) for h in re.findall(r"\\u([0-9A-Fa-f]{4})", text) if int(h, 16) > 0x7E}
    return out


def sha(p: pathlib.Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def run(name: str, unicodes: set[int]) -> None:
    src = SRC / f"{name}.ttf"
    want = ORIGINALS.get(name)
    got = sha(src)
    if want is None:
        print(f'    "{name}": "{got}",  # record me')
    elif want != got:
        sys.exit(f"{src}: sha256 {got} is not the original {want}")
    font = TTFont(src)
    have = set(font.getBestCmap())
    opts = subset.Options()
    opts.layout_features = ["*"]
    opts.name_IDs = ["*"]
    opts.name_languages = ["*"]
    opts.notdef_outline = True
    opts.glyph_names = False
    opts.legacy_kern = True
    sub = subset.Subsetter(opts)
    sub.populate(unicodes=sorted(unicodes & have))
    sub.subset(font)
    dst = OUT / f"{name}.ttf"
    font.save(dst)
    print(f"{name}: {src.stat().st_size:,} -> {dst.stat().st_size:,} bytes, {len(unicodes & have)} chars")


def main() -> None:
    src = source_chars()
    hangul = {c for c in src if 0xAC00 <= c <= 0xD7A3 or 0x1100 <= c <= 0x11FF or 0x3130 <= c <= 0x318F}
    # common in text from outside the app (notification and media titles): CJK punctuation, full-width forms,
    # enclosed numbers, misc symbols (★☆♥), Latin Extended-A
    titles = ranges((0x3000, 0x303F), (0xFF00, 0xFFEF), (0x2460, 0x24FF), (0x2600, 0x26FF), (0x0100, 0x017F))
    ui = ksx1001() | hangul | ranges((0x3130, 0x318F)) | LATIN | titles | src
    run("pretendard", ui)
    for name in LATIN_FAMILIES:
        run(name, LATIN | {c for c in src if c < 0x2E80})  # plus any non-CJK symbol a source uses


if __name__ == "__main__":
    main()
