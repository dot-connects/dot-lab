"""Theme c preview with notebook-style sample text near each slot's max length + overflow check.
uv run --no-project --with fonttools --with brotli --with pillow --with playwright python c_preview.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import preview  # noqa: E402

preview.SAMPLE.update({
    "title": "제주", "period": "2026. 6. 12 — 6. 14", "day": "6/13 토", "route2": "x", "route": "김포 → 제주 → 협재 → 우도 → 성산 → 제주 → 김포",
    "date": "6/13 토 · 아침엔 흐리고 바람", "dayline": "둘째 날, 우도까지", "time": "9:25", "time2": "13:40", "time3": "19:52",
    "no": "4", "no2": "5", "place": "하고수동 해변가", "place2": "서빈백사", "area": "우도면 · 걸어서 20분 · 물 차가움",
    "area2": "우도면 · 자전거로 10분",
    "note": "물 빠진 자리에 돌이 까맣게 드러나 있었다. 아이 둘이 양동이를 들고 게를 찾았는데 한 마리도 못 잡았다고 했다.",
    "note2": "모래가 아니라 산호 조각이라고. 맨발로 걷다가 금방 신을 다시 신었다.",
    "cap": "배 뒤편. 섬이 생각보다 금방 작아졌다", "cap2": "천진항 앞 자전거 가게, 시간당 만 원", "cap3": "해 지고 십 분쯤 더 앉아 있었다",
    "caps": "1  성게국수 집 앞 줄. 우리 앞에 열한 명\n2  창가 자리, 바다가 반쯤 보임\n3  주인 할머니가 덤으로 준 톳무침",
    "times": "9:10\n9:25\n10:40\n12:30\n15:10\n18:30\n19:50\n21:00",
    "stops": "성산항, 우도 가는 배\n천진항, 자전거 빌림\n하고수동 해변\n성게국수, 줄 서서 40분\n서빈백사\n배로 성산 돌아옴\n광치기 해변 노을\n숙소, 귤 한 봉지",
    "eats": "성게국수 2 · 24,000\n톳무침 (덤)\n땅콩 아이스크림 2 · 9,000\n한라봉 주스 · 6,000\n\n줄 서서 40분 기다림\n국물이 생각보다 맑음",
    "body": "우도 가는 배는 9시 10분이었는데 표를 사고 나니 바람이 세져서 갑판에 오래 못 있었다. 섬에 내려서 자전거를 빌렸다. 하고수동까지는 오르막이 많아 절반은 끌고 갔다. 해변에 닿았을 때는 물이 빠져 있어서 돌 사이로 게를 찾는 아이들이 있었다. 우리는 그 옆에 앉아 땅콩 아이스크림을 먹었다. 돌아오는 배 시간을 놓칠까 봐 계속 시계를 봤다. 배를 타고 나서야 가방에 넣어 둔 귤 두 개가 생각났고, 성산 쪽이 가까워질 때까지 그걸 천천히 까먹었다. 손에서 한참 귤 냄새가 났다.",
    "places": "6/12 금\n1  협재 해변 — 한림읍 13:20\n2  금능 돌담길 — 한림읍 15:05\n3  신창 풍차해안도로 — 한경면 18:40\n6/13 토\n4  하고수동 해변 — 우도면 10:40\n5  서빈백사 — 우도면 15:10\n6  광치기 해변 — 성산읍 19:50\n6/14 일\n7  성산일출봉 — 성산읍 6:10\n8  해녀의 부엌 — 구좌읍 11:30\n9  다랑쉬오름 — 구좌읍 14:20\n10  귤밭 카페 — 조천읍 16:40",
    "colophon": "제주, 2026년 6월 12일 금요일 — 14일 일요일\n사진 214장 중 31장, 걸은 거리 38km\n첫 사진 협재 13:02, 마지막 사진 김포 21:48",
})

LIMITS = {}


def check(page) -> list[str]:
    return page.evaluate("""() => [...document.querySelectorAll('[data-text]')].filter(e => {
        const o = getComputedStyle(e).whiteSpace;
        return e.scrollHeight > e.clientHeight + 1 || e.scrollWidth > e.clientWidth + 1;
      }).map(e => e.closest('figure').querySelector('figcaption').textContent.split(' ')[0] + ' ' + e.dataset.text
               + ' ' + e.scrollWidth + 'x' + e.scrollHeight + ' > ' + e.clientWidth + 'x' + e.clientHeight)""")


def main() -> None:
    lays = json.loads((HERE / "layouts.json").read_text(encoding="utf-8"))
    for lay in lays:  # sample lengths vs limits
        for k, n in lay["texts"].items():
            s = preview.SAMPLE.get(k)
            if s and len(s) > n:
                print("sample longer than limit:", lay["id"], k, len(s), ">", n)
    out = HERE / "preview.html"
    out.write_text(preview.build("c"), encoding="utf-8")
    from renderlock import render_slot
    from playwright.sync_api import sync_playwright
    with render_slot(), sync_playwright() as pw:
        b = pw.chromium.launch(channel="chrome")
        pg = b.new_page(viewport={"width": 1500, "height": 900}, device_scale_factor=1)
        pg.goto(out.as_uri())
        pg.wait_for_timeout(900)
        pg.screenshot(path=str(HERE / "sheet-c.png"), full_page=True)
        print("overflow:", check(pg) or "none")
        if "--big" in sys.argv:  # a few pages at 2x for detail
            pg.set_viewport_size({"width": 1500, "height": 900})
            pg.add_style_tag(content=".cell{width:460px!important}.cell .pg{width:460px!important}")
            pg.wait_for_timeout(400)
            pg.screenshot(path=str(HERE / "sheet-c-big.png"), full_page=True)
        b.close()


if __name__ == "__main__":
    main()
