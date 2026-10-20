"""Record a draw-Korea play as a 9:16 reel: hook caption, a finger drawing, the reveal, the score.

Headless Chromium 540x960 at dpr 2; frames come from CDP Page.screencast at device pixels (1080x1920; Playwright's
recorder letterboxed the page into a corner), touch via CDP, captions injected into the page so they use the work's
own fonts. Writes out/reel.mp4 (silent; content-system adds music), out/cover.png and out/card.png.
Usage: python record_reel.py <work dir>   (needs playwright + ffmpeg on PATH)
The reel's score is written into manifest.json captions ("이 그림은 NN%"), so the caption cannot disagree with the video.
"""
import base64
import json
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from playwright.sync_api import sync_playwright

WORK = Path(sys.argv[1]).resolve()
OUT = WORK / "out"
OUT.mkdir(exist_ok=True)
# a plausible human attempt (traced from the operator's 55% drawing): narrow north, a filled Wonsan bay, no Jeju
DRAWING = [(445, 920), (520, 820), (560, 800), (600, 845), (630, 810), (670, 770), (740, 730), (770, 735), (790, 790), (740, 830),
           (720, 870), (690, 900), (670, 920), (690, 960), (690, 1080), (680, 1140), (700, 1220), (710, 1235), (650, 1260), (560, 1290),
           (430, 1330), (420, 1270), (470, 1230), (500, 1190), (520, 1170), (470, 1120), (490, 1060), (500, 1000), (445, 990), (425, 960)]
OVERLAY = """(() => {
  const st = document.createElement('style');
  st.textContent = `#cap{position:fixed;left:0;right:0;top:0;z-index:50;padding:18px 16px 14px;background:var(--paper);font-family:var(--hand);
    font-size:46px;line-height:1.05;text-align:center;color:var(--ink);transition:opacity .25s}
    #cap b{color:var(--red);font-weight:400} #finger{position:fixed;z-index:60;width:34px;height:34px;margin:-17px 0 0 -17px;border-radius:50%;
    background:rgba(29,39,51,.18);border:2px solid rgba(29,39,51,.45);pointer-events:none;display:none}`;
  document.head.append(st);
  const cap = document.createElement('div'); cap.id = 'cap'; document.body.append(cap);
  const f = document.createElement('div'); f.id = 'finger'; document.body.append(f);
  addEventListener('pointerdown', e => { f.style.display = 'block'; f.style.left = e.clientX + 'px'; f.style.top = e.clientY + 'px'; }, true);
  addEventListener('pointermove', e => { f.style.left = e.clientX + 'px'; f.style.top = e.clientY + 'px'; }, true);
  addEventListener('pointerup', () => { f.style.display = 'none'; }, true);
  window.caption = html => { cap.style.opacity = html ? 1 : 0; if (html) cap.innerHTML = html; };
})()"""


def dense(pts: list[tuple[float, float]], step: float = 7) -> list[tuple[float, float]]:
    out = []
    for (x1, y1), (x2, y2) in zip(pts, pts[1:] + pts[:1]):
        n = max(1, int(((x2 - x1) ** 2 + (y2 - y1) ** 2) ** .5 / step))
        out += [(x1 + (x2 - x1) * i / n, y1 + (y2 - y1) * i / n) for i in range(n)]
    return out


