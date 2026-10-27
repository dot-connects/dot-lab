# Plate methods on the 4 clips (crop x 128..512): BGMv2 (ORT CPU, same graph as web), hybrid keying on MODNet / fast matte.
# Also: real auto-exposure drift in the footage, and a simulated -8 % exposure step on the plate.
import json, numpy as np, cv2, onnxruntime as ort
from analyze import *
bgm = ort.InferenceSession("models/bgmv2_mnv2_hd.onnx", providers=["CPUExecutionProvider"])
def bgmv2(I, B):
    t = lambda x: (x.astype(np.float32) / 255).transpose(2, 0, 1)[None]
    return bgm.run(["pha"], {"src": t(I), "bgr": t(B)})[0][0, 0]
res = {}
for c in "ab":
    P, cov, bgmask = plate(c); cv2.imwrite(f"out/plate_{c}.png", cv2.cvtColor(P, cv2.COLOR_RGB2BGR))
    I = frames[c][:, :, X0:X1].astype(np.float32)
    # exposure drift: per-frame median ratio frame/plate on background samples, and residual noise floor after gain
    ratios = [float(np.median(I[k][bgmask[k]].mean(1)) / max(1, np.median(P[bgmask[k]].astype(np.float32).mean(1)))) for k in range(0, 240, 10)]
    resid = float(np.median(np.abs(I[100][bgmask[100]] - P[bgmask[100]]).mean(1)))
    res[c] = {"hole_pct": round(float((cov < 3).mean() * 100), 1), "ae_ratio_min_max": [round(min(ratios), 3), round(max(ratios), 3)], "bg_resid_median_0_255": round(resid, 1)}
    print(c, res[c], flush=True)
for c, s in CLIPS:
    P = cv2.cvtColor(cv2.imread(f"out/plate_{c}.png"), cv2.COLOR_BGR2RGB)
    R = f32(M512[c][s - 1:s + 59])[:, :, X0:X1]; F = np.load(f"out/fast_{c}{s}.npy")[:, :, X0:X1].astype(np.float32) / 255
    out = {k: [] for k in ["bgm", "bgm_dark", "hyb_m", "hyb_f", "hyb_m_dark", "hyb_m_dark_nogain"]}
    for k in range(60):
        I = frames[c][s - 1 + k][:, X0:X1]
        Pd = np.clip(P.astype(np.float32) * 0.92, 0, 255).astype(np.uint8)
        out["bgm"].append(bgmv2(I, P)); out["bgm_dark"].append(bgmv2(I, Pd))
        out["hyb_m"].append(hybrid(I, P, R[k])[0]); out["hyb_f"].append(hybrid(I, P, F[k])[0])
        out["hyb_m_dark"].append(hybrid(I, Pd, R[k])[0]); out["hyb_m_dark_nogain"].append(hybrid(I, Pd, R[k], gain=False)[0])
    bd = np.stack([band(r) for r in R])
    far = np.stack([cv2.dilate((r > 0.05).astype(np.uint8), np.ones((25, 25), np.uint8)) == 0 for r in R])
    r = {}
    for kk, v in out.items():
        v = np.stack(v); np.save(f"out/{kk}_{c}{s}.npy", (v * 255 + .5).astype(np.uint8))
        r[kk] = [round(float(np.abs(v - R)[bd].mean() * 255), 1), round(float(np.abs(np.diff(v, axis=0))[bd[1:]].mean() * 255), 1),
                 round(float(v[far].mean() * 255), 2)]
    res[f"{c}{s}"] = r; print(c, s, r, flush=True)
json.dump(res, open("out/plate_metrics.json", "w"), indent=1)
