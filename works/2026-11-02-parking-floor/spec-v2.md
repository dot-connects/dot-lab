# 어디댔어 — 설계 문서 (v2, 2026-11-19)

T-0189. 주차 자리를 말로 남기면 가족 폰 위젯에 바로 뜨는 Android 앱. Play 출시(개인 계정, T-0188·잡기와 같은 테스터 묶음).

- **정본 시안:** https://claude.ai/artifact/378AkDknbSmaqnAvhdLr9T (파일 `mockups/v2/final/index.html`, 원본 `mockups/v2/round2/src.html`). 화면·위젯·문구는 시안이 정본이고, 이 문서는 동작·데이터·구조를 정한다. 둘이 어긋나면 시안을 따르고 이 문서를 고친다.
- **결정 이력:** `C:\DC\DD\tasks\T-0189-parking-voice-play.md` decisions.
- **조사:** `C:\DC\DD\docs\archive\2026-11-19-parking-voice-research\` 01 가족·차·인증, 02 경쟁·이름·아이콘, 03 음성·위젯·설정, 04 디자인 방향, 05 추가 기능.

## 0. 요약

```
위젯 줄 마이크 / 차 BT 끊김 알림 / 빠른 설정 타일 / 앱 큰 버튼
        │  (어느 차인지 = CarResolver)
        ▼
ListenActivity (투명, 바텀시트)  ── 폰 안 음성 인식(on-device 전용)
        │  글자
        ▼
SpotParser (규칙, LLM 없음) → 층·구역·번호·메모 / 애매하면 칩 질문 / 2번 실패면 직접 입력
        │
        ▼
ParkStore 로컬 저장 (즉시) ─→ 위젯 다시 그림 (WidgetRenderer 비트맵)
        │
        ▼
SyncQueue → Worker PUT /v2/cars/{id}/park  (신호 없으면 대기, 생기면 자동 전송)
        │
        ▼
