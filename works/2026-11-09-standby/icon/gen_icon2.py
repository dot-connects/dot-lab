"""Stand Screen icon round 2: flat, no AI look, readable at 48 px. usage: python gen_icon2.py [id...]"""
import json, shutil, subprocess, sys, tempfile, time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

OUT = Path(__file__).parent / "r2"
OUT.mkdir(exist_ok=True)
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")

COMMON = ("Android app icon for 'Stand Screen' (a charging phone turned sideways becomes a bedside/desk clock). "
          "Style: a crisp FLAT vector system icon, like a well-made Material / One UI app icon designed by a human "
          "graphic designer: solid flat colours only, NO gradients, NO glow, NO bloom, NO gloss, NO 3D, NO bevel, NO "
          "shadows, NO texture, NO noise, NO reflections, NO sparkles. Thick, confident geometric shapes with consistent "
          "stroke weight and rounded corners. The symbol fills about 60 % of the canvas, centred, and must read clearly "
          "at 48 pixels. Square 1:1 full-bleed background reaching all edges; do NOT draw a rounded icon tile, border or "
          "device frame around the canvas. No text, letters or digits. Nothing that looks like Apple's StandBy or an iPhone.")
SYMBOL = {
    "colon": "Symbol: a wide landscape rounded rectangle (a phone on its side) with only a big clock colon of two "
             "round dots in the centre, nothing else.",
    "stand": "Symbol: a side view of a simple charging stand: a thick tilted bar (the phone) resting on a small wedge "
             "base, drawn as two solid shapes; a tiny colon of two dots on the bar.",
    "moon": "Symbol: a wide landscape rounded rectangle (a phone on its side) with a crescent moon and one small "
            "colon of two dots beside it.",
}
PALETTE = {
    "dark": "Colours: background near-black navy #10131c; symbol warm amber #FFB36B with one cream #F6EFE6 detail.",
    "amber": "Colours: background warm amber #FFB36B; symbol near-black navy #10131c with one cream #F6EFE6 detail.",
}
JOBS = {f"{s}-{p}": (s, p) for s in SYMBOL for p in PALETTE}


def run(key: str) -> None:
    s, p = JOBS[key]
    dest, started = OUT / f"{key}.png", time.time()
    prompt = f"Use your image generation tool to create exactly one image, then reply with one word: done.\n\n{COMMON}\n{SYMBOL[s]}\n{PALETTE[p]}"
    args = [CODEX, "exec", "--json", "--sandbox", "read-only", "--config", 'approval_policy="never"',
            "--config", 'web_search="disabled"', "--skip-git-repo-check", "-"]
    with tempfile.TemporaryDirectory() as cwd:
        r = subprocess.run(args, input=prompt, capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=cwd, timeout=540)
    thread = next((json.loads(l).get("thread_id") for l in r.stdout.splitlines() if '"thread.started"' in l), None)
    imgs = [q for q in (GEN / thread).glob("*.png") if q.stat().st_mtime >= started - 5] if thread else []
    if not imgs:
        print("FAIL", key, r.returncode, r.stderr[-300:], flush=True); return
    shutil.copy(max(imgs, key=lambda q: q.stat().st_mtime), dest)
    print("ok", key, round(time.time() - started), "s", flush=True)


with ThreadPoolExecutor(6) as ex:
    list(ex.map(run, sys.argv[1:] or list(JOBS)))
