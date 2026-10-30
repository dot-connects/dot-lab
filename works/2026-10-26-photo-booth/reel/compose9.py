"""Ad draft 5: draft 4's story in the 말랑부스 look (lavender dotted paper, pink pills, Dongle / Gowun Dodum), with
eased transitions between every scene (they overlap) and slow motion inside every scene, so nothing cuts hard.
usage: python compose6.py take7 out.mp4"""
import math, subprocess, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

R = Path(__file__).parent
TAKE, OUT = R / sys.argv[1], sys.argv[2]
AI, VOICE, SFX, FONTS = R / "ai", R / "voice3" / "fast2", R / "sfx", R / "fonts"
OG = Path(r"C:\DC\DD\dot-lab\works\2026-10-26-photo-booth\site\og.png")
MUSIC = Path(r"C:\DC\DD\content-system\private-assets\music\prettyjohn1-pop-pop-music-503314.mp3")
FPS, W, H = 30, 1080, 1920
PX, PY, PW, PH = 52, 350, 975, 1340
GAP = 14; HALF = (PH - GAP) // 2
INK, SUB, PINK, PINK3D, DEEP, LAV, SOFT = (42, 33, 64), (87, 76, 110), (255, 143, 189), (217, 87, 143), (90, 49, 171), (201, 184, 240), (230, 220, 255)
F = ImageFont.truetype
DB, DR, GO = str(FONTS / "Dongle-Bold.ttf"), str(FONTS / "Dongle-Regular.ttf"), str(FONTS / "GowunDodum-Regular.ttf")
CAP_F, TITLE_F, TAG_F, LAB_F = F(DB, 118), F(DB, 64), F(GO, 24), F(GO, 30)
CHAT_F, CHATS_F, NAME_F, BIG_F, MID_F = F(GO, 36), F(GO, 28), F(GO, 30), F(DB, 150), F(GO, 38)
ease = lambda x: (lambda c: c * c * (3 - 2 * c))(max(0, min(1, x)))          # smoothstep
eout = lambda x: 1 - (1 - max(0, min(1, x))) ** 3
back = lambda x: 1 + 2.2 * (max(0, min(1, x)) - 1) ** 3 + 1.2 * (max(0, min(1, x)) - 1) ** 2

_c = {}
def load(p):
    if p not in _c: _c[p] = Image.open(p).convert("RGB")
    return _c[p]
def cover(im, w, h, zoom=1.0, fx=0.5, fy=0.5):
    k = max(w / im.width, h / im.height) * zoom; cw, ch = w / k, h / k
    x = min(max(fx * im.width - cw / 2, 0), im.width - cw); y = min(max(fy * im.height - ch / 2, 0), im.height - ch)
    return im.crop((round(x), round(y), round(x + cw), round(y + ch))).resize((w, h), Image.LANCZOS)
def put(p, im, cx, cy, mask=None): p.paste(im, (round(cx - im.width / 2), round(cy - im.height / 2)), mask)
def rmask(size, r):
    m = Image.new("L", size, 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), r, fill=255); return m
def glow(img, a):                                   # soft white bloom instead of a hard flash
    return Image.blend(img, Image.new("RGB", img.size, (255, 255, 255)), 0.55 * max(0, a)) if a > 0 else img
