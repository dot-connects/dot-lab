"""Cut B (operator pick 11-07): "몇 번째 만에?" — a try counter top left all the way through.

1번째  the hammer straight onto the star, an arm snaps, he aims, the shot, 탈락.
2번째  careful needle, a big clock runs down from 0:06, 시간 종료, the shot.
3..12  a film strip runs past (stills of the tries above, each stamped 탈락) and pushes into a blank 13 (11-08).
13번째 slow needle, a look up at him, the hammer at fast forward, one blow cracks into the star, the swab
       melts the crack shut, the last crumbs off, 통과. End card "당신은 몇 번째에 통과하시겠습니까?".

Every shot of the game is the real page (?reel) on the virtual clock; render.py holds the driver (Reel).
Run: uv run --with playwright python render_b.py   then   uv run --with playwright python compose.py
"""
import asyncio, json, math, random, subprocess, sys
from playwright.async_api import async_playwright
from render import HERE, OUT, URL, PORT, VW, VH, DSF, VCLOCK, STRIDE, Reel, ease, lerp

# 11-08 operator: the first captions read childish; now plain statements in a cold, rule-reading voice
CAP = {
    "rule": "모양대로 떼어 내면 통과<br>깨지면 <em>탈락</em>",
    "bang": '<b class="out">탈락</b>',
    "time": "남은 시간",
    "over": "시간 종료<small>모양을 다 떼어 내지 못했다</small>",
    "slow": "숨을 참고, 천천히",
    "watch": "누군가 보고 있다",
    "hammer": "바깥부터 조금씩",
    "crack": "금이 갔다",
    "swab": "녹여서 다시 붙인다",
    "last": "마지막 한 번",
}
END = ('<img class="logo" src="../logo.svg" alt="달고나">'
       '<div class="ask">당신은 몇 번째에<br>통과하시겠습니까?</div>'
       '<span>폰 카메라로 하는 달고나 뽑기</span>'
       '<i>프로필 링크 · 무료 · 설치 없음</i>')
# what ended the tries the film strip runs past
WHY = {3: "깨짐", 4: "시간 종료", 5: "깨짐", 6: "녹아서 구멍", 7: "깨짐", 8: "시간 종료", 9: "깨짐", 10: "깨짐", 11: "시간 종료", 12: "깨짐"}


async def cap(r, key): await r.js(f"__reel.cap({json.dumps(CAP.get(key, key))})")
async def count(r, n): await r.js(f"__reel.count({n}, 1)")


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
    await r.js(f"__ar.pickTool('{tool}'); __reel.timer(1); __reel.clock(null, 0)")
    r.rec = True
    await count(r, n); r.ev("again")


# tries 3..12, each ended its own way (operator 11-08: every frame of the film a different break). kind, then:
# arm = which star arm the hammer clipped (0..4) and how far out (0..1 of the tip), inner = a blow between two arms,
# knocks = how many blows on the outside before it, carve = how deep the groove got, clock = the game clock shown
VARIANTS = {
    3: dict(kind="arm", arm=0, at=0.6, knocks=1, carve=0.25, clock=101),
    4: dict(kind="time", knocks=4, carve=0.6),
    5: dict(kind="arm", arm=2, at=0.55, knocks=7, carve=0.5, clock=64),
    6: dict(kind="hole", arm=3, knocks=3, carve=0.45, clock=77),
    7: dict(kind="inner", arm=1, knocks=2, carve=0.4, clock=88),
    8: dict(kind="time", knocks=9, carve=0.8),
    9: dict(kind="arm", arm=4, at=0.45, knocks=5, carve=0.6, clock=52),
    10: dict(kind="arm", arm=1, at=0.5, knocks=10, carve=0.7, clock=31),
    11: dict(kind="time", knocks=1, carve=0.3),
    12: dict(kind="inner", arm=3, knocks=12, carve=0.85, clock=12),
}


