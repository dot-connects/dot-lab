const LIFE = 7 * 864e5, MAX_FILE = 3 * 1024 * 1024;
const NAMES = [1, 2, 3, 4].flatMap(c => [`${c}-raw.jpg`, `${c}-cut.png`, `${c}-clip`]);
const typeOk = (name, t) => name.endsWith(".jpg") ? t === "image/jpeg" : name.endsWith(".png") ? t === "image/png"
  : t === "video/mp4" || t === "video/webm";
const b64u = a => btoa(String.fromCharCode(...a)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const sha = async s => b64u(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s))));
const TOKEN = /^[A-Za-z0-9_-]{43}$/;
// B uploads into a staging folder named by its token hash; `done` claims the B slot, so an abandoned upload never blocks it
const bDir = (id, hash) => `s/${id}/b-${hash.slice(0, 16)}`;

export default {
  async fetch(req, env) {
    const allowed = env.ORIGINS.split(",");
    const origin = req.headers.get("Origin");
    const cors = { "Access-Control-Allow-Origin": allowed.includes(origin) ? origin : allowed[0], "Vary": "Origin",
      "Access-Control-Allow-Headers": "Authorization, Content-Type", "Access-Control-Allow-Methods": "GET, POST, PUT, OPTIONS",
      "Access-Control-Max-Age": "86400" };
    const json = (status, obj) => new Response(obj === undefined ? null : JSON.stringify(obj),
      { status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" } });
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    const now = env.NOW ?? Date.now();
    const p = new URL(req.url).pathname.split("/").filter(Boolean);
    if (p[0] !== "s") return json(404, { error: "not-found" });

    if (req.method === "POST" && p.length === 1) {
      let token = null;
      try { token = (await req.json()).token; } catch {}
      if (!TOKEN.test(token || "")) return json(400, { error: "token" });
      const id = b64u(crypto.getRandomValues(new Uint8Array(16)));
      await env.BUCKET.put(`s/${id}/meta.json`, JSON.stringify({ created: now,
        a: { tokenHash: await sha(token), done: false }, b: { tokenHash: null, done: false } }));
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
      return json(200, { a: { done: meta.a.done }, b: { done: meta.b.done }, expires: meta.created + LIFE });

    const side = p[2];
    if ((side !== "a" && side !== "b") || p.length !== 4) return json(404, { error: "not-found" });

    if (req.method === "GET") {
      if (!NAMES.includes(p[3])) return json(400, { error: "name" });
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
      if (!NAMES.includes(p[3])) return json(400, { error: "name" });
      const type = (req.headers.get("Content-Type") || "").split(";")[0].trim();
      if (!typeOk(p[3], type)) return json(415, { error: "type" });
      const body = new Uint8Array(await req.arrayBuffer());
      if (body.byteLength === 0) return json(400, { error: "empty" });
      if (body.byteLength > MAX_FILE) return json(413, { error: "file-too-big" });
      await env.BUCKET.put(`${dir}/${p[3]}`, body, { httpMetadata: { contentType: type } });
      return json(204);
    }
    return json(405, { error: "method" });
  },
};
