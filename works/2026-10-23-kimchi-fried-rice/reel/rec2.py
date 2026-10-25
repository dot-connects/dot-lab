"""Reel take v3: 390x844 at dpr 2.5 (975 px wide panel), reel-only CSS, per-frame log incl. flicks and the visible word."""
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from rec import SITE, INIT, SYNC_CSS
from bot import BOT
from playwright.sync_api import sync_playwright

CSS = """#mute,#home,#tip,#sub,#stats,#scSmall,.open,.credit{display:none!important}
#gauge::after{display:none!important}
.word{bottom:41.5vh!important}"""
FLICK = """(()=>{const G=KFR.Game,f=G.flick;window.__fl=0;G.flick=(o)=>{window.__fl=(o&&o.strength)||1;return f(o)}})()"""
LOG = """[KFR.Game.state.ph,+KFR.Game.state.clock.toFixed(2),KFR.Game.state.darkPh||'',KFR.Game.state.combo,KFR.Game.state.tossN,KFR.Egg.ph,!!KFR.Game.state.slowT,window.__fl,document.getElementById('word').textContent,(document.querySelector('#scBig')||{}).textContent||'']"""

out = Path(sys.argv[1]); out.mkdir(exist_ok=True); n = int(float(sys.argv[2]) * 30)
for f in out.glob("f*.png"): f.unlink()
with sync_playwright() as p:
    br = p.chromium.launch(channel="chrome")
    pg = br.new_page(viewport={"width": 390, "height": 844}, device_scale_factor=2.5)
    errs = []; pg.on("pageerror", lambda e: errs.append(str(e))); pg.add_init_script(INIT)
    pg.goto(SITE + "?ts=0.0001"); pg.wait_for_timeout(1500)
    pg.add_style_tag(content=CSS); pg.evaluate(BOT); pg.evaluate(FLICK)
    log = open(out / "timeline.jsonl", "w", encoding="utf-8")
    for i in range(n):
        gt = i / 30
        pg.evaluate("window.__fl=0; window.__bot && window.__bot()"); pg.evaluate("KFR.Game.tick(2)"); pg.evaluate("(gt)=>window.__run(gt)", gt)
        log.write(json.dumps([i] + pg.evaluate(LOG), ensure_ascii=False) + "\n"); log.flush()
        pg.evaluate(SYNC_CSS, gt)
        pg.screenshot(path=str(out / f"f{i:04d}.png"))
    out.joinpath("result.json").write_text(json.dumps(pg.evaluate("({score:KFR.Game.finalScore()})")), encoding="utf-8")
    br.close()
print("errors", errs[:3], "frames", n)
