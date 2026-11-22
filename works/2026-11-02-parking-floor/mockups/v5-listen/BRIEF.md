# Brief — 어디댔어 1.1.1: listen until stop, sentence-first parser

Operator-approved 2026-11-22. Mockup + truth table: `mockups/v5-listen/index.html` (open as text).
Option **C (아래 시트)** chosen. The 60-row table's "바뀐 뒤 (목표)" column is the expected output for every row.

## 1. Listening (C · bottom sheet)
- Tap mic (app or widget row) → bottom sheet over home, mic becomes **[정지]**; keeps listening until 정지.
  On-device `SpeechRecognizer` ends on silence: on `onResults`/`ERROR_NO_MATCH`/`ERROR_SPEECH_TIMEOUT` restart at once and
  append; show partials live (grey tail), timer, auto-stop at 60 s. Keep the live waveform. Car chip as today.
- 정지 → parse the joined text → save immediately (today's undo stays) → sheet shows the saved card (layout "2. 저장된 모습").

## 2. Parser = sentence first
- The record is the spoken sentence, cleaned: letters normalised (에이→A, 에이치원→H1, 비타워→B타워, 지→G only before a
  number/기둥), Korean numerals → digits, "다시" between two tokens → "-", trailing fillers removed (있어/있음/댔어/이야/요/에 at end),
  corrected parts removed (아니/아니다/말고/아 잠깐: the later value replaces the earlier one of the same kind, keep 지하).
  No word is ever dropped otherwise. Stored in `memo` (server limit raised to 80).
- Floor chip only for: 지하 N층 / BN / N층 / 지상 N층 / 옥상 / N.5층 / 지하 N.5층 / M층 / 중2층 / G층 / LN / PN (keep P as said),
  "지하 주차장 N층" = BN. Two different floors in one sentence → no chip.
- Zone/number chip only when adjacent and clear ("C 37", "A-7", "3-12", "가 7", "노랑 12", "E구역 212번"); a "-" pair is the
  pillar/spot label as said. Numbers followed by 동/호기/시/분/시간/번째/주차장 or preceded by 제/출구 are never the spot number.
  "N번 기둥" / "X N 기둥" = pillar.
- **Gentle asks (operator: "진짜 애매할 때는 되묻는 것도 좋다")** — never block saving. Save the default, then show one
  question row on the saved sheet with one-tap answers:
  - "N층" with no 지하/지상/B context → saved as N층, row "지하예요?" [BN으로] [아니요].
  - "이 구역" (E or 2) → saved E, row [2구역으로].
  - two floors → saved without chip, row "어느 층이에요?" [1층] [2층].
  Everything else: no question.
- Floor model + wire: add half floors and named floors. Wire regex (app + Worker `cleanPark`):
  `^(B[1-9](\.5)?|[1-9](\.5)?F|M|MZ2|G|L[1-9]|P[1-9]|ROOF)$` — `MZ2` = 중2층, `M` = M층. Labels: B1.5, 2.5층, M층, 중2층, G층, L2, P3.
  Old apps map unknown floors to null (check `floorOfWire`), so this is backward compatible.

## 3. Display
- Card: big floor chip (+ zone/number chip) + sentence. Widget row: floor big + sentence with the floor words removed,
  2 lines max with ellipsis. Check every widget shape still fits (WidgetSpecsTest + shots).

## 4. Worker
- `worker/src/core.js` `cleanPark`: floor regex above, memo ≤ 80. Tests in `worker/test/`. Do not deploy (Claude deploys).

## 5. Version
- `versionCode = 3`, `versionName = "1.1.1"`.

## Rules
- Tests: replace/extend `SpotParserTest` with a table test of all 60 rows (input → floor label, zone/number chip, sentence,
  ask kind). Keep the old cases that still hold. Full app suite + worker `npm test` green at the end.
- Gradle: `export JAVA_HOME=C:/DC/tools/jdk-17; cd app-v2 && timeout 1500 ./gradlew :app:testDebugUnitTest --max-workers=2 -Ptest.forks=1`.
  Timeout = deadlock: kill java, investigate. Render roborazzi shots of the listen sheet (listening + saved + an ask row)
  with `:app:recordRoborazziDebug --tests '<class>'` and list their paths.
- No commit, push, deploy or APK. Korean strings real UTF-8 (`git diff | grep -n '??'` empty). Only `app-v2/` and `worker/`.
