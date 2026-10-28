# Module contract — 따로 같이 네컷 site (2026-10-26)

Every implementer reads PLAN.md (Global Constraints, Shared numbers, Task 4-6), brief.md, research/frames-filters.md,
research/cutout.md (when present) and this file. Names and shapes below are fixed; change them only by editing this
file first. All modules are ES modules under `site/js/`, no build step, no npm. Allowed CDN: jsDelivr pinned
`@mediapipe/tasks-vision@0.10.14`, Google Fonts (Jua, Gowun Dodum, plus frame fonts listed in frames.js).

## Data

```js
// One shot of one person. Produced by capture.js, uploaded by app.js, consumed by compose.js.
Cut = {
  raw: Blob,            // image/jpeg, 4:3, <= 960x720 (shape tall: 3:4, <= 720x960), mirrored as previewed
  cut: Blob,            // image/png, same size, person with alpha (refined matte)
  clip: Blob,           // side-by-side [raw | matte] 2 s, each half 4:3 <= 640x480 (tall: 3:4 <= 480x640), even sizes
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
export function frameRect(video, shape = camState.shape);   // centre 4:3 (wide) / 3:4 (tall) crop rect of the current video {sx, sy, sw, sh}
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
export const LAYOUTS;    // Round 6 #9: { wide: LAYOUT, tall: { W: 1800, H: 800, M: 24, GAP: 16, FOOT: 208, CW: 426, CH: 568 } }
                         // every strip/video/story call takes opts.shape ("wide" default | "tall"); frame.rects = { wide, tall }
export const BACKGROUNDS; // [{ id: "a"|"b"|"booth-purple"|"booth-pink"|"white-wall"|"mono"|"sky", label, draw?(ctx,w,h) }]
export function cellRects(frame, shape = "wide");      // 4 rects (tall: side by side)
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

## Round 2 additions (2026-10-27, after the 11-reviewer pass — research/review/*.md)
- frames.js: `frame.draw(ctx, layout, { date, caption, cellRects })` — `caption` = optional one-line user text (<= 16
  chars, e.g. "D+100", "우리 오늘") drawn in the footer in the frame's style; when empty the footer shows "따로 같이 네컷".
  FRAMES order = display order, first = default: white, pink, check, coquette, strawberry, sky, film, duo, black,
  minimal, chrome, purikura, + new wanted (지명수배 meme), vs (게임 VS) frames.
- compose.js: `drawStrip({ ..., caption })`, `recordMovingStrip({ ..., caption, format = "strip" | "story" })`
  ("story" = 1080x1920, strip centred on a soft backdrop that matches the frame, for Instagram stories).
  `createClipAlpha({ choke = 0 })` — app.js uses `{ choke: 2 }` for B's overlay of A (edge decontaminated), compose `{ choke: 0 }`.
  `matchLuma(aCut, bCut)` optional helper used inside compose to even out the two people's brightness.
- api.js: `deleteLink(id, token)` -> DELETE /s/:id (A's token, or the finished B's) -> { ok } | { error }.
- Worker: DELETE /s/:id live (6 tests).

## Round 3 (2026-10-27, operator after the walkthrough)
Operator: no left/right split. A shoots the full frame freely (only a soft hint "친구 자리를 생각해서 한쪽으로 비켜서
찍어 줘", no dashed spot). B's camera is the full frame and A floats over it like a Photoism celeb frame. A is ALWAYS in
front of B. A's position is fixed by default; before sharing, A can switch on "친구가 나를 옮기고 크기 바꿀 수 있게".
Sticker outline around both cut-outs: colour chips, styles solid | dotted | glitter, width thin | normal | thick.
Cut-out: stills (the 4 shutter photos) get a heavy high-quality model; live preview + clips keep the light path.

- Worker (live, 7 tests): `POST /s {token, opts:{movable}}`; `GET /s/:id` -> `{..., opts:{movable}}` (default false).
  Round 6 #9 (not deployed yet): `opts.shape` "wide" | "tall" (anything else 400), GET echoes it, links without it read "wide".
  Optional side-b file `layout.json` (application/json, <= 4 KB, not needed for done): `{ a: { x, y, scale } }` =
  B's placement of A, x/y = offset as a fraction of the frame width/height, scale = factor (1 = as shot).
- api.js: `create(token, opts)`, `status()` returns `opts`, `put(..., "layout.json", blob, "application/json")`.
- compose.js: `drawStrip/recordMovingStrip({ ..., layout, outline })`; `layout` as above (null = as shot);
  `outline` = null | { color: "#ffffff"…, style: "solid"|"dotted"|"glitter", width: "thin"|"normal"|"thick" }
  applied to both cut-outs (in bg "a"/"b" the raw person also gets its cut drawn on top so it can carry the outline).
  Draw order everywhere: background -> B -> A (A in front).
- capture.js: `Cut.cut` comes from the HQ still model when ready (fallback: live matte), same names.
- compose.js Round 3 API (implemented 10-27, sheets in research/round3-compose/):
  `OUTLINE_COLORS` = [{ id, label, color: "#rrggbb" }] (흰색 분홍 노랑 하늘 민트 보라 검정), `OUTLINE_STYLES` =
  [{ id: "solid"|"dotted"|"glitter", label: 실선|점선|반짝이 }], `OUTLINE_WIDTHS` = [{ id: "thin"|"normal"|"thick",
  label: 얇게|보통|두껍게, px: 6|10|16 (strip px, independent of B's scale of A) }]. `outline.color` = hex (chip id also
  accepted); unknown values fall back to white / solid / normal.
  `layoutAnchor(cutImg)` -> { x, y } = A's centre-bottom as fractions of the cell (the strip crop of the camera frame;
  y may be a little > 1). Layout maths, cell space: A is scaled by `scale` about the anchor, then shifted by
  (x * cell width, y * cell height) — CSS on a cell-sized box: `transform-origin: anchor; transform: translate(x*w, y*h)
  scale(scale)`. Values are clamped (x, y to [-1, 1], scale to [0.25, 3]); |x|,|y| <= 0.002 and |scale-1| <= 0.005 = as shot.
  `bgAllowed(layout)` -> BACKGROUNDS ids; drops "a" once A is moved (A would show twice); drawCell falls back to "b".
  `drawCell(ctx, rect, i, { a, b, bg, t, layout, outline })`. `createClipAlpha().draw/still(src, { ..., outline, time })`
  returns a canvas with `.pad` px of margin when an outline is set (no outline: unchanged, pad 0).

## Round 4 (2026-10-27, operator: "실제 인생네컷처럼 8장 찍고 4장 고르기", 10 s per shot, B also 8 -> 4)
Research: research/booth-flow.md (Photoism: 8 shots, 8-10 s timer, pick 4, no retake; QR = strip + session video).
- Shooting: one tap starts 8 shots in a row, 10 s countdown each (big number, beeps 3-2-1, "n/8", last-shot thumbnail),
  2 s pre-roll clip per shot as before. No per-shot retake; one "전체 다시 찍기" allowed.
- Pick 4 of 8: grid of 8, tap to add/remove, order badges 1-4 = strip order, no time limit, "이 4장으로" when 4 chosen.
- A uploads only the 4 picked (same 12 names, cuts 1-4 in pick order).
- B's 8 shots: shot j shows A's cut (j % 4) floating (A's 4 picked cuts cycle twice). B picks 4; cell i = B's pick i
  + A's cut pairs[i] where pairs[i] = (shot index of pick i) % 4. B uploads pairs.json = [4 ints 0..3] (Worker live,
  9 tests) — repeats allowed.
- compose.js: `drawStrip/recordMovingStrip/drawCell({ ..., pairs })` — pairs = null (identity) or [4 ints]; A media for
  cell i = a.cuts[pairs[i]]; layout (B's move of A) applies per cell the same way.
- HQ cut-out (MODNet) runs only on the 4 picked stills, after the pick (progress shown), not during shooting.
- segment.js speed (research/cutout-speed/, research/round4-engine/): MODNet input 512x384, model self-hosted at
  site/models/modnet.onnx (LICENSE-MODNET.txt) + Cache Storage "booth-modnet-v1" (bump the name when the file changes).
  `prefetchStill()` -> Promise<boolean> = download (or cache) + session in the worker, no camera, no warm-up; call on
  the landing page and/or while shooting (state "loaded"). `warmStill()` -> Promise<boolean> = the first real-size run
  (WebGPU shader compile: the page freezes ~3-10 s), once, after the 8th shot ("8장 준비 중"); state "ready".
  `cutStill(canvas)` keeps its signature, warms up itself if needed, calls are queued in order (one at a time).
  `cutStills(canvases, onEach?)` -> Promise<(canvas|null)[]>, onEach(i, canvas|null, error) as each lands (null = keep
  the live matte). `stillBusy()` is a no-op kept for old callers. No WebGPU / failure: state "failed", cuts throw.

## Round 5 video (2026-10-27, research/round5-video/)
- compose.js `recordMovingStrip({ ..., fx, signal, mattes })`: frame-exact WebCodecs render (H.264 + vendored
  `js/vendor/mp4-muxer.mjs` 5.2.2, MIT) when VideoEncoder/VideoDecoder exist and the clips are fragmented MP4; else the
  MediaRecorder path as before. `fx[side](frame, tMs, video)` per decoded clip frame (frame = that clip's own canvas
  [raw | matte], tMs = clip time, repeats each loop). `signal` abort -> DOMException "AbortError". `mattes` = Map from
  clipmatte.js. `renderStats` = last render's ms / frames per clip. `readFmp4(buf)` exported.
- capture.js: clips recorded with WebCodecs (fragmented MP4, camera frame times) when available, MediaRecorder otherwise
  and for the empty-clip retry. Cut.clipType stays "video/mp4" | "video/webm".
- clipmatte.js `clipMattes(videos, onProgress) -> Map<video, matte>` (HQ moving cut); segment.js `matteFrame(bitmap)`.

## Round 6 UI themes (2026-10-28, operator: A picks the look first, B follows)
- Screen 0 (`#s-theme`, mockups/cute3.html 0): two live cards 필름 / 귀여운 -> `body.dataset.ui = "film" | "cute"`, then A's cover.
  `film` = 필름레일 (screens3.html concept 1) in `css/app.css` under `body[data-ui="film"]`; `cute` = 말랑 부스 (cute3.html
  concept 3) only in `css/cute.css` + `js/cute.js` (index.html links both), styling everything under `body[data-ui="cute"]`.
