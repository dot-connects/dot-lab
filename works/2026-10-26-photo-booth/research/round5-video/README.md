# Round 5 video: smooth moving strip + HQ moving cut (2026-10-27, worker "video")

Operator item 7 (saved strip stutters on Android) and item 3 (moving cut-out poor).

## How it is measured

- Fake cameras `scratchpad/media/r5-a-noflash.y4m`, `r5-b-pos.y4m` = the Round 4 cameras plus two top-corner blocks
  whose luma steps every camera frame (made with ffmpeg `geq`, `16 + (n % 8) * 28`).
- `fps.py FILE clip|strip` reads those blocks: per clip / per strip cell, distinct camera frames per second, repeats,
  skipped camera frames, `cover` = camera frames shown / camera frames that passed. Strip mode needs bg `a` or `b`
  (that side's raw frame is the cell background). Self-check: ffmpeg 30 fps clip -> 30.0, `fps=15` copy -> 15.0.
- `bench.mjs <tag> capture <a|b> [throttle] [query]` shoots 4 clips on `dev-capture.html?fast&still=0&app` (`?app` = raw
  preview like app.js, so only capture.js segments) into `site/dev-media/r5/<tag>/`; `bench.mjs <tag> strip <bg>
  [throttle] [mr|hq]` renders the moving strip from them (`mr` forces the MediaRecorder fallback, `hq` runs the HQ moving
  cut first). Throttle = CDP `Emulation.setCPUThrottlingRate`. `ideal` = ffmpeg-made 30 fps fMP4 clips from the marker
  cameras (isolates the renderer from the clips).
- The machine was at 100 % CPU (8 cores, ~90 Chrome processes of parallel workers) for most runs: real-time numbers
  (capture, MediaRecorder) swing run to run; the offline render does not depend on it.

## Output (cause 2): real-time MediaRecorder vs frame-exact WebCodecs

| clips | path | throttle | length | distinct fps per cell (min / mean) | render |
|---|---|---|---|---|---|
| ideal 30 fps | MediaRecorder (before) | x1 | 1.57 s (of 6) | 11.9 / 13.0 | 28 s |
| ideal 30 fps | MediaRecorder (before) | x4 | 6.94 s | 4.2 / 4.4 | 16 s |
| ideal 30 fps | **WebCodecs (after)** | x1 | **6.000 s, 180 frames** | **30.0 / 30.0** (0 repeats) | 18 s |
| ideal 30 fps | **WebCodecs (after)** | x4 | **6.000 s** | **30.0 / 30.0** (byte-identical file) | 16 s |
| recorded (~9-15 fps) | MediaRecorder | x1 / x4 | 6.52 / 6.72 s | 5.7 / 1.8 mean | 12 / 22 s |
| recorded (~9-15 fps) | WebCodecs | x1 / x4 | 6.000 s | 9.3 / 9.3 mean (= the clips' own rate) | 16 / 17 s |

The only skips left in the WebCodecs output are the clips' loop seams (8-12 in 6 s). Render time is headless with a
software GPU on a saturated machine (~90 ms per output frame); every frame still lands.

## Source clips (cause 1)

Before: the capture loop ran the model on every rAF frame and MediaRecorder sampled the canvas on the wall clock.
Clips measured 5-14 distinct fps (segMs 43-50, the preview of dev-capture also segmenting) and 13-54 frames per 2 s.

After: requestVideoFrameCallback loop (every camera frame drawn), the model limited to a third of the time (matte half
holds its last mask), clip encoded with WebCodecs at the camera's own frame times (encoded / drawn ~ 0.9-1.0).

Same load, alternating old / new, x1 (`?app`): old cover 0.66-0.83 (mean 0.73), 20-25 fps; new cover 0.72-0.86
(mean 0.79), 18-24 fps over the true camera span. The fake camera itself delivered only ~25-28 fps here, and at x4
both versions fall apart (old: page did not reach the shoot in 4 min; new: 1-14 fps, segMs 65-1200). The capture gain
can't be shown well on this box; it matters where the model is slower than a frame (a phone).

## HQ moving cut (item 3)

`clipmatte.js`: MODNet (stills' worker, `segment.js matteFrame`) on one frame per 100 ms of each clip, after the clips
load on the done screen. Strip frames blend the two nearest keyframes, and keep the fast matte where it moved since
(research/video-matting `analyze.py` "fuse"; motion map at 1/4 size, HQ drawn over the fast matte with opacity 1 - m).
The video key changes when it lands and the strip video is made again.

- Headless A -> B walk (`e2e/`): first video 6.000 s / 180 frames, HQ mattes for all 8 clips 94 s later, HQ video
  re-rendered (`b-video-fast.mp4` vs `b-video-hq.mp4`, `e2e/sheet-fast-vs-hq.png`).
- `zoom-fast-vs-hq-base.png` (fast, HQ at t 0.4 s; fast, HQ at 2.0 s): HQ keeps the braids on top and at the side
  that the fast matte flattens; edges a little softer.
- Cost: MODNet ~0.9 s per keyframe in headless (96-128 s for 8 clips of 2-5 s); the render with HQ 27 s vs 14 s.

## Empty clip bug (merged e2e, 10-27)

B's shot 5 came out "0 KB" (a bare MP4 header): under load the WebCodecs flush took > STOP_MAX, the recorder finalized
with no chunks, `clip.size > 0` so no retry, and loadClips' one rejected clip dropped the whole video + story.
Fixed: the recorder waits up to STOP_EMPTY while it has no chunk (MediaRecorder rule), `clipOk` (>= 5 frames in the
fMP4 tables, else must play 0.1 s: Chrome fires loadeddata for a frameless MP4) decides the retry, and a clip that does
not load / decode leaves that person still in its cell (`bench.mjs bad strip a`: b2 = 700-byte header -> 6.0 s strip,
cells 30 fps; MediaRecorder path no longer hangs on it). Merged A->B walk on 8801: B's 8 clips 24-57 frames, video and
story both made.
