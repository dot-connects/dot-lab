"""Reel v4 composer: the whole game flow (prep -> toss -> spill -> blackout -> slow-mo -> plate -> egg -> receipt).

Same frame as v3 (top band with fixed title + one yellow caption, 975x1350 game panel). EDL segment keys:
  src [a, b]       source seconds; a > b plays backwards (the rewind after the hook)
  speed            source seconds per output second (default 1)
  cap / caps       one caption, or [[src_t, text], ...] switching at source times
  flicks           up-chevron at each logged flick
  counter          spill badge from the timeline ("흘린 밥 N알 · N원")
  freeze           seconds to hold the last frame
  zoom [z0, z1, y, x] push-in over the segment, centred at panel height fraction y (or source px row if > 1), source px column x
  xf               cross-dissolve frames into this part (default: 5 at a cut, 0 when it continues the moment)
  say / say_t      story lines over the blurred, still-running game for say_t s, then the game sharpens
  card             the drawn last card (score to beat, phone flick, link button) over the dimmed frame
  phone [x, y]     looping phone-flick glyph centred at frame px (the opening: how you play)
  flash            white flash at the start (lights back on)
  y0 [a, b]        source crop top in px, eased from a to b over the first 0.8 s (the receipt sits below the panel)
  end + ring       ending: a yellow ring around one receipt line, ring = [[t, src_y_top, src_y_bottom], ...]
  title [t, text]  swap the fixed top title from t on (the ending's link line)
"""
import json, math, shutil, subprocess, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
from compose3 import f, centered, arrow, mask, BG, YEL, WHITE, DIM, PX, PY, PW, PH, TITLE


def cap_font(d, text, size=104):
    while size > 60 and d.textlength(text, font=f(size)) > 1000: size -= 4
    return size


def frame(src_im, cap, k, zoom=1.0, zy=0.5, y0=0, title=TITLE, zx=PW / 2):
    im = Image.new("RGB", (1080, 1920), BG); d = ImageDraw.Draw(im)
    if zoom > 1.001:
        w, h = PW / zoom, PH / zoom; cy = zy if zy > 1 else y0 + PH * zy; x0 = min(max(0, zx - w / 2), PW - w)  # zy > 1: absolute source px row
        y0 = min(max(0, cy - h / 2), src_im.height - h)
        panel = src_im.crop((int(x0), int(y0), int(x0 + w), int(y0 + h))).resize((PW, PH), Image.LANCZOS)
    else:
        panel = src_im.crop((0, int(y0), PW, int(y0) + PH))
    im.paste(panel, (PX, PY), MASK)
    d.text((PX + 4, 60), "D:OT lab  실험 #002", font=f(34), fill=DIM)
    centered(d, 120, title, f(62), WHITE)
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


def sampler(src):
    """Source frame at a fractional time: blend the two neighbours (slow parts), or average the frames a fast part skips
    over (motion blur), so speed changes don't judder."""
    cache = {}
    def load(i):
        i = min(len(src) - 1, max(0, i))
        if i not in cache:
            if len(cache) > 48: cache.pop(next(iter(cache)))
            cache[i] = Image.open(src[i]).convert("RGB")
        return cache[i]
    def at(st, sp):
        x = st * 30
        if sp > 1.15:
            span = min(4, int(round(sp))); i0 = int(round(x - (span - 1) / 2))
            im = load(i0)
            for j in range(1, span): im = Image.blend(im, load(i0 + j), 1 / (j + 1))
            return im
        i = int(x); fr = x - i
        if fr < 0.08: return load(i)
        if fr > 0.92: return load(i + 1)
        return Image.blend(load(i), load(i + 1), fr)
    return at


