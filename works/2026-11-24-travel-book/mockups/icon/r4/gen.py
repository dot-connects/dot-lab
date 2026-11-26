"""Round 4 launcher icon candidates via Codex image generation (same call as photo-booth gen_logo.py).
usage: python gen.py [key...]   keys like shelf_a; default = every concept x a,b"""
import json, shutil, subprocess, sys, tempfile, time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

OUT = Path(__file__).parent
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")

COMMON = ("Android launcher icon artwork for a Korean app '여행 한 권' (one book per trip): you pick trip dates and the app "
          "turns that trip's phone photos into a small flip-through photo book that lives on a wooden bookshelf in the app. "
          "Match the app's look exactly: warm off-white paper background #F4EFE7, dark warm ink #2B2621, a thin warm light-oak "
          "wooden shelf, real travel photos (golden-hour sea, dark volcanic rocks, green fields), calm editorial Korean "
          "photobook taste, quiet and premium, not cute, not cartoon. Render as a real tactile object: soft natural window "
          "light, gentle soft shadow, subtle paper and cloth texture, like a beautifully photographed miniature product shot. "
          "One clear idea readable at 48 pixels: big silhouette, few shapes, the object fills about 60 % of the canvas and sits "
          "in the centre so a circle mask does not cut it. Square 1:1, background reaches all four edges; do NOT draw a rounded "
          "icon shape, border, phone, or hands. No text, letters, numbers or logos anywhere, including on covers and spines.")
IDEAS = {
    "shelf": "One small hardcover photo book standing upright, turned slightly toward the viewer, on a short light-oak wooden "
             "shelf ledge. Its cover is one full-bleed photo of a golden sunset over the sea with a dark rocky headland. "
             "Off-white cloth spine. Nothing else.",
    "flip": "One small square photo book lying open seen from slightly above, a single page caught mid-flip and curving up. "
            "The visible pages are full-bleed travel photos: golden sea sunset on the left, green coastal field on the right. "
            "Thin white page edges. Nothing else.",
    "stack": "Three slim photo books stacked slightly askew, seen from a low three-quarter angle. Cloth spines in ink #2B2621, "
             "cobalt #2A45AE and brick red #C8401F. The top book's cover is a full-bleed photo of a golden sea sunset. Nothing else.",
    "cover": "Close, straight-on view of one square photo book cover filling most of the canvas, slightly tilted, with a soft "
             "shadow: the cover is a full-bleed photo of a golden sunset over the sea and a dark rocky headland, with a "
             "narrow off-white cloth spine strip on the left edge. Nothing else.",
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
