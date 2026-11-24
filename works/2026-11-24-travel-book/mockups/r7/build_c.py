# Generates S/mock/r5/c.html (theme c, round 7 + crit round 1) from DIRECTION.md.
import re
import sys
from pathlib import Path
R5 = Path(__file__).resolve().parents[2] / "mock" / "r5"
sys.path.insert(0, str(R5))
import jeju

ORDER = ["hyeopjae", "sinchang", "udo", "hado", "woljeong", "seongsan", "eorimok", "wonmul", "cafe"]
PTS = [jeju.xy(jeju.PLACES[k]) for k in ORDER]
COAST, UDO = jeju.smooth(jeju.COAST), jeju.smooth(jeju.UDO)
VB0 = (12.0, 4.0)  # map-space origin
SHORT = ["협재", "신창", "우도", "하도", "월정리", "성산", "어리목", "원물오름", "귤나무 카페"]
TIMES = ["13:20", "17:40", "09:42", "14:10", "16:05", "19:05", "08:30", "11:10", "13:30"]
EAST = [4, 3, 5, 2]  # 0-based east pins (05,04,06,03) pulled to a row, ordered by dot x asc
PAGES = [2, 3, 4, 5, 6, 7, 8, 9, 10]


def mapblock(x0: float, y0: float, w: float, box: int, pulled: list, sea: bool, refs: bool, rel: dict | None = None) -> str:
    """Page-coordinate SVG. sea=True: island filled with paper on an acc page. East pins sit in a row above the
    island; elbow leaders (up from the dot, across, up to the box) with mid levels decreasing left->right never cross."""
    s = w / 276.0
    P = [(x0 + (a - VB0[0]) * s, y0 + (b - VB0[1]) * s) for a, b in PTS]
    tf = f'transform="translate({x0 - VB0[0] * s:.2f} {y0 - VB0[1] * s:.2f}) scale({s:.4f})"'
    line = lambda pts, extra="": '<polyline points="%s" fill="none" stroke="var(--ink)" stroke-width="1" stroke-linejoin="round"%s/>' % (
        " ".join(f"{a:.1f},{b:.1f}" for a, b in pts), extra)
    land = ('fill="var(--paper)" stroke="none"' if sea else
            'fill="var(--land)" stroke="none"')
    svg = ['<svg class="map" viewBox="0 0 330 440" aria-hidden="true">',
           f'<g {tf} {land}><path d="{COAST}" vector-effect="non-scaling-stroke"/><path d="{UDO}" vector-effect="non-scaling-stroke"/></g>',
           line(P[0:2]), line(P[2:6]), line(P[6:9])]
    # chips for the remaining pins sit straight above/below their dot: rel[i] = chip-centre offset from the dot
    pulled = list(pulled) + [(i, None, P[i][1] + d - box / 2, None) for i, d in (rel or {}).items()]
    anchor = {}
    # pulled: (pin, box x or None = straight above/below the dot, box y, elbow level or None = straight leader)
    for i, bx, by, m in pulled:
        px, py = P[i]
        bx = px - box / 2 if bx is None else bx
        anchor[i] = (bx, by)
        cx, ey = bx + box / 2, (by + box if by < py else by)
        pts = f"{px:.1f},{py:.1f} {cx:.1f},{ey}" if m is None else f"{px:.1f},{py:.1f} {px:.1f},{m} {cx:.1f},{m} {cx:.1f},{ey}"
        svg.append(f'<polyline points="{pts}" fill="none" stroke="var(--ink)" stroke-width="1"/>'
                   f'<rect x="{px - 2.5:.1f}" y="{py - 2.5:.1f}" width="5" height="5" fill="var(--ink)"/>')
    out = []
    for i, (px, py) in enumerate(P):
        bx, by = anchor.get(i, (px - box / 2, py - box / 2))
        out.append(f'<i class="p nb{" dk" if sea else ""}" style="--x:{bx:.1f};--y:{by:.1f};--w:{box};--h:{box}">{i + 1:02d}</i>')
        if refs:
            t = f'{PAGES[i]:02d}쪽'
            out.append(f'<span class="p cp ml" style="--x:{bx + box + 3:.1f};--y:{by + 2:.1f};--w:30">{t}</span>')
    return "\n    ".join(["\n".join(svg) + "</svg>"] + out)



