# 여행 책자 — 테마 시안 5차 지시서 (테마 전문가 공통)

S = C:\Users\ehdck\AppData\Local\Temp\claude\c62cf31192c54cdd\50332936-9748-4893-ad04-c354d2294c91\scratchpad

## 상황
Android 앱 「여행 책자」: 여행 날짜만 넣으면 폰 사진·영상 중 잘 나온 것을 골라 넘겨 보는 책 + 페이지 이미지 + PDF + 하이라이트 영상을 만든다.
테마 6개 중 하나를 고른다. 너는 **한 테마의 디자인 전문가**다.
4차 시안을 본 운영자(한국인 20~30대 감각, 폰으로 봄): **"너무 아재같아. 좀 더 예쁘게."** — 거의 처음부터 다시 그린다.
통과 기준: 20~30대가 인스타에 올리고 싶은 책. "요즘 독립출판 진/포토북" 처럼 보여야 한다.

## 4차가 아재 같았던 이유 (전부 금지)
- 손글씨 글꼴(Nanum Pen, Gamja Flower, Gaegu), 명조·세리프 제목(Hahmlet, Nanum Myeongjo, Noto Serif KR, Gowun Batang), 세로쓰기 제목
- 도장·우표·마스킹테이프·크라프트지·폴라로이드 흩뿌림·필름 구멍 띠 장식·영수증 바코드·짐표
- 검은 앨범 바탕, 사진 흰 테두리 + 그림자, 모서리 꽂이
- 큰 흰 여백 속에 작은 사진이 떠 있는 배치(카탈로그/도록 같음)
- 쨍한 관광 엽서 사진 그대로(→ 사진 톤 보정, 아래)
- "제주, 사흘" 같은 문어체 제목, 설명조 문장

## 먼저 볼 것 (반드시 Read 로 이미지 직접 보기)
- 국내 젊은 레퍼런스 35장: `S\refs5\young\sheetY1.jpg`~`sheetY5.jpg`, 규칙 `S\refs5\young\GRAMMAR.md`
- 해외 레퍼런스 33장: `S\refs5\global\sheetG1.jpg`~`sheetG5.jpg`, 규칙 `S\refs5\global\GRAMMAR.md`
  (두 GRAMMAR 가 이번 라운드의 공통 문법이다: 산세리프, 크기 대비 극단 8~12:1, 풀블리드 기본, 사진끼리 0~4px 로 맞붙임,
  한 페이지 강한 색 하나를 넓은 면으로, 숫자·시각·좌표를 그래픽으로, 장식 대신 UI 조각(지도 경로 알약·말풍선·달력 점·재생바)·누끼 한 점,
  짧은 사실 한 줄 캡션)
- 새 임시 사진: `S\mock\img5\sheet.jpg` + `S\mock\img5\credits.json` (키·방향·설명). 키를 `{{키}}` 로 쓴다.
  메인 키 10개(hyeopjae road udo haenyeo doldam woljeongri seongsan hallasan oreum tangerine), 변형 `_2`, 디테일 d1…가 있으면 써라
  (실제로 있는 파일만: `S\mock\img5\<키>.webp`). 세로 사진은 세로 칸에.
- 4차 네 테마 파일(무엇을 버려야 하는지): `S\mock\r3\<키>.html`, 쪽별 모음 `S\mock\r3\sheet-<키>.jpg`
- 제주 해안선 다각형·투영: `S\mock\r5\jeju.py` (지도는 1색 실루엣 + 점 + 얇은 선. 실사 지도 금지)

## 공통 규칙
- 글꼴: 산세리프만(한글: Noto Sans KR, IBM Plex Sans KR, Gowun Dodum, Nanum Gothic, Do Hyeon, Black Han Sans, Gasoek One, Bagel Fat One, Orbit /
  라틴: Inter, Inter Tight, Space Grotesk, Archivo, Familjen Grotesk, Instrument Sans, DM Mono, IBM Plex Mono, JetBrains Mono 등 Google Fonts).
  테마끼리 같은 조합 쓰지 말 것 — 아래 테마별 배정을 따른다.
- 사진 톤: 앱 옵션 "사진 톤 맞추기"(켜짐 기본)를 가정하고, 사진에 테마별 보정을 CSS 로: 예 `filter: saturate(.82) contrast(.96) brightness(1.03)`
  + 필요하면 아주 옅은 그레인(인라인 SVG feTurbulence, 불투명 3~6%). 테마마다 톤을 다르게(따뜻한 필름/차가운 매트/흑백 한 장 등).
- 문구: 한국어, 짧게. 사실 한 줄 또는 반말 혼잣말 한 줄(12~20자). 금지: 추억, 힐링, 소중한, ~했던 순간, 잊지 못할, 이모지, 번역투, 'AI', 감탄.
  라틴 소문자 라벨은 좋지만 한 쪽에 한두 개만(예: "jeju, june", "day 02", "09:42"). 날짜·시간·장소는 아래 데이터와 맞출 것.
- 글자 크기: 폰 한 쪽(약 330×440px)에서 본문 11px 미만 금지, 캡션 9.5px 미만 금지(9.5px 는 굵기 500+). 사진 위 글자 대비 확보.
- AI 티 금지: 크림+세리프+테라코타, 검정+형광 한 점, 보라 그라데이션, 모든 요소 둥근 카드, 이모지.
- 한 권에 같은 장치 2~3번까지.

