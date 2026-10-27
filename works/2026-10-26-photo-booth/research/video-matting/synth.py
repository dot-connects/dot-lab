# Synthetic plate test with a known alpha: person from a still (alpha = MODNet on the original, used as ground truth),
# composited over 3 backgrounds; the plate is that background (exact / -8 % exposure / 2 px camera shift).
import json, numpy as np, cv2, onnxruntime as ort
from analyze import hybrid, sstep
mod = ort.InferenceSession("models/modnet.onnx", providers=["CPUExecutionProvider"])
bgm = ort.InferenceSession("models/bgmv2_mnv2_hd.onnx", providers=["CPUExecutionProvider"])
def modnet(I):
    x = ((cv2.resize(I, (512, 384), interpolation=cv2.INTER_AREA).astype(np.float32) / 255 - .5) / .5).transpose(2, 0, 1)[None]
    return cv2.resize(mod.run(None, {"input": x})[0][0, 0], (640, 480))
def bgmv2(I, B):
    t = lambda x: (x.astype(np.float32) / 255).transpose(2, 0, 1)[None]
    return bgm.run(["pha"], {"src": t(I), "bgr": t(B)})[0][0, 0]
rd = lambda p: cv2.cvtColor(cv2.imread(p), cv2.COLOR_BGR2RGB)
FG = {"hair2": cv2.resize(rd("../cutout-poc/media/hair2.jpg")[100:1060], (640, 480), interpolation=cv2.INTER_AREA),
      "hair1": cv2.resize(rd("../cutout-poc/media/hair1.jpg")[60:1020], (640, 480), interpolation=cv2.INTER_AREA)}
yy, xx = np.mgrid[0:480, 0:640].astype(np.float32); rng = np.random.default_rng(1)
BG = {"cream_wall": np.clip(np.stack([235 - yy / 20, 228 - yy / 20, 210 - yy / 18], 2) + rng.normal(0, 2, (480, 640, 3)), 0, 255),
      "dark_wall": np.clip(np.stack([38 + xx / 40, 34 + xx / 40, 36 + xx / 40], 2) + rng.normal(0, 2, (480, 640, 3)), 0, 255),
      "busy": cv2.resize(rd("media/busy.jpg"), (640, 480)).astype(np.float32)}
res, rows = {}, []
for fn, F in FG.items():
    A = np.clip(modnet(F), 0, 1)
    b = (A > 0.5).astype(np.uint8); k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (25, 25)); band = (cv2.dilate(b, k) - cv2.erode(b, k)) > 0
    for bn, B in BG.items():
        I = np.clip(F * A[..., None] + B * (1 - A[..., None]) + rng.normal(0, 2, B.shape), 0, 255).astype(np.uint8)
        plates = {"exact": B, "exp-8%": B * 0.92, "shift2px": np.roll(B, (2, 2), (0, 1))}
        M = np.clip(modnet(I), 0, 1)
        r = {"modnet": round(float(np.abs(M - A)[band].mean() * 255), 1)}
        for pn, P in plates.items():
            P8 = np.clip(P + rng.normal(0, 2, B.shape), 0, 255).astype(np.uint8)
            bg = bgmv2(I, P8); hy = hybrid(I, P8, M)[0]
            r[f"bgmv2_{pn}"] = round(float(np.abs(bg - A)[band].mean() * 255), 1)
            r[f"hybrid_{pn}"] = round(float(np.abs(hy - A)[band].mean() * 255), 1)
            if pn == "exact": keep = (I, M, bg, hy)
        res[f"{fn}/{bn}"] = r; print(fn, bn, r, flush=True)
        I, M, bg, hy = keep
        ys = np.where((A > 0.5).any(1))[0]; cy = ys[0] + 40; cx = np.where(A[cy] > 0.5)[0][0]
        x0, y0 = int(np.clip(cx - 64, 0, 512)), int(np.clip(cy - 48, 0, 384))
        z = lambda a: cv2.resize((np.repeat(a[..., None], 3, 2) * 255)[y0:y0 + 96, x0:x0 + 128].astype(np.uint8), (256, 192), interpolation=cv2.INTER_NEAREST)
        rows.append(np.concatenate([cv2.resize(I[y0:y0 + 96, x0:x0 + 128], (256, 192), interpolation=cv2.INTER_NEAREST), z(A), z(M), z(bg), z(hy)], 1))
sheet = cv2.cvtColor(np.concatenate(rows, 0), cv2.COLOR_RGB2BGR)
hdr = np.full((30, sheet.shape[1], 3), 255, np.uint8)
for i, t in enumerate(["composite (2x crop)", "truth alpha", "MODNet", "BGMv2 + plate", "MODNet + plate keying"]): cv2.putText(hdr, t, (6 + 256 * i, 21), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 1, cv2.LINE_AA)
cv2.imwrite("synth_plate_crops.png", np.concatenate([hdr, sheet], 0)); json.dump(res, open("out/synth_metrics.json", "w"), indent=1)
