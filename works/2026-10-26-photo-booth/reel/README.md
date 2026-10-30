# 광고 릴스 「따로 같이 네컷」 (draft 9, 2026-10-30)

21.4초, 말랑부스 톤. 출연자는 AI로 만든 두 사람이고(Codex 이미지 생성), 화면 속 앱 장면은 그 사진을 카메라로 넣고 실제 앱을
돌려 얻은 것이다. 남은 일: "꾸미는 것도 마음대로!" 목소리(Leda+, 내일) → `mix8.py` 다시 → 합치기.

| 초 | 장면 | 자막 = 목소리 |
|---|---|---|
| 0–2.4 | 위아래 분할: 나(카페) / 단짝(집), 둘 다 손 흔듦 | 나는 카페, 넌 집인데 |
| 2.4–5.6 | 친구 폰 카톡 "야 같이 사진 찍자!" → 링크 카드 → 탭 → 앱 첫 화면 → 바로 찍기 | 같이 사진 찍자고? 지금? |
| 5.6–10 | 친구 카메라: 혼자 → 단짝이 누끼로 옆에 → 3·2·1 찰칵 ×2 (친구만 움직이고 단짝 포즈는 장마다 바뀜) | 열자마자 네가 내 옆에 있어! |
| 10–13.8 | 앱의 메이킹 영상 (누끼 테두리 없음) | 따로 찍었는데 / 한 장에, 같이 |
| 13.8–18.2 | 핀에 걸린 네컷이 흔들리며 5가지 꾸밈으로 톡톡 바뀜 | 꾸미는 것도 마음대로! (목소리 없음) |
| 18.2–21.4 | 끝: 러블리로 꾸민 네컷 + 가입 없이 · 7일 + 프로필 링크 | 같이 찍을 친구 태그해! |

## 만드는 순서
미디어(ai/, fonts/, sfx/, voice5/, take*/)는 git 밖이다(.gitignore). 이 폴더에만 있다.

1. `python gen.py base A|B`, `python gen.py pose A|B 0 1 2 …`, `gen2.py`: AI 출연자. 기준 사진 1장 → 같은 얼굴·같은 자리 포즈 편집.
   Windows 의 `codex.cmd` 는 여러 줄 인자를 첫 줄에서 자르므로 프롬프트는 stdin 으로 넘긴다(`-`). 손 흔들기 같은 움직임은
   "ONE FRAME LATER, keep the camera EXACTLY" 로 같은 구도 컷을 여러 장 만든다.
2. 포즈 사진을 `site/dev-media/reel/` 에 복사(이것이 가짜 카메라 = 촬영 중인 장 번호의 포즈).
3. 로컬: dot-lab 루트를 8799 로 서빙, `worker/` 에서 `npx wrangler@4 dev --port 8787`.
4. `UI=cute NO_OUTLINE=1 DECO=1 node rec.mjs 9`: A→B 한 판. 친구 카메라 위치·단짝 누끼 PNG(geo*.json/png), 앱 화면 스틸,
   움직이는 네컷·메이킹 mp4, 꾸미기 상태 캡처를 `take9/` 에 남긴다.
5. `node deco.mjs <B 프로필 폴더> take9 looks3`: 꾸민 조합 5개(배경·프레임·필터·테두리·문구·스티커)를 앱 컨트롤로 만들고 캡처.
6. `python sfx.py` → `sfx/`.
7. 목소리: content-system 에서 `.venv/Scripts/python.exe <여기>/voice_samples.py <여기>/voice5 Leda` (STYLE·TAG 환경변수로
   밝고 어린 톤, Gemini 무료 하루 약 10회를 다른 채널과 나눠 씀).
8. `python compose9.py take9 x.mp4` → `v9-video.mp4` + `v9-timeline.json` (화면만, 30fps, 장면 사이 0.32초 전환).
9. content-system 에서 `VDIR=voice5 TL=v9 .venv/Scripts/python.exe <여기>/mix8.py take-Leda-young` → 대사를 whisper 로 맞춰
   자르고 음악(lab 의 pop-pop-music)·효과음과 섞음.
10. `ffmpeg -i v9-video.mp4 -i v9-take-Leda-young.m4a -map 0:v -map 1:a -c copy -shortest out/reel.mp4`

절대 경로로 쓰는 외부 파일: 음악 `content-system/private-assets/music/`, 제목 글꼴 `works/2026-10-23-kimchi-fried-rice/reel/BlackHanSans.ttf`,
링크 미리보기 `site/og.png`. Dongle·Gowun Dodum 은 Google Fonts(OFL) 에서 받은 것.
