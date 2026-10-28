const LIFE = 7 * 864e5, MAX_FILE = 3 * 1024 * 1024;
const NAMES = [1, 2, 3, 4].flatMap(c => [`${c}-raw.jpg`, `${c}-cut.png`, `${c}-clip`]);
// optional per-side files: B's placement of A when A allowed moving (not required for `done`)
const OPTIONAL = ["layout.json", "pairs.json"], MAX_JSON = 4096;
// either side's face retouch picks (look.json), uploaded with its own photos
const BOTH = ["look.json"];
const typeOk = (name, t) => name.endsWith(".json") ? t === "application/json" : name.endsWith(".jpg") ? t === "image/jpeg" : name.endsWith(".png") ? t === "image/png"
  : t === "video/mp4" || t === "video/webm";
const b64u = a => btoa(String.fromCharCode(...a)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const sha = async s => b64u(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s))));
const TOKEN = /^[A-Za-z0-9_-]{43}$/;
// A's 가로/세로 pick (Round 6 #9): wide = 4 wide cells in a tall strip, tall = 4 tall cells side by side; B follows it
const SHAPES = ["wide", "tall"];
// A's UI theme (필름레일 / 말랑 부스, Round 6): B's whole flow follows it; links made before it read as film
const UIS = ["film", "cute"];
const optsOf = o => ({ movable: o?.movable === true, shape: SHAPES.includes(o?.shape) ? o.shape : "wide", ui: UIS.includes(o?.ui) ? o.ui : "film" });
const num = (v, lo, hi) => typeof v === "number" && Number.isFinite(v) && v >= lo && v <= hi;
// B shoots 8 and keeps 4; each kept cell pairs with one of A's 4 cuts (repeats allowed)
function cleanPairs(bytes) {
  try {
    const a = JSON.parse(new TextDecoder().decode(bytes));
    return Array.isArray(a) && a.length === 4 && a.every(v => Number.isInteger(v) && v >= 0 && v <= 3) ? a : null;
  } catch { return null; }
}
function cleanLook(bytes) {
  try {
    const o = JSON.parse(new TextDecoder().decode(bytes)), lv = v => Number.isInteger(v) && v >= 0 && v <= 2;
    return o && !Array.isArray(o) && lv(o.skin) && lv(o.slim) && lv(o.eye) ? { skin: o.skin, slim: o.slim, eye: o.eye } : null;
  } catch { return null; }
}
function cleanLayout(bytes) {
  try {
    const a = JSON.parse(new TextDecoder().decode(bytes))?.a;
    return a && num(a.x, -1, 1) && num(a.y, -1, 1) && num(a.scale, 0.3, 3) ? { a: { x: a.x, y: a.y, scale: a.scale } } : null;
  } catch { return null; }
}
// B uploads into a staging folder named by its token hash; `done` claims the B slot, so an abandoned upload never blocks it
const bDir = (id, hash) => `s/${id}/b-${hash.slice(0, 16)}`;

