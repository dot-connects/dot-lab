"""Build one static share page per score (site/s/<n>/index.html) with its own link-preview image.

The site is static (GitHub Pages), so a shared link cannot carry a per-player preview. Instead each score 0..100 has
its own page whose og:image is a receipt with that score and its title stamp; the page sends the visitor into the game
with ?vs=<n> (a challenge). Crawlers don't run JS, so they keep the score preview.

Run: uv run --with pillow python make_score_pages.py   (Windows: uses Malgun Gothic Bold)
Bump VER when the card design changes: KakaoTalk and Meta cache previews per image URL.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

VER = "v1"
BASE = "https://dot-connects.github.io/dot-lab/works/2026-10-23-kimchi-fried-rice/site/"
HERE = Path(__file__).parent
SITE = HERE / "site"
FONT = r"C:\Windows\Fonts\malgunbd.ttf"
# keep in step with js/copy.js tiers
TIERS = [(92, "주방장"), (80, "불판 에이스"), (65, "칼판"), (45, "면판 막내"), (0, "설거지 담당")]


def tier(score: int) -> str:
    return next(name for lo, name in TIERS if score >= lo)


def font(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT, size)


def card(score: int, dish: Image.Image) -> Image.Image:
    im = Image.new("RGB", (1200, 630), (27, 31, 36))
    im.paste(dish.resize((630, 630)), (570, 0))
    d = ImageDraw.Draw(im)
    # the receipt slip
    d.rounded_rectangle((40, 40, 600, 590), radius=18, fill=(251, 246, 234))
    d.text((320, 92), "00반점 영수증", font=font(46), fill=(31, 42, 51), anchor="mm")
    d.line((80, 140, 560, 140), fill=(190, 180, 160), width=3)
    d.text((80, 175), "김치볶음밥 1 · 계란후라이 1", font=font(28), fill=(91, 102, 112))
    d.text((320, 330), f"{score}점", font=font(170), fill=(232, 72, 28), anchor="mm")
    # the red title stamp, tilted
    name = tier(score)
    st = Image.new("RGBA", (460, 120), (0, 0, 0, 0))
    sd = ImageDraw.Draw(st)
    sd.rounded_rectangle((6, 6, 454, 114), radius=12, outline=(200, 30, 30), width=8)
    sd.text((230, 60), name, font=font(58), fill=(200, 30, 30), anchor="mm")
    st = st.rotate(7, expand=True, resample=Image.BICUBIC)
    im.paste(st, (95, 410), st)
    # the challenge band
    d.rectangle((570, 540, 1200, 630), fill=(232, 72, 28))
    d.text((885, 585), "이 점수 이길 수 있어? →", font=font(40), fill=(255, 255, 255), anchor="mm")
    return im


PAGE = """<!doctype html>
<html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>친구가 {n}점 받았다 · 김치볶음밥 한 그릇</title>
<meta property="og:title" content="친구가 {n}점 받았다 · 김치볶음밥 한 그릇">
<meta property="og:description" content="00반점 영수증 {n}점 「{t}」. 폰을 웍처럼 휙! 이 점수 이길 수 있어?">
<meta property="og:url" content="{base}s/{n}/">
<meta property="og:type" content="website">
<meta property="og:image" content="{base}s/img/s{n}-{ver}.jpg">
<meta property="og:image:width" content="1200"><meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" type="image/png" href="../../icon.png">
<script>location.replace("../../?vs={n}");</script>
</head><body style="margin:0;background:#1b1f24;color:#fff;font-family:system-ui,sans-serif;display:flex;align-items:center;justify-content:center;height:100vh">
<a href="../../?vs={n}" style="color:#ffb000;font-size:20px">친구가 {n}점! 이겨 보러 가기 →</a>
</body></html>
"""


def main() -> None:
    dish = Image.open(HERE / "share-src" / "dish.png").convert("RGB")
    img_dir = SITE / "s" / "img"
    img_dir.mkdir(parents=True, exist_ok=True)
    for old in img_dir.glob("s*.jpg"):
        if not old.name.endswith(f"-{VER}.jpg"):
            old.unlink()
    for n in range(101):
        card(n, dish).save(img_dir / f"s{n}-{VER}.jpg", quality=82, optimize=True)
        page = SITE / "s" / str(n)
        page.mkdir(parents=True, exist_ok=True)
        (page / "index.html").write_text(PAGE.format(n=n, t=tier(n), base=BASE, ver=VER), encoding="utf-8")
    print("101 pages", sum(f.stat().st_size for f in img_dir.glob("*.jpg")) // 1024, "KB of images")


if __name__ == "__main__":
    main()
