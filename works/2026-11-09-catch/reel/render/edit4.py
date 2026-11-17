"""Fifth cut of the catch reel: the spec from Fable's second review (11-17) on the operator's 9 notes. 25 s, 1080 x 1920.

python reel/render/edit4.py

Beats (s): open 1.5 | catch 3.6 | capsule pop 2.4 | dex 2.0 (zoom on the new card, out to the grid, 7 -> 8) |
home on a hand-free phone: lives 2.0, double tap -> shortcut cloud held -> app 3.0, throw 1.6, wall 1.4 |
fur-ball grid 4.5 | end card 3.0. Hard cuts. Captions v2 (cap2.html): app card pill, highlighter swept in.
The home screen sits on a phone (black bezel, round corners) over the blurred room, swaying slightly; the dex shrinks
into the app icon on the way out. Sound: each page's events -> the app's res/raw sfx.
"""
import json
import math
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

R = Path(__file__).resolve().parent
OUT = R / "out-reel4"
FPS, W, H = 30, 1080, 1920


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


def grade(im: Image.Image, s: float) -> Image.Image:
    if not s:
        return im
    f = np.asarray(im).astype(np.float32) / 255
    lum = f @ np.array([.299, .587, .114], np.float32)
    f = lum[..., None] + (f - lum[..., None]) * (1 + .12 * s)
    sep = np.stack([lum * 1.07 + .03, lum * .97 + .02, lum * .80], -1)
    f = f * (1 - .16 * s) + np.clip(sep, 0, 1) * .16 * s
    return Image.fromarray(np.clip(f * 255, 0, 255).astype(np.uint8))


def frames(folder: str, t: float) -> Image.Image:
    d = R / folder
    last = len(list(d.glob("[0-9]*.*")))
    i = min(last, max(1, int(round(t * FPS)) + 1))
    p = d / f"{i:04d}.png"
    return img(p if p.exists() else d / f"{i:04d}.jpg")


# ---------- the phone (home beats) ----------
ROOM = Image.open(R / "a10/0060.jpg").convert("RGB").resize((W, H)).filter(ImageFilter.GaussianBlur(40))
ROOM = Image.fromarray((np.asarray(ROOM).astype(np.float32) * .82).astype(np.uint8))
PS = .96
SW, SH = int(W * PS), int(H * PS)
MASK = Image.new("L", (SW, SH), 0); ImageDraw.Draw(MASK).rounded_rectangle((0, 0, SW - 1, SH - 1), radius=int(90 * PS), fill=255)
BEZ = 14
BODY = Image.new("RGBA", (SW + 2 * BEZ, SH + 2 * BEZ), (0, 0, 0, 0))
ImageDraw.Draw(BODY).rounded_rectangle((0, 0, SW + 2 * BEZ - 1, SH + 2 * BEZ - 1), radius=int(90 * PS) + BEZ, fill=(16, 16, 18, 255))
ICON = (.875, 423 / (1920 / 2.625))   # 리얼뽑기 on the home grid (home.html ourIcon), share of the screen


