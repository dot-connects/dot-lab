import re
from pathlib import Path
HERE = Path(__file__).resolve().parent
def body(k):
    h = (HERE / "out" / f"{k}.html").read_text(encoding="utf-8")
    h = h.split("<body", 1)[1].split(">", 1)[1].rsplit("</body>", 1)[0]
    return re.sub(r"<h2>([^<(]*)\(시도 \d\)</h2>", r"<h2>\1</h2>", h)
intro = (HERE / "intro.html").read_text(encoding="utf-8")
html = f"""<!doctype html><html lang=ko><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>여행 한 권 — 테마 시안 11차</title><body style="margin:0;background:#e9e7e3">{intro}{body('photo')}{body('a')}{body('album')}</body></html>"""
out = Path(r"C:\Users\ehdck\AppData\Local\Temp\claude\c62cf31192c54cdd\f762ff8f-2127-4f5a-9e4a-aeda6102229c\scratchpad\pub11\index.html")
out.parent.mkdir(exist_ok=True); out.write_text(html, encoding="utf-8"); print(len(html))
