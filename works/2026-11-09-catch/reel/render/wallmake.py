# Paints the reel's phone wallpaper: a lake at dusk (procedural, no stock people/animals). -> reel/render/wall-home.jpg
import sys, numpy as np
from PIL import Image, ImageFilter
W, H = 1080, 1920
rng = np.random.default_rng(7)
def hexc(s): return np.array([int(s[i:i+2], 16) for i in (1, 3, 5)], float)
def ramp(stops, t):   # stops [(pos, hex)], t array 0..1 -> rgb
    ps = [p for p, _ in stops]; cs = np.array([hexc(c) for _, c in stops])
    return np.stack([np.interp(t, ps, cs[:, k]) for k in range(3)], -1)
def noise1(n, cells, seed):   # smooth 1D value noise
    r = np.random.default_rng(seed); v = r.random(cells + 2); x = np.linspace(0, cells, n); i = x.astype(int); f = x - i; f = f * f * (3 - 2 * f)
    return v[i] * (1 - f) + v[i + 1] * f
def fbm1(n, seed, octs=5, base=3):
    return sum(noise1(n, base * 2 ** o, seed + o) / 2 ** o for o in range(octs)) / 2
def noise2(w, h, cx, cy, seed):
    r = np.random.default_rng(seed); small = r.random((cy + 1, cx + 1)).astype(np.float32)
    return np.asarray(Image.fromarray((small * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), float) / 255
HOR = int(H * .50)
yy = np.linspace(0, 1, HOR)
sky = ramp([(0, "#22315e"), (.35, "#44578f"), (.62, "#8f7fa8"), (.82, "#e3a39a"), (1, "#f8c493")], yy)[:, None, :].repeat(W, 1)
# soft cloud streaks, pink lit from below
cl = noise2(W, HOR, 5, 14, 3) * .6 + noise2(W, HOR, 12, 40, 4) * .4
cl = np.clip((cl - .52) * 4, 0, 1) * np.clip((yy - .15) * 2.2, 0, 1)[:, None] * .55
sky = sky * (1 - cl[..., None]) + ramp([(0, "#7a7fb0"), (1, "#ffd2b0")], yy)[:, None, :] * cl[..., None]
# sun glow low on the left
X, Y = np.meshgrid(np.arange(W), np.arange(HOR)); d = np.hypot((X - W * .30) / W, (Y - HOR * .98) / W)
sky += (np.exp(-(d / .22) ** 2) * 70)[..., None] * np.array([1, .75, .45])
img = np.zeros((H, W, 3)); img[:HOR] = sky
# ridges, far to near
ridges = [(.80, .05, "#b98fa6", 11, 3), (.85, .07, "#8f7a9c", 21, 3), (.90, .10, "#5e5f88", 31, 4), (.955, .13, "#343f68", 41, 5)]
for top, amp, col, seed, base in ridges:
    prof = HOR * (top - amp * (fbm1(W, seed, 5, base) - .25))
    if seed == 41: prof -= (noise1(W, 260, 99) ** 2) * 9 + (noise1(W, 90, 98)) * 5   # a tree line
    m = np.clip(np.arange(HOR)[:, None] - prof[None, :], 0, 1.5) / 1.5
    depth = np.clip((np.arange(HOR)[:, None] - prof[None, :]) / (HOR * .22), 0, 1)
    c = hexc(col)[None, None, :] * (1 - depth[..., None] * .0) + (hexc("#f1b9a0") - hexc(col))[None, None, :] * (depth[..., None] * .28)   # mist at the base
    img[:HOR] = img[:HOR] * (1 - m[..., None]) + c * m[..., None]
# the lake: the mirror, darker and bluer, rippled
mir = img[:HOR][::-1]
n = H - HOR; src = np.clip((np.linspace(0, 1, n) ** .8) * (HOR - 1), 0, HOR - 1).astype(int)
water = mir[src]
shift = ((noise2(W, n, 3, 160, 5) - .5) * 26 * np.linspace(.3, 1, n)[:, None]).astype(int)
water = water[np.arange(n)[:, None], np.clip(np.arange(W)[None, :] + shift, 0, W - 1)]
k = np.linspace(.25, .62, n)[:, None, None]
water = water * (1 - k) + hexc("#5f72a3")[None, None, :] * k
water *= (.93 + noise2(W, n, 2, 220, 6)[..., None] * .1)
img[HOR:] = water
img[HOR - 2:HOR + 3] = img[HOR - 2:HOR + 3] * .8 + hexc("#f3cdb4") * .2   # the shore's haze line
out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.6))
a = np.asarray(out, float) + rng.normal(0, 2.2, (H, W, 1))   # sensor grain
X, Y = np.meshgrid(np.linspace(-1, 1, W), np.linspace(-1, 1, H)); a *= (1 - .10 * (X ** 2 + Y ** 2))[..., None]   # vignette
Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).save(sys.argv[1], quality=92)
