# 여행 책자 앱 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 여행 날짜만 넣으면 폰 사진·영상에서 잘 나온 것을 골라, 승인된 6개 테마로 넘겨 보는 책 + 페이지 이미지 + PDF + 하이라이트 영상을 만드는 Android 앱(Flutter).

**Architecture:** Flutter = 갤러리·거르기·AI 호출·저장·내보내기. **책·편집 화면 = 승인 시안 HTML/CSS 를 그대로 쓰는 WebView 런타임**(`assets/book/`, `WebViewAssetLoader` 로 앱 자산과 책 폴더를 한 origin 에서). 거르기·장 나누기·타임라인·참고 자료·AI 응답 검증·쪽 배치는 순수 Dart 로 테스트한다. AI 는 Cloudflare Worker 한 곳(키 숨김·월 상한, catch Worker 방식).

**Tech Stack:** Flutter 3.50.1 / Dart 3.14 (`C:\DC\tools\flutter`, 이미 설치), JDK `C:\DC\tools\jdk-17`. pub(설치됨, 버전은 pubspec.lock): `photo_manager`, `geocoding`, `flutter_inappwebview`, `file_picker`, `pdf`, `share_plus`, `image`, `path_provider`, `http`, `read_pdf_text`, `pdfx`. Kotlin 채널 `kr.dot.tripbook/media`(androidx ExifInterface, MediaMetadataRetriever, Media3 Transformer). Worker: Cloudflare Workers + D1 + ratelimits, `@anthropic-ai/sdk`, `node --test`.

**Spec:** `works/2026-11-24-travel-book/spec.md` (11-25 판). 리뷰 기록: `works/2026-11-24-travel-book/review/`.

## Global Constraints

- applicationId `kr.dot.tripbook`, 이름 "여행 책자", minSdk 29, targetSdk 35. Android 만 빌드, Dart 에 Android 전용 가정 금지.
- 원본은 폰을 떠나지 않는다. Worker 로 가는 것: 긴 변 512px JPEG(품질 80), 영상 장면, 현지 시각·장소 이름·참고 자료 글. 좌표 숫자는 Worker 로 보내지 않는다. (장소 이름은 기기 Geocoder 가 구한다 — Google 에 좌표가 가지만 사진은 아니다.)
- 이미지 디코딩은 플랫폼에 맡긴다(`photo_manager` `thumbnailDataWithSize` / `thumbnailDataWithOption`, JPEG 출력). `package:image` 는 256px 썸네일 측정에만. HEIC 는 플랫폼이 JPEG 로 준다.
- AI 이미지 상한 60장 = 사진 ≤ 44 + 영상 장면 ≤ 12 + 참고 자료 이미지 ≤ 4. 모델 `claude-sonnet-5-5`(spec "Claude Sonnet"), Worker `MODEL` 변수 한 곳. lab Worker 라 `ai-hub/llm-registry.toml` 대상 아님(catch Worker 와 같음, 시스템 코드 아님).
- Worker 는 요청 내용·AI 답을 저장하지 않는다. 로그 = 상태·토큰 수만. 월 상한 `MONTH_CENTS = "1000"`, 하루 책 상한 `DAY_BOOKS = "20"`, IP 당 분당 4회.
- 앱 토큰은 코드에 없다: `--dart-define-from-file=secrets.json`(gitignore). 커밋 전 `git diff --staged` 에 토큰 없음 확인.
- 'AI' 라는 말은 첫 실행 한 줄, 진행 화면, 상한 문구("이번 달 AI 사용량이 다 찼어요", spec 그대로)에서만.
- 디자인 정본 = `mockups/r5/{a,b,c,diary,mag.tpl,album}.html` + `mockups/edit-ui/src.html`. 숫자·색·글꼴을 바꾸지 않는다. 사진 위 작은 글자 대비 ≥ 4.5:1. 색 면은 표지에만, 사용자가 고르는 색은 표지 색 하나.
- 글꼴: 한글 6가족(Pretendard, Wanted Sans, Paperlogy, LINE Seed KR, Gmarket Sans, SUIT — 원본은 scratchpad `fontsrc`, 지워질 수 있음) + 시안의 라틴 5가족(Inter Tight, Instrument Serif, DM Mono, Red Hat Mono, JetBrains Mono — Google Fonts OFL, 내려받기는 운영자 승인 항목). 전부 `assets/book/fonts/` 에 넣고 오프라인으로만 쓴다. 각 라이선스 파일도 같이 넣는다(repo 가 공개 Pages 라 재배포 조건 확인).
- 무거운 작업(Gradle·에뮬레이터·헤드리스 Chrome)은 Task 마다 `docs/procedures/heavy-jobs.md` 칸 잡기(ListAgents → SendMessage) 후. Chrome 은 `mockups/r5/renderlock.py` `render_slot()` 안에서.
- 테스트는 틀리면 비싼 곳만(돈·파서·경계·잃어버리는 사진). 레이아웃·문구는 스크린샷으로 본다.
- 시안과 다르게 하는 곳(운영자 보고 대상): ① 시간대 — GPS 위치 시간대 대신 "사진 EXIF 의 현지 시각, 영상은 가장 가까운 사진의 오프셋"(더 정확하고 패키지 2개를 안 씀) ② 정보 줄의 날씨는 1차에서 뺀다(과거 날씨는 외부 API 에 좌표를 보내야 함) ③ 지도는 장소 실루엣 없이 점 + 경로선.

