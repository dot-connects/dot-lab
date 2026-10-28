# Round 7 — operator phone run after the themes (2026-10-28)

Every item must ship; status updated by the lead on merge.

| # | Item | Decision / owner | Status |
|---|---|---|---|
| 1 | Works on iPhone, especially Chrome (WebKit) | r7-ios audit → fixes; final check on a real iPhone by the operator | audit done (research/ios-audit.md), fixes after r7-fix merge with the perf fixes |
| 2 | Back to the theme pick from the cover | "‹ 테마" before shooting only (operator) — r7-fix | done (r7-fix merge), phone check pending |
| 3 | Retouch screen: background on/off (B: friend's / mine / none) | same buttons as the pick, choice carries over — r7-fix | done (r7-fix merge), phone check pending |
| 4 | Camera preview too small; then every screen's UI one by one | 3 mockups per screen, operator picks, apply, next screen. Order: camera A → camera B → pick → retouch → share → decorate — r7-cam (camera3.html) | mockups running |
| 5 | 8 shots is many | 6 shots, pick 4 (operator) — r7-fix | done (r7-fix merge), phone check pending |
| 6 | Empty white notification with a mint ✕ over the camera on entry | bug — r7-fix | done (r7-fix merge), phone check pending |
| 7 | No moving / scaling A (A's room keeps the original A, A shows twice) | remove all A moves: B's mover + A's own move; B keeps own move (operator) — r7-fix | done (r7-fix merge), phone check pending |
| 8 | B's face retouch does not change the photos on B's review screen | bug — r7-fix | done (r7-fix merge), phone check pending |
| 9 | Decorate 보정 tab: some options make my photo disappear | bug — r7-fix | done (r7-fix merge), phone check pending |
| 10 | Phone gets hot and lags | r7-perf measures first; fixes after r7-fix merges | study done (research/perf-study.md): top 5 = shared WebGL ctx (also a bug: Chrome drops old contexts), one DeepLab in the worker, live cut at 15 fps on new frames, camera off after the last shot, beauty model after B camera |
| 11 | Comfortable on every phone, any aspect ratio | every screen mockup shown at 360x640, 375x667, 390x844, 412x915, 430x932; layout rules by viewport ratio, not fixed px (operator) — r7-cam first, then each screen | running |
