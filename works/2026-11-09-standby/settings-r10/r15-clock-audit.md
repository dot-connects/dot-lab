# r15 E9 — clock layout audit (Fable, 2026-11-17)

Contact sheet of the problem cases: scratchpad `clock-audit.jpg`. Shots: `app/standby/build/shots/iphone/clock/`.
OK everywhere: DIGITAL, SOLAR, THIN, SPLIT, COLOR_BLOCK, FLIP, WALL, CALENDAR, DAY_BAR, RELIEF_DIGITAL, PHOTOS_TIME (no slot shot).
No clipping/overlap found. Rule for "portrait" below: height > width × 1.15.

## P1 — operator-named (portrait + M/St slots draw one landscape row at ~15–20 % of the screen)
1. STICKER (`PopFaces.kt` StickerFace): hours row over minutes row, the two digits = 80 % of width; colon dropped or small
   between rows; keep tilt/shadow; info pill bottom-centre. Same in M/St slots.
2. BALLS (`BallsFace`): hour ball(s) on top, two minute balls below; diameter = min((w − margins)/2.1, (0.8h − info)/2.2);
   no colon.
3. LED (`RetroFaces.kt` SevenSegmentFace): reuse the LCD portrait two-row digit geometry without the panel; digit height
   ≈ 0.3h; AM/PM + alarm lamps on one row on top.
4. RIM (`GaugeFaces.kt` RimFace): two-row digits like DIGITAL portrait, digits 0.7w wide; info row below; keep the 60 dots.

## P2 — small in some frames
5. WORLD (`WorldFace.kt`): portrait time = 60 % width, map full width; landscape non-bar (fold-cover-land) time ≈ 0.3h.
6. DOT_MATRIX: portrait switch threshold h > w×18/13 → h > w×1.1 (M slots stack too); landscape with w/h < 1.5: unlit
   dots over the whole frame and the digit band = 70 % height.
7. LCD: w/h < 1.35 → digits 85 % of panel width, info one row at the bottom; XSt stacks two rows.

## P3 — minor
8. HANGUL: w/h < 1.4 → flip-cover layout without the side panel; portrait centres the block vertically, text 90 % width.
9. DESK: fold-inner info lines as large as on flip cover.
10. ANALOG: dial = 80 % of the short side when there is no info.
11. XSt fallback (STICKER, BALLS, FLIP, WALL, CALENDAR, HANGUL, COLOR_BLOCK, SPLIT, DAY_BAR, RIM fall back to one small
    orange row): show "9 / 24" on two rows instead.
12. RELIEF / RELIEF_SQUARE portrait: weekday/date in the empty top/bottom, longer hands (low priority).
Missing shots to add while fixing: fold-inner-port, flip-small for all faces; bar-land/fold-inner for the relief faces;
slot shots for WORLD, DESK, PHOTOS_TIME.
