"""Stand Screen app icon, final (F4: sideways phone on a stand, clock hands). One geometry, three outputs:
master SVG, Android adaptive-icon vector drawables (foreground + monochrome) and preview PNGs.
Units: the 108 x 108 adaptive canvas; the symbol stays inside the 66-unit safe circle."""
import math
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).parent
BG, INK, ACC = "#F4EEE3", "#1B2238", "#FF6F59"
S = 0.72                                 # symbol scale around the canvas centre (keeps corners in the safe circle)
C = 54.0
DY = 3.0                                 # symbol shift down (canvas units): the heavy phone outline reads high otherwise


def sx(v: float) -> float:
    return C + (v - C) * S


def sy(v: float) -> float:
    return C + (v - C) * S + DY


# geometry in unscaled canvas units
PHONE = (22, 33, 86, 63)                 # outline rect (stroke centre line)
PHONE_R, STROKE = 9.0, 4.4
CAM = (22.0, 48.0, 1.9)                  # camera dot on the short left edge
STAND = (27, 68.3, 81, 71.7)               # stand bar
HUB = (54.0, 52.0)
HANDS = [(302, 11.0), (60, 18.0)]        # (angle deg clockwise from 12, length): 10 o'clock-ish hour, 2 o'clock minute
HAND_W, HUB_R = 3.8, 2.9


def hand_end(ang: float, ln: float) -> tuple[float, float]:
    a = math.radians(ang)
    return HUB[0] + ln * math.sin(a), HUB[1] - ln * math.cos(a)


def svg(monochrome: bool = False, background: bool = True) -> str:
    ink = "#000" if monochrome else INK
    acc = "#000" if monochrome else ACC
    x0, y0, x1, y1 = PHONE
    hands = "".join(f'<line x1="{HUB[0]}" y1="{HUB[1]}" x2="{hand_end(a, l)[0]:.2f}" y2="{hand_end(a, l)[1]:.2f}"/>' for a, l in HANDS)
    bg = f'<rect width="108" height="108" fill="{BG}"/>' if background and not monochrome else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">{bg}'
            f'<g transform="translate({C} {C + DY}) scale({S}) translate({-C} {-C})">'
            f'<rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" rx="{PHONE_R}" fill="none" stroke="{ink}" stroke-width="{STROKE}"/>'
            f'<circle cx="{CAM[0]}" cy="{CAM[1]}" r="{CAM[2]}" fill="{acc}"/>'
            f'<rect x="{STAND[0]}" y="{STAND[1]}" width="{STAND[2] - STAND[0]}" height="{STAND[3] - STAND[1]}" rx="{(STAND[3] - STAND[1]) / 2}" fill="{acc}"/>'
            f'<g stroke="{acc}" stroke-width="{HAND_W}" stroke-linecap="round">{hands}</g>'
            f'<circle cx="{HUB[0]}" cy="{HUB[1]}" r="{HUB_R}" fill="{acc}"/></g></svg>')


def draw(px: int, monochrome: bool = False) -> Image.Image:
    """Raster of the same geometry, 4x supersampled."""
    k = px * 4 / 108
    ink = (0, 0, 0, 255) if monochrome else INK
    acc = (0, 0, 0, 255) if monochrome else ACC
    im = Image.new("RGBA", (px * 4, px * 4), (0, 0, 0, 0) if monochrome else BG)
    d = ImageDraw.Draw(im)
    t = lambda x, y: (sx(x) * k, sy(y) * k)
    w = STROKE * S * k
    x0, y0 = t(PHONE[0], PHONE[1]); x1, y1 = t(PHONE[2], PHONE[3])
    d.rounded_rectangle((x0 - w / 2, y0 - w / 2, x1 + w / 2, y1 + w / 2), radius=(PHONE_R + STROKE / 2) * S * k, fill=ink)
    d.rounded_rectangle((x0 + w / 2, y0 + w / 2, x1 - w / 2, y1 - w / 2), radius=(PHONE_R - STROKE / 2) * S * k,
                        fill=(0, 0, 0, 0) if monochrome else BG)
    cx, cy = t(CAM[0], CAM[1]); r = CAM[2] * S * k
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=acc)
    a0, b0 = t(STAND[0], STAND[1]); a1, b1 = t(STAND[2], STAND[3])
    d.rounded_rectangle((a0, b0, a1, b1), radius=(b1 - b0) / 2, fill=acc)
    hx, hy = t(*HUB); hw = HAND_W * S * k
    for a, l in HANDS:
        ex, ey = t(*hand_end(a, l))
        d.line((hx, hy, ex, ey), fill=acc, width=round(hw))
        for px_, py_ in ((ex, ey), (hx, hy)):
            d.ellipse((px_ - hw / 2, py_ - hw / 2, px_ + hw / 2, py_ + hw / 2), fill=acc)
    r = HUB_R * S * k
    d.ellipse((hx - r, hy - r, hx + r, hy + r), fill=acc)
    return im.resize((px, px), Image.LANCZOS)


