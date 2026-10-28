# iPhone audit (iOS Safari / iOS Chrome) — 2026-10-28

Read-only code audit. Range: iOS 18 and iOS 26. **Chrome on iOS is a WKWebView**, so it has the same engine and the
same API surface as Safari on that iOS version. The differences are in app-level behaviour only: the camera
permission prompt, downloads, and the share sheet. Not run on an iPhone.

## Table

| API | used where | iOS Safari | iOS Chrome | risk | fix idea |
|---|---|---|---|---|---|
| getUserMedia `facingMode/aspectRatio/width` ideal | camera.js:9-10 | yes (11+); ideal constraints are honoured loosely. A portrait phone gives a portrait track (e.g. 960x1280), and frameRect crops it | yes since iOS 14.3, when WKWebView got gUM [5]. iOS asks for the app's camera permission first, then the page prompt | ok | check on the device that the 4:3 crop of a portrait track looks right in 가로 mode |
| permission prompt | app.js:395 | asks per site. The "Allow" setting lives in aA → Website Settings, and the prompt can come back each visit | WKWebView prompts can repeat on every gUM call [5]. A flip (flipCam → new gUM) may prompt again | degraded | copy at app.js:396 says "주소창 옆 자물쇠", which is Chrome-Android wording. iOS needs different words |
| requestVideoFrameCallback + `mediaTime` | capture.js:208-224 | yes (15.4+). mediaTime on a camera stream is unverified; the code falls back to performance.now() | same | ok | — |
| WebCodecs VideoEncoder H.264 `avc1.42e01f/4d401f/640028`, `avc:{format:"avc"}` | capture.js:85-128, compose.js:729-735 | video WebCodecs since 16.4 [1]. Our 2 s clips and the final strip are encoded by WebCodecs, so iOS never relies on MediaRecorder output by default | same | ok (verify) | log `isConfigSupported` results on the device. The first passing codec decides everything |
| VideoDecoder + readFmp4 | compose.js:743-860 | 16.4+. readFmp4 reads **our own** fMP4 (mp4-muxer `fastStart:"fragmented"`), so iOS output parses the same as on Android | same | ok | — |
| MediaRecorder fallback (`video/mp4;codecs=avc1`, `video/mp4`) | capture.js:11, 52-79 (straight retry, shrinkClip, warm); compose.js:716-725 | 14.1+, records MP4/H.264 [6] (webm since 18.4). **Whether Safari's MP4 is fragmented in a form readFmp4 accepts is unverified.** If it is not, clipSource throws "clip not decodable" (compose.js:823) and that cell becomes a still | same | degraded | on the device, record one straight-retry clip and run `readFmp4` on it. If it returns null, decode via `<video>` (the real-time path) instead of failing the cell |
| WebGL2 + `precision highp` | segment.js:251, refine.js:72, compose.js:177-182 | WebGL2 15+. highp in fragment shaders is fine on Apple GPUs | same | ok | — |
| RGBA16F render targets | refine.js:74-75, 111 | EXT_color_buffer_half_float / float available on Metal-ANGLE | same | ok (verify) | `stats.webgl` already reports it |
| WebGL context count | segment, refine ×2, compose clipAlpha, beauty, filters, plus one per MediaPipe GPU task (×4 on the page, +1 in the worker) | WebKit caps the number of live contexts per page, and oldest lost | same | degraded | watch for `webglcontextlost`. Reuse a single refiner |
| MediaPipe tasks-vision 0.10.14, GPU delegate | segment.js:65-69 (selfie), beauty.js:44-50 (FaceLandmarker), segment.js:487-491 + pet-worker.js:20 (DeepLab) | runs. **Known bug: ImageSegmenter on the GPU delegate in iOS Safari returns scrambled multiclass categories; CPU is correct** [7]. DeepLab cat=8/dog=12 is a multiclass model, so the pet masks may be wrong | same | degraded | `delegate: isIOS ? "CPU" : "GPU"` for DeepLab (page + worker). The selfie model is 1-class, which is probably fine |
| classic Worker + `importScripts(blob:)` | pet-worker.js:13-15 | yes. GPU delegate inside the worker needs OffscreenCanvas WebGL, which arrived in 17.0 [2]; on 16.x it falls to CPU via the catch | same | ok | — |
| WebGPU (MODNet HQ) | segment.js:392-393, matting-worker.js:51 | **iOS 18: none, so WASM. iOS 26: WebGPU ships** [3]. WebGPU in workers is not stated there. Safari 26 WebGPU bugs: device lost, command-buffer stalls [8] | same | iOS 26 = verify | the code already falls back to WASM once on a run error (segment.js:398). A *hang* has only the 15 s timeout |
| onnxruntime-web 1.22 WASM | matting-worker.js:6-7 | SIMD 16.4+ [1]. GitHub Pages has no COOP/COEP, so no crossOriginIsolated, so **1 thread** [4]. The `ort.webgpu` bundle pulls `ort-wasm-simd-threaded.jsep.wasm` = **21.9 MB** (plain wasm 11.2 MB) [9] | same | degraded | on iOS 18 import `ort.wasm.min.mjs` (11 MB, no JSEP). Already measured: ~9 s per still on a phone CPU (app.js:689) |
| HQ moving cut (clipmatte) on WASM | app.js:1316, clipmatte.js:10, 44 | ~20 keyframes × 8 clips = ~160 MODNet runs. On iOS 18 WASM that is **many minutes** of 100% CPU, plus heat, battery, and a tab-kill risk | same | **blocker-ish (iOS 18)** | skip `hqClips()` when `hqPath()==="wasm"` (fast matte only), or cap it to a few keyframes |
| Canvas memory cap | ~40 `document.createElement("canvas")` sites, never shrunk | **iOS limits total canvas memory (224/256/384/576 MB seen) and logs "Total canvas memory use exceeds the maximum limit"; the canvas goes blank** [10]. 1080×1920 = 8.3 MB each | same | **blocker risk** | set `c.width = c.height = 0` on temporary canvases (stills(), small(), hqCut probe, clipOk, warm, stripCanvas temps); reuse named canvases as compose.js `canvas(name)` already does |
| Web Audio shutter | app.js:115, 135, unlocked at 247 in the tap | the unlock in the tap is correct. **The ringer/silent switch mutes Web Audio** unless `navigator.audioSession.type = "playback"` (17+) [11] | same | degraded | set `navigator.audioSession && (navigator.audioSession.type = "playback")` before ctxA() if a silent-mode shutter is wanted (it also pauses the user's music) |
| download via `<a download>` blob | app.js:1283-1286, 1638-1653 | iOS 13+ shows a "Download?" sheet. The file lands in **Files → Downloads, not Photos**. People expect the camera roll | blob downloads in WKWebView browsers were broken for years; reported OK on 17.3 [12] | **degraded (Safari) / risk (Chrome)** | iOS: `navigator.canShare({files})` then `navigator.share({files:[new File([blob],name,{type})]})`, which gives "이미지 저장 / 비디오 저장" into Photos. It must run inside the tap, so pre-render before the tap. The autoVid save (app.js:1346) happens outside a gesture: on iOS, show a "영상 저장" button instead |
| In-app detection | env.js:4-11 | Instagram/Threads(Barcelona)/KakaoTalk/FB are matched by UA. **Other iOS WebViews (Naver, Band, Line…) have no `; wv)` marker**, so they are treated as a normal browser | — | degraded | iOS in-app = `isIOS && !/Safari\//.test(ua)` (CriOS still carries `Safari/`) |
| hand-off `kakaotalk://web/openExternal` | env.js:16, app.js:188-193 | on iOS the code **never tries it** (hoGo/toChrome hidden on iOS) and shows the "공유 → Safari로 열기" text. Community reports say it works on iOS only when called synchronously in the tap [13] | — | degraded | try it on iOS KakaoTalk from the button tap (openOut is already sync in the click) |
| `googlechromes://` | — (unused) | real scheme: https → `googlechromes://host/path` [14]. Whether Instagram/Kakao WKWebViews allow custom-scheme navigation is unknown, and it fails if Chrome is not installed | — | — | optional second button after testing. `x-safari-https://` is reported to open Safari on iOS 15/17/18 (not 16) [15]; unverified from Instagram |
| gUM inside iOS in-app browsers | app.js:252 | KakaoTalk / Instagram on iOS: unknown. WKWebView gUM needs the host app's camera usage string and WebKit config | — | **unknown** | real-iPhone test. The shoot screen has no hand-off on iOS (camError → Safari text only) |
| Cache Storage for models | beauty.js:33, matting-worker.js:34 | yes, worker too. Private mode quota is small and `put` failures are caught | same | ok | — |
| localStorage | api.js:69-72, app.js:114, 139 | works in private mode since iOS 11 (ephemeral). All calls are wrapped in try | same | ok | — |
| `:has()`, cqw/cqh, container-type, svh, Google Fonts, zoom caps, VideoFrame(canvas), captureStream, toBlob, createImageBitmap, OffscreenCanvas 2D, module worker, clipboard | css/*, frames.js:11, camera.js:20, capture.js:109/136/259, matting-worker.js:63 | all supported on iOS 17+ (most on 15.4–16.4) [1][2] | same | ok | — |

## Peak memory estimate (iOS kills the tab at about 1–1.5 GB on 4–6 GB iPhones)

| item | ≈ MB |
|---|---|
| MediaPipe wasm instances: selfie, DeepLab (page), FaceLandmarker IMAGE + VIDEO, DeepLab (worker) = 5 × (9.4 MB code + heap) | 150–300 |
| ort JSEP wasm 21.9 MB + MODNet 26 MB (fetch buffer + session copy) + tensors | 100–150 |
| WebGL contexts / GPU textures (≈ 9 contexts) | 100–200 |
| 8 shots: clip ~0.6 MB, raw JPG + cut PNG ~1–2 MB, plus blob URLs + `<video>` elements | 30–60 |
| offline strip render: 8 VideoDecoders × 2–5 frames × (1280×480×4 = 2.4 MB) + 1080×1920 canvases | 60–120 |
| **peak (decorate + video render + HQ clips running together)** | **~450–850** |

Fits on paper; the canvas-memory and WebGL-context caps bite first.

## Change before an iPhone test (ranked)

1. **Saving into Photos:** on iOS, use `navigator.share({files})` (canShare check) inside the tap. Keep `<a download>` as the fallback. Remove the autoVid auto-download on iOS (no gesture).
2. **Skip the HQ moving cut on WASM** (`hqPath()==="wasm"`, i.e. iOS 18 and any WebGPU failure). Otherwise it is minutes of CPU and a tab-kill risk.
3. **Free temporary canvases** (`width = height = 0`) in capture.js `stills/small/hqCut/clipOk/warm` and the compose temps. This avoids the iOS canvas-memory cap and blank cells.
4. **DeepLab on the CPU delegate on iOS** (segment.js:491, pet-worker.js:20), because of the GPU multiclass bug [7].
5. On iOS 18, load `ort.wasm.min.mjs` instead of `ort.webgpu.min.mjs`: 11 MB less wasm.
6. **iOS copy:** the camera-permission text (Settings → Safari → 카메라, or aA → 웹사이트 설정), and the iOS in-app UA rule (`!Safari/`).
7. Optional: try `kakaotalk://web/openExternal` on iOS KakaoTalk from the tap. Set `audioSession.type="playback"` if the shutter must sound in silent mode.

## Only a real iPhone can tell

- gUM inside KakaoTalk / Instagram / Threads on iOS. How often the prompt repeats (Safari vs Chrome, on flip).
- Which avc strings pass `isConfigSupported`. Encode fps at 1280×480 with MediaPipe running.
- iOS 26 WebGPU MODNet: works, falls back, or hangs. Is navigator.gpu exposed in the worker?
- `readFmp4` on a Safari MediaRecorder clip. Canvas-memory warnings and peak memory over 8 shots + video render.
- The hand-off schemes from each in-app browser. Does `share({files})` offer "비디오 저장"? Shutter sound with the silent switch on.

## Sources

1. https://webkit.org/blog/13966/webkit-features-in-safari-16-4/
2. https://webkit.org/blog/14445/webkit-features-in-safari-17-0/
3. https://webkit.org/blog/16993/news-from-wwdc25-web-technology-coming-this-fall-in-safari-26-beta/
4. https://onnxruntime.ai/docs/tutorials/web/env-flags-and-session-options.html
5. https://developer.apple.com/forums/thread/670322 , https://github.com/home-assistant/iOS/issues/1671
6. https://webkit.org/blog/11353/mediarecorder-api/
7. https://github.com/google-ai-edge/mediapipe/issues/6142
8. https://bugs.webkit.org/show_bug.cgi?id=311598 , https://github.com/tensorflow/tfjs/issues/8733
9. https://data.jsdelivr.com/v1/packages/npm/onnxruntime-web@1.22.0?structure=flat , https://data.jsdelivr.com/v1/packages/npm/@mediapipe/tasks-vision@0.10.14?structure=flat
10. https://www.pqina.nl/blog/total-canvas-memory-use-exceeds-the-maximum-limit/ , https://bugs.webkit.org/show_bug.cgi?id=195325
11. https://developer.mozilla.org/docs/Web/API/AudioSession/type , https://bugs.webkit.org/show_bug.cgi?id=264473
12. https://bugs.webkit.org/show_bug.cgi?id=216918
13. https://devtalk.kakao.com/t/topic/150815
14. https://chromium.googlesource.com/chromium/src/+/main/docs/ios/opening_links.md
15. https://christiantietze.de/posts/2023/05/safari-for-mac-url-scheme/
