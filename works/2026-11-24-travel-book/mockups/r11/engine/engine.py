"""Round-11 prototype: a vision model edits and designs the whole book on a theme grid; we validate and render.

usage: python engine.py <theme> [--tries N] [--model M]   -> out/<theme>-<n>.json + out/<theme>.html
       python engine.py <theme> --render out/<theme>-1.json
The page is 3:4 split into a GRID_W x GRID_H grid of square cells. The model places photos and text in cell units.
"""
from __future__ import annotations

import json
import shutil
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
IMG5 = HERE.parent.parent / "img5"
GRID_W, GRID_H = 48, 64


def load_trip() -> dict:
    t = json.loads((HERE / "trip.json").read_text(encoding="utf-8"))
    t["aspect"] = {k: (lambda s: s[0] / s[1])(Image.open(IMG5 / f"{k}.webp").size) for k, _, _ in t["shots"]}
    return t


def load_theme(key: str) -> dict:
    return json.loads((HERE / "themes" / f"{key}.json").read_text(encoding="utf-8"))


# ---------- validation ----------

def _overlap(a: dict, b: dict) -> bool:
    return a["x"] < b["x"] + b["w"] and b["x"] < a["x"] + a["w"] and a["y"] < b["y"] + b["h"] and b["y"] < a["y"] + a["h"]


