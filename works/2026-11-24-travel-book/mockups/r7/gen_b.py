"""Generate mock/r5/b.html (theme b, round 7, after crit 1) — 46-tick ledger book."""
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "mock" / "r5" / "b.html"

# 46 shot times, chronological (No.01..46). day1 = 1-10, day2 = 11-36, day3 = 37-46
TIMES = ("13:20 13:24 13:31 13:47 14:02 14:15 17:12 17:25 17:33 17:40 "
         "09:42 09:44 09:51 10:03 10:12 10:20 10:31 10:40 10:55 11:08 11:21 14:10 14:12 14:19 14:26 "
         "16:05 16:18 16:40 17:08 17:11 17:15 17:20 19:05 19:12 19:20 19:31 "
         "08:30 08:41 08:52 11:10 11:18 11:25 11:33 11:47 13:30 13:52").split()
assert len(TIMES) == 46
DAYS = [(1, 10, "06.12"), (11, 36, "06.13"), (37, 46, "06.14")]
DAY_START = {11, 37}


def _min(t: str) -> int:
    h, m = t.split(":")
    return int(h) * 60 + int(m)


# longest stay per place (first-last shot): Udo 09:42-11:21 = 99 min beats Woljeong 16:05-17:20 = 75
assert _min("11:21") - _min("09:42") == 99 and _min("17:20") - _min("16:05") == 75 and _min("11:47") - _min("11:10") == 37

# longest gap inside a day (used on p11) — must match the copy
GAPS = [(_min(TIMES[i]) - _min(TIMES[i - 1]), i) for a, b, _ in DAYS for i in range(a, b)]
assert max(GAPS) == (177, 6), max(GAPS)  # 06.12 14:15 -> 17:12 = 2:57


def ticks(on: tuple[int, ...] = (), all_on: bool = False, cls: str = "") -> str:
    cells = []
    for i in range(1, 47):
        c = []
        if i == 1 or i in DAY_START:
            c.append("d")
        if all_on or i in on:
            c.append("on")
        cells.append(f'<i class="{" ".join(c)}"></i>' if c else "<i></i>")
    return f'<div class="tk {cls}">{"".join(cells)}</div>'


def folio(head: str, n: int, white: bool = False, ink: bool = False) -> str:
    st = ' style="color:#fff"' if white else (' style="color:var(--ink)"' if ink else "")
    return f'<span class="fl"{st}>{head}</span><span class="fr"{st}>{n:02d}/11</span>'


def cap(no: int, place: str, top: float, left: float = 6, width: float = 88, extra: str = "") -> str:
    pl = f"<span>{place}</span>" if place else ""
    return (f'<p class="cap" style="left:{left}cqw;top:{top}cqw;width:{width}cqw">'
            f'<span class="mo">No.{no:02d}</span><span class="mo">{TIMES[no-1]}</span>{pl}{extra}</p>')


def fcap(no: int, place: str, pre: str = "", lead: str = "") -> str:
    """caption inside a full-bleed photo, bottom-left, above the ticks"""
    return (f'<p class="cap in" style="left:6cqw;bottom:14cqw;width:88cqw">{pre}{lead}'
            f'<span class="mo">No.{no:02d}</span><span class="mo">{TIMES[no-1]}</span><span>{place}</span></p>')


def ph(key: str, x: float, y: float, w: float, h: float, pos: str = "50% 50%") -> str:
    return (f'<div class="ph" style="left:{x}cqw;top:{y}cqw;width:{w}cqw;height:{h}cqw">'
            f'<img src="{{{{{key}}}}}" alt="" style="object-position:{pos}"></div>')


def grid_page(n: int, head: str, title: str, cells: list[tuple[str, int, str]], values: list[tuple[str, str]]) -> str:
    # 3x3 of 1:1, cell 28cqw, gap G; 8 photos + 1 value cell (last). caption = No + time only
    xs = (6, 36, 66)
    ys = (24, 54, 84)
    nos = [no for _, no, _ in cells]
    span = f'No.{nos[0]:02d}–{nos[-1]:02d} · {TIMES[nos[0] - 1]}–{TIMES[nos[-1] - 1]}'
    out = [f'<div class="pg">{folio(head, n)}',
           f'<p class="cap" style="left:6cqw;top:17cqw;width:88cqw"><span class="memo">{title}</span><span class="r mo">{span}</span></p>']
    slots = [(x, y) for y in ys for x in xs]
    for (key, no, pos), (x, y) in zip(cells, slots):
        out.append(ph(key, x, y, 28, 28, pos))
    for (value, note), (x, y) in zip(values, slots[len(cells):]):
        out.append(f'<div class="more" style="left:{x}cqw;top:{y}cqw;width:28cqw;height:28cqw">'
                   f'<p class="lb" style="margin:0 0 1cqw">{note}</p><p class="mv">{value}</p></div>')
    out.append("</div>")
    return "\n  ".join(out)


