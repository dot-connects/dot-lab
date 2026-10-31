# 01. 앱·시장 전수조사와 성패 분석 — 흐린 사진을 맑게

조사일: 2026-11-01. 범위: 하늘·날씨·조명을 바꾸는 제품의 목록과 성패. 기술·시각 디자인은 다른 문서가 맡는다.

## 먼저 결론

1. **이 시장의 진짜 레퍼런스는 한국 앱 피크닉(PICNIC)이다.** 이스트소프트가 2017년에 낸 하늘 전용 앱으로, 2019년에 1,000만 다운로드를 넘었고 앱스토어 평점은 지금도 4.87이다. 리뷰에 적힌 쓰임은 편집이 아니라 위로다: "날씨가 망한 날을 살려주는 앱".
2. **피크닉의 약점이 곧 이번 작품의 빈자리다.** 호평은 하늘 쪽에, 불만은 사람 쪽에 몰린다. 파란 옷이 구름으로 바뀌고, 사람이 끼면 어색하다. 사람·건물의 빛까지 바꾸는 시도는 이 틈을 정확히 찌른다.
3. **무너지는 시점은 거의 항상 공짜를 거둘 때다.** 경쟁 앱 Lightleap은 2021~2022년 "무료였던 하늘이 유료가 됐다"는 불만과 함께 글 리뷰 평균이 2.2로 내려앉았다(유료화 정확한 시점은 못 찾음). 피크닉도 2024년부터 광고·워터마크 불만이 늘며 4.81에서 4.02로 떨어졌다.
4. **날씨 고치기가 독자적인 바이럴 유행이 된 증거는 못 찾았다.** 지브리·나노바나나 같은 폭발은 "나를 바꾸는" 쪽에서 나왔다. 날씨 보정은 조용한 입소문형이다.
5. **챗GPT가 이미 공짜로 해 주지만 원본을 해친다.** 한겨레 기고는 빛과 그림자까지 만들어 줬다고 쓰면서, 원본을 해치고 선명도가 떨어졌다고 함께 적었다. 프롬프트 없이, 원본을 지키고, 간직할 물건을 주는 쪽이 lab의 자리다.

## 근거 표시 규칙

