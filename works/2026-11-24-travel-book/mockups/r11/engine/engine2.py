"""Round-11b: designer-made layout library + model as editor. The model orders the photos, picks a layout per page,
fills its slots and writes its text slots; code validates and renders. Layout format = the app's layouts.json/html.

usage: python engine2.py <theme> [--tries N]        -> out2/<theme>-<n>.json + out2/<theme>.html
       python engine2.py <theme> --render out2/<theme>-1.json
Run with: uv run --no-project --with fonttools --with brotli --with pillow python engine2.py ...
"""
from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
LIB = HERE.parent / "lib"
OUT = HERE / "out2"
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent.parent / "r10"))
import engine  # noqa: E402
from PIL import Image  # noqa: E402

TRIPS = HERE.parent.parent / "trips"
TRIP_DIR = HERE  # set in main(): folder with trip.json; photos in IMG_DIR


def load_trip(name: str) -> dict:
    """jeju = the original set (engine/trip.json + mock/img5); others = mock/trips/<name>/{trip.json,img/}."""
    global TRIP_DIR
    if name == "jeju":
        TRIP_DIR, engine.IMG5 = HERE, HERE.parent.parent / "img5"
    else:
        TRIP_DIR, engine.IMG5 = TRIPS / name, TRIPS / name / "img"
    t = json.loads((TRIP_DIR / "trip.json").read_text(encoding="utf-8"))
    t["aspect"] = {k: (lambda s: s[0] / s[1])(Image.open(engine.IMG5 / f"{k}.webp").size) for k, _, _ in t["shots"]}
    small = TRIP_DIR / "img512"
    small.mkdir(exist_ok=True)
    for k, _, _ in t["shots"]:
        if not (small / f"{k}.jpg").exists():
            im = Image.open(engine.IMG5 / f"{k}.webp").convert("RGB")
            im.thumbnail((512, 512))
            im.save(small / f"{k}.jpg", quality=80)
    return t


def _b64(k: str) -> str:
    return engine._b64(k)

SCHEMA = {
    "type": "object", "additionalProperties": False, "required": ["title", "plan", "pages"],
    "properties": {
        "title": {"type": "string"}, "plan": {"type": "string"},
        "pages": {"type": "array", "items": {
            "type": "object", "additionalProperties": False, "required": ["layout", "intent", "photos", "texts"],
            "properties": {
                "layout": {"type": "string"}, "intent": {"type": "string"},
                "photos": {"type": "array", "items": {"type": "object", "additionalProperties": False, "required": ["id", "fx", "fy"],
                           "properties": {"id": {"type": "string"}, "fx": {"type": "integer"}, "fy": {"type": "integer"}}}},
                "texts": {"type": "array", "items": {"type": "object", "additionalProperties": False, "required": ["key", "text"],
                          "properties": {"key": {"type": "string"}, "text": {"type": "string"}}}},
            }}},
    },
}


def load_lib(key: str) -> tuple[list[dict], dict[str, str], str]:
    d = LIB / key
    lays = json.loads((d / "layouts.json").read_text(encoding="utf-8"))
    for l in lays:
        if isinstance(l.get("texts"), list):
            l["texts"] = {t: 30 for t in l["texts"]}
        l.setdefault("texts", {})
    tpl = dict(re.findall(r'<template id="([^"]+)">(.*?)</template>', (d / "layouts.html").read_text(encoding="utf-8"), re.S))
    return lays, tpl, (d / "theme.css").read_text(encoding="utf-8")


def orient(a: float) -> str:
    return "P" if a < 0.95 else "L" if a > 1.05 else "S"


def slot_ok(slot: str, a: float) -> bool:
    o = orient(a)
    return slot in ("A", "V") or slot == o or (slot == "S" and 0.8 <= a <= 1.25) or (o == "S" and slot in "PL")


