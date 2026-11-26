import re
from pathlib import Path
HERE = Path(__file__).resolve().parent
LIB = HERE.parent / "lib"
def book(k):
    h = (HERE / "out2" / f"{k}.html").read_text(encoding="utf-8")
    h = h.split("<body", 1)[1].split(">", 1)[1].rsplit("</body>", 1)[0]
    return re.sub(r"<h2>([^<(]*)\(시도 \d\)</h2>", r"<h2>\1</h2>", h)
def lib(k):
    h = (LIB / k / "preview.html").read_text(encoding="utf-8")
    h = re.sub(r"<!doctype html>.*?<style>", "<style>", h, count=1, flags=re.S)
    h = re.sub(r"body\{[^}]*\}", "", h).replace(".cell", ".lc").replace('class="cell"', 'class="lc"')
    h = h.replace("document.querySelectorAll('img[data-k]')", f"document.querySelectorAll('.libwrap-{k} img[data-k]')")
    h = h.replace("const I=", f"const I_{k}=").replace("i.src=I[", f"i.src=I_{k}[")
    return f'<details class="libwrap-{k}" style="margin:0 12px 36px"><summary style="font:600 15px sans-serif;cursor:pointer;padding:6px 0">틀 목록 보기 ({k})</summary>{h}</details>'
intro = (HERE / "intro2.html").read_text(encoding="utf-8")
html = f"""<!doctype html><html lang=ko><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>여행 한 권 — 테마 시안 11차 b</title><body style="margin:0;background:#e9e7e3">{intro}""" + "".join(book(k) + lib(k) for k in ("photo", "a", "album")) + "</body></html>"
out = Path(r"C:\Users\ehdck\AppData\Local\Temp\claude\c62cf31192c54cdd\f762ff8f-2127-4f5a-9e4a-aeda6102229c\scratchpad\pub11b\index.html")
out.parent.mkdir(exist_ok=True); out.write_text(html, encoding="utf-8"); print(len(html))
