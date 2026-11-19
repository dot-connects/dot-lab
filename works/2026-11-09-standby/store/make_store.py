"""Build the Play store graphics for Stand Screen from the app's own Roborazzi shots.

Shots come from the default-theme clock/screens matrices and the StoreShots class (every layout and theme):
    gradlew :standby:testPublicDebugUnitTest --tests kr.dot.sb.board.StoreShots -Proborazzi.test.record=true

    python store/make_store.py --mock DIR     every candidate as DIR/cNN.webp + DIR/index.html (pick page)
    python store/make_store.py c03 c07 ...    the picked candidates, in order, as store/shot-1.png ...
Always writes store/icon-512.png and store/feature-1024x500.png. Shots are 1920x1080 (under Play's 2:1 limit).
"""
import html
import os
import shutil
import sys
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.dirname(HERE)
SHOTS = os.path.join(WORK, "app", "standby", "build", "shots")
BG, FG, MUTED, ACC = (14, 15, 18), (236, 234, 231), (155, 154, 160), (255, 170, 92)
BOLD = r"C:\Windows\Fonts\malgunbd.ttf"
REG = r"C:\Windows\Fonts\malgun.ttf"

# id: (shot under build/shots, headline, what it shows)
CANDIDATES = {
    "c01": ("iphone/clock/digital-day-bar-land.png", "충전기에 꽂으면 큰 탁상시계로", "디지털 한 칸"),
    "c02": ("iphone/store/s03-big2-clock-agenda-weather-bar-land.png", "시계 옆에 일정과 날씨", "큰 칸 + 작은 칸 2 · 겹친 카드"),
    "c03": ("iphone/store/s05-four-weather-calendar-timer-apps-bar-land.png", "네 칸에 날씨 · 달력 · 타이머 · 앱", "네 칸"),
    "c04": ("iphone/store/s04-two2-dday-timer-music-bar-land.png", "D-day · 집중 타이머 · 음악", "작은 칸 2 + 큰 칸"),
    "c05": ("iphone/store/s06-three-memories-calendar-sleep-bar-land.png", "추억 사진 · 달력 · 수면 소리", "세 칸"),
    "c06": ("iphone/store/s02-two-analog-weather-bar-land.png", "아날로그 시계와 날씨", "두 칸"),
    "c07": ("iphone/store/s14-four-notif-bar-land.png", "새 알림도 한눈에", "네 칸 · 알림 카드"),
    "c08": ("iphone/store/s16-four-alarm-apps-bar-land.png", "다음 알람과 자주 쓰는 앱", "네 칸 · LED 시계"),
    "c09": ("iphone/store/s11-two-night-sleep-bar-land.png", "밤에는 어둡게, 수면 소리와 함께", "밤 화면"),
    "c10": ("daypaper/store/s07-two-memories-flip-bar-land.png", "N년 전 오늘의 사진", "종이 테마 · 플립 시계"),
    "c11": ("daypaper/store/s15-two-hangul-agenda-bar-land.png", "한글 시계와 오늘 일정", "종이 테마"),
    "c12": ("neon/store/s08-big2-neon-bar-land.png", "네온", "네온 테마"),
    "c13": ("sky/store/s09-four-sky-bar-land.png", "하늘", "하늘 테마 · 네 칸"),
    "c14": ("radio/store/s10-three-radio-bar-land.png", "라디오", "라디오 테마 · 세 칸"),
    "c15": ("terminal/store/s12-big2-terminal-bar-land.png", "터미널", "터미널 테마"),
    "c16": ("moodlight/store/s13-two2-moodlight-bar-land.png", "무드등", "무드등 테마"),
    "c17": ("iphone/clock/photos_time-day-bar-land.png", "내 사진 위에 시간", "사진 위 시간"),
    "c18": ("iphone/clock/flip-night-bar-land.png", "플립 시계", "플립 · 밤"),
    "c19": ("iphone/clock/hangul-night-bar-land.png", "시계 20가지 넘게", "한글 시계 · 밤"),
    "c20": ("iphone/clock/relief-date-bar-land.png", "입체 시계", "입체 시계"),
    "c21": ("iphone/clock/led-night-bar-land.png", "LED 시계", "LED · 밤"),
    "c22": ("iphone/screens/alarm-bar-land.png", "알람이 울리면 큰 화면으로", "알람 화면"),
}
# theme names for the 7-theme headline candidates
THEME_HEAD = {"c12": "테마 7가지 — 네온", "c13": "테마 7가지 — 하늘", "c14": "테마 7가지 — 라디오",
              "c15": "테마 7가지 — 터미널", "c16": "테마 7가지 — 무드등"}


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def shot(src: str, title: str) -> Image.Image:
    W, H, top, pad = 1920, 1080, 190, 80
    c = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(c)
    f = font(BOLD, 64)
    d.text(((W - d.textlength(title, font=f)) / 2, 62), title, font=f, fill=FG)
    im = Image.open(os.path.join(SHOTS, src)).convert("RGB")
    w = W - pad * 2
    h = round(im.height * w / im.width)
    im = im.resize((w, h), Image.LANCZOS)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w, h), radius=36, fill=255)
    c.paste(im, (pad, top + (H - top - h - 40) // 2 + 10), mask)
    return c


def feature(out: str) -> None:
    W, H = 1024, 500
    c = Image.new("RGB", (W, H), BG)
    icon = Image.open(os.path.join(WORK, "icon", "final", "icon-512.png")).convert("RGB").resize((260, 260), Image.LANCZOS)
    mask = Image.new("L", (260, 260), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 260, 260), radius=58, fill=255)
    c.paste(icon, (90, 120), mask)
    d = ImageDraw.Draw(c)
    d.text((400, 150), "Stand Screen", font=font(BOLD, 72), fill=FG)
    d.text((402, 255), "충전할 때 켜지는 탁상시계", font=font(BOLD, 40), fill=ACC)
    d.text((402, 318), "시계 · 날씨 · 일정 · 사진 · 수면 소리", font=font(REG, 30), fill=MUTED)
    c.save(out)


def head(cid: str) -> str:
    return THEME_HEAD.get(cid, CANDIDATES[cid][1])


def mock(out: str) -> None:
    os.makedirs(out, exist_ok=True)
    cards = []
    for cid, (src, _, what) in CANDIDATES.items():
        shot(src, head(cid)).resize((960, 540), Image.LANCZOS).save(os.path.join(out, f"{cid}.webp"), quality=82)
        cards.append((cid, head(cid), what))
    tpl = open(os.path.join(HERE, "pick.html"), encoding="utf-8").read()
    items = "\n".join(
        f'<label class="c" data-id="{c}"><input type="checkbox" value="{c}"><span class="n">{c[1:]}</span>'
        f'<img src="{c}.webp" alt="{html.escape(t)}" loading="lazy"><b>{html.escape(t)}</b><span class="w">{html.escape(w)}</span></label>'
        for c, t, w in cards)
    open(os.path.join(out, "index.html"), "w", encoding="utf-8").write(tpl.replace("<!--CARDS-->", items))


def main() -> None:
    shutil.copy(os.path.join(WORK, "icon", "final", "icon-512.png"), os.path.join(HERE, "icon-512.png"))
    feature(os.path.join(HERE, "feature-1024x500.png"))
    if sys.argv[1:2] == ["--mock"]:
        mock(sys.argv[2]); print("mock", len(CANDIDATES)); return
    picks = sys.argv[1:]
    assert 2 <= len(picks) <= 8 and all(p in CANDIDATES for p in picks), "pick 2-8 candidate ids"
    for f in os.listdir(HERE):
        if f.startswith("shot-") and f.endswith(".png"): os.remove(os.path.join(HERE, f))
    for i, cid in enumerate(picks, 1):
        shot(CANDIDATES[cid][0], head(cid)).save(os.path.join(HERE, f"shot-{i}.png"))
    print("ok", picks)


if __name__ == "__main__":
    main()
