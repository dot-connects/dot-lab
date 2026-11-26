# 여행 한 권 — 11차 d: 나머지 테마 4개 (기록카드 b · 여행수첩 c · 일기 diary · 잡지 mag)

S = C:\Users\ehdck\AppData\Local\Temp\claude\c62cf31192c54cdd\50332936-9748-4893-ad04-c354d2294c91\scratchpad

**먼저 `S\mock\r11\BRIEF11b.md` 를 끝까지 읽어라** — 구조(디자이너 틀 라이브러리 + AI 편집), 파일 형식, 미리보기 방법, 휑함 금지 규칙이 거기 있다.
11차 b 에서 같은 방식으로 만든 사진만·여백의 책·앨범 틀이 운영자에게 통과했다("괜찮은 것 같은데"):
- 틀 예: `S\mock\r11\lib\photo|a|album\` (theme.css · layouts.json · layouts.html · NOTES.md). **형식·품질 기준은 이것들과 맞춘다.**
- AI 가 그 틀로 엮은 책 9권: https://claude.ai/artifact/Uwdyh6jqWavDCQtu7FWbFX
- AI 편집 지침 예: `S\mock\r11\engine\themes2\a.md`, `album.md`, `_common.md` (+ `a.json` 쪽 수 규칙)

## 운영자 피드백 (11-26, 10차를 보고) — 원문
- 기록카드: "카드들의 테두리 뭐야.. 촌스럽고 AI 같고 느낌도 없어" (10차 b 의 파란 이중 괘선 카드)
- 여행수첩/일기/잡지: "마찬가지 여백, 문구, 글꼴 등등 AI 티가 나. 특히 잡지의 8, 11, 12페이지"
- 전체: "여행 앨범·여행 책자를 진짜 꾸민다는 느낌으로 더 예쁘고 느낌 있고 여행을 추억할 수 있게. 글의 문구·사진 배치·디자인
  모두 AI 가 아니고 디자이너·여행작가·사진작가가 만든 것처럼."
- 10차 시안: https://claude.ai/artifact/98xNTJbuARueWAssrR2fCy , 파일 `S\mock\r10\<키>.html` + `NOTES-<키>.md`, 그때 리서치
  `S\refs10\<b|c|diary-album|a-mag>\GRAMMAR.md`.

## 할 일
1. **레퍼런스 더 보기 (필수, 짧게)**: 담당 장르의 실제 책 펼침면 20장 이상 새로(앱 UI 금지). 저장 `S\refs11\<키>\`, 4×3 시트로
   **Read 로 직접 본다**. `GRAMMAR11.md`(3KB 이하: 이 장르를 사람이 만든 책으로 보이게 하는 것 8개, 기록·글이 들어가는 실제 예 5개,
   AI 티 함정 6개, 레퍼런스 번호). 이미지는 Python urllib + 브라우저 UA(curl 막힘). **사이트의 봇 차단(Cloudflare·Anubis 같은
   확인 관문)은 풀거나 우회하지 마라** — 막히면 다른 출처로.
2. **틀 라이브러리** `S\mock\r11\lib\<키>\` — BRIEF11b 형식 그대로. 표지 3 · 날 여는 쪽 2~3 · 본문 14~18 · 글/끝 2~3.
   10차에서 운영자가 싫어한 것(위 피드백)은 버리고, 그 테마의 정체성은 레퍼런스로 다시 세운다.
3. **AI 편집 지침** `S\mock\r11\engine\themes2\<키>.md` (a.md·album.md 와 같은 꼴: 이 테마의 순서 짓기 + 글 문체 + 금지) 와
   `<키>.json` (`{"pages":[lo,hi],"min_distinct":n}`, 메모 비율이 필요하면 `"memo_ratio"`).
   글 문체는 반드시 그 장르의 실제 문장 예(출처)를 근거로. 긴 글은 "A 했고, B 했고, C 했다" 식으로 일정을 나열하지 않는다 —
   한 장면을 붙잡는다.
4. 미리보기 3바퀴 이상(`S\mock\r11\lib\preview.py <키>`), 사진 위 글 대비 4.5:1, 가장 작은 글 2.8cqw, 글 칸은 최대 글자 수로 넘침 시험.
5. 한국어 보고 10줄 + `lib\<키>\NOTES.md`(2KB 이하).
담당 테마 파일만 만든다. 다른 테마·앱 코드·engine2.py 는 건드리지 않는다.
