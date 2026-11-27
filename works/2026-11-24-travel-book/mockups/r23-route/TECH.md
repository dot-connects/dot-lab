# 다닌 길 지도 — 앱에서 어떻게 그리나

시안은 OSM 원본(Overpass)을 SVG로 직접 그림. Overpass는 앱용 아님.

| 방법 | 무료 | 유료 | 인쇄·내보내기 |
|---|---|---|---|
| Mapbox Static Images | 월 5만 건 | $1/1천 건 | 인쇄물 사용 금지, 별도 권리 구매 |
| Google Static Maps | 월 1만 건 | $2/1천 건 | 약관 미확인 |
| Stadia Static | 상업 사용 불가 | 크레딧제 | 유료 플랜에서 이미지당 5천 부 |
| MapTiler | 비상업·시험용 | $30/월~ | 무료엔 인쇄 권리 없음 |
| OSM 공식 타일 | — | — | 오프라인·미리 받기 금지 → 불가 |
| **자체 벡터 (Protomaps PMTiles + MapLibre)** | 데이터 ODbL | 저장비만 | 출처 표기만 하면 인쇄 가능(ODbL) |

## 권장: 자체 벡터
- OSM 기반 PMTiles 한 파일을 R2에 두고, WebView 안 MapLibre GL JS(또는 지금처럼 SVG)로 그림 → 스타일(옅은 육지·가는 선)을 마음대로, 책 해상도로 내보내기.
- 사진 위치에서 범위(bbox)를 구해 그 범위 타일만 받음. 책 1권 ≈ 타일 수십 개.
- 책 1권 비용 ≈ 0원. 고정비는 R2 저장비뿐(전 세계 파일 ~100GB대, 미확인).
- 위치 정보: 서버로 가는 건 "어느 범위 지도를 보는지"뿐이고, 사진·GPS 원본은 폰에 남음. 받는 곳도 우리 R2뿐, 제3자 없음.
- 인천→발리 위치 지도는 Natural Earth(퍼블릭 도메인)를 앱에 내장.
- 쪽 하단에 "© OpenStreetMap 기여자" 표기 필수.

## 미확인 (공식 문서 확인 못 함)
- Protomaps 전 세계 파일 크기·무료 다운로드 조건, `pmtiles extract`로 지역만 잘라내기.
- Google Static Maps 인쇄 약관, R2 저장 단가.
- 정지 장소 이름: 폰 지오코더가 렘봉안을 "Nusa Penida"(군 이름)로 줌 → 섬 이름은 OSM `place=island`에서 따로 찾아야 함.

출처(11-28 확인): 각 사 pricing 페이지, OSMF 타일 정책
