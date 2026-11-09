# 「우리 집 몬스터 잡기」 — 구현 계획 v2 (2026-11-09)

기획: `brief.md`. 운영자 11-09: "기획 → /plan-review → Claude 로 끝까지 초안 앱(웹 + 플로팅 펫 Android)". 그래서 brief 의
"2단계는 1단계 통과 뒤 판단"과 "AR 흐름 시안 먼저"는 이번 초안에서 운영자 지시로 건너뛴다. 실폰 확인은 초안을 받은 운영자 몫.
StandBy 작업과 동시 진행이라 무거운 일은 하나씩(6절). 리뷰 기록: `docs/archive/2026-11-09-catch-plan-review/` (C:\DC\DD).

## 0. 기획서에서 바뀐 것
- **같은 녀석 판정은 사람에게**: 잡힌 뒤 "누구야?" — 같은 종류의 기존 녀석 칩 + "새 친구"(이름 입력, 기본 "멍멍이 N"/"야옹이 N"/"사람 N").
- **모습 라벨은 한 번 탭**: 자동 제안 1개 강조 + 칩 5개. 임베딩 판정 뺌(라벨을 못 줘서 해금표를 못 채움).
- **모델 하나**: 진짜 녀석 찾기와 오려 내기 모두 DeepLab v3 (PASCAL: person 15, cat 8, dog 12) 를 워커에서. ObjectDetector·selfie 모델 안 씀.
- 가상 = 포켓몬 1세대 151종 PokeAPI 공식 일러스트 빌보드. 모습은 `front` 하나. 치이카와는 무료 에셋이 없어 뺌.
- 설명·타입은 템플릿 풀(`lines.js`, 종류별 20줄+·타입 6+), 녀석 id 를 시드로 고정. LLM 호출 없음.

## 1. 폴더
```
works/2026-11-09-catch/
  brief.md  plan.md  manifest.json(status "draft")  .gitignore(app/build, app/.gradle, app/local.properties, *.apk, app/**/build)
  site/index.html
  site/js/catch.js     순수: grade·chance·roll                          + catch.test.mjs
  site/js/geom.js      순수: video→화면 cover 변환, 스와이프 → 착지점·커브, 명중  + geom.test.mjs
  site/js/moves.js     순수: 라벨 → 행동, n/5, 라벨 제안, 교체 판정        + moves.test.mjs
  site/js/petfile.js   순수: 내보내기 직렬화·검증                         + petfile.test.mjs
  site/js/store.js     IndexedDB + persist() + 메모리 폴백
  site/js/ar.js        three.js 장면·자이로 3DoF·빛 (달고나 ar.js 에서 복사, 헤더에 출처)
  site/js/seg.js       워커 래퍼: 워밍업, 주기 찾기, 명중 프레임 오려 내기, 타임아웃
  site/js/seg-worker.js  photo-booth `site/js/pet-worker.js` 복사 + person 클래스 + 박스 계산 (iOS CPU 분기 유지)
  site/js/refine.js    photo-booth `site/js/refine.js` 복사 (실패 시 원본 마스크로)
  site/js/ui.js        화면 전환·칩·도감·상세·카드
  site/js/lines.js     설명·타입 풀
  test/petfile-vector.json   웹·Kotlin 공통 고정 벡터 (S1 에서 손으로 작성, 작은 2×2 PNG 2장)
  app/                 2단계 Android (5절)
```
다른 작품 폴더는 import 하지 않는다(복사 + 출처 주석). `platform.js`·`segment.js` 는 Toss·워터마크가 엮여 있어 가져오지 않는다.

