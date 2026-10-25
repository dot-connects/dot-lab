"""Reel v4 composer: the whole game flow (prep -> toss -> spill -> blackout -> slow-mo -> plate -> egg -> receipt).

Same frame as v3 (top band with fixed title + one yellow caption, 975x1350 game panel). EDL segment keys:
  src [a, b]       source seconds; a > b plays backwards (the rewind after the hook)
  speed            source seconds per output second (default 1)
  cap / caps       one caption, or [[src_t, text], ...] switching at source times
  flicks           up-chevron at each logged flick
  counter          spill badge from the timeline ("흘린 밥 N알 · N원")
  freeze           seconds to hold the last frame
  zoom [z0, z1, y] push-in over the segment, centred at panel height fraction y
  flash            white flash at the start (lights back on)
  y0 [a, b]        source crop top in px, eased from a to b over the first 0.8 s (the receipt sits below the panel)
  cta              ending: CTA lines in a dark box over the plate, the receipt score stays readable
"""
import json, shutil, subprocess, sys
from pathlib import Path
from PIL import Image, ImageDraw
from compose3 import f, centered, arrow, mask, BG, YEL, WHITE, DIM, PX, PY, PW, PH, TITLE


def cap_font(d, text, size=104):
    while size > 60 and d.textlength(text, font=f(size)) > 1000: size -= 4
    return size


def frame(src_im, cap, k, zoom=1.0, zy=0.5, y0=0):
    im = Image.new("RGB", (1080, 1920), BG); d = ImageDraw.Draw(im)
    if zoom > 1.001:
        w, h = PW / zoom, PH / zoom; cy = PH * zy; x0 = (PW - w) / 2; y0 = min(max(0, cy - h / 2), PH - h)
        panel = src_im.crop((int(x0), int(y0), int(x0 + w), int(y0 + h))).resize((PW, PH), Image.LANCZOS)
    else:
        panel = src_im.crop((0, int(y0), PW, int(y0) + PH))
    im.paste(panel, (PX, PY), MASK)
    d.text((PX + 4, 60), "D:OT lab  실험 #002", font=f(34), fill=DIM)
    centered(d, 120, TITLE, f(62), WHITE)
    if cap:
        s0 = cap_font(d, cap); pop = 1.15 - 0.15 * min(1, k / 0.12); size = int(s0 * pop)
        if d.textlength(cap, font=f(size)) > 1040: size = s0
        centered(d, 214 + (104 - s0) // 2 - (size - s0) // 2, cap, f(size), YEL)
    return im


def over(im, draw_fn):
    ov = Image.new("RGBA", (1080, 1920)); draw_fn(ImageDraw.Draw(ov))
    return Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB")


def caption_at(seg, st):
    if "caps" not in seg: return seg.get("cap", ""), seg.get("_t0", 0)
    cur, t0 = "", 0
    for t, text in seg["caps"]:
        fwd = seg["src"][0] <= seg["src"][1]
        if (st >= t) if fwd else (st <= t): cur, t0 = text, t
    return cur, t0


def build(frames_dir, edl, out_mp4, score):
    global MASK; MASK = mask()
    src = sorted(Path(frames_dir).glob("f*.png")); log = [json.loads(l) for l in open(Path(frames_dir) / "timeline.jsonl", encoding="utf-8")]
    flick_at = [r[0] / 30 for r in log if r[8]]
    spilled = [r[11] for r in log]
    tmp = Path("cut4"); shutil.rmtree(tmp, ignore_errors=True); tmp.mkdir(); n = 0
    prev_cap = None; cap_t = 0.0  # output time the current caption appeared (same caption across a cut: no second pop)
    out_t = 0.0
    for seg in edl:
        a, b = seg["src"]; sp = seg.get("speed", 1.0); dirn = 1 if b >= a else -1
        frames = int(round(abs(b - a) / sp * 30)); hold = int(round(seg.get("freeze", 0) * 30)); total = frames + hold
        z = seg.get("zoom")
        for i in range(total):
            st = a + dirn * min(i, frames - 1 if frames else 0) / 30 * sp; idx = min(len(src) - 1, max(0, int(round(st * 30)))); lt = i / 30
            cap, _ = caption_at(seg, st)
            if cap != prev_cap: prev_cap = cap; cap_t = out_t
            zoom, zy = 1.0, 0.5
            if z: q = min(1, i / max(1, frames - 1)); q = q * q * (3 - 2 * q); zoom = z[0] + (z[1] - z[0]) * q; zy = z[2]
            y0 = 0
            if seg.get("y0"): q = min(1, i / 24); q = q * q * (3 - 2 * q); y0 = seg["y0"][0] + (seg["y0"][1] - seg["y0"][0]) * q
            im = frame(Image.open(src[idx]).convert("RGB"), cap, out_t - cap_t, zoom, zy, y0)
            if seg.get("flicks") and dirn > 0:
                past = [t for t in flick_at if t <= st]
                if past:
                    ov = arrow(ImageDraw.Draw(im), (st - past[-1]) / sp)
                    if ov: im = Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB")
            if seg.get("counter"):
                nsp = spilled[idx]
                if nsp >= 20:  # a grain or two is noise, the badge is for the 와르르
                    pop = 1 + 0.25 * max(0, 1 - (i - frames) / 6) if i >= frames else 1  # the freeze punches the badge
                    def badge(o, nsp=nsp, pop=pop):
                        txt = f"흘린 밥 {nsp}알 = {nsp * 10:,}원"; fs = f(int(64 * pop)); w = o.textlength(txt, font=fs)  # Black Han Sans has no middle dot
                        x0 = (1080 - w) / 2 - 36; y0 = PY + PH - 130  # under the in-game word line
                        o.rounded_rectangle((x0, y0, x0 + w + 72, y0 + 64 * pop + 44), 28, fill=(12, 8, 6, 225))
                        o.text((x0 + 36, y0 + 16), txt, font=fs, fill=YEL + (255,))
                    im = over(im, badge)
            if seg.get("flash") and lt < 0.25:
                al = int(200 * (1 - lt / 0.25)); im = over(im, lambda o: o.rounded_rectangle((PX, PY, PX + PW, PY + PH), 40, fill=(255, 250, 235, al)))
            if seg.get("cta"):
                k = max(0.0, (lt - seg["cta"]) / 0.3)
                if k > 0:
                    al = int(150 * min(1, k))
                    def cta(o):
                        o.rounded_rectangle((PX, PY, PX + PW, PY + 250), 40, fill=(12, 8, 6, al))
                    im = over(im, cta); d = ImageDraw.Draw(im)
                    if k >= 1: centered(d, PY + 40, "영수증 점수 댓글로", f(78), YEL, 3)
                    if k >= 1 and lt - seg["cta"] > 0.5: centered(d, PY + 150, "링크는 프로필에", f(64), WHITE, 3)
            im.save(tmp / f"o{n:05d}.png"); n += 1; out_t += 1 / 30
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", "30", "-i", str(tmp / "o%05d.png"), "-c:v", "libx264",
                    "-pix_fmt", "yuv420p", "-crf", "19", "-preset", "slow", "-movflags", "+faststart", out_mp4], check=True)
    return n


if __name__ == "__main__":
    print(build(sys.argv[1], json.load(open(sys.argv[2], encoding="utf-8")), sys.argv[3], int(sys.argv[4])))
