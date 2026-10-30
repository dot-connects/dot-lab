"""Per voice take: whisper-align the six lines, cut, speed up like the lab voice, then mix music + effects + voice
on draft 7's timeline into one soundtrack (m4a). Run with content-system's venv from content-system.
usage: python mix7.py take-Leda take-Autonoe-young ..."""
import json, shutil, subprocess, sys
from pathlib import Path
from content_system import reel_voice, lab_work

R = Path(__file__).parent
import os
V4, SFX = R / os.environ.get("VDIR", "voice4"), R / "sfx"
TLN = os.environ.get("TL", "v7")
MUSIC = Path(r"C:\DC\DD\content-system\private-assets\music\prettyjohn1-pop-pop-music-503314.mp3")
LINES = json.loads((V4 / "lines.json").read_text(encoding="utf-8"))
TL = json.loads((R / f"{TLN}-timeline.json").read_text())
ff = shutil.which("ffmpeg")
for name in sys.argv[1:]:
    work = V4 / name; take = V4 / f"{name}.wav"
    segs = reel_voice.align(take, [{"say": x} for x in LINES], work / "align")
    print(name, [(round(g.start, 2), round(g.end, 2), g.ok) for g in segs])
    cut = reel_voice.split(take, segs, work / "lines", ffmpeg=ff, pre=0.03, post=0.15)
    clips = reel_voice.speed(cut, lab_work.VOICE_TEMPO, work / "fast", ffmpeg=ff)
    ins, flt, k = ["-i", str(MUSIC)], [], 1
    for a, v in TL["voices"]:
        ins += ["-i", str(work / "fast" / f"{v}.wav")]; flt.append(f"[{k}:a]adelay={round(a * 1000)}|{round(a * 1000)},volume=1.6[s{k}]"); k += 1
    for a, x in TL["fx"]:
        ins += ["-i", str(SFX / f"{x}.wav")]; flt.append(f"[{k}:a]adelay={round(a * 1000)}|{round(a * 1000)},volume=0.5[s{k}]"); k += 1
    t = TL["total"]
    flt.append("".join(f"[s{i}]" for i in range(1, k)) + f"amix=inputs={k - 1}:normalize=0[fg]")
    flt.append(f"[0:a]atrim=0:{t:.2f},volume=0.28,afade=t=in:d=0.3,afade=t=out:st={t - 1.0:.2f}:d=1.0[mu]")
    flt.append(f"[mu][fg]amix=inputs=2:normalize=0:duration=first,alimiter=limit=0.95,atrim=0:{t:.2f}[a]")
    subprocess.run([ff, "-v", "error", "-y", *ins, "-filter_complex", ";".join(flt), "-map", "[a]", "-c:a", "aac", "-b:a", "128k",
                    str(R / f"{TLN}-{name}.m4a")], check=True)
    print("mixed", name)
