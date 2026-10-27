# Round 5 — operator's first real-phone run (2026-10-27 evening)

Operator feedback, verbatim intent:
1. Countdown 5 s; "3" stalled before "2" (encoder wait). — DONE in 9e105fd (lead).
2. Face retouch: skin smooth + bright tone AND face slim + eye enlarge. Choosable on the final decorate screen, and
   each person can set it on their own photos before sending (A before the link, B before upload).
3. Cut-out: background left around the person, body parts cut off, hair/edges rough, moving cut poor.
4. Tell B (and A when sharing) that after shooting + upload they decorate together.
5. Decorate screen faster; tap the photo to zoom (pinch/drag).
6. Stickers on the decorate screen: drawn set (photoism style) + emoji tab.
7. Downloaded video stutters — make it smooth.

## Shared interfaces (all workers code against these)

- `look` = `{ skin: 0|1|2, slim: 0|1|2, eye: 0|1|2 }` per side. Default `{skin:1, slim:0, eye:0}`.
  Uploaded as `look.json` by each side before its `done` (Worker accepts it on both sides since 9e105fd; ints 0..2,
  re-serialised; missing file = default).
- Beauty (`site/js/beauty.js`, worker: beauty):
  - `await beautyStill(raw: HTMLImageElement|Canvas, cut: same, look) -> { raw: Canvas, cut: Canvas }` — the same warp
    on both; skin/tone only on raw RGB (cut keeps its alpha, gets the same RGB treatment).
  - `await beautyFx(look) -> (frame: Canvas 2w x h [raw | matte], tMs) => void` — edits one decoded clip frame in place
    (warp both halves identically, smooth/tone the raw half). Null when look is all 0.
- Video (`compose.js recordMovingStrip`, worker: video): accepts `opts.fx = { a: fn|null, b: fn|null }` and calls
  `fx[side](frame, tMs)` on each decoded clip frame before alpha/compositing.
- Stickers (worker: deco): `opts.stickers = [{ kind: "svg"|"emoji", id: string, x, y, s, r }]` in strip px
  (600x1800; x,y = centre, s = size px, r = radians). `paint()` draws them last, in stills and in the moving strip.

## Test rig

- Local Worker: `http://127.0.0.1:8787` (lead runs it from the main checkout). Allowed site origins:
  127.0.0.1:8799 (main), 8801 video, 8802 matte, 8803 beauty, 8804 deco. Serve your worktree root with
  `python -m http.server <port> --bind 127.0.0.1`; site = `http://127.0.0.1:<port>/works/2026-10-26-photo-booth/site/`.
- Headless Chrome A/B walk: `C:\Users\ehdck\AppData\Local\Temp\claude\C--DC-DD\dd5f501f-171a-4f5a-9d24-ee18560f566d\scratchpad\e2e-r4.mjs`
  (copy it, change SITE port + OUT). Fake cameras in `...\scratchpad\media\person-a-noflash.y4m`, `person-b-pos.y4m`.
  `?fast` = 4 s shots for tests. 430x932 dpr 2, CDP touch.

## Status (2026-10-28, merged on main)

All seven items merged (beauty, deco, matte, video branches) and the A→B headless walk passes: 5 s countdown, look per
side, stickers, zoom, frame-exact video 6.000 s / 30 fps per cell, story, pairs, delete, cached revisit. Worker with
look.json deployed (version 9e27e0cd).

Open, check on a real phone (`?debug` shows the still-matte path):
- Under heavy load headless Chrome sometimes cancels B's `compose.js` fetch (net::ERR_ABORTED, canceled) and the
  failed URL stays failed in the module map. `load()` and capture's `imp()` retry (as is, then `?r=`), so the shoot and
  the moving strip survive; `clipmatte.js` imports compose statically, so the HQ moving cut is lost in that run.
- Camera-rate clips: gain unproven on PC (CPU-bound); HQ moving cut takes 1-3 min on a phone, no progress shown.
- WASM still-matte fallback: ~40 s for the 4 picks on a phone (estimate).
- Disabled "이 4장으로" / "저장하기" text contrast 2.44:1.
