"""Build the no-camera walkthrough page from the live-run screenshots (research/review/live-screens)."""
import base64, io, os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(HERE, "..", "live-screens")
OUT = os.path.join(HERE, "walkthrough.html")

STEPS = [
    ("보내는 사람", [
        ("01-landing-a", "첫 화면", "멀리 있어도 한 칸에. 무료·가입 없음, 카메라 허용 안내가 먼저 나와요."),
        ("02-a-shoot-ready", "촬영 준비", "오른쪽 점선이 친구 자리. 좌우반전·앞/뒤 카메라·소리 버튼."),
        ("02-a-shoot-countdown", "셔터 한 번 = 네 컷", "3·2·1 카운트다운이 네 번 이어져요. 컷마다 포즈 문구가 바뀌어요."),
        ("02-a-shoot-snap", "찰칵", "찍힌 순간 사진이 톡 튀어나와요."),
        ("02-a-shoot-review", "맘에 들어?", "네 컷을 한 번에 확인. 썸네일을 누르면 그 컷만 다시 찍어요."),
        ("03-a-upload", "올리는 중", "퍼센트로 올라가요. 링크로만 열리고 7일 뒤 자동 삭제."),
        ("04-a-share", "친구한테 보내기", "내 네 컷 옆에 친구 자리가 비어 있는 미리보기."),
        ("06-a-waiting", "기다리는 중", "같은 링크를 다시 열면 친구가 찍었는지 보여요."),
    ]),
    ("받은 친구", [
        ("07-landing-b", "친구 첫 화면", "링크를 열면 \"친구가 옆자리를 비워 뒀어요\". 바로 찍기."),
        ("08-b-shoot-countdown", "친구 옆에 서기", "보낸 사람이 이미 서 있고, 나는 오려져서 그 옆에 들어가요."),
        ("08-b-shoot-review", "둘이 같이 확인", "따로 찍었는데 한 칸에 같이 나와요."),
        ("09-b-done-reveal", "짠, 완성", "네컷이 인화되듯 한 칸씩 나타나요."),
        ("10-b-done-video", "프레임 고르기", "결과는 위에 고정, 아래 탭에서 틀을 바꿔요. 영상은 그동안 미리 만들어요."),
        ("11-b-tab-bg", "배경", "보낸 사람 방·내 방·부스 배경 중에서 골라요."),
        ("12-b-tab-filter", "필터", "원본·뽀샤시·흑백·따뜻·시원·필름."),
        ("13-b-tab-caption", "한 줄 문구", "D+100, 우리 오늘처럼 틀 아래 글씨를 바꿔요."),
        ("15-b-story-ready", "스토리용 영상", "인스타 스토리 크기(9:16) 영상도 따로 저장해요."),
    ]),
    ("그다음", [
        ("16-a-done", "보낸 사람도 완성본", "같은 링크로 둘 다 사진과 영상을 저장해요."),
        ("17-a-delete-confirm", "지금 삭제", "7일을 기다리지 않고 바로 지울 수 있어요."),
        ("18-a-deleted", "삭제됨", "서버에서 사진·영상이 전부 지워져요."),
        ("19-after-delete-open", "지운 링크", "다시 열면 없는 링크로 나와요."),
        ("20-framed", "블로그 안에서", "카메라가 막히는 곳에서는 새 탭으로 열라고 안내해요."),
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

def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

parts, n = [], 0
for section, steps in STEPS:
    cards = []
    for name, title, text in steps:
        n += 1
        uri, h = jpg_uri(os.path.join(SHOTS, name + ".png"), 430)
        tall = " tall" if h > 932 else ""
        cards.append(f'''<figure class="step">
  <div class="phone{tall}"><div class="scr"><img src="{uri}" alt="{esc(title)} 화면" loading="lazy"></div></div>
  <figcaption><b><i>{n}</i>{esc(title)}</b><span>{esc(text)}</span></figcaption>
</figure>''')
    parts.append(f'<section><h2>{esc(section)}</h2><div class="row">{"".join(cards)}</div></section>')

frames_uri, _ = jpg_uri(os.path.join(HERE, "..", "round2-frames", "frames-dplus100.png"), 1400)
html = open(os.path.join(HERE, "template.html"), encoding="utf-8").read()
html = (html.replace("{{SECTIONS}}", "\n".join(parts))
            .replace("{{FRAMES}}", frames_uri)
            .replace("{{MOVING}}", mp4_uri(os.path.join(HERE, "moving.mp4")))
            .replace("{{STORY}}", mp4_uri(os.path.join(HERE, "story.mp4"))))
open(OUT, "w", encoding="utf-8").write(html)
print(OUT, round(len(html) / 1e6, 2), "MB", n, "steps")
