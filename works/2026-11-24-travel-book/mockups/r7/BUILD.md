# 7차 구현 지시서 (테마 디자이너)

S = C:\Users\ehdck\AppData\Local\Temp\claude\c62cf31192c54cdd\50332936-9748-4893-ad04-c354d2294c91\scratchpad

너는 테마 하나를 맡은 에디토리얼 디자이너다. 패널 토론 결과 `S\refs7\<키>\DIRECTION.md` 를 HTML 시안으로 만든다.
6차를 손보는 게 아니라 DIRECTION 대로 **새로 짠다**(쓸 만한 CSS 조각은 가져와도 됨).

## 먼저 읽기
`S\refs7\<키>\DIRECTION.md`, `NOTES.md`, 레퍼런스 `sheet*.jpg`(Read 로 보기), 형식·규칙 `S\mock\r5\BRIEF.md`(형식 절·사진 주의),
`S\mock\r5\PRO.md`(디자이너 체크리스트 — 그대로 유효), 지금 파일 `S\mock\r5\<키>.html`(잡지는 `mag.tpl.html`).

## 형식 (깨지면 안 됨)
- 파일: `S\mock\r5\<키>.html` (잡지는 `mag.tpl.html` 수정 후 `python mag_build.py`). 고치기 전 판은 `S\refs7\<키>\v6.html` 로 복사해 둔다.
- 1번째 줄 `<!-- fonts: Google Fonts 라틴/모노 -->`, 2번째 줄 `<!-- kfonts: 한글 글꼴:굵기;굵기 -->`(fonts.py FAMILIES 에 있는 것만, 쓰는 굵기만).
- `<style>` 하나, 모든 선택자 `.t-<키>` 로 시작. `<section class="t-<키>" data-palettes='[...]'>` 안에 `.head`(h2·p.idea·button.shuffle),
  표지 후보 `.strip` 3쪽 + `.under`, 책 본문 `.strip` 9~11쪽 + `.under`, `details.notes`(디자인 규칙 6~10줄, 수치 포함).
- 껍데기 `.strip` `.pg`(3:4) `.play` `.under` 크기·비율 바꾸지 말 것. 이미지는 `<img src="{{키}}" alt="">`, 실제 있는 파일만(`S\mock\img5`).
  같은 사진 한 권에 한 번(표지·영상 프레임 제외). 사진 filter 금지(원본 색). 본문 11px·캡션 9.5px 미만 금지.

## 확인 (최소 3번 렌더, 실제로 보고 고친다)
```
cd S\mock\r5
C:/DC/DD/content-system/.venv/Scripts/python.exe preview.py <키>
C:/DC/DD/content-system/.venv/Scripts/python.exe pages.py --src=out-<키>.html <키>
```
`sheet-<키>.jpg` 와 쪽별 `pp-<키>-N.png` 를 Read 로 크게 본다. 묻기: "이 쪽만 떼어 인스타에 올려도 디자이너 작업처럼 보이나?
빈 곳이 의도로 보이나 실수로 보이나? 레퍼런스 옆에 놓으면 부끄럽지 않나?"
다른 테마 파일·preview.py·pages.py·fonts.py·shell.html 수정 금지. travel-book-themes-r5.html 만들지 않는다.

## 비평 받기
끝나면 보고한다. 이후 비평가(Fable)의 의견이 메시지로 오면, 동의하는 건 고치고 동의 안 하는 건 이유를 한 줄로 답한다(토론).

## 보고 (짧게, 한국어)
DIRECTION 에서 그대로 한 것 / 바꾼 것(이유), 쪽 구성 한 줄씩, 남은 약점 하나.