CSS = """<style>
/* grid: M 6cqw, 4 cols, G 2cqw -> col i starts 6+22.5i, col w 20.5, 2-col 43. page h 133.33cqw; folio top 6, ticks bottom 6 */
.t-b{--paper:#ECECE8;--ink:#141414;--rule:rgba(20,20,20,.28);--spot:#2447E6;
  --f:"Wanted Sans",system-ui,sans-serif;--mono:"Red Hat Mono","Wanted Sans",monospace}
.t-b .pg{background:var(--paper);color:var(--ink);container-type:inline-size;font:500 3.4cqw/1.5 var(--f);letter-spacing:.02em;word-break:keep-all;font-variant-numeric:tabular-nums}
.t-b .pg>*{position:absolute;margin:0}
.t-b .pg p{margin:0}
.t-b .spot{background:var(--spot);color:#fff;--rule:rgba(255,255,255,.55)}
.t-b .ph{overflow:hidden}
/* folio */
.t-b .fl,.t-b .fr{top:6cqw;font:500 3cqw/1 var(--mono);letter-spacing:0;z-index:2}
.t-b .fl{left:6cqw}.t-b .fr{right:6cqw}
.t-b .wh .fl,.t-b .wh .fr{color:#fff}
.t-b .tg{left:0;right:0;top:0;height:20cqw;background:linear-gradient(rgba(0,0,0,.3),rgba(0,0,0,0));z-index:1}
.t-b .bg{left:0;right:0;bottom:0;height:50cqw;background:linear-gradient(rgba(0,0,0,0),rgba(0,0,0,.4));z-index:1}
/* 46 ticks: one per photo; off 1px x 2cqw, this page's photos 2px x 4cqw in spot, day start 3cqw */
.t-b .tk{left:6cqw;right:6cqw;bottom:6cqw;height:4cqw;display:flex;justify-content:space-between;align-items:flex-end;z-index:2}
.t-b .tk i{width:1px;height:1.6cqw;background:rgba(20,20,20,.3)}
.t-b .tk i.d{height:2.6cqw}
.t-b .tk i.on{background:var(--spot);width:1px;height:3.4cqw}
.t-b .wh .tk i,.t-b .spot .tk i{background:rgba(255,255,255,.45)}
.t-b .wh .tk i.on{background:#fff}
.t-b .spot .sr i{background:#fff}
.t-b .spot .hx s{opacity:.85}
.t-b .spot .lb{opacity:.85}
/* type: XL 40 / M 10 / S 3.4 (+ mono 3) */
.t-b .xl{font:800 40cqw/.82 var(--f);letter-spacing:-.05em;margin-left:-.05em;white-space:nowrap}
.t-b .mv{font:800 10cqw/1 var(--f);letter-spacing:-.03em;white-space:nowrap}
.t-b .u{font:700 3.4cqw/1 var(--f);letter-spacing:.02em;margin-left:1.2cqw}
.t-b .xl .u{margin-left:2cqw}
.t-b .lb{font:500 3.4cqw/1.5 var(--f);opacity:.62}
.t-b .memo{font:700 3.4cqw/1.5 var(--f)}
.t-b .mo{font:500 3cqw/1.5 var(--mono);letter-spacing:0}
.t-b .hd{left:6cqw;width:88cqw;font:700 3.4cqw/1.5 var(--f)}
.t-b .hd .mo{margin-right:2cqw}
/* caption = one table row */
.t-b .cap{display:flex;gap:2.4cqw;align-items:baseline;border-top:.5px solid var(--rule);padding-top:1cqw;font:500 3.4cqw/1.4 var(--f);white-space:nowrap;overflow:hidden}
.t-b .cap.in{color:#fff;border-top:0;padding:0;z-index:2;flex-wrap:wrap;row-gap:0}
.t-b .cap.in .memo{flex-basis:100%;margin-bottom:1cqw}
.t-b .cap .r{margin-left:auto}
.t-b .cap.sm{line-height:1.25;padding-top:.8cqw}
/* ruled value cells */
.t-b .cell{border-top:.5px solid var(--rule);padding-top:2cqw}
.t-b .cell .lb{margin-bottom:1cqw}
.t-b .more{border-top:.5px solid var(--rule);padding-top:2cqw;box-sizing:border-box}
.t-b .more>*{margin:0}
.t-b .more .lb{margin-top:2cqw}
/* table (05): time col1, place col2, bar cols 3-4 */
.t-b .tb{left:6cqw;width:88cqw;display:grid;grid-template-columns:20.5cqw 20.5cqw 1fr;column-gap:2cqw;align-items:center;border-top:.5px solid var(--rule);height:6.2cqw}
.t-b .tb>span:nth-child(2){font:700 3.4cqw/1 var(--f)}
.t-b .tb b{display:block;height:2cqw;background:var(--spot)}
.t-b .tb .bar{display:flex;align-items:center;gap:1.6cqw}
/* shot strip (08): one 2px spot mark per photo at its minute, 06-20h over 88cqw */
.t-b .sr{left:6cqw;width:88cqw;height:9cqw;border-bottom:.5px solid var(--rule)}
.t-b .sr i{position:absolute;bottom:0;width:2px;height:9cqw;background:var(--spot);transform:translateX(-1px)}
.t-b .sr s,.t-b .hx s{position:absolute;text-decoration:none;font:500 3cqw/1 var(--mono);letter-spacing:0;white-space:nowrap}
.t-b .sr s{top:-4.6cqw}
.t-b .sr s.r{transform:translateX(-100%)}
.t-b .hx{left:6cqw;width:88cqw;height:3cqw}
.t-b .hx s{top:0;transform:translateX(-50%);opacity:.62}
/* distance axis (01) */
.t-b .ax{left:6cqw;width:88cqw;height:1px;background:rgba(255,255,255,.75)}
.t-b .ax i{position:absolute;top:0;width:2.4cqw;height:2.4cqw;border-radius:50%;background:#fff;transform:translate(-50%,-50%)}
.t-b .ax s{position:absolute;text-decoration:none;font:500 3cqw/1 var(--mono);letter-spacing:0;white-space:nowrap}
.t-b .ax s.a{top:-6.5cqw}
.t-b .ax s.b{top:3.6cqw;font:700 3.4cqw/1 var(--f);transform:translateX(-50%)}
/* index cover */
.t-b .ix{left:6cqw;width:88cqw;display:grid;grid-template-columns:repeat(4,20.5cqw);column-gap:2cqw;font:500 3cqw/1.85 var(--mono);color:rgba(255,255,255,.8)}
</style>"""


