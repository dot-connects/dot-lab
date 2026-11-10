# 펫 레퍼런스 — 놓는 자리·행동 모드 / 사진 → 캐릭터 변환

조사일 2026-11-11. 링크는 웹 검색으로 존재를 확인했다(스토어 집계 사이트 appgoblin/appbrain 링크는 원 스토어 페이지 대신 쓴 것).
설치 수·평점은 제3자 집계라 대략치. 번호로 고르면 된다.

---

## Q1. 펫을 어디에 두고 어떻게 움직이게 할까

### 먼저: Android(사이드로드 APK)에서 각 자리가 되나

| 자리 | Android 구현 수단 | 사이드로드로 가능? | 한계 |
|---|---|---|---|
| 가장자리 걷기 / 화면 자유 이동 | `SYSTEM_ALERT_WINDOW` overlay (지금 방식) | ✅ | 상태바·키보드(IME)·잠금화면 **위**에는 못 그림 |
| 상태바·노치·내비바 위 | Accessibility overlay (`TYPE_ACCESSIBILITY_OVERLAY`) | ⚠️ | Android 13+ 사이드로드 앱은 "제한된 설정" 해제 필요, Play Protect 경고(우리 memory: 원탭 APK 때 막힘) |
| 다른 앱 창 위에 "앉기" | 불가(다른 앱 창 좌표를 못 읽음) | ❌ | Accessibility로 화면 요소 경계는 읽을 수 있으나 무겁고 권한 부담 |
| 키보드 위에 앉기 / 타이핑 반응 | 직접 IME(키보드 앱) 제작 | ⚠️ | overlay는 IME 아래에 깔림. 키 입력 감지는 IME 또는 Accessibility만 |
| 홈 화면 위젯 | AppWidget(RemoteViews / Glance) | ✅ | 자유 애니메이션 불가. 프레임 교체·`ViewFlipper` 정도 |
| 잠금화면 | Android 16 QPR2+ 잠금화면 위젯(Pixel), 일부 One UI | ⚠️ | 기기·OS 버전 의존. overlay는 잠금화면에 안 뜸 |
| AOD(Always-On) | 서드파티 API 없음 | ❌ | Samsung ClockFace에 GIF 넣는 정도(사용자 수동) |
| 라이브 배경화면 | `WallpaperService` | ✅ | 홈 아이콘 **뒤**에만. 터치 이벤트는 받음. 배터리 주의 |
| 충전 화면 | 이미 도는 foreground service가 `ACTION_POWER_CONNECTED` 수신 → overlay 띄움 | ✅ | manifest 정적 수신은 Android 8+에서 안 됨 |
| 알림 반응 | `NotificationListenerService` | ⚠️ | 사이드로드는 "제한된 설정" 해제 필요 |
| Dynamic Island 류 | Android 대응 없음(Android 16 Live Updates는 길찾기·배달 등 진행형 알림 전용) | ❌ | iOS 전용 |
| 시간대(밤에 잠) | 시계만 보면 됨 | ✅ | 권한 없음 |

### A. 가장자리 걷기 · 화면 자유 이동 (overlay)

1. **Shimeji-ee** — https://kilkakon.com/shimeji/
   데스크톱 마스코트 원조. 걷기·창 가장자리 기어오르기·잡아서 던지기·분열. 
   맞는 점: "던지면 떨어져서 다시 걸어옴"이 공 던지기 게임과 이어짐. 안 맞는 점: Java·데스크톱 전용.
