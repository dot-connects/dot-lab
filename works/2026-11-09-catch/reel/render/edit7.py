"""Eighth cut = cut 7 (edit6.py) + the voice (Aoede) + the catch re-rendered with the claw back at 1x once the capsule
wraps. python reel/render/edit7.py -> out/reel-rough8.mp4

The take (reel/voice/take-<VOICE>.wav, one line per caption, reel/voice/lines.json) is split at its longest pauses
(voice_mix.split) and every line starts when its caption comes in; the last line starts with the end card's name.
"""
import sys
import wave
from pathlib import Path

import numpy as np

import edit6 as e

sys.path.insert(0, str(e.R.parent))
import voice_mix as vm  # noqa: E402

NAME = "Aoede"
OLD, NEW = "던지면 *어질어질*", "휘 튕기면 *통통*"   # operator 11-17: not "throwing" one's own pet
THROW = e.R.parent / "voice" / "throw" / f"take-{NAME}.wav"   # that line alone, re-recorded (voice_samples.py on voice/throw/lines.json);
                                                               # until it exists the line plays without voice (the big take says the old words)


def main() -> None:
    for c in e.CUTS:
        c[4][:] = [(NEW if text == OLD else text, *rest) for text, *rest in c[4]]
    line = [text for c in e.CUTS for text, *_ in c[4]].index(NEW)
    starts, t0 = [], 0.0
    for c in e.CUTS:
        starts.append(t0); t0 += c[1]
    times = [starts[i] + cin + .05 for i, c in enumerate(e.CUTS) for _, _, cin, _ in c[4]] + [starts[-1] + .3]
    segs = vm.split(vm.read(e.R.parent / "voice" / f"take-{NAME}.wav"), len(times))
    assert len(segs) == len(times), (len(segs), len(times))
    if THROW.is_file():
        x = vm.read(THROW); v = np.nonzero(np.abs(x) > .01)[0]; segs[line] = x[max(0, v[0] - 720):v[-1] + 1920]
    else:
        segs[line] = np.zeros(0, np.float32); print(f"line {line + 1}: no voice yet ({THROW.name} missing)")
    track = np.zeros(int(t0 * vm.SR) + vm.SR, np.float32)
    for k, (s, t) in enumerate(zip(segs, times)):
        nxt = times[k + 1] if k + 1 < len(times) else t0
        print(f"line {k + 1} at {t:5.2f}s, {len(s) / vm.SR:.2f}s" + ("  OVERLAPS the next line" if t + len(s) / vm.SR > nxt else ""))
        a = int(t * vm.SR); end = min(len(track), a + len(s)); track[a:end] += s[: end - a]
    e.OUT = e.R / "out-reel8"; e.OUT.mkdir(exist_ok=True)
    e.VOICE = e.OUT / "voice.wav"
    with wave.open(str(e.VOICE), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(vm.SR)
        w.writeframes((np.clip(track, -1, 1) * 32767).astype(np.int16).tobytes())
    e.DST = e.R.parent.parent / "out" / "reel-rough8.mp4"
    if "--voice-only" not in sys.argv:
        e.main()


if __name__ == "__main__":
    main()