def axis() -> str:
    km = [(31, ""), (44, ""), (97, "우도"), (108, ""), (121, ""), (139, ""), (178, "어리목"), (196, ""), (207, "")]
    km[0] = (31, "협재")
    dots = "".join(f'<i style="left:{k / 214 * 100:.2f}%"></i>' for k, _ in km)
    names = "".join(f'<s class="b" style="left:{k / 214 * 100:.2f}%">{n}</s>' for k, n in km if n)
    above = ('<s class="a" style="left:0">06.12 · 62km</s><s class="a" style="left:28.97%">06.13 · 88km</s>'
             '<s class="a" style="left:70.09%">06.14 · 64km</s>')
    return f'<div class="ax" style="top:82cqw">{dots}{above}{names}</div>'


def shot_strip() -> str:
    x0, span = 6 * 60, 14 * 60  # 06:00-20:00
    out = []
    notes = {0: ('<s style="left:{:.2f}%">06.12</s>'), }
    labels = {
        0: [("06.12 금", None), ("2시간 57분", (14 * 60 + 15 + 17 * 60 + 12) / 2)],
        1: [("06.13 토", None), ("우도 11", 9 * 60 + 42)],
        2: [("06.14 일", None)],
    }
    for d, (a, b, _) in enumerate(DAYS):
        marks = "".join(f'<i style="left:{(_min(TIMES[i - 1]) - x0) / span * 100:.2f}%"></i>' for i in range(a, b + 1))
        lab = ""
        for text, at in labels[d]:
            if at is None:
                lab += f'<s style="left:0">{text}</s>'
            elif text.startswith("2시간"):
                g0, g1 = 14 * 60 + 15, 17 * 60 + 12
                lab += f'<s style="left:{(at - x0) / span * 100:.2f}%;transform:translateX(-50%)">{text}</s>'
                lab += (f'<u style="position:absolute;top:-.6cqw;height:0;border-top:.5px solid currentColor;'
                        f'left:{(g0 - x0) / span * 100:.2f}%;width:{(g1 - g0) / span * 100:.2f}%"></u>')
            else:
                lab += f'<s style="left:{(at - x0) / span * 100:.2f}%">{text}</s>'
        out.append(f'<div class="sr" style="top:{76 + d * 14.5}cqw">{marks}{lab}</div>')
    hx = "".join(f'<s style="left:{(h * 60 - x0) / span * 100:.2f}%">{h:02d}</s>' for h in (9, 12, 15, 18))
    hx = '<s style="left:0;transform:none">06</s>' + hx
    hx += '<s style="left:100%;transform:translateX(-100%)">20시</s>'
    out.append(f'<div class="hx" style="top:{76 + 2 * 14.5 + 10.5}cqw">{hx}</div>')
    return "\n  ".join(out)


