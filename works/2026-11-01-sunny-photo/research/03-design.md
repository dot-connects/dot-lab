# 03. 화면 디자인·흐름·보여주는 방식 조사

조사일 2026-11-01. 이미지 경로는 이 파일 기준 상대경로(`images/...`).
앱을 직접 설치해 쓰지 않았다. 관찰 근거는 세 가지이고 표마다 구분한다.

- **[스토어]** App Store 스크린샷: 회사가 고른 장면. 실제 흐름의 증거는 아니다.
- **[영상]** 튜토리얼·광고 영상에서 뽑은 30프레임 시트: 실제 화면과 흐름의 증거. 단 구버전일 수 있다.
- **[미확인]** 위 둘로 확인할 수 없는 것. 추측하지 않는다.

## 결론 먼저

1. **이 장르는 전부 "버튼을 누르면 하늘이 이미 바뀌어 있다"이다.** 확인한 앱 어디에도 하늘이 개는 과정을 보여 주는 앱이 없었다. PICNIC 도 4프레임/초 시트에서 필터 사이가 그냥 끊어 바뀐다. 전환 자체가 즐거움인 제품은 없다. 비어 있는 자리다.
2. **손은 고르기만 한다.** 썸네일을 누르고, 슬라이더를 끌고, 비교선을 민다. 하늘을 직접 닦거나 해를 끌어다 놓는 제품은 못 찾았다. 가까운 것은 앱 밖에 있다: 손으로 안개를 닦는 MediaPipe 데모, 손가락을 튕기면 날씨가 바뀌는 CapCut 영상.
3. **세로 분할선이 업계 공용어다.** 스토어 스크린샷·유튜브 썸네일의 기본 문법이라 안 쓰면 설명이 길어지고, 그대로 쓰면 다른 앱과 똑같아진다. 분할선을 "끌어서 비교하는 도구"가 아니라 다른 물건(창 닦기, 구름 걷기)으로 바꾸면 문법은 지키면서 달라진다.

## 1. 앱별 관찰

### 1-1. 하늘이 주 기능이거나 흐름을 영상으로 확인한 앱

