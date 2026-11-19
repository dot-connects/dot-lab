# 어디댔어 v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 주차 자리를 말로 남기면 가족 폰 위젯에 바로 뜨는 Android 앱 `kr.dot.wherepark`(어디댔어) v1을 만들고 운영자 폰에 깔 APK를 낸다.

**Architecture:** 새 Gradle 프로젝트 `app-v2/`(Kotlin, Compose 앱 화면 + RemoteViews 위 Canvas 비트맵 위젯). 순수 로직은 `core/`에서 JUnit TDD. 서버는 Worker `dot-lab-parking`에 `/v2/*`·`/j`·`/.well-known/assetlinks.json`을 더한다(v1 그대로).

**Tech Stack:** Kotlin 2.0.21, AGP 8.10.1, Compose, WorkManager, FCM, Install Referrer, ZXing, Robolectric + Roborazzi (버전은 Task 0). Worker = Cloudflare Workers + D1 + R2, `node --test`.

**Spec:** `spec-v2.md` · **Contracts (전송 형식·공용 이름, 이 plan의 일부):** `contracts-v2.md` · **정본 시안:** `mockups/v2/final/index.html`(원본 `mockups/v2/round2/src.html`). 모든 경로는 `C:\DC\DD\dot-lab\works\2026-11-02-parking-floor\` 기준.

## Global Constraints

- applicationId·namespace `kr.dot.wherepark`, 이름 `어디댔어`, versionName `1.0.0`, versionCode 1. min 34 / target 35 / compile 35, JVM 17.
- 빌드 출력 `${WP_BUILD_DIR:-$LOCALAPPDATA/wherepark-build}/<project>`(C:\DC 밖).
- **무거운 작업(매 빌드·테스트 전):** `C:\DC\DD\docs\procedures\heavy-jobs.md` 순서대로 — 보호 창(트레이딩 마감 등)이면 기다림, `ListAgents`로 다른 세션 칸 확인 후 `SendMessage`로 칸 잡기(종료 예상 시각), Gradle `--max-workers=2`, 다른 세션 렌더가 돌면 `-Ptest.forks=1`, 빌드는 동시에 1개(서브에이전트 병렬이어도), 끝나면 "끝" 보내고 트레이딩 tick 결과 `0x41306` 아닌지 확인.
- 음성 인식은 `createOnDeviceSpeechRecognizer`만. 서버 인식기 금지. 인식 원문 `raw`는 폰 밖으로 안 보낸다.
- 위치 권한·좌표 없음. 차 BT 주소는 `CarLinkStore`(로컬)만.
- 문구 해요체, 이모지 금지, 아이콘은 시안 `IP` 선 아이콘(1.7 stroke, round)을 VectorDrawable로.
- 색: 바탕 `#e9e7e3`, 면 `#f6f5f2`, 글자 `#161616`, 보조 `#6c6a66`, 선 `#d5d2cc`, 주황 `#ff6a13`(말하기 + 화면당 주 버튼 1개, 그 버튼 글자 `#1a0f06`), 초록 `#3a8a4f`.
- 터치 48dp, 위젯 마이크 44dp 이상(시안 값). 글자 sp, 보조 12sp 이상. 위젯은 Canvas 가능한 효과만(흐림 없음).
- 커밋: task마다 dot-lab repo, 명시 경로만 `git add`, `git diff --staged` **읽고** 커밋, 바로 `git push`. 메시지 영어 Conventional + Co-Authored-By 줄. `.gitignore`에 `local.properties firebase.properties *.keystore *.jks build/`.
- Worker 배포 순서: Worker 먼저, 그다음 앱. dot-lab push는 Pages에 바로 나간다(앱 파일엔 영향 없음).

## File Structure

`app-v2/`(Gradle 루트, StandBy 구조) → `app/src/main/java/kr/dot/wherepark/{core,data,voice,widget,car,family,ui}/` — 파일 이름은 contracts §2·각 task. 테스트 `app/src/test/java/kr/dot/wherepark/`, 대조표 도구 `app-v2/tools/sheets.py`·`contrast.py`. 서버 `worker/src/v2.js`·`jpage.js`, `worker/migrations/2026-11-19-v2.sql`, `worker/tools/migrate_v2.py`·`smoke_v2.py`, `worker/test/v2.test.js`. 개인정보 `privacy.html`(작업 폴더, Pages).

---

## Task 0: 프로젝트 골격

