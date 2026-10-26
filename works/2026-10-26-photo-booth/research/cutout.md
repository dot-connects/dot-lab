# 누끼(cut-out) 품질 조사 — 2026-10-26

## 결론 (3줄)

1. **실시간 미리보기 + 2초 클립 matte**: 모델은 지금 그대로(selfie_segmenter 256, 249 KB)에 `refine.js`
   (WebGL2 guided filter + EMA + 배경색 번짐 제거)를 붙인다. 머리 윤곽이 실제 머리선에 붙고 흰 벽 테두리가 사라진다.
   추가 비용 **프레임당 +8~10 ms** (PC iGPU 실측).
2. **셔터 스틸**: **selfie_multiclass_256 + `refine.js`** (Apache-2.0). 붉은 머리·어두운 배경에서도 잔머리 결이 살고,
   셀피 모델이 날리는 손·마이크까지 잡는다. 스틸 1장 seg ≈150 ms + refine ≈150 ms (iGPU), Pixel 6 공식 71 ms.
   대가: 모델 **16.4 MB** (10 MB 예산 초과) → 카메라가 켜진 뒤 뒤에서 받고, 다 받기 전에 찍으면 1번 결과로 저장.
3. RVM(TF.js)은 품질은 비슷하거나 조금 위지만 **GPL-3.0**, 스틸 1장 ≈1.5 s/회, 첫 실행 셰이더 컴파일 ≈60 s →
   채택하지 않음. MODNet·RVM ONNX는 ORT wasm 11 MB가 더 붙어 제외.

## 측정 (PC, headless Chrome, Intel UHD 620 / ANGLE D3D11)

| 단계 | 크기 | ms | 비고 |
|---|---|---|---|
| selfie_segmenter GPU (마스크 readback 포함) | 640×480 영상 | **20~22** | 첫 프레임 ~10 s(셰이더 워밍업), 5프레임 이후 평균 |
| 지금 앱 합성(2D canvas destination-in) | 640×480 | 5~13 | |
| `refine.js` (GPU sync 포함) | 640×480 | **8~10** | 작업 해상도 320 폭, 8 pass + 전해상도 1 pass |
| `refine.js` | 720×960 스틸 | 140~155 | 첫 호출(셰이더 컴파일 포함) |
| RVM tfjs int8, 매 프레임(재귀 상태 유지) | 640×480, ratio 0.4 | **643** | 라이브 불가 |
| selfie_multiclass GPU (마스크 6장 readback + 1−bg) | 640×480 | **153** | 첫 호출 21~24 s 워밍업 |
| RVM 스틸, 2회 반복 | 720×960, ratio 0.5 | **3,094~3,235** | 첫 실행 56~61 s (셰이더 컴파일) |

폰(Android 인앱) 기존 실측: selfie 39~55 ms/frame. 폰의 refine·multiclass 숫자는 **아직 없음** (아래 한계).

## 눈으로 본 결과 (3배 확대 크롭, `?crop=`)

- **검은 머리·흰 벽(hair2)**: 전 = 가장자리 6~8 px가 흐리고 보라 배경에서 흰 벽 회색 테두리가 보임. 후(guided) =
  머리선이 원본 머리 경계를 따라 날카롭게 붙고 테두리 없음, 대신 잔머리 한 올은 여전히 없음(마스크가 처음부터
  못 잡은 것은 필터가 못 살린다). RVM = 잔머리 결이 반투명으로 살아 있음, 가장 자연스러움.
- **인터뷰 영상(여러 명, 로고 배경)**: selfie는 손·마이크·곱슬머리 일부를 얼룩으로 날림, guided는 경계만 정리할 뿐
  빠진 부분은 그대로. multiclass+guided 와 RVM은 손·마이크·곱슬머리를 잡음.
- **guided filter의 한계**: 줄무늬 옷처럼 무늬가 강한 곳은 가장자리에 무늬 모양 톱니가 생긴다(가이드가 회색조
  1채널이라서). 컬러 가이드(3×3 공분산)는 폰에서 보이면 그때.
