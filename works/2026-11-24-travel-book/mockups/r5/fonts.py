"""Subset the self-hosted Korean fonts to the glyphs the mockups use and write fontfaces.css (base64 @font-face)."""
import base64
import io
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

R5 = Path(__file__).resolve().parent
SRC = R5.parent.parent / "fontsrc"
# family, file, css weight range
FACES = [
    ("Pretendard", "Pretendard-Thin.otf", "100 199"),
    ("Pretendard", "Pretendard-Light.otf", "200 350"),
    ("Pretendard", "Pretendard-Medium.otf", "400 600"),
    ("Pretendard", "Pretendard-ExtraBold.otf", "700 950"),
    ("Wanted Sans", "WantedSans-Medium.otf", "300 550"),
    ("Wanted Sans", "WantedSans-Bold.otf", "600 750"),
    ("Wanted Sans", "WantedSans-Black.otf", "800 950"),
    ("Wanted Sans Display", "WantedSans-Black.otf", "1 1000"),
    ("Paperlogy", "Paperlogy-4Regular.woff2", "300 450"),
    ("Paperlogy", "Paperlogy-6SemiBold.woff2", "500 650"),
    ("Paperlogy", "Paperlogy-8ExtraBold.woff2", "700 950"),
    ("LINE Seed KR", "LINESeedKR-Th.woff2", "100 350"),
    ("LINE Seed KR", "LINESeedKR-Rg.woff2", "400 500"),
    ("LINE Seed KR", "LINESeedKR-Bd.woff2", "600 900"),
    ("Gmarket Display", "GmarketSansBold.woff", "1 1000"),
    ("SUIT", "SUIT-Regular.otf", "300 450"),
    ("SUIT", "SUIT-SemiBold.otf", "500 650"),
    ("SUIT", "SUIT-ExtraBold.otf", "700 950"),
]


def glyph_text() -> str:
    t = "".join(f.read_text(encoding="utf-8") for f in R5.glob("*.html") if not f.name.startswith(("out-", "travel-")))
    return "".join(sorted(set(t) | set(map(chr, range(32, 127)))))


def main() -> None:
    text = glyph_text()
    css = []
    for fam, fn, w in FACES:
        p = SRC / fn
        if fn.endswith(".woff2"):  # no brotli here: embed whole woff2 as shipped
            data, fmt, mime = p.read_bytes(), "woff2", "font/woff2"
        else:
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
            data, fmt, mime = buf.getvalue(), "woff", "font/woff"
        print(f"{fam:20} {fn:28} {len(data)//1024} KB")
        css.append(f'@font-face{{font-family:"{fam}";font-weight:{w};font-display:block;src:url(data:{mime};base64,{base64.b64encode(data).decode()}) format("{fmt}")}}')
    (R5 / "fontfaces.css").write_text("\n".join(css), encoding="utf-8")


if __name__ == "__main__":
    main()
