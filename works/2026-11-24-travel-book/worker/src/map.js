// 다닌 길 map (operator 11-28: draw it ourselves from OpenStreetMap, no map service). The app sends a box
// [south, west, north, east] (rounded outward to 0.01°, ~1 km) and a detail flag; Overpass gives coastline, water,
// roads and island names in it; the coast is clipped into land polygons, everything thinned to what a book page can
// show, and the answer cached by box. Only the box leaves the phone: photos and the GPS track stay on it.
// ponytail: public Overpass instances + edge cache; self-hosted PMTiles on R2 when traffic grows (mockups/r23-route/TECH.md)

// public instances are often full (11-28: overpass-api.de 502 "at capacity", kumi and private.coffee timing out):
// each is tried in turn, twice, within ~4 min (the app waits up to 5)
const OVERPASS = ["https://maps.mail.ru/osm/tools/overpass/api/interpreter", "https://overpass-api.de/api/interpreter",
  "https://overpass.kumi.systems/api/interpreter", "https://overpass.private.coffee/api/interpreter"];
const MAX_SPAN = 12, MIN_SPAN = 0.005;
const PLACE_RANK = { city: 4, town: 2.5, suburb: 1.5, village: 1 };
const ROAD_CLASS = { motorway: 1, trunk: 1, primary: 2, secondary: 3, tertiary: 4, unclassified: 5, residential: 5 };

/** The box as the app may send it, rounded outward to 0.01°, or null. */
export function cleanBox(box) {
  if (!Array.isArray(box) || box.length !== 4 || !box.every(v => typeof v === "number" && Number.isFinite(v))) return null;
  const [s, w, n, e] = [Math.floor(box[0] * 100) / 100, Math.floor(box[1] * 100) / 100, Math.ceil(box[2] * 100) / 100, Math.ceil(box[3] * 100) / 100];
  if (s < -85 || n > 85 || w < -180 || e > 180 || n - s < MIN_SPAN || e - w < MIN_SPAN) return null;
  if (n - s > MAX_SPAN || e - w > MAX_SPAN) return null;
  return [s, w, n, e].map(v => +v.toFixed(2));
}

/** Overpass QL: full coastline ways (clipped here), roads/water clipped by Overpass, island names as boxes. */
export function mapQuery([s, w, n, e], detail) {
  const span = Math.max(n - s, e - w), b = `(${s},${w},${n},${e})`;
  const roads = detail ? "motorway|trunk|primary|secondary|tertiary|unclassified|residential"
    : span > 3 ? "motorway|trunk" : span > 0.8 ? "motorway|trunk|primary" : "motorway|trunk|primary|secondary";
  // a wide box keeps only named lakes (thousands of ponds otherwise)
  const water = span > 0.8 && !detail ? `way["natural"="water"]["name"]${b};relation["natural"="water"]["name"]${b};`
    : `way["natural"="water"]${b};relation["natural"="water"]${b};`;
  return `[out:json][timeout:80][maxsize:268435456];` +
    `way["natural"="coastline"]${b}->.c;.c out geom;` +
    `(way["highway"~"^(${roads})$"]${b};${water})->.r;.r out tags geom${b};` +
    `(way["place"~"^(island|islet)$"]["name"]${b};relation["place"="island"]["name"]${b};)->.i;.i out tags bb;` +
    `node["place"~"^(${span > 3 ? "city|town" : "city|town|suburb|village"})$"]["name"]${b}->.p;.p out;`;
}

const key = p => `${p[0].toFixed(7)},${p[1].toFixed(7)}`;

/** Join ways that share end points into longer chains (OSM splits coastlines and lake outlines into many ways). */
export function joinChains(ways) {
  const chains = ways.filter(w => w.length > 1).map(w => [...w]);
  const byStart = new Map(chains.map(c => [key(c[0]), c]));
  const dead = new Set();
  for (const c of chains) {
    if (dead.has(c)) continue;
    for (let g = 0; g < 100000; g++) {
      const k = key(c.at(-1));
      if (k === key(c[0])) break;
      const nx = byStart.get(k);
      if (!nx || nx === c || dead.has(nx)) break;
      dead.add(nx);
      byStart.delete(k);
      c.push(...nx.slice(1)); // nx may already carry the chains after it
    }
  }
  return chains.filter(c => !dead.has(c));
}

