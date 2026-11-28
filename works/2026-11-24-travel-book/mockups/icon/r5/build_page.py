"""Round 5 comparison page (app-icon skill) (Codex image generation): masks, sizes, home-screen strips, splash."""
import base64
import io
from pathlib import Path

from PIL import Image

H = Path(__file__).parent
NAMES = {
    "now": ("지금 아이콘", "4차에서 고른 선반 위 노을 책. 책이 그림의 절반 정도라 폰에선 작아요."),
    "lean": ("1 벽에 기댄 한 권", "앱 진열장처럼 나무 선반에 기대 세운 책, 벽에 그림자."),
    "face": ("2 정면 한 권 크게", "선반 위 책을 정면에서 크게. 표지 사진이 거의 전부."),
    "pair": ("3 두 권", "앞에 노을 표지 한 권, 뒤에 먹색 책등 한 권."),
    "open": ("4 펼쳐 세운 책", "V자로 펼쳐 세운 책, 왼쪽 노을, 오른쪽 파란 바다."),
}


def uri(k: str, size: int) -> str:
    im = Image.open(H / "../r4/shelf_a.jpg" if k == "now_a" else H / f"{k}.png").convert("RGB")
    w = im.width; m = 0  # the whole render becomes the visible 72 dp; the outer ring is filled with its paper colour
    im = im.crop((m, m, w - m, w - m)).resize((size, size), Image.LANCZOS)
    b = io.BytesIO()
    im.save(b, "JPEG", quality=88)
    return "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode()


cards = []
for base, (title, desc) in NAMES.items():
  for v in ("a",) if base == "now" else "ab":
    k = f"{base}_{v}"; title2 = title if base == "now" else f"{title} · {v.upper()}"
    big, small = uri(k, 384), uri(k, 144)
    strip = lambda cls: (f'<div class="strip {cls}">' + "".join(
        f'<div class="app"><i class="g"></i><span>앱</span></div>' if j != 2 else
        f'<div class="app"><img class="sq" src="{small}" alt=""><span>여행 한 권</span></div>' for j in range(5)) + "</div>")
    cards.append(f'''<article class="card" id="{k.replace('_','-')}">
  <h3>{title2}</h3><p>{desc}</p>
  <div class="masks">
      <img class="ci" src="{big}" alt="" style="width:96px;height:96px">
      <img class="sq" src="{big}" alt="" style="width:96px;height:96px">
      <img class="ci" src="{small}" alt="" style="width:48px;height:48px">
      <img class="sq" src="{small}" alt="" style="width:48px;height:48px">
  </div>
  {strip("light")}{strip("dark")}
</article>''')

splash = None
page = f'''<title>여행 한 권 아이콘 5차</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+KR:wght@400;600;700&display=swap">
<style>
:root{{--bg:#F4EFE7;--fg:#2B2621;--soft:#7D7366;--card:#FBF8F3;--line:#E2DACD}}
@media (prefers-color-scheme: dark){{:root:not([data-theme="light"]){{--bg:#1B1A18;--fg:#ECE8E1;--soft:#A39C92;--card:#24221F;--line:#3A3631;color-scheme:dark}}}}
:root[data-theme="dark"]{{--bg:#1B1A18;--fg:#ECE8E1;--soft:#A39C92;--card:#24221F;--line:#3A3631;color-scheme:dark}}
body{{background:var(--bg);color:var(--fg);font-family:"IBM Plex Sans KR",sans-serif;word-break:keep-all;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px));padding-block:24px 48px}}
h1{{font-size:26px;margin:0 0 6px}} .lead{{color:var(--soft);margin:0 0 22px;max-width:62ch;line-height:1.55}}
.grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:16px}}
.card{{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:16px;min-width:0}}
.card h3{{margin:0 0 4px;font-size:16px}} .card p{{margin:0 0 12px;color:var(--soft);font-size:13px;line-height:1.5}}
.masks{{display:flex;align-items:center;gap:14px;flex-wrap:wrap;margin-bottom:12px}}
.ci{{border-radius:50%}} .sq{{border-radius:22%}}
.strip{{display:flex;justify-content:space-around;padding:12px 6px 8px;border-radius:12px;margin-top:8px}}
.strip.light{{background:linear-gradient(120deg,#dfe6ee,#efe5d8)}} .strip.dark{{background:linear-gradient(120deg,#1d1f2a,#2b2533)}}
.app{{display:grid;justify-items:center;gap:4px;font-size:10.5px}} .light .app{{color:#2B2621}} .dark .app{{color:#f2f0ea}}
.app .g{{width:40px;height:40px;border-radius:22%;background:#9aa0a8;opacity:.75}} .app img{{width:40px;height:40px}}
.splash{{margin-top:22px;display:flex;gap:18px;align-items:center;flex-wrap:wrap}}
.phone{{width:180px;height:360px;border-radius:24px;background:#F4EFE7;border:1px solid var(--line);display:grid;place-items:center;align-content:center;gap:10px;color:#2B2621}}
.phone img{{width:84px;height:84px;border-radius:22%}} .phone b{{font-size:15px}} .phone small{{font-size:10px;letter-spacing:.2em;color:#7D7366}}
</style>
<h1>여행 한 권 — 아이콘 5차</h1>
<p class="lead">4차의 방향(앱의 나무 선반, 노을 바다 표지)은 그대로 두고, 폰 홈 화면에서 잘 보이게 다시 그렸어요. 책을 크게, 표지 사진은 큰 덩어리 몇 개로 단순하게, 원형으로 잘려도 책 전체가 보이게 가운데 모았어요. 맨 앞은 지금 아이콘이에요. 각 안을 원형·둥근 사각, 큰 크기·실제 크기, 밝은·어두운 바탕화면에 놓았어요.</p>
<div class="grid">{"".join(cards)}</div>
'''
(H / "icons.html").write_text(page, encoding="utf-8")
print(len(page) // 1024, "KB")