def phone_glyph():
    """A phone held like a wok handle: dark body, the wok on its screen."""
    g = Image.new("RGBA", (190, 330)); d = ImageDraw.Draw(g)
    d.rounded_rectangle((6, 6, 184, 324), 34, fill=(24, 22, 26, 255), outline=(255, 255, 255, 255), width=9)
    d.rounded_rectangle((22, 34, 168, 296), 18, fill=(70, 74, 82, 255))
    d.ellipse((40, 118, 150, 212), fill=(28, 30, 36, 255), outline=(150, 156, 168, 255), width=4)
    for x, y in ((70, 150), (88, 140), (104, 156), (82, 168), (120, 146), (96, 176), (112, 170), (66, 172)):
        d.ellipse((x, y, x + 13, y + 8), fill=(240, 120, 60, 255))
    d.rounded_rectangle((80, 16, 110, 22), 3, fill=(80, 80, 90, 255))
    return g


PHONE = None


def phone(im, lt, cx, cy):
    """Flick loop (0.7 s): the phone dips back, snaps up with ghost trails and 휙!, settles. Draws on im."""
    global PHONE
    if PHONE is None: PHONE = phone_glyph().resize((266, 462), Image.LANCZOS)
    ov = Image.new("RGBA", (1080, 1920)); o = ImageDraw.Draw(ov)
    c = (lt % 0.7) / 0.7
    def pose(c):
        if c < 0.35: q = c / 0.35; return 30 * q, -14 * q  # dip: down and tilted back
        if c < 0.5: q = (c - 0.35) / 0.15; q = 1 - (1 - q) ** 3; return 30 - 190 * q, -14 + 26 * q  # snap up
        q = (c - 0.5) / 0.5; q = q * q * (3 - 2 * q); return -160 + 160 * q, 12 - 12 * q  # settle
    def put(dy, ang, alpha):
        g = PHONE.rotate(ang, resample=Image.BICUBIC, expand=True)
        if alpha < 255: g.putalpha(g.getchannel("A").point(lambda v: v * alpha // 255))
        ov.alpha_composite(g, (int(cx - g.width / 2), int(cy + dy - g.height / 2)))
    if 0.36 < c < 0.62:
        for back, al in ((0.08, 70), (0.04, 120)):
            dy, ang = pose(max(0, c - back)); put(dy, ang, al)
        a = int(255 * (1 - abs(c - 0.47) / 0.15))
        for k in range(3):
            x = cx + 150 + k * 26; o.arc((x - 60, cy - 330 + k * 40, x + 60, cy - 60 + k * 40), 240, 360, fill=(255, 210, 80, max(0, a)), width=14)
        o.text((cx + 150, cy - 420), "휙!", font=f(110), fill=(255, 210, 80, max(0, a)), stroke_width=5, stroke_fill=(30, 20, 10, max(0, a)))
    dy, ang = pose(c); put(dy, ang, 255)
    return Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB")


def say(im, lt, seg):
    """Story text over the game, which keeps playing blurred behind it; when the text leaves, the game comes back sharp.
    seg["say"] = [[text, "w" | "y"], ...] lines, seg["say_t"] = seconds the text holds."""
    T = seg.get("say_t", 1.6); box = (PX, PY, PX + PW, PY + PH)
    k = 1.0 if lt < T else max(0.0, 1 - (lt - T) / 0.3)
    if lt < 0.25: k = min(k, lt / 0.25)
    if k <= 0: return im
    q = k * k * (3 - 2 * k)
    blur = im.crop(box).filter(ImageFilter.GaussianBlur(int(3 + 11 * q)))  # panel: shapes and fire colours stay readable
    im.paste(Image.blend(im.crop(box), blur, min(1, q * 1.4)), box[:2], MASK)
    im = over(im, lambda o: o.rounded_rectangle(box, 40, fill=(14, 9, 7, int(115 * q))))
    lines = seg["say"]; sizes = [56 if col == "s" else 96 for _, col in lines]  # "s": a small line under the story
    total = sum(int(sz * 1.25) for sz in sizes); y = PY + PH // 2 - total // 2 - 40
    if seg.get("say_phone"): y -= 230  # the flicking phone (and its 휙!) gets the lower half
    a = int(255 * q); ov = Image.new("RGBA", (1080, 1920)); o = ImageDraw.Draw(ov)
    for (text, col), sz in zip(lines, sizes):
        fnt = f(sz)
        while o.textlength(text, font=fnt) > PW - 80: sz -= 4; fnt = f(sz)
        w = o.textlength(text, font=fnt); rise = int(18 * (1 - q))
        o.text(((1080 - w) / 2, y + rise), text, font=fnt, fill=(YEL if col == "y" else DIM if col == "s" else WHITE) + (a,), stroke_width=5, stroke_fill=(20, 12, 8, a))
        y += int(sz * 1.25)
    im = Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB")
    if seg.get("say_phone"): im = Image.blend(im, phone(im, lt, *seg["say_phone"]), q)  # the phone leaves with the text
    return im


def challenge(im, lt, score):
    """The last card, drawn over the dimmed receipt: the score to beat, how you play, where the link is."""
    k = min(1, lt / 0.25); box = (PX, PY, PX + PW, PY + PH)
    im.paste(Image.blend(im.crop(box), im.crop(box).filter(ImageFilter.GaussianBlur(40)), k), box[:2], MASK)  # the receipt fades back
    al = int(242 * k)
    im = over(im, lambda o: o.rounded_rectangle((PX, PY, PX + PW, PY + PH), 40, fill=(16, 10, 8, al)))
    if lt < 0.15: return im
    d = ImageDraw.Draw(im)
    centered(d, PY + 90, f"이 판은 {score}점", f(92), WHITE, 4)  # the question itself is the top caption
    centered(d, PY + 215, "주방장은 92점", f(70), YEL, 3)
    im = phone(im, lt, 540, PY + 720); d = ImageDraw.Draw(im)
    centered(d, PY + 1000, "설치 없이 폰으로 12초", f(62), WHITE, 3)
    if lt > 0.5:
        pulse = 1 + 0.04 * math.sin(lt * 7)
        bw, bh = int(860 * pulse), int(150 * pulse); x0 = 540 - bw // 2; y0 = PY + 1130
        d.rounded_rectangle((x0, y0, x0 + bw, y0 + bh), 75, fill=(232, 72, 28))
        centered(d, y0 + bh // 2 - 40, "프로필 링크에서 바로 해 보기", f(int(64 * pulse)), WHITE)  # Black Han Sans has no arrow glyph
    return im


def END(im, lt, seg, score):
    """Receipt ending: a yellow box rings receipt lines in turn, seg["ring"] = [[t, src_y_top, src_y_bottom], ...]."""
    on = [r for r in seg["ring"] if lt >= r[0]]
    if not on: return im
    t, ya, yb = on[-1]
    k = min(1, (lt - t) / 0.2); pad = int(14 * (1 - k)) + 6
    top = PY + ya - seg["_y0"] - pad; bot = PY + yb - seg["_y0"] + pad
    def spot(o):  # the rest of the receipt sinks back so the ringed line is the only thing to read
        dim = (12, 8, 6, int(170 * k))
        o.rectangle((PX, PY, PX + PW, top), fill=dim); o.rectangle((PX, bot, PX + PW, PY + PH), fill=dim)
        o.rounded_rectangle((PX + 24 - pad, top, PX + PW - 24 + pad, bot), 22, outline=YEL + (int(255 * k),), width=10)
    return over(im, spot)


XF = 5  # frames of cross-dissolve at a real cut
RAMP = 0.22  # per-frame easing toward a new speed when the next part continues the same moment


def build(frames_dir, edl, out_mp4, score):
    global MASK; MASK = mask()
    src = sorted(Path(frames_dir).glob("f*.png")); log = [json.loads(l) for l in open(Path(frames_dir) / "timeline.jsonl", encoding="utf-8")]
    flick_at = [r[0] / 30 for r in log if r[8]]
    spilled = [r[11] for r in log]
    at = sampler(src)
    tmp = Path("cut4"); shutil.rmtree(tmp, ignore_errors=True); tmp.mkdir(); n = 0
    prev_cap = None; cap_t = 0.0  # output time the current caption appeared (same caption across a cut: no second pop)
    out_t = 0.0; st = None; cur_sp = 1.0; last_im = None; prev_b = None
    for seg in edl:
        a, b = seg["src"]; sp = seg.get("speed", 1.0)
        joined = st is not None and prev_b is not None and abs(a - prev_b) < 0.3  # same moment continues: ramp, no cut
        if not joined: st = a; cur_sp = sp
        xf = 0 if last_im is None else seg.get("xf", 0 if joined else XF)  # xf: dissolve a joined part too (the title card vanishes in one frame)
        hold = int(round(seg.get("freeze", 0) * 30)); z = seg.get("zoom"); i = 0; held = 0
        while True:
            if st < b - 1e-6:
                cur_sp += (sp - cur_sp) * RAMP
            elif held < hold:
                held += 1; cur_sp = 0
            else:
                break
            lt = i / 30
            cap, _ = caption_at(seg, st)
            if cap != prev_cap: prev_cap = cap; cap_t = out_t
            zoom, zy, zx = 1.0, 0.5, PW / 2
            if z: q = min(1, (st - a) / max(1e-6, b - a)); q = q * q * (3 - 2 * q); zoom = z[0] + (z[1] - z[0]) * q; zy = z[2]; zx = z[3] if len(z) > 3 else PW / 2
            y0 = 0
            if seg.get("y0"): q = min(1, i / 24); q = q * q * (3 - 2 * q); y0 = seg["y0"][0] + (seg["y0"][1] - seg["y0"][0]) * q
            title = seg["title"][1] if seg.get("title") and lt >= seg["title"][0] else TITLE
            im = frame(at(st, cur_sp), cap, out_t - cap_t, zoom, zy, y0, title, zx)
            seg["_y0"] = y0
            idx = min(len(src) - 1, int(round(st * 30)))
            if seg.get("flicks"):
                past = [t for t in flick_at if t <= st]
                if past:
                    ov = arrow(ImageDraw.Draw(im), (st - past[-1]) / max(1.0, sp))
                    if ov: im = Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB")
            if seg.get("counter"):
                nsp = spilled[idx]
                if nsp >= 20:  # a grain or two is noise, the badge is for the 와르르
                    pop = 1 + 0.25 * max(0, 1 - held / 6) if held else 1  # the freeze punches the badge
                    def badge(o, nsp=nsp, pop=pop):
                        txt = f"흘린 밥 {nsp}알 = {nsp * 10:,}원"; fs = f(int(64 * pop)); w = o.textlength(txt, font=fs)  # Black Han Sans has no middle dot
                        x0 = (1080 - w) / 2 - 36; y0 = PY + 170  # under the beat bar, clear of the in-game word line
                        o.rounded_rectangle((x0, y0, x0 + w + 72, y0 + 64 * pop + 44), 28, fill=(12, 8, 6, 225))
                        o.text((x0 + 36, y0 + 16), txt, font=fs, fill=YEL + (255,))
                    im = over(im, badge)
            if seg.get("flash") and lt < 0.25:
                al = int(200 * (1 - lt / 0.25)); im = over(im, lambda o: o.rounded_rectangle((PX, PY, PX + PW, PY + PH), 40, fill=(255, 250, 235, al)))
            if seg.get("phone"): im = phone(im, lt, *seg["phone"])
            if seg.get("end"): im = END(im, lt, seg, score)
            if seg.get("card"): im = challenge(im, lt, score)
            if seg.get("say"): im = say(im, lt, seg)
            if i < xf:  # dissolve the game panel only; the caption band switches cleanly
                box = (0, PY, 1080, PY + PH); im.paste(Image.blend(last_im.crop(box), im.crop(box), (i + 1) / (xf + 1)), box[:2])
            im.save(tmp / f"o{n:05d}.png"); n += 1; out_t += 1 / 30; i += 1
            last_im = im
            if cur_sp: st += cur_sp / 30
        prev_b = b
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", "30", "-i", str(tmp / "o%05d.png"), "-c:v", "libx264",
                    "-pix_fmt", "yuv420p", "-crf", "19", "-preset", "slow", "-movflags", "+faststart", out_mp4], check=True)
    return n


if __name__ == "__main__":
    print(build(sys.argv[1], json.load(open(sys.argv[2], encoding="utf-8")), sys.argv[3], int(sys.argv[4])))
