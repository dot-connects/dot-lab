"""Build edit-ui.html from src.html + the real theme-b fragment (CSS, pages, photos, subset fonts)."""
import base64
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
R5 = HERE.parent / "mock" / "r5"
sys.path.insert(0, str(R5))
import fonts  # noqa: E402
import preview  # noqa: E402

src = (HERE / "src.html").read_text(encoding="utf-8")
b = (R5 / "b.html").read_text(encoding="utf-8")
style = re.search(r"<style>(.*?)</style>", b, re.S).group(1)
section = re.search(r"<section.*?</section>", b, re.S).group(0)
keys = sorted(f.stem for f in (HERE.parent / "mock" / "img5").glob("*.webp"))
imgs = ",".join(f'"{k}":"data:image/webp;base64,{base64.b64encode(preview._img(k)).decode()}"' for k in keys)
# keep {{k}} out of src= so the browser does not request it; JS fills src from IMG
section = re.sub(r'src="\{\{(\w+)\}\}"', r'src="\1"', section)
text = src + section
want = {"Wanted Sans": {500, 700, 800}, "Pretendard": {400, 500, 600, 700, 800}, "SUIT": {500, 700, 800}}
faces = "<style>" + fonts.faces_css(want, text) + "</style>"
out = (src.replace("<!--FONTS-->", faces).replace("<!--BSTYLE-->", style).replace("<!--BSECTION-->", section)
       .replace("<script>\nconst ICON", "<script>const IMG={" + imgs + "};</script>\n<script>\nconst ICON", 1))
(HERE / "edit-ui.html").write_text(out, encoding="utf-8")
print("bytes", len(out.encode()))
