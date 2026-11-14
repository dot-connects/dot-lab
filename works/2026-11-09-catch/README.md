# 우리 집 몬스터 잡기 — 작업 폴더

## 이어서 하기 (2026-11-15 기준 0.3.3 — 이제 **안드로이드 앱**이 본체)

**웹 `site/` 는 동결했다.** 앞으로 작품은 `app/` 네이티브 앱(kr.dot.catchpet 0.2.0) 하나다. 설계는 `spec-app.md`, 계획은 `plan-app.md`(/plan-review 4회, High 11·2·1·0, 기록 `C:/DC/DD/docs/archive/2026-11-12-catch-app-plan-review`).
동결된 웹 게임도 같은 Worker 를 쓰므로 이제 OpenAI(gpt-image-2.6-flare)로 그린다.

| 부분 | 파일 | 상태 |
|---|---|---|
| 뽑기 | `ui/CatchScreen.kt`, `game/`, `seg/`, `camera/` | CameraX + DeepLab 0.3 + EfficientDet 보충 + 4프레임 유지(`research/seg-compare.md` 120/120), 집게 판정, 캡슐, 멈춤 없는 다음 판 |
| 털 녀석 | `ar/Critters.kt`(자이로), `ar/` AR(ARCore 1.48, 개발자 스위치) | 16마리 × 14장 굽기 `tools/bake-critters.mjs` → `assets/critters/`, 크기 1/4·1/5 스위치 |
| 도감·상세 | `dex/Dex.kt`, `ui/DexScreen.kt`, `ui/DetailScreen.kt` | 원자 저장, 털 녀석 모습 합치기, AI 미리보기(하루 5), 구매 잠금 500원 |
| AI | `ai/`, `worker/` | Worker 배포됨: /transform low, /sheet medium, 월 500센트 상한, `x-app: catchpet`, 디버그 구매 토큰 |
| 펫 | `pet/PetBrain.kt`, `pet/PetService.kt`, `ui/PetScreen.kt` | 오버레이 하나: 켜기 + [홈 화면(사용정보 권한으로 런처가 위일 때만) \| 항상]. 라이브 배경화면·잠금화면은 11-14 삭제(안드로이드가 잠금 위 오버레이를 막음) |
| 메뉴 모양 | `ui/Book.kt`, `mockups/menus.html` A안 | 하늘·잔디 바탕, 잉크 카드, Jua·고운돋움(Google 다운로드 글꼴), 소리 `ui/Sfx.kt` + `res/raw` (Kenney Interface Sounds, CC0, OpenGameArt) |

- 빌드: `app/` 에서 `JAVA_HOME=C:/DC/tools/jdk-17 ./gradlew assembleDebug testDebugUnitTest`. APK 는 `%LOCALAPPDATA%/catchpet-build/app/outputs/apk/debug/`.
- **트레이딩 틱 보호:** 06:00–07:00 · 08:30–09:10 · 15:30–16:30 · 22:00–23:50 KST(+05:30–06:00)에는 Gradle · 헤드리스 Chrome 금지(plan R12/R17).
- 디버그 구매 토큰: `%USERPROFILE%\.catchpet-debug-purchase` — debug 빌드는 빌드할 때 이 파일을 BuildConfig 에 넣어 AI 펫 그림이 무료(11-14). release 는 빈 값. 개발자 칸은 펫 화면 "펫" 제목 길게 누르기.

**운영자가 할 일**
1. **OpenAI API 키 교체** — 11-12 밤 Worker 로그 확인 중 키 원문이 세션 기록에 찍혔다. platform.openai.com 에서 폐기·새로 만들고 `dot-lab/scripts/wizard-catch-openai.bat` 을 다시 실행(BOM 버그는 고침).
2. (11-14 해결: 라이브 배경화면 삭제, 펫은 홈 화면/항상 오버레이)
3. 실폰 확인 목록(아래).

