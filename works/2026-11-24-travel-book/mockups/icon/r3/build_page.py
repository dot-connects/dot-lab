"""Comparison page for the Codex-generated 꺾쇠 icons: masks, sizes, home-screen strips, splash."""
import base64
import io
from pathlib import Path

from PIL import Image

H = Path(__file__).parent
NAMES = {
    "c1": ("1 즉석사진 표지", "폴라로이드 + 책. 표지가 즉석사진처럼 생긴 하드커버, 붉은 천 책등. 파란 바탕."),
    "c2": ("2 넘어가는 사진첩", "폴라로이드 + 책. 즉석사진 장들로 된 두꺼운 작은 책, 표지가 넘어가는 중."),
    "c3": ("3 짐 든 책", "여행자 같은 책. 손잡이·가방끈·짐표를 단 파란 책."),
    "c4": ("4 trip/book", "글자 아이콘. 두 줄 굵은 글자, p 기둥이 k 로 이어져 책등처럼."),
    "c5": ("5 잡지 제호", "여행 잡지 제호 'TRIPBOOK' 에 붉은 해와 초록 산."),
    "c6": ("6 창간호", "붉은 바탕 위 여행 잡지 창간호, 'TRIPBOOK' 제호와 바다 위 해."),
}


def uri(k: str, size: int) -> str:
    im = Image.open(H / f"{k}.png").convert("RGB")
    w = im.width; m = w * 18 // 108
    im = im.crop((m, m, w - m, w - m)).resize((size, size), Image.LANCZOS)
    b = io.BytesIO()
    im.save(b, "JPEG", quality=88)
    return "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode()


cards = []
for base, (title, desc) in NAMES.items():
  for v in "ab":
    k = f"{base}_{v}"; title2 = f"{title} · {v.upper()}"
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

splash = uri("c1_a", 256)
page = f'''<title>여행 한 권 아이콘 3차 — 여섯 안</title>
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
<h1>여행 한 권 — 아이콘 3차, 여섯 안</h1>
<p class="lead">Fable 이 실제 앱 아이콘 24개를 보고 말씀하신 네 방향(폴라로이드+책, 여행자 같은 책, tripbook 글자, 여행 잡지 제호)으로 정한 여섯 안을 Codex 가 안마다 두 장씩 그렸어요. 폰이 실제로 보여 주는 가운데 부분만 잘라 놓았어요. 원형·둥근 사각, 큰·작은 크기, 밝은·어두운 바탕화면에 놓았어요.</p>
<div class="grid">{"".join(cards)}</div>
<div class="splash"><div class="phone"><img src="{splash}" alt=""><b>「여행 한 권」</b><small>TRIPBOOK</small></div>
<p class="lead" style="margin:0">앱을 열 때 화면(1 기준). 고르신 안으로 바꿔 넣어요.</p></div>
'''
(H / "icons.html").write_text(page, encoding="utf-8")
print(len(page) // 1024, "KB")