FCM data push → 가족 폰 ParkStore 갱신 → 위젯 다시 그림 (+ 사람마다 켠 경우 알림)
```

## 1. 범위

| v1 (이번 출시) | v1.1 이후 |
|---|---|
| 말로 기록(위젯 줄 마이크·BT 끊김 알림·빠른 설정 타일·앱 버튼·아이콘 길게 누르기 바로가기) | 요금 타이머 + Live Update 칩 |
| 규칙 파서, 애매할 때 칩 질문, 실패 시 드롭다운 입력, 8초 되돌리기, [고치기]·[사진] | "어디 댔어요?" 묻기 + 알림에서 말로 답하기 |
| 차 최대 4대, 차별 색·차량번호, 차량번호 크게 + [번호 복사] | 잠금화면 위젯(Pixel), iPhone 가족용 웹 링크 |
| 가족: 카톡 초대 링크(App Links + Install Referrer)·QR·코드 복사, 30일 무활동 정리 | 자주 가는 곳 추론, Android Auto 연결 끊김 신호 |
| 위젯: 모양 5(줄 목록·포스터·기둥 띠·점 행렬·주차권), 모든 차 / 한 대, 배경 진하기, 색 바꿔 보기 + 되돌리기 | 기둥 사진 OCR, Wear |
| 운행 중 자동 표시(그 차 BT 재연결), 오프라인 전송 대기 | 공유 시트로 받기(카톡 문장·기둥 사진) |
| 상태 화면 6종, 첫 실행 4단계(연습 발화 포함), 설정 4묶음 + 정보 | |

v1 / v1.1 경계는 이 표대로 확정(운영자 2026-11-19). 이 문서는 v1.1 기능이 들어올 자리(데이터 필드·라우트 이름)만 비워 둔다.

## 2. 프로젝트 구조 (만드는 방식 A)

같은 폴더에 새 Gradle 프로젝트 `app-v2/`를 만들고 0.4.1(`app/`)에서 필요한 것만 옮긴다. 0.4.1은 출시 뒤 지운다(그 전까지 운영자 폰에 깔린 0.4.1이 Worker v1 라우트를 쓴다 → §6 호환).

| 항목 | 값 |
|---|---|
| applicationId | `kr.dot.wherepark` (운영자 2026-11-19 확정, 출시 후 못 바꿈) |
| 표시 이름 | 어디댔어 (Play 제목: `어디댔어 - 말하면 가족 폰에 주차 자리가 떠요`) |
| minSdk / target / compile | 34 / 35 / 35 (Play 2026 요구 target 확인 후 올림) |
| UI | 앱 화면 Jetpack Compose(StandBy 앱과 같은 방식, Google 다운로드 글꼴 금지 → 위젯과 같은 번들 글꼴), 위젯 RemoteViews + Canvas 비트맵 |
| 의존성 | core-ktx, activity-compose, compose BOM, firebase-messaging, work-runtime-ktx(전송 대기), installreferrer, zxing core(QR 생성만). ML Kit 글자 인식은 뺀다(OCR v1.1) |
| 빌드 | 무거운 작업 규칙(`docs/procedures/heavy-jobs.md`) — Gradle 빌드 1칸, 트레이딩 보호 창 피함 |

패키지 구성 (파일 하나 = 책임 하나):

```
kr.dot.wherepark
├─ core/      SpotParser · KoNum · CarResolver · Spot(model) · Clock   ← 순수 Kotlin, 단위 테스트
├─ data/      ParkStore(로컬) · CarStore · FamilyStore · SyncQueue(WorkManager) · Api(Worker)
├─ voice/     ListenActivity · Recognizer(on-device 래퍼) · LangPack(확인·다운로드)
├─ widget/    WidgetRenderer(비트맵) · Skins · Palette(셔플) · FamilyWidgetProvider · WidgetConfigActivity
├─ car/       CarLink(CDM 연결·presence) · ExitNotifier · DrivingDetector
├─ family/    Invite(링크·QR·코드) · JoinActivity · Push(FCM)
└─ ui/        Compose 화면: Home · CarDetail · Plate · Settings · CarEdit · Family · Onboarding
```

0.4.1에서 옮길 것: `Family.kt`(인증 헤더·secret 저장 방식), `PushSetup.kt`(Firebase 수동 초기화), `CarLink.kt`(CDM 연결 흐름), `ParkWidget.kt`의 크기 판정·`WidgetSizeTest`. 버릴 것: 기압·측정·기둥 OCR·FloorModel·DriveService·TestActivity.

## 3. 데이터 모델

```kotlin
data class Car(val id: String, val name: String, val color: Int, val plate: String?, val order: Int)   // 가족당 ≤4
data class Spot(val floor: Floor, val zone: String?, val number: Int?, val memo: String?)               // Floor = B(n) | G(n)
data class Park(val id: Long, val carId: String, val spot: Spot, val atMs: Long, val byId: String, val byName: String,
                val raw: String?, val photo: String?, val state: ParkState)                               // raw = 인식 원문
