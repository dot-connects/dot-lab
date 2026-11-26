"""Worst-case contrast of every text element sitting on a photo.

Shoots each page twice (normal, then text made transparent) and compares each text element's
colour against the 90th-percentile-worst background pixel under its box.
Usage: python textcontrast.py [--src=file] [keys...]
"""
import asyncio, io, sys
from pathlib import Path
from PIL import Image
from playwright.async_api import async_playwright
from renderlock import render_slot

R = Path(__file__).parent


def lum(c):
    r = [(v / 255) / 12.92 if v / 255 <= .04045 else ((v / 255 + .055) / 1.055) ** 2.4 for v in c[:3]]
    return .2126 * r[0] + .7152 * r[1] + .0722 * r[2]


JS_TEXT = """(pg)=>{const out=[];const pr=pg.getBoundingClientRect();
 const imgs=[...pg.querySelectorAll('img')].map(i=>i.getBoundingClientRect());
 for(const el of pg.querySelectorAll('*')){
  if(el.closest('svg'))continue;
  const own=[...el.childNodes].some(n=>n.nodeType===3&&n.textContent.trim());if(!own)continue;
  const r=el.getBoundingClientRect();if(r.width<2||r.height<2)continue;
  const onImg=imgs.some(b=>r.left<b.right&&r.right>b.left&&r.top<b.bottom&&r.bottom>b.top);if(!onImg)continue;
  const cs=getComputedStyle(el);if(cs.visibility==='hidden'||+cs.opacity===0)continue;
  out.push({t:el.textContent.trim().slice(0,24),c:cs.color,fs:parseFloat(cs.fontSize),fw:+cs.fontWeight,
   x:r.left-pr.left,y:r.top-pr.top,w:r.width,h:r.height});}
 return out}"""


async def go():
    src = next((a[6:] for a in sys.argv[1:] if a.startswith('--src=')), 'travel-book-themes-r5.html')
    keys = [a for a in sys.argv[1:] if not a.startswith('--')] or ['a', 'b', 'c', 'diary', 'mag', 'album']
    h = (R / src).read_text(encoding='utf-8')
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page(viewport={'width': 430, 'height': 900}, device_scale_factor=2)
        await pg.set_content('<!doctype html><meta charset=utf-8>' + h)
        await pg.wait_for_timeout(3500)
        for k in keys:
            for i, e in enumerate(await pg.query_selector_all(f'#theme-{k} .pg')):
                await e.scroll_into_view_if_needed()
                items = await e.evaluate(JS_TEXT)
                if not items:
                    continue
                await e.evaluate("p=>p.querySelectorAll('*').forEach(n=>{n.dataset.oc=n.style.color;n.style.setProperty('color','transparent','important');n.style.setProperty('-webkit-text-stroke','0','important')})")
                bg = Image.open(io.BytesIO(await e.screenshot())).convert('RGB')
                await e.evaluate("p=>p.querySelectorAll('*').forEach(n=>{n.style.removeProperty('color');n.style.removeProperty('-webkit-text-stroke');if(n.dataset.oc)n.style.color=n.dataset.oc})")
                for it in items:
                    rgb = [int(float(v)) for v in it['c'][it['c'].index('(') + 1:-1].split(',')[:3]]
                    lt = lum(rgb)
                    box = [int(v * 2) for v in (it['x'], it['y'], it['x'] + it['w'], it['y'] + it['h'])]
                    box = [max(0, box[0]), max(0, box[1]), min(bg.width, box[2]), min(bg.height, box[3])]
                    if box[2] <= box[0] or box[3] <= box[1]:
                        continue
                    px = list(bg.crop(box).getdata())[::3]
                    ls = sorted((abs(lum(q) - lt), lum(q)) for q in px)
                    lb = ls[int(len(ls) * .1)][1]  # 10% worst
                    cr = (max(lt, lb) + .05) / (min(lt, lb) + .05)
                    flag = 'LOW' if cr < 4.5 else ''
                    if flag or '-v' in sys.argv:
                        print(f"{k} p{i} {cr:5.2f} {flag:3} {it['fs']:.1f}px/{it['fw']} {it['t']!r}")
        await b.close()


with render_slot():
    asyncio.run(go())