## 2. 웹 화면 흐름
```
첫 화면: [카메라로 잡기] [화면으로 잡기]  · 우상단 도감(N마리·모습 M)
  ↓ 상단 토글로 언제든 모드 전환 (잠그지 않음)
AR: 뒤 카메라 + 가상 녀석 1마리 / 진짜가 보이면 그 위에 링·이름표 "야생의 강아지"
   공: 화면 아래에서 22% 높이(가장자리 제스처 피함), touch-action:none
   엄지로 공을 잡아 밀어 던짐, 놓기 전 원을 그리면 커브
  ↓ 명중 → (진짜면) 그 프레임 고정·오려 내기 시작 → 흔들림 1~3 → 잡힘/탈출
  ↓ 잡힘: 진짜 = "누구야?" → "어떤 모습?" / 가상 = 바로 등록
포획 카드 [공유] [계속 잡기]  ← 계속 잡기 = 다음 공 바로 손에
```
- 빗나감·탈출 = 다음 공이 즉시 손에(0탭 재시도). 화면 위 상시 표시: "N마리 · 모습 M개".
- **화면 모드**: 배경 = Poly Haven CC0 실내 HDRI 의 등장방형 JPG(2k)를 `scene.background` 그대로(기울이면 방 전체가 같이 돔, 출처 README), 가상 녀석만. 자이로 없으면 드래그로 시점.
- 가상 녀석: 잡히거나 30초 지나면 교체. 진짜가 보이면 진짜가 목표(가상은 숨김). 진짜 여럿이면 화면 중앙에 가까운 1개.
- 카메라 거부·없음 → 화면 모드로, 상단 한 줄 안내.

## 3. 규칙 (순수 함수, TDD)
**catch.js**
- `grade({onBody, inRing, ring})` (ring = 던진 순간 링 반지름/최대, 0.3..1): 안 맞음 `null`, 몸만 `"hit"`, 링 안 ring<0.5 `"excellent"`, <0.75 `"great"`, 그 외 `"nice"`.
- `chance(grade, curve)`: hit .50 · nice .65 · great .80 · excellent .95, curve +.10, 상한 .95.
- `roll(p, rnd)` (`rnd` = 0..1 함수): `rnd() < p` → `{caught:true, shakes:3}`, 아니면 `{caught:false, shakes: min(3, 1+floor(rnd()*3*p))}`.
- 링: 반지름 최대 → 30% → 최대, 1.6초 주기 삼각파.
- 순서: 확률 판정이 먼저, 오려 내기는 결과를 바꾸지 않는다(실패하면 박스 크롭 둥근 사각형으로 저장 + 카드에 "흐릿하게 찍힘").
**geom.js**
- `coverMap(videoW, videoH, viewW, viewH)` → video 좌표 → 화면 좌표 (object-fit: cover 와 같은 식).
- 던지기: 공 위에서 시작한 포인터만. 마지막 80ms 속도 v(px/ms). 위쪽 성분 < 0.5 또는 이동 < 60px → 던지기 아님(공 복귀).
  착지점 = 놓은 점 + v × 260ms (화면 위쪽으로), 비행 0.5초, 공은 착지까지 1 → 0.35 배로 작아짐(깊이 느낌), 포물선 정점 = 화면 높이 12% 위.
- 커브: 놓기 전 경로에서 연속 선분 사이 부호 있는 각도의 누적합 |Σ| ≥ 300° → curve, 부호 방향으로 비행 후반에 착지점을 화면 폭 15% 휨.
- 명중: 착지 순간의 목표 박스(가상 = 투영된 빌보드 사각형, 진짜 = 최신 박스) 안 = onBody, 링 원 안 = inRing.
**moves.js**
- 라벨: `front | side | walk | lying | back`. 행동 = 합집합:
  모습 1개 이상 → `bounce, flinch` · `side` → `walk` · `side` 와 `walk` 둘 다 → `run` · `lying` → `sleep` · `back` → `hide`.
- `count(labels)` → "모습 n/5" (가상은 n/1).
- `suggest(boxW, boxH)`: w/h > 1.6 → `lying`, > 1.15 → `side`, 그 외 `front` (walk·back 은 제안 안 함).
- 같은 라벨 재포획: 판정 순위 hit<nice<great<excellent 에서 더 높을 때만 사진 교체, 같거나 낮으면 "이미 있는 모습".
- No. = 전체 녀석 첫 포획 순서(1부터, 지워도 재사용 안 함).
- 가상 재포획: 같은 `pokeId` 는 같은 녀석(새 No. 없음, "또 잡았다 ×n" 카운트만).
**petfile.js** — 2단계 계약. 파일 `우리집몬스터-YYYYMMDD.dotpet`(내용 JSON), 다운로드로 내보냄(Chrome Web Share 는 json 파일 불가). Blob 타입 `application/octet-stream`(확장자 유지).
```json
{ "format": "dotpet", "v": 1, "exported": "2026-11-09T12:00:00Z",
  "creatures": [ { "id": "c_…", "name": "콩이", "kind": "dog|cat|person|pokemon", "no": 3, "pokeId": null,
     "poses": [ { "label": "front|side|walk|lying|back", "png": "<base64>", "w": 256, "h": 256 } ] } ] }
```
- PNG 투명, 긴 변 256. 라벨당 1장. `format`/`v` 다르면 거부, 모르는 라벨·깨진 base64 → 그 모습만 버림, 모습 0개 녀석은 버림.

