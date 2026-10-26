# 따로 같이 네컷 (#003) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A phone web work where A shoots four cuts (each with the 2 s before the shutter), shares one link, B opens it and
shoots beside A's cut-out; once both are done each saves a 4-cut image and a moving 4-cut video with a chosen background.

**Architecture:** Static client on GitHub Pages (`site/`, ES modules, no build) does all camera, cut-out (MediaPipe
selfie_segmenter) and compositing on the phone. A small Cloudflare Worker (`worker/`) only issues link ids and write
tokens, enforces size caps and the A-then-B order, and proxies R2 reads/writes; R2 lifecycle deletes everything after 7
days. In-app browsers shoot; saving happens in Chrome (in-app saves were measured to fail).

**Tech Stack:** vanilla JS (ES modules), Canvas 2D, MediaRecorder, `@mediapipe/tasks-vision@0.10.14` (jsDelivr),
Cloudflare Workers + R2 (wrangler 4), Node 24 `node:test` for Worker tests, headless Chrome + CDP for runs.

**Spec:** `works/2026-10-26-photo-booth/brief.md` (read first). Spike with measured code: `spike/index.html`.

## Global Constraints

- Claude creates no accounts and posts nothing. Cloudflare account + `wrangler login`: operator, via
  `scripts/wizard-cloudflare.bat`. No secret, token or account credential in the repo.
- Every CDN URL pinned: `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14`, model
  `https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite`.
