# Assembles mag.html: injects the jeju.py coast path into the template (map + cover cut-out mask).
from pathlib import Path
from urllib.parse import quote

import jeju

R = Path(__file__).parent
coast, udo = jeju.smooth(jeju.COAST), jeju.smooth(jeju.UDO)
svg = f"<svg xmlns='http://www.w3.org/2000/svg' viewBox='16 9 270 146'><path d='{coast}'/><path d='{udo}'/></svg>"
mask = "data:image/svg+xml," + quote(svg, safe="/:=' ")
t = (R / "mag.tpl.html").read_text(encoding="utf-8")
out = t.replace("@@COAST@@", coast).replace("@@UDO@@", udo).replace("@@MASK@@", mask)
assert "@@" not in out
(R / "mag.html").write_text(out, encoding="utf-8")
print("ok", len(out))
