"""Rough cut of the catch reel (spec-reel.md, look A): one tortie cat, the real app's screens, ~21 s, 1080 x 1920, 30 fps.

python reel/render/edit.py   (run from works/2026-11-09-catch)

Sources (all made by the render pages in this folder or extracted from reel/stock, see reel/SOURCES.md):
  b01/        B01 opener (owner + the cat on the bed)        a10/  A10 the same cat staring (closing reaction)
  out-catch-a09/  the app's catch screen (catch.html)        out-dex/shot.png  the dex (dex.html)
  out-phone/  the home-screen pet inside C01's phone (home.html + phone_comp.py)
  out-cap-N/shot.png  captions (cap.html)                    out-end/shot.png  end card (end.html)
Writes out-reel/frames/*.png, out-reel/sfx.json (cue list for the mix) and ../../out/reel-rough.mp4.
"""
import json
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image

R = Path(__file__).resolve().parent
OUT = R / "out-reel"
FPS, W, H = 30, 1080, 1920


def img(p: Path) -> Image.Image:
    im = Image.open(p).convert("RGB")
    return im if im.size == (W, H) else im.resize((W, H), Image.LANCZOS)


def push(im: Image.Image, k: float, cx: float = .5, cy: float = .5) -> Image.Image:
    """Zoom in by k (1 = none) about (cx, cy) of the frame."""
    if k <= 1.0001:
        return im
    w, h = W / k, H / k
    x0 = min(max(0, cx * W - w / 2), W - w); y0 = min(max(0, cy * H - h / 2), H - h)
    return im.resize((W, H), Image.BICUBIC, box=(x0, y0, x0 + w, y0 + h))


def grade(a: np.ndarray, s: float = 1.0) -> np.ndarray:
    """Look A: warm, a touch more saturation and contrast, soft vignette (the mockup's CSS filter, strength s)."""
    f = a.astype(np.float32) / 255
    lum = f @ np.array([.299, .587, .114], np.float32)
    f = lum[..., None] + (f - lum[..., None]) * (1 + .12 * s)
    sep = np.stack([lum * 1.07 + .03, lum * .97 + .02, lum * .80], -1)
    f = f * (1 - .16 * s) + np.clip(sep, 0, 1) * .16 * s
    f = (f - .5) * (1 + .06 * s) + .5 + .03 * s
    return np.clip(f * 255, 0, 255)


yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
VIG = (1 - .38 * np.clip((np.hypot((xx - W / 2) / (W * .62), (yy - H * .45) / (H * .62)) - .55) / .45, 0, 1))[..., None]


def vig(a: np.ndarray, s: float = 1.0) -> np.ndarray:
    return a * (1 - (1 - VIG) * s)


def ease(x: float) -> float:
    x = min(1, max(0, x)); return 1 - (1 - x) ** 3


# ---------- the cut list: (name, seconds, frame(t_local) -> float32 array) ----------
def b01(t):  # 0..2.6 s: slow push-in
    im = push(img(R / f"b01/{int(t * FPS) + 1:04d}.jpg"), 1 + .07 * t / 2.6, .55, .62)
    return vig(grade(np.asarray(im)))


CATCH0 = 10   # catch.html frame where the reel enters (0.3 s: the claw resting, hint showing)


def catch(t):
    im = img(R / f"out-catch-a09/{CATCH0 + int(t * FPS):04d}.png")
    return vig(grade(np.asarray(im), .45), .5)


def dex(t):  # card 8 (row 3, middle) pulled in
    im = push(img(R / "out-dex/shot.png"), 1 + .16 * ease(t / 1.4), .5, .62)
    return np.asarray(im).astype(np.float32)


def phone(t):
    im = push(img(R / f"out-phone/{int(t * FPS) + 1:04d}.png"), 1.3 + .2 * t / 6.8, .46, .58)
    return vig(grade(np.asarray(im)))


def a10(t):  # the real cat stares back
    im = push(img(R / f"a10/{60 + int(t * FPS):04d}.jpg"), 1.05 + .08 * t / 2.4, .62, .42)
    return vig(grade(np.asarray(im)))


def end(t):
    return np.asarray(push(img(R / "out-end/shot.png"), 1.06 - .06 * ease(t / .5))).astype(np.float32)


