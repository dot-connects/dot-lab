# 리얼뽑기 0.5 — 혼잣말 · 위젯 · 자랑하기 · 앱 고르기 고정

2026-11-16, operator picks from mockups `mockups/features.html` (A / ABC / A) and `mockups/picker.html` (A, 가).
Scope: Android app `app/` only. No new permission. No link anywhere until the Play listing exists.

## Decisions (operator 11-16)

| # | Thing | Pick |
|---|---|---|
| 1 | Talk bubble | A cloud (same paper cloud as the app cloud). On a wall: 가, beside the pet toward the screen middle, puffs in a row |
| 1 | Lines | the mockup table as the start set |
| 2 | Widget | all three sizes: A 2×2 yard, B 4×2 yard + line + [뽑기][도감], C 1×1 sticker |
| 3 | Share card | A 4:5, no link before release (app name only) |
| 4 | App picker | A: chosen apps pinned as one chip row above the list, tap a chip to remove |

## 1. Talk (혼잣말)

- `pet/Talk.kt`, pure Kotlin: `Talk.pick(now, hour, situation, last, rnd): String?` and the line table.
  Slots: morning 5–11, noon 11–17, evening 17–22, night 22–5. Situations, ahead of the slot lines:
  `NEW_FRIEND(name)` (a dex count rise since the last talk), `MISSED` (app not opened for 3 days),
  `CHARGING` (power connected in the last minute). `{name}` = the shown pet's dex name.
- When: `ACTION_USER_PRESENT` (unlock) or the pet window becoming visible on home → 30 % chance.
  Never within 10 min of the last line, never over 12 a day, never the same line twice in a row.
  Not while asleep, held, falling, pondering, with the app cloud open, or `upHigh` (in another app).
- `pet/TalkBubble.kt`: an overlay view like `AppBubble` (same paper and shadow, bold 13 sp text,
  max 2 lines). Floor: above the pet, two puffs down to its head. Wall: beside the pet toward the middle,
  two puffs in a row (가). Shown 4 s, a tap closes it and the pet smiles. Not touchable outside its box.
- State in `PetPrefs`: `talkAt`, `talkDay`/`talkCount`, `talkLast`, `seenCount` (dex size at last talk),
  `openedAt` (MainActivity onResume).

## 2. Widgets

- Three `AppWidgetProvider`s: `PetWidget2x2`, `PetWidget4x2`, `PetWidget1x1`, one shared renderer
  `widget/WidgetArt.kt` that draws a Bitmap (yard by time slot, pet cell by slot: morning front, noon smile,
  evening front, night sleep; 4×2 adds name, `Talk` slot line, two pills). RemoteViews = one ImageView,
  plus two invisible click areas on 4×2 ([뽑기] → catch, [도감] → dex via `MainActivity` extras).
- Refresh: `updatePeriodMillis = 1800000`, plus a broadcast on pet change, on a catch, on name change.
- No pet chosen: the yard with a grey placeholder and "털뭉치를 뽑아 주세요".
- Bitmap sized from `AppWidgetManager.getAppWidgetOptions` min width/height × density, capped at 600 px.

## 3. Share card (자랑하기)

- `share/ShareCard.kt`: draws a 1080×1350 Bitmap — sky/grass, title, "date · kind" line, the open
  two-tone capsule with the pet (critter front cell, or the cut-out photo), a footer "리얼뽑기 · 찍으면 캡슐에 담겨요".
  The capsule drawing moves from `CatchScreen.drawCapsule` into `ui/CapsuleArt.kt` so both use one shape.
- Capsule colour: stored on the dex entry (`cap` in meta) at catch time; old entries use `CAP_COLORS[no % n]`.
- Share: PNG in `cacheDir/share/`, `FileProvider`, `ACTION_SEND` image/png + text
  "리얼뽑기에서 {name}을(를) 뽑았어!". No link.
- Entry points: catch screen — the win toast stays 6 s with a [자랑하기] pill (hit-tested in the game);
  dex detail — a [자랑하기] pill under the picture. A dex entry holding a person asks once more before sharing
  ("사진 속 사람에게 보내도 된다고 물어봤나요?").

## 4. App picker

- `AppPicker` becomes `BookPage(scroll = false)`: header, then a pinned chip row (count `n/8`, tap = remove),
  then the list in its own `LazyColumn` (`Modifier.weight(1f)`), chosen ones first (already in 0.4.9).

## Tests (TDD where it pays)

- `TalkTest`: slot by hour, situations first, cooldown 10 min, day cap 12, no repeat, nothing while blocked.
- Everything else (drawing, widgets, share intent) is checked by building and running on the phone.

## Tasks

1. Picker pinned row (PetScreen) — small, first.
2. `Talk` + tests → `TalkBubble` → PetService hooks (unlock, power, visible) + prefs.
3. `CapsuleArt` extract (no visual change) → `ShareCard` → FileProvider + share → catch toast pill → detail pill → `cap` on entries.
4. `WidgetArt` → three providers + XML info + layouts → refresh broadcasts.
5. Version 0.5.0, build, unit tests, APK to the operator, /done review.

## Review 11-16 (one Opus reviewer; operator to run /plan-review if wanted)

- H: the pet is usually asleep at unlock / plug-in → an unlock or plug-in wakes it, then it speaks.
- H: `Dex.write` rebuilds JSON from `Entry` → `cap: Int?` is a real `Entry` field (read + write); the colour is
  picked before `sink.onCaught` and passed in; a critter met again keeps its first colour.
- H: the win toast comes after `reset()` cleared the cut-out → the game keeps a `Brag(name, kind, bmp, critterId, cap)`
  from the moment of the catch until the toast ends; sharing never waits on the dex write.
- H: both share paths ask about people, judged by `Dex.hasPerson(kind)`.
- M: the toast pill is hit-tested before aiming; a new toast replaces it; widgets refresh from the writes executor
  after `Dex.add`, on delete / sheet / style / pet change and `onAppWidgetOptionsChanged`; 4×2 buttons are real
  TextViews with distinct immutable PendingIntents; `MainActivity` reads `open=catch|dex`.
- M: the talk cloud follows the pet and closes in `hide()` / screen off; receiver branches per action.
- L: a real thing's card shows its cut-out photo (`poses[0]`); slot changes may show up to 30 min late on widgets.
