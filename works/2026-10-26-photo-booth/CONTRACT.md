# Module contract — 따로 같이 네컷 site (2026-10-26)

Every implementer reads PLAN.md (Global Constraints, Shared numbers, Task 4-6), brief.md, research/frames-filters.md,
research/cutout.md (when present) and this file. Names and shapes below are fixed; change them only by editing this
file first. All modules are ES modules under `site/js/`, no build step, no npm. Allowed CDN: jsDelivr pinned
`@mediapipe/tasks-vision@0.10.14`, Google Fonts (Jua, Gowun Dodum, plus frame fonts listed in frames.js).

## Data

```js
// One shot of one person. Produced by capture.js, uploaded by app.js, consumed by compose.js.
Cut = {
  raw: Blob,            // image/jpeg, 4:3, <= 960x720, mirrored as previewed
  cut: Blob,            // image/png, same size, person with alpha (refined matte)
  clip: Blob,           // side-by-side [raw | matte] 2 s, each half 4:3 <= 640x480, even sizes
  clipType: "video/mp4" | "video/webm",
}
// What compose needs per person; app.js builds it from local Cuts or from Worker downloads.
PersonMedia = { raw: HTMLImageElement|ImageBitmap, cut: HTMLImageElement|ImageBitmap, clip: HTMLVideoElement|null }
Side = { cuts: PersonMedia[4] }
```

## env.js
```js
export const app;      // "Threads" | "Instagram" | "KakaoTalk" | "Facebook" | "브라우저"
export const inApp;    // boolean
export const isIOS;    // boolean
export const framed;   // top !== self
export function chromeUrl(url);   // KakaoTalk: kakaotalk://web/openExternal?url=…; Android: intent://…#Intent;scheme=https;package=com.android.chrome;end; else url
```

## camera.js
```js
export const camState;  // { mirror: boolean, front: boolean, track, caps }
export async function startCam(video, facing = "user");  // getUserMedia 4:3 ideal, widest zoom; 5 s no answer -> throws Error("stall"); NotAllowedError passes through
export async function flipCam(video);                      // front <-> back, mirror default follows
export function bindZoomFocus(stageEl);                     // pinch zoom + tap focus if caps allow; README in-app touch fixes
export function frameRect(video);                           // centre 4:3 crop rect of the current video {sx, sy, sw, sh}
```

## segment.js
```js
export async function loadSegmenter();          // MediaPipe ImageSegmenter, VIDEO mode, GPU then CPU; pinned model URL (float16/1)
export function segmentFrame(video, tsMs);      // runs the model, keeps the latest mask (texture or Float32Array + w,h)
export function drawMatte(ctx, w, h, mirror);   // draws the latest refined matte (white person on black) into ctx at w x h
export function drawCutout(ctx, video, w, h, mirror); // person with alpha (refined), for the live preview and stills
// refinement (guided/joint-bilateral upsampling, temporal smoothing, feather) lives here or in refine.js from research/
```

## capture.js
```js
export async function shootOne(i, { video, onTick });   // countdown 3 s (onTick(3|2|1)), recorder starts at 2, shutter at 0 -> Cut
export async function shootFour({ video, onTick, onCut }); // 4x shootOne, onCut(i, cut) after each
export const CLIP_MAX = { w: 640, h: 480 };  export const STILL_MAX = { w: 960, h: 720 };
```

## filters.js
```js
export const FILTERS;   // [{ id: "original"|"bbosasi"|"mono"|"warm"|"cool"|"film", label: "원본"|"뽀샤시"|"흑백"|... }]
export function createFilter();               // -> { apply(srcCanvas, id, strength = 1) -> canvas }  WebGL, ImageData fallback
// Parameters: research/frames-filters.md §1 build list (뽀샤시, 흑백 = must; warm/cool/film = extras). Grain seeded per cell.
```

## frames.js
```js
export const FRAMES;    // [{ id, label, fonts: [...], draw(ctx, layout, { date, cellRects }) }]  drawn around/over the cells
// At least: black, white, pink, sky, check, film (first six) + duo (우리 둘, remote concept) + coquette + purikura + strawberry
export async function loadFrameFonts(frame);   // document.fonts.load for the frame's fonts before drawing
```

## compose.js
```js
export const LAYOUT;     // { W: 600, H: 1800, M: 24, GAP: 16, FOOT: 200, CW: 552, CH: 382 } (frames may override cell rects, e.g. film)
export const BACKGROUNDS; // [{ id: "a"|"b"|"booth-purple"|"booth-pink"|"white-wall"|"mono"|"sky", label, draw?(ctx,w,h) }]
export function cellRects(frame);                      // 4 rects
export function cropRect(w, h);                        // centre crop of a 4:3 frame to CW:CH
export function drawCell(ctx, rect, i, { a, b, bg, t });  // t = clip time or null for stills; bg rule from PLAN Task 5
export async function drawStrip({ a, b, bg, frame, filter, date });         // -> Blob image/jpeg 600x1800
export async function recordMovingStrip({ a, b, bg, frame, filter, date, onProgress }); // -> { blob, type }, 6 s, 4 Mbps; webm retry; throws on failure
export function createClipAlpha();                     // WebGL: side-by-side clip frame -> RGBA canvas (premultiplied output), limited-range threshold
```

## api.js
```js
export const WORKER;     // from site/config.js: export const WORKER = "http://127.0.0.1:8787" (local) | "https://dot-lab-booth.<sub>.workers.dev"
export function newToken();                      // 32 random bytes base64url
export async function create(token);             // -> { id, expires }
export async function put(id, side, name, blob, type, token);   // retries 2x on network error only
export async function done(id, side, token);     // -> { ok } | { missing: [...] } | { error: "taken"|"not-ready"|"done" }
export async function status(id);               // -> { a:{done}, b:{done}, expires } | { error: "expired"|"not-found" }
export async function blobUrl(id, side, name);  // fetch -> blob -> objectURL
export function loadRole(id); export function saveRole(id, role, token);   // localStorage "booth:"+id = {role, token}
```

## app.js + index.html
Screens: landing (A / B variants), shoot, upload, share, waiting, done, expired, framed. Role table: PLAN Task 6.
