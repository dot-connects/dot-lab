"""Generate theme c 「여행 수첩」 layouts.html + layouts.json from one spec.

Page = 21 x 28 grid squares (one square = 100/21 cqw, the 5 mm graph of a passport-size travel notebook).
Every photo and text block sits on grid lines: (c, r, w, h) are in squares.
photo: ("P|L|S|A", c, r, w, h)          text: (key, max_chars, cls, c, r, w, h)
"""
from __future__ import annotations

import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
G = 100 / 21
L: list[dict] = []


def lay(name: str, kind: str, when: str, photos: list, texts: list = (), pg: str = "", extra: str = "") -> None:
    L.append(dict(name=name, kind=kind, when=when, photos=photos, texts=texts, pg=pg, extra=extra))


HD = ("date", 18, "hd", 1, 1, 19, 1)


def entry(c: float, r: float, w: float, place_w: float | None = None) -> list:
    """time (red) | circled number | place name, then area line under the name."""
    return [("time", 5, "tm", c, r, 3, 1), ("no", 2, "no", c + 3, r, 1, 1),
            ("place", 9, "pl", c + 4.2, r - 0.25, place_w or w - 4.2, 1.5),
            ("area", 16, "ar", c + 4.2, r + 1.25, w - 4.2, 1)]


# ---------- covers ----------
lay("cover-kraft", "cover", "사진 없는 크라프트 표지(고무줄 밴드). 조용한 여행, 또는 첫 쪽 사진이 셀 때 그 앞에", [],
    [("title", 5, "ttl", 1.5, 3, 15, 5), ("period", 22, "per", 1.6, 8, 15, 1)], pg="kraft", extra='<i class="band"></i>')
lay("cover-first", "cover", "기본 표지. 수첩 첫 쪽: 제목·날짜·간 길 한 줄 + 도착 컷 크게 아래", [("A", 1, 10, 19, 17)],
    [("title", 5, "ttl", 1, 1.5, 19, 5), ("period", 22, "per", 1.1, 6.5, 19, 1), ("route", 40, "rt", 1.1, 7.5, 19, 2)])
lay("cover-photo", "cover", "센 풍경 한 장이 있는 여행. 사진이 위 3/4을 채우고 아래 모눈 칸에 제목", [("A", 0, 0, 21, 19)],
    [("title", 5, "ttl s", 1, 19.75, 12, 4), ("period", 22, "per", 1.1, 24, 19, 1), ("route", 40, "rt", 1.1, 25, 19, 2)])

# ---------- day openers ----------
lay("day-route", "chapter", "날 여는 기본 쪽. 그날 간 길을 시각·장소 줄로(times 와 stops 는 같은 줄 수, 4~8줄) + 이동·첫 장면 가로 컷", [("L", 1, 15, 19, 12)],
    [HD, ("dayline", 16, "dl", 1, 3, 19, 2), ("times", 48, "tms", 1, 6, 3, 8), ("stops", 150, "stp", 4.2, 6, 15.8, 8)])
lay("day-wide", "chapter", "날 첫 컷이 넓은 가로일 때(배·길·바다). 사진이 위를 채우고 아래에 날짜 크게 + 그날 아침 메모", [("L", 0, 0, 21, 16)],
    [("day", 8, "bigd", 1, 17, 19, 2), ("dayline", 16, "dl", 1, 19.5, 19, 2), ("note", 110, "nt", 1, 22, 19, 5)])
lay("day-pair", "chapter", "날 여는 쪽, 사진 둘(이동 컷 가로 + 그날 첫 디테일). 왼쪽 아래 정사각 옆에 아침 메모", [("L", 1, 6, 19, 11), ("S", 1, 18, 9, 9)],
    [HD, ("dayline", 16, "dl", 1, 3, 19, 2), ("note", 90, "nt", 11, 18, 9, 9)])

# ---------- body: one place ----------
lay("entry-big", "photo", "장소 한 곳 기본 쪽. 시각·번호·장소·동네 + 메모 두 줄 + 그곳의 가장 좋은 한 장", [("A", 1, 10, 19, 17)],
    [HD, *entry(1, 3, 19), ("note", 52, "nt", 5.2, 6, 14.8, 3)])
lay("entry-pair", "photo", "장소 한 곳, 세로 두 컷 짝(같은 곳의 먼 컷·가까운 컷, 사람·물건). 사진마다 아래 한 줄", [("P", 1, 10, 9.25, 13), ("P", 10.75, 10, 9.25, 13)],
    [HD, *entry(1, 3, 19), ("note", 52, "nt", 5.2, 6, 14.8, 3), ("cap", 24, "cp", 1, 23.5, 9.25, 2), ("cap2", 24, "cp", 10.75, 23.5, 9.25, 2)])
