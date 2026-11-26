"""Flat sheet of out/<key>.html: python shoot.py <key> -> out/sheet-<key>.png"""
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent.parent / "r10"))
from renderlock import render_slot
from playwright.sync_api import sync_playwright
key = sys.argv[1]
css = "<style>.strip{flex-wrap:wrap;overflow:visible}.strip .pg{flex:0 0 230px}</style>"
html = (HERE / "out" / f"{key}.html").read_text(encoding="utf-8") + css
f = HERE / "out" / f"_flat-{key}.html"; f.write_text(html, encoding="utf-8")
with render_slot(), sync_playwright() as pw:
    b = pw.chromium.launch(channel="chrome"); pg = b.new_page(viewport={"width": 1500, "height": 900}, device_scale_factor=1)
    pg.goto(f.as_uri()); pg.wait_for_timeout(800)
    pg.screenshot(path=str(HERE / "out" / f"sheet-{key}.png"), full_page=True); b.close()
print("ok")