# GPS altitude (m) per photo, No.01..46 — p09 line; max must be No.37 = 970
ALT = [3, 2, 4, 3, 5, 2, 8, 6, 10, 12,
       3, 3, 4, 6, 12, 25, 40, 18, 9, 5, 4, 2, 3, 2, 4,
       12, 15, 10, 4, 3, 5, 6, 8, 10, 12, 9,
       970, 955, 940, 410, 438, 452, 446, 420, 95, 92]
assert len(ALT) == 46 and max(ALT) == ALT[36] == 970


def alt_line(top: float) -> str:
    pts = " ".join(f"{i / 45 * 88:.2f},{10 - a / 970 * 10:.2f}" for i, a in enumerate(ALT))
    return (f'<svg style="left:6cqw;top:{top}cqw;width:88cqw;height:11cqw;overflow:visible;z-index:2" viewBox="0 0 88 10" preserveAspectRatio="none">'
            f'<polyline points="{pts}" fill="none" stroke="#fff" stroke-width="1" vector-effect="non-scaling-stroke"/></svg>'
            f'<i style="left:{6 + 36 / 45 * 88 - 0.8:.2f}cqw;top:{top - 0.8}cqw;width:1.6cqw;height:1.6cqw;border-radius:50%;background:#fff;z-index:2"></i>')


def day2_table() -> str:
    rows = [("09:42", "우도", 11), ("14:10", "하도", 4), ("16:05", "월정리", 7), ("19:05", "성산", 4)]
    out = []
    for i, (t, p, n) in enumerate(rows):
        out.append(f'<div class="tb" style="top:{92 + i * 6.2:.1f}cqw"><span class="mo">{t}</span><span>{p}</span>'
                   f'<span class="bar"><b style="width:{n / 11 * 36:.2f}cqw"></b><span class="mo">{n}</span></span></div>')
    return "\n  ".join(out)


def big3(n: str, unit: str, top: float) -> str:
    return (f'<p class="mv" style="left:6cqw;top:{top}cqw;font-size:28cqw;line-height:.82;letter-spacing:-.05em;margin-left:-.05em">'
            f'{n}<span class="mv" style="font-size:10cqw;margin-left:1.6cqw;letter-spacing:-.03em">{unit}</span></p>')