def validate(book: dict, trip: dict, lays: list[dict], rules: dict) -> list[str]:
    errs: list[str] = []
    by = {l["id"]: l for l in lays}
    pages = book["pages"]
    lo, hi = rules["pages"]
    n = len(trip["shots"])
    if n > 40:
        lo, hi = max(lo, round(n * 0.4)), max(hi, round(n * 0.75))
    if not lo <= len(pages) <= hi:
        errs.append(f"쪽 수 {len(pages)}: {lo}~{hi}")
    used: dict[str, int] = {}
    for pi, p in enumerate(pages, 1):
        l = by.get(p["layout"])
        if not l:
            errs.append(f"{pi}쪽: 없는 틀 {p['layout']}")
            continue
        if (pi == 1) != (l.get("kind") == "cover"):
            errs.append(f"{pi}쪽: 표지 틀은 1쪽에만, 1쪽은 표지 틀")
        ids = [x["id"] for x in p["photos"]]
        if len(ids) != len(l["slots"]):
            errs.append(f"{pi}쪽 {l['id']}: 사진 {len(ids)}장, 이 틀의 칸은 {len(l['slots'])}개 ({''.join(l['slots'])})")
        for k, s in zip(ids, l["slots"]):
            if k not in trip["aspect"]:
                errs.append(f"{pi}쪽: 없는 사진 {k}")
                continue
            used[k] = used.get(k, 0) + 1
            if not slot_ok(s, trip["aspect"][k]):
                errs.append(f"{pi}쪽 {l['id']}: {k}({orient(trip['aspect'][k])}) 는 {s} 칸에 맞지 않는다")
        keys = {t["key"]: t["text"] for t in p["texts"]}
        for k, v in keys.items():
            if k not in l["texts"]:
                errs.append(f"{pi}쪽 {l['id']}: 글 칸 {k} 없음 (있는 칸: {', '.join(l['texts']) or '없음'})")
            elif len(v) > l["texts"][k]:
                errs.append(f"{pi}쪽 {l['id']}.{k}: {len(v)}자 > {l['texts'][k]}자")
        for k in l.get("required", []):
            if not keys.get(k):
                errs.append(f"{pi}쪽 {l['id']}: 글 칸 {k} 을 채워라")
    cover = {x["id"] for x in pages[0]["photos"]} if pages else set()
    for k, n in used.items():
        if n > 1 and not (n == 2 and k in cover):
            errs.append(f"{k}: {n}번 썼다 (표지 사진만 본문에 한 번 더)")
    if len(pages) > 1 and cover & {x["id"] for x in pages[1]["photos"]}:
        errs.append("2쪽: 표지 사진을 바로 다음 쪽에 다시 썼다 — 다른 사진으로")
    texts = [t["text"] for p in pages[1:] for t in p["texts"] if t["key"] not in ("title", "period", "stamp", "colophon") and len(t["text"]) < 60]
    timed = [bool(re.match(r"^[^,.]{1,14}[,.]\s*(오전|오후|아침|저녁|낮|밤|새벽|해 질|해질)", t)) for t in texts]
    if texts and sum(timed) > max(2, len(texts) * 0.3):
        errs.append(f"짧은 글 {len(texts)}개 중 {sum(timed)}개가 '장소, 시각' 으로 시작한다 — 같은 틀의 문장이 반복돼 기계처럼 보인다. 3할 이하로, 나머지는 본 것·한 일·들은 말로 시작해라")
    for i in range(2, len(timed)):
        if timed[i] and timed[i - 1] and timed[i - 2]:
            errs.append("'장소, 시각' 으로 시작하는 글이 3번 연속")
            break
    mr = rules.get("memo_ratio")
    if mr:
        body = pages[1:-1]
        withtext = sum(1 for p in body if any(t["key"] not in ("stamp",) for t in p["texts"]))
        if body and withtext < len(body) * mr:
            errs.append(f"글(메모·머리줄)이 있는 쪽이 {withtext}/{len(body)}쪽뿐이다. 최소 {round(mr * 100)}% — 빈 자리가 있는 틀이면 짧은 메모를 적어라")
    seq = [p["layout"] for p in pages]
    for i in range(2, len(seq)):
        if seq[i] == seq[i - 1] == seq[i - 2]:
            errs.append(f"{i - 1}~{i + 1}쪽: 같은 틀 {seq[i]} 3쪽 연속")
    body = [s for s, p in zip(seq, pages) if by.get(s, {}).get("kind") == "photo"]
    if body and len(set(body)) < min(rules.get("min_distinct", 6), len({l['id'] for l in lays if l.get('kind') == 'photo'})):
        errs.append(f"본문 틀을 {len(set(body))}가지만 썼다. 최소 {rules.get('min_distinct', 6)}가지 — 장면에 맞는 다른 틀을 골라라")
    return errs


