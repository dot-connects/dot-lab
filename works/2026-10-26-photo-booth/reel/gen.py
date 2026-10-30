"""AI performers for the photo-booth reel draft: one base frame per person, then pose edits with the base attached.
usage: python gen.py base A|B   |   python gen.py pose A|B <idx...>"""
import json, shutil, subprocess, sys, tempfile, time
from pathlib import Path

OUT = Path(__file__).parent / "ai"
GEN = Path.home() / ".codex" / "generated_images"
CODEX = shutil.which("codex.cmd") or shutil.which("codex")

COMMON = ("Photorealistic still frame from a smartphone FRONT camera propped up at chest height, landscape 3:2, "
          "natural colours, slight phone-camera softness, no text, no logos, no watermark, no phone visible. "
          "Exactly one person, waist-up, facing the camera, standing in the LEFT {side} of the frame; the other side of the "
          "frame is empty background with no people or objects in front (room for a friend to stand later).")
PEOPLE = {
    "A": dict(side="third", who="a Korean woman in her early twenties, shoulder-length dark hair, cream knit cardigan over a white tee",
              where="her cosy bedroom at night: warm lamp light, a bed with a light grey blanket and a small shelf behind her"),
    "B": dict(side="third", who="a Korean woman in her early twenties, long hair in a low ponytail, light blue denim shirt",
              where="a bright cafe by day: wooden tables, plants and a window with soft daylight behind her"),
}
# B stands on the RIGHT: the friend (A) is pasted on the left of B's camera
SIDE = {"A": "LEFT", "B": "RIGHT"}
POSES = {
    "A": ["waving hello with her right hand, big smile",
          "a V sign next to her cheek, playful smile",
          "a small Korean finger heart (thumb and index finger crossed) held up near her face, smiling",
          "making HALF of a heart with her right hand only: fingers curved into a C shape, arm held out toward the empty side of the frame, smiling toward that side",
          "a thumbs up with both hands, laughing",
          "both palms on her cheeks, cute smile"],
    "B": ["both hands on her cheeks, mouth open in happy surprise",
          "a V sign next to her cheek, playful smile",
          "a small Korean finger heart (thumb and index finger crossed) held up near her face, smiling",
          "making HALF of a heart with her left hand only: fingers curved into a reversed C shape, arm held out toward the empty side of the frame, smiling toward that side",
          "waving hello with her left hand, big smile",
          "a thumbs up with one hand, laughing"],
}

def run(prompt: str, image: Path | None, dest: Path) -> bool:
    started = time.time()
    args = [CODEX, "exec", "--json", "--sandbox", "read-only", "--config", 'approval_policy="never"',
            "--config", 'web_search="disabled"', "--skip-git-repo-check"]
    if image: args += ["-i", str(image)]
    with tempfile.TemporaryDirectory() as cwd:
        # prompt on stdin: codex.cmd on Windows cuts a multi-line argument at its first newline
        p = subprocess.run(args + ["-"], input=prompt, capture_output=True, text=True, encoding="utf-8", errors="replace",
                           cwd=cwd, timeout=600)
    thread = next((json.loads(l).get("thread_id") for l in p.stdout.splitlines() if '"thread.started"' in l), None)
    imgs = [q for q in (GEN / thread).glob("*.png") if q.stat().st_mtime >= started - 5] if thread else []
    if not imgs:
        print("FAIL", dest.name, p.returncode, [l for l in p.stdout.splitlines() if "error" in l][-2:], p.stderr[-300:]); return False
    shutil.copy(max(imgs, key=lambda q: q.stat().st_mtime), dest)
    print("ok", dest.name, round(time.time() - started), "s"); return True

def main():
    mode, who = sys.argv[1], sys.argv[2]
    P = PEOPLE[who]; common = COMMON.replace("LEFT {side}", f"{SIDE[who]} third")
    OUT.mkdir(exist_ok=True)
    if mode == "base":
        prompt = (f"Use your image generation tool to create exactly one image, then reply with one word: done.\n\n{common}\n"
                  f"Person: {P['who']}, arms relaxed, gentle smile. Place: {P['where']}.")
        run(prompt, None, OUT / f"{who}-base.png")
    else:
        for i in map(int, sys.argv[3:]):
            prompt = ("Use your image generation tool to create exactly one image by editing the attached photo, then reply "
                      "with one word: done.\n\nKeep EVERYTHING identical to the attached photo: the same person (same face, "
                      "hair, clothes), the same room and background, the same camera position, framing, lighting and colours. "
                      f"The person stays in the same spot on the {SIDE[who]} side. Change ONLY her pose and expression: "
                      f"{POSES[who][i]}. Hands and fingers must look natural (five fingers). Landscape 3:2, no text.")
            run(prompt, OUT / f"{who}-base.png", OUT / f"{who}-{i}.png")

main()
