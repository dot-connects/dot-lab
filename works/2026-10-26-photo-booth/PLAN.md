# 따로 같이 네컷 (#003) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A shoots four cuts (each with the 2 s before the shutter) and shares a link; B shoots beside A's cut-out; then
anyone with the link saves a 4-cut image and a moving 4-cut video on a chosen background.

**Architecture:** Static client on GitHub Pages (`site/`, ES modules, no build) does camera, cut-out (MediaPipe
selfie_segmenter) and compositing on the phone. A Cloudflare Worker (`worker/`) only stores files in R2, checks write
tokens and the A-then-B order; R2 lifecycle deletes after 7 days. In-app browsers shoot; saving happens in Chrome
(in-app saves were measured to fail).

**Tech Stack:** vanilla JS, Canvas 2D, WebGL, MediaRecorder, MediaPipe tasks-vision 0.10.14, Cloudflare Workers + R2.

**Spec:** `works/2026-10-26-photo-booth/brief.md`. Measured code: `spike/index.html`. Review log:
`C:\DC\DD\docs\archive\2026-10-26-photo-booth-plan-review\`.

## Global Constraints

- Claude creates no accounts and posts nothing. Cloudflare account + `wrangler login` done by the operator
  (`scripts/wizard-cloudflare.bat`, 2026-10-26; `npx wrangler@4 whoami` OK). No token or credential in the repo.
- No encryption (operator 2026-10-26). Disclose instead: the landing and the upload button say "올리면 이 링크를 가진
  사람은 7일 동안 볼 수 있어요. 7일 뒤 자동 삭제". Only the camera makes photos (no file upload).
- Pinned URLs: `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14`, model
  `https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/1/selfie_segmenter.tflite`
  (same bytes as `latest`, ETag 3b2e3e1c…, checked 10-26).
- Camera: never crop (stage aspect = video aspect), widest zoom on start, mirror toggle (front on, back off), front/back
  switch, pinch zoom + tap focus only where `getCapabilities()` offers them. Saved = what the preview showed.
- In-app (Threads/Instagram/KakaoTalk UA): shoot in-app; saves become "크롬에서 열어 저장하기" (`chromeUrl`). iOS in-app
  has no hand-off: show "공유 메뉴 → Safari로 열기" text. Camera request with no answer in 5 s → same hand-off.
- README "Touch input inside Threads / Instagram" fixes on the stage (pinch, tap).
- UI copy Korean, short, no score. Footer text: `따로 같이 네컷 · YYYY.MM.DD` (no names, no place names).
- Tests only for the Worker (state machine + boundary checks). Everything else is verified by running: headless Chrome
  430 px + CDP fake camera + CDP touch, then the operator's phone.

## Shared numbers (sources)

| What | Value | Source |
|---|---|---|
| Strip canvas | 600 × 1800 px | 2 × 6 in photo-booth strip at 300 dpi (the format of the 50 reference strips) |
| Strip layout | margin 24, gap 16, footer 200 → cell 552 × 382 | our layout; cell = (1800 − 24 − 200 − 3·16) / 4 |
| Cell fill | center-crop the 4:3 camera frame to 552:382 | our rule; same crop in preview and save |
| Person placement | as shot: each cut-out keeps its position in its own camera frame | our rule (preview = save) |
| Pre-roll | 2.0 s, recorder starts at countdown "2", stops at the shutter | brief |
| Moving strip | 6 s = each 2 s clip looped 3× | our rule |
| Link life / caps | 7 days; 3 MB per file; 12 files per side | brief + Worker |

## File map

`mockups/app/index.html` (T1) · `worker/{package.json, src/index.js, test/worker.test.js}` (T2) · `worker/wrangler.toml`
(T3) · `site/index.html`, `site/js/{env,camera,segment,capture}.js` (T4) · `site/js/compose.js` (T5) ·
`site/js/{api,app}.js` (T6) · `manifest.json`, `out/`, `reel/`, `site/sample-strip.jpg` (T8).

Order and gates: T1 ‖ T2 → T3 → T4 → (T1 pick) → T5 → T6 → T7 → T8. T4 does not wait for the pick; T5's colours,
frames and backgrounds do.

---

### Task 1: App-screen mockups on a live camera (operator picks)

**Files:** `mockups/app/index.html` (one file, inline CSS/JS, spike camera + segment code).

- [ ] Three directions as tabs, each with the shooting scene (live cut-out; A sees the dashed "친구 자리" on the right
  40 % of the frame; B sees A standing; 3-2-1; 1/4; thumbnails; mirror/flip) and the done scene (strip + background
  chips + frame chips + two save buttons + the 7-day disclosure line). A "부스": dark curtain, flash at shutter.
  B "다이어리": paper, stickers. C "필름": viewfinder, LED date.
- [ ] Five preset backgrounds as Canvas functions: 보라 부스, 분홍 부스, 흰 벽, 흑백, 하늘.
- [ ] Headless 430 px with fake camera; screenshots at dpr 2, crops checked at full size; body text contrast ≥ 4.5:1.
- [ ] Commit, push, give the operator the Pages URL `…/works/2026-10-26-photo-booth/mockups/app/`; record the pick in
  `brief.md` "시안".

### Task 2: Worker (TDD)

**Files:** `worker/package.json`, `worker/test/worker.test.js`, `worker/src/index.js`.

**API (Produces, used by `site/js/api.js`):**
```
Tokens are made by the client: 32 random bytes, base64url (43 chars). The Worker stores only SHA-256.
POST /s              {token}                       -> 201 {id, expires}                    (A)
PUT  /s/:id/:side/:name  Authorization: Bearer <token>, Content-Type matching name -> 204
POST /s/:id/:side/done   Authorization              -> 204 | 400 {error:"missing", names} | 409 {error:"taken"|"not-ready"|"done"}
GET  /s/:id                                         -> 200 {a:{done}, b:{done}, expires} | 404 | 410
GET  /s/:id/:side/:name                             -> 200 body, stored Content-Type, no-store | 404 (b before b.done) | 410
side a|b; name = `${cut}-raw.jpg` (image/jpeg) | `${cut}-cut.png` (image/png) | `${cut}-clip` (video/mp4 | video/webm), cut 1..4
```
There is no join step. Any B token may upload once A is done; B's files go to a staging folder named by the token hash
(`s/<id>/b-<hash16>/`), and `POST …/b/done` claims the B slot with compare-and-set. An abandoned or failed B upload
never blocks the link; if two friends both finish, the first `done` wins and the other gets `taken`. Staging leftovers
die with the 7-day lifecycle. Meta writes (create, A done, B done) use `onlyIf: {etagMatches}`; file PUTs never write meta.

- [ ] **Step 1: failing tests** `worker/test/worker.test.js`:

→ `PLAN-task2-code.md` § worker/test/worker.test.js (copy verbatim).

- [ ] **Step 2:** `node --test worker/test/worker.test.js` from the work folder (a directory argument fails on Node 24).
  Expected FAIL: cannot find `../src/index.js`.
- [ ] **Step 3: implement** `worker/src/index.js`:

→ `PLAN-task2-code.md` § worker/src/index.js (copy verbatim).
- [ ] **Step 4:** run the tests; expected 5 pass. Mutation check: drop the `onlyIf` option → "the first finished friend
  wins" must fail (checked 10-26 on this code). Fix code, not tests; list any deviation.
- [ ] **Step 5:** independent review of the `git diff` (the side that did not write it), one round, fix Highs.
- [ ] **Step 6:** commit `feat(photo-booth): worker for links, client tokens, staged B uploads, caps`.

Departures from the brief, recorded there in this task: no per-IP limit on `POST /s` (cost bound: 12 × 3 MB per upload
folder, Class A alert set by the operator); no per-link byte counter.

### Task 3: Deploy + real-browser smoke

**Files:** `worker/wrangler.toml`:
```toml
name = "dot-lab-booth"
main = "src/index.js"
compatibility_date = "2026-10-01"
[vars]
ORIGINS = "https://dot-connects.github.io,http://127.0.0.1:8799"
[[r2_buckets]]
binding = "BUCKET"
bucket_name = "dot-lab-booth"
```
- [ ] `npx wrangler@4 r2 bucket create dot-lab-booth`
- [ ] `npx wrangler@4 r2 bucket lifecycle add dot-lab-booth expire-7d s/ --expire-days 7 -y`, then
  `npx wrangler@4 r2 bucket lifecycle list dot-lab-booth` shows the rule.
- [ ] `npx wrangler@4 deploy` from `worker/`; put the `*.workers.dev` URL in `brief.md` and as `WORKER` in `site/js/api.js`.
  If wrangler asks to register a workers.dev subdomain, stop and ask the operator.
- [ ] Smoke in headless Chrome from `http://127.0.0.1:8799` (a browser, so CORS preflight with `Content-Type` is real):
  create → 12 PUTs with real types → A done → 12 B PUTs → B done → status → GET one file into a blob. All 2xx.
  Then real-R2 compare-and-set: 10 B tokens upload one full set each, fire 10 `done` at once → exactly one 204.