## 마일스톤

```
M1 (운영자 폰에서 첫 책): 1 → 2,3,4,5 → 6 → 7 → 8 → 9 → 10 → 11 → 12
M2 (나머지 spec):         13 테마 5개 → 14 편집 6단계 → 15 내보내기·영상 → 16 실제 여행 2회차
```
M1 은 테마 a 하나로 처음부터 끝까지 돌려 운영자 폰에서 실제 여행 1건을 만든다(spec 검증의 실측 숫자). 범위를 줄이는 게 아니라 순서다 — M2 는 M1 뒤 바로 이어간다.

## 파일 구조

```
app/lib/core/models.dart       Shot Candidate Pick Clip BookOptions BookPlan LayoutDef PageSpec Book (순수)
app/lib/core/prefilter.dart    측정·판정·연사·상한            test/prefilter_test.dart
app/lib/core/chapters.dart     현지 시각·장 나누기·gate        test/chapters_test.dart
app/lib/core/timeline.dart     타임라인 3형식                   test/timeline_test.dart
app/lib/core/refdocs.dart      참고 자료 글 조각                test/refdocs_test.dart
app/lib/core/plan_check.dart   AI 응답 검증·대체 책             test/plan_check_test.dart
app/lib/core/paginate.dart     쪽 배치                          test/paginate_test.dart
app/lib/device/{gallery,places,inputs,media_channel}.dart
app/lib/ai/client.dart         Worker 호출(스트림, 재시도 없음)
app/lib/pipeline.dart          날짜 → 조회 → 거르기 → 장 → AI → 고르기 → 배치 → 저장 (진행 Stream)
app/lib/book/{store,viewer,export}.dart
app/lib/ui/*.dart              시작·옵션·찾는 중·둘 중 하나·책장·내보내기
app/android/app/src/main/kotlin/kr/dot/tripbook/MediaChannel.kt
app/assets/book/{index.html,book.js,edit.js,contrast.js,fonts/,themes/<t>/{theme.css,layouts.html,layouts.json}}
worker/{wrangler.toml,schema.sql,src/index.js,src/counts.js,src/prompt.js,test/worker.test.js}
tools/{check_book.py,seed_emulator.py}
```

## 공용 타입 (Task 2 가 `models.dart` 에 만들고 모두 이것만 쓴다)