## 4. 기술 메모
- **AR**: 달고나 `site/ar/ar.js` 의 `gyroTarget`(약 315행)과 프레임 루프 안 첫 자세 배치·2.5초 재배치(약 1419행), `lightFromFrame`(393행),
  FOV 66° `?fov=` 를 복사. 필요한 모듈 상태는 같이 옮긴다. 손 인식·손 가림·도구·`fracture`·`engine` 은 안 가져온다.
  가상 녀석: 빌보드(높이 0.35m), 거리 1.2m, **첫 자세의 시선 방향 그대로, 수직 오프셋 없음** → 녀석 중심이 화면 높이 35~50%(공은 78%, 착지점은 늘 공보다 위라 링을 맞힐 수 있어야 함). 바닥은 모름(3DoF) → 발밑 접촉 그림자 원판만, 숨쉬기 y ±3%.
  three.js 0.160.0 (달고나와 같음). 빌보드 재질은 `MeshBasicMaterial` 이므로 `lightFromFrame` 은 밝기·색만 뽑아 재질 `color` 에 곱하는 부분만 가져온다(env/PMREM·grain 은 안 가져옴).
  **포켓몬 이미지는 어디서 쓰든(three 텍스처, 카드, 내보내기 PNG) `crossOrigin = "anonymous"` 로 한 로더(`loadImg`)를 거친다** — 빠지면 canvas 오염으로 카드·내보내기 실패.
- **진짜 찾기**: `seg-worker` 가 카메라 프레임(긴 변 320 으로 축소한 ImageBitmap)을 받아 DeepLab 확률 두 장: 사람(15), 동물(cat 8 + dog 12 합 — DeepLab 이 한 마리 안에서 cat/dog 를 섞어서).
  각 장에서 0.5 이상 가장 큰 덩어리 박스. 동물의 종류 = 박스 안 cat 합 vs dog 합(“누구야?”에서 개/고양이 고칠 수 있음).
  워커 응답 계약: 박스는 **0..1 정규화**(마스크가 257px 일 수 있어 픽셀 좌표 금지) → 메인에서 video 크기 곱한 뒤 `coverMap`.
  주기: 직전 결과가 오면 다음 프레임(최소 300ms). 워밍업(첫 호출, wasm·모델 다운로드 + GPU 컴파일)은 30초 타임아웃, 그 뒤 호출마다 3초 타임아웃(wasm abort 는 error 이벤트가 없음), 연속 3회 → 워커 terminate 후 재생성.
  박스 EMA α 0.5, 놓친 결과 2회까지는 직전 박스 유지(DeepLab 이 3번 중 1번꼴로 놓침). 0.5 이상 픽셀이 프레임의 3% 미만이면 없음. 링 최대 지름 = 박스 짧은 변 × 0.8.
- **오려 내기**: 명중 순간 원본 해상도 프레임을 캔버스로 고정 → 박스 25% 넓혀 자름 → 워커에 사람 또는 동물(cat+dog 합) 마스크로 → `refine.js` (WebGL2 없거나 throw → 원본 마스크)
  → 투명 PNG 긴 변 256. 4초 타임아웃 → 박스 크롭 폴백.
- **모델 로드**: 카메라가 켜진 직후 워커 생성 + 검은 프레임 1장으로 워밍업(첫 GPU 컴파일 수 초). 워밍업 전에는 링이 안 뜨고 가상만.
  모델 URL = `pet-worker.js` 의 DeepLab 상수, tasks-vision 0.10.14. iPhone 은 CPU delegate(워커에 이미 있음).
