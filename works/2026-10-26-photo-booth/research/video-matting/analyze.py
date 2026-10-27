# Quality study on real footage (no ground truth): fast matte vs MODNet keyframes vs background-plate methods.
# Reference for the clip study = MODNet 512x384 on every frame. Plate = per-pixel median of background samples over 8 s.
import sys, json, glob, numpy as np, cv2, onnxruntime as ort
X0, X1 = 128, 512                      # 384 px wide crop around the 376 px sharp inset (outside = blurred moving copy)
CLIPS = [("a", 1), ("a", 121), ("b", 1), ("b", 121)]
frames = {c: np.stack([cv2.cvtColor(cv2.imread(f), cv2.COLOR_BGR2RGB) for f in sorted(glob.glob(f"media/{c}/*.jpg"))]) for c in "ab"}
M512 = {c: np.load(f"out/modnet_512_{c}.npy") for c in "ab"}
M384 = {c: np.load(f"out/modnet_384_{c}.npy") for c in "ab"}
f32 = lambda a: a.astype(np.float32) / 255
def sstep(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)

# ---- fast matte: segment.js port (EMA k0 0.6 motion-adaptive, dropout guard skipped, 7x7 joint bilateral, smoothstep .3/.85)
def fast(c, s):
    raw = np.fromfile(f"out/selfie_{c}{s}.u8", np.uint8).reshape(60, 192, 256).astype(np.float32) / 255
    out, ema = [], None
    for k in range(60):
        a = raw[k]
        if ema is None: ema = a.copy()
        else:
            d = np.abs(a - ema); kk = np.where(d >= 0.25, 0, 0.6 * (1 - 4 * d)); ema = ema * kk + a * (1 - kk)
        img = f32(frames[c][s - 1 + k]); H, W = img.shape[:2]
        m = cv2.resize(ema, (W, H), interpolation=cv2.INTER_LINEAR)
        step = max(1, W / 256); acc = np.zeros((H, W), np.float32); ws = np.zeros((H, W), np.float32)
        gx, gy = np.meshgrid(np.arange(W, dtype=np.float32), np.arange(H, dtype=np.float32))
        for y in range(-3, 4):
            for x in range(-3, 4):
                mx, my = gx + x * step, gy + y * step
                q = cv2.remap(img, mx, my, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
                mq = cv2.remap(m, mx, my, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
                dc = q - img; w = np.exp(-(x * x + y * y) / 8 - (dc * dc).sum(2) / (2 * 0.08 ** 2))
                acc += w * mq; ws += w
        out.append(sstep(0.3, 0.85, acc / ws))
    return np.stack(out)

# ---- keyframe propagation (MODNet every `step` frames)
def keyed(R, F, step, how):
    n = len(R); keys = list(range(0, n, step)) + ([n - 1] if (n - 1) % step else [])
    out = []
    for t in range(n):
        k0 = max(k for k in keys if k <= t); k1 = min(k for k in keys if k >= t); u = 0 if k1 == k0 else (t - k0) / (k1 - k0)
        if how == "hold": out.append(R[k0] if u < 0.5 else R[k1]); continue
        base = (1 - u) * R[k0] + u * R[k1]
        if how == "lerp": out.append(base); continue
        # fuse: where the fast matte moved since the keyframes, trust the fast matte (motion), else the HQ keyframe blend
        fk = (1 - u) * F[k0] + u * F[k1]; mot = cv2.GaussianBlur(np.abs(F[t] - fk), (0, 0), 3)
        w = sstep(0.05, 0.25, mot); out.append(np.clip((1 - w) * base + w * F[t], 0, 1))
    return np.stack(out)

def band(R, r=12):
    b = (R > 0.5).astype(np.uint8); k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * r + 1, 2 * r + 1))
    return (cv2.dilate(b, k) - cv2.erode(b, k)) > 0
def crop(a): return a[..., X0:X1] if a.ndim == 3 and a.shape[-1] == 640 else a[:, :, X0:X1]

# ---- plate: per-pixel median over frames where MODNet (dilated 15 px) says background
def plate(c):
    I = frames[c][:, :, X0:X1].astype(np.float32); A = M512[c][:, :, X0:X1]
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (31, 31))
    bg = np.stack([cv2.dilate((a > 25).astype(np.uint8), k) == 0 for a in A])
    cov = bg.sum(0); P = np.zeros(I.shape[1:], np.float32)
    for ch in range(3):
        v = np.where(bg, I[..., ch], np.nan); P[..., ch] = np.nan_to_num(np.nanmedian(v, 0))
    hole = (cov < 3).astype(np.uint8)
    P8 = cv2.inpaint(np.clip(P, 0, 255).astype(np.uint8), hole, 7, cv2.INPAINT_TELEA)
    return P8, cov, bg

# ---- hybrid difference keying: alpha = projection of (I-B) on (F^-B), only in the MODNet unknown band, weighted by |F^-B|
def hybrid(I, B, M, gain=True, ring=10):
    I = f32(I); B = f32(B).copy()
    sure_bg = M < 0.02
    if gain:  # auto-exposure drift: per-channel gain from pixels that are surely background in this frame
        k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (41, 41)); far = cv2.erode(sure_bg.astype(np.uint8), k) > 0
        if far.sum() > 500: B *= np.median(I[far], 0) / np.maximum(np.median(B[far], 0), 1e-3)
    fg = (M > 0.97).astype(np.float32); s = 9
    Fh = cv2.GaussianBlur(I * fg[..., None], (0, 0), s) / np.maximum(cv2.GaussianBlur(fg, (0, 0), s), 1e-4)[..., None]
    dFB = Fh - B; den = (dFB * dFB).sum(2); ak = np.clip(((I - B) * dFB).sum(2) / np.maximum(den, 1e-4), 0, 1)
    kk = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * ring + 1, 2 * ring + 1)); b = (M > 0.5).astype(np.uint8)
    unk = ((cv2.dilate(b, kk) - cv2.erode(b, kk)) > 0) | ((M > 0.02) & (M < 0.98))
    w = sstep(0.06, 0.18, np.sqrt(den)) * unk
    return np.clip(w * ak + (1 - w) * M, 0, 1), w

if __name__ == "__main__":
    res = {}; Fs = {}
    for c, s in CLIPS:
        R = f32(M512[c][s - 1:s + 59]); F = fast(c, s); Fs[(c, s)] = F; np.save(f"out/fast_{c}{s}.npy", (F * 255).astype(np.uint8))
        bd = np.stack([band(r) for r in R])[:, :, X0:X1]
        mad = lambda A: round(float(np.abs(crop(A) - crop(R))[bd].mean() * 255), 1)
        flick = lambda A: round(float(np.abs(np.diff(crop(A), axis=0))[bd[1:]].mean() * 255), 1)
        r = {"fast": [mad(F), flick(F)], "modnet384_all": [mad(f32(M384[c][s - 1:s + 59])), flick(f32(M384[c][s - 1:s + 59]))], "ref512_all": [0, flick(R)]}
        for fps, st in [(15, 2), (10, 3), (5, 6)]:
            for how in ["hold", "lerp", "fuse"]: r[f"{how}{fps}"] = [mad(keyed(R, F, st, how)), flick(keyed(R, F, st, how))]
        res[f"{c}{s}"] = r; print(c, s, r, flush=True)
    json.dump(res, open("out/clip_metrics.json", "w"), indent=1)
