// Book runtime: renders book.json pages with the theme's templates (assets/book/themes/<t>/layouts.html).
// Flutter calls window.renderBook(book); JS talks back through flutter_inappwebview's callHandler.
//
// book = { id, theme, format: "phone"|"a5"|"square", pages: [PageSpec], media: { <shotId>: { src, frames?: [src], video? } } }
// PageSpec = { layout, ids, crop, text }   (lib/core/models.dart)
// book.style = { font?: family, size?: 0.9|1|1.15, cover?: "#rrggbb" }   (다듬기)
(function () {
  const RATIO = { phone: 4 / 3, a5: 1.4142, square: 1 };
  const bridge = (name, data) => window.flutter_inappwebview?.callHandler(name, data);
  let current = null;
  let reader = false, pressed = false; // reader: the book view (taps turn pages); pressed: a long press just fired

  async function loadTheme(theme) {
    document.getElementById("theme").href = `themes/${theme}/theme.css`;
    // theme.css scopes every rule under .t-<theme>; without it the pages render unstyled (emulator run 2026-11-26)
    for (const c of [...document.body.classList]) if (c.startsWith("t-")) document.body.classList.remove(c);
    document.body.classList.add(`t-${theme}`);
    const html = await (await fetch(`themes/${theme}/layouts.html`)).text();
    document.getElementById("tpl").innerHTML = html;
  }

  /** A slot's crop "x% y%" or "x% y% s": where the photo sits in its frame, and how far it is zoomed (s >= 1). */
  function setCrop(el, c) {
    let [x = 50, y = 50, s = 1] = (c || "50% 50%").split(" ").map(parseFloat);
    s = Math.max(1, Math.min(4, s || 1));
    el.dataset.c = s > 1.005 ? `${x.toFixed(1)}% ${y.toFixed(1)}% ${s.toFixed(2)}` : `${x.toFixed(1)}% ${y.toFixed(1)}%`;
    el.style.objectPosition = `${x}% ${y}%`;
    // zoom = a smaller view box of the picture, placed by the same x/y; it stays inside its frame (no transform spill)
    const w = 100 / s, l = x / 100 * (100 - w), t = y / 100 * (100 - w);
    if (s > 1.005) el.style.setProperty("object-view-box", `inset(${t.toFixed(2)}% ${(100 - w - l).toFixed(2)}% ${(100 - w - t).toFixed(2)}% ${l.toFixed(2)}%)`);
    else el.style.removeProperty("object-view-box");
  }

  /**
   * 다닌 길 (operator 11-28: a real map, drawn by us from OpenStreetMap). book.route = route.dart's routeOf + the
   * Worker's /map shapes: {pts, stops: [{lat, lng, n, label, sub}], km, base: {box, sea, land, water, roads, islands},
   * legs: [{n, label, sub, km, pts, map}], pins: [{id, lat, lng}]}. Three looks, the editor picks one per book:
   * a = one quiet map with the line and named stops, b = the photos as round pins where they were taken,
   * c = the whole trip on top + a close-up of the main places with the walks there.
   */
  const MAPC = { sea: "var(--map-sea,#e3eaec)", land: "var(--map-land,#fbfaf6)", coast: "var(--map-coast,#b4bfc3)",
    road: "var(--map-road,#e0d9cc)", ink: "var(--map-ink,#1d2326)", sub: "var(--map-sub,#5f666a)", line: "var(--map-line,#1d2326)",
    pin: "var(--map-pin,#d9553c)", walk: "var(--map-walk,#1f5d8c)" };
  const SVGNS = "http://www.w3.org/2000/svg";
  const sv = (t, a = {}, style) => { const e = document.createElementNS(SVGNS, t); for (const k in a) e.setAttribute(k, a[k]); if (style) e.setAttribute("style", style); return e; };
  const mercY = la => Math.log(Math.tan(Math.PI / 4 + la * Math.PI / 360)) * 180 / Math.PI;

  /** A view that fits [pts] ([lat, lng]) into w x h with room around, never wider than the map's box. */
  function mapView(pts, w, h, box, pad = 1.3, top = 0, bottom = 0) {
    const xs = pts.map(p => p[1]), ys = pts.map(p => mercY(p[0]));
    const x0 = Math.min(...xs), x1 = Math.max(...xs), y0 = Math.min(...ys), y1 = Math.max(...ys);
    const ih = h - top - bottom;
    let k = Math.min(w / Math.max((x1 - x0) * pad, 1e-4), ih / Math.max((y1 - y0) * pad, 1e-4));
    if (box) k = Math.max(k, w / (box[3] - box[1]), h / (mercY(box[2]) - mercY(box[0]))); // stay inside the drawn map
    const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
    const P = (la, lo) => [w / 2 + (lo - cx) * k, top + ih / 2 - (mercY(la) - cy) * k];
    P.kmPx = la => k / (111.32 * Math.cos(la * Math.PI / 180));
    P.w = w; P.h = h;
    return P;
  }

  function flatPath(P, f, closed, tol = 0.7) {
    let d = "", lx = 1e9, ly = 1e9, n = 0;
    for (let i = 0; i < f.length; i += 2) {
      const [x, y] = P(f[i], f[i + 1]);
      if (i && i < f.length - 2 && Math.abs(x - lx) + Math.abs(y - ly) < tol) continue;
      d += (n++ ? "L" : "M") + x.toFixed(1) + "," + y.toFixed(1);
      lx = x; ly = y;
    }
    return n > 1 ? d + (closed ? "Z" : "") : "";
  }

  /** Point in the land polygons (even-odd), for telling a boat from a road. */
  function onLand(m, la, lo) {
    if (!m.sea) return true;
    let inside = false;
    for (const r of m.land) {
      for (let i = 0, j = r.length - 2; i < r.length; j = i, i += 2) {
        if ((r[i] > la) !== (r[j] > la) && lo < (r[j + 1] - r[i + 1]) * (la - r[i]) / (r[j] - r[i]) + r[i + 1]) inside = !inside;
      }
    }
    return inside;
  }

  function baseMap(g, m, P, { roads = 3, coastW = 0.7 } = {}) {
    g.append(sv("rect", { width: P.w, height: P.h }, `fill:${m.sea ? MAPC.sea : MAPC.land}`));
    const land = m.land.map(r => flatPath(P, r, true)).join("");
    if (land) g.append(sv("path", { d: land, "stroke-width": coastW, "stroke-linejoin": "round" }, `fill:${MAPC.land};stroke:${MAPC.coast}`));
    const water = m.water.map(r => flatPath(P, r, true)).join("");
    if (water) g.append(sv("path", { d: water, "stroke-width": coastW * 0.7 }, `fill:${MAPC.sea};stroke:${MAPC.coast}`));
    for (let c = 5; c >= 1; c--) {
      if (c > roads) continue;
      const d = m.roads.filter(r => r.c === c).map(r => flatPath(P, r.p, false, 0.9)).join("");
      if (d) g.append(sv("path", { d, fill: "none", "stroke-width": [0, 1.3, 1.0, 0.8, 0.6, 0.45][c], "stroke-linecap": "round", "stroke-linejoin": "round" }, `stroke:${MAPC.road}`));
    }
  }

  /** The trip's line: on land solid, a long hop over the sea as a dashed arc (a boat). */
  function tripLine(g, m, P, pts, color, w) {
    let solid = "";
    for (let i = 1; i < pts.length; i++) {
      const a = pts[i - 1], b = pts[i], [x0, y0] = P(a[0], a[1]), [x1, y1] = P(b[0], b[1]);
      const long = Math.hypot(x1 - x0, y1 - y0) > 14;
      if (long && !onLand(m, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2)) {
        const mx = (x0 + x1) / 2 + (y1 - y0) * 0.18, my = (y0 + y1) / 2 - (x1 - x0) * 0.18;
        g.append(sv("path", { d: `M${x0.toFixed(1)},${y0.toFixed(1)}Q${mx.toFixed(1)},${my.toFixed(1)} ${x1.toFixed(1)},${y1.toFixed(1)}`, fill: "none",
          "stroke-width": w * 0.8, "stroke-dasharray": `${w * 1.4} ${w * 2.2}`, "stroke-linecap": "round" }, `stroke:${color}`));
        solid += `M${x1.toFixed(1)},${y1.toFixed(1)}`;
      } else solid += (solid ? "L" : "M") + `${x1.toFixed(1)},${y1.toFixed(1)}`.replace(/^/, i === 1 ? `${x0.toFixed(1)},${y0.toFixed(1)}L` : "");
    }
    g.append(sv("path", { d: solid, fill: "none", "stroke-width": w, "stroke-linecap": "round", "stroke-linejoin": "round" }, `stroke:${color}`));
  }

  /** The overview's line: stop to stop in visit order, a gentle curve; over the sea dashed (a boat). */
  function visitLine(g, m, P, r, color, w) {
    const at = Object.fromEntries(r.stops.map(s => [s.n, s]));
    const vs = (r.visits || []).filter(n => at[n]);
    const done = new Set();
    for (let i = 1; i < vs.length; i++) {
      const a = at[vs[i - 1]], b = at[vs[i]], key = [a.n, b.n].sort().join("-"), back = done.has(key);
      done.add(key);
      const [x0, y0] = P(a.lat, a.lng), [x1, y1] = P(b.lat, b.lng), bend = back ? -0.16 : 0.16;
      const mx = (x0 + x1) / 2 + (y1 - y0) * bend, my = (y0 + y1) / 2 - (x1 - x0) * bend;
      let sea = 0;
      for (const t of [0.25, 0.5, 0.75]) {
        const q = 1 - t, la = q * q * a.lat + 2 * q * t * ((a.lat + b.lat) / 2) + t * t * b.lat, lo = q * q * a.lng + 2 * q * t * ((a.lng + b.lng) / 2) + t * t * b.lng;
        if (!onLand(m, la, lo)) sea++;
      }
      g.append(sv("path", { d: `M${x0.toFixed(1)},${y0.toFixed(1)}Q${mx.toFixed(1)},${my.toFixed(1)} ${x1.toFixed(1)},${y1.toFixed(1)}`, fill: "none",
        "stroke-width": w, "stroke-linecap": "round", ...(sea >= 2 ? { "stroke-dasharray": `${w * 1.6} ${w * 2.4}` } : {}) }, `stroke:${color}`));
    }
  }

  /** Text with a paper halo, placed where it does not hit what is already there (taken = boxes). */
  function placeLabel(g, P, x, y, lines, taken, { size = 11.5, anchorGap = 7 } = {}) {
    const wOf = s => [...s].reduce((a, ch) => a + (ch.charCodeAt(0) > 0x2e80 ? 1 : 0.56), 0);
    const tw = Math.max(...lines.map((s, i) => wOf(s) * (i ? size * 0.88 : size))), th = size * (1.25 + (lines.length - 1) * 1.15);
    const tries = [[anchorGap, -size * 0.3, "start"], [-anchorGap, -size * 0.3, "end"], [-tw / 2, -anchorGap - th + size, "start"], [-tw / 2, anchorGap + size * 0.9, "start"]];
    let best = tries[0], bestHit = 1e9;
    for (const t of tries) {
      const left = t[2] === "end" ? x + t[0] - tw : x + t[0], top = y + t[1] - size;
      let hit = (left < 4 || left + tw > P.w - 4 || top < 4 || top + th > P.h - 4) ? 1e6 : 0;
      for (const b of taken) hit += Math.max(0, Math.min(left + tw, b[2]) - Math.max(left, b[0])) * Math.max(0, Math.min(top + th, b[3]) - Math.max(top, b[1]));
      if (hit < bestHit) { bestHit = hit; best = t; }
      if (!hit) break;
    }
    const [dx, dy, anchor] = best, left = anchor === "end" ? x + dx - tw : x + dx;
    taken.push([left, y + dy - size, left + tw, y + dy - size + th]);
    const t = sv("text", { x: anchor === "end" ? x + dx : left, y: y + dy, "text-anchor": anchor === "end" ? "end" : "start", "paint-order": "stroke", "stroke-width": 3, "stroke-linejoin": "round" },
      `fill:${MAPC.ink};stroke:${MAPC.land}`);
    lines.forEach((s, i) => {
      const sp = sv("tspan", { x: anchor === "end" ? x + dx : left, dy: i ? size * 1.15 : 0, "font-size": i ? size * 0.88 : size, "font-weight": i ? 400 : 600 }, i ? `fill:${MAPC.sub}` : "");
      sp.textContent = s;
      t.append(sp);
    });
    g.append(t);
  }

  function scaleBar(g, P, x, y, la) {
    const per = P.kmPx(la), km = [0.1, 0.2, 0.5, 1, 2, 5, 10, 20, 50, 100, 200].find(v => v * per >= P.w * 0.14) || 200, L = km * per;
    g.append(sv("path", { d: `M${x},${y - 3}V${y}H${(x + L).toFixed(1)}V${y - 3}`, fill: "none", "stroke-width": 0.8 }, `stroke:${MAPC.sub}`));
    const t = sv("text", { x: x + L + 4, y: y + 1, "font-size": 9 }, `fill:${MAPC.sub}`);
    t.textContent = `${km} km`;
    g.append(t);
  }

  function numDot(g, x, y, n, r = 7.5) {
    g.append(sv("circle", { cx: x, cy: y, r }, `fill:${MAPC.ink}`));
    const t = sv("text", { x, y: y + r * 0.42, "text-anchor": "middle", "font-size": r * 1.25, "font-weight": 700 }, `fill:${MAPC.land}`);
    t.textContent = n;
    g.append(t);
  }

  function drawMap(svg, book, style) {
    const r = book.route;
    svg.replaceChildren();
    if (!r || !r.base || !r.pts || r.pts.length < 2) return;
    const W = 360, H = Math.round(360 * (RATIO[book.format] || RATIO.phone));
    svg.setAttribute("viewBox", `0 0 ${W} ${H}`);
    svg.setAttribute("preserveAspectRatio", "xMidYMid slice");
    const m = r.base, all = [...r.pts, ...r.stops.map(s => [s.lat, s.lng])], taken = [];
    if (style === "c") {
      // the whole trip above, the close-ups below
      const top = H * 0.17, oh = H * 0.36;
      const ov = sv("svg", { x: 0, y: top, width: W, height: oh, viewBox: `0 0 ${W} ${oh}` });
      const P = mapView(all, W, oh, m.box, 1.35);
      baseMap(ov, m, P, { roads: 2 });
      visitLine(ov, m, P, r, MAPC.walk, 1.4);
      for (const s of r.stops) { const [x, y] = P(s.lat, s.lng); numDot(ov, x, y, s.n, 6.5); }
      svg.append(ov);
      const legs = (r.legs || []).slice(0, 3), gap = 8, pw = (W - 2 * 14 - gap * (legs.length - 1)) / legs.length, ph = H * 0.3, py = top + oh + 12;
      legs.forEach((l, i) => {
        const px = 14 + i * (pw + gap);
        const pn = sv("svg", { x: px, y: py, width: pw, height: ph, viewBox: `0 0 ${pw} ${ph}` });
        const Q = mapView(l.pts.length > 1 ? l.pts : [[l.pts[0][0] - 0.005, l.pts[0][1] - 0.005], [l.pts[0][0] + 0.005, l.pts[0][1] + 0.005]], pw, ph, l.map.box, 1.4);
        baseMap(pn, l.map, Q, { roads: 5, coastW: 0.8 });
        tripLine(pn, l.map, Q, l.pts, MAPC.walk, 1.2);
        if (l.n) numDot(pn, 12, 12, l.n, 7);
        scaleBar(pn, Q, 7, ph - 7, l.pts[0][0]);
        svg.append(pn, sv("rect", { x: px, y: py, width: pw, height: ph, fill: "none", "stroke-width": 0.6 }, `stroke:${MAPC.coast}`));
        const name = sv("text", { x: px, y: py + ph + 15, "font-size": 11, "font-weight": 600 }, `fill:${MAPC.ink}`);
        name.textContent = l.label;
        const sub = sv("text", { x: px, y: py + ph + 29, "font-size": 9.5 }, `fill:${MAPC.sub}`);
        sub.textContent = l.km >= 0.1 ? `${l.sub} · ${l.km} km` : l.sub;
        svg.append(name, sub);
      });
      return;
    }
    const pts = r.stops.map(s => [s.lat, s.lng]);
    const P = mapView(pts.length > 1 ? pts : all, W, H, m.box, style === "b" ? 1.9 : 1.6, H * 0.2, 36);
    baseMap(svg, m, P, { roads: style === "b" ? 2 : 3 });
    // the title sits top-left on the map: keep labels out of it
    taken.push([0, 0, W * 0.72, H * 0.17]);
    const color = style === "b" ? MAPC.pin : MAPC.line;
    visitLine(svg, m, P, r, color, style === "b" ? 1.7 : 1.4);
    const dots = r.stops.map(s => [s, ...P(s.lat, s.lng)]);
    for (const [, x, y] of dots) {
      svg.append(sv("circle", { cx: x, cy: y, r: 3.4, "stroke-width": 1.4 }, `fill:${MAPC.land};stroke:${color}`));
      taken.push([x - 5, y - 5, x + 5, y + 5]);
    }
    if (style === "b") {
      const R = W * 0.085, placed = [];
      for (const [i, p] of (r.pins || []).slice(0, 5).entries()) {
        const src = book.media[p.id]?.src;
        if (!src) continue;
        const [ax, ay] = P(p.lat, p.lng);
        let best = null, bestHit = 1e9;
        for (let k = 0; k < 16; k++) {
          const a = k * Math.PI / 8 + (ay > H / 2 ? Math.PI : 0), d = R * (k < 8 ? 2.2 : 3.2);
          const x = ax + Math.cos(a) * d, y = ay + Math.sin(a) * d;
          let hit = (x < R + 6 || x > W - R - 6 || y < H * 0.2 + R || y > H - R - 22) ? 1e6 : 0;
          for (const q of placed) hit += Math.max(0, 2 * R + 8 - Math.hypot(x - q[0], y - q[1])) * 100;
          for (const [, dx, dy] of dots) hit += Math.max(0, R + 6 - Math.hypot(x - dx, y - dy)) * 10;
          if (hit < bestHit) { bestHit = hit; best = [x, y]; }
          if (!hit) break;
        }
        const [x, y] = best;
        placed.push(best);
        taken.push([x - R - 3, y - R - 3, x + R + 3, y + R + 3]);
        svg.append(sv("path", { d: `M${ax.toFixed(1)},${ay.toFixed(1)}L${x.toFixed(1)},${y.toFixed(1)}`, "stroke-width": 0.8 }, `stroke:${MAPC.ink}`));
        svg.append(sv("circle", { cx: ax, cy: ay, r: 2 }, `fill:${MAPC.ink}`));
        const id = `pin${book.id || ""}${i}${Math.random().toString(36).slice(2, 6)}`;
        const cp = sv("clipPath", { id });
        cp.append(sv("circle", { cx: x, cy: y, r: R }));
        svg.append(cp, sv("circle", { cx: x, cy: y, r: R + 2.5, "stroke-width": 0.6 }, "fill:#fff;stroke:rgba(0,0,0,.14)"));
        svg.append(sv("image", { href: src, x: x - R, y: y - R, width: 2 * R, height: 2 * R, "clip-path": `url(#${id})`, preserveAspectRatio: "xMidYMid slice" }));
      }
      for (const [s, x, y] of dots) placeLabel(svg, P, x, y, [s.label], taken, { size: 11 });
      return;
    }
    for (const [s, x, y] of dots) placeLabel(svg, P, x, y, s.sub ? [s.label, s.sub] : [s.label], taken);
    scaleBar(svg, P, 18, H - 24, r.stops[0].lat);
  }

  function fill(pg, spec, book, n) {
    pg.style.setProperty("--ratio", RATIO[book.format] || RATIO.phone);
    pg.dataset.page = n;
    const route = pg.querySelector("svg[data-route]");
    if (route) drawMap(route, book, route.dataset.route || "a");
    for (const img of pg.querySelectorAll("img[data-slot]")) {
      const i = +img.dataset.slot, id = spec.ids[i], m = book.media[id] || {};
      const frame = img.dataset.frame;
      // only pages near the screen decode their photos: a 50-page book no longer decodes every 1600 px JPEG at open
      img.loading = "lazy";
      img.decoding = "async";
      img.src = frame != null && m.frames ? m.frames[+frame] || m.src : m.src;
      setCrop(img, (spec.crop && spec.crop[i]) || img.dataset.crop);
      if (m.video && frame === "0") {
        // the clip plays in place of its first frame, only while visible; export keeps the poster
        const v = document.createElement("video");
        Object.assign(v, { muted: true, loop: true, playsInline: true, poster: img.src, src: m.video });
        setCrop(v, img.dataset.c);
        v.dataset.clip = "1";
        v.dataset.slot = img.dataset.slot;
        img.replaceWith(v);
      }
    }
    for (const el of pg.querySelectorAll("[data-text]")) el.textContent = spec.text[el.dataset.text] || "";
    const ix = pg.querySelector("[data-index]");
    if (ix && spec.text.index) renderIndex(ix, JSON.parse(spec.text.index));
  }

  const NAMES = ["첫째", "둘째", "셋째", "넷째", "다섯째", "여섯째", "일곱째", "여덟째", "아홉째", "열째"];
  const WD = ["일", "월", "화", "수", "목", "금", "토"];
  function renderIndex(el, days) {
    // two columns, split by day count (mockup: days 1-2 left, 3 right)
    const cols = [document.createElement("div"), document.createElement("div")];
    days.forEach((d, i) => {
      const col = cols[i < Math.ceil(days.length / 2) ? 0 : 1];
      const dt = new Date(d.day + "T00:00:00");
      const head = document.createElement("span");
      head.className = "sm" + (col.childElementCount ? " g" : "");
      head.textContent = `${d.day.slice(5).replace("-", ".")} ${WD[dt.getDay()]} · ${NAMES[i] || i + 1 + "번째"} 날`;
      col.append(head);
      for (const s of d.stops) {
        const row = document.createElement("span");
        row.className = "cp";
        const t = document.createElement("span");
        t.className = "lb";
        t.textContent = s.time;
        row.append(t, s.place || "");
        col.append(row);
      }
    });
    el.replaceChildren(...cols);
  }

  // big display words (cover titles) shrink until they fit the page width
  function fitBig(pg) {
    for (const el of pg.querySelectorAll(".big")) {
      let fs = parseFloat(getComputedStyle(el).fontSize);
      const max = pg.clientWidth * 0.94;
      while ((el.scrollWidth > max || el.scrollWidth > el.clientWidth + 1) && fs > 10) { fs *= 0.92; el.style.fontSize = fs + "px"; }
      el.scrollLeft = 0;
    }
    // a one-line title the person typed longer than the AI's (or in a wider font) shrinks to end inside the page
    for (const el of pg.querySelectorAll('[data-text="title"]')) {
      if (!pg.clientWidth || el.classList.contains("big")) continue; // .big has its own fit above
      // start from the size before any fit (sizeText's, or the theme's), kept on the element
      if (el.dataset.fs0 === undefined) el.dataset.fs0 = el.style.fontSize;
      el.style.fontSize = el.dataset.fs0;
      let fs = parseFloat(getComputedStyle(el).fontSize);
      const pr = pg.getBoundingClientRect(), pad = pr.width * 0.03;
      // one-line titles must end inside the page; wrapping ones may take two lines at most
      // (contenteditable rewrites white-space to keep spaces; text-wrap-mode still says nowrap)
      const cs = getComputedStyle(el), nowrap = cs.whiteSpace === "nowrap" || cs.textWrapMode === "nowrap";
      const out = () => {
        const r = el.getBoundingClientRect();
        if (el.scrollWidth > el.clientWidth + 1) return true; // text wider than its own box (fixed-width grid cells)
        return nowrap ? r.left < pr.left + pad * 0.5 - 1 || r.right > pr.right - pad : r.height > fs * 2.7;
      };
      while (out() && fs > 8) { fs *= 0.94; el.style.fontSize = (fs / pg.clientWidth * 100) + "cqw"; }
      el.scrollLeft = 0;
      pg.scrollLeft = 0; // the caret scrolls an overflow:hidden page sideways
    }
  }

  // ----- 다듬기 style: font for the whole book, size of the small text, the cover's colour -----
  function applyFont(book) {
    const f = book.style && book.style.font, b = document.body.style;
    if (f) { b.setProperty("--f", `"${f}","Pretendard",sans-serif`); b.fontSynthesis = "none"; }
    else { b.removeProperty("--f"); b.fontSynthesis = ""; }
  }

  /** Captions and body text (not headings) times k, kept in cqw so a resize keeps the ratio. */
  function sizeText(pg, k) {
    const w = pg.clientWidth;
    if (!k || k === 1 || !w) return;
    for (const el of pg.querySelectorAll("[data-text]")) {
      const fs = parseFloat(getComputedStyle(el).fontSize);
      if (fs < w * 0.06) el.style.fontSize = (fs * k / w * 100).toFixed(3) + "cqw";
    }
  }

  const lum = h => {
    const n = parseInt(h.slice(1), 16);
    const c = [n >> 16, (n >> 8) & 255, n & 255].map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; });
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
  };
  /** White text when it reads better on the colour than near-black does (WCAG contrast). */
  window.whiteOn = h => 1.05 / (lum(h) + 0.05) >= (lum(h) + 0.05) / (lum("#161616") + 0.05);

  /** Whether a page shows its paper (photos cover less than 85% of it): a full-bleed cover has no colour to pick. */
  function paperShows(pg) {
    const pr = pg.getBoundingClientRect(), area = pr.width * pr.height;
    if (!area) return true;
    let ph = 0;
    for (const im of pg.querySelectorAll("[data-slot]")) { const r = im.getBoundingClientRect(); ph += r.width * r.height; }
    return ph / area < 0.85;
  }

  function coverColor(pg, c) {
    if (!c) return;
    const white = window.whiteOn(c), ink = white ? "#ffffff" : "#161616";
    const soft = white ? "rgba(255,255,255,.78)" : "rgba(22,22,22,.68)";
    for (const v of ["--paper", "--cloth", "--kraft", "--edge"]) pg.style.setProperty(v, c);
    for (const v of ["--ink", "--kink"]) pg.style.setProperty(v, ink);
    for (const v of ["--soft", "--dim"]) pg.style.setProperty(v, soft);
    pg.style.background = c;
    pg.style.color = ink;
  }

  const seen = new IntersectionObserver(es => {
    for (const e of es) for (const v of e.target.querySelectorAll("video[data-clip]")) e.isIntersecting && !document.hidden ? v.play().catch(() => {}) : v.pause();
  }, { threshold: 0.6 });
  // hidden (screen off, app in the background, another screen on top): no clip decodes; back: the visible one plays
  document.addEventListener("visibilitychange", () => {
    for (const v of document.querySelectorAll("video[data-clip]")) v.pause();
    if (!document.hidden) for (const pg of document.querySelectorAll(".pg")) { seen.unobserve(pg); seen.observe(pg); }
  });

  /** The page index at the left edge of what the reader shows now (0 = cover). */
  function shownPage(root) {
    const sheets = root.querySelectorAll(".sheet");
    if (!sheets.length || !root.clientWidth) return 0;
    const sh = sheets[Math.round(root.scrollLeft / root.clientWidth)] || sheets[0];
    const p = sh.querySelector(".pg[data-page]");
    return p ? +p.dataset.page : 0;
  }

  /** The reader: which page(s) are on screen ("표지", "3", "3–4") of how many. */
  function tellPage(root) {
    const sheets = root.querySelectorAll(".sheet");
    const sh = sheets[Math.round(root.scrollLeft / Math.max(1, root.clientWidth))];
    if (!sh) return;
    const ns = [...sh.querySelectorAll(".pg[data-page]")].map(p => +p.dataset.page);
    const label = ns.length === 0 ? "" : ns[0] === 0 ? "표지" : ns.length > 1 ? `${ns[0]}–${ns[ns.length - 1]}` : `${ns[0]}`;
    bridge("page", { label, total: current ? current.pages.length - 1 : 0, at: [...sheets].indexOf(sh) });
  }

  // the reader: a tap on the left or right quarter turns the page, one in the middle shows or hides the bars
  document.addEventListener("click", e => {
    if (!reader || edit) return;
    if (pressed) { pressed = false; return; }
    const root = document.getElementById("book"), x = e.clientX / innerWidth;
    const go = d => root.scrollTo({ left: (Math.round(root.scrollLeft / root.clientWidth) + d) * root.clientWidth, behavior: "smooth" });
    if (x < 0.25) go(-1);
    else if (x > 0.75) go(1);
    else bridge("tapMiddle", {});
  });

  window.renderBook = async function (book, opts = {}) {
    // the old pages stop taking edits now: their events would land on a page that is about to change
    if (window.editMode) window.editMode(null);
    current = book;
    await loadTheme(book.theme);
    document.body.classList.toggle("export", !!opts.exportMode);
    document.body.classList.toggle("tone", !!book.toneMatch);
    applyFont(book);
    // a redraw builds the new pages under the old ones and swaps when they are ready: no blank flash on a font change
    const old = document.getElementById("book");
    const keep = opts.page != null ? opts.page : old.children.length ? shownPage(old) : 0;
    const root = old.cloneNode(false);
    root.style.cssText = "position:absolute;inset:0;visibility:hidden";
    old.after(root);
    // spread: an open book - the cover on the right with the inside cover blank, then 1|2, 3|4 (export: one page)
    const spread = !!opts.spread && !opts.exportMode;
    document.body.classList.toggle("spread", spread);
    let sheet = null;
    book.pages.forEach((spec, n) => {
      const t = document.getElementById(spec.layout);
      if (!t) return;
      if (!spread || n % 2 === 1 || n === 0) {
        sheet = document.createElement("div");
        sheet.className = spread ? "sheet two" : "sheet";
        root.append(sheet);
        if (spread && n === 0) { const blank = document.createElement("div"); blank.className = "pg blank left"; sheet.append(blank); }
      }
      const pg = t.content.firstElementChild.cloneNode(true);
      fill(pg, spec, book, n);
      if (spread) pg.classList.add(n % 2 === 1 ? "left" : "right");
      sheet.append(pg);
      seen.observe(pg);
      let timer = null;
      pg.addEventListener("pointerdown", () => { pressed = false; timer = setTimeout(() => { pressed = true; bridge("longPress", { page: n }); }, 550); });
      for (const ev of ["pointerup", "pointercancel", "pointerleave"]) pg.addEventListener(ev, () => clearTimeout(timer));
    });
    if (spread) { const b0 = root.querySelector(".pg.blank"), p0 = root.querySelector(".pg[data-page]"); if (b0 && p0) b0.style.setProperty("--ratio", p0.style.getPropertyValue("--ratio")); }
    await document.fonts.ready;
    const st = book.style || {};
    const pgs = [...root.querySelectorAll(".pg[data-page]")];
    if (pgs[0]) coverColor(pgs[0], st.cover);
    pgs.slice(1).forEach(pg => sizeText(pg, st.size)); // before the fit: a larger size must still fit
    pgs.forEach(fitBig);
    // land on the same page as before; its photos decode before the swap
    const target = pgs.find(p => +p.dataset.page === keep) || pgs[0];
    const sh = target ? target.closest(".sheet") : null;
    if (opts.page != null) [...root.children].forEach(x => (x.style.display = x === sh ? "" : "none"));
    if (sh) {
      root.scrollLeft = [...root.children].indexOf(sh) * root.clientWidth;
      const imgs = [...sh.querySelectorAll("img")];
      imgs.forEach(i => (i.loading = "eager"));
      await Promise.race([Promise.all(imgs.map(i => i.decode().catch(() => {}))), new Promise(r => setTimeout(r, 700))]);
    }
    old.remove();
    root.style.cssText = "";
    if (sh) root.scrollLeft = [...root.children].indexOf(sh) * root.clientWidth;
    edit = null;
    reader = !!opts.reader && !opts.exportMode;
    if (reader) {
      let t = null;
      root.addEventListener("scroll", () => { clearTimeout(t); t = setTimeout(() => tellPage(root), 140); });
      tellPage(root);
    }
    bridge("rendered", { pages: book.pages.length, coverPaper: pgs[0] ? paperShows(pgs[0]) : false });
  };

  /** Export: show one page alone (Flutter screenshots it), video slots as posters. */
  window.showPage = async function (n) {
    const sheets = document.querySelectorAll(".sheet");
    sheets.forEach((s, i) => (s.style.display = i === n ? "" : "none"));
    for (const v of document.querySelectorAll("video[data-clip]")) v.pause();
    // lazy photos of the shown page must be decoded before Flutter takes the screenshot
    const imgs = sheets[n] ? [...sheets[n].querySelectorAll("img")] : [];
    for (const i of imgs) i.loading = "eager";
    await Promise.all(imgs.map(i => (i.complete ? i.decode() : new Promise(r => { i.onload = i.onerror = r; })).catch(() => {})));
    return new Promise(r => requestAnimationFrame(() => requestAnimationFrame(() => r(true))));
  };

  window.currentBook = () => current;

  // ----- 다듬기 editing on the shown page (Flutter: showPage(n) then editMode) -----
  // "text": the page's words are typed in place -> bridge("text", {page, key, value})
  // "photo": tap a photo to select it -> bridge("slot", {page, slot}); drag the selected one to move what shows
  //          -> bridge("crop", {page, slot, pos})
  let edit = null;

  /** Typing: the page grows around the words being typed so they stay readable above the keyboard. */
  function zoomTo(pg, el) {
    pg.style.transform = "";
    const pr = pg.getBoundingClientRect(), er = el.getBoundingClientRect();
    const vw = innerWidth, vh = innerHeight;
    const k = Math.min(2.6, (vw - 40) / Math.max(er.width, 60), (vh * 0.6) / Math.max(er.height, 20));
    if (k <= 1.05) return;
    const cx = er.left + er.width / 2 - pr.left, cy = er.top + er.height / 2 - pr.top;
    Object.assign(pg.style, { transformOrigin: "0 0", transition: "transform .22s ease", position: "relative", zIndex: 5,
      transform: `translate(${(vw / 2 - pr.left - cx * k).toFixed(1)}px,${(vh / 2 - pr.top - cy * k).toFixed(1)}px) scale(${k.toFixed(3)})` });
  }
  function unzoom(pg) { if (pg) pg.style.transform = ""; }
  // the keyboard opening (or closing) resizes the view: keep the typed words in the middle
  addEventListener("resize", () => {
    const a = document.activeElement;
    if (edit && edit.mode === "text" && a && a.classList && a.classList.contains("editing")) zoomTo(edit.pg, a);
  });

  window.editMode = function (mode, n, slot) {
    const pg = n == null ? null : document.querySelector(`.pg[data-page="${n}"]`);
    for (const el of document.querySelectorAll("[contenteditable]")) { el.removeAttribute("contenteditable"); el.classList.remove("editing"); }
    for (const el of document.querySelectorAll(".selslot")) el.classList.remove("selslot");
    if (edit) unzoom(edit.pg);
    edit = mode && pg ? { mode, n, slot: slot || 0, pg } : null;
    document.body.classList.toggle("photoedit", !!edit && mode === "photo");
    if (!edit) return;
    if (mode === "text") {
      for (const el of pg.querySelectorAll("[data-text]")) {
        if (!el.textContent.trim() || el.closest("[data-index]")) continue;
        el.contentEditable = "plaintext-only";
        el.spellcheck = false;
        el.classList.add("editing");
        el.onfocus = () => zoomTo(pg, el);
        el.onblur = () => unzoom(pg);
        el.oninput = () => {
          if (el.dataset.text === "title") fitBig(pg);
          bridge("text", { page: n, key: el.dataset.text, value: el.textContent.replace(/\s+/g, " ").trim() });
        };
      }
    } else if (mode === "photo") {
      // the photo's frame carries the mark (a ring drawn on the <img> itself sits under the picture)
      const sel = pg.querySelector(`[data-slot="${edit.slot}"]`);
      const box = sel && (sel.parentElement !== pg ? sel.parentElement : sel);
      if (box) {
        if (box !== sel && getComputedStyle(box).position === "static") box.style.position = "relative";
        box.classList.add("selslot");
      }
    }
  };

  // photo edit: one finger moves what shows, two fingers zoom and move; the result goes back once all lift
  const pts = new Map();
  let g = null;
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const mid = ps => ({ x: ps.reduce((t, p) => t + p.x, 0) / ps.length, y: ps.reduce((t, p) => t + p.y, 0) / ps.length });
  const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y) || 1;
  function rebase() {
    const [x = 50, y = 50, s = 1] = (g.im.dataset.c || "50% 50%").split(" ").map(parseFloat);
    Object.assign(g, { x, y, s, start: [...pts.values()].map(p => ({ ...p })) });
  }
  document.addEventListener("pointerdown", e => {
    if (!edit || edit.mode !== "photo") return;
    const im = e.target.closest && e.target.closest("[data-slot]");
    if (!im || !edit.pg.contains(im) || (g && g.im !== im)) return;
    const slot = +im.dataset.slot;
    if (slot !== edit.slot) { edit.slot = slot; window.editMode("photo", edit.n, slot); bridge("slot", { page: edit.n, slot }); }
    e.preventDefault();
    im.setPointerCapture(e.pointerId);
    pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (!g) g = { im, slot, n: edit.n, r: im.getBoundingClientRect(), moved: false, c0: im.dataset.c };
    rebase();
  });
  document.addEventListener("pointermove", e => {
    if (!g || !pts.has(e.pointerId)) return;
    pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const now = [...pts.values()], a = g.start, m0 = mid(a), m1 = mid(now);
    const s = now.length > 1 && a.length > 1 ? clamp(g.s * dist(now[0], now[1]) / dist(a[0], a[1]), 1, 4) : g.s;
    const k = 160 / s; // a zoomed photo moves less per finger pixel
    const x = clamp(g.x - (m1.x - m0.x) / g.r.width * k, 0, 100), y = clamp(g.y - (m1.y - m0.y) / g.r.height * k, 0, 100);
    setCrop(g.im, `${x}% ${y}% ${s}`);
    if (Math.abs(m1.x - m0.x) + Math.abs(m1.y - m0.y) > 3 || s !== g.s) g.moved = true;
  });
  function lift(e) {
    if (!g || !pts.has(e.pointerId)) return;
    pts.delete(e.pointerId);
    if (pts.size) return rebase();
    if (g.moved && g.im.dataset.c !== g.c0) bridge("crop", { page: g.n, slot: g.slot, pos: g.im.dataset.c });
    g = null;
  }
  document.addEventListener("pointerup", lift);
  document.addEventListener("pointercancel", lift);
})();