function clipSeg(p, q, [s, w, n, e]) {
  let t0 = 0, t1 = 1;
  const dx = q[1] - p[1], dy = q[0] - p[0];
  const P = [-dx, dx, -dy, dy], Q = [p[1] - w, e - p[1], p[0] - s, n - p[0]];
  for (let i = 0; i < 4; i++) {
    if (P[i] === 0) { if (Q[i] < 0) return null; continue; }
    const r = Q[i] / P[i];
    if (P[i] < 0) { if (r > t1) return null; if (r > t0) t0 = r; } else { if (r < t0) return null; if (r < t1) t1 = r; }
  }
  return [t0, t1];
}
const lerp = (p, q, t) => [p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t];

/** The parts of a line inside the box. */
export function clipLine(pts, box) {
  const runs = [];
  let cur = null;
  for (let i = 1; i < pts.length; i++) {
    const c = clipSeg(pts[i - 1], pts[i], box);
    if (!c) { if (cur) runs.push(cur); cur = null; continue; }
    if (!cur) cur = [lerp(pts[i - 1], pts[i], c[0])];
    cur.push(lerp(pts[i - 1], pts[i], c[1]));
    if (c[1] < 1) { runs.push(cur); cur = null; }
  }
  if (cur) runs.push(cur);
  return runs;
}

/** Position on the box edge, 0..4 counter-clockwise from the south-west corner; null when not on the edge. */
function perim([s, w, n, e], [la, lo]) {
  const eps = 1e-9;
  if (Math.abs(la - s) < eps) return (lo - w) / (e - w);
  if (Math.abs(lo - e) < eps) return 1 + (la - s) / (n - s);
  if (Math.abs(la - n) < eps) return 2 + (e - lo) / (e - w);
  if (Math.abs(lo - w) < eps) return 3 + (n - la) / (n - s);
  return null;
}
const corner = ([s, w, n, e], k) => [[s, w], [s, e], [n, e], [n, w]][k % 4];

/**
 * Land polygons from coastline chains. OSM draws coastlines with the land on the left, so a piece that leaves the box
 * continues counter-clockwise along the edge to the next piece that enters it. Closed chains inside are islands.
 * sea = whether there is any coast at all (no coast: the whole box is land, an inland trip).
 */
export function landOf(chains, box) {
  const rings = [], pcs = [];
  for (const c of chains) {
    const closed = key(c[0]) === key(c.at(-1));
    const inside = c.every(p => p[0] >= box[0] && p[0] <= box[2] && p[1] >= box[1] && p[1] <= box[3]);
    if (closed && inside) { rings.push(c); continue; }
    // a closed coast crossing the edge is clipped from a point outside, or its first run would start mid-box
    const out = closed ? c.findIndex(p => !(p[0] >= box[0] && p[0] <= box[2] && p[1] >= box[1] && p[1] <= box[3])) : 0;
    const line = out > 0 ? [...c.slice(out), ...c.slice(1, out + 1)] : c;
    for (const r of clipLine(line, box)) {
      const a = perim(box, r[0]), z = perim(box, r.at(-1));
      if (a !== null && z !== null && r.length > 1) pcs.push({ p: r, a, z, used: false }); // a broken coast is dropped
    }
  }
  for (const st of pcs) {
    if (st.used) continue;
    const ring = [];
    let cur = st;
    for (let g = 0; g < 10000; g++) {
      cur.used = true;
      ring.push(...cur.p);
      let best = null, bd = 9;
      for (const c of pcs) {
        if (c.used && c !== st) continue;
        const d = (c.a - cur.z + 4) % 4;
        if (d < bd) { bd = d; best = c; }
      }
      for (let k = Math.floor(cur.z) + 1; k <= cur.z + bd; k++) ring.push(corner(box, k));
      if (!best || best === st) break;
      cur = best;
    }
    rings.push(ring);
  }
  return { land: rings, sea: rings.length > 0 };
}

/** Drop points closer than tol to the last kept one; 5 decimals (~1 m). Flat [lat, lng, lat, lng, …]. */
export function thin(pts, tol) {
  const out = [];
  let lx = Infinity, ly = Infinity;
  pts.forEach((p, i) => {
    if (i === pts.length - 1 || Math.abs(p[0] - lx) + Math.abs(p[1] - ly) >= tol) {
      out.push(+p[0].toFixed(5), +p[1].toFixed(5));
      lx = p[0]; ly = p[1];
    }
  });
  return out;
}

