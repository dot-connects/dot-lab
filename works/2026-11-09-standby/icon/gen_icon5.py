"""Stand Screen icon round 5: new symbols that read more clearly as 'a charging phone turned into a clock',
in the two chosen palettes. usage: python gen_icon5.py [id...]"""
import json, shutil, subprocess, sys, tempfile, time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

OUT = Path(__file__).parent / "r5"
OUT.mkdir(exist_ok=True)
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")
REF = Path(__file__).parent / "r4" / "forest-cream.png"

COMMON = ("Android app icon for 'Stand Screen': a phone laid sideways on its charger becomes a bedside / desk clock "
          "(a 'StandBy mode' app). The icon must say 'sideways phone + charging + clock' at a glance, but must NOT be the "
          "common 'rectangle with digits inside' that competitors use, and no digits at all. Style: same family as the "
          "attached reference: crisp FLAT vector, thick rounded strokes, solid flat colours only, no gradients, glow, "
          "gloss, 3D, shadow or texture; symbol about 60 % of the canvas, centred precisely; reads at 48 px. "
          "Square 1:1 full-bleed background to all edges, no rounded tile, no border, no text.")
SYMBOL = {
    "plug": "Symbol: a big clock colon (two round dots) where a charging cable leaves the lower dot and curls down "
            "into a small plug, inside the two corner brackets of the reference (sideways screen hint).",
    "bolt": "Symbol: the reference's two corner brackets (sideways screen), and in the middle a clock colon whose two "
            "dots are joined by a small lightning bolt between them (charging clock).",
    "sidephone": "Symbol: a phone drawn as a thick rounded outline lying on its side (landscape) with a tiny camera dot "
                 "on its short left edge, resting on a short flat stand bar; inside it only a clock colon.",
    "moon": "Symbol: the reference's two corner brackets (sideways screen); the clock colon's upper dot is a crescent "
            "moon and the lower dot a round dot (night clock).",
    "pad": "Symbol: a sideways screen drawn as the reference's two corner brackets, floating just above a flat wireless "
           "charging pad (a wide rounded ellipse) with three small arcs of charge between them; clock colon in the screen.",
    "hands": "Symbol: the reference's two corner brackets (sideways screen) with two thick analog clock hands from a "
             "centre dot inside (like 10 past 10), and a short stand bar under the bottom bracket.",
}
PALETTE = {
    "forest": "Colours: background deep forest green #12271f, main strokes warm cream #F3E6C8, accent amber #FFB36B.",
    "cream": "Colours: background warm cream #F4EEE3, main strokes deep navy #1B2238, accent coral #FF6F59.",
}
JOBS = {f"{s}-{p}": (s, p) for s in SYMBOL for p in PALETTE}


def run(key: str) -> None:
    s, p = JOBS[key]
    dest, started = OUT / f"{key}.png", time.time()
    prompt = ("Use your image generation tool to create exactly one image (the attached icon is a style reference "
              f"only), then reply with one word: done.\n\n{COMMON}\n{SYMBOL[s]}\n{PALETTE[p]}")
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
    list(ex.map(run, sys.argv[1:] or list(JOBS)))
