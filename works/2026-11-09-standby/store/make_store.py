"""Build the Play store graphics for Stand Screen from the app's own Roborazzi shots (default theme).

Run after a shot run that recorded the IPHONE (default) theme:  python store/make_store.py
Writes store/icon-512.png, store/feature-1024x500.png and store/shot-1..7.png (1920x1080, under Play's 2:1 limit).
"""
import os
import shutil
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.dirname(HERE)
SHOTS = os.path.join(WORK, "app", "standby", "build", "shots", "iphone")
BG, FG, MUTED, ACC = (14, 15, 18), (236, 234, 231), (155, 154, 160), (255, 170, 92)
BOLD = r"C:\Windows\Fonts\malgunbd.ttf"
REG = r"C:\Windows\Fonts\malgun.ttf"

SHOTS_LIST = [  # (shot, headline)
    ("clock/digital-day-bar-land.png", "충전기에 꽂으면 큰 탁상시계로"),
    ("board/preset-modular-bar-land.png", "날씨 · 일정 · 사진을 한눈에"),
    ("clock/photos_time-day-bar-land.png", "내 사진 위에 시간"),
    ("clock/flip-night-bar-land.png", "밤에는 눈부시지 않게"),
    ("clock/hangul-night-bar-land.png", "시계 20가지 넘게, 골라서 넘겨 보기"),
    ("board/preset-bed-bar-land.png", "수면 소리와 집중 타이머"),
    ("clock/relief-date-bar-land.png", "테마와 색은 내 마음대로"),
]


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def shot(src: str, title: str, out: str) -> None:
    W, H, top, pad = 1920, 1080, 190, 80
    c = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(c)
    f = font(BOLD, 64)
    tw = d.textlength(title, font=f)
    d.text(((W - tw) / 2, 62), title, font=f, fill=FG)
    im = Image.open(src).convert("RGB")
    w = W - pad * 2
    h = round(im.height * w / im.width)
    im = im.resize((w, h), Image.LANCZOS)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w, h), radius=36, fill=255)
    c.paste(im, (pad, top + (H - top - h - 40) // 2 + 10), mask)
    c.save(out)


def feature(out: str) -> None:
    W, H = 1024, 500
    c = Image.new("RGB", (W, H), BG)
    icon = Image.open(os.path.join(WORK, "icon", "final", "icon-512.png")).convert("RGB").resize((260, 260), Image.LANCZOS)
    mask = Image.new("L", (260, 260), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 260, 260), radius=58, fill=255)
    c.paste(icon, (90, 120), mask)
    d = ImageDraw.Draw(c)
    d.text((400, 150), "Stand Screen", font=font(BOLD, 72), fill=FG)
    d.text((402, 255), "충전할 때 켜지는 탁상시계", font=font(BOLD, 40), fill=ACC)
    d.text((402, 318), "시계 · 날씨 · 일정 · 사진 · 수면 소리", font=font(REG, 30), fill=MUTED)
    c.save(out)


def main() -> None:
    shutil.copy(os.path.join(WORK, "icon", "final", "icon-512.png"), os.path.join(HERE, "icon-512.png"))
    feature(os.path.join(HERE, "feature-1024x500.png"))
    for i, (src, title) in enumerate(SHOTS_LIST, 1):
        shot(os.path.join(SHOTS, src), title, os.path.join(HERE, f"shot-{i}.png"))
    print("ok", len(SHOTS_LIST), "shots")


if __name__ == "__main__":
    main()
