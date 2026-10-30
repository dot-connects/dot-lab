"""Ad v2 stills, each an edit of an existing pose (same face, same clothes)."""
import sys
from pathlib import Path
src = (Path(__file__).parent / "gen.py").read_text(encoding="utf-8").replace("\nmain()\n", "\n")
exec(src)
JOBS = {
  "P1": ("A-0", "Turn this into a glossy celebrity photo-booth poster still: the SAME woman (same face, hair, cream cardigan), chest-up, facing the camera with a bright smile, "
                "centred, against a seamless deep navy studio backdrop, one soft spotlight from above with a gentle halo, magazine beauty lighting, sharp focus. "
                "No text, no logo, no frame, no other people. Portrait 2:3."),
  "P2": ("A-2", "Turn this into a glossy celebrity photo-booth poster still: the SAME woman (same face, hair, cream cardigan), chest-up, holding a small Korean finger heart near her face, "
                "playful smile, centred, against a seamless deep navy studio backdrop, one soft spotlight from above, magazine beauty lighting. No text, no logo, no frame, no other people. Portrait 2:3."),
  "A-selfie": ("A-0", "Keep the SAME woman, the SAME bedroom, lighting and colours. Now she holds a smartphone up in her right hand at arm's length taking a selfie, smiling at the phone; "
                "the phone screen is not visible. Same framing, landscape 3:2, no text."),
  "B-react": ("B-0", "Keep the SAME woman, the SAME cafe, lighting and colours. Now she holds a smartphone in one hand looking at its screen (screen not visible to us), "
                "the other hand on her cheek, mouth open laughing with happy surprise, three-quarter angle. Same framing, landscape 3:2, no text."),
}
from concurrent.futures import ThreadPoolExecutor
def job(k):
    ref, edit = JOBS[k]
    run("Use your image generation tool to create exactly one image by editing the attached photo, then reply with one word: done.\n\n"
        + edit + " Hands and fingers must look natural (five fingers).", OUT / f"{ref}.png", OUT / f"{k}.png")
with ThreadPoolExecutor(4) as ex: list(ex.map(job, JOBS))
