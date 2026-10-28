# Round 6 — operator walk as A (2026-10-28)

Every item below must ship; none may be dropped. Status column is updated by the lead on merge.

## Operator items (verbatim intent) + decisions

| # | Item | Decision / owner | Status |
|---|---|---|---|
| 1 | A's 8→4 pick: toggle background on/off to preview | flow | |
| 2 | A opening A's own link sees only "친구가 아직 안 찍었어요"; small "직접 찍어 보기(테스트용)" button stays until launch (operator pick) | flow | |
| 3 | B after 8→4 + own retouch sends "완성됐어" link to A; that link (and B's own finish screen) opens in Chrome — in-app browsers (KakaoTalk, Threads, Instagram) hand off to Chrome | flow | |
| 4 | In Chrome each side decorates separately (bg, frame, stickers, outline, caption) and saves; decorations are not shared (operator confirmed) | flow | |
| 5 | B's shoot: A is a STILL cut-out (A's picked still for that slot), never a moving clip. Photos are the product; the moving strip is only a final download option | flow | |
| 6 | B's 8→4 pick: background choice A's / B's / none | flow | |
| 7 | Layer order top→bottom: A, B, background (A's / B's / none). Bug: with A's bg, B disappears | flow | |
| 8 | Camera: already widest zoom. Shoot view shows ONLY the area that ends up in the cell (operator pick: cropped view) | layout | |
| 9 | A picks 가로/세로 mode first. 가로 = today's tall strip of 4 wide photos; 세로 = 4 tall photos side by side in one wide strip. B follows A's mode. Portrait phone hold both ways (operator confirmed) | layout | |
| 10 | B's shoot view: option (a) B's real camera background with A's cut-out over it, or (b) A's background + A + B cut-out (order of #7) | flow | |
| 11 | Retouch and decorate: inline pinch zoom + two-finger pan on the photo; the controls under it stay put (no fullscreen viewer) | zoom | |
| 12 | Decorate shows stills; video is only a download option | flow | |
| 13 | Video download button warns up front that the moving cut can stutter / lose the person at times | flow | |
| 14 | Mockups of ALL screens, 3 concepts, each keeping the cover concept (film + 합체, clean) — operator picks before the reskin is finished | mockups | |
| 15 | During the 5 s countdown: front/back switch and mirror stay usable. 8 shots continuous, no stop button | flow | |
| + | Shutter: web capture is silent; add our own shutter sound + flash per shot (etiquette + feedback) | flow | |
| + | Pets: care a lot — stills (done), moving cut (r6-engine), live/B-shoot preview | engine | |

## Carried from the audit (2026-10-28)

| Item | Owner | Status |
|---|---|---|
| Result themes 클린/Y2K/다이어리 | r6-themes (running) | |
| Filters: soft 뽀샤시, two B&W, 3 strengths; light/colour match A↔B; pets in moving cut | r6-engine (running) | |
| HQ moving cut progress display | after flow | |
| Shrink/move my own cut-out (promise 1196) | zoom/decorate | |
| Save photo + video at once; toggle copy; video "0%" label; outline default (r3 review #5-#7) | after flow | |
| Delete public dev-*.html + dev-media before launch | lead | |
| Two-viewpoint film cover | merged e518116 | done |
| Clean reskin | WIP in e518116, finish after #14 pick | |

Deferred on purpose: reels / list tile / manifest (after phone check), ViTMatte fur model (52-104 MB, after phone check).
