"""Preview a theme layout library: python preview.py <key> [--no-shot]
-> lib/<key>/preview.html (every template filled with sample photos/text) + lib/<key>/sheet-<key>.png (flat, 230px pages).
Run with: uv run --no-project --with fonttools --with brotli --with pillow --with playwright python preview.py <key>
"""
from __future__ import annotations

import base64
import io
import json
import re
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
R10 = HERE.parent.parent / "r10"
IMG5 = HERE.parent.parent / "img5"
sys.path.insert(0, str(R10))

SAMPLE = {
    "title": "제주", "period": "2026. 6. 12 — 14", "sub": "2026. 6. 12 — 14", "day": "우도 가는 배, 6월 13일",
    "dayline": "둘째 날, 성산 쪽으로", "place": "협재, 한림읍",
    "cap": "협재, 오후 한 시. 물이 발목까지 왔다 돌아간다", "caption": "협재, 오후 한 시. 물이 발목까지 왔다 돌아간다",
    "memo": "우도 배. 갈매기가 끝까지 따라옴",
    "body": "어리목은 안개 때문에 아무것도 안 보였다. 그게 더 좋았다. 오름에서는 억새가 키만큼 자라 있었고, 마지막엔 귤밭 카페에서 귤 하나를 까먹고 공항으로 갔다.",
    "colophon": "제주, 2026년 6월 12일—14일\n사진 30장 중 24장",
}
FILL = "어리목은 안개 때문에 아무것도 안 보였다 그게 더 좋았다 오름에서는 억새가 키만큼 자라 있었다 "


def photos() -> dict[str, list[str]]:
    out: dict[str, list[str]] = {"P": [], "L": [], "S": []}
    for f in sorted(IMG5.glob("*.webp")):
        w, h = Image.open(f).size
        r = w / h
        out["P" if r < 0.95 else "L" if r > 1.05 else "S"].append(f.stem)
    return out


def b64(k: str) -> str:
    im = Image.open(IMG5 / f"{k}.webp").convert("RGB")
    im.thumbnail((700, 700))
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=76)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()


def sample_text(key: str, limit: int) -> str:
    s = SAMPLE.get(key) or SAMPLE.get(key.rstrip("0123456789")) or FILL
    while len(s) < limit * 0.85 and key not in SAMPLE:
        s += FILL
    return s[:limit]


def build(key: str) -> str:
    d = HERE / key
    css = (d / "theme.css").read_text(encoding="utf-8")
    lays = json.loads((d / "layouts.json").read_text(encoding="utf-8"))
    tpl = dict(re.findall(r'<template id="([^"]+)">(.*?)</template>', (d / "layouts.html").read_text(encoding="utf-8"), re.S))
    pool = photos()
    cur = {k: 0 for k in pool}
    imgs: dict[str, str] = {}
    pages, text_all = [], ""
    for lay in lays:
        html = tpl.get(lay["id"])
        if html is None:
            pages.append(f'<div class="pg" style="background:#fdd"><p style="padding:8cqw;font:4cqw sans-serif">템플릿 없음: {lay["id"]}</p></div>')
            continue
        for i, s in enumerate(lay["slots"]):
            kind = {"A": "L", "V": "L"}.get(s, s)
            kind = kind if pool[kind] else "L"
            k = pool[kind][cur[kind] % len(pool[kind])]
            cur[kind] += 1
            imgs.setdefault(k, b64(k))
            html = re.sub(rf'(<img[^>]*data-slot="{i}")', rf'\1 data-k="{k}"', html, count=1)
        texts = lay.get("texts", {})
        if isinstance(texts, list):
            texts = {t: 30 for t in texts}
        for t, n in texts.items():
            v = sample_text(t, n)
            text_all += v
            html = re.sub(rf'(<(\w+)[^>]*data-text="{t}"[^>]*>)(</\2>)', lambda m: m.group(1) + v.replace("\n", "<br>") + m.group(3), html, count=1)
        pages.append(f'<figure class="cell"><figcaption>{lay["id"]} · {lay.get("kind", "")} · {"".join(lay["slots"])}</figcaption>{html}</figure>')
    import fonts
    want = fonts.parse("<!-- kfonts:" + (re.search(r"kfonts:(.*?)\*/", css) or re.search("(.*)", "")).group(1) + "-->")
    faces = fonts.faces_css(want, text_all + "0123456789 .,·—JUNFEBMAR") if want else ""
    return f"""<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<style>{faces}
body{{margin:0;background:#e6e4df;font:13px sans-serif}}.lib{{display:flex;flex-wrap:wrap;gap:14px;padding:14px}}
.cell{{margin:0;width:230px}}.cell figcaption{{font:11px/1.3 monospace;color:#555;margin-bottom:4px;height:28px;overflow:hidden}}
.cell .pg{{width:230px;aspect-ratio:3/4;position:relative;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.18)}}
.pg img{{width:100%;height:100%;object-fit:cover;display:block}}
{css}</style><section class="t-{key}"><div class="lib">{''.join(pages)}</div></section>
<script>const I={json.dumps(imgs)};document.querySelectorAll('img[data-k]').forEach(i=>i.src=I[i.dataset.k]);</script>"""


def main() -> None:
    key = sys.argv[1]
    out = HERE / key / "preview.html"
    out.write_text(build(key), encoding="utf-8")
    print("wrote", out)
    if "--no-shot" in sys.argv:
        return
    from renderlock import render_slot
    from playwright.sync_api import sync_playwright
    with render_slot(), sync_playwright() as pw:
        b = pw.chromium.launch(channel="chrome")
        pg = b.new_page(viewport={"width": 1500, "height": 900}, device_scale_factor=1)
        pg.goto(out.as_uri())
        pg.wait_for_timeout(800)
        pg.screenshot(path=str(HERE / key / f"sheet-{key}.png"), full_page=True)
        b.close()
    print("shot", HERE / key / f"sheet-{key}.png")


if __name__ == "__main__":
    main()
