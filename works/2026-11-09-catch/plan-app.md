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

### Task 0: 오려 내기 후보 실측 (PC, 웹 tasks-vision)

**Files:** Create `works/2026-11-09-catch/research/seg-compare.md`. scratchpad 하네스(커밋 안 함).

- [ ] **Step 1:** 고양이 영상 앞 10초 120프레임에 세 가지를 돌린다. 각각 고양이(또는 사람)를 찾은 프레임 수를 센다. 하네스는 이미 있는 scratchpad `claw/seg.html` 을 고친다.
  - (a) DeepLab 문턱 0.5
  - (b) DeepLab 문턱 0.3
  - (c) EfficientDet-Lite0 ObjectDetector (점수 ≥ 0.3, COCO cat/dog/person)
- [ ] **Step 2:** 결과 표를 `seg-compare.md` 에 쓰고 고른 조합을 적는다.
  - 기본 규칙: DeepLab 0.3 마스크를 쓰고, 마스크가 비면 EfficientDet 상자에 내접한 타원을 마스크로 쓴다.
- [ ] **Step 3:** Commit `docs(catch): segmenter comparison on the kitten clip`.

### Task 1: Gradle · 의존성 · 앱 뼈대

**Files:** Modify `app/app/build.gradle.kts`, `app/build.gradle.kts`, `AndroidManifest.xml`; Create `ui/App.kt`; Modify `MainActivity.kt`.

- [ ] **Step 1:** 플러그인 `org.jetbrains.kotlin.plugin.compose` 2.0.21, `buildFeatures { compose = true }` 를 넣는다.
- [ ] **Step 2:** 의존성을 넣는다: compose BOM, activity-compose, camera-core/camera2/lifecycle/view 1.4.0, tasks-vision 0.10.14, arsceneview 2.2.1, billing-ktx 7.1.1. test 쪽은 robolectric·roborazzi·compose-ui-test-junit4 를 넣는다.
- [ ] **Step 3:** 매니페스트에 권한·기능을 넣는다.
  - 권한: CAMERA, INTERNET, SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE(_SPECIAL_USE), POST_NOTIFICATIONS
  - `<uses-feature android:name="android.hardware.camera.ar" android:required="false"/>`
  - `<meta-data com.google.ar.core value="optional"/>`
- [ ] **Step 4:** `MainActivity` 를 `ComponentActivity` 로 바꾸고 `App()` 을 띄운다. App 은 화면 4개(Catch/Dex/Detail/Pet)를 상태 하나로 전환한다(내비게이션 라이브러리 없음).
- [ ] **Step 5:** `assembleDebug` 와 기존 테스트가 통과하는지 본다. Commit.

### Task 2: 마스크와 추적 (순수)

**Interfaces — Produces:**
```kotlin
class Mask(val w: Int, val h: Int, val bits: BooleanArray, val kind: Kind) {
  enum class Kind { PERSON, DOG, CAT, CRITTER }
  val area: Int; val box: Box?            // Box(x0, y0, x1, y1) inclusive, mask px
  fun at(x: Int, y: Int): Boolean
  fun firstRowInColumn(cx: Int, halfWidth: Int = 2): Int?   // first row with a set pixel in cx±halfWidth
  fun spanInBand(y0: Int, rows: Int): IntRange?               // min..max x of set pixels in rows y0 until y0+rows
  companion object { fun ellipseIn(w: Int, h: Int, b: Box, kind: Kind): Mask }
}
fun iou(a: Mask, b: Mask): Float
class Tracker(private val hold: Int = 4) { fun update(m: Mask?): Mask? }   // returns m, or the last non-empty one for up to `hold` misses
```
- [ ] **Step 1: 실패 테스트**
  - `firstRowInColumn`: 2×2 블록이 (5..6, 10..11)에 있으면 cx=5 → 10, cx=20 → null.
  - `spanInBand(10, 18)` → 5..6.
  - `ellipseIn` 의 면적이 상자 면적 × π/4 의 ±10% 안.
  - `iou`: 같은 마스크 1, 겹침 없음 0.
  - Tracker: [m, null, null, null, null, null] → m, m, m, m, m, null. 여섯 번째(5번째 놓침)에 null.
- [ ] **Step 2:** 실패를 확인한다. **Step 3:** 구현한다. **Step 4:** 통과를 확인한다. **Step 5:** Commit.

### Task 3: 집게 상태 기계 + 판정 (순수, 틀리면 비싼 곳)

**Interfaces — Produces:**
```kotlin
class Claw(val screenW: Float, val screenH: Float, val railY: Float, private val rnd: () -> Float) {
  enum class St { AIM, DOWN, GRIP, UP, FALL, CARRY, DONE }
  var st: St; var x: Float /*0..1*/; var y: Float /*px, claw hub*/; var open: Float /*0..1*/
  var result: Result?          // Result(win: Boolean, p: Float, target: Mask?, hitRow: Int?)
  fun aim(x01: Float)          // AIM only
  fun release()                // AIM -> DOWN
  fun step(dtMs: Float, targets: List<Mask>, toMask: (Float, Float) -> Pair<Int, Int>)
  fun next()                   // any end -> AIM (continuous play)
}
fun chance(m: Mask, cx: Int, row: Int): Float  // 0.9 - 0.7 * min(1, |cx - mid| / max(3, half)), band 18 rows; 0 if no span
```
규칙(시안 `claw-endings.html` 과 같게):
- 하강 속도 0.26 px/ms. 집게 끝 = hub + 44px.
- 끝이 닿은 열의 첫 마스크 행에 도달하면 GRIP. 여러 마스크면 가장 위 행이 먼저.
- 바닥(screenH − 30)까지 아무것도 없으면 헛집기.
- GRIP 560ms → UP(0.15 px/ms). 실패면 70px 올라간 뒤 FALL.
- 성공이면 레일까지 올라간 뒤 CARRY(캡슐 단계로 넘김). DONE → next() 로 AIM.
- [ ] **Step 1: 실패 테스트**
  - 가운데 정렬: `chance` = 0.9.
  - 몸 가장자리: 0.2.
  - 몸 밖: 0.
  - 두 마스크: 위에 있는 것이 target.
  - 아무것도 없음: 헛집기(result.target == null, win false).
  - rnd=0.05 → win, rnd=0.95 → FALL 을 거쳐 DONE.
  - release() 는 AIM 에서만 먹힌다.
  - next() 뒤 st == AIM.
