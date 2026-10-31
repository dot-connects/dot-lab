# 02. 흐린 사진을 맑은 사진으로: 방식·기술 조사

확인일 2026-11-01. 가격은 전부 이 날짜의 공식 페이지 기준이다. 표시 규칙: **[미확인]** = 찾지 못함, **[가정]** = 출처 없는 내 전제, **[제안]** = 출처 없는 내 판단, **[일반 지식]** = 이번에 출처를 확인하지 못한 배경 지식.

## 결론

- **그림자·건물·사람까지 바뀌는 그림은 생성 모델만 낸다.** 시판 앱의 재조명은 공개 문서 기준 노출과 색 조정이다.
- **처음 시험할 경로는 서버리스.** 기존 Worker 뒤에 Gemini Nano Banana 2.1을 두고, 브라우저 하늘 교체를 무료 기준선으로 같은 사진에 비교한다. 장당 약 $0.035.
- **비공개 사진의 약점이 분명하다.** 유료 Gemini도 55일 보관, OpenAI 기본 30일 보관, BFL 직접 API는 기본 학습 대상이다. 별도 승인 없이 무보관을 약속한 곳은 조사 범위에서 Cloudflare Workers AI뿐인데 입력이 512×512 미만으로 제한된다.
- **논문 쪽 "흐림 → 맑음 + 그림자" 전용 해법은 바로 돌릴 수 있는 것을 못 찾았다.** 가장 가까운 IntrinsicWeather도 공개 코드를 확인하지 못했다.

## 1. 시판 제품은 어떻게 하나

