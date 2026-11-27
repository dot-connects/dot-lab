"""Ninth cut: the opening and the script re-planned (operator 11-18: plan "나. 제 폰에 누가 살거든요",
mockups/reel-open.html), the grid with three "holding something" cells. Captions + app sfx only; the voice (Aoede,
the whole new script in one take) is recorded later and laid in like edit7.py.

python reel/render/edit9.py -> out/reel-rough10.mp4  (cut 10, 11-27: edit8 with the home shots from out-home8 = real phone wallpaper, pet at the app default size; other shots reused from out-reel10 = a copy of out-reel9)

Shots: home 1.6 (the pet already lives there: out-home8 0.5 s on) | the real cat 1.8 (a10, raw footage) |
catch 4.6 | pop 3.0 | dex 2.0 | home 11.9 in one take from the tap on (out-home8 1.6 s on) |
grid 6.4 (out-mont9: baby + teddy, reader + book, poodle + ball; the picture eases in on those three at the end) |
end 3.4 (out-end9). Reuses edit6.py's captions, dex move and sound mix.
"""
import sys
from pathlib import Path

from PIL import Image

import edit6 as e

R, TOP, MID = e.R, e.TOP, e.MID
H0 = 1.6   # where the long home take starts on out-home8's clock (just before the tap on the cloud at 2.2 s)


def cat(t: float) -> Image.Image:   # the real cat looking up (a10, 30 fps JPGs), no app layer
    return e.img(R / "a10" / f"{70 + int(round(t * e.FPS)):04d}.jpg")


def home_open(t: float) -> Image.Image:
    return e.frames("out-home8", .5 + t)


def home(t: float) -> Image.Image:
    scr = e.frames("out-home8", H0 + t)
    if t < .4:   # the dex shrinks into the app's icon, as edit6.home
        from PIL import ImageDraw
        k = e.ease(t / .4); s = 1 + (e.ICON[2] - 1) * k
        w, h = max(1, int(e.W * s)), max(1, int(e.H * s))
        x = int((e.W / 2) * (1 - k) + e.ICON[0] * e.W * k - w / 2); y = int((e.H / 2) * (1 - k) + e.ICON[1] * e.H * k - h / 2)
        m = Image.new("L", (w, h), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), radius=int(min(w, h) * (.02 + .27 * k)), fill=int(255 * (1 - k ** 3)))
        scr = scr.copy(); scr.paste(e.img(R / "out-dex8/shot.png").resize((w, h)), (x, y), m)
    return scr


# 9d (operator 11-18): the photo -> fur-ball changes play 1.2x slower; everything after them moves later by the
# same 0.64 s. P0..P1 = the first pop to the last one settled, on the page's clock.
P0, P1, SLOW = .9, 4.1, 1.2
LATE = (P1 - P0) * (SLOW - 1)


def src(t: float) -> float:   # the cut's clock -> the grid page's clock
    return t if t < P0 else P0 + (t - P0) / SLOW if t < P0 + (P1 - P0) * SLOW else t - LATE


def cut_t(s: float) -> float:   # and back (captions, sounds)
    return s if s < P0 else P0 + (s - P0) * SLOW if s < P1 else s + LATE


SLATE = (46, 57, 68)   # the grid page's background (montage2 ?bg=%232E3944)


def grid(t: float) -> Image.Image:
    # once all six have popped (3.65 s), move in on rows 2-3 (the teddy, the book, the ball): the picture grows 1.25x
    # and slides up until row 2 sits under the caption; row 1 goes dark so the caption has a clear band (9b: it sat on
    # the row 1 faces)
    t = src(t)
    k = e.ease((t - 4.3) / .8)
    a, b = e.frames("out-mont9", int(t * e.FPS) / e.FPS), e.frames("out-mont9", (int(t * e.FPS) + 1) / e.FPS)
    im = Image.blend(a, b, t * e.FPS - int(t * e.FPS)) if P0 < t < P1 else a   # between two source frames while slowed
    f = e.ease((t - 3.9) / .4)   # 9c: row 1 is gone by 4.3 s, before the last caption comes in at 4.4 s
    if f > 0:
        im = im.copy(); im.paste(Image.new("RGB", (e.W, 440), SLATE), (0, 362), Image.new("L", (e.W, 440), int(255 * f)))
    if k <= 0:
        return im
    s = 1 + .25 * k
    out = Image.new("RGB", (e.W, e.H), SLATE)
    big = im.resize((int(e.W * s), int(e.H * s)), Image.BICUBIC)
    out.paste(big, (int(e.W / 2 - e.W * s / 2), int((440 - 810 * 1.25) * k)))
    return out


def dex(t: float) -> Image.Image:
    return e.dex(t * 2.4 / 2.0)   # the same move in 2.0 s


CUTS = [   # (name, s, frame fn, events (folder, source offset s), captions [(text, centre y, in s, out s)])
    ("open", 1.6, home_open, ("out-home8", .5), [("제 폰에 누가 *살거든요*", TOP, .05, 1.6)]),
    ("cat", 1.8, cat, None, [("우리 집 *고양이*예요", MID, .05, 1.8)]),
    ("catch", 4.6, lambda t: e.frames("out-catch7", .2 + t), ("out-catch7", .2), [("폰으로 비추고|*집게*로 집으니까", MID, .1, 3.0)]),
    ("pop", 3.0, lambda t: e.frames("out-pop7", t), ("out-pop7", 0), [("*저렇게* 돼서 나왔어요", TOP, 1.3, 3.0)]),
    ("dex", 2.0, dex, None, [("도감에 *이름*도 올라가요", 262, .15, 2.0)]),
    ("home", 11.9, home, ("out-home8", H0), [("톡 치면 *좋아하고요*", TOP, .45, 2.0), ("*두 번* 치면|자주 쓰는 앱이 나와요", TOP, 2.2, 5.1),
                                              ("휘 튕기면 *통통*", TOP, 5.4, 8.9), ("벽에도 잘 *붙어* 있네요", TOP, 9.3, 11.9)]),
    ("grid", 6.4 + LATE, grid, ("out-mont9-ev", 0), [("강아지든 사람이든|다 되더라고요", 250, .15, cut_t(2.5)), ("머리도 옷도|찍힌 *그때 모습*이라", 250, cut_t(2.6), cut_t(4.3)),
                                                    ("안고 있던 *곰인형*도|같이 와요", 250, cut_t(4.4), 6.4 + LATE)]),
    ("end", 3.4, lambda t: e.frames("out-end9", t), ("out-end9", 0), []),
]


def main() -> None:
    import json
    ev = json.loads((R / "out-mont9/events.json").read_text(encoding="utf-8"))["events"]   # the pops on the slowed clock
    (R / "out-mont9-ev").mkdir(exist_ok=True)
    (R / "out-mont9-ev/events.json").write_text(json.dumps({"events": [[round(cut_t(x[0] / 1000) * 1000), *x[1:]] for x in ev]}), encoding="utf-8")
    e.CUTS[:] = CUTS
    e.OUT = R / "out-reel10"
    e.DST = R.parent.parent / "out" / "reel-rough10.mp4"
    if True:   # cut 10 (11-27): only the home shots change (real phone wallpaper, app-default pet size)
        e.ONLY = {"open", "home"}
    e.main()


if __name__ == "__main__":
    main()
