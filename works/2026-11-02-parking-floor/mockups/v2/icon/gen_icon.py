"""어디댔어 app icon candidates via Codex image generation (same call as photo-booth gen_logo.py).
usage: python gen_icon.py [id ...]   (no ids = all)"""
import json, shutil, subprocess, sys, tempfile, time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

OUT = Path(__file__).parent
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")

COMMON = ("App icon artwork for a Korean Android app named '어디댔어' ('where did you park?'): a family says where the car "
          "is parked by voice, and every family phone shows it. The fixed concept: a SPEECH BUBBLE that contains the parking "
          "floor code 'B2' (exactly the two characters capital B and digit 2, nothing else). "
          "Instantly readable at 48 pixels: one big silhouette, few shapes, generous margins, the 'B2' large and crisp. "
          "Square 1:1 full-bleed: the background reaches all four edges and corners; do NOT draw a rounded icon shape, "
          "border, phone, or drop shadow around the canvas. No other text, letters, words, or Korean characters. "
          "No P parking sign, no map pin, no car silhouette, no blue background, no existing brand logos.")

IDEAS = {
    # parking signage
    "sign-yellow": "Style: painted parking-garage signage. Matte near-black concrete background. A flat safety-yellow speech "
                   "bubble with a short tail at bottom-left, 'B2' in black condensed stencil numerals like paint on a garage pillar, "
                   "very subtle paint texture. Bold, public-signage, no gloss.",
    "sign-pillar": "Style: a square concrete parking pillar seen front-on fills the background in warm grey, with one horizontal "
                   "painted band in signal red across the middle; the speech bubble is white paint on that band, 'B2' in heavy "
                   "condensed numerals in red. Flat, slightly worn paint.",
    "sign-floor": "Style: wayfinding colour system. Background is a solid deep floor-colour green (#2f7d4f). A white speech "
                  "bubble with a sharp geometric tail; 'B2' in very heavy condensed sans in the same green. Clean Swiss signage, "
                  "flat, no texture.",
    # warm family
    "warm-two": "Style: warm family chat. Soft warm cream background. Two overlapping rounded speech bubbles, a peach one behind "
                "and a white one in front; the front one says 'B2' in friendly rounded bold numerals in deep brown. Gentle, "
                "flat with a tiny soft shading, cosy.",
    "warm-puffy": "Style: puffy glossy 3D sticker, cute Korean stationery feel. Pastel coral background. A chubby inflated white "
                  "speech bubble with soft highlights; 'B2' in rounded chunky coral numerals on it. Friendly, squishy.",
    "warm-hand": "Style: hand-drawn marker. Warm off-white paper background. A speech bubble drawn with one confident thick "
                 "marker line in charcoal, filled with a light sunny yellow wash; 'B2' written by hand in the same marker, "
                 "neat and legible. Feels like a note left on the fridge.",
    # minimal device
    "min-dot": "Style: Nothing OS / dot-matrix minimal device. Pure black background. A speech bubble drawn only as an outline "
               "made of evenly spaced small white dots; 'B2' inside in a white dot-matrix (LED grid) typeface; one single red "
               "dot accent at the bubble tail. Precise, monochrome.",
    "min-braun": "Style: Dieter Rams / Braun industrial design. Light warm grey matte background. A slightly raised off-white "
                 "speech bubble like a moulded plastic button, 'B2' in clean grotesk black; one small orange round indicator dot "
                 "at the top-right of the bubble. Precise, quiet, product-like.",
    "min-te": "Style: Teenage Engineering hardware. Flat light aluminium grey background. A speech bubble as a crisp black "
              "rounded-rectangle display window with a tiny tail; 'B2' glowing in warm orange segmented display digits inside. "
              "Minimal, technical, flat.",
    # hybrid
    "hybrid-stencil-warm": "Style: signage meets warmth. Soft warm sand background. A rounded speech bubble in warm white with a "
                           "soft shadow; 'B2' in condensed stencil numerals painted in a friendly tomato red with tiny paint "
                           "drips at the bottom. Half parking-garage paint, half family note.",
}


def run(key: str) -> None:
    dest, started = OUT / f"{key}.png", time.time()
    prompt = f"Use your image generation tool to create exactly one image, then reply with one word: done.\n\n{COMMON}\n{IDEAS[key]}"
    args = [CODEX, "exec", "--json", "--sandbox", "read-only", "--config", 'approval_policy="never"',
            "--config", 'web_search="disabled"', "--skip-git-repo-check", "-"]
    with tempfile.TemporaryDirectory() as cwd:
        p = subprocess.run(args, input=prompt, capture_output=True, text=True, encoding="utf-8", errors="replace",
                           cwd=cwd, timeout=600)
    thread = next((json.loads(l).get("thread_id") for l in p.stdout.splitlines() if '"thread.started"' in l), None)
    imgs = [q for q in (GEN / thread).glob("*.png") if q.stat().st_mtime >= started - 5] if thread else []
    if not imgs:
        print("FAIL", key, p.returncode, p.stderr[-300:], flush=True)
        return
    shutil.copy(max(imgs, key=lambda q: q.stat().st_mtime), dest)
    print("ok", key, round(time.time() - started), "s", flush=True)


if __name__ == "__main__":
    with ThreadPoolExecutor(4) as ex:
        list(ex.map(run, sys.argv[1:] or list(IDEAS)))
