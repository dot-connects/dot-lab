"""Mockup vs render contact sheet for the widgets (based on StandBy tools/sheets.py).

    python tools/sheets.py            -> shots/contact-widget.png (+ numbers printed)

1. Captures the mockup (mockups/v2/final/preview.html) in headless Chrome at 2x, twice: strength 100 % and 30 %, with a probe
   script that reports every widget's box, its text boxes (with baselines) and mic boxes.
2. Crops each mockup widget with the same shadow margins the renders use (WidgetRenderer MX/MT/MB) and pairs it with
   shots/widget/<name>.png from WidgetShots.
3. Per pair prints the worst text offset (left or right edge, and baseline) and the mic offset in dp, and the surface / mic
   colours sampled from both images, then writes one sheet: label | mockup | render.
A missing render is a red cross, never a silent shift.
"""
import html
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
MOCK = ROOT.parent / "mockups" / "v2" / "final" / "preview.html"
SHOTS = ROOT / "shots" / "widget"
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
MX, MT, MB = 6, 4, 12          # WidgetRenderer margins (dp)
K = 2                          # capture / render scale

# mockup widget order in final/preview.html (sections 1 and 2) -> render name
GRID = ["row-2", "row-one", "poster-2", "poster-one", "pillar-2", "pillar-one", "dot-2", "dot-one", "ticket-2", "ticket-one",
        "row-3", "poster-3", "pillar-3", "pillar-4", "dot-3", "ticket-4"]
STATES = ["row-st-none", "row-st-pend", "row-st-ok", "row-st-gone", "row-st-old", "row-st-nomic"]

PROBE = """<script>
addEventListener('load',()=>{%s document.fonts.ready.then(()=>setTimeout(()=>{
 const out=[];
 document.querySelectorAll('#wgrid .wg-home .wx, #stgrid .wg-home .wx').forEach(w=>{
  const R=w.getBoundingClientRect(),els=[];
  w.querySelectorAll('.f,.s,.big,.nm,.wh,.mic').forEach(e=>{let r=e.getBoundingClientRect();let bl=null;
   if(!e.classList.contains('mic')&&!e.classList.contains('nm')){const g=document.createRange();g.selectNodeContents(e);const q=g.getBoundingClientRect();if(q.width)r=q}
   if(!e.classList.contains('mic')){const z=document.createElement('span');z.style.cssText='display:inline-block;width:0;height:0;vertical-align:baseline';
    const tn=[...e.childNodes].filter(n=>n.nodeType===3&&n.textContent.trim());if(tn.length){tn[tn.length-1].after(z);bl=z.getBoundingClientRect().top-R.top;z.remove()}}
   const b=e.querySelector('b');
   els.push({c:e.className.split(' ')[0],flex:getComputedStyle(e).display==='flex',x:r.left-R.left,y:r.top-R.top,w:r.width,h:r.height,bl,
     all:e.textContent.trim(),b:b?b.textContent:null})});
  out.push({x:R.left+scrollX,y:R.top+scrollY,w:R.width,h:R.height,els})});
 const p=document.createElement('pre');p.id='OUT';p.textContent=JSON.stringify(out);document.body.appendChild(p);
},600))});
</script>"""


def capture(strength: int, tmp: Path) -> tuple[list, Image.Image]:
    inject = "" if strength == 100 else f"WOP={strength / 100};applyOp();"
    page = tmp / f"probe{strength}.html"
    page.write_text(MOCK.read_text(encoding="utf-8") + PROBE % inject, encoding="utf-8")
    url = page.as_uri()
    common = [CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--window-size=1240,3600", "--virtual-time-budget=15000",
              f"--user-data-dir={tmp / 'chr'}"]
    dom = subprocess.run(common + ["--dump-dom", url], capture_output=True, timeout=120).stdout.decode("utf-8", "replace")
    geo = json.loads(html.unescape(re.search(r'<pre id="OUT">(.*?)</pre>', dom, re.S).group(1)))
    png = tmp / f"shot{strength}.png"
    subprocess.run(common + [f"--force-device-scale-factor={K}", f"--screenshot={png}", url], capture_output=True, timeout=120)
    return geo, Image.open(png).convert("RGB")


def crop(im: Image.Image, w: dict) -> Image.Image:
    return im.crop((round((w["x"] - MX) * K), round((w["y"] - MT) * K), round((w["x"] + w["w"] + MX) * K), round((w["y"] + w["h"] + MB) * K)))


def hexof(px) -> str:
    return "#%02x%02x%02x" % px[:3]