with sync_playwright() as p, tempfile.TemporaryDirectory() as tmp:
    b = p.chromium.launch()
    ctx = b.new_context(viewport={"width": 540, "height": 960}, device_scale_factor=2, has_touch=True, is_mobile=True)
    page = ctx.new_page()
    page.goto((WORK / "site" / "index.html").as_uri())
    page.wait_for_selector("#pad")
    page.evaluate("document.fonts.ready")
    page.evaluate(OVERLAY)
    page.evaluate("caption('지도 안 보고<br><b>한반도</b> 그릴 수 있어?')")
    cdp = ctx.new_cdp_session(page)
    frames: list[tuple[float, str]] = []

    def on_frame(ev: dict) -> None:
        frames.append((ev["metadata"]["timestamp"], ev["data"]))
        cdp.send("Page.screencastFrameAck", {"sessionId": ev["sessionId"]})

    cdp.on("Page.screencastFrame", on_frame)
    cdp.send("Page.startScreencast", {"format": "jpeg", "quality": 92, "maxWidth": 1080, "maxHeight": 1920, "everyNthFrame": 1})
    page.wait_for_timeout(2200)
    box = page.locator("#pad").bounding_box()
    xs, ys = [x for x, _ in DRAWING], [y for _, y in DRAWING]
    k = min(box["width"] * 0.62 / (max(xs) - min(xs)), box["height"] * 0.7 / (max(ys) - min(ys)))
    ox = box["x"] + (box["width"] - (max(xs) - min(xs)) * k) / 2 - min(xs) * k
    oy = box["y"] + box["height"] * 0.1 - min(ys) * k
    pts = dense([(ox + x * k, oy + y * k) for x, y in DRAWING])
    page.evaluate("caption('')")
    tp = lambda q: [{"x": q[0], "y": q[1], "id": 1}]
    cdp.send("Input.dispatchTouchEvent", {"type": "touchStart", "touchPoints": tp(pts[0])})
    for q in pts[1:] + pts[:1]:
        cdp.send("Input.dispatchTouchEvent", {"type": "touchMove", "touchPoints": tp(q)})
    cdp.send("Input.dispatchTouchEvent", {"type": "touchEnd", "touchPoints": []})
    page.wait_for_timeout(1500)
    page.evaluate("caption('채점 들어갑니다')")
    page.wait_for_timeout(2600)
    page.evaluate("caption(res.pct + '% · ' + label(res.pct) + '<br><b>너도 해 봐 · 링크는 프로필</b>')")   # the score row sits under the reel UI
    page.wait_for_timeout(2600)
    cdp.send("Page.stopScreencast")
    page.screenshot(path=OUT / "cover.png")
    blob = page.evaluate("""async () => { const b = await card(); return await new Promise(r => { const f = new FileReader(); f.onload = () => r(f.result); f.readAsDataURL(b); }); }""")
    (OUT / "card.png").write_bytes(base64.b64decode(blob.split(",")[1]))
    score = page.evaluate("res.pct")
    ctx.close(); b.close()
    if len(frames) < 30:
        sys.exit(f"only {len(frames)} screencast frames; reel not written")
    # screencast frames arrive only when the page changes: keep each one on screen until the next (concat durations)
    lines = []
    for i, (ts, data) in enumerate(frames):
        f = Path(tmp) / f"f{i:05d}.jpg"; f.write_bytes(base64.b64decode(data))
        lines += [f"file '{f.as_posix()}'", f"duration {(frames[i + 1][0] if i + 1 < len(frames) else ts + 0.5) - ts:.4f}"]
    lines.append(lines[-2])   # concat demuxer needs the last file repeated
    (Path(tmp) / "list.txt").write_text("\n".join(lines), encoding="utf-8")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(Path(tmp) / "list.txt"),
                    "-vf", "fps=30,scale=1080:1920:flags=lanczos,format=yuv420p", "-c:v", "libx264", "-crf", "18", "-movflags", "+faststart",
                    str(OUT / "reel.mp4")], check=True)
    man = WORK / "manifest.json"
    if man.exists():
        m = json.loads(man.read_text(encoding="utf-8"))
        for key in ("caption", "caption_threads"):
            if key in m:
                m[key] = re.sub(r"이 그림은 \d+%", f"이 그림은 {score}%", m[key])
        man.write_text(json.dumps(m, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("frames", len(frames), "span", round(frames[-1][0] - frames[0][0], 2), "s")
    print("score", score, "->", OUT / "reel.mp4")
