# 우리 집 몬스터 잡기 — 작업 폴더

## 이어서 하기 (2026-11-10 밤 기준, 다음 세션은 여기부터)

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
