"""White tutorial-style hand from the operator's pinch photo: the real outline, flat fill, a sleeve that runs on
far past the frame (operator 11-07: the photo hand cut off at the wrist looked odd). Same pixel origin as
hand2_sam.png, so reel.js PX/PY still mark the pinch point.
Run: uv run --with numpy --with pillow python silhouette.py   (writes assets/hand_white.png)"""
import pathlib
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = pathlib.Path(__file__).parent
SRC = HERE / "assets" / "hand2_sam.png"
OUT = HERE / "assets" / "hand_white.png"
im = Image.open(SRC).convert("RGBA"); a = np.asarray(im).astype(int)
r, g, b, al = a[..., 0], a[..., 1], a[..., 2], a[..., 3]
mask = (al > 128) & ~((r - b > 110) & (r > 150))          # drop the candy shard between the fingers
W, H = 2600, 2400                                          # room for the sleeve to run off any frame
m = Image.new("L", (W, H), 0); m.paste(Image.fromarray((mask * 255).astype(np.uint8)), (0, 0))
# the sleeve: continue the cuff down-right along the forearm, a little wider toward the elbow
d = np.array([0.80, 0.60]); d /= np.hypot(*d); n = np.array([-d[1], d[0]])
top, bot = np.array([548, 604]), np.array([432, 748])
far = 2200
ImageDraw.Draw(m).polygon([tuple(top), tuple(top + d * far - n * 170), tuple(bot + d * far + n * 150), tuple(bot)], fill=255)
# the candy shard hid the index fingertip in the photo: carry the finger on, tapering, until its tip meets the thumb's
def capsule(dr, a, ra, b, rb, steps=24):
    for i in range(steps + 1):
        t = i / steps; x = a[0] + (b[0] - a[0]) * t; y = a[1] + (b[1] - a[1]) * t; r = ra + (rb - ra) * t
        dr.ellipse([x - r, y - r, x + r, y + r], fill=255)
capsule(ImageDraw.Draw(m), (205, 191), 23, (150, 199), 16)
# clean edges: drop specks, then round the outline
m = m.filter(ImageFilter.MedianFilter(9)).filter(ImageFilter.GaussianBlur(3)).point(lambda v: 255 if v > 128 else 0)
m = m.filter(ImageFilter.GaussianBlur(1.2))
M = np.asarray(m).astype(float) / 255
# sleeve region (beyond the cuff line) gets a cooler grey so hand and sleeve read apart
yy, xx = np.mgrid[0:H, 0:W]
cuff = ((xx - 432) * (top[1] - bot[1]) - (yy - bot[1]) * (top[0] - bot[0])) < 0
rgb = np.zeros((H, W, 3)); rgb[...] = (250, 250, 252); rgb[cuff] = (214, 218, 228)
# one soft shade on the far (lower-left) side so the hand has volume without looking like a photo
sh = np.asarray(m.filter(ImageFilter.GaussianBlur(26))).astype(float) / 255
rim = np.clip((1 - sh) * 2.2, 0, 1) * M
off = np.roll(np.roll(M, -14, 0), 10, 1)                  # light from the top-right
shade = np.clip(M - off, 0, 1)
shade = np.asarray(Image.fromarray((shade * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(10))).astype(float) / 255
rgb = rgb * (1 - 0.16 * np.maximum(shade, rim * 0.5))[..., None]
# finger separation: strong luminance edges from the photo, faint
lum = Image.fromarray(((r * .3 + g * .59 + b * .11) * (al > 0)).astype(np.uint8)).filter(ImageFilter.GaussianBlur(4)).filter(ImageFilter.FIND_EDGES)
e = np.zeros((H, W)); E = np.asarray(lum).astype(float); e[:E.shape[0], :E.shape[1]] = E
e = ~cuff * np.clip((e - 5) / 5, 0, 1) * (np.asarray(m.filter(ImageFilter.MinFilter(15))).astype(float) / 255)
e = np.asarray(Image.fromarray((e * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1))).astype(float) / 255
# two drawn creases instead of photo edges (those came out as specks): middle finger over index, thumb into palm
ln = Image.new("L", (W, H), 0); dr = ImageDraw.Draw(ln)
dr.line([(212, 170), (240, 166), (290, 168), (350, 182), (410, 208)], fill=255, width=6, joint="curve")
dr.line([(214, 318), (232, 352), (262, 380)], fill=255, width=6, joint="curve")
# where the index tip lies on the thumb tip: the index's underside and tip, drawn like an outline so the two read apart
seam = Image.new("L", (W, H), 0)
ImageDraw.Draw(seam).line([(182, 217), (162, 216), (146, 213), (137, 205), (135, 196), (139, 187)], fill=255, width=5, joint="curve")
seam = np.asarray(seam.filter(ImageFilter.GaussianBlur(1.0))).astype(float) / 255
ln = np.asarray(ln.filter(ImageFilter.GaussianBlur(1.6))).astype(float) / 255
rgb = rgb * (1 - 0.22 * ln)[..., None]
rgb = rgb * (1 - seam[..., None]) + np.array([150, 154, 164]) * seam[..., None]
# thin grey outline
edge = np.asarray(m.filter(ImageFilter.MaxFilter(5))).astype(float) / 255
outl = np.clip(edge - M, 0, 1)
out = np.dstack([rgb * M[..., None] + np.array([150, 154, 164]) * outl[..., None], np.clip(M + outl * 0.9, 0, 1) * 255])
Image.fromarray(out.astype(np.uint8), "RGBA").save(OUT)
print("wrote", OUT)
