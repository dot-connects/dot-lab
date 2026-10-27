# Round 5 still cut-out (matte) — 2026-10-27

## 결론 (3줄)

1. 폰에서 본 세 증상(사람 주변 배경 남음·팔/손/옷 잘림·거친 머리)은 **빠른 라이브 matte(selfie 256)** 의 특징과 그대로
   맞는다. WebGPU 가 없으면(`--disable-gpu` 로 재현: `no webgpu adapter`) HQ(MODNet) 가 `failed` 로 끝나고 8장 모두
   라이브 matte 로 저장됐다. WebGPU 가 있어도 한 장 6 s 를 넘으면 그 장은 라이브 matte 였다(`still-timeout`).
2. 이제 WebGPU 가 없거나 깨지면 **같은 MODNet 을 WASM 으로** 돌린다(입력 짧은 변 256, 고른 4장만). PC 1.4~2.6 s,
   CPU 4배 감속 ≈ 8~10 s/장. 개·고양이는 **DeepLab-v3(2.8 MB) 를 MODNet 에 합쳐** 옆에 앉은 개도 남는다.
3. 결과(아래 표, 8장 평균): IoU 0.907 → 0.981(WebGPU) / 0.977(WASM), 배경 남음 4.87 → 1.18 %, 몸 잘림 5.08 → 0.92 %,
   경계 오차 0.184 → 0.097. 어느 경로였는지는 `stats.still.path` 와 `?debug` 배지로 보인다.

## 숫자 (bench.html, 960x720, PC headless Chrome / Intel iGPU)

fp = 배경 남음(GT 면적 대비 %), far = 그중 사람에서 16 px 넘게 떨어진 덩어리, fn = 몸 잘림(%), edge = 경계 띠 평균 |α−GT|.

| case | live (폰이 쓴 길) | HQ 이전 | HQ 이후 WebGPU | HQ 이후 WASM |
|---|---|---|---|---|
| person-a | 0.984 / fp 1.54 / fn 0.35 / e .134 | 0.995 / 1.34 / 0.28 / .039 | 0.995 / 1.06 / 0.21 / .041 | 0.984 / 1.96 / 0.10 / .093 |
| hair1 (긴 머리, 어두운 배경) | 0.989 / 0.83 / 0.68 / .135 | 0.989 / 0.90 / 0.76 / .117 | 0.989 / 0.68 / 0.66 / .117 | 0.991 / 0.70 / 0.51 / .110 |
| hair2 | 0.994 / 0.46 / 0.32 / .100 | 0.994 / 1.40 / 0.52 / .069 | 0.994 / 0.94 / 0.43 / .063 | 0.993 / 0.81 / 0.49 / .070 |
| hold-dog (개 들어 올림) | 0.976 / 1.52 / 1.17 / .142 | 0.986 / 0.72 / 0.66 / .085 | 0.985 / 0.63 / 0.65 / .092 | 0.952 / 1.75 / 2.84 / .117 |
| hold-cat | 0.988 / 0.98 / 0.38 / .098 | 0.985 / 1.43 / 0.86 / .085 | 0.985 / 1.02 / 0.86 / .069 | 0.985 / 0.58 / 0.93 / .082 |
| arms-books (팔 위로, 손 위 가장자리, 책 더미) | **0.892** / 2.23 / **6.86** / .202 | 0.978 / 2.01 / 0.39 / .057 | 0.978 / 1.78 / 0.38 / .061 | 0.970 / 2.51 / 0.05 / .085 |
| far-person (거리 인파 + 뒤의 다른 사람) | **0.766** / **29.03 (far 25.1)** / 0.79 / .291 | 0.964 / 0.45 / 2.72 / .143 | 0.964 / 0.39 / 2.73 / .152 | 0.986 / 0.40 / 0.49 / .126 |
| dog-beside (옆에 앉은 개) | **0.665** / 2.35 / **30.08** / .370 | **0.683** / 2.07 / **29.0** / .273 | 0.954 / 2.96 / 1.42 / .183 | 0.956 / 2.48 / 1.59 / .188 |
| **평균** | **0.907 / 4.87 / 5.08 / .184** | 0.947 / 1.29 / 4.40 / .108 | **0.981 / 1.18 / 0.92 / .097** | **0.977 / 1.40 / 0.88 / .109** |

시간(한 장, MODNet + 정리): WebGPU 0.7~1.2 s. WASM(단일 스레드, 짧은 변 256) PC 1.0~2.6 s(부하에 따라).
CDP CPU 감속 4배는 dedicated worker 에 걸리지 않아서 같은 모델을 페이지 스레드에서 따로 쟀다(`speed-main.html`):
352x256 **8.2~10.6 s**, 512x384 16~20 s → 폰 WASM 은 고른 4장만(≈ 40 s, 뒤에서), "조금만 기다리기" 는 WASM 일 때 45 s.