2. **Shijima – Shimeji for Android** — https://www.appbrain.com/app/shijima-shimeji-for-android/com.pixelomer.shijima
   Shimeji를 Android로 옮긴 앱(10만+). 던지기·쓰다듬기·크기 조절. 데스크톱판은 GPLv3 오픈소스(https://pixelomer.itch.io/shijima).
   맞는 점: 우리와 가장 가까운 Android 선례, 행동 세트 참고용. 안 맞는 점: 행동 정의가 Shimeji XML 포맷이라 그대로는 안 씀.
3. **ANeko Reborn** — https://fossdroid.com/a/aneko.html (소스 https://github.com/pass-with-high-score/ANeko, LGPL-2.1)
   고전 Neko를 Android overlay로. 걷기·앉기·잠자기, 스킨 교체.
   맞는 점: **오픈소스 Android overlay 펫** — 상태 머신·스킨 구조를 그대로 읽어볼 수 있음. 안 맞는 점: 도트 2D.
4. **oneko.js** — https://github.com/adryd325/oneko.js
   커서를 쫓아 뛰고, 커서가 멈추면 앉았다가 잠듦. JS 1개 + 256×128 스프라이트.
   맞는 점: "손가락 따라오기 → 가만히 두면 잠" 모드의 최소 구현. 안 맞는 점: 모바일엔 커서가 없어 터치 지점으로 바꿔야 함.
5. **Desktop Goose** — https://samperson.itch.io/desktop-goose
   거위가 화면을 돌아다니며 커서를 물고 가고, 메모·밈을 끌고 들어옴.
   맞는 점: "가끔 뭔가 물고 오는" 장난 모드(예: 잡은 날짜 카드 물고 오기). 안 맞는 점: 방해형이라 하루 종일 켜두긴 피곤.
6. **Pixel Shimeji – Desktop Pet** — https://appgoblin.info/apps/com.coolguy.desktoppet
   Android Shimeji 류 최다 설치(약 1,600만). 도트 펫 overlay.
   맞는 점: 수요 검증. 안 맞는 점: 광고 범벅, 디자인 참고 가치 낮음.
7. **Live Anime Shimeji Pets** — https://appgoblin.info/apps/com.pet.desktop.onscreen
   약 290만. Accessibility로 상태바 overlay까지 씀. 일부 기기에서 펫이 구석에 끼는 버그 리뷰.
   맞는 점: "구석에 끼임" 버그 = 우리가 미리 막을 엣지 케이스. 안 맞는 점: Accessibility 의존.
8. **Soonshik** — https://www.hunted.space/product/soonshik
   **홈 화면에서만** 보이는 Android 픽셀 펫. 다른 앱을 열면 멈춰서 숨음.
   맞는 점: "앱 쓸 땐 안 거슬리게" 모드 — 지금 가장자리 펫의 피로를 줄이는 선택지. 안 맞는 점: 홈 판별에 usage stats 등 추가 권한 필요할 수 있음.
9. **WindowPet** — https://github.com/SeakMengs/WindowPet
   Tauri+React 데스크톱 펫 overlay(MIT). 여러 펫 동시, 중력·벽 타기.
   맞는 점: 웹 기술로 만든 펫 행동 코드라 우리 lab 쪽에서 읽기 쉬움. 안 맞는 점: 모바일 아님.

### B. 앱 창·작업표시줄 위에 앉기 / 입력에 반응

10. **Bongo Cat** — https://store.steampowered.com/app/3419430
    작업표시줄 구석에 앉아 키보드·마우스 입력마다 손으로 탁탁. 타이핑 수로 모자 해금. 친구 고양이도 띄움.
    맞는 점: "내가 폰을 쓸수록 펫이 뭔가 함" 루프. 안 맞는 점: Android overlay는 키 입력을 못 봄.
11. **VPet Simulator** — https://store.steampowered.com/app/1920960/VPet_Simulator/ (소스 https://github.com/LorisYounger/VPet)
    무료·오픈소스 데스크톱 펫. 배고픔·기분·잠, 일/공부 모드, 워크숍 모드 지원. 리뷰 수만 건 98% 긍정.
    맞는 점: 상태(졸림·배고픔)에 따라 자세가 바뀌는 설계 참고. 안 맞는 점: 큰 캐릭터라 폰 화면엔 과함.
12. **Tapling: Type with Bongo Cat** — https://apps.apple.com/app/id6756097345
    iOS 키보드 제안 막대에 고양이가 살며 타이핑에 맞춰 두드림. 키 내용은 안 읽고 횟수만 셈.
    맞는 점: **키보드 펫**의 실례 + "내용 안 읽음"으로 프라이버시 해결. 안 맞는 점: Android에선 우리 IME를 따로 만들어야 함(과함).

### C. 상태바 · 노치 · 내비게이션 바

13. **Barpie: Status Bar, Notch Pets** — https://appgoblin.info/apps/com.barpie.statusbar.pets
    상태바 위를 걷는 펫 + 카메라 펀치홀에 매달린 "Notch Friends".
    맞는 점: **펀치홀에 매달리기**는 공 모양 털뭉치에 딱 맞는 자세. 안 맞는 점: 상태바 위 = Accessibility 필요, 평점 2.3.
14. **Pixel Pals – Nav bar pets (Android)** — https://appgoblin.info/apps/com.extinct.pixelpals
    하단 내비게이션 바(제스처 바) 위를 고양이가 뛰어다님. 밥 줘야 유지. 약 30만.
    맞는 점: 제스처 바 위 = 화면 가리는 면적 최소. 안 맞는 점: 제스처 바 위치도 overlay로는 덮기 애매(기기마다 다름).

### D. Dynamic Island · Live Activity (iOS 전용 — Android 대응 없음, 아이디어만)

15. **Pixel Pals (Christian Selig)** — https://apps.apple.com/app/id6443919232
    아일랜드 안을 걷는 도트 펫. 다른 Live Activity(통화 등)가 오면 비켜줬다 돌아옴. 홈·잠금 위젯도.
    맞는 점: "더 중요한 게 오면 비켜준다" 규칙 — 우리 overlay도 전화·영상 시 숨기 규칙으로 차용. 안 맞는 점: iOS.
16. **Chimomo – Charging Animation / Island Pet** — https://apps.apple.com/us/app/-/id6747741618
    아일랜드에서 걷고·앉고·튀고·빼꼼. 충전 애니메이션·배터리 위젯 겸용.
    맞는 점: "빼꼼(peek)" 동작 + 충전 연결. 안 맞는 점: iOS.
17. **IslandMates – Pet Widget** — https://apps.apple.com/us/app/id6761184037
    커플이 펫 하나를 공유, 상대 메시지가 내 아일랜드/잠금화면에 뜸.
    맞는 점: 친구 몬스터가 놀러오는 공유 모드 아이디어. 안 맞는 점: 서버 필요.

### E. 홈 화면 위젯

18. **Finch: Self-Care Pet 위젯** — https://help.finchcare.com/hc/en-us/articles/39758423780621-The-Finch-Widget
    앱을 안 열어도 펫이 지금 뭐 하는지 보임. 옷·방 꾸밈이 위젯에 반영.
    맞는 점: 움직임이 적어도 "지금 상태" 한 장면으로 충분 — 위젯의 정답 사례. 안 맞는 점: 실시간 애니메이션 아님.
19. **Widgetable: Besties & Couples** — https://apps.apple.com/app/id1641107226 (Android: https://appgoblin.info/apps/com.widgetable.theme.android)
    둘이 같이 키우는 펫 위젯, Android 4,100만+.
    맞는 점: Android 위젯 펫이 대중적으로 통한다는 증거. 안 맞는 점: 서버·계정 필요.
20. **Mittens – Virtual Widget Pet** — https://apps.apple.com/us/app/id6739215463
    위젯·잠금화면·아일랜드에서 돌보는 고양이, 완전 무료.
    맞는 점: 위젯 한 칸 = 펫 방 한 칸 구성 참고. 안 맞는 점: iOS.

### F. 잠금화면 · AOD

21. **Android 16 QPR2 잠금화면 위젯** — https://www.androidauthority.com/lock-screen-widgets-on-phones-android-16-qpr2-3589668/
    Pixel 폰 잠금화면에서 옆으로 밀면 서드파티 위젯 패널. 충전·거치 시 띄우게 OEM이 설정 가능.
    맞는 점: E번 위젯을 만들면 공짜로 잠금화면에도 감. 안 맞는 점: 운영자 폰 기종·OS 확인 필요.
22. **Samsung Good Lock – ClockFace / LockStar** — https://news.samsung.com/global/exploring-good-lock-%E2%91%A2-3-features-recommended-by-samsung-developers-and-newsroom-editors
    잠금화면·AOD 시계를 GIF 등으로 교체.
    맞는 점: 펫 GIF를 내보내 주면 사용자가 AOD에 걸 수 있음(코드 0). 안 맞는 점: Galaxy 전용, 수동.

### G. 라이브 배경화면

23. **Stalker Cat Live Wallpaper** — https://alternativeto.net/software/stalker-cat-live-wallpaper/about
    화면 가장자리 여기저기서 고양이가 빼꼼 나왔다가, 들킨 줄 모르면 의기양양.
    맞는 점: **"숨어 있다 빼꼼" = 잡기 게임과 정확히 같은 정서.** 배경화면이라 앱을 가리지 않음. 안 맞는 점: 홈 화면에서만 보임.
24. **Peper Kitten / HD Cat Live Wallpaper** — https://alternativeto.net/software/peper-kitten-live-wallpaper/
    배경화면 고양이를 쓰다듬으면 반응, 배 간질이면 춤, 놔두면 잠.
    맞는 점: 터치 반응 + 방치 시 잠 — 털뭉치 쓰다듬기와 궁합. 안 맞는 점: 오래된 앱, 유지보수 불명.

### H. 충전 화면

25. **Battery Charging Animation 류 (Android)** — https://appgoblin.info/apps/com.batterycharging.artcharginganimation.chargermaster
    충전기 꽂으면 전체화면 애니메이션(약 490만 설치, "귀여움" 카테고리 다수).
    맞는 점: "충전 = 펫 밥 먹는 시간/잠자는 시간" 연출이 직관적. 안 맞는 점: 대부분 광고형 저품질 — 수요만 참고.

### I. 상태·알림·시간 반응

26. **Android 7.0 Neko 이스터에그** — https://www.androidpolice.com/2016/07/18/gotta-catch-em-android-7-0-easter-egg-basically-pokemon-go-cats/
    빠른 설정 타일에 밥을 놓으면 나중에 고양이가 와서 **알림으로** "왔다"고 알려줌. 고양이 수집.
    맞는 점: **"우리 집 몬스터 잡기"와 구조가 거의 같음** — 미끼 두기 → 알림 → 수집. 빠른 설정 타일(`TileService`)은 사이드로드로 바로 됨. 안 맞는 점: 화면 위를 돌아다니진 않음.
27. **LCDPet** — https://f-droid.org/packages/today.notfeelingit.lcdpet/
    오픈소스 Android 다마고치. 밥·목욕·낮잠·산책, 로컬 알림으로 원하는 것 알림.
    맞는 점: 시간대·욕구 상태 머신 코드 참고. 안 맞는 점: overlay 아님(앱 안에서만).

---

## Q2. 실제 사진(사람·개·고양이)을 귀여운 캐릭터로 바꾸기

표기: **기기내** = 사진이 폰 밖으로 안 나감 / **클라우드** = 서버로 업로드. 지연은 대략.

### A. 상용 앱·렌즈 (만들 기술이 아니라 "결과물 감" 참고)

1. **Snapchat "Cartoon Face" / "Cartoon 3D Style" 렌즈** — https://www.techadvisor.com/article/740559/how-to-get-the-snapchat-disney-filter-and-cartoonise-your-pet.html
   반려동물 얼굴에 큰 만화 눈을 얹음(Cartoon Face) / 사람을 픽사풍 3D로(Cartoon 3D). 실시간, 기기내 렌즈. 무료.
   맞는 점: **눈만 바꿔도 캐릭터가 된다** — 우리 점눈 털뭉치에 사진 털 색만 입히는 접근의 근거. 안 맞는 점: 동물 얼굴 인식이 잘 안 잡힌다는 후기.
2. **Apple Genmoji / Image Playground** — https://www.intego.com/mac-security-blog/how-to-use-image-playground-and-genmoji-on-iphone-ipad-and-mac/
   사진 앱에서 이름 붙인 사람·**반려동물**로 이모지/일러스트 생성. 기기내 생성, 무료, 수 초.
   맞는 점: "프라이버시 + 귀여운 단순화"의 기준점. 안 맞는 점: Apple 기기 전용, API로 못 씀.
3. **Bitmoji (셀피 자동 생성)** — https://dreamina.capcut.com/resource/how-to-create-bitmoji
   셀피 한 장 → 얼굴 특징을 **기존 부품 조합**으로 맞춤, 이후 수동 수정.
   맞는 점: 생성형이 아니라 "부품 선택"이라 스타일이 절대 안 깨짐. 안 맞는 점: 부품 세트를 먼저 그려야 함.
4. **ZEPETO** — https://www.gadgetmatch.com/zepeto-lets-you-create-a-3d-character-version-of-yourself/
   셀피 얼굴 인식 → 3D 아바타 파라미터 자동 세팅. 클라우드+앱.
   맞는 점: 3D 템플릿에 사진 특징만 매핑 — 3D 털뭉치와 같은 구조. 안 맞는 점: 사람 얼굴 전용.
5. **Mii Maker "사진으로 만들기" (3DS)** — https://kotaku.com/5652733/using-magic-the-3ds-can-make-a-mii-using-a-photo
   성별·피부·눈·머리색을 고른 뒤 사진 찍으면 Mii 부품을 자동 선택. 기기내.
   맞는 점: **"사진은 파라미터만 뽑고, 그림은 우리 템플릿"**의 고전적 모범. 안 맞는 점: 결과 맞추느라 30분 만진다는 리뷰도.
6. **Picrew** — https://picrew.me/en
   작가가 만든 레이어 부품으로 아바타 조립(사진 없음). 무료, 웹.
   맞는 점: 사진 대신 "털 색·귀·꼬리 고르기" 수동 메이커로 폴백 가능. 안 맞는 점: 자동 아님.
7. **SpritePals: Pixel Pet Widget** — https://apps.apple.com/us/app/id6754703807
   반려동물 사진 → AI로 도트 펫 생성 → 아일랜드·잠금화면·위젯에 삶.
   맞는 점: **우리 기획과 가장 가까운 실제 앱**(사진 펫 → 화면에 사는 펫). 안 맞는 점: iOS, 리뷰 1건(3.0) — 검증 안 된 시장. 클라우드 추정.
8. **Pet to Human: Pet to Person** — https://apps.apple.com/app/id6744840765
   반려동물 사진 → 사람 캐릭터 / 카툰 인형 이미지. 클라우드, 인앱결제.
   맞는 점: 펫→인형 결과 톤 참고. 안 맞는 점: 방향이 반대(사람화).
9. **Photogotchi** — https://www.gmanetwork.com/news/scitech/technology/276103/tamagotchi-resurrected-in-smartphone-game/story/
   다마고치 계열(2013), 찍은 사진을 도트로 바꿔 펫에게 "먹임". 기기내.
   맞는 점: 사진을 펫 **먹이**로 쓰는 발상(펫 외형은 고정) — 사진을 캐릭터로 바꾸지 않는 대안. 안 맞는 점: 오래된 앱.
10. **Niantic Peridot** — https://apps.apple.com/app/id6445801028
    AR 펫. 사진 변환은 없고, 유전자 조합으로 펫마다 색·무늬가 다름.
    맞는 점: "사진 없이도 내 것만의 개체" — 색 유전 방식의 레퍼런스. 안 맞는 점: 사진 연결 없음.

### B. 클라우드 생성형 트렌드 (결과 좋음, 사진이 밖으로 나감)

11. **ChatGPT 지브리풍 / 반려동물→사람 트렌드 (2025-03~04)** — https://bgr.com/tech/reimagining-peoples-pets-as-humans-is-the-latest-viral-chatgpt-trend
    사진 → 지브리풍 일러스트. 클라우드, 무료 한도 있음, 수십 초~분.
    맞는 점: 사람들이 "내 펫이 캐릭터로"를 좋아한다는 증거. 안 맞는 점: 스타일 제어 약함, 저작권 논란.
12. **ChatGPT AI 액션피규어 / 바비 박스 (2025-04)** — https://www.yahoo.com/news/chatgpt-ai-action-figure-trend-what-is-it-and-how-can-i-join-in-200532692.html
    사진 → 포장 박스 속 피규어 + 소품. 클라우드.
    맞는 점: "포장 속 피규어" = 잡은 몬스터 카드/도감 연출 아이디어. 안 맞는 점: 비용·지연·프라이버시.
13. **Gemini "Nano Banana" 3D 피규어 (2025-09)** — https://www.techradar.com/ai-platforms-assistants/gemini/i-turned-myself-into-a-3d-figurine-with-googles-nano-banana-heres-how-you-can-hop-on-the-latest-ai-image-trend
    사진(사람·**펫**) → 책상 위 3D 피규어. 앱 무료(일일 한도), API는 장당 과금. 클라우드, 10초 안팎.
    맞는 점: **3D 털 질감 + 원본 특징 유지**가 가장 잘 됨 — 품질 상한 기준. 안 맞는 점: 사진 업로드, 키·과금, 매번 결과가 다름.
14. **Toonify Yourself** — https://gigazine.net/gsc_news/en/20200917-toonify-yourself/ (원 사이트 toonify.justinpinkney.com 은 2026-11-11 확인 시 응답 없음)
    StyleGAN 레이어 스왑으로 얼굴 → 디즈니풍. 서버, 트래픽 폭주로 한때 중단.
    맞는 점: 기법 역사 참고. 안 맞는 점: 사람 얼굴 전용, 서비스 불안정.

### C. 기기내/브라우저에서 돌릴 수 있는 모델·라이브러리

15. **White-box Cartoonization (TF.js 포트)** — https://github.com/pratapvardhan/cartoonizer-with-tfjs (데모 https://gramener.com/cartoonizer/)
    사진 전체를 셀 애니풍으로. **모델 약 1.5MB**, 256px 입력, CPU 브라우저 5–10초. 기기내.
    맞는 점: 작고 브라우저에서 돎. 안 맞는 점: 원본 모델 CC BY-NC-SA(상업 금지), 결과가 "만화 필터 사진"이지 캐릭터가 아님.
16. **vladmandic/anime** — https://github.com/vladmandic/anime
    AnimeGAN 계열을 TF.js WebGL로. 기기내.
    맞는 점: 브라우저 GPU 경로 예시. 안 맞는 점: 사람 얼굴 중심.
17. **AnimeGANv2 (PyTorch, Face Portrait v2)** — https://github.com/bryandlee/animegan2-pytorch
    `face_paint_512_v2` 등. 생성기 약 8MB, 512px. ONNX 변환본 있음(https://huggingface.co/spaces/akhaliq/animeganv2-onnx). MIT(코드).
    맞는 점: ONNX Runtime Web로 브라우저 실행 가능 크기. 안 맞는 점: 얼굴용, 동물엔 어색.
18. **MediaPipe Face Stylizer** — https://developers.google.com/edge/mediapipe/solutions/vision/face_stylizer
    얼굴을 정해진 스타일로. Web(`@mediapipe/tasks-vision`)·Android 지원, 기기내. 스타일 이미지 **1장**으로 재학습 가능.
    맞는 점: 우리 그림체 한 장으로 커스텀 스타일. 안 맞는 점: 사람 얼굴 전용(정면, 두 눈·입 필요).
19. **BRIA RMBG-1.4 (Transformers.js 배경 제거)** — https://huggingface.co/briaai/RMBG-1.4
    44M 파라미터, 브라우저 WebGPU 데모 있음. 기기내, 수 초.
    맞는 점: 지금 쓰는 "누끼"의 품질 개선용. 안 맞는 점: **비상업 라이선스**, 수십 MB 다운로드.
20. **MediaPipe Interactive / Image Segmenter** — https://developers.google.com/edge/mediapipe/solutions/vision/interactive_segmenter
    탭한 지점의 물체만 마스크. Web·Android, 기기내, 수백 ms, Apache-2.0.
    맞는 점: 펫을 탭 → 털 영역만 뽑아 색 샘플링(아래 21과 조합). 안 맞는 점: 마스크까지만, 스타일화는 안 함.
21. **Color Thief** — https://github.com/lokesh/color-thief
    이미지에서 대표색·팔레트 추출(JS, 수 ms). 기기내, MIT.
    맞는 점: **사진에서 털 색 2–3개만 뽑아 우리 털뭉치 템플릿에 입히기** — 가장 싸고 스타일이 안 깨짐. 안 맞는 점: 무늬(얼룩·줄무늬)는 못 옮김.
22. **Pixel It** — https://github.com/giventofly/pixelit
    사진을 지정 팔레트로 도트화(JS, MIT). 기기내, 즉시.
    맞는 점: 팔레트 고정 → 그림체 통일. 안 맞는 점: 도트풍은 우리 3D 털 톤과 다름.
23. **Meta Animated Drawings** — https://github.com/facebookresearch/AnimatedDrawings (데모 https://sketch.metademolab.com/)
    아이 그림 한 장 → 관절 잡아 걷기·춤 애니메이션. 오픈소스(Python), 2025-09 아카이브 추정.
    맞는 점: "아이가 그린 몬스터를 펫으로" 대안 입력. 안 맞는 점: 사람형 그림 전용, 서버 필요.

---

## 추천 조합

우리 몬스터 = 옅은 3D 털뭉치 + 점눈. **사진을 그림으로 "변환"하지 말고, 사진에서 "재료"만 뽑아 우리 템플릿에 입히는 쪽**이 그림체도 지키고 사진도 폰 밖으로 안 나간다.

| # | 조합 | 사용자에게 보이는 효과 | 왜 |
|---|---|---|---|
| ① | Q2-20 탭 분할 + Q2-21 색 추출 → 털뭉치 셰이더 색 · Q2-5 Mii식 "귀/꼬리 부품 고르기" | 우리 집 고양이를 찍으면 **그 고양이 털색의 털뭉치**가 태어남. 귀 모양만 골라줌 | 전부 기기내·무료·1초 미만. 그림체 절대 안 깨짐. 무늬는 못 옮기는 게 한계 |
| ② | Q1-23 "빼꼼" 라이브 배경화면 + Q1-26 미끼→알림 수집 | 홈 화면 가장자리에서 몬스터가 빼꼼, 미끼를 두면 나중에 "왔어요" 알림 | 잡기 게임 정서와 그대로 이어짐. 둘 다 사이드로드 권한 문제 없음 |
| ③ | 지금 가장자리 overlay + Q1-8 "홈에서만" 모드 + Q1-15 "전화·영상 땐 비켜줌" + 밤에 잠 | 앱 쓸 땐 안 보이고, 홈에선 걷고, 밤엔 구석에서 잠 | 기존 코드에 규칙만 추가. 하루 종일 켜둬도 안 거슬림 |
| ④ | Q1-13 펀치홀에 매달리기(보조 모드) + Q1-18 Finch식 상태 위젯(→ Q1-21 잠금화면 자동) | 카메라 구멍에 털뭉치가 대롱, 위젯엔 "지금 자는 중" 한 장면 | 공 모양이 매달리기에 최적. 단 상태바 위는 Accessibility 필요 → 옵션으로만 |
| ⑤ | 고급형: Q2-13 Nano Banana에 "우리 털뭉치 기준 이미지 + 사진" → 3D 털 피규어 1장 → 위젯·도감 카드용 | 내 강아지를 닮은 **고퀄 털뭉치 초상화** 1장 | 품질 최고지만 사진 업로드·과금·결과 편차. 동의 받은 1회성 "특별 카드"로만 |

추천 순서: ③(이미 있는 것 다듬기) → ①(사진 연결의 핵심, 싸고 안전) → ②(새 자리 하나 추가). ④⑤는 운영자 선택.
