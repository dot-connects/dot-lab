# 우리 집 몬스터 잡기 앱 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 펫 앱(`app/`, `kr.dot.catchpet`)을 인형뽑기로 진짜 녀석·털 녀석을 뽑아 도감에 모으고 펫으로 띄우는 네이티브 앱으로 바꾼다.

**Architecture:** Compose 한 액티비티(뽑기 · 도감 · 상세 · 펫 설정) + 기존 오버레이 서비스. 게임 규칙(판정, 추적, 캡슐, 펫 두뇌, 한도)은 안드로이드에 의존하지 않는 순수 Kotlin 으로 두고 JVM 테스트한다. 카메라는 ARCore(SceneView) 우선, 미지원 기기는 CameraX + 자이로. 오려 내기는 MediaPipe tasks-vision(DeepLab v3, 웹과 같은 모델) + EfficientDet 상자로 놓친 프레임 보충(Task 0 실측으로 결정).

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Compose (BOM 2024.10.01), CameraX 1.4.0, ARCore 1.46 + SceneView `arsceneview` 2.2.1, MediaPipe tasks-vision 0.10.14, Play Billing 7.1.1, org.json(파일 저장), JUnit4 + Robolectric 4.13 + Roborazzi 1.32(화면 렌더 확인). JDK `C:\DC\tools\jdk-17`.

**Spec:** `works/2026-11-09-catch/spec-app.md` (2026-11-12, 가격 500원)

## Global Constraints

- minSdk 34, targetSdk 35, applicationId `kr.dot.catchpet` (기존 유지).
- 빌드 출력은 `%LOCALAPPDATA%\catchpet-build` (기존 `build.gradle.kts`).
- 빌드: `JAVA_HOME=C:/DC/tools/jdk-17 ./gradlew assembleDebug testDebugUnitTest` (app/ 에서). workers.max=2 유지.
- 사용자에게 보이는 글은 모두 한국어. 코드·주석·커밋은 영어.
- 진짜 녀석 사진은 앱 내부 저장소에만. 서버로는 AI 요청 때 오린 PNG 한 장만.
- AI 모델 `gpt-image-2.6-flare`: 미리보기 low 1024², 동작표 medium 1536×1024. 미리보기 한도 기기 5 · 전체 30 / 일.
- 구매 500원. 출시 전에는 구매 버튼 잠금, 운영자 디버그 해제만.
- AGPL 모델·라이브러리 금지.
- 남의 캐릭터 금지. 털 녀석 16마리는 `site/js/creatures.js` 의 우리 것.
- 펫 크기 ≈ 앱 아이콘 하나(48dp). 자리: 아래 줄 · 왼쪽 벽 · 오른쪽 벽 · 아이콘 뒤(라이브 배경화면). 빼꼼·카메라 구멍 없음.

## 파일 구조

```
app/app/src/main/java/kr/dot/catchpet/
  game/Claw.kt          집게 상태 기계 + 판정 (순수)          test: game/ClawTest.kt
  game/Mask.kt          비트 마스크, 열 스캔, 상자, IoU (순수)  test: game/MaskTest.kt
  game/Tracker.kt       놓친 프레임 최대 4개 유지 (순수)         test: game/TrackerTest.kt
  game/Capsule.kt       캡슐 감싸기·튀기·굴러가기 (순수)        test: game/CapsuleTest.kt
  seg/Segmenter.kt      MediaPipe DeepLab + 상자 보충 → Mask
  seg/CutOut.kt         고해상도 프레임 + Mask → 투명 PNG (흰 테두리 없이 부드러운 가장자리)
  camera/FrameSource.kt 카메라 추상: ARCore 또는 CameraX, 최신 프레임 Bitmap + 미리보기
  ar/Critters.kt        털 녀석 배치(ARCore 앵커 / 자이로), 화면 좌표·크기 → Mask 로 판정에 참여
  ui/CatchScreen.kt     카메라 + 레일·집게·캡슐·알림·"도감 N" (Compose Canvas)
  ui/DexScreen.kt       캡슐 칸 그리드
  ui/DetailScreen.kt    모습 · AI 미리보기 · 펫으로 쓰기/사기
  ui/PetScreen.kt       펫 켜기, 자리 고르기
  ui/App.kt             MainActivity 의 Compose 내비게이션 (화면 4개)
  dex/Dex.kt            도감 저장: filesDir/dex/<id>/meta.json + <label>.png (+ ai/ 미리보기·동작표)
  ai/AiClient.kt        Worker /transform(미리보기) · /sheet(동작표), 기기 ID
  ai/Sheet.kt           3×2 동작표 자르기 + 초록 지우기 (순수 픽셀 함수)   test: ai/SheetTest.kt
  billing/Purchase.kt   구매 잠금 + 디버그 해제 (출시 전)                    test: billing/PurchaseTest.kt
  pet/PetBrain.kt       펫 두뇌 (자리·동작 규칙, 순수)                       test: pet/PetBrainTest.kt
  pet/PetService.kt     (이동) 기존 오버레이 + PetBrain + 터치(한 번·두 번·끌기)
  pet/PetWallpaper.kt   아이콘 뒤 = WallpaperService
  pet/Frames.kt         펫 그림 6칸 로드 (털 녀석 = assets, 진짜 = 동작표)
app/app/src/main/assets/
  critters/<id>/{dir0..dir7,front,smile,walkA,walkB,sleep,curl}.png   (Task 6 굽기 결과)
  models/deeplab_v3.tflite, models/efficientdet_lite0.tflite
works/2026-11-09-catch/tools/bake-critters.mjs   헤드리스 Chrome 으로 털 녀석 PNG 굽기
works/2026-11-09-catch/worker/src/index.js       Gemini → OpenAI, /sheet 추가
```

