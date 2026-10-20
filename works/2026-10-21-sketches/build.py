"""Build the sketches page: inject the peninsula mask -> preview.html (Artifact body) + ../../site/sketches/index.html.

Usage: python build.py <peninsula.b64>   (from tools/land_mask.py 160 233 land-50m.json 124 131 33 43.2 | tools/peninsula.py)
"""
import sys
from pathlib import Path

here = Path(__file__).parent
body = (here / "src.html").read_text(encoding="utf-8").replace("__PENINSULA__", Path(sys.argv[1]).read_text().strip())
(here / "preview.html").write_text(body, encoding="utf-8")
end = body.index("</style>") + len("</style>")
head = ('<!doctype html>\n<html lang="ko">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n')
out = here / "site" / "index.html"
out.parent.mkdir(exist_ok=True)
out.write_text(head + body[:end] + "\n</head>\n<body>\n" + body[end:] + "\n</body>\n</html>\n", encoding="utf-8")
print("built", out)
