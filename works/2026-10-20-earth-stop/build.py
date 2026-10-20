"""Build the work: inject land masks into src.html -> site/index.html (full page for Pages) + preview.html (Artifact body).

Usage: python build.py <world.b64> <korea.b64>
Masks come from tools/land_mask.py (world: 360 180 land-110m.json; korea: 300 346 land-50m.json 124 130.5 33 39).
"""
import sys
from pathlib import Path

here = Path(__file__).parent
body = (here / "src.html").read_text(encoding="utf-8")
body = body.replace("__WORLD__", Path(sys.argv[1]).read_text().strip()).replace("__KOREA__", Path(sys.argv[2]).read_text().strip())
(here / "preview.html").write_text(body, encoding="utf-8")
head = ('<!doctype html>\n<html lang="ko">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n')
title_end = body.index("</style>") + len("</style>")
page = head + body[:title_end] + "\n</head>\n<body>\n" + body[title_end:] + "\n</body>\n</html>\n"
(here / "site").mkdir(exist_ok=True)
(here / "site" / "index.html").write_text(page, encoding="utf-8")
print("built", len(page), "bytes")

# The brake game (no masks): brake/src.html -> brake/preview.html (Artifact) + site/brake/index.html (Pages).
brake = (here / "brake" / "src.html").read_text(encoding="utf-8")
(here / "brake" / "preview.html").write_text(brake, encoding="utf-8")
end = brake.index("</style>") + len("</style>")
(here / "site" / "brake").mkdir(exist_ok=True)
(here / "site" / "brake" / "index.html").write_text(
    head + brake[:end] + "\n</head>\n<body>\n" + brake[end:] + "\n</body>\n</html>\n", encoding="utf-8")
print("built brake")
