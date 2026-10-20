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
        '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n')
out = here / "site" / "index.html"
out.write_text(head + body[:end] + "\n</head>\n<body>\n" + body[end:] + "\n</body>\n</html>\n", encoding="utf-8")
print("built", out)