- **붉은 머리·어두운 배경(hair1, 어려운 쪽)**: selfie 전 = 머리 위로 어두운 배경이 네모난 덩어리로 딸려 옴.
  selfie+guided = 덩어리는 매끈해지지만 여전히 어두운 그림자 띠(마스크가 0.5 근처인 평평한 배경은 guided가 못 고친다,
  반경 밖이라). **multiclass+guided = 잔머리 한 올씩 살아 있고 그림자 없음** — 이번 비교의 최고. RVM = 잔머리는 살지만
  흰 배경 위에 연기 같은 밝은 결이 남음.
- multiclass 를 refine 없이 쓰면 배경 확률이 정확히 0이 아니라(≈0.07) 화면 전체에 흰 막이 낀다 → lo ≥ 0.1 필수.

## 모델 후보 (고정 URL, 크기 = HTTP Content-Length, 2026-10-26)

| 모델 | 입력 | 크기 | 라이선스 | 판정 |
|---|---|---|---|---|
| selfie_segmenter (square) `…/image_segmenter/selfie_segmenter/float16/1/selfie_segmenter.tflite` | 256² | 249,537 B | Apache-2.0 | **유지** (Pixel 6 GPU 35 ms) |
| selfie_segmenter_landscape `…/selfie_segmenter_landscape/float16/1/selfie_segmenter_landscape.tflite` | 144×256 | 250,177 B | Apache-2.0 | 4:3 세로 프레임엔 해상도 손해, 제외 |
| hair_segmenter `…/hair_segmenter/float32/1/hair_segmenter.tflite` | 512² | 781,618 B | Apache-2.0 | 셀피 마스크와 max 합치기(POC 옵션) → 어두운 배경 덩어리를 키워 **더 나빠짐**. 제외 |
| selfie_multiclass_256x256 `…/selfie_multiclass_256x256/float32/1/selfie_multiclass_256x256.tflite` | 256² | 16,371,837 B | Apache-2.0 | **스틸용 채택**, 예산 초과라 지연 로드. Pixel 6 GPU 71 ms |
| RVM mobilenetv3 TF.js int8 `github.com/PeterL1n/RobustVideoMatting/releases/download/v1.0.0/rvm_mobilenetv3_tfjs_int8.zip` | 임의 | zip 2,704,179 B (풀면 4.37 MB) | **GPL-3.0** | 제외 (GPL, 느림). 비교용으로 POC에만 |
| MODNet ONNX int8 `huggingface.co/Xenova/modnet/resolve/main/onnx/model_quantized.onnx` | 512 | 6,632,188 B | Apache-2.0 | ORT 11 MB 추가, 미측정 |

`…` = `https://storage.googleapis.com/mediapipe-models/image_segmenter`. 벤치(Pixel 6 GPU): square 35.2 ms,
landscape 33.6, hair 52.1, multiclass 71.2 ([MediaPipe Image Segmenter](https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter)).
런타임: tasks-vision 0.10.14 wasm 9.4 MB(이미 냄), tfjs 4.22.0 1.47 MB, onnxruntime-web 1.20.1 wasm 11.2 MB.
"Google Meet 모델"은 따로 공개되지 않았고, selfie_segmenter 가 그 계열의 공개판이다.

**Task 4 상수로 박을 사실:** tasks-vision 0.10.14 + selfie_segmenter는 `confidenceMasks.length === 1`, 그 하나가
**사람 확률**(높을수록 사람)이고 크기는 **입력 이미지 크기**(720×960 입력 → 720×960 마스크; 256 결과를 내부에서
늘려 줌). 스파이크의 첫 프레임 `flip` 추측은 지우고 `p = masks[0]` 고정.

## 추천 파이프라인

```
라이브(매 프레임)       video ─► selfie 256 (GPU) ─► Float32 mask ─► refine.js ─► 미리보기 합성
                                                              │ (EMA로 깜빡임 억제)
2초 클립 matte          같은 refine 결과의 alpha ─► 흰/검 matte 절반 (raw | matte), 실시간 녹화
셔터 스틸              960×720 ─► selfie_multiclass (1 − bg) ─► refine.js (temporal 끔, lo 0.15) ─► PNG alpha
  (multiclass 아직 로드 전) 라이브와 같은 selfie + refine 결과를 그대로 저장
```

- refine 기본값: radius 4 (320폭 작업해상도에서 = 전해상도 ~9 px), eps 1e-3, lo 0.15 / hi 0.85, temporal 0.5,
  decontam 1. lo를 올리면 침식(배경 덜 묻음), hi를 내리면 팽창.