CUTS = [("b01", 2.6, b01), ("catch", 5.6, catch), ("dex", 1.6, dex), ("phone", 6.8, phone), ("a10", 2.4, a10), ("end", 2.6, end)]
# captions: (start, end, cap folder); times on the reel clock
CAPS = [(0.25, 2.6, "out-cap-1"), (4.0, 6.9, "out-cap-2"), (7.1, 9.8, "out-cap-3"), (10.3, 16.6, "out-cap-4")]
XF = 5   # frames of dissolve into each cut


def main() -> None:
    (OUT / "frames").mkdir(parents=True, exist_ok=True)
    caps = [(a, b, np.asarray(Image.open(R / d / "shot.png").convert("RGBA")).astype(np.float32)) for a, b, d in CAPS]
    starts, t0 = [], 0.0
    for name, dur, _ in CUTS:
        starts.append(t0); t0 += dur
    total = int(round(t0 * FPS))
    prev_last = None
    for f in range(total):
        t = f / FPS
        i = max(k for k in range(len(CUTS)) if starts[k] <= t + 1e-6)
        name, dur, fn = CUTS[i]
        a = fn(min(dur - 1e-3, t - starts[i]))
        into = int(round((t - starts[i]) * FPS))
        if i > 0 and into < XF:
            pn, pdur, pfn = CUTS[i - 1]
            b = pfn(pdur - 1e-3)
            k = (into + 1) / (XF + 1)
            a = a * k + b * (1 - k)
        for c0, c1, ca in caps:
            if c0 <= t < c1:
                al = min(1, (t - c0) / .12, (c1 - t) / .12) * ca[..., 3:4] / 255
                a = a * (1 - al) + ca[..., :3] * al
        Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).save(OUT / "frames" / f"{f + 1:04d}.png")
        if f % 60 == 0:
            print(f"{f + 1}/{total} {name}", flush=True)
    # sound cues from the catch render, moved onto the reel clock
    ev = json.loads((R / "out-catch-a09/events.json").read_text(encoding="utf-8"))["events"]
    off = starts[1] - CATCH0 / FPS
    cues = [[round(ms / 1000 + off, 3), n, v] for ms, n, v in ev if 0 <= ms / 1000 + off < starts[2] + .5 and v > 0]
    cues += [[round(s, 3), "cut", .3] for s in starts[1:]]
    (OUT / "sfx.json").write_text(json.dumps({"cues": cues, "starts": starts, "total": t0}, ensure_ascii=False, indent=1), encoding="utf-8")
    dst = R.parent.parent / "out" / "reel-rough.mp4"
    # the app's own sounds (res/raw, Sfx.kt volumes) at the cue times; the motor runs while the claw moves
    raw = R.parent.parent / "app/app/src/main/res/raw"
    files = {"rail": "sfx_rail", "drop": "sfx_drop", "grip": "sfx_grip", "lift": "sfx_lift", "bounce": "sfx_land", "win": "sfx_win"}
    play = [(t, files[n], v, None) for t, n, v in cues if n in files]
    down = [c[0] for c in cues if c[1] == "drop"]; lift = [c[0] for c in cues if c[1] == "lift"]
    if down: play.append((down[0], "sfx_motor", .85 * .5, .25))
    if lift: play.append((lift[0], "sfx_motor", 1.0 * .5, .67))
    args = ["ffmpeg", "-v", "error", "-threads", "2", "-y", "-framerate", str(FPS), "-i", str(OUT / "frames" / "%04d.png")]
    for _, f, _, _ in play:
        args += ["-i", str(raw / f"{f}.ogg")]
    parts = []
    for k, (t, f, v, dur) in enumerate(play):
        trim = f"atrim=0:{dur}," if dur else ""
        parts.append(f"[{k + 1}:a]{trim}volume={v:.2f},adelay={int(t * 1000)}|{int(t * 1000)}[a{k}]")
    mix = ";".join(parts) + ";" + "".join(f"[a{k}]" for k in range(len(play))) + f"amix=inputs={len(play)}:normalize=0,apad,atrim=0:{t0:.3f}[aout]"
    args += ["-filter_complex", mix, "-map", "0:v", "-map", "[aout]", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18",
             "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", str(dst)]
    subprocess.run(args, check=True)
    print("wrote", dst)


if __name__ == "__main__":
    main()
