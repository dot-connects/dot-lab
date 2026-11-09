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
- 쿼리: `?seed=N` 결정적 난수, `?touch` 시작 화면 없이 화면 모드, `?fov=` 카메라 FOV, `?lag=` 자이로 지연(ms).