def vector_drawable(monochrome: bool) -> str:
    """Android <vector> for the adaptive-icon foreground (colour) or monochrome layer, same geometry."""
    ink = "#FF000000" if monochrome else "#FF1B2238"
    acc = "#FF000000" if monochrome else "#FFFF6F59"
    x0, y0, x1, y1 = PHONE
    r = PHONE_R
    outline = (f"M{x0 + r},{y0} H{x1 - r} A{r},{r} 0 0 1 {x1},{y0 + r} V{y1 - r} A{r},{r} 0 0 1 {x1 - r},{y1} "
               f"H{x0 + r} A{r},{r} 0 0 1 {x0},{y1 - r} V{y0 + r} A{r},{r} 0 0 1 {x0 + r},{y0} Z")
    def circle(cx: float, cy: float, rr: float) -> str:
        return f"M{cx - rr},{cy} a{rr},{rr} 0 1 0 {2 * rr},0 a{rr},{rr} 0 1 0 {-2 * rr},0 Z"
    sh = (STAND[3] - STAND[1]) / 2
    stand = (f"M{STAND[0] + sh},{STAND[1]} H{STAND[2] - sh} A{sh},{sh} 0 0 1 {STAND[2] - sh},{STAND[3]} "
             f"H{STAND[0] + sh} A{sh},{sh} 0 0 1 {STAND[0] + sh},{STAND[1]} Z")
    hands = "".join(f'\n        <path android:strokeColor="{acc}" android:strokeWidth="{HAND_W}" android:strokeLineCap="round" '
                    f'android:pathData="M{HUB[0]},{HUB[1]} L{hand_end(a, l)[0]:.2f},{hand_end(a, l)[1]:.2f}"/>' for a, l in HANDS)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Stand Screen launcher icon {"monochrome" if monochrome else "foreground"} (icon/final/make_icon.py; do not edit by hand) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:pivotX="54" android:pivotY="54" android:scaleX="{S}" android:scaleY="{S}" android:translateY="{DY}">
        <path android:strokeColor="{ink}" android:strokeWidth="{STROKE}" android:pathData="{outline}"/>
        <path android:fillColor="{acc}" android:pathData="{circle(*CAM)}"/>
        <path android:fillColor="{acc}" android:pathData="{stand}"/>{hands}
        <path android:fillColor="{acc}" android:pathData="{circle(HUB[0], HUB[1], HUB_R)}"/>
    </group>
</vector>
'''


if __name__ == "__main__":
    (OUT / "icon.svg").write_text(svg(), encoding="utf-8")
    (OUT / "icon-mono.svg").write_text(svg(monochrome=True), encoding="utf-8")
    (OUT / "ic_launcher_foreground.xml").write_text(vector_drawable(False), encoding="utf-8")
    (OUT / "ic_launcher_monochrome.xml").write_text(vector_drawable(True), encoding="utf-8")
    # Play Store icon (512 px) = the part a launcher shows: the middle 72 of the 108-unit canvas
    big = draw(1536)
    big.crop((256, 256, 1280, 1280)).resize((512, 512), Image.LANCZOS).convert("RGB").save(OUT / "icon-512.png")
    big.crop((256, 256, 1280, 1280)).convert("RGB").save(OUT / "icon-1024.png")
    draw(512, monochrome=True).save(OUT / "icon-mono-512.png")
    # sanity: the outline's rounded outer corner and the stand stay inside the 66-unit safe circle
    ro = PHONE_R + STROKE / 2
    corner = math.dist((PHONE[0] + PHONE_R, PHONE[1] + PHONE_R), (C, C)) + ro
    stand = math.dist((STAND[2], STAND[3]), (C, C))
    worst = max(corner, stand) * S + DY
    assert worst <= 33, worst
    print("ok, farthest symbol point from centre:", round(worst, 1), "(safe radius 33)")
