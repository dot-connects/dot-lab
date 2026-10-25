"""Reel take v3: 390x844 at dpr 2.5 (975 px wide panel), reel-only CSS, per-frame log incl. flicks and the visible word."""
import io, json, sys
from PIL import Image
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from rec import SITE, INIT, SYNC_CSS
from bot import BOT
from playwright.sync_api import sync_playwright

CSS = """#mute,#home,#tip,#sub,#stats,#scSmall,.open,.credit{display:none!important}
#gauge::after{display:none!important}
.word{bottom:41.5vh!important}"""
CLEAN_CSS = """#gauge,#beat,#combo,.num,#startCue,#hud:not([data-ph=rice]) #timer{display:none!important}
#intro{opacity:0!important}"""
FLICK = """(()=>{const G=KFR.Game,f=G.flick;window.__fl=0;G.flick=(o)=>{window.__fl=(o&&o.strength)||1;return f(o)}})()"""
LOG = """[KFR.Game.state.ph,+KFR.Game.state.clock.toFixed(2),KFR.Game.state.darkPh||'',KFR.Game.state.combo,KFR.Game.state.tossN,KFR.Egg.ph,!!KFR.Game.state.slowT,window.__fl,document.getElementById('word').textContent,(document.querySelector('#scBig')||{}).textContent||'',KFR.Food.spilled]"""

out = Path(sys.argv[1]); out.mkdir(exist_ok=True); n = int(float(sys.argv[2]) * 30)
SPILL = sys.argv[3] if len(sys.argv) > 3 else ""  # 4th flick sideways: 'side,strength' (v4 spill beat)
SEED = int(sys.argv[4]) if len(sys.argv) > 4 else 0  # seeded Math.random: takes repeat only roughly (probe4 vs take4 parted at 5 s), so read timeline.jsonl after a take
PROBE = len(sys.argv) > 5 and sys.argv[5] == "probe"
BLUR = "blur" in sys.argv[5:]
GO_AT = 1.4 if "title" in sys.argv[5:] else 0  # hold the title card (its own attract tosses) before the tap
CLEAN = "clean" in sys.argv[5:]  # v7: only the timer and the big words; no gauge, beat bar, combo, +/- pops, cue bubbles, title card
RNG = "(()=>{let a=%d>>>0;Math.random=()=>{a=(a+0x6D2B79F5)>>>0;let t=a;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return((t^t>>>14)>>>0)/4294967296}})();"
for f in out.glob("f*.png"): f.unlink()
with sync_playwright() as p:
    br = p.chromium.launch(channel="chrome")
    pg = br.new_page(viewport={"width": 390, "height": 844}, device_scale_factor=2.5)
    errs = []; pg.on("pageerror", lambda e: errs.append(str(e))); pg.add_init_script(INIT + (RNG % SEED if SEED else ""))
    pg.goto(SITE + "?ts=0.0001"); pg.wait_for_timeout(1500)
    # goAt and spill before BOT: evaluate() calls the returned bot once right away
    pg.add_style_tag(content=CSS + (CLEAN_CSS if CLEAN else "")); pg.evaluate(f"window.__goAt={GO_AT}"); SPILL and pg.evaluate(f"window.__spill=[{SPILL}]"); pg.evaluate(BOT); pg.evaluate(FLICK)
    log = open(out / "timeline.jsonl", "w", encoding="utf-8")
    for i in range(n):
        gt = i / 30
        pg.evaluate("window.__fl=0; window.__bot && window.__bot()")
        if BLUR:  # two 1/60 s shots averaged: the fast wok moves get motion blur instead of jumping
            pg.evaluate("KFR.Game.tick(1)"); pg.evaluate("(gt)=>window.__run(gt)", gt - 1 / 60)
            if not PROBE: pg.evaluate(SYNC_CSS, gt - 1 / 60); half = Image.open(io.BytesIO(pg.screenshot())).convert("RGB")
            pg.evaluate("KFR.Game.tick(1)")
        else:
            pg.evaluate("KFR.Game.tick(2)")
        pg.evaluate("(gt)=>window.__run(gt)", gt)
        log.write(json.dumps([i] + pg.evaluate(LOG), ensure_ascii=False) + "\n"); log.flush()
        if PROBE: continue
        pg.evaluate(SYNC_CSS, gt)
        if BLUR: Image.blend(half, Image.open(io.BytesIO(pg.screenshot())).convert("RGB"), 0.5).save(out / f"f{i:04d}.png")
        else: pg.screenshot(path=str(out / f"f{i:04d}.png"))
    out.joinpath("result.json").write_text(json.dumps(pg.evaluate("({score:KFR.Game.finalScore()})")), encoding="utf-8")
    br.close()
print("errors", errs[:3], "frames", n)