- Worker: `POST /s {token, opts:{movable, shape, ui}}`, `ui` enum "film" | "cute" (anything else 400), stored re-serialised,
  `GET /s/:id` echoes `opts.ui`, links made before it read "film". api.js `create(token, { ..., ui })`.
- The link carries cute in the query too (`?s=<id>&ui=cute`, film links stay `?s=<id>`), as does the Chrome hand-off
  (`&me=&bg=&ui=cute`): an inline script in `<body>` sets `body.dataset.ui` from it before first paint; route() then takes
  the Worker's `opts.ui` (B, A's own wait screen, done / decorate).
- Hooks (also the comment at the top of index.html), stable for both themes:
  `body[data-ui]`; `body[data-screen]` (theme | landA | mode | landB | shoot | pick | upload | share | handoff | msg | done |
  framed); `body[data-flow]` = "a" | "b" | "c" (c = decorate in Chrome, incl. the hand-off); `body[data-step]` = "0".."3" on
  the 4-step tracker (a: 찍기·고르기·보정·보내기, b: 찍기·고르기·보정·완성, c: 꾸미기 = 3; shoot 0, pick 1, the 보정 review
  on the shoot screen 2, upload / share / hand-off / done 3; absent on theme, landings, messages); one
  `<div class="uihead" aria-hidden="true">` first in every `.screen` (film fills it with "D:OT LAB · tag" + the rail, with
  cute it stays empty for cute.js; `:empty` = display none); `document` event `booth:screen` after every screen change
  (dispatched in a microtask, so the screen's text is set). Ids / classes the e2e uses keep their names and places.
- Decorate filter tab: chips compare `parseFilter(D.filter).id`; a 약/중/강 pill row `#filterLv` under them (filters.js
  `FILTER_LEVELS`, `filterId`), 중 when a filter is first picked, the level kept when switching filters, hidden on 원본; the
  filter tile previews render at the current level.

## Round 7 (2026-10-28, operator phone run; ROUND7.md)
- 6 shots per side (was 8), still pick 4; pick grid 2x3 (세로 3x2). B's shot j floats A's cut j % 4; pairs.json unchanged
  (4 ints 0..3). The Worker never counted shots (only the 4 picks are uploaded), so 8-shot links read as before.
- Nobody moves or scales A (A over A's own room showed A twice). Gone: B's mover, the "친구가 내 위치를 바꿀 수 있게"
  switch, B's layout.json upload, A's own size / place in decorate. B keeps its own size / place (`own.b`; a drag keeps
  B's anchor in the middle 70 % of the cell). New links are created without `opts.movable` (Worker default false), so
  the Worker refuses layout.json for them (403 "fixed"); an old movable link may still hold one, the Worker keeps
  accepting it unchanged and the app never reads it. compose.js ignores `layout` and `own.a`; `bgAllowed()` = all ids.
- compose.js keeps one cut-out GL context per side ("a" / "b") for the page, not one per person object (fresh person
  objects per pick / review cell and per 보정 change went past Chrome's live WebGL limit, which drops the oldest contexts
  silently: the face landmarker, so B's 보정 did nothing).
- `#revBg` on the 보정 review = the pick screen's background pills (same `S.pickBg`); `[data-act="toTheme"]` "‹ 테마" on
  `#s-landA` and `#s-mode` only.