const extent = pts => {
  let a = Infinity, b = Infinity, c = -Infinity, d = -Infinity;
  for (const p of pts) { a = Math.min(a, p[0]); b = Math.min(b, p[1]); c = Math.max(c, p[0]); d = Math.max(d, p[1]); }
  return Math.max(c - a, d - b);
};

/** Overpass JSON -> {box, sea, land, water, roads: [{c, p}], islands: [{n, b}]} for book.js. */
export function shapeMap(osm, box, detail) {
  const span = Math.max(box[2] - box[0], box[3] - box[1]), tol = span / (detail ? 900 : 600);
  const coast = [], waterWays = [], roads = [], islands = [], roadWays = {}, places = [];
  const pts = g => (g || []).filter(Boolean).map(x => [x.lat, x.lon]);
  for (const el of osm.elements || []) {
    const t = el.tags || {};
    if (el.type === "node" && t.place && t.name) {
      places.push({ n: t["name:ko"] || t.name, lat: +el.lat.toFixed(4), lng: +el.lon.toFixed(4), r: PLACE_RANK[t.place] || 1 });
    } else if (!el.geometry && !el.members && el.bounds) {
      const b = el.bounds;
      islands.push({ n: t["name:ko"] || t.name, b: [b.minlat, b.minlon, b.maxlat, b.maxlon].map(v => +v.toFixed(4)) });
    } else if (el.type === "way" && t.natural === "coastline") coast.push(pts(el.geometry));
    else if (el.type === "way" && t.highway) (roadWays[ROAD_CLASS[t.highway] || 5] ??= []).push(pts(el.geometry)); else if (el.type === "way" && t.natural === "water") waterWays.push(pts(el.geometry));
    else if (el.type === "relation" && t.natural === "water") {
      for (const m of el.members || []) if (m.role !== "inner" && m.geometry) waterWays.push(pts(m.geometry));
    }
  }
  // a road is many short ways: joined first, or the thinning leaves it dashed
  for (const [c, ws] of Object.entries(roadWays)) for (const ch of joinChains(ws)) for (const r of clipLine(ch, box)) roads.push({ c: +c, p: r });
  const { land, sea } = landOf(joinChains(coast), box);
  const water = joinChains(waterWays).filter(r => r.length > 3 && key(r[0]) === key(r.at(-1)) && extent(r) > tol * 4);
  return {
    box, sea,
    land: land.filter(r => extent(r) > tol * 2).map(r => thin(r, tol)),
    water: water.map(r => thin(r, tol)),
    roads: roads.filter(r => extent(r.p) > tol * 2).map(r => ({ c: r.c, p: thin(r.p, tol) })),
    islands: islands.filter(i => i.n).slice(0, 60),
    // biggest first: a wide box keeps its cities, not a thousand villages
    places: places.sort((a, b) => b.r - a.r).slice(0, 1500),
  };
}

/** POST /map {box, detail}: cached by box; Overpass is asked only on a miss. */
export async function handleMap(body, ctx, fetcher = fetch) {
  const box = cleanBox(body?.box), detail = body?.detail === true;
  if (!box) return { status: 400, body: { error: "box" } };
  const cacheKey = new Request(`https://map.cache/v3/${box.join(",")}/${detail ? 1 : 0}`);
  const cache = typeof caches !== "undefined" ? caches.default : null;
  const hit = cache && await cache.match(cacheKey);
  if (hit) return { status: 200, body: await hit.json() };
  const q = mapQuery(box, detail);
  let osm = null;
  const end = Date.now() + 240_000;
  for (let round = 0; round < 2 && !osm; round++) {
    for (const u of OVERPASS) {
      const left = end - Date.now();
      if (left < 5_000) break;
      try {
        const r = await fetcher(u, { method: "POST", body: new URLSearchParams({ data: q }), signal: AbortSignal.timeout(Math.min(90_000, left)),
          headers: { "user-agent": "dot-lab-travel-book/1.0 (book route maps)" } });
        if (r.ok) {
          const j = await r.json();
          if (!j.remark?.includes("error")) { osm = j; break; }
        }
      } catch { /* next instance */ }
    }
  }
  if (!osm) return { status: 200, body: { error: "map" } };
  const out = shapeMap(osm, box, detail);
  if (cache) ctx?.waitUntil?.(cache.put(cacheKey, new Response(JSON.stringify(out), { headers: { "cache-control": "public, max-age=2592000" } })));
  return { status: 200, body: out };
}