- [ ] **Step 1:** StandBy `works/2026-11-09-standby/app/`에서 `settings.gradle.kts`·루트 `build.gradle.kts`(플러그인 버전 4개)·`gradle/`·`gradlew*`·`gradle.properties`를 복사. rootProject `wherepark`, include `:app`, 빌드 폴더 변수 `WP_BUILD_DIR`/`wherepark-build`.
- [ ] **Step 2:** `app/build.gradle.kts` = StandBy **`standby/build.gradle.kts`**(Compose 모듈)를 바탕으로: flavor·secret 블록 삭제, applicationId/namespace `kr.dot.wherepark`, min 34 target 35 compile 35, `buildConfigField SYNC_URL "https://dot-lab-parking.dotconnects.workers.dev"`, Firebase 필드는 0.4.1 `app/app/build.gradle.kts`처럼 `app-v2/firebase.properties`에서. 의존성 = StandBy 목록 + work-runtime-ktx 2.9.1, firebase-messaging 25.1.3, installreferrer 2.2, zxing core 3.5.3. release = `isMinifyEnabled=false`, 서명은 `~/.wherepark/upload.properties`가 있으면 그 키, 없으면 debug 키(아래 Task 11에서 키를 만듦). testOptions는 StandBy 그대로.
- [ ] **Step 3:** 글꼴: `github.com/google/fonts` `ofl/`에서 Inter Tight, Big Shoulders Display, IBM Plex Mono(Bold 정적), Big Shoulders Stencil Display, Playfair Display, Chakra Petch(Bold 정적), Doto를 python urllib로 받는다. variable font면 그대로 두고 `Fonts.kt`가 `Typeface.Builder(assets, path).setFontVariationSettings("'wght' 800|900")`로 연다(모양별 굵기는 시안 CSS 값). 합계 3 MB를 넘으면 `pyftsubset --unicodes=U+0020-007E`. `OFL.txt` 포함.
- [ ] **Step 4:** 앱 아이콘: `mockups/v2/icon/min-braun.png`로 adaptive 아이콘(전경 PNG, 배경 `#e3e1dc`), mipmap 각 밀도(PIL).
- [ ] **Step 5:** 칸 잡고 `./gradlew :app:assembleDebug --max-workers=2` → 성공.
- [ ] **Step 6:** commit `chore(wherepark): scaffold app-v2 project` + push.

## Task 1: KoNum + SpotParser (TDD)

**Produces:** contracts §2 `KoNum.normalize`, `SpotParser.parse`, `Floor.label/wire`.

- [ ] **Step 1: 실패하는 표 테스트.** `SpotParserTest`(JUnit4 Parameterized)에 03 조사 문서(`C:\DC\DD\docs\archive\2026-11-19-parking-voice-research\03-voice-widget-settings-ux.md` 103~148행) 34줄 + 차 이름 행 1줄. 표기 변환: 표의 `F2` → label `"2F"`; `F2?` → `Above(2)` + `FLOOR_SIGN`, options = [Under(2), Above(2)]; `E?` → zone "E" + `ZONE_E_OR_2`, options = [zone "E", zone "2"]; `B2?`(P2) → `Under(2)` + `P_FLOOR`; `가 · 3?` → zone "가", number 3, `JOINED`; `파랑` → zone "파랑"; `기둥 가-3` → pillar "가-3"; `옥상` → `Roof`; `실패` → spot null; 빈 문자열 → spot null. 추가 행 `"회색 차 지하 4층 A12"`(carNames ["흰 차","회색 차"]) → carName "회색 차", B4·A·12.