```dart
class Shot { String id; DateTime utc; DateTime? wall; Duration? exifOffset; bool isVideo; int durationMs;
  double? lat, lng; String relPath; int w, h; }            // wall = EXIF DateTimeOriginal (camera clock)
class Candidate { Shot shot; double sharp, luma; int dhash; String? place; DateTime local; }
class Pick { String id; String caption; int score; }        // score 0..100 from AI, sharpness rank without AI
class Clip { String id; int startMs, endMs; String? file; } // file = trimmed copy inside the book folder
class BookOptions { String length = 'normal'; String? theme; String textAmount = 'caption_essay';
  String tone = 'auto'; String chapterBy = 'day'; String people = 'mix';
  Set<String> extras = {'numbers','map','essay','video'}; String format = 'phone'; String quality = 'reduced';
  bool tone_match = false; int highlightSec = 45; String music = 'calm'; }
class BookPlan { List<String> titles; String theme; List<Pick> picks; List<(String,String)> ask;
  Map<String,String> chapterLines /* 'yyyy-MM-dd' -> line */; String essay; List<Clip> clips; bool aiText; }
class LayoutDef { String id; String kind; List<String> slots /* P|L|A */; List<String> texts; List<String> formats; }
class PageSpec { String layout; List<String> ids; List<String> crop /* "50% 50%" per slot */; Map<String,String> text; }
class Book { String id; BookOptions opts; BookPlan plan; List<PageSpec> pages; List<PageSpec> original;
  List<String> spare /* picks not on any page */; Map<String,dynamic> edits; String cover; String? coverColor; }
```
`PageSpec.text` 키 = 테마 `layouts.json` 의 `texts` 와 같은 이름: `title, period, place, time, caption0..3, day, weather, n_days, n_places, n_photos, km, essay, folio, running`. paginate 는 배치가 원하는 키만 채운다.

---

### Task 1: 뼈대 (진행됨: create + pub add + 매니페스트)

- [x] `flutter create`, pub add, applicationId·minSdk 29·targetSdk 35·라벨·권한(READ_MEDIA_IMAGES/VIDEO/VISUAL_USER_SELECTED, ACCESS_MEDIA_LOCATION, INTERNET), `org.gradle.workers.max=2`, jvm 3G.
- [ ] 한글 글꼴 `fontsrc` → `assets/book/fonts/`(woff2 변환, 라이선스 파일 포함). 라틴 5가족은 운영자 승인 뒤 Google Fonts 에서 받는다. 합계 크기 기록(목표 ≤ 15 MB).
- [ ] `secrets.json`, `build/` gitignore. 확인(빌드 칸): `flutter test`, `flutter build apk --debug`. Commit `feat(travel-book): flutter app skeleton`.

### Task 2: 1차 거르기 (순수) + 공용 타입

```dart
({double sharp, double luma, int dhash}) measure(img.Image thumb256); // Laplacian variance (grey), mean luma, 64-bit dHash
bool isScreenshot(Shot s);  // relPath contains screenshots|스크린샷|screen recordings (case-insensitive)
bool isBad(Candidate c);    // sharp < kSharpMin || luma < 28 || luma > 245
List<Candidate> dedupeBursts(List<Candidate> xs); // <= 4 s apart and hamming <= 10: keep sharpest; time order
List<Candidate> capEvenly(List<Candidate> xs, int cap); // per local day share ∝ count, min 3 per day, best sharpness first
```
- [ ] 테스트(`package:image` 합성): 체커보드 vs `gaussianBlur(radius:6)` 판정, 검정 = bad, 연사 3 + 다른 1 → 2장, 3일 × 30장 `capEvenly(36)` 날마다 ≥ 10, 스크린샷 경로 1건. FAIL → 구현 → PASS. Commit.

### Task 3: 현지 시각 · 장 나누기 · 시작 판정 (순수)