def compare(w: dict, meta: dict, mock: Image.Image, rend: Image.Image) -> tuple[float, float, float, list[str]]:
    """Worst text dx, text dy (baseline), mic offset (dp) and colour notes."""
    texts = meta["texts"]
    dxs, dys = [], []
    for e in w["els"]:
        if e["c"] == "mic" or not e["all"] or e["all"] in ("–",):
            continue
        mx0, mx1 = e["x"] + MX, e["x"] + e["w"] + MX
        base = (e["y"] + 11 if e["flex"] else e["bl"]) if e["bl"] is not None or e["flex"] else None
        if e["c"] == "wh" and e["b"]:
            who = [t for t in texts if t["text"] == e["b"]]
            rest = e["all"][len(e["b"]):].strip()
            tail = [t for t in texts if t["text"].strip() == rest]
            if who and tail:
                best = min(((a, b) for a in who for b in tail), key=lambda ab: min(abs(ab[0]["box"][0] / K - mx0), abs(ab[1]["box"][2] / K - mx1)))
                dxs.append(min(abs(best[0]["box"][0] / K - mx0), abs(best[1]["box"][2] / K - mx1)))
                if base is not None:   # the baseline probe sits after the last text node: compare the "when" part
                    dys.append(abs(best[1]["base"] / K - (base + MT)))
            continue
        cand = [t for t in texts if t["text"] == e["all"]]
        if not cand:
            continue
        t = min(cand, key=lambda t: abs(t["base"] / K - ((base or 0) + MT)) + min(abs(t["box"][0] / K - mx0), abs(t["box"][2] / K - mx1)))
        dx = min(abs(t["box"][0] / K - mx0), abs(t["box"][2] / K - mx1))
        if e["c"] == "nm":
            dx = abs(t["box"][2] / K - mx1) if abs(t["box"][2] / K - mx1) < abs(t["box"][0] / K - (mx0 + 13)) else abs(t["box"][0] / K - (mx0 + 13))
        dxs.append(dx)
        if base is not None:
            dys.append(abs(t["base"] / K - (base + MT)))
    mics_m = [e for e in w["els"] if e["c"] == "mic"]
    mics_r = sorted([h for h in meta["hits"] if h["kind"] == "MIC"], key=lambda h: (h["box"][1], h["box"][0]))
    mic = 0.0
    notes = []
    for em, hr in zip(sorted(mics_m, key=lambda e: (e["y"], e["x"])), mics_r):
        cxm, cym = e_c = (em["x"] + em["w"] / 2 + MX, em["y"] + em["h"] / 2 + MT)
        cxr, cyr = (hr["box"][0] + hr["box"][2]) / 2 / K, (hr["box"][1] + hr["box"][3]) / 2 / K
        mic = max(mic, abs(cxm - cxr), abs(cym - cyr))
    if mics_m:
        em = mics_m[0]
        pt = (round((em["x"] + MX + 8) * K), round((em["y"] + MT + em["h"] / 2) * K))
        notes.append(f"mic {hexof(mock.getpixel(pt))}/{hexof(rend.getpixel(pt)) if pt[0] < rend.width and pt[1] < rend.height else '-'}")
    pt = (round((MX + 4) * K), round((MT + w["h"] * .5) * K))
    notes.append(f"surf {hexof(mock.getpixel(pt))}/{hexof(rend.getpixel(pt)) if pt[1] < rend.height else '-'}")
    return (max(dxs, default=0), max(dys, default=0), mic, notes)


def main() -> int:
    tmp = Path(tempfile.mkdtemp(prefix="wsheet"))
    rows = []
    for strength in (100, 30):
        geo, im = capture(strength, tmp)
        names = GRID + (STATES if strength == 100 else [])
        for w, name in zip(geo, names):
            full = f"{name}-{strength}" if not name.startswith("row-st") else name
            mock = crop(im, w)
            f = SHOTS / f"{full}.png"
            rend = Image.open(f).convert("RGB") if f.exists() else None
            nums = ""
            if rend is not None:
                dx, dy, mic, notes = compare(w, json.loads((SHOTS / f"{full}.json").read_text(encoding="utf-8")), mock, rend)
                nums = f"text dx {dx:.1f} dy {dy:.1f} mic {mic:.1f} dp | " + " ".join(notes)
                ok = "OK " if max(dx, dy, mic) <= 4 else "OFF"
                size = f"{mock.width // K}x{mock.height // K} vs {rend.width // K}x{rend.height // K}"
                print(f"{ok} {full:18} {size:18} {nums}")
            rows.append((full, mock, rend, nums))
    font = ImageFont.load_default()
    LABEL, GAP = 150, 8
    cw = max(r[1].width for r in rows) // K
    rh = [max(r[1].height, r[2].height if r[2] else 0) // K for r in rows]
    half = (len(rows) + 1) // 2
    cols = [rows[:half], rows[half:]]
    colw = LABEL + 2 * (cw + GAP)
    H = max(sum(rh[i] + GAP + 12 for i in range(len(c))) for c in cols) + 20
    sheet = Image.new("RGB", (2 * colw + 20, H), (32, 32, 34))
    d = ImageDraw.Draw(sheet)
    for ci, col in enumerate(cols):
        y = 10
        for j, (name, mock, rend, nums) in enumerate(col):
            x = ci * (colw + 20)
            h = rh[ci * half + j]
            d.text((x + 4, y + 4), name, fill=(230, 230, 230), font=font)
            d.text((x + 4, y + 18), "mockup | render", fill=(150, 150, 150), font=font)
            sheet.paste(mock.resize((mock.width // K, mock.height // K)), (x + LABEL, y))
            if rend is None:
                d.line((x + LABEL + cw + GAP, y, x + LABEL + 2 * cw + GAP, y + h), fill=(220, 40, 40), width=3)
            else:
                sheet.paste(rend.resize((rend.width // K, rend.height // K)), (x + LABEL + cw + GAP, y))
            d.text((x + LABEL, y + h + 1), nums[:110], fill=(160, 160, 160), font=font)
            y += h + GAP + 12
    out = ROOT / "shots" / "contact-widget.png"
    # 256 colours keeps the sheet under 1 MB so it can live in git next to the code
    sheet.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(out, optimize=True)
    print(out, f"{out.stat().st_size // 1024} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
