"""Reel v3 composer: top band (fixed title + one changing yellow caption) / game panel / empty IG-UI floor.

EDL segment: {"src": [a, b], "speed": s, "cap": "text" | "", "flicks": bool, "end": bool}
Captions live only in the top band, so they never sit on in-game words.
"""
import json, shutil, subprocess, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

BHS = Path(__file__).with_name("BlackHanSans.ttf")  # OFL, fetched on first run, not committed
if not BHS.exists():
    import urllib.request
    urllib.request.urlretrieve("https://github.com/google/fonts/raw/main/ofl/blackhansans/BlackHanSans-Regular.ttf", BHS)
BHS = str(BHS)
BG = (20, 14, 12); YEL = (255, 201, 60); WHITE = (255, 255, 255); DIM = (150, 138, 128)
PX, PY, PW, PH = 52, 330, 975, 1350  # panel box in the 1080x1920 frame; source crop = css y 0..540 at dpr 2.5
TITLE = "폰 흔들면 볶아지는 김치볶음밥"


def f(px): return ImageFont.truetype(BHS, px)


def centered(d, y, text, font, fill, stroke=0):
    w = d.textlength(text, font=font); d.text(((1080 - w) / 2, y), text, font=font, fill=fill, stroke_width=stroke, stroke_fill=BG)


def mask():
    m = Image.new("L", (PW, PH), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, PW - 1, PH - 1), 40, fill=255); return m


def base(src_im, cap, k):
    im = Image.new("RGB", (1080, 1920), BG); d = ImageDraw.Draw(im)
    im.paste(src_im.crop((0, 0, PW, PH)), (PX, PY), MASK)
    d.text((PX + 4, 60), "D:OT lab  실험 #002", font=f(34), fill=DIM)
    centered(d, 120, TITLE, f(62), WHITE)
    if cap:
        pop = 1.15 - 0.15 * min(1, k / 0.12); size = int(104 * pop)
        centered(d, 214 - (size - 104) // 2, cap, f(size), YEL)
    return im, d


def arrow(d, k):
    """Phone-flick marker at the panel's lower left: an up chevron that rises and fades over 0.35 s."""
    if k > 0.35: return
    a = int(255 * (1 - k / 0.35)); y = PY + PH - 260 - int(120 * k / 0.35); x = PX + 90
    col = (255, 255, 255, a)
    ov = Image.new("RGBA", (1080, 1920)); o = ImageDraw.Draw(ov)
    for j in range(2):
        yy = y + j * 46; o.line([(x - 44, yy + 40), (x, yy), (x + 44, yy + 40)], fill=col, width=16, joint="curve")
    o.text((x - 34, y + 110), "휙", font=f(52), fill=col)
    return ov


def build(frames_dir, edl, out_mp4, score):
    global MASK; MASK = mask()
    src = sorted(Path(frames_dir).glob("f*.png")); log = [json.loads(l) for l in open(Path(frames_dir) / "timeline.jsonl", encoding="utf-8")]
    flick_at = [r[0] / 30 for r in log if r[8]]
    tmp = Path("cut3"); shutil.rmtree(tmp, ignore_errors=True); tmp.mkdir(); n = 0; prev = None
    for seg in edl:
        carry = 9.0 if seg.get("cap") and seg.get("cap") == prev else 0.0; prev = seg.get("cap")  # same caption across a cut: no second pop
        a, b = seg["src"]; sp = seg.get("speed", 1.0); frames = int(round((b - a) / sp * 30))
        for i in range(frames):
            st = a + i / 30 * sp; idx = min(len(src) - 1, int(round(st * 30))); lt = i / 30
            im, d = base(Image.open(src[idx]).convert("RGB"), seg.get("cap", ""), lt + carry)
            if seg.get("flicks"):
                past = [t for t in flick_at if t <= st]
                if past:
                    ov = arrow(d, (st - past[-1]) / sp)
                    if ov: im = Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB")
            if seg.get("end"):
                ov = Image.new("RGBA", (1080, 1920)); o = ImageDraw.Draw(ov)
                o.rounded_rectangle((PX, PY, PX + PW, PY + PH), 40, fill=(12, 8, 6, int(205 * min(1, lt / 0.3))))
                im = Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB"); d = ImageDraw.Draw(im)
                pop = 1.25 - 0.25 * min(1, lt / 0.18)
                centered(d, 640 - int(150 * (pop - 1)), f"{score}점", f(int(300 * pop)), YEL)
                gap = 92 - score
                if lt > 0.5: centered(d, 1000, f"주방장까지 {gap}점" if gap > 0 else "주방장 등극", f(96), WHITE)
                if lt > 1.1: centered(d, 1150, "링크는 프로필에", f(60), DIM)
            im.save(tmp / f"o{n:05d}.png"); n += 1
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", "30", "-i", str(tmp / "o%05d.png"), "-c:v", "libx264",
                    "-pix_fmt", "yuv420p", "-crf", "19", "-preset", "slow", "-movflags", "+faststart", out_mp4], check=True)
    return n


if __name__ == "__main__":
    print(build(sys.argv[1], json.load(open(sys.argv[2], encoding="utf-8")), sys.argv[3], int(sys.argv[4])))