async def stills(r):
    """play each of tries 3..12 off camera to its end and photograph the candy; returns the image URLs"""
    STILL = OUT.parent / "stills"; STILL.mkdir(exist_ok=True)
    r.rec = False; r.hand_on = False
    await until(r, "!document.getElementById('dead').hidden", 150)   # try 2's shot timers land first
    rnd, urls = random.Random(13), {}
    for n, v in VARIANTS.items():
        await r.js("__ar.reset('star')"); r.alpha, r.beta = 0, 50
        await r.frame(10); await r.js("__ar.place()"); await r.frame(10)
        await r.js(f"__ar.carveAll({v['carve']}); __ar.S.tool = null; __reel.cap(''); __reel.count(null, 0); __reel.clock(null, 0); __reel.flash(0); __reel.timer(1)")
        # star geometry: tip angle and radius, inner-corner radius
        a0, rt, ri = await r.js("(() => { const P = __ar.S.pts, rs = P.map(p => Math.hypot(...p)); const i = rs.indexOf(Math.max(...rs));"
                                " return [Math.atan2(P[i][1], P[i][0]), rs[i], Math.min(...rs)]; })()")
        for k in range(v["knocks"]):   # blows on the outside: bits of the ring gone, different every time
            a = rnd.uniform(0, 2 * math.pi); d = rnd.uniform(0.84, 0.95); sx, sy = d * math.cos(a), d * math.sin(a)
            await r.js(f"__ar.S.lastHit = 0; __ar.hit({{sx: {sx}, sy: {sy}, px: (0.5 + {sx} * 0.5) * 512, py: (0.5 + {sy} * 0.5) * 512, onCandy: true}}, 1)")
            await r.frame(3)
        await r.frame(12)
        if v["kind"] == "time":
            await r.js("__ar.WT.timeLeft = 0.05; __ar.WT.running = true"); await r.frame(4)
        else:
            await r.js(f"__ar.WT.timeLeft = {v['clock'] + 0.5}; __ar.WT.running = true")
            ta = a0 + v["arm"] * 2 * math.pi / 5
            if v["kind"] == "arm": d, ang = rt * v["at"], ta
            else: d, ang = ri * 0.75, ta + math.pi / 5
            sx, sy = d * math.cos(ang), d * math.sin(ang)
            if v["kind"] == "hole":   # the swab held on one spot inside the star until it melts through
                await r.js("__ar.pickTool('swab')")
                q = await r.scr(sx, sy); r.mouse = q; await r.pg.mouse.move(*q); await r.press(True)
                for _ in range(200):
                    if await r.js("__ar.S.ended"): break
                    await r.frame()
                await r.press(False); await r.js("__ar.S.tool = null")
            else:
                await r.js(f"__ar.S.lastHit = 0; __ar.hit({{sx: {sx}, sy: {sy}, px: (0.5 + {sx} * 0.5) * 512, py: (0.5 + {sy} * 0.5) * 512, onCandy: true}}, 1.2)")
        await r.frame(16)   # the pieces fall and settle
        cx, cy = await r.js("__ar.screenOf(0, 0)")
        w, h = 310, 400
        path = STILL / f"{n:02d}.png"
        await r.pg.screenshot(path=str(path), clip={"x": max(0, cx - w / 2), "y": max(0, min(VH - h, cy - h / 2)), "width": w, "height": h})
        print(f"still {n} {v['kind']} ended={await r.js('__ar.S.ended')} why={await r.js('document.getElementById(`deadWhy`).textContent')}", flush=True)
        urls[n] = f"/works/2026-11-04-dalgona/reel/out/stills/{n:02d}.png?{r.f}"
    r.mouse = None
    return urls