def img(key: str, x, y, w, h, pos: str = "50% 50%", extra: str = "", st: str = "") -> str:
    return f'<div class="p ph{extra}" style="--x:{x};--y:{y};--w:{w};--h:{h}"><img src="{{{{{key}}}}}" alt="" style="object-position:{pos};{st}"></div>'



def block(n: int, name: str, meta: str, memo: str, nxt: str, w: float = 190, y: float = 278, ny: float = 384) -> str:
    head, meta_p = name, f'<p class="cp">{meta}</p>'
    return (f'<div class="p blk" style="--x:20;--y:{y};--w:{w}"><h4 class="nm"><i class="nb">{n:02d}</i>{head}</h4>'
            f'{meta_p}<p class="bd">{memo}</p></div>'
            + (f'<p class="p nx" style="--x:20;--y:{ny};--w:{w}">{nxt}</p>' if nxt else ''))



DAYY = [278, 322, 366]
RUN = ["첫째 날 · 06.12 금", "둘째 날 · 06.13 토", "셋째 날 · 06.14 일"]


def folio(run: str, no: str, cls: str = "") -> str:
    return f'<div class="fo{cls}"><span>{run}</span><span>{no}</span></div>'


def page(parts: list) -> str:
    return '  <div class="pg"><div class="in">\n    ' + "\n    ".join(parts) + "\n  </div></div>"


STRIP2 = [(0, 126), (136, 126)]
STRIP3 = [(0, 80.7), (90.7, 80.7), (181.3, 80.7)]


FIRST = {'04', '08', '02'}


def module_c(big, cells, blk, no, day):
    geo = STRIP2 if len(cells) == 2 else STRIP3
    parts = [img(big[0], 0, 0, 210, 262, big[1])]
    parts += [img(k, 220, cy, 110, ch, pos) for (k, pos), (cy, ch) in zip(cells, geo)]
    return page(parts + [blk, folio(RUN[day - 1] if no in FIRST else '', no)])


def module_a(hero, sq, blk, no, day):
    return page([img(hero[0], 0, 0, 330, 262, hero[1]), img(sq[0], 220, 286, 90, 90, sq[1]), blk, folio(RUN[day - 1] if no in FIRST else '', no)])


def module_b(key, pos, n, name, meta, memo, no, day, grad=False, play=False, st="", play_y=200, note="", big=False):
    """B: true full bleed; white text bottom-left over the photo. grad = 160px ink 0->40% at the foot (bright feet only)."""
    parts = [img(key, 0, 0, 330, 440, pos, " bleed" + ((" grad " + grad) if isinstance(grad, str) else (" grad" if grad else "")), st)]
    if play:
        parts.append(f'<div class="play" style="height:calc({play_y * 2}*var(--u))"><span></span></div>')
    parts.append(f'<div class="blk ov"><h4 class="nm{' big' if big else ''}"><i class="nb">{n:02d}</i>{name}</h4><p class="cp">{meta}</p><p class="bd">{memo}</p>{f'<p class="cp nt">{note}</p>' if note else ''}</div>')
    parts.append(folio(RUN[day - 1] if no in FIRST else '', no, ' w'))
    return page(parts)


# ---------------- covers
cover1 = page([
    '<div class="sea"></div>',
    '<h1 class="p xl on" style="--x:20;--y:20;--w:290">제주</h1>',
    '<p class="p cp on" style="--x:20;--y:100;--w:150">2026.06.12–06.14</p>',
    mapblock(6, 195, 322, 20, [(4, None, 138, None), (3, None, 138, None), (2, None, 138, None), (5, None, 318, None)], True, False,
             {0: -24, 8: -24, 6: -24, 7: 24, 1: 24}),
    '<p class="p cp on" style="--x:20;--y:402;--w:290">2박 3일 · 9곳 · 214km</p>',
])
lst = "".join(f'<li><span>{i:02d}</span>{n}</li>' for i, n in enumerate(SHORT, 1))
cover2 = page([
    '<h1 class="p xl" style="--x:20;--y:20;--w:200">제주</h1>',
    '<p class="p cp ik" style="--x:20;--y:100;--w:290">2026.06.12–06.14</p>',
    img("udo", 110, 120, 220, 320, "22% 30%"),
    '<div class="p accol" style="--x:0;--y:120;--w:110;--h:320"></div>',
    f'<ol class="p clist" style="--x:20;--y:136;--w:84">{lst}</ol>',
])
cover3 = page([
    img("doldam", 0, 0, 330, 440, "50% 40%", " bleed"),
    '<div class="p band c3" style="--x:0;--y:246;--w:330;--h:88">'
    '<h1 class="tl">제주</h1><span class="rg"><i class="nb">01</i><i class="dash"></i><i class="nb">09</i></span>'
    '<p class="cp ik">2박 3일 · 9곳 · 46장</p></div>',
])

