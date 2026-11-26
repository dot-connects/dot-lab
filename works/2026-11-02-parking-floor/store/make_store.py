"""Play store graphics for 어디댔어 from the app's own Roborazzi shots (app-v2/shots, T-0203).

    python store/make_store.py     -> store/icon-512.png, feature-1024x500.png, shot-1.png ...
Shots are 1080x1920 (Play allows at most 2:1; the raw shots are 652x1472 = 2.26:1, so they sit on a card under a headline).
"""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.join(os.path.dirname(HERE), "app-v2")
RES = os.path.join(APP, "app", "src", "main", "res")
SHOTS = os.path.join(APP, "shots")
BG, ICON_BG, FG, MUTED, ACC = (233, 231, 227), (205, 202, 199), (22, 22, 22), (108, 106, 102), (255, 106, 19)
BOLD, REG = r"C:\Windows\Fonts\malgunbd.ttf", r"C:\Windows\Fonts\malgun.ttf"

# (shot, headline, sub)
PICKS = [
    ("app/home-mock.png", "B2 C37, 말 한마디로 저장", "층·구역·번호를 크게, 누가 언제 댔는지까지"),
    ("listen/1-listening-mock.png", "버튼 누르고 말하면 끝", "\"B타워 3층 계단 옆\"처럼 평소 말투로"),
    ("listen/2-saved-mock.png", "알아듣고 바로 저장", "음성은 폰 안에서만 인식해요"),
    ("app/detail-mock.png", "직전 자리와 메모, 사진까지", "어제 어디 댔는지도 남아요"),
    ("app/group-mock.png", "가족과 차를 같이 써도", "그룹에 초대하면 자리가 같이 보여요"),
]


def icon() -> Image.Image:
    fg = Image.open(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_foreground.webp")).convert("RGBA")
    s = fg.width
    m = round(s * 18 / 108)   # keep 72 of the 108 dp canvas: the safe zone (the outer ring holds a faint frame)
    fg = fg.crop((m, m, s - m, s - m)).resize((512, 512), Image.LANCZOS)
    out = Image.new("RGBA", (512, 512), ICON_BG + (255,))
    out.alpha_composite(fg)
    return out.convert("RGB")


def feature(ic: Image.Image) -> Image.Image:
    im = Image.new("RGB", (1024, 500), BG)
    d = ImageDraw.Draw(im)
    mask = Image.new("L", (300, 300), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 299, 299), 66, fill=255)
    im.paste(ic.resize((300, 300), Image.LANCZOS), (80, 100), mask)
    d.text((430, 130), "어디댔어", font=ImageFont.truetype(BOLD, 96), fill=FG)
    d.text((434, 270), "주차 자리, 말 한마디로 저장", font=ImageFont.truetype(BOLD, 40), fill=ACC)
    d.text((434, 330), "가족과 같이 보는 주차 메모", font=ImageFont.truetype(REG, 34), fill=MUTED)
    return im


def shot(path: str, head: str, sub: str) -> Image.Image:
    im = Image.new("RGB", (1080, 1920), BG)
    d = ImageDraw.Draw(im)
    for y, text, font, color in ((90, head, ImageFont.truetype(BOLD, 64), FG), (190, sub, ImageFont.truetype(REG, 38), MUTED)):
        w = d.textlength(text, font=font)
        d.text(((1080 - w) / 2, y), text, font=font, fill=color)
    raw = Image.open(os.path.join(SHOTS, path)).convert("RGB")
    if path.startswith("listen/"):   # the listen sheet covers only the lower half; the rest is a grey scrim
        raw = raw.crop((0, round(raw.height * 0.4), raw.width, raw.height))
    h = 1920 - 300 - 60
    w = round(raw.width * h / raw.height)
    if w > 960:
        w, h = 960, round(raw.height * 960 / raw.width)
    raw = raw.resize((w, h), Image.LANCZOS)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), 40, fill=255)
    x = (1080 - w) // 2
    d.rounded_rectangle((x - 6, 294, x + w + 5, 300 + h + 5), 46, fill=(205, 202, 199))
    im.paste(raw, (x, 300), mask)
    return im


if __name__ == "__main__":
    ic = icon()
    ic.save(os.path.join(HERE, "icon-512.png"))
    feature(ic).save(os.path.join(HERE, "feature-1024x500.png"))
    for i, (p, h, s) in enumerate(PICKS, 1):
        shot(p, h, s).save(os.path.join(HERE, f"shot-{i}.png"))
    print("ok", len(PICKS), "shots")
