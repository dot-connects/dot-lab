# 여행 한 권 — 구현 계획 2차 (앱 화면 · AI 두 번)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `plan.md`(1차) 위에, 11-25 저녁 확정한 앱 화면 46장(`mockups/shelf/r4-all.src.html`)과 결정(spec "앱 화면 결정")을 구현한다 — 첫 화면, 책장 두 보기, 새 책(날짜 → 앨범 → 더 넣을 것), AI 두 번(고르기 → 고른 사진 보기 → 글쓰기), 테마 바꾸기 글 저장, 다시 쓰기 상한.

**Architecture:** 1차와 같다(Flutter 셸 + WebView 책 런타임 + 순수 Dart 핵심 + Cloudflare Worker). 바뀌는 것: Worker `/book` 을 `/pick` 과 `/write` 로 나누고, 앱 화면은 Flutter 위젯으로 시안 그대로 그린다(책 쪽만 WebView). 앱 화면 색·글꼴 토큰은 시안 `:root` 값을 `lib/ui/tokens.dart` 한 곳에.

**Spec:** `spec.md` (11-25 판, "앱 화면 결정" 절). **1차 계획:** `plan.md` — Global Constraints·공용 타입·Task 1~10 은 그대로 유효(이 문서가 바꾸는 곳만 아래에).

## 바뀐 Global Constraints

- 앱 이름 **여행 한 권**(라벨·스토어), Latin `tripbook`. applicationId `kr.dot.tripbook` 그대로.
- AI 두 번: `/pick`(512px 미리보기 ≤ 60장 → 고른 id·점수·애매한 쌍·추천 테마·장 줄) → 앱에서 고른 사진 보기 → `/write`(확정 사진만 ≤ 48장, 512px → 캡션·장 한 줄·에세이·제목 3, 테마의 글 모양 `short|diary|mag`). 한 권 비용 실측 목표 ≤ 15센트.
- 테마별 글 모양: a·b·c·album = `short`(캡션 12~20자), diary = `diary`(쪽마다 두세 줄), mag = `mag`(제목 + 2단 글). 다른 모양으로 처음 갈 때만 알림 한 번 → `/write` 다시. 쓴 글은 `Book.texts[shape]` 에 저장, 돌아오면 재사용. 횟수 제한 없음.
- [글 다시 쓰기] 책 한 권 20번(`Book.rewrites`), 넘으면 버튼 흐리게 + "이 책은 다시 쓰기를 다 썼어요".
- 가격 표시만: 처음 2권 무료, 그다음 1,500원. **결제는 넣지 않는다**(개인 빌드). 셋째 책부터 가격 시트는 [결제는 아직 준비 중이에요 · 이번엔 그냥 만들기] 로. 무료 횟수 = 글이 들어간 책 수(`Book.aiText`).
- 시안 숫자·문구·색은 `mockups/shelf/r4-all.src.html` 이 정본. 표지 색 줄은 사진만 꽉 찬 표지 배치에서 숨김.

## 파일 구조 (추가·변경만)

```
worker/src/index.js        /pick · /write · /rewrite (공통: 토큰·상한·정산·스트림)
worker/src/prompt.js       SYSTEM_PICK, SYSTEM_WRITE(shape별 규칙), SYSTEM_REWRITE, PICK_SCHEMA, WRITE_SCHEMA
app/lib/core/plan_check.dart   checkPick(ai, sent) · checkWrite(ai, picks, shape)  (checkPlan 을 둘로)
app/lib/core/library.dart      책장 목록·연표 묶기·무료 횟수 (순수)          test/library_test.dart
app/lib/core/text_shape.dart   테마 → 글 모양, 다시 써야 하나 (순수)         test/text_shape_test.dart
app/lib/ui/tokens.dart         색·글꼴·간격 (시안 :root)
app/lib/ui/onboarding.dart     처음 3장 + 권한
app/lib/ui/shelf.dart          진열 · 연표 · 빈 책장 · 길게 누름 메뉴
app/lib/ui/new_book/{dates,albums,extras,must_pick,book_shape,notes,timeline_help}.dart
app/lib/ui/making/{picking,pairs,picks_review,swap_sheet,writing}.dart
app/lib/ui/book_view.dart      책 보기 상단 바 + WebView + 길게 누름 시트
app/lib/ui/export.dart, price_sheet.dart, failures.dart, settings.dart
app/assets/fonts/              IBM Plex Sans KR(앱 화면용, OFL) — 책 글꼴과 별개
```