**실폰 확인 목록 (PC 에 기기·에뮬레이터가 없어 아무것도 실폰에서 돌려 보지 못함)**
- [ ] 앱이 켜지고 카메라 권한 → 뽑기 화면
- [ ] 강아지·고양이·사람 윤곽이 화면과 맞는지(집게 조준선이 녀석 머리에 닿는지), 오린 그림이 녀석과 맞는지
- [ ] 끌어서 놓기 → 캡슐 → "도감 N" 증가 → 바로 다음 판
- [ ] 털 녀석 3마리가 바닥 쪽에 보이는지, 폰을 돌리면 제자리에 있는지(자이로), 크기 1/4 vs 1/5
- [ ] 도감 → 상세 → AI 미리보기(털뭉치·피규어) 실제로 오는지
- [ ] 디버그 토큰 넣고 "펫으로 사기" → 동작표 → 펫으로 쓰기
- [ ] 펫: 아래 줄 걷기·갸웃, 벽 타기, 누르기(웃음)·두 번(춤)·꾹 끌기, 오래 두면 잠
- [ ] 아이콘 뒤(라이브 배경화면) 설정과 탭
- [ ] AR 스위치 켜고: 바닥 찾기, 털 녀석이 바닥에 서 있는지, 돌아보면 방향 그림이 반대면 `ArCritters.DIR_SIGN = -1`

**출시 전(6단계):** Play 결제 연결, 개인정보 처리방침, 데이터 안전 양식, 16KB 페이지 정렬(tasks-vision · ARCore so), APK 78MB(모델 2개 + 털 녀석 그림 + ARCore).

<details><summary>2026-11-11 까지의 웹 기록 (옛 "이어서 하기")</summary>