lay("entry-side", "photo", "장소 한 곳, 세로 컷이 셀 때. 오른쪽 큰 세로 사진, 왼쪽 좁은 단에 장소와 긴 메모(수첩 여백에 적듯)", [("P", 7, 3, 13, 20)],
    [HD, ("time", 5, "tm", 1, 3, 3, 1), ("no", 2, "no", 4.5, 3, 1, 1), ("place", 10, "pl n", 1, 4.5, 5.5, 3),
     ("area", 12, "ar", 1, 7.5, 5.5, 2), ("note", 80, "nt n", 1, 10, 5.5, 17), ("cap", 34, "cp", 7, 23.5, 13, 2)])
lay("entry-scene", "photo", "장소 한 곳에서 한 장면을 길게 적고 싶을 때(메모가 긴 곳). 가로 한 장 + 그 아래 문단", [("L", 1, 6, 19, 13)],
    [HD, *entry(1, 3, 19), ("note", 150, "nt", 1, 20, 19, 7)])
lay("two-entries", "photo", "짧게 들른 곳 둘을 한 쪽에(각 가로 한 장 + 옆 메모). 같은 날 이어 들른 곳", [("L", 1, 5, 11, 9), ("L", 1, 17.5, 11, 9)],
    [HD, ("time", 5, "tm", 1, 3, 3, 1), ("no", 2, "no", 4, 3, 1, 1), ("place", 9, "pl", 5.2, 2.75, 14.8, 1.5),
     ("area", 14, "ar", 13, 5, 7, 2), ("note", 40, "nt", 13, 7, 7, 7),
     ("time2", 5, "tm", 1, 15.5, 3, 1), ("no2", 2, "no", 4, 15.5, 1, 1), ("place2", 9, "pl", 5.2, 15.25, 14.8, 1.5),
     ("area2", 14, "ar", 13, 17.5, 7, 2), ("note2", 40, "nt", 13, 19.5, 7, 7)])
lay("eat", "photo", "먹은 것 쪽. 그 집 가로 한 장 + 먹은 것 목록(한 줄에 하나, 값까지, 10줄 이내) + 음식 한 장", [("L", 1, 6, 19, 10), ("S", 12, 17, 8, 10)],
    [HD, *entry(1, 3, 19), ("eats", 110, "eats", 1, 17, 10, 10)])

# ---------- body: photo-led ----------
lay("full", "photo", "센 컷 하나(노을·바다·얼굴). 쪽 꽉 + 아래 모눈 띠에 시각·한 줄. 하루 한 번", [("A", 0, 0, 21, 24)],
    [("time", 5, "tm", 1, 25, 3, 1), ("cap", 44, "cp lg", 4.2, 25, 15.8, 2)])
lay("stack-time", "photo", "같은 곳 가로 두 컷, 시간이 흐른 것(낮→노을, 썰물→밀물). 왼쪽 여백에 각각 시각과 한 줄", [("L", 6, 3, 14, 11), ("L", 6, 15, 14, 11)],
    [HD, ("time", 5, "tm", 1, 3, 4.5, 1), ("cap", 24, "cp", 1, 4, 4.5, 9), ("time2", 5, "tm", 1, 15, 4.5, 1), ("cap2", 24, "cp", 1, 16, 4.5, 9)])
lay("three-moments", "photo", "이어진 세 순간(가는 길·바뀌는 하늘·걸음). 가로 셋을 오른쪽에 쌓고 왼쪽에 시각·한 줄씩", [("L", 7, 3, 13, 7), ("L", 7, 11, 13, 7), ("L", 7, 19, 13, 7)],
    [HD, ("time", 5, "tm", 1, 3, 5, 1), ("cap", 22, "cp", 1, 4, 5.5, 5), ("time2", 5, "tm", 1, 11, 5, 1), ("cap2", 22, "cp", 1, 12, 5.5, 5),
     ("time3", 5, "tm", 1, 19, 5, 1), ("cap3", 22, "cp", 1, 20, 5.5, 5)])
lay("strip-wide", "photo", "한 장소의 디테일 셋(손·간판·음식)을 띠로 위 + 그곳 넓은 가로 컷 아래 + 한 줄", [("S", 1, 3, 6, 6), ("S", 7.5, 3, 6, 6), ("S", 14, 3, 6, 6), ("L", 1, 10, 19, 13)],
    [HD, ("time", 5, "tm", 1, 23.5, 3, 1), ("cap", 60, "cp lg", 4.2, 23.5, 15.8, 3)])
