"""Generate album layouts.html + layouts.json from one compact spec (page = 100 x 133.33 cqw)."""
from __future__ import annotations

import json
import sys
from pathlib import Path

OUT = Path(sys.argv[1])

# print: (slot, x, y, w, h, flags)  flags: d=date stamp border, t=tilt, r=rounded square
# text:  (key, max, cls, style)
L: list[dict] = []


def lay(name, kind, when, prints, texts=(), pg=""):
    L.append(dict(name=name, kind=kind, when=when, prints=prints, texts=texts, pg=pg))


# ---------- covers ----------
lay("cover-board", "cover", "기본 표지. 가장 센 한 장(도착 컷·바다)을 위에 크게, 아래 큰 제목", [
    ("A", 6, 6, 88, 76, ""),
], [("title", 5, "ttl", "left:5.4cqw;top:86cqw"), ("period", 22, "per", "left:6cqw;top:106.5cqw"),
    ("memo", 18, "memo", "left:6cqw;bottom:8cqw")])
lay("cover-side", "cover", "세로 사진이 센 여행. 제목 위 왼쪽, 세로 인화가 오른쪽 아래 모서리에", [
    ("P", 30, 35.7, 64, 89.6, ""),
], [("title", 5, "ttl", "left:5.4cqw;top:5cqw"), ("period", 22, "per", "left:6cqw;top:24cqw"),
    ("place", 12, "memo", "left:6cqw;bottom:8cqw;width:21cqw")])
lay("cover-cloth", "cover", "천 표지에 정사각 인화 한 장을 끼운 앨범. 차분한 여행, 사람·가족 여행", [
    ("S", 12, 9, 76, 76, "r"),
], [("title", 5, "ttl", "left:0;right:0;top:92cqw;text-align:center"),
    ("period", 22, "per", "left:0;right:0;top:112cqw;text-align:center")], pg="cloth")

# ---------- day openers (first print of the day carries the lab date stamp) ----------
lay("day-road", "chapter", "날이 바뀔 때 기본. 이동 컷(차창·배·길)을 가로로 크게 + 그날 둘째 컷 정사각", [
    ("L", 6, 14, 88, 61.5, "d"), ("S", 47.2, 78.5, 46.8, 46.8, ""),
], [("dayline", 18, "day", "left:6cqw;top:5.2cqw"), ("stamp", 6, "stp", ""),
    ("memo", 24, "memo", "left:6cqw;bottom:8cqw;width:37cqw")])
lay("day-tall", "chapter", "날 첫 컷이 세로일 때. 큰 세로 인화 + 오른쪽 여백에 세로로 쓴 머리줄(쉼표 없이)", [
    ("P", 6, 6, 76, 106.4, "d"),
], [("dayline", 16, "day vt", "right:5.2cqw;top:6cqw"), ("stamp", 6, "stp", ""),
    ("memo", 24, "memo", "left:6cqw;bottom:8cqw;width:76cqw")])
lay("day-note", "chapter", "날을 글로 열고 싶을 때(날씨·계획이 바뀐 날). 머리줄 + 두 줄 글 + 세로 인화", [
    ("P", 24, 30.3, 70, 95, "d"),
], [("dayline", 18, "day", "left:6cqw;top:5.2cqw"), ("body", 44, "body", "left:6cqw;top:12.6cqw;width:88cqw"),
    ("stamp", 6, "stp", "")])