---

### Task A: Worker 나누기 (/pick · /write)

**Interfaces — Produces (HTTP, 공통 규칙은 1차 Task 7 그대로: x-app-token, 429, 413, 월 상한 밀 정산, 스트림 + 본문 error):**
- `POST /pick` `{images ≤60, shots, notes, opts}` → `{ picks:[{id,score}], ask:[{a,b}], theme, chapters:[{day,line}] }` — 글 없음. `DAY_BOOKS` 는 /pick 에서만 센다.
- `POST /write` `{images ≤48, shots, notes, opts, shape:"short"|"diary"|"mag", picks:[ids], cover:id}` → `{ titles, captions:[{id,text}], chapters:[{day,line}], essay }`. diary 는 caption 이 두세 줄, mag 는 장마다 `chapters.line` 이 기사 제목, essay 가 본문 2단.
- `/rewrite` 그대로.
- [ ] 테스트: 기존 11개를 /pick 에 맞추고 + /write 3개(shape 별 스키마 통과, 48장 초과 413, 정산). FAIL → 구현 → PASS. 배포 후 표본 30장으로 /pick + /write 실호출 1회: 두 번 합친 시간·센트 기록(목표 ≤ 15센트).

### Task B: 고르기·글 검증 분리 + 테마 글 모양 (순수)

```dart
({List<Pick> picks, List<(String,String)> ask, String theme, Map<String,String> chapterLines}) checkPick(Map<String,dynamic>? ai, List<Candidate> sent, BookOptions o, {required String recommended});
({List<String> titles, Map<String,String> captions, Map<String,String> chapterLines, String essay}) checkWrite(Map<String,dynamic>? ai, List<String> pickedIds, String shape);
String shapeOf(String theme);                       // a,b,c,album → short; diary → diary; mag → mag
bool needsRewrite(Book b, String newTheme);          // shape differs and b.texts[shape] missing
```
- [ ] `Book` 에 `texts: Map<shape, WriteResult>`, `rewrites: int`, `mustInclude: Set<String>`, `albums: List<String>?` 추가(models.dart, JSON 왕복).
- [ ] 테스트: checkPick 의 기존 규칙(안 보낸 id, 중복, <5 → fallback, ask ≤5, 모르는 theme) 이전; checkWrite: 고르지 않은 id 의 캡션 버림, shape 별 길이 상한(short 40자 / diary 160자 / mag 장 제목 30자) 넘으면 빈 글; mustInclude id 는 checkPick 결과에 없으면 점수 50 으로 넣음; needsRewrite 4경우.

### Task C: 갤러리 앨범 + 꼭 넣을 사진

- [ ] `gallery.dart`: 기간 조회 결과를 `AssetPathEntity` 별로 세기 — `PhotoManager.getAssetPathList(type: common, filterOption: 같은 DATE_TAKEN 조건)` 각 경로의 `assetCountAsync`, 전체(isAll) 경로는 목록에서 뺀다. 이 기간 0장 앨범도 목록에 남김(흐리게). 한 사진이 여러 앨범에 있으면 고른 앨범들의 합집합을 id 로 중복 제거해 센다.
- [ ] 꼭 넣을 사진: 고른 앨범·기간 사진을 날짜별로(썸네일 200px 격자). 고른 id 는 거르기(흐림·연사)와 AI 상한에서 빠지지 않는다 — `capEvenly` 전에 따로 빼 두고 뒤에 합친다(테스트: prefilter_test 에 mustInclude 1건).

### Task D: 앱 화면 (시안 r4 그대로, Flutter)

