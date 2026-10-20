"""land-110m topojson -> equirectangular land bitmap (base64, 1 bit/cell, row-major from north), drawn with PIL.
Rings that jump across the antimeridian are unwrapped into one continuous ring and drawn at -360/0/+360."""
import base64
import json
import sys

from PIL import Image, ImageDraw

W, H, SS = int(sys.argv[1]), int(sys.argv[2]), 4  # SS = supersampling factor
topo = json.load(open(sys.argv[3], encoding="utf-8"))
sx, sy = topo["transform"]["scale"]
tx, ty = topo["transform"]["translate"]
arcs = []
for arc in topo["arcs"]:
    x = y = 0
    pts = []
    for dx, dy in arc:
        x += dx
        y += dy
        pts.append((x * sx + tx, y * sy + ty))
    arcs.append(pts)

img = Image.new("L", (W * SS, H * SS), 0)
draw = ImageDraw.Draw(img)
for geom in topo["objects"]["land"]["geometries"]:
    polys = geom["arcs"] if geom["type"] == "MultiPolygon" else [geom["arcs"]]
    for poly in polys:
        for ri, r in enumerate(poly):
            pts: list[tuple[float, float]] = []
            for i in r:
                a = arcs[i] if i >= 0 else arcs[~i][::-1]
                pts.extend(a if not pts else a[1:])
            shift, un = 0.0, [pts[0]]
            for (x1, _), (x2, y2) in zip(pts, pts[1:]):
                if x2 - x1 > 180:
                    shift -= 360
                elif x1 - x2 > 180:
                    shift += 360
                un.append((x2 + shift, y2))
            fill = 255 if ri == 0 else 0  # first ring = outer, the rest = holes
            for off in (-360, 0, 360):
                draw.polygon([((x + off + 180) / 360 * W * SS, (90 - y) / 180 * H * SS) for x, y in un], fill=fill)
small = img.resize((W, H), Image.BOX)
bits = bytearray((W * H + 7) // 8)
for k, v in enumerate(small.get_flattened_data()):
    if v >= 128:
        bits[k >> 3] |= 1 << (k & 7)
small.point(lambda v: 255 if v >= 128 else 0).resize((W * 3, H * 3)).save("mask.png")
print(base64.b64encode(bytes(bits)).decode())
