// One cut = countdown N..1 (default 3), a 2 s side-by-side clip [raw | matte] from "2" to the shutter, then the stills.
// The HQ (MODNet) cut runs later, only on the picked stills: hqCut().
import { camState, frameRect } from "./camera.js";
import * as SEG from "./segment.js";
import { segmentFrame, drawMatte, drawCutout, prefetchStill, stillReady, stillWarm, cutStill, stats, keepLargest } from "./segment.js";

export const CLIP_MAX = { w: 640, h: 480 };
export const STILL_MAX = { w: 960, h: 720 };
const CAP = 3 * 1024 * 1024;
const STOP_MAX = 1500, STOP_EMPTY = 6000;   // ms the shutter waits for the recorder's last chunk (see recorder)
const MIMES = ["video/mp4;codecs=avc1", "video/mp4", "video/webm;codecs=vp9", "video/webm"];
const FAST = new URLSearchParams(location.search).has("fast");   // tests only: shortens the "3" second and the ones before it
const sleep = ms => new Promise(r => setTimeout(r, ms));

// 4:3 size under max, width a multiple of 8 so both sides are even
function fit(r, max) { const w = Math.floor(Math.min(r.sw, max.w) / 8) * 8; return { w, h: w * 3 / 4 }; }

// Draw the full camera frame so that its centre 4:3 crop r lands on (x, 0, W, H) of ctx, clipped to that box.
function place(ctx, video, r, x, W, H, fn) {
  const k = W / r.sw;
  ctx.save();
  ctx.beginPath(); ctx.rect(x, 0, W, H); ctx.clip();
  ctx.translate(x - r.sx * k, -r.sy * k);
  fn(ctx, video.videoWidth * k, video.videoHeight * k);
  ctx.restore();
}
function drawRaw(ctx, video, w, h, mirror) {
  ctx.save(); if (mirror) { ctx.translate(w, 0); ctx.scale(-1, 1); }
  ctx.drawImage(video, 0, 0, w, h); ctx.restore();
}

const toBlob = (c, type, q) => new Promise(res => c.toBlob(res, type, q));

// At the shutter: the raw still and the live-matte cut-out of the same frame (the fallback cut), as canvases.
function stills(video, mirror) {
  const r = frameRect(video), { w, h } = fit(r, STILL_MAX);
  const canvas = () => { const c = Object.assign(document.createElement("canvas"), { width: w, height: h }); return [c, c.getContext("2d")]; };
  segmentFrame(video);              // the matte belongs to the frame drawn at the shutter
  const [rawC, rx] = canvas(), [liveC, lx] = canvas();
  place(rx, video, r, 0, w, h, (cx, fw, fh) => drawRaw(cx, video, fw, fh, mirror));
  place(lx, video, r, 0, w, h, (cx, fw, fh) => drawCutout(cx, video, fw, fh, mirror));
  return { rawC, liveC };
}
const small = c => { const s = Object.assign(document.createElement("canvas"), { width: 720, height: 540 });
  s.getContext("2d").drawImage(c, 0, 0, 720, 540); return s; };

function pickMime() {
  return MIMES.find(t => window.MediaRecorder && MediaRecorder.isTypeSupported(t)) || "";
}

// Recorder whose encoder may need ~0.9 s to start (headless, 10-26): callers start it early and pause.
function recorder(stream, bps) {
  const mime = pickMime();
  const rec = new MediaRecorder(stream, { ...(mime && { mimeType: mime }), videoBitsPerSecond: bps });
  const chunks = [];
  rec.ondataavailable = e => e.data.size && chunks.push(e.data);
  const started = new Promise(res => rec.onstart = res);
  const stopped = new Promise(res => rec.onstop = res);
  rec.start(250);
  // stop never holds the shot rhythm for long: with data in hand it waits STOP_MAX for the last chunk, then the clip
  // is what the 250 ms chunks gave so far (a late encoder flush took 6.8 s once on shot 3, 10-27). With nothing yet
  // (encoder slow to start) it waits up to STOP_EMPTY; still empty -> shootOne's straight retry.
  return { rec, started, late: false, async stop() {
    if (rec.state !== "inactive") rec.stop();
    const t0 = performance.now();
    let done = false; stopped.then(() => done = true);
    while (!done) {
      const ms = performance.now() - t0;
      if (ms > (chunks.length ? STOP_MAX : STOP_EMPTY)) { this.late = true; break; }
      await sleep(50);
    }
    return new Blob(chunks, { type: (rec.mimeType || mime || "video/webm").split(";")[0] });
  } };
}

