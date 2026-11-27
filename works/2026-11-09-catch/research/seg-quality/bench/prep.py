"""Builds img/ (long side 640) and gt/ (hand polygons in poly.json) next to this file.
Run from works/2026-11-09-catch:  python research/seg-quality/bench/prep.py   (needs ffmpeg on PATH for video frames)"""
import json, os, subprocess
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
CATCH = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
SRC = {n: f"reel/render/mont/{n}.jpg" for n in "src00 src01 src02 src03 src04 src05 src06 f01 f03 g01".split()}
SRC["guineapig_04"] = "research/small-animals/guineapig_04.jpg"
FRAMES = {"kitten_2": ("kitten", 2), "kitten_5p7": ("kitten", 5.7), "kitten_9": ("kitten", 9),
          "puppy_3": ("puppy", 3), "puppy_13": ("puppy", 13), "dogwag_0p5": ("dog-wag", 0.5)}
os.makedirs(os.path.join(HERE, "img"), exist_ok=True); os.makedirs(os.path.join(HERE, "gt"), exist_ok=True)
for n, (v, t) in FRAMES.items():
    out = os.path.join(HERE, "img", n + ".raw.png")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(t), "-i", os.path.join(CATCH, "research/claw-media", v + ".webm"), "-frames:v", "1", out], check=True)
    SRC[n] = out
for n, poly in json.load(open(os.path.join(HERE, "poly.json"))).items():
    im = Image.open(os.path.join(CATCH, SRC[n])).convert("RGB")
    s = 640 / max(im.size); im = im.resize((round(im.size[0] * s), round(im.size[1] * s)), Image.LANCZOS)
    im.save(os.path.join(HERE, "img", n + ".png"))
    m = Image.new("L", im.size, 0); ImageDraw.Draw(m).polygon([tuple(p) for p in poly], fill=255)
    m.save(os.path.join(HERE, "gt", n + ".png"))
