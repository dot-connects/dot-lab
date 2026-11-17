"""Render the v2 captions: for each line a full shot (highlight on) and a bare shot (highlight off), both matted.
python capmake.py   (run from reel/render, the local server on 8766 must be up)"""
import os, subprocess, urllib.parse
CAPS = {"v1": ("우리 집 *모찌*를", 73), "v2": ("인형뽑기로 *뽑았다*", 73), "v3": ("*털뭉치*가 됐다", 14), "v4": ("도감에 *쏙*", 14),
        "v5": ("내 폰에 *살아요*", 14), "v6": ("*톡톡* 하면 앱이 열려요", 14), "v7": ("던지면 *데굴데굴*", 14), "v8": ("벽에도 *착*", 14),
        "v9": ("강아지도 *사람도*", 14)}
for k, (t, y) in CAPS.items():
    for hl in ("1", "0"):
        d = f"out-cap2-{k}-{hl}"
        for bg in ("black", "white"):
            q = urllib.parse.urlencode({"bg": bg, "t": t, "y": y, "hl": hl})
            subprocess.run(["node", "render.mjs", "cap2.html?" + q, "0", d], capture_output=True, check=True)
            os.replace(f"{d}/shot.png", f"{d}/{bg}.png")
        subprocess.run(["python", "capmatte.py", d], check=True)
    print(k)
