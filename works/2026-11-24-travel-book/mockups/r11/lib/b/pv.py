"""Theme b preview with realistic sample text: python pv.py [--max]
--max fills every text slot to its limit (multiline slots to their line count) and runs a fit check:
text outside the page, text over a photo, text over text, font smaller than 2.8cqw."""
from __future__ import annotations
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import preview  # noqa: E402

S = {
 "title": "제주", "period": "2026년 6월 12일부터 사흘", "count": "장소 열두 곳, 사진 서른 장",
 "toc": "01 협재 해변\n02 금능 포구\n03 한림 칼국수\n04 성산일출봉\n05 우도 가는 배\n06 하도 해녀 탈의장\n07 월정리\n08 세화 오일장\n09 어리목\n10 사려니숲",
 "dayline": "둘째 날", "day": "6월 13일 토요일, 흐리다 갬",
 "times": "07:40 숙소 앞 골목\n09:10 성산일출봉\n10:30 우도 가는 배\n12:20 하우목동 포구\n15:00 하도 해녀 탈의장\n18:40 월정리",
 "no": "04", "place": "협재 해변", "meta": "한림읍 · 6월 12일 오후 1시 20분",
 "memo": "물이 생각보다 차가웠다. 비양도가 손에 잡힐 듯 가깝다. 모래가 하얘서 물 색이 두 번 바뀐다.",
 "cap": "물이 발목까지 왔다 돌아간다", "capl": "보말칼국수, 주문하고 십오 분", "capr": "가게 앞 귤 상자",
 "capu": "갑판에서 본 성산", "capd": "내리기 직전, 하우목동 포구",
 "l0": "한치 물회", "l1": "우도 땅콩 아이스크림", "l2": "보말칼국수", "l3": "하귤 세 개, 천 원", "l4": "주운 소라 껍데기", "l5": "배표 두 장",
 "quote": "물질은 숨 참는 게 아니라 숨 아끼는 거야.", "n0": "카페 한림", "t0": "창가 자리, 아이스 라테 한 잔", "n1": "소품 가게 물결", "t1": "엽서 세 장을 샀다", "n2": "골목 끝 귤밭", "t2": "주인 없는 무인 판매대",
 "body": "셋째 날 아침 어리목은 안개 때문에 아무것도 안 보였다. 앞사람 등산복 색만 보고 걸었다.\n\n삼십 분쯤 올랐을 때 안개가 한 번 걷혔다. 아래로 숲이 다 보였다가, 셋을 세기 전에 다시 닫혔다. 사진은 그 사이에 한 장 찍었다.",
 "hd": "장소 색인",
 "index": "01  협재 해변  6월 12일  14장\n02  금능 포구  6월 12일  6장\n03  한림 칼국수  6월 12일  4장\n04  성산일출봉  6월 13일  9장\n05  우도 가는 배  6월 13일  5장\n06  하도 해녀 탈의장  6월 13일  7장\n07  월정리  6월 13일  8장\n08  어리목  6월 14일  6장\n09  사려니숲  6월 14일  5장",
 "last": "마지막 귤은 공항 의자에서 깠다.",
 "colophon": "제주, 2026년 6월 12일—14일\n장소 아홉 곳, 사진 서른 장 중 스물네 장",
}
LINES = {"toc": 12, "times": 7, "index": 13}


def maxed(lays: list[dict]) -> dict[str, str]:
    lim: dict[str, int] = {}
    for l in lays:
        for k, n in l.get("texts", {}).items():
            lim[k] = max(lim.get(k, 0), n)
    out = {}
    for k, v in S.items():
        if k in LINES:
            lines = [x for x in v.split("\n") if x]
            longest = max(lines, key=len)
            out[k] = "\n".join((lines + [longest] * 20)[:LINES[k]])
        else:
            s = v
            while len(s) < 400:
                s += " " + v
            out[k] = s
    return out


CHECK = r"""() => {
 const bad = [];
 document.querySelectorAll('figure.cell').forEach(cell => {
  const id = cell.querySelector('figcaption').textContent.split(' ')[0];
  const pg = cell.querySelector('.pg'), P = pg.getBoundingClientRect(), W = P.width;
  const phs = [...pg.querySelectorAll('.ph')].map(e => e.getBoundingClientRect());
  const txt = [...pg.querySelectorAll('[data-text]')].filter(e => e.textContent.trim());
  const rects = e => { const r = document.createRange(); r.selectNodeContents(e); return [...r.getClientRects()]; };
  const hit = (a, b) => a.left < b.right - .5 && b.left < a.right - .5 && a.top < b.bottom - .5 && b.top < a.bottom - .5;
  txt.forEach(e => {
   const fs = parseFloat(getComputedStyle(e).fontSize) / W * 100;
   if (fs < 2.79) bad.push(id + ' small ' + e.dataset.text + ' ' + fs.toFixed(2));
   const onPhoto = e.closest('.c-bleed');
   rects(e).forEach(r => {
    if (r.left < P.left - .5 || r.right > P.right + .5 || r.top < P.top - .5 || r.bottom > P.bottom + .5) bad.push(id + ' out ' + e.dataset.text);
    if (!onPhoto && phs.some(p => hit(r, p))) bad.push(id + ' onphoto ' + e.dataset.text);
    txt.forEach(o => { if (o !== e && !o.contains(e) && !e.contains(o) && rects(o).some(q => hit(r, q))) bad.push(id + ' overlap ' + e.dataset.text + '/' + o.dataset.text); });
   });
   const cs = getComputedStyle(e.closest('[class*=toc],[class*=mm],[class*=tm]') || e);
   if (e.scrollHeight > e.clientHeight + 1 && cs.columnCount !== 'auto') bad.push(id + ' coloverflow ' + e.dataset.text);
  });
  if (pg.classList.contains('fo')) {
   const fy = P.bottom - W * .072;
   txt.forEach(e => rects(e).forEach(r => { if (r.bottom > fy && r.right > P.right - W * .16) bad.push(id + ' folio ' + e.dataset.text); }));
  }
 });
 return [...new Set(bad)];
}"""


def main() -> None:
    import json
    mx = "--max" in sys.argv
    lays = json.loads((HERE / "layouts.json").read_text(encoding="utf-8"))
    preview.SAMPLE.clear()
    preview.SAMPLE.update(maxed(lays) if mx else S)
    name = "max" if mx else "preview"
    out = HERE / f"{name}.html"
    out.write_text(preview.build("b"), encoding="utf-8")
    from renderlock import render_slot
    from playwright.sync_api import sync_playwright
    with render_slot(), sync_playwright() as pw:
        b = pw.chromium.launch(channel="chrome")
        pg = b.new_page(viewport={"width": 1500, "height": 900}, device_scale_factor=1)
        pg.goto(out.as_uri())
        pg.wait_for_timeout(900)
        pg.screenshot(path=str(HERE / f"sheet-{'max' if mx else 'b'}.png"), full_page=True)
        bad = pg.evaluate(CHECK)
        b.close()
    print("issues:", len(bad))
    for x in bad:
        print(" ", x)


if __name__ == "__main__":
    main()