def validate(book: dict, trip: dict, theme: dict) -> list[str]:
    errs: list[str] = []
    roles = theme["roles"]
    used: dict[str, int] = {}
    crop_min = theme.get("crop_min", 0.7)
    min_side = theme.get("min_photo_side", 5)
    pages = book["pages"]
    if not (theme["pages"][0] <= len(pages) <= theme["pages"][1]):
        errs.append(f"쪽 수 {len(pages)}: {theme['pages'][0]}~{theme['pages'][1]} 이어야 한다")
    for pi, p in enumerate(pages, 1):
        photos = [i for i in p["items"] if i["kind"] == "photo"]
        texts = [i for i in p["items"] if i["kind"] == "text"]
        for it in p["items"]:
            tag = f"{pi}쪽 {it['photo'] or it['role']}"
            if it["w"] <= 0 or it["h"] <= 0 or it["x"] < 0 or it["y"] < 0 or it["x"] + it["w"] > GRID_W or it["y"] + it["h"] > GRID_H:
                errs.append(f"{tag}: 쪽 밖 (x {it['x']}, y {it['y']}, w {it['w']}, h {it['h']})")
        is_index = len(photos) >= theme.get("anchors", {}).get("index_min", 99)
        for it in photos:
            k = it["photo"]
            if k not in trip["aspect"]:
                errs.append(f"{pi}쪽: 없는 사진 id {k}")
                continue
            if not is_index:  # an index page repeats the book's photos by design
                used[k] = used.get(k, 0) + 1
            r, a = it["w"] / it["h"], trip["aspect"][k]
            kept = min(r / a, a / r)
            if kept < crop_min:
                errs.append(f"{pi}쪽 {k}: 칸 비율 {r:.2f} 이 사진 비율 {a:.2f} 와 너무 달라 {round((1 - kept) * 100)}% 가 잘린다 (최대 {round((1 - crop_min) * 100)}%)")
            if min(it["w"], it["h"]) < min_side and not is_index:
                errs.append(f"{pi}쪽 {k}: 너무 작다 (짧은 변 {min(it['w'], it['h'])}칸 < {min_side})")
        ov_max = theme.get("overlap_max", 0)
        for i, a in enumerate(photos):
            for b in photos[i + 1:]:
                if _overlap(a, b):
                    ox = min(a["x"] + a["w"], b["x"] + b["w"]) - max(a["x"], b["x"])
                    oy = min(a["y"] + a["h"], b["y"] + b["h"]) - max(a["y"], b["y"])
                    share = ox * oy / min(a["w"] * a["h"], b["w"] * b["h"])
                    if share > ov_max:
                        errs.append(f"{pi}쪽: {a['photo']} 와 {b['photo']} 가 {round(share * 100)}% 겹친다 (최대 {round(ov_max * 100)}%)")
        for it in photos:
            ws = theme.get("widths")
            if ws and it["w"] not in ws:
                errs.append(f"{pi}쪽 {it['photo']}: 폭 {it['w']}칸은 인화 크기가 아니다. 쓸 수 있는 폭: {ws}")
        for t in texts:
            ro = roles.get(t["role"])
            if not ro:
                errs.append(f"{pi}쪽: 없는 글 역할 {t['role']} (쓸 수 있는 것: {', '.join(roles)})")
                continue
            if len(t["text"]) > ro["max_chars"]:
                errs.append(f"{pi}쪽 {t['role']}: {len(t['text'])}자 > {ro['max_chars']}자")
            if not ro.get("on_photo") and any(_overlap(t, ph) for ph in photos):
                errs.append(f"{pi}쪽 {t['role']}: 글이 사진 위에 놓였다 (이 역할은 사진 밖에만)")
            if ro.get("under_photo") and not any(ph["y"] + ph["h"] <= t["y"] <= ph["y"] + ph["h"] + 2 and (t["x"] == ph["x"] or t["x"] + t["w"] == ph["x"] + ph["w"]) for ph in photos):
                errs.append(f"{pi}쪽 {t['role']}: 사진 바로 아래(1~2칸)에 붙이고 사진 왼끝이나 오른끝에 맞춰라")
    for k, n in used.items():
        if n > 1 and not (n == 2 and any(i["photo"] == k for i in pages[0]["items"])):
            errs.append(f"{k}: {n}번 썼다 (표지 말고는 한 번만)")
    cover_roles = {i["role"] for i in pages[0]["items"] if i["kind"] == "text"} if pages else set()
    for r in theme.get("cover_roles", []):
        if r not in cover_roles:
            errs.append(f"1쪽(표지): 글 역할 {r} 이 없다")
    cov = theme.get("min_cover")
    if cov:
        for pi, p in enumerate(pages[1:-1], 2):
            ph = [i for i in p["items"] if i["kind"] == "photo"]
            area = sum(i["w"] * i["h"] for i in ph) / (GRID_W * GRID_H)
            if ph and area < cov:
                errs.append(f"{pi}쪽: 인화가 대지의 {round(area * 100)}%만 채운다 (최소 {round(cov * 100)}%). 인화를 한 단계 큰 크기로 하거나 같은 장면 인화를 더 붙여라")
    mm = theme.get("memo_max")
    if mm:
        for pi, p in enumerate(pages, 1):
            n = sum(1 for i in p["items"] if i["kind"] == "text" and i["role"] == "memo")
            if n > mm:
                errs.append(f"{pi}쪽: 메모 {n}개. 한 쪽에 최대 {mm}개 — 한 덩이로 합쳐라")
    lim = theme.get("limits", {})
    if lim:
        body = [p for p in pages[1:] if p["items"]]
        small = sum(1 for p in body for i in p["items"] if i["kind"] == "photo" and i["w"] < lim["small_w"] and sum(j["kind"] == "photo" for j in p["items"]) == 1)
        if small > lim["small_max"]:
            errs.append(f"작은 사진 한 장 쪽(폭 {lim['small_w']}칸 미만)이 {small}쪽이다. 최대 {lim['small_max']}쪽 — 나머지는 hang 기본 크기로 키워라")
        for role, n in lim.get("role_max", {}).items():
            c = sum(1 for p in body if not any(i["kind"] == "photo" for i in p["items"]) and any(i["role"] == role for i in p["items"]))
            if c > n:
                errs.append(f"사진 없는 {role} 쪽이 {c}쪽이다. 최대 {n}쪽")
    if theme.get("anchors"):
        errs += anchor_rules(book, theme["anchors"])
    return errs


