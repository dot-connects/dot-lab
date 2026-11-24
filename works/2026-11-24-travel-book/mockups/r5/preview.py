"""Build the r3 theme page and screenshot it.

usage: python preview.py <key> [<key> ...]   -> r3/out-<keys>.html + shots r3/shot-<key>-*.png
       python preview.py --all --publish     -> r3/travel-book-themes-r5.html (all five, no screenshots)
"""
import asyncio
import base64
import re
import sys
from pathlib import Path

R3 = Path(__file__).resolve().parent
IMG = R3.parent / "img5"
IMG_OLD = R3.parent / "img"
ORDER = ["a", "b", "c", "diary", "mag", "album"]
NAMES = {"a": "여백의 책", "b": "기록 카드", "c": "여행 수첩", "diary": "일기", "mag": "잡지", "album": "앨범"}


def _img(k: str) -> bytes:
    f = IMG / f"{k}.webp"
    return (f if f.exists() else IMG_OLD / f"{k}.webp").read_bytes()


def build(keys: list[str]) -> str:
    shell = (R3 / "shell.html").read_text(encoding="utf-8")
    fams: list[str] = []
    secs: list[str] = []
    for k in keys:
        f = R3 / f"{k}.html"
        if not f.exists():
            continue
        t = f.read_text(encoding="utf-8")
        m = re.search(r"<!--\s*fonts:(.*?)-->", t)
        if m:
            fams += [x.strip() for x in m.group(1).split("|") if x.strip()]
        secs.append(f'<div id="theme-{k}">{t}</div>')
    merged: dict[str, set[str]] = {}
    for x in fams:
        name, _, w = x.partition(":wght@")
        merged.setdefault(name, set()).update(w.split(";") if w else [])
    fams = [n + (":wght@" + ";".join(sorted(ws, key=int)) if ws else "") for n, ws in merged.items()]
    link = ""
    if fams:
        link = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?' + "&".join(f"family={x}" for x in fams) + '&display=swap">'
    toc = '<div class="toc">' + "".join(f'<a href="#theme-{k}">{NAMES.get(k, k)}</a>' for k in keys if (R3 / f"{k}.html").exists()) + "</div>"
    faces = "<style>" + (R3 / "fontfaces.css").read_text(encoding="utf-8") + "</style>"
    h = shell.replace("<!--FONTS-->", link + faces).replace("<!--SECTIONS-->", "\n".join(secs)).replace("<!--TOC-->", toc)
    keys_used = sorted(set(re.findall(r'src="\{\{(\w+)\}\}"', h)))
    h = re.sub(r'src="\{\{(\w+)\}\}"', r'data-k="\1" src="data:image/gif;base64,R0lGODlhAQABAAAAACwAAAAAAQABAAA="', h)
    imgs = ",".join(f'"{k}":"data:image/webp;base64,{base64.b64encode(_img(k)).decode()}"' for k in keys_used)
    # each photo embedded once; a tiny script fills every <img data-k>
    h += '<script>const IMG={' + imgs + '};document.querySelectorAll("img[data-k]").forEach(i=>{i.src=IMG[i.dataset.k]||""});</script>'
    return h


async def shoot(html: str, tag: str) -> None:
    from playwright.async_api import async_playwright
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page(viewport={"width": 430, "height": 900})
        await pg.set_content('<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width">' + html)
        await pg.wait_for_timeout(3000)
        print("scrollWidth", await pg.evaluate("document.documentElement.scrollWidth"))
        await pg.screenshot(path=str(R3 / f"shot-{tag}-0.png"), full_page=True)
        n = await pg.evaluate("document.querySelectorAll('.strip').length")
        for step in (1, 2, 3):
            await pg.evaluate(f"document.querySelectorAll('.strip').forEach(s=>s.scrollLeft=(s.scrollWidth-s.clientWidth)*{step}/3)")
            await pg.wait_for_timeout(300)
            await pg.screenshot(path=str(R3 / f"shot-{tag}-{step}.png"), full_page=True)
        print("strips", n, "shots", [f"shot-{tag}-{i}.png" for i in range(4)])
        await b.close()


if __name__ == "__main__":
    args = sys.argv[1:]
    if "--all" in args:
        keys = ORDER
    else:
        keys = [a for a in args if not a.startswith("--")]
    html = build(keys)
    if "--publish" in args:
        (R3 / "travel-book-themes-r5.html").write_text(html, encoding="utf-8")
        print("wrote", R3 / "travel-book-themes-r5.html", len(html))
    else:
        tag = "-".join(keys)
        (R3 / f"out-{tag}.html").write_text(html, encoding="utf-8")
        asyncio.run(shoot(html, tag))