- [ ] `tokens.dart` + IBM Plex Sans KR(400/500/600/700) + Inter Tight 를 앱 화면 글꼴로(이미 `assets/book/fonts` 에 있는 Inter Tight 재사용, Plex 는 Google Fonts OFL 받기 — 운영자 승인 범위).
- [ ] 화면 순서대로: onboarding(3, 권한은 3장째, '일부만 허용' 안내) → shelf(진열: 선반 2권/칸, 옆면 두께 = 쪽수; 연표: 연도 + 합계; 빈 책장; 길게 누름 = 펼치기/내보내기/이름 바꾸기/지우기 확인 한 줄) → new_book(dates: 사진 있는 날 점·날짜별 장수; albums: 전체 선택 기본 켬; extras: 꼭 넣을 사진·일기·메모·타임라인·예상 쪽수/시간/가격; book_shape: 테마 6 표지·길이·글의 양 + [더 보기] 8개; notes; timeline_help) → making(picking 숫자 줄, pairs, picks_review: 날짜별 격자·빼기·바꾸기·[+]·'꼭'/'표지' 표시·모양 바꾸기 줄, swap_sheet, writing) → book_view → export·price_sheet·failures·settings.
- [ ] 표지 썸네일(책장·책 모양 고르기)은 WebView 런타임으로 표지 쪽만 렌더해 PNG 로 캐시(`books/<id>/cover.png`), 없으면 첫 사진.
- [ ] 확인: 에뮬레이터 스크린샷을 시안 프레임과 나란히(화면마다 한 장), 첫 화면 2초.

### Task E: 파이프라인 두 단계 + 테마 바꾸기

- [ ] `pipeline.dart`: `pick()` → `ask` 있으면 pairs → picks_review(사용자 확정) → `write(shape)` → paginate → store. 상태는 `Book.stage` (`picked|written`) 로 저장해, 앱이 꺼졌다 켜지면 picks_review 부터 이어감.
- [ ] 테마 바꾸기(다듬기 1단계·책 모양): `needsRewrite` 면 알림 "이 테마에 맞게 글을 다시 써요" → `/write(shape)` → `texts[shape]` 저장. 다시 쓰기 20번 상한은 `/rewrite` 만 센다.
- [ ] 다듬기 1단계에 표지 사진 줄(추천 3 = 점수 상위 가로 사진 + [다른 사진] = 책에 든 사진 시트). 사진만 꽉 찬 표지 배치면 색 줄 숨김.

### Task F: M1 확인 (1차 Task 11·12 대체)

- [ ] 에뮬레이터: 첫 실행 → 책장(빈) → 새 책(날짜 → 앨범 2개만 → 꼭 넣을 사진 3장) → 고르기 → 둘 중 하나 → 고른 사진 보기에서 1장 바꾸기 → 글쓰기 → 책 → 테마 diary 로 바꾸기(알림·다시 쓰기) → a 로 돌아오기(다시 안 씀) → 책장 진열/연표. 실패 행 5개 일부러. 릴리스 APK 로 한 번.
- [ ] 운영자 폰 APK + 실측(장수·시간·센트) 보고. 아이콘은 운영자가 고른 안으로 `android/app/src/main/res/mipmap-*` + adaptive icon.

## 순서

```
A Worker 나누기 ─┐
B 검증·글 모양 ──┼─ E 파이프라인 ─ F 확인
C 앨범·꼭 넣기 ──┤
1차 Task 6 기기 ─┤
D 앱 화면 ───────┘   (D 는 A~C 와 나란히: 화면은 가짜 데이터로 먼저)
```
1차 Task 6(갤러리·Kotlin 채널·에뮬레이터)은 C 와 함께 한다. 1차 Task 13~16(테마 5개·편집·내보내기·2회차)은 M2 로 그대로.

## 리뷰 반영 (11-25, 실패·구현자·규칙 렌즈 1회 — High 7, 아래로 모두 닫음)

아래가 위 Task 들보다 우선한다.