```kotlin
@RunWith(Parameterized::class)
class SpotParserTest(val raw: String, val floor: String?, val zone: String?, val number: Int?, val pillar: String?, val memo: String?, val amb: Ambiguity) {
  companion object { @JvmStatic @Parameterized.Parameters(name = "{index}: {0}") fun rows(): List<Array<Any?>> = listOf(
    arrayOf("지하 2층 C구역 37번", "B2", "C", 37, null, null, Ambiguity.NONE),
    arrayOf("지하 이층 씨구역 삼십칠번", "B2", "C", 37, null, null, Ambiguity.NONE),
    arrayOf("지하 4층 3번 출구 쪽", "B4", null, null, null, "3번 출구 쪽", Ambiguity.NONE),
    arrayOf("지하 이십층", null, null, null, null, null, Ambiguity.NONE),
    // … 03 표 나머지 행을 모두 같은 꼴로
  ) }
  @Test fun parses() { val r = SpotParser.parse(raw, listOf("흰 차", "회색 차")); val s = r.spot
    if (floor == null && zone == null && pillar == null && memo == null) { assertNull(s); return }
    assertEquals(floor, s?.floor?.label()); assertEquals(zone, s?.zone); assertEquals(number, s?.number)
    assertEquals(pillar, s?.pillar); assertEquals(memo, s?.memo); assertEquals(amb, r.ambiguity) }
}
```
`KoNumTest`: "삼십칠" → `Num(37, korean=true)`, "서른일곱" → 37, "이십" → 20, "삼칠" → `Num(3)`,`Num(7)` 각각 `digitsRead=true`, "비투" → `Letter('B')`,`Num(2)`, "씨"/"시" → `Letter('C', fromSyllable)`, "디" → D, "에프" → F, "에이" → A, "이"(단독) → `Num(2, korean=true)`이면서 `Letter('E', fromSyllable="이")` 후보 — 표현: `Num(2, korean=true, alt='E')`.
- [ ] **Step 2:** 칸 잡고 `./gradlew :app:testDebugUnitTest --tests '*SpotParserTest*' --tests '*KoNumTest*' --max-workers=2` → FAIL.
- [ ] **Step 3: 구현(규칙 순서 고정).** ① 조사·군말 삭제를 **먼저**(숫자·글자 뒤 "이요/이고/이야/요/에/은/는", 문장 앞 "음/어") ② KoNum 토큰화(한자어 일~구·십 조합, 고유어 하나~아흔아홉, 영문 글자 이름: 에이 비 씨/시 디 이 에프 지 에이치 아이 제이 케이 엘 엠 엔 오 피 큐 알 에스 티 유 브이 더블유 엑스 와이 제트, 붙은 발음 비원/비투/비쓰리…) ③ carNames 중 원문에 있는 것 → carName, 토큰에서 뺌 ④ 정정: "아니", "아니다", "말고" 뒤 같은 종류 값이 앞 값을 덮음 ⑤ **출구 메모 먼저**: `Num` 뒤 `번?\s*(출구|입구|게이트)` 덩어리를 memo로 떼어 냄 ⑥ 기둥: `기둥` 앞뒤 `X-N`/`X N` → pillar ⑦ 층: 첫 층 키워드(`지하 N층?`, `B N`, `비N`, `P N`→P_FLOOR, `옥상`, `N층`만 → Above + FLOOR_SIGN). N은 1~9만, 두 자리(10 이상)면 실패 ⑧ 층 다음 글자 1개 = 구역(라틴·한글·색 단어 빨강 주황 노랑 초록 파랑 보라, "파란"→"파랑"). `Num(2, alt='E')`가 구역 자리면 ZONE_E_OR_2. 층이 정해진 뒤의 "비" = 구역 B ⑨ 번호 1~999: 남은 첫 `Num`(digitsRead가 연속이면 이어 붙임 3,7 → 37) ⑩ 구역 글자와 숫자가 붙어 한 덩어리로만 나온 경우("가삼") = JOINED ⑪ 나머지 Word들 = memo(없으면 null). **층도 기둥도 없으면 spot null**(구역·번호만으로는 저장 안 함, spec §4.3-7과 같음; 표 21행 "기둥 가 3"은 기둥이 있어 통과).
- [ ] **Step 4:** 테스트 PASS(실패 행은 규칙을 고친다, 표는 정본).
- [ ] **Step 5:** commit `feat(wherepark): rule parser for spoken parking spots` + push.

## Task 2: CarResolver · ParkRules · Contrast · Palette (TDD)

- [ ] **Step 1: 테스트.**
```kotlin
class CarResolverTest { val a = Car("c_a","흰 차",0,null,0); val b = Car("c_b","회색 차",0,null,1)
  @Test fun explicitWins() = assertEquals("c_b", CarResolver.resolve("c_b","c_a","흰 차",listOf(a,b),"c_a")?.id)
  @Test fun btNext() = assertEquals("c_a", CarResolver.resolve(null,"c_a","회색 차",listOf(a,b),"c_b")?.id)
  @Test fun spokenNext() = assertEquals("c_b", CarResolver.resolve(null,null,"회색 차",listOf(a,b),"c_a")?.id)
  @Test fun lastUsed() = assertEquals("c_b", CarResolver.resolve(null,null,null,listOf(a,b),"c_b")?.id)
  @Test fun firstByOrder() = assertEquals("c_a", CarResolver.resolve(null,null,null,listOf(b,a),null)?.id)
  @Test fun unknownIdFallsThrough() = assertEquals("c_a", CarResolver.resolve("c_gone",null,null,listOf(a,b),null)?.id)
  @Test fun none() = assertNull(CarResolver.resolve(null,null,null,emptyList(),null)) }
class ParkRulesTest { val z = ZoneId.of("Asia/Seoul"); fun t(s: String) = java.time.LocalDateTime.parse(s).atZone(z).toInstant().toEpochMilli()
  fun p(id: Long, at: Long) = Park(id,"c_a",Spot(Floor.Under(2),"C",37,null,null),at,"m","아빠",null,false,ParkState.PARKED)
  @Test fun sameIdOverwrites() = assertTrue(ParkRules.accept(p(1,200), p(1,100)))
  @Test fun newerWins() = assertTrue(ParkRules.accept(p(1,100), p(2,200)))
  @Test fun olderLoses() = assertFalse(ParkRules.accept(p(2,200), p(1,100)))
  @Test fun today() = assertEquals("14:32", ParkRules.whenLabel(t("2026-11-19T14:32"), t("2026-11-19T15:00")))
  @Test fun yesterday() = assertEquals("어제 19:05", ParkRules.whenLabel(t("2026-11-18T19:05"), t("2026-11-19T09:00")))
  @Test fun older() = assertEquals("11월 16일", ParkRules.whenLabel(t("2026-11-16T14:32"), t("2026-11-19T15:00"))) }
class PaletteTest { @Test fun shuffleReadableAndVaried() { val r = java.util.Random(7); for (s in Shape.values()) { var prev: Style? = null
  repeat(200) { val st = Palette.shuffle(s, prev, r)
    assertTrue(Contrast.ratio(st.fg, st.bg) >= 4.5); assertTrue(Contrast.ratio(st.mu, st.bg) >= 3.0)
    assertTrue(Contrast.ratio(0xFFFFFFFF.toInt(), st.fl1) >= 4.5); assertTrue(Contrast.ratio(0xFFFFFFFF.toInt(), st.fl2) >= 4.5); assertTrue(Contrast.ratio(st.acc, st.bg) >= 1.6)
    if (s == Shape.DOT) assertEquals("Doto", st.font) else { assertNotEquals("Doto", st.font)
      prev?.let { assertNotEquals(it.font, st.font); assertNotEquals(it.material, st.material); assertNotEquals(it.family, st.family) } }
    prev = st } } } }
```
- [ ] **Step 2:** FAIL 확인. **Step 3:** 구현 — CarResolver = spec §4.1 순서(없는 id는 건너뜀). Palette.shuffle = 시안 `round2/src.html`의 `randomPal`·`FONTS`·재질 목록을 Kotlin으로(색 5계열·재질 5·글꼴 6(+DOT 전용 Doto)·모서리 [10,16,22,28,34]·연속 중복 금지·대비 재뽑기 300회 후 default). `Palette.default(shape)` = 시안 `.wx.kN`(ROW) `.wx.kT` `.wx.kF` `.wx.kA`(DOT) `.wx.kP`(TICKET) 블록의 `--wbg --wfg --wmu --wacc` 값과 그 블록의 숫자 글꼴·모서리. `fl1/fl2` = 차 줄의 층 색(기둥 띠 띠 색, 차 1·2번째; 3·4번째는 `Car.color`). Contrast = WCAG 상대 휘도.
- [ ] **Step 4:** PASS. **Step 5:** commit `feat(wherepark): car resolver, park rules, palette` + push.