// Clip recorder on WebCodecs (same shape as recorder()): every frame the loop draws is encoded with the camera's own
// frame time, so the clip has all of them at their real spacing. MediaRecorder on the canvas stream kept 30-60 % of
// the frames pushed to it (headless 10-27, fake camera) and stamps them with the wall clock. null -> MediaRecorder.
let clipCfg = null;
async function frameRecorder(canvas, bps) {
  const { width, height } = canvas;
  clipCfg ||= (async () => {
    if (!window.VideoEncoder || !window.VideoFrame) return null;
    for (const codec of ["avc1.42e01f", "avc1.4d401f", "avc1.640028"]) {
      const c = { codec, width, height, bitrate: bps, framerate: 30, avc: { format: "avc" } };
      if ((await VideoEncoder.isConfigSupported(c).catch(() => null))?.supported) return c;
    }
    return null;
  })();
  const cfg = await clipCfg;
  if (!cfg || cfg.width !== width || cfg.height !== height) return null;
  const { Muxer, ArrayBufferTarget } = await imp("./vendor/mp4-muxer.mjs");
  // fragmented: what MediaRecorder writes too, and what compose.js reads frame by frame
  const muxer = new Muxer({ target: new ArrayBufferTarget(), video: { codec: "avc", width, height, frameRate: 30 },
    fastStart: "fragmented", firstTimestampBehavior: "offset" });
  let failed = null, paused = false, n = 0, last = -1, done = false, out = 0;
  const enc = new VideoEncoder({ output: (c, m) => { if (!done) { muxer.addVideoChunk(c, m); out++; } }, error: e => { failed = e; } });
  enc.configure(cfg);
  return { rec: { pause() { paused = true; }, resume() { paused = false; } }, started: Promise.resolve(), late: false, drops: 0, n: 0,
    // us = the frame's time in microseconds (only increasing ones are kept)
    push(us) {
      if (paused || failed || enc.state !== "configured" || us <= last) return;
      if (enc.encodeQueueSize > 8) { this.drops++; return; }    // a stuck encoder drops frames rather than queueing seconds
      last = us; const f = new VideoFrame(canvas, { timestamp: Math.round(us) });
      enc.encode(f, { keyFrame: n++ % 60 === 0 }); f.close(); this.n = n;
    },
    async stop() {
      paused = true;
      // like MediaRecorder: with encoded chunks in hand wait STOP_MAX for the rest, with none yet up to STOP_EMPTY. A busy
      // phone can take > 1.5 s to hand out the first chunk: the clip was finalized as a bare MP4 header (B's shot 5,
      // merged e2e 10-27, "0 KB", no retry) and the strip video died on it.
      const flushed = enc.state === "configured" ? enc.flush().then(() => true, () => false) : Promise.resolve(false);
      if (!(await Promise.race([flushed, sleep(STOP_MAX)]))) {
        this.late = true;
        if (!out) await Promise.race([flushed, sleep(STOP_EMPTY - STOP_MAX)]);
      }
      done = true;
      if (enc.state !== "closed") enc.close();
      if (!out) return new Blob([], { type: "video/mp4" });   // empty -> shootOne's straight retry (a partial clip is kept)
      muxer.finalize();
      return new Blob([muxer.target.buffer], { type: "video/mp4" });
    } };
}

// The first recorder of a page took 3.7 s to start (headless 10-26, a warm one ~0.3-0.9 s): warm the encoder at import.
const warm = (async () => {
  const c = Object.assign(document.createElement("canvas"), { width: 64, height: 48 }), x = c.getContext("2d");
  let on = true;
  const l = () => { if (on) { x.fillStyle = `hsl(${performance.now() % 360},50%,50%)`; x.fillRect(0, 0, 64, 48); requestAnimationFrame(l); } };
  l();
  const R = recorder(c.captureStream(30), 200_000);
  await Promise.race([R.started, sleep(6000)]);
  await R.stop().catch(() => {});
  on = false;
})().catch(() => {});

// Over the cap: play the clip once into a smaller canvas at a lower bitrate (real time, ~2 s).
async function shrinkClip(blob) {
  const v = Object.assign(document.createElement("video"), { muted: true, playsInline: true, src: URL.createObjectURL(blob) });
  await v.play();
  const c = Object.assign(document.createElement("canvas"), { width: 2 * 480, height: 360 }), ctx = c.getContext("2d");
  let on = true;
  const loop = () => { if (!on) return; ctx.drawImage(v, 0, 0, c.width, c.height); requestAnimationFrame(loop); };
  loop();
  const R = recorder(c.captureStream(30), 1_000_000);
  await Promise.race([new Promise(r => v.onended = r), sleep(5000)]);
  const out = await R.stop();
  on = false; URL.revokeObjectURL(v.src);
  return out.size ? out : blob;
}

