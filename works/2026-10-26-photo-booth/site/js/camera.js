// Camera: 4:3 ideal, widest zoom, mirror follows the facing, pinch zoom + tap focus where the track offers them.
// shape (Round 6 #9): "wide" (가로) keeps the centre 4:3 of the camera, "tall" (세로) the centre 3:4.
export const camState = { mirror: true, front: true, track: null, caps: {}, shape: "wide" };
let stream = null;

export async function startCam(video, facing = "user") {
  if (stream) stream.getTracks().forEach(t => t.stop());
  stream = null;
  const req = navigator.mediaDevices.getUserMedia({ audio: false,
    video: { facingMode: { ideal: facing }, aspectRatio: { ideal: 4 / 3 }, width: { ideal: 1280 } } });
  let timer, timedOut = false;
  const stall = new Promise((_, rej) => { timer = setTimeout(() => rej(new Error("stall")), 5000); });
  // a late answer after the stall must not leave the camera on
  req.then(s => { if (timedOut) s.getTracks().forEach(t => t.stop()); }, () => {});
  try { stream = await Promise.race([req, stall]); }
  catch (e) { if (e.message === "stall") timedOut = true; throw e; }
  finally { clearTimeout(timer); }

  const track = stream.getVideoTracks()[0];
  const caps = track.getCapabilities ? track.getCapabilities() : {};
  if (caps.zoom) { try { await track.applyConstraints({ advanced: [{ zoom: caps.zoom.min }] }); } catch {} }
  camState.track = track;
  camState.caps = caps;
  camState.front = (track.getSettings().facingMode || facing) !== "environment";
  camState.mirror = camState.front;
  video.muted = true; video.playsInline = true;
  video.srcObject = stream;
  await video.play();
  if (video.readyState < 2 || !video.videoWidth) await new Promise(r => video.addEventListener("loadeddata", r, { once: true }));
  return { track, front: camState.front, caps };
}

export async function flipCam(video) {
  return startCam(video, camState.front ? "environment" : "user");
}

// Centre 4:3 (wide) or 3:4 (tall) crop of the current video (some cameras ignore the 4:3 request and give 16:9).
export function frameRect(video, shape = camState.shape) {
  const vw = video.videoWidth, vh = video.videoHeight, [p, q] = shape === "tall" ? [3, 4] : [4, 3];
  if (vw * q > vh * p) { const sw = Math.round(vh * p / q); return { sx: (vw - sw) >> 1, sy: 0, sw, sh: vh }; }
  const sh = Math.round(vw * q / p);
  return { sx: 0, sy: (vh - sh) >> 1, sw: vw, sh };
}

async function setZoom(v) {
  const z = camState.caps.zoom;
  if (!z || !camState.track) return;
  v = Math.min(z.max, Math.max(z.min, v));
  try { await camState.track.applyConstraints({ advanced: [{ zoom: v }] }); } catch {}
}

// Pinch zoom + tap focus, with the README in-app touch fixes (page keeps the gesture, never at scroll top).
export function bindZoomFocus(stageEl) {
  for (const t of ["touchstart", "touchmove"])
    stageEl.addEventListener(t, e => { if (!e.target.closest("button")) e.preventDefault(); }, { passive: false });
  const nudge = () => { if (scrollY < 1) scrollTo(0, 1); };
  addEventListener("load", nudge);
  addEventListener("scroll", nudge, { passive: true });
  stageEl.addEventListener("touchstart", nudge, { passive: true });
  nudge();

  let pinch = null, moved = false, start = null;
  const dist = t => Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY);
  stageEl.addEventListener("touchstart", e => {
    moved = false;
    start = e.touches.length === 1 ? { x: e.touches[0].clientX, y: e.touches[0].clientY } : null;
    if (e.touches.length === 2 && camState.caps.zoom)
      pinch = { d: dist(e.touches), z: camState.track.getSettings().zoom ?? camState.caps.zoom.min };
  }, { passive: true });
  stageEl.addEventListener("touchmove", e => {
    if (start && e.touches.length === 1 && Math.hypot(e.touches[0].clientX - start.x, e.touches[0].clientY - start.y) > 10) moved = true;
    if (e.touches.length > 1) moved = true;
    if (pinch && e.touches.length === 2) setZoom(pinch.z * dist(e.touches) / pinch.d);
  }, { passive: true });
  stageEl.addEventListener("touchend", e => {
    if (e.touches.length < 2) pinch = null;
    // touchstart is cancelled, so no click follows: a still single touch is the tap
    if (!moved && start && e.touches.length === 0 && !e.target.closest("button")) focusAt(stageEl, start.x, start.y);
    start = null;
  });
  stageEl.addEventListener("click", e => { if (!e.target.closest("button")) focusAt(stageEl, e.clientX, e.clientY); });
}

async function focusAt(stageEl, cx, cy) {
  const { track, caps, mirror } = camState;
  if (!track || !("pointsOfInterest" in caps)) return;
  const r = stageEl.getBoundingClientRect();
  let x = (cx - r.left) / r.width; const y = (cy - r.top) / r.height;
  if (mirror) x = 1 - x;
  try { await track.applyConstraints({ advanced: [{ pointsOfInterest: [{ x, y }], focusMode: "single-shot" }] }); } catch {}
}
