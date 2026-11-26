"""Diary preview with real diary-length text + fit/contrast check.
python fill.py [max]  -> fill-diary.html + fill-diary.png (real text) ; with 'max' every text is padded to its limit.
Checks every text box: inside page (4cqw safe), no overlap with photos/other texts (unless .on), font >= 2.8cqw,
white text on photo >= 4.5:1 (90th-percentile worst pixel under the box)."""
from __future__ import annotations

import io
import json
import re
import sys
from pathlib import Path

from PIL import Image

D = Path(__file__).resolve().parent
sys.path.insert(0, str(D.parent.parent.parent / "r10"))

REAL = {
    "title": "제주 일기", "period": "2026년 6월 12일부터 사흘",
    "date": "6월 13일 토요일", "place": "우도, 월정리, 성산",
    "cap": "우도 가는 배. 갈매기가 끝까지 따라왔다",
    "line": "“여기서 조금만 더 있다 가자.”",
    "w1": "아침", "w2": "밤",
    "b1": "비가 그쳐 있었다. 어제 사 둔 귤을 하나씩 주머니에 넣고 나왔다. 길이 아직 젖어 있었다.",
    "b2": "숙소 마당에서 J가 별이 많다고 했다. 나는 모기를 잡느라 하늘을 거의 못 봤다.",
    "last": "공항 의자에서 마지막 귤을 깠다. 손에서 사흘 내내 귤 냄새가 났다.",
    "coltitle": "제주 일기",
    "colophon": "2026년 6월 12일부터 14일까지\n협재, 우도, 성산, 어리목\n사진 서른 장과 짧은 영상 세 편",
}
BODY = ("협재에 도착한 건 한 시쯤이었다. 물이 생각보다 맑아서 신발을 벗어 들고 한참 걸었다. 발목까지 들어온 물이 돌아갈 때마다 "
        "모래가 발가락 사이로 빠져나갔다.\n\n비양도가 바로 앞에 있었다. 손을 뻗으면 닿을 것 같다고 했더니 J가 배로 십오 분이라고 했다. "
        "그 말을 듣고 나니 더 멀어 보였다.\n\n해 질 무렵 다시 나왔을 때는 바람이 세져 있었다. 모자가 두 번 날아갔다. 두 번째는 J가 "
        "물에 들어가서 주워 왔다. 바지가 무릎까지 젖었다.")


def text_for(key: str, limit: int, mode: str) -> str:
    s = REAL.get(key, BODY)
    if mode == "max":
        while len(s) < limit:
            s += " " + s
    if len(s) > limit:
        s = s[:limit]
        if "\n" not in s[-3:] and key in ("body", "last"):
            s = s.rstrip() + "."
    return s


JS = """(pw)=>{const out=[];for(const pg of document.querySelectorAll('.pg')){
 const P=pg.getBoundingClientRect(),u=P.width/100,id=pg.closest('figure').querySelector('figcaption').textContent.split(' ')[0];
 const phs=[...pg.querySelectorAll('.ph')].map(e=>e.getBoundingClientRect());
 const ts=[...pg.querySelectorAll('[data-text]')];
 const hit=(a,b)=>a.left<b.right-1&&a.right>b.left+1&&a.top<b.bottom-1&&a.bottom>b.top+1;
 ts.forEach((el,i)=>{const r=el.getBoundingClientRect(),fs=parseFloat(getComputedStyle(el).fontSize)/u;
  const on=el.classList.contains('on');
  if(r.bottom>P.bottom-4*u||r.right>P.right-4*u+.5||r.left<P.left+4*u-.5)out.push(id+' '+el.dataset.text+' OUT bottom='+((r.bottom-P.top)/u).toFixed(1));
  if(!on&&phs.some(b=>hit(r,b)))out.push(id+' '+el.dataset.text+' ON-PHOTO');
  ts.slice(i+1).forEach(o=>{if(hit(r,o.getBoundingClientRect()))out.push(id+' '+el.dataset.text+' x '+o.dataset.text)});
  if(fs<2.79)out.push(id+' '+el.dataset.text+' small '+fs.toFixed(2));
  const room=(()=>{let lim=P.bottom-4*u;for(const b of phs)if(b.top>=r.bottom-1&&b.left<r.right&&b.right>r.left)lim=Math.min(lim,b.top);
   for(const o of ts)if(o!==el){const q=o.getBoundingClientRect();if(q.top>=r.bottom-1&&q.left<r.right&&q.right>r.left)lim=Math.min(lim,q.top)}return (lim-r.bottom)/u})();
  el.dataset.room=room.toFixed(1);
 });}return out}"""


