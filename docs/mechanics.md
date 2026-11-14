# Mechanic ledger (T-0162)

Operator rule (2026-10-20): every work uses a mechanic no earlier work used. Reusing a liked mechanic with a new
subject ("draw an apple") is a reskin and does not ship. A mechanic = the hand's verb + what decides the score.
Score is optional (operator 2026-10-26); a work can leave a keepsake instead. Replay recipe (docs/archive/2026-10-17-app-admob-research/20): 0-tap retry, one number + best, a visible reason for
failure, comparison, a funny label, the player's own input decides the result.

## Ledger

| Mechanic (verb -> score) | Status | Where |
|---|---|---|
| draw from memory -> outline distance | **used** | #001 draw-korea (passed 2026-10-20) |
| lever / brake to a target in time | used, failed (no replay) | #0 earth-stop |
| watch a simulation (no input) | failed | earth-stop v0 |
| tap when N seconds pass (inner clock) | avoid: "seen too often" | sketches |
| bracket vote between two cards | avoid: no reaction | sketches |
| type recall against a list (Sporcle) | rejected: "no fun" (operator 2026-10-20) | was candidate 5, Seoul 25 gu |
| estimate a number on a log slider | sketch liked ("OK") | candidate 9 |
| flick to toss, tilt/drag to catch -> one-bowl score /100 | **used** (#002 ready 2026-10-26, operator picked 2026-10-23) | works/2026-10-23-kimchi-fried-rice |
| flick to flip a cube, cook all faces | dropped: "too close to Mario Party" (operator 2026-10-23) | works/2026-10-22-steak |
| hold to pour, release at a hidden line (라면 물 550ml) | rejected: "no fun" (operator 2026-11-01) | was candidate 1 |
| pinch to size next to a person (대왕고래 실제 크기) | rejected: operator 2026-11-01 | was candidate 4 |

## Candidates (each a different mechanic; 2026-10-20)

Operator 2026-11-01: none of these appeal. Kept for the record; do not pitch them unless asked.

| # | Work | Mechanic | One number | Replay | Data |
|---|---|---|---|---|---|
| 2 | 내 간은 어디 있나 | pin a point on a body from knowledge | cm off per organ | 6 organs, best | textbook diagram, simplified |
| 3 | 피자 8등분 | swipe cut lines | spread of slice areas % | new shape each round | geometry only |
| 6 | 진짜 태극기 고르기 | pick the real one among near fakes | streak | new fakes each round | flag law diagram |
| 7 | 비싼 순서로 줄 세우기 | drag to sort | pairs right % | new set | price survey (source) |
| 8 | 더 많을까 적을까 | binary compare, streak | streak | endless pairs | public stats (source each) |
| 9 | 이상한 숫자 10문제 | estimate on a log slider | mean ratio off | 10 questions, daily seed | constants table |
| 10 | 행성에서 던지기 | drag to aim + power, parabola | landing distance off | gravity changes | planet g table |
| 11 | 2호선 손 안 떨기 | trace inside a corridor | % of line done | wall = instant retry | line geometry (OSM) |
| 12 | 시도 퍼즐 | drag + rotate jigsaw pieces | seconds | timer best | admin boundaries; overlaps #001 topic |
| 13 | 평생 출퇴근 환산 | type my numbers -> converted card | days of life | weak (calculator) | commute stats (source) |
| 14 | 지옥철 버티기 | one-thumb dodge | stations survived | endless | none |
| 15 | 한식 조합 도감 | combine two -> discover | found /N | collection | own recipe tree (large) |
| 16 | 이삿짐 트럭 싣기 | pack boxes into a fixed space | fill % | new boxes | 1-ton truck size (source) |
| 17 | 착시 이기기 (2026-10-26) | slide until two parts look equal | % fooled | new illusion each round, best | classic illusions with a measurable truth (Müller-Lyer, checker shadow, colour contrast); feed post = the illusion still as hook, share card = % fooled + link |
| 17 | 3초 보고 없어진 것 | memorise a shelf, find the missing item | found count | shelf grows | own drawings |
| 18 | 내 하루 24시간 나누기 | split a fixed total across sliders | distance from the national average | change and compare | time-use survey (source) |

Pick for the next (Claude's view): 8 (streak, cheap to build, data-driven).

## Known thing x device input (2026-10-21, operator)

Study: docs/archive/2026-10-17-app-admob-research/23-build-creator-references.md (C:\DC\DD). Operator direction: a thing
everyone knows (IP feel, nostalgia, fad, date) x an input the phone or laptop browser has (camera hand tracking,
motion, mic), and the link plays at once - reference creators' builds are watch-only. Copyright is not a constraint on this
channel (operator 10-21, repeated): use IP characters and names freely, no merchandise. Operator 10-21: keep every candidate parked in this ledger; build only when the operator picks one.

| # | Work | Known thing | Mechanic (verb -> score) | Status |
|---|---|---|---|---|
| 1 | 주사위 스테이크 6면 굽기 | Mario Party "Sizzling Stakes" feel | flick the phone to roll a cube, cook every face by sound/vibration -> seconds, none burnt | dropped 10-23: too close to Mario Party |
| 2 | 허공 달고나 | Squid Game dalgona | camera fingertip traces the outline, shaking cracks it -> % carved | **#004 done** (manifest ready; classic Squid Game, timer, crack = out) works/2026-11-04-dalgona |
| 3 | 계란후라이 뒤집기 + 웍질 볶음밥 | flipping an egg, 중국집 wok toss | flick to toss rice (bottom burns if idle, too hard spills), then toss and catch a fried egg -> one-bowl score /100 | **#002 ready** works/2026-10-23-kimchi-fried-rice |
| 4 | 절대 안 지는 가위바위보 | Janken robot meme | camera reads your hand, AI answers at the last moment -> how long you last | rejected: "no fun" (operator 2026-11-01) |
| 5 | 손으로 찍는 인생네컷 | photo booth, 셋로그 (2 s clips, 2026) | hand-frame gesture shoots 4 cuts, frames swap with the trend. 10-26 brainstorm: (a) memory mode - 4 cuts each keep the 2 s before the shutter, saved as a moving 4-cut strip (mp4) + image; (b) pose-hole relay - friend's pose outline (~150 chars in the link, no face) becomes a hole, fit your body -> overlap %, default set for solo play. No backend: photos/video travel as saved files, only outlines fit a link. First: in-app camera/record/save spike | **#003 shipped** as 따로 같이 네컷 (take a photo beside a friend's cut-out via link) works/2026-10-26-photo-booth |
| 6 | 내 방에 뭔가 잡기 | "2026 is the new 2016" / Pokemon GO feel, own creatures | flick to throw over the camera view -> caught /N | merged into #9 (2026-11-09) |
| 7 | 수능 실전 시계 | 수능 (mid November) | real timetable bells and announcements | rejected: "no fun" (operator 2026-11-01) |
| 9 | 우리 집 몬스터 잡기 | Pokemon GO | throw a ball at your pet/person on the camera; segmentation cuts them out of the view and they land in an in-phone dex -> caught /N. Operator 10-21 extension: a subject that stays still cannot be caught - keep recording while it moves (motion fills the catch gauge), then turn the clip into a 3D character that lives on the phone screen; if it works, swap skins to Chiikawa-style characters. Browser ceiling: cut-out living 2.5D sticker; true 3D needs a GPU server (new PC), staying on screen over other apps needs a native app (tap-trigger Android base). 2026-11-09 brainstorm (merged with #6): swipe a curve ball on screen at a shrinking ring -> catch chance from the throw only (50-95%); Pokemon/Chiikawa AR creatures plus real pets and people (no consent gate for now, eye-contact gate later); each catch keeps the pose it was caught in, more poses unlock more floating-pet moves (step 2, Android) | **draft built (2026-11-10)**, operator phone check pending — works/2026-11-09-catch/ (brief.md, plan.md, site/, app/) |
| 8 | 폰 두 대로 펼치는 폴드 | Fold "iPhone Duo" reel | two phones join one screen | hard (needs pairing server) |
| 10 | 흐린 사진 맑게 바꾸기 | bad-weather travel and outing photos | pick a photo -> the sky changes and the buildings and people lit by it change too; keepsake, no score. Overcast light has no direction, so real shadows need image generation. Next step: convert the same overcast photo both ways (on-device sky swap + grade vs generative relight), then the operator picks after comparing | stopped 2026-11-01: on-device relight spike looked like a colour grade, no real building shadows; those need paid image generation and the operator wants it free. Research kept in works/2026-11-01-sunny-photo/research |
| 11 | 카드 실적 자동 확인 | card spend targets, each with its own dates | Android app reads payment SMS and card-app notifications -> met / how much is left per target. Same card can have different windows for card benefits and for the loan-rate discount, so each target gets its own period. Operator chose: sideloaded app for own phone first (like tap-trigger; public release judged later), major cards built in + manual rule entry. Utility rather than a replay work; browser cannot read SMS | parked (operator 2026-10-31) |
| 12 | 내 사인 만들기 + 받아쓰기 | personal signature, company name | operator 2026-11-01: a signature that only spells the name is not special. Six structures from real signatures, mapped to Hangul: long ㅡ as backbone (Joseon sugyeol), dots plus one long slash (Picasso), first letter holds the rest (Bowie), initial-consonant seal (Dürer), half-turn reading such as 문 to 곰 (ambigram), name hidden in a picture (munjado). Then trace and write from memory. Plain stroke joining was rejected as scrawl; plain fonts as too much of just a name. Operator's picks from 41 real signatures share an even felt-tip line, a big first letter, a wave for the rest and one long stroke. Motifs chosen: a letter becomes the number (ㄱ as 7), a long stroke ends in a drawing. Inputs chosen: name written by hand (slant, size, speed measured), birthday, handedness, mood (loop, slash, long stroke), MBTI as the emphasised hook, hobby, job. Favourite colour dropped. Stopped after about 10 rounds: joined strokes, fonts, generated one-line pen, refined handwriting, font plus pulled strokes, six layouts from Kmong structures, per-name emphasis. Final verdict on 60 samples: feels like lines drawn over a name. A professional redraws the letters and the stroke as one form for that name; free automatic rules did not reach it. Revisit only with a new letter source such as a licensed cursive jamo set or per-name design by a person | **parked** (operator 2026-11-01, not good enough) |
| 13 | 갤럭시 StandBy + 폴드 무드등 | iPhone StandBy, iPhone Duo wake glow | charging folded Fold8 shows clock·music·calendar·photo pages on the cover screen; slightly opened inner screen is a mood light, Samsung alarm brings it up. Native Android app (Kotlin); first build is a measuring APK on Fold8 (cover DreamService, hinge angle, alarm moment). Personal use + channel intro reel. Research: `C:\DC\DD\ideas\applied\I-0053-galaxy-standby-moodlight.md` | **picked 2026-11-09** (operator) |
| 14 | 폰 물수제비 | skipping stones at a river | hold the phone like a stone with a full grip; sidearm swing and stop (operator 11-04: no thumb-lift release, it risks dropping the phone). Release = the peak of the swing's rotation rate just before the stop; motion sensors read swing speed, tilt at release (Clanet 2004 Nature: about 20 deg to the water skips best) and wrist spin -> skip count + distance, simple model from published stone-skipping physics with its assumptions on screen. Haptic tick per skip on Android (iOS Safari cannot vibrate). Intro line "폰을 실제로 던지지 마세요" is the hook, also a real safety line. Same sensor family as #002 toss, but the score comes from angle + release timing, not a flick | parked (operator 2026-11-04) |
| 15 | 카메라로 방 평수 재기 | 원룸 광고 평수 vs 실평수, 줄자 앱 | sweep the camera around the room -> floor outline + 평수, compared with the listed size / a friend's room. Android Chrome WebXR hit-test gives real metres; iOS Safari has no WebXR, fallback = depth model + a known-size reference (A4, card) with lower accuracy. Score-free keepsake card | parked (operator asked 2026-11-14) |

## Parked 2026-11-01 (from docs/archive/2026-11-01-lab-references in C:\DC\DD)

Complaints, small twists and trends. Not picked yet.

| Work | Reference | Mechanic |
|---|---|---|
| 가짜 X 닫기 | fake close buttons, hidden reject buttons | thumb-hunt the real close -> time, fakes hit, thumb reach map |
| 합격 염주 | 석가머니 beads app, 수능 | friend's photo as a bead, roll by finger -> wish card |
| 포차 건배 | 온라인 담타, SlapMac | knock two phones -> sound, strength |
| 정확히 반 접기 | Center This Div | drag to the exact centre -> error rank |
| 가짜 택배 조회 | 음식만안와요 | "delivered" but the door is empty, find clues -> ending |
| 찐친 동시 탭 | 친한 친구 이름 대기 | choose at the same moment with a friend -> match or betrayal; needs a server |
| 해지 미로 | subscription dark patterns | find the cancel path -> receipt of steps and seconds |
| 썩어 가는 짤 | KakaoTalk image compression | each friend's pass degrades the photo -> people passed; needs a server |

## Problem-solving tools (2026-11-01)

Operator 2026-11-01: try solving real inconveniences ahead of fun. Replay here means using it again next time. Evidence: docs/archive/2026-11-01-lab-references/04 and 05 in C:\DC\DD.

| Work | Inconvenience | Tool | Evidence |
|---|---|---|---|
| 모임 결산 한 장 | treasurer's spreadsheet goes unopened, gets screenshotted anyway | enter money in and out, attach receipts -> one image for the group chat + read-only link | 12 posts on 4 sites, posts up to 208k views |
| 차등 N빵 | fights over who pays for drinks | tap who ate what -> three split results side by side, vote by link | Clien post 31k views, 44 comments |
| 부모님 따라하기 링크 | parents stall at remote-app authorization | child picks task and phone model -> big-text step link | 4 Clien posts, 1.3k-22k views |
| 경조사 장부 | gifts given and received are scattered | one-line entry -> totals per person, stored only in the browser | 7 posts |
| 제출용 사진 변환 | government sites reject size and format | re-encode locally to the entered limits | 5 posts; 1.2% of reviews and many free tools exist, sources disagree |
| 거주사실조사 위치 점검 | Gov24 location check fails | show location permission and accuracy; not the app's own verdict | 18% of 199 low Gov24 reviews, seasonal |
| 알림 점검 순서 | school and bank alerts do not arrive | checklist per phone model and app | 14.9% of 94 low 아이알리미 reviews |
| 기프티콘 만료 모음 | vouchers expire unnoticed | images + dates -> sorted list and calendar file | 4 posts |
| 이사 체크리스트 | missed address changes | move date -> tasks with deadlines; no legal claims | 3+ posts |
| 주차 층 찾기 (operator idea) | forgetting the parking floor | native widget: Bluetooth disconnect + barometer drop from the last outdoor point -> estimated floor, pillar photo as fallback. Not a web work: needs an Android app; iOS needs a Mac. In progress: measurement app at works/2026-11-02-parking-floor, not published, garage accuracy unmeasured | 3 Clien posts, up to 4k views |
| 여행 사진 기록 (operator idea) | after a trip, photos and the places they were taken are hard to piece back together into a record | photo taken -> its location is kept; merged with the Google Timeline route so the trip can be written up later. Form undecided (app or program). Check first, from memory and unverified: Timeline moved on-device with no public API, so the likely route is a manual export file; Android may strip photo GPS when a browser picks the file, which would push this to a native app | operator 2026-11-01; not researched |

## Device-input ideas, second batch (2026-11-01)

Not checked against references yet.

| Work | Known thing | Mechanic |
|---|---|---|
| 김 서린 창문 | writing on a fogged window | blow at the mic to fog, write by finger, send by link, the message fades as it arrives |
| 입으로 끄는 생일 촛불 | birthday candles | friend's link has their age in candles, blow at the mic -> breaths used |
| 노래방 99점 | karaoke score screen | sing into the mic -> real pitch score, retro fanfare |
| 웃음 참기 | no-laugh challenge | camera watches your face while funny clips play -> seconds held |
| 고양이 눈싸움 | staring contest | camera detects blinks -> seconds held, friend's record as rival |
| 손 그림자 놀이 | hand shadows | camera hand shape casts a shadow -> matched shapes |
| 색 사냥 | treasure hunt | find an object of the target colour in your room -> seconds |
| 딱지치기 | 딱지, Squid Game | swing the phone to flip a friend's 딱지 by link -> flips; check overlap with #002 |

## Useless programs (2026-11-01)

Mockups of the phone-idle and phone-nap directions: https://claude.ai/artifact/N5BoztnfwrXRMLtDbV36Cw (operator moved on 2026-11-01, not rejected).

Rule from Chindogu: it really works, and nobody needs it.

| Work | Reference | Mechanic |
|---|---|---|
| 폰 멍때리기 대회 | 한강 멍때리기 대회 | leave the phone still while fake alerts tempt you -> seconds held |
| 폰 재우기 | lullaby | rock the phone gently, shush at the mic -> it falls asleep, too rough wakes it; check overlap with #002 shaking |
| 임금님 귀는 당나귀 귀 | Korean folk tale | shout a secret at the mic, only loudness is measured, nothing is kept -> hole depth |
| 손으로 돌리는 충전기 | crank flashlight | crank a circle by finger -> real energy needed for 1% of battery |
| 손으로 돌리는 시계 | wind-up clock | time moves only while you wind it -> how far behind you are |
| 로딩 밀어 주기 | a bar stuck at 99% | push the bar by hand, it slips back -> how long you lasted |
| 초정밀 사물 판별기 | Not Hotdog (from memory) | point the camera, long serious analysis -> "이것은 컵입니다" |
| 모두의 버튼 | One Million Checkboxes, r/thebutton (from memory) | one nationwide counter; needs a server |

Dropped from the 10-21 artifact sketch: dalgona-by-finger-on-screen and the bungeoppang tycoon (operator: too simple).
