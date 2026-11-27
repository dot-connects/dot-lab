"""Claw-screen art directions for 리얼뽑기 (mockup only). usage: python gen.py A|B|C"""
import json, shutil, subprocess, sys, tempfile, time
from pathlib import Path
D = Path(__file__).parent
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")
COMMON = ("Use your image generation tool to create exactly one image, then reply with one word: done.\n\n"
  "A PORTRAIT phone game background, aspect 9:19.5 (tall, like 1080x2340), for a cute mobile claw-machine game whose prizes are "
  "the round fluffy plush critters in the attached reference (soft real-looking fur, tiny dot eyes, pink cheeks). "
  "The background must look like it belongs in the same world as those critters: same soft lighting and material realism. "
  "NO critters, NO animals, NO characters, NO text, NO letters, NO logos, NO UI buttons. "
  "Keep the top 12% calm and plain (a claw rail runs there) and keep a calm open floor area in the lower-middle where prizes will sit. ")
DIR = {
 "A": "Style: a handmade FELT MINIATURE DIORAMA seen inside a toy claw machine: needle-felted grass floor, wool-ball bushes, a tiny wooden picket fence, felt clouds hanging on threads against a pale sky-blue felt backdrop, warm soft studio light, very shallow depth of field like tilt-shift macro photography. Palette: sky blue, soft grass green, cream, a little sunny yellow.",
 "B": "Style: the INSIDE of a real glossy Japanese-style claw machine (UFO catcher): glass walls with soft reflections, a chrome rail at the top, pastel pink and mint padded walls, a soft heap-free plush-felt floor in pale green, warm marquee light glowing from above, gentle bokeh sparkles. Photographic, cosy, premium toy-shop feel.",
 "C": "Style: a soft 3D CLAY-RENDER sunny garden inside a rounded glass dome (like a capsule toy): rolling grass hills, a few round shrubs, scattered half-open gacha capsules (pastel colours), soft morning sun, gentle bloom, smooth stylised but tactile materials, Pixar-like lighting. Palette: sky blue, grass green, cream, sunny yellow, a touch of pink.",
}
def run(k):
    started = time.time(); dest = D / f"dir-{k}.png"
    args = [CODEX, "exec", "--json", "--sandbox", "read-only", "--config", 'approval_policy="never"',
            "--config", 'web_search="disabled"', "--skip-git-repo-check", "-i", str(D / "ref.png"), "-"]
    with tempfile.TemporaryDirectory() as cwd:
        p = subprocess.run(args, input=COMMON + DIR[k], capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=cwd, timeout=900)
    th = next((json.loads(l).get("thread_id") for l in p.stdout.splitlines() if '"thread.started"' in l), None)
    imgs = [q for q in (GEN / th).glob("*.png") if q.stat().st_mtime >= started - 5] if th else []
    if not imgs: print("FAIL", k, p.returncode, p.stdout[-400:], p.stderr[-300:]); return
    shutil.copy(max(imgs, key=lambda q: q.stat().st_mtime), dest); print("ok", dest, round(time.time() - started), "s")
if __name__ == "__main__":
    for k in sys.argv[1:]: run(k)
