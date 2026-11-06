"""Render the promo reel frame by frame from the real AR page (?reel), 1080x1920 at 30 fps.

Run: uv run --with playwright python render.py [--from SEC] [--to SEC]
Frames go to out/frames/NNNN.png, the event log (for the sound track) to out/events.json.
The page's game loop runs on a virtual clock (one step per frame), so the result is smooth however slow the render.
"""
import asyncio, base64, json, math, os, pathlib, subprocess, sys
from playwright.async_api import async_playwright

if sys.platform == "win32":   # idle priority, inherited by Chrome: the trading tick shares this machine
    import ctypes; ctypes.windll.kernel32.SetPriorityClass(ctypes.windll.kernel32.GetCurrentProcess(), 0x40)
    # and only the last two cores (children inherit the mask), so the scheduled tick keeps the other six
    ctypes.windll.kernel32.SetProcessAffinityMask(ctypes.windll.kernel32.GetCurrentProcess(), 0xC0)

HERE = pathlib.Path(__file__).parent
OUT = HERE / "out" / "frames"; OUT.mkdir(parents=True, exist_ok=True)
FPS = 30; DT = 1000 / FPS
STRIDE = int(os.environ.get("STRIDE", 1))   # preview: keep every Nth frame only
VW, VH, DSF = 405, 720, 8 / 3          # 9:16 viewport -> 1080x1920
URL = "http://127.0.0.1:8765/works/2026-11-04-dalgona/site/ar/?reel&room=metro&yaw=0"
VCLOCK = ("(()=>{let vt=0;const rq=[];performance.now=()=>vt;const rr=window.requestAnimationFrame.bind(window);"
          "window.requestAnimationFrame=cb=>{if(cb.name!=='loop')return rr(cb);rq.push(cb);return 1};"
          "window.__step=ms=>{vt+=ms;const q=rq.splice(0);q.forEach(cb=>cb(vt))}})()")

ease = lambda u: 0.5 - 0.5 * math.cos(math.pi * max(0.0, min(1.0, u)))
lerp = lambda a, b, u: a + (b - a) * u

CAP = {
    "candy": "폰 카메라로 비추면<br>바닥에 <em>달고나</em>가 뜬다",
    "stay": "폰을 돌려도 <em>그 자리</em>",
    "guard": "2분 안에 못 빼면<br><em>감시자</em>가 온다",
    "needle": "엄지·검지 모으면 <em>바늘</em>",
    "carve": "살살 긁어서<br>선을 판다",
    "crack": "<em>짜작!</em><br>금 갔다",
    "swab": "젖은 면봉으로<br><em>녹여 붙이고</em>",
    "hammer": "검지로 <em>톡톡</em><br>바깥을 깨고",
    "near": "<em>벌써</em> 코앞",
    "win": "<em>통째로</em><br>빠졌다!",
}
END = ('<b>손끝 <em>달고나</em></b><span>폰 카메라로 하는 진짜 달고나</span>'
       '<i>프로필 링크 · 무료 · 설치 없음</i>')