// a module fetch is sometimes cancelled mid-shoot under load (B's compose.js, headless 10-28: "Failed to fetch
// dynamically imported module" and the whole shoot stopped); retry once as is, then under a fresh URL
async function imp(url) {
  let err;
  for (const q of ["", "", `?r=${Date.now()}`]) { try { return await import(url + q); } catch (e) { err = e; await sleep(400); } }
  throw err;
}
// seconds: countdown length (Round 5: 5). stop(): checked during the seconds before "3"; true -> throws "stopped".
// hold(): a promise to wait for before the stills (a camera switch in flight, Round 6); onTick(0) = the shutter itself
export async function shootOne(i, { video, onTick, seconds = 3, stop, hold }) {
  // no prefetchStill here: its session create can freeze the page ~4.5 s (the landing page starts it).
  // A recorder whose encoder had not started by the shutter returns an empty clip (0 bytes: seen 10-27 on cut 1 of
  // a full run and cut 4 of a review run); the Worker rejects it and the upload stalls. Shoot that cut once more,
  // recording straight through the countdown (no pause/resume), and never return an empty clip.
  // Round 5: "empty" = any clip that will not play (a 0-byte one, or an MP4 with no frames), not only size 0.
  const c = await shootOneNow(i, { video, onTick, seconds, stop, hold });
  if (await clipOk(c.clip)) return c;
  const again = await shootOneNow(i, { video, onTick, seconds: 3, hold }, true);
  stats.still.cuts[i].retried = true;
  if (!(await clipOk(again.clip))) throw new Error("clip-empty");
  return again;
}
// fragmented MP4 (ours, MediaRecorder on Chrome): >= 5 frames in its tables. Anything else must actually play for 0.1 s:
// Chrome fires loadeddata even for an MP4 header with no frames (10-27), so "it loads" proves nothing.
export async function clipOk(blob) {
  if (!blob?.size) return false;
  const { readFmp4 } = await imp("./compose.js");
  const m = /mp4/.test(blob.type) ? readFmp4(await blob.arrayBuffer()) : null;
  if (m) return m.samples.length >= 5;
  return new Promise(res => {
    const v = Object.assign(document.createElement("video"), { muted: true, playsInline: true, preload: "auto" }), u = URL.createObjectURL(blob);
    let iv = 0;
    const end = ok => { clearTimeout(t); clearInterval(iv); v.onloadeddata = v.onerror = v.onended = null; v.pause(); v.removeAttribute("src"); v.load(); URL.revokeObjectURL(u); res(ok); };
    const t = setTimeout(() => end(false), 4000);
    v.onloadeddata = () => { v.play().catch(() => end(false)); iv = setInterval(() => v.currentTime > 0.1 && end(true), 50); };
    v.onerror = v.onended = () => end(false); v.src = u;
  });
}

