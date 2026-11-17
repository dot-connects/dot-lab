# r15 settings copy (from the Fable settings UX review, 2026-11-17)

Source of plan-r15 F7. Rewrite in place; brand: every user-visible 스탠바이/StandBy → "Stand Screen".

## One term per concept

| Mixed today | Use |
|---|---|
| 카드 더하기 / 도시 더하기 / 페이지 추가 / 내 시계에 넣기 / 시계 넣기 / 추가 | 추가 |
| 빼기 / 지우기 / 끔 / ✕ | from a list = 빼기, destroy = 지우기 |
| 꾸미기 / 사용자 지정 / 고치기 / 바꾸기 | look = 꾸미기, swap = 바꾸기 |
| 서체 / 글꼴 | 글꼴 |
| 위젯 페이지 / 폰 위젯 / 카드 | page name 기본 페이지; 위젯 only for home-screen widgets |
| 켜짐과 꺼짐 / 밤과 꺼짐 / 화면 꺼짐 | 자동으로 켜기·끄기 |

## Rewrites

| Where | Now | New |
|---|---|---|
| HomeScreen.kt:263 | 충전 중 스탠바이가 뜨려면 설정이 더 필요해요 / 8개 중 4개 완료 | 충전할 때 자동으로 켜지게 하려면 한 가지만 더 해 주세요 |
| Kit.kt:352 | 설정 한 가지가 더 필요해요 · 설정하기 | name the grant: "사진을 보려면 허용이 필요해요 · 허용하기" |
| Perms.kt:19 | 충전 중 스탠바이 띄우기 / 선택하기 | 충전하면 자동으로 켜기 / 켜러 가기 (+ "화면 보호기에서 Stand Screen을 골라 주세요") |
| Perms.kt:21 | 알림 띄우기 — 무드등과 알람 화면을… | 알람 화면 보여주기 — 알람이 울릴 때 큰 화면을 띄워요 |
| Perms.kt:23 | 알람 시각 미리 알기 | 타이머가 제때 끝나게 (exact alarm) |
| Perms.kt:26 | 밤새 멈추지 않게 하기 / 설정 열기 | 밤새 꺼지지 않게 하기 / 켜러 가기 |
| Perms FULL_SCREEN | 잠금 화면 위에 띄우기 | 화면이 꺼져 있을 때도 바로 켜기 |
| MoreScreens.kt:544 | N개를 켜면 스탠바이가 제대로 움직여요 | 아래를 허용하면 모든 기능을 쓸 수 있어요 |
| HomeScreen.kt:77 | 페이지 구성 · "페이지 8개 · 처음: 시계" | 페이지와 카드 · "8개" |
| HomeScreen.kt:86 | 안 씀 / 어두워지면 / 정한 시간에 22–07 | 끔 / 방이 어두워지면 / 밤 10시~아침 7시 |
| MoreScreens.kt:132 | 밤 밝기 30% · 밤에 화면이 넘지 않을… | 밤 밝기 (어둡게 ↔ 밝게) · 밤에는 이보다 밝아지지 않아요 |
| MoreScreens.kt:142-149 | 어두워졌다고 볼 밝기 · 어두움 정도 14 · 5초 이어지면… | 얼마나 어두우면 밤으로 볼까요 (조금만 어두워도 ↔ 캄캄할 때만); one slider |
| MoreScreens.kt:217 | 어두워지면 / 20초 뒤 / 계속 켜 둠 | 방이 어두우면 / 20초 뒤 / 끄지 않기 |
| MoreScreens.kt:228 | 밤에 알림 오면 화면 켜기 — 밤 화면일 때만요… | 밤에도 알림 오면 화면 켜기 — 꺼 두면 밤에는 알림이 와도 조용해요 |
| MoreScreens.kt:235 | 발열 보호 — 배터리가 42도를 넘으면… | 뜨거워지면 화면 어둡게 — 폰이 식으면 다시 밝아져요 |
| MoreScreens.kt:236 | 잔상 막기 — 1분마다 화면을 조금씩… | 화면 자국 막기 — 같은 그림이 오래 남지 않게 조금씩 움직여요 |
| MoreScreens.kt:283 | 스탠바이 화면 / 시계 앱 화면 | 큰 알람 화면 / 원래 알람 화면 |
| MoreScreens.kt:279 | 알람 소리와 끄기·다시 알림은 언제나 시계 앱이 맡아요. | 알람 시간과 소리는 시계 앱에서 정해요. |
| BoardScreens.kt:93 | 위젯 페이지 · 사진 · 시계는 맨 앞에 고정돼요… ▲▼… | 끈 페이지는 보이지 않아요. |
| BoardScreens.kt:109 | 두 칸 · 카드 쌓기 | 카드 6장 |
| BoardScreens.kt:378 | 칸 2 · 카드 3장 (위아래로 넘겨요) | 오른쪽 위 칸 · 3장 |
| BoardScreens.kt:414 | 한 번 더 누르면 지워져요 | dialog: "'작업 책상' 페이지를 지울까요?" |
| ClockScreens.kt:103,112 | 내 시계 · 4개 / 둘러보기 · 23개 | 쓰는 시계 / 모든 시계 |
| ClockScreens.kt:106 | 스탠바이에서 시계 페이지를 위아래로… 길게 눌러… | 시계 화면에서 위아래로 넘기면 다음 시계가 나와요. |
| ClockScreens.kt:155 | 내 시계 1번째 / 둘러보는 중 | delete |
| ClockScreens.kt:350 | 굵기 600 | 굵기 (가늘게 ↔ 굵게) |
| ClockScreens.kt:409 | 이 시계만 시간 표시 | 이 시계의 12시간/24시간 |
| ClockScreens.kt:412 | 꺼진 획 보이기 | 꺼진 숫자 흐리게 보이기 |
| Config FontId | 스텐실 / 슬랩 / 레일 / 기하학 / 굵은 장식 명조 | no names; the "12" sample tile is enough |
| Config BoardBg | 순검정 / 그래파이트 / 미드나잇 / 앰버 / 오로라 / 시간 따라 / 사진 흐림 | 검정 / 진회색 / 남색빛 / 호박빛 / 오로라 / 시간에 따라 / 내 사진 흐리게 (with swatches) |
| Config CardType TIMER | 타이머·뽀모도로 | 집중 타이머 |
| HomeScreen.kt:176 | ＋ 사용자 지정 / 저장 안 함 | ＋ 직접 만들기 / 저장 전 |
| ThemeEditor.kt:478 | 어디서 시작할까요 | 바탕이 될 테마 |
| ThemeEditor.kt:159 | 판 / 유리 / 테두리선 / 구분선 | 채움 / 유리 / 테두리만 / 줄만 |
| ThemeEditor.kt:129 | 필름 그레인 / 헤어라인 금속 / 서리 유리 | 모래알 / 금속결 / 뿌연 유리 |
| CardScreens.kt:96 | 설정할 게 없는 카드예요 | 이 카드는 바로 쓰면 돼요 |
| Preview.kt:359 | 옆으로 넘기면 다른 페이지가 보여요 · 누르면 전체 화면 | delete (page dots) |
| default page names | 모듈러 / 책 (펼침) | 한눈에 / 펼친 화면 |

## Word-slider ends (F6)

굵기: 가늘게 ↔ 굵게 · 크기: 작게 ↔ 크게 · 밤 밝기: 어둡게 ↔ 밝게 · 밤 감지: 조금만 어두워도 ↔ 캄캄할 때만 ·
카드 진하기: 비치게 ↔ 진하게 · 모서리: 각지게 ↔ 둥글게 · 질감 정도: 옅게 ↔ 짙게.
