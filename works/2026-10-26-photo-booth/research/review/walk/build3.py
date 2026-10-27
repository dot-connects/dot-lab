"""Walkthrough v2: round-3 screens (celeb-frame layout, move option, sticker outline, MODNet stills, landing motion)."""
import base64, io, os, subprocess
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.join(HERE, "..", "..")
SHOTS = os.path.join(R, "round3-screens")
OUT = os.path.join(HERE, "walkthrough.html")

STEPS = [
    ("보내는 사람", [
        ("video:landing.mp4", "첫 화면 (착! 합체)", "서울·부산 반쪽 사진이 날아와 한 장의 네컷이 돼요. 한 번 재생하고 살짝 흔들리며 멈춰요."),
        ("fixed-02-a-shoot-ready", "화면 전체로 찍기", "자리 표시 없이 카메라 전체. \"친구 자리를 생각해서 한쪽으로 비켜서 찍어 줘\" 알림만 떠요."),
        ("fixed-02-a-shoot-countdown", "셔터 한 번 = 네 컷", "3·2·1이 네 번 이어져요. 컷마다 포즈 문구."),
        ("movable-02-a-toggle-on", "친구가 날 옮길 수 있게?", "올리기 전에 정해요. 기본은 꺼짐(인생네컷 셀럽처럼 고정)."),
        ("fixed-04-a-share", "친구한테 보내기", "내 네 컷 미리보기와 링크 보내기."),
    ]),
    ("받은 친구", [
        ("fixed-07-landing-b", "친구 첫 화면", "보낸 사람 사진이 톡 떨어지며 \"옆자리를 비워 뒀어요\"."),
        ("fixed-08-b-shoot-ready", "셀럽 프레임처럼", "내 카메라가 화면 전체, 보낸 사람은 찍힌 자리에 떠 있고 항상 내 앞에 있어요."),
        ("movable-08-b-dragged", "옮기기 (허용했을 때만)", "보낸 사람을 손가락으로 끌어 옮겨요."),
        ("movable-08-b-pinched", "크기 바꾸기", "두 손가락으로 줄이고 키워요. \"원래 자리\"로 되돌리기."),
        ("fixed-08-b-shoot-review", "둘이 같이 확인", "네 컷을 한 번에 보고, 컷을 눌러 다시 찍어요."),
        ("fixed-09-b-done", "짠, 완성", "결과 위 고정, 아래 탭에서 배경·프레임·필터·문구·테두리."),
        ("fixed-11-b-outline-on", "스티커 테두리", "색 7가지 · 실선/점선/반짝이 · 얇게/보통/두껍게."),
        ("movable-16-a-done", "보낸 사람도 같은 결과", "친구가 옮긴 자리 그대로 보여요."),
    ]),
]

def jpg_uri(path, width):
    im = Image.open(path).convert("RGB")
    h = round(im.height * width / im.width)
    im = im.resize((width, h), Image.LANCZOS)
    b = io.BytesIO(); im.save(b, "JPEG", quality=74, optimize=True, progressive=True)
    return "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode(), h

def mp4_uri(path):
    return "data:video/mp4;base64," + base64.b64encode(open(path, "rb").read()).decode()

def loop_small(src, dst, width, loops=3):
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-stream_loop", str(loops - 1), "-i", src, "-vf", f"scale={width}:-2,fps=24",
                    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "30", "-movflags", "+faststart", "-an", dst], check=True)
    return dst

esc = lambda s: s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
parts, n = [], 0
for section, steps in STEPS:
    cards = []
    for name, title, text in steps:
        n += 1
        if name.startswith("video:"):
            inner = f'<video src="{mp4_uri(os.path.join(HERE, name[6:]))}" autoplay muted loop playsinline></video>'
            tall = ""
        else:
            uri, h = jpg_uri(os.path.join(SHOTS, name + ".png"), 430)
            inner = f'<img src="{uri}" alt="{esc(title)} 화면" loading="lazy">'
            tall = " tall" if h > 932 else ""
        cards.append(f'''<figure class="step">
  <div class="phone{tall}"><div class="scr">{inner}</div></div>
  <figcaption><b><i>{n}</i>{esc(title)}</b><span>{esc(text)}</span></figcaption>
</figure>''')
    parts.append(f'<section><h2>{esc(section)}</h2><div class="row">{"".join(cards)}</div></section>')

moving = loop_small(os.path.join(R, "round3-compose", "moving-preset-glitter-moved.mp4"), os.path.join(HERE, "moving3.mp4"), 240, 3)
strip_fixed, _ = jpg_uri(os.path.join(SHOTS, "fixed-strip-b-outline.jpg"), 300)
strip_moved, _ = jpg_uri(os.path.join(SHOTS, "movable-strip-b-outline.jpg"), 300)
cut_cmp, _ = jpg_uri(os.path.join(R, "cutout-hq", "cam-a-cut1.png"), 1200)
outline_sheet, _ = jpg_uri(os.path.join(R, "round3-compose", "outline-booth-purple.png"), 1200)

html = open(os.path.join(HERE, "template3.html"), encoding="utf-8").read()
for k, v in {"{{SECTIONS}}": "\n".join(parts), "{{MOVING}}": mp4_uri(moving), "{{STRIP_FIXED}}": strip_fixed,
             "{{STRIP_MOVED}}": strip_moved, "{{CUTOUT}}": cut_cmp, "{{OUTLINES}}": outline_sheet}.items():
    html = html.replace(k, v)
open(OUT, "w", encoding="utf-8").write(html)
print(OUT, round(len(html) / 1e6, 2), "MB", n, "steps")
