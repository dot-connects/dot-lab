"""Frame-exact gameplay capture for the reel.

The game runs frozen (?ts=0.0001); each video frame = KFR.Game.tick(2) (2 fixed steps = 1/30 s of game time), then a
screenshot. CSS animations (word pops, cue, splat) are paused and set to their game-time position so they play at the
same speed as the game. Actions are JS snippets fired at game-time offsets from 'go'.

record(out_dir, seconds, actions, url_extra="", before=None) -> number of frames
"""
import json, time
from pathlib import Path
from playwright.sync_api import sync_playwright

SITE = Path(r"C:\DC\DD\dot-lab\works\2026-10-23-kimchi-fried-rice\site\index.html").as_uri()
INIT = """window.__cap=true;window.__gt=0;window.__q=[];const __st=window.setTimeout.bind(window);
window.setTimeout=(fn,ms,...a)=>{if(!window.__cap||typeof fn!=='function')return __st(fn,ms,...a);window.__q.push({at:window.__gt+(ms||0)/1000,fn:()=>fn(...a)});return 0};
window.__run=(gt)=>{window.__gt=gt;const due=window.__q.filter(x=>x.at<=gt);window.__q=window.__q.filter(x=>x.at>gt);due.forEach(x=>{try{x.fn()}catch(e){}})};"""
SYNC_CSS = """(gt)=>{window.__as=window.__as||new Map();for(const a of document.getAnimations()){if(!window.__as.has(a))window.__as.set(a,gt);
  a.pause();a.currentTime=Math.max(0,(gt-window.__as.get(a))*1000);}}"""


def record(out_dir, seconds, actions, url_extra="", before=None, w=360, h=640, dpr=3, phase=None):
    out = Path(out_dir); out.mkdir(parents=True, exist_ok=True)
    for f in out.glob("f*.png"): f.unlink()
    with sync_playwright() as p:
        br = p.chromium.launch(channel="chrome")
        pg = br.new_page(viewport={"width": w, "height": h}, device_scale_factor=dpr)
        errs = []; pg.on("pageerror", lambda e: errs.append(str(e))); pg.add_init_script(INIT)
        pg.goto(SITE + "?ts=0.0001" + url_extra); pg.wait_for_timeout(1500)
        pg.add_style_tag(content="#mute,#home{display:none!important}")
        if before: before(pg)
        acts = sorted(actions, key=lambda a: a[0]); ai = 0; log = open(out / "timeline.jsonl", "w")
        n = int(seconds * 30)
        t0 = pg.evaluate("KFR.Game.state.t")
        for i in range(n):
            gt = i / 30
            while ai < len(acts) and acts[ai][0] <= gt + 1e-9:
                pg.evaluate(acts[ai][1]); ai += 1
            pg.evaluate("window.__bot && window.__bot()"); pg.evaluate("KFR.Game.tick(2)"); pg.evaluate("(gt)=>window.__run(gt)", gt)
            log.write(json.dumps([i] + pg.evaluate("[KFR.Game.state.ph, +KFR.Game.state.clock.toFixed(2), KFR.Game.state.darkPh||'', KFR.Game.state.combo, KFR.Game.state.tossN, KFR.Egg.ph, !!KFR.Game.state.slowT, !!KFR.Game.state.fever && KFR.Game.state.t < KFR.Game.state.fever]")) + "\n"); log.flush()
            pg.evaluate(SYNC_CSS, gt)
            pg.screenshot(path=str(out / f"f{i:04d}.png"))
        out.joinpath("result.json").write_text(json.dumps(pg.evaluate("({score:KFR.Game.finalScore(), receipt:(document.querySelector('.slip')||{}).innerText})"), ensure_ascii=False), encoding="utf-8")
        br.close()
    if errs: print("page errors", errs[:3])
    return n


if __name__ == "__main__":
    import sys
    n = record(sys.argv[1], 3, [(0.2, "document.getElementById('go').click()")])
    print(n)