# ---------------- book
p01 = page([
    '<h2 class="p ttl" style="--x:20;--y:20;--w:190">제주 9곳</h2>',
    '<p class="p cp" style="--x:20;--y:60;--w:180">2박 3일 · 214km</p>',
    mapblock(20, 150, 290, 20, [(4, None, 104, None), (3, None, 82, None), (2, None, 112, None), (5, None, 236, None)], False, False,
             {0: -24, 8: -24, 6: -24, 7: 24, 1: 24}),
    *[f'<ol class="p idx" style="--x:{20 + 100 * c};--y:345;--w:{82 + 8 * (c == 2)}">' + ''.join(f'<li><span class="n">{n:02d}</span><b>{SHORT[n - 1]}</b></li>' for n in range(3 * c + 1, 3 * c + 4)) + '</ol>' for c in range(3)],
    folio("06.12–06.14", "01"),
])



p02 = module_c(("hyeopjae_2", "50% 60%"), [("hyeopjae", "40% 50%"), ("d9", "50% 60%")],
               block(1, "협재 해변", "13:20 · 한림읍 협재리 · 맑음 24°", "비양도가 흐렸다가 맑아짐. 물은 생각보다 차가움.",
                     "다음 → 02 신창 풍차해안도로", w=290), "02", 1)
p03 = module_b("road", "50% 50%", 2, "신창<br>풍차해안도로", "17:40 · 한경면 신창리", "해 질 무렵, 바람 셈.", "03", 1, grad=True, big=True)
p04 = module_a(("udo", "20% 60%"), ("udo_2", "50% 50%"),
               block(3, "우도", "09:42 · 우도면 · 바람 강함 21°", "도항선 15분.<br>모자가 두 번 날아감.", "다음 → 04 하도"), "04", 2)
p05 = module_b("haenyeo", "50% 50%", 4, "하도<br>해녀 탈의장", "14:10 · 구좌읍 하도리", "해녀 두 분이 그물 정리 중.",
               "05", 2, grad=True, play=True, st="transform:scale(1.3);transform-origin:50% 100%", play_y=110, note="영상 0:03 · PDF에서는 사진 한 장으로")
p06 = module_c(("doldam", "50% 45%"), [("d7", "50% 50%"), ("woljeongri_2", "55% 50%"), ("d2", "50% 55%")],
               block(5, "월정리", "16:05–17:20 · 구좌읍 월정리", "돌담과 밭, 분홍 벤치,<br>바다 찍는 사람, 커피 두 잔.",
                     "다음 → 06 성산일출봉", w=290), "06", 2)
p07 = page([img("seongsan", 0, 0, 330, 330, "50% 60%"),
            block(6, "성산일출봉", "19:05 · 성산읍 성산리", "해가 넘어간다.", "", w=290, y=346), folio("", "07")])
p08 = module_b("hallasan", "50% 40%", 7, "한라산<br>어리목", "08:30 · 흐림 22°", "안개. 계단만 보고 걸음.", "08", 3, big=True)
p09 = page([
    img("oreum", 170, 0, 160, 440, "74% 50%"),
    img("oreum_2", 20, 20, 140, 230, "50% 50%"),
    '<div class="p blk" style="--x:20;--y:266;--w:140"><i class="nb">08</i><h4 class="nm nm0">원물오름</h4>'
    '<p class="cp">11:10 · 동광리</p><p class="bd">억새가 키만 하다.<br>멀리 한라산.</p></div>',
    folio("", "09", " sd"),
])
p10 = module_b("tangerine_2", "50% 40%", 9, "귤나무 카페 마당", "13:30", "비행기 시간까지 귤 구경. 하나 까먹음.", "10", 3, grad=True)
# last page: route ledger, no photo
NAMES = ["협재 해변", "신창 풍차해안도로", "우도", "하도 해녀 탈의장", "월정리", "성산일출봉", "한라산 어리목", "원물오름", "귤나무 카페 마당"]
GAP = [26, 40, 24, 24, 24, 40, 24, 24]  # centre-to-centre; a day change gets 36
ys = [96.0]
for g in GAP:
    ys.append(ys[-1] + g)
