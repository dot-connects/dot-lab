"""Seventh cut of the catch reel (re-planned 11-17, reel/PLAN-v7.md). 32 s, 1080 x 1920, 30 fps.

python reel/render/edit6.py

Six shots, no crops, no camera moves except the dex easing out from the new card:
catch 4.8 (out-catch7, the app's layers on from frame 1) -> capsule pop 3.0 (out-pop7: the same capsule on the catch
shot's own plate, so catch -> capsule -> fur-ball is one shot) | dex 2.4 | home 13.5 in ONE take, the whole screen
(out-home7: lives, double tap cloud held 2.6 s, throw across the screen, wall) | grid 5.2 (out-mont7) | end 3.4.
Captions are drawn here (Noto Sans KR 900, white, ink outline, the key word yellow; no pill): the footage is real
video and a real-looking phone, so the type is the plain reels type, and the app's own cards keep their own look.
Sound: each page's events -> the app's res/raw sfx.
"""
import json
import math
import re
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

R = Path(__file__).resolve().parent
OUT = R / "out-reel7"
FPS, W, H = 30, 1080, 1920
INK, WHITE, SUN = (27, 33, 24), (255, 255, 255), (255, 216, 77)
FONT = "C:/Windows/Fonts/NotoSansKR-VF.ttf"


def ease(x: float) -> float:
    x = min(1, max(0, x)); return 1 - (1 - x) ** 3


def img(p: Path) -> Image.Image:
    im = Image.open(p).convert("RGB")
    return im if im.size == (W, H) else im.resize((W, H), Image.LANCZOS)


def push(im: Image.Image, k: float, cx: float = .5, cy: float = .5) -> Image.Image:
    if k <= 1.0001:
        return im
    w, h = W / k, H / k
    x0 = min(max(0, cx * W - w / 2), W - w); y0 = min(max(0, cy * H - h / 2), H - h)
    return im.resize((W, H), Image.BICUBIC, box=(x0, y0, x0 + w, y0 + h))


def frames(folder: str, t: float) -> Image.Image:
    d = R / folder
    last = len(list(d.glob("[0-9]*.png")))
    return img(d / f"{min(last, max(1, int(round(t * FPS)) + 1)):04d}.png")


# ---------- captions ----------
def caption(text: str, cy: int, size: int = 88) -> tuple[Image.Image, tuple[int, int, int, int]]:
    """'*word*' is the yellow key word, '|' a line break. Returns the full-frame RGBA layer and its box."""
    font = ImageFont.truetype(FONT, size); font.set_variation_by_axes([900])
    lines = [[(seg.strip("*"), SUN if seg.startswith("*") else WHITE) for seg in re.split(r"(\*[^*]+\*)", ln) if seg] for ln in text.split("|")]
    lh, sw = int(size * 1.24), max(6, size // 9)
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(layer)
    place = []
    for i, segs in enumerate(lines):
        x = (W - sum(font.getlength(s) for s, _ in segs)) / 2
        y = cy - lh * len(lines) / 2 + lh * i + lh * .78
        for s, col in segs:
            place.append((x, y, s, col)); x += font.getlength(s)
    for x, y, s, _ in place:   # every outline first, so a neighbour's outline never covers a letter
        d.text((x, y), s, font=font, fill=INK, anchor="ls", stroke_width=sw, stroke_fill=INK)
    for x, y, s, col in place:
        d.text((x, y), s, font=font, fill=col, anchor="ls")
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 110), (0, 8), layer.getchannel("A"))
    layer = Image.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(10)), layer)
    box = layer.getbbox()
    return layer, box


def put_cap(frame: Image.Image, cap, u: float, left: float) -> Image.Image:
    """u: s since the caption came in; left: s until it leaves. In: 0.9 -> 1 and fade over 5 frames; out: 4 frames."""
    layer, (x0, y0, x1, y1) = cap
    if u < 0 or left <= 0:
        return frame
    a = min(1.0, u * FPS / 4, left * FPS / 4)
    sc = .9 + .1 * ease(u * FPS / 6)
    part = layer.crop((x0, y0, x1, y1))
    if sc < .999:
        part = part.resize((max(1, int(part.width * sc)), max(1, int(part.height * sc))), Image.BICUBIC)
    if a < 1:
        part.putalpha(part.getchannel("A").point(lambda v: int(v * a)))
    frame.paste(part, (int((x0 + x1) / 2 - part.width / 2), int((y0 + y1) / 2 - part.height / 2)), part)
    return frame


# ---------- shots ----------
DEX_CARD = (.498, .755)
ICON = (0.8385, 300 / 731.43, 52 / 411.43)   # 리얼뽑기 on the quiet home (home.html ONE_ROW, ONE_S): x, y share, width share


def dex(t: float) -> Image.Image:
    # starts close on the new card (it stamps in), eases out to the whole dex; 7 -> 8 once the whole page shows
    kc = 1.45
    stamp = 1 + .08 * math.sin(min(1, t / .35) * math.pi)
    e = ease((t - 1.0) / .7)
    k = (kc + (1 - kc) * e) * (stamp if t < .35 else 1)
    return push(img(R / ("out-dex8/shot.png" if t >= 1.75 else "out-dex7/shot.png")), k, DEX_CARD[0] + (.5 - DEX_CARD[0]) * e, DEX_CARD[1] + (.5 - DEX_CARD[1]) * e)


