"""Store screenshot 4: the pet on a phone home screen with its double-tap app bubble (app look, generic icons)."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 1080, 2340
MAL = r"C:/Windows/Fonts/malgun.ttf"; MALB = r"C:/Windows/Fonts/malgunbd.ttf"
INK = (36, 51, 31)


def glyph(d, kind, x, y, s):
    c, w = (255, 255, 255), max(4, s // 22)
    m = s // 2
    if kind == "camera":
        d.rounded_rectangle((x + s*.2, y + s*.32, x + s*.8, y + s*.74), radius=s*.08, outline=c, width=w)
        d.ellipse((x + m - s*.12, y + s*.41, x + m + s*.12, y + s*.65), outline=c, width=w)
    elif kind == "photo":
        d.polygon([(x + s*.2, y + s*.72), (x + s*.45, y + s*.4), (x + s*.62, y + s*.6), (x + s*.7, y + s*.5), (x + s*.82, y + s*.72)], fill=c)
        d.ellipse((x + s*.62, y + s*.22, x + s*.76, y + s*.36), fill=c)
    elif kind == "memo":
        for i in range(3): d.line((x + s*.28, y + s*(.36 + i*.14), x + s*.72, y + s*(.36 + i*.14)), fill=c, width=w)
    elif kind == "map":
        d.ellipse((x + s*.34, y + s*.2, x + s*.66, y + s*.52), fill=c)
        d.polygon([(x + s*.36, y + s*.42), (x + s*.64, y + s*.42), (x + m, y + s*.8)], fill=c)
    elif kind == "music":
        d.ellipse((x + s*.28, y + s*.58, x + s*.48, y + s*.76), fill=c)
        d.line((x + s*.46, y + s*.66, x + s*.46, y + s*.24), fill=c, width=w)
        d.line((x + s*.46, y + s*.24, x + s*.7, y + s*.32), fill=c, width=w)
    elif kind == "clock":
        d.ellipse((x + s*.22, y + s*.22, x + s*.78, y + s*.78), outline=c, width=w)
        d.line((x + m, y + m, x + m, y + s*.32), fill=c, width=w); d.line((x + m, y + m, x + s*.64, y + m), fill=c, width=w)
    elif kind == "calc":
        for i in range(3):
            for j in range(3): d.rounded_rectangle((x + s*(.25 + j*.18), y + s*(.25 + i*.18), x + s*(.37 + j*.18), y + s*(.37 + i*.18)), radius=3, fill=c)
    else:
        d.ellipse((x + s*.3, y + s*.3, x + s*.7, y + s*.7), outline=c, width=w * 2)


APPS = [("카메라", "camera", (84, 104, 128)), ("사진", "photo", (236, 116, 104)), ("메모", "memo", (240, 186, 60)),
        ("지도", "map", (80, 170, 112)), ("음악", "music", (160, 104, 214)), ("시계", "clock", (64, 84, 116)),
        ("계산기", "calc", (236, 140, 56)), ("설정", "gear", (112, 122, 134))]


im = Image.new("RGB", (W, H)); top, bot = (146, 168, 232), (250, 206, 192)
for y in range(H): im.paste(tuple(int(top[i] + (bot[i] - top[i]) * y / H) for i in range(3)), (0, y, W, y + 1))
d = ImageDraw.Draw(im)
d.text((60, 40), "9:41", font=ImageFont.truetype(MALB, 38), fill="white")
d.text((W // 2, 330), "9:41", font=ImageFont.truetype(MAL, 200), fill="white", anchor="mm")
d.text((W // 2, 470), "11월 19일 목요일", font=ImageFont.truetype(MAL, 34), fill="white", anchor="mm")
for i, (n, k, c) in enumerate(APPS):
    x, y = 110 + (i % 4) * 235, 1650 + (i // 4) * 270
    d.rounded_rectangle((x, y, x + 150, y + 150), radius=40, fill=c); glyph(d, k, x, y, 150)
    d.text((x + 75, y + 186), n, font=ImageFont.truetype(MAL, 34), fill="white", anchor="mm")
d.rounded_rectangle((60, 2140, W - 60, 2300), radius=60, fill=(255, 255, 255))
im = im.convert("RGBA")
pet = Image.open("app/app/src/main/assets/critters/meongsom/front.webp").convert("RGBA")   # the app's own pet art
pet = pet.crop(pet.getbbox()); pet = pet.resize((330, int(330 * pet.height / pet.width)), Image.LANCZOS)
sh = Image.new("RGBA", (420, 140), (0, 0, 0, 0)); ImageDraw.Draw(sh).ellipse((60, 40, 360, 100), fill=(40, 40, 70, 80))
im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(12)), (500, 1500))
im.alpha_composite(pet, (530, 1580 - pet.height))
b = Image.new("RGBA", (W, H), (0, 0, 0, 0)); bd = ImageDraw.Draw(b)
bx, by, bw, bh = 110, 700, 700, 470
bd.rounded_rectangle((bx, by, bx + bw, by + bh), radius=50, fill=(255, 253, 246, 255), outline=INK + (255,), width=5)
for cx, cy, r in [(610, 1212, 22), (655, 1262, 14)]: bd.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(255, 253, 246, 255), outline=INK + (255,), width=4)
for i, (n, k, c) in enumerate(APPS[:6]):
    x, y = bx + 70 + (i % 3) * 200, by + 40 + (i // 3) * 170
    bd.rounded_rectangle((x, y, x + 110, y + 110), radius=30, fill=c); glyph(bd, k, x, y, 110)
    bd.text((x + 55, y + 138), n, font=ImageFont.truetype(MAL, 28), fill=INK, anchor="mm")
ly = by + bh - 80
for x in range(bx + 30, bx + bw - 30, 24): bd.line((x, ly, x + 12, ly), fill=(150, 160, 145), width=3)
bd.line((bx + bw // 2, ly + 15, bx + bw // 2, by + bh - 15), fill=(150, 160, 145), width=3)
bd.text((bx + bw // 4, ly + 40), "펫 설정", font=ImageFont.truetype(MALB, 38), fill=INK, anchor="mm")
bd.text((bx + 3 * bw // 4, ly + 40), "펫 끄기", font=ImageFont.truetype(MALB, 38), fill=(196, 60, 50), anchor="mm")
im.alpha_composite(b)
im.convert("RGB").save("store/shots/raw-home.png")