삭제: `Behaviour.kt`(→ PetBrain), `PetFile.kt` · `Pets.kt`(→ Dex), 기존 MainActivity 의 `.dotpet` 가져오기 화면.

---

### Task 0: 오려 내기 후보 실측 — 끝남

결과: `research/seg-compare.md`. DeepLab 0.3 + EfficientDet 타원 보충 + 4프레임 유지로 120/120.

### Task 1: Gradle · 의존성 · 앱 뼈대

의존성은 끝났다(빌드·테스트 통과, 11-12 23:15). 남은 것: 매니페스트 권한(CAMERA, INTERNET), `xmlns:tools`, MainActivity → ComponentActivity + `ui/App.kt`(화면 4개를 상태 하나로). 원래 명세: archive `task1-body.md`.

### Task 2–4: 마스크·추적 · 집게 · 캡슐 — 끝남

`game/Mask.kt`(Mask, Box, iou, Tracker) · `game/Claw.kt`(Claw, chance, handedOff) · `game/Capsule.kt` + `GameTest.kt` 13개 통과. 원래 명세: `docs/archive/2026-11-12-catch-app-plan-review/tasks-2-4-done.md`. 다시 쓰지 않는다.

### Task 5: 오려 내기 + 카메라 소스

**Files:** `seg/Segmenter.kt`, `seg/CutOut.kt`, `camera/FrameSource.kt`, assets 모델 2개.
- [ ] **Step 1:** 모델 파일을 받아 assets 에 넣는다(Google 공식 URL).
  - `deeplab_v3.tflite` = 웹 `seg-worker.js` 의 DEEPLAB URL
  - `efficientdet_lite0.tflite` = `https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float16/1/efficientdet_lite0.tflite`
- [ ] **Step 2:** `Segmenter.run(bmp: Bitmap): List<Mask>` 을 만든다.
  - 입력: 긴 변 320 으로 줄인 프레임.
  - DeepLab confidence 마스크에서 사람(15)과 개(12)+고양이(8)를 각각 90×160 으로 줄이고 문턱 0.3 을 쓴다.
  - Task 0 결정에 따라, 마스크가 비면 EfficientDet 상자를 `ellipseIn` 으로 보충한다.
  - 백그라운드 스레드 하나에서 돌리고, 300ms 마다 최신 프레임 하나만 처리한다.
- [ ] **Step 3:** `CutOut.make(frame: Bitmap, m: Mask): Bitmap` 을 만든다.
  - 마스크를 프레임 크기로 키운 뒤 흐림 2px 를 알파로 쓴다.
  - 상자 + 여백 8% 로 자르고, 긴 변 512 PNG 로 만든다.
- [ ] **Step 4:** `FrameSource` 인터페이스를 만든다: `latest(): Bitmap?`, `@Composable Preview()`, `close()`.
  - 구현 1: `CameraXSource` (PreviewView + ImageAnalysis RGBA_8888, 뒤 카메라)
  - 구현 2: `ArSource` (SceneView `ARScene`, `frame.acquireCameraImage()` YUV → Bitmap)
  - `ArCoreApk.checkAvailability` 결과로 고른다.
- [ ] **Step 5:** `assembleDebug` 가 통과하는지 본다. Commit. (실폰 확인은 운영자.)

### Task 6: 털 녀석 굽기 + 배치

**Files:** `tools/bake-critters.mjs`, assets `critters/`, `ar/Critters.kt`.
- [ ] **Step 1:** `bake-critters.mjs` 를 만든다.
  - 기존 정적 서버와 헤드리스 Chrome(`check/s1.mjs` 패턴)으로 `site/js/creatures.js` 의 털 녀석 16마리를 그린다.
  - 녀석마다 방향 8개(0°..315°, 정면 = 0)와 펫 6칸을 PNG 256px(투명 배경)으로 저장한다.
  - 펫 6칸: front · smile(눈 감은 웃음 = setPose 가 있으면 그것, 없으면 front) · walkA/walkB(옆, 발 위상 0/π) · sleep(누움) · curl(말림).
  - 셰이더 털이 있어 소프트웨어 GL 로 느리면 `--use-angle=swiftshader` 로 한 장씩 그린다.
