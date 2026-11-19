"""Play store screenshots 1080x1920: a caption on top, the real screen below in a phone outline (store/listing.md)."""
from PIL import Image, ImageDraw, ImageFont

# Jua from github.com/google/fonts ofl/jua (download it again if this scratch path is gone)
JUA = r"C:/Users/ehdck/AppData/Local/Temp/claude/c62cf31192c54cdd/0e456975-fa5e-4720-b535-a9d01f009af3/scratchpad/Jua-Regular.ttf"
PAPER, INK, SUN, GRASS = (255, 253, 246), (36, 51, 31), (255, 216, 77), (217, 242, 201)
SHOTS = [   # file, line 1, line 2 (highlighted)
    ("raw-catch.png", "집게를 맞추고", "쏙 뽑아요!"),
    ("raw-detail.png", "찍힌 모습 그대로", "털뭉치가 돼요"),
    ("raw-dex.png", "뽑은 녀석은", "도감에 모여요"),
    ("raw-home.png", "홈 화면에서", "같이 살아요"),
]

def frame(src, l1, l2, out):
    W, H = 1080, 1920
    im = Image.new("RGB", (W, H), PAPER if out[-5] in "13" else GRASS)
    d = ImageDraw.Draw(im); f = ImageFont.truetype(JUA, 92)
    d.text((W // 2, 150), l1, font=f, fill=INK, anchor="mm")
    bb = d.textbbox((W // 2, 270), l2, font=f, anchor="mm")
    d.rounded_rectangle((bb[0] - 22, bb[1] - 14, bb[2] + 22, bb[3] + 20), radius=26, fill=SUN)
    d.text((W // 2, 270), l2, font=f, fill=INK, anchor="mm")
    shot = Image.open(src).convert("RGB")
    sw = 700; sh = int(sw * shot.height / shot.width)
    shot = shot.resize((sw, sh), Image.LANCZOS)
    x, y = (W - sw) // 2, 400
    pad, r = 18, 70
    d.rounded_rectangle((x - pad, y - pad + 8, x + sw + pad, y + sh + pad + 8), radius=r + pad, fill=INK)   # offset shadow
    d.rounded_rectangle((x - pad, y - pad, x + sw + pad, y + sh + pad), radius=r + pad, fill=INK)
    m = Image.new("L", (sw, sh), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, sw - 1, sh - 1), radius=r, fill=255)
    im.paste(shot, (x, y), m)
    im.save(out)

if __name__ == "__main__":
    import os
    for i, (src, a, b) in enumerate(SHOTS, 1):
        p = os.path.join("store/shots", src)
        if os.path.exists(p): frame(p, a, b, f"store/shots/shot{i}.png"); print("shot", i)