- Link lifetime 7 days (R2 lifecycle + Worker check). Caps: 3 MB per file, 30 MB per link. Only the camera can make a
  photo (no file upload, so nobody composites a stranger's picture).
- Camera: never crop the frame (stage aspect = video aspect), widest zoom on start, mirror is a toggle (front on, back off),
  front/back switch, pinch zoom + tap focus only where `getCapabilities()` offers them. Everything saved = what the
  preview showed.
- In-app (Threads/Instagram/KakaoTalk UA): shooting stays in-app; every save button becomes "크롬에서 열어 저장하기"
  (`kakaotalk://web/openExternal?url=` / `intent://…#Intent;scheme=https;package=com.android.chrome;end`). Camera
  request with no answer in 5 s → same Chrome button.
- In-app touch fixes from `dot-lab/README.md` ("Touch input inside Threads / Instagram") on every element that reads
  drags or pinches.
- UI copy Korean, short, from the user's side. No score.
- Tests only where wrong is expensive (global CLAUDE.md): the Worker's state machine and boundary checks. Layout,
  colours and UI glue are verified by running (headless Chrome 430 px + CDP fake camera, then the operator's phone).

## File map

```
works/2026-10-26-photo-booth/
  mockups/app/index.html      Task 1  three app-screen directions, live camera (served by Pages: Artifacts cannot open a camera)
  worker/src/index.js         Task 2  routes + state machine
  worker/test/worker.test.js  Task 2  node:test with an in-memory R2 fake
  worker/wrangler.toml        Task 3
  site/index.html             Task 4+ screens (landing, shoot, upload, share/wait, done, expired)
  site/js/env.js              Task 4  in-app detection, Chrome hand-off URL
  site/js/camera.js           Task 4  startCam / flip / mirror / zoom / focus (from spike)
  site/js/segment.js          Task 4  loadSegmenter, cutFrame(video, mirror) -> canvas with alpha
  site/js/capture.js          Task 4  shootFour(): countdown, 2 s pre-roll clips (raw + green key), stills (jpg + png)
  site/js/compose.js          Task 5  drawStrip(), recordMovingStrip() with background / frame choice, chroma key
  site/js/api.js              Task 6  Worker client
  site/js/app.js              Task 6  screen flow, roles A/B from ?s=<id>
  manifest.json               Task 7
```

---

### Task 1: App-screen mockups with a live camera (operator picks)

**Files:** Create `mockups/app/index.html` (single file, inline CSS/JS, reuses spike camera + segment code).

Gate: nothing after Task 2 starts until the operator picks. Rule: look at real references first (memory
`feedback_look_at_real_references_first`) — the 50 Naver 4-cut photos in `scratchpad/ref` and the promo mockup.

- [ ] **Step 1:** Build three directions on one page, switchable by tabs, each with two scenes driven by the real camera:
  shooting (live cut-out, A's "친구 자리" dashed spot or B's view with A standing, 3-2-1, 1/4, thumbnails, mirror/flip
  buttons) and done (strip preview + background chips + frame chips + two save buttons).
  - A "부스": dark curtain booth, big countdown, flash-white at shutter.
  - B "다이어리": light paper, hand-drawn stickers, rounded.
  - C "필름": film-camera viewfinder, date stamp in orange LED digits.
- [ ] **Step 2:** Draw 8 preset backgrounds as Canvas functions (no images): 보라 부스, 분홍 부스, 흰 벽, 흑백,
  하늘, 노을, 별밤, 체크 커튼. Shown as chips in every direction.
- [ ] **Step 3:** Run headless Chrome 430 px with `--use-fake-device-for-media-stream` (driver
  `scratchpad/drive.mjs` pattern), screenshot each direction at dpr 2, inspect crops at full size, measure text contrast
  (≥ 4.5:1 body).
- [ ] **Step 4:** Commit, push, wait for Pages, send the operator
  `https://dot-connects.github.io/dot-lab/works/2026-10-26-photo-booth/mockups/app/` — ask which direction and which
  backgrounds. Record the answer in `brief.md` "시안" section.

### Task 2: Worker state machine (TDD)

**Files:** Create `worker/src/index.js`, `worker/test/worker.test.js`, `worker/package.json` (`{"type":"module"}`).

**Interfaces — Produces (used by `site/js/api.js`):**
```
POST /s                         -> 201 {id, token, expires}                        (A)
POST /s/:id/join                -> 201 {token} | 409 {error:"taken"|"not-ready"}   (B)
PUT  /s/:id/:side/:name   (Authorization: Bearer <token>) body=file -> 204
POST /s/:id/:side/done    (Authorization)  -> 204 | 400 {error:"missing", names:[...]}
GET  /s/:id                     -> 200 {a:{done}, b:{joined, done}, expires} | 404 | 410
GET  /s/:id/:side/:name         -> 200 file (readable by anyone holding the id, once uploaded) | 404 | 410
side = "a" | "b";  name = `${cut}-${kind}`, cut 1..4, kind in raw.jpg | cut.png | raw.mp4 | key.mp4
```
Storage layout in R2: `s/<id>/meta.json`, `s/<id>/<side>/<name>`. meta = `{created, a:{tokenHash, done}, b:{tokenHash|null, done}, bytes}`.
Token = 32 random bytes base64url; only its SHA-256 is stored. id = 16 random bytes base64url (22 chars).

- [ ] **Step 1: Write the failing tests**

```js
// worker/test/worker.test.js
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

function fakeR2() {
  const m = new Map();
  return {
    m,
    async get(k) { const v = m.get(k); return v && { body: v.body, size: v.body.byteLength,
      httpMetadata: v.meta, async json() { return JSON.parse(new TextDecoder().decode(v.body)); } }; },
    async put(k, body, opt = {}) { const b = typeof body === "string" ? new TextEncoder().encode(body)
      : new Uint8Array(await new Response(body).arrayBuffer()); m.set(k, { body: b, meta: opt.httpMetadata }); },
    async head(k) { return m.has(k) ? { size: m.get(k).body.byteLength } : null; },
  };
}
const env = () => ({ BUCKET: fakeR2(), ORIGIN: "https://dot-connects.github.io", NOW: undefined });
const call = (e, method, path, { token, body } = {}) => worker.fetch(new Request("https://w.test" + path,
  { method, body, headers: token ? { Authorization: "Bearer " + token } : {} }), e);
const NAMES = [1, 2, 3, 4].flatMap(c => ["raw.jpg", "cut.png", "raw.mp4", "key.mp4"].map(k => `${c}-${k}`));
async function upAll(e, id, side, token) {
  for (const n of NAMES) assert.equal((await call(e, "PUT", `/s/${id}/${side}/${n}`, { token, body: "x" })).status, 204);
}

test("A creates, uploads 16 files, marks done; B can then join once", async () => {
  const e = env();
  const { id, token } = await (await call(e, "POST", "/s")).json();
  assert.equal(id.length, 22);
  assert.equal((await call(e, "POST", `/s/${id}/join`)).status, 409);          // A not done yet
  await upAll(e, id, "a", token);
  assert.equal((await call(e, "POST", `/s/${id}/a/done`, { token })).status, 204);
  const j = await call(e, "POST", `/s/${id}/join`);
  assert.equal(j.status, 201);
  assert.equal((await call(e, "POST", `/s/${id}/join`)).status, 409);          // second friend refused
  const st = await (await call(e, "GET", `/s/${id}`)).json();
  assert.deepEqual([st.a.done, st.b.joined, st.b.done], [true, true, false]);
});

test("done is refused while a file is missing and names the gaps", async () => {
  const e = env();
  const { id, token } = await (await call(e, "POST", "/s")).json();
  await call(e, "PUT", `/s/${id}/a/1-raw.jpg`, { token, body: "x" });
  const r = await call(e, "POST", `/s/${id}/a/done`, { token });
  assert.equal(r.status, 400);
  assert.equal((await r.json()).names.length, 15);
});

test("writes need the side's own token, a known name, and stay under the caps", async () => {
  const e = env();
  const { id, token } = await (await call(e, "POST", "/s")).json();
  assert.equal((await call(e, "PUT", `/s/${id}/a/1-raw.jpg`, { body: "x" })).status, 401);
  assert.equal((await call(e, "PUT", `/s/${id}/b/1-raw.jpg`, { token, body: "x" })).status, 403);
  assert.equal((await call(e, "PUT", `/s/${id}/a/evil.html`, { token, body: "x" })).status, 400);
  assert.equal((await call(e, "PUT", `/s/${id}/a/5-raw.jpg`, { token, body: "x" })).status, 400);
  const big = new Uint8Array(3 * 1024 * 1024 + 1);
  assert.equal((await call(e, "PUT", `/s/${id}/a/1-raw.mp4`, { token, body: big })).status, 413);
});

test("a finished side cannot be overwritten", async () => {
  const e = env();
  const { id, token } = await (await call(e, "POST", "/s")).json();
  await upAll(e, id, "a", token);
  await call(e, "POST", `/s/${id}/a/done`, { token });
  assert.equal((await call(e, "PUT", `/s/${id}/a/1-raw.jpg`, { token, body: "y" })).status, 409);
});

test("links older than 7 days answer 410", async () => {
  const e = env();
  const { id } = await (await call(e, "POST", "/s")).json();
  e.NOW = Date.now() + 7 * 864e5 + 1000;
  assert.equal((await call(e, "GET", `/s/${id}`)).status, 410);
});

test("unknown id is 404 and CORS allows only the Pages origin", async () => {
  const e = env();
  const r = await call(e, "GET", "/s/AAAAAAAAAAAAAAAAAAAAAA");
  assert.equal(r.status, 404);
  assert.equal(r.headers.get("Access-Control-Allow-Origin"), "https://dot-connects.github.io");
});
```

- [ ] **Step 2:** Run `node --test worker/test/` from the work folder. Expected: FAIL, `Cannot find module '../src/index.js'`.
- [ ] **Step 3: Implement** `worker/src/index.js`:

```js
const DAY = 864e5, LIFE = 7 * DAY, MAX_FILE = 3 * 1024 * 1024, MAX_LINK = 30 * 1024 * 1024;
const KINDS = ["raw.jpg", "cut.png", "raw.mp4", "key.mp4"];
const NAMES = [1, 2, 3, 4].flatMap(c => KINDS.map(k => `${c}-${k}`));
const TYPES = { jpg: "image/jpeg", png: "image/png", mp4: "video/mp4" };

const b64u = a => btoa(String.fromCharCode(...a)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const rand = n => b64u(crypto.getRandomValues(new Uint8Array(n)));
const sha = async s => b64u(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s))));

export default {
  async fetch(req, env) {
    const cors = { "Access-Control-Allow-Origin": env.ORIGIN, "Access-Control-Allow-Headers": "Authorization",
      "Access-Control-Allow-Methods": "GET,POST,PUT,OPTIONS" };
    const json = (status, obj) => new Response(obj === undefined ? null : JSON.stringify(obj),
      { status, headers: { ...cors, "Content-Type": "application/json" } });
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    const now = env.NOW ?? Date.now();
    const p = new URL(req.url).pathname.split("/").filter(Boolean);   // ["s", id, side|join|done, name]
    if (p[0] !== "s") return json(404, { error: "not-found" });

    if (req.method === "POST" && p.length === 1) {
      const id = rand(16), token = rand(32);
      const meta = { created: now, a: { tokenHash: await sha(token), done: false }, b: { tokenHash: null, done: false }, bytes: 0 };
      await env.BUCKET.put(`s/${id}/meta.json`, JSON.stringify(meta));
      return json(201, { id, token, expires: now + LIFE });
    }

    const id = p[1];
    if (!/^[A-Za-z0-9_-]{22}$/.test(id || "")) return json(404, { error: "not-found" });
    const metaObj = await env.BUCKET.get(`s/${id}/meta.json`);
    if (!metaObj) return json(404, { error: "not-found" });
    const meta = await metaObj.json();
    if (now - meta.created > LIFE) return json(410, { error: "expired" });
    const save = () => env.BUCKET.put(`s/${id}/meta.json`, JSON.stringify(meta));

    if (req.method === "GET" && p.length === 2)
      return json(200, { a: { done: meta.a.done }, b: { joined: !!meta.b.tokenHash, done: meta.b.done }, expires: meta.created + LIFE });

    if (req.method === "POST" && p[2] === "join" && p.length === 3) {
      if (!meta.a.done) return json(409, { error: "not-ready" });
      if (meta.b.tokenHash) return json(409, { error: "taken" });
      const token = rand(32);
      meta.b.tokenHash = await sha(token); await save();
      return json(201, { token });
    }

    const side = p[2];
    if (side !== "a" && side !== "b") return json(404, { error: "not-found" });

    if (req.method === "GET" && p.length === 4) {
      if (!NAMES.includes(p[3])) return json(400, { error: "name" });
      const o = await env.BUCKET.get(`s/${id}/${side}/${p[3]}`);
      if (!o) return json(404, { error: "not-found" });
      return new Response(o.body, { headers: { ...cors, "Content-Type": TYPES[p[3].split(".").pop()],
        "Cache-Control": "private, max-age=604800" } });
    }

    // writes
    const auth = (req.headers.get("Authorization") || "").replace(/^Bearer /, "");
    if (!auth) return json(401, { error: "token" });
    if (!meta[side].tokenHash || (await sha(auth)) !== meta[side].tokenHash) return json(403, { error: "token" });
    if (meta[side].done) return json(409, { error: "done" });

    if (req.method === "POST" && p[3] === "done" && p.length === 4) {
      const missing = [];
      for (const n of NAMES) if (!(await env.BUCKET.head(`s/${id}/${side}/${n}`))) missing.push(n);
      if (missing.length) return json(400, { error: "missing", names: missing });
      meta[side].done = true; await save();
      return json(204);
    }

    if (req.method === "PUT" && p.length === 4) {
      if (!NAMES.includes(p[3])) return json(400, { error: "name" });
      const body = new Uint8Array(await req.arrayBuffer());
      if (body.byteLength > MAX_FILE) return json(413, { error: "file-too-big" });
      if (meta.bytes + body.byteLength > MAX_LINK) return json(413, { error: "link-full" });
      await env.BUCKET.put(`s/${id}/${side}/${p[3]}`, body, { httpMetadata: { contentType: TYPES[p[3].split(".").pop()] } });
      meta.bytes += body.byteLength; await save();
      return json(204);
    }
    return json(405, { error: "method" });
  },
};
```
  Note: `meta.bytes` counts re-uploads of the same name twice; accepted (cap is a guard, 30 MB vs ~6 MB per side).
  The brief's per-IP limit on link creation is left out of v1: per-link caps + the billing alert bound the cost; add a
  Workers rate-limit binding if the dashboard shows abuse.
- [ ] **Step 4:** Run `node --test worker/test/`. Expected: 6 pass.
- [ ] **Step 5:** Independent review (Worker = boundary/state code): fresh Claude subagent or Codex per usage, input
  `git diff`, one round. Fix Highs.
- [ ] **Step 6:** Commit `feat(photo-booth): worker for link ids, write tokens, caps and A-then-B order`.

### Task 3: Deploy the Worker and bucket

Precondition: operator finished `scripts/wizard-cloudflare.bat` (`npx wrangler@4 whoami` shows the account).

**Files:** Create `worker/wrangler.toml`:
```toml
name = "dot-lab-booth"
main = "src/index.js"
compatibility_date = "2026-10-01"
[vars]
ORIGIN = "https://dot-connects.github.io"
[[r2_buckets]]
binding = "BUCKET"
bucket_name = "dot-lab-booth"
```
- [ ] **Step 1:** `npx wrangler@4 r2 bucket create dot-lab-booth`
- [ ] **Step 2:** Lifecycle: `npx wrangler@4 r2 bucket lifecycle add dot-lab-booth expire-7d s/ --expire-days 7`
  (check `npx wrangler@4 r2 bucket lifecycle --help` first; the exact flag names are not verified).
- [ ] **Step 3:** `npx wrangler@4 deploy` from `worker/`. Note the `*.workers.dev` URL in `brief.md`.
- [ ] **Step 4: Smoke** with Node: POST /s → PUT 16 one-byte files → done → join → GET status → GET one file.
  Expected 201/204/201/200/200. Then check the R2 dashboard lists the lifecycle rule.
- [ ] **Step 5:** Commit `chore(photo-booth): deploy config for the booth worker`.

### Task 4: Capture engine (camera, cut-out, four cuts with pre-roll)

**Files:** Create `site/js/env.js`, `site/js/camera.js`, `site/js/segment.js`, `site/js/capture.js`, a bare
`site/index.html` that runs `shootFour()` and shows the 16 results (temporary harness, replaced in Task 6).

**Interfaces — Produces:**
```js
// env.js
export const app;                       // "Threads" | "Instagram" | "KakaoTalk" | "Facebook" | "브라우저"
export const inApp;                     // boolean
export function chromeUrl(url): string  // kakaotalk://web/openExternal?url=… | intent://… | url
// camera.js  (spike code, moved)
export async function startCam(video, facing = "user"): {stream, track, front, caps}
export const state = {mirror: boolean, front: boolean};
export function bindZoomFocus(stageEl, track, caps): void      // pinch + tap focus, no-op without caps
// segment.js
export async function loadSegmenter(): void
export function cutFrame(video, mirror, outCanvas): void          // outCanvas gets the person with alpha
// capture.js
export async function shootFour({video, preview, mirror, onTick}): Cut[]
//   Cut = {raw: Blob(jpeg), cut: Blob(png), rawClip: Blob(mp4|webm), keyClip: Blob(mp4|webm)}
//   countdown 3 s per cut; recorders start at "2" and stop at the shutter -> 2 s clips;
//   keyClip = cut-out drawn over pure #00ff00 at the same size; stills taken at the shutter frame.
```
- [ ] **Step 1:** Move spike camera code into `camera.js` (startCam, flip, mirror, zoom, focus, 5 s stall) and spike
  segmentation into `segment.js`. Keep the centre-vs-corner mask direction check from the spike.
- [ ] **Step 2:** Write `capture.js`: one rAF loop draws `raw` (mirrored per state) and `key` (green + cut-out) canvases;
  two `MediaRecorder(canvas.captureStream(30))` per cut; mime pick order from spike (`video/mp4;codecs=avc1` first).
- [ ] **Step 3:** Run headless with fake camera (`drive.mjs`): `shootFour` returns 4 cuts, each blob non-empty, clip
  durations ~2 s (read `video.duration` after load; webm may report Infinity — then accept size > 0 and note it).
- [ ] **Step 4:** Push, operator runs the harness once in Threads (in-app) and once in Chrome: 4 cuts, clips play,
  mirror toggled cut looks mirrored. Record in `brief.md` 실측.
- [ ] **Step 5:** Commit `feat(photo-booth): capture engine with 2 s pre-roll clips and cut-outs`.

### Task 5: Compose (strip image + moving strip video)

**Files:** Create `site/js/compose.js`; harness page `site/compose-test.html` with two sets of saved cuts (from Task 4
run, saved to `site/test-cuts/` — delete before ship).

**Interfaces — Produces:**
```js
export const BACKGROUNDS;  // [{id, label, draw(ctx,w,h)}] from Task 1 pick + "a" (A's raw) + "b" (B's raw)
export const FRAMES;       // [{id, label, color, ink}] from Task 1 pick
export async function drawStrip({a: Cut[], b: Cut[], bg, frame, footer}): Blob   // jpeg, 2x6 ratio (600x1800)
export async function recordMovingStrip({a, b, bg, frame, footer, seconds = 6}): Blob  // mp4|webm
//  bg "a": A's raw frame + B's cut-out; "b": B's raw + A's cut-out; preset: both cut-outs over draw().
//  video: 8 <video> elements loop, keyClip pixels with g > 150 && r < 100 && b < 100 -> transparent
//  (tune on real clips; soften 1 px), drawn per frame into the strip canvas, recorded 30 fps.
```
People placement: A left, B right, each scaled so the cut-out's alpha bounding box height = 80 % of the cell height,
bottoms on the cell bottom. Bounding box from the png alpha once per cut.
- [ ] **Step 1:** Implement `drawStrip` and check the four bg modes in the harness, screenshot at dpr 2, inspect edges.
- [ ] **Step 2:** Implement `recordMovingStrip`; check the output plays 6 s, both people move, no green fringe at
  full size (crop 3x). If fringe shows, shrink the key mask by 1 px before drawing.
- [ ] **Step 3:** Commit `feat(photo-booth): strip image and moving strip video with background choice`.

### Task 6: Flow (roles, upload, join, B's live view, done, save, expiry)

**Files:** `site/index.html` (real screens from the Task 1 pick), `site/js/api.js`, `site/js/app.js`.

**Interfaces — Consumes:** Worker API (Task 2), capture (Task 4), compose (Task 5). **Produces:**
```js
// api.js  (WORKER = the workers.dev URL from Task 3)
export async function create(): {id, token, expires}
export async function join(id): {token} | {error}
export async function put(id, side, name, blob, token): void      // retries 2x on network error
export async function done(id, side, token): void
export async function status(id): {a:{done}, b:{joined, done}, expires} | {error: "expired"|"not-found"}
export const fileUrl = (id, side, name) => `${WORKER}/s/${id}/${side}/${name}`;
```
Role and token: A's token kept in `localStorage["booth:"+id]`; B's after join, same key. URL `site/?s=<id>`.
- [ ] **Step 1:** Screens: landing (A: [먼저 찍기]; B: "옆자리를 비워 뒀어요" + [바로 찍기]), shoot, uploading
  (progress n/16), share (A: Web Share text+url if `navigator.share`, else copy button; in-app shows the link to copy),
  waiting (A reopening before B done: "친구가 아직 안 찍었어요"), done (strip + bg/frame chips + saves), expired.
- [ ] **Step 2:** B's shoot view: A's cut `n-cut.png` (and `n-key.mp4` playing, keyed) drawn on the left of the live
  preview so B can stand beside A.
- [ ] **Step 3:** Saves appear only when `status().b.done`. Chrome → `drawStrip`/`recordMovingStrip`, then
  `<a download>` of the blob. Only https downloads were measured in Chrome; if a blob download fails on the phone, use a
  `data:` URL for the image and keep the video as blob (report back before inventing more).
  In-app → one button "크롬에서 열어 저장하기" = `chromeUrl(location.href)`.
- [ ] **Step 4:** One-phone end-to-end on the operator's phone: A in Chrome, B in KakaoTalk (link sent to self), both
  save in Chrome. Then a second link with B in Threads.
- [ ] **Step 5:** Commit `feat(photo-booth): link flow from first shot to both saves`.

### Task 7: Ship checks

- [ ] Headless 430 px: first screen readable in 2 s on throttled 4G (CDP `Network.emulateNetworkConditions`), segmenter
  loads only after [찍기]. Contrast ≥ 4.5:1 on every text.
- [ ] `manifest.json` and index tile per `dot-lab/README.md` "Before a work ships"; OG image = promo hero strip.
- [ ] Delete `site/test-cuts/`, `site/compose-test.html`; spike stays as a measured record.
- [ ] Update `brief.md` 결과, `docs/mechanics.md` (#003 row), commit and push.