# ---------- body ----------
lay("full", "photo", "센 컷 하나(얼굴·노을·풍경). 한 쪽 꽉, 글 없음. 빽빽한 쪽 다음, 끝에서 셋째 쪽쯤", [
    ("P", 6, 6, 88, 119.3, ""),
])
lay("big", "photo", "센 세로 컷 하나 + 위 왼쪽 메모 한 줄. 왼쪽 한 변이 비는 쉬어 가는 쪽", [
    ("P", 18, 18.9, 76, 106.4, ""),
], [("memo", 22, "memo", "left:6cqw;top:6.4cqw;width:88cqw")])
lay("square", "photo", "정사각으로 잘라도 되는 한 장(물건·음식·사람). 아래 오른쪽에 메모 두 줄", [
    ("S", 6, 6, 88, 88, "r"),
], [("memo", 30, "memo", "right:6cqw;top:99cqw;width:56cqw;text-align:right")])
lay("stack", "photo", "같은 곳 가로 두 컷, 넓은 컷 위 → 그 안의 사람·장면 아래. 위아래 맞붙임, 글 없음", [
    ("L", 6, 6, 88, 58.7, ""), ("L", 6, 65.7, 88, 58.7, ""),
])
lay("offset", "photo", "이어진 가로 두 컷(걷다가·차 타고). 왼쪽-오른쪽 엇갈림, 아래 장 살짝 기울임 + 메모", [
    ("L", 6, 6, 72, 50.3, ""), ("L", 22, 59.3, 72, 50.3, "t"),
], [("memo", 24, "memo", "left:6cqw;bottom:8cqw;width:60cqw")])
lay("pair-wide", "photo", "세로 두 컷 짝(같은 종류: 가게·사람·음식) 위 + 그 장소 넓은 가로 컷 아래", [
    ("P", 6, 6, 42.5, 59, ""), ("P", 51.5, 6, 42.5, 59, ""), ("L", 6, 68, 88, 57.3, ""),
])
lay("squares-wide", "photo", "정사각 두 장 나란히(손·음식·물건) + 메모 + 그 자리 가로 컷", [
    ("S", 6, 6, 42.5, 42.5, "r"), ("S", 51.5, 6, 42.5, 42.5, "r"), ("L", 6, 63.8, 88, 61.5, ""),
], [("memo", 26, "memo", "left:6cqw;top:51.6cqw;width:88cqw")])
lay("satellite", "photo", "큰 가로 하나 + 아래 세로·정사각 위성 둘. 장면 첫 넓은 컷과 그 안의 것들", [
    ("L", 6, 6, 88, 58.7, ""), ("P", 6, 67.7, 42.5, 57.6, ""), ("S", 51.5, 67.7, 42.5, 42.5, "r"),
], [("memo", 22, "memo", "left:51.5cqw;bottom:8cqw;width:42.5cqw")])
lay("corner-wide", "photo", "가까운 정사각 한 장 위 + 넓은 가로 한 장 아래. 왼쪽 위 귀퉁이에 메모", [
    ("S", 37, 6, 57, 57, "r"), ("L", 6, 66.6, 88, 58.7, ""),
], [("memo", 26, "memo", "left:6cqw;top:6.4cqw;width:27cqw")])
lay("pano", "photo", "바다·능선·해안도로 같은 넓은 컷을 파노라마 띠로 위에 + 세로 한 장, 오른쪽 아래 메모", [
    ("L", 3, 6, 94, 33, ""), ("P", 6, 42, 58, 83.3, ""),
], [("memo", 30, "memo", "left:67cqw;bottom:8cqw;width:27cqw")])
lay("dense", "photo", "볼거리 많은 장소(시장·골목). 큰 세로 + 정사각 둘 + 아래 파노라마, 오른쪽에 메모", [
    ("P", 6, 6, 58, 82.9, ""), ("S", 67, 6, 27, 27, "r"), ("S", 67, 36, 27, 27, "r"), ("L", 6, 92, 88, 33.3, ""),
], [("memo", 20, "memo", "left:67cqw;top:66cqw;width:27cqw")])
lay("stair", "photo", "같은 장면 연속 세 컷(노을·파도·아이 걸음). 왼쪽-오른쪽-왼쪽 계단, 빈 귀퉁이에 메모·장소", [
    ("A", 6, 6, 42.5, 59.4, ""), ("A", 51.5, 35.95, 42.5, 59.4, ""), ("A", 6, 65.9, 42.5, 59.4, ""),
], [("memo", 20, "memo", "left:51.5cqw;top:6.4cqw;width:42.5cqw"),
    ("place", 14, "memo dim", "left:51.5cqw;bottom:8cqw;width:42.5cqw")])