- [ ] **Step 2:** 결과를 `assets/critters/<id>/` 에 넣고 `critters/index.json` 을 만든다. 형식: `[{id, name, type}]`.
- [ ] **Step 3:** `Critters.kt` 를 만든다.
  - **ARCore 일 때:** 바닥면을 찾으면 녀석 3마리를 바닥 위 1~3m 에 앵커로 놓는다. 크기는 웹 녀석의 1/4(실제 높이 약 8cm)이다.
  - 그리기는 SceneView `ImageNode`(빌보드, 카메라를 향함)이고, 보는 방향에 맞는 dir 이미지를 고른다.
  - **자이로일 때:** 화면 아래 1/3 의 고정 방위각에 놓는다.
  - 각 녀석의 화면 상자는 `Mask.ellipseIn(kind = CRITTER)` 로 만들어 집게 판정에 넣는다.
  - 뽑히면 그 앵커를 지우고 다른 자리에 새 녀석을 놓는다.
- [ ] **Step 4:** build. Commit.

### Task 7: 뽑기 화면

**Files:** `ui/CatchScreen.kt`. Robolectric 렌더 테스트 `ui/CatchScreenShotTest.kt`(가짜 프레임 + 가짜 마스크).
- [ ] **Step 1:** Compose Canvas 로 레일 · 집게(3갈래, 흔들림) · 조준선과 착지 타원 · 캡슐 · 알림(1.2초) · "도감 N" 을 그린다. 모양과 색은 시안 `claw-endings.html` 과 같다.
- [ ] **Step 2:** 입력과 연결한다.
  - 가로로 끌면 `claw.aim`, 손을 떼면 `release`.
  - 매 프레임 `claw.step(dt, segmenter 마스크 + critter 마스크)` 를 돈다.
  - GRIP 순간: 프레임을 멈추고 `CutOut.make` 을 하고, 빈자리는 흐리게 칠한다.
  - 성공: Capsule 을 돌리고, GONE 에서 `Dex.add` 하고 "○○를 뽑았다!" 를 띄운다.
  - 실패: "아깝다!" 를 띄운다.
  - 어느 쪽이든 `claw.next()` 로 바로 다음 뽑기.
- [ ] **Step 3:** 카메라 권한이 없으면 안내 한 줄과 권한 버튼을 띄운다.
- [ ] **Step 4:** Roborazzi 로 3장을 렌더한다(AIM · GRIP · 캡슐 DROP). 1080×2400 PNG 로 저장하고 직접 열어 확인한다.
- [ ] **Step 5:** Commit.

### Task 8: 도감 저장 + 도감·상세 화면

```kotlin
object Dex {
  data class Entry(val id: String, val kind: String /*person|dog|cat|critter*/, val name: String, val no: Int,
                   val critterId: String?, val poses: List<String>, val caughtAt: Long, val ai: AiState)
  data class AiState(val previews: List<String> /*file names*/, val sheet: String? /*file name*/, val owned: Boolean)
  fun add(ctx: Context, kind: String, png: Bitmap?, critterId: String?, label: String): Entry   // same critter id = same entry, new label = new pose
  fun list(ctx: Context): List<Entry>
  fun get(ctx: Context, id: String): Entry?
  fun savePreview(ctx: Context, id: String, png: ByteArray): Entry
  fun saveSheet(ctx: Context, id: String, png: ByteArray): Entry
}
```
- 진짜 녀석은 뽑을 때마다 새 항목입니다. 이름은 "강아지 1", "고양이 2", "사람 3" 식입니다.
  - 같은 녀석을 알아보는 판정은 이번 범위에서 뺍니다. 모습 모으기는 털 녀석부터 적용합니다.
  - 진짜 녀석의 모습 판정은 웹에서도 미완이었습니다.
- 털 녀석은 critterId 가 같으면 같은 항목입니다. 모습 = 뽑은 순간 방향(front/side/back)입니다.
- [ ] 테스트(Robolectric, 임시 filesDir):
  - add 를 같은 털 녀석 id 로 두 번(방향만 다르게) → 항목 1개, 모습 2개.
  - 진짜 둘 → 항목 2개.
  - 앱 재시작(새 list 호출) 뒤에도 남는다.