| 제품 | 공개된 방식 | 그림자 생성 | 출처 |
|---|---|---|---|
| Photoshop Sky Replacement | 하늘 마스크 + 전경 색·조명 조화. 경계 이동·페더링 슬라이더, 얇은 물체 주변 대비 보존 | 공개 설명 없음 [미확인] | [Adobe 튜토리얼](https://www.adobe.com/learn/photoshop/web/sky-replacement?ntd=1), [Adobe 도움말](https://helpx.adobe.com/uk/photoshop/desktop/effects-filters/artistic-stylize-filters/replace-the-sky-in-images.html) (도움말은 자동 접근 403, 검색 발췌만 확인) |
| Luminar Sky AI | 하늘 경계와 물을 인식해 교체하고 수면 반사 추가. Relight Strength = 노출, Relight Saturation = 하늘 색 유입, Relight Human = 사람을 새 하늘에 맞춤 | 문서는 노출·색만 언급. 그림자 생성 언급 없음 | [Skylum 매뉴얼](https://manual.skylum.com/ai/en/topic/sky-ai-tool), [Update 3](https://manual.skylum.com/ai/en/topic/luminar-ai-update-3) |
| Google Magic Editor / Reimagine | 영역을 지정하고 "golden sunset" 같은 문구 입력. 보이지 않는 SynthID 삽입. 처리 위치는 [미확인] (공식 페이지는 "right on your phone"이라 쓸 뿐 기술 문서 아님) | 비교 리뷰에 따르면 골든아워 편집이 하늘뿐 아니라 건물·물까지 따뜻하게 바꿈 | [Google Store](https://store.google.com/intl/en/ideas/articles/magic-editor/), [Android Authority](https://www.androidauthority.com/galaxy-s24-generative-edit-vs-google-magic-editor-3410818/), [PetaPixel](https://petapixel.com/2025/02/06/google-to-digitally-watermark-ai-adjusted-images-in-magic-editor) |
| Samsung Generative Edit | 네트워크·계정 필요, 결과 최대 12MP, 보이는 워터마크 | [미확인] | [Android Authority](https://www.androidauthority.com/galaxy-s24-generative-edit-vs-google-magic-editor-3410818/), [Android Central](https://www.androidcentral.com/apps-software/samsung-adds-watermark-ai-generated-images-wallpapers) |
| Lightroom 마스크 | Select Sky는 하늘 선택뿐. 조정은 사용자가 슬라이더로. 교체·재조명 기능 아님 | 없음 | [Adobe](https://helpx.adobe.com/lightroom-classic/desktop/process-and-develop-photos/masking.html) |

Samsung 자료는 Galaxy S24 시기 기준이라 현행과 다를 수 있다.

## 2. 고전 파이프라인 (브라우저에서 가능한 범위)

```
사진 → 하늘 분할 → 경계 다듬기 → 하늘 합성 → 전경 색 보정 → (수면 반사)
```

### 하늘 분할·경계 모델

| 모델 | 크기 (실측: HF 파일 목록) | 하늘 지원 | 비고 |
|---|---|---|---|
| SegFormer B0 ADE20K | quantized 4.4MB, fp16 7.9MB, 원본 15.3MB | 있음, label id 2 ([config](https://huggingface.co/Xenova/segformer-b0-finetuned-ade-512-512/raw/main/config.json)) | 입력 512×512, Transformers.js용 ONNX 제공 ([모델 카드](https://huggingface.co/Xenova/segformer-b0-finetuned-ade-512-512)). 라이선스는 이번에 확인 못 함, 공개 전 [NVlabs repo](https://github.com/NVlabs/SegFormer) 확인 필요 |
| MediaPipe DeepLab-v3 | 입력 257×257 | 문서 예시는 background, person, cat, dog, potted plant. sky 언급 없음 | [공식](https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter). 나머지 모델은 사람·머리카락 전용이라 사람 마스크에만 쓸 수 있음 |
| MODNet | uint8 6.6MB, fp16 13.0MB | 없음, 사람 매팅 용도 [일반 지식] | [ONNX](https://huggingface.co/Xenova/modnet) |
| BiRefNet lite | fp16 114.5MB | 일반 전경 분리 | [ONNX](https://huggingface.co/onnx-community/BiRefNet_lite-ONNX). 한 번에 받기엔 큼 |
| Depth Anything V2 small | q4f16 19.1MB | 깊이 추정 | [ONNX](https://huggingface.co/onnx-community/depth-anything-v2-small) |

- **속도:** 위 모델의 브라우저 측정치는 [미확인]. 찾은 수치는 데스크톱 임베딩 모델의 WebGPU 10~50ms 대 WASM 355~530ms뿐이고 ([HF 벤치](https://huggingface.co/spaces/Xenova/webgpu-embedding-benchmark/discussions/1)), 휴대폰 분할 수치는 없다. 직접 재야 한다.
- **WebGPU 범위:** Safari 26에서 iOS까지 지원 ([web.dev](https://web.dev/blog/webgpu-supported-major-browsers?hl=en), [Safari 정리](https://app.cinevva.com/news/2025-09-15-safari-webgpu)). iOS WebView는 미지원 보고가 있으나 날짜 불명 ([Apple 포럼](https://developer.apple.com/forums/thread/781602)). Threads 인앱 브라우저에서는 WASM 대체 경로를 전제해야 한다 [제안].
- **나무·머리카락 경계:** SkyAR은 coarse-to-fine 하늘 매팅 + 합성으로 조화를 맞춘다. 단 코드는 CC BY-NC-SA 4.0이라 비상업 전용이고 야간에는 실패할 수 있다 ([GitHub](https://github.com/jiupinjia/SkyAR)). 저해상도 마스크를 원본 크기로 올릴 때 guided filter로 원본 경계에 맞추는 방식이 흔하다 [일반 지식].
- **전경 조화:** 평균·분산을 맞추는 색 전이(Reinhard)가 가장 가볍다 [일반 지식]. Photoshop도 Luminar도 공개된 범위는 이 수준의 색·노출 조화다. 방향 있는 빛은 못 만든다. 이것이 사양 메모의 전제와 일치한다.
- **하늘 소스:** [Poly Haven](https://polyhaven.com/license)은 전부 CC0라 그대로 넣을 수 있다. Pexels 라이선스는 자동 접근이 막혀 확인 못 함.

## 3. 재조명 연구

| 방법 | 야외 장면 전체 | 라이선스 | 지금 돌릴 수 있나 | 출처 |
|---|---|---|---|---|
| IntrinsicWeather (2025) | **예.** 대규모 야외, 재질·기하·조명을 분해 후 텍스트 날씨로 재생성. 합성 38k + 실제 18k 데이터 | [미확인] | 공개 코드 확인 못 함 | [arXiv](https://arxiv.org/abs/2508.06982) |
| OutCast (2022) | **예.** 단안 깊이로 새 방향 그림자 예측, 태양색·구름도 다룸. 깊이만으로 광선 추적하기엔 품질이 부족하다고 명시 | [미확인] | 공개 코드 확인 못 함 | [arXiv](https://arxiv.org/abs/2204.09341) |
| LightIt (CVPR 2024) | 부분. shading + normal 조건, 그림자 포함 단일 반사 모델. OutCast로 데이터 확장 | [미확인] | [미확인] | [프로젝트](https://peter-kocsis.github.io/LightIt) |
| DiffusionRenderer (2025) | 동영상 G-buffer 기반. 야외 전용 여부는 초록에 없음 | NVIDIA Source Code License + SVD는 Stability Community License | 가능하나 VRAM 22GB 초과(4090 기준), inverse 약 2분. Cosmos판은 16GB 이상, 가중치 약 56GB | [arXiv](https://arxiv.org/abs/2501.18590), [repo](https://github.com/nv-tlabs/diffusion-renderer), [Cosmos판](https://github.com/nv-tlabs/cosmos-transfer1-diffusion-renderer) |
| WeatherWeaver (2025) | 동영상. 비·눈·안개·구름 합성·제거. 맑은 햇빛 그림자 주장은 없음 | [미확인] | [미확인] | [arXiv](https://arxiv.org/abs/2505.00704) |
| LightLab (2025) | 아니오에 가까움. 조명원 단위 제어(창·램프 등) | [미확인] | 공개 코드 확인 못 함 | [arXiv](https://arxiv.org/abs/2505.09608) |
| IC-Light | 아니오. 입력이 전경 이미지 | HF 카드는 OpenRAIL-M, 커뮤니티 미러는 Apache-2.0으로 표기가 엇갈림. 번들 RMBG 1.4는 비상업 | 가능, ComfyUI 지원 | [repo](https://github.com/lllyasviel/IC-Light), [HF](https://huggingface.co/lllyasviel/ic-light/blob/main/README.md), [ComfyUI wiki](https://comfyui-wiki.com/en/models/ic-light) |
| SwitchLight (CVPR 2024) | 아니오. 인물 전용 | 소프트웨어 약관 [미확인] | 가중치 공개 확인 못 함, Beeble 상용 데모 | [arXiv](https://arxiv.org/abs/2402.18848) |
| Relightful Harmonization (CVPR 2024) | 아니오. 인물 배경 교체 조화 | [미확인] | [미확인] | [arXiv](https://arxiv.org/abs/2312.06886) |
| Neural Gaffer (NeurIPS 2024) | 아니오. 단일 물체 + 환경맵 | [미확인] | 코드 공개 | [repo](https://github.com/Haian-Jin/Neural_Gaffer) |

핵심 근거 하나: 야외 장면에서 ControlNet 계열은 전체 빛 방향은 그럴듯하지만 그림자가 충분히 단단하지 않다는 보고 ([arXiv 2409.08947](https://arxiv.org/pdf/2409.08947)). 범용 편집 모델도 같은 약점을 가질 수 있다 [제안], 시험에서 봐야 할 첫 항목이다.

## 4. 생성 편집 API (가격은 2026-11-01 공식)

| 모델 | 가격 | 해상도 | 지연 | 얼굴·동일성 |
|---|---|---|---|---|
| Gemini Nano Banana 2.1 | 출력 $0.0336/1K, $0.0504/2K, $0.113/4K. Batch 절반 | 1K~4K | [미확인] | 공식 표현 "multi-turn character consistency". 수치 없음 |
| Gemini 3.1 Flash Lite Image | $0.0336/1K | 1K만 | "ultra-low latency", 수치 없음 | [미확인] |
| Gemini 3.1 Flash Image | $0.067/1K, $0.101/2K, $0.151/4K | 512~4K | [미확인] | [미확인] |
| Gemini 3 Pro Image | $0.134/1K·2K, $0.24/4K, 입력 $0.0011/장 | 1K~4K | [미확인] | [미확인] |
| OpenAI GPT Image 2.5 | 입력 $8, 출력 $30, 텍스트 $5 (100만 토큰당) | 긴 변 3840, 800만 화소대. 2560×1440 초과는 실험적 | 복잡한 프롬프트 최대 2분 | `gpt-image-2`는 입력을 항상 고충실도 처리. 공식 한계에 "일관성 유지 어려울 수 있음" |
| FLUX.1 Kontext [pro] / [max] (BFL) | 4 / 8 credit = $0.04 / $0.08 | [미확인] | [미확인] | [fal 소개](https://fal.ai/models/fal-ai/flux-pro/kontext)는 인물 보존을 강점으로 주장, 수치 없음 |
| FLUX.1 Fill [pro] | 숫자 표시가 깨져 [미확인] | [미확인] | [미확인] | 인페인팅이라 마스크 밖은 보존 |
| Workers AI FLUX.2 klein 9B | 첫 MP $0.015, 추가 MP $0.002, 입력 MP당 $0.002 | 출력 256~1920, **입력 512×512 미만**, 4스텝 고정 | [미확인] | [미확인] |
| Workers AI FLUX.2 klein 4B | 입력 타일 $0.000059, 출력 타일 $0.000287 (512×512 타일) | 입력 제한 동일 여부 [미확인] | [미확인] | [미확인] |
| FLUX.1 Kontext [dev] 자체 호스팅 | 비상업 라이선스, 상용은 별도 계약 | 로컬 | 하드웨어 의존 | 위와 같음 |

출처: [Gemini 가격](https://ai.google.dev/gemini-api/docs/pricing) (2026-10-30 갱신), [Gemini 이미지 문서](https://ai.google.dev/gemini-api/docs/image-generation), [OpenAI 가격](https://platform.openai.com/docs/pricing), [OpenAI 이미지 가이드](https://developers.openai.com/api/docs/guides/image-generation), [BFL 비용](https://help.bfl.ai/articles/7986977817-what-are-the-costs-associated-with-using-your-models), [BFL 문서](https://docs.bfl.ai/quick_start/pricing.md), [Workers AI 가격](https://developers.cloudflare.com/workers-ai/platform/pricing/), [Workers AI 변경기록](https://developers.cloudflare.com/changelog/2026-01-28-flux-2-klein-9b-workers-ai/), [Kontext dev 카드](https://huggingface.co/black-forest-labs/FLUX.1-Kontext-dev).

Gemini 이미지 모델은 전부 무료 등급이 없다 (같은 가격 페이지). Workers AI는 하루 10,000 neuron 무료.

### 장당 비용 계산

| 경로 | 계산 | 장당 |
|---|---|---|
| Nano Banana 2.1, 1K | 출력 1120토큰 × $30/M = $0.0336 + 입력 560토큰 [가정, Pro 문서값 차용] × $1.50/M = $0.00084 | **약 $0.0344** |
| Nano Banana 2.1, 2K | $0.0504 + $0.00084 | 약 $0.0512 |
| Flash Lite, 1K | $0.0336 + 560 × $0.25/M = $0.00014 | 약 $0.0337 |
| Gemini 3 Pro, 2K | $0.134 + $0.0011 | 약 $0.135 |
| klein 9B, 출력 1MP + 입력 512×512 (0.25MP) | $0.015 + 0.25 × $0.002 = $0.0005 | 약 $0.0155 |
| klein 4B, 출력 1024² (4타일) + 입력 512² (1타일) | 4 × $0.000287 + $0.000059 | 약 $0.0012 |
| Kontext [pro] | 4 credit | $0.04 |
| GPT Image 2.5 | 편집 입력 토큰 수치가 공식에 없음 | **산출 불가**. 응답 `usage`로 측정 필요. 재판매 사이트 수치는 서로 달라 쓰지 않음 |

프롬프트 텍스트 비용은 Gemini에서 100토큰 기준 $0.00015로 무시 가능 [가정]. 무료 분 환산: klein 9B 출력 1MP + 입력 1MP 기준 1363.64 + 181.82 = 1545 neuron, 10,000 ÷ 1545 = 하루 6장.

### 사진 비공개 관점

| 공급자 | 학습 사용 | 보관 | 사람 사진 정책 |
|---|---|---|---|
| Gemini 유료 | 안 함 | **55일** 프롬프트 등 보관 ([정책](https://ai.google.dev/gemini-api/docs/usage-policies)). 무료 서비스는 민감·개인정보 입력 금지 ([약관](https://ai.google.dev/gemini-api/terms)) | 업로드 권리 보유 요구. 법적 동의 없는 개인정보·생체 사용 금지 ([정책](https://policies.google.com/terms/generative-ai/use-policy)). 일반 인물 편집 금지 조항은 찾지 못함. 생성물은 SynthID 포함 |
| OpenAI API | 기본 안 함 | 남용 감시 로그 **최대 30일**, 무보관은 사전 승인 ([문서](https://developers.openai.com/api/docs/guides/your-data)) | 동의 없이 인물 재현 금지 ([서비스 약관](https://openai.com/policies/service-terms/), 검색 발췌), `moderation_blocked` 오류 처리 필요. 일반 사진 편집 전용 조항은 [미확인] |
| BFL 직접 API | **기본 사용, 옵트아웃 요청 방식** ([개인정보](https://bfl.ai/legal/privacy-policy), [약관](https://bfl.ai/legal/terms-of-service)) | "합리적으로 필요한 기간" | 동의 없는 현실 인물의 친밀·기만 묘사 금지 ([정책](https://bfl.ai/legal/usage-policy)) |
| Workers AI | 안 함 | **요청 처리 후 보관 안 함**, 제한적 예외 있음. 모델 개별 라이선스는 따로 ([문서](https://developers.cloudflare.com/workers-ai/platform/privacy/)) | 모델 제공사 조건 따름 |

## 5. 실패 유형별 대응

| 실패 | 하늘 교체 + 색 보정 | 생성 재조명 | 근거 |
|---|---|---|---|
| 물 반사 불일치 | Luminar만 공식 대응(수면 감지·반사 강도·물 블러). 하늘 위 추가 물체는 반사 안 됨. Photoshop 대응은 [미확인] | 전체를 다시 그려 맞출 가능성은 있으나 보장 없음, 측정 자료 [미확인] | [Skylum](https://manual.skylum.com/ai/en/topic/sky-ai-tool), [Update 3](https://manual.skylum.com/ai/en/topic/luminar-ai-update-3) |
| 유리 건물 반사 | 공식 대응 [미확인]. 흐린 하늘이 유리에 남는다 [제안] | [미확인] | 자료 없음, 시험 항목 |
| 젖은 바닥 | 대응 [미확인]. 바닥은 그대로 남는다 [제안] | 프롬프트로 지시 가능, 품질 [미확인] | 자료 없음, 시험 항목 |
| 평평한 그림자 | 해결 불가. 공개 기능은 노출·색뿐 | 생기지만 단단함 부족 가능. 사진 안 빛 방향 일관성을 시험에서 확인 | [2409.08947](https://arxiv.org/pdf/2409.08947) |
| 사람 얼굴 변함 | 발생 안 함. 픽셀을 다시 그리지 않음 | **주요 위험.** 범용 확산 모델이 조명 지시 시 얼굴을 자주 바꾼다는 보고. 동일성 보존과 재조명 강도가 맞바뀜. IC-Light는 전신·다인 장면에서 왜곡 큼 | [2604.24877](https://arxiv.org/html/2604.24877v1), [SynthLight](https://arxiv.org/html/2501.09756v1), [Comprehensive Relighting](https://arxiv.org/html/2504.03011) |
| 간판·글자 깨짐 | 발생 안 함 | OpenAI 공식 문서가 글자 배치·선명도 한계를 인정. 다른 모델의 측정 자료 [미확인] | [OpenAI 가이드](https://developers.openai.com/api/docs/guides/image-generation) |
| 구도·소품 변함 | 발생 안 함 | Magic Editor가 전체 분위기를 바꾸는 것처럼 범위 통제가 어렵다. 마스크 편집(Fill)은 범위가 좁아지는 대신 전체 재조명이 안 된다 [제안] | [Android Authority](https://www.androidauthority.com/galaxy-s24-generative-edit-vs-google-magic-editor-3410818/) |
| 나무·머리카락 경계 후광 | 복잡한 경계에서 약함. 지평선이 깨끗한 사진에서 가장 잘 됨 | 해당 없음 | [Skylum 블로그](https://blog.skylum.com/how-sky-replacement-works-in-photoshop/), [SkyAR](https://github.com/jiupinjia/SkyAR) |

**잠금 합성 [제안, 시험 필요]:** 생성 결과에서 저주파 빛 변화만 뽑아 원본에 곱하면, 얼굴·글자는 원본 픽셀을 유지하면서 생성된 빛만 가져올 수 있다. 또는 얼굴과 간판 영역에 원본을 다시 덮는다. 단 그림자 경계가 흐려질 수 있어 비교가 필요하다.

## 6. 세 경로 비교

제약: 기존 Cloudflare Worker 있음, 운영자 PC MX150 2GB, 새 PC 가정 안 함, 사진은 비공개 취급.

| 항목 | 브라우저 전용 | 서버리스 API + Worker | 자체 GPU 서버 |
|---|---|---|---|
| 그림자·건물 재조명 | 불가 | **가능** | 가능 |
| 사진이 기기를 벗어나나 | **안 벗어남** | 벗어남. Gemini 55일, OpenAI 30일, Workers AI 무보관 | 내 서버까지만 |
| 장당 비용 | $0 + 모델 CDN 전송 | Workers AI 약 $0.0012~0.0155, Gemini 약 $0.034~0.135 (위 계산) | 항상 켜두면 월 $244.80 |
| 초기 다운로드 | SegFormer 4.4MB + 사람 마스크 6.6MB | 없음 | 없음 |
| 대기 시간 | 측정 필요 | 측정 필요, OpenAI 최대 2분 | 측정 필요, 콜드 스타트 별도 |
| 동일성·글자 | 안전 | 위험, 잠금 합성으로 완화 시도 | 위험 |
| 얼굴·남용 대응 | 불필요 | 업체 필터 + 호출 제한·비용 상한 필요 | 직접 구현 |
| 라이선스 | SegFormer 확인 필요, 하늘 CC0 | 업체 약관 | Kontext dev는 비상업, 서비스 운영 시 상용 계약 |
| 운영 부담 | 낮음 | 낮음, 기존 Worker 재사용 | 높음, MX150 2GB로 불가 |

자체 GPU 계산: RTX 4090 [$0.34/시간](https://www.runpod.io/pricing) × 720시간 = $244.80/월. 필요 메모리는 Kontext dev FP8 약 12GB, NF4 약 7GB로 커뮤니티 수치다 ([예시](https://dev.to/jovan_chan_9500711396d4e6/flux1-kontext-dev-for-local-ai-in-2026-image-editing-on-consumer-gpus-without-the-api-bills-3nd3)). 초당 과금 서버리스 GPU 단가는 공식 표에서 못 뽑았다 [미확인]. 켜두지 않으면 재생 시간 비용만 내지만 장당 처리 시간이 [미확인]이라 장당 비용은 계산하지 않는다.

## 7. 권장 경로

**처음 시험: 서버리스 생성(Gemini Nano Banana 2.1) 대 브라우저 기준선.** 이유는 세 가지다. 2K·4K 출력이 되고 512 미만 같은 입력 제한 문구를 찾지 못했으며(실제 내부 입력 해상도는 [미확인]), 기존 Worker를 재사용하고, 사양 메모의 (a)(b) 비교를 그대로 한다. 비공개 약속이 작품의 핵심이면 Workers AI klein이 차선이지만 입력 512 미만이 얼굴·글자를 망칠 가능성이 높아 시험으로 판정한다.

작품 공개 시 안내 문구 방향 [제안]: "브라우저 처리 = 기기 안", "생성 처리 = 업체로 전송, 보관 기간 명시, 동의 필요"로 나눈다. BFL 직접 API는 기본 학습 조항 때문에 후보에서 뺀다.

### 승부를 가를 나란히 비교

사진 8장. 운영자 본인 사진 또는 CC0만 쓰고, 다른 사람이 찍힌 사진은 동의 없이 올리지 않는다.

| 조건 | 장수 | 보는 것 |
|---|---|---|
| 근거리 인물 (얼굴 크게) | 2 | 동일성 |
| 원거리 인물·군중 | 1 | 사람 추가·변형 |
| 한글 간판 | 1 | 글자 보존 |
| 수면 | 1 | 반사 |
| 유리 건물 | 1 | 반사 |
| 젖은 도로 | 1 | 바닥 |
| 나무·머리카락과 하늘 경계 | 1 | 경계 |

시험군 5개:

1. 브라우저 하늘 교체 + 색 보정
2. Nano Banana 2.1 2K
3. 2번 + 잠금 합성
4. Workers AI klein 9B (512 입력)
5. GPT Image 2.5 (토큰 실측 겸용)

고정 조건: 같은 프롬프트, 같은 태양 방향 지시. 각 시험군에서 지연, 실제 `usage` 비용, 거부율, 동일 입력 3회 편차를 기록한다.

판정:

- 근거리 인물 2장에서 얼굴이 같은 사람으로 보이고 한글 간판이 그대로이며, 그림자 방향이 사진 안에서 일치 → 해당 시험군 채택.
- 2번이 그림자는 내지만 얼굴·글자에서 지면 3번을 본다.
- 3번도 지면 인물·간판 없는 풍경으로 작품 범위를 좁히거나 1번을 기본으로 둔다.
- 1번이 눈으로 "맑은 날 사진"처럼 보이면 생성은 선택 기능으로 내린다.

## 공백

- 모든 후보의 실제 지연, 브라우저 분할 속도, 재조명 품질 수치: 찾지 못함. 시험으로만 나온다.
- OpenAI 편집 입력 토큰 수, BFL Fill 가격: 공식 페이지에서 숫자를 못 뽑음.
- Adobe 도움말은 403, 검색 발췌 기준. Samsung 현행 처리 위치와 Magic Editor 처리 위치 미확인.
- Reinhard 색 전이, guided filter, MODNet 용도는 일반 지식으로 출처 미확인.
