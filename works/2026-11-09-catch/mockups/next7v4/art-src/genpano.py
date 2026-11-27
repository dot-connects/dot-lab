"""Panorama strips for the felt world (camera off). usage: python genpano.py a b  (each = one Codex image call)"""
import json, shutil, subprocess, sys, tempfile, time
from pathlib import Path
D = Path(__file__).parent
REF = D.parent / "img" / "bg-claw.jpg"
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")
HEAD = ("Use your image generation tool to create exactly one image, then reply with one word: done.\n\n"
 "Match the attached reference EXACTLY in material, colours and light: a handmade needle-felted miniature world, sky of pale "
 "blue felt, ground of fuzzy green wool felt, soft warm studio light, tactile wool fibres, macro photography, shallow depth of field. ")
COMMON = (" Make it the WIDEST landscape format you can (at least 3:2, wider is better). It is one slice of a 360-degree "
 "panorama that will be joined side by side with other slices: keep the horizon (where the green floor meets the hills) "
 "perfectly level at 47% of the image height across the whole width, keep the sky one even pale-blue felt colour (same as the "
 "reference) with no gradient, and keep the floor the same flat green felt seen from eye level, no path, no perspective lines. "
 "The leftmost 12% and the rightmost 12% of the image contain ONLY sky, a gentle low green felt hill on the horizon and plain "
 "green floor (no objects cut by the edge). Everything else sits on the horizon band, small in scale like the reference "
 "(objects no taller than 25% of the image height), leaving the lower half of the image as open green floor. "
 "No critters, no animals, no people, no text, no letters, no logos, no strings hanging into the frame except from clouds.")
P = {
 "a": "Scene: along the horizon, from left to right: rolling felt hills, a short cream picket fence with two round moss bushes "
      "and yellow felt flowers, a small round felt tree with a fat green crown, a cosy little felt cottage with a red roof far away, "
      "more hills. Two puffy cream felt clouds hang on threads in the sky.",
 "b": "Scene: along the horizon, from left to right: rolling felt hills, a little wooden felt windmill far away, a row of three "
      "round felt pine trees, a tiny pond of blue felt with a lily pad, pink and white felt flowers in a cluster, a cream picket "
      "fence segment, more hills. One puffy cream felt cloud and a small felt sun hang on threads in the sky.",
}
def run(k):
    t0 = time.time(); dest = D / f"pano-{k}.png"
    args = [CODEX, "exec", "--json", "--sandbox", "read-only", "--config", 'approval_policy="never"',
            "--config", 'web_search="disabled"', "--skip-git-repo-check", "-i", str(REF), "-"]
    with tempfile.TemporaryDirectory() as cwd:
        p = subprocess.run(args, input=HEAD + P[k] + COMMON, capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=cwd, timeout=900)
    th = next((json.loads(l).get("thread_id") for l in p.stdout.splitlines() if '"thread.started"' in l), None)
    imgs = [q for q in (GEN / th).glob("*.png") if q.stat().st_mtime >= t0 - 5] if th else []
    if not imgs: print("FAIL", k, p.returncode, p.stdout[-400:], p.stderr[-300:], flush=True); return
    shutil.copy(max(imgs, key=lambda q: q.stat().st_mtime), dest); print("ok", dest.name, round(time.time() - t0), "s", flush=True)
if __name__ == "__main__":
    for k in sys.argv[1:]: run(k)