def anchor_rules(book: dict, a: dict) -> list[str]:
    """No floating photos: every photo hangs from a baseline or an edge, and is wide unless a named exception allows it."""
    errs: list[str] = []
    pages = book["pages"]
    sigs: list[tuple] = []
    for pi, p in enumerate(pages, 1):
        photos = [i for i in p["items"] if i["kind"] == "photo"]
        sigs.append(tuple(sorted((i["x"], i["y"], i["w"], i["h"]) for i in photos)))
        if pi == 1 or not photos:
            continue
        last = pi == len(pages)
        if len(photos) >= a.get("index_min", 99):
            errs.append(f"{pi}쪽: 사진 {len(photos)}장 격자는 만들지 마라 (색인은 기계가 책 끝에 붙인다). 한 쪽에 사진은 최대 {a['index_min'] - 1}장")
            continue
        pair = len(photos) == 2 and photos[0]["w"] == photos[1]["w"] and photos[0]["y"] == photos[1]["y"]
        for it in photos:
            others = [o for o in photos if o is not it]
            gaps = range(0, a.get("gap_max", 1) + 1)
            v_ok = (it["y"] in a["tops"] or it["y"] + it["h"] in a["bottoms"]
                    or any(it["y"] in (o["y"], o["y"] + o["h"] + g) or it["y"] + it["h"] in (o["y"] + o["h"], o["y"] - g) for o in others for g in gaps))
            cx = it["x"] + it["w"] / 2
            h_ok = (it["x"] in a["lefts"] or it["x"] + it["w"] in a["rights"] or abs(cx - GRID_W / 2) <= .5
                    or any(it["x"] in (o["x"], o["x"] + o["w"] + g) or it["x"] + it["w"] in (o["x"] + o["w"], o["x"] - g) for o in others for g in gaps))
            if not v_ok:
                errs.append(f"{pi}쪽 {it['photo']}: 세로로 떠 있다 (y={it['y']}). 위 기준선 y∈{a['tops']} 이나 다른 사진 바로 아래, 또는 아래 끝에 붙여라")
            if not h_ok:
                errs.append(f"{pi}쪽 {it['photo']}: 가로로 어중간하다 (x={it['x']}). 좌우 여백선·쪽 끝에 붙이거나 가운데 정렬")
            if it["w"] < a["min_w"] and not pair and not last:
                errs.append(f"{pi}쪽 {it['photo']}: 폭 {it['w']}칸 < {a['min_w']}. 좁은 사진은 나란한 짝(같은 폭·같은 y)이나 마지막 컷에만")
    cover = {i["photo"] for i in pages[0]["items"] if i["kind"] == "photo"}
    if len(pages) > 1 and cover & {i["photo"] for i in pages[1]["items"] if i["kind"] == "photo"}:
        errs.append("2쪽: 표지 사진을 바로 다음 쪽에 다시 썼다. 표지 사진을 본문에 다시 쓰려면 그 장면 자리(뒤쪽)에")
    pairs = sum(1 for p in pages[1:] if sum(i["kind"] == "photo" for i in p["items"]) == 2)
    need = round((len(pages) - 1) * a.get("pair_ratio", 0))
    if pairs < need:
        errs.append(f"두 장 쪽(짝)이 {pairs}쪽뿐이다. 최소 {need}쪽 — 같은 장면의 가로 두 컷은 쌓은 짝으로 묶어라")
    for i in range(2, len(sigs) if a.get("no_repeat3", True) else 0):
        if sigs[i] and sigs[i] == sigs[i - 1] == sigs[i - 2]:
            errs.append(f"{i - 1}~{i + 1}쪽: 같은 배치가 3쪽 연속")
    for i in range(1, len(sigs)):
        if sigs[i] == sigs[i - 1] == ((0, 0, GRID_W, GRID_H),):
            errs.append(f"{i}~{i + 1}쪽: 풀 블리드 2쪽 연속")
    return errs


# ---------- render ----------

def _b64(k: str) -> str:
    import base64, io
    im = Image.open(IMG5 / f"{k}.webp").convert("RGB")
    im.thumbnail((900, 900))
    buf = io.BytesIO(); im.save(buf, "JPEG", quality=78)
    return base64.b64encode(buf.getvalue()).decode()


MON = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"]


