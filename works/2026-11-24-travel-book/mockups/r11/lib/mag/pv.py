"""Preview wrapper for mag: sample texts that fit this theme, Instrument Serif from the app, folios.
python pv.py [--max]  -> preview.html / sheet-mag.png  (--max: every text slot at its limit -> pv-max.html / sheet-mag-max.png)"""
from __future__ import annotations
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import preview  # noqa: E402

FONT = Path(r"C:/DC/DD/dot-lab/works/2026-11-24-travel-book/app/assets/book/fonts")
MAX = "--max" in sys.argv
S = {
    "mast": "JEJU", "title": "제주, 6월", "period": "2026. 6. 12 — 14", "lines": "협재의 물은 발목에서 돌아갔다\n우도까지 배로 십오 분\n안개 속 어리목",
    "item1": "서쪽 — 협재와 신창", "pg1": "4", "item2": "섬 하나 더, 우도", "pg2": "9", "item3": "동쪽의 해 질 녘", "pg3": "13", "item4": "안쪽, 안개", "pg4": "17",
    "kicker": "Part One — West", "deck": "물은 생각보다 차가웠고, 비양도는 손에 잡힐 듯 가까웠습니다.", "credit": "6월 12일 금요일 — 협재, 신창",
    "head": "동쪽에서 고른 네 곳", "quote": "해녀 삼촌들은 물에 들어가기 전에 꼭 노래를 해요.", "who": "하도 해녀 탈의장 앞에서",
    "cap": "협재, 오후 한 시. 물이 발목까지 왔다 돌아갑니다", "cap1": "우도 땅콩 아이스크림", "cap2": "하도 해녀 탈의장", "cap3": "월정리 창가 자리", "cap4": "성산 가는 버스",
    "body": "어리목 입구에 닿았을 때 안개는 아직 봉우리 위에 머물러 있었습니다. 열 걸음 앞의 사람도 흐릿했고, 들리는 건 나무 계단을 밟는 소리뿐이었습니다. 정상까지 가는 대신 첫 쉼터에서 돌아섰습니다. 내려오는 길에 안개가 조금 걷히면서 조릿대가 바람 쪽으로 한꺼번에 누웠습니다.",
    "sign": "6월, 제주에서 돌아와", "colophon": "제주 — 2026년 6월 12일부터 14일까지\n협재, 신창, 우도, 하도, 성산, 어리목\n사진과 글, 여행한 사람",
}
if MAX:
    S = {"mast": "GYEONGJU", "pg1": "123", "pg2": "123", "pg3": "123", "pg4": "123", "period": "2026. 12. 28 — 2027. 1. 3"}
preview.SAMPLE.clear()
preview.SAMPLE.update(S)
html = preview.build("mag")
faces = "".join(f'@font-face{{font-family:"Instrument Serif";font-style:{st};src:url({(FONT / f).as_uri()})}}'
                for st, f in (("normal", "InstrumentSerif-Regular.ttf"), ("italic", "InstrumentSerif-Italic.ttf")))
html = html.replace("<style>", "<style>" + faces, 1)
n = [0]
import re  # noqa: E402
def num(m: re.Match) -> str:
    n[0] += 1
    return f'{m.group(0)} data-page="{n[0] + 1}"'
html = re.sub(r'<div class="pg [^"]*\bfo\b[^"]*"', num, html)
out = HERE / ("pv-max.html" if MAX else "preview.html")
out.write_text(html, encoding="utf-8")
if "--no-shot" not in sys.argv:
    from renderlock import render_slot
    from playwright.sync_api import sync_playwright
    with render_slot(), sync_playwright() as pw:
        b = pw.chromium.launch(channel="chrome")
        pg = b.new_page(viewport={"width": 1500, "height": 900}, device_scale_factor=2 if "--hi" in sys.argv else 1)
        pg.goto(out.as_uri()); pg.wait_for_timeout(900)
        pg.screenshot(path=str(HERE / ("sheet-mag-max.png" if MAX else "sheet-mag.png")), full_page=True)
        b.close()
print("ok", out)
