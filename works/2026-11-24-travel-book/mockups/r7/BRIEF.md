# 7차 레퍼런스 수집 지시서 (테마별 리서처)

S = C:\Users\ehdck\AppData\Local\Temp\claude\c62cf31192c54cdd\50332936-9748-4893-ad04-c354d2294c91\scratchpad

## 상황
Android 앱 「여행 책자」: 여행 날짜를 넣으면 폰 사진으로 넘겨 보는 책 + PDF + 하이라이트 영상을 만든다. 테마 6개.
6차 시안을 본 운영자(20~30대 한국인 감각): **"기록카드, 여행수첩, 일기, 잡지가 특히 별로였어."**
지금 시안: `S\mock\r5\sheet-<키>.jpg` (b=기록 카드, c=여행 수첩, diary=일기, mag=잡지) — 반드시 Read 로 먼저 본다.
지난 레퍼런스(이미 본 것, 중복 수집 금지): `S\refs5\young\sources.md`, `S\refs5\global\sources.md`.

## 너의 일
배정된 테마 하나의 **진짜 프로 레퍼런스 30장 이상**을 모은다. 목표는 "이 테마가 정말 잘 됐을 때 어떤 모습인가"를
디자이너들이 토론할 수 있는 근거. 일반 여행 포토북 말고, 테마의 **원형(archetype)** 에서 최고 수준 작업을 찾는다.

- 좋은 출처: fontsinuse.com, slanted.de, itsnicethat.com, magculture.com, stackmagazines.com, behance.net(큐레이티드·수상작),
  are.na 채널, 디자인 스튜디오 사이트(워크룸, 일상의실천, 프로파간다, 스튜디오 FNT, 김가든, Hato, Pentagram, Studio Dumbar 등),
  출판사·서점 상품 사진(매거진 B, AROUND, 스토리지북앤필름, 땡스북스, Monocle, Cereal, Kinfolk, Brutus, POPEYE, TRANSIT),
  앱 UI(Spotify Wrapped, Strava, Apple 사진 추억, Polarsteps, Day One) 등.
- 이미지 저장: `S\refs7\<키>\NN_짧은이름.jpg` (긴 변 1200px 이하로 줄여 저장). 받기는 Python urllib(브라우저 UA),
  curl 은 막혀 있음. 사이트가 막으면 다른 출처로. 로그인·결제 필요한 곳은 건너뛴다.
- 모은 뒤 4×3 모음 시트 `S\refs7\<키>\sheet1.jpg …` (Pillow) 를 만들어 **Read 로 직접 보고** 약한 것은 버린다.
- `S\refs7\<키>\sources.md`: | # | 파일 | URL | 무엇(한 줄) | 표.
- `S\refs7\<키>\NOTES.md` (핵심, 6KB 이하):
  1. 이 원형을 "프로답게" 만드는 것 8~12개 — 구체 수치(여백 비율, 글자 크기 대비, 단 수, 사진 비율, 색 수)와 레퍼런스 번호.
  2. 페이지 패턴 10개 — 각 패턴을 우리 시안 한 쪽(폰 세로 3:4)에 옮기는 방법 한 줄.
  3. 6차 시안(sheet-<키>.jpg)이 이것들과 비교해 아마추어처럼 보이는 이유 5개 — 쪽을 짚어서.
  4. 표지 아이디어 5개.
  5. 이 원형에서 "아재/AI 템플릿" 처럼 보이는 함정 5개.
- 사람 얼굴이 크게 나온 사진이나 저작권 문제 될 것을 우리 시안에 쓰지는 않는다 — 레퍼런스로만.

## 하지 말 것
시안 파일(`S\mock\r5\*`) 수정 금지. 렌더·빌드·Gradle 금지. 계정 생성·로그인 금지.

## 보고 (짧게, 한국어)
모은 장 수, 가장 좋은 레퍼런스 5개(번호·한 줄), NOTES.md 경로.