- **WebGL**: 메인 페이지 컨텍스트는 three.js 1개 + refine 1개(오려 낼 때만 만들고 끝나면 `loseContext`). MediaPipe 는 워커 안.
- **워커 순서**: 명중 ~ 오려 내기 끝까지 찾기 루프를 멈춘다(같은 워커, 찾기 타임아웃 terminate 가 오려 내기를 죽이지 않게).
- **눈 맞춤 자리**: `seg.js` 에 `gate(kind) { return true }` 한 줄.
- **저장**: IndexedDB `dotcatch` v1, stores `creatures`(keyPath id), `poses`(keyPath id, index creatureId), `meta`(nextNo).
  시작 때 `navigator.storage.persist()`. 열기 실패 → 메모리 + 상단 경고 "이 브라우저에선 도감이 저장되지 않아요".
  도감 상단에 iOS 안내 한 줄: "Safari 는 7일 안 열면 지울 수 있어요 — 내보내기로 보관".
- **카드**: 1080×1350, 카드 화면이 열릴 때 미리 그려 Blob 준비 → [공유] 탭에서 바로 `navigator.share({files})`, 실패·미지원 → 다운로드.
  내용: 스티커/일러스트, `No.003`, 이름, 타입, 설명, 판정 글자, 아래 `dot-connects.github.io/dot-lab/works/2026-11-09-catch/site/`.
- iOS: 시작 탭에서 DeviceOrientation 권한. 진동은 Android 만(`navigator.vibrate` 흔들림마다 30ms).

## 5. 2단계 Android (`app/`, 패키지 `kr.dot.catchpet`, 앱 이름 "우리집 몬스터")
**새 독립 Gradle 프로젝트.** StandBy 에서 wrapper·AGP·Kotlin 버전·`gradle.properties`(`android.useAndroidX=true` 등)·루트 `build.gradle.kts` 의 빌드 폴더 재지정 블록을 복사하되 경로는 `%LOCALAPPDATA%/catchpet-build` 로 바꾼다(C:\DC 동기화 도구가 `build/` 를 잠가서 StandBy 가 밖으로 뺀 것, 같은 경로를 쓰면 StandBy 와 충돌). `local.properties` 는 StandBy 의 sdk.dir 로 생성(커밋 안 함). Compose·roborazzi 는 가져오지 않는다.
minSdk 34 / targetSdk·compileSdk = StandBy 와 같게, debug 서명. View 기반.
- **MainActivity**: [펫 파일 열기] = SAF `ACTION_OPEN_DOCUMENT` `*/*` → `PetFile` 파싱 → `filesDir/pets/<id>/<label>.png` 로 풀기(같은 id 는 덮어씀).
  목록: 이름·종류·"모습 n/5"·행동 목록. 탭 → [띄우기]: 오버레이 권한 없으면 `ACTION_MANAGE_OVERLAY_PERMISSION`, POST_NOTIFICATIONS 요청 →
  선택 id 를 SharedPreferences 에 저장 → 서비스 시작. [내리기].
