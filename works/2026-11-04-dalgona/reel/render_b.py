"""Cut B (operator pick 11-07): "몇 번째 만에?" — a red try counter top left all the way through.

1번째  the hammer straight onto the star, an arm snaps, he aims, 탕.
2번째  careful needle, but the clock runs out, 탕.
       the counter spins 3..12 on the white flash
13번째 slow needle, a look up at him, the hammer at fast forward, one blow cracks into the star ("아…"), the swab
       melts the crack shut, the last crumbs off, 통과. End card "너는 몇 번 만에?".

Every shot of the game is the real page (?reel) on the virtual clock; render.py holds the driver (Reel).
Run: uv run --with playwright python render_b.py   then   uv run --with playwright python compose.py
"""
import asyncio, json, math, subprocess, sys
from playwright.async_api import async_playwright
from render import HERE, OUT, URL, PORT, VW, VH, DSF, VCLOCK, Reel, ease, lerp

CAP = {
    "candy": "깨면 <em>총 맞는</em><br>달고나",
    "snap": '<span style="font-size:1.6em">아.</span>',
    "bang": '<span style="font-size:2.2em">탕.</span>',
    "time": "2분 안에<br><em>모양만</em> 떼야 한다",
    "over": '<span style="font-size:1.4em">시간 끝.</span>',
    "slow": "이번엔<br><em>천천히</em>",
    "watch": "<em>저 사람</em>은<br>계속 보고 있다",
    "hammer": "망치로 <em>바깥만</em> 톡톡",
    "crack": '<span style="font-size:1.6em">아…</span><small>금 갔다</small>',
    "swab": "면봉으로<br><em>녹여서</em> 붙이고",
    "last": "마지막 <em>한 톡</em>",
}
END = ('<img class="logo" src="../logo.svg" alt="달고나">'
       '<div class="ask">너는 <em>몇 번</em><br>만에?</div>'
       '<span>폰 카메라로 하는 진짜 달고나</span>'
       '<i>프로필 링크 · 무료 · 설치 없음</i>')


async def cap(r, key): await r.js(f"__reel.cap({json.dumps(CAP.get(key, key))})")
async def count(r, n): await r.js(f"__reel.count('{n}번째', 1)")


async def until(r, cond, most):
    for _ in range(most):
        if await r.js(cond): return True
        await r.frame()
    return False


async def die(r, look=(30, 92)):
    """after a fail: he is looked at, the cut jumps to the aim, the shot, a white flash"""
    await r.frame(14)                                 # the break / the silence, held
    await cap(r, "")
    await r.press(False)
    await r.look(*look, 10, hand=(0, 900)); r.hand_on = False
    r.rec = False                                      # jump cut: he has already brought the rifle up
    await until(r, "!!(__ar.WT.doom && __ar.WT.doom.racked)", 120)
    r.rec = True
    await until(r, "!!(__ar.WT.doom && __ar.WT.doom.fired)", 60)
    await cap(r, "bang"); await r.frame(8)
    for i in range(1, 4): await r.js(f"__reel.flash({i / 3})"); await r.frame()


async def again(r, n, tool):
    """off camera on the white: a new candy, the camera back down, then the white fades off"""
    r.rec = False
    await cap(r, "")
    # the shot's timers (drained colours, the 탈락 card) must land before the reset, or they land on the new try
    await until(r, "!document.getElementById('dead').hidden", 150)
    await r.js(f"__ar.reset('star')")
    r.alpha, r.beta = 0, 50
    # while the camera was up on him the page re-anchored the candy up there; put it back in front, as at the start
    await r.frame(10); await r.js("__ar.place()"); await r.frame(20)
    await r.js(f"__ar.pickTool('{tool}'); __reel.timer(1)")
    r.rec = True
    await count(r, n); r.ev("again")


