# 토스 시험판 (Apps in Toss spike)

「따로 같이 네컷」의 엔진이 토스 앱 웹뷰 안에서 도는지 한 화면으로 판정한다. 계획: `C:\DC\DD\ideas\I-0054-photo-booth-apps-in-toss.md` P1.

| # | 시험 | 통과 기준 |
|---|---|---|
| 1 | 카메라 켜기 (탭 안에서 getUserMedia) | 미리보기가 움직인다 |
| 2 | MediaRecorder 2초 / WebCodecs 2초 / 8초 | 녹화된 영상이 아래에서 재생된다 |
| 3 | 영상 저장 / 사진 저장 (`saveBase64Data`) | 갤러리(또는 파일 앱)에서 보이고 재생된다 |
| 4 | 빠른 누끼 / 정밀 누끼(MODNet 26MB) | 둘 다 숫자가 찍히고 흰 화면이 안 된다 |
| 5 | 보상형 광고 (테스트 ID) | 광고가 뜨고 "보상 받음" |
| 6 | 공유 링크 | 링크가 만들어진다 (intoss:// 는 출시 뒤에만 열림) |

## 빌드
```sh
npm install
AIT_APP_NAME=<콘솔에 등록한 appName> npm run build   # -> <appName>.ait
```
- 라이브러리(onnxruntime-web, MediaPipe)와 모델은 번들 안에 넣는다 — CDN 코드 실행 금지 규정. `scripts/vendor.mjs` 가 복사.
- 번들 압축 해제 크기 65MB (한도 100MB). 빌드 후 `eval(` · `new Function(` 0건 확인함.

## 토스 앱에서 시험
1. 콘솔 → 앱 출시 → `.ait` 업로드 → 나온 QR 을 폰 카메라로 찍기 (토스 앱이 열림).
2. 위에서부터 차례로 누르고, 저장은 갤러리에서 직접 확인.
3. 맨 아래 "결과 복사" → 붙여 넣기.

## 로컬 확인 (2026-10-30, 헤드리스 Chrome 430px + 가짜 카메라)
- 카메라 640x480 ✔, MediaRecorder mp4 302KB 재생 ✔, WebCodecs mp4 249KB 재생 ✔
- 빠른 누끼 GPU ✔, 정밀 누끼 WebGPU 512x384 run 5.7s ✔ / GPU 끈 상태 WASM 352x256 run 2.6s ✔
- 토스 SDK 호출(저장·광고·공유·환경)은 토스 앱 밖이라 예상대로 실패 — 폰에서 판정한다.