```dart
DateTime localOf(Shot s, List<Shot> photosNearby); // photo: wall ?? utc+exifOffset ?? utc.toLocal(); video: utc + offset of the nearest photo with wall
List<Chapter> byDay(List<Candidate> xs);   // day = local date, day starts 04:00
List<Chapter> byPlace(List<Candidate> xs); // runs of the same place; null joins previous run; a run never crosses a day
class Chapter { String day; String? place; List<Candidate> items; }
({bool ok, String? message, int noGps}) gate(int photos, int noGps); // < 5 photos → "사진이 N장뿐이에요, 기간을 넓혀 볼까요?"
```
- [ ] 테스트: 하와이 사진(wall 06-03 23:30, utc 06-04)은 현지 06-03 장; 01:30 현지 → 전날; 영상은 가까운 사진 오프셋을 받음; `byPlace` 날 경계에서 끊김; `gate(4,0)` 문구, `gate(10,3).noGps == 3`. Commit.

### Task 4: 타임라인 파서 (순수)

```dart
class Visit { DateTime start, end; double lat, lng; String? name; }
class Timeline { List<Visit> visits; List<List<(double,double)>> paths; }
Timeline parseTimeline(String json);  // FormatException when no shape matches
String? placeAt(Timeline t, DateTime utc); // visit covering utc ±15 min
```
- [ ] 손으로 만든 표본 3개(`test/fixtures/timeline_{android,ios,takeout}.json`: Android `semanticSegments` + `"37.5°, 127.0°"`, iOS 최상위 배열 + `"geo:37.5,127.0"`, Takeout `timelineObjects` + `latitudeE7`). 테스트: 각 visits 2·paths 1, 모르는 형식 예외, `placeAt` 경계. Commit.

### Task 5: 참고 자료 (순수 + 접착)

```dart
class RefPiece { String? day; String text; String source; }
List<RefPiece> fromText(String raw, String source, {required DateTime tripStart}); // 2026.6.3, 6/3, 6월 3일, 2026-06-03, 3일차, 둘째 날
String htmlToText(String html);
List<RefPiece> trimForAi(List<RefPiece> xs, {int maxChars = 12000}); // every day kept, longest cut first
```
- [ ] 테스트: 날짜 형식마다 한 줄, 날짜 없는 글 day null, `trimForAi` 날 수 유지.
- [ ] `inputs.dart`: txt·md·html·붙여 넣기 = 글. pdf = `read_pdf_text` ≥ 200자면 글, 아니면 `pdfx` 앞 쪽들을 512px 이미지(참고 이미지 몫 ≤ 4). 이미지 = 플랫폼 디코드 512px. 실패 파일 이름 목록 반환 → "못 읽은 파일: 이름". Commit.

### Task 6: 기기 조각 + 에뮬레이터 첫 실행

- [ ] 권한: `requestPermissionExtend(requestOption: PermissionRequestOption(androidPermission: AndroidPermission(type: RequestType.common, mediaLocation: true)))`. `limited`(일부 사진만 허용)면 "전체 사진 허용이 필요해요" + 설정 열기.
- [ ] 조회: 사용자 날짜를 앞뒤 14시간 넓혀 `createTimeCond` 로 조회 → `localOf` 계산 뒤 현지 날짜로 다시 거른다. photo_manager 가 `DATE_TAKEN` 을 쓰는지 소스에서 확인해 주석 한 줄(아니면 `DATE_TAKEN` 조건을 직접).
- [ ] 측정: `thumbnailDataWithSize(256)` → isolate 에서 `measure`. 400장 기준 시간·메모리를 기록.
- [ ] `MediaChannel.kt`: `exif(uri) -> {wall, offset}`(TAG_DATETIME_ORIGINAL, TAG_OFFSET_TIME_ORIGINAL), `videoFrames(uri) -> 4장`(길이의 15/40/65/90%, `getScaledFrameAtTime` 긴 변 512, JPEG 80), `trim(uri, startMs, endMs, outPath)`(Media3, 720p, 소리 유지).
- [ ] `places.dart`: `placemarkFromCoordinates` → `locality ?? subAdministrativeArea ?? name`, 좌표 소수 2자리 캐시. 장소 우선순위 = 사진 GPS > `placeAt`(타임라인) > 없음.
- [ ] `tools/seed_emulator.py <사진 폴더>`: EXIF(DateTimeOriginal·OffsetTimeOriginal·GPS) 붙인 사본 + 일부러 흐림·어둠·스크린샷·연사 사본 → `adb push /sdcard/DCIM/Camera` + 미디어 스캔. 표본 사진은 repo 에 커밋하지 않는다.
- [ ] 확인(빌드 칸 + 에뮬레이터): "N장 → M장, 장 K개, 위치 없는 사진 J장". 흐린 사본 분포로 `kSharpMin` 확정(근거 주석 한 줄). Commit.

