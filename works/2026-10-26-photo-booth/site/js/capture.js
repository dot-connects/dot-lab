// One cut = countdown 3-2-1, a 2 s side-by-side clip [raw | matte] from "2" to the shutter, then the stills.
import { camState, frameRect } from "./camera.js";
import { segmentFrame, drawMatte, drawCutout, prefetchStill, stillReady, cutStill, stillBusy, stats, keepLargest } from "./segment.js";

export const CLIP_MAX = { w: 640, h: 480 };
export const STILL_MAX = { w: 960, h: 720 };
const CAP = 3 * 1024 * 1024;
const MIMES = ["video/mp4;codecs=avc1", "video/mp4", "video/webm;codecs=vp9", "video/webm"];
const FAST = new URLSearchParams(location.search).has("fast");   // tests only: shortens the "3" second
const BOTH = new URLSearchParams(location.search).has("both");   // tests only: also keep the live-matte PNG in stats
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
  return { rec, started, async stop() {
    rec.stop();
    await Promise.race([stopped, sleep(4000).then(() => { throw new Error("recorder-stop"); })]);
    return new Blob(chunks, { type: (rec.mimeType || mime || "video/webm").split(";")[0] });
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
  await new Promise(r => v.onended = r);
  const out = await R.stop();
  on = false; URL.revokeObjectURL(v.src);
  return out;
}

export async function shootOne(i, { video, onTick }) {
  prefetchStill();                   // no-op when loadSegmenter already started it (or ?still=0)
  stillBusy(true);                   // the countdown never waits on the HQ model (a model not warm by now stays off)
  try {
    // A recorder whose encoder had not started by the shutter returns an empty clip (0 bytes: seen 10-27 on cut 1 of
    // a full run and cut 4 of a review run); the Worker rejects it and the upload stalls. Shoot that cut once more,
    // recording straight through the countdown (no pause/resume), and never return an empty clip.
    const c = await shootOneNow(i, { video, onTick });
    if (c.clip.size) return c;
    const again = await shootOneNow(i, { video, onTick }, true);
    stats.still.cuts[i].retried = true;
    if (!again.clip.size) throw new Error("clip-empty");
    return again;
  } finally { stillBusy(false); }
}

async function shootOneNow(i, { video, onTick }, straight = false) {
  const mirror = camState.mirror;
  const r = frameRect(video), { w, h } = fit(r, CLIP_MAX);
  const clipC = Object.assign(document.createElement("canvas"), { width: 2 * w, height: h });
  const cctx = clipC.getContext("2d");
  let on = true;
  const loop = () => {
    if (!on) return;
    segmentFrame(video);
    place(cctx, video, r, 0, w, h, (cx, fw, fh) => drawRaw(cx, video, fw, fh, mirror));
    place(cctx, video, r, w, w, h, (cx, fw, fh) => drawMatte(cx, fw, fh, mirror));
    requestAnimationFrame(loop);
  };

  await warm;
  onTick?.(3);
  loop();
  const R = recorder(clipC.captureStream(30), 2_500_000);
  if (!straight) R.rec.pause();      // the encoder starts during "3", recording runs from "2"
  await sleep(FAST ? 200 : 1000);
  // a slow encoder (first recorder of a page: up to ~4 s headless) gets up to 1.5 s more before "2", not a dead clip
  await Promise.race([R.started, sleep(1500)]);
  onTick?.(2);
  if (!straight) R.rec.resume();
  await sleep(1000);
  onTick?.(1); await sleep(1000);
  const clipP = R.stop();             // shutter
  on = false;
  const t0 = performance.now();
  const { rawC, liveC } = stills(video, mirror);
  const rawP = toBlob(rawC, "image/jpeg", 0.9);
  let clip = await clipP;
  const t1 = performance.now();
  // the HQ cut runs after the recorder stopped (never inside the countdown); not ready or failed = live matte
  let cutC = liveC, path = "live";
  if (stillReady()) try { cutC = await cutStill(rawC); path = "modnet"; } catch (e) { path = "live:" + (e?.message || e); }
  else path = "live:" + (stats.still.state || "off");
  keepLargest(cutC);                 // a passer-by or a second face in the frame is not cut out with the person
  const t2 = performance.now();
  let raw = await rawP, cut = await toBlob(cutC, "image/png");
  if (raw.size > CAP || cut.size > CAP) { raw = await toBlob(small(rawC), "image/jpeg", 0.8); cut = await toBlob(small(cutC), "image/png"); }
  const t3 = performance.now();
  if (clip.size > CAP) clip = await shrinkClip(clip);
  stats.still.cuts[i] = { path, clipStopMs: Math.round(t1 - t0), cutMs: Math.round(t2 - t1), encodeMs: Math.round(t3 - t2),
    pauseMs: Math.round(performance.now() - t0), ...(BOTH && { live: await toBlob(liveC, "image/png") }) };
  return { raw, cut, clip, clipType: clip.type };
}

export async function shootFour({ video, onTick, onCut }) {
  const cuts = [];
  stillBusy(true);                   // no warm-up may start in the gaps between the four cuts
  try { for (let i = 0; i < 4; i++) { cuts[i] = await shootOne(i, { video, onTick }); onCut?.(i, cuts[i]); } } finally { stillBusy(false); }
  return cuts;
}