async function shootOneNow(i, { video, onTick, seconds = 3, stop, hold }, straight = false) {
  // Round 6: mirror and front/back can change mid-countdown, so both are read per frame (and again at the shutter);
  // the clip keeps the size it started with
  const { w, h } = fit(frameRect(video), CLIP_MAX);
  const clipC = Object.assign(document.createElement("canvas"), { width: 2 * w, height: h });
  const cctx = clipC.getContext("2d");
  // The raw half takes every camera frame (requestVideoFrameCallback, one recorder frame each); the model runs at most
  // a third of the time and the matte half holds its last mask in between. Segmenting every frame tied the clip to the
  // model's speed (headless 10-27: 5-14 distinct fps at segMs ~45), a stutter in every moving strip; the HQ moving cut
  // (clipmatte.js) redoes the matte later anyway.
  let on = true, segAt = -1e9, segMs = 0, push = () => {}, mt = null, tPrev = -1, tOff = 0;
  const fs = { frames: 0, segs: 0, segMs: 0, drawMs: 0 };    // per-shot loop stats (dev pages)
  const vfc = "requestVideoFrameCallback" in video;
  const loop = (now, m) => {
    if (!on) return;
    const t = performance.now();
    if (t - segAt >= 3 * segMs && segmentFrame(video)) { segAt = t; segMs = performance.now() - t; fs.segs++; fs.segMs += segMs; }
    const t1 = performance.now(), r = frameRect(video), mirror = camState.mirror;
    if (r.sw) {
      place(cctx, video, r, 0, w, h, (cx, fw, fh) => drawRaw(cx, video, fw, fh, mirror));
      place(cctx, video, r, w, w, h, (cx, fw, fh) => drawMatte(cx, fw, fh, mirror));
      fs.frames++; fs.drawMs += performance.now() - t1;
      mt ??= m?.mediaTime > 0;         // the camera's frame time when the browser gives one (rVFC), else the clock
      // a new camera (front/back switch) restarts its media time near 0: keep the clip's timestamps going up
      const ts = mt && m ? m.mediaTime * 1e6 : performance.now() * 1000;
      if (ts + tOff <= tPrev) tOff = tPrev + 33333 - ts;
      tPrev = ts + tOff; push(tPrev);
    }
    if (vfc) video.requestVideoFrameCallback(loop); else requestAnimationFrame(loop);
  };

  // The encoder starts paused at the first number and has every second before "2" to come up: waiting for it at "3"
  // held that number up to 2.5 s on a phone (operator 10-27: "3에서 끊겼다가 2"). Recording runs from "2".
  // The straight retry of an empty clip goes to MediaRecorder: a second WebCodecs failure would end the shoot.
  let R = straight ? null : await frameRecorder(clipC, 2_500_000).catch(() => null);
  if (R) push = us => R.push(us);
  else {
    await warm;
    let stream = clipC.captureStream(0);
    const track = stream.getVideoTracks()[0];
    if (track?.requestFrame) push = () => track.requestFrame(); else { stream.getTracks().forEach(t => t.stop()); stream = clipC.captureStream(30); }
    R = recorder(stream, 2_500_000);
  }
  loop();
  if (!straight) R.rec.pause();
  // ponytail: stop is only honoured before "3"; a press during 3-2-1 lets that shot finish (the caller stops after it)
  for (let n = seconds; n > 2; n--) {
    if (n > 3 && stop?.()) { on = false; R.stop().catch(() => {}); throw new Error("stopped"); }
    onTick?.(n); await sleep(FAST && n > 3 ? 300 : FAST ? 200 : 1000);
  }
  // only a very slow encoder still waits here (first recorder of a page: up to ~4 s headless), not a dead clip
  await Promise.race([R.started, sleep(1500)]);
  onTick?.(2);
  if (!straight) R.rec.resume();
  await sleep(1000);
  onTick?.(1); await sleep(1000);
  const clipP = R.stop();             // shutter (resolves within STOP_MAX)
  on = false;
  await hold?.();                     // a camera switch still in flight: the still waits for the new camera
  if (video.readyState < 2) await Promise.race([new Promise(r => video.addEventListener("loadeddata", r, { once: true })), sleep(3000)]);
  onTick?.(0);
  const t0 = performance.now();
  const { rawC, liveC } = stills(video, camState.mirror);
  const rawP = toBlob(rawC, "image/jpeg", 0.9);
  let clip = await clipP;
  const t1 = performance.now();
  keepLargest(liveC);                // a passer-by or a second face in the frame is not cut out with the person
  const t2 = performance.now();
  let raw = await rawP, cut = await toBlob(liveC, "image/png");
  if (raw.size > CAP || cut.size > CAP) { raw = await toBlob(small(rawC), "image/jpeg", 0.8); cut = await toBlob(small(liveC), "image/png"); }
  const t3 = performance.now();
  if (clip.size > CAP) clip = await shrinkClip(clip);
  stats.still.cuts[i] = { path: "live", clipStopMs: Math.round(t1 - t0), cutMs: Math.round(t2 - t1), encodeMs: Math.round(t3 - t2),
    pauseMs: Math.round(performance.now() - t0), ...(R.late && { stopLate: true }),
    loop: { frames: fs.frames, encoded: R.n, drops: R.drops, segs: fs.segs, segMs: Math.round(fs.segMs / (fs.segs || 1)), drawMs: Math.round(fs.drawMs / (fs.frames || 1)) } };
  return { raw, cut, clip, clipType: clip.type };
}

export async function shootFour({ video, onTick, onCut }) {
  const cuts = [];
  for (let i = 0; i < 4; i++) { cuts[i] = await shootOne(i, { video, onTick }); onCut?.(i, cuts[i]); }
  return cuts;
}

// After the pick (Round 4): the picks show their live cut at once; the HQ cuts replace them in the background.
// hqReady: waits (up to `wait` ms) for the still model to load + warm -> true when hqCut can run.
// The Pick screen already started warmStill (the one GPU freeze); this joins it.
export async function hqReady(wait = 60000) {
  if (stillReady()) return true;
  return !!await Promise.race([SEG.warmStill ? SEG.warmStill() : prefetchStill().then(() => stillWarm()).then(stillReady), sleep(wait)]);
}
export const hqState = () => stats.still.state || "off";
export const hqPath = () => stats.still.path;   // "webgpu" | "wasm" | "live" (segment.js)
// The HQ cut of one picked still, same size as cut.raw (PNG). Throws when the model is not ready or fails; the caller
// then keeps cut.cut (the live matte).
export async function hqCut(cut) {
  const bmp = await createImageBitmap(cut.raw);
  const c = Object.assign(document.createElement("canvas"), { width: bmp.width, height: bmp.height });
  c.getContext("2d").drawImage(bmp, 0, 0); bmp.close?.();
  const out = await cutStill(c);
  keepLargest(out);
  const png = await toBlob(out, "image/png");
  if (!png || png.size > CAP) throw new Error("still-size");
  return png;
}