### Task 7: Worker

- [ ] `counts.js` = catch `worker/src/index.js:154-195` 의 `MemoryCounts`/`D1Counts`/`num` 을 복사(파일 첫 줄 "copied from works/2026-11-09-catch/worker, keep in sync"). 단위는 **밀(1/10 센트) 정수**.
- [ ] `POST /book`: `x-app-token` 확인(401) → ratelimit(429) → 이미지 > 60 (413) → `DAY_BOOKS` take → 예약 = 입력 추정(이미지 × 260 + 글자 수/2 토큰 × $2/M) + `max_tokens` 12000 × $10/M, 밀 올림. 상한 못 넘으면 503 off, SDK 호출 0.
- [ ] 호출: `new Anthropic({ apiKey, maxRetries: 0 }).messages.stream({ model: env.MODEL, max_tokens: 12000, output_config: { effort: "medium", format: { type: "json_schema", schema } }, system, messages })`. 응답 본문은 스트림으로 내보내며 10초마다 공백 한 칸(연결 유지), 끝에 JSON 한 덩어리. `ctx.waitUntil` 으로 정산: `finalMessage().usage` 로 실제 밀 계산 → 예약과 차액을 `add(k, diff, Infinity)`(넘어도 기록). 연결이 끊겨도 정산은 돈다.
- [ ] 결과: `content` 에서 `type === "text"` 블록을 찾아 JSON.parse. `stop_reason` 이 `refusal`·`max_tokens` 이거나 파싱 실패면 `{error:"model"}`(비용은 기록). 
- [ ] 스키마(`additionalProperties:false`, 전부 required): `titles[3]`, `theme` enum 6, `picks[{id,caption,score}]`, `ask[{a,b}]`(≤5), `chapters[{day,line}]`, `essay`, `clips[{id,startMs,endMs}]`.
- [ ] `/rewrite`: 같은 경로, 이미지 ≤ 4, `max_tokens` 1000, `{caption}`.
- [ ] `prompt.js`: 캡션 12~20자 사실 한 줄 또는 반말 혼잣말, 금지어(`mockups/r5/BRIEF.md` 공통 규칙), 참고 자료 말투 따르기, 사람 비율, 애매한 두 장만 `ask`, 영상 구간 3~5초(받은 장면 시각 안에서). 예시 문장 없음.
- [ ] 테스트(`node --test`, SDK 는 `env.makeClient` 로 주입): 401, 413, 429, 상한 503 + 호출 0, SDK 가 보내기 전 throw → 예약 환불, 정상 → usage 정산, 실제가 예약보다 크면 넘어도 기록, max_tokens 정지 → 502 + 기록, 로그에 글 없음.
- [ ] CPU 확인: 60장 요청을 `wrangler dev` 로 보내 CPU ms 측정. 무료 한도(10ms)를 넘으면 멈추고 운영자에게(유료 $5/월 또는 이미지 수 축소).
- [ ] 배포: `scripts/wizard-travel-book.bat`(운영자: Console 에서 키 만들기 → `wrangler secret put ANTHROPIC_API_KEY`, 앱 토큰 생성 → `APP_TOKEN` + 로컬 `app/secrets.json`). D1 생성·`wrangler deploy` 는 운영자에게 한 줄 확인받고 Claude 가. Commit.