**잡은 진짜(개·고양이·사람) 사진은 그대로 쓰지 않는다 (7561ee9).** 잡으면 폰 안 털뭉치 그림(`fluffify.js` 색 → 멍솜/냥솜/사람 틀)이
바로 나오고, 동의한 폰이면 AI 그림이 10~30초 뒤 바뀌어 들어온다. AI 스타일은 3a 털뭉치 / 3b 3D 피규어 (운영자 2026-11-11 "3a, 3b 만").
툰 필터(`toonify.js`)·진짜 3D 모델(GLB)은 뺐다. 시안 `mockups/transform.html`, 계획 `plan-transform.md`.
- 서버 `worker/` = Cloudflare Worker `dot-lab-catch` (https://dot-lab-catch.dotconnects.workers.dev/transform). Gemini `gemini-3.1-flash-image`
  Interactions API, `store: false`. 하루 한도: 기기 5 · IP 15 · 전체 100 (D1 `dot-lab-catch`). `ALL_DAY = "0"` 이면 즉시 끔.
  모델 응답 형식은 문서로만 확인 — 첫 실호출에서 `pickImage` 가 맞는지 볼 것.
- **아직 꺼져 있음(503 off):** 운영자가 `dot-lab/scripts/wizard-catch-gemini.bat` 로 결제 연결된 Gemini 키를 넣어야 켜진다.
- AI 는 초록 배경으로 그리게 하고 `ai.js` `chromaKey` 로 투명하게 만든다. 원래 사진은 폰 IndexedDB pose `orig` 에만, 도감 상세에 작게.
- 헤드리스 S2 는 `?ai=` 로컬 스텁(초록 고양이 `check/ai-stub.jpg`)으로 AI 교체까지 돈다. 단, 털 녀석 풀에선 소프트웨어 GL 이 느려 첫 사진(흰 개)
  인식이 안 잡힌다(말랑 풀로 돌리면 통과) — 실폰에서 털 녀석이 있을 때 진짜 찾기가 느려지는지 확인 필요.
- 펫 자리: 시안 `mockups/places.html`(7자리)에서 운영자 2026-11-11 **①옆 벽 ②배경 속 ④카메라 구멍 ⑥맨 아래 줄** 을 골랐고, 손가락으로 옮겨 놓을 수 있어야 한다.
  둥둥 떠 있지 말고 벽을 짚고 오르내리기·거꾸로 매달리기·발 움직이기. 움직이는 시안 `mockups/move.html`(3D 털뭉치 발 애니메이션) — 운영자 판정 대기.
  조사 원문 `research/pet-refs.md`. Gemini 대신 Codex 는 불가(구독은 API 아님). 운영자 구독은 Google AI **Plus**(Pro 아님, 2026-11-11 앱 화면). 같은 사진·지시문으로 Gemini 앱에서 6장 뽑아 Codex 와 비교: `mockups/gemini.html`, 원본 `out/ai/gemini/` (한 장 ~20초, 셀카 컷은 사람이라 "사람"으로 다시 보냄).

**게임 녀석 = 털뭉치 16마리 (1e00bb9).** `VIRTUAL` = `group` 이 `fluff`(컨셉 6: 몽글·삐약·후후·실타·달솜·밤톨·살랑·보들) 또는
`fluff2`(운영자 요청 동물: 냥솜·멍솜·펭송·뭉게·라라·미어·꿀꿀·곰솜). 정면 시트 `mockups/fluff.html` 에서 운영자 "좋네, 적용해줘".
- 털(셸 퍼) 재질은 조명을 안 받아서 `modelCreature` 가 `uC` 색에 방 색 tint 와 포획 번쩍임을 직접 곱한다.
- 실타 바늘이 짧아 더듬이처럼 보일 수 있음(운영자 미지적). 다음은 운영자 실폰 판정.
- 헤드리스 feel 은 방향 센서 override 가 없어 녀석이 거꾸로 찍힌다 — 화면 확인은 s1 스크린샷으로.
- 8765 포트는 ai-hub `ops serve`(0.0.0.0)와 겹친다. 정적 서버를 띄울 땐 다른 포트를 쓰고, 끌 때 포트 주인을 확인할 것.

**지금 라이브 상태 (push 됨):**
- 던지기 손맛·GO 식 확률·연출·자체 공(cfe60ec), 헤드리스 자이로 0값 수정(e8982b8).
- 말랑 3D(몽실·보리·차돌 + 새 친구들 8)는 SPECS 에 남아 있다(옛 도감 기록용). 게임 풀에선 빠짐. `jelly` 재질 있음(컨셉 5용).
- **그림 넣기** 버튼(1eeeb5e): 운영자가 휴대폰에서 고른 PNG 가 녀석이 된다(브라우저 localStorage 에만, 파일 이름 = 이름, 다시 고르면 덮어씀).
  아쉬운 점: 기본 녀석으로 되돌리는 버튼 없음, 그림 세트를 바꾸면 도감의 옛 그림 녀석 사진이 빈다.
- 실존 캐릭터(포켓몬·치이카와 등)를 가져오거나 3D·코드로 재현하지 않는다 — 운영자가 여러 번 요청했고 Claude 가 거절함(2026-11-10).
  운영자 자신의 그림은 그림 넣기로만, 리포에는 넣지 않는다.

**남은 것(운영자 판단 대기):** 실폰 확인(손맛·커브·녀석·바닥 붙음), 화면 모드에서 녀석이 떠 보임, 자이로 없는 화면 모드 스와이프가 시점도
돌림, 검은 고양이 머리 빠짐·셀카 강아지가 고양이로, Android 앱은 웹 최신 변경 미반영(옆 가장자리 20% vs 목표 15%). 목록 페이지 미등록·manifest 없음 유지.

기획 `brief.md`, 구현 계획 `plan.md`. 작품은 `site/`.


</details>

## 출처
- 화면 모드 배경 `site/img/room.jpg`: Poly Haven **Hotel Room** (`hotel_room`), CC0 —
  https://polyhaven.com/a/hotel_room . 공식 tonemapped JPG(8192×4096)를 4096×2048 로 줄여 저장
  (계획의 2k 는 폰 세로 화면에서 벽이 뭉개져 4k 로 올림, 618 KB).
- (옛, 지금 안 씀) 포켓몬 일러스트: PokeAPI sprites `other/official-artwork/<id>.png` 를 jsDelivr 로
  (`cdn.jsdelivr.net/gh/PokeAPI/sprites@master/...`), 한국어 이름은 `pokeapi.co/api/v2/pokemon-species/<id>`.
- three.js 0.160.0 (jsDelivr).
- `site/js/ar.js` 의 자이로·첫 자세 배치·2.5초 재배치·카메라 FOV·빛 색은
  `works/2026-11-04-dalgona/site/ar/ar.js` 에서 복사 (파일 머리에 출처).

## 확인
- 단위 테스트: `node --test site/js/*.test.mjs`
- 헤드리스(S1): `python -m http.server 8765 --bind 127.0.0.1 --directory C:/DC/DD/dot-lab` 를 띄운 뒤
  `node check/s1.mjs 8765 <빈 프로필 폴더>` → `out/s1/*.png` + `out/s1/result.json`.
  끝나면 남은 headless chrome 을 확인해 끈다(Windows 에서 자식 프로세스가 남는다).
- 헤드리스(S2): 같은 서버에서 `node check/s2.mjs <port> <빈 프로필 폴더>` → photo-booth `research/pet-matting/media` 사진 3장으로
  박스·오려 내기·칩·저장·새로고침·재포획 → `out/s2/*.png`(`*-cut.png` = 저장된 투명 PNG) + `out/s2/result.json`.
- 헤드리스(S3): 같은 서버에서 `node check/s3.mjs <port> <빈 프로필 폴더>` → 빈 도감·포켓몬 포획 카드(Web Share 경로를 흉내 내 File 로 받음)·
  S2 오려 낸 사진으로 채운 도감·상세·상세에서 만든 카드(다운로드 경로)·`.dotpet` 내보내기(한 마리·전체)를 `petfile.parse` 로 다시 읽어 도감과 대조
  → `out/s3/*.png`(`card-*.png` = 카드 PNG, `export-*.png` = 내보낸 모습) + `all.dotpet`·`one.dotpet` + `result.json`(`ok`). 끝나고 남은 headless chrome 확인.
- 손맛(feel): 같은 서버에서 `node check/feel.mjs <port> <빈 프로필 폴더> [flickSeed]` → 1.2–3.5 px/ms·±6° 흔들린 일부러 던지기 20번의 명중률
  (목표 ≥ 80%) + `?slow=6` 으로 장전된 커브·명중 글자·흔들림·잡음·튀어나옴·빗나감 스크린샷 → `out/feel/*.png` + `result.json`. 끝나고 남은 headless chrome 확인.
- 녀석 그리기는 `site/js/creature.js` 의 `{root, hit, shadowR, update, dispose}` 뒤에 있다: `modelCreature(spec)`(creatures.js 3D) · `spriteCreature(img)`(그림 넣기).
- 시안 스크린샷: 서버를 띄우고 헤드리스로 `mockups/*.html` 을 찍는다. 자이로 테스트는 CDP `DeviceOrientation.setDeviceOrientationOverride {alpha:0,beta:85,gamma:0}` 를 줘야 화면이 바로 선다(안 주면 헤드리스 가짜 센서로 거꾸로).
- 쿼리: `?seed=N` 결정적 난수, `?slow=N` 게임 시계 N배 느리게(테스트 스크린샷용), `?touch` 시작 화면 없이 화면 모드, `?fov=` 카메라 FOV, `?lag=` 자이로 지연(ms),
  `?still=<url>` 카메라 대신 사진(사진 전체가 카메라 프레임, 화면은 cover 로 자름), `?cpu` DeepLab CPU delegate(헤드리스 소프트웨어 GL 은 GPU 경로가 프레임당 3초+),
  `?boxes` 진짜 박스 전부 점선으로.
- 진짜 찾기·오려 내기: `site/js/seg-worker.js` 는 photo-booth `site/js/pet-worker.js`, `site/js/refine.js` 는 photo-booth
  `site/js/refine.js` 에서 복사 (파일 머리에 출처). DeepLab v3 (MediaPipe tasks-vision 0.10.14, Apache-2.0).