- [ ] 화면:
  - Dex 는 캡슐 모양 칸 3열입니다(진짜 = 오린 사진, 털 녀석 = front.png).
  - Detail 은 모습 가로 목록입니다.
    - 털 녀석: "펫으로 쓰기"
    - 진짜 녀석: "AI 미리보기"(남은 횟수 표시), 미리보기 그림, "펫으로 사기 · 500원"(잠금 시 "출시 후 열려요")
- [ ] Roborazzi 렌더 2장. Commit.

### Task 9: Worker 를 OpenAI 로 + 동작표

**Files:** `worker/src/index.js`, `worker/test/*.test.mjs`, `worker/wrangler.toml`, `ai-hub/llm-registry.toml`.
- [ ] **Step 1: 실패 테스트(node --test)** — 가짜 fetch 를 씁니다.
  - `/transform` 이 `https://api.openai.com/v1/images/edits` 에 multipart 로 보내는지: model, quality=low, size 1024x1024, image[].
  - 응답 `data[0].b64_json` 을 `{image, mime}` 로 돌려주는지.
  - 500 → 한 번 재시도.
  - 400·429 → 재시도 없음.
  - `OPENAI_KEY` 없으면 503 `off`.
  - `/sheet` 는 `x-purchase` 헤더가 env `DEBUG_PURCHASE` 와 같을 때만 받는다. 아니면 402.
  - `/sheet` 는 quality=medium, size 1536x1024 이고 프롬프트가 3×2 동작표다(spec 5절 6칸).
- [ ] **Step 2–4:** 실패 확인, 구현, 통과.
  - 프롬프트: 미리보기는 기존 `PROMPTS` 를 유지합니다. 동작표는 scratchpad `oai_sheet.py` 의 PROMPT 에서 6번 칸을 "curled up asleep in a ball"로 바꿉니다.
  - 기존 일일 한도는 `ALL_DAY` 를 "30" 으로 바꿉니다.
- [ ] **Step 5:** `llm-registry.toml` 의 `lab.catch_transform` chain 을 openai/gpt-image-2.6-flare 로 바꾸고, `llm-check.py --all` 을 돌린다.
- [ ] **Step 6:** 배포합니다.
  - `npx wrangler@4 deploy`
  - `DEBUG_PURCHASE` 는 무작위 32자를 `wrangler secret put` 으로 넣습니다. 값은 `%USERPROFILE%\.catchpet-debug-purchase` 에만 두고 커밋하지 않습니다.
  - 실호출 1회로 미리보기를 확인합니다(≈ $0.03).
- [ ] Commit (dot-lab), Commit (ai-hub).

### Task 10: 앱 AI 클라이언트 · 동작표 자르기 · 구매 잠금

```kotlin
object Sheet { fun cells(sheet: Bitmap): Map<String, Bitmap>  // keys front, smile, walkA, walkB, sleep, curl; green keyed out
               fun keyGreen(argb: Int): Int }                 // pure: k = g - max(r,b); k>60 -> alpha 0; 18<k<=60 -> partial + despill
object Purchase { fun locked(debugToken: String?): Boolean   // true unless a debug token is set (release: Play Billing later)
                  const val PRICE_WON = 500 }
class AiClient(base: String, deviceId: String) { suspend fun preview(png: ByteArray, kind: String, style: String): Result<ByteArray>
                                                 suspend fun sheet(png: ByteArray, kind: String, token: String): Result<ByteArray> }
```
- [ ] 테스트:
  - `keyGreen`: 순수 초록 → 알파 0, 흰색 → 그대로, 경계 초록 → 반투명 + G 감소.
  - `Purchase.locked(null)` == true, `locked("x")` == false.
- [ ] 디버그 해제: 펫 화면 맨 아래 "개발자" 칸에 토큰을 붙여 넣으면 저장됩니다(SharedPreferences).
- [ ] Detail 화면 연결: 미리보기 → `Dex.savePreview` / 구매(디버그) → `sheet` → `Dex.saveSheet` → "펫으로 쓰기".
- [ ] Commit.

### Task 11: 펫 두뇌 (순수, 틀리면 비싼 곳)