## 여행 데이터 (공통, 예시)
제주 2박 3일, 2026.06.12(금)–06.14(일). 사진 46 · 영상 3 · 9곳 · 이동 214km.
| 날 | 시각 | 곳 | 사진 키 | 메모 |
|---|---|---|---|---|
| 1일 06.12 금 · 맑음 24° | 13:20 | 협재 해변 | hyeopjae | 물이 생각보다 차가움, 비양도가 흐려졌다 맑아짐 |
| | 17:40 | 신창 풍차해안도로 | road | 해 질 무렵, 바람 셈 |
| 2일 06.13 토 · 바람 강함 21° | 09:42 | 우도 (도항선 15분) | udo | 모자가 두 번 날아감 |
| | 14:10 | 하도 해녀 탈의장 앞 | haenyeo | 영상 0:03, 해녀 두 분 그물 정리 |
| | 16:05 | 월정리 돌담길 | doldam | 돌담과 밭, 낮은 집 |
| | 17:20 | 월정리 해변 | woljeongri | 산책로, 사람들 |
| | 19:05 | 성산일출봉 앞 | seongsan | 수국, 해 진 뒤 파란 시간 |
| 3일 06.14 일 · 흐림 22° | 08:30 | 한라산 어리목 숲길 | hallasan | 나무 계단, 안개 |
| | 11:10 | 원물오름 | oreum | 오름 길 |
| | 13:30 | 귤나무 카페 마당 | tangerine | 아직 초록 귤, 비행기 시간 |
디테일 사진(d…)은 위 장소·시간 어딘가에 자연스럽게 끼워 넣어라(카페 커피 = 13:30 귤나무 카페 등).

## 결과물 형식 (그대로)
파일 하나: `S\mock\r5\<테마키>.html`
1. 첫 줄: `<!-- fonts: Family+Name:wght@400;700 | Other+Family -->` (Google Fonts 이름 그대로. 이 목록만 로드됨)
2. `<style>` 하나, **모든 선택자 `.t-<테마키>` 로 시작**, 색은 `.t-<테마키>{--변수:값}` 토큰.
   껍데기 공통 클래스 `.strip` `.pg`(폭 min(80vw,330px), 3:4, overflow hidden) `.play`(▶, 안에 span) `.under` 는 크기·비율 바꾸지 말 것.
3. `<section class="t-<테마키>" data-palettes='[{...},{...},{...}]'>` 하나. 안에:
   - `<div class="head"><h2>테마 이름</h2><p class="idea">한 줄 성격</p><button class="shuffle" type="button">색 조합 바꾸기</button></div>`
   - `<h3 class="sub">표지 후보</h3><div class="strip"> .pg 3개 </div><div class="under"><span>①…②…③…</span><span>3개</span></div>`
   - `<h3 class="sub">책 본문</h3><div class="strip"> .pg 9~11개 </div><div class="under"><span>쪽 순서</span><span>N쪽</span></div>`
   - `<details class="notes"><summary>디자인 규칙</summary><ul>` 6~10줄 (앱이 다른 여행에도 적용할 규칙, 수치 포함) `</ul></details>`
   표지 3개는 서로 확실히 달라야 한다(사진·제목 배치·구도). 본문에 영상 쪽(▶ + "PDF 에서는 사진 한 장으로" 처리), 마지막 쪽 포함.
4. 이미지: `<img src="{{키}}" alt="">`. 외부 이미지·스크립트 금지. 장식은 CSS/인라인 SVG.
5. `.head` `.sub` `.notes` 모양은 껍데기 것(테마 색 쓰지 말 것).
6. 같은 사진을 한 권에서 두 번 쓰지 않는다(변형 `_2`·디테일로 채움). 표지 사진은 본문과 겹쳐도 된다.

## 스스로 확인 (2번 이상)
`C:/DC/DD/content-system/.venv/Scripts/python.exe S\mock\r5\preview.py <테마키>` → `S\mock\r5\shot-<키>-*.png`.
쪽별 확대: `preview.py --all --publish` 대신 `python pages.py <키>` 는 합친 파일이 필요하니,
간단히: preview 결과 `out-<키>.html` 을 쓰도록 `pages.py` 를 복사해 `pages_<키>.py` 로 경로만 바꿔 써도 된다 → `sheet-<키>.jpg`.
Read 로 직접 보고 고칠 것(글자 잘림·겹침·대비·빈 면·사진 반복). 렌더·빌드·Gradle 하지 않는다.
스스로 묻기: "이 쪽 하나만 인스타에 올려도 예쁜가? 20대가 보면 아재 같다고 할 요소가 남았나?"

## 보고 (짧게, 한국어)
파일 경로, 표지 3개 아이디어 한 줄씩, 본문 쪽 구성, 참고한 레퍼런스 번호, 4차 대비 바꾼 핵심 3가지.

## 사진 주의 (Claude 확인)
- haenyeo_2 는 쓰지 말 것(얼굴·공연 장면). 해녀는 haenyeo 한 장 + 영상 쪽.
- 임시 사진과 메모가 안 맞는 곳은 사진에 맞춰 문구를 쓴다: 귤은 주황(“초록 귤” 쓰지 말 것), seongsan_2 는 유채(수국이라 쓰지 말 것),
  hallasan 은 안개 낀 봉우리, hallasan_2 는 돌하르방 나무 계단, oreum 은 억새밭 뒷모습. woljeongri 는 해변 카페 마당.
- 좋은 컷: udo(배 위 뒷모습+갈매기), road(풍차 노을, 세로), doldam, oreum, tangerine, d1(카페 창가), d7(돌담 벤치), d2(커피 두 잔), d4(조개 손), d6(귤 손).