led = [f'<i class="p vl" style="--x:37.5;--y:{ys[0]};--w:1;--h:{ys[-1] - ys[0]}"></i>',
       f'<p class="p cp dh" style="--x:58;--y:{ys[0] - 27};--w:252">첫째 날 · 06.12 금</p>']
for i, y in enumerate(ys):
    led.append(f'<i class="p nb dk" style="--x:28;--y:{y - 10};--w:20;--h:20">{i + 1:02d}</i>'
               f'<p class="p lr" style="--x:58;--y:{y - 8};--w:252"><b>{NAMES[i]}</b><span class="cp">{TIMES[i]}</span></p>')
for d, i in (("둘째 날 · 06.13 토", 2), ("셋째 날 · 06.14 일", 6)):
    led.append(f'<p class="p cp dh" style="--x:58;--y:{ys[i] - 27};--w:252">{d}</p>')
p11 = page(['<div class="sea"></div>'] + led + [
    '<h2 class="p ttl" style="--x:20;--y:20;--w:290">제주 → 김포</h2>',
    f'<i class="p rule" style="--x:20;--y:{ys[-1] + 16};--w:290"></i>',
    f'<p class="p ttl" style="--x:20;--y:{ys[-1] + 22};--w:180">214km</p>',
    '<p class="p bd" style="--x:20;--y:386;--w:290">귤 한 봉지 사서 공항으로.</p>',
    folio("", "11", " on"),
])