async def film(r, urls):
    """tries 3..12 run past as a film strip: each a still of that try's own break, stamped 탈락; it lands on a blank
    13 and pushes into it, so the white of the blank frame becomes the white the 13th try fades in from"""
    cells = [{"n": n, "src": urls[n], "why": WHY[n]} for n in range(3, 13)]
    cells.append({"n": 13, "blank": True})
    await r.js("__reel.flash(1)")
    r.rec = False
    await r.js(f"__reel.filmSetup({json.dumps(cells, ensure_ascii=False)})")
    for _ in range(100):
        if await r.js("__reel.filmReady()"): break
        await r.pg.wait_for_timeout(100)
    r.rec = True
    rnd = random.Random(7)
    flick = lambda: 1 + rnd.uniform(-0.07, 0.05)
    await count(r, 3)
    for i in range(1, 7):                     # in from the white
        await r.js(f"__reel.film({i / 6}, 0, 1, {flick()})"); await r.frame()
    N, last, y0 = 66, 0, 0.0
    for i in range(1, N + 1):
        y = 10 * ease(i / N); sp = y - y0; y0 = y
        await r.js(f"__reel.film(1, {y}, 1, {flick()}, {min(1.6, sp * 5):.2f})")
        if round(y) != last:
            last = round(y); await count(r, cells[last]["n"]); r.ev("frame")
        if sp > 0.02: r.ev("rattle")
        await r.frame()
    for i in range(1, 9): await r.js(f"__reel.film(1, 10, 1, {flick()})"); await r.frame()
    for i in range(1, 11):                    # push into the blank 13
        await r.js(f"__reel.film(1, 10, {1 + 3.2 * ease(i / 10) ** 2}, 1)"); await r.frame()
    await r.js("__reel.flash(1); __reel.film(0)")


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
            await count(r, 1); await cap(r, "rule")
            await r.frame(30)
            tip = await r.js("__ar.S.pts.reduce((m, p) => Math.hypot(...p) > Math.hypot(...m) ? p : m)")
            arm = (tip[0] * 0.55, tip[1] * 0.55)            # inside an arm of the star
            q2 = await r.scr(*arm)
            await r.move(*q2, 16)
            if not await until(r, "__ar.S.ended", 30):       # the swing missed the arm: the next blow lands on it
                await r.js(f"__ar.S.lastHit = 0; __ar.hit({{sx: {arm[0]}, sy: {arm[1]}, px: (0.5 + {arm[0]} * 0.5) * 512, py: (0.5 + {arm[1]} * 0.5) * 512, onCandy: true}}, 1.2)")
                await r.frame()
            r.ev("snap"); await cap(r, ""); r.zt = 1.0
            await r.diag("try 1")
            await die(r)

            # ---- 2번째: careful, but the clock runs out (11-08: a big clock, so the death reads as time) ----
            await again(r, 2, "needle")
            r.rec = False   # two minutes in: the groove is already round the whole star, the outside still on
            await r.js("__ar.carveAll(0.55); __ar.WT.timeLeft = 5.95; __ar.WT.running = true; __reel.timer(0); __reel.clock('0:06', 1)")
            r.rec = True
            path = await r.outline(0, 60, 60)
            q = await r.scr(*path[0]); r.mouse = q; await pg.mouse.move(*q)
            r.hand_on = True; r.zt = 1.25
            await r.press(True); r.hk = 0.95
            await cap(r, "time")
            for i in range(3, 0, -1):   # the white fades off the new try
                await r.js(f"__reel.flash({(i - 1) / 3})"); await r.frame()
            k, t0 = 0, 6
            while not await r.js("__ar.S.ended") and k < 260:
                await r.trace(path[min(k // 3 + 1, len(path) - 1)])
                s = await r.js("Math.max(0, Math.ceil(__ar.WT.timeLeft))")
                if s != t0: r.ev("tock" if s <= 3 else "tick"); t0 = s
                await r.js(f"__reel.clock('0:0{s}', 1, {str(s <= 3).lower()})")
                if k % 8 == 0: r.ev("scratch")
                k += 1
            await r.js("__reel.clock('0:00', 1, true)")
            r.ev("hush"); await cap(r, "시간 종료"); r.zt = 1.0
            await r.diag("try 2")
            await r.frame(14)   # the clock at 0:00, held; then it gives way to the reason
            await r.js("__reel.clock(null, 0)"); await cap(r, "over")
            await r.frame(10)
            await die(r)

            # ---- tries 3..12 run past as film ----
            await film(r, await stills(r))

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
