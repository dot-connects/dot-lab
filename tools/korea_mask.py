"""Korean peninsula mainland mask from Natural Earth admin-0 countries (KOR + PRK), so the northern edge follows the
real Yalu-Tumen border instead of peninsula.py's straight cut.

Draws both countries at 4x supersampling, keeps a cell when at least half of it is land, then keeps only the land
connected to Seoul (Jeju and the other islands drop out). Output format matches land_mask.py: base64, 1 bit per cell,
row-major from the north.
Usage: python -I korea_mask.py ne_10m_admin_0_countries.geojson W H > peninsula.b64   (W H = 160 233)
"""
import base64
import json
import sys
from collections import deque

from PIL import Image, ImageDraw

LON0, LON1, LAT0, LAT1 = 124.0, 131.0, 33.0, 43.2
W, H = int(sys.argv[2]), int(sys.argv[3])
SS = 4
geo = json.load(open(sys.argv[1], encoding="utf-8"))
img = Image.new("L", (W * SS, H * SS), 0)
draw = ImageDraw.Draw(img)
for f in geo["features"]:
    if f["properties"].get("ADM0_A3") not in ("KOR", "PRK"):
        continue
    g = f["geometry"]
    for poly in g["coordinates"] if g["type"] == "MultiPolygon" else [g["coordinates"]]:
        for i, ring in enumerate(poly):
            pts = [((lon - LON0) / (LON1 - LON0) * W * SS, (LAT1 - lat) / (LAT1 - LAT0) * H * SS) for lon, lat in ring]
            draw.polygon(pts, fill=0 if i else 255)  # ring 0 = outline, the rest = holes
small = img.resize((W, H), Image.BOX)
land = [1 if v >= 128 else 0 for v in small.get_flattened_data()]
keep = [0] * (W * H)
q = deque([(int((LAT1 - 37.57) / (LAT1 - LAT0) * H), int((126.98 - LON0) / (LON1 - LON0) * W))])
while q:
    r, c = q.popleft()
    if not (0 <= r < H and 0 <= c < W) or keep[r * W + c] or not land[r * W + c]:
        continue
    keep[r * W + c] = 1
    q.extend(((r + 1, c), (r - 1, c), (r, c + 1), (r, c - 1)))
out = bytearray((W * H + 7) // 8)
for k, v in enumerate(keep):
    if v:
        out[k >> 3] |= 1 << (k & 7)
print(f"cells {sum(keep)}", file=sys.stderr)
print(base64.b64encode(bytes(out)).decode())
