"""Round 5 launcher icon candidates (app-icon skill: bigger mark, 66 dp safe circle) via Codex image generation (same call as photo-booth gen_logo.py).
usage: python gen.py [key...]   keys like shelf_a; default = every concept x a,b"""
import json, shutil, subprocess, sys, tempfile, time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

OUT = Path(__file__).parent
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")

COMMON = ("Android launcher icon artwork for a Korean app (one photo book per trip): you pick trip dates and the app "
          "turns that trip's phone photos into a small photo book that stands on a light-oak wall shelf in the app. "
          "Match the app's look: warm off-white plaster wall #F1EADF, dark warm ink #2B2621, light warm oak, a golden-hour "
          "sea photo on the cover, calm premium Korean photobook taste, not cute, not cartoon, not clip-art. "
          "Render as a real tactile miniature object in soft window light with a soft natural shadow. "
          "It must read instantly at 48 pixels on a phone home screen: ONE bold object, big simple silhouette, strong value "
          "contrast against the wall, the cover photo simplified to a few big shapes (a big low sun, a dark sea band, one dark "
          "headland) with no fine detail. The main object is BIG: it fills about 62 % of the canvas width and height and sits "
          "exactly centred, so a circle mask covering the middle 60 % shows the whole object. "
          "Square 1:1, plain wall background reaches all four edges; do NOT draw a rounded icon shape, circle, disc, border, frame, phone or "
          "hands. No text, letters, numbers or logos anywhere, including on covers and spines.")
IDEAS = {
    "lean": "One hardcover photo book leaning back against the wall at a slight angle on a short light-oak ledge, its "
            "shadow falling on the wall behind it. Off-white cloth spine on the left. The book is tall and big.",
    "face": "Straight-on, one thick square hardcover photo book standing on a short light-oak ledge, the cover photo filling "
            "almost the whole book, a narrow off-white cloth spine strip on the left, a soft contact shadow under it.",
    "pair": "Two photo books on a short light-oak ledge: the front one faces us with a golden sea-sunset cover and is big, "
            "a slimmer ink-coloured book stands just behind it to the left, only its spine visible. Two shapes only.",
    "open": "One square photo book standing open like a V on a short light-oak ledge, seen from the front: the left page "
            "is the golden sea sunset, the right page a deep blue sea with a dark headland. Thick page block visible.",
}


def run(key: str) -> None:
    idea = IDEAS[key.split("_")[0]]
    dest, started = OUT / f"{key}.png", time.time()
    prompt = f"Use your image generation tool to create exactly one image, then reply with one word: done.\n\n{COMMON}\nConcept: {idea}"
    args = [CODEX, "exec", "--json", "--sandbox", "read-only", "--config", 'approval_policy="never"',
            "--config", 'web_search="disabled"', "--skip-git-repo-check", "-"]
    with tempfile.TemporaryDirectory() as cwd:
        p = subprocess.run(args, input=prompt, capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=cwd, timeout=600)
    thread = next((json.loads(l).get("thread_id") for l in p.stdout.splitlines() if '"thread.started"' in l), None)
    imgs = [q for q in (GEN / thread).glob("*.png") if q.stat().st_mtime >= started - 5] if thread else []
    if not imgs:
        print("FAIL", key, p.returncode, p.stderr[-300:], flush=True); return
    shutil.copy(max(imgs, key=lambda q: q.stat().st_mtime), dest)
    print("ok", key, round(time.time() - started), "s", flush=True)


keys = sys.argv[1:] or [f"{k}_{v}" for k in IDEAS for v in "ab"]
with ThreadPoolExecutor(4) as ex:
    list(ex.map(run, keys))
