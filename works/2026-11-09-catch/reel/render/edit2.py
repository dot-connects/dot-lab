"""Second cut of the catch reel after the 11-17 review (Fable) and the operator's notes. ~20.6 s, 1080 x 1920, 30 fps.

python reel/render/edit2.py

Beats: catch (press -> lift, the app screen) | capsule pop -> 모찌 fur-ball (transform.html) | dex card |
home screen: talks, double tap -> shortcut cloud -> app opens, throw -> bounce -> dizzy, stick to the wall (home.html) |
the real cat stares (A10) | other cats and dogs pop into fur-balls (montage.html) | animated end card (endcard.html).
Captions (cap.html, matted by capmatte.py) pop in and sit away from the subject. Sound: the app's own res/raw sfx
at each page's event times; music and voice are added later.
"""
import json
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image

R = Path(__file__).resolve().parent
OUT = R / "out-reel2"
FPS, W, H = 30, 1080, 1920


def img(p: Path) -> Image.Image:
    im = Image.open(p).convert("RGB")
    return im if im.size == (W, H) else im.resize((W, H), Image.LANCZOS)


def push(im: Image.Image, k: float, cx: float = .5, cy: float = .5) -> Image.Image:
    if k <= 1.0001:
        return im
    w, h = W / k, H / k
    x0 = min(max(0, cx * W - w / 2), W - w); y0 = min(max(0, cy * H - h / 2), H - h)
    return im.resize((W, H), Image.BICUBIC, box=(x0, y0, x0 + w, y0 + h))


def grade(a: np.ndarray, s: float = 1.0) -> np.ndarray:
    f = a.astype(np.float32) / 255
    lum = f @ np.array([.299, .587, .114], np.float32)
    f = lum[..., None] + (f - lum[..., None]) * (1 + .12 * s)
    sep = np.stack([lum * 1.07 + .03, lum * .97 + .02, lum * .80], -1)
    f = f * (1 - .16 * s) + np.clip(sep, 0, 1) * .16 * s
    f = (f - .5) * (1 + .06 * s) + .5 + .02 * s
    return np.clip(f * 255, 0, 255)


def ease(x: float) -> float:
    x = min(1, max(0, x)); return 1 - (1 - x) ** 3


def seq(folder: str, start_s: float, g: float = 0, k0: float = 1, k1: float = 1, cx=.5, cy=.5, dur: float = 1):
    last = len(list((R / folder).glob("[0-9]*.*")))
    def f(t: float) -> np.ndarray:
        fr = min(last, int(round((start_s + t) * FPS)) + 1)
        im = push(img(R / folder / f"{fr:04d}.{'jpg' if folder in ('a10',) else 'png'}"), k0 + (k1 - k0) * t / dur, cx, cy)
        a = np.asarray(im).astype(np.float32)
        return grade(a, g) if g else a
    return f


def still(path: str, k0: float, k1: float, cx: float, cy: float, dur: float):
    base = img(R / path)
    return lambda t: np.asarray(push(base, k0 + (k1 - k0) * ease(t / dur), cx, cy)).astype(np.float32)


# (name, seconds, frame fn, caption folder or None, events json + its time offset in the source, s)
CUTS = [
    ("catch", 2.0, seq("out-catch-a09", 1.43, g=.4, k0=1.0, k1=1.08, cx=.5, cy=.42, dur=2.0), "out-cap-c1", ("out-catch-a09", 1.43)),
    ("pop", 1.9, seq("out-pop", 0), "out-cap-c2@1.0", ("out-pop-ev", 0)),
    ("dex", 1.0, still("out-dex/shot.png", 1.0, 1.35, .5, .66, 1.0), "out-cap-c3", None),
    ("home1", 1.5, seq("out-home2", .2), "out-cap-c4", ("out-home2", .2)),
    ("home2", 2.0, seq("out-home2", 3.0), "out-cap-c5", ("out-home2", 3.0)),
    ("home3", 2.3, seq("out-home2", 5.5), "out-cap-c6", ("out-home2", 5.5)),
    ("home4", 1.5, seq("out-home2", 8.5), "out-cap-c7", ("out-home2", 8.5)),
    ("stare", 1.8, seq("a10", 2.0, g=1, k0=1.05, k1=1.13, cx=.62, cy=.42, dur=1.8), None, None),
    ("montage", 4.1, seq("out-mont-r", 0), "out-cap-c8", ("out-mont-r", 0)),
    ("end", 3.0, seq("out-end2", 0), None, ("out-end2", 0)),
]
XF = 3


def cap_layer(spec: str):
    folder, _, delay = spec.partition("@")
    a = np.asarray(Image.open(R / folder / "shot.png").convert("RGBA")).astype(np.float32)
    ys, xs = np.nonzero(a[..., 3] > 8)
    return a, (xs.mean(), ys.mean()), float(delay or 0)