## Task 3: Worker v2 (Codex 구현, Claude 리뷰·배포)

**Codex 위임:** `codex-run.ps1` 백그라운드, 프롬프트 = 이 Task 3 + `contracts-v2.md` §1 + 파일 경로(`worker/`). 금지: `wrangler` 실행·배포·D1/R2 명령, `worker/` 밖 수정. 완료 기준 = Step 1~3 + `node --test` 전부 통과. Claude는 diff를 **읽고** 테스트 출력·exit code·범위만 확인(재실행 안 함). `/j` 페이지의 한국어 HTML은 Codex 대신 Claude가 `worker/src/jpage.js`로 쓴다.

- [ ] **Step 1: 테스트** `worker/test/v2.test.js`(node:test, MemoryStore): ① 그룹 만들기→초대→peek(초대 남음)→합류 ② 차 5대째 409 ③ 다른 가족 같은 car id 서로 안 덮음 ④ park 같은 id 덮어씀 ⑤ 오래된 park → `stale` + 서버 값 ⑥ 새 park 후 undo → prev 복원, 두 번째 undo 409 ⑦ 사진 300 KB(`content-length` 헤더 명시) 200, 401 KB 413, 헤더 없음 413 ⑧ 새 park 2번 뒤 첫 사진 삭제 ⑨ 남이 삭제: last_seen 31일 전 200, 29일 403, last_seen null이면 created 기준 ⑩ `DELETE /v2/me` 마지막 사람 → cars·parks2·R2 `g/{group}/` 비움 ⑪ `/j` HTML 200, `/.well-known/assetlinks.json` 배열 ⑫ `scheduled()`가 30일 지난 parks2 `photo=false` ⑬ 기존 `test/worker.test.js` 그대로 통과 ⑭ `groupName` 저장·반환(v1 그룹 null) ⑮ stale = 409 + 서버 park ⑯ `/state` DRIVING이 id와 무관, `driving_by` ⑰ prev 없는 undo = 기록 비움 + push ⑱ 다른 가족 member 삭제 404 ⑲ v1 `dropGroup`이 cars·parks2·R2까지 ⑳ `/v2/groups` rate limit ㉑ park·photo·state가 가족에 없는 차면 404 ㉒ 현재 기록이 아닌 parkId 사진 409 ㉓ 클라이언트가 보낸 photo:true 무시 ㉔ 방장 `DELETE /v2/me` → 넘김. 마이그레이션 스크립트 `worker/tools/migrate_v2.py`(wrangler d1 execute를 부르고 PRAGMA로 ALTER 건너뜀) 두 번 실행해도 성공.
- [ ] **Step 2:** `cd worker && node --test` → v2 FAIL. **Step 3:** 구현(contracts §1 그대로, 라우팅은 `BODY_MAX` 검사 **앞**에서 `/v2/`·`/j`·`/.well-known/` 위임, `newMember`에 `last_seen`).
- [ ] **Step 4 (Claude, 고위험 — auth·삭제 경로):** fresh Claude 서브에이전트(opus) adversarial 리뷰 1회: `git diff`만 입력, 인증 우회·다른 가족 데이터 접근·삭제 범위·v1 회귀. High는 Codex 재위임 또는 Claude가 고침.
- [ ] **Step 5 (Claude, 배포):** `npx wrangler@4 whoami`로 로그인 확인 — 실패하면 `dot-lab/scripts/wizard-cloudflare.bat`을 운영자에게 안내하고 Step 5·6은 대기(앱 작업은 계속). 순서: ① `npx wrangler@4 d1 export dot-lab-parking --remote --output=$SCRATCH/d1-backup-2026-11-19.sql`(되돌리기용) ② `python worker/tools/migrate_v2.py --remote`(추가만, 재실행 안전) ③ `r2 bucket create dot-lab-parking-photos` + lifecycle 30일 ④ `ASSETLINKS_SHA256`는 Task 11의 키 지문(그 전엔 debug 키 지문) ⑤ `npx wrangler@4 deploy --dry-run` 후 `deploy`. 되돌리기 = `npx wrangler@4 rollback`(코드), D1은 추가만이라 그대로 둬도 v1 동작.
- [ ] **Step 6 (live validation):** python urllib 스크립트(`worker/tools/smoke_v2.py`): 테스트 그룹 만들기 → 차·park·undo·photo 100 KB → state 확인 → `DELETE /v2/me`로 정리. `/v1/park` 401, `/v1/groups` 정상(v1 회귀). 결과를 T-0189 verification에.
- [ ] **Step 7:** commit `feat(parking-worker): v2 routes, invite page, photo store` + push.

