"""One Codex image: the calico (img/cam2-sticker.png) as a vinyl-felt figure in the style of ref-figure.png -> fig-calico.png"""
import json, shutil, subprocess, tempfile, time
from pathlib import Path
D = Path(__file__).parent
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")
PROMPT = ("Use your image generation tool to create exactly one image, then reply with one word: done.\n\n"
 "Two reference images are attached. Image 1 is a real calico cat (white body, orange and black patches, black-and-brown "
 "tabby tail). Image 2 is the STYLE: a small collectible toy figure of a cat, sitting upright facing the camera, big round "
 "black glossy eyes, pink blush cheeks, soft flocked fuzzy surface, big head, tiny body. Make ONE such figure of THE CAT "
 "FROM IMAGE 1: same style, pose, proportions and lighting as image 2, but with image 1's calico coat pattern (mostly white, "
 "an orange patch and a black patch on the head around one ear, orange and black patches on the back, a black tabby tail curled "
 "round the side). Full body, centred, square image, on a perfectly flat uniform pure green background (#00FF00), no shadow on "
 "the background, no text.")
t0 = time.time()
args = [CODEX, "exec", "--json", "--sandbox", "read-only", "--config", 'approval_policy="never"', "--config", 'web_search="disabled"',
        "--skip-git-repo-check", "-i", str(D.parent / "img" / "cam2-sticker.png"), "-i", str(D / "ref-figure.png"), "-"]
with tempfile.TemporaryDirectory() as cwd:
    p = subprocess.run(args, input=PROMPT, capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=cwd, timeout=900)
th = next((json.loads(l).get("thread_id") for l in p.stdout.splitlines() if '"thread.started"' in l), None)
imgs = [q for q in (GEN / th).glob("*.png") if q.stat().st_mtime >= t0 - 5] if th else []
if not imgs: print("FAIL", p.returncode, p.stdout[-400:], p.stderr[-300:])
else: shutil.copy(max(imgs, key=lambda q: q.stat().st_mtime), D / "fig-calico.png"); print("ok", round(time.time() - t0), "s")