CSS = """<style>
/* Travel notebook r7. Design px on a 330x440 page, scaled by --u.
   Grid: M 20, 6 cols x 40, G 10 (col c at 20+50(c-1)). Lines: photo floor y262, text y278, rule y384, folio y420.
   Type: XL 72 (cover) / L 32 / B 11.5 / C 9.5 (digits DM Mono, Hangul Paperlogy 500). */
.t-c{--paper:#FBF6EA;--ink:#151515;--soft:#6B665C;--line:#D9D2C2;--acc:#E8553B;--on:#000000;--acct:#C63F26;--land:#EFE7D3;
  --f-ko:"Paperlogy",sans-serif;--f-mono:"DM Mono","Paperlogy",monospace}
.t-c .pg{container-type:inline-size;background:var(--paper)}
.t-c .in{--u:calc(100cqw/330);position:absolute;inset:0;color:var(--ink);font-family:var(--f-ko);background:var(--paper);overflow:hidden;word-break:keep-all}
.t-c .in *{margin:0;padding:0;font-style:normal}
.t-c .p{box-sizing:border-box;position:absolute;left:calc(var(--x,0)*var(--u));top:calc(var(--y,0)*var(--u));width:calc(var(--w)*var(--u))}
.t-c .p[style*="--h"]{height:calc(var(--h)*var(--u))}
.t-c .bleed{inset:0;width:auto;height:auto}
.t-c .ph{overflow:hidden;background:var(--line)}
/* type */
.t-c .xl{font:700 calc(72*var(--u))/1 var(--f-ko);letter-spacing:-.05em;margin-left:-.04em}
.t-c .ttl,.t-c .nm,.t-c .c3 .tl{font:700 calc(32*var(--u))/1.12 var(--f-ko);letter-spacing:-.04em;text-wrap:balance}
.t-c .bd{font:400 calc(11.5*var(--u))/1.55 var(--f-ko);letter-spacing:-.015em;word-spacing:.08em;text-wrap:pretty}
.t-c .cp,.t-c .fo,.t-c .nb,.t-c .idx,.t-c .clist,.t-c .kt,.t-c .tab{font:500 calc(9.5*var(--u))/1.35 var(--f-mono);letter-spacing:.02em;font-variant-numeric:tabular-nums}
/* Hangul inside mono lines: Paperlogy 500, tighter tracking (DM Mono has no Hangul) */
.t-c :is(.cp,.fo,.clist,.kt) .k,.t-c .idx li.h .k{font-family:var(--f-ko);font-weight:500;color:inherit;letter-spacing:0}
.t-c .cp{color:var(--soft)}
.t-c .cp.ik{color:var(--ink)}
.t-c .r{text-align:right}
/* number box */
.t-c .nb{display:inline-grid;place-items:center;box-sizing:border-box;width:calc(20*var(--u));height:calc(20*var(--u));background:var(--acc);color:var(--on);line-height:1;letter-spacing:0}
.t-c .p.nb{display:grid;z-index:1}
.t-c .map{position:absolute;inset:0;width:100%;height:100%;overflow:visible}
.t-c .p.ml{white-space:nowrap;width:auto;z-index:1;background:var(--paper);padding:0 calc(2*var(--u))}
.t-c .p.ml.r{transform:translateX(-100%)}
.t-c .ml .n{color:var(--soft)}
.t-c .rg{display:flex;align-items:center;gap:calc(5*var(--u))}
.t-c .dash{width:calc(10*var(--u));border-top:1px solid var(--ink)}
/* place block from y278, next line on fixed rule y384 */
.t-c .blk{display:flex;flex-direction:column}
.t-c .nm{position:relative;padding-left:calc(28*var(--u));margin-bottom:calc(3*var(--u))}
.t-c .nm .nb{position:absolute;left:0;top:calc(8*var(--u))}
.t-c .blk .bd{margin-top:calc(4*var(--u))}
.t-c .nx{padding-top:calc(6*var(--u));border-top:.75px solid var(--line);color:var(--ink);font:500 calc(11.5*var(--u))/1.3 var(--f-ko);letter-spacing:-.015em}
/* B band: full width paper from y322 (y286 for 2-line names) to the foot */
.t-c .band.b{position:absolute;left:0;right:0;bottom:0;top:calc(var(--y)*var(--u));background:var(--paper);z-index:2}
.t-c .band.b .blk{top:calc(10*var(--u))}
.t-c .band.two .blk{top:calc(8*var(--u))}
.t-c .band.two .nm{line-height:1.05}
.t-c .band.two .bd{margin-top:calc(2*var(--u))}
/* day tab: 12 wide on the right edge, day digit inside */
.t-c .tab{position:absolute;right:0;top:calc(var(--ty)*var(--u));width:calc(12*var(--u));height:calc(44*var(--u));background:var(--acc);color:var(--on);display:grid;place-items:center;line-height:1;z-index:3}
/* folio */
.t-c .fo{position:absolute;left:calc(20*var(--u));right:calc(20*var(--u));top:calc(411*var(--u));display:flex;justify-content:space-between;color:var(--soft);line-height:1;z-index:3}
.t-c .fo>span:last-child{color:var(--ink)}
/* day index, 3 columns */
.t-c .idx{list-style:none}
.t-c .idx li{display:flex;align-items:baseline;gap:calc(6*var(--u));height:calc(18*var(--u));line-height:calc(18*var(--u))}
.t-c .idx li .n{color:var(--acct)}
.t-c .idx li b{flex:1;font:500 calc(11.5*var(--u))/inherit var(--f-ko);letter-spacing:-.02em;white-space:nowrap}
.t-c .idx li .pn{color:var(--soft)}
.t-c .idx li.h{border-top:.75px solid var(--line);color:var(--soft);height:calc(20*var(--u))}
.t-c .clist{list-style:none;color:var(--ink)}
.t-c .clist li{display:flex;gap:calc(8*var(--u));line-height:calc(15.5*var(--u))}
.t-c .clist li>span:first-child{color:var(--acc)}
/* cover 3 band */
.t-c .c3{background:var(--paper);padding:calc(12*var(--u)) calc(20*var(--u)) 0;z-index:2}
.t-c .c3 .tl{font-size:calc(48*var(--u));line-height:1}
.t-c .c3 .rg{position:absolute;right:calc(20*var(--u));top:calc(16*var(--u))}
.t-c .c3 .cp{margin-top:calc(8*var(--u))}
/* colophon table */
.t-c .kt div{display:flex;flex-wrap:wrap;border-top:.75px solid var(--line);padding:calc(5*var(--u)) 0}
.t-c .kt div:last-child{border-bottom:.75px solid var(--line)}
.t-c .kt dt{color:var(--soft);width:calc(30*var(--u))}
.t-c .kt dd{width:calc(42.5*var(--u));color:var(--ink)}
.t-c .kt dd.w2{width:auto}
/* round 2 */
.t-c .sea{position:absolute;inset:0;background:var(--acc)}
.t-c .on,.t-c .cp.on{color:var(--on)}
.t-c .nb.dk{background:var(--ink);color:var(--paper)}
.t-c .blk .bd,.t-c .lr+.bd,.t-c .p.bd{font-weight:500;color:var(--ink)}
.t-c .tm{position:absolute;right:0;bottom:calc(7*var(--u));color:var(--ink)}
.t-c .grad::after{content:"";position:absolute;left:0;right:0;bottom:0;height:calc(220*var(--u));background:linear-gradient(transparent,rgba(21,21,21,.5) 35%,rgba(21,21,21,.72))}
.t-c .blk.ov{position:absolute;left:calc(20*var(--u));right:calc(20*var(--u));bottom:calc(36*var(--u));z-index:2;color:#fff}
.t-c .blk.ov .cp{color:rgba(255,255,255,.85)}
.t-c .blk.ov .bd{color:#fff}
.t-c .fo.w,.t-c .fo.w>span:last-child{color:#fff}
.t-c .play{height:calc(400*var(--u));bottom:auto}
.t-c .vl{background:var(--ink)}
.t-c .lr{display:flex;justify-content:space-between;align-items:baseline;font:500 calc(11.5*var(--u))/1.4 var(--f-ko);letter-spacing:-.015em}
.t-c .lr .cp{color:var(--ink)}
.t-c .cp.ac{color:var(--acc)}
.t-c .dh{border-top:.75px solid var(--line);padding-top:calc(3*var(--u))}
.t-c .rule{height:0;border-top:1px solid var(--ink)}
.t-c .nm0{padding-left:0;margin-top:calc(8*var(--u))}
.t-c .fo.on,.t-c .fo.on>span:last-child{color:var(--on)}
.t-c .fo.sd>span:last-child{color:#fff}
.t-c .sea~.p .cp,.t-c .sea~.p.cp,.t-c .sea~.p.ttl,.t-c .sea~.p.bd,.t-c .sea~.p.lr,.t-c .sea~.p .lr .cp{color:var(--on)}
.t-c .sea~.vl,.t-c .sea~.rule{background:var(--on);border-color:var(--on)}
.t-c .sea~.p.dh{border-color:var(--on)}
.t-c .nx0{margin-top:calc(14*var(--u))}
.t-c .sea~.p .cp,.t-c .sea~.p.cp,.t-c .sea~.p.lr .cp,.t-c .sea~.fo{font-size:calc(10*var(--u))}
.t-c .nm.big{font-size:calc(48*var(--u));line-height:1.02}
.t-c .nm.big .nb{top:calc(14*var(--u))}
.t-c .accol{background:var(--acc)}
.t-c .accol~.clist,.t-c .accol~.clist li>span:first-child{color:var(--on)}
.t-c .blk.ov .nt{margin-top:calc(4*var(--u))}
</style>"""