enum class ParkState { PARKED, DRIVING }      // DRIVING = 그 차 BT 재연결로 자동, 또는 [가져갔어요]
data class Pending(val parkId: Long, val carId: String, val tries: Int)   // SyncQueue 항목
```

- **차마다 최근 기록 1건**만 가족에게 공유한다. 내 이력은 폰 안에만(최근 50건, "내 기록" 화면). 가족에게 출입 이력이 보이지 않게(05 결론 5).
- 위치 좌표는 저장·전송하지 않는다. 층·구역·번호·메모·차 이름만(family-legal: 측위 정보가 아니면 위치정보법 밖일 가능성. 장소 이름도 v1은 넣지 않는다).
- `Floor` 표기: 지하 = `B1..B9`, 지상 = `1층..` 내부 값 `-1..-9 / 1..`. 앱 설정 "P층 = 지하"(일부 주차장 표기)는 v1.1.

## 4. 말로 기록하기

### 4.1 어느 차인가 (CarResolver, 순수 함수)

| 순서 | 입력 | 결과 |
|---|---|---|
| 1 | 위젯 줄 마이크·홈 카드 [말하기]·차 상세에서 연 경우 `carId` extra | 그 차 |
| 2 | BT 끊김 알림에서 연 경우 `btAddress` → CarLink 연결표 | 그 차 |
| 3 | 인식 글자에 차 이름("회색 차", "아빠 차")이 있으면 | 그 차, 이름 부분은 파싱에서 뺌 |
| 4 | 그 외 | 내가 마지막에 기록·운행한 차, 없으면 첫 차 |

저장 시트 오른쪽 위 차 칩으로 언제든 바꾼다. 차가 1대면 칩을 숨긴다.

### 4.2 ListenActivity

- 투명 Activity(`Theme.Translucent.NoTitleBar`, `excludeFromRecents`, `taskAffinity=""`), 화면 아래 바텀시트만 그린다. 위젯·알림·타일·바로가기 PendingIntent가 모두 이것을 연다(백그라운드 Activity 실행 예외: 위젯·알림 탭, 03 §2.1).
- 마이크 권한이 없으면 시트 안에서 이유 한 줄 + 시스템 권한 창. 두 번 거절이면 "설정에서 켜기"와 직접 입력.
- **인식기:** `SpeechRecognizer.createOnDeviceSpeechRecognizer` 만 쓴다. `checkRecognitionSupport(ko-KR)`이 installed가 아니면 → 직접 입력 시트 + "Wi-Fi에서 음성팩 받기" 버튼(`triggerModelDownload`). 서버 인식기로 넘기지 않는다(데이터 안전 양식 "음성은 폰 밖으로 나가지 않음"을 지키려고. 리뷰 High).
- 상태: 듣는 중(소리 막대 = `onRmsChanged`, 부분 결과 30sp, [취소]) → 결과 → 파싱.
- 실패 처리: `ERROR_NO_MATCH`/`SPEECH_TIMEOUT` 1회 = "다시 말해 주세요" 자동 재시작, 2회 = 직접 입력 시트(층·구역 드롭다운, 번호 숫자 입력, 메모, 이 주차장에서 쓴 층 칩은 이력 있을 때만).
- 시트 디자인은 브라운 1벌. 숫자 글씨체·강조색만 그 위젯(또는 기본 위젯) 설정에서 빌린다.

### 4.3 SpotParser (규칙, TDD)

입력 = 인식 원문, 출력 = `ParseResult(spot, ambiguity, leftover)`.

1. 정규화: 공백·문장부호 정리, 한자어·고유어 수사 → 숫자(`KoNum`: 일~구십구, 하나~아흔아홉, "서른일곱" → 37), 영문 글자 읽기(비/씨/디/이/에프 → B/C/D/E/F, "비투" → B2).
2. 층: `지하 N층 | B N | 비 N | 지하 N` → B N. "지상"·"N층"만 있으면 지상이지만 **지하 없이 "2층"은 ambiguity=FLOOR_SIGN**(칩: 지하 2층 / 지상 2층). 같은 주차장에서 한 번 고르면 다음엔 안 묻는다(장소 키 = 그 차 BT + 시간대, v1은 "마지막 선택" 1개만 기억).
3. 구역: 글자 1개(A~Z, 가~하) 또는 "N구역". "이 구역"처럼 이(2)/E가 겹치면 ambiguity=ZONE(칩 2개).
4. 번호: 1~999, "N번". 기둥 표기 "가-3", "C-12"는 구역+번호로.
5. 자기 정정: "아니", "아니다", "말고" 뒤의 값이 앞 값을 덮는다("37번 아니 38번" → 38).
6. 남은 말 → `memo`("엘리베이터 옆", "3번 출구 쪽"). 숫자 하나만 남은 "3번 출구"는 번호로 먹지 않는다(출구·게이트·입구 앞뒤 단어 목록).
7. 아무 층도 못 찾으면 실패(직접 입력으로).

**고정 테스트:** 03 §1.5의 34줄을 `SpotParserTest` 표로 옮기고, 실기기에서 모은 원문을 계속 보탠다. 이 파일이 파서의 정본이다.

### 4.4 저장 직후

- 로컬 저장 즉시 위젯 갱신, 시트에 "저장했어요" + 전송 상태 줄(`가족에게 보냈어요` / `신호가 잡히면 가족에게 보내요`), 8초 되돌리기 막대, [고치기]·[사진].
- 되돌리기 = 로컬 이전 기록 복원 + 아직 안 보냈으면 큐에서 빼고, 이미 보냈으면 이전 기록을 다시 PUT(서버는 `id` 같은 기록을 덮어씀, §6).
- [사진]: CameraX 없이 `ACTION_IMAGE_CAPTURE` → 1600px 긴 변·JPEG 80으로 줄여 로컬 저장 → 큐로 업로드. 다음 주차 기록이 생기면 그 차의 이전 사진은 폰·서버 모두 지운다.
- 차 상세에서 층·칸을 눌러 언제든 고친다(같은 park id로 PUT).

## 5. 차·블루투스·운행 중

- **연결:** 차 편집 → [차 블루투스 연결] → CompanionDeviceManager `associate`(BT 권한 대화상자, 위치 권한 없음). 0.4.1 `CarLink` 흐름을 옮긴다.
- **감지:** CDM `startObservingDevicePresence` — 상주 서비스 없이 시스템이 `CompanionDeviceService`를 깨운다(tech-light). 끊김 = 주차 후보, 다시 붙음 = 운행 시작.
- **끊김 알림(ExitNotifier):** "흰 차에서 내렸어요. 어디에 댔어요?" [말하기][층 고르기][안 댔어요]. 같은 차 30분 안 1회, `setTimeoutAfter(10분)`, [안 댔어요] 3번 연속이면 "이 차 알림 끌까요?" 제안. 끊긴 뒤 2분 안에 다시 붙으면(시동 재걸기) 알림을 거둔다.
- **운행 중:** 그 차 BT가 다시 붙으면 `ParkState.DRIVING`으로 바꾸고 PUT(가족 위젯에 "엄마 운행 중"). 수동 [가져갔어요]는 차 상세의 보조 버튼. 다음 주차 기록이 들어오면 PARKED로.
- BT 연결표(주소 ↔ carId)는 폰마다 로컬. 서버에 올리지 않는다.

## 6. 서버 (Worker `dot-lab-parking`)

v1 라우트(0.4.1)는 그대로 두고 `/v2/*`를 더한다. 같은 D1, 같은 인증(Bearer `memberId.secret`).

| 라우트 | 내용 |
|---|---|
| `POST /v2/groups` `POST /v2/join` | v1과 같음 + 응답에 `cars` |
| `POST /v2/invites` | v1과 같음(12자, 24h, 1회용, 가족당 최신 1개). 응답에 `link` = `https://<도메인>/j#<code>` |
| `GET /v2/state` | 가족 전체: members(이름, 마지막 활동, me, owner), cars, 차별 최근 park(+by, mine), 사진 유무 |
| `PUT /v2/cars/{id}` `DELETE /v2/cars/{id}` | 차 만들기·고치기·지우기(가족당 ≤4) |
| `PUT /v2/cars/{id}/park` | 차별 최근 기록. 같은 `id`는 덮어씀(고치기·되돌리기·운행 중), 다른 id는 `atMs`가 더 새것만(지금 v1 규칙을 차 단위로) |
| `PUT /v2/cars/{id}/photo` `GET` `DELETE` | R2 `PHOTOS` 버킷, ≤400KB JPEG, 키 `g/{group}/{car}/{parkId}.jpg`, 다음 기록·30일 지나면 삭제(크론 1일 1회) |
| `DELETE /v2/members/{id}` | 본인 / 방장 / **대상의 last_seen이 30일 넘었으면 누구나**(분실 폰 정리, 01 결론 4) |
| `DELETE /v2/me` | 내 데이터 지우기(설정 > 정보). 마지막 1명이면 가족 전체 삭제 |

스키마 추가 (`schema.sql`):

```sql
CREATE TABLE IF NOT EXISTS cars (id TEXT PRIMARY KEY, group_id TEXT NOT NULL, json TEXT NOT NULL, ord INTEGER NOT NULL, updated INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS parks2 (group_id TEXT NOT NULL, car_id TEXT NOT NULL, json TEXT NOT NULL, by_name TEXT, by_id TEXT,
  updated INTEGER NOT NULL, PRIMARY KEY (group_id, car_id));
ALTER TABLE members ADD COLUMN last_seen INTEGER;   -- 인증된 요청마다 갱신(분 단위로 묶어 쓰기 줄임)
```

- 푸시: v1처럼 data-only FCM(`type: park|cars|members`, `carId`). 받는 폰이 `GET /v2/state`로 당겨 온다(푸시에 자리 문자열을 싣지 않음).
- 합류 알림: 새 멤버가 들어오면 가족 폰에 보이는 알림("엄마가 가족에 들어왔어요").
- 배포 순서: Worker 먼저(하위 호환), 앱은 그 뒤. Pages(dot-lab 사이트) 푸시 ≠ Worker 배포(memory `feedback_pages_before_worker`).

## 7. 가족 초대·합류

- **링크:** `https://<도메인>/j#<code>` — 코드는 `#` 뒤(서버·로그에 안 남음). 도메인 = dot-lab Pages 사이트 아래 `/j`(운영자 확정, 별도 도메인 안 삼), `/.well-known/assetlinks.json`에 앱 서명 지문.
  - 앱 있음 → App Links로 JoinActivity가 바로 열림.
  - 앱 없음 → `/j` 페이지가 Play 링크로 보냄(`&referrer=code%3D...`) → 설치 후 첫 실행에서 Install Referrer로 코드 읽음(90일) → JoinActivity.
  - 카톡 인앱 브라우저에서 App Links가 안 열리면 `/j` 페이지의 [앱으로 열기] 버튼(intent:// 스킴). **실기기 확인 필요**(01 UNVERIFIED).
- **보내기:** [카카오톡으로 보내기] = `com.kakao.talk` 패키지 지정 ACTION_SEND, 없으면 공유 시트. [다른 앱으로], QR(zxing로 링크 그림), 코드 [복사].
- **받는 화면:** "민지네 가족 · 3명"만 보이고(합류 전 이름 비공개), 내 이름 입력 → [들어가기]. 코드 붙여 넣기 입력칸(클립보드에 코드 모양이 있으면 자동 채움 제안).
- 가족 이름 = 방장이 정함(기본 "○○네 가족").

## 8. 위젯

### 8.1 그리는 방식

- **AppWidgetProvider 1개**, 위젯마다 설정(`WidgetConfigActivity`, Android 12+ 재설정 가능): 무엇을(모든 차 / 차 하나), 모양(줄 목록·포스터·기둥 띠·점 행렬·주차권 중 하나, 스타일 단계 없음), 배경 진하기 0~100, 색 셔플 결과(팔레트 시드), 되돌리기 1단계.
- **WidgetRenderer:** 위젯 하나 = Canvas 비트맵 1장(번들 글꼴·그라데이션·그림자 그대로) + RemoteViews 위의 투명 클릭 영역(차 줄마다 마이크 48dp, 줄 나머지 = 앱의 그 차 상세). `contentDescription`에 "흰 차 지하 2층 C구역 37번, 아빠 14시 32분" 전체.
- 크기: `OPTION_APPWIDGET_SIZES`로 받은 dp × density로 그림. 비트맵 메모리 상한(위젯당 `6×w×h`바이트, Android 17) 안에 들도록 큰 위젯은 해상도를 낮춤. 크기 바꿀 때마다 다시 그림.
- 배경 흐림 없음. 진하기 60% 미만은 반투명 막 + 글자 외곽선, 글자색은 **배경화면 대표색**과 섞은 실제 배경으로 대비 계산(시안 v10 로직 이식). 배경화면 대표색은 `WallpaperManager.getWallpaperColors`(권한 불필요). 사진 배경 0%에서도 막이 남는 것은 의도(4.5:1 유지).
- 갱신: 로컬 저장·푸시 수신·설정 변경·1분 단위 경과 시간은 **매분 갱신하지 않는다** — "2시간 4분째"는 위젯에서 빼고 "14:32"만(앱·상세에서만 경과 시간). 2일 넘은 기록은 날짜.

### 8.2 모양·셔플

| 모양 | 모든 차 | 한 대 | 3~4대 |
|---|---|---|---|
| 줄 목록 (기본, 브라운) | 줄 = 층·칸 · 차 이름/누가·언제 · 마이크 | 2단 | 줄 추가 |
| 포스터 | 첫 차(등록 순) 거대 숫자 + 나머지 띠 | 거대 숫자 | 띠 추가 |
| 기둥 띠 | 기둥 2개 나란히 | 기둥 1개 | 2×2(3대면 셋째가 한 줄) |
| 점 행렬 | 전광판 패널: 차마다 한 줄, 줄 사이 점선, 마이크는 패널 밖 테두리 버튼 | 왼쪽 패널 + 오른쪽 테두리(누가·언제·버튼) | 줄 추가 |
| 주차권 | 차마다 표 한 장(바코드 띠 + 큰 층·칸 │ 절취선 │ 이름·누가·언제·마이크), 절취선 끝 노치 | 세로 표(가로 절취선) | 표 추가 |

모든 차 위젯은 2대 기준 약 280×114~126dp, 한 대는 약 150×104~123dp(시안 실측). 점 행렬의 숫자만 점 글꼴, 층의 B와 구역 글자는 굵은 일반 글꼴(8·E 오독 방지).

- 셔플: 색 5계열(파스텔·원색·깊은·무채·보색) × 재질 5(무광·유광·종이·금속·소프트) × 숫자 글꼴 6(Inter Tight, Big Shoulders Display, IBM Plex Mono, Big Shoulders Stencil, Playfair Display, Chakra Petch — 전부 OFL, `assets/fonts/`에 숫자·라틴 서브셋) × 모서리 5. 점 행렬 모양은 글꼴 고정(Doto), 다른 모양에는 점 글꼴이 나오지 않음. 대비 미달 조합은 다시 뽑음(글자 4.5:1). 같은 글꼴·재질·계열이 연달아 나오지 않음.
- 비트맵으로 그릴 때 주의: 주차권 노치 = 원으로 뚫기, 점 행렬 점 격자·점선 = 점 반복 그리기.
- 위젯 안 마이크는 한 모양·한 색. 한국어 라벨은 시스템 글꼴(번들 안 함).

### 8.3 상태

| 상태 | 위젯 줄 | 앱 홈 카드 |
|---|---|---|
| 기록 없음 | `–` + "기록 없음" | "아직 기록이 없어요. 마이크를 눌러 말해 보세요" |
| 전송 대기 | 시각 자리에 ↑ "전송 대기" | 태그 "전송 대기" + "신호가 잡히면 가족에게 보내요" |
| 정상 | 층·칸 · 누가·시각 | + 경과 시간 태그, 메모, 칩 |
| 운행 중 | "운행 중" · "엄마 15:10 출발" | 흐린 숫자 + "엄마 운행 중" + "블루투스가 다시 연결됐어요" |
| 오래된 기록(2일+) | 시각 대신 날짜 | 태그 "3일 전" |
| 마이크·음성팩 필요 | 마이크 자리에 금지 아이콘 + 한 줄 안내 | 안내 줄 + [켜기] |

## 9. 앱 화면

시안 그대로: 홈(카드 목록, 카드마다 말하기 아이콘 칩, 큰 주황 버튼 아래 "흰 차에 저장"), 차 상세(층·칸 [고치기], 무료 시간은 v1.1, 사진, 메모, [차량번호], [가져갔어요] 보조, "내 기록" 줄), 차량번호 크게(화면 켜 둠 `FLAG_KEEP_SCREEN_ON`, 뒤 네 자리 크게, [번호 복사]), 가족(마지막 활동, 30일 무활동 [내보내기], 방장은 줄 메뉴), 설정(내 차 · 알림(나만, 차별: 내리면 물어보기 / 가족이 댔을 때 / 운행 시작, [알림 시험]) · 가족 · 보기(글자 크게) · 정보(개인정보 처리방침, 내 데이터 지우기, 문의, 버전)), 차 편집(별명·색 6·번호·BT).

- 색 규칙: 주황 = 말하기 + 화면당 주 버튼 1개, 주 버튼 글자는 검정(대비 6.6:1). 나머지는 회색 면.
- 문구: 해요체, 기술 용어 금지("동기화" → "가족 폰에도 보여요").
- 접근성: 터치 48dp, 글자 sp, 200% 글자에서 홈 칩 줄바꿈 확인, 저장·듣기 시작에 진동.

## 10. 첫 실행 (4단계)

1. 소개 + 알림 권한.
2. 어느 차예요? 별명·번호(선택)·[차 블루투스 연결](건너뛸 수 있음). 이 사이 Wi-Fi면 음성팩 확인·다운로드(`triggerModelDownload`).
3. **한번 말해 보세요:** "지하 2층 C37"을 말해 보게 해서 마이크 권한·온디바이스 인식기·음성팩을 한 번에 확인. 성공 = 파싱 결과 표시, 실패 = 원인별 안내(권한 / 음성팩 없음 / 기기 미지원 → 직접 입력으로 쓰면 된다).
4. 가족과 같이 보기(카톡 초대 / 나중에) → [위젯 놓고 끝내기](`requestPinAppWidget`, Samsung 런처 동작 실기기 확인).

## 11. 테스트·검증

| 무엇 | 방법 | 기준 |
|---|---|---|
| SpotParser·KoNum | JUnit 표 테스트(TDD, 03 §1.5 34줄 + 실기기 원문) | 전부 통과 |
| CarResolver·되돌리기·SyncQueue 순서 | JUnit | 표 시나리오 통과 |
| Worker v2 | `worker/test` node 테스트(MemoryStore): 인증, 차 ≤4, 차별 stale 규칙, 같은 id 덮어쓰기, 30일 무활동 삭제 권한, 사진 크기 제한, v1 회귀 | 통과 |
| 위젯 렌더 | Robolectric + Roborazzi로 모양 5 × 크기(2×1·4×2·4×3) × 상태 6 × 차 1~4 PNG, 시안과 나란히 보는 대조표(StandBy `sheets.py` 방식) | 운영자 눈 확인 |
| 대비 | 렌더 PNG에서 글자 영역 픽셀 대비 측정(시안은 대표색 계산이었음) | 4.5:1 |
| 실기기 | Fold8(커버·메인) + 가능한 Galaxy/Pixel 1대: 비행기 모드 온디바이스 인식, 위젯 마이크 → 저장 → 두 번째 폰 위젯 갱신(0.4.1 미확인 항목), BT 끊김 알림, 카톡 링크 합류 | T-0189 done_when 1·3 |
| Play | 개인정보 처리방침, 데이터 안전 양식(음성 = 폰 안, 서버 = 층·칸·메모·차 이름·사진), 16KB 정렬, 권한 목록(마이크·알림·BT CONNECT·카메라 없음(인텐트) · 위치 없음) | 비공개 테스트 12명 × 14일 |

## 12. 확정된 질문 (운영자 2026-11-19)

1. v1 범위 = §1 표 그대로(요금 타이머·묻기·잠금화면·iPhone 링크·공유로 받기는 v1.1).
2. applicationId = `kr.dot.wherepark`.
3. 초대 링크 = dot-lab Pages 사이트 `/j`.
4. 주 버튼 = 주황 바탕 + 검정 글자.
5. 사진 = 다음 기록 때 삭제, 최대 30일.

## 13. 위험

| 위험 | 대응 |
|---|---|
| Galaxy 온디바이스 한국어 미지원 | 첫 실행 3단계에서 바로 드러남 → 직접 입력으로 계속 쓸 수 있게. 출시 전 실기기 표(03 §1.4) 채움 |
| 카톡 인앱 브라우저에서 App Links 안 열림 | `/j` 페이지 [앱으로 열기] + 코드 복사 경로 |
| Samsung 런처 위젯 비트맵 크기·`requestPinAppWidget` | Fold8 실측, 안 되면 "위젯 놓는 법" 안내 화면 |
| CDM presence가 일부 차에서 늦거나 안 옴 | 알림은 보조, 위젯 마이크가 기본 경로 |
| Play 스토커웨어 정책 | "사람"이 아니라 "차"를 공유, 위치 좌표 없음, 문구 점검(family-legal) |
| 위치정보법 | 측위 정보 없음 유지. 장소 이름·좌표를 넣는 기능(v1.1 자주 가는 곳)은 그때 다시 판단 |