def covers() -> str:
    c1 = f"""<div class="pg wh">
    {ph("oreum_2", 0, 0, 100, 133.33, "40% 50%")}
    <p class="mv" style="left:6cqw;top:6cqw;z-index:2">제주</p><p class="mo" style="right:6cqw;top:6cqw;z-index:2">2026</p>
    {big3("3", "일", 60).replace('style="', 'style="z-index:2;', 1)}
    {big3("9", "곳", 83).replace('style="', 'style="z-index:2;', 1)}
    {big3("46", "장", 105.5).replace('style="', 'style="z-index:2;', 1)}
  </div>"""
    c2 = f"""<div class="pg">
    {ph("udo", 0, 0, 100, 103.33, "88% 60%")}
    <div class="cell" style="left:6cqw;top:106cqw;width:20.5cqw"><p class="lb">기간</p><p class="mv">3<span class="u">일</span></p></div>
    <div class="cell" style="left:28.5cqw;top:106cqw;width:20.5cqw"><p class="lb">장소</p><p class="mv">9<span class="u">곳</span></p></div>
    <div class="cell" style="left:51cqw;top:106cqw;width:43cqw"><p class="lb">거리</p><p class="mv">214<span class="u">km</span></p></div>
    <p class="mv" style="left:6cqw;top:82cqw;color:#fff">제주</p><p class="mo" style="left:6cqw;top:93cqw;color:#fff">2026.06.12–14</p>
  </div>"""
    times = "".join(f"<span>{t}</span>" for t in TIMES)
    c3 = f"""<div class="pg spot">
    <div class="ix" style="top:6cqw">{times}</div>
    <p class="xl" style="left:6cqw;top:84cqw">제주</p>
    <p class="cap" style="left:6cqw;top:119cqw;width:88cqw;color:#fff"><span class="memo">46장</span><span class="mo">2026.06</span></p>
  </div>"""
    return f"""<h3 class="sub">표지 후보</h3>
<div class="strip">
  {c3}
  {c1}
  {c2}
</div>
<div class="under"><span>① 색인(기본) — 별색 면, 촬영 시각 46개 위에 제주 ② 세 줄 — 오름 분화구 사진 위에 3일·9곳·46장 ③ 티켓 — 우도 사진 70% + 라벨-값 3칸 + 별색 46눈금</span><span>3개</span></div>"""