## Task 4: 로컬 저장 · API · 작업 대기열

- [ ] **Step 1: 테스트**(Robolectric) `SyncQueueTest`(contracts §3 표 행마다 1개 이상): 같은 key 교체 + seq 증가, 보낸 seq ≠ 현재 seq면 drop 안 함(전송 중 [고치기] 보존), 409 stale → 응답 park 로컬 반영, 409 full → 로컬 차 삭제, 401 → 큐·가족 비움, 전송 전 되돌리기 = op 교체/삭제, 전송 후 되돌리기 = Undo op, 두 스레드가 동시에 add해도 항목 유실 없음, 처리 순서 car→undo→park→state→photo(사진·고치기 순서가 바뀌어도), State가 대기 PutPark에 합쳐짐, 합류 중 옛 인증 401이 새 인증을 안 지움, 가족 없으면 pendingGroup으로 createGroup 먼저(동시에 두 번 안 만듦), PutPark가 대기 state를 버림, 전송 시작한 기록 되돌리기 = park op 버리고 Undo(앱 재시작 뒤에도), 409 응답도 seq 일치일 때만 반영, 401 뒤 로컬 차 남김·새 가족 만들면 같은 id로 다시 올림, GET 중 큐가 바뀌면 병합 버림. `MergeTest`: 서버 값이 정본(서버 undo로 옛 기록이 와도 그대로 씀), 큐에 `park:`/`car:` op가 있는 차만 로컬 유지, `DelCar` 대기 차는 안 살림, `CarLinkStore` 그대로. `ParkStore`: save → latest, prev, undo가 prev 복원, history ≤50.
- [ ] **Step 2:** FAIL. **Step 3: 구현.** `Prefs` = SharedPreferences + org.json(`wp_parks`, `wp_cars`, `wp_links`, `wp_family`, `wp_queue`). Api = `HttpURLConnection`(0.4.1 `Family.kt`의 `call/authed` 이식, Bearer `memberId.secret`). SyncWorker = contracts §3 그대로(`APPEND_OR_REPLACE`, `inFlight`, generation, 그룹 생성은 SyncWorker만). `NetworkType.CONNECTED`. 앱 시작·푸시·작업 끝에 `Api.state` → `Merge` → 위젯 갱신.
- [ ] **Step 4:** PASS. **Step 5:** commit `feat(wherepark): local store, api client, op queue` + push.

## Task 5: 푸시

- [ ] **Step 1:** 0.4.1 `PushSetup.kt`를 `family/Push.kt`로 이식(`FirebaseApp.initializeApp(options)` 수동, `PushService : FirebaseMessagingService`). `onNewToken` → `Api.setFcmToken`. 메시지 `park|cars|members` → `Api.state` → Merge → 위젯 → 사람마다 켠 알림("아빠가 흰 차를 댔어요", "엄마가 운행을 시작했어요", "민지가 가족에 들어왔어요").
- [ ] **Step 2: 대체 경로 조건 = 토큰을 못 받았을 때**(값이 있어도 package 불일치로 실패 가능): 토큰이 없으면 WorkManager 15분 주기 `Api.state` + 앱 열 때 당김.
- [ ] **Step 3:** `dot-lab/scripts/wizard-parking-firebase.bat`에 단계 추가: **같은 Firebase 프로젝트**에 Android 앱 `kr.dot.wherepark` 추가 → `google-services.json` 값을 `app-v2/firebase.properties`에(기존 `app/` 것은 그대로). 새 프로젝트를 만들지 말 것(Worker의 `FCM_SA`가 바뀌어 v1이 끊김). 운영자가 돌리기 전엔 대체 경로로 동작.
- [ ] **Step 4:** commit `feat(wherepark): family push with pull fallback` + push.