```kotlin
class PetBrain(var zone: Zone, val w: Float, val h: Float, val size: Float = 48f, private val rnd: () -> Float) {
  enum class Zone { FLOOR, LEFT, RIGHT, BEHIND }
  enum class Act { IDLE, WALK, THINK, SMILE, DANCE, SLEEP, CLIMB, HANG, FALL }
  var act: Act; var x: Float; var y: Float; var dir: Int
  fun step(dtMs: Float, sinceTouchMs: Float, night: Boolean, charging: Boolean)
  fun tap(); fun doubleTap(); fun grab(x: Float, y: Float); fun drag(x: Float, y: Float); fun drop()
  fun frame(tMs: Float): String   // front|smile|walkA|walkB|sleep|curl
  fun rotationDeg(): Float        // LEFT 90, RIGHT -90, else 0
}
```
규칙(spec 5절):
- 동작이 끝나면 다음 동작: FLOOR {IDLE, WALK, THINK, WALK} · 벽 {IDLE, CLIMB, CLIMB} · BEHIND {IDLE, WALK, WALK}. 동작 길이 2.5–5.5초.
- 잠: sinceTouch ≥ 180초, 또는 night, 또는 charging 이면 SLEEP(그 자리). tap 하면 SMILE.
- 위치 범위: WALK 은 x 가 [size/2, w−size/2] 안에서 벽에 닿으면 방향을 바꿈. CLIMB 은 y 가 [h·0.2, floorY−size].
- tap → SMILE 1.4초, doubleTap → DANCE 3초.
- drop: x < 0.18w → LEFT, > 0.82w → RIGHT, 그 밖 → FALL 뒤 FLOOR.
- [ ] 테스트(위 규칙을 하나씩):
  - 180초 무접촉 → SLEEP.
  - tap 중 SLEEP → SMILE.
  - drop(0.1w) → LEFT, rotationDeg 90.
  - drop(0.5w, 0.3h) → FALL 이 유한 시간 안에 FLOOR + IDLE.
  - WALK 10초 시뮬레이션 동안 x 가 범위 안.
  - 벽에서 고르는 동작은 {IDLE, CLIMB, SLEEP, SMILE, DANCE} 안에서만.
- [ ] Commit.

### Task 12: 펫 오버레이 + 아이콘 뒤 배경화면 + 펫 화면

- [ ] `pet/PetService.kt` 로 옮기고 `Behaviour` 를 `PetBrain` 으로 바꿉니다.
  - 창 크기 48dp. FLOOR 일 때 y = 화면 아래 − 내비게이션 바 − 독 높이 근사(화면 높이의 12%).
  - 터치: 한 번 · 두 번(320ms) · 길게(380ms) 끌기.
  - 밤 = 22–7시, 충전 = `BatteryManager.isCharging`.
- [ ] `pet/Frames.kt`: 털 녀석은 assets `critters/<id>/`, 진짜 녀석은 `Sheet.cells(sheet)`.
- [ ] `pet/PetWallpaper.kt`: WallpaperService. 배경은 사용자 원래 배경화면 대신 단색 그라데이션입니다(원래 배경을 읽으려면 권한이 필요해 뺌). PetBrain(BEHIND) 으로 펫을 그립니다. 매니페스트에 `BIND_WALLPAPER` 서비스와 `xml/wallpaper.xml` 을 넣습니다.
- [ ] `ui/PetScreen.kt`:
  - 펫 고르기: 도감에서 털 녀석이거나 owned 인 것.
  - 자리 4개 칩. 아이콘 뒤를 고르면 "배경화면 설정" 인텐트를 엽니다.
  - 오버레이 권한 안내.
  - 개발자 토큰 칸.
- [ ] `Behaviour.kt` · `PetFile.kt` · `Pets.kt` 와 그 테스트를 삭제합니다.
- [ ] build + 모든 테스트. Roborazzi 렌더 1장(펫 화면). Commit.

### Task 13: 마무리

- [ ] `README.md` "이어서 하기" 를 앱 기준으로 다시 씁니다. 웹 `site/` 는 동결한다고 한 줄 남깁니다.
- [ ] `assembleDebug` 로 APK 를 만들고 버전을 `0.2.0` 으로 올립니다.
- [ ] 실폰 확인 목록을 README 에 씁니다: 뽑기, 털 녀석 크기 1/4·1/5, ARCore 미지원 시 전환, 펫 자리 4개, 미리보기, 디버그 구매.
- [ ] Commit, push.

## 실폰에서만 확인되는 것 (이 PC 에는 기기·에뮬레이터 없음)

- 카메라 프레임 속도와 오려 내기 지연
- ARCore 바닥 찾기
- 오버레이 터치
- 라이브 배경화면

PC 에서는 순수 로직 JVM 테스트와 Robolectric 화면 렌더로 확인합니다.

---

## 계획 리뷰 반영 (사전 리뷰)

12개 항목 전문은 `C:\DC\DD\docsrchive6-11-12-catch-app-plan-review
eview0.md` 에 있다. 이 계획에 여전히 적용한다. 요약:

- Worker 는 Origin 이 없고 `x-app: catchpet` 이 있으면 허용한다.
- Robolectric 4.14.1 + sdk=34 를 쓴다.
- 첫 화면은 CameraX + 자이로다. AR 은 스위치 뒤에 둔다.
- 순서: 1 → 7 → 8 → 11 → 12 → 10 → 9 → 6 → 5 → 배경화면.
- `ScreenMap` 좌표 변환, ImageAnalysis 긴 변 1280.
- 배경화면 매니페스트 규칙.
- 털 녀석 크기 1/4·1/5 전환 칩.
- compose 플러그인을 루트에 둔다. billing 은 나중. arm64 만. 16KB 정렬은 출시 단계.

