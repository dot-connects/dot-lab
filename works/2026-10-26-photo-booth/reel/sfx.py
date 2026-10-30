"""The reel's six sound effects, synthesized (run with any Python that has numpy): whoosh, ding, pop, shutter, tick, thud."""
import wave
from pathlib import Path
import numpy as np

SR, OUT = 44100, Path(__file__).parent / "sfx"
OUT.mkdir(exist_ok=True)
def save(name, x):
    w = wave.open(str(OUT / f"{name}.wav"), "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((np.clip(x, -1, 1) * 32767 * 0.9).astype(np.int16).tobytes()); w.close()
t = lambda d: np.arange(int(SR * d)) / SR
rng = np.random.default_rng(1)
d = 0.35; save("whoosh", 1.5 * np.convolve(rng.standard_normal(len(t(d))), np.ones(30) / 30, "same") * np.sin(np.pi * t(d) / d) ** 2)
x = t(0.8); save("ding", (0.6 * np.sin(2 * np.pi * 1568 * x) + 0.3 * np.sin(2 * np.pi * 2352 * x)) * np.exp(-x * 5))
x = t(0.18); save("pop", 0.8 * np.sin(2 * np.pi * (300 + 2200 * x) * x) * np.exp(-x * 18))
x = t(0.16); c = rng.standard_normal(len(x)) * np.exp(-x * 90); c2 = np.zeros_like(c); k = int(0.07 * SR); c2[k:] = c[:-k] * 0.8; save("shutter", (c + c2) * 0.9)
x = t(0.06); save("tick", 0.7 * np.sin(2 * np.pi * 2000 * x) * np.exp(-x * 80))
x = t(0.4); save("thud", 0.9 * np.sin(2 * np.pi * 90 * x) * np.exp(-x * 12) + 0.3 * rng.standard_normal(len(x)) * np.exp(-x * 120))