### Task 8: AI 응답 검증 + 클라이언트

```dart
BookPlan checkPlan(Map<String,dynamic>? ai, List<Candidate> sent, BookOptions o);
BookPlan fallbackPlan(List<Candidate> sent, BookOptions o); // sharpest per chapter, no text, aiText=false, theme by recommendTheme
String recommendTheme(List<Chapter> ch, bool hasDiaryNotes); // diary notes → diary; places/day ≥ 3 → b; else a
BookPlan resolveAsk(BookPlan p, Map<String,String> chosen /* key "$a|$b" with a<b */); // unanswered → higher score
```
- [ ] 테스트: 안 보낸 id 버림, 중복 한 번, 캡션 > 40자 → 빈 캡션, 모르는 theme → `recommendTheme`, 영상 밖 구간 → 0~4초, picks < 5 → fallback, null → fallback, resolveAsk 미응답.
- [ ] `client.dart`: 512px 는 `thumbnailDataWithSize(512, format: jpeg, quality: 80)`. 상한 배분(사진 44 = 점수순, 장면 12 = 영상마다 고르게, 참고 4). 스트림 읽기, 타임아웃 300초, 재시도 없음. 결과 `AiResult.ok(map) | off | failed`. Commit.

### Task 9: 테마 a 배치 틀 + 쪽 배치

- [ ] **배치 틀 만들기 규칙(모든 테마 공통):** 시안 `.pg` 하나 = 배치 하나. id = 테마/순번(`a/op`, `a/sz` …, 같은 class 가 여러 번이면 `a/op` 하나). 슬롯 방향 = 그 자리 시안 사진의 `photo-credits.json` `orientation`(portrait→P, landscape→L), 표지·풀블리드는 A. 사진 키 `{{udo}}` → `data-slot="0"`, `object-position` → `data-crop`, 글 → `data-text="<키>"`. kind = cover(표지 3개) · chapter(장 표지 op) · photo · video(vd) · numbers(en) · essay · back. `layouts.json` = `LayoutDef` 배열. 테마 a 는 시안에서 cover×3, op, sz, rd, vd, dl, st, pn, en.
- [ ] `paginate(Book …)`:
```dart
List<PageSpec> paginate(BookPlan p, List<Chapter> ch, BookOptions o, List<LayoutDef> defs, {required String cover});
int recommendPages(int photos, int days); // 2 + days + ceil(photos / 1.8), length ×0.7 / ×1 / ×1.4
```
  규칙: 표지 → 장마다 chapter + photo 쪽 → numbers·map·essay·video(옵션, 배치가 있을 때만, 없으면 건너뜀) → back(있으면). photo 쪽은 슬롯 방향이 맞는 배치만, 같은 배치 연속 금지, 장 첫 photo 쪽은 슬롯 1개짜리. 쪽 수가 목표보다 많으면 슬롯 많은 배치를 고르고, 그래도 넘치면 점수 낮은 사진을 `spare` 로.
- [ ] 테스트: P 사진이 L 칸에 안 감, 연속 없음, 옵션 끄면 그 쪽 없음, 배치 없는 kind 는 건너뜀, 모든 pick 이 쪽 또는 spare 에(잃어버림 0), 쪽 수 = 목표 ±2. Commit.

### Task 10: 책 런타임 (테마 a)

