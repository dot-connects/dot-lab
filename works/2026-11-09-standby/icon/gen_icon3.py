"""Stand Screen icon round 3: variants of A1 (colon on dark) that do not read as 'a screen with a clock'.
usage: python gen_icon3.py [id...]"""
import json, shutil, subprocess, sys, tempfile, time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

OUT = Path(__file__).parent / "r3"
OUT.mkdir(exist_ok=True)
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")
REF = Path(__file__).parent / "r2" / "colon-dark.png"

COMMON = ("Android app icon for 'Stand Screen' (a charging phone turned sideways becomes a bedside/desk clock). "
          "Style: crisp FLAT vector system icon designed by a human graphic designer, same family as the attached "
          "reference (dark navy background, warm amber #FFB36B and cream #F6EFE6 shapes, thick geometric strokes): solid "
          "flat colours only, NO gradients, NO glow, NO gloss, NO 3D, NO shadows, NO texture. Must read clearly at 48 px. "
          "Square 1:1 full-bleed background #141a2b reaching all edges; no rounded tile, border or device frame around the "
          "canvas. No text, letters or digits. IMPORTANT: do NOT draw a screen/monitor/phone outline with a clock inside "
          "it — competing apps all use that; the idea must be a distinct glyph.")
IDEAS = {
    "standing-colon": "Symbol: a big clock colon (two round dots, top amber, bottom cream) standing on a short thick "
                      "horizontal base bar, like the colon itself is standing on a stand. Nothing else.",
    "day-night-colon": "Symbol: a big clock colon made of two large circles: the top one a solid amber sun disc, the "
                       "bottom one a cream crescent moon. Nothing else, lots of breathing room.",
    "bracket": "Symbol: two thick rounded corner brackets (top-left and bottom-right, amber) framing a big cream clock "
               "colon of two dots in the middle; the brackets suggest a sideways screen without drawing one.",
    "tilt-colon": "Symbol: a thick amber rounded bar leaning back at about 70 degrees on a small cream wedge (a phone on "
                  "a stand, side view) with a large cream clock colon of two dots floating in front of it, offset right.",
}


def run(key: str) -> None:
    dest, started = OUT / f"{key}.png", time.time()
    prompt = ("Use your image generation tool to create exactly one image (use the attached icon only as the style "
              f"reference), then reply with one word: done.\n\n{COMMON}\n{IDEAS[key]}")
    args = [CODEX, "exec", "--json", "--sandbox", "read-only", "--config", 'approval_policy="never"',
            "--config", 'web_search="disabled"', "--skip-git-repo-check", "-i", str(REF), "-"]
    with tempfile.TemporaryDirectory() as cwd:
        r = subprocess.run(args, input=prompt, capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=cwd, timeout=540)
    thread = next((json.loads(l).get("thread_id") for l in r.stdout.splitlines() if '"thread.started"' in l), None)
    imgs = [q for q in (GEN / thread).glob("*.png") if q.stat().st_mtime >= started - 5] if thread else []
    if not imgs:
        print("FAIL", key, r.returncode, r.stderr[-300:], flush=True); return
    shutil.copy(max(imgs, key=lambda q: q.stat().st_mtime), dest)
    print("ok", key, round(time.time() - started), "s", flush=True)


with ThreadPoolExecutor(4) as ex:
    list(ex.map(run, sys.argv[1:] or list(IDEAS)))
