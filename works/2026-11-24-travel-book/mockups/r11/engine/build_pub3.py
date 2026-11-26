"""9 books (3 themes x 3 trips) with images as files: -> f762 scratchpad/pub11c/index.html + img/<trip>/<k>.jpg"""
import json, re, sys
from pathlib import Path
from PIL import Image
import engine2
HERE = Path(__file__).resolve().parent
OUT = Path(r"C:\Users\ehdck\AppData\Local\Temp\claude\c62cf31192c54cdd\f762ff8f-2127-4f5a-9e4a-aeda6102229c\scratchpad\pub11c")
engine2._b64 = lambda k: ""
TRIPS = [("jeju", "제주 2박 3일 · 친구 둘 · 사진 30장"), ("japan", "교토·오사카 2박 3일 · 대학 친구 · 사진 36장"), ("samerica", "남미 2주 신혼여행 · 사진 67장")]
BOOKS = {"photo": {}, "a": {}, "album": {"samerica": 2}}
intro = (HERE / "intro3.html").read_text(encoding="utf-8")
parts, files = [intro], {}
for t, label in TRIPS:
    trip = engine2.load_trip(t)
    (OUT / "img" / t).mkdir(parents=True, exist_ok=True)
    parts.append(f'<h1 class="trip" id="{t}">{label}</h1>')
    for k in ("photo", "a", "album"):
        n = BOOKS[k].get(t, 1)
        tag = k if t == "jeju" else f"{k}-{t}"
        book = json.loads((HERE / "out2" / f"{tag}-{n}.json").read_text(encoding="utf-8"))
        h = engine2.render(book, trip, k)
        h = re.sub(r"<script>\(\(\)=>\{const I=.*?</script>", "", h, flags=re.S)
        for kk in set(re.findall(r'data-k="([^"]+)"', h)):
            f = OUT / "img" / t / f"{kk}.jpg"
            if not f.exists():
                im = Image.open(engine2.engine.IMG5 / f"{kk}.webp").convert("RGB"); im.thumbnail((760, 760)); im.save(f, quality=74)
            files[f"img/{t}/{kk}.jpg"] = str(f)
        h = re.sub(r'data-k="([^"]+)"', lambda m: f'data-k="{m.group(1)}" loading="lazy" src="img/{t}/{m.group(1)}.jpg"', h)
        parts.append(h)
html = '<!doctype html><html lang=ko><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><title>여행 한 권 — 11차 c: 여행 셋 × 테마 셋</title><body style="margin:0;background:#e9e7e3">' + "".join(parts) + "</body></html>"
(OUT / "index.html").write_text(html, encoding="utf-8")
json.dump(files, open(OUT / "files.json", "w"), indent=0)
print(len(html), len(files), sum(Path(v).stat().st_size for v in files.values()))