- **[증거]** 공개 수치·리뷰 원문·발표. **[추정]** 제3자 추정치(Appfigures, Sensor Tower, Apptopia, AppGoblin 등). 회사 실적이 아니다. **[추론]** 내 해석.
- App Store 평점·평가 수·갱신일은 [iTunes Search API](https://itunes.apple.com/search?term=sky&country=kr&entity=software)로, Google Play 설치 구간·평점은 Play 상세 페이지에서 2026-11-01에 직접 받았다. 표의 "KR/US"는 그 나라 스토어 값이다.
- 리뷰 표본은 [Apple 공개 RSS](https://itunes.apple.com/kr/rss/customerreviews/id=1236058595/sortby=mostrecent/json)(나라별 최근 최대 500건)와 Play 내부 API(비공식, 앱당 소량)로 받았다. 글 리뷰는 불만 쪽으로 치우치므로 스토어 별점보다 낮게 나온다. 피크닉·Lightleap은 표본을 직접 읽었다.
- 찾지 못한 것은 "못 찾음"으로 적었다.

---

## 1. 전수조사 표

### 1-1. 하늘·날씨 전용 앱

| 제품 / 제작사 | 플랫폼 | 기능과 실행 방식 | 가격 | 사용 증거 | 사용자 반응 |
|---|---|---|---|---|---|
| **PICNIC 피크닉** / 이스트소프트(한국) | iOS, Android | AI가 하늘을 찾아 33종 필터를 입힘. 촬영 중 또는 사진 선택 후 탭, 강도 조절 가능 ([앱스토어](https://apps.apple.com/kr/app/id1236058595)) | 무료+광고. 광고 제거 월 ₩1,500·연 ₩14,900 | KR 4.87(24,317), JP 4.71(20,007), US 4.84(4,398), CN 4.89(4,811). Play 10M+, 4.6(233,736). 2M(2018-07, [보안뉴스](http://www.boannews.com/news/articleView.html?idxno=71126)) → 5M(2019-06, [뉴시안](http://www.newsian.co.kr/news/articleView.html?idxno=35516)) → 10M(2019-11, [한경](https://www.hankyung.com/article/2019112515811), 본문 차단으로 검색 발췌). [추정] 설치 14.4M, MAU 188K, 월 $10K+, 광고 77%·결제 23% ([AppGoblin](https://appgoblin.info/apps/1236058595)) | 2절 참고 |
| **Lightleap** (구 Quickshot) / Lightricks(이스라엘) | iOS, Android | 하늘 도구 60종+, 탭 한 번 교체 후 브러시로 다듬음 ([앱스토어](https://apps.apple.com/us/app/id1254875992)) | 구독. 인앱 $3.99~$199.99 | US 4.72(98,640), KR 4.60(2,127). Play 10M+, 4.0(66,668) | 2절 참고 |
| **Luminar Mobile** / Skylum | iOS, Android | 범용 편집 앱 안의 하늘 교체 | 구독. 주 $4.99 청구 언급 리뷰 있음 | US 4.76(4,746), KR 4.77(255) | 글 리뷰 243건 중 72건이 결제 불만, 무단 청구 호소 다수 ([RSS](https://itunes.apple.com/us/rss/customerreviews/id=6476109261/sortby=mostrecent/json)) |
| **Sky Changer** / Vyro AI | Android | 하늘 교체 | 무료+광고 | Play 100K+, 3.8(737), 마지막 갱신 2024-05-30 | 1점 76건이 "광고뿐" 유형: "it just gives you ads and in the end you cant do anything" |
| **PicNic Sky Photo Filter Editor** (피크닉과 무관한 유사 앱) | Android | 하늘 필터 | 못 찾음 | Play 500K+, 3.8(1,160) | 이름을 빌린 복제가 나올 만큼 카테고리가 정착했다는 신호 [추론] |
| **SkyPic** / YAWAI | iOS | 하늘 교체, Sky Glow, relight ([앱스토어](https://apps.apple.com/us/app/id1629318957)) | 무료+결제 | US 4.5(256) | RSS 7건 중 6건이 1점. Lightleap 리뷰에도 "wasted my money on Skypic" |
| **SkyLab** / James Grote | iOS | 하늘 편집 | $4.99 유료 | US 3.53(32), 2016년 출시 | 유료 선결제 모델은 평가 32건에 그침 |
| **Sky Replacement** / Quicksilver | iOS | 하늘 교체 | 무료 | US 3.48(23), 마지막 갱신 2018-09 | 사실상 방치 |
| **SkyAR** / 개인 개발자 | iOS | 지평선 인식 후 교체 | 무료 | US 평가 5건, KR 0건 | 표본 없음 |
| **AI Sky Replacement, Sky Changer AI, Sky Replacement AI, Sky Replacer** / 개인 개발자들 | iOS | AI 하늘 템플릿 | 무료+결제 | 평가 0~11건 (2024~2026 출시) | 생성형 시대에도 새 전용 앱이 자리 잡지 못함 [추론] |
| **Weathershot / InstaWeather** / BYSS | iOS, Android | 사진에 날씨 정보 스티커. 하늘 변경 아님, 인접 사례 | 무료+결제 | US 4.41(1,038), Play 500K+, 3.4 | 글 리뷰 평균 2013년 4.37 → 2018~2022년 약 2 |

### 1-2. 범용 편집기

| 제품 / 제작사 | 하늘 기능 | 가격 | 사용 증거 |
|---|---|---|---|
| Photoshop 데스크톱 / Adobe | 하늘 대체. 생성 후 빛·그림자 자동 조정 ([Adobe](https://www.adobe.com/uk/products/photoshop/sky-replacement)) | 구독 | 모바일 앱 US 4.46(95,058). 데스크톱 사용량 못 찾음 |
| Lightroom Mobile / Adobe | 교체 없음. Adaptive: Sky 프리셋과 하늘 마스크만 ([Adobe 커뮤니티](https://community.adobe.com/questions-680/how-do-i-do-sky-replacements-on-ipad-925639), [Photofocus](https://photofocus.com/mobile/mobile-mondays-lightroom-mobile-adaptive-presets-give-quick-results-with-ai/)) | 프리미엄 | US 4.77(338,964), Play 100M+ |
| Luminar Neo / Skylum | 대표 기능. 전경 재조명으로 평가받음 ([Pop Photo](https://www.popphoto.com/how-to/use-ai-to-replace-a-sky/), [PetaPixel](https://petapixel.com/2020/11/09/photoshop-vs-luminar-4-which-performs-sky-replacements-better)) | 구독/영구 | 못 찾음 |
| Picsart | 2026 모음 글에 하늘 앱으로 언급. 전용 기능 확인 못 함 ([근거 글](https://weedit.photos/?p=41425)) | 구독 | Play 1B+, 3.9(12.1M). KR 4.39(67,702) |
| Photoleap / Lightricks | AI 배경으로 흐린 하늘을 노을로 교체한 후기 ([Cutout.pro](https://www.cutout.pro/learn/photoleap-by-lightricks)) | 구독 | US 4.68(138,218), KR 4.64(2,244) |
| YouCam Perfect / Perfect Corp | 자동 전경·하늘 분리 ([제작사 글](https://www.perfectcorp.com/ko/consumer/blog/photo-editing/skys-the-limit-best-app-for-dreamy-sky-editing)) | 구독 | Play 100M+, 3.9(2.05M) |
| BeautyPlus / Meitu 계열 | 흐린 사진용 하늘 교체 ([제작사 글](https://www.beautyplus.com/how-to-edit-cloudy-photos-or-bad-weather-images)) | 구독 | Play 500M+, 4.5(5M) |
| CapCut 계열 Dreamina | 웹 AI Sky Changer ([페이지](https://dreamina.capcut.com/ko-kr/ai-image/sky-changer)) | 무료 체험 | CapCut Play 1B+ |
| 미니 프로그램·웹 도구 (Fix Your Pic, insMind, aiease, Evoto 등) | 브라우저에서 하늘 교체 ([Fix Your Pic](https://fixyourpic.com/ko/replace-sky), [Evoto](https://www.evoto.ai/features/sky-replacement)) | 무료~구독 | 못 찾음 |
| Meitu, Wink, Lensa, SNOW, EPIK, B612, Ulike, 醒图/Hypic, Canva, VSCO | 공식 하늘 기능 자료 **못 찾음**. 미확인이지 부재 확정이 아니다 | 구독 | Meitu KR 4.42(38,305). SNOW KR 4.44(191,420), KR MAU 499만(2025-06, [와이즈앱·리테일](https://www.openads.co.kr/content/contentDetail?contsId=17076)). EPIK KR 4.77(81,683). Ulike KR 4.64(106,976) |

한국 시장 메모: 사진 앱 쪽은 SNOW 계열(SNOW, EPIK, SODA, B612, Foodie)의 평가 수가 크다. 다만 이들의 쓰임은 얼굴·셀카다 [추론]. 하늘 영역의 한국 앱은 피크닉이 사실상 유일하다.

### 1-3. OS·폰 기본 기능

| 제품 | 기능 | 조건 | 사용 증거·평가 |
|---|---|---|---|
| **Google Photos 하늘 제안** | 하늘 감지 시 제안 자동 노출. Vivid, Luminous, Radiant, Ember, Airy, Afterglow, Stormy ([9to5Google](https://9to5google.com/2020/12/07/google-photos-sunset-filters/), [Android Police](https://www.androidpolice.com/2021/07/08/how-to-give-your-pixel-pics-the-spectacular-sky-they-deserve/)) | 2020-12 Pixel, 2023년 구독자 확대 ([Gigazine](https://gigazine.net/gsc_news/en/20230224-google-photos-sky)) | 색만 바꾸는 조정. 하늘 자체 교체 아님 |
| **Google Magic Editor** | Sky, Golden hour 프리셋 ([Google](https://www.blog.google/products/photos/google-photos-magic-editor-pixel-io-2023/), [PhoneArena](https://phonearena.com/news/google-photos-opens-up-magic-editor-and-other-ai-image-editing-tools-to-everyone_id158540)) | 비Pixel은 월 10회 저장 ([iPhone in Canada, 2026-04](https://www.iphoneincanada.ca/2026/04/20/google-photos-debuts-ai-touch-up-tools/)). 2025-08 편집기 개편에 흡수 ([PiunikaWeb](https://piunikaweb.com/2026/02/18/from-magic-to-mess-why-is-google-letting-magic-eraser-rot/)) | "효과는 미묘" ([Tom's Guide](https://tomsguide.com/phones/google-pixel-phones/i-tried-reimagine-ai-on-my-pixel-9-pro-xl-to-edit-photos-heres-what-happened)), 하늘을 지우다 호텔 건물 상단을 망침 ([Digital Trends](https://uat4.www.digitaltrends.com/mobile/how-to-use-magic-editor-on-google-pixel-8)) |
| **Google Photos 대화형 편집** | 말이나 글로 수정 지시. 일부 나노바나나 사용 ([PetaPixel](https://petapixel.com/2025/08/20/you-can-now-edit-images-in-google-photos-using-only-your-voice), [PhoneArena](https://phonearena.com/news/nano-banana-is-now-in-google-photos-and-even-ios-users-can-use-its-best-features_id175655)) | 한국 출시 여부 못 찾음 | Google Photos Play 10B+ |
| **샤오미 갤러리 Sky** | 2019년 베타. 프리셋이 하늘과 함께 **나머지 화면의 조명도 바꾸고**, 조명 강도 슬라이더 제공 ([XDA](https://www.xda-developers.com/xiaomi-testing-sky-replacement-feature-miui-gallery/)). HyperOS에도 있음 ([Smartprix](https://www.smartprix.com/bytes/8-hidden-features-in-the-hyperos-gallery-app-you-didnt-know/)) | 샤오미 폰 | 사용량 못 찾음 |
| **삼성 생성형 편집** | 공식 기능은 객체 이동·제거·채움 ([삼성](https://www.samsung.com/ca/support/mobile-devices/how-to-use-generative-photo-editing-on-the-galaxy-s24)). 약 12MP로 축소. "하늘을 노을로"는 One UI 8.5 관련 저신뢰 보도뿐, 공식 확인 못 함 | 삼성 계정·네트워크 | 하늘 용도 한국 후기 못 찾음 |
| **Apple Photos** | 내장 하늘 교체·날씨 변경 못 찾음. Clean Up만 있음 ([AppleInsider](https://appleinsider.com/inside/ios-26/tips/inside-photos-in-ios-26-macos-26----refinements-in-apples-image-and-video-management-tool)) | Apple Intelligence | 해당 없음 |
| **Snapchat Sky Filters** | 2017년, 하늘 감지 시 필터 자동 제시, 날씨·노을·별 ([MacRumors](https://www.macrumors.com/2017/09/25/snapchat-gains-new-sky-filters/)) | 앱 내 | 현재 존속 여부 못 찾음 |

### 1-4. 범용 생성 AI

| 제품 | 증거 | 하늘 용도 |
|---|---|---|
| **ChatGPT** | KR 4.77(827,529). 한국 MAU 2,162만(2025-11, [와이즈앱·리테일](https://aimatters.co.kr/tag/ai-%EC%95%B1-%EC%B6%94%EC%B2%9C/), 발췌). 지브리 유행 때 2025-03 다운로드 약 4,600만, 전월 대비 28% 증가 [추정, Appfigures] ([Business Today](https://www.businesstoday.in/technology/news/story/chatgpt-beats-instagram-tiktok-becoming-most-downloaded-app-globally-in-march-all-thanks-to-ghibli-trend-471813-2025-04-12)) | 한겨레 기고가 실제 절차로 소개 (아래) |
| **Gemini / 나노바나나** | KR 4.76(428,528). 출시 후 2주 남짓 첫 사용자 2,300만, 공유 이미지 5억 장(구글 임원 발언) ([TechCrunch](https://techcrunch.com/2025/09/16/gemini-tops-the-app-store-thanks-to-new-ai-image-model-nano-banana/)). 유행의 주인공은 3D 피규어 ([Yahoo](https://tech.yahoo.com/ai/gemini/articles/google-gemini-exec-says-nano-111112810.html)) | 하늘 전용 유행 자료 못 찾음 |

**가장 직접적인 한국 자료**: 김성주 사진가의 한겨레 기고(2026-10-09, [서플 게재본](https://supple.kr/news/cmv05adjk005isnikab51gf4v)).

- 챗GPT: "건물과 수면에 비친 빛, 인물의 그림자까지 생성됐다." 다만 원본을 해치는 경우가 있었고 선명도가 원본보다 떨어져 추가 지시로 완화했다.
- 피크닉: "결과물은 다소 부자연스럽지만" 가족이나 SNS 공유용으로는 쓸 만하다.
- 루미나 네오: "출시 초창기에는 합성된 영역의 이질감이 심했지만" 개선 중.

### 1-5. 사라졌거나 멈춘 것

| 대상 | 상태 | 근거 |
|---|---|---|
| Enlight Quickshot | 소멸 아닌 리브랜딩. Lightleap이 되었고 Play 패키지 이름에 quickshot이 남음 | Play `com.lightricks.quickshot`, 리뷰 "when it was QuickShot" |
| Artleap / Lightricks | 정체. 마지막 갱신 2024-07-14 | iTunes API |
| Sky Replacement, SkyLab, SkyAR, Sky Changer | 방치 또는 저활성 | 1-1표 |
| Aperty / Skylum | 2026-11-04 종료 예정. 5년 업데이트 약속을 깸 (하늘 앱은 아님) ([PetaPixel](https://petapixel.com/2026/10/05/skylum-saying-goodbye-to-the-ai-portrait-editor-aperty-despite-promising-5-years-of-updates/), [Fstoppers](https://fstoppers.com/apps/aperty-skylum-goner-november-904937)) | 공고 |
| 공식 종료된 하늘 전용 앱 | **못 찾음.** 대개 종료 공지 없이 방치로 사라진다 [추론] | 검색 결과 없음 |

요청서의 "Replika 류 하늘 앱"은 Replika가 대화 앱이라 해당 사례를 찾지 못했다. 바이럴 후 하강 사례는 Prisma·FaceApp·Lensa·EPIK으로 대신했다.

### 1-6. 유행 사례

| 항목 | 결과 |
|---|---|
| TikTok·릴스의 "흐린 여행 사진 고치기" 유행 | **못 찾음.** 나온 것은 도구 업체 글뿐이다. "휴가 사진 60%가 편집된다"는 [Pincel](https://blog.pincel.app/?p=6967) 수치는 출처 확인 불가 |
| 샤오훙슈 | 사용자 유행은 못 찾음. 대신 반감 쪽 자료가 있다. 숙소 사진 보정 "3.5元", 창밖 하늘 교체는 추가 요금 ([什么值得买](https://post.smzdm.com/p/aomv38nm/)). 2026-08-02 플랫폼이 AI 가짜 사진 포함 위반 여행 게시물 4천여 건 처리 ([분석 글](https://noobclaw.com/cn/blog/xiaohongshu-ai-images-reach-2026/)) |
| 피크닉의 확산 | 무마케팅 입소문이라는 회사 주장. 해외 비중 97%(2018), 90% 이상(2019) ([보안뉴스](http://www.boannews.com/news/articleView.html?idxno=71126), [뉴시안](http://www.newsian.co.kr/news/articleView.html?idxno=35516)). 홍콩 사진 1위, 애플 "오늘의 앱" 다수국 |

한국 커뮤니티(더쿠, 인스티즈, 클리앙, 네이버 블로그): 검색 도구로는 원문이 잡히지 않았다. 한국 반응은 앱스토어·Play 리뷰가 대신한다.

---

## 2. 실제 리뷰 분석

### 2-1. 리뷰 불만 비교

키워드 집계라 대략값이며, 표본 시기가 앱마다 다르다.

| 앱 | 표본 | 글 리뷰 평균 | 결제 불만 | 광고 불만 |
|---|---|---:|---:|---:|
| **PICNIC** | 1,262 | 4.33 | 2.5% | 3.0% |
| Lightleap | 843 | 2.67 | 35.8% | 4.3% |
| Luminar Mobile | 243 | 2.84 | 29.6% | 0.4% |
| Photoleap | 500 | 2.55 | 35.8% | 0.8% |
| Lensa | 500 | 2.67 | 25.4% | 0.2% |
| Prisma | 500 | 3.24 | 23.4% | 2.2% |
| EPIK (KR) | 500 | 2.97 | 55.2% | 3.8% |
| SNOW (KR) | 500 | 2.71 | 32.4% | 5.6% |
| Meitu (KR) | 500 | 2.41 | 37.2% | 11.2% |
| Picsart (KR) | 500 | 3.06 | 44.2% | 15.0% |
| Sky Changer | 135 | 2.64 | 1.5% | 24.4% |

[증거] 표본 중 피크닉만 평균이 4점대다. [추론] 표본 시기가 길어 옛 호평이 포함된 영향도 있으나, 아래 연도표가 보여 주듯 초기 몇 년의 차이는 실제다.

### 2-2. 피크닉 연도별

| 연도 | 표본 | 평균 | 광고 언급 | 워터마크 언급 | 저장 실패 언급 |
|---|---:|---:|---:|---:|---:|
| 2018 | 114 | 4.69 | 0 | 4 | 1 |
| 2019 | 283 | 4.73 | 1 | 0 | 0 |
| 2020 | 265 | 4.49 | 1 | 1 | 17 |
| 2021 | 217 | **3.48** | 3 | 1 | **80** |
| 2022 | 53 | 4.21 | 0 | 0 | 8 |
| 2023 | 54 | 4.81 | 0 | 0 | 0 |
| 2024 | 90 | 4.51 | 7 | 5 | 0 |
| 2025 | 128 | 4.12 | 18 | 5 | 1 |
| 2026 | 58 | 4.02 | 8 | 3 | 1 |

- 2021년의 급락은 저장이 안 되는 버그 탓이다. **결과를 손에 못 쥐면 다른 장점이 사라진다.**
- 2024년 이후 하락은 수익화 탓이다: "앱 시작할 때 영상광고 뜨더니 광고 안 보면 마크 붙고" (2025-06), "예전에 이 어플 잘 사용했는데 다시 쓰려고보니까 광고가 진짜 너무 심해졌네요" (2025-10).
- 카메라 맥락의 지적도 있다: "사진을 찍으려고 어플을 켰다는건 지금 빨리 찍어야하는 건데" (2024-09).

### 2-3. 피크닉 칭찬: 왜 깔았나

5점 912건 중 117건이 날씨·여행을 직접 말한다. 사용 상황이 구체적이다.

- "날씨가 망한 날을 살려주는 앱" (2025-08)
- "아마 다시오지 않을 해외여행 장소에서 흐린날씨가 너무 아쉬웠는데.. 어플하나로 인생사진 뚝딱이네요" (2023-11)
- "여행동안 날씨 흐려서 우울했는데 하늘 합성하면서 이게 찐이지 위로중이에요" (2023-12)
- "제가 오래 기다렸던 여행이었는데 하필 그날 안개와 미세먼지가 가득해서... 추억을 아름답게 남길 수 있었어요" (2021-02)
- "옛날 여행사진 다 꺼내서 코로나 끝날 때까지 보정만 하고 있을듯해요! 덕분에 다시 랜선여행 떠나는 기분이에요" (2020-10)
- 「天気さえ良ければいうことない写真になったのにな」という時に重宝しています。そのついでにいろんなバリエーション作って遊んでみたり (2024-01)
- 娘の写真が沢山あるのですが、あいにくの天気で残念な写真もいい感じに加工出来て待ち受けに出来ました (2024-04)

### 2-4. 피크닉 불만: 무엇이 부족한가

| 주제 | 건수 | 원문 |
|---|---:|---|
| 하늘 아닌 곳에 하늘이 묻음 | 최대 58 (넓은 키워드 기준 상한) | "파란색 치마를 입었는데 파란치마까지 구름으로 바뀌어요" / "청바지까지 구름이 들아가더라구요 히힛 구름바지 되어버림" / 体まで全部青空になってしまいます |
| 사람이 끼면 어색 | 소수지만 명시적 | "간혹가다 약간 어색하게 보정 되기도 하더라구요!! 인물이 같이 나오면 어색하고!!" (2021-04) |
| 톤 불일치 | 소수 | "본인이 찍은 사진과 톤 색감은 좀 보정해야 자연스럽게 합성" (2019-11) |
| 같은 하늘 반복 | 최대 26 | "같은 별이랑 구름위치들이 매번 똑같아서 좀 빨리 지겨워지고" / 他の人の写真と同じ空だし |
| 하늘 인식 실패 | 12 | "Can't even detect the sky" |
| 화질 저하·노이즈 | 15 | "필터적용하고 나면 사진 해상도가 많이 떨어져요" |
| 지우개 요구 | "이 문의가 되게 많아보이는데" 라고 사용자가 직접 언급 | "필요한 부분만큼 지우거나 더 칠할 수 있게" (2019-11) |

칭찬에 "자연스럽다"가 많고 "가짜 같다"는 적다(58 대 9). [추론] 사용자가 원한 것은 사진적 진실이 아니라 **아쉬움이 풀릴 만큼의 그럴듯함**이다. 강도 조절이 그 만족을 돕는다: "적용 정도를 자유롭게 조절해서 자연스러운 하늘을 만들 수 있어요" (2024-04).

### 2-5. Lightleap: 같은 기능, 반대 결과

| 연도 | 2018 | 2019 | 2020 | 2021 | 2022 | 2023 | 2024 | 2025 | 2026 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 글 리뷰 평균 | 3.08 | 2.50 | 2.73 | **2.21** | **2.17** | 2.66 | 3.45 | 3.59 | 4.01 |
| 결제 언급 비율 | 68% | 71% | 65% | 39% | 36% | 34% | 36% | 9% | 6% |

- "This app used to be so great... a few in each category were free" (2021-06)
- "하늘 꾸밀려면 90프로는 돈내고 사용해야됨 무료 이쁜거 하나도 없음" (2021-06)
- "하늘 필터 적용하고 적용 버튼 체크하면 구매하라는것 밖에 안나와요" (2020-07): 끝까지 작업시킨 뒤 저장에서 막는 구조.
- "The sky replacements were the only reason i was downloading this app... very few of them were realistic" (2023-02)
- "you never change the sky options" (2022-11), "the same sky" (2026-06): 칭찬하는 사용자조차 하늘 고갈을 말한다.
- 2025~2026 평균 회복은 표본이 작고 원인을 확인하지 못했다.

---

## 3. 왜 성공했나, 왜 실패했나

### 3-1. 사례별

| 사례 | 무슨 일이 있었나 [증거] | 왜 [추론] |
|---|---|---|
| **피크닉** 성공 | 무마케팅 주장, 2년 만에 1,000만. 평가 수가 KR·JP에 고르게 쌓임 | 기능이 아니라 상황을 샀다. 흐린 날은 전 세계 공통의 아쉬움이고 말이 필요 없어 해외로 번졌다. 일본 스토어 이름("天気の妖精カメラ")과 출시 문구가 "날씨 요정"이었다 |
| **피크닉** 한계 | 스토어 문구가 아직 "1,000만". [추정] 월 $10K+, MAU 188K | 돈이 안 되는 아쉬움이다. 무료 앱으로 사랑받았지만 사업은 작고, 광고가 사랑을 깎는다 |
| **Lightleap** 부진 | 2021년 대규모 불만, 평균 2.2대 | 쓰는 빈도가 낮은 기능에 정기 구독을 붙였다. "I don't need this app every month"(2023-05)가 핵심 |
| **Luminar** | 재조명 평가가 경쟁작보다 앞섬. 모바일은 결제 분쟁 | 기술 우위가 신뢰 붕괴를 못 막는다 |
| **신생 전용 앱** | 평가 0~11건 | 기능이 범용 도구로 흡수된 뒤 들어왔다. 광고·결제가 전부면 끝난다 (Sky Changer) |
| **Google·샤오미** | 사진첩 안에서 자동 제안 | 깔 필요 없이 찾아온다. 다만 색 조정이거나 사용량이 드러나지 않는다 |
| **ChatGPT/Gemini** | 무료로 빛·그림자까지 생성 | 위협이지만 프롬프트가 필요하고 원본을 건드린다. 빈 입력창은 "오늘 사진 살려 줘"를 먼저 떠올린 사람만 쓴다 |

### 3-2. 바이럴 앱의 궤적 (비교 대상)

| 앱 | 정점 | 이후 |
|---|---|---|
| Prisma (2016) | 첫 달 약 1,700만, 일 200만 이상 [보도, 검색 발췌라 출처 글 특정 못 함] ([PhoneArena](https://www.phonearena.com/news/Prisma-surpasses-50-million-downloads-in-the-Google-Play-Store-do-you-still-use-it_id101204), [NVIDIA](https://developer.nvidia.com/technology-behind-viral-prisma-photo-app)) | 2018년 구독 도입, 팀은 Lensa로 ([TechCrunch](https://techcrunch.com/?p=1818922)). 앱은 존속, US 211,465건. 정확한 잔존율 못 찾음 |
| FaceApp (2019) | 일 6.5만 → 180만, 7월 6,300만 [추정, Sensor Tower] ([BI](https://businessinsider.in/since-going-viral-again-for-making-people-look-old-faceapp-has-been-downloaded-by-12-7-million-new-users/amp_articleshow/70280757.cms), [Sensor Tower](https://sensortower.com/blog/top-apps-worldwide-july-2019-downloads)) | Play 500M+. 이후 하강 수치 못 찾음 |
| Lensa (2022) | 하루 $2M 이상 [추정, Appfigures] ([원문](https://appfigures.com/resources/insights/20230106?f=5)). 2022-12 1,930만 → 2023-01 140만 ([Statista](https://statista.com/statistics/1350961/lensa-ai-app-downloads-worldwide)) | 연 매출 $43M → $18M [추정] ([Business of Apps](https://www.businessofapps.com/data/lensa-ai-statistics), 수치 수정 이력 있음) |
| AI 사진 앱군 | 일 430만 다운로드, 소비 약 $180만 [추정, Apptopia] ([9to5Mac](https://9to5mac.com/2023/02/15/ai-chat-ai-photo/)) | 2개월 뒤 95.2만, $50.7만 |
| EPIK AI Yearbook (2023) | 56개국 1위(회사 발표) ([보도자료](https://aap.com.au/aapreleases/cision20231010ae33941/)). $0.99~$38.99 ([MobileAction](https://www.mobileaction.co/blog/epik-apps-epic-win-breaking-the-social-media-with-ai-yearbook/)) | 하강 수치 못 찾음. 최근 KR 글 리뷰 55%가 결제 불만 |
| Remini (2023) | 일 $9만 → $56.7만 [추정, Appfigures] ([원문](https://appfigures.com/resources/insights/20230714?f=2)) | 트라이얼 후 해지 관행 보도 |

[추론] 이들의 공통점은 결과물이 **나 자신**이었다는 것이다. 자랑이 쉬워 폭발하고, 한 번 본 뒤 식는다. 날씨 보정은 반대로 조용히 퍼지고, 결과물이 추억이라 자랑 대신 간직으로 간다. 그래서 피크닉은 폭발 없이 평점을 지켰다.

### 3-3. 공통 패턴

| 패턴 | 증거 | 해석 [추론] |
|---|---|---|
| 단일 기능 vs 모음 | 전용 앱 중 살아남은 건 사실상 피크닉, Lightleap. 나머지는 범용 앱에 흡수 | 단일 기능은 상황을 소유할 때만 산다 |
| 공짜의 범위 | 피크닉 결제 불만 2.5% vs 구독형 25~55% | 결과를 먼저 주지 않으면 무너진다 |
| 끝에서 막기 | Lightleap 저장 단계 결제 불만 | 공들인 뒤 막으면 분노가 가장 크다 |
| 사실감 | 하늘은 관대, 사람·경계는 엄격 | 사람이 품질 판단의 기준이다 |
| 워터마크 | 피크닉 19건, "구독을했는데 워터마크가 계속 나옴" | 강제 마크는 확산보다 반감이 빠르다 |
| 재사용 | 연간 잔존율 AI 앱 21.1%, 비AI 30.7%. 월간 6.1% vs 9.5% ([TechCrunch, RevenueCat 2026](https://techcrunch.com/2026/03/10/ai-powered-apps-struggle-with-long-term-retention-new-report-shows)). 사진·영상은 낮은 쪽 ([RevenueCat](https://www.revenuecat.com/blog/growth/ai-app-retention-study)) | 날씨 보정은 여행 때만 필요하다. 정기 구독과 맞지 않는 빈도다 |
| 한 번 쓰고 삭제 | 삭제 키워드는 피크닉 0.9%, Lightleap 5.9%. Weathershot 등 노후 앱 평가 급락 | 깔아 둘 이유보다 다시 찾아올 이유가 필요하다 |
| 기억·정직 | MIT 실험: AI 편집 영상 조건에서 허위 기억 2.05배, 200명, 휴가 사진의 극적인 노을이 예시 ([arXiv](https://arxiv.org/html/2409.08895v1), [MIT](https://www-prod.media.mit.edu/publications/synthetic-human-memories/)). 샤오훙슈는 "실사인 척"을 단속 | 간직용 물건은 바뀌었다는 사실을 감추지 않는 편이 안전하고 오히려 더 애틋하다 |

---

## 4. 무료 lab 작품에 주는 뜻

전부 [추론]이다. 운영자가 다시 쓰고 싶어지려면, 이라는 기준으로 골랐다.

| 원칙 | 왜 | 작품에서 |
|---|---|---|
| **재사용 단위는 사진 한 장이 아니라 그 여행** | "옛날 여행사진 다 꺼내서" 리뷰가 가장 강한 재방문 신호다 | 갤러리의 흐린 사진들을 한 번에 넣는 흐름을 검토. 다음 여행이 아니라 지난 여행들이 재료다 |
| **입력창을 없앤다** | 챗GPT와 갈리는 유일하고 큰 차이 | 사진 고르기 → 바로 결과. 프롬프트 없음 |
| **사람부터 지킨다** | 피크닉의 최대 불만이고 이번 시도의 차별점 | 시연 사진은 인물이 있는 걸로. 옷·얼굴이 하늘에 먹히면 실패로 친다 |
| **원본을 건드리지 않은 느낌** | 생성 AI의 약점. 얼굴·선명도가 바뀌면 추억이 아니다 | 전후 비교를 주 화면으로. 바뀐 건 빛뿐임을 보여 준다 |
| **하늘을 매번 다르게** | 양쪽 앱에서 똑같이 나온 불만 | 고정 팩을 쓰더라도 위치·조합을 섞어 "남과 같은 하늘"을 피한다 |
| **강도 조절** | 만족의 이유로 직접 언급됨 | 슬라이더 하나 |
| **결과를 먼저, 전부 공짜로** | 끝에서 막는 게 최악 | 저장 잠금·로그인·대기 잠금 없음. 저장 실패는 치명적이니 저장을 가장 먼저 검증 |
| **사진에 마크를 박지 않는다** | 강제 워터마크 반감 | 링크는 공유 문구로 전달 |
| **간직할 형태** | 점수 없는 기념품이 브리핑의 뜻. 정직 문제도 함께 풀린다 | 날짜와 함께 "그날은 흐렸지만" 같은 짧은 표기를 카드에. 나쁜 날씨 자체를 추억으로 남긴다 |
| **유행을 기대하지 않는다** | 날씨 고치기 유행 증거 없음 | 성공 기준은 조회수보다 운영자의 재사용. 공유는 전후 대비로 충분 |

한 줄로: **챗GPT는 사진을 고쳐 주고, 피크닉은 하늘을 바꿔 준다. 이 작품은 그날을 다시 건네줘야 한다.**

---

## 5. 빈틈

- 한국 커뮤니티 원문(더쿠·인스티즈·클리앙·네이버 블로그)은 도구로 수집하지 못했다. 운영자가 필요하면 브라우저로 직접 봐야 한다.
- Meitu, Wink, 醒图, Ulike, Lensa, SNOW, EPIK, B612, Canva, VSCO의 하늘 기능 유무는 미확인이다. 앱 안에서 직접 봐야 확정된다.
- 한경 2019 기사는 본문이 차단되어 검색 발췌에 의존했다. 500만 기사의 "출시 1년 6개월"은 2017년 출시일과 맞지 않지만 원문 그대로 인용했다.
- Prisma·FaceApp의 잔존율, 피크닉의 실제 재방문율은 공개 자료가 없다.
- PetaPixel의 2026-10 허위 기억 기사는 접근이 막혀 MIT 원문으로 대체했다.
- Play 리뷰는 비공식 경로라 건수가 적고, 키워드 집계는 오탐이 있다. 숫자는 규모 감각으로만 쓸 것.

원자료: `C:\Users\ehdck\AppData\Local\Temp\claude\C--DC-DD\776decc7-044b-4fb6-9196-d7a0e8091f4a\scratchpad\` 의 `itunes\apps.json`, `play\play.json`, `reviews\reviews.json`.