def prompt(trip: dict, lays: list[dict], key: str, rules: dict) -> str:
    lines = [f"[{i + 1}] id={k} · {t} · {pl} · {orient(trip['aspect'][k])} {trip['aspect'][k]:.2f}" for i, (k, t, pl) in enumerate(trip["shots"])]
    notes = "\n".join(f"{n['day']}: {n['text']}" for n in trip["notes"])
    cat = "\n".join(f"- {l['id']} [{l.get('kind', '')}] 칸 {' '.join(l['slots']) or '없음'}"
                    + (f" · 글 {', '.join(f'{k}≤{v}자' for k, v in l['texts'].items())}" if l["texts"] else "")
                    + f" — {l.get('when', '')}" for l in lays)
    guide = (HERE / "themes2" / f"{key}.md").read_text(encoding="utf-8") + "\n\n" + (HERE / "themes2" / "_common.md").read_text(encoding="utf-8")
    n = len(trip["shots"])
    pr = rules["pages"] if n <= 40 else [max(rules["pages"][0], round(n * 0.4)), max(rules["pages"][1], round(n * 0.75))]
    return f"""{guide}

## 쪽 틀 (디자이너가 만든 것. 모양은 못 바꾼다. 쪽마다 하나를 고르고 칸에 사진을 넣는다)
칸: P=세로 사진, L=가로 사진, S=정사각에 가까운 사진, A=아무 사진. 칸 순서대로 photos 를 준다.
{cat}

## 출력 규칙 (기계가 검사한다)
- 1쪽은 cover 틀. 쪽 수 {pr[0]}~{pr[1]}. 같은 틀 3쪽 연속 금지. 본문 틀 최소 {rules.get('min_distinct', 6)}가지.
- 칸의 방향과 사진 방향을 맞춘다. 한 사진은 한 번만(표지 사진은 본문에 한 번 더 가능). 약한 사진은 빼도 된다.
- fx, fy(0~100): 칸에 맞춰 잘릴 때 남길 중심.
- texts: 그 틀에 있는 글 칸만, 글자 수 이하. 글 칸이 있어도 비워 둘 거면 넣지 않는다(표지 title·period 는 꼭).
- intent: 그 쪽에서 하려는 것 한 줄(책에 안 나온다). plan: 책 전체 흐름 3~6줄.

## 이 여행
사진(첨부 이미지 순서와 번호가 같다):
{chr(10).join(lines)}

여행한 사람이 적은 여행 성격: {trip.get('concept') or '(없음)'}

여행한 사람의 메모:
{notes}

답은 스키마 JSON 만."""


USAGE: list[dict] = []


def call(trip: dict, p: str, key: str) -> tuple[dict, float]:
    OUT.mkdir(exist_ok=True)
    (OUT / "schema.json").write_text(json.dumps(SCHEMA), encoding="utf-8")
    out = OUT / f"_last-{key}.txt"
    imgs = [f"{k}.jpg" for k, _, _ in trip["shots"]]  # relative to cwd: codex.cmd goes through cmd.exe (8191-char line limit)
    cmd = [shutil.which("codex") or "codex", "exec", "--skip-git-repo-check", "--sandbox", "read-only",
           "--output-schema", str((OUT / "schema.json").resolve()), "--output-last-message", str(out.resolve()), "--json", "-i", *imgs, "--", "-"]
    t0 = time.time()
    r = subprocess.run(cmd, input=p, capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=TRIP_DIR / "img512")
    if r.returncode != 0:
        raise SystemExit(f"codex failed {r.returncode}: {r.stderr[-600:]}")
    usage = {}
    for line in r.stdout.splitlines():
        if '"usage"' in line:
            try:
                u = json.loads(line).get("usage") or {}
                usage = {k: usage.get(k, 0) + v for k, v in u.items() if isinstance(v, int)}
            except ValueError:
                pass
    USAGE.append(usage)
    return json.loads(out.read_text(encoding="utf-8")), time.time() - t0