## Task 6: 말하기 (ListenActivity)

- [ ] **Step 1:** Manifest: `<queries><intent><action android:name="android.speech.RecognitionService"/></intent></queries>`, `RECORD_AUDIO`, ListenActivity(`Theme.WP.Translucent`, `excludeFromRecents`, `taskAffinity=""`, `exported=false`). Recognizer = `createOnDeviceSpeechRecognizer`, `EXTRA_LANGUAGE="ko-KR"`, partial results, rms. LangPack = `checkRecognitionSupport`/`triggerModelDownload`.
- [ ] **Step 2:** ListenSheet 상태: `Listening(partial, rms)` → `Saved` / `Ambiguous(options)` / `Manual(prefill)` / `Practice(result)`(from=practice, 저장 안 함). 시안 3절 그대로: 소리 막대, 인식 글자 30sp, [취소]; 저장됨 = 차 칩(2대+), "저장했어요" + 전송 줄(보냄/신호 잡히면 보내요), 8초 되돌리기 막대, [고치기](Manual) [사진](`ACTION_IMAGE_CAPTURE` + FileProvider `files/photos/`, 긴 변 1600px·JPEG 80, `Op.PutPhoto`); 실패 2회 → Manual(층·구역 `ExposedDropdownMenuBox`, 번호 `KeyboardType.Number` 칸, 메모, 이 차 층 이력 칩은 이력 있을 때만). 숫자 글꼴·강조색 = 그 차의 위젯 Style(없으면 ROW default).
- [ ] **Step 3:** 권한: 시트 안 이유 한 줄 → 요청 → 2번 거절이면 "설정에서 켜기" + Manual. 음성팩 SUPPORTED/NONE → Manual + [Wi-Fi에서 음성팩 받기].
- [ ] **Step 4:** `ShotKit`(StandBy `standby/src/test/java/kr/dot/sb/ShotKit.kt` 복사·패키지 변경)로 시트 5상태 PNG → 합격 기준 Task 7 Step 5와 같음. 칸 잡고 실행.
- [ ] **Step 5:** commit `feat(wherepark): listen sheet with on-device recognition` + push.

## Task 7: 위젯

- [ ] **Step 1:** `Shapes.kt` — 시안 `round2/src.html` 위젯 CSS·JS(`WALL/WONE`, 모양 ROW/POSTER/PILLAR/DOT/TICKET, 1·2·3·4대)를 Canvas로. 시안 px = dp. 숫자 = Style.font Typeface, 한국어 = `Typeface.DEFAULT(_BOLD)`. 그림자 `setShadowLayer`(소프트웨어 비트맵), 재질 5 = 그라데이션·점 반복·결 선, DOT = 점 격자 반복 + Doto(B·구역 글자는 굵은 기본 글꼴), TICKET 노치 = `PorterDuff.Mode.CLEAR` 원.
- [ ] **Step 2:** 배경 진하기: 표면 알파 = strength/100, `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)?.primaryColor`와 `Contrast.blend`로 실제 바탕 → 글자색·최소 막·외곽선 2dp(시안 `applyOp` 로직). 상태 6종(spec §8.3). 배경화면이 바뀌면(`ACTION_WALLPAPER_CHANGED`는 막혀 있으므로) 앱 열 때·매 갱신 때 다시 읽음. 자정 갱신: `AlarmManager.setAndAllowWhileIdle`(inexact) 다음 00:01 — `refreshAll` 할 때마다 다시 걸고 `BOOT_COMPLETED` 수신기에서도 걸음 →.
- [ ] **Step 3: 크기별 그리기.** `WidgetReceiver.onUpdate/onAppWidgetOptionsChanged` → `OPTION_APPWIDGET_SIZES`의 크기마다 `render` → `RemoteViews(Map<SizeF, RemoteViews>)`(API 31+): 크기마다 자기 비트맵 + 자기 `Hit` 위치(`setViewLayoutMargin`/`setViewLayoutWidth/Height`, dp)로 투명 버튼 슬롯 8개(`w_root.xml`), PendingIntent requestCode = `appWidgetId * 16 + 차순번 * 2 + (MIC 0 / ROW 1)`(크기마다 같은 값). MIC → ListenActivity(`car_id`, `from=widget`), ROW → MainActivity 차 상세. `setContentDescription` 차마다("흰 차 지하 2층 C구역 37번, 아빠 14시 32분"). 비트맵 바이트 합계(모든 크기) ≤ `1.5 × 화면 w × h × 4`(RemoteViews 상한 = 화면 크기 기준) 안 되면 큰 크기부터 0.75배.
- [ ] **Step 4:** `WidgetConfigActivity`(Compose, 시안 "위젯 꾸미기"): 무엇을(모든 차/차별), 모양 5 썸네일, 미리 보기(실제 `render` 결과), [색 바꿔 보기]·[되돌리기]·지금 조합 한 줄, 배경 진하기 슬라이더, [홈 화면에 놓기]. `widget_info.xml`: `configure`, `widgetFeatures="reconfigurable|configuration_optional"`, `targetCellWidth=4 targetCellHeight=2`, `minResizeWidth=110dp minResizeHeight=50dp`, `previewImage`.
- [ ] **Step 5:** `WidgetShots`: 모양 5 × (1·2·3~4대) × 진하기 100/30 + ROW 상태 6 → `tools/sheets.py` 대조표. **합격 기준:** 시안(`final/preview.html`을 같은 크기로 헤드리스 캡처)과 나란히 놓아 요소 위치·크기 ±4dp, 색 hex 같음, 글자 같음. `tools/contrast.py`로 렌더 PNG의 글자 영역 대비 ≥4.5 측정. 칸 잡고.
- [ ] **Step 6:** commit `feat(wherepark): bitmap widgets in five shapes` + push.

