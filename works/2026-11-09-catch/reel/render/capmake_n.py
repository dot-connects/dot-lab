"""Render the v2 captions: for each line a full shot (highlight on) and a bare shot (highlight off), both matted.
python capmake.py   (run from reel/render, the local server on 8766 must be up)"""
import os, subprocess, urllib.parse
CAPS = {"n1": ("우리 집 모찌를 *뽑았다*", 77), "n2": ("인형뽑기 *앱*으로", 77), "n3": ("*털뭉치*가 됐다", 14)}
for k, (t, y) in CAPS.items():
    for hl in ("1", "0"):
        d = f"out-cap2-{k}-{hl}"
        for bg in ("black", "white"):
            q = urllib.parse.urlencode({"bg": bg, "t": t, "y": y, "hl": hl})
            subprocess.run(["node", "render.mjs", "cap2.html?" + q, "0", d], capture_output=True, check=True)
            os.replace(f"{d}/shot.png", f"{d}/{bg}.png")
        subprocess.run(["python", "capmatte.py", d], check=True)
    print(k)
