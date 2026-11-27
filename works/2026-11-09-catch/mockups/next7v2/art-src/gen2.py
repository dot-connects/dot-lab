"""Assets in style A (felt miniature) for the 리얼뽑기 mockups. usage: python gen2.py kit|wx|make"""
import json, shutil, subprocess, sys, tempfile, time
from pathlib import Path
D = Path(__file__).parent
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")
HEAD = ("Use your image generation tool to create exactly one image, then reply with one word: done.\n\n"
 "Match the attached reference EXACTLY in material and light: a handmade needle-felted miniature world (left image) whose "
 "inhabitants are round fluffy plush critters (right). Soft warm studio light, tactile wool fibres, macro photography. ")
GREEN = (" Put every object separately on a perfectly flat, uniform pure green background (#00FF00), with clear green space "
 "between objects, no cast shadows on the background, no text, no letters, no logos, no critters or animals.")
P = {
 "kit": "One SQUARE image, a 2x2 sheet of four props for a toy claw machine game: (1) top-left: a cute toy crane CLAW hanging straight down, "
        "three curved prongs of polished pale-wood and cream felt, brass joints, a short string above it; (2) top-right: a big round "
        "arcade PUSH BUTTON, glossy cherry-red dome in a cream felt-wrapped base; (3) bottom-left: a closed gacha CAPSULE, top half "
        "translucent pastel pink plastic, bottom half milky white, seen from the front; (3) bottom-right: the same capsule popped OPEN "
        "with its top half tilted up, empty inside." + GREEN,
 "wx": "One SQUARE image, a 3x2 sheet of six tiny weather props sized for a palm-size plush pet, all needle-felted wool: "
       "(1) a small grey rain cloud with three blue felt raindrops dangling under it on threads; (2) a tiny open umbrella in sunny "
       "yellow felt with a wooden handle; (3) a little knitted red-and-cream striped scarf, coiled; (4) a small smiling felt sun with "
       "short rays; (5) three white felt snowflakes of different sizes; (6) a tiny white cloth face mask with ear loops." + GREEN,
 "make": "A PORTRAIT image, aspect 9:19.5 (tall phone screen), no objects at the very top 15%. Scene: the felt maker's little "
         "WORKBENCH seen from the front at eye level: a round cushion pedestal of cream felt in the lower-middle glowing softly with warm "
         "golden light from inside, a few wool roving tufts, a spool of thread and two felting needles resting on a wooden table at the "
         "sides, warm dark-honey background with gentle bokeh, cosy and magical. Leave the space just above the pedestal empty (a "
         "critter will appear there). No critters, no text.",
}
def run(k):
    t0 = time.time(); dest = D / f"a-{k}.png"
    args = [CODEX, "exec", "--json", "--sandbox", "read-only", "--config", 'approval_policy="never"',
            "--config", 'web_search="disabled"', "--skip-git-repo-check", "-i", str(D / "ref2.png"), "-"]
    with tempfile.TemporaryDirectory() as cwd:
        p = subprocess.run(args, input=HEAD + P[k], capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=cwd, timeout=900)
    th = next((json.loads(l).get("thread_id") for l in p.stdout.splitlines() if '"thread.started"' in l), None)
    imgs = [q for q in (GEN / th).glob("*.png") if q.stat().st_mtime >= t0 - 5] if th else []
    if not imgs: print("FAIL", k, p.returncode, p.stdout[-400:], p.stderr[-300:]); return
    shutil.copy(max(imgs, key=lambda q: q.stat().st_mtime), dest); print("ok", dest.name, round(time.time() - t0), "s")
if __name__ == "__main__":
    for k in sys.argv[1:]: run(k)