- [ ] Remove the untracked `.wrangler/` from the repo root and add `.wrangler/` to `.gitignore`. Commit.

### Task 4: Capture engine

**Files:** `site/js/env.js`, `camera.js`, `segment.js`, `capture.js`, harness `site/index.html`.

**Produces:**
```js
// env.js
export const app, inApp, isIOS;            // app: "Threads"|"Instagram"|"KakaoTalk"|"Facebook"|"브라우저"
export function chromeUrl(url);            // Kakao: kakaotalk://web/openExternal?url=… ; Android: intent://…;package=com.android.chrome;end ; else url
// camera.js
export async function startCam(video, facing = "user");   // -> {track, front, caps}; 5 s stall -> throws Error("stall")
export const camState = { mirror: true, front: true };
export function bindZoomFocus(stageEl, track, caps);       // pinch + tap focus; README touch fixes
// segment.js
export async function loadSegmenter();
export function drawMatte(video, mirror, matteCanvas);     // white person on black, same size as video
// capture.js
export async function shootFour({ video, stage, onTick, onCut });
// -> Cut[4], Cut = { raw: Blob(jpeg), cut: Blob(png), clip: Blob, clipType: "video/mp4"|"video/webm" }
// onTick(n, secondsLeft) for the countdown UI; onCut(i, cut) after each shutter (retake = call shootOne(i) again)
export async function shootOne(i, opts);                   // same as one step of shootFour
```
Every frame is first centre-cropped to 4:3 (some cameras ignore the 4:3 request and give 16:9), so preview, overlay and
saves share one frame shape. One recorder per cut: a canvas of two halves, each half the 4:3 frame scaled to at most
640 × 480 (even sizes; 1280 × 480 total stays under H.264 level 4.1), left = mirrored raw, right = matte.
`videoBitsPerSecond: 2_500_000` (2 s ≈ 0.6 MB, under the 3 MB cap). Recorder starts at countdown "2", stops at the
shutter; mime order from the spike (`video/mp4;codecs=avc1`, `video/mp4`, `video/webm;codecs=vp9`, `video/webm`).
`raw` = the 4:3 frame at the shutter scaled to at most 960 × 720 (JPEG 0.9), `cut` = the same size with the matte as
alpha (PNG). Any blob over 3 MB before upload → re-encode smaller (JPEG 0.8 / PNG at 720 × 540) instead of a 413 loop.

