"""v3 bright bed for the Stand Screen reel: review3 mixes 1-10. Built on the page timeline (0-33.5 s),
then the first TRIM s are dropped so it lines up with the trimmed video (33.1 s)."""
import sys, wave
import numpy as np
from scipy.signal import butter, sosfilt, fftconvolve

SR, TRIM, PAGE_END = 48000, 0.4, 33.5
N = int(SR * PAGE_END)
rng = np.random.default_rng(11)
t = np.arange(N) / SR


def mtof(m: float) -> float:
    return 440.0 * 2 ** ((m - 69) / 12)


def filt(x, kind: str, fc):
    return sosfilt(butter(2, fc, kind, fs=SR, output="sos"), x)


def smooth(x, a: float, b: float):
    k = np.clip((x - a) / (b - a), 0, 1)
    return k * k * (3 - 2 * k)


def place(buf, sig, at: float, gain: float = 1.0, pan: float = 0.0) -> None:
    i = int(round(at * SR))
    n = min(len(sig), N - i)
    if n <= 0:
        return
    a = (pan + 1) * np.pi / 4
    buf[0, i:i + n] += sig[:n] * gain * np.cos(a) * 1.414
    buf[1, i:i + n] += sig[:n] * gain * np.sin(a) * 1.414


def marimba(f: float, dur: float = 0.6) -> np.ndarray:
    ts = np.arange(int(dur * SR)) / SR
    s = (np.sin(2 * np.pi * f * ts) * np.exp(-ts / 0.28) + 0.35 * np.sin(2 * np.pi * f * 3.99 * ts) * np.exp(-ts / 0.05)
         + 0.15 * np.sin(2 * np.pi * f * 10 * ts) * np.exp(-ts / 0.008))  # mix 1: 10th-harmonic click
    return s * smooth(ts, 0, 0.003)


def bell(f: float, dur: float = 2.5, decay: float = 1.0) -> np.ndarray:
    ts = np.arange(int(dur * SR)) / SR
    parts = [(1.0, 1.0, decay), (2.0, 0.4, decay * 0.6), (3.0, 0.18, decay * 0.35), (4.2, 0.08, decay * 0.2)]
    return sum(a * np.sin(2 * np.pi * f * r * ts) * np.exp(-ts / d) for r, a, d in parts) * smooth(ts, 0, 0.003)