def put_cap(frame: np.ndarray, cap, t: float, dur: float) -> np.ndarray:
    a, (cx, cy), delay = cap
    u = t - delay
    if u < 0 or u > dur - delay:
        return frame
    k = .82 + .18 * ease(u / .16) + .04 * np.sin(min(1, u / .3) * np.pi)   # pop in with a small overshoot
    al = min(1, u / .08, (dur - delay - u) / .1)
    im = Image.fromarray(a.astype(np.uint8), "RGBA")
    if abs(k - 1) > 1e-3:
        w, h = int(W * k), int(H * k)
        big = im.resize((w, h), Image.BICUBIC)
        canvas = Image.new("RGBA", (W, H))
        canvas.paste(big, (int(cx - cx * k), int(cy - cy * k)))
        im = canvas
    c = np.asarray(im).astype(np.float32)
    m = c[..., 3:4] / 255 * al
    return frame * (1 - m) + c[..., :3] * m


def main() -> None:
    (OUT / "frames").mkdir(parents=True, exist_ok=True)
    caps = {i: cap_layer(c[3]) for i, c in enumerate(CUTS) if c[3]}
    starts, t0 = [], 0.0
    for c in CUTS:
        starts.append(t0); t0 += c[1]
    total = int(round(t0 * FPS))
    for f in range(total):
        t = f / FPS
        i = max(k for k in range(len(CUTS)) if starts[k] <= t + 1e-6)
        name, dur, fn, _, _ = CUTS[i]
        lt = min(dur - 1e-3, t - starts[i])
        a = fn(lt)
        into = int(round((t - starts[i]) * FPS))
        if i > 0 and into < XF:
            b = CUTS[i - 1][2](CUTS[i - 1][1] - 1e-3)
            k = (into + 1) / (XF + 1)
            a = a * k + b * (1 - k)
        if i in caps:
            a = put_cap(a, caps[i], lt, dur)
        Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).save(OUT / "frames" / f"{f + 1:04d}.png")
        if f % 60 == 0:
            print(f"{f + 1}/{total} {name}", flush=True)

    # sound: each cut's own events, moved onto the reel clock and cut to its window
    raw = R.parent.parent / "app/app/src/main/res/raw"
    sound = {"drop": ("sfx_drop", 1), "grip": ("sfx_grip", 1), "lift": ("sfx_lift", 1), "rail": ("sfx_rail", 1), "bounce": ("sfx_land", 1),
             "win": ("sfx_win", 1), "shake": ("sfx_rail", .8), "pop": ("sfx_win", .8), "boing": ("sfx_land", .8), "tap": ("sfx_rail", 1.2),
             "land": ("sfx_land", 1.2), "bonk": ("sfx_land", .9), "grab": ("sfx_grip", .7), "whoosh": ("sfx_drop", 1), "stick": ("sfx_grip", 1.1)}
    (R / "out-pop-ev").mkdir(exist_ok=True)
    (R / "out-pop-ev" / "events.json").write_text(json.dumps({"events": [[450, "shake", .4], [850, "pop", .8], [900, "boing", .6]]}))
    play = []
    for i, c in enumerate(CUTS):
        if not c[4]:
            continue
        folder, off = c[4]
        evs = json.loads((R / folder / "events.json").read_text(encoding="utf-8")).get("events", [])
        for e in evs:
            ms, n = e[0], e[1]; v = e[2] if len(e) > 2 else .5
            tt = ms / 1000 - off
            if 0 <= tt < c[1] and n in sound and v > 0:
                f_, gain = sound[n]
                play.append((starts[i] + tt, f_, min(1, v * gain)))
    (OUT / "sfx.json").write_text(json.dumps({"play": play, "starts": starts, "total": t0}, indent=1), encoding="utf-8")
    dst = R.parent.parent / "out" / "reel-rough2.mp4"
    args = ["ffmpeg", "-v", "error", "-threads", "2", "-y", "-framerate", str(FPS), "-i", str(OUT / "frames" / "%04d.png")]
    for _, f_, _ in play:
        args += ["-i", str(raw / f"{f_}.ogg")]
    parts = [f"[{k + 1}:a]volume={v:.2f},adelay={int(t * 1000)}|{int(t * 1000)}[a{k}]" for k, (t, _, v) in enumerate(play)]
    mix = ";".join(parts) + ";" + "".join(f"[a{k}]" for k in range(len(play))) + f"amix=inputs={len(play)}:normalize=0,apad,atrim=0:{t0:.3f}[aout]"
    args += ["-filter_complex", mix, "-map", "0:v", "-map", "[aout]", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18",
             "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", str(dst)]
    subprocess.run(args, check=True)
    print("wrote", dst, f"{t0:.1f}s")


if __name__ == "__main__":
    main()