def render(book: dict, trip: dict, key: str, label: str = "") -> str:
    lays, tpl, css = load_lib(key)
    by = {l["id"]: l for l in lays}
    when = {k: t for k, t, _ in trip["shots"]}
    first_of_day: set[str] = set()
    seen_days: set[str] = set()
    for p in book["pages"][1:]:
        for x in p["photos"]:
            d = when.get(x["id"], "")[:10]
            if d and d not in seen_days:
                seen_days.add(d)
                first_of_day.add(x["id"])
    imgs: dict[str, str] = {}
    pages, text_all = [], ""
    mon = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"]
    for pi, p in enumerate(book["pages"], 1):
        html = tpl.get(p["layout"], '<div class="pg"></div>')
        for i, x in enumerate(p["photos"]):
            if x["id"] not in trip["aspect"]:
                continue
            imgs.setdefault(x["id"], _b64(x["id"]))
            html = re.sub(rf'<img([^>]*)data-slot="{i}"([^>]*)>',
                          lambda m, x=x: f'<img{m.group(1)}data-k="{x["id"]}" style="object-position:{x["fx"]}% {x["fy"]}%"{m.group(2)}>', html, count=1)
        texts = {t["key"]: t["text"] for t in p["texts"]}
        if "stamp" in by.get(p["layout"], {}).get("texts", {}) and "stamp" not in texts:
            k = next((x["id"] for x in p["photos"] if x["id"] in first_of_day), None)
            if k:
                d = when[k]
                texts["stamp"] = f"{mon[int(d[5:7]) - 1]} {int(d[8:10])}"
        for k, v in texts.items():
            text_all += v
            esc = v.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>")
            html = re.sub(rf'(<(\w+)[^>]*data-text="{k}"[^>]*>)(</\2>)', lambda m: m.group(1) + esc + m.group(3), html, count=1)
        html = re.sub(r'<(\w+)([^>]*)data-text="[^"]+"([^>]*)></\1>', "", html)  # empty text slots disappear
        pages.append(f'<div class="cell" title="{pi}. {p["layout"]} — {p["intent"]}">{html}</div>')
    ix = sorted((l for l in lays if "/ix" in l["id"]), key=lambda l: len(l["slots"]))
    if ix:
        order = list(dict.fromkeys(x["id"] for p in book["pages"][1:] for x in p["photos"] if x["id"] in trip["aspect"]))
        l = next((l for l in ix if len(l["slots"]) >= len(order)), ix[-1])
        n = len(l["slots"])
        pick = [order[round(i * (len(order) - 1) / max(n - 1, 1))] for i in range(n)] if len(order) > n else order
        html = tpl[l["id"]]
        for i, k in enumerate(pick):
            imgs.setdefault(k, _b64(k))
            html = re.sub(rf'<img([^>]*)data-slot="{i}"([^>]*)>', lambda m, k=k: f'<img{m.group(1)}data-k="{k}"{m.group(2)}>', html, count=1)
        html = re.sub(r'<img[^>]*data-slot="\d+"[^>]*>', "", html)
        pages.append(f'<div class="cell" title="색인 (코드가 붙임)">{html}</div>')
    import fonts
    m = re.search(r"kfonts:(.*?)\*/", css)
    faces = fonts.faces_css(fonts.parse(f"<!-- kfonts:{m.group(1)}-->"), text_all + "0123456789 .,·—JUNFEBMAR") if m else ""
    name = {"photo": "사진만", "a": "여백의 책", "album": "앨범"}.get(key, key)
    return f"""<section class="book t-{key}"><h2>{name} {label}</h2><p class="plan">{book.get('plan', '')}</p>
<div class="strip">{''.join(pages)}</div></section>
<style>{faces}{BASE}{css}</style><script>(()=>{{const I={json.dumps(imgs)};document.querySelectorAll('.t-{key} img[data-k]').forEach(i=>i.src='data:image/jpeg;base64,'+I[i.dataset.k])}})()</script>"""


BASE = """.book{margin:0 0 40px}.book h2{font:600 18px sans-serif;margin:16px 12px 4px}.plan{font:13px/1.5 sans-serif;color:#555;margin:0 12px 10px;max-width:760px}
.strip{display:flex;gap:14px;overflow-x:auto;padding:4px 12px 16px;scroll-snap-type:x mandatory}
.cell{flex:0 0 min(86vw,380px);scroll-snap-align:center}.cell>.pg{width:100%;aspect-ratio:3/4;position:relative;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.15)}
.cell .pg img{width:100%;height:100%;object-fit:cover;display:block}"""

PAGE = """<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<body style="margin:0;background:#e9e7e3">{body}</body>"""


def main() -> None:
    key, args = sys.argv[1], sys.argv[2:]
    tname = args[args.index("--trip") + 1] if "--trip" in args else "jeju"
    trip = load_trip(tname)
    tag = key if tname == "jeju" else f"{key}-{tname}"
    lays, _, _ = load_lib(key)
    lays = [l for l in lays if "/ix" not in l["id"]]  # the index page is appended by code
    rules = json.loads((HERE / "themes2" / f"{key}.json").read_text(encoding="utf-8"))
    OUT.mkdir(exist_ok=True)
    if "--render" in args:
        book = json.loads(Path(args[args.index("--render") + 1]).read_text(encoding="utf-8"))
        print("\n".join(validate(book, trip, lays, rules)) or "valid")
        (OUT / f"{tag}.html").write_text(PAGE.format(body=render(book, trip, key)), encoding="utf-8")
        return
    tries = int(args[args.index("--tries") + 1]) if "--tries" in args else 3
    base = prompt(trip, lays, key, rules)
    p = base
    for n in range(1, tries + 1):
        book, sec = call(trip, p, tag)
        (OUT / f"{tag}-{n}.json").write_text(json.dumps(book, ensure_ascii=False, indent=1), encoding="utf-8")
        errs = validate(book, trip, lays, rules)
        print(f"try {n}: {len(book['pages'])} pages, {len(errs)} errors, {sec:.0f}s, usage {USAGE[-1]}")
        for e in errs[:12]:
            print("  ", e)
        (OUT / f"{tag}.html").write_text(PAGE.format(body=render(book, trip, key, f"(시도 {n})")), encoding="utf-8")
        if not errs:
            break
        p = (base + "\n\n## 지난 답의 문제 (모두 고쳐서 책 전체를 다시 답해라)\n" + "\n".join(f"- {e}" for e in errs)
             + "\n\n지난 답:\n" + json.dumps(book, ensure_ascii=False))
    (OUT / f"{tag}-usage.json").write_text(json.dumps(USAGE), encoding="utf-8")


if __name__ == "__main__":
    main()