- **PetService**: 매니페스트에 `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` 권한 + 서비스 `foregroundServiceType="specialUse"` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` "floating pet" (StandBy 매니페스트엔 없음 — 빠지면 `startForeground` SecurityException). START_STICKY, 시작 때 prefs 에서 녀석을 읽음.
  `TYPE_APPLICATION_OVERLAY` 96dp 창, 플래그 `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS`(뒤 앱 키 입력 안 뺏음, 화면 밖으로 반쯤 숨기 가능) + `PetView`(현재 프레임 비트맵, 좌우 반전, 숨쉬기). 화면 꺼짐 → 정지, 켜짐 → 재개.
  화면 크기 변경(폴드 접기/펴기, 회전) → 경로 다시 계산, 펫을 가장 가까운 변으로.
- **Behaviour (순수 Kotlin, JUnit)**: 입력 = 해금 행동, 화면 크기, 시드. 상태 bounce/walk/run/sleep/hide 중 해금된 것만, 각 3~12초.
  walk 40dp/s · run 120dp/s 로 화면 둘레를 따라 이동(아래 변 위주: 아래 60%, 위 25%, 옆 15% — 옆 변은 멈추지 않고 지나감, 뒤로 가기 제스처 가림 최소화).
  run = side↔walk 사진 150ms 교대. hide = 가장 가까운 변 밖으로 70% 숨었다 3초 뒤 나옴. sleep = lying 사진 + "z".
  탭 → flinch 0.4초(sleep 이면 깨어나 bounce). 길게 500ms → MainActivity. 드래그 → 따라오다 놓으면 가까운 변에 붙음.
- **PetFile (JUnit)**: `site/test/petfile-vector.json` 를 테스트 리소스로 복사해 웹과 같은 결과(녀석 수·라벨·버린 것). 파서는 Android API 없이: `java.util.Base64` + `org.json`(`testImplementation("org.json:json:20240303")`), PNG 는 시그니처 8바이트로만 검사, 비트맵 디코드는 파서 밖(MainActivity).
- 빌드: `gradlew assembleDebug -Dorg.gradle.workers.max=2`. APK 는 SendUserFile. adb 없음 → 기기 확인은 운영자.

## 6. 실행 순서·완료 기준
한 단계 = 새 서브에이전트 1개, 앞 단계가 끝나야 다음. 헤드리스 Chrome·Gradle 은 단계 끝에 반드시 종료.
StandBy 세션의 Gradle/java 가 돌고 있으면 S4 빌드는 기다린다.
| # | 단계 | 완료 기준 (실행으로 확인) |
|---|---|---|
| S1 | catch/geom/moves/petfile + 테스트 + 벡터, AR(가상), 던지기·흔들림, 화면 모드, 상시 카운터 | `node --test site/js/*.test.mjs` 통과 · 헤드리스(`?seed=1&touch`)에서 콘솔 에러 0 · viewport 390×844 · DeviceOrientation A(`alpha 0, beta 90, gamma 0`) 주입 → B(`alpha 10`) 주입 후 2.5초 안에 스크린샷 2장(녀석이 B 에서 화면 안에서 옆으로 이동) · 스와이프 주입으로 포획 1회 → 카운터 1 · 빗나감 뒤 공이 탭 없이 다시 손에 |
| S2 | seg-worker/seg/refine, 진짜 링, 오려 내기, 누구야·모습 칩, 저장 | `?still=<사진>` 주입 경로(카메라 대신 사진)로 photo-booth `research/pet-matting/media` 의 `white-dog.jpg`·`black-cat.jpg` + 사람과 개가 같이 있는 `selfie-dog.jpg`(사람·동물 박스가 한 장에서 따로 잡히는지): 박스 스크린샷 + 투명 PNG 저장, 3배 확대 크롭을 직접 눈으로 확인 · 칩 → IndexedDB 에 creature/pose 저장 → 새로고침 뒤 카운터 유지 · 같은 녀석 재포획 시 "모습 2/5" |
| S3 | 도감·상세(실루엣 빈칸)·카드·내보내기 | 카드 PNG 생성(포켓몬·진짜 각 1장 눈으로 확인) · 내보낸 .dotpet 을 `petfile` 로 다시 읽어 녀석 수·라벨 일치 · 이후 **push** (초안 URL 운영자 전달용) |
| S4 | Android 앱 | JUnit(Behaviour·PetFile) 통과 · `assembleDebug` APK · 빌드된 APK 의 매니페스트(`aapt2 dump xmltree` 또는 merged manifest)에 FGS 권한·서비스 타입 확인 · Behaviour 시뮬레이션 로그(해금 1개/5개 각각 60초) 확인 |
| S5 | 마무리 | `mechanics.md` 9번 행 상태 "draft built", T-0162 attempts, ROADMAP Recent, `references/techniques.md`(DeepLab 한 모델로 찾기+오려 내기, PokeAPI 스프라이트) |
- "한 번 더 하고 싶은가"는 운영자가 실폰으로 판정 — 초안 보고에 그 질문을 명시.
- **git**: dot-lab 작업 트리는 StandBy 세션과 공유. 단계마다 로컬 커밋 `git add works/2026-11-09-catch && git commit -m … -- works/2026-11-09-catch`
  (pathspec 커밋이라 다른 세션이 stage 한 파일은 안 딸려 옴) 전후로 `git status --short` 확인. **push 는 S3 끝에 한 번, S4 끝에 한 번**
  (push = Pages 공개. 목록 페이지엔 안 올림). push 전 `git log origin/main..HEAD --stat` 로 다른 세션의 미푸시 커밋이 섞였는지 보고, 섞였으면 push 하지 않고 보고.

## 7. 하지 않는 것 (초안)
서버·순위·교환, 배틀·진화, 치이카와, 눈 맞춤 실제 판정, 개체 자동 인식, 생성형 이미지, 블로그·릴스·게시, dot-lab 목록 등록, 펫 파일 Web Share.
