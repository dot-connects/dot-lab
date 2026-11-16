# 리얼뽑기 — 광고 릴스 (spec)

2026-11-16. Operator picks: free live-action stock footage + our app scenes redrawn, "pro-made" look,
posted BEFORE the Play listing, as soon as the closed test's opt-in link works; CTA = "안드로이드 먼저 해 보기 · 프로필 링크"
→ lab site landing page (spec-release §9) → testers. Paid real-pet sheet shown as a small line at the end.
Reference study: 10 real ads (Clawee, DOLLPICK, 캐치돌 TVC, Shijima, Xiaomi pet widget, Pocket Pals,
Pokémon GO ×3); notes in `research/reel-refs.md`. Process lessons: memory `feedback_lab_ad_reel_process`.

## What the references taught (applied)

1. Real scene first; app UI late, small and short (Clawee 3 s, Pokémon GO KR 11 s for 3 s).
2. Magic moment = the thing on the screen crosses into real life (dolls pour out, pet sits on a palm,
   characters walk over a real web page). Ours: the real cat goes in → a fluffy pet walks out onto the home screen.
3. One short promise, three beats. No explanation, no feature list.
4. Second beat = collection (DOLLPICK: catch → card → dex).
5. End = name + store badge + one thing you get. No participation question, no long credits.

## Flow (≈20 s, 9:16, 30 fps)

| s | Picture | Caption = voice |
|---|---|---|
| 0–2.5 | Live action: a cat asleep on a sofa in warm window light, slow push-in; a hand with a phone rises into frame | 우리 집 고양이를 |
| 2.5–6.5 | Phone screen fills the frame (screen replacement on a stock "hand holds green-screen phone" clip → match cut to full screen): our claw drops, wraps the cat, the cat kicks, lifted | 뽑아 버렸다 |
| 6.5–9 | Two-tone capsule falls, pops open in a burst → dex card fills "도감 N" (1 s) | 캡슐을 열면 |
| 9–15.5 | The cat's own fluffy pet (real Worker sheet of the stock cat) walks along a real-looking home screen edge, climbs the wall, tapped → smiles, talk cloud | 털뭉치가 돼서 내 폰에 산다 |
| 15.5–18 | Live action: the real cat paws at the phone where the pet is walking (match on the paw) | (sfx only) |
| 18–20.5 | End card in the app's sky/grass look: pet in the widget, 리얼뽑기, "안드로이드 먼저 해 보기 · 프로필 링크", small line "테스트 기간 우리 아이 펫 그림 3장 무료" | 리얼뽑기 |

After the Play release a second cut swaps only the end card (Google Play badge, "우리 아이 펫 그림 500원 · 털뭉치 16마리 무료").

The 15.5–18 shot loops into 0 s visually (same cat, same sofa).

## "Pro-made" look — the kit

| Element | How |
|---|---|
| One grade | warm, soft contrast LUT-like curve applied to every stock clip so they read as one shoot |
| Camera moves | slow push-in / parallax on every still moment; nothing sits static > 0.5 s |
| Match cuts | phone screen → full screen; real paw → pet paw; cat shape in camera → cat in claw |
| Real depth | stock clips with shallow depth of field; our redrawn screen gets a subtle glass glare when inside the phone |
| Motion blur | on claw drop, capsule pop and whip transitions (render at 120 fps, blend to 30) |
| Sound design | claw motor and capsule pop from the app's own `Sfx`, a soft whoosh per cut, music cut on the beat |
| Type | heavy wide two-line captions in the app's fonts (Jua / 고운돋움), one hero per frame (lesson: "AI 같다" = small type, empty frame) |

## Footage and sources

- Stock: Pexels / Pixabay video (free commercial use, no attribution required; keep the URLs in `reel/SOURCES.md`).
  Needed: cat asleep on a sofa (warm light), the same or matching cat pawing at something, a hand raising a
  green-screen phone in a living room. Prefer clips where the cat in shot 1 and shot 5 can be the same animal.
- Pet: run the real app pipeline on a frame of the stock cat (cut-out → Worker `/sheet` fluff) so the pet in the reel
  is exactly what the app makes. Free critters only as a fallback.
- App scenes: redrawn at 30 fps from the app's geometry and assets (claw, capsule art, dex card, pet sheet cells,
  talk cloud), not screen-recorded (no phone on the PC; low-fps recordings read as broken).
- Home screen: a clean generic Android home layout drawn by us (no real brand icons).

## Production steps

1. Collect 6–10 candidate clips per shot, contact sheet → pick (Claude), save sources.
2. Storyboard mockups: 3 HTML variants (phone portrait, Artifact) differing in grade/type/pacing, real frames inside.
   Operator picks; picked elements are fixed for the render.
3. Pet sheet from the stock cat (Worker, debug token).
4. Render: HTML/canvas scenes with a fake clock at 30 fps (techniques: fake-clock recording) + ffmpeg composite of
   stock clips (grade, screen replacement by chroma key, motion blur).
5. Voice: bright young female voice samples on the video, operator picks (Gemini TTS free quota ~10/day, shared).
6. One review panel (6 personas on 0.5 s frame sheets), then fixes.
7. `out/reel.mp4` + cover frame → `manifest.json` (status ready only once the opt-in link works) → content-system posts.

## Checks

- First 2 s: is it clearly "my real cat" (not an app screen)? Frame sheet review.
- Smoothness: `mpdecimate` unique-frame count, scene-change jumps measured.
- Caption contrast measured on the busiest frame.
- No "AI" word in captions or end card (lab copy rule).
