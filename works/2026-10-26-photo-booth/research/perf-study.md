# Perf study (2026-10-28): "폰이 뜨거워지고 랙"

Method: headless Chrome 4x CPU throttle (`Emulation.setCPUThrottlingRate`), 430x932 dpr 2, fake camera 640x480,
real GPU (ANGLE D3D11, Intel UHD 620), WebGPU on. Per screen: rAF fps of the page, long tasks (count / total ms /
max), GPU-process CPU %, live WebGL contexts / made, camera tracks, workers, CDP profile attributed to the nearest app
function. A/B = same build with a `?pf=` prototype flag (patches not committed; diff kept in the session scratchpad
`perf/prototypes.diff`). Live-cut modes were A/B/A/B-alternated in one page, 3 rounds x 6 s, so the GPU state is the
same. Absolute numbers are a throttled laptop, not a phone: read the ratios. Main-thread "busy %" from
`Performance.getMetrics` is not trustworthy under throttling and is not used.

## Per screen

| screen | cost now (4x) | cause (file:line) | fix | measured gain | quality risk |
|---|---|---|---|---|---|
| B pick grid, bg toggles, review, 보정 taps | **2 new WebGL contexts per composed cell**: 13 → 39 → 87 → 143 made; Chrome then **kills the oldest** — lost: segment.js live-cut GL, refine.js (HQ refiner), beauty.js, MediaPipe's own. bg toggles: 27 long tasks / 5.1 s, fps 6.6-19 | app.js:537 builds new `{cuts:[…]}` objects per call; compose.js:456 `alphas` WeakMap keyed on them → `createClipAlpha` each time | one shared `createClipAlpha` for the still path (sources(), t == null); per-person instances only for clip frames (`late` is per slot) | contexts 143 → **8**, none lost; bg toggles long tasks 5.1 s → **2.3 s**, fps 6.6 → 16.4; 보정 taps 3.9 s → 1.9 s | none (cut() result is drawn before the next call) — this is also a **bug**: after the pick a reshoot runs with a dead live-cut/HQ context |
| B camera, view b (live cut) | fps ~10, long tasks 3.9 s per 6 s (66 %), segMs 55-75 | app.js:374-389 `liveLoop` on rAF: redraws the cut-out (WebGL pass + readback drawImage) at display rate even with no new camera frame, and runs the model on every new frame (30/s) | (a) drive it with `requestVideoFrameCallback`; (b) model on every 2nd camera frame (15/s) | (a) fps 10 → 13.5, long tasks −12 %; **(a)+(b) fps 10 → 20.5, long tasks 3.95 s → 1.5 s (−62 %)** | (a) none; (b) the matte updates at 15 fps (capture's clip already holds the matte ~1/3 of frames); pet map every 16 instead of 8: no extra gain, skip |
| A/B pick (HQ of 8) | A: **8.2 s single long task**, fps 1.2; DeepLab loaded **twice** (page + pet-worker) | segment.js:485-506 `loadPet`/`petMask`: page DeepLab runs synchronously on the main thread per HQ still (+ its ~6 s GPU warm-up), segment.js:468 | stills use the worker: `addPet(c, m, await petMap(bitmap))` (clipmatte.js already does this); drop the page DeepLab | A pick fps 1.2 → **51.6**, long tasks 9.9 s → 0.27 s; shooting long tasks 19.3 s → 5.2 s; one fewer GPU model | none (same model). Warm the worker after the camera is up, not on the landing (warming on landing froze it: fps 1.3) |
| pick / review (camera left on) | camera + video decode live until upload: pick idle fps 15.9, review idle ~19 | app.js:912 is the only `track.stop()` (upload) | stop tracks in `pickScreen`, restart in `reshoot` | B pick idle fps 15.9 → **33.5**, review idle 19 → 39; HQ of 8 total 97 s → 51 s (less GPU contention); on a phone: the camera sensor/ISP off for the whole pick + 보정 time (the main heat source we cannot see headless) | none; reshoot pays a camera start (~1 s, no segmenter reload) |
| B landing (cover) | **fps 1.4-1.6** for the first 5 s | app.js:220 `ghostLook` → face landmarker load + GPU compile + 8 `beautyStill` while the cover animates (A's default look skin:1 = always) | A uploads its looked overlay (or B runs ghostLook after the camera is up, behind 카메라 켜는 중) | landing fps 1.5 → **24**, long tasks 750 → 130 ms (`?pf=noghost`) | none if A uploads the looked PNG/JPG; if deferred, the overlay switches to the looked one a little later |
| A/B camera start | one long task **9-12.8 s** (shader compile, behind 배경 지우개 준비 중) | segment.js:62-76 MediaPipe GPU delegate warm-up | keep GPU. CPU delegate (`?pf=segcpu`): start 9.3 s → 0.9 s but live segMs 55-75 → 120-245 ms (2-3x) | not worth it | worse live fps |
| A landing (cover animation) | style+layout 18-32 %, GPU-proc 50 %, 12 long tasks / 5 s | index.html:384-447 writes left/top/width/height (cqw) on ~10 elements every frame | stop after 2 loops (hold the last frame, the `RM` path) or animate with transform | reduced-motion floor: long tasks 1.3 s → 0.2 s, GPU 55 → 2 %. Caching `geo()` alone: **no gain** (the layout just moves) | none for "stop after 2 loops" |
| A prep + pick (HQ on all 8) | prep 5 s at fps ~1 (GPU 95 %), per HQ still 1.2-17 s under contention | app.js:471 `upgradeAll` cuts all 8 before the pick | after #1-#4 this is the remaining GPU block; picked-only (`?pf=hq4`) → pick fps 17 vs 1.2 but the HQ of the picks then lands in review | 4 MODNet runs saved; the 8→6 shots change saves 2 anyway | pick grid shows live cuts longer; operator chose "all 8" on 10-27 — decide with 8→6 |
| done: making strip | **14.9 s long task** | beauty.js:63 `facesOf` first detect (face landmarker GPU compile) on the done screen | `prefetchBeauty()` at the share/landing-of-done screen idle, or reuse look.json landmarks | not prototyped | none |
| video making | 119 long tasks / 48 s, max 8 s | beauty.js:249 `detect` per clip frame (55 %) + clipmatte | not prototyped; only when 보정 is on | — | — |
| A camera idle, view a, share, decorate idle | fps 33-50, no long tasks | `<video>` only | nothing to do | — | — |

## Leaks / always-on

- WebGL context churn on B (above) — the only real leak, and it destroys working contexts.
- Camera tracks stay live through pick + review (above).
- capture.js:136 warm-up `captureStream` track is never stopped (one idle canvas track for the page). Cheap; `stream.getTracks().forEach(t => t.stop())` after `R.stop()`.
- matting-worker + pet-worker stay alive for the page (by design, reused for the moving cut).
- Cover rAF loop stops on hidden screens (MutationObserver), CSS `infinite` animations on hidden screens: none running
  (all hidden screens are `display:none`). `?debug` interval: only with `?debug` (500 ms, cheap). No intervals otherwise.
- No `<video>` left playing after its screen.

## Fixes to apply, ranked

1. **Share one still `createClipAlpha` in compose.js `sources()`** — contexts 143 → 8, ends Chrome killing the live-cut /
   HQ / beauty contexts; B pick/review long tasks −55 %. ~6 lines.
2. **Stills' pets through pet-worker (`petMap`), no page DeepLab** — removes the 8 s freeze on the pick and one model; A
   pick fps 1.2 → 52. Warm the worker after the camera is up. ~5 lines.
3. **Live cut: `requestVideoFrameCallback` + model every 2nd frame** — view b fps 10 → 20, long tasks −62 %. 2 lines.
4. **Camera off after the last shot, back on for reshoot** — pick/review fps x2, HQ 97 → 51 s, and the camera (the
   phone's biggest steady heat source) off for the minutes of pick + 보정. ~3 lines.
5. **B landing: no face landmarker during the cover** (A uploads its looked overlay, or defer ghostLook) — landing fps
   1.5 → 24.
6. Cover: stop the animation after 2 loops. 7. Prefetch the face landmarker before the done screen. 8. Stop the warm-up
   capture track. 9. HQ picked-only: decide together with 8 → 6 shots.