def lum(c: tuple[int, ...]) -> float:
    r = [(v / 255) / 12.92 if v / 255 <= .04045 else ((v / 255 + .055) / 1.055) ** 2.4 for v in c[:3]]
    return .2126 * r[0] + .7152 * r[1] + .0722 * r[2]


def main() -> None:
    mode = sys.argv[1] if len(sys.argv) > 1 else "real"
    html = (D / "preview.html").read_text(encoding="utf-8")
    lays = {l["id"]: l for l in json.loads((D / "layouts.json").read_text(encoding="utf-8"))}

    def fig(m: re.Match) -> str:
        block = m.group(0)
        lid = re.search(r"<figcaption>(\S+)", block).group(1)
        for k, n in lays[lid]["texts"].items():
            v = text_for(k, n, mode).replace("\n", "<br>")
            block = re.sub(rf'(<(\w+)[^>]*data-text="{k}"[^>]*>).*?(</\2>)', lambda mm: mm.group(1) + v + mm.group(3), block, count=1, flags=re.S)
        return block

    html = re.sub(r'<figure class="cell">.*?</figure>', fig, html, flags=re.S)
    # fonts were subset to the sample text: fall back to whole woff2 is not available, so add the glyphs via a second face pass
    import fonts
    want = fonts.parse("<!-- kfonts:" + re.search(r"kfonts:(.*?)\*/", (D / "theme.css").read_text(encoding="utf-8")).group(1) + "-->")
    sys.path.insert(0, str(D.parent))
    import preview
    alltext = "".join(REAL.values()) + BODY + "".join(preview.SAMPLE.values()) + preview.FILL + "0123456789“”"
    html = re.sub(r"@font-face\{[^}]*\}", "", html)
    html = html.replace("</style>", fonts.faces_css(want, alltext) + "</style>", 1)
    out = D / f"fill-diary{'-max' if mode == 'max' else ''}.html"
    out.write_text(html, encoding="utf-8")
    from renderlock import render_slot
    from playwright.sync_api import sync_playwright
    with render_slot(), sync_playwright() as pw:
        b = pw.chromium.launch(channel="chrome")
        pg = b.new_page(viewport={"width": 1500, "height": 900}, device_scale_factor=1)
        pg.goto(out.as_uri())
        pg.wait_for_timeout(900)
        probs = pg.evaluate(JS, 0)
        rooms = pg.evaluate("()=>[...document.querySelectorAll('[data-room]')].map(e=>e.closest('figure').querySelector('figcaption').textContent.split(' ')[0]+' '+e.dataset.text+' room '+e.dataset.room)")
        pg.screenshot(path=str(out.with_suffix(".png")), full_page=True)
        # contrast of white text on photos
        boxes = pg.evaluate("()=>[...document.querySelectorAll('.on')].map(e=>{const r=e.getBoundingClientRect();return [e.closest('figure').querySelector('figcaption').textContent.split(' ')[0],e.dataset.text,r.left,r.top+scrollY,r.width,r.height]})")
        pg.add_style_tag(content=".on{color:transparent!important}")
        pg.wait_for_timeout(200)
        shot = Image.open(io.BytesIO(pg.screenshot(full_page=True))).convert("RGB")
        b.close()
    for lid, k, x, y, w, h in boxes:
        px = sorted(lum(p) for p in shot.crop((int(x), int(y), int(x + w), int(y + h))).getdata())
        worst = px[int(len(px) * .9)]
        probs.append(f"contrast {lid} {k} {(1.05) / (worst + .05):.2f}")
    print("\n".join(probs) or "no problems")
    if mode == "max":
        print("\n".join(rooms))


if __name__ == "__main__":
    main()