그 밖에 잰 것: int8 MODNet(Xenova `model_quantized`, 6.6 MB)은 2배 빠르지만 hair1 머리 절반·hold-dog 개 대부분을 잘라
**IoU 평균 0.81** → 버림. WebGPU 입력 512 는 far-person 에서 뒤 인파를 사람으로 잡아(fp 27 %) 384 유지.
알파 레벨 0.03/0.97 → **0.15/0.9**: 머리 주변 회색 안개가 줄고(fp 1.44 → 1.18) 경계 오차 같음, 0.25 이상은 머리결 손실.

시트: `sheet-before-after.png`(전체), `sheet-edges-crop.png`(머리·손 확대), GT 확인용 `gt-sheet.png`.

## 바뀐 것 (site/)

- `segment.js` — HQ 로드: WebGPU 어댑터 없음/세션 실패 → WASM, 워밍업·실행 중 WebGPU 오류 → WASM 으로 한 번 갈아탐.
  장당 timeout 6 s → WebGPU 15 s / WASM 60 s. DeepLab-v3 cat+dog 확률을 guided filter 로 사진 경계에 붙여 MODNet α 에
  max. `keepLargest(c, pet = c.pet)`: 개·고양이가 든 덩어리는 크기와 상관없이 남김. `stats.still.path`
  (`webgpu|wasm|live`), `stats.still.hq[]`(장별 경로·ms·pet), `?debug` 좌하단 배지.
- `capture.js` — `hqPath()` 추가(시그니처 변경 없음). `app.js` — WASM 이면 고른 4장만 자르고 그동안 "다듬는 중" 배지
  안 붙임, 기다리기 45 s.

## 한계

- 실제 폰 숫자는 아직 없다. 다음 폰 실행은 `?debug` 로 열어 배지의 `still webgpu|wasm|live` 와 `hq w:ms` 를 보면 된다.
- 실사진 6장의 GT 는 IS-Net ∪ MODNet(512) → trimap → ViTMatte-S(오프라인, `gt.html`) 라 MODNet 쪽으로 조금 기운다.
  합성 3장(arms-books, far-person, dog-beside)은 합성 α 그대로라 정확.
- 개 가장자리에 원래 배경색(청록)이 조금 묻는다(dog-beside fp 2.5~3 %): DeepLab 257 px 확률을 guided 로 붙인 한계.
  털 결은 ViTMatte(104 MB)가 필요(pet study) — 이번엔 안 함.
- 라이브 matte(촬영 중 미리보기·클립)는 그대로 — 영상 쪽은 다른 작업자 몫.
- WASM 멀티스레드는 cross-origin isolation(COOP/COEP) 이 있어야 해서 배포 헤더를 바꿔야 한다 — 안 함.

## 실행

`python -m http.server 8802 --bind 127.0.0.1` (worktree 루트), 이 폴더에서:
`node drive.mjs out.json "<base>/bench.html?p=live|hq[&scpu=1][&wshort=256][&slo=..][&tag=..]"`, `node sum.mjs out.json`,
`sheet.html?tags=a,b[&only=..][&crop=x,y,w,h]`, GT 다시 만들기 `gt.html` (`models/` 에 `isnet-general-use-floor.onnx`,
`vitmatte-s-d646.onnx` — `../pet-matting/models/` 에서 복사). `FLAGS=--disable-gpu` = WebGPU 없는 폰 재현,
`THROTTLE=4` = CPU 감속(페이지 스레드만). `media/`, `models/`, `gt/`, `cut/` 는 gitignore.

## 테스트 사진 (media/, Wikimedia Commons 1280px 썸네일)

| file | source | licence |
|---|---|---|
| person-a-2s.jpg, person-b-2s.jpg | VOA Our Voices selfie videos (`../cutout-speed`, scratchpad media README) | Public domain (US gov) |
| hair1.jpg, hair2.jpg | `../cutout.md` 표 | CC BY 2.0 / CC BY-SA 4.0 |
| hold-dog.jpg, hold-cat.jpg, white-dog.jpg | `../pet-matting/README.md` 표 | CC BY 2.0 / CC0 / CC BY 4.0 |
| arms-up.jpg | [Woman holding up arms (Unsplash)](https://commons.wikimedia.org/wiki/File:Woman_holding_up_arms_(Unsplash).jpg), Christopher Campbell | CC0 |
| books.jpg | [Heaps of books (Unsplash)](https://commons.wikimedia.org/wiki/File:Heaps_of_books_(Unsplash).jpg), Cristina Gottardi | CC0 |
| city.jpg | [City rush (Unsplash)](https://commons.wikimedia.org/wiki/File:City_rush_(Unsplash).jpg), Christopher Burns | CC0 |

모델: MODNet(Apache-2.0, site/models), DeepLab-v3 MediaPipe(Apache-2.0), GT 전용 IS-Net(Apache-2.0)·ViTMatte-S(Apache-2.0).
