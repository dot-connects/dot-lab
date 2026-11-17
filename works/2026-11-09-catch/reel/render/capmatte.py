"""Caption alpha from two shots of cap.html (difference matte): python capmatte.py <dir>  (dir/black.png + dir/white.png -> dir/shot.png)."""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

d = Path(sys.argv[1])
b = np.asarray(Image.open(d / "black.png").convert("RGB")).astype(np.float32)
w = np.asarray(Image.open(d / "white.png").convert("RGB")).astype(np.float32)
a = np.clip(1 - (w - b).mean(axis=2) / 255, 0, 1)
rgb = np.where(a[..., None] > 1e-3, b / np.maximum(a[..., None], 1e-3), 0)
out = np.dstack([np.clip(rgb, 0, 255), a * 255]).astype(np.uint8)
Image.fromarray(out, "RGBA").resize((1080, 1920), Image.LANCZOS).save(d / "shot.png")
