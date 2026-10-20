"""Build draw-Korea: inject the peninsula mask -> preview.html (Artifact body) + site/index.html.

Usage: python build.py mock|a|b|c   (mock = result-style switcher for the mockup round)
Mask: tools/land_mask.py 160 233 land-50m.json 124 131 33 43.2 | tools/peninsula.py (copied from the sketches work).
"""
import sys
from pathlib import Path

here = Path(__file__).parent
style = sys.argv[1]
body = (here / "src.html").read_text(encoding="utf-8")
body = (body.replace("__PENINSULA__", (here / "peninsula.b64").read_text().strip())
        .replace("__MOCK__", "true" if style == "mock" else "false")
        .replace("__STYLE__", "a" if style == "mock" else style))
(here / "preview.html").write_text(body, encoding="utf-8")
end = body.index("</style>") + len("</style>")
head = ('<!doctype html>\n<html lang="ko">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n')
out = here / "site" / "index.html"
out.write_text(head + body[:end] + "\n</head>\n<body>\n" + body[end:] + "\n</body>\n</html>\n", encoding="utf-8")
print("built", out)
