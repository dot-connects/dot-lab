"""Pack StandBy renders as WebP data URIs and merge them into the mockup template."""
import base64, io, json, os, sys
from PIL import Image

SHOTS = r"C:\DC\DD\dot-lab\works\2026-11-09-standby\app\standby\build\shots"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = r"C:\DC\DD\dot-lab\works\2026-11-09-standby\settings-r10\mockup.html"
THEMES = "iphone paper moodlight candle flip sky daypaper radio neon terminal".split()
BOARDS = "modular desk music bed exam book video".split()
FACES = "digital analog world solar float desk photos_time".split()
HI, LO = 760, 520
C = "-fold-cover-land"

def uri(path: str, w: int, q: int = 68) -> str:
    im = Image.open(os.path.join(SHOTS, path + ".png")).convert("RGB")
    im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "WEBP", quality=q, method=6)
    return "data:image/webp;base64," + base64.b64encode(buf.getvalue()).decode()

imgs: dict[str, str] = {}
def add(key: str, path: str, w: int) -> None:
    imgs[key] = uri(path, w)

for t in THEMES:
    add(f"{t}/widgets", f"{t}/screens/widgets{C}", HI)
    add(f"{t}/widgets-n", f"{t}/screens/widgets-night{C}", HI)
    add(f"{t}/modular", f"{t}/screens/board-modular{C}", HI)
    add(f"{t}/modular-n", f"{t}/screens/board-modular-night{C}", HI)
    add(f"{t}/modular-L", f"{t}/board/large-modular{C}", HI)
    add(f"{t}/digital", f"{t}/clock/digital-day{C}", HI)
    add(f"{t}/digital-n", f"{t}/clock/digital-night{C}", HI)
    add(f"{t}/photos", f"{t}/screens/photos{C}", LO)
    add(f"{t}/photos-n", f"{t}/screens/photos-night{C}", LO)
    for b in BOARDS[1:]:
        add(f"{t}/{b}", f"{t}/screens/board-{b}{C}", LO)
        add(f"{t}/{b}-n", f"{t}/screens/board-{b}-night{C}", LO)
    for f in FACES[1:]:
        add(f"{t}/f-{f}", f"{t}/clock/{f}-day{C}", LO)
        add(f"{t}/f-{f}-n", f"{t}/clock/{f}-night{C}", LO)
    # other devices, for the device chip (widgets page only)
    add(f"{t}/widgets@inner", f"{t}/screens/widgets-fold-inner", LO)
    add(f"{t}/widgets@bar", f"{t}/screens/widgets-bar-land", LO)
    add(f"{t}/widgets@flip", f"{t}/screens/widgets-flip-cover", LO)
    add(f"{t}/widgets@cover-port", f"{t}/screens/widgets-fold-cover-port", LO)
    add(f"{t}/widgets@bar-port", f"{t}/screens/widgets-bar-port", LO)

t = "iphone"
for k, p in {
    "alarm": f"screens/alarm{C}", "alarm-n": f"screens/alarm-night{C}",
    "notif": f"screens/notif{C}", "notif-n": f"screens/notif-night{C}",
    "notif-hidden": f"screens/notif-hidden{C}", "notif-hidden-n": f"screens/notif-hidden-night{C}",
    "book-inner": "board/preset-book-fold-inner",
}.items():
    add(k, f"{t}/{p}", HI)
for s in "round rect big4 minimal duo".split():
    add(f"an-{s}", f"{t}/clock/analog-{s}-day{C}", HI)
    add(f"an-{s}-n", f"{t}/clock/analog-{s}-night{C}", LO)

js = "const IMG=" + json.dumps(imgs, separators=(",", ":")) + ";"
print(len(imgs), "images,", round(len(js) / 1e6, 2), "MB of data URIs")
if "--pack-only" in sys.argv:
    sys.exit()
tpl = open(os.path.join(HERE, "mockup.src.html"), encoding="utf-8").read()
assert "/*IMAGES*/" in tpl
html = tpl.replace("/*IMAGES*/", js)

# New clock faces: reuse the renderers and CSS of clocks.html as they are (one source for the 15 candidates).
ck = open(os.path.join(os.path.dirname(OUT), "clocks.html"), encoding="utf-8").read()
def cut(text: str, start: str, end: str) -> str:
    i = text.index(start)
    return text[i:text.index(end, i)]
face_css = cut(ck, "/* 780 x 360 dp frame", "</style>")
face_js = cut(ck, "var AL=", "var out=document.getElementById('out')") + cut(ck, "function face(f,n){", "\n")
fonts = cut(ck, '<link rel="stylesheet" href="https://fonts.googleapis.com', ">") + ">"
for mark, val in {
    "/*CLOCK_JS*/": "const NEWC = (function(){" + face_js + "\nreturn {F:F,FAM:FAM,REF:REF,face:face};})();",
    "/*CLOCK_CSS*/": json.dumps(face_css, ensure_ascii=False),
    "<!--CLOCK_FONTS-->": fonts,
}.items():
    assert html.count(mark) == 1, mark
    html = html.replace(mark, val)
open(OUT, "w", encoding="utf-8", newline="\n").write(html)
print("wrote", OUT, round(len(html.encode()) / 1e6, 2), "MB")
