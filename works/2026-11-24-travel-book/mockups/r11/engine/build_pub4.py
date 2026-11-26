"""7 themes x 3 trips with shared image files -> f762 scratchpad/pub11d/ (index.html, img/, fonts/, flist.json)"""
import json, re, shutil
from pathlib import Path
from PIL import Image
import engine2
HERE = Path(__file__).resolve().parent
OUT = Path(r"C:\Users\ehdck\AppData\Local\Temp\claude\c62cf31192c54cdd\f762ff8f-2127-4f5a-9e4a-aeda6102229c\scratchpad\pub11d")
APPFONTS = Path(r"C:\DC\DD\dot-lab\works\2026-11-24-travel-book\app\assets\book\fonts")
engine2._b64 = lambda k: ""
TRIPS = [("jeju", "제주 2박 3일 · 친구 둘 · 사진 30장"), ("japan", "교토·오사카 2박 3일 · 대학 친구 · 36장"), ("samerica", "남미 2주 신혼여행 · 67장")]
THEMES = ["a", "photo", "album", "b", "c", "diary", "mag"]

def latest(tag):
    fs = sorted(HERE.glob(f"out2/{tag}-[0-9].json"), key=lambda f: f.stat().st_mtime)
    return fs[-1] if fs else None

(OUT / "fonts").mkdir(parents=True, exist_ok=True)
files = {}
for f in ("InstrumentSerif-Regular.ttf", "InstrumentSerif-Italic.ttf"):
    shutil.copy(APPFONTS / f, OUT / "fonts" / f); files[f"fonts/{f}"] = 1
head = (HERE / "intro4.html").read_text(encoding="utf-8")
nav = " · ".join(f'<a href="#{t}">{l.split(" ·")[0]}</a>' for t, l in TRIPS)
parts = [head.replace("{NAV}", nav), '<style>@font-face{font-family:"Instrument Serif";src:url(fonts/InstrumentSerif-Regular.ttf)}@font-face{font-family:"Instrument Serif";font-style:italic;src:url(fonts/InstrumentSerif-Italic.ttf)}</style>']
missing = []
for t, label in TRIPS:
    trip = engine2.load_trip(t)
    (OUT / "img" / t).mkdir(parents=True, exist_ok=True)
    parts.append(f'<h1 class="trip" id="{t}">{label}</h1>')
    for k in THEMES:
        tag = k if t == "jeju" else f"{k}-{t}"
        f = latest(tag)
        if not f:
            missing.append(tag); continue
        book = json.loads(f.read_text(encoding="utf-8"))
        h = engine2.render(book, trip, k)
        h = re.sub(r"<script>\(\(\)=>\{const I=.*?</script>", "", h, flags=re.S)
        for kk in set(re.findall(r'data-k="([^"]+)"', h)):
            p = OUT / "img" / t / f"{kk}.jpg"
            if not p.exists():
                im = Image.open(engine2.engine.IMG5 / f"{kk}.webp").convert("RGB"); im.thumbnail((760, 760)); im.save(p, quality=74)
            files[f"img/{t}/{kk}.jpg"] = 1
        h = re.sub(r'data-k="([^"]+)"', lambda m: f'data-k="{m.group(1)}" loading="lazy" src="img/{t}/{m.group(1)}.jpg"', h)
        parts.append(h)
html = '<!doctype html><html lang=ko><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><title>여행 한 권 — 11차 d: 일곱 테마 × 여행 셋</title><body style="margin:0;background:#e9e7e3">' + "".join(parts) + "</body></html>"
(OUT / "index.html").write_text(html, encoding="utf-8")
(OUT / "flist.json").write_text(json.dumps([{"path": p} for p in files]), encoding="utf-8")
print("html", len(html), "files", len(files), "missing", missing)
