"""Text-box overlap + min font size check. usage: overlap.py key  (checks page widths 200, 330, 360)"""
import asyncio, sys
from pathlib import Path
R = Path(r"C:\Users\ehdck\AppData\Local\Temp\claude\c62cf31192c54cdd\50332936-9748-4893-ad04-c354d2294c91\scratchpad\mock\r9")
sys.path.insert(0, str(R))
from renderlock import render_slot
from playwright.async_api import async_playwright

JS = r"""
() => {
  const out = [];
  document.querySelectorAll('.pg').forEach((pg, pi) => {
    const P = pg.getBoundingClientRect();
    const boxes = [];
    const w = document.createTreeWalker(pg, NodeFilter.SHOW_TEXT);
    let n;
    while ((n = w.nextNode())) {
      if (!n.textContent.trim()) continue;
      const el = n.parentElement;
      const cs = getComputedStyle(el);
      if (cs.visibility === 'hidden' || cs.display === 'none') continue;
      const r = document.createRange(); r.selectNodeContents(n);
      let fs = parseFloat(cs.fontSize);
      if (el.closest('svg')) { const svg = el.closest('svg'); const vb = svg.viewBox.baseVal; fs = fs * svg.getBoundingClientRect().width / vb.width; }
      // clip to an overflow-hidden ancestor (ellipsis), and use the ink band (not the content area) for big type
      let clip = null; for (let e = el; e && e !== pg; e = e.parentElement) { const s = getComputedStyle(e); if (s.overflow === 'hidden' || s.overflowX === 'hidden') { clip = e.getBoundingClientRect(); break; } }
      for (const b of r.getClientRects()) if (b.width > 0) {
        let x0 = b.left, x1 = b.right, y0 = b.top, y1 = b.bottom;
        if (fs > 24 && !el.closest('svg')) { y1 = b.bottom - 0.2 * fs; y0 = y1 - 0.78 * fs; }
        if (clip) { x0 = Math.max(x0, clip.left); x1 = Math.min(x1, clip.right); y0 = Math.max(y0, clip.top); y1 = Math.min(y1, clip.bottom); }
        if (x1 - x0 > 0 && y1 - y0 > 0) boxes.push({el, t: n.textContent.trim().slice(0, 18), x0, y0, x1, y1, fs, big: fs > 40});
      }
    }
    const small = new Set();
    for (const b of boxes) if (b.fs < P.width * 0.028 - 0.05 && b.fs < 40) small.add(b.t + ' ' + (b.fs / P.width * 100).toFixed(2) + 'cqw');
    for (let i = 0; i < boxes.length; i++) for (let j = i + 1; j < boxes.length; j++) {
      const a = boxes[i], b = boxes[j];
      if (a.el === b.el) continue;
      const ox = Math.min(a.x1, b.x1) - Math.max(a.x0, b.x0), oy = Math.min(a.y1, b.y1) - Math.max(a.y0, b.y0);
      if (ox > 1.5 && oy > Math.max(2.5, 0.3 * Math.min(a.fs, b.fs))) out.push(`p${pi} OVERLAP "${a.t}" x "${b.t}" (${ox.toFixed(0)}x${oy.toFixed(0)})`);
    }
    for (const b of boxes) if (!b.big) if (b.x1 > P.right + 1 || b.y1 > P.bottom + 1 || b.x0 < P.left - 1) out.push(`p${pi} OUTSIDE "${b.t}"`);
    if (small.size) out.push(`p${pi} SMALL ` + [...small].join(' | '));
  });
  return out;
}
"""


async def main(key):
    html = (R / f"out-{key}.html").read_text(encoding="utf-8")
    async with async_playwright() as p:
        b = await p.chromium.launch(channel="chrome")
        for vw, pw in [(1500, 200), (430, 330), (1500, 360)]:
            pg = await b.new_page(viewport={"width": vw, "height": 900})
            css = f"<style>.strip{{flex-wrap:wrap}}.strip .pg{{flex:0 0 {pw}px;max-width:none}}</style>"
            await pg.set_content('<!doctype html><meta charset=utf-8>' + html + css)
            await pg.wait_for_timeout(2000)
            res = await pg.evaluate(JS)
            print(f"== {key} page {pw}px: {len(res)} issues")
            for r in res: print("  ", r)
            await pg.close()
        await b.close()

with render_slot():
    asyncio.run(main(sys.argv[1]))
