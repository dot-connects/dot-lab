"""Inline site/index.html + site/js/*.js into one page body for an Artifact preview (out/preview.html).

The Artifact host wraps the file in its own document, so the doctype/html/head/body tags are dropped and every
<script src="js/x.js"> is replaced by the file's contents. Usage: python bundle.py
"""
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
src = (HERE / "site" / "index.html").read_text(encoding="utf-8")


def inline(m: re.Match) -> str:
    js = (HERE / "site" / m.group(1)).read_text(encoding="utf-8")
    return "<script>\n" + js.replace("</script", "<\\/script") + "\n</script>"


page = re.sub(r'<script src="(js/[\w.]+)"></script>', inline, src)
page = re.sub(r"<!doctype html>|</?html[^>]*>|</?head>|</?body>", "", page, flags=re.I)
page = re.sub(r'<meta (charset|name="viewport")[^>]*>\n?', "", page)
(HERE / "out").mkdir(exist_ok=True)
(HERE / "out" / "preview.html").write_text(page.strip() + "\n", encoding="utf-8")
print("out/preview.html", len(page) // 1024, "KB")