| 앱 | 첫 화면 | 결과까지 탭 | 변화 보여주기 | 대기 표현 | 하늘 고르는 방식 | 결과·공유 | 근거 |
|---|---|---|---|---|---|---|---|
| **PICNIC** (ESTsoft, 흐림 해결이 핵심 메시지) | 구버전: 전면 그림에 갤러리·카메라 동그라미 2개. 현행: 기능 카드 홈, 맨 위 "AI Sky Filter / Clear cloudy sky" 큰 분할 카드, 우상단 AD 제거 배지 | 약 3~4 (갤러리 → 사진 → 필터 → 저장) [영상] | 구버전은 필터 탭으로 끊어 바뀜. 현행 스토어는 위아래 before/after 분할도 사용 | 어두운 빈 화면 잠깐, 사진이 뜨면 구름 마스코트와 "피크닉과 딱 어울리는 사진이네요!" 문구 | 가로 필터 줄, 색 사진 썸네일. 고르면 사진 위에 분위기 문장이 잠깐 뜸. 스토어 "33 Mood Filters". 강도 슬라이더 2개가 사진 위에 뜸 | 하단 X·다운로드·공유 3개. 저장 뒤 공유 시트 [영상] | `images/picnic-1.jpg`, `images/picnic-6.jpg`, `images/video-picnic-ad.jpg`, `images/burst-picnic-switch.jpg`, `images/video-picnic-mikyung.jpg` |
| **Lightleap** (Lightricks) | 사진 그리드 [영상] | 약 5~6 (사진 → 도구 줄 Sky → 카테고리 → 썸네일 → Export → 저장) [영상] | 스토어: 세로 분할 + 파란 미니 슬라이더. 영상: 썸네일 탭으로 바뀜 | [미확인] | 카테고리 탭 + 썸네일. 스토어의 썸네일 이름은 SU03, AU04 같은 코드. 세부 값은 숫자 슬라이더(-100~100) | Export 화면 → 체크 원과 "Success!" [영상] | `images/lightleap-2.jpg`, `images/video-lightleap.jpg`, `images/web-play-lightleap.jpg` |
| **Google 포토 Magic Editor** | 사진 뷰어에서 편집 진입 | 약 6~7 (편집 → Magic Editor → Sky → 스타일 → 결과 고르기 → 저장) [영상] | **누르고 있으면 원본, 떼면 결과.** 좌우로 넘겨 4장 비교 | Magic Editor 진입 시 원형으로 번지는 파랑→보라→빨강 전체 화면 전환. 생성 중은 사진이 어두워지고 "Generating…", 오른쪽에 다음 장 끝이 보임 | 사진을 분석해 Sky 칩 제안 → Golden hour / Stylized 2개 | 체크 → Save copy. "새 결과 받기"로 4장 추가 | `images/video-gphotos-sky.jpg`, `images/burst-gphotos-gen.jpg` (2024년경 영상으로 추정, 현행과 다를 수 있음) |
| **갤럭시 갤러리 생성형 편집** | 갤러리 사진 | 8회 이상. 지상 영역을 손가락으로 드래그해 선택, 위치 드래그, 생성 대기, 저장 [영상] | 저장된 결과만 봄 | 사진 위 반짝이 아이콘 + 안내문 | **내가 찍어 둔 하늘 사진을 갤러리에서 고른다.** "평소에 멋진 하늘 사진을 촬영해 저장" 안내 | 저장 시 갤러리로 | `images/video-galaxy-gallery.jpg` (생성형 편집을 우회해 쓰는 방법 영상) |
| **Picsart** | 스티커 검색 "sky"로 시작 [영상] | 10회 이상 (스티커, 지우개, 불투명도, 효과, 저장) | 공식 영상 앞부분에 세로 분할 | [미확인] | 구름 스티커를 사진에 얹고 인물을 지움. 완전 수동 | Save / Post, 해시태그 입력 | `images/video-picsart-official.jpg` (#StayHome 태그로 보아 2020년경) |
| **Luminar Mobile** | [미확인] | [미확인] | 세로 분할 | [미확인] | 가운데 크게 + 양옆 작게 나오는 하늘 캐러셀 | 스토어에 SHARE 버튼 | `images/luminar-3.jpg` |
| **Luminar 4 (데스크톱)** | 전문 편집 화면 | 데스크톱이라 제외 | 영상에서 하늘 프리셋이 끊어 바뀜 | [미확인] | 드롭다운 프리셋 | - | `images/video-luminar4.jpg` |

### 1-2. 전용 소형 앱: 스토어 문법만 확인 [스토어]

흐름·유료벽은 전부 [미확인]. 평가 수가 0~111로 작아 시장 표준이 아니라 "장르가 어떻게 자기를 파는지"의 표본으로만 본다.

| 앱 | 보이는 것 | 눈여겨볼 점 | 근거 |
|---|---|---|---|
| Infinite Skies | 라일락빛 구름 배경, 세로로 긴 축소 글꼴, 하단 글자 탭 | "250+ SKIES", "Create animated videos" | `images/infinite-skies-1.jpg`~`6` |
| SkyPic (Sky Photo Editor) | 같은 사진·같은 구도를 해·석양·번개·눈·달로 반복, 폰 위에 날씨 그림 | Sunny / Cloudy / Storm / Sunset 글자 탭 + 번호 썸네일(19, 20, 21). **날씨를 카테고리로 쓴 드문 사례.** Overlay / Element 탭에 비·눈 파티클 | `images/sky-photo-editor-2.jpg`~`6` |
| Sky Replacement AI | 부채꼴로 여러 하늘을 한 인물 뒤에 펼침. Night to Day / Day to Night 표시 | **타임랩스 영상 메뉴가 있음.** 전환을 결과물로 파는 드문 경우. 실제 품질은 미확인 | `images/sky-replacement-ai-1.jpg`, `-3.jpg`, `-5.jpg` |
| Sky Changer AI | 진보라 다크 UI, 이름 붙은 하늘 칩(Fluffy Clouds, Cumulus, Bright Midday), 프롬프트 입력칸, Generate 버튼 | 스크린샷상 흐름은 사진 → 칩 → 생성 → 저장 | `images/sky-changer-ai-5.jpg`, `-6.jpg` |
| AI Sky Replacement | 다크 + 인물 뒤 띠 분할, 원형 하늘 칩 | "Moving Clouds" 효과 판매 | `images/ai-sky-replacement-1.jpg`~`6` |
| SkyReplacer | 어두운 파랑, 슬라이더가 많음 | 색 맞추기(Match the colors)를 전면에 | `images/sky-replacer-1.jpg`~`5` |
| SkyAR | 아래 아이콘 줄만 있는 가로 화면, 달·행성·UFO | 판타지 쪽 | `images/skyar-1.jpg`~`6` |
| SkyLab | 흑백 표지 + 초록 버튼 | 하늘 띠 썸네일 | `images/skylab-1.jpg`~`6` |
| 일몰 사진 (SunSet Lab) | 스크립트 로고 + After/Before | Sky Color Tuner 슬라이더 | `images/korean-sky-1.jpg`~`6` |
| 우리하늘 | 흰 구름 얼굴 마스코트, 파란 배경. 사진 편집이 아니라 동네 하늘 기록·공유 앱 | **편집 없이 "오늘의 하늘"을 모은다.** 구름 캐릭터의 친근함 | `images/new-kr-1.jpg`~`6` |
| Weathershot | 사진 위에 지명·기온 스킨(Norway 23°C) | **사진을 날씨 카드로 꾸미기.** 결과물이 찍힌 기록처럼 보임 | `images/weathershot-1.jpg`~`6` |

### 1-3. 대형 범용 앱: 스토어에서 하늘이 보이는지 [스토어]

| 앱 | 하늘 노출 | 스토어 문법 | 근거 |
|---|---|---|---|
| Photoleap | 없음(AI 효과 분할 예시로 빅벤 하늘) | 상단 글자 + 세로 분할 + Before/After 알약 + 가운데 핸들 | `images/photoleap-2.jpg` |
| Motionleap | "움직이는 하늘" 1장 | 분할 + 주황 슬라이더. **구름이 움직이는 사진.** 한 장을 움직이게 하는 쪽 | `images/motionleap-3.jpg` |
| Remini | 없음 | 파스텔 단색 + 굵은 글자 + 폰 + 곡선 화살표 + 작은 원본 사진. 세로 분할 + 핸들 | `images/remini-1.jpg`, `-4.jpg` |
| Darkroom | 없음 | 그라디언트 + 폰, 전문가 도구 강조 | `images/darkroom-1.jpg` |
| Picsart | 없음 | 세로 분할 + 핸들 | `images/picsart-3.jpg` |
| EPIK, SNOW, B612, Meitu, Wink, Lensa | 없음. 얼굴·피부·AI 프로필 위주 | 밝은 단색 배경 + 인물 큰 사진 + 짧은 글자. Wink 만 검정 | `images/snow-1.jpg`, `images/b612-3.jpg`, `images/wink-2.jpg` |
| LightX | 없음 | 흰 배경 + 굵은 대문자 | `images/lightx-1.jpg` |
| Google 포토 | 스토어엔 없음 (지우기만) | 폰 그대로 + 색 강조 글자 | `images/google-photos-2.jpg` |

한국 사용자가 많은 앱은 얼굴 위주이고, 스토어에서 **하늘을 팔지 않는다**. 하늘을 전면에 놓은 앱은 사실상 PICNIC, Lightricks 계열, 소형 전용 앱이다.

### 1-4. 영상·광고에서 본 것

| 자료 | 관찰 | 출처 / 이미지 |
|---|---|---|
| 유튜브 첫 시트 30개 | 하늘 주제 썸네일의 약 18/30이 좌우 또는 3분할 before/after(육안 계산). 화살표 + 앱 아이콘 + "1 CLICK" 조합 | `images/yt-contact-1.jpg` |
| 한국어 썸네일 문구 | "날씨때문에 망한 여행 사진", "오늘 하늘 이래도 되는거?", "사진을 못 찍으면 만들어!", "클릭 한번으로 하늘 교체". 민가든 영상은 "흐린날씨보정"을 쓰고 자막에 "하늘이 우중충한데"가 나옴 | https://youtu.be/r9SQM9Ch9ac , https://youtu.be/J671XOedbAQ , https://youtu.be/nvhkjts35to , https://youtu.be/ahlJQKzCSyE · `images/yt-contact-2.jpg`, `images/yt-contact-3.jpg`, `images/video-mingarden-travel.jpg` |
| 뷰 규모 | 민가든 여행사진 보정 38만, 앱 전용 튜토리얼은 수백~수천. 하늘 교체는 단독 주제보다 "여행사진 보정"의 일부로 소비된다 | 위 링크, https://youtu.be/vwYGU_ytGEI |
| PICNIC 사용법 영상 | 흐린 놀이공원 사진에서 필터를 눌러 파란 하늘로. 구버전 UI | https://youtu.be/vwYGU_ytGEI · `images/video-picnic-filmanja.jpg` |
| Meta 광고 라이브러리 (로그인 없이 열림) | 하늘 전용 앱 광고는 못 찾음. 잡힌 것은 Luminar 뿐이고 내용은 "sky replacement"를 문구로만 언급하는 할인 광고. 소재는 노트북+폰 또는 RAW / WOW 세로 분할 | `images/web-meta-luminar-1.jpg`, `images/web-meta-luminar-3.jpg`, 라이브러리 ID 846233274551398 등 |

## 2. 전환·손 상호작용 참고

앱 밖에서 찾았다. 그대로 베낄 것이 아니라 "이런 감각이 이미 통한다"의 근거다.

| 자료 | 관찰 | 작품에 주는 힌트 | 출처 / 이미지 |
|---|---|---|---|
| Clouds Clearing Transition (스톡 전환) | 구름 덩어리가 화면을 덮고 지나가며 다음 장면이 드러남. 약 3~4초 | 구름이 "가림막" 역할. 가리는 동안 AI 결과로 바꿔치기할 수 있다 | https://youtu.be/1EvytEkWIls · `images/video-clouds-clearing.jpg` |
| Foggy Screen Wiping (MediaPipe) | 화면 전체가 뿌옇고, 카메라에 비친 손이 지나간 자리만 또렷해짐. 가장자리가 부드러움 | **닦기 감각의 가장 가까운 사례.** 다만 데모이고 조회 102, 제품 아님 | https://youtu.be/IKakB1448xE · `images/video-fog-wipe.jpg` |
| Weather snap CapCut | 맑은 사진 앞에 손이 나타나 튕기자 먹구름으로 바뀜. 날씨를 손이 일으키는 연출 | 한 번의 몸짓 = 날씨 변화. 방향만 반대로 | https://youtu.be/3cHFbr0mP9A · `images/video-weather-snap.jpg` |
| Rainy Glass 앱 | 카메라·사진 위에 빗방울 유리를 씌움. 빗방울이 얹힌 채 뒤가 흐림 | 유리창이라는 물건을 UI로 쓰는 전례 | https://youtu.be/mAc67ehXfZE · `images/video-rainy-glass.jpg` |
| Freepik AI Relight | 빛 스타일 썸네일 + 조명 위치 패널(위·앞·좌우). 데스크톱 도구 | 해 위치 조절의 기술 쪽 전례. 폰에서 끌어 놓는 손맛은 없음 | https://youtu.be/quLtKONAl6s · `images/video-ai-relight.jpg` |
| iPhone 날씨 앱 애니메이션 | 날씨별 배경. 프레임만 봐서 움직임은 판단 못 함 | 파랑 그라디언트+흰 큰 숫자가 날씨의 시각 기억 | https://youtu.be/ybaUmkUSTDA · `images/video-ios-weather.jpg` |
| Google 포토 원형 전환 | Magic Editor 진입에서 색이 원형으로 번짐 | 대기를 전환으로 바꾸는 큰 회사의 예 | `images/burst-gphotos-gen.jpg` |

Motionleap, AI Sky Replacement 의 "움직이는 구름"은 하늘을 **움직이는 결과물**로 판다. 흐림이 맑음으로 바뀌는 과정을 파는 것과는 다르다.

## 3. 반복되는 문법

| 항목 | 굳어진 문법 | 비고 |
|---|---|---|
| 스토어 1장 | 사진 전체 + 세로 분할 + 상단 큰 글자 한 줄 | Lightleap, Photoleap, PICNIC, Luminar |
| 예시 사진 | 뒷모습 인물이 풍경을 봄. 모자, 호수, 산, 도시 | 뒷모습은 합성 경계와 얼굴 문제를 피한다 (해석) |
| 하늘 고르기 | 하단 가로 썸네일 줄, 위로 카테고리 글자 | 거의 전부. 이름은 코드, 번호, 분위기 말 중 하나 |
| 색 | 어두운 편집 화면 + 하늘색 강조, 또는 진보라 | PICNIC 만 하늘색·흰색 밝은 톤 |
| 비교 | 세로 분할선 > 누르고 있기 > 위아래 분할 | 누르고 있기는 Google 포토에서 확인 |
| 대기 | 소형 앱은 Generate 버튼. 구체 표현은 대부분 미확인 | 대기를 쇼로 만든 곳은 Google 포토 정도 |
| 마무리 | 체크 → 저장 → 공유 시트 | 결과 자체를 공유용 영상으로 내는 사례는 미확인. Sky Replacement AI 타임랩스가 가장 가까움 |
| 말투 | 영어권 "Replace, Transform, Magic", 한국어 "망한, 우중충, 인생샷, 한 번에" | 한국은 실패 감정에서 출발 |
| 손 | 탭 + 슬라이더 | 드래그는 분할선과 영역 지정에만 |

## 4. 아무도 안 하는 것

1. **개는 과정을 본다.** 흐림에서 맑음까지의 시간 자체를 보여 주는 제품이 없다.
2. **손으로 하늘을 개게 한다.** 닦기·밀기·불기 없음.
3. **빛이 땅까지 내려온다는 연출.** 하늘만 바뀌는 앱이 많고, 색 맞춤은 슬라이더로 해결한다. 그림자와 햇빛이 풍경을 쓸고 지나가는 전환은 못 봤다.
4. **해 위치를 사진 위에서 끌어 놓기.** 데스크톱 패널은 있어도 폰 직접 조작은 못 봤다.
5. **결과물이 전환 영상.** 공유물은 한 장의 사진이다. "before/after 3초 클립"을 기본 출력으로 주는 앱을 확인하지 못했다.
6. **그날의 실패를 이름 붙이기.** 우중충했던 날이라는 사연을 결과물에 남기는 경우가 없다. 분위기 필터 이름은 있어도 "그날"이 없다.
7. **그 장소의 실제 맑은 날.** 갤럭시 방식은 내 하늘 사진을 쓰지만, 다른 앱은 일반 하늘이다. 기술적 가능 여부는 기술 조사 쪽 판단.

## 5. 흐름·상호작용 방향 6가지

공통: 첫 화면은 사진 고르기 한 번으로 시작한다. 팔레트·배치는 위 앱 어느 것도 따르지 않고, 작품이 받은 **사진 자체의 색**을 화면 색으로 쓰는 쪽이 자연스럽다. 전부 AI 결과를 미리 받아 두고, 손이 드러내는 구조라 대기 시간과도 맞다: 손을 움직이는 동안 결과가 도착하면 그때 닦여 나오면 된다.

| # | 방향 | 손이 하는 일 | 흐름 | 가져온 문법 | 위험 |
|---|---|---|---|---|---|
| A | **창 닦기** | 사진이 뿌옇게 김 서린 유리 뒤에 있다. 손가락으로 닦는 자리만 맑은 하늘이 드러남. 놓아 두면 다시 서서히 김이 낌 | 사진 고름 → 유리에 김이 서림 → 닦기 → 일정 비율 넘으면 유리가 통째로 맑아짐 → 저장 | 분할선 비교를 닦기로 변환. 안개 데모의 부드러운 가장자리 | 닦는 행위는 흐린 하늘이 아니라 유리에 작용한다. 의미가 약간 어긋날 수 있다 |
| B | **구름 밀어내기** | 구름 층을 손가락으로 쓸면 구름 조각이 밀려 나가고 사이로 파란 하늘과 빛이 샘 | 사진 고름 → 사진 속 흐린 하늘이 뭉게구름 층으로 변함 → 밀기 → 빛이 땅으로 번짐 → 끝 | 구름이 가림막이 되는 스톡 전환 | 구름 파티클 품질. 많이 밀어야 끝나면 피로 |
| C | **해 끌어오기** | 화면 가장자리에서 해를 끌어 사진 안에 놓는다. 위치에 따라 빛 방향과 그림자가 달라지는 것처럼 보임 | 사진 고름 → 구석에 해 → 끌기 → 놓은 자리부터 원형으로 맑음 번짐 → 저장 | Google 포토 원형 전환, Relight 패널을 손 하나로 축약 | 빛 방향을 실제로 맞추려면 기술 부담이 크다. 원형 번짐 눈속임이 가능한지 기술 검토 필요 |
| D | **숨 불기** | 마이크에 후 불면 불어 넣은 만큼 구름이 흩어짐. 손가락 쓸기를 대체 경로로 둠 | 사진 고름 → "후 불어 보세요" → 구름 흩어짐 → 맑음 | 없는 문법. 화면을 건드리지 않고 몸을 쓰는 한 번의 동작 | 마이크 권한, 공공장소 부담. 인앱 브라우저에서 막힐 수 있다 |
| E | **시간 되감기 다이얼** | 큰 원을 돌리면 그날 하루가 흐름에서 맑음으로 타임랩스처럼 흐름. 앞뒤로 돌려 마음에 드는 순간에 멈춤 | 사진 고름 → 다이얼 돌리기 → 원하는 시각에서 정지 → 사진 + 3초 클립 저장 | Sky Replacement AI 타임랩스, SkyPic 날씨 시리즈 | 중간 상태 여러 장이 필요. 비용과 품질 일관성은 기술 쪽 확인 필요 |
| F | **한 번의 스냅, 그리고 쇼** | 조작 최소. 한 번 튕기거나 탭하면 구름이 지나가며 빛이 땅을 쓸고 맑아지는 5초 쇼. 끝나면 그 쇼가 그대로 공유 클립 | 사진 고름 → 탭 → 쇼 → 저장 | CapCut 스냅 영상, PICNIC 의 최소 탭 | 손이 하는 일이 적어 lab 다시 하고 싶은 힘이 약할 수 있다. 대신 릴스 소재로 가장 강함 |

**내 판단:** 손맛과 뜻이 가장 맞는 것은 **B(구름 밀어내기)**다. 밀어낸 대상이 진짜 원인인 구름이고, 도착하는 빛이 보상이다. A는 구현이 가장 쉽고 첫 시연이 안정적이라 예비안이다. 어떤 방향이든 끝에 **F의 before→after 클립을 출력**으로 붙이면 "아무도 안 하는 것" 5번을 함께 얻는다. 필터 이름 대신 "우중충했던 그날" 같은 사연 문구를 쓰면 6번도 붙는다.

## 6. 확인 못 한 것

- **Reels, TikTok:** 인스타그램 태그는 로그인 화면으로 넘어감. TikTok 태그는 오류 화면(`images/web-tiktok-tag.jpg`). TikTok Creative Center 는 열리지만 무관한 팝업과 광고만 잡혔다(`images/web-tiktok-cc.jpg`). 같은 장르·같은 플랫폼 기준의 릴스 표본은 **없다.** 이 문서의 영상 근거는 유튜브뿐이다.
- **Google Play:** Lightleap 상세는 확인(`images/web-play-lightleap.jpg`). PICNIC 은 패키지명을 처음에 잘못 넣어 실패했고, 실제 ID는 `com.estsoft.picnic`. 스토어 이미지는 iOS 로 대체.
- **유료벽 위치, 워터마크:** 실제 설치를 안 해 거의 미확인. PICNIC 홈의 AD 제거 배지, Sky Changer AI 의 크레딧 표시로 보이는 숫자 정도만 봤다.
- **구버전 위험:** PICNIC 2개, Picsart, Google 포토 영상은 몇 해 된 화면이다. 탭 수는 그때 기준이다.
- **프레임 시트의 한계:** 움직임 평가는 대부분 10초 이상 간격의 프레임이다. PICNIC 과 Google 포토만 3~4프레임/초로 확인했다. "전환 애니메이션 없음"은 이 범위의 관찰이고, 스토어 영상 전수 검사는 아니다.
- **사용 안 한 이미지:** `images/web-meta-0.jpg`~`5` 등 전체 페이지 캡처는 근거 검색용이다.
- **용량:** `images/` 약 75MB, 199개. dot-lab 에 커밋하면 저장소가 커지므로 `research/images`는 커밋 제외를 권한다.

## 부록: 앱 스토어 링크

| 앱 | App Store |
|---|---|
| PICNIC | https://apps.apple.com/us/app/picnic-ai-sky-filter-enhancer/id1236058595 |
| Lightleap | https://apps.apple.com/kr/app/id1254875992 |
| Photoleap | https://apps.apple.com/kr/app/id1191337894 |
| Motionleap | https://apps.apple.com/kr/app/id1381206010 |
| Luminar | https://apps.apple.com/kr/app/id6476109261 |
| Infinite Skies | https://apps.apple.com/us/app/infinite-skies-photo-editor/id1557797120 |
| Sky Replacement AI | https://apps.apple.com/kr/app/id6766159729 |
| SkyAR | https://apps.apple.com/kr/app/id1582408968 |
| Sky Changer AI | https://apps.apple.com/kr/app/id6753913535 |
| AI Sky Replacement | https://apps.apple.com/kr/app/id6477859497 |
| SkyPic (Sky Photo Editor) | https://apps.apple.com/kr/app/id1629318957 |
| SkyReplacer | https://apps.apple.com/kr/app/id6768034446 |
| SkyLab | https://apps.apple.com/kr/app/id1084234960 |
| 일몰 사진 | https://apps.apple.com/kr/app/id1457318728 |
| 우리하늘 | https://apps.apple.com/kr/app/id6815455706 |
| Weathershot | https://apps.apple.com/us/app/weathershot-instaweather/id564147490 |
| Darkroom | https://apps.apple.com/us/app/darkroom-photo-video-editor/id953286746 |
| LightX | https://apps.apple.com/kr/app/id1400740705 |
| Picsart | https://apps.apple.com/kr/app/id587366035 |
| EPIK | https://apps.apple.com/kr/app/id1577705074 |
| Meitu | https://apps.apple.com/kr/app/id416048305 |
| Wink | https://apps.apple.com/us/app/wink-video-enhancer-editor/id1594288016 |
| Lensa | https://apps.apple.com/kr/app/id1436732536 |
| Google 포토 | https://apps.apple.com/kr/app/id962194608 |
| SNOW | https://apps.apple.com/kr/app/id1022267439 |
| B612 | https://apps.apple.com/us/app/b612-ai-photo-video-editor/id904209370 |
| Remini | https://apps.apple.com/us/app/remini-ai-photo-enhancer/id1470373330 |

Google Photos, Galaxy 영상 출처: https://youtu.be/a64qvIFn7nk , https://youtu.be/ot4pqHcZrAc . Lightleap: https://youtu.be/pMvTrQQRE5Q . Luminar 4: https://youtu.be/0eA6dUECpAA . Picsart: https://youtu.be/N6323pfLPRw .
