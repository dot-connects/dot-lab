# Comparison sheets: per clip, frames 15 and 45; rows = methods, cols = cut-out on purple | alpha | 3x crop (cut-out) | 3x crop (alpha)
import numpy as np, cv2
from analyze import frames, M512, CLIPS, X0, X1, f32, keyed
PUR = np.array([64, 22, 42], np.float32)  # BGR of #2a1640
def row(I, a, cx, cy, label):
    comp = I * a[..., None] + PUR * (1 - a[..., None]); al = np.repeat(a[..., None] * 255, 3, 2)
    x0, y0 = int(np.clip(cx - 48, 0, I.shape[1] - 96)), int(np.clip(cy - 36, 0, I.shape[0] - 72))
    z = lambda m: cv2.resize(m[y0:y0 + 72, x0:x0 + 96], (288, 216), interpolation=cv2.INTER_NEAREST)
    small = lambda m: cv2.resize(m, (169, 216), interpolation=cv2.INTER_AREA)
    r = np.concatenate([small(comp), small(al), z(comp), z(al)], 1).astype(np.uint8)
    lab = np.full((216, 150, 3), 255, np.uint8)
    for i, t in enumerate(label.split("|")): cv2.putText(lab, t, (6, 30 + 24 * i), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 0, 0), 1, cv2.LINE_AA)
    return np.concatenate([lab, r], 1)
for c, s in CLIPS:
    L = lambda n: np.load(f"out/{n}_{c}{s}.npy").astype(np.float32) / 255
    R = f32(M512[c][s - 1:s + 59])[:, :, X0:X1]; F = L("fast")[:, :, X0:X1]
    meth = [("fast (now)|selfie+bilateral|real time", F), ("MODNet 10fps|keyframes + lerp", keyed(R, F, 3, "lerp")),
            ("MODNet 5fps|+ fast-matte|motion fuse", keyed(R, F, 6, "fuse")), ("MODNet 30fps|every frame|(reference)", R),
            ("plate + fast|diff keying|real time?", L("hyb_f")), ("plate + MODNet|diff keying", L("hyb_m")),
            ("BGMv2|(plate)", L("bgm")), ("BGMv2 plate|-8% exposure", L("bgm_dark"))]
    for k in (15, 45):
        I = cv2.cvtColor(frames[c][s - 1 + k][:, X0:X1], cv2.COLOR_RGB2BGR).astype(np.float32)
        m = R[k] > 0.5; ys = np.where(m.any(1))[0]; cy = ys[0] + 190; xs = np.where(m[cy])[0]; cx = xs[0]
        sheet = np.concatenate([row(I, a[k], cx, cy, lab) for lab, a in meth], 0)
        cv2.imwrite(f"cmp_{c}{s}_f{k}.png", sheet)
    print(c, s)
