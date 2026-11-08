"""Render the promo reel frame by frame from the real AR page (?reel), 1080x1920 at 30 fps.

Cut A (operator 11-07): carving goes well, the guard, the hammer taps the outside off, "성공?", the hammer
clips a star arm, it snaps off, he aims, the shot, 탈락, end card "너는 안 깨고 할 수 있어?".

Run: uv run --with playwright python render.py
Frames go to out/frames/NNNN.png, the event log (for the sound track) to out/events.json.
The page's game loop and its timeouts run on a virtual clock (one step per frame), so the result is smooth however
slow the render.
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
PORT = os.environ.get("PORT", "8771")   # 8765 is taken by ai-hub ops serve
URL = f"http://127.0.0.1:{PORT}/works/2026-11-04-dalgona/site/ar/?reel&room=metro&yaw=0"
# virtual clock: performance.now, the game loop's rAF and setTimeout all advance only with __step
VCLOCK = ("(()=>{let vt=0,tid=1e6;const rq=[],tq=[];performance.now=()=>vt;const rr=window.requestAnimationFrame.bind(window);"
          "window.requestAnimationFrame=cb=>{if(cb.name!=='loop')return rr(cb);rq.push(cb);return 1};"
          "const st=window.setTimeout.bind(window),ct=window.clearTimeout.bind(window);"
          "window.setTimeout=(cb,ms=0,...a)=>{if(typeof cb!=='function')return st(cb,ms);const id=++tid;tq.push({t:vt+(+ms||0),cb,a,id});return id};"
          "window.clearTimeout=id=>{const i=tq.findIndex(x=>x.id===id);if(i>=0)tq.splice(i,1);else ct(id)};"
          "window.__step=ms=>{vt+=ms;for(;;){tq.sort((x,y)=>x.t-y.t);if(!tq.length||tq[0].t>vt)break;const x=tq.shift();try{x.cb(...x.a)}catch(e){console.error(e)}}"
          "const q=rq.splice(0);q.forEach(cb=>cb(vt))}})()")

ease = lambda u: 0.5 - 0.5 * math.cos(math.pi * max(0.0, min(1.0, u)))
lerp = lambda a, b, u: a + (b - a) * u

CAP = {
    "candy": "깨면 <em>총 맞는</em><br>달고나",
    "guard": "시간 넘기면<br><em>저 사람</em>이 쏜다",
    "hammer": "망치로 <em>바깥만</em> 톡톡",
    "almost": "<em>성공?</em>",
    "snap": '<span style="font-size:1.6em">아.</span>',
    "bang": '<span style="font-size:2.2em">탕.</span>',
}
END = ('<img class="logo" src="../logo.svg" alt="달고나">'
       '<div class="ask">너는 <em>안 깨고</em><br>할 수 있어?</div>'
       '<span>폰 카메라로 하는 진짜 달고나</span>'
       '<i>프로필 링크 · 무료 · 설치 없음</i>')


class Reel:
    def __init__(self, pg):
        self.pg, self.f, self.t, self.rec, self.events = pg, 0, 0.0, True, []
        self.alpha, self.beta, self.mouse, self.down = 0.0, 50.0, None, False
        self.hk, self.hand_on = 1.0, False
        self.zk = self.zt = 1.0; self.zo = (VW / 2, VH / 2)   # lens zoom, eased toward zt each frame
        self.seen = [0, 0, 0, 0]   # last hit time, racked, fired: a change logs a sound event

    async def js(self, s): return await self.pg.evaluate(s)

    async def frame(self, n=1, cap=None):
        for _ in range(n):
            a = self.alpha + 0.6 * math.sin(self.t * 1.3); b = self.beta + 0.5 * math.sin(self.t * 1.7 + 1)   # hand-held drift
            hx, hy = self.mouse or (0, 0)
            self.zk += (self.zt - self.zk) * 0.1
            st = await self.js(f"dispatchEvent(new DeviceOrientationEvent('deviceorientation',{{alpha:{a},beta:{b},gamma:0}}));"
                               f"__reel.hand({hx},{hy},{self.hk},0,{str(self.hand_on and self.mouse is not None).lower()});"
                               f"__reel.zoom({self.zk},{self.zo[0]},{self.zo[1]});__step({DT});"
                               "[__ar.S.lastHit || 0, (__ar.WT.doom || {}).racked || 0, (__ar.WT.doom || {}).fired || 0, __ar.H.swingAt || 0]")
            if self.rec:
                if st[0] != self.seen[0] and st[0]: self.ev("thud")
                elif st[3] and self.seen[3] and st[3] != self.seen[3]: self.ev("swish")   # a blow that met only air
                if st[1] and not self.seen[1]: self.ev("cock")
                if st[2] and not self.seen[2]: self.ev("shot")
            self.seen = st
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

    async def outside_left(self):
        return await self.js("(() => { const F = __ar.S.F; let n = 0; for (let i = 0; i < F.N * F.N; i++) if (F.alive[i] && !F.inside[i]) n++; return n; })()")


async def main():
    srv = subprocess.Popen([sys.executable, "-m", "http.server", PORT, "--bind", "127.0.0.1"], cwd=str(HERE.parents[2]),
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

            # the needle is already in hand when the video starts
            await r.js("__ar.pickTool('needle'); __ar.WT.timeLeft = 58.6; __reel.timer(1)")
            r.hand_on = True; r.zo = tuple(await r.js("__ar.screenOf(0, 0)")); r.zk = r.zt = 1.25
            path = await r.outline(0, 75, 75)
            await r.trace(path[0], 1); r.hk = 0.95; await r.press(True); await r.frame(2)
            r.rec = True

            # 1. carving the star on the subway floor (0 - 2.5 s)
            await r.cap("candy")
            for i, q in enumerate(path[1:]):
                await r.trace(q)
                if i % 6 == 0: r.ev("scratch")
            await r.press(False); r.hk = 1.0
            # 2. turn: the guard (2.5 - 5.0 s)
            await r.cap(""); r.zt = 1.0
            await r.js("__reel.timer(0)")   # the clock chip would sit over him
            await r.look(30, 88, 22, hand=(0, 900)); r.hand_on = False; await r.cap("guard"); r.ev("breath")
            await r.frame(34)
            await r.cap(""); await r.look(0, 50, 18); await r.js("__reel.timer(1)")
            # (off camera) the groove is finished, most of the clock is gone, the hammer comes out
            await r.snap_hold(); r.rec = False
            await r.js("__ar.carveAll(0.97); __ar.WT.timeLeft = 24.4; __ar.pickTool('hammer')")
            r.zk = r.zt = 1.15; r.zo = tuple(await r.js("__ar.screenOf(0, 0)"))
            r.hand_on = True; th = 0.1
            q = await r.scr(0.88 * math.cos(th), 0.88 * math.sin(th)); r.mouse = q; await pg.mouse.move(*q)
            await r.press(True); r.hk = 0.94; await r.frame(4)
            r.rec = True; await r.dissolve(n=8, label="30초 뒤"); r.ev("tick")
            # 3. the hammer swings on its own; carried round the rim it knocks the outside off (5.0 - 8.5 s)
            await r.cap("hammer")
            for k in range(66):
                th += 0.035; q = await r.scr(0.88 * math.cos(th), 0.88 * math.sin(th))
                await r.move(*q, 1)
            await r.press(False); r.hk = 1.0
            await r.diag("after hammer")
            # (off camera) nearly all of the outside gone, 9 s left, the guard has come closer
            await r.snap_hold(); r.rec = False
            await r.js("__ar.WT.running = false")
            ang = 1.95
            for _ in range(40):
                if await r.outside_left() < 1300 or await r.js("__ar.S.ended"): break
                q = await r.scr(0.88 * math.cos(ang), 0.88 * math.sin(ang)); ang += 0.6
                await r.move(*q, 2); await r.js("__ar.S.lastHit = 0; __ar.AR.autoSwing = false")
                await pg.mouse.down(); await r.frame(2); await pg.mouse.up(); await r.frame(10)
            # what the taps left hugging the star goes too, but a few crumbs: "성공?" has to look nearly done
            await r.js("""(() => { const F = __ar.S.F, N = F.N, near = new Uint8Array(N * N), R = 4;
              for (let k = 0; k < N * N; k++) if (F.inside[k]) { const x = k % N, y = (k / N) | 0;
                for (let j = -R; j <= R; j++) for (let i = -R; i <= R; i++) { const X = x + i, Y = y + j; if (X >= 0 && Y >= 0 && X < N && Y < N) near[Y * N + X] = 1; } }
              const far = []; for (let k = 0; k < N * N; k++) if (F.alive[k] && !F.inside[k] && !near[k]) far.push(k);
              if (far.length) __ar.dropCells(far); })()""")
            await r.frame(70)   # (off camera) let the dropped pieces fall away
            await r.js("__ar.AR.autoSwing = true; __ar.S.lastHit = 0; __ar.WT.timeLeft = 9.6; __ar.WT.running = true; __ar.guardTo(1.8)")
            print("ended before slip", await r.js("__ar.S.ended"), await r.outside_left(), flush=True)
            tip = await r.js("__ar.S.pts.reduce((m, p) => Math.hypot(...p) > Math.hypot(...m) ? p : m)")
            q = await r.scr(tip[0] * 1.45, tip[1] * 1.45); r.mouse = q; await pg.mouse.move(*q)
            r.zk = r.zt = 1.9; r.zo = tuple(await r.js("__ar.screenOf(0, 0.1)"))   # the star fills the frame for the last beat
            await r.frame(4)
            r.rec = True; await r.dissolve(n=8, label="15초 뒤")
            # 4. only the star is left; the hammer drifts toward an arm, the clock ticks (8.5 - 11.0 s)
            await r.cap("almost")
            q = await r.scr(tip[0] * 0.55, tip[1] * 0.55)
            x0, y0 = r.mouse
            for i in range(1, 51):
                if i % 15 == 1: r.ev("tick")
                if i == 30: await r.press(True); r.hk = 0.94   # the pinch: the hammer starts its swing
                u = ease(i / 50); await r.pg.mouse.move(lerp(x0, q[0], u), lerp(y0, q[1], u)); r.mouse = (lerp(x0, q[0], u), lerp(y0, q[1], u))
                await r.frame()
            # 5. the blow lands on the arm: it snaps off with the candy around it (11.0 - 12.0 s)
            sx, sy = tip[0] * 0.55, tip[1] * 0.55
            await r.js(f"if (!__ar.S.ended) {{ __ar.S.lastHit = 0; __ar.hit({{ onCandy: true, sx: {sx}, sy: {sy}, px: {(sx + 1) * 256}, py: {(sy + 1) * 256} }}); }}")
            r.ev("snap"); await r.cap("snap"); await r.press(False); r.hk = 1.0
            await r.diag("slip")
            # the hand lifts off so the break is in plain view
            await r.move(r.mouse[0] + 70, r.mouse[1] + 110, 8); await r.frame(22)
            # 6. look up: he is right there, stops, racks, aims; the shot (12.0 - 15.0 s)
            await r.cap(""); r.zt = 1.0
            await r.look(32, 100, 18, hand=(0, 900)); r.hand_on = False
            r.zo = (VW / 2, VH * 0.35)
            for _ in range(90):
                r.zt = min(1.12, r.zt + 0.004)   # a slow push-in on the aim
                await r.frame()
                if r.seen[2]: break
            r.zk = r.zt = 1.0; await r.cap("bang"); await r.frame(12); await r.cap("")
            # the white frame, black, the drained room (15.0 - 16.5 s); the game's result line comes up sooner here
            await r.frame(2)
            await r.js("document.getElementById('deadWhy').textContent = '망치가 모양을 쳤어요'; document.getElementById('dead').hidden = false")
            await r.frame(24)
            await r.diag("dead")
            # 7. end card (16.5 - 20.0 s)
            for i in range(1, 11): await r.js(f"__reel.end({json.dumps(END)}, {i / 10}); document.getElementById('dead').style.opacity = {1 - i / 10}"); await r.frame()
            r.ev("end"); await r.frame(75)
            (HERE / "out" / "events.json").write_text(json.dumps({"frames": r.f, "events": r.events}, ensure_ascii=False))
            print("frames", r.f, flush=True)
            await b.close()
    finally:
        srv.terminate()

if __name__ == "__main__":
    asyncio.run(main())
