"""Inline photos (base64 webp) + their muted average colour into template.html -> index.html."""
import base64
import io
import json
from pathlib import Path

from PIL import Image, ImageStat

HERE = Path(__file__).parent


def main() -> None:
    imgs: dict[str, dict[str, str]] = {}
    for p in sorted((HERE.parent / "r24-home" / "photos").iterdir()):
        im = Image.open(p).convert("RGB")
        im.thumbnail((560, 560))
        buf = io.BytesIO()
        im.save(buf, "WEBP", quality=74)
        r, g, b = ImageStat.Stat(im.resize((32, 32))).mean
        # muted: pull towards grey so spines / ambient light stay pale
        m = (r + g + b) / 3
        r, g, b = (int(m + (x - m) * 0.6) for x in (r, g, b))
        imgs[p.stem] = {"src": "data:image/webp;base64," + base64.b64encode(buf.getvalue()).decode(), "col": f"rgb({r},{g},{b})"}
    credits = json.loads((HERE.parent / "r24-home" / "credits.json").read_text(encoding="utf-8"))
    html = (HERE / "template.html").read_text(encoding="utf-8")
    html = html.replace("/*IMG*/{}", json.dumps(imgs)).replace("/*CREDITS*/{}", json.dumps(credits, ensure_ascii=False))
    (HERE / "index.html").write_text(html, encoding="utf-8")
    print("index.html", len(html) // 1024, "KB")


if __name__ == "__main__":
    main()