lay("column", "photo", "시간 순서가 중요한 가로 세 컷(가는 길·변하는 하늘). 오른쪽 한 줄 세로 배열, 왼쪽 위 메모", [
    ("L", 36.5, 6, 57.5, 37.8, ""), ("L", 36.5, 46.75, 57.5, 37.8, ""), ("L", 36.5, 87.5, 57.5, 37.8, ""),
], [("memo", 26, "memo", "left:6cqw;top:6.4cqw;width:27cqw")])
lay("seq", "photo", "같은 자리 세 순간을 작은 정사각 띠로 위 + 그중 가장 좋은 한 장 크게 아래. 글 없음", [
    ("S", 6, 6, 27.33, 27.33, "r"), ("S", 36.33, 6, 27.33, 27.33, "r"), ("S", 66.67, 6, 27.33, 27.33, "r"),
    ("A", 6, 36.33, 88, 89, ""),
])
lay("grid", "photo", "같은 자리 다른 순간 네 장(사람 여럿·식탁). 2×2, 가운데 틈에 메모 한 줄", [
    ("A", 6, 6, 42.5, 56.6, ""), ("A", 51.5, 6, 42.5, 56.6, ""),
    ("A", 6, 68.7, 42.5, 56.6, ""), ("A", 51.5, 68.7, 42.5, 56.6, ""),
], [("memo", 24, "memo", "left:6cqw;top:63.4cqw;width:88cqw")])

# ---------- text / end ----------
lay("note", "text", "글이 긴 메모가 있을 때(그날 있었던 일). 위에 글, 오른쪽 아래 정사각 인화", [
    ("S", 36, 67.3, 58, 58, "r"),
], [("dayline", 18, "day", "left:6cqw;top:5.2cqw"), ("body", 200, "body", "left:6cqw;top:12.6cqw;width:88cqw")])
lay("last", "end", "닫는 쪽. 돌아가는 길·마지막 저녁 한 장 + 짧은 한 줄 + 판권 두 줄", [
    ("A", 6, 6, 88, 80, ""),
], [("memo", 24, "memo", "left:6cqw;top:89.4cqw;width:88cqw"),
    ("colophon", 48, "col", "left:6cqw;bottom:8cqw;width:88cqw")])
lay("back", "end", "맨 뒤 천 대지. 판권 두 줄만", [], [
    ("colophon", 48, "col", "left:0;right:0;bottom:12cqw;text-align:center")], pg="cloth")


def html() -> str:
    out = ["<!-- theme album layouts (r11b): print = white-bordered photo at a fixed print size on the board.\n"
           "     data-slot = photo index; data-text = text key. Generated by gen_album.py. -->\n"]
    for d in L:
        cls = " ".join(x for x in ("pg", d["name"], d["pg"]) if x)
        parts = []
        stamp_on = None
        for i, (s, x, y, w, h, f) in enumerate(d["prints"]):
            c = "pr" + "".join(" " + {"d": "dt", "t": "tl", "r": "rq"}[ch] for ch in f)
            inner = f'<img data-slot="{i}" alt="">'
            if "d" in f:
                stamp_on = i
                inner += '<p class="stp" data-text="stamp"></p>'
            parts.append(f'  <div class="{c}" style="--x:{x};--y:{y};--w:{w};--h:{h}">{inner}</div>')
        for k, n, c, st in d["texts"]:
            if k == "stamp":
                continue
            parts.append(f'  <p class="{c}" style="{st}" data-text="{k}"></p>')
        out.append(f'<template id="album/{d["name"]}"><div class="{cls}">\n' + "\n".join(parts) + "\n</div></template>\n")
    return "\n".join(out)


def js() -> str:
    rows = []
    for d in L:
        rows.append(json.dumps({"id": f'album/{d["name"]}', "kind": d["kind"], "slots": [p[0] for p in d["prints"]],
                                "texts": {k: n for k, n, *_ in d["texts"]}, "when": d["when"]}, ensure_ascii=False))
    return "[\n " + ",\n ".join(rows) + "\n]\n"


(OUT / "layouts.html").write_text(html(), encoding="utf-8")
(OUT / "layouts.json").write_text(js(), encoding="utf-8")
print(len(L), "layouts")