class Reel:
    def __init__(self, pg):
        self.pg, self.f, self.t, self.rec, self.events = pg, 0, 0.0, True, []
        self.alpha, self.beta, self.mouse, self.down = 0.0, 50.0, None, False
        self.hk, self.hand_on = 1.0, False
        self.zk = self.zt = 1.0; self.zo = (VW / 2, VH / 2)   # lens zoom, eased toward zt each frame

    async def js(self, s): return await self.pg.evaluate(s)

    async def frame(self, n=1, cap=None):
        for _ in range(n):
            a = self.alpha + 0.6 * math.sin(self.t * 1.3); b = self.beta + 0.5 * math.sin(self.t * 1.7 + 1)   # hand-held drift
            hx, hy = self.mouse or (0, 0)
            self.zk += (self.zt - self.zk) * 0.1
            await self.js(f"dispatchEvent(new DeviceOrientationEvent('deviceorientation',{{alpha:{a},beta:{b},gamma:0}}));"
                          f"__reel.hand({hx},{hy},{self.hk},0,{str(self.hand_on and self.mouse is not None).lower()});"
                          f"__reel.zoom({self.zk},{self.zo[0]},{self.zo[1]});__step({DT})")
            self.t += DT / 1000
            await self.tick_chip()
            if self.rec:
                if self.f % STRIDE == 0: await self.pg.screenshot(path=str(OUT / f"{self.f:04d}.png"))
                self.f += 1

    async def snap_hold(self):
        """remember the frame before an off-camera stretch, to dissolve from it afterwards"""
        self._hold = "data:image/jpeg;base64," + base64.b64encode(await self.pg.screenshot(type="jpeg", quality=92)).decode()

    async def dissolve(self, n=10, label=None):
        await self.js(f"__reel.hold({json.dumps(self._hold)}, 1)")
        if label: await self.js(f"__reel.chip({json.dumps(label)}, 1)")
        for i in range(1, n + 1):
            await self.js(f"__reel.hold(null, {1 - ease(i / n)})"); await self.frame()
        if label: self.chip_off = self.f + 24

    async def tick_chip(self):
        if getattr(self, "chip_off", None) and self.f >= self.chip_off:
            await self.js("__reel.chip(null, 0)"); self.chip_off = None

    def ev(self, name): self.events.append([round(self.f / FPS, 3), name])

    async def diag(self, tag):
        d = await self.js("({toast: document.getElementById('toast').textContent, why: (document.getElementById('deadWhy')||{}).textContent, ended: __ar.S.ended, cracks: __ar.S.shapeCracks.map(k => +k.sev.toFixed(2))})")
        print(f"[{self.f / FPS:5.1f}s] {tag}: {d}", flush=True)

    async def move(self, x, y, frames=1):
        x0, y0 = self.mouse or (x, y)
        for i in range(1, frames + 1):
            u = ease(i / frames); p = (lerp(x0, x, u), lerp(y0, y, u))
            await self.pg.mouse.move(*p); self.mouse = p
            await self.frame()

    async def press(self, on):
        self.down = on
        await (self.pg.mouse.down() if on else self.pg.mouse.up())

    async def look(self, alpha, beta, frames, hand=(0, 0)):
        a0, b0 = self.alpha, self.beta; m0 = self.mouse
        for i in range(1, frames + 1):
            u = ease(i / frames); self.alpha, self.beta = lerp(a0, alpha, u), lerp(b0, beta, u)
            if m0 and hand != (0, 0):
                # the drawn hand and the pointer (which carries the 3D tool) leave together; once the hand is
                # out of frame the tool is dropped so it cannot hang at the bottom edge
                self.mouse = (m0[0] + hand[0] * u, m0[1] + hand[1] * u)
                await self.pg.mouse.move(self.mouse[0], min(self.mouse[1], VH - 2))
                if self.mouse[1] > VH + 40: await self.js("__ar.S.tool = null")
            await self.frame()

    async def cap(self, key): await self.js(f"__reel.cap({json.dumps(CAP.get(key, key))})")

    async def scr(self, sx, sy):
        """screen point of a candy point (candy radii), plus the touch offset so the TOOL TIP lands there"""
        x, y = await self.js(f"__ar.screenOf({sx},{sy})")
        return x + 20, y + 44

    async def outline(self, i0, i1, n):
        """candy-space points along the shape; trace() projects each one on the frame it is used"""
        return await self.js(f"""(() => {{ const P = __ar.S.pts, o = [];
          for (let k = 0; k <= {n}; k++) {{ const i = Math.round({i0} + ({i1} - {i0}) * k / {n}) % P.length; o.push(P[i]); }}
          return o; }})()""")

    async def trace(self, c, frames=1):
        await self.move(*(await self.scr(*c)), frames)


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
            # place the guard off to the left, then the candy straight ahead
            r.alpha, r.beta = 34, 50; await r.frame(40)
            await pg.wait_for_timeout(1200); await r.frame(5)
            r.alpha = 0; await r.frame(10); await r.js("__ar.place()"); await r.frame(30)
            for _ in range(60):
                if await r.js("__reel.ready()"): break
                await pg.wait_for_timeout(250)
            print("ready", flush=True)
            r.rec = True

            r.zo = tuple(await r.js("__ar.screenOf(0, 0)"))
            # 1. the candy appears (0 - 2.2 s)
            await r.js("__reel.timer(0); __ar.place()"); await r.cap("candy"); await r.frame(62)
            # 2. turn: it stays put; the guard is there (2.2 - 5.0 s)
            await r.cap("stay"); await r.look(-22, 56, 24)
            await r.look(30, 95, 30); await r.cap("guard"); r.ev("breath")
            for i in range(1, 9): await r.js(f"__reel.timer({i / 8})"); await r.frame()
            await r.frame(22)
            await r.look(0, 50, 22); await r.cap("")
            # 3. the hand comes in, pinches the needle, carves (5.0 - 9.0 s)
            await r.js("__ar.pickTool('needle')"); r.ev("tick")
            r.hand_on = True; x, y = await r.scr(0.1, 0.55)
            r.mouse = (x + 160, y + 260); r.zt = 1.3; await r.move(x, y, 16); await r.cap("needle")
            r.hk = 0.95; await r.press(True); r.ev("poke"); await r.frame(6)
            path = await r.outline(0, 70, 70)
            await r.trace(path[0], 6)
            for i, q in enumerate(path[1:]):
                await r.trace(q)
                if i == 20: await r.cap("carve")
                if i % 6 == 0: r.ev("scratch")
            await r.press(False); r.hk = 1.0
            # (off camera) two more slow passes, so the groove is real
            await r.snap_hold(); r.rec = False
            full = await r.outline(0, 400, 400)
            await r.press(True)
            for q in full + full: await r.trace(q)
            await r.press(False)
            r.rec = True; await r.dissolve(label="30초 뒤")
            # 4. a careless fast stroke: a crack, the guard raises the rifle (9.0 - 10.6 s)
            a = await r.scr(0.62, -0.1); bq = await r.scr(0.25, 0.5)
            await r.move(*a, 8); r.hk = 0.95; await r.press(True)
            for i in range(1, 5): await r.move(lerp(a[0], bq[0], i / 4), lerp(a[1], bq[1], i / 4), 1)
            r.ev("crack"); await r.cap("crack"); await r.press(False); r.hk = 1.0; await r.diag("after slash")
            r.zo = (bq[0] - 20, bq[1] - 44); r.zt = 1.55
            await r.frame(26)
            # 5. the wet swab melts it shut (10.6 - 13.0 s)
            await r.js("__ar.pickTool('swab')"); r.ev("tick")
            # rub up and down the crack itself (texture px -> candy coords -> screen)
            pts = await r.js("(() => { const k = __ar.S.shapeCracks.at(-1); return k ? k.pts : [[330, 230]]; })()")
            pts = pts[: max(2, len(pts) * 2 // 3)]
            along = [(x / 256 - 1, y / 256 - 1) for x, y in pts]
            r.zt = 1.35; await r.trace(along[0], 10); await r.cap("swab")
            r.hk = 0.95; await r.press(True); r.ev("melt")
            for i in range(56):
                u = 0.5 - 0.5 * math.cos(i * 0.35); q = along[min(len(along) - 1, int(u * (len(along) - 1)))]
                x, y = await r.scr(*q); await r.move(x + 2 * math.sin(i * 1.7), y + 2 * math.cos(i * 1.3), 1)
            # whatever the rubbing missed is treated as mended: this run is the clean one
            await r.js("__ar.S.shapeCracks.forEach(k => k.sev = 0)")
            await r.press(False); r.hk = 1.0; await r.diag("after swab")
            # 6. hammer: tap off the outside (13.0 - 15.2 s); off camera the groove is finished and time has run down
            await r.snap_hold(); r.rec = False
            r.zk = r.zt = 1.15; r.zo = tuple(await r.js("__ar.screenOf(0, 0)"))
            await r.js("__ar.carveAll(0.97); __ar.WT.timeLeft = 26; __ar.WT.running = false; __ar.pickTool('hammer')")
            await r.cap(""); await r.frame(20)
            await r.js("__ar.WT.running = true"); await r.frame(4)
            r.rec = True; await r.dissolve(label="1분 뒤"); r.ev("tick"); await r.cap("hammer")
            # taps marching round the rim: the first only cracks, each next one frees the piece between
            for th in (0.15, 0.75, 1.35):
                q = await r.scr(0.88 * math.cos(th), 0.88 * math.sin(th)); await r.move(*q, 9)
                await r.js("__ar.S.lastHit = 0"); r.hk = 0.94; await r.pg.mouse.down(); r.ev("thud"); await r.frame(3)
                await r.pg.mouse.up(); r.hk = 1.0; await r.frame(9); await r.diag(f"hammer {th}")
            # 7. look up: he is closer (15.2 - 17.0 s)
            await r.js("__ar.guardTo(1.8)"); hand0 = r.mouse
            # the hand (and the hammer on the pointer) drop out of frame as the phone tilts up
            r.zt = 1.0; await r.look(32, 110, 18, hand=(0, 900)); await r.cap("near"); r.ev("scuff"); await r.frame(24)
            # look back down with the hand still out; it returns with the hammer through the dissolve below
            await r.look(0, 50, 16); r.zt = 1.15
            # (off camera) knock most of the rest off, clock held
            await r.cap(""); await r.frame(); await r.snap_hold(); r.rec = False
            await r.js("__ar.S.tool = 'hammer'"); r.mouse = hand0
            await r.js("__ar.WT.running = false"); r.zk = r.zt = 1.2
            ang = 1.95
            for k in range(30):
                left = await r.js("(() => { const F = __ar.S.F; let n = 0; for (let i = 0; i < F.N * F.N; i++) if (F.alive[i] && !F.inside[i]) n++; return n; })()")
                if left < 2600 or await r.js("__ar.S.ended"): break
                q = await r.scr(0.88 * math.cos(ang), 0.88 * math.sin(ang)); ang += 0.6
                await r.move(*q, 2); await r.js("__ar.S.lastHit = 0"); await r.pg.mouse.down(); await r.frame(2); await r.pg.mouse.up(); await r.frame(10)
            await r.js("__ar.WT.running = true")
            r.rec = True; await r.dissolve()
            # 8. two more taps where candy is left, then the crumbs fall and the shape comes out (17.0 - 19.5 s)
            spots = await r.js("""(() => { const F = __ar.S.F, N = F.N, o = [];
              for (let a = 0; a < 6.28; a += 0.15) { const x = Math.cos(a) * 0.86, y = Math.sin(a) * 0.86, k = Math.floor((y + 1) / 2 * N) * N + Math.floor((x + 1) / 2 * N);
                if (F.alive[k] && !F.inside[k]) o.push(a); } return o; })()""")
            pick = spots[:1] + [a for a in spots if abs(a - spots[0]) > 0.5][:1] if spots else []
            for a in pick:
                q = await r.scr(0.86 * math.cos(a), 0.86 * math.sin(a)); await r.move(*q, 9)
                await r.js("__ar.S.lastHit = 0"); r.hk = 0.94; await r.pg.mouse.down(); r.ev("thud"); await r.frame(3)
                await r.pg.mouse.up(); r.hk = 1.0; await r.frame(8)
            await r.js("if (!__ar.S.ended) __ar.finish()"); await r.diag("finish")
            r.ev("win"); await r.cap("win"); r.zt = 1.6
            await r.move(r.mouse[0] + 220, r.mouse[1] + 330, 16); r.hand_on = False; await r.js("__ar.S.tool = null")
            await r.frame(44)
            # 9. end card (19.5 - 22.5 s)
            await r.cap("")
            for i in range(1, 11): await r.js(f"__reel.end({json.dumps(END)}, {i / 10})"); await r.frame()
            await r.frame(62)
            (HERE / "out" / "events.json").write_text(json.dumps({"frames": r.f, "events": r.events}, ensure_ascii=False))
            print("frames", r.f, flush=True)
            await b.close()
    finally:
        srv.terminate()

asyncio.run(main())