- [ ] Move spike camera/segment code without the spike's first-frame `flip` guess (A and B stand off-centre): pin the
  mask polarity from the selfie_segmenter model card / one fake-camera run, as a constant. Add `drawMatte` (alpha → grey).
- [ ] `capture.js` as above.
- [ ] Headless fake camera: 4 cuts, each blob > 0, clip `videoWidth` = 2 × `videoHeight` × 4/3, ≤ 1280.
- [ ] Phone, Threads in-app and Chrome: 4 cuts; each clip plays 1.6–2.2 s (`video.duration`; if webm reports Infinity,
  play to end and time it); left half mirrored when the toggle is on. Pass = all four lines true; record in `brief.md`.
- [ ] Commit.

### Task 5: Compose

**Files:** `site/js/compose.js`. Harness: a button on `site/index.html` that composes the cuts just shot as both A and B
(no saved test files).

**Produces:**
```js
export const BACKGROUNDS;   // [{id:"a"|"b"|<preset>, label, draw?(ctx,w,h)}], presets from the T1 pick
export const FRAMES;        // [{id, label, color, ink}] from the T1 pick
export const LAYOUT = { W: 600, H: 1800, M: 24, GAP: 16, FOOT: 200, CW: 552, CH: 382 };
export function cellRect(i);                                  // -> {x, y, w, h}
export function cropRect(frameW, frameH);                     // centre crop of the camera frame to CW:CH
export async function loadClip(blobUrl);                      // -> HTMLVideoElement, muted, playsinline, ready
export function drawCell(ctx, i, { aRaw, aCut, bRaw, bCut, bg });   // images or video halves, bg rule below
export async function drawStrip({ a, b, bg, frame, date }) ;  // -> Blob jpeg
export async function recordMovingStrip({ a, b, bg, frame, date }); // -> {blob, type}; 6 s
```
Background rule per cell: "a" → A's raw frame, then B's cut-out; "b" → B's raw, then A's cut-out; preset → `draw()`,
then A's and B's cut-outs. Cut-outs keep their own frame positions (A stood left of the dashed spot, B saw A and stood
right). Video: one WebGL program per strip turns a clip into an RGBA cell — colour from the left half, alpha from the
right half's green channel remapped for H.264 limited range: `a = clamp((g − 24/255) · 255/207, 0, 1)` — and the 2D strip
canvas draws that WebGL canvas per cell (no `getImageData` per frame). The remap is an edge threshold (the browser
already hands WebGL full-range RGB). Context `premultipliedAlpha: true`, so the shader outputs `vec4(rgb * a, a)`;
check edges on the white and the dark 보라 preset.

