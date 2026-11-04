// node --test works/2026-11-04-dalgona/site/js/camera.engine.test.mjs
// The tracker -> engine path end to end (code review r2 H1: face frames reset the landing grace, so rushing on camera
// never built pressure). Fake video and fake models; the hand pinches and sweeps a circle.
import test from "node:test";
import assert from "node:assert/strict";
import { createTracker } from "./camera.js";
import { createGame, step } from "./engine.js";

const W = 430, H = 932, GEOM = { cx: 215, cy: 540, R: 155 };
function run(speedR) {
  const video = { videoWidth: 640, videoHeight: 480, currentTime: 0 };
  let pos = [0, 0];
  // landmarks in video-normalised coords; the screen maps x mirrored with cover scaling
  const k = Math.max(W / 640, H / 480), dw = 640 * k, dh = 480 * k;
  const toNorm = ([sx, sy]) => ({ x: (W - sx + (dw - W) / 2) / dw, y: (sy + (dh - H) / 2) / dh });
  const handAt = ([x, y]) => {
    const hs = 90, P = Array.from({ length: 21 }, () => [x, y + hs]);
    P[0] = [x, y + hs * 1.6]; P[5] = [x - hs * 0.3, y + hs * 0.6]; P[17] = [x + hs * 0.5, y + hs * 0.9];
    P[4] = [x - 3, y]; P[8] = [x + 3, y];   // pinched
    return P;
  };
  const models = {
    hand: { detectForVideo: () => ({ landmarks: [handAt(pos).map(toNorm)] }) },
    face: { detectForVideo: () => ({ faceLandmarks: [] }) },
  };
  const tr = createTracker(video, models, {}); tr.size(W, H);
  const g = createGame("circle", GEOM, () => 0.5, { camera: true });
  const r = 0.84 * GEOM.R, w = speedR * GEOM.R / r;
  let maxStress = 0, now = 1000;
  for (let i = 0; i < 6 * 60; i++) {   // 60 Hz screen, 30 fps camera
    now += 1000 / 60;
    const t = i / 60, a = t * w;
    // the hand sits so that its needle tip (0.45 hand sizes ahead along P5->P8) lands near the outline; close enough
    pos = [GEOM.cx + r * Math.cos(a), GEOM.cy + r * Math.sin(a)];
    if (i % 2 === 0) video.currentTime += 1 / 30;
    const s = tr.update(now);
    const needle = s.needle && (s.fresh ? s.needle : { ...s.needle, held: true });
    step(g, 1 / 60, { needle, jitter: s.jitter, moveDt: s.vdt });
    maxStress = Math.max(maxStress, g.stress);
  }
  return { maxStress, g };
}

test("rushing on camera builds pressure like it does on touch", () => {
  const { maxStress } = run(1.35 * 1.3 * 1.6);
  assert.ok(maxStress > 0.5, `max stress ${maxStress.toFixed(3)}`);
});

test("a calm camera hand stays safe", () => {
  const { maxStress } = run(0.8);
  assert.ok(maxStress < 0.3, `max stress ${maxStress.toFixed(3)}`);
});
