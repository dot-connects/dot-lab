"""Cut bundled coastline data down to one trip -> map/out/trip-<key>.json (input for mapkit.js drawMap).

In the app this step runs once per book (or the data ships pre-tiled per 1-degree cell); here it runs per fake trip.
  uv run --no-project --with shapely --with pyshp python prep.py [keys...]
Sources: Natural Earth 10m (land + minor islands, lakes, rivers; public domain) and
OSM simplified land polygons (osmdata.openstreetmap.de, ODbL, needs "(c) OpenStreetMap contributors").
"""
import json, math, statistics, sys, zipfile
from pathlib import Path
import shapefile
from shapely.geometry import box, mapping, shape, Polygon, MultiPolygon
from shapely.ops import unary_union
from trips import TRIPS, photos

HERE = Path(__file__).parent
D, OUT = HERE / "data", HERE / "out"
_cache: dict = {}

def ne(name: str) -> list:
    if name not in _cache:
        fc = json.loads((D / f"ne_10m_{name}.geojson").read_text(encoding="utf-8"))
        _cache[name] = [(shape(f["geometry"]), f["properties"]) for f in fc["features"]]
    return _cache[name]

def osm(bb: tuple) -> list:
    if "osm" not in _cache:
        _cache["osm"] = shapefile.Reader(str(D / "osm-simplified-3857.zip"))
    r = _cache["osm"]
    m = lambda lon, lat: (lon * 20037508.34 / 180, math.log(math.tan((90 + lat) * math.pi / 360)) * 6378137)
    x0, y0 = m(bb[0], bb[1]); x1, y1 = m(bb[2], bb[3])
    out = []
    for s in r.iterShapes(bbox=(x0, y0, x1, y1)):  # pyshp skips non-overlapping records via their header bbox
        g = shape(s.__geo_interface__)
        inv = lambda x, y: (x * 180 / 20037508.34, math.degrees(2 * math.atan(math.exp(y / 6378137)) - math.pi / 2))
        from shapely.ops import transform
        out.append(transform(lambda xs, ys, z=None: tuple(zip(*[inv(a, b) for a, b in zip(xs, ys)])), g))
    return out

def core_bbox(ph: list) -> tuple:
    """Same rule as mapkit.js: drop a first/last stop with <=2 photos that sits far from the rest (airport, home)."""
    names = list(dict.fromkeys(p["place"] for p in ph))
    lat0, lon0 = statistics.median(p["lat"] for p in ph), statistics.median(p["lon"] for p in ph)
    km = lambda p: math.hypot((p["lat"] - lat0) * 111, (p["lon"] - lon0) * 111 * math.cos(math.radians(lat0)))
    ds = sorted(km(p) for p in ph); R = max(ds[int(len(ds) * .75)], 1)
    drop = {n for n in (names[0], names[-1]) if sum(p["place"] == n for p in ph) <= 2
            and min(km(p) for p in ph if p["place"] == n) > 3 * R}
    core = [p for p in ph if p["place"] not in drop]
    return (min(p["lon"] for p in core), min(p["lat"] for p in core), max(p["lon"] for p in core), max(p["lat"] for p in core))

def clip(geoms, bb, tol, polys=True) -> dict:
    b = box(*bb); feats = []
    for g in geoms:
        if not g.intersects(b): continue
        c = g.intersection(b).simplify(tol, preserve_topology=True)
        if c.is_empty: continue
        if polys:
            for p in (c.geoms if hasattr(c, "geoms") else [c]):
                if isinstance(p, Polygon) and p.area > tol * tol * 4:
                    feats.append(p)
        else:
            feats.append(c)
    rnd = lambda o: json.loads(json.dumps(mapping(o)), parse_float=lambda s: round(float(s), 5))
    return {"type": "FeatureCollection", "features": [{"type": "Feature", "properties": {}, "geometry": rnd(f)} for f in feats]}

def smooth(g, d: float):
    """Opening then closing: drops piers/breakwaters and fills channels narrower than ~2 px on the page."""
    return g.buffer(-d).buffer(2 * d).buffer(-d)

def prep(key: str) -> None:
    ph = photos(key)
    x0, y0, x1, y1 = core_bbox(ph)
    span = max(x1 - x0, y1 - y0, 0.02)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    h = span * 2.2  # room for whole islands + page-ratio padding
    bb = (cx - h, cy - h, cx + h, cy + h)
    tol = span / 900
    land_ne = unary_union([g for g, _ in ne("land") + ne("minor_islands") if g.intersects(box(*bb))])
    wide_bb = (cx - 6, cy - 4.5, cx + 6, cy + 4.5)
    trip = {"title": TRIPS[key][0], "photos": ph,
            "land": clip([land_ne], bb, tol),
            "landDetail": clip([smooth(unary_union(osm(bb)).intersection(box(*bb)).simplify(tol / 2), span / 130)], bb, tol),
            "lakes": clip([g for g, _ in ne("lakes")], bb, tol),
            "rivers": clip([g for g, p in ne("rivers_lake_centerlines") if g.intersects(box(*bb))], bb, tol, polys=False),
            "wide": clip([unary_union([g for g, _ in ne("land") + ne("minor_islands") if g.intersects(box(*wide_bb))])], wide_bb, 0.01)}
    OUT.mkdir(exist_ok=True)
    p = OUT / f"trip-{key}.json"
    p.write_text(json.dumps(trip, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    sizes = {k: len(json.dumps(v, separators=(",", ":"))) // 1024 for k, v in trip.items() if isinstance(v, dict)}
    print(key, p.stat().st_size // 1024, "KB", sizes, flush=True)

if __name__ == "__main__":
    for k in sys.argv[1:] or TRIPS: prep(k)