---

## /plan-review 1회차 반영 (2026-11-12, High 11 → 반영)

이 절이 위 모든 절보다 우선한다. 근거와 렌즈별 원문은 `C:\DC\DD\docs\archive\2026-11-12-catch-app-plan-review\round1.md` 에 있다.
Task 0 · 2 · 3 · 4 는 이미 끝났다(`game/`, `GameTest.kt`, `research/seg-compare.md`). **다시 쓰지 않는다.**

**R1. 레지스트리.** 순서를 지켜야 라이브 봇이 깨지지 않는다.
1. 4개 사본(`ai-hub/src/ai_hub`, `content-system/src/content_system`, `trading-system/trading_system`, `mpk-deck/src/mpk_deck/core`)의 `llm_registry.py` `PROVIDER_FIELDS` 에 `"openai": {"quality"}` 를 넣는다. ai-hub 테스트를 1개 추가해 통과시킨다.
2. 그다음 `llm-registry.toml` 의 chain 을 고친다.
   - `lab.catch_transform` 은 openai/gpt-image-2.6-flare, quality low.
   - `lab.catch_sheet` 를 새로 만든다: medium, templates = index.js.
3. `llm-check.py --all` 이 통과해야 커밋한다.
4. 레포마다 따로 커밋한다.

**R2. 돈 상한(Worker).**
- `wrangler.toml` `[vars] ALL_DAY = "30"`.
- D1 키 `usd|YYYY-MM` 에 호출마다 센트를 더한다(low 3, medium 5). 500 이상이면 503 `off`. `/transform` 과 `/sheet` 둘 다 센다.
- `/sheet` 는 `x-purchase` 가 env `DEBUG_PURCHASE` 와 같고 **그 값이 비어 있지 않을 때만** 받는다.
  - 키 `sheet|<token>|<entry>` 는 최대 2(호출 + 재시도 1).
  - 키 `sheetday|<token>|<date>` 는 최대 10.
  - `ALL_DAY` 에는 넣지 않는다.
- 테스트(기존 `test/worker.test.js` 에 추가, `callModel` 주입 방식 유지):
  - 빈 토큰 거절
  - 월 상한 503
  - `ALL_DAY` 를 wrangler.toml 에서 읽음
  - `left` 유지
  - Origin 없음 + `x-app: catchpet` 허용

**R3. `/sheet` 계약.**
- 요청: POST JSON `{image: base64 PNG, kind: dog|cat|person, device, entry}`
  - 헤더 `x-app: catchpet`, `x-purchase: <token>`
- 응답: `{image, mime, left}` (left = 이 entry 의 남은 재시도)
- 모델에 보내는 것: image[] = 사진 + `STYLE_URL` 스타일 그림, size 1536x1024, quality medium.
- 프롬프트는 index.js 상수 `SHEET_PROMPT` 로 둔다. 내용은 3×2: 앞 · 웃음(눈 감고) · 옆걸음 A · 옆걸음 B · 누워 자기 · 몸 말고 자기. 초록 배경, 글자 없음.
- 미리보기 `/transform` 응답 모양 `{image, mime, left}` 는 그대로 둔다.

**R4. 굽기(Task 6) 방식.**
- `makeCreature` 는 idle/hop/caught 자세만 있다. 그래서 방향은 `object3d.rotation.y` 로 정한다(0 = 정면, +45° 씩 시계 방향 8장).
- 펫 칸:

| 칸 | 만드는 법 |
|---|---|
| front | idle, 정면 |
| smile | hop 자세, 정면 |
| walkA | 옆(90°) idle |
| walkB | 옆(90°) hop |
| sleep | 옆(90°) idle 을 z 로 −80° 눕힘 |
| curl | 정면 idle 을 y 0.7 · x 1.15 로 눌러 둥글게 |

- 녀석 목록은 `VIRTUAL` 배열의 16마리다. `type` 은 SPECS 의 `group`.
- 투명 배경 PNG 256px.
- **낮은 우선순위로 돌린다:** Windows `start /low`. 끝나면 trading 예약 작업의 마지막 결과를 확인해 로그에 남긴다.
- 실패하면 front 하나만 굽고 모든 칸에 쓴다.

**R5. PetBrain 값.**
- 생성자 `PetBrain(zone, w, h, floorY, size = 48f, rnd)`.
- 속도: WALK 0.022 px/ms, CLIMB 0.018. FALL 중력 0.003 px/ms².
- dir: +1 = 오른쪽 / 아래.
- 동작 → 칸:

| 동작 | 칸 |
|---|---|
| IDLE · THINK · HANG · FALL | front |
| SMILE · DANCE | smile |
| WALK · CLIMB | walkA / walkB 를 170ms 마다 번갈아 |
| SLEEP | 벽에서는 curl, 아래 줄·아이콘 뒤에서는 sleep |

- `drop(x, y)` 는 인자 둘. HANG = 끌고 있는 동안.
- 잠: sinceTouch ≥ 180초, 또는 (night 또는 charging 이고 sinceTouch ≥ 30초).
- PetService: `floorY = 화면 높이 × 0.88 − 48dp`. 매니페스트 클래스 이름도 `.pet.PetService` 로 바꾸고, 매니페스트 서비스가 실제로 있는 클래스인지 Robolectric 테스트로 확인한다.

**R6. Dex.**
- 번호 `no` 는 전체에서 이어지는 순번이다(털 녀석 포함).
- 이름: 진짜는 "강아지 N / 고양이 N / 사람 N"(같은 kind 의 N 번째), 털 녀석은 `critters/index.json` 의 name.
- 털 녀석 모습: 뽑은 순간의 방향이 dir0·1·7 이면 front, 2·6 이면 side, 3·4·5 이면 back.
- 저장은 임시 파일에 쓴 뒤 이름을 바꾼다. 읽지 못하는 항목은 건너뛴다. 테스트로 확인한다.
- **CARRY 에 들어서는 순간 저장한다**(캡슐 연출 중에 앱을 꺼도 남게).

**R7. 잡는 순간.**
- GRIP 프레임(긴 변 1280)으로 세그먼트를 한 번 더 돌려 그 마스크로 오린다.
- 그래도 비면 원래 마스크를 쓴다.
- 오린 결과의 불투명 픽셀이 5% 미만이면 "헛집기"로 처리한다(도감에 넣지 않음).
- `ScreenMap`(순수)은 버퍼 회전(0/90/270), FILL_CENTER 자르기, 마스크 격자를 함께 바꾼다. 테스트 2개.

**R8. AiClient.**
- 연결 30초, 읽기 200초.
- 오류 코드 → 한국어 문구(순수 함수 + 테스트):
  - `device` = "오늘 AI 미리보기 5번을 다 썼어요"
  - `all` · `off` · `model` · 그 밖 = "AI 그림을 잠시 못 그려요"
  - `net` = "인터넷 연결을 확인해 주세요"
  - 402 = "구매가 확인되지 않았어요"

**R9. 배경화면.**
- 기본은 은은한 그라데이션이다.
- 사용자가 "배경 사진 고르기"를 하면 사진을 `filesDir/wallpaper.jpg` 로 **복사**한다(URI 를 보관하지 않음).

**R10. 사람이 할 일.**
- Worker 비밀값 `DEBUG_PURCHASE` 는 세션이 `wrangler secret put` 에 파이프로 넣는다.
- 실패하면 `dot-lab/scripts/wizard-catch-openai.bat` 에 "디버그 구매 토큰" 단계를 추가하고, 운영자 할 일로 보고한다.
- 토큰 값은 `%USERPROFILE%\.catchpet-debug-purchase` 에만 둔다.

**R11. 범위.** 운영자가 정한 범위라 그대로 둔다. 다만 아래를 지킨다.
- AR 은 스위치 뒤에 둔다.
- 배경화면은 마지막에 한다.
- Task 마다 "마지막 성공 APK" 를 보관한다.
- 아침 확인 목록은 Task 13 에서 **실제로 된 것만** 적는다.
- 실제 사진을 무료 펫으로 쓰는 안(zoom-out 렌즈 제안)은 운영자가 11-12 에 뺐으므로 받지 않는다.

## /plan-review 2회차 반영 (2026-11-12, High 2 → 반영)

이 절이 1회차 반영보다 우선한다. 원문은 `docs/archive/2026-11-12-catch-app-plan-review/round2.md`.

**R12. 트레이딩 시간대 지키기(High).**
- 06:00–07:00, 08:30–09:10, 15:30–16:30, 22:00–23:50 KST 에는 Gradle · 헤드리스 Chrome · 테스트를 돌리지 않는다. 코드 쓰기는 해도 된다.
- 무거운 명령 앞에 시각을 확인한다.
- Task 하나가 끝날 때마다 `Get-ScheduledTaskInfo trading-system-briefing` 결과를 확인한다. 0x41306 이 나오면 멈추고 기록한다.

**R13. Task 5 를 나눈다(High).**
- 5a = 모델 파일 2개 · Segmenter · CutOut · ScreenMap · CameraXSource. Task 7 바로 앞에 한다.
- 5b = ArSource · AR 털 녀석. 맨 뒤에 한다.
- 순서: 1 → 5a → 7 → 8 → 11 → 12 → 10 → 9 → 6 → 5b → 배경화면.