pal = ('[{"--acc":"#E8553B","--on":"#000000","--acct":"#C63F26"},{"--acc":"#2F5FD0","--on":"#ffffff","--acct":"#2F5FD0"},'
       '{"--acc":"#1F6B4A","--on":"#ffffff","--acct":"#1F6B4A"}]')
NOTES = [
    "그리드(330×440 쪽): 바깥 여백 M 20, 6단×40, 단 간격 G 10 → c단 시작 x = 20 + 50(c−1). 가로 기준선: 사진 바닥 y262 · 글 시작 y278 · 다음 줄 괘선 y384 · 폴리오 y420.",
    "글꼴: 장소명 L Paperlogy 700·32·−0.04em·1.12(최대 2줄) / 메모 B 500·11.5·1.55 먹 100%(내 말이라 메타보다 진하게) / 메타·번호·폴리오 C 9.5·500 — 숫자는 DM Mono tabular, 한글은 Paperlogy 500·−0.01em. 표지 제목만 XL 72.",
    "번호 하나가 책을 꿴다: 장소 번호 01~09 = 지도 핀 = 장소 쪽 머리 = 마지막 정산표. 번호 상자 18×18 직각(acc + on 숫자), 지도 핀 16~18. 지도 쪽 핀 옆에 `03쪽` 쪽 참조.",
    "모듈은 사진 장수로 자동: 1장 B(진짜 풀블리드, 글은 사진 위 왼쪽 아래 흰색, 바닥 y404) · 2장 A(히어로 330×262 + 정사각 90) · 3~4장 C(큰 세로 210×262 + 오른쪽 띠 110폭 2~3칸). 같은 모듈 연속 금지.",
    "A·C 글 블록은 y278: 번호+장소명 → 메타 한 줄 `시각 · 읍면리 · 날씨`(쪽당 회색 줄은 이 한 줄뿐) → 메모 ≤2줄. `다음 →` 줄은 C·A 쪽에서만, 한 권 3번까지. 폴리오 왼쪽 날짜는 그날 첫 쪽에만. 풀블리드 B 는 4쪽 안팎, 연속 금지.",
    "사진 위 글자 예외 하나: 바닥이 밝은 B 사진만 아래 160px 먹 0→40% 그라디언트. 그 밖에 그림자·질감 없음.",
    "폴리오 y420 전 쪽 같은 자리: 왼쪽 `첫째 날 · 06.12 금`, 오른쪽 쪽번호 2자리(B 는 흰색).",
    "색: 종이 #FBF6EA · 먹 #151515 · 회 #6B665C · 괘선 #D9D2C2 고정, 포인트 1색(토마토/코발트/숲)은 본문에서 번호 상자·목록 숫자·거리에만 — 쪽당 ≤5%. 예외는 기본 표지 ① 하나: 바탕 전면 acc, 섬은 종이색 채움, 핀은 먹 상자 + 종이색 숫자.",
    "지도: 윤곽 0.75px 회색(표지는 면), 경로 1px 먹 한 줄(01→09, 밤을 넘는 구간 2-2 점선). 16px 안에 몰린 핀은 섬 위 한 줄로 빼고, 점 3px에서 위→옆→위로 꺾은 0.75px 지시선(왼쪽 핀일수록 낮게 꺾어 교차 0).",
    "마지막 쪽은 사진 없는 동선 정산표: 세로 1px 선 위 번호 상자 9개, 줄마다 장소·시각, 날 바뀔 때만 괘선+날 머리글, 구간 거리는 절반 이상 있을 때만 줄 사이 acc `↓ 19km`(없으면 합계만), 합계 L 32 `214km`. 영상 쪽은 B + ▶ 하나(인물 위 하늘), PDF 는 ▶ 빼고 사진 한 장.",
]

