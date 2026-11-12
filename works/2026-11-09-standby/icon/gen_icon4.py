"""Stand Screen icon round 4: colour variants of D3 (corner brackets + colon). usage: python gen_icon4.py [id...]"""
import json, shutil, subprocess, sys, tempfile, time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

OUT = Path(__file__).parent / "r4"
OUT.mkdir(exist_ok=True)
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")
REF = Path(__file__).parent / "r3" / "bracket.png"

KEEP = ("Edit the attached app icon. Keep the SAME symbol exactly: two thick rounded corner brackets (top-left and "
        "bottom-right) framing a clock colon of two round dots in the middle; same proportions, stroke weight, "
        "position and size. Keep it a crisp FLAT vector icon: solid flat colours only, no gradients, glow, gloss, 3D, "
        "shadow or texture. Square 1:1 full-bleed background to all edges, no rounded tile, no border, no text. "
        "Change ONLY the colours as follows: ")
PALETTES = {
    "navy-amber": "background deep navy #141a2b, brackets warm amber #FFB36B, dots cream #F6EFE6 (refine, cleaner edges).",
    "plum-peach": "background deep plum #2a1830, brackets soft peach #FFB59A, dots cream #FFF1E6.",
    "forest-cream": "background deep forest green #12271f, brackets warm cream #F3E6C8, dots amber #FFB36B.",
    "charcoal-mint": "background charcoal #17191c, brackets mint #7FE0C2, dots white #F4F6F5.",
    "cream-navy": "background warm cream #F4EEE3, brackets deep navy #1B2238, dots coral #FF6F59.",
    "coral-cream": "background coral red #F2553F, brackets cream #FFF4E8, dots deep navy #1B2238.",
    "blue-yellow": "background cobalt blue #2346D8, brackets sunny yellow #FFD54A, dots white #FFFFFF.",
    "black-white": "background pure black #000000, brackets white #FFFFFF, dots warm red #FF4A3D.",
}


def run(key: str) -> None:
    dest, started = OUT / f"{key}.png", time.time()
    prompt = f"Use your image generation tool to create exactly one image, then reply with one word: done.\n\n{KEEP}{PALETTES[key]}"
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
    list(ex.map(run, sys.argv[1:] or list(PALETTES)))