PROG = [("C", [60, 64, 67, 71], 36), ("G", [59, 62, 67, 69], 43), ("Am", [60, 64, 67, 69], 45), ("F", [57, 60, 65, 67], 41)]
C_END = ("C", [60, 64, 67, 71], 36)
def chord_at(x: float):
    return PROG[int(x // 2) % 4] if x < 28 else C_END

mus = np.zeros((2, N))

# ---- pad (mix 5): gain .13, detune +-.0015, each voice 0.7/0.3 across channels; one held C from 28 s (no re-hit) ----
pad = np.zeros((2, N))
for bar in range(15):
    a = bar * 2.0
    _, notes, _ = chord_at(a)
    last = a >= 28
    seg = np.arange(int(max(a - 0.15, 0) * SR), int((PAGE_END if last else a + 2.4) * SR))
    ts = seg / SR
    env = smooth(ts, a - 0.15, a + 0.35) * (1 if last else (1 - smooth(ts, a + 2.0, a + 2.4)))
    for m in notes:
        for side, det in ((0, -0.0015), (1, 0.0015)):
            f = mtof(m) * (1 + det)
            v = (np.sin(2 * np.pi * f * ts) + 0.3 * np.sin(4 * np.pi * f * ts) + 0.1 * np.sin(6 * np.pi * f * ts)) * env * 0.5
            pad[side, seg] += 0.7 * v
            pad[1 - side, seg] += 0.3 * v
pad = filt(pad, "low", 2400)
pad_gain = smooth(t, 0.6, 2.0) * (1 - 0.5 * smooth(t, 21.8, 22.2) * (1 - smooth(t, 23.8, 24.4)))  # mix 10: duck 50 % 22-24 s
mus += pad * pad_gain * 0.13
mus += pad * (smooth(t, 25.0, 25.8) * (1 - smooth(t, 27.6, 28.8)))[None, :] * 0.12  # mood-light swell
mus *= (1 - smooth(t, 28.7, PAGE_END))[None, :] * 0.55 + 0.45  # gentle fade over the extended ending

# ---- bass (mix 4): +12 st, 2nd .5 / 3rd .2, g .22; no hits 22-24 s; last hit at 28 s ----
for bar in range(1, 15):
    a = bar * 2.0
    if 22 <= a < 24:
        continue
    f = mtof(chord_at(a)[2] + 12)
    hits = [(0, 0.9), (1.0, 0.7), (1.75, 0.35)] if a < 22 else [(0, 1.9 if a < 28 else 4.5)]
    for off, d in hits:
        ts = np.arange(int(d * SR)) / SR
        s = (np.sin(2 * np.pi * f * ts) + 0.5 * np.sin(4 * np.pi * f * ts) + 0.2 * np.sin(6 * np.pi * f * ts)) \
            * smooth(ts, 0, 0.01) * np.exp(-ts / (0.6 if a < 22 else 1.4))
        s *= 1 - smooth(ts, d - 0.08, d)
        place(mus, s, a + off, 0.22 if a < 22 else 0.16)

# ---- marimba arpeggio (mix 1 velocity +-15 %, mix 7 octave layer 13-22 s, mix 10 night g .06) ----
ARP = [0, 1, 2, 3, 2, 1, 2, 3]
for k in range(int(2 / 0.25), int(28.5 / 0.25)):
    x = k * 0.25
    if x >= 22 and (k % 2):
        continue
    _, notes, _ = chord_at(x)
    step = int((x % 2) / 0.25)
    m = notes[ARP[step]] + 12
    g = (0.16 if x < 22 else 0.06 if x < 24 else 0.10) * rng.uniform(0.85, 1.15)
    if step == 0:
        g *= 1.25
    pan = 0.3 if step % 2 else -0.3
    place(mus, marimba(mtof(m)), x, g, pan)
    if 13 <= x < 22:
        place(mus, marimba(mtof(m + 12), 0.4), x, 0.05, -pan)

# ---- mix 8: 1-bar lifts into 6 s and 18 s: soft rising noise + an ascending 16th run ----
for a, b in ((5.0, 6.0), (17.0, 18.0)):
    L = int((b - a) * SR); ts = np.arange(L) / SR
    rise = filt(rng.standard_normal(L), "band", [2000, 7000]) * smooth(ts, 0, b - a) ** 2 * (1 - smooth(ts, b - a - 0.05, b - a))
    place(mus, rise, a, 0.05)
    for j, m in enumerate([72, 76, 79, 84]):
        place(mus, marimba(mtof(m + 12), 0.4), b - 0.5 + 0.125 * j, 0.07 + 0.015 * j, -0.3 + 0.2 * j)

# ---- percussion: shaker 8ths from 2 s (mix 2), claps 2 & 4 from 6 s, none 9.5-13 s (mix 3); all off at 22 s ----
perc = np.zeros((2, N))
L = int(0.18 * SR); ts = np.arange(L) / SR
nz = filt(rng.standard_normal(L), "band", [1200, 8000])
clap = nz * (sum(np.exp(-np.clip(ts - d, 0, None) / 0.006) * (ts >= d) for d in (0, 0.008, 0.016)) * 0.5 + np.exp(-ts / 0.05) * (ts >= 0.024))
L2 = int(0.07 * SR); ts2 = np.arange(L2) / SR
shk = filt(rng.standard_normal(L2), "high", 7000) * smooth(ts2, 0, 0.012) * np.exp(-ts2 / 0.02)
for k in range(int(2 / 0.25), int(22 / 0.25)):
    x = k * 0.25
    q = round(x / 0.5, 6)
    if q == int(q) and int(q) % 2 == 1 and x >= 6 and not (9.5 <= x < 13):
        place(perc, clap, x, 0.16, 0.1)
    place(perc, shk, x, 0.14 if k % 2 else 0.09, -0.25 if k % 2 else 0.25)
perc *= (1 - smooth(t, 21.8, 22.05))[None, :]
mus += perc

# ---- SFX (page times) ----
fx = np.zeros((2, N))
place(fx, bell(mtof(79), 1.5, 0.5), 1.0, 0.16, -0.15)  # charger G5 -> C6
place(fx, bell(mtof(84), 2.0, 0.7), 1.12, 0.16, 0.15)
for i, x in enumerate([2, 2.5, 3, 3.5, 4, 4.5, 5, 5.25, 5.5, 5.75]):  # clock changes (FACE_T)
    ts_ = np.arange(int(0.15 * SR)) / SR
    place(fx, np.sin(2 * np.pi * mtof(96 if i % 2 else 91) * ts_) * np.exp(-ts_ / 0.03) * smooth(ts_, 0, 0.002), x, 0.06, 0.4 if i % 2 else -0.4)
ts_ = np.arange(int(0.05 * SR)) / SR
click = np.sin(2 * np.pi * 1800 * ts_) * np.exp(-ts_ / 0.008) + 0.3 * filt(rng.standard_normal(len(ts_)), "high", 4000) * np.exp(-ts_ / 0.003)
# v10 PAGE_T = 9.5/10.3/11.1/11.9, neighbour boards +0.4, LOOKS every 0.5 s from 13 s
for i, (x, g) in enumerate([(x, 0.05) for x in (9.5, 10.3, 11.1, 11.9)] + [(x, 0.03) for x in (9.9, 10.7, 11.5, 12.3)]
                           + [(x, 0.05) for x in np.arange(13.0, 18.0, 0.5)]):
    place(fx, click, x + 0.01, g, 0.3 if i % 2 else -0.3)
L3 = int(0.8 * SR); ts3 = np.arange(L3) / SR  # whoosh at the v10 rotation start 18.2 s, sweep <= 5 kHz
wn = filt(rng.standard_normal(L3), "high", 800)
fc = 1500 + 3500 * np.sin(np.pi * ts3 / 0.8) ** 2
al = 1 - np.exp(-2 * np.pi * fc / SR)
y = np.empty(L3); s = 0.0
for n in range(L3):
    s += al[n] * (wn[n] - s); y[n] = s
wh = y * smooth(ts3, 0, 0.4) * (1 - smooth(ts3, 0.4, 0.8))
place(fx, wh, 18.2, 0.10, -0.3); place(fx, np.roll(wh, 300), 18.2, 0.10, 0.3)  # starts with the turn, peaks mid-turn
for j, m in enumerate([72, 76, 79, 83, 84]):  # mood light shimmer
    place(fx, bell(mtof(m), 3.0, 1.2), 25.3 + 0.09 * j, 0.11, -0.4 + 0.2 * j)
place(fx, bell(mtof(79), 4.8, 1.4), 28.7, 0.20, -0.1)  # logo G5 -> C6, natural decay
place(fx, bell(mtof(84), 4.6, 1.9), 28.85, 0.20, 0.1)

# ---- reverb (mix 6: HPF 250 on the wet, wet .30), master soft clip ----
ir_len = int(1.6 * SR); irt = np.arange(ir_len) / SR
ir = np.stack([filt(rng.standard_normal(ir_len), "low", 7000) * np.exp(-irt / 0.35) for _ in range(2)])
ir /= np.sqrt((ir ** 2).sum(axis=1, keepdims=True))
dry = mus + fx
wet = filt(np.stack([fftconvolve(dry[c] - perc[c] * 0.7, ir[c])[:N] for c in range(2)]), "high", 250)
out = dry + wet * 0.30
out = out[:, int(TRIM * SR):]
out[:, -int(0.3 * SR):] *= np.linspace(1, 0, int(0.3 * SR))
out *= 0.9 / np.abs(out).max()
out = np.tanh(out * 1.4) / 1.4
pcm = (np.clip(out.T, -1, 1) * 32767).astype("<i2")
with wave.open(sys.argv[1], "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
print("ok", out.shape[1] / SR)
