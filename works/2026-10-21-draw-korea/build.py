"""Build draw-Korea: inject the peninsula mask -> preview.html (Artifact body) + site/index.html.

Usage: python build.py
Mask: python tools/korea_mask.py ne_10m_admin_0_countries.geojson 160 233 > peninsula.b64 (Natural Earth 1:10m).
"""
from pathlib import Path

here = Path(__file__).parent
body = (here / "src.html").read_text(encoding="utf-8")
body = body.replace("__PENINSULA__", (here / "peninsula.b64").read_text().strip())
(here / "preview.html").write_text(body, encoding="utf-8")
end = body.index("</style>") + len("</style>")
head = ('<!doctype html>\n<html lang="ko">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
        # link preview when the page or a challenge link is pasted into KakaoTalk / Threads
        '<meta property="og:title" content="한반도를 그릴 수 있나? · D:OT lab">\n'
        '<meta property="og:description" content="지도 안 보고 손가락으로 그리면 진짜 지도랑 겹쳐서 채점해 준다.">\n'
        '<meta property="og:image" content="https://dot-connects.github.io/dot-lab/works/2026-10-21-draw-korea/out/card.png">\n'
        '<meta property="og:type" content="website">\n<meta name="twitter:card" content="summary_large_image">\n')
out = here / "site" / "index.html"
out.write_text(head + body[:end] + "\n</head>\n<body>\n" + body[end:] + "\n</body>\n</html>\n", encoding="utf-8")
print("built", out)