def body() -> str:
    p = []
    # 01 totals
    p.append(f"""<div class="pg spot wh">{folio("제주 · 06.12–14", 1)}
  <p class="xl" style="left:6cqw;top:16cqw">214<span class="u" style="font:800 6cqw/1 var(--f);margin-left:1.5cqw">km</span></p>
  <p class="memo" style="left:6cqw;top:52cqw">협재에서 귤나무 카페까지</p>
  {axis()}
  <div class="cell" style="left:6cqw;top:100cqw;width:88cqw;display:grid;grid-template-columns:repeat(4,20.5cqw);column-gap:2cqw">
    <p class="mv">3<span class="u">일</span></p><p class="mv">9<span class="u">곳</span></p><p class="mv">46<span class="u">장</span></p><p class="mv">3<span class="u">영상</span></p>
  </div>
  {ticks()}
</div>""")
    # 02 ticket: photo 100cqw, caption, three counts bottom-aligned to M
    p.append(f"""<div class="pg">
  {ph("hyeopjae", 0, 0, 100, 100, "45% 50%")}<div class="tg"></div>
  {folio("제주 · 06.12 금", 2, True)}
  {cap(1, "협재 해변", 102, extra='<span class="r memo">물이 생각보다 차가움.</span>')}
  <div class="cell" style="left:6cqw;top:109.3cqw;width:20.5cqw;border:0;padding-top:0"><p class="lb">06.12 금</p><p class="mv">10<span class="u">장</span></p></div>
  <div class="cell" style="left:28.5cqw;top:109.3cqw;width:20.5cqw;border:0;padding-top:0"><p class="lb">이동</p><p class="mv">62<span class="u">km</span></p></div>
  <div class="cell" style="left:51cqw;top:109.3cqw;width:43cqw;border:0;padding-top:0"><p class="lb">협재 · 신창</p><p class="mv">2<span class="u">곳</span></p></div>
</div>""")
    # 03 day 1 afternoon: full-bleed + one number + edge-to-edge strip of four 1:1-ish
    strip = "".join(ph(k, i * 25 + (0.15 if i else 0), 108.33, 25 - (0.3 if 0 < i < 3 else 0.15), 25, pos)
                    for i, (k, pos) in enumerate([("hyeopjae_2", "50% 70%"), ("d4", "55% 60%"),
                                                  ("d2", "50% 60%"), ("road_2", "45% 50%")]))
    p.append(f"""<div class="pg wh">
  {ph("d9", 0, 0, 100, 108.33, "50% 55%")}<div class="bg" style="bottom:25cqw;height:44cqw;background:linear-gradient(rgba(0,0,0,0),rgba(0,0,0,.4) 60%)"></div>
  {folio("제주 · 06.12 금", 3, ink=True)}
  <p class="lb" style="left:6cqw;top:80cqw;color:#fff;opacity:.9;z-index:2">협재 → 신창 · 사진 사이 거리</p>
  <p class="mv" style="left:6cqw;top:85.5cqw;color:#fff;z-index:2">13<span class="u">km</span></p>
  <p class="cap in" style="left:6cqw;top:99cqw;width:88cqw"><span class="mo">No.03</span><span class="mo">13:31</span><span>협재 모래 위</span></p>
  {strip}
</div>""")
    # 04 rest: road
    p.append(f"""<div class="pg wh">
  {ph("road", 0, 0, 100, 133.33, "50% 50%")}<div class="bg"></div>
  {folio("제주 · 06.12 금", 4, ink=True)}
  {fcap(10, "신창 풍차해안도로").replace("bottom:14cqw", "bottom:6cqw")}
</div>""")
    # 05 day 2: full-bleed, the day's two counts on the photo
    p.append(f"""<div class="pg wh">
  {ph("udo", 0, 0, 100, 133.33, "22% 50%")}<div class="bg" style="height:72cqw;background:linear-gradient(rgba(0,0,0,0),rgba(0,0,0,.4) 40%)"></div>
  {folio("제주 · 06.13 토", 5, ink=True)}
  <p class="lb" style="left:6cqw;top:68cqw;color:#fff;opacity:.9;z-index:2">06.13 토 · 26장 · 우도 → 하도 → 월정리 → 성산</p>
  <p class="xl" style="left:6cqw;top:74cqw;color:#fff;z-index:2">88<span class="u" style="font:800 6cqw/1 var(--f);margin-left:1.5cqw">km</span></p>
  {fcap(12, "우도", pre='<span class="memo">모자가 두 번 날아감.</span>').replace("bottom:14cqw", "bottom:6cqw")}
</div>""")
    # 06 video
    p.append(f"""<div class="pg wh">
  {ph("haenyeo", 0, 0, 100, 133.33, "40% 50%").replace("object-position:40% 50%", "object-position:40% 0%;height:119%")}<div class="tg" style="height:16cqw;background:linear-gradient(rgba(0,0,0,.25),rgba(0,0,0,0))"></div><div class="bg" style="height:56cqw;background:linear-gradient(rgba(0,0,0,0),rgba(0,0,0,.4) 50%)"></div>
  <div class="play"><span></span></div>
  {folio("제주 · 06.13 토", 6)}
  {fcap(22, "하도 해녀 탈의장 앞", pre='<span class="lb" style="flex-basis:100%;opacity:1">PDF에서는 사진 한 장으로</span>', lead='<span class="mo">▶ 3초</span>').replace("bottom:14cqw", "bottom:6cqw")}
</div>""")
    # 07 wolj: photo + edge strip of three + one number
    strip = "".join(ph(k, i * 33.33 + (0.15 if i else 0), 88.3, 33.34 - (0.3 if i == 1 else 0.15), 33.33, pos)
                    for i, (k, pos) in enumerate([("doldam_2", "50% 55%"), ("woljeongri", "40% 60%"), ("woljeongri_2", "62% 50%")]))
    p.append(f"""<div class="pg">
  {ph("doldam", 0, 0, 100, 88, "50% 55%")}
  {folio("제주 · 06.13 토", 7, ink=True)}
  {strip}
  <p class="cap" style="left:6cqw;top:123.3cqw;width:88cqw;border:0;padding:0"><span class="mo">No.26–32</span><span class="mo">16:05–17:20</span><span>월정리 돌담길 → 해변 1.2km</span></p>
  <p class="mv" style="left:6cqw;top:46cqw;z-index:2">7<span class="u">장 · 월정리</span></p>
</div>""")
    # 08 shot strip as the cobalt hero: band + white marks on spot
    p.append(f"""<div class="pg spot wh" style="--rule:rgba(255,255,255,.45)">
  {ph("seongsan", 0, 0, 100, 66.67, "50% 50%")}<div class="bg" style="bottom:66.67cqw;height:24cqw"></div>
  {folio("제주 · 06.13 토", 8, ink=True)}
  <p class="cap in" style="left:6cqw;top:59.5cqw;width:88cqw"><span class="mo">No.33</span><span class="mo">19:05</span><span>성산일출봉 앞</span></p>
  {shot_strip()}
</div>""")
    # 09 hallasan + altitude: XL 970 on the photo, 46-point GPS altitude line aligned with the ticks
    p.append(f"""<div class="pg wh">
  {ph("hallasan", 0, 0, 100, 133.33, "50% 40%")}<div class="tg" style="height:16cqw;background:linear-gradient(rgba(0,0,0,.25),rgba(0,0,0,0))"></div><div class="bg" style="height:84cqw;background:linear-gradient(rgba(0,0,0,0),rgba(0,0,0,.45) 45%)"></div>
  {folio("제주 · 06.14 일", 9)}
  <p class="lb" style="left:6cqw;top:53cqw;color:#fff;opacity:.85;z-index:2">가장 높은 곳</p>
  <p class="xl" style="left:6cqw;top:59cqw;color:#fff;z-index:2">970<span class="u" style="font:800 6cqw/1 var(--f);margin-left:1.5cqw">m</span></p>
  {alt_line(96.5)}
  {fcap(37, "한라산 어리목", pre='<span class="memo">안개밖에 안 보임.</span>')}
  {ticks((37,))}
</div>""")
    # 10 full-bleed oreum, one count
    p.append(f"""<div class="pg wh">
  {ph("oreum", 0, 0, 100, 133.33, "76% 50%")}<div class="bg" style="height:56cqw;background:linear-gradient(rgba(0,0,0,0),rgba(0,0,0,.4) 50%)"></div>
  {folio("제주 · 06.14 일", 10, ink=True)}
  <p class="lb" style="left:6cqw;top:96cqw;color:#fff;opacity:.9;z-index:2">원물오름에 머문 시간 · 5장</p>
  <p class="mv" style="left:6cqw;top:102cqw;color:#fff;z-index:2">37<span class="u">분</span></p>
  {fcap(41, "억새 사이").replace("bottom:14cqw", "bottom:6cqw")}
</div>""")
    # 11 final: one big number (first to last photo), the rest in one quiet line
    p.append(f"""<div class="pg spot wh" style="--rule:rgba(255,255,255,.45)">
  {ph("tangerine", 0, 0, 100, 66.67, "50% 55%")}<div class="tg"></div>
  {folio("제주 · 06.14 일", 11)}
  {cap(46, "귤나무 카페 마당", 68.67, extra='<span class="r mo">마지막 사진</span>')}
  <p class="lb" style="left:6cqw;top:78cqw">첫 사진에서 마지막 사진까지</p>
  <p class="xl" style="left:6cqw;top:84cqw">48<span class="u" style="font:800 6cqw/1 var(--f);margin-left:1.5cqw">시간 32분</span></p>
  <p class="lb" style="left:6cqw;top:117cqw;width:88cqw;white-space:nowrap">우도 11장 · 1시간 39분 · 가장 긴 공백 2시간 57분 · 46장</p>
</div>""")
    return f"""<h3 class="sub">책 본문</h3>
<div class="strip">
{chr(10).join(p)}
</div>
<div class="under"><span>01 총계 → 02 협재(티켓) → 03 첫째 날(풀블리드 + 13km + 아래 띠 넷) → 04 신창(쉬는 쪽) → 05 둘째 날(풀블리드 + 88km) → 06 해녀(영상) → 07 월정리(사진 + 띠 셋 + 7장) → 08 46장 찍은 시각(별색 면) → 09 한라산(970m + 고도선) → 10 오름(풀블리드 + 37분) → 11 결산(별색 면, 48시간 32분 하나)</span><span>11쪽</span></div>"""