- [ ] `drawStrip`, all bg modes; screenshot dpr 2, crop edges 3×.
- [ ] `recordMovingStrip` at 600 × 1800, `videoBitsPerSecond: 4_000_000`; on recorder error retry with webm, then fall back
  to the JPG save with "이 폰에서는 영상 저장이 안 돼요". Plays 6 s, both people move, no ghost of the raw background on a white preset. Phone check
  first: 8 clips decoding at once on the operator's phone. If cells freeze or drift, record clips at 320 × 240 halves
  (Task 4 constant) and re-check; report before inventing another path.
- [ ] Commit.

### Task 6: Flow

**Files:** `site/index.html`, `site/js/api.js`, `site/js/app.js`.

**api.js** (`WORKER` from T3): `create(token)`, `put(id, side, name, blob, type, token)`,
`done(id, side, token)`, `status(id)`, `getBlobUrl(id, side, name)` — media are always fetched to a blob URL (no
tainted canvas, no Range needed). Uploads run one file at a time; `put` retries 2× on network errors only.

**Roles** (stored as `localStorage["booth:"+id] = JSON {role: "a"|"b", token}`; after `create`, `history.replaceState`
puts `?s=<id>` in the URL so a reload finds A's row):

| URL / state | Shows |
|---|---|
| no `?s=` | A landing → shoot → create(token) → upload 12 → done → share screen with the link |
| `?s=`, role a, `!a.done` | blobs in memory → "다시 올리기" (missing names from `done`); none → "다시 찍기" |
| `?s=`, `!a.done`, role b or none | "아직 친구가 올리는 중이에요" (A reopened a half-uploaded link elsewhere) |
| `?s=`, `a.done && !b.done`, role a | waiting: "친구가 아직 안 찍었어요" + the link again |
| `?s=`, `a.done && !b.done`, role b or none | B landing: A's cut 1, "친구가 옆자리를 비워 뒀어요", [바로 찍기]; small link "내가 보낸 링크예요" → waiting screen (A opening its own link in another app or Chrome never shoots by accident) |
| B mid-upload, failure | blobs in memory → "다시 올리기"; reload or another browser → B shoots again (staging, slot still free) |
| `?s=`, `b.done` (any browser) | done screen: compose + saves (this is how A and B save in Chrome) |
| B's `done` → 409 taken | "다른 친구가 먼저 찍었어요" + the done screen |
| 404 / 410 | "없거나 7일이 지나 사라진 링크예요" + [새로 찍기] (404 also covers links the lifecycle already deleted) |

There is no slot until B's `done`, so a peek, a camera stall, a hand-off to Chrome or a killed in-app browser never
blocks the link.

- [ ] Screens per the T1 pick; the disclosure line on the landing and next to the upload button.
- [ ] Shoot preview shows the strip-cell crop (dimmed bands, about 4 % top and bottom), so the preview is what gets saved.
- [ ] Share screen, from a tap on [링크 보내기] (share needs a gesture): `navigator.share({url, text})` if present → `navigator.clipboard.writeText` → the link as
  large selectable text with "길게 눌러 복사". Each step falls to the next on rejection; the link text is always visible.
- [ ] Inside a frame (`top !== self`, e.g. the blog embed): show only the title, `site/sample-strip.jpg` (placeholder = the promo hero strip until
  Task 8 replaces it) and "새 탭에서 열기"
  (content-system rebuilds blog iframes without `allow="camera"`, so the camera can never open there).
- [ ] B's shoot view draws A's cut `n-cut.png` (and A's clip, looping) at A's frame position over B's live preview, so
  B stands beside A.
- [ ] Done screen: render first (progress), then show the save buttons; changing background or frame re-renders
  (image at once, video on [영상 만들기], about 6 s); each button calls `click()` on an `<a download>`
  from its own tap. Names `ttaro-gachi-<date>.jpg` and `.mp4`/`.webm` from the clip type. In-app → one
  "크롬에서 열어 저장하기" (Android) or the Safari text (iOS).
- [ ] Upload failure keeps the blobs in memory and shows "다시 올리기"; a reload loses them (note in the copy).
- [ ] One-phone run: A in Threads (the real entry path: reel → profile link), B in KakaoTalk (link sent to self), both
  save in Chrome. Second link: A in Chrome, B in Threads. In the KakaoTalk B run, watch the overlay of A's looping clip
  next to the live cut-out and the recorder (all at once in a WebView): no freeze, fps noted.
  Pass = both files in the gallery, the video plays from the gallery and attaches to an Instagram story, B stands next
  to A in all 4 cells, the video moves in all 8 halves. Blob downloads in Chrome are measured here for the first time.
- [ ] Commit.

### Task 7: Ship checks

- [ ] Throttled 4G (CDP `Network.emulateNetworkConditions`): first screen readable ≤ 2 s; the segmenter loads only after
  the first tap.
- [ ] CDP touch (`Input.dispatchTouchEvent`): pinch and tap on the stage keep `scrollY` ≥ 1 and the page does not scroll.
- [ ] Contrast ≥ 4.5:1 on every text, both frames of each screen.
- [ ] iOS: borrow one iPhone for one run (Safari + one in-app). If none, the brief says so.
- [ ] Operator plays it twice with a friend → "한 번 더 하고 싶은가" answered in `brief.md` 결과.

### Task 8: Reel, manifest, blog, index tile

- [ ] Ask the operator how to film the reel (two real people needed): proposal = screen-record A in Chrome and B in
  KakaoTalk on one phone + the saved moving strip. Build with `tools/record_reel.py` patterns and the reel rules in
  `C:\DC\DD\references\techniques.md` §2.
- [ ] `manifest.json` per `content-system/src/content_system/lab_work.py` and #002's file: `slug`, `title` (one line),
  `link` (the Pages URL, reachable), `media.reel`, `cover`, `card`, caption ending "프로필 링크에서 바로 해 볼 수 있어.",
  `caption_threads` ending with the work URL, `blog_html` whose iframe `src` equals `link` (content-system rebuilds it;
  the work shows its framed view there). Set `status: "ready"` last: editing a ready manifest replaces the story.
- [ ] `thumbs/003.jpg` 600×600 (no title text), `index.html` tile first with `href="works/2026-10-26-photo-booth/site/"`,
  NEW badge moved, NEXT last; the work's start
  screen gets the "‹ 시켜본 것들" chip and the done screen "시켜본 것 더 보기 →" (README).
- [ ] `docs/mechanics.md` #003 row → used; `brief.md` 결과; commit, push.
