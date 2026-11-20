"""Series reel frame: the work reel inside a phone frame + "N번째 그냥 시켜본 것" title + tester line (operator 2026-11-20)."""
import subprocess, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).parent / "_series_frame"  # intermediate PNGs
OUT.mkdir(exist_ok=True)
FONT = r"C:\Windows\Fonts\NotoSansKR-VF.ttf"
W, H = 1080, 1920
SH = 1340
SW = round(SH * 1080 / 1920)  # 754
PAD = 16
SX = (W - SW) // 2
SY = 318

WORKS = {
    "standby": dict(
        src=r"C:\DC\DD\dot-lab\works\2026-11-09-standby\out\reel.mp4",
        n=5, name="Stand Screen", desc="충전하는 동안 시계가 되는 앱",
        bg=(243, 238, 230), ink=(28, 24, 20), sub=(110, 100, 90), accent=(222, 110, 40),
    ),
    "catch": dict(
        src=r"C:\DC\DD\dot-lab\works\2026-11-09-catch\out\reel.mp4",
        n=6, name="리얼뽑기", desc="우리 아이를 뽑아 폰에 키우는 앱",
        bg=(221, 238, 250), ink=(24, 34, 48), sub=(80, 98, 120), accent=(232, 160, 0),
    ),
}


def font(size: int, weight: int) -> ImageFont.FreeTypeFont:
    f = ImageFont.truetype(FONT, size)
    f.set_variation_by_axes([weight])
    return f


def centered(d: ImageDraw.ImageDraw, y: int, parts: list[tuple[str, ImageFont.FreeTypeFont, tuple]]) -> None:
    widths = [d.textlength(t, font=f) for t, f, _ in parts]
    x = (W - sum(widths)) / 2
    for (t, f, c), w in zip(parts, widths):
        d.text((x, y), t, font=f, fill=c)
        x += w


def build(key: str) -> None:
    c = WORKS[key]
    # background + title + phone body
    bg = Image.new("RGB", (W, H), c["bg"])
    d = ImageDraw.Draw(bg)
    chip = font(30, 600)
    label = "그냥 시켜봄"
    lw = d.textlength(label, font=chip)
    d.rounded_rectangle(((W - lw) / 2 - 22, 92, (W + lw) / 2 + 22, 142), 25, outline=c["sub"], width=2)
    d.text(((W - lw) / 2, 98), label, font=chip, fill=c["sub"])
    big = font(80, 800)
    centered(d, 168, [(f"{c['n']}번째", big, c["accent"]), (" 그냥 시켜본 것", big, c["ink"])])
    d.rounded_rectangle((SX - PAD, SY - PAD, SX + SW + PAD, SY + SH + PAD), 72, fill=(17, 17, 19))
    d.rounded_rectangle((SX - PAD + 3, SY - PAD + 3, SX + SW + PAD - 3, SY + SH + PAD - 3), 69, outline=(70, 70, 76), width=2)
    bg.save(OUT / f"{key}-bg.png")
    # rounded screen mask
    m = Image.new("L", (SW, SH), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, SW - 1, SH - 1), 56, fill=255)
    m.save(OUT / f"{key}-mask.png")
    # bottom lines: A = what it is, B = tester call
    ty = SY + SH + PAD + 34
    for tag, parts in (
        ("a", [[(c["name"], font(44, 800), c["ink"]), ("  ·  " + c["desc"], font(36, 500), c["sub"])]]),
        ("b", [[("지금 테스터 모집 중", font(44, 800), c["accent"])],
               [("많이 써 주시면 진짜 출시해 볼게요", font(34, 500), c["sub"])]]),
    ):
        im = Image.new("RGBA", (W, H - ty), (0, 0, 0, 0))
        dd = ImageDraw.Draw(im)
        y = 0 if len(parts) > 1 else 22
        for line in parts:
            centered(dd, y, line)
            y += 60
        im.save(OUT / f"{key}-{tag}.png")

    dur = float(subprocess.check_output(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", c["src"]]).decode())
    sw = dur - 4.0  # switch to tester line for the last 4 s
    fc = (
        f"[0:v]scale={SW}:{SH},format=rgba[v];[2:v]format=gray[m];[v][m]alphamerge[r];"
        f"[1:v][r]overlay={SX}:{SY}[o1];"
        f"[3:v]format=rgba,fade=t=out:st={sw}:d=0.4:alpha=1[a];"
        f"[4:v]format=rgba,fade=t=in:st={sw}:d=0.4:alpha=1[b];"
        f"[o1][a]overlay=0:{ty}[o2];[o2][b]overlay=0:{ty},format=yuv420p[out]"
    )
    dst = Path(c["src"]).parent / "framed-v1.mp4"
    cmd = ["ffmpeg", "-v", "error", "-y", "-threads", "2", "-i", c["src"],
           "-loop", "1", "-t", f"{dur}", "-i", str(OUT / f"{key}-bg.png"),
           "-loop", "1", "-t", f"{dur}", "-i", str(OUT / f"{key}-mask.png"),
           "-loop", "1", "-t", f"{dur}", "-i", str(OUT / f"{key}-a.png"),
           "-loop", "1", "-t", f"{dur}", "-i", str(OUT / f"{key}-b.png"),
           "-filter_complex", fc, "-map", "[out]", "-map", "0:a", "-c:v", "libx264", "-preset", "medium",
           "-crf", "20", "-r", "30", "-c:a", "copy", "-shortest", "-movflags", "+faststart", str(dst)]
    subprocess.run(cmd, check=True)
    print(dst)


if __name__ == "__main__":
    for k in sys.argv[1:] or WORKS:
        build(k)