## Task 8: 차 · 블루투스 · 운행 중

- [ ] **Step 1:** Manifest: `BLUETOOTH_CONNECT`, `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`, `POST_NOTIFICATIONS`, `uses-feature companion_device_setup required=false`, `PresenceService`(`BIND_COMPANION_DEVICE_SERVICE`). CarLink = 0.4.1 `CarLink.kt`·`MainActivity`의 associate 흐름 이식하되 **차마다**: associate 결과 address → `CarLinkStore.set(carId, address)`, `startObservingDevicePresence(address)`. 차를 지울 때 그 차 association만 `disassociate`(다른 차 유지). BT CONNECT 런타임 권한 요청.
- [ ] **Step 2:** `onDeviceDisappeared(info)` → `CarLinkStore.carOf` → `ExitNotifier.maybeNotify`: 같은 차 30분 안 1회, `setTimeoutAfter(10분)`, 액션 [말하기](ListenActivity `bt_car_id`, `from=notif`)·[층 고르기](Manual)·[안 댔어요](연속 카운트, 3번이면 다음 알림에 [이 차 알림 끄기]). 2분 안 Appeared면 취소.
- [ ] **Step 3:** Appeared(끊긴 지 2분 뒤) → 로컬 최신 Park의 state=DRIVING, drivingBy=내 이름 + `SyncQueue.add(Op.State(carId, DRIVING))`(대기 중 PutPark가 있으면 합쳐짐, contracts §3) + 위젯.
- [ ] **Step 4:** 알림 채널 3개(내리면 묻기 / 가족이 댔을 때 / 운행 시작), 설정 토글과 연결, [알림 시험]. 0.4.1이 깔려 있으면 같은 차 알림이 두 번 올 수 있다 → APK 전달 메모에 "0.4.1(주차층) 지우기".
- [ ] **Step 5:** commit `feat(wherepark): per-car bluetooth presence, exit prompt, driving` + push.

## Task 9: 가족 초대·합류

- [ ] **Step 1:** `Invite.kt`: `Api.invite` → link·code. [카카오톡으로 보내기] = `Intent(ACTION_SEND).setPackage("com.kakao.talk")`(없으면 chooser), [다른 앱으로], QR(ZXing `QRCodeWriter` → Bitmap), 코드 [복사](`ClipboardManager`).
- [ ] **Step 2:** JoinActivity: intent-filter `autoVerify` `https://dot-lab-parking.dotconnects.workers.dev/j`, 코드 = `uri.fragment` 또는 쿼리 `c`(카톡 [앱으로 열기] 경로). `Api.peek` → "민지네 가족 · 3명" → 내 이름 → `Api.join`. 이미 다른 가족이면 "지금 가족에서 나가고 들어갈까요?" 확인 → **contracts §3 "합류" 순서**(새 join 먼저, 성공 후 옛 인증으로 deleteMe, 혼자였던 가족의 차 옮길지 묻기). 클립보드에 `[A-Z2-9]{4}-?[A-Z2-9]{4}-?[A-Z2-9]{4}`가 있으면 붙여 넣기 제안.
- [ ] **Step 3:** `Referrer.kt`: 첫 실행에서 Install Referrer `code=` 읽기(앱 설치가 Play 경유일 때만; 사이드로드는 링크/코드 경로).
- [ ] **Step 4:** commit `feat(wherepark): invite link, qr, code copy, join` + push.

## Task 10: 앱 화면 · 첫 실행 · 타일 · 바로가기

