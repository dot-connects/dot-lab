# Distinct camera frames per second in a clip or a moving strip, read from the fake camera's frame marker.
# The test cameras (r5-*.y4m) carry two top-corner blocks whose luma steps 16 + (n % 8) * 28 every camera frame, so a
# marker step of k between two output frames = k camera frames advanced: 0 = repeated frame (stutter), 1 = smooth,
# k >= 2 = k-1 camera frames skipped.
# usage: python fps.py FILE clip|strip   -> one JSON line (strip = 600x1800 white frame, bg "a" or "b": that side's raw
#        frame is the cell background, so its markers show in every cell)
import sys, json, subprocess, numpy as np
f, kind = sys.argv[1], sys.argv[2]
run = lambda a: subprocess.run(a, capture_output=True)
s = json.loads(run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height", "-of", "json", f]).stdout)["streams"][0]
W, H = s["width"], s["height"]
fr = np.frombuffer(run(["ffmpeg", "-v", "error", "-i", f, "-fps_mode", "passthrough", "-f", "rawvideo", "-pix_fmt", "gray", "-"]).stdout,
                   np.uint8).reshape(-1, H, W).astype(np.float32)
pts = [float(p.strip(", ")) for p in run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "frame=pts_time",
       "-of", "csv=p=0", f]).stdout.decode().split() if p.strip(", ") not in ("", "N/A")]
dur = pts[-1] - pts[0] + (pts[1] - pts[0]) if len(pts) > 1 else len(fr) / 30
# marker box inner parts, fractions of the camera frame: x 0.05-0.125 (left) / 0.875-0.95 (right), y 0.06-0.13
if kind == "clip":
  hw = W // 2
  boxes = {"L": (0.06 * H, 0.13 * H, 0.05 * hw, 0.125 * hw), "R": (0.06 * H, 0.13 * H, 0.875 * hw, 0.95 * hw)}
else:   # cell i at (24, 24 + 398 i), 552x382 = the camera frame's full width, top ~1 % cropped, 0.9225 of its height
  y0, y1 = (0.06 - 0.0097) / 0.9225 * 382, (0.13 - 0.0097) / 0.9225 * 382
  boxes = {f"c{i+1}{s}": (24 + 398 * i + y0, 24 + 398 * i + y1, 24 + x0 * 552, 24 + x1 * 552)
           for i in range(4) for s, x0, x1 in (("L", 0.05, 0.125), ("R", 0.875, 0.95))}
hold = 0.3 if kind == "strip" else 0          # the strip's last 0.3 s holds the still photo
use = int(round(len(fr) * (dur - hold) / dur))
out = {"file": f.replace("\\", "/").split("/")[-1], "frames": len(fr), "dur": round(dur, 3), "wh": f"{W}x{H}"}
res = {}
for k, (a, b, c, d) in boxes.items():
  m = fr[:use, int(a):int(b), int(c):int(d)].mean(axis=(1, 2))
  lo, hi = np.percentile(m, 2), np.percentile(m, 98)             # levels 0 and 7 (the range differs per path)
  idx = np.round((m - lo) / max((hi - lo) / 7, 1)).astype(int)
  st = np.diff(idx) % 8
  runs = "".join("1" if x == 0 else "0" for x in st).split("0")
  res[k] = {"fps": round((int((st > 0).sum()) + 1) / (dur - hold), 1), "rep": int((st == 0).sum()), "skip": int(np.clip(st - 1, 0, None).sum()),
            "maxRun": max(len(r) for r in runs), "std": round(float(np.std(m)), 1),
            "cover": round(int((st > 0).sum()) / max(1, int(st.sum())), 2)}   # camera frames shown / camera frames passed
# a box covered by the other person shows no marker (std small): keep the best box per clip / cell
pick = {}
for k, v in res.items():
  g = k[:-1] or "clip"
  if g not in pick or v["std"] > pick[g]["std"]: pick[g] = v
out.update(pick)
fps = [v["fps"] for v in pick.values()]
out["minFps"], out["meanFps"] = min(fps), round(sum(fps) / len(fps), 1)
print(json.dumps(out))