NOTES = """<details class="notes"><summary>디자인 규칙</summary><ul>
<li>그리드: M = 쪽 폭 6%, 4단, G 2% (단 폭 20.5%, 단 i 시작 = 6 + 22.5i %). 글 상자·사진 가장자리는 단 경계 또는 풀블리드. 칸은 가로 괘선 0.5px 로만, 세로선·박스 없음.</li>
<li>타입 3단 + 모노: XL 40%(Wanted Sans 800, 자간 −5%, 행간 .82, 한 권 3번까지 — 01 총 거리·05 둘째 날 거리·09 최고 고도, 사진 위면 흰색) / M 10%(800, −3%) / S 3.4%(500, 라벨은 잉크 62%, 메모 한 줄만 700). 시각·날짜·번호·쪽수는 Red Hat Mono 500 3%. 표지만 28%·40% 허용.</li>
<li>큰 숫자는 세는 값(km·장·곳·분)만, 항상 단위·라벨을 붙인다. 촬영 시각·날짜는 캡션 크기로만. 같은 총계(46·214)는 표지와 01 밖에서 다시 키우지 않고, 결산은 새 사실 4개(최다·최장 체류·최장 공백·첫–끝 사진 간격)를 M 으로, 시간은 「1시간 15분」처럼 단위를 붙여 시각과 구별. 모든 사실은 촬영 시각·GPS(고도)·장수에서 계산.</li>
<li>색: 종이 #ECECE8 + 먹 #141414 + 별색 1. 팔레트는 별색만 바꾼다(코발트·주홍·초록). 별색 자리는 눈금·막대·시각 띠·01쪽 면·11쪽 아래 면·표지③ 면뿐, 글자엔 쓰지 않는다.</li>
<li>46눈금(책 유일 장치): 쪽 아래 M, 폭 88%, 사진 1장 = 1px·2% 눈금(날 시작 3%), 이 쪽 사진은 별색 2px·4%. 사진이 쪽 위에 붙는 틀은 눈금이 종이 위에 오게 띠를 위에 둔다. 풀블리드 쉬는 쪽에는 넣지 않는다(고도선이 눈금과 1:1인 09쪽만 흰색). 08쪽은 같은 눈금을 촬영 분 위치에 날별 3줄로 키운 별색 면 쪽. 사진 60장 넘으면 시간당 1눈금.</li>
<li>사진 비율 3: 풀블리드 3:4 / 전폭 띠 3:2(쪽 위에 붙임, 티켓만 65%) / 1:1(2단 43% 또는 격자 28%, 간격 G). 3×3 격자(사진 8 + 값 칸 1)는 한 권 1번(사진이 가장 많은 날). 다른 장소는 3:2 띠 + 1:1 셋(28%) + 값 2개. 격자·띠 아래 캡션은 「No. 시각」만, 장소는 머리줄.</li>
<li>틀 4종(장소 사진 수 n): 세로 대표컷 → 쉬는 쪽 / 가로 대표컷 → 티켓(사진 + 세는 값 3칸) 또는 표(띠 + 표) / n≥8 → 격자. 사진 없는 쪽은 01 총계뿐, 표와 풀블리드를 교대. 한 쪽 사진은 시간 순(왼→오, 위→아래).</li>
<li>캡션 = 표 한 줄: 「No.07 13:20 장소」, 사진 아래 G, 위 0.5px 괘선, 메모는 같은 줄 오른쪽. 풀블리드는 사진 안 왼쪽 아래(흰색, 아래 그라데이션 ≤ .4, 눈금 위 8%). 영상은 「▶ 3초」을 캡션 앞에 붙이고, PDF 에서는 같은 쪽을 사진 한 장으로.</li>
<li>폴리오: 위 M — 왼쪽 「제주 · 06.13 토」, 오른쪽 「05/11」 모노. 사진이 쪽 위에 붙으면 흰색 + 위 그라데이션 .3.</li>
</ul></details>"""


def main() -> None:
    pals = '[{"--spot":"#2447E6"},{"--spot":"#F0441E"},{"--spot":"#0C8A5E"}]'
    html = "\n".join([
        "<!-- fonts: Red+Hat+Mono:wght@500 -->",
        "<!-- kfonts: Wanted Sans:500;700;800 -->",
        CSS,
        f"<section class=\"t-b\" data-palettes='{pals}'>",
        '<div class="head"><h2>기록 카드</h2><p class="idea">여행을 계측한 집계표 — 괘선 서식 위에 잉크 한 색, 숫자는 사실만</p>'
        '<button class="shuffle" type="button">색 조합 바꾸기</button></div>',
        covers(), body(), NOTES, "</section>", ""])
    OUT.write_text(html, encoding="utf-8")
    print("wrote", OUT, len(html))


if __name__ == "__main__":
    main()
