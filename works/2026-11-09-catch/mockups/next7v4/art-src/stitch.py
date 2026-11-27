"""pano-a + pano-b -> ../img/pano.webp: one seamless 360 ring strip, horizon at 52% (SceneSource.HORIZON)."""
import numpy as np
from PIL import Image
H, ABOVE, BELOW, X = 1000, 520, 480, 260   # X = cross-fade width at each joint
def fit(f, hz):
    im = Image.open(f).convert("RGB"); w, h = im.size
    s = max(ABOVE / hz, BELOW / (h - hz))
    im = im.resize((round(w * s), round(h * s)), Image.LANCZOS)
    top = round(hz * s) - ABOVE
    return np.asarray(im.crop((24, top, im.width - 24, top + H)), dtype=np.float32)   # Codex edges carry a dark column
a, b = fit("pano-a.png", 385), fit("pano-b.png", 525)
# match b's sky and floor colour to a's (mean per band)
for y0, y1 in ((0, ABOVE - 60), (ABOVE + 40, H)):
    b[y0:y1] += a[y0:y1].mean((0, 1)) - b[y0:y1].mean((0, 1))
# blend the band between them too so the shift has no hard row edge
def xfade(l, r):
    t = np.linspace(0, 1, X)[None, :, None]
    return np.concatenate([l[:, :-X], l[:, -X:] * (1 - t) + r[:, :X] * t, r[:, X:]], 1)
ring = xfade(a, b)                      # a | b
# wrap joint: b's right end into a's left end
t = np.linspace(0, 1, X)[None, :, None]
ring[:, :X] = ring[:, -X:] * (1 - t) + ring[:, :X] * t
ring = ring[:, :-X]
Image.fromarray(np.clip(ring, 0, 255).astype(np.uint8)).save("../img/pano.webp", quality=82)
print(ring.shape)