async def main():
    for f in OUT.glob("*.png"): f.unlink()   # cut A left more frames than this one has
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
            r.alpha, r.beta = 34, 50; await r.frame(40)       # the guard off to the left, the candy straight ahead
            await pg.wait_for_timeout(1200); await r.frame(5)
            r.alpha = 0; await r.frame(10); await r.js("__ar.place()"); await r.frame(30)
            for _ in range(60):
                if await r.js("__reel.ready()"): break
                await pg.wait_for_timeout(250)
            print("ready", flush=True)
            center = tuple(await r.js("__ar.screenOf(0, 0)"))

            # ---- 1번째: the hammer straight away; the second blow lands on the star ----
            await r.js("__ar.pickTool('hammer'); __ar.WT.timeLeft = 117.4; __reel.timer(1)")
            r.hand_on = True; r.zo = center; r.zk = r.zt = 1.1
            q = await r.scr(0.62, 0.55); r.mouse = q; await pg.mouse.move(*q)
            await r.press(True); r.hk = 0.94
            r.rec = True
            await count(r, 1); await cap(r, "candy")
            await r.frame(14)
            tip = await r.js("__ar.S.pts.reduce((m, p) => Math.hypot(...p) > Math.hypot(...m) ? p : m)")
            arm = (tip[0] * 0.55, tip[1] * 0.55)            # inside an arm of the star
            q2 = await r.scr(*arm)
            await r.move(*q2, 16)
            if not await until(r, "__ar.S.ended", 30):       # the swing missed the arm: the next blow lands on it
                await r.js(f"__ar.S.lastHit = 0; __ar.hit({{sx: {arm[0]}, sy: {arm[1]}, px: (0.5 + {arm[0]} * 0.5) * 512, py: (0.5 + {arm[1]} * 0.5) * 512, onCandy: true}}, 1.2)")
                await r.frame()
            r.ev("snap"); await cap(r, "snap"); r.zt = 1.0
            await r.diag("try 1")
            await die(r)

            # ---- 2번째: careful, but the clock runs out ----
            await again(r, 2, "needle")
            await r.js("__ar.WT.timeLeft = 2.9; __ar.WT.running = true")
            path = await r.outline(0, 40, 40)
            q = await r.scr(*path[0]); r.mouse = q; await pg.mouse.move(*q)
            r.hand_on = True; r.zt = 1.25
            await r.press(True); r.hk = 0.95
            await cap(r, "time")
            for i in range(3, 0, -1):   # the white fades off the new try
                await r.js(f"__reel.flash({(i - 1) / 3})"); await r.frame()
            k, t0 = 0, await r.js("Math.ceil(__ar.WT.timeLeft)")
            while not await r.js("__ar.S.ended") and k < 140:
                await r.trace(path[min(k // 3 + 1, len(path) - 1)])
                s = await r.js("Math.ceil(__ar.WT.timeLeft)")
                if s != t0: r.ev("tick"); t0 = s
                if k % 8 == 0: r.ev("scratch")
                k += 1
            r.ev("hush"); await cap(r, "over"); r.zt = 1.0
            await r.diag("try 2")
            await die(r)

            # ---- the counter spins on the white ----
            for n in range(3, 13):
                await count(r, n)
                if n % 3 == 0: r.ev("tick")
                await r.frame(2)

            # ---- 13번째: slow needle ----
            await again(r, 13, "needle")
            await r.js("__ar.WT.timeLeft = 108.6")
            path = await r.outline(0, 75, 75)
            q = await r.scr(*path[0]); r.mouse = q; await pg.mouse.move(*q)
            r.hand_on = True; r.zo = center; r.zk = r.zt = 1.25
            await r.press(True); r.hk = 0.95
            for i in range(1, 7): await r.js(f"__reel.flash({1 - i / 6})"); await r.frame()
            await cap(r, "slow")
            for i, q in enumerate(path[1:]):
                await r.trace(q)
                if i % 6 == 0: r.ev("scratch")
            await r.press(False); r.hk = 1.0
            # a look up at him; while the camera is on him the groove gets finished and the hammer comes out
            await cap(r, ""); r.zt = 1.0
            await r.js("__reel.timer(0)")
            await r.look(30, 88, 20, hand=(0, 900)); r.hand_on = False; await cap(r, "watch"); r.ev("breath")
            await r.frame(26)
            r.rec = False
            await r.js("__ar.carveAll(0.97); __ar.WT.timeLeft = 41.2; __ar.pickTool('hammer')")
            th = 0.1
            q = await r.scr(0.88 * math.cos(th), 0.88 * math.sin(th)); r.mouse = q; await pg.mouse.move(*q)
            await r.press(True); r.hk = 0.94
            r.rec = True
            await cap(r, ""); r.hand_on = True
            await r.look(0, 50, 18); await r.js("__reel.timer(1)")
            # the hammer round the rim at x3, then the leftovers at x8
            await cap(r, "hammer"); await r.js("__reel.chip('▶▶ 빨리 감기', 1)")
            for k in range(66):
                th += 0.035; q = await r.scr(0.88 * math.cos(th), 0.88 * math.sin(th))
                r.rec = k % 3 == 0
                await r.move(*q, 1)
            r.rec = True
            await r.press(False); r.hk = 1.0
            outer0 = await r.js("__ar.S.outer0")
            ang, k = 1.95, 0
            await r.js("__ar.AR.autoSwing = false")
            while await r.outside_left() > max(1300, outer0 * 0.2) and not await r.js("__ar.S.ended") and k < 60:
                # aim at a leftover bit (the rim pass leaves the wedges between the arms), well away from the star
                c = await r.js("""(() => { const F = __ar.S.F, N = F.N, o = [];
                  for (let i = 0; i < N * N; i++) if (F.alive[i] && !F.inside[i]) {
                    const sx = ((i % N) + .5) / N * 2 - 1, sy = (((i / N) | 0) + .5) / N * 2 - 1;
                    if (Math.min(...__ar.S.pts.map(p => Math.hypot(p[0] - sx, p[1] - sy))) > 0.12) o.push([sx, sy]); }
                  return o.length ? o[(o.length * 0.618 * K | 0) % o.length] : null; })()""".replace("K", str(k + 1)))
                if not c: break
                q = await r.scr(*c); k += 1
                r.rec = False; await r.move(*q, 2); await r.js("__ar.S.lastHit = 0")
                await pg.mouse.down(); await r.frame(2); await pg.mouse.up()
                r.rec = True; r.ev("thud"); await r.frame(1)
                r.rec = False; await r.frame(9); r.rec = True
            await r.js("__reel.chip(null, 0); __ar.AR.autoSwing = true; __ar.S.lastHit = 0")
            print("left", await r.outside_left(), "of", outer0, "ended", await r.js("__ar.S.ended"), flush=True)
            # ---- one blow next to an arm: a crack runs into the star ----
            await cap(r, "")
            tip = await r.js("__ar.S.pts.reduce((m, p) => Math.hypot(...p) > Math.hypot(...m) ? p : m)")
            r.zo = tuple(await r.js(f"__ar.screenOf({tip[0] * 0.6}, {tip[1] * 0.6})")); r.zt = 1.9
            side = (tip[0] * 0.62 - tip[1] * 0.28, tip[1] * 0.62 + tip[0] * 0.28)   # beside the arm, outside the star
            q = await r.scr(*side)
            await r.move(*q, 24)
            await r.press(True); r.hk = 0.94
            last = await r.js("__ar.S.lastHit || 0")
            await until(r, f"(__ar.S.lastHit || 0) !== {last}", 40)
            crack = await r.js(f"__ar.crackInto({tip[0] * 0.7}, {tip[1] * 0.7})")
            await r.press(False); r.hk = 1.0
            r.ev("crack"); await cap(r, "crack")
            await r.frame(26)
            await r.diag("crack")
            # ---- the swab melts it shut ----
            await r.js("__ar.pickTool('swab')")
            await cap(r, "swab"); r.ev("melt")
            a, z = crack[0], crack[-1]
            await r.move(*(await r.scr(*a)), 10)
            await r.press(True); r.hk = 0.95
            for i in range(90):
                u = 0.5 - 0.5 * math.cos(i / 9 * math.pi)                 # back and forth along the crack
                p = crack[min(len(crack) - 1, int(u * (len(crack) - 1)))]
                await r.move(*(await r.scr(*p)), 1)
                if i % 15 == 0: r.ev("scratch")
                if i > 20 and await r.js("__ar.S.shapeCracks.every(k => k.sev <= 0)"): break
            await r.press(False); r.hk = 1.0
            await r.frame(14)
            await r.diag("mended")
            # ---- last tap, the shape comes out ----
            await cap(r, "last"); r.zt = 1.35
            await r.js("__ar.pickTool('hammer')")
            q = await r.scr(-0.86, 0.3); await r.move(*q, 12)
            await r.frame(6)
            await r.js("__ar.finish()"); r.ev("thud"); r.ev("win")
            await cap(r, ""); r.zt = 1.0
            await r.move(r.mouse[0] + 60, r.mouse[1] + 140, 10); r.hand_on = False
            await r.frame(40)
            await r.diag("win")
            # ---- end card ----
            for i in range(1, 11): await r.js(f"__reel.end({json.dumps(END)}, {i / 10}); document.getElementById('dead').style.opacity = {1 - i / 10}; __reel.count(null, {1 - i / 10})"); await r.frame()
            r.ev("end"); await r.frame(80)
            (HERE / "out" / "events.json").write_text(json.dumps({"frames": r.f, "events": r.events}, ensure_ascii=False))
            print("frames", r.f, flush=True)
            await b.close()
    finally:
        srv.terminate()

if __name__ == "__main__":
    asyncio.run(main())