- [ ] **Step 2–4:** 실패 확인, 구현, 통과. **Step 5:** Commit.

### Task 4: 캡슐 (순수)

```kotlin
class Capsule(startX: Float, startY: Float, val floorY: Float, val screenW: Float) {
  enum class Phase { WRAP, DROP, ROLL, GONE }
  var x: Float; var y: Float; var r: Float; var rot: Float; var phase: Phase
  fun step(dtMs: Float)
}
```
- WRAP: 420ms 동안 반지름이 0 → 64 로 ease-out.
- DROP: 중력 0.0042 px/ms², 바닥에서 튀어 오르는 비율 0.45. 3번 튀면 ROLL.
- ROLL: 가속 0.0006, 화면 밖으로 나가면 GONE.
- [ ] 테스트:
  - 520ms 뒤 r == 64.
  - DROP 은 유한 시간(≤ 5초 시뮬레이션) 안에 ROLL 이 된다.
  - ROLL 은 ≤ 5초 안에 GONE 이 된다.
  - y 는 floorY − r 를 넘지 않는다.
- Commit.

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

## 계획 리뷰 반영 (2026-11-12, 독립 Claude 리뷰 1회)

위 Task 본문보다 이 절이 우선한다.

1. **Worker 가 앱 요청을 막음:** Origin 헤더가 없으면 403 이다. Task 9 에서 Origin 이 없고 `x-app: catchpet` 이 있으면 받게 하고, 테스트를 하나 넣는다. AiClient 는 기기 ID `dev-[a-z0-9]{8,32}` 를 보낸다.
2. **Robolectric:** 4.14.1 과 `src/test/resources/robolectric.properties` 의 `sdk=34` 를 쓴다. 그 밖에 아래를 넣는다. Windows 에서 화면 렌더가 안 되면 렌더 단계는 건너뛴다(밤을 막지 않음).
   - `isIncludeAndroidResources = true`
   - `debugImplementation ui-test-manifest`
   - Roborazzi 플러그인
   - `@GraphicsMode(NATIVE)`
3. **첫 화면은 CameraX + 자이로가 기본이다.** ARCore 는 펫 화면의 "AR 털 녀석 (실험)" 스위치로만 켠다. 세션 생성에 성공한 뒤에만 쓰고, 실패하면 try/catch 로 CameraX 로 돌아간다.
4. **순서:** 1 → 2·3·4 → 7(CameraX, 털 녀석은 자이로 + 임시 그림) → 8 → 11 → 12(오버레이) → 10 → 9 → 6(굽기, 시간 제한, 실패하면 모든 방향에 front) → 5 AR → 배경화면.
   - Task 하나가 초록이 될 때마다 "마지막 성공 APK" 를 scratchpad 에 복사해 둔다.
5. **좌표:** Preview 와 ImageAnalysis 를 `ViewPort` / `UseCaseGroup` 하나로 묶고, 이미지 → 화면 행렬 하나(`ScreenMap`, 순수)로 마스크 ↔ 화면을 바꾼다. 회전과 FILL_CENTER 자르기를 반영한다. 순수 테스트 1개. AR 은 `transformCoordinates2d`.
6. **오려 내기 해상도:** ImageAnalysis 는 긴 변 약 1280 으로 받는다. 세그먼트에는 줄여서 넣는다. AR 모드의 오려 내기는 640×480 이라 거칠어진다(기록).
7. **펫 잠:** 터치하면 sinceTouch 가 0 이 된다. 밤이나 충전이어도 sinceTouch ≥ 30초여야 잔다. 테스트를 넣는다.
8. **배경화면:**
   - 매니페스트: `BIND_WALLPAPER` permission 속성, intent-filter, `live_wallpaper` feature(required=false), `ACTION_CHANGE_LIVE_WALLPAPER`
   - 터치는 `android.wallpaper.tap` 한 번만 받는다.
   - 배경은 **사용자가 고른 사진**이다(없으면 은은한 그라데이션). 운영자 확인이 필요하다고 보고한다.
9. **빠진 것 추가:**
   - 털 녀석 크기 1/4·1/5 전환 칩(펫 화면 개발자 칸)
   - 503 `off` / 일일 한도 / 실패 안내 문구 "AI 그림을 잠시 못 그려요"
   - `/sheet` 는 구매 1건당 호출 1번 + 재시도 1번(기기 ID + 토큰으로 D1 카운트)
10. **Gradle:**
    - compose 플러그인을 루트에 `apply false`
    - billing-ktx 는 출시 단계로 미룸(Purchase 는 디버그 토큰만)
    - `abiFilters("arm64-v8a")`
    - `pickFirsts libc++_shared.so`
11. **출시 단계 메모:** Play 의 16KB 페이지 정렬. tasks-vision · Filament 버전을 올리고 `zipalign -P 16` 으로 확인한다(6단계).
12. **Worker 배포는 마지막에 건너뛸 수 있는 단계다.** 로그인이 만료됐으면 기록만 남긴다. 비밀값은 파이프로 넣는다. README 에 "동결된 웹 게임도 이제 OpenAI 를 탄다"고 적는다.