- [ ] 로드: `InAppWebView` `webViewAssetLoader: WebViewAssetLoader(domain: 'appassets.androidplatform.net', pathHandlers: [AssetsPathHandler(path: '/assets/'), InternalStoragePathHandler(path: '/books/', directory: <docs>/books)])`. `index.html` 은 `https://appassets.androidplatform.net/assets/flutter_assets/assets/book/index.html`, 사진은 `/books/<id>/img/<pid>.jpg`. `@font-face` 는 `fonts/` 상대 경로. file:// 는 쓰지 않는다.
- [ ] `book.js`: `render(book)` — PageSpec 마다 template 복제·채우기, 가로 스크롤 스냅, `.pg` 비율 변수 `--ratio`(phone 4/3, a5 1.414, square 1). 영상 칸 = `<video muted playsinline loop poster>` + `src=/books/<id>/vid/<pid>.mp4`(Task 6 `trim` 결과), 보일 때만 재생. JS→Flutter 다리 이름: `edit`, `longPress {page}`, `save`, `rewrite {page}`, `swap {page, slot}`.
- [ ] `store.dart`: `books/<id>/book.json`(`Book`), 임시 파일에 쓰고 rename. img = 화질 `reduced` 1600px / `original` 4000px, 모두 `thumbnailDataWithSize(... jpeg)`. 목록·삭제.
- [ ] `tools/check_book.py`: 표본 book.json 을 런타임으로 렌더 → 시안 쪽과 나란히 시트 + `textcontrast.py` 대비(`render_slot()` 안). 시트를 Read 로 직접 본다. 대비 LOW 0. Commit.

### Task 11: 파이프라인 + M1 화면

- [ ] `pipeline.dart`: `Stream<Progress> make(DateTimeRange r, BookOptions o, {List<RefPiece> notes, Timeline? tl})` — 조회 → `gate` → 측정·거르기 → 장 → 512px → AI → `checkPlan` → (ask 있으면 화면 3 대기) → `resolveAsk` → 원본 복사·영상 trim → `paginate` → `store`. 각 단계 숫자를 Progress 로(“사진 412 → 38 → 고르는 중”). AI off/failed → `fallbackPlan` + [다시 시도].
- [ ] M1 화면(테마 a 종이·글꼴 문법, 시안 없이 최소): 시작(달력 두 번 탭, 참고 자료, 타임라인, 첫 실행 한 줄), 찾는 중, 둘 중 하나([다 맡기기]), 책(뷰어), 책장. 실패 문구는 spec "실패할 때" 표 그대로(사진 5장 미만, 위치 없는 사진 N장, AI 실패·상한, 못 읽은 파일, 타임라인 실패).
- [ ] 확인: 에뮬레이터에서 표본 여행 끝까지(실제 Worker). 실패 행마다 한 번씩 일부러 일으켜 스크린샷. 첫 화면 시간 = `flutter run --profile` 첫 프레임 로그 ≤ 2초. Commit.

### Task 12: 운영자 폰 (M1 끝)

- [ ] 릴리스 APK(디버그 키 서명, `--dart-define-from-file`) → SendUserFile(30 MiB 넘으면 글꼴 정리). 운영자는 날짜만 넣는다.
- [ ] 실측 기록: 갤러리 장수 → 거른 뒤 → AI 고른 장수, 단계별 시간, Worker usage → 원. 이 값으로 "이번 책 약 N원" 계산식 상수를 정한다. 보고 = 숫자 + 화면 캡처, 통과 기준 질문.
- [ ] M1 화면(시작·찾는 중·둘 중 하나·책장·내보내기)의 디자인 시안 3방향을 Artifact 로 — 운영자 고른 뒤 M2 에서 교체.

### Task 13: 테마 5개 (b · c · diary · mag · album)

- [ ] Task 9 배치 틀 규칙으로 `mockups/r5/{b,c,diary,mag.tpl,album}.html` 변환(테마마다 커밋). b·c 의 팔레트는 `theme.css` `:root` 의 `--spot`/`--acc` 로 남겨 표지 색이 그 변수만 바꾸게. numbers 쪽: 일·곳·장·km(장 사이 GPS 직선거리 합, 타임라인 경로가 있으면 그 길이).
- [ ] 지도: 점 + 경로선 SVG(실루엣 없음). c 표지 "색 바다 번호 지도"는 스크린샷으로 운영자 확인 — 아니라고 하면 c 기본 표지를 ②로 바꾼다.
- [ ] 테마마다 `check_book.py` 시트 + 대비 LOW 0. 사진 톤 맞추기 = `<body class="tone">` 시 테마 보정 필터 한 줄.