**R14. 레지스트리 적용(R1 보완).**
- ai-hub 사본을 먼저 고치고 테스트한다. 나머지 셋에는 바이트 그대로 복사한다. 무거운 시간대 밖에서 한다.
- toml 을 바꾼 뒤 `ai-hub/scripts/restart-bots.ps1 -Bot content`, `-Bot trading` 을 하고 ai-hub 를 재시작한다.
- `llm-calls/registry-error.json` 이 없는지 확인한다.
- `llm-check <id>` 에는 openai 분기가 없어서 `--all` 만 돌린다(기록).

**R15. 돈 계산(R2 보완).**
- 호출 **전에** 센트를 예약한다: 미리보기 5, 동작표 8 (입력 이미지 토큰 포함 보수치).
- 과금되지 않은 실패(4xx 거절, 네트워크)일 때만 돌려준다. 패턴은 `kAll` 과 같다.
- `D1Counts` 에 `add(k, n, limit)` 를 추가하고 테스트한다.
- 모델 요청은 `output_format=webp`, `output_compression=80`.
- 배포 확인에 실제 `/sheet` 1회(약 5센트)를 넣는다.

**R16. 앱 쪽.**
- 세그먼트(300ms 반복)와 GRIP 재세그먼트는 **같은 단일 스레드 executor** 에서 돈다.
- CatchScreen: Claw 가 CARRY 가 되면 오린 결과를 확인한다. 불투명 5% 미만이면 `handedOff()` → `next()` → "아깝다!" 를 띄우고 Dex 에 넣지 않는다.
- `Sheet.cells`: 3×2 로 나눈 각 칸에서 초록이 아닌 부분의 상자를 찾고 여백 6% 로 자른다(칸 경계에 걸린 팔다리 보존).
- AI 요청은 화면이 아닌 앱 범위 코루틴(Application scope)에서 돈다. 화면을 나가도 계속된다.
- 자이로 모드 털 녀석은 나타날 때마다 무작위 방향(dir0–7)이다.
- 매니페스트 `com.google.ar.core` meta-data 에 `tools:replace="android:value"` 를 넣는다.
- 굽기: 녀석당 60초, 전체 20분 제한. 실패하면 앱에 미리 넣은 회색 털뭉치 placeholder(`critters/_placeholder/front.png`)를 쓴다.

## /plan-review 3회차 반영 (High 1 → 반영)

- **R17(High).** 무거운 작업은 "다음 시간대 시작 − 지금 > 예산"일 때만 시작한다. 예산은 Gradle 20분, 굽기 25분이다. 05:30–06:00 도 시간대로 친다(새벽 스터디 작업).
- **R18.** 기기 ID 는 `dev-` + 소문자·숫자 24자다(SharedPreferences 에 한 번 만들어 둔다). 400 은 따로 "앱을 업데이트해 주세요" 로 바꾼다.
- **R19.**
  - `critters/index.json` 은 Chrome 없이 `creatures.js` 의 VIRTUAL · SPECS 를 읽어 node 로 만든다(굽기 실패와 무관).
  - `Frames` 는 파일 하나씩 front → `_placeholder` 순으로 대신한다.
- **R20.** CARRY 에서 GRIP 재세그먼트 결과를 **기다린다**(최대 1.5초, 넘으면 원래 마스크). 결과 중 같은 kind 이고 `result.target` 과 IoU 가 가장 큰 마스크를 쓴다. 털 녀석(CRITTER)은 재세그먼트 · 오려 내기 · 5% 검사를 하지 않는다.
- **R21.**
  - 매니페스트 루트에 `xmlns:tools`.
  - 레지스트리 templates 는 `::NAME` 없이 경로만.
  - 돈 환불은 예약한 만큼 `add(k, −n)` 으로 돌려준다. OpenAI 로 요청을 보내기 **전에** 실패한 경우만 환불한다.

## /plan-review 4회차 (High 0 — 리뷰 끝)

- **R22.**
  - 매 Task 뒤에 `trading-system` 의 `tick.log` 에서 Task 시작 이후 "tick done" 줄 수를 센다(10분당 1개). 모자라면 무거운 작업을 멈춘다.
  - 레지스트리 사본 2곳도 같이 고친다: `trading-system-t0185/trading_system`, `.worktrees/content-t0115-fixes/src/content_system`.
  - AiClient 의 base64 는 `Base64.NO_WRAP` 이다(테스트 1개).
  - 털 녀석 목록은 `site/js/creature.js` 의 `SPECS` 텍스트를 정규식으로 읽어 group 으로 거른다.
  - Task 7 에는 털 녀석 마스크 빈 스텁을 둔다.
  - mpk-deck 트레이도 재시작한다.
