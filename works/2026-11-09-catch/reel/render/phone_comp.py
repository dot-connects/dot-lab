"""Put rendered phone-screen frames into the C01 green-screen phone (reel beat: the pet on the home screen).

python reel/render/phone_comp.py <c01 frames dir> <screen frames dir> <out dir> [n]

Per frame: key the green (the app's own Sheet.keyGreen thresholds: k = g - max(r, b), > 60 clear, 18..60 edge),
find the screen quad from the green mask's extreme corners (smoothed over 5 frames), warp the screen frame into it
(a hair larger, the key trims it to the glass), pull green spill out of the hand and bezel, add a faint glass glare.
Writes 1080 x 1920 PNGs. Thumb and fingers over the glass stay in front because they are not green.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

OUT_W, OUT_H = 1080, 1920


def corners(img: np.ndarray) -> np.ndarray:
    k = img[..., 1].astype(int) - np.maximum(img[..., 0], img[..., 2]).astype(int)
    ys, xs = np.nonzero(k > 60)
    s, d = xs + ys, xs - ys
    return np.array([[xs[s.argmin()], ys[s.argmin()]], [xs[d.argmax()], ys[d.argmax()]],
                     [xs[s.argmax()], ys[s.argmax()]], [xs[d.argmin()], ys[d.argmin()]]], float)   # tl tr br bl


def grow(q: np.ndarray, by: float) -> np.ndarray:
    c = q.mean(axis=0)
    return c + (q - c) * (1 + by)


def coeffs(dst: np.ndarray, w: int, h: int) -> list[float]:
    """PIL PERSPECTIVE coefficients mapping output pixels (dst quad) back to the source rectangle w x h."""
    src = np.array([[0, 0], [w, 0], [w, h], [0, h]], float)
    a, b = [], []
    for (x, y), (u, v) in zip(dst, src):
        a.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); b.append(u)
        a.append([0, 0, 0, x, y, 1, -v * x, -v * y]); b.append(v)
    return list(np.linalg.solve(np.array(a), np.array(b)))


def main() -> None:
    cdir, sdir, odir = map(Path, sys.argv[1:4])
    odir.mkdir(parents=True, exist_ok=True)
    cfr = sorted(cdir.glob("*.jpg")) or sorted(cdir.glob("*.png"))
    sfr = sorted(sdir.glob("[0-9]*.png"))
    n = int(sys.argv[4]) if len(sys.argv) > 4 else min(len(cfr), len(sfr))
    imgs = [np.asarray(Image.open(f).convert("RGB").resize((OUT_W, OUT_H), Image.LANCZOS)) for f in cfr[:n]]
    qs = np.array([corners(i) for i in imgs])
    smooth = np.array([qs[max(0, i - 2):i + 3].mean(axis=0) for i in range(n)])
    yy, xx = np.mgrid[0:OUT_H, 0:OUT_W]
    for i in range(n):
        bg = imgs[i].astype(np.float32)
        k = bg[..., 1] - np.maximum(bg[..., 0], bg[..., 2])
        a = np.clip((k - 18) / 42, 0, 1)[..., None]
        # spill: where green leaks into skin and bezel, pull it down to max(r, b)
        spill = (k > 0) & (a[..., 0] < 1)
        bg[..., 1] = np.where(spill, np.maximum(bg[..., 0], bg[..., 2]), bg[..., 1])
        scr = Image.open(sfr[min(i, len(sfr) - 1)]).convert("RGB")
        q = grow(smooth[i], 0.02)
        warped = np.asarray(scr.transform((OUT_W, OUT_H), Image.PERSPECTIVE, coeffs(q, *scr.size), Image.BICUBIC)).astype(np.float32)
        # glass glare: a soft diagonal sheen across the upper screen
        tl = q[0]
        diag = ((xx - tl[0]) * 0.55 + (yy - tl[1])) / max(1.0, q[2][1] - tl[1])
        glare = (np.clip(1 - np.abs(diag - 0.18) / 0.16, 0, 1) * 0.10)[..., None]
        warped = warped + (255 - warped) * glare
        out = bg * (1 - a) + warped * a
        Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).save(odir / f"{i + 1:04d}.png")
        if i % 30 == 0:
            print(f"{i + 1}/{n}", flush=True)


if __name__ == "__main__":
    main()
