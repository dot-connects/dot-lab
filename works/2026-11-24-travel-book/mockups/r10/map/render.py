"""Render test grids: trips x styles -> out/grid-*.jpg (one headless Chrome via renderlock.render_slot)."""
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from renderlock import render_slot
from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
KEYS = ["jeju", "seoul", "kansai", "paris", "danang", "busan"]
STYLES = [("ink", 300, 400), ("paper", 300, 400), ("plate", 300, 400), ("ink", 300, 225), ("mini", 140, 140)]

def page(keys, styles, extra="") -> str:
    trips = {k: json.loads((HERE / "out" / f"trip-{k}.json").read_text(encoding="utf-8")) for k in keys}
    return f"""<!doctype html><meta charset=utf-8><style>body{{margin:0;padding:12px;background:#ddd;font:13px sans-serif}}
.row{{display:flex;gap:10px;align-items:flex-start;margin-bottom:12px}}.row b{{width:80px}}svg{{display:block;flex:none}}</style>
<script>{(HERE / 'mapkit.js').read_text(encoding='utf-8')}</script><body><script>
const T={json.dumps(trips, ensure_ascii=False)}, S={json.dumps(styles)};
for (const k of {json.dumps(keys)}) {{ let h=`<div class=row><b>${{T[k].title}}</b>`;
  for (const [st,w,h2,lab] of S) h += drawMap(T[k], {{style:st, w, h:h2, labels: lab||'name'{extra}}});
  document.body.insertAdjacentHTML('beforeend', h+'</div>'); }}
</script>"""

def shoot(name: str, html: str) -> None:
    f = HERE / "out" / f"{name}.html"; f.write_text(html, encoding="utf-8")
    with render_slot(), sync_playwright() as pw:
        b = pw.chromium.launch(channel="chrome"); pg = b.new_page(viewport={"width": 1400, "height": 800}, device_scale_factor=1.4)
        pg.goto(f.as_uri()); pg.screenshot(path=str(HERE / "out" / f"{name}.jpg"), full_page=True, quality=82, type="jpeg"); b.close()
    print("shot", name)

if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "a"
    if which == "a": shoot("grid-a", page(KEYS[:3], STYLES))
    if which == "b": shoot("grid-b", page(KEYS[3:], STYLES))
    if which == "c": shoot("grid-c", page(KEYS, [("sea", 300, 400, "number"), ("sea", 300, 225, "number"), ("paper", 300, 225, "number"), ("plate", 200, 200, "none")]))
