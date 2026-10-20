"""Korean peninsula mainland mask for the 'draw Korea' sketch.

Takes a land_mask.py bitmap of lon 124..131, lat 33..43.2, keeps the land connected to Seoul, and cuts China/Russia
off along an approximate Yalu-Tumen line. Jeju and other islands drop out (not connected to Seoul).
Usage: python peninsula.py mask.b64 W H > peninsula.b64
"""
import base64
import sys
from collections import deque

W, H = int(sys.argv[2]), int(sys.argv[3])
LON0, LON1, LAT0, LAT1 = 124.0, 131.0, 33.0, 43.2
raw = base64.b64decode(open(sys.argv[1]).read())
land = [(raw[k >> 3] >> (k & 7)) & 1 for k in range(W * H)]
# ponytail: border as a 5-point polyline (Yalu -> Tumen); good to a few km, enough for a drawing game.
BORDER = [(124.3, 39.8), (126.5, 41.4), (128.2, 41.9), (129.7, 42.4), (130.7, 42.3), (131.0, 42.3)]


def border_lat(lon: float) -> float:
    for (x1, y1), (x2, y2) in zip(BORDER, BORDER[1:]):
        if x1 <= lon <= x2:
            return y1 + (lon - x1) / (x2 - x1) * (y2 - y1)
    return BORDER[0][1] if lon < BORDER[0][0] else BORDER[-1][1]


def cell(lat: float, lon: float) -> tuple[int, int]:
    return int((LAT1 - lat) / (LAT1 - LAT0) * H), int((lon - LON0) / (LON1 - LON0) * W)


for r in range(H):
    for c in range(W):
        lon = LON0 + (c + 0.5) / W * (LON1 - LON0)
        lat = LAT1 - (r + 0.5) / H * (LAT1 - LAT0)
        if lat > border_lat(lon):
            land[r * W + c] = 0
keep = [0] * (W * H)
r0, c0 = cell(37.57, 126.98)
q = deque([(r0, c0)])
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
