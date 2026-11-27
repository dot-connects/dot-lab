"""Scores every method's mask against the hand GT. python eval.py -> table + per-image csv + example PNGs."""
import json, glob, os, sys
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage as ndi

HERE = os.path.dirname(os.path.abspath(__file__))
P = lambda *a: os.path.join(HERE, *a)
NAMES = [n for n in json.load(open(P("poly.json"))) if os.path.exists(P("out", n + ".json"))]
DET_ANI = {"cat", "dog", "bird", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe"}


def ld(n, k):
    return np.asarray(Image.open(P("out", f"{n}_{k}.png")).convert("L"), np.float32) / 255


def box_filter(a, r):
    return ndi.uniform_filter(a, size=2 * r + 1, mode="nearest")


def guided(I, p, r=6, eps=1e-3):
    """He et al. guided filter, grey guide I, input p (both float 0..1)."""
    mI, mp = box_filter(I, r), box_filter(p, r)
    cov = box_filter(I * p, r) - mI * mp
    var = box_filter(I * I, r) - mI * mI
    a = cov / (var + eps); b = mp - a * mI
    return box_filter(a, r) * I + box_filter(b, r)


def grid_app(conf, dets, W, H, claw):
    """The app's animal path: DeepLab conf sampled at cell centres of the 90x160 grid (160x90 for landscape),
    > 0.3; empty -> ellipse in the best animal box >= 0.3; pieceNear(claw); CutOut.alpha (3x3 count/9, bilinear)."""
    gw, gh = (90, 160) if H >= W else (160, 90)
    ys = ((np.arange(gh) + 0.5) * H / gh).astype(int); xs = ((np.arange(gw) + 0.5) * W / gw).astype(int)
    bits = conf[np.ix_(ys, xs)] > 0.3
    fell = False
    if not bits.any():
        best = sorted([d for d in dets if d["label"] in DET_ANI and d["score"] >= 0.3], key=lambda d: -d["score"])
        if best:
            d = best[0]; fell = True
            x0, y0 = int(d["x"] / W * gw), int(d["y"] / H * gh); x1, y1 = int((d["x"] + d["w"]) / W * gw), int((d["y"] + d["h"]) / H * gh)
            yy, xx = np.mgrid[0:gh, 0:gw]
            cx, cy, rx, ry = (x0 + x1) / 2, (y0 + y1) / 2, max(1, (x1 - x0) / 2), max(1, (y1 - y0) / 2)
            bits = ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1
    # pieceNear: the 4-connected piece nearest the claw cell
    lab, n = ndi.label(bits)
    if n > 1:
        cx, cy = int(claw[0] / W * gw), int(claw[1] / H * gh)
        pts = np.argwhere(bits); j = np.argmin((pts[:, 0] - cy) ** 2 + (pts[:, 1] - cx) ** 2)
        bits = lab == lab[tuple(pts[j])]
    cnt = ndi.uniform_filter(bits.astype(np.float32), 3, mode="constant")
    alpha = np.asarray(Image.fromarray((cnt * 255).astype(np.uint8)).resize((W, H), Image.BILINEAR), np.float32) / 255
    return alpha, fell


def piece(mask, pt):
    """The connected piece of a binary mask holding pt (or nearest to it)."""
    lab, n = ndi.label(mask)
    if n <= 1: return mask
    x, y = int(pt[0]), int(pt[1])
    if lab[y, x]: return lab == lab[y, x]
    pts = np.argwhere(mask); j = np.argmin((pts[:, 0] - y) ** 2 + (pts[:, 1] - x) ** 2)
    return lab == lab[tuple(pts[j])]


def edge(m):
    return m & ~ndi.binary_erosion(m)


def bf(pred, gt, tol):
    pe, ge = edge(pred), edge(gt)
    if not pe.any() or not ge.any(): return 0.0
    dg = ndi.distance_transform_edt(~ge); dp = ndi.distance_transform_edt(~pe)
    prec = (dg[pe] <= tol).mean(); rec = (dp[ge] <= tol).mean()
    return 0 if prec + rec == 0 else 2 * prec * rec / (prec + rec)


def score(pred, gt, tol):
    i = (pred & gt).sum(); u = (pred | gt).sum()
    return dict(iou=i / u if u else 0, bf=bf(pred, gt, tol), fp=(pred & ~gt).sum() / gt.sum(), fn=(gt & ~pred).sum() / gt.sum())


def methods(n):
    R = json.load(open(P("out", f"{n}.json")))
    W, H = R["W"], R["H"]
    img = np.asarray(Image.open(P("img", f"{n}.png")).convert("L"), np.float32) / 255
    gt = np.asarray(Image.open(P("gt", f"{n}.png"))) > 127
    # the claw lands inside the animal: GT's deepest point
    dt = ndi.distance_transform_edt(gt); cy, cx = np.unravel_index(dt.argmax(), dt.shape); claw = (cx, cy)
    A, B, C1, C2, C3, U, E = (ld(n, k) for k in "A B C1 C2 C3 U E".split())
    app, fell = grid_app(A, R["dets"], W, H, claw)
    seed = R["seed"]; centre = R["centre"]
    pb = R["crop"]; inbox = np.zeros_like(gt); inbox[pb["y"]:pb["y"] + pb["h"], pb["x"]:pb["x"] + pb["w"]] = True
    out = {}
    out["a 현행(DeepLab 전체→90x160)"] = app >= 0.5
    out["a' 현행 + guided filter"] = guided(img, app) >= 0.5
    out["b DeepLab 박스 crop"] = piece(B > 0.3, claw) if (B > 0.3).any() else B > 0.3
    out["b' crop + guided filter"] = piece(guided(img, B) > 0.4, claw) if (B > 0.3).any() else B > 0.3
    out["c1 magic touch crop·박스중심"] = piece((C1 > 0.5) & inbox, centre)
    out["c2 magic touch crop·DeepLab seed"] = piece((C2 > 0.5) & inbox, seed)
    out["c3 magic touch 전체·seed"] = piece(C3 > 0.5, seed)
    out["c2' c2 + guided filter"] = piece((guided(img, C2) > 0.5) & inbox, seed)
    out["e1 u2netp crop"] = piece((U > 0.5) & inbox, seed)
    out["v 투표(c2,c3,u2netp,b) 2표 이상"] = piece((((C2 > 0.5) * 1 + (C3 > 0.5) + (U > 0.5) + (B > 0.5)) >= 2) & inbox, seed)
    out["w 추천: c3 ∪ crop DeepLab>0.8, 박스 안"] = piece(((C3 > 0.5) | (B > 0.8)) & inbox, seed)
    tol = 0.01 * np.hypot(W, H)
    return {k: score(v, gt, tol) for k, v in out.items()}, out, R, fell, claw


def overlay(n, m, color):
    im = Image.open(P("img", f"{n}.png")).convert("RGB")
    a = np.asarray(im, np.float32); c = np.array(color, np.float32)
    a[m] = a[m] * 0.45 + c * 0.55
    e = edge(ndi.binary_dilation(m, iterations=1)); a[e] = c
    return Image.fromarray(a.astype(np.uint8))


if __name__ == "__main__":
    rows = {}; per = []; ms = {}
    allmasks = {}
    for n in NAMES:
        s, masks, R, fell, claw = methods(n)
        allmasks[n] = masks
        for k, v in s.items(): rows.setdefault(k, []).append(v)
        per.append((n, fell, s))
        if n != "src00":
            for k, v in R["ms"].items(): ms.setdefault(k, []).append(v)
    print("method | mIoU | median IoU | IoU<0.7 | BF@1% | FP/GT | FN/GT")
    for k, v in rows.items():
        iou = np.array([x["iou"] for x in v])
        print(f"{k} | {iou.mean():.3f} | {np.median(iou):.3f} | {(iou < 0.7).sum()}/{len(iou)} | {np.mean([x['bf'] for x in v]):.3f} | "
              f"{np.mean([x['fp'] for x in v]):.3f} | {np.mean([x['fn'] for x in v]):.3f}")
    print("\nms (desktop CPU wasm, 1 thread, median excl. warm-up):", {k: round(float(np.median(v))) for k, v in ms.items()})
    print("\nper image IoU (a / a' / b / c2 / e2) fell=ellipse fallback")
    for n, fell, s in per:
        g = lambda k: [x for x in s if x.startswith(k)][0]
        print(n, "fell" if fell else "", " ".join(f"{s[g(k)]['iou']:.2f}" for k in ["a ", "a'", "b ", "c2 ", "e1"]),
              f"fp={s[g('a ')]['fp']:.2f} fn={s[g('a ')]['fn']:.2f} bf={s[g('a ')]['bf']:.2f}")
    if len(sys.argv) > 1:   # example strips: image | GT | current | best
        od = sys.argv[1]; os.makedirs(od, exist_ok=True)
        for n in NAMES:
            gt = np.asarray(Image.open(P("gt", f"{n}.png"))) > 127
            tiles = [overlay(n, gt, (40, 200, 90)), overlay(n, allmasks[n]["a 현행(DeepLab 전체→90x160)"], (230, 60, 60)),
                     overlay(n, [v for k, v in allmasks[n].items() if k.startswith("w ")][0], (60, 120, 240))]
            w, h = tiles[0].size; k = 200 / max(w, h)
            tiles = [t.resize((round(w * k), round(h * k)), Image.LANCZOS) for t in tiles]
            S = Image.new("RGB", (tiles[0].width * 3 + 8, tiles[0].height), "white")
            for i, t in enumerate(tiles): S.paste(t, (i * (t.width + 4), 0))
            S.save(os.path.join(od, f"{n}.png"), optimize=True)
