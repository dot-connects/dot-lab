"""Caption-free stills from the real AR page for the reel storyboard (out/stills/*.jpg, 360x640).

Run: uv run --with playwright --with pillow python stills.py
Reuses render.py's driver (everything above its main()), so the shots match what the reel render will show.
"""
import asyncio, io, math, pathlib, subprocess, sys
from PIL import Image

HERE = pathlib.Path(__file__).parent
src = (HERE / "render.py").read_text(encoding="utf-8")
exec(src[: src.index("async def main")])   # Reel, VCLOCK, URL, VW/VH/DSF, idle priority + 2-core affinity

SOUT = HERE / "out" / "stills"; SOUT.mkdir(parents=True, exist_ok=True)


async def shot(pg, name):
    png = await pg.screenshot()
    Image.open(io.BytesIO(png)).convert("RGB").resize((360, 640), Image.LANCZOS).save(SOUT / f"{name}.jpg", quality=86)
    print("shot", name, flush=True)


async def knock(r, until):
    ang = 1.95
    for _ in range(40):
        left = await r.js("(() => { const F = __ar.S.F; let n = 0; for (let i = 0; i < F.N * F.N; i++) if (F.alive[i] && !F.inside[i]) n++; return n; })()")
        if left < until or await r.js("__ar.S.ended"): break
        q = await r.scr(0.88 * math.cos(ang), 0.88 * math.sin(ang)); ang += 0.6
        await r.move(*q, 2); await r.js("__ar.S.lastHit = 0"); await r.pg.mouse.down(); await r.frame(2); await r.pg.mouse.up(); await r.frame(10)


async def main():
    srv = subprocess.Popen([sys.executable, "-m", "http.server", "8765", "--bind", "127.0.0.1"], cwd=str(HERE.parents[2]),
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        async with async_playwright() as p:
            b = await p.chromium.launch(channel="chrome", args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
            ctx = await b.new_context(viewport={"width": VW, "height": VH}, device_scale_factor=DSF)
            pg = await ctx.new_page(); pg.on("pageerror", lambda e: print("ERR", e, flush=True))
            await pg.add_init_script(VCLOCK)
            await pg.goto(URL); await pg.wait_for_timeout(1500)
            await pg.evaluate("__step(500); document.getElementById('touch').click(); document.getElementById('shStar').click()")
            r = Reel(pg); r.rec = False
            r.alpha, r.beta = 34, 50; await r.frame(40)
            await pg.wait_for_timeout(1200); await r.frame(5)
            r.alpha = 0; await r.frame(10); await r.js("__ar.place()"); await r.frame(30)
            for _ in range(60):
                if await r.js("__reel.ready()"): break
                await pg.wait_for_timeout(250)
            await r.js("__reel.cap(''); __reel.timer(0)"); await r.frame(20)
            await shot(pg, "candy")
            await r.look(30, 95, 20); await r.frame(10); await shot(pg, "guard")
            await r.look(0, 50, 20); await r.js("__reel.timer(1)")
            # needle, mid-stroke
            await r.js("__ar.pickTool('needle')"); r.hand_on = True; r.zk = r.zt = 1.3
            r.zo = tuple(await r.js("__ar.screenOf(0, 0)"))
            path = await r.outline(0, 60, 60)
            await r.trace(path[0], 6); r.hk = 0.95; await r.press(True)
            for q in path[1:]: await r.trace(q)
            await shot(pg, "needle")
            await r.press(False); r.hk = 1.0
            # groove done off camera, then the hammer at the rim
            await r.js("__ar.carveAll(0.97); __ar.pickTool('hammer')"); r.zk = r.zt = 1.15
            for th in (0.15, 0.75):
                q = await r.scr(0.88 * math.cos(th), 0.88 * math.sin(th)); await r.move(*q, 6)
                await r.js("__ar.S.lastHit = 0"); r.hk = 0.94; await pg.mouse.down(); await r.frame(3); await pg.mouse.up(); r.hk = 1.0; await r.frame(9)
            q = await r.scr(0.88 * math.cos(1.35), 0.88 * math.sin(1.35)); await r.move(*q, 6); r.hk = 0.94; await r.frame(1)
            await shot(pg, "hammer"); r.hk = 1.0
            # nearly out: hover over the tip of a star arm
            await knock(r, 2000); print("ended before slip", await r.js("__ar.S.ended"), flush=True); await r.js("__ar.WT.timeLeft = 9")
            tip = await r.js("__ar.S.pts.reduce((m, p) => Math.hypot(...p) > Math.hypot(...m) ? p : m)")
            near = (tip[0] * 1.3, tip[1] * 1.3)
            await r.trace(near, 10); await r.frame(4); await shot(pg, "almost")
            # the slip: the hammer lands on the arm (struck directly at candy coords, so the pointer offset can't miss)
            await r.trace((tip[0] * 0.6, tip[1] * 0.6), 3); r.hk = 0.94
            sx, sy = tip[0] * 0.55, tip[1] * 0.55
            await r.js(f"__ar.S.lastHit = 0; __ar.hit({{ onCandy: true, sx: {sx}, sy: {sy}, px: {(sx + 1) * 256}, py: {(sy + 1) * 256} }})")
            await r.frame(2); r.hk = 1.0
            print("doom", await r.js("[!!__ar.WT.doom, document.getElementById('toast').textContent]"), flush=True)
            await r.frame(6); await shot(pg, "snap")
            # look up: he holds, racks, aims
            await r.frame(40)
            await r.look(32, 108, 20, hand=(0, 900)); r.hand_on = False
            for _ in range(40):
                await r.frame()
                d = await r.js("(() => { const D = __ar.WT.doom; return D && D.seen ? D.t - D.seen : -1; })()")
                if d > 1.2: break
            await shot(pg, "aim")
            for _ in range(14):
                await r.frame()
                if await r.js("__ar.WT.doom.fired"): break
            await shot(pg, "fire")
            await pg.wait_for_timeout(2600); await r.frame(3); await shot(pg, "dead")
            await b.close()
    finally:
        srv.terminate()

asyncio.run(main())