def render(book: dict, theme: dict, label: str = "", trip: dict | None = None) -> str:
    css = theme["css"]
    stamps: set[str] = set()
    if theme.get("stamp") and trip:
        used = {i["photo"] for p in book["pages"][1:] for i in p["items"] if i["kind"] == "photo"}
        days: dict[str, str] = {}
        for k, t, _ in trip["shots"]:
            if k in used:
                days.setdefault(t[:10], k)
        stamps = set(days.values())
        when = {k: t for k, t, _ in trip["shots"]}
    imgs: dict[str, str] = {}
    out = []
    for pi, p in enumerate(book["pages"], 1):
        items = []
        for it in p["items"]:
            pos = (f"left:{it['x'] / GRID_W * 100:.3f}%;top:{it['y'] / GRID_H * 100:.3f}%;"
                   f"width:{it['w'] / GRID_W * 100:.3f}%;height:{it['h'] / GRID_H * 100:.3f}%")
            if it["kind"] == "photo":
                imgs.setdefault(it["photo"], _b64(it["photo"]))
                img = f'<img class="ph" data-k="{it["photo"]}" style="{pos};object-position:{it["fx"]}% {it["fy"]}%">'
                if it["photo"] in stamps and pi > 1:
                    d = when[it["photo"]]
                    img = f'<div class="pr" style="{pos}">{img.replace(pos, "")}<b class="st">{MON[int(d[5:7]) - 1]} {int(d[8:10])}</b></div>'
                items.append(img)
            else:
                txt = it["text"].replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>")
                onp = " onp" if any(_overlap(it, o) for o in p["items"] if o["kind"] == "photo") else ""
                items.append(f'<div class="tx r-{it["role"]}{onp}" style="{pos};text-align:{it["align"]}">{txt}</div>')
        out.append(f'<div class="pg bg-{p["bg"]}" title="{pi}. {p["intent"]}">{"".join(items)}<i class="no">{pi}</i></div>')
    if theme.get("auto_index"):
        cells, seen = [], set()
        for pi, p in enumerate(book["pages"][1:], 2):
            for it in p["items"]:
                if it["kind"] == "photo" and it["photo"] not in seen:
                    seen.add(it["photo"])
                    cells.append(f'<figure><img data-k="{it["photo"]}"><figcaption>{pi}</figcaption></figure>')
        out.append(f'<div class="pg bg-paper idx"><div class="ix">{"".join(cells)}</div></div>')
    data = json.dumps(imgs)
    sys.path.insert(0, str(HERE.parent.parent / "r10"))
    import fonts
    text = "".join(i["text"] for p in book["pages"] for i in p["items"]) + theme["name"] + "0123456789 .,·—JANFEBMRPYULGSOCTVD"
    css = fonts.faces_css({f: set(w) for f, w in theme.get("fonts", {}).items()}, text) + css
    return f"""<section class="book t-{theme['key']}"><h2>{theme['name']} {label}</h2><p class="plan">{book.get('plan', '')}</p>
<div class="strip">{''.join(out)}</div></section>
<style>{BASE_CSS}{css}</style><script>(()=>{{const I={data};document.querySelectorAll('.t-{theme['key']} img[data-k]').forEach(i=>i.src='data:image/jpeg;base64,'+I[i.dataset.k])}})()</script>"""


BASE_CSS = """
.book{margin:0 0 40px}.book h2{font:600 18px sans-serif;margin:16px 12px 4px}.plan{font:13px/1.5 sans-serif;color:#555;margin:0 12px 10px;max-width:760px}
.strip{display:flex;gap:14px;overflow-x:auto;padding:4px 12px 16px;scroll-snap-type:x mandatory}
.pg{position:relative;flex:0 0 min(86vw,380px);aspect-ratio:3/4;container-type:inline-size;overflow:hidden;scroll-snap-align:center;box-shadow:0 1px 4px rgba(0,0,0,.15)}
.pg>*{position:absolute;margin:0;box-sizing:border-box}.ph{object-fit:cover;display:block}.pr>.ph{position:absolute;inset:0;width:100%;height:100%}
.no{position:absolute;right:-9999px}
"""


# ---------- model call ----------