- 클립 matte는 H.264 limited range 보정이 PLAN Task 5에 이미 있다 — refine의 alpha를 그대로 회색으로 그리면 된다.
- 모든 GPU 모델은 **첫 호출이 10~24 s**(셰이더 컴파일, iGPU). 라이브 selfie는 미리보기가 자연히 데워 주지만,
  multiclass는 로드 직후 **같은 크기 프레임으로 한 번 미리 돌려 둔다** — 안 그러면 첫 셔터가 수십 초 멈춘다.
- multiclass 결과는 마스크 6장: `p = 1 − confidenceMasks[0]`(배경). 사람 확률을 직접 주는 채널은 없다.
- 빛 번짐(light wrap)은 넣지 않았다 — 배경색 번짐 제거만으로 테두리가 사라졌다. 새 배경이 아주 밝을 때 경계가
  "오려 붙인" 느낌이면 그때 추가.

## 한계

- 모든 숫자는 PC iGPU 기준. 폰 GPU·인앱 WebView에서 WebGL2 + `EXT_color_buffer_float/half_float`가 없으면
  `createRefiner`가 throw → 호출 쪽에서 지금 방식으로 폴백해야 한다.
- refine 은 마스크가 못 잡은 부분(손, 마이크, 떠 있는 잔머리)을 되살리지 못한다. 그건 모델 몫(multiclass).
- RGBA16F 정밀도 때문에 eps < 3e-4 는 노이즈.
- 16.4 MB 지연 로드는 4G에서 수십 초 걸릴 수 있다 → 1~2컷은 selfie+refine로 저장될 수 있음(품질 차이 = 위 크롭).
- 폰 실측이 남았다: POC를 폰에서 열어 `selfie_multiclass` 선택 후 영상 버튼 → `seg_ms`, `refine_ms` 를 본다.

## POC

`research/cutout-poc/index.html` + `refine.js`. 실행: `python -m http.server <port> --directory research/cutout-poc`.
버튼: 사진 2장·영상 1개·카메라·파일. 행 = 전(지금 앱) / 후(refine) / RVM, 열 = 흰·진보라·무늬·알파. 슬라이더로
radius·eps·lo·hi, 체크박스로 깜빡임 억제·번짐 제거. headless용 쿼리: `?src=media/hair2.jpg&rvm=1&crop=x,y,w,h`,
`?src=media/interview.mp4&frames=90[&rvmLive=1&ratio=0.4]` → `window.__r`에 ms.
RVM 행은 `models/rvm/`이 있어야 돈다(`.gitignore`, GPL이라 저장소에 안 넣음): release의
`rvm_mobilenetv3_tfjs_int8.zip`을 풀어 `models/rvm/{model.json,group1-shard1of1.bin}`. tf.min.js 는 SRI 고정.

## 테스트 자료 라이선스

| 파일 | 출처 | 라이선스 |
|---|---|---|
| `media/hair2.jpg` (1280px 썸네일) | [Young woman with long black hair (14 June 2018)](https://commons.wikimedia.org/wiki/File:Young_woman_with_long_black_hair_(14_June_2018).jpg), Alex Neman | CC BY-SA 4.0 |
| `media/hair1.jpg` (1280px 썸네일) | [Pretty woman with long hair](https://commons.wikimedia.org/wiki/File:Pretty_woman_with_long_hair.jpg), Vladimir Pustovit | CC BY 2.0 |
| `media/interview.mp4` (26–32 s 구간, 4:3 크롭, 640×480) | [SBIFF 2022- LICORICE PIZZA - PAUL THOMAS ANDERSON](https://commons.wikimedia.org/wiki/File:SBIFF_2022-_LICORICE_PIZZA_-_PAUL_THOMAS_ANDERSON.webm), Lyn Fairly Media | CC BY 3.0 |

## 출처

- He, Sun, Tang, "Guided Image Filtering" (ECCV 2010 / TPAMI 2013); He & Sun, "Fast Guided Filter" (arXiv 1505.00996).
- Lin et al., "Robust High-Resolution Video Matting with Temporal Guidance" (WACV 2022, arXiv 2108.11515);
  README 의 downsample_ratio 표(축소 해상도가 256~512가 되게).
- MediaPipe Image Segmenter 문서(모델 표·카테고리·Pixel 6 벤치), 각 모델 카드.
