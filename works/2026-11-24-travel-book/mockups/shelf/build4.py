"""src4.html -> travel-shelf-all.html. Each photo is embedded once (JS map fills img[data-k]) so 46 frames stay small."""
import base64
import io
import re
from pathlib import Path

from PIL import Image

H = Path(__file__).parent
GIF = "data:image/gif;base64,R0lGODlhAQABAAAAACwAAAAAAQABAAA="


def data(k: str) -> str:
    pp = k.startswith("pp_")  # pp_a_0 -> mock/r5/pp-a-0.png (real theme page renders)
    f = H / "../mock/r5" / (k.replace("_", "-") + ".png") if pp else H / "../mock/img5" / (k + ".webp")
    im = Image.open(f).convert("RGB")
    im.thumbnail((400, 400))
    b = io.BytesIO()
    im.save(b, "JPEG", quality=80 if pp else 72)
    return "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode()


s = (H / "src4.html").read_text(encoding="utf-8")
keys = sorted(set(re.findall(r'src="\{\{(\w+)\}\}"', s)))
s = re.sub(r'src="\{\{(\w+)\}\}"', lambda m: f'data-k="{m.group(1)}" src="{GIF}"', s)
assert "{{" not in s, re.findall(r"\{\{\w+\}\}", s)[:5]
s += "\n<script>const IMG={" + ",".join(f'"{k}":"{data(k)}"' for k in keys) + '};document.querySelectorAll("img[data-k]").forEach(i=>{i.src=IMG[i.dataset.k]});</script>\n'
(H / "travel-shelf-all.html").write_text(s, encoding="utf-8")
print(len(keys), "images,", len(s.encode("utf-8")) // 1024, "KB")
