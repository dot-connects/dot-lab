"""Voice samples on cut 7: each TTS take is split at its 8 longest pauses into the 9 caption lines, every line starts at
its caption's time, the app sfx track of cut 7 stays under it. Video is copied, not re-encoded (a light job).

python reel/voice_mix.py   (from works/2026-11-09-catch) -> out/reel7-voice-<Voice>.mp4
python reel/voice_mix.py 9 -> cut 9d with the 13 lines of voice9/ -> out/reel9-voice-<Voice>.mp4
"""
import sys
import subprocess
import wave
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
VOICE = HERE / "voice"
OUT = HERE.parent / "out"
CUT = OUT / "reel-rough7.mp4"
# caption in-times on the cut 7 clock (render/edit6.py CUTS: cut start + caption in)
STARTS = [0.15, 6.1, 7.95, 10.65, 13.85, 17.2, 21.1, 23.85, 29.1]
TAG = "7"
if sys.argv[1:] == ["9"]:   # cut 9d (render/edit8.py CUTS: cut start + caption in, the grid on its slowed clock)
    VOICE, CUT, TAG = HERE / "voice9", OUT / "reel-rough9d.mp4", "9"
    STARTS = [0.05, 1.8, 3.5, 9.3, 11.15, 13.45, 15.2, 18.4, 22.3, 25.05, 27.75, 30.42, 32.2]   # no two lines overlap
    # the commas made pauses as long as the line breaks, so the longest-pause split merged lines: the 13 lines' spans in
    # take-Aoede.wav, matched by hand from the pause list and each line's syllable count (11-18)
    SPANS = [(.18, 1.88), (2.62, 3.66), (4.32, 6.62), (6.92, 8.76), (9.06, 11.42), (12.22, 13.38), (13.82, 15.92),
             (16.74, 18.16), (18.82, 20.42), (21.14, 23.86), (24.68, 27.46), (27.84, 29.62), (30.48, 35.06)]
SR = 24000


def read(p: Path) -> np.ndarray:
    with wave.open(str(p)) as w:
        global SR
        SR = w.getframerate()
        return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768


def split(x: np.ndarray, n: int) -> list[np.ndarray]:
    hop = int(SR * .02)
    rms = np.sqrt(np.convolve(x ** 2, np.ones(hop) / hop, "same"))[::hop]
    quiet = rms < max(.01, np.percentile(rms, 30) * 1.5)
    runs, i = [], 0
    while i < len(quiet):
        if quiet[i]:
            j = i
            while j < len(quiet) and quiet[j]:
                j += 1
            runs.append((j - i, i, j)); i = j
        else:
            i += 1
    voiced = np.nonzero(~quiet)[0]
    first, last = voiced[0], voiced[-1] + 1
    inner = sorted([r for r in runs if r[1] > first and r[2] < last], reverse=True)[: n - 1]
    cuts = sorted((a + b) // 2 for _, a, b in inner)
    edges = [first] + cuts + [last]
    segs = []
    for a, b in zip(edges, edges[1:]):
        s = x[a * hop:b * hop]
        v = np.nonzero(np.abs(s) > .01)[0]
        segs.append(s[max(0, v[0] - int(SR * .03)):v[-1] + int(SR * .08)] if len(v) else s)
    return segs


def squeeze(s: np.ndarray, most: float = .22) -> np.ndarray:
    """Pauses inside one line shortened to `most` s (the take paused as long at a comma as between lines)."""
    hop = int(SR * .02)
    loud = np.sqrt(np.convolve(s ** 2, np.ones(hop) / hop, "same")) >= .01
    out, i, keep = [], 0, int(most * SR)
    while i < len(s):
        j = i
        while j < len(s) and loud[j] == loud[i]:
            j += 1
        out.append(s[i:j] if loud[i] or j - i <= keep else s[i:i + keep])
        i = j
    return np.concatenate(out)


def main() -> None:
    dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(CUT)],
                               capture_output=True, text=True, check=True).stdout)
    for take in sorted(VOICE.glob("take-*.wav")):
        name = take.stem.split("-", 1)[1]
        x = read(take)
        segs = [squeeze(x[int(a * SR):int(b * SR)]) for a, b in SPANS] if TAG == "9" else split(x, len(STARTS))
        track = np.zeros(int(dur * SR) + SR, np.float32)
        for s, t in zip(segs, STARTS):
            a = int(t * SR)
            end = min(len(track), a + len(s))
            track[a:end] += s[: end - a]
            print(f"{name}: line at {t:5.2f}s, {len(s) / SR:.2f}s long")
        vo = VOICE / f"vo-{name}.wav"
        with wave.open(str(vo), "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
            w.writeframes((np.clip(track, -1, 1) * 32767).astype(np.int16).tobytes())
        dst = OUT / f"reel{TAG}-voice-{name}.mp4"
        # voice on top, the sfx 6 dB under it while the voice talks
        subprocess.run(["ffmpeg", "-v", "error", "-threads", "2", "-y", "-i", str(CUT), "-i", str(vo), "-filter_complex",
                        "[1:a]volume=1.6,asplit[v1][v2];[0:a][v1]sidechaincompress=threshold=0.05:ratio=4:release=250[sfx];"
                        "[sfx][v2]amix=inputs=2:normalize=0,alimiter=limit=0.95[a]",
                        "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-b:a", "160k", "-t", f"{dur:.3f}", str(dst)], check=True)
        print("wrote", dst)


if __name__ == "__main__":
    main()
