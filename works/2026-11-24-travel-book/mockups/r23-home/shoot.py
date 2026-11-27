"""Screenshot index.html at 1200x900 (full page) and 412x900 with headless Chrome."""
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright

HERE = Path(__file__).parent
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE
with sync_playwright() as p:
    b = p.chromium.launch(channel="chrome")
    for w in (1200, 412):
        pg = b.new_page(viewport={"width": w, "height": 900}, device_scale_factor=1)
        pg.goto((HERE / "index.html").as_uri())
        pg.wait_for_timeout(2800)
        pg.screenshot(path=str(OUT / f"shot-{w}.png"), full_page=True)
        if w == 1200:
            for i, el in enumerate(pg.query_selector_all(".live")):
                el.screenshot(path=str(OUT / f"live-{i}.png"))
            for i, el in enumerate(pg.query_selector_all(".live [class^=sw]")):
                el.click()
            pg.wait_for_timeout(1500)
            for i, el in enumerate(pg.query_selector_all(".live")):
                el.screenshot(path=str(OUT / f"live-tl-{i}.png"))
            for i, el in enumerate(pg.query_selector_all(".minis")):
                el.screenshot(path=str(OUT / f"minis-{i}.png"))
    b.close()