1. **영상 구간은 /pick 이 맡는다.** /pick 에 영상 장면(`atMs` 달린 4장)을 보내고 `clips:[{id,startMs,endMs}]` 를 돌려받는다(PICK_SCHEMA 에 포함, checkPick 이 기존 clip 검증을 그대로). /write 에는 고른 영상마다 장면 1장만.
2. **장수 상한을 한 곳에서.** 꼭 넣을 사진 ≤ 10(11번째 체크는 "꼭 넣을 사진은 10장까지"). /pick 이미지 = 60 − 영상 장면 − 참고 자료 이미지 − 꼭 넣을 사진 만큼만 capEvenly. checkPick 은 사진 ≤ 44 + 영상 ≤ 4 로 자르고(점수 낮은 것부터, 꼭 넣을 사진은 안 자름), fallbackPlan 도 같은 상한. 고른 사진 보기의 [+] 는 합계 48 에서 잠김("한 권에 48장까지"). 그래서 /write 는 늘 ≤ 48.
3. **/write 출력 예산은 글 모양별.** `max_output_tokens` short 8000 · diary 16000 · mag 16000, 예약도 그 값으로. 배포 전 실호출은 short 30장과 **diary 48장** 두 번(diary 는 M2 테마지만 Worker 는 M1 에서 검증).
4. **앨범 목록 = 필터 없는 목록 + 이 기간 장수.** `getAssetPathList` 는 필터에 맞는 앨범만 돌려주므로(AndroidQDBUtils 37–80) 필터 없이 한 번 더 불러 전체 앨범을 만들고, 장수는 `tripDay` 로 다시 거른 후보를 앨범(BUCKET) 별로 센다. Android 에선 사진 하나 = 앨범 하나라 합집합 계산은 필요 없다. DATE_TAKEN 없는 사진도 같은 후보 묶음에서 센다.
5. **이어 하기용 저장.** `Book.stage=picked` 일 때 후보 전체(id·local·place·sharp·w·h·isVideo·durationMs·dhash)를 `book.json` 에 함께 저장 — 재시작 후 바꾸기·[+]·/write 라벨이 그대로 동작. /pick·/write 응답은 받자마자 저장(크래시 뒤 재결제 없음).
6. **사진을 바꾼 뒤의 글.** 글을 쓴 뒤 사진을 바꾸면(다듬기 4단계) 그 쪽만 `/rewrite`(현재 글 모양, 20번에 포함)로 쓰고, 다른 모양의 저장된 글은 지운다(그 모양으로 다시 갈 때 /write). `/rewrite` 에 `shape` 를 넘기고 스키마·길이를 모양별로.
7. **/write 실패.** 책은 `stage=picked` 로 남고 [다시 시도] 는 /write 만 다시(DAY_BOOKS 안 씀). 두 번 실패하면 [글 없이 만들기]. 무료 권수는 `stage=written` 인 책만 센다.
8. **Worker 남용 방지.** /write 도 하루 상한 `WRITE_DAY = "60"`(KST 일), /rewrite 는 `REWRITE_DAY = "200"`. 테스트 각 1.
9. **표지 썸네일.** 책장 표지 = 책 저장 때 WebView 로 표지 쪽 한 장을 `document.fonts.ready` + 이미지 decode 뒤 PNG 로(`books/<id>/cover.png`), 다듬기 1단계에서 표지·색·사진이 바뀌면 다시. 책 모양 고르기의 테마 표지 6장은 사용자 사진이 아니라 시안 렌더(`mockups/r5/pp-<t>-0.png`)를 앱 자산으로.
10. **M1 범위 정리.** 테마 a 만 있는 M1 에서는 Task F 의 "diary 로 바꾸기" 대신 a → b 같은 글 모양 바꾸기만 확인(b 는 M2 라 실제로는 '모양 바꾸기 줄이 뜨는지'까지), 글 모양 바꾸기 로직은 단위 테스트(text_shape_test)로. 다듬기 6단계·내보내기 화면은 M1 에서 1단계(표지 사진)와 [PDF 만들기]만, 나머지는 M2(1차 Task 14·15).
11. **Task D 를 6 묶음으로.** D1 첫 화면(시안 §1 3장) · D2 책장(§2 4장, 보기 선택은 설정에 저장) · D3 새 책(§3 10장) · D4 만드는 중(§4 5장) · D5 책·가격·실패(§5·§8·§9) · D6 설정(§10). 각 묶음 끝에 에뮬레이터 스크린샷을 시안 프레임과 나란히 한 시트로 만들고, 통과 기준 = 시안의 요소·문구·숫자가 다 있고 겹침·잘림 없음. 실패 화면 문구는 시안 §9 그대로.
12. **library.dart 테스트 사례.** 연표 묶기(해 경계 12-31/01-01 여행은 시작 연도), 그해 합계(곳·장·일), 무료 권수(stage=written 만, 2권까지 "무료").
13. **글꼴·비용.** IBM Plex Sans KR 은 운영자가 11-25 "1,2번은 다 너가 진행"으로 승인한 글꼴 내려받기 범위로 받는다(OFL, 라이선스 파일 포함). 비용 목표는 "표본 30장 short, /pick+/write 합계 ≤ 15센트"; 넘으면 /pick 이미지 40장·effort low 로 줄여 다시 잰다.
14. `needsRewrite` 는 이미 쓴 코드 시그니처(`{written, newTheme}`)를 쓴다; `written` = `book.texts.keys`.
