"""Toss app logo candidates via Codex image generation, same call as reel/gen.py. usage: python gen_logo.py <id...>"""
import json, shutil, subprocess, sys, tempfile, time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

OUT = Path(__file__).parent
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")

COMMON = ("App icon artwork for a Korean mobile photo-booth app called 'Together Apart Four Cuts': two friends far away "
          "take a four-cut photo strip together. Mood: soft, squishy, cute Korean sticker-shop style, glossy puffy 3D, "
          "pastel lavender #f1eaff, baby pink #ff8fbd, deep violet #5a31ab accents, a touch of butter yellow. "
          "One bold idea, instantly readable at 48 pixels: few shapes, big silhouette, generous margins. "
          "Square 1:1 full-bleed: the solid or softly graded background reaches all four edges and corners; do NOT draw a "
          "rounded icon shape, border, device, or drop shadow around the canvas. No text, letters or numbers. No real or "
          "photorealistic people, no faces of humans, no logos of existing brands, no blue toss-style branding.")
IDEAS = {
    "heart": "Two separate tilted photo strips lean toward each other from left and right, and the half heart on each strip meets in the middle to make one whole heart.",
    "booth": "A chubby little photo booth with a scalloped pink awning and a curtain, seen from the front, with two small round blob friends peeking out side by side.",
    "split": "One square polaroid-like photo split down the middle by a thin seam: a lavender side and a pink side, a round blob friend on each side leaning together across the seam.",
    "strip": "A single puffy vertical four-cell photo strip pinned at the top with a pink pin, slightly tilted; two tiny round blob friends shown together in the cells, small sparkles around it.",
}

DARK = ("Use your image generation tool to create exactly one image by editing the attached app icon, then reply with one "
        "word: done.\n\nKeep EVERYTHING identical to the attached image: the same two blob friends (same shapes, faces, colours, "
        "gloss), the same yellow heart, the same polaroid frame, same position, size and composition. Change ONLY the flat "
        "background outside the polaroid: left half deep midnight violet #2a2140, right half slightly lighter plum #4a2a5e, "
        "split on the same centre seam. Also slightly dim the white polaroid frame to a soft pale lavender so it does not glare "
        "on a dark screen. Square 1:1 full-bleed to all four corners, no rounded canvas, no text.")

def run(key: str) -> None:
    dest, started = OUT / f"{key}.png", time.time()
    prompt = f"Use your image generation tool to create exactly one image, then reply with one word: done.\n\n{COMMON}\nConcept: {IDEAS.get(key)}"
    args = [CODEX, "exec", "--json", "--sandbox", "read-only", "--config", 'approval_policy="never"',
            "--config", 'web_search="disabled"', "--skip-git-repo-check"]
    if key == "split-dark":
        prompt = DARK
        args += ["-i", str(OUT / "split.png")]
    args += ["-"]
    with tempfile.TemporaryDirectory() as cwd:
        p = subprocess.run(args, input=prompt, capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=cwd, timeout=540)
    thread = next((json.loads(l).get("thread_id") for l in p.stdout.splitlines() if '"thread.started"' in l), None)
    imgs = [q for q in (GEN / thread).glob("*.png") if q.stat().st_mtime >= started - 5] if thread else []
    if not imgs:
        print("FAIL", key, p.returncode, p.stderr[-300:], flush=True); return
    shutil.copy(max(imgs, key=lambda q: q.stat().st_mtime), dest)
    print("ok", key, round(time.time() - started), "s", flush=True)

with ThreadPoolExecutor(1 if "split-dark" in sys.argv else 4) as ex:   # the dark logo edits split.png: run in order
    list(ex.map(run, sys.argv[1:] or list(IDEAS)))