### Task 14: 편집 6단계 + 옵션 시트

- [ ] `edit.js` = `mockups/edit-ui/src.html` 4차 그대로: 표지(+색) → 글자 → 제목·한 줄 → 사진 → 정보 줄 → 확인, [이전] [원본] [다음], n/6, [바로 저장], 요약 줄 → 그 단계 → [요약으로]. [원본] = 그 단계 값만 `Book.original` 에서. 글꼴 후보 = edit-ui 4차 3개, 크기 = 작은 글자 클래스(시안의 `.lb .sm .cp` 계열, 테마마다 목록을 `layouts.json` 에) 0.92/1/1.1.
- [ ] 사진 단계·쪽 길게 누르기: 빼기, 바꾸기(같은 장 `spare`), 끌어서 `crop`. [글 다시 쓰기] → `/rewrite`.
- [ ] `contrast.js` `pickInk(bg, ink) -> "#fff"|ink|"scrim"`(대비 4.5, 둘 다 못 넘으면 rgba(0,0,0,.4) 막). `node --test`: 흰 → ink, 진한 남색 → 흰, #808080 → scrim.
- [ ] 옵션 시트(spec "디자인 옵션" 표 전부, 추천값 채움, [바로 만들기]). 화질 옆 예상 용량 = 장수 × (reduced 0.4 MB | original 2.5 MB, Task 12 실측으로 교체).
- [ ] 확인: 헤드리스 Chrome + CDP 터치로 6단계 끝까지, [원본]·[이전].

### Task 15: 내보내기 + 하이라이트

- [ ] 페이지 이미지: `HeadlessInAppWebView(initialSize: 픽셀 / devicePixelRatio)` — 폰 1080×1440, A5 1748×2480, 정사각 2362². 영상 칸은 poster(Task 6 장면)로 바꿔 찍는다. 저장 = 앱 폴더에 쓴 뒤 MediaStore `Pictures/여행 책자/<책 id>/NN.jpg`(같은 책 다시 내보내면 덮어씀, 제목은 파일명에 안 씀).
- [ ] PDF: 같은 이미지를 `pdf` 로 판형 크기에 꽉 채움 → `Documents/여행 책자/<책 id>.pdf`. 판형이 폰이면 PDF 는 A5 로 다시 렌더. 판형마다 무너지는 배치는 `layouts.json` `formats` 에서 뺀다(시트로 확인).
- [ ] 하이라이트(Kotlin, Looper 스레드): Media3 `Composition` — 사진 `durationUs` 1.6초 + `Presentation` 1080×1920 꽉 채움, 영상 = clip, 음악 시퀀스 `isLooping`, 사진 구간 `experimentalSetForceAudioTrack`, 입력 디코드 최대 2048, HDR 은 SDR 톤매핑. 앱 폴더 → MediaStore `Movies/여행 책자/`. 실패 → 책 그대로 + [다시 만들기].
- [ ] 음악: 퍼블릭 도메인·CC0 후보 5곡(분위기·출처·라이선스 링크 표) → 운영자 승인 뒤 내려받아 `assets/music/`.
- [ ] `share_plus` 공유. 확인: `adb pull` 로 결과를 직접 본다(PDF 쪽 수·해상도, `ffprobe` 길이).

### Task 16: 실제 여행 2회차 (M2 끝)

- [ ] 새 APK → 운영자 폰. 다른 여행으로 만들고 테마·편집·내보내기 3종을 써 본다. 숫자(장수·시간·원) + 캡처 보고. 통과 기준: "다른 여행으로도 한 번 더 만들어 보고 싶다".
- [ ] `docs/mechanics.md` #21 상태, `manifest.json`.
