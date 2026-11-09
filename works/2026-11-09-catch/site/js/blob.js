// Mask -> blob helpers. A classic script (no import/export): seg-worker.js loads it with importScripts, and
// blob.test.mjs imports it for its side effect. Both read the functions off globalThis. Pure, no DOM.
(function (G) {
  /** Largest 4-connected component of m >= th: {lab (component id per pixel), id, size, x0, y0, x1, y1} or null. */
  function largest(m, w, h, th) {
    const lab = new Int32Array(w * h), st = new Int32Array(w * h);
    let n = 0, best = null;
    for (let s = 0; s < w * h; s++) {
      if (lab[s] || m[s] < th) continue;
      const id = ++n;
      let sp = 0, size = 0, x0 = w, y0 = h, x1 = -1, y1 = -1;
      lab[s] = id; st[sp++] = s;
      while (sp) {
        const i = st[--sp], x = i % w, y = (i - x) / w;
        size++;
        if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
        if (x > 0 && !lab[i - 1] && m[i - 1] >= th) { lab[i - 1] = id; st[sp++] = i - 1; }
        if (x < w - 1 && !lab[i + 1] && m[i + 1] >= th) { lab[i + 1] = id; st[sp++] = i + 1; }
        if (y > 0 && !lab[i - w] && m[i - w] >= th) { lab[i - w] = id; st[sp++] = i - w; }
        if (y < h - 1 && !lab[i + w] && m[i + w] >= th) { lab[i + w] = id; st[sp++] = i + w; }
      }
      if (!best || size > best.size) best = { id, size, x0, y0, x1, y1 };
    }
    return best && Object.assign(best, { lab });
  }

  /**
   * The largest blob of m >= th as a 0..1 box {x, y, w, h, frac} (the mask may be 257 px square on a 4:3 frame, so
   * never pixels), or null when it covers under minFrac of the frame. b.lab/b.id come along for per-blob sums.
   */
  function blobBox(m, w, h, th = 0.5, minFrac = 0.03) {
    const b = largest(m, w, h, th);
    if (!b || b.size < minFrac * w * h) return null;
    return { x: b.x0 / w, y: b.y0 / h, w: (b.x1 - b.x0 + 1) / w, h: (b.y1 - b.y0 + 1) / h, frac: b.size / (w * h), lab: b.lab, id: b.id };
  }

  /** dog or cat for a blob: whichever class has more probability inside it (DeepLab mixes them inside one animal) */
  function kindOf(cat, dog, lab, id) {
    let c = 0, d = 0;
    for (let i = 0; i < lab.length; i++) if (lab[i] === id) { c += cat[i]; d += dog[i]; }
    return c > d ? "cat" : "dog";
  }

  /**
   * Cut-out mask: keep the largest blob, zero the other blobs and anything farther than pad px outside its box
   * (their soft edges would show through the refine levels). Changes m in place, returns it.
   */
  function keepLargest(m, w, h, th = 0.5, pad = 4) {
    const b = largest(m, w, h, th);
    if (!b) return m;
    for (let i = 0; i < w * h; i++) {
      const x = i % w, y = (i - x) / w;
      if ((b.lab[i] && b.lab[i] !== b.id) || x < b.x0 - pad || x > b.x1 + pad || y < b.y0 - pad || y > b.y1 + pad) m[i] = 0;
    }
    return m;
  }

  Object.assign(G, { blobBox, kindOf, keepLargest });
})(globalThis);
