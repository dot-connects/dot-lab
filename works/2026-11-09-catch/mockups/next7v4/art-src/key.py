"""Green-screen key: fig-calico.png -> ../img/calico-figure.webp (transparent, trimmed, 320 px tall)."""
import numpy as np
from PIL import Image, ImageFilter
a = np.asarray(Image.open("fig-calico.png").convert("RGB")).astype(np.float32)
r, g, b = a[..., 0], a[..., 1], a[..., 2]
spill = g - np.maximum(r, b)                       # how green a pixel is
alpha = np.clip(1 - (spill - 30) / 90, 0, 1)        # >120 green excess = background
a[..., 1] = np.minimum(g, np.maximum(r, b) + 8)     # despill the fur edge
im = Image.fromarray(np.dstack([a, alpha * 255]).clip(0, 255).astype(np.uint8), "RGBA")
al = im.getchannel("A").filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(.8)); im.putalpha(al)
im = im.crop(im.getbbox()); im.thumbnail((400, 320), Image.LANCZOS)
im.save("../img/calico-figure.webp", quality=88); print(im.size)
