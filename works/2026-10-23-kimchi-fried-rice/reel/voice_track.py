"""Build the lab's finished voice track (clips at their seconds, silence elsewhere, reel length) and a preview mixed
exactly the way the bot does it (media.voice -> add_music voices=[(0, track)])."""
import json, shutil, subprocess, sys
from pathlib import Path
from content_system import reel, reel_render

PLAN = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))   # [[at, clip path, text], ...]
OUT = Path(sys.argv[2])
REEL = Path(r"C:/DC/DD/dot-lab/works/2026-10-23-kimchi-fried-rice/out/reel.mp4")
ff = shutil.which("ffmpeg")
length = reel_render._probe(ff, REEL)
cmd = [ff, "-y", "-v", "error"]
for _, clip, _ in PLAN:
    cmd += ["-i", clip]
parts = [f"[{k}:a]aresample=24000,adelay={int(at * 1000)}|{int(at * 1000)}[v{k}]" for k, (at, _, _) in enumerate(PLAN)]
parts.append("".join(f"[v{k}]" for k in range(len(PLAN))) + f"amix=inputs={len(PLAN)}:normalize=0,apad,atrim=0:{length:.3f}[a]")
subprocess.run(cmd + ["-filter_complex", ";".join(parts), "-map", "[a]", "-ac", "1", str(OUT / "voice.wav")], check=True)
music = reel.pick_music("lab", turn=0)
reel_render.add_music(REEL, (Path(music["path"]), float(music.get("start_s", 0))), OUT / "preview.mp4", ffmpeg=ff,
                      voices=[(0.0, OUT / "voice.wav")])
end = 0
for at, clip, text in PLAN:
    d = reel_render._probe(ff, Path(clip)); print(f"{at:6.2f}-{at + d:6.2f} gap {at - end:5.2f} {text}"); end = at + d
print("track", reel_render._probe(ff, OUT / "voice.wav"), "preview", reel_render._probe(ff, OUT / "preview.mp4"))