lay("grid4", "photo", "같은 곳 다른 순간 넷(사람 여럿·식탁·골목). 2×2, 아래에 번호 붙여 한 줄씩", [("A", 1, 3, 9.25, 10), ("A", 10.75, 3, 9.25, 10), ("A", 1, 13.5, 9.25, 10), ("A", 10.75, 13.5, 9.25, 10)],
    [HD, ("caps", 75, "cp lst", 1, 24, 19, 3)])
lay("big-small", "photo", "넓은 가로 한 장 위 + 아래 정사각(그 안의 것) + 옆 좁은 단에 시각·장소·메모", [("L", 1, 3, 19, 12), ("S", 1, 16, 11, 11)],
    [HD, ("time", 5, "tm", 13, 16, 3, 1), ("place", 9, "pl sm", 13, 17, 7, 1.5), ("note", 60, "nt", 13, 19, 7, 8)])
lay("pano-tall", "photo", "바다·능선 같은 넓은 컷을 띠로 위(쪽 끝까지) + 세로 한 장 + 옆 단에 장소와 메모", [("L", 0, 0, 21, 8), ("P", 1, 9, 11, 15)],
    [("cap", 24, "cp", 1, 24.5, 11, 2), ("time", 5, "tm", 13, 9, 3, 1), ("place", 9, "pl sm", 13, 10, 7, 1.5), ("note", 78, "nt", 13, 12, 7, 15)])
lay("move", "photo", "이동한 쪽(배·버스·렌터카·비행기). 어디서→어디로 한 줄 + 걸린 시간·값 + 창밖 가로 컷 + 표·창 정사각", [("L", 1, 7, 19, 13), ("S", 1, 21, 6, 6)],
    [HD, ("route", 16, "pl rt2", 1, 2.75, 19, 1.5), ("area", 30, "ar", 1, 4.5, 19, 1), ("note", 66, "nt", 8.5, 21, 11.5, 6)])

# ---------- text / end ----------
lay("note-page", "text", "글이 긴 메모가 있는 날(그날 있었던 일 하나). 모눈 위 한 단락 + 그날 가로 한 장", [("L", 1, 15, 19, 12)],
    [HD, ("dayline", 16, "dl", 1, 3, 19, 2), ("body", 320, "nt", 1, 5, 19, 9)])
lay("places", "text", "끝에서 둘째 쪽. 다닌 곳 목록(날마다 머리 줄 하나 + '번호 장소 — 동네 시각' 한 줄씩, 22줄 이내, 동그라미 숫자 금지)", [],
    [("dayline", 16, "dl", 1, 2, 19, 2), ("places", 420, "lst", 1, 4.5, 19, 22.5)])
lay("last", "end", "닫는 쪽. 돌아가는 길·마지막 저녁 한 장 + 한 줄 + 판권", [("A", 1, 3, 19, 16)],
    [HD, ("note", 52, "nt", 1, 20, 19, 2), ("colophon", 90, "col", 1, 24, 19, 3)])
lay("back", "end", "맨 뒤 크라프트 뒤표지. 판권 두세 줄만", [], [("colophon", 90, "col", 1.5, 21.5, 14, 4)], pg="kraft", extra='<i class="band"></i>')


def box(c: float, r: float, w: float, h: float) -> str:
    return f"--c:{c};--r:{r};--w:{w};--h:{h}"


def main() -> None:
    html = ["<!-- theme c 「여행 수첩」 layouts (r11d). Generated by gen_c.py: edit the spec there.\n"
            "     Grid 21x28 squares; --c/--r/--w/--h in squares. data-slot = photo index, data-text = text key. -->\n"]
    js = []
    for d in L:
        lid = f"c/{d['name']}"
        parts = [d["extra"]] if d["extra"] else []
        for i, (_, c, r, w, h) in enumerate(d["photos"]):
            parts.append(f'<div class="ph" style="{box(c, r, w, h)}"><img data-slot="{i}" alt=""></div>')
        for key, _, cls, c, r, w, h in d["texts"]:
            parts.append(f'<p class="{cls}" style="{box(c, r, w, h)}" data-text="{key}"></p>')
        cls = f"pg {d['name']}" + (f" {d['pg']}" if d["pg"] else "")
        html.append(f'<template id="{lid}"><div class="{cls}">\n  ' + "\n  ".join(parts) + "\n</div></template>\n")
        js.append({"id": lid, "kind": d["kind"], "slots": [p[0] for p in d["photos"]],
                   "texts": {t[0]: t[1] for t in d["texts"]}, "when": d["when"]})
    (HERE / "layouts.html").write_text("\n".join(html), encoding="utf-8")
    (HERE / "layouts.json").write_text("[\n" + ",\n".join(" " + json.dumps(j, ensure_ascii=False) for j in js) + "\n]\n", encoding="utf-8")
    print(len(js), "layouts")


if __name__ == "__main__":
    main()