def pill(img, text, x, y, font=LAB_F, bg=PINK, fg=(58, 18, 48)):
    d = ImageDraw.Draw(img); tw = d.textlength(text, font=font); h = font.size + 22
    d.rounded_rectangle((x, y + 4, x + tw + 36, y + h + 4), h // 2, fill=PINK3D); d.rounded_rectangle((x, y, x + tw + 36, y + h), h // 2, fill=bg)
    d.text((x + 18, y + 9), text, font=font, fill=fg)
def paper(w, h, pink=False):                       # the 말랑부스 dotted paper
    top, bot, dot = ((253, 235, 242), (246, 211, 226), (243, 191, 211)) if pink else ((246, 241, 255), (239, 230, 255), (217, 204, 251))
    g = Image.new("RGB", (1, h)); [g.putpixel((0, y), tuple(round(top[i] + (bot[i] - top[i]) * y / h) for i in range(3))) for y in range(h)]
    p = g.resize((w, h)); d = ImageDraw.Draw(p); step = 17 if not pink else 13
    for y in range(4, h, step):
        for x in range(4, w, step): d.ellipse((x - 1.2, y - 1.2, x + 1.2, y + 1.2), fill=dot)
    return p
BG = paper(W, H); PANEL_PINK = paper(PW, PH, pink=True)

def frames_of(name):
    d = TAKE / name.replace(".mp4", "")
    if not d.exists():
        d.mkdir(); subprocess.run(["ffmpeg", "-v", "error", "-i", str(TAKE / name), "-vf", "fps=30", str(d / "%04d.png")], check=True)
    return sorted(d.glob("*.png"))
MK, ST = frames_of("making.mp4"), frames_of("strip.mp4")

# ---- scenes: draw(s) where s may run past the scene's own length while it hands over -------------------------------
A_WAVE = ["A-0.png", "A-w3.png", "A-w1.png", "A-0.png", "A-w2.png"]          # wave, wave, laugh, wave, blink
def cycle(frames, s, per, fx, z):
    i = s / per; a, b = frames[int(i) % len(frames)], frames[(int(i) + 1) % len(frames)]
    x = ease((i % 1 - 0.6) / 0.4)                                               # hold, then a short cross-fade
    A_ = cover(load(AI / a), PW, HALF, z, fx, 0.42)
    return A_ if x <= 0 else Image.blend(A_, cover(load(AI / b), PW, HALF, z, fx, 0.42), x)
def s_split(s):
    p = Image.new("RGB", (PW, PH), (255, 255, 255)); z = 1.02 + 0.02 * s
    a = cycle(A_WAVE, s, 0.34, 0.36, z); pill(a, "단짝 · 집", 20, 18)
    b = cycle(["B-4.png", "B-4b.png"], s + 0.17, 0.3, 0.62, z); pill(b, "나 · 카페", 20, 18)
    p.paste(b, (0, 0)); p.paste(a, (0, HALF + GAP)); return p

KAKAO_BG, BUB, TIME_F, URL_F = (171, 193, 209), (255, 255, 255), F(GO, 20), F(GO, 24)
LANDB = load(TAKE / "bcam" / "landB.png")
def avatar(size=70):
    a = cover(load(AI / "A-0.png"), size, size, 3.4, 0.17, 0.27); return a, rmask((size, size), 26)
def link_card(press=0.0):
    c = Image.new("RGB", (430, 470), (255, 255, 255)); c.paste(cover(load(OG), 430, 286), (0, 0)); d = ImageDraw.Draw(c)
    d.text((22, 300), "따로 같이 네컷", font=CHAT_F, fill=(20, 20, 20))
    d.text((22, 352), "내 옆자리 비워 뒀어 · 2분이면 돼 · 가입 없음", font=F(GO, 22), fill=(110, 110, 110))
    d.line((0, 400, 430, 400), fill=(236, 236, 236), width=2); d.text((22, 418), "dot-connects.github.io", font=F(GO, 22), fill=(150, 150, 150))
    if press: c = Image.blend(c, Image.new("RGB", c.size, (0, 0, 0)), 0.12 * press)
    return c
def kakao(s, press=0.0):
    """the friend's KakaoTalk with 단짝 (me): '야 이거 해보자!' first, then the link with its preview"""
    sc = Image.new("RGB", (620, 1220), KAKAO_BG); d = ImageDraw.Draw(sc)
    d.text((28, 16), "오후 7:21", font=F(GO, 24), fill=(30, 30, 30)); d.rounded_rectangle((540, 20, 590, 42), 6, outline=(30, 30, 30), width=3); d.rectangle((545, 25, 578, 37), fill=(30, 30, 30))
    d.text((22, 64), "<", font=F(GO, 44), fill=(30, 30, 30)); d.text((70, 70), "단짝", font=F(GO, 36), fill=(20, 20, 20))
    d.ellipse((505, 80, 527, 102), outline=(30, 30, 30), width=3); d.line((524, 99, 534, 109), fill=(30, 30, 30), width=3)
    for k in range(3): d.line((556, 82 + k * 10, 586, 82 + k * 10), fill=(30, 30, 30), width=3)
    d.text((310 - d.textlength("10월 30일 목요일", font=TIME_F) / 2, 150), "10월 30일 목요일", font=TIME_F, fill=(90, 105, 120))
    d.rectangle((0, 1132, 620, 1220), fill=(255, 255, 255)); d.text((26, 1150), "+", font=F(GO, 44), fill=(120, 120, 120))
    d.rounded_rectangle((80, 1148, 540, 1200), 26, fill=(243, 243, 243))
    y = 210
    if s > 0:                                                      # message 1 pops in
        k = back(s / 0.3); av, am = avatar(); sc.paste(av, (20, y), am); d.text((104, y), "단짝", font=F(GO, 24), fill=(40, 40, 40))
        msg = "야 같이 사진 찍자!"; bw = round(d.textlength(msg, font=F(GO, 32))) + 44
        bub = Image.new("RGB", (bw, 74), BUB); ImageDraw.Draw(bub).text((22, 16), msg, font=F(GO, 32), fill=(20, 20, 20))
        bub = bub.resize((max(1, round(bw * k)), max(1, round(74 * k)))); sc.paste(bub, (104, y + 36), rmask(bub.size, 22))
        d.text((104 + bw + 10, y + 82), "오후 7:21", font=TIME_F, fill=(90, 105, 120))
    if s > 0.75:                                                   # the link: url bubble + preview card
        k = eout((s - 0.75) / 0.35); yy = y + 140 + round(40 * (1 - k))
        ub = Image.new("RGB", (440, 80), BUB); ImageDraw.Draw(ub).text((20, 10), "dot-connects.github.io/dot-lab/", font=URL_F, fill=(46, 112, 205))
        ImageDraw.Draw(ub).text((20, 42), "works/2026-10-26-photo-booth/…", font=URL_F, fill=(46, 112, 205))
        lay = sc.copy(); lay.paste(ub, (104, yy), rmask(ub.size, 22)); card = link_card(press)
        lay.paste(card, (104, yy + 94), rmask(card.size, 22)); ImageDraw.Draw(lay).text((104 + 440 + 8, yy + 548), "오후 7:21", font=TIME_F, fill=(90, 105, 120))
        sc = Image.blend(sc, lay, min(1, k * 1.5))
    return sc
CARD_BOX = (104, 210 + 140 + 94, 104 + 430, 210 + 140 + 94 + 470)     # where the card sits in the chat screen
def browser(s):
    """the in-app browser page the link opens: the app's real 'friend left you a seat' screen"""
    sc = Image.new("RGB", (620, 1220), (255, 255, 255)); d = ImageDraw.Draw(sc)
    d.rectangle((0, 0, 620, 104), fill=(250, 250, 250)); d.text((22, 50), "×", font=F(GO, 40), fill=(40, 40, 40))
    d.text((310 - d.textlength("dot-connects.github.io", font=F(GO, 24)) / 2, 58), "dot-connects.github.io", font=F(GO, 24), fill=(60, 60, 60))
    k = 1116 / LANDB.height; pg = LANDB.resize((round(LANDB.width * k), 1116), Image.LANCZOS)   # whole page, 바로 찍기 included
    sc.paste((241, 234, 255), (0, 104, 620, 1220)); sc.paste(pg, ((620 - pg.width) // 2, 104)); return sc
def phone(screen):
    ph = Image.new("RGB", (screen.width + 40, screen.height + 40), (40, 30, 60))
    ph.paste(screen, (20, 20), rmask(screen.size, 44)); ImageDraw.Draw(ph).rounded_rectangle((ph.width / 2 - 70, 30, ph.width / 2 + 70, 58), 14, fill=(40, 30, 60))
    return ph, rmask(ph.size, 60)
def ripple(img, cx, cy, s, t0):
    if not (t0 < s < t0 + 0.55): return img
    r = 30 + 50 * eout((s - t0) / 0.45); a = 1 - ease((s - t0) / 0.55)
    m = Image.new("L", img.size, 0); ImageDraw.Draw(m).ellipse((cx - r, cy - r, cx + r, cy + r), fill=round(160 * a))
    out = img.copy(); out.paste((255, 255, 255), (0, 0, img.width, img.height), m); return out
TAP1, OPEN, TAP2, DIVE = 1.55, 1.75, 2.45, 2.75
def screen_at(s):
    if s < OPEN: return kakao(s - 0.15, press=ease((s - TAP1) / 0.1) if s > TAP1 else 0)
    x = ease((s - OPEN) / 0.45)                                    # the card grows into the page (shared-element open)
    out = kakao(s - 0.15, 1.0).copy()
    x0, y0, x1, y1 = [round(a + (b - a) * x) for a, b in zip(CARD_BOX, (0, 0, 620, 1220))]
    pg = browser(s).resize((max(1, x1 - x0), max(1, y1 - y0)))
    if x < 1:
        card = link_card(1.0).resize(pg.size); pg = Image.blend(card, pg, ease((x - 0.15) / 0.6))
    out.paste(pg, (x0, y0), rmask(pg.size, round(22 * (1 - x)) + 1)); return out
def s_chat(s):
    p = PANEL_PINK.copy(); sc = screen_at(s)
    sc = ripple(sc, CARD_BOX[0] + 215, CARD_BOX[1] + 160, s, TAP1)
    sc = ripple(sc, 250, 104 + round(1700 * 1116 / LANDB.height), s, TAP2)   # 바로 찍기 (bottom of the page)
    ph, m = phone(sc); put(p, ph, PW / 2, PH / 2 + 10, m)
    if s > DIVE: p = cover(p, PW, PH, 1 + 1.9 * ease((s - DIVE) / 0.5), 0.5, 0.5)   # into the screen
    return p

import json as _json
GEO = [_json.loads((TAKE / "bcam" / f"geo{i}.json").read_text()) for i in (0, 1)]
GHOST = [Image.open(TAKE / "bcam" / f"geo{i}.png").convert("RGBA") for i in (0, 1)]
CHROME = [load(TAKE / "bcam" / "chrome0.png"), None]
SHOT2 = load(R / "take6" / "bcam" / "shot1.png")                       # same cute layout one shot later: '2 / 6' top bar, dots
_c1 = CHROME[0].copy(); _c1.paste(SHOT2.crop((0, 0, 860, 150)), (0, 0)); _c1.paste(SHOT2.crop((0, 1300, 860, 1864)), (0, 1300)); CHROME[1] = _c1
DIG_F = F(DB, 430)
def camera_frame(B_img, alpha_ghost, shot, digit, t):
    """chrome + the friend's live camera (moving) + my still cut-out, in the app's own coordinates (2x)"""
    g = GEO[shot]; k = 2.0; scr = CHROME[shot].copy()
    vx, vy, vw, vh = (round(g["video"][q] * k) for q in ("x", "y", "w", "h"))
    sx0, sy0 = round(g["stage"]["x"] * k), round(g["stage"]["y"] * k)
    sx1, sy1 = round((g["stage"]["x"] + g["stage"]["w"]) * k), round((g["stage"]["y"] + g["stage"]["h"]) * k)
    s = 1.015 + 0.012 * math.sin(t * 2.3); dx = 10 * math.sin(t * 1.7); dy = 8 * math.sin(t * 2.1 + 1)   # handheld sway
    im = B_img; sw = im.height * 4 / 3
    crop = im.crop((round((im.width - sw) / 2), 0, round((im.width + sw) / 2), im.height)).resize((round(vw * s), round(vh * s)), Image.LANCZOS)
    vid = Image.new("RGB", (vw, vh)); vid.paste(crop, (round((vw - crop.width) / 2 + dx), round((vh - crop.height) / 2 + dy)))
    if alpha_ghost > 0:
        gh = GHOST[shot].resize((vw, vh), Image.LANCZOS); a = gh.split()[3].point(lambda v: round(v * alpha_ghost)); vid.paste(gh.convert("RGB"), (0, 0), a)
    stage = Image.new("RGB", (sx1 - sx0, sy1 - sy0)); stage.paste(vid, (vx - sx0, vy - sy0))
    if digit:
        d = ImageDraw.Draw(stage); tw = d.textlength(digit, font=DIG_F); cx, cy = stage.width / 2 - tw / 2, stage.height / 2 - 300
        d.text((cx + 6, cy + 10), digit, font=DIG_F, fill=(201, 184, 240)); d.text((cx, cy), digit, font=DIG_F, fill=(255, 255, 255))
    scr.paste(stage, (sx0, sy0)); return scr
def cam_panel(scr):
    """the phone screen without its empty middle: camera band on top, controls card under it"""
    sc = PW / scr.width; top = scr.crop((0, 400, scr.width, 1110)); bot = scr.crop((0, 1350, scr.width, scr.height))
    top = top.resize((PW, round(top.height * sc)), Image.LANCZOS); bot = bot.resize((PW, round(bot.height * sc)), Image.LANCZOS)
    im = Image.new("RGB", (PW, PH), (241, 234, 255)); im.paste(top, (0, 0)); im.paste(bot, (0, PH - bot.height)); return im
def bpose(names, x):
    a, b = load(AI / names[0]), load(AI / names[1]); return a if x <= 0 else b if x >= 1 else Image.blend(a, b, ease(x))
POP, SHOT_A, SHOT_B = 0.5, 2.35, 3.95
def digit_for(q): return "3" if 0.75 < q <= 1.1 else "2" if 0.4 < q <= 0.75 else "1" if 0.05 < q <= 0.4 else ""
def s_cam(s):
    if s < SHOT_A + 0.15:
        shot = 0; Bimg = bpose(("B-base.png", "B-0.png"), (s - POP - 0.1) / 0.3)   # she reacts when I appear
        ga = 0 if s < POP else ease((s - POP) / 0.15); dig = digit_for(SHOT_A - s)
    else:
        shot = 1; Bimg = bpose(("B-0.png", "B-1.png"), (s - SHOT_A - 0.15) / 0.3); ga = 1.0; dig = digit_for(SHOT_B - s)
    p = cam_panel(camera_frame(Bimg, ga, shot, dig, s))
    if POP <= s < POP + 0.35: p = cover(p, PW, PH, 1.0 + 0.04 * (1 - back((s - POP) / 0.35)), 0.5, 0.25)
    for t0 in (SHOT_A, SHOT_B):
        if 0 <= s - t0 < 0.28: p = glow(p, 1 - (s - t0) / 0.28)
    return glow(p, 1 - (s - POP) / 0.2) if 0 <= s - POP < 0.2 else p

def s_making(s):                                       # the app's own making video, straight through, a bit faster
    t = 0.7 + s * 1.65; im = load(MK[min(len(MK) - 1, int(t * FPS))])
    return cover(im, PW, PH, 1.0, 0.5, 0.42)
def s_strip(s):
    im = load(ST[int(s * FPS) % len(ST)]); p = PANEL_PINK.copy(); k = (PH - 70) / im.height * (1.0 + 0.03 * ease(s / 2))
    put(p, im.resize((round(im.width * k), round(im.height * k)), Image.LANCZOS), PW / 2, PH / 2); return p
def s_end(s):
    p = PANEL_PINK.copy(); last = DSTRIPS[-1]; k = 760 / last.height
    st = last.resize((round(last.width * k), 760), Image.LANCZOS); y = 440 + 520 * (1 - back(s / 0.55)) + 6 * math.sin(s * 3)
    p.paste(st, (round(PW / 2 - st.width / 2), round(y - 380)), st)
    d = ImageDraw.Draw(p)
    rows = ((880, "따로 같이 네컷", BIG_F, INK), (1060, "가입 없이 · 사진은 7일 뒤 지워져요", MID_F, SUB), (1125, "프로필 링크에서 바로", MID_F, SUB))
    for i, (y, t, f, c) in enumerate(rows):
        a = ease((s - 0.3 - i * 0.18) / 0.3)
        if a > 0:
            layer = p.copy(); dl = ImageDraw.Draw(layer); x = (PW - dl.textlength(t, font=f)) / 2
            dl.text((x, y + 30 * (1 - a)), t, font=f, fill=c); p = Image.blend(p, layer, a)
    return p


# ---- 꾸미기 (mockup A): the strip hangs from a pin and sways; each look pops in with a sparkle and a #label --------------
LOOKS = sorted((R / "take9" / "looks3").glob("*.png"))
LOOK_NAMES = {"1-vintage": "#빈티지 필름", "2-y2k": "#키치 Y2K", "3-minimal": "#미니멀 흑백", "4-scrap": "#스크랩 다꾸", "5-lovely": "#러블리"}
DSH = 1180
DSTRIPS = [(lambda im: im.resize((round(im.width * DSH / im.height), DSH), Image.LANCZOS))(Image.open(p).convert("RGBA")) for p in LOOKS]
CHIP_F = F(GO, 34)
def hang(p, im, cx, top, angle, scale=1.0):
    if scale != 1.0: im = im.resize((round(im.width * scale), round(im.height * scale)), Image.BICUBIC)
    pad = Image.new("RGBA", (im.width * 2 + 40, im.height * 2 + 40), (0, 0, 0, 0)); ox, oy = pad.width // 2 - im.width // 2, pad.height // 2
    pad.paste(im, (ox, oy))
    sh = Image.new("RGBA", pad.size, (0, 0, 0, 0)); sh.paste((120, 70, 120, 255), (ox + 10, oy + 16), Image.new("L", im.size, 70))
    pad = Image.alpha_composite(sh.rotate(angle, resample=Image.BICUBIC), pad.rotate(angle, resample=Image.BICUBIC))
    p.paste(pad, (round(cx - pad.width / 2), round(top - pad.height / 2)), pad)
    d = ImageDraw.Draw(p); d.ellipse((cx - 16, top - 22, cx + 16, top + 10), fill=PINK3D); d.ellipse((cx - 12, top - 20, cx + 12, top + 4), fill=PINK)
def sparkle(p, s, cx, cy):
    if not 0 <= s < 0.45: return
    d = ImageDraw.Draw(p); k = s / 0.45
    for a in range(0, 360, 45):
        r0, r1 = 120 + 260 * k, 150 + 300 * k; x = math.radians(a + 20)
        d.line((cx + r0 * math.cos(x), cy + r0 * math.sin(x), cx + r1 * math.cos(x), cy + r1 * math.sin(x)),
               fill=(255, 255, 255) if a % 90 else (255, 220, 120), width=round(10 * (1 - k)) + 1)
def chip(p, text, a, y=1250):
    if a <= 0: return p
    layer = p.copy(); d = ImageDraw.Draw(layer); tw = d.textlength(text, font=CHIP_F); x = (PW - tw) / 2
    d.rounded_rectangle((x - 26, y + 6, x + tw + 26, y + 66), 30, fill=PINK3D); d.rounded_rectangle((x - 26, y, x + tw + 26, y + 60), 30, fill=(255, 255, 255))
    d.text((x, y + 8), text, font=CHIP_F, fill=INK); return Image.blend(p, layer, a)
DSWAP = 0.8
sway = lambda t: 4.0 * math.sin(2 * math.pi * t / 1.6)
def s_deco(s):
    p = PANEL_PINK.copy(); i = min(len(DSTRIPS) - 1, int(s / DSWAP)); q = s - i * DSWAP
    sc = 1.0 + 0.06 * (1 - back(q / 0.3)) if i > 0 else 1.0
    # start where the making video left the strip (pin at y 277) and glide up so it shows bigger; the sway grows in
    top = 60 + 217 * (1 - ease(s / 0.7))
    hang(p, DSTRIPS[i], PW / 2, top, sway(s) * ease(s / 0.6), sc)
    if i > 0: sparkle(p, q, PW / 2, PH / 2 - 60)
    return chip(p, LOOK_NAMES.get(LOOKS[i].stem, ""), ease(q / 0.15))

# (seconds, caption, voices, sfx, draw, transition into the next scene)
SC = [(2.4, "나는 카페, 넌 집인데", [("00", 0.15)], [], s_split, "push"),
      (3.25, "같이 사진 찍자고? 지금?", [("01", 0.95)], [("ding", 0.15), ("ding", 0.9), ("tick", 1.55), ("whoosh", 1.75), ("tick", 2.45), ("whoosh", 2.75)], s_chat, "fade"),
      (4.35, "열자마자 네가 내 옆에 있어!", [("02", 0.55)], [("pop", 0.5), ("tick", 1.3), ("tick", 1.65), ("tick", 2.0), ("shutter", 2.35),
                                                      ("tick", 2.9), ("tick", 3.25), ("tick", 3.6), ("shutter", 3.95)], s_cam, "fade"),
      (3.8, "따로 찍었는데", [("03", 0.1), ("04", 1.8)], [("tick", 0.0), ("tick", 0.62), ("tick", 1.24), ("tick", 1.86), ("thud", 3.1)], s_making, "fade"),
      (4.4, "꾸미는 것도 마음대로!", [], [("pop", 0.8), ("pop", 1.6), ("pop", 2.4), ("pop", 3.2), ("ding", 3.25)], s_deco, "fade"),
      (3.2, "같이 찍을 친구 태그해!", [("05", 0.1)], [("pop", 0.35)], s_end, None)]
CAP2 = {3: (1.8, "한 장에, 같이")}
TR = 0.32                                              # every hand-over lasts this long

def transition(kind, a, b, x):
    x = ease(x)
    if kind == "push":                                 # the old scene slides up, the new one rises in
        p = Image.new("RGB", (PW, PH), (241, 234, 255)); off = round(PH * x)
        p.paste(a, (0, -off)); p.paste(b, (0, PH - off)); return p
    return Image.blend(a, b, x)

def chrome(cap_a, cap_b, x, pan):
    f = BG.copy(); d = ImageDraw.Draw(f)
    d.text(((W - d.textlength("따로 같이 네컷", font=TITLE_F)) / 2, 40), "따로 같이 네컷", font=TITLE_F, fill=DEEP)
    # the old caption fades out in the first half of a hand-over, the new one fades in during the second: never both
    for cap, a in ((cap_a, 1 - ease(x * 2)), (cap_b, ease(x * 2 - 1))) if cap_a != cap_b else ((cap_a, 1.0),):
        if a <= 0 or not cap: continue
        layer = f.copy(); dl = ImageDraw.Draw(layer); cw = dl.textlength(cap, font=CAP_F); x0 = (W - cw) / 2
        dl.rounded_rectangle((x0 - 34, 148, x0 + cw + 34, 262), 57, fill=PINK3D); dl.rounded_rectangle((x0 - 34, 140, x0 + cw + 34, 254), 57, fill=(255, 255, 255))
        dl.text((x0, 128), cap, font=CAP_F, fill=INK); f = Image.blend(f, layer, a) if a < 1 else layer
    d = ImageDraw.Draw(f); tag = "출연: AI로 만든 사람"; d.text((W - 40 - d.textlength(tag, font=TAG_F), 300), tag, font=TAG_F, fill=SUB)
    sh = Image.new("L", (PW, PH), 0); ImageDraw.Draw(sh).rounded_rectangle((0, 0, PW - 1, PH - 1), 40, fill=90)
    f.paste((201, 184, 240), (PX, PY + 10, PX + PW, PY + PH + 10), sh)          # soft lavender shadow
    f.paste(pan, (PX, PY), rmask(pan.size, 40)); return f

def caption_at(j, s):
    cap = SC[j][1]
    return CAP2[j][1] if j in CAP2 and s >= CAP2[j][0] else cap

enc = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
                        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18", "-preset", "medium", str(R / "v9-video.mp4")], stdin=subprocess.PIPE)
starts, t = [], 0.0
for sc in SC: starts.append(t); t += sc[0]
total, voices, fx = t, [], []
for j, sc in enumerate(SC):
    voices += [(starts[j] + a, v) for v, a in sc[2]]; fx += [(starts[j] + a, x) for x, a in sc[3]]
for i in range(round(total * FPS)):
    tt = i / FPS; j = max(k for k in range(len(SC)) if starts[k] <= tt + 1e-9); s = tt - starts[j]
    pan = SC[j][4](s); cap_a = cap_b = caption_at(j, s); x = 1.0
    if j > 0 and s < TR and SC[j - 1][5]:             # still handing over from the previous scene
        x = s / TR; prev = SC[j - 1][4](SC[j - 1][0] + s)
        pan = transition(SC[j - 1][5], prev, pan, x); cap_a = caption_at(j - 1, SC[j - 1][0] + s)
    enc.stdin.write(chrome(cap_a, cap_b, x, pan).tobytes())
enc.stdin.close(); enc.wait()

import json as _j
(R / "v9-timeline.json").write_text(_j.dumps({"total": total, "voices": voices, "fx": fx}))
print("video done", f"{total:.1f}s")
