# 따로 같이 네컷: web watermark + Toss mini-app (P2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship two builds of the same `site/` code. The web build gets the F watermark, a Toss-only 메이킹 영상, and the Y2K/다이어리 decoration lock. The Toss build is a mini-app with no watermark, everything open, one rewarded ad shown while both videos render, and Toss save, share and back navigation.

**Architecture:** One new seam, `site/js/platform.js`, holds every web↔Toss difference as flags plus a few functions. The web build runs `site/` as it is (no bundler, as now). The Toss build (`toss/`, graduated from `toss-spike/`) copies `site/` raw into `toss/dist`. It then overwrites `js/platform.js` with a Vite-bundled `platform-toss.js` (the only file that needs the npm SDK) and `js/config.js` with `config-toss.js` (local library paths), copies the vendored libs and models, and runs `ait build`. Library URLs that are hard-coded in four files today move into `config.js`, so the swap is one file.

**Tech Stack:** plain ES modules + Canvas/WebCodecs (site), Vite 6 lib build + `@apps-in-toss/web-framework` 3.0.4 (Toss), Cloudflare Worker (`worker/`), `node:test`.

**Spec:** `C:\DC\DD\ideas\I-0054-photo-booth-apps-in-toss.md` (sections 3, 5, 6, and "웹판·토스판 차이"). Final mockups: https://claude.ai/artifact/7hELaqzHeshemqQ3719uzH and watermark F at https://claude.ai/artifact/GHG99n4PPDMhk4gYDVm8mT.

## Global Constraints
- Watermark F: a speech-bubble sticker, fill `#fff6c9`, 3px `#0d0d0d` line, white die-cut border, Jua font, text `따로 같이 네컷` plus `토스에서 검색!` in `#3182f6`, bottom-right of the footer, rotated about 5°. Web only. It goes on the still JPEG, every moving-strip frame, and the done-screen preview, but not on the mini tiles (`scale < 0.5`).
- Web: 움직이는 네컷 can be downloaded. 메이킹 영상 shows a preview only, with the ribbon `토스 전용` and the button `토스에서 받기`.
- Web lock: frames of the `y2k` and `diary` themes (12) and the sticker sets `y2k` and `diary` are visible with a `토스` badge. A tap opens the hint sheet and does not apply the item. Filters, outline, captions, backgrounds and retouch stay open.
- Toss: no watermark, nothing locked. The save sheet shows both videos side by side. Any tile tap opens the copy `만드는 동안 광고 하나만 봐 줄래?` / `그사이에 다 만들어 둘게! 광고가 이 앱을 무료로 지켜 줘요` / `[좋아, 2개 다 만들어 줘]`. One ad covers both renders. Save is enabled only after `userEarnedReward` and the finished render. If the ad is closed early: `광고가 끝나야 저장할 수 있어요` + `[다시 보기]`. Buttons read `보내기·저장`.
- Toss policy: no remotely loaded code (MediaPipe and onnxruntime are bundled), no external links (the `toChrome` escape and the `시켜본 것 더 보기` link are removed), light mode (default UI `cute` = 말랑 부스), the Toss nav bar's back = `history.back()`, test ad ID only (`ait-ad-test-rewarded-id`).
- The web site is live on push (Pages). The Worker deploys only through `wrangler`; operator OK'd the deploy for this plan on 2026-10-31 (it also ships the pending `devOf`). Push the site only after the Worker is live, or keep the site compatible with both (memory: Pages ships before the Worker). Rollback = `wrangler rollback`.
- Copy is Korean, code, comments and commits are English. No new dependencies in `site/`.


