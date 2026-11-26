# mag 잡지 — 11차 d (25틀)
10차 진단: 8쪽=17cqw 굵은 초록 고딕 구호 + 위 1/3 빈칸 + 구석 작은 사진. 11쪽=번호 원·점선·축척·잘린 목록. 12쪽=초록 전면 + 큰 구호 + 표 양식 판권. 공통: Gmarket 디스플레이 고딕.
새 바탕: 종이+잉크, 영문·숫자 Instrument Serif(앱 내장), 한글 Pretendard만. 격자 옆 7·위 9·아래 11, 6단. 지도·숫자 쪽 없음.

| 틀 | 모양 | 근거(raw) |
|---|---|---|
| c-mast | 세로 꽉 + 자간 넓은 세리프 제호 + 아래 제목·세 줄·날짜 | 10차 ① |
| c-window | 회색 톤, 작은 제호, 세로 사진 창 | Cereal 33 |
| c-frame | 큰 세리프 제호 + 테두리 사진 | Travel Almanac(10차 38) |
| toc | 위 가로 사진 + Contents + 번호·꼭지·쪽 | 새 |
| letter | Editor's Letter 한 단 | 새 |
| op-spread | 가운데 축: 꼭지명·제목·4:5 사진·리드·크레딧 | Openhouse 22·20·25 |
| op-bleed | 세로 꽉 + 아래 글 | 10차 |
| op-half | 위 반 사진 + 제목·리드·첫 문단 | Kinfolk 01·04 |
| bleed / plate | 꽉 / 여백 안 세로 + 캡션 | — |
| stack / strip / mosaic | 가로 둘 / 가로 셋 띠 / 가로 1 + 세로 2 | 매거진 B 17 |
| grid4 | 머리말 + 정사각 넷 + 번호 캡션 | POPEYE·매거진 B |
| triptych | 머리말·문단 + 세로 셋 | 새 |
| wide-text / text-photo / pair-text | 사진 + 2단 본문 | Openhouse 26 |
| detail | 가로 + 짧은 글 + 디테일 | 새 |
| subhead | 소제목·리드 + 아래 블리드 | Apartamento 09 |
| quote / quote-side | 본문 서체 인용 + 사진 반 / 세로 사진 옆 | Gentlewoman 10·12 |
| text2 | 사진 없는 2단 한 쪽 | Kinfolk 05 |
| colophon / back | 사진 + 작은 제호 + 사실 3줄 / 뒤표지 | 10차 12쪽 대체 |

확인: `pv.py --max` + `fit.py` = 모든 칸 최대 글자 넘침·겹침 0, 최소 글 2.8cqw. 사진 위 글 대비 최저 4.82(textcontrast).
본문 왼끝 맞춤. 약점: kicker·mast 는 영문 전제. 쪽 머리줄 없음(쪽 번호만, data-page).