def phone(screen: Image.Image, t: float) -> Image.Image:
    body = BODY.copy()
    body.paste(screen.resize((SW, SH), Image.LANCZOS), (BEZ, BEZ), MASK)
    body = body.rotate(1.2 * math.sin(t * 1.3), resample=Image.BICUBIC, expand=False)
    out = ROOM.copy()
    out.paste(body, ((W - body.width) // 2, (H - body.height) // 2), body)
    return out


def home(src_t0: float, k0: float, k1: float, cx: float, cy: float, dex_in: bool = False):
    def f(t: float) -> Image.Image:
        scr = frames("out-home3", src_t0 + t)
        if dex_in and t < .35:   # the dex shrinks into the app's icon (going home)
            e = ease(t / .35)
            dex = img(R / "out-dex8/shot.png")
            s = 1 + (48 / 411.43 - 1) * e
            w, h = int(W * s), int(H * s)
            x = int((W / 2) * (1 - e) + ICON[0] * W * e - w / 2); y = int((H / 2) * (1 - e) + ICON[1] * H * e - h / 2)
            m = Image.new("L", (w, h), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), radius=int(60 * s + 60 * e * s), fill=255)
            scr = scr.copy(); scr.paste(dex.resize((max(1, w), max(1, h))), (x, y), m)
        return push(phone(scr, t), k0 + (k1 - k0) * ease(t / .6), cx, cy)
    return f


def catch(t: float) -> Image.Image:
    im = frames("out-catch4", t)
    k = 1 + .25 * ease((t - .5) / .5)
    return grade(push(im, k, .5, .5), .35)


def dex(t: float) -> Image.Image:
    # the new card fills ~70 % of the width, stamps in (1 -> 1.12 -> 1), holds; then out to the grid; 7 -> 8 at the end
    cx, cy, kc = .498, .755, 2.25
    stamp = 1 + .12 * math.sin(min(1, t / .3) * math.pi)
    if t < .8:
        k, x, y = kc * stamp, cx, cy
    else:
        e = ease((t - .8) / .6); k = kc + (1 - kc) * e; x = cx + (.5 - cx) * e; y = cy + (.5 - cy) * e
    src = "out-dex8/shot.png" if t >= 1.4 else "out-dex7/shot.png"
    return push(img(R / src), k, x, y)


CUTS = [   # (name, s, frame fn, caption key, events (folder, source offset s))
    ("open", 1.5, lambda t: grade(push(frames("a10", .5 + t), 1 + .05 * t / 1.5, .55, .45), 1), "v1", None),
    ("catch", 3.6, catch, "v2", ("out-catch4", 0)),
    ("pop", 2.4, lambda t: frames("out-pop", t), "v3", ("out-pop-ev", 0)),
    ("dex", 2.0, dex, "v4", None),
    ("home1", 2.0, home(.1, 1.0, 1.15, .4, .66, dex_in=True), "v5", ("out-home3", .1)),
    ("home2", 3.0, home(2.9, 1.15, 1.32, .45, .6), "v6", ("out-home3", 2.9)),
    ("home3", 1.6, home(6.3, 1.15, 1.15, .58, .48), "v7", ("out-home3", 6.3)),
    ("home4", 1.4, home(9.3, 1.12, 1.12, .66, .5), "v8", ("out-home3", 9.3)),
    ("grid", 4.5, lambda t: frames("out-mont2", t), "v9", ("out-mont2", 0)),
    ("end", 3.0, lambda t: frames("out-end4", t), None, ("out-end4", 0)),
]
CAP_DELAY = {"v8": .5}


def cap_layers(key: str):
    full = np.asarray(Image.open(R / f"out-cap2-{key}-1/shot.png").convert("RGBA")).astype(np.float32)
    bare = np.asarray(Image.open(R / f"out-cap2-{key}-0/shot.png").convert("RGBA")).astype(np.float32)
    ys, xs = np.nonzero(full[..., 3] > 8)
    return full, bare, (xs.min(), xs.max(), (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2)


def put_cap(frame: np.ndarray, cap, u: float, left: float) -> np.ndarray:
    """u: s since the caption appeared; left: s until the cut ends."""
    full, bare, (x0, x1, cx, cy) = cap
    if u < 0 or left < .1:
        return frame
    fu = u * FPS
    sc = .7 + .36 * ease(fu / 4) if fu < 4 else 1.06 - .06 * ease((fu - 4) / 3)   # 0.7 -> 1.06 -> 1.0 over 7 frames
    sweep = x0 + (x1 - x0) * ease((fu - 5) / 5)                                     # highlighter left to right, frames 5-10
    layer = np.where((np.arange(W)[None, :, None] <= sweep), full, bare)
    al = min(1.0, (left - .1) / .1)
    im = Image.fromarray(layer.astype(np.uint8), "RGBA")
    if abs(sc - 1) > 1e-3:
        big = im.resize((int(W * sc), int(H * sc)), Image.BICUBIC)
        canvas = Image.new("RGBA", (W, H)); canvas.paste(big, (int(cx - cx * sc), int(cy - cy * sc))); im = canvas
    c = np.asarray(im).astype(np.float32)
    m = c[..., 3:4] / 255 * al
    return frame * (1 - m) + c[..., :3] * m


def main() -> None:
    (OUT / "frames").mkdir(parents=True, exist_ok=True)
    caps = {c[3]: cap_layers(c[3]) for c in CUTS if c[3]}
    starts, t0 = [], 0.0
    for c in CUTS:
        starts.append(t0); t0 += c[1]
    total = int(round(t0 * FPS))
    for f in range(total):
        t = f / FPS
        i = max(k for k in range(len(CUTS)) if starts[k] <= t + 1e-6)
        name, dur, fn, ck, _ = CUTS[i]
        lt = min(dur - 1e-3, t - starts[i])
        a = np.asarray(fn(lt).convert("RGB")).astype(np.float32)
        if ck:
            a = put_cap(a, caps[ck], lt - .15 - CAP_DELAY.get(ck, 0), dur - lt)
        Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).save(OUT / "frames" / f"{f + 1:04d}.png")
        if f % 60 == 0:
            print(f"{f + 1}/{total} {name}", flush=True)

    raw = R.parent.parent / "app/app/src/main/res/raw"
    sound = {"drop": ("sfx_drop", 1), "grip": ("sfx_grip", 1), "lift": ("sfx_lift", 1), "rail": ("sfx_rail", 1), "bounce": ("sfx_land", 1),
             "win": ("sfx_win", 1), "shake": ("sfx_rail", .8), "pop": ("sfx_win", .8), "boing": ("sfx_land", .8), "tap": ("sfx_rail", 1.2),
             "land": ("sfx_land", 1.2), "bonk": ("sfx_land", .9), "grab": ("sfx_grip", .7), "whoosh": ("sfx_drop", 1), "stick": ("sfx_grip", 1.1)}
    play = []
    for i, c in enumerate(CUTS):
        if not c[4]:
            continue
        folder, off = c[4]
        for e in json.loads((R / folder / "events.json").read_text(encoding="utf-8")).get("events", []):
            ms, n = e[0], e[1]; v = e[2] if len(e) > 2 else .5
            tt = ms / 1000 - off
            if 0 <= tt < c[1] and n in sound and v > 0:
                fname, gain = sound[n]; play.append((starts[i] + tt, fname, min(1, v * gain)))
    (OUT / "sfx.json").write_text(json.dumps({"play": play, "starts": starts, "total": t0}, indent=1), encoding="utf-8")
    dst = R.parent.parent / "out" / "reel-rough5.mp4"
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