## Plan-review round 1 changes (2026-10-31, binding: these override the task text below)
Four lenses (zoom-out, naive implementer, failure, rules). Log: `docs/archive/2026-10-31-toss-plan-review/round1.md`.
1. **Order:** Task 9 (Worker) runs FIRST, before any push or Toss local run. Origins live in `worker/wrangler.toml` `[vars] ORIGINS` (comma list read at `index.js:55`), not in code: append `https://ttarogachi-test.private-web.tossmini.com,https://ttarogachi-test.private-apps.tossmini.com,https://ttarogachi-test.apps.tossmini.com,https://ttarogachi.private-apps.tossmini.com,https://ttarogachi.apps.tossmini.com,http://127.0.0.1:8803` (8803 = local Toss preview). Test: a request with each origin gets `Access-Control-Allow-Origin` = that origin; an unknown `*.tossmini.com` gets `allowed[0]`. Deploy, then live A→B on the current Pages site (old site + new Worker = compatible) before pushing the site.
2. **Seam = `platform.js` only** (no config swap, no parity check). `platform.js` exports `MP`, `ORT`, `MODELS`, `WORKER` (web = today's values, moved from the module constants and `config.js`; `config.js` re-exports `WORKER` from platform so `api.js` is untouched). `pet-worker.js` gets its bases as query params: `new Worker(new URL("./pet-worker.js?mp=…&deeplab=…", import.meta.url))`, read with `new URL(self.location).searchParams`. `matting-worker.js` gets `ort` inside the existing `load` spec.
3. **Toss bundle contents:** `assemble.mjs` deletes `dist/` first. ORT: copy every `node_modules/onnxruntime-web/dist/ort*.min.mjs`, `ort-wasm-simd-threaded*.mjs` and `ort-wasm-simd-threaded*.wasm` (the loaders the `.min.mjs` fetch from `wasmPaths`). MediaPipe: `vision_bundle.mjs`, `vision_bundle.cjs`, `wasm/`. Models: selfie, deeplab, modnet, face_landmarker. **Fonts self-hosted:** `scripts/fonts.mjs` fetches the two Google Fonts CSS URLs (`index.html:28-30`, `frames.js:11` FONT_CSS) with a Chrome UA, downloads every woff2, rewrites `url()` to `fonts/…`, writes `dist/fonts/fonts.css`; `assemble` rewrites both URLs to `fonts/fonts.css` (frames.js: `FONT_CSS` becomes `P.FONT_CSS`). Print the unpacked size per folder before `ait build`; over 95 MB → drop deeplab first (pet cut-out off in Toss) and report.
4. **`check.mjs`** (after build): fails if `dist/js/platform.js` lacks `id = "toss"`; any `eval(`/`new Function(` in our own files (`dist/js/**`, not vendor); any `https://` in `import(`/`importScripts(`/`<script src`/`<link href`/`@import`/`fetch(` call sites of our files other than the Worker; any `adGroupId` other than `ait-ad-test-rewarded-id`; size > 100 MB.
5. **Toss UI is cute (말랑 부스) only:** `assemble` patches `dist/index.html`'s inline `data-ui` script to always `cute`; `route()` skips the theme picker when `P.id === "toss"` (goes where `cute` pick goes).
6. **Entry link:** `platform-toss.js` exports `entryQuery(): URLSearchParams` = `new URL(Environment.initialURL).searchParams` (docs: launch params are in `Environment.initialURL`, not `location.search`); `app.js:41` `Q` = `P.entryQuery?.() ?? new URLSearchParams(location.search)`. `APP` = `import.meta.env.VITE_AIT_APP_NAME` (keep the spike's `define`).
7. **Ad gate (Task 8) rewritten:** `adgate.js` keeps only `{ earned: boolean, closed: boolean }` with `adOk(s) = s.earned` ; save allowed when `adOk && D[kind]?.key === vkey()` (the existing ready test, so a stale render is never offered). One ad per done-screen visit covers every later re-render (look changes do not ask again). `failed` on **load** (no fill / SDK error) sets `earned = true` (operator OK 10-31). Retry = `loadAd` then `showAd`; a load failure on retry also sets `earned`. In Toss: `D.auto` is never set (no auto-save), so `videoJob`'s auto-save path cannot fire; renders still pre-start as today (`wantVideo` when HQ lands) so they are often done before the ad ends. Test cases: earned→save; dismissed→retry needed; load-fail→earned; dismissed then retry load-fail→earned; earned survives a later dismiss.
8. **Save in Toss:** per-tile state `보내는 중…` with a busy lock (no second bridge call), photo button too. `P.videoBps` (web = current, Toss = 2.5 Mbps) to cut the ~3 MB / ~35 s moving-strip transfer (85 KB/s measured). Timeout none (bridge resolves), but log ms.
9. **Back in Toss:** no `pushState`. Subscribe `graniteEvent.addEventListener("backEvent")` (SDK `index.d.ts:2186`): open `.dsheet` → close it; else on done/share/wait screens → toast `한 번 더 누르면 닫혀요` and a second press within 2 s calls the SDK close-view API (name from `documentation/common/screen/close.md`); on shoot/upload screens → ignore (no half state). Verify on the phone that the nav-bar back fires `backEvent`.
10. **No web links in Toss:** in Toss, `saveSheet()` copies nothing and hides `#saveLink`; `share()`/`copy()`/`#linkText`/`sendUrl()` use `P.shareLink(S.id)` (Toss link) only; hide `#inappSave`, `.morelink`, `#openTab`, `.uihead` lab links.
11. **Watermark** is drawn once to a cached offscreen canvas per layout (`wmCanvas(L)`) and `drawImage`d each frame (no per-frame shadowBlur). Applied when `scale >= 0.5` (mini tiles use `scale` ≤ 0.3 in `renderMinis`; the done preview uses `into` at scale 1).
12. **Web lock (Task 4):** `D.frame` is never restored from links (only `own`), so no restore guard; the locks are UI-only plus a guard in `opts()` (`if (lockedFrame(D.frame)) D.frame = FR.FRAMES[0]`) as a belt. Making: gate `D.wantStory`/`saveVid("story")` at the source with `!P.makingLocked` instead of branching inside `videoJob`.
13. **Headless Toss run:** `platform-toss.js` falls back to logged stubs when `getOperationalEnvironment()` is not `"toss"` (save = log size, ad = 3 s fake with `userEarnedReward`, share = log) so a 430px CDP touch run covers tap → ad note → fake ad → render → 보내기·저장, with screenshots and contrast for the new texts (web hint sheet, lock badge, ad note, retry line).
14. **Phone checklist adds:** `document.hidden` + frames/s logged during the ad (render behind the ad); memory peak (both renders + HQ matte + ad, watch for white screen); back on every screen; save times for photo / moving / making.
15. **Wizard:** manual `.ait` upload in the console is the main path (it worked for the spike); the deploy key (console → workspace → 키 → API 키 발급, then `npx ait token add` once, `npx ait deploy`) is an optional stage that, once done, lets Claude upload future builds. Claude never runs `ait deploy` without the key the operator registered.

## Plan-review round 2 changes (2026-10-31, binding; override round 1 where they differ)
Log: `docs/archive/2026-10-31-toss-plan-review/round2.md`.
16. **Web changes stay dormant until the mini-app is public.** `platform.js` web: `tossLive = false` → `watermark`, `makingLocked`, `decoLocked` are all `tossLive || preview` where `preview = new URLSearchParams(location.search).has("toss")`. Live users keep today's app; the operator tests the web version with `?toss=1` (the flag rides along on share links via `uiQ()`-style param). Flip `tossLive = true` on release day.
17. **Entry link once:** `Q` = `location.search` if it has `s`, else `initialURL`'s params only when `sessionStorage["booth:entry"]` is unset (set it after reading). `fresh()` / 다시 찍기 then land on a clean start.
18. **Bundle budget (must stay < 100 MB):** ORT: only `ort.webgpu.min.mjs`, `ort.wasm.min.mjs`, `ort-wasm-simd-threaded.jsep.mjs`, `ort-wasm-simd-threaded.mjs`, `ort-wasm-simd-threaded.jsep.wasm`, `ort-wasm-simd-threaded.wasm`. MediaPipe: `vision_bundle.mjs`, `vision_bundle.cjs`, and from `wasm/` only the simd files. **Fonts are NOT self-hosted in this test build** (12 Korean families ≈ 30-50 MB); the private test needs no review. Before P3 submission: subset the canvas fonts to KS X 1001 Hangul with `pyftsubset` (task note in I-0054). `check.mjs` allows `fonts.googleapis.com` / `fonts.gstatic.com` `<link>`/CSS only. Print per-folder sizes.
19. **No raw `.mjs` in the Toss bundle** (Toss host MIME untested): copy as `.js`; ORT gets `wasmPaths = { mjs: <base>/ort-wasm-simd-threaded.jsep.js (or .js), wasm: <base>/…wasm }` per the chosen bundle; MediaPipe `import(MP + "/vision_bundle.js")` via a `P.MP_BUNDLE` name (web keeps `.mjs`).
20. **Platform paths at runtime:** `platform-toss.js` builds library/model URLs from `document.baseURI` (no `new URL(…, import.meta.url)` literals, so Vite lib mode inlines nothing).
21. **Workers keep today's CDN URLs as defaults** when no base arrives (cached old `segment.js` during the Pages window). Workers never import `platform.js`.
22. **Back:** every screen not in the done/share/wait/sheet rules calls `closeView()` on back, except shoot/upload (ignored while busy).
23. **Toss camera errors:** `camError` in Toss shows `토스 앱 설정에서 카메라 권한을 켜 줘` and no Chrome button; the `stall` path shows `다시 시도` only.
24. **Ad edge cases:** tap while loading → wait for `loaded` up to 5 s, then `earned = true`; `failedToShow` / show `onError` → `earned = true`.
25. **Watermark side from stickers:** right by default; left when any sticker's box overlaps the right footer corner, or for `WM_SIDE` frames. The side is part of the per-render key, so the cached sticker canvas follows.
26. **Stub detection:** real Toss = `loadFullScreenAd.isSupported()` inside try → true; otherwise stubs (headless/local).
27. **B test tomorrow:** link opening between two phones may not work before release. Test path: A in Toss on the operator's phone; B through the **web** link with `?toss=1` on a second device (same Worker). If a Toss share link opens on the second phone with the test app, also try that. Report which worked.

## Plan-review round 3 changes (2026-10-31, binding)
28. **B test path:** the `ttarogachi-test` build's share message also carries the web link `…/site/?s=<id>&toss=1` (flag `P.debugWebLink`, true only when `VITE_AIT_APP_NAME` ends in `-test`); strip before P3.
29. **Render behind the ad:** in Toss, a `videoJob` error while `document.hidden`, or a codec-reclaim error (`QuotaExceededError` / message contains `reclaim`), waits for `visibilitychange` to visible and retries once instead of setting `D.noVideo`. Log `e.name` + `document.hidden`.
30. **Toss origins are guesses:** the Worker also accepts `ORIGIN_RE` (new var, `^https://ttarogachi(-test)?\.[a-z-]+\.tossmini\.com$`); the phone log prints `location.origin`. Redeploy.
31. **`?toss=1` sticks:** `tossQ` is appended in `shareUrl`, `sendUrl`, `fresh()` and the `replaceState` at `app.js:886`.
32. **HQ landing in Toss:** `hqClips` also sets `wantStory` when `D.story` exists (both stay saveable after the ad).
33. `check.mjs` looks for the `"toss"` literal in `dist/js/platform.js`; the lib build uses `publicDir: false`.
34. Stubs per function: `saveBase64Data.isSupported()`, `loadFullScreenAd.isSupported()`, `getTossShareLink` in try.
35. Keep `site/js/vendor/mp4-muxer.mjs` as a copy for a week (cached old pages request it).

## File map
| File | Change |
|---|---|
| `site/js/platform.js` | **new**: web flags (`id`, `watermark`, `makingLocked`, `decoLocked`, `tossUrl`) |
| `site/js/config.js` | + `MP`, `ORT`, `MODELS` bases (web = today's CDN URLs) |
| `site/js/segment.js`, `beauty.js` | read `MP` and model URLs from config |
| `site/js/matting-worker.js`, `pet-worker.js` | receive `ort`/`mp`/`deeplab` bases in their first message instead of constants |
| `site/js/watermark.js` | **new**: `drawWatermark(ctx, L, frameId)`, `WM_SIDE` |
| `site/js/compose.js` | `paint` calls `drawWatermark` last when `P.watermark && scale >= 0.5` |
| `site/js/adgate.js` | **new**: pure state machine for "reward + render → save" (Toss) |
| `site/js/adgate.test.mjs` | **new**: `node --test` |
| `site/js/app.js` | making lock, deco locks, hint sheet, Toss save sheet and ad flow, Toss save/share, history push |
| `site/index.html`, `css/app.css` | hint sheet markup, lock badge, ad note, Toss tile states |
| `toss/` | **new** (from `toss-spike/`): `package.json`, `apps-in-toss.config.ts`, `vite.config.ts`, `src/platform-toss.js`, `src/config-toss.js`, `scripts/assemble.mjs` |
| `worker/src/index.js` + test | ORIGINS + Toss origins |

---

## Part 1: web build (live on push)

### Task 1: platform seam + library URLs in config
**Files:** create `site/js/platform.js`. Modify `site/js/config.js`, `segment.js:5-6`, `beauty.js:7`, `pet-worker.js:8-9`, `matting-worker.js:8,53-54`, and the posting sites of those workers (`segment.js:32,388`).

**Interfaces — produces:**
```js
// platform.js (web)
export const id = "web";
export const watermark = true, makingLocked = true, decoLocked = true;
export const tossUrl = "";   // the mini-app link once it is live; "" = say "토스에서 검색"
// config.js (added)
export const MP = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14";
export const ORT = "https://cdn.jsdelivr.net/npm/onnxruntime-web@1.22.0/dist/";
export const MODELS = { selfie: "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/1/selfie_segmenter.tflite",
  deeplab: "https://storage.googleapis.com/mediapipe-models/image_segmenter/deeplab_v3/float32/1/deeplab_v3.tflite" };
```
- [ ] Add the constants. Replace the module constants in `segment.js` and `beauty.js` with imports from `./config.js`.
- [ ] Workers: `pet-worker.js` is a classic worker and cannot import. Its `segP` becomes lazy, started by the first message `{ init: { mp, deeplab } }`. `segment.js` posts `init` right after `new Worker(...)`. `matting-worker.js` reads `m.ort` from the existing `load` message (`segment.js` adds `ort: ORT` to the spec it sends).
- [ ] Verify: serve `site/` (`python -m http.server 8802` in `site/`), then run the R6 e2e (`research/round6-themes/e2e-themes.mjs`, paths fixed to this checkout) → both themes end on the done screen, and the fast + HQ cut-outs log their ready lines. Network log: the same CDN URLs as before.
- [ ] Commit `refactor(booth): library URLs from config, platform seam`.

### Task 2: watermark F on stills and moving strips
**Files:** create `site/js/watermark.js`. Modify `compose.js:628-644` (`paint`) and `compose.js:563-580` (`prepare` font load).

**Interfaces — produces:** `drawWatermark(ctx: CanvasRenderingContext2D, L: Layout, frameId: string): void`. `WM_SIDE: Record<string, "left"|"right">` (default right; `lime: "left"`, because its date is right-aligned at `L.W - L.M - 24`).

```js
// watermark.js: the web build's "made with" sticker (I-0054 F). Drawn last in compose.paint, so stills, every
// moving-strip frame and the story's strip carry it; the Toss build never calls it (platform.watermark = false).
export const WM_SIDE = { lime: "left" };
export const WM_FONT = '40px "Jua"';
export function drawWatermark(c, L, frameId) {
  const w = 190, h = 74, pad = L.M + 10, left = WM_SIDE[frameId] === "left";
  const x = left ? pad : L.W - pad - w, y = L.H - L.M - h - 14;
  c.save();
  c.translate(x + w / 2, y + h / 2); c.rotate((left ? -5 : 5) * Math.PI / 180); c.translate(-w / 2, -h / 2);
  const bubble = () => { c.beginPath(); c.roundRect(0, 0, w, h, 22);
    const tx = left ? w - 46 : 34; c.moveTo(tx, h); c.lineTo(tx + 10, h + 16); c.lineTo(tx + 22, h); };
  c.shadowColor = "#0003"; c.shadowBlur = 10; c.shadowOffsetY = 4;
  bubble(); c.lineJoin = "round"; c.lineWidth = 16; c.strokeStyle = "#fff"; c.stroke();   // die-cut border
  c.shadowColor = "transparent";
  bubble(); c.fillStyle = "#fff6c9"; c.fill(); c.lineWidth = 4; c.strokeStyle = "#0d0d0d"; c.stroke();
  c.textAlign = "center"; c.textBaseline = "middle";
  c.fillStyle = "#0d0d0d"; c.font = '30px "Jua"'; c.fillText("따로 같이 네컷", w / 2, 28);
  c.fillStyle = "#3182f6"; c.font = '20px "Jua"'; c.fillText("토스에서 검색!", w / 2, 54);
  c.restore();
}
```
- [ ] In `compose.js`: `import * as P from "./platform.js"; import { drawWatermark, WM_FONT } from "./watermark.js";`. At the end of `paint`: `if (P.watermark && scale >= 0.5) drawWatermark(ctx, st.L, st.F.id);`. In `prepareNow`, when `P.watermark`, await `document.fonts.load(WM_FONT)` next to the frame fonts (Jua is already in `FONT_CSS`).
- [ ] Check sizes on `dev-frames.html`: all 19 frames × wide/tall, with the longest caption the app allows and a sticker in the bottom-right corner. Save screenshots, then look at each at full size (memory: inspect visuals full size). Overlap with a caption or date → add that frame to `WM_SIDE` or nudge `y`. Tall layout (1800×800): if the bubble reads too small, scale `w,h` and the fonts by `L.FOOT / 200`.
- [ ] `dev-compose.html`: render a moving strip, extract frame 0 and the last frame with ffmpeg → the bubble is in both, at the same pixels.
- [ ] Commit `feat(booth): web watermark sticker on stills and moving strips`.

### Task 3: making video = Toss only, plus the hint sheet
**Files:** `index.html:676-692` (save sheet) + a new `.dsheet[data-sheetid="toss"]`. `app.js:1294-1306` (`setVid`), `1382` (`saveVid`), `1389` (`saveSheet`), `ACT` (`1658+`). `css/app.css` near `.vtile` (`394-409`).

**Interfaces — produces:** `tossHint(title: string): void` opens the hint sheet. It is used by Tasks 3 and 4.
- [ ] Markup: the hint sheet has `<b id="tossTitle">`, the line `토스에서 하면 워터마크 없이 받아요` and a list (`메이킹 영상까지` / `프레임·스티커 전부` / `앱 설치 없이, 토스 앱 안에서`). When `P.tossUrl` is set, an `<a class="tossbtn" href>` reads `토스에서 「따로 같이 네컷」 열기`. Otherwise `<p>토스 앱에서 「따로 같이 네컷」을 검색해 줘</p>`. Also `닫기`.
- [ ] `#storyBtn` when `P.makingLocked`: ribbon `토스 전용`, `.lockveil` `미리보기만` over `#mkPv`, small button `토스에서 받기`, `data-act="tossMaking"` → `tossHint("메이킹 영상은 토스에서 받아요")`. The web never calls `recordMaking`, so remove the `story` work from the web `videoJob` path (save the render time). The preview `#mkPv` stays the existing still (it is not a video today; keep that). The sheet head becomes `영상 받아 가!`.
- [ ] Verify (e2e + screenshot of the open save sheet at 430px, CDP touch): tapping 메이킹 opens the hint, 움직이는 네컷 downloads an mp4 whose frame 0 shows the bubble, and no `recordMaking` call appears in the console log.
- [ ] Commit `feat(booth): making video is Toss-only on the web`.

### Task 4: decoration lock (web)
**Files:** `app.js:1145-1152` (`buildFrames`), `1081-1091` (`tile`), `1433-1449` (`buildStickers` + pills), `css/app.css`.

- [ ] `const lockedFrame = f => P.decoLocked && f.theme !== "clean";` and `const lockedSet = s => P.decoLocked && (s === "y2k" || s === "diary");`.
- [ ] `buildFrames`: keep the order (clean first, since `D.theme` is always `"clean"`). Locked tiles get `.locked` + `<i class="lk">토스</i>`. Their tap does `tossHint(`${f.label} 프레임은 토스에서 열려요`)` and leaves `D.frame` alone. The mini thumbnails still render, so people can see what they would get.
- [ ] Pills: locked sets get `.locked` (`· 토스` suffix in CSS). A tap → `tossHint(`${label} 스티커는 토스에서 열려요`)` and the grid stays. Default set when locked = `clean`.
- [ ] Guard: an old link whose saved state has a locked frame falls back to `FR.FRAMES[0]` (`D.frame ??=` at 1113 → check `lockedFrame`).
- [ ] Verify: screenshot of the frame tab and the sticker tab. Tap Y2K → hint and no change. Export → still the clean frame.
- [ ] Commit `feat(booth): lock Y2K and diary decorations on the web`.

### Task 5: web regression, then push
- [ ] Full e2e for both UI themes (film, cute) A→B→done. On the done screen: photo save, moving-strip save, hint sheets. In-app path (UA = KakaoTalk): the `#inappSave` escape still works and the watermark is the same.
- [ ] Contrast check on the new texts (memory: measure text contrast). Time to first screen under 2 s.
- [ ] Commit, push (Pages goes live), open the live URL once, and run one A→B on the live site against the deployed Worker.

---

## Part 2: Toss build

### Task 6: `toss/` assembly build
**Files:** create `toss/` (copy `package.json`, `apps-in-toss.config.ts` and `vendor.mjs` logic from `toss-spike/`), `toss/vite.config.ts`, `toss/src/config-toss.js`, `toss/scripts/assemble.mjs`.

```js
// config-toss.js: same exports as site/js/config.js, every library and model inside the bundle (no remote code)
export const WORKER = "https://dot-lab-booth.dotconnects.workers.dev";
export const MP = new URL("../vendor/mediapipe", import.meta.url).href;
export const ORT = new URL("../vendor/ort/", import.meta.url).href;
export const MODELS = { selfie: new URL("../models/selfie_segmenter.tflite", import.meta.url).href,
  deeplab: new URL("../models/deeplab_v3.tflite", import.meta.url).href };
```
- [ ] Read `site/js/config.js` at build time and fail the build if its export names differ from `config-toss.js` (a silent missing export breaks the app at runtime only).
- [ ] `vite.config.ts`: lib build of `src/platform-toss.js` → `dist/js/platform.js` (ES, SDK inlined), with `emptyOutDir: false`.
- [ ] `assemble.mjs` (runs before `vite build`):
  1. Copy `site/` → `dist/`, without `dev-*` and `dev-media/`.
  2. Overwrite `dist/js/config.js`.
  3. Copy `node_modules/@mediapipe/tasks-vision/{vision_bundle.mjs,vision_bundle.cjs,wasm/}` → `dist/vendor/mediapipe/`.
  4. Copy `node_modules/onnxruntime-web/dist/{ort.webgpu.min.mjs,ort.wasm.min.mjs,*.wasm}` → `dist/vendor/ort/`.
  5. Copy models into `dist/models/`: selfie and deeplab downloaded once into `toss/cache/`, plus modnet and face_landmarker from site.
- [ ] Scripts: `"build": "node scripts/assemble.mjs && vite build && node scripts/check.mjs && ait build"`. `check.mjs` fails on any `eval(` / `new Function(` in `dist/**/*.{js,mjs}`, on any `https://` script/import URL outside `WORKER`, and on unpacked size > 100 MB (print the size).
- [ ] Google Fonts: decide here. Fonts are not code, so keep the stylesheet unless the review rejects it. Log the outcome in I-0054.
- [ ] Verify: `npm run dev` (devtools mock) and `vite preview` of `dist/` in headless Chrome with a fake camera → A flow to the done screen, and the network log has no jsdelivr or storage.googleapis requests.
- [ ] Commit `feat(booth-toss): assemble site into an Apps in Toss bundle`.

### Task 7: `platform-toss.js` (save, share, env, back)
**Interfaces — produces** (the same names as web, plus functions; `app.js` branches on `P.id === "toss"`):
```js
export const id = "toss";
export const watermark = false, makingLocked = false, decoLocked = false, tossUrl = "";
export const AD_ID = "ait-ad-test-rewarded-id";
export async function save(blob, fileName) {}      // blob -> base64 -> saveBase64Data({ data, fileName, mimeType })
export async function shareLink(id) {}             // getTossShareLink(`intoss://${APP}/?s=${id}`) -> share({ message })
export function loadAd(onReady, onError) {}        // loadFullScreenAd
export function showAd(onEvent) {}                 // showFullScreenAd; events userEarnedReward | dismissed | failedToShow
```
- [ ] `app.js`: `download()` → `P.save` for each file when Toss. `share()` → `P.shareLink(S.id)`. `env` → `inApp = false` in Toss, so the handoff screens never show. Hide `#inappSave`, `.morelink`, `#openTab`. `S.ui` defaults to `cute` in Toss when there is no `ui=`.
- [ ] Back: in Toss, `show()` pushes `history.pushState({ screen }, "")` on every change of screen name. `popstate` → `show(prevScreen)`. The first screen does not push, so the nav bar's back closes the app. On the done screen, back closes an open `.dsheet` first.
- [ ] Copy: Toss save buttons read `보내기·저장`. The photo save button also reads `보내기·저장`.
- [ ] Verify with the devtools mock (`npm run dev`): save/share are called with the expected arguments (console), and back walks screens in reverse.
- [ ] Commit `feat(booth-toss): Toss save, share link and back navigation`.

### Task 8: one ad while both videos render (TDD: state machine)
**Files:** create `site/js/adgate.js`, `site/js/adgate.test.mjs`. Modify the `app.js` save sheet code (`setVid`, `saveVid`, `videoJob`) behind `P.id === "toss"`, plus `index.html` and `app.css` (`.adnote`).

**Interfaces — produces:**
```js
// adgate.js: may the Toss user save yet? Saving needs the reward AND the finished render, in either order.
// state: { ad: "idle"|"showing"|"earned"|"closed"|"failed", video: "idle"|"rendering"|"ready", story: same }
export const start = () => ({ ad: "idle", video: "idle", story: "idle" });
export function step(s, ev) {}   // ev: "go" | "earned" | "dismissed" | "failed" | "videoReady" | "storyReady" | "retry"
export const canSave = (s, kind) => (s.ad === "earned" || s.ad === "failed") && s[kind] === "ready";
export const needsRetry = s => s.ad === "closed";
```
- [ ] **Test first** (`node --test site/js/adgate.test.mjs`) with these cases:
  - `go` → ad `showing`, both `rendering`.
  - `earned` before `videoReady` → `canSave(video)` is false until `videoReady`, then true.
  - `videoReady` before `earned` → false until `earned`.
  - `dismissed` without `earned` → `needsRetry`, `canSave` false. `retry` → `showing`, and the renders are not restarted (they stay `ready`/`rendering`).
  - `earned` then `dismissed` → still earned.
  - `failed` (no ad fill, SDK error) → saving is allowed. Users are not blocked by our ad supply, and it is logged.
  - A second `go` is ignored.
- [ ] Run → FAIL. Implement `step` as a pure switch. Run → PASS.
- [ ] UI: tap either tile → `.adnote` sheet with the global copy. `[좋아, 2개 다 만들어 줘]` → `step("go")`, `P.showAd(...)`, and `videoJob()` for `video` then `story` (sequential, the existing job). Tiles show the existing `rendering` progress. When `canSave(kind)` → that tile button becomes `보내기·저장`. `needsRetry` → the line `광고가 끝나야 저장할 수 있어요` + `[다시 보기]` → `step("retry")` + `showAd`. Preload with `P.loadAd` when the done screen opens.
- [ ] Commit `feat(booth-toss): one rewarded ad while both videos render`.

### Task 9: Worker origins + deploy
**Files:** `worker/src/index.js` (ORIGINS), `worker/test/worker.test.js`.
- [ ] Test first: a request with `Origin: https://ttarogachi.apps.tossmini.com`, `https://ttarogachi.private-apps.tossmini.com` or `https://ttarogachi-test.private-web.tossmini.com` (from the spike log) gets the CORS header, and a random `*.tossmini.com` subdomain does not. Make the app names a list constant.
- [ ] `node --test` in `worker/` → all pass.
- [ ] Commit, then `wrangler deploy` (operator OK 10-31). Right after: one A→B on the live web site + `curl`-free check via python of an OPTIONS request with a Toss Origin. Broken → `wrangler rollback`, report.

### Task 10: `.ait` build + phone test
- [ ] `AIT_APP_NAME=ttarogachi-test npm run build` → `.ait`, and the `check.mjs` output (size, 0 eval, 0 remote code).
- [ ] Wizard `dot-lab/scripts/wizard-toss-app.bat` (from `wizard-toss-spike.bat`): stage 1 issue the console deploy key (console → workspace → API key / 배포 키; look up the exact menu in the Apps in Toss docs for `ait deploy` first) and save it to a git-ignored file the build reads; stage 2 `ait deploy` (or manual upload if the key path fails); stage 3 QR on the phone; stage 4 paste results back. Web test steps (live URL, two phones) go in the same wizard's last stage.
- [ ] Operator checklist (Android, QR):
  1. The first screen loads in under 2 s.
  2. A: camera → 4 shots → share link via the Toss share sheet.
  3. B opens that link → does `?s=` arrive? Log `location.href`.
  4. Done screen: all decorations open, no watermark.
  5. Ad flow: earn, close early, retry.
  6. Both videos saved through the share sheet.
  7. Back button on every screen.
  8. HQ cut-out with no white screen.
- [ ] Update I-0054 P2 with the results, then T-0162 `next_step`.

## Risks to watch
- The render may stall while the fullscreen ad covers the webview (background throttling). Measure in Task 10. If it stalls, the render finishes after the ad, and the flow is still correct because of `adgate`.
- B entry through `intoss://…/?s=`: the spike never opened a shared link (it only works after release or in the private test). If the query does not arrive, read it from the SDK's entry params. Look this up in the docs at Task 10.
- Toss review may reject the Google Fonts stylesheet or the pinch zoom in decorate (see I-0054 §3). The fallbacks are self-hosted fonts and +/− buttons.