export default {
  async fetch(req, env) {
    const allowed = env.ORIGINS.split(",");
    const origin = req.headers.get("Origin");
    const cors = { "Access-Control-Allow-Origin": allowed.includes(origin) ? origin : allowed[0], "Vary": "Origin",
      "Access-Control-Allow-Headers": "Authorization, Content-Type", "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
      "Access-Control-Max-Age": "86400" };
    const json = (status, obj) => new Response(obj === undefined ? null : JSON.stringify(obj),
      { status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" } });
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    const now = env.NOW ?? Date.now();
    const p = new URL(req.url).pathname.split("/").filter(Boolean);
    if (p[0] !== "s") return json(404, { error: "not-found" });

    if (req.method === "POST" && p.length === 1) {
      let token = null, opts = {};
      try { const b = await req.json(); token = b.token; opts = b.opts || {}; } catch {}
      if (!TOKEN.test(token || "")) return json(400, { error: "token" });
      if (opts?.shape !== undefined && !SHAPES.includes(opts.shape)) return json(400, { error: "shape" });
      if (opts?.ui !== undefined && !UIS.includes(opts.ui)) return json(400, { error: "ui" });
      const id = b64u(crypto.getRandomValues(new Uint8Array(16)));
      await env.BUCKET.put(`s/${id}/meta.json`, JSON.stringify({ created: now,
        a: { tokenHash: await sha(token), done: false }, b: { tokenHash: null, done: false }, opts: optsOf(opts) }));
      return json(201, { id, expires: now + LIFE });
    }

    const id = p[1];
    if (!/^[A-Za-z0-9_-]{22}$/.test(id || "")) return json(404, { error: "not-found" });
    const key = `s/${id}/meta.json`;
    const load = async () => { const o = await env.BUCKET.get(key); return o && { meta: await o.json(), etag: o.etag }; };
    const store = (meta, etag) => env.BUCKET.put(key, JSON.stringify(meta), { onlyIf: { etagMatches: etag } });
    let cur = await load();
    if (!cur) return json(404, { error: "not-found" });
    if (now - cur.meta.created > LIFE) return json(410, { error: "expired" });
    const meta = cur.meta;

    if (req.method === "GET" && p.length === 2)
      return json(200, { a: { done: meta.a.done }, b: { done: meta.b.done }, expires: meta.created + LIFE, opts: optsOf(meta.opts) });

    // "지금 삭제": A, or the friend whose shots are in the strip, wipes the whole link (staging folders included)
    if (req.method === "DELETE" && p.length === 2) {
      const auth = (req.headers.get("Authorization") || "").replace(/^Bearer /, "");
      if (!TOKEN.test(auth)) return json(401, { error: "token" });
      const h = await sha(auth);
      if (h !== meta.a.tokenHash && !(meta.b.done && h === meta.b.tokenHash)) return json(403, { error: "token" });
      const keys = [];                                     // list everything first, then delete (no paging under deletes)
      let cursor;
      do {
        const page = await env.BUCKET.list({ prefix: `s/${id}/`, cursor });
        keys.push(...page.objects.map(o => o.key));
        cursor = page.truncated ? page.cursor : undefined;
      } while (cursor);
      for (let i = 0; i < keys.length; i += 1000) await env.BUCKET.delete(keys.slice(i, i + 1000));
      return json(204);
    }

    const side = p[2];
    if ((side !== "a" && side !== "b") || p.length !== 4) return json(404, { error: "not-found" });

    if (req.method === "GET") {
      if (!NAMES.includes(p[3]) && !OPTIONAL.includes(p[3]) && !BOTH.includes(p[3])) return json(400, { error: "name" });
      if (side === "b" && !meta.b.done) return json(404, { error: "not-found" });
      const dir = side === "a" ? `s/${id}/a` : bDir(id, meta.b.tokenHash);
      const o = await env.BUCKET.get(`${dir}/${p[3]}`);
      if (!o) return json(404, { error: "not-found" });
      return new Response(o.body, { headers: { ...cors, "Cache-Control": "no-store",
        "Content-Type": o.httpMetadata?.contentType || "application/octet-stream" } });
    }

    const auth = (req.headers.get("Authorization") || "").replace(/^Bearer /, "");
    if (!TOKEN.test(auth)) return json(401, { error: "token" });
    const h = await sha(auth);
    let dir;
    if (side === "a") {
      if (h !== meta.a.tokenHash) return json(403, { error: "token" });
      if (meta.a.done) return p[3] === "done" ? json(204) : json(409, { error: "done" });
      dir = `s/${id}/a`;
    } else {
      if (!meta.a.done) return json(409, { error: "not-ready" });
      if (meta.b.done) return meta.b.tokenHash === h && p[3] === "done" ? json(204) : json(409, { error: meta.b.tokenHash === h ? "done" : "taken" });
      dir = bDir(id, h);
    }

    if (req.method === "POST" && p[3] === "done") {
      const missing = [];
      for (const n of NAMES) if (!(await env.BUCKET.head(`${dir}/${n}`))) missing.push(n);
      if (missing.length) return json(400, { error: "missing", names: missing });
      for (let i = 0; i < 3; i++) {                        // compare-and-set on meta.json
        if (side === "b" && cur.meta.b.done) return cur.meta.b.tokenHash === h ? json(204) : json(409, { error: "taken" });
        if (side === "a") cur.meta.a.done = true; else cur.meta.b = { tokenHash: h, done: true };
        if (await store(cur.meta, cur.etag)) return json(204);
        cur = await load();
        if (!cur) return json(404, { error: "not-found" });
      }
      return json(409, { error: "busy" });
    }

    if (req.method === "PUT") {
      if (!NAMES.includes(p[3]) && !BOTH.includes(p[3]) && !(side === "b" && OPTIONAL.includes(p[3]))) return json(400, { error: "name" });
      const type = (req.headers.get("Content-Type") || "").split(";")[0].trim();
      if (!typeOk(p[3], type)) return json(415, { error: "type" });
      if (+(req.headers.get("Content-Length") || 0) > MAX_FILE) return json(413, { error: "file-too-big" });
      let body = new Uint8Array(await req.arrayBuffer());
      if (body.byteLength === 0) return json(400, { error: "empty" });
      if (body.byteLength > (p[3].endsWith(".json") ? MAX_JSON : MAX_FILE)) return json(413, { error: "file-too-big" });
      if (p[3] === "layout.json") {                        // only when A allowed moving; keep just {a:{x,y,scale}} in range
        if (!meta.opts?.movable) return json(403, { error: "fixed" });
        const lay = cleanLayout(body);
        if (!lay) return json(400, { error: "layout" });
        body = new TextEncoder().encode(JSON.stringify(lay));
      }
      if (p[3] === "look.json") {
        const look = cleanLook(body);
        if (!look) return json(400, { error: "look" });
        body = new TextEncoder().encode(JSON.stringify(look));
      }
      if (p[3] === "pairs.json") {
        const pairs = cleanPairs(body);
        if (!pairs) return json(400, { error: "pairs" });
        body = new TextEncoder().encode(JSON.stringify(pairs));
      }
      await env.BUCKET.put(`${dir}/${p[3]}`, body, { httpMetadata: { contentType: type } });
      return json(204);
    }
    return json(405, { error: "method" });
  },
};
