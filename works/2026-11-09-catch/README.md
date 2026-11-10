# 우리 집 몬스터 잡기 — 작업 폴더

기획 `brief.md`, 구현 계획 `plan.md`. 작품은 `site/`.

## 출처
- 화면 모드 배경 `site/img/room.jpg`: Poly Haven **Hotel Room** (`hotel_room`), CC0 —
  https://polyhaven.com/a/hotel_room . 공식 tonemapped JPG(8192×4096)를 4096×2048 로 줄여 저장
  (계획의 2k 는 폰 세로 화면에서 벽이 뭉개져 4k 로 올림, 618 KB).
- 포켓몬 일러스트: PokeAPI sprites `other/official-artwork/<id>.png` 를 jsDelivr 로
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
- 녀석 그리기는 `site/js/creature.js` 의 `{root, hit, shadowR, update, dispose}` 뒤에 있다(지금은 그림 카드, 나중에 3D 녀석으로 교체).
- 쿼리: `?seed=N` 결정적 난수, `?slow=N` 게임 시계 N배 느리게(테스트 스크린샷용), `?touch` 시작 화면 없이 화면 모드, `?fov=` 카메라 FOV, `?lag=` 자이로 지연(ms),
  `?still=<url>` 카메라 대신 사진(사진 전체가 카메라 프레임, 화면은 cover 로 자름), `?cpu` DeepLab CPU delegate(헤드리스 소프트웨어 GL 은 GPU 경로가 프레임당 3초+),
  `?boxes` 진짜 박스 전부 점선으로.
- 진짜 찾기·오려 내기: `site/js/seg-worker.js` 는 photo-booth `site/js/pet-worker.js`, `site/js/refine.js` 는 photo-booth
  `site/js/refine.js` 에서 복사 (파일 머리에 출처). DeepLab v3 (MediaPipe tasks-vision 0.10.14, Apache-2.0).