- [ ] **Step 1:** `Theme.kt`(Global 색, 글자 크기 = 시안 CSS의 `h3 22px/800`, 카드 숫자 `64px/900`, 본문 `14.5px`, 보조 `12.5~13.5px` → sp), `Icons.kt`(시안 `IP` 경로 → ImageVector). 화면: 홈(카드 목록, 카드별 마이크 칩, 큰 주황 버튼 + "흰 차에 저장"), 차 상세(층·칸 [고치기], 사진, 메모, [차량번호], [가져갔어요] 외곽선, 내 기록 줄), 차량번호 크게(`FLAG_KEEP_SCREEN_ON`, [번호 복사]), 설정(내 차 · 알림(차별 3토글 + [알림 시험]) · 가족 · 보기(글자 크게) · 정보(처리방침 링크, 내 데이터 지우기 = `deleteMe` + 로컬 초기화, 문의 메일 표시·복사, 버전)), 차 편집(별명·색 6·번호·BT 연결·지우기), 가족 관리(마지막 활동, 30일 무활동 [내보내기], 방장 줄 메뉴).
- [ ] **Step 2: 첫 실행.** 0단계(보이지 않음): Referrer·링크 코드가 있으면 **바로 합류 흐름**(그룹 만들기·차 만들기 건너뜀, 차는 가족 것을 받음). 없으면: 1 소개 + 알림 권한 → 2 내 이름·차(별명·번호·BT 연결 선택) + Wi-Fi면 음성팩 다운로드, 끝에 `pendingGroup(name, "${name}네 가족")` 저장 + `PutCar` + kick(그룹은 SyncWorker가 만듦, contracts §3) → 3 "한번 말해 보세요"(ListenActivity `from=practice`) → 4 가족 초대 / 나중에 → `requestPinAppWidget`.
- [ ] **Step 3:** `QsTile`(`TileService`, `startActivityAndCollapse(PendingIntent)` — API 34 PendingIntent 버전), `shortcuts.xml`(말로 남기기) + 동적 바로가기(차마다 "흰 차 어디?" → 차 상세).
- [ ] **Step 4:** `ScreenShots`: 시안 2~8절 화면 PNG + 글자 200% 한 번 → 대조표. 합격 기준은 Task 7 Step 5와 같음(±4dp·hex·글자, 200%에서 잘림 없음). 칸 잡고.
- [ ] **Step 5:** commit `feat(wherepark): app screens, onboarding, quick tile` + push.

## Task 11: 서명 · APK · 출시 메모

- [ ] **Step 1:** 업로드 키: `keytool -genkeypair` → `~/.wherepark/upload.jks`(repo 밖), `~/.wherepark/upload.properties`(storeFile·비밀번호는 `secrets` 대신 로컬 파일, 커밋 안 함). SHA-256 지문을 Worker `wrangler.toml` `ASSETLINKS_SHA256`에 넣고 `npx wrangler@4 deploy` → `https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://dot-lab-parking.dotconnects.workers.dev&relation=delegate_permission/common.handle_all_urls`로 검증(python urllib). Play App Signing 지문은 Play 등록 때 추가(출시 task).
- [ ] **Step 2:** 전체 단위 테스트 + 스크린샷 테스트 1회(칸 잡고) → 출력 첨부. `./gradlew :app:assembleRelease --max-workers=2` → APK ≤30 MiB, `zipalign -c -P 16 -v 4` 확인.
- [ ] **Step 3:** `privacy.html`(작업 폴더): 보내는 것 = 층·구역·번호·메모·차 이름·색·번호판(선택)·사진(선택)·가족 이름. 보내지 않는 것 = 음성, 위치 좌표, BT 주소. 메모(말에서 자리 정보를 뺀 나머지 문장, 예 "엘리베이터 옆")는 사용자가 남긴 내용으로 가족에게 보낸다고 적는다. "차의 주차 자리를 가족과 나누는 앱이며 사람의 위치를 추적하지 않아요"(스토커웨어 정책 문구). 보관·삭제(사진은 직전 기록 1건까지·최대 30일, 내 데이터 지우기), 문의. `app-v2/RELEASE.md`: 데이터 안전 양식 답, 권한 목록, Play 출시 남은 일(AAB, Play App Signing 지문 → assetlinks, 12명×14일, LBS 간이 신고 판단 = family-legal 결론 참조).
- [ ] **Step 4:** SendUserFile로 APK + 확인 5줄(위젯 마이크 → 저장 / 비행기 모드 인식 / 두 번째 폰 합류·위젯 갱신 / 차 BT 끊김 알림 / 카톡 링크) + "주차층 0.4.1 지우기" + Firebase 마법사 안내(푸시용).
- [ ] **Step 5:** commit `chore(wherepark): signing, privacy page, release notes` + push.

## 완료 판정

- 이 plan이 끝나면 T-0189 done_when 1·2·4는 테스트·스크린샷으로 확인, **3(두 폰 푸시)과 5(Play 출시)는 운영자 실기기·Play 단계가 남는다** → T-0189 status `in_progress`, verification에 "APK 전달, 실기기 확인 대기"로. 두 폰 확인 전엔 done으로 옮기지 않는다.
- 실행 순서: 0 → 1 → 2 → 3(Codex 병렬, 같은 파일 없음) → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11. 서브에이전트: 1·2·4·5·8·9 Sonnet, 6·7·10 Opus(시안 맞추기). 빌드는 언제나 1개.
