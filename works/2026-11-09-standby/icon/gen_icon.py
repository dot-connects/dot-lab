"""Stand Screen app icon candidates via Codex image generation (same call as photo-booth gen_logo.py).
usage: python gen_icon.py [id...]"""
import json, shutil, subprocess, sys, tempfile, time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

OUT = Path(__file__).parent
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")

COMMON = ("App icon artwork for an Android app called 'Stand Screen': a phone standing sideways on its charger becomes a "
          "bedside and desk clock-and-widget display, like a calm night-stand clock. Mood: calm, premium, warm at night; "
          "deep near-black navy background #0b0d14 with a warm amber glow #FFB36B, soft cream highlights. "
          "One bold idea, instantly readable at 48 pixels: few shapes, big silhouette, generous margins. "
          "Square 1:1 full-bleed: the background reaches all four edges and corners; do NOT draw a rounded icon shape, "
          "border, device mockup frame around the canvas, or drop shadow. No text, letters, numbers or digits. "
          "No logos of existing brands, nothing that looks like Apple's StandBy or an iPhone.")
IDEAS = {
    "lean": "A sleek phone lying sideways, leaning back on a small rounded stand, its screen showing a big soft amber clock "
            "made only of two abstract rounded bars and a colon of two dots (no real digits), a faint warm glow under it.",
    "daynight": "A landscape rounded-rectangle screen split in two halves: the left half warm amber daylight with a small sun "
                "disc, the right half deep night with a thin crescent moon, both halves sitting on one small stand.",
    "dot": "Minimal: a wide rounded rectangle screen standing on a short pedestal, and on the screen one glowing warm amber "
           "dot (like a calm night light) with two thin clock hands; lots of dark empty space, very geometric and flat.",
    "candle": "A dark landscape screen on a stand where a warm candle-like glow rises from the bottom edge of the screen, "
              "lighting a simple analog clock face with two hands; cosy, soft 3D, glossy.",
}


def run(key: str) -> None:
    dest, started = OUT / f"{key}.png", time.time()
    prompt = f"Use your image generation tool to create exactly one image, then reply with one word: done.\n\n{COMMON}\nConcept: {IDEAS[key]}"
    args = [CODEX, "exec", "--json", "--sandbox", "read-only", "--config", 'approval_policy="never"',
            "--config", 'web_search="disabled"', "--skip-git-repo-check", "-"]
    with tempfile.TemporaryDirectory() as cwd:
        p = subprocess.run(args, input=prompt, capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=cwd, timeout=540)
    thread = next((json.loads(l).get("thread_id") for l in p.stdout.splitlines() if '"thread.started"' in l), None)
    imgs = [q for q in (GEN / thread).glob("*.png") if q.stat().st_mtime >= started - 5] if thread else []
    if not imgs:
        print("FAIL", key, p.returncode, p.stderr[-300:], flush=True); return
    shutil.copy(max(imgs, key=lambda q: q.stat().st_mtime), dest)
    print("ok", key, round(time.time() - started), "s", flush=True)


with ThreadPoolExecutor(4) as ex:
    list(ex.map(run, sys.argv[1:] or list(IDEAS)))