def home(t: float) -> Image.Image:
    scr = frames("out-home7", t)
    if t < .4:   # the dex shrinks into the app's icon (going home)
        e = ease(t / .4)
        s = 1 + (ICON[2] - 1) * e
        w, h = max(1, int(W * s)), max(1, int(H * s))
        x = int((W / 2) * (1 - e) + ICON[0] * W * e - w / 2); y = int((H / 2) * (1 - e) + ICON[1] * H * e - h / 2)
        m = Image.new("L", (w, h), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), radius=int(min(w, h) * (.02 + .27 * e)), fill=int(255 * (1 - e ** 3)))
        scr = scr.copy(); scr.paste(img(R / "out-dex8/shot.png").resize((w, h)), (x, y), m)
    return scr


TOP, MID = 330, 1420
CUTS = [   # (name, s, frame fn, events (folder, source offset s), captions [(text, centre y, in s, out s)])
    ("catch", 4.8, lambda t: frames("out-catch7", t), ("out-catch7", 0), [("우리 집 고양이를|*인형뽑기*로 뽑았다", MID, .1, 2.6)]),
    ("pop", 3.0, lambda t: frames("out-pop7", t), ("out-pop7", 0), [("*털뭉치*가 됐다", TOP, 1.3, 3.0)]),
    ("dex", 2.4, dex, None, [("도감에 *쏙*", 262, .15, 2.4)]),
    ("home", 13.5, home, ("out-home7", 0), [("이제 내 폰에 *살아요*", TOP, .45, 3.4), ("*톡톡* 두드리면|자주 쓰는 앱이 나와요", TOP, 3.65, 6.7),
                                             ("던지면 *어질어질*", TOP, 7.0, 10.5), ("벽에도 *착* 붙어요", TOP, 10.9, 13.5)]),
    ("grid", 5.2, lambda t: frames("out-mont7", t), ("out-mont7", 0), [("강아지도 *사람도*", 250, .15, 5.2)]),
    ("end", 3.4, lambda t: frames("out-end7", t), ("out-end7", 0), []),
]
SOUND = {"drop": ("sfx_drop", 1), "grip": ("sfx_grip", 1), "lift": ("sfx_lift", 1), "rail": ("sfx_rail", 1), "bounce": ("sfx_land", 1),
         "win": ("sfx_win", 1), "shake": ("sfx_rail", .8), "pop": ("sfx_win", .8), "boing": ("sfx_land", .8), "tap": ("sfx_rail", 1.2),
         "land": ("sfx_land", 1.2), "bonk": ("sfx_land", .9), "grab": ("sfx_grip", .7), "whoosh": ("sfx_drop", 1), "stick": ("sfx_grip", 1.1)}


def main() -> None:
    (OUT / "frames").mkdir(parents=True, exist_ok=True)
    caps = {c[0]: [(caption(text, cy), t0, t1) for text, cy, t0, t1 in c[4]] for c in CUTS}
    starts, t0 = [], 0.0
    for c in CUTS:
        starts.append(t0); t0 += c[1]
    total = int(round(t0 * FPS))
    for f in range(total):
        t = f / FPS
        i = max(k for k in range(len(CUTS)) if starts[k] <= t + 1e-6)
        name, dur, fn, _, _ = CUTS[i]
        lt = min(dur - 1e-3, t - starts[i])
        im = fn(lt).convert("RGB").copy()
        for cap, c0, c1 in caps[name]:
            im = put_cap(im, cap, lt - c0, c1 - lt)
        im.save(OUT / "frames" / f"{f + 1:04d}.png", compress_level=1)
        if f % 90 == 0:
            print(f"{f + 1}/{total} {name}", flush=True)

    raw = R.parent.parent / "app/app/src/main/res/raw"
    play = []
    for i, c in enumerate(CUTS):
        if not c[3]:
            continue
        folder, off = c[3]
        for e in json.loads((R / folder / "events.json").read_text(encoding="utf-8")).get("events", []):
            ms, n = e[0], e[1]; v = e[2] if len(e) > 2 else .5
            tt = ms / 1000 - off
            if 0 <= tt < c[1] and n in SOUND and v > 0:
                fname, gain = SOUND[n]; play.append((starts[i] + tt, fname, min(1, v * gain)))
    (OUT / "sfx.json").write_text(json.dumps({"play": play, "starts": starts, "total": t0}, indent=1), encoding="utf-8")
    dst = R.parent.parent / "out" / "reel-rough7.mp4"
    args = ["ffmpeg", "-v", "error", "-y", "-framerate", str(FPS), "-i", str(OUT / "frames" / "%04d.png")]
    for _, fname, _ in play:
        args += ["-i", str(raw / f"{fname}.ogg")]
    parts = [f"[{k + 1}:a]volume={v:.2f},adelay={int(t * 1000)}|{int(t * 1000)}[a{k}]" for k, (t, _, v) in enumerate(play)]
    mix = ";".join(parts) + ";" + "".join(f"[a{k}]" for k in range(len(play))) + f"amix=inputs={len(play)}:normalize=0,apad,atrim=0:{t0:.3f}[aout]"
    args += ["-filter_complex", mix, "-map", "0:v", "-map", "[aout]", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18",
             "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", str(dst)]
    subprocess.run(args, check=True)
    print("wrote", dst, f"{t0:.1f}s")


if __name__ == "__main__":
    main()