def prompt(trip: dict, theme: dict) -> str:
    lines = [f"[{i + 1}] id={k} · {t} · {pl} · {'가로' if trip['aspect'][k] > 1.05 else '세로' if trip['aspect'][k] < 0.95 else '정사각'} {trip['aspect'][k]:.2f}"
             for i, (k, t, pl) in enumerate(trip["shots"])]
    notes = "\n".join(f"{n['day']}: {n['text']}" for n in trip["notes"])
    grammar = (HERE / "themes" / f"{theme['key']}.md").read_text(encoding="utf-8")
    roles = "\n".join(f"- {r}: {v['desc']} (최대 {v['max_chars']}자{', 사진 위 가능' if v.get('on_photo') else ''})" for r, v in theme["roles"].items())
    return f"""{grammar}

## 출력 규칙 (기계가 검사한다)
- 쪽은 3:4. 가로 {GRID_W}칸 x 세로 {GRID_H}칸의 정사각 칸 격자. x,y 는 왼쪽 위 칸(0부터), w,h 는 칸 수. x+w ≤ {GRID_W}, y+h ≤ {GRID_H}.
  블리드(쪽 끝까지)는 x=0 또는 x+w={GRID_W} 처럼 가장자리에 닿게.
- 사진 칸의 비율(w/h)은 사진 비율과 가까워야 한다: 잘려 나가는 부분 최대 {round((1 - theme.get('crop_min', .7)) * 100)}%. 짧은 변 최소 {theme.get('min_photo_side', 5)}칸.
  fx,fy(0~100)는 잘릴 때 남길 중심(object-position).
- 사진끼리 겹치지 않는다. 한 사진은 한 번만(표지 사진은 본문에 한 번 더 써도 된다). 모든 사진을 쓸 필요는 없다 — 약한 사진은 뺀다.
- 쪽 수 {theme['pages'][0]}~{theme['pages'][1]}. 첫 쪽이 표지.
- 글 item 은 kind=text, photo="", fx=fy=0. 사진 item 은 kind=photo, text="", role="".
- 글 역할(role):
{roles}
- intent: 그 쪽에서 무엇을 하려는지 한 줄(편집 메모, 책에는 안 나온다). plan: 책 전체 흐름 설계 3~6줄.

## 이 여행
사진(첨부 이미지 순서와 번호가 같다):
{chr(10).join(lines)}

여행한 사람의 메모:
{notes}

이 사진들과 메모로 「{theme['name']}」 테마의 책 한 권을 편집하고 디자인해라. 답은 스키마 JSON 만."""


def call_model(trip: dict, theme: dict, fix: str = "", prev: str = "", model: str | None = None) -> tuple[dict, float]:
    p = prompt(trip, theme)
    if fix:
        p += f"\n\n## 지난 답의 문제 (모두 고쳐서 책 전체를 다시 답해라)\n{fix}\n\n지난 답:\n{prev}"
    imgs = [str(HERE / "img512" / f"{k}.jpg") for k, _, _ in trip["shots"]]
    out = HERE / "out" / f"_last-{theme['key']}.txt"
    cmd = [shutil.which("codex") or "codex", "exec", "--skip-git-repo-check", "--sandbox", "read-only", "--output-schema", str(HERE / "schema.json"),
           "--output-last-message", str(out), "-i", *imgs]
    if model:
        cmd += ["-m", model]
    cmd += ["--", "-"]
    t0 = time.time()
    r = subprocess.run(cmd, input=p, capture_output=True, text=True, encoding="utf-8", shell=False)
    if r.returncode != 0:
        raise SystemExit(f"codex failed {r.returncode}: {r.stderr[-800:]}")
    return json.loads(out.read_text(encoding="utf-8")), time.time() - t0


def main() -> None:
    key = sys.argv[1]
    args = sys.argv[2:]
    trip, theme = load_trip(), load_theme(key)
    (HERE / "out").mkdir(exist_ok=True)
    if "--render" in args:
        book = json.loads(Path(args[args.index("--render") + 1]).read_text(encoding="utf-8"))
        print("\n".join(validate(book, trip, theme)) or "valid")
        (HERE / "out" / f"{key}.html").write_text(PAGE.format(body=render(book, theme, trip=trip)), encoding="utf-8")
        return
    tries = int(args[args.index("--tries") + 1]) if "--tries" in args else 3
    model = args[args.index("--model") + 1] if "--model" in args else None
    fix, prev = "", ""
    for n in range(1, tries + 1):
        book, sec = call_model(trip, theme, fix, prev, model)
        (HERE / "out" / f"{key}-{n}.json").write_text(json.dumps(book, ensure_ascii=False, indent=1), encoding="utf-8")
        errs = validate(book, trip, theme)
        print(f"try {n}: {len(book['pages'])} pages, {len(errs)} errors, {sec:.0f}s")
        for e in errs[:12]:
            print("  ", e)
        (HERE / "out" / f"{key}.html").write_text(PAGE.format(body=render(book, theme, f'(시도 {n})', trip)), encoding="utf-8")
        if not errs:
            break
        fix, prev = "\n".join(f"- {e}" for e in errs), json.dumps(book, ensure_ascii=False)


PAGE = """<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<body style="margin:0;background:#e9e7e3">{body}</body>"""

if __name__ == "__main__":
    main()
