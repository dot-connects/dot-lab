// mapkit.js — travel-book map from photo coordinates. No dependencies; runs in a WebView or Node.
//   drawMap(trip, opts) -> SVG string
// trip: { photos: [{lat, lon, day, t:"HH:MM", place?}], land?, landDetail?, lakes?, rivers?, wide? }
//   land/landDetail/lakes/wide = GeoJSON FeatureCollection of (Multi)Polygon in lon/lat, rivers = (Multi)LineString.
//   prep.py cuts these from Natural Earth 10m (land) and OSM simplified land polygons (landDetail).
// opts: { w=300, h=400, style: "ink"|"paper"|"sea"|"plate"|"mini", labels: "name"|"number"|"none",
//         font, fs (label px), colors: {bg, land, coast, water, route, dot, text} }
(function (root) {
  const STYLES = {
    ink:   { bg: "#f6f2ea", land: "none",    coast: "#2a2723", water: "#2a2723", route: "#2a2723", dot: "#2a2723", text: "#2a2723", coastW: .0026, routeW: .0042 },
    paper: { bg: "#f4efe6", land: "#e2d9c6", coast: "none",    water: "#f4efe6", route: "#a8402a", dot: "#a8402a", text: "#3a332b", coastW: 0,     routeW: .0050 },
    sea:   { bg: "#d9d6cf", land: "#fbf9f4", coast: "none",    water: "#d9d6cf", route: "#1d1b18", dot: "#1d1b18", text: "#1d1b18", coastW: 0,     routeW: .0040 },
    plate: { bg: "#21384f", land: "#2c4762", coast: "#e9eef2", water: "#21384f", route: "#ffffff", dot: "#ffffff", text: "#ffffff", coastW: .0022, routeW: .0042 },
    mini:  { bg: "#f4efe6", land: "#d8cfbc", coast: "none",    water: "#f4efe6", route: "#a8402a", dot: "#a8402a", text: "#3a332b", coastW: 0,     routeW: 0 },
  };
  const DASH = [null, [3.2, 2.2], [0.01, 2.0], [5, 1.8, 0.01, 1.8]]; // per day, in units of route width

  const median = a => { const s = [...a].sort((x, y) => x - y); return s[s.length >> 1]; };
  const esc = s => String(s).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const r1 = v => Math.round(v * 10) / 10;

  // consecutive photos at the same place (or within ~300 m when unnamed) become one stop
  function stopsOf(photos) {
    const out = [];
    for (const p of photos) {
      const s = out[out.length - 1];
      const same = s && s.day === p.day && (p.place ? p.place === s.name
        : Math.hypot(p.lat - s.lat, (p.lon - s.lon) * Math.cos(p.lat * Math.PI / 180)) < .0027);
      if (same) { s.lat += (p.lat - s.lat) / ++s.n; s.lon += (p.lon - s.lon) / s.n; }
      else out.push({ lat: p.lat, lon: p.lon, day: p.day, t: p.t, name: p.place || "", n: 1 });
    }
    out.forEach((s, i) => s.no = i + 1);
    return out;
  }

  // same rule as prep.py core_bbox: a first/last stop with <=2 photos far from the rest (airport, home) goes off-map
  function markOff(stops, photos) {
    const lat0 = median(photos.map(p => p.lat)), lon0 = median(photos.map(p => p.lon)), k = Math.cos(lat0 * Math.PI / 180);
    const km = s => Math.hypot((s.lat - lat0) * 111, (s.lon - lon0) * 111 * k);
    const ds = photos.map(km).sort((a, b) => a - b), R = Math.max(ds[Math.floor(ds.length * .75)], 1);
    for (const s of [stops[0], stops[stops.length - 1]]) if (s.n <= 2 && km(s) > 3 * R && stops.length > 2) s.off = km(s);
  }

  const polys = fc => (fc ? fc.features : []).flatMap(f => f.geometry.type === "Polygon" ? [f.geometry.coordinates]
    : f.geometry.type === "MultiPolygon" ? f.geometry.coordinates : []);
  const lines = fc => (fc ? fc.features : []).flatMap(f => f.geometry.type === "LineString" ? [f.geometry.coordinates]
    : f.geometry.type === "MultiLineString" ? f.geometry.coordinates : []);
  const bboxOf = pts => pts.reduce((b, [x, y]) => [Math.min(b[0], x), Math.min(b[1], y), Math.max(b[2], x), Math.max(b[3], y)], [1e9, 1e9, -1e9, -1e9]);
  function inRing(x, y, ring) {
    let c = false;
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      const [xi, yi] = ring[i], [xj, yj] = ring[j];
      if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c;
    }
    return c;
  }
  const inPoly = (x, y, poly) => inRing(x, y, poly[0]) && !poly.slice(1).some(h => inRing(x, y, h));

  function niceScale(kmPerPx, target) {
    const raw = kmPerPx * target, e = Math.pow(10, Math.floor(Math.log10(raw)));
    const km = [1, 2, 5, 10].map(m => m * e).filter(v => v <= raw).pop() || e;
    return { km, px: km / kmPerPx, text: km >= 1 ? `${km} km` : `${Math.round(km * 1000)} m` };
  }

  function drawMap(trip, o = {}) {
    const W = o.w || 300, H = o.h || 400, style = o.style || "ink";
    const C = Object.assign({}, STYLES[style], o.colors || {});
    const font = o.font || "Pretendard, 'Noto Sans KR', 'Apple SD Gothic Neo', sans-serif";
    const fs = o.fs || Math.max(W * .034, 9), U = W / 300; // U = 1 unit at a 300px-wide map
    const photos = trip.photos.filter(p => p.lat != null && p.lon != null);
    if (!photos.length) return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}"><rect width="${W}" height="${H}" fill="${C.bg}"/></svg>`;
    const stops = stopsOf(photos); markOff(stops, photos);
    const core = stops.filter(s => !s.off);
    const lat0 = median(core.map(s => s.lat)), K = Math.cos(lat0 * Math.PI / 180);
    const P = ([lon, lat]) => [lon * K, -lat]; // equirectangular, fine below ~1000 km

    // ---- view: core stops (or a wide locator box for mini) ----
    let vb = bboxOf(core.map(s => P([s.lon, s.lat])));
    let span = Math.max(vb[2] - vb[0], vb[3] - vb[1]);
    const minSpan = style === "mini" ? Math.max(span * 8, 4) : .016; // ~1.8 km minimum for one-place trips
    const grow = (b, s) => { const cx = (b[0] + b[2]) / 2, cy = (b[1] + b[3]) / 2, hw = Math.max(b[2] - b[0], s) / 2, hh = Math.max(b[3] - b[1], s) / 2; return [cx - hw, cy - hh, cx + hw, cy + hh]; };
    vb = grow(vb, minSpan);
    const useDetail = style !== "mini" && trip.landDetail && trip.landDetail.features.length && span < 1;
    const land = polys(style === "mini" ? trip.wide : useDetail ? trip.landDetail : trip.land);
    if (style !== "mini") {
      // pull whole islands in (Jeju, Udo, Yeongdo) when they hold a stop and are not much bigger than the trip
      const all = bboxOf(land.flatMap(pg => pg[0]).map(P)), eps = 1e-3, area = (vb[2] - vb[0]) * (vb[3] - vb[1]);
      for (const pg of land) {
        const b = bboxOf(pg[0].map(P));
        const edge = b[0] - all[0] < eps || all[2] - b[2] < eps || b[1] - all[1] < eps || all[3] - b[3] < eps;
        const holds = core.some(s => { const [x, y] = P([s.lon, s.lat]); return x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]; });
        if (!edge && holds && (b[2] - b[0]) * (b[3] - b[1]) < area * 5)
          vb = [Math.min(vb[0], b[0]), Math.min(vb[1], b[1]), Math.max(vb[2], b[2]), Math.max(vb[3], b[3])];
      }
    }
    // fit the view into a frame: side room for labels, bottom room for the scale bar / day key
    const M = 6 * U, nDays = new Set(core.map(s => s.day)).size, legendH = fs * (nDays > 1 ? 3.4 : 1.8) + M;
    const [fx0, fy0, fx1, fy1] = style === "mini" ? [W * .05, H * .05, W * .95, H * .95] : [W * .12, H * .08, W * .88, H - legendH - H * .04];
    const cx = (vb[0] + vb[2]) / 2, cy = (vb[1] + vb[3]) / 2, ox = (fx0 + fx1) / 2, oy = (fy0 + fy1) / 2;
    const sc = Math.min((fx1 - fx0) / (vb[2] - vb[0]), (fy1 - fy0) / (vb[3] - vb[1]));
    const X = ll => { const [x, y] = P(ll); return [(x - cx) * sc + ox, (y - cy) * sc + oy]; };
    const kmPerPx = 111 / K / sc;

    const thin = pts => pts.map(X).filter((q, i, a) => { // drop vertices < 1.2 px from the last kept one
      if (i && Math.hypot(q[0] - a.k[0], q[1] - a.k[1]) < 1.2 * U) return false; a.k = q; return true; });
    const ringD = ring => { const q = thin(ring); return q.length < 3 ? "" : "M" + q.map(([x, y]) => `${r1(x)} ${r1(y)}`).join("L") + "Z"; };
    const lineD = ln => "M" + thin(ln).map(([x, y]) => `${r1(x)} ${r1(y)}`).join("L");
    const svg = [`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" font-family="${esc(font)}">`,
      `<rect width="${W}" height="${H}" fill="${C.bg}"/>`];

    // inland check: sample a grid — if (almost) every sample is land, the page itself is the land (no fill, no coast)
    let landHits = 0;
    const inv = (px, py) => [((px - ox) / sc + cx) / K, -((py - oy) / sc + cy)];
    for (let i = 0; i < 12; i++) for (let j = 0; j < 12; j++) {
      const [lon, lat] = inv((i + .5) * W / 12, (j + .5) * H / 12);
      if (land.some(pg => inPoly(lon, lat, pg))) landHits++;
    }
    const inland = landHits >= 143;
    if (inland && C.land !== "none") svg.push(`<rect width="${W}" height="${H}" fill="${C.land}"/>`), C.bg = C.land; // the page is the land
    if (!inland && land.length) {
      const d = land.map(pg => pg.map(ringD).join("")).join("");
      svg.push(`<path d="${d}" fill="${C.land}" fill-rule="evenodd" stroke="${C.coast}" stroke-width="${r1(C.coastW * W * 10) / 10}" stroke-linejoin="round"/>`);
    }
    if (style !== "mini") {
      const lk = polys(trip.lakes).map(pg => pg.map(ringD).join("")).join("");
      if (lk) svg.push(`<path d="${lk}" fill="${C.water === C.bg ? C.bg : "none"}" stroke="${C.coast === "none" ? "none" : C.water}" stroke-width="${.5 * U}"/>`);
      const rv = W * kmPerPx < 80 ? "" : lines(trip.rivers) // 1:10M rivers are wrong at city scale.map(lineD).join("");
      if (rv) svg.push(`<path d="${rv}" fill="none" stroke="${style === "paper" ? C.bg : C.water}" stroke-opacity="${style === "paper" ? 1 : .55}" stroke-width="${(style === "paper" ? 2.4 : .8) * U}" stroke-linejoin="round" stroke-linecap="round"/>`);
    }

    if (style === "mini") { // locator: one dot (+ ring) at the trip centre, nothing else
      const [x, y] = X([median(core.map(s => s.lon)), median(core.map(s => s.lat))]);
      svg.push(`<circle cx="${r1(x)}" cy="${r1(y)}" r="${r1(W * .05)}" fill="none" stroke="${C.dot}" stroke-width="${r1(W * .008)}"/>`,
        `<circle cx="${r1(x)}" cy="${r1(y)}" r="${r1(W * .016)}" fill="${C.dot}"/>`, "</svg>");
      return svg.join("");
    }

    // ---- markers: stops closer than ~2 dots merge into one marker "3·4" ----
    const rDot = 2.6 * U, mk = [];
    for (const s of core) {
      const [x, y] = X([s.lon, s.lat]);
      const m = mk.find(m => Math.hypot(m.x - x, m.y - y) < rDot * 3.2);
      if (m) { m.ss.push(s); m.x += (x - m.x) / m.ss.length; m.y += (y - m.y) / m.ss.length; }
      else mk.push({ x, y, ss: [s] });
    }
    // ---- routes: one line per day through its stops, Catmull-Rom smoothed, broken at off-map stops ----
    const days = [...new Set(stops.map(s => s.day))], hops = [];
    const hop = .3 * Math.hypot(fx1 - fx0, fy1 - fy0); // a leg longer than this is a ride/flight: hairline, not route
    const rw = C.routeW * W;
    days.forEach((d, di) => {
      const runs = [[]];
      for (const s of stops.filter(s => s.day === d)) {
        if (s.off) { runs.push([]); continue; }
        const m = mk.find(m => m.ss.includes(s)), last = runs[runs.length - 1][runs[runs.length - 1].length - 1];
        if (last && Math.hypot(m.x - last[0], m.y - last[1]) > hop) { hops.push([last, [m.x, m.y]]); runs.push([]); }
        if (!last || last[0] !== m.x || last[1] !== m.y) runs[runs.length - 1].push([m.x, m.y]);
      }
      for (const q of runs.filter(q => q.length > 1)) {
        let dd = `M${r1(q[0][0])} ${r1(q[0][1])}`;
        for (let i = 0; i < q.length - 1; i++) {
          const p0 = q[i - 1] || q[i], p1 = q[i], p2 = q[i + 1], p3 = q[i + 2] || p2;
          const c1 = [p1[0] + (p2[0] - p0[0]) / 9, p1[1] + (p2[1] - p0[1]) / 9], c2 = [p2[0] - (p3[0] - p1[0]) / 9, p2[1] - (p3[1] - p1[1]) / 9];
          dd += `C${r1(c1[0])} ${r1(c1[1])} ${r1(c2[0])} ${r1(c2[1])} ${r1(p2[0])} ${r1(p2[1])}`;
        }
        const da = DASH[di % DASH.length];
        svg.push(`<path d="${dd}" fill="none" stroke="${C.route}" stroke-width="${r1(rw * 10) / 10}" stroke-linecap="round" stroke-linejoin="round"${da ? ` stroke-dasharray="${da.map(v => r1(v * rw * 10) / 10).join(" ")}"` : ""}/>`);
      }
    });
    for (const [a, b] of hops) svg.push(`<path d="M${r1(a[0])} ${r1(a[1])}L${r1(b[0])} ${r1(b[1])}" stroke="${C.route}" stroke-opacity=".45" stroke-width="${r1(.6 * U * 10) / 10}"/>`);
    // dots with a bg halo so lines stop short of them
    for (const m of mk) svg.push(`<circle cx="${r1(m.x)}" cy="${r1(m.y)}" r="${r1(rDot)}" fill="${C.dot}" stroke="${C.bg}" stroke-width="${r1(1.4 * U)}"/>`);

    // ---- labels: greedy, 8 candidate spots, never over another label/dot/edge; falls back name -> number -> none ----
    const taken = mk.map(m => [m.x - rDot, m.y - rDot, m.x + rDot, m.y + rDot]);
    taken.push([0, H - legendH, W * .55, H]); // scale bar / day key corner
    const tw = s => [...s].reduce((a, c) => a + (/[ㄱ-힣぀-ヿ一-鿿]/.test(c) ? .96 : c === " " ? .3 : /[0-9]/.test(c) ? .58 : .6), 0) * fs;
    const free = r => r[0] > M && r[1] > M && r[2] < W - M && r[3] < H - M && !taken.some(t => r[0] < t[2] && r[2] > t[0] && r[1] < t[3] && r[3] > t[1]);
    const label = (x, y, text, dx0) => {
      const w = tw(text), h = fs * 1.05, g = rDot + 2.2 * U;
      const cand = [[g, -h / 2], [-g - w, -h / 2], [g * .7, -g * .7 - h], [g * .7, g * .7], [-w / 2, -g - h], [-w / 2, g], [-g * .7 - w, -g * .7 - h], [-g * .7 - w, g * .7]];
      for (const [dx, dy] of dx0 ? [dx0] : cand) {
        const r = [x + dx, y + dy, x + dx + w, y + dy + h];
        if (free(r)) { taken.push([r[0] - U, r[1] - U, r[2] + U, r[3] + U]); return r; }
      }
      return null;
    };
    const T = [];
    // ---- off-map stops: chevron at the page edge pointing toward them + "김포공항 452 km" ----
    for (const s of stops.filter(s => s.off)) {
      const [tx, ty] = X([s.lon, s.lat]), ang = Math.atan2(ty - H / 2, tx - W / 2);
      const ex = W / 2 - M * 2.2, ey = H / 2 - M * 2.2, t = Math.min(ex / Math.abs(Math.cos(ang) || 1e-9), ey / Math.abs(Math.sin(ang) || 1e-9));
      const x = W / 2 + Math.cos(ang) * t, y = H / 2 + Math.sin(ang) * t, a = 4 * U;
      svg.push(`<path d="M${r1(-a * 3)} 0H0M${r1(-a)} ${r1(-a * .8)}L0 0L${r1(-a)} ${r1(a * .8)}" transform="translate(${r1(x)} ${r1(y)}) rotate(${r1(ang * 180 / Math.PI)})" fill="none" stroke="${C.route}" stroke-width="${r1(rw * 10) / 10}" stroke-linecap="round" stroke-linejoin="round"/>`);
      const text = `${s.no} ${s.name} ${Math.round(s.off)} km`.trim(), w = tw(text);
      const lx = Math.min(Math.max(x - (Math.cos(ang) > .3 ? w + a * 2 : Math.cos(ang) < -.3 ? -a * 2 : w / 2), M), W - M - w);
      const ly = y + (Math.sin(ang) > .3 ? -a * 2 - fs * 1.05 : Math.sin(ang) < -.3 ? a * 3.6 : -fs * .52);
      const r = [lx, ly, lx + w, ly + fs * 1.05]; taken.push(r);
      T.push(`<text x="${r1(r[0])}" y="${r1(r[3] - fs * .2)}" font-size="${r1(fs * .9)}" fill="${C.text}" fill-opacity=".8">${esc(text)}</text>`);
    }

    const mode = o.labels || "name";
    const numTxt = m => m.ss.length > 2 ? `${m.ss[0].no}–${m.ss[m.ss.length - 1].no}` : m.ss.map(s => s.no).join("·");
    const txt = (r, num, name) => T.push(`<text x="${r1(r[0])}" y="${r1(r[3] - fs * .2)}" font-size="${r1(fs)}" fill="${C.text}" stroke="${C.bg}" stroke-width="${r1(2.4 * U)}" stroke-linejoin="round" paint-order="stroke">` +
      `<tspan font-weight="700">${esc(num)}</tspan>${name ? `<tspan dx="${r1(fs * .3)}">${esc(name)}</tspan>` : ""}</text>`);
    if (mode !== "none") for (const m of [...mk].sort((a, b) => b.ss.length - a.ss.length || a.ss[0].no - b.ss[0].no)) {
      const num = numTxt(m), name = m.ss.length === 1 ? m.ss[0].name : "";
      let r = mode === "name" && name && label(m.x, m.y, num + " " + name);
      if (r) { txt(r, num, name); continue; }
      r = label(m.x, m.y, num);
      if (r) txt(r, num, "");
    }

    // ---- scale bar (+ day key when >1 day), bottom-left ----
    const sb = niceScale(kmPerPx, W * .2), by = H - M - fs * .3;
    svg.push(`<path d="M${r1(M)} ${r1(by - 3 * U)}V${r1(by)}H${r1(M + sb.px)}V${r1(by - 3 * U)}" fill="none" stroke="${C.text}" stroke-width="${r1(.8 * U * 10) / 10}"/>`,
      `<text x="${r1(M + sb.px + 3 * U)}" y="${r1(by)}" font-size="${r1(fs * .82)}" fill="${C.text}" stroke="${C.bg}" stroke-width="${r1(2.4 * U)}" stroke-linejoin="round" paint-order="stroke">${sb.text}</text>`);
    const mdays = [...new Set(core.map(s => s.day))];
    if (mdays.length > 1) {
      let x = M; const y = by - fs * 1.5;
      mdays.forEach((d, di) => {
        const da = DASH[days.indexOf(d) % DASH.length], L = 14 * U;
        svg.push(`<path d="M${r1(x)} ${r1(y - fs * .3)}h${r1(L)}" stroke="${C.route}" stroke-width="${r1(rw * 10) / 10}" stroke-linecap="round"${da ? ` stroke-dasharray="${da.map(v => r1(v * rw * 10) / 10).join(" ")}"` : ""}/>`,
          `<text x="${r1(x + L + 3 * U)}" y="${r1(y)}" font-size="${r1(fs * .82)}" fill="${C.text}" stroke="${C.bg}" stroke-width="${r1(2.4 * U)}" stroke-linejoin="round" paint-order="stroke">${d}일</text>`);
        x += L + 3 * U + tw(`${d}일`) * .82 + 8 * U;
      });
    }
    svg.push(...T, "</svg>");
    return svg.join("");
  }

  root.drawMap = drawMap;
  if (typeof module !== "undefined") module.exports = { drawMap };
})(typeof window !== "undefined" ? window : globalThis);