body = "\n".join([
    '<h3 class="sub">표지 후보</h3>',
    '<div class="strip">', cover1, cover2, cover3, "</div>",
    '<div class="under"><span>① 색 바다 위 번호 지도(기본, 사진 없음, 팔레트 3색 = 표지 3종) ② 사진 한 장 블리드 + 지명 목록 ③ 풀블리드 + 종이 띠 라벨</span><span>3개</span></div>',
    '<h3 class="sub">책 본문</h3>',
    '<div class="strip">', p01, p02, p03, p04, p05, p06, p07, p08, p09, p10, p11, "</div>",
])
# wrap Hangul runs so mono lines set Hangul in Paperlogy (only text nodes, never attributes)
body = re.sub(r">([^<]+)<", lambda m: ">" + re.sub(r"[가-힣]+(?: [가-힣]+)*", lambda k: f'<span class="k">{k.group(0)}</span>', m.group(1)) + "<", body)

html = "\n".join([
    "<!-- fonts: DM+Mono:wght@500 -->",
    "<!-- kfonts: Paperlogy:400;500;700 -->",
    CSS,
    f"<section class=\"t-c\" data-palettes='{pal}'>",
    '<div class="head"><h2>여행 수첩</h2><p class="idea">번호 하나가 지도·목록·장소 쪽을 꿰는 내 여행 안내서 — 직각 사진, 괘선, 포인트색은 번호에만</p><button class="shuffle" type="button">색 조합 바꾸기</button></div>',
    body,
    '<div class="under"><span>지도 M → 협재 C → 신창 B → 우도 A → 하도 영상 B → 월정리 C → 성산 A → 어리목 B → 원물오름 반풀블리드 → 귤나무 카페 B → 동선 정산표 K</span><span>11쪽</span></div>',
    '<details class="notes"><summary>디자인 규칙</summary><ul>',
    *[f"<li>{n}</li>" for n in NOTES],
    "</ul></details>",
    "</section>",
])
(R5 / "c.html").write_text(html + "\n", encoding="utf-8")
print("ok", len(html))
