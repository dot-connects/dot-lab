# Round 7 — operator phone run (2026-10-28)

| # | Item | Root cause / decision | Status |
|---|---|---|---|
| 2 | "‹ 테마" back to the theme pick on A's cover + mode pick (before shooting only) | button in both topbars (cute: pill) | done |
| 3 | Retouch review: the pick's background buttons, choice carried over | `#revBg`, same `S.pickBg`, redraws the 4 cells | done |
| 5 | 6 shots instead of 8, still pick 4 | copy / counters / pips / cover / grid 2x3; Worker unchanged (never counted shots) | done |
| 6 | Empty white chip with a ✕ over the camera | not empty: film's dark shoot screen sets `--ink: #fff`, the white chip took it (white on white, contrast 1.0) | done |
| 7 | Remove all moving / scaling of A | mover, switch, layout.json upload, A's own size/place gone; old layout.json ignored; new links never movable (Worker 403) | done |
| 8 | B's 보정 did not change B's review | compose made a WebGL context per person object; B's pick grid + review make fresh objects per cell -> 135 contexts; at the phone's ~8 live-context budget Chrome dropped the oldest (face landmarker) -> no faces -> unchanged. Repro: `--max-active-webgl-contexts=8`, B diff 0 % (17 lost); fixed: one context per side | done |
| 9 | Decorate 보정 tab: my own photo disappears | (a) a one-finger drag of ~50 px on the small preview moved B +0.44 of the cell, out of it (B stands near the edge); drag now keeps B's anchor inside the cell. (b) the context churn above (each 보정 pick = 2 new contexts) | done |

Open: B's live overlay of A (shoot view) shows A's cut-out on a rectangle of A's room in headless runs (before and after
this round) — check on the phone.
