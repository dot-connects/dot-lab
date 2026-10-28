# Round 6 — operator walk as A (2026-10-28)

Every item below must ship; none may be dropped. Status column is updated by the lead on merge.

## Operator items (verbatim intent) + decisions

| # | Item | Decision / owner | Status |
|---|---|---|---|
| 1 | A's 8→4 pick: toggle background on/off to preview | flow | done 624fce5 |
| 2 | A opening A's own link sees only "친구가 아직 안 찍었어요"; small "직접 찍어 보기(테스트용)" button stays until launch (operator pick) | flow | done 624fce5 |
| 3 | B after 8→4 + own retouch sends "완성됐어" link to A; that link (and B's own finish screen) opens in Chrome — in-app browsers (KakaoTalk, Threads, Instagram) hand off to Chrome | flow | done 624fce5 |
| 4 | In Chrome each side decorates separately (bg, frame, stickers, outline, caption) and saves; decorations are not shared (operator confirmed) | flow | done 624fce5 |
| 5 | B's shoot: A is a STILL cut-out (A's picked still for that slot), never a moving clip. Photos are the product; the moving strip is only a final download option | flow | done 624fce5 |
| 6 | B's 8→4 pick: background choice A's / B's / none | flow | done 624fce5 |
| 7 | Layer order top→bottom: A, B, background (A's / B's / none). Bug: with A's bg, B disappears | flow | no repro headless; likely the old moving preview (B drawn by clip matte), gone with still-only decorate — check on phone |
| 8 | Camera: already widest zoom. Shoot view shows ONLY the area that ends up in the cell (operator pick: cropped view) | layout | merged (worker deploy + phone check pending) |
| 9 | A picks 가로/세로 mode first. 가로 = today's tall strip of 4 wide photos; 세로 = 4 tall photos side by side in one wide strip. B follows A's mode. Portrait phone hold both ways (operator confirmed) | layout | merged (worker deploy + phone check pending) |
| 10 | B's shoot view: option (a) B's real camera background with A's cut-out over it, or (b) A's background + A + B cut-out (order of #7) | flow | done 624fce5 |
| 11 | Retouch and decorate: inline pinch zoom + two-finger pan on the photo; the controls under it stay put (no fullscreen viewer) | zoom | r6-zoom running |
| 12 | Decorate shows stills; video is only a download option | flow | done 624fce5 |
| 13 | Video download button warns up front that the moving cut can stutter / lose the person at times | flow | done 624fce5 |
| 14 | Mockups of ALL screens, 3 concepts, each keeping the cover concept (film + 합체, clean) — operator picks before the reskin is finished | mockups | mockups/screens3.html fceac70 — awaiting pick |
| 15 | During the 5 s countdown: front/back switch and mirror stay usable. 8 shots continuous, no stop button | flow | done 624fce5 |
| + | Shutter: web capture is silent; add our own shutter sound + flash per shot (etiquette + feedback) | flow | done 624fce5 |
| + | Pets: care a lot — stills (done), moving cut (r6-engine), live/B-shoot preview | engine | done db5949e (filter 약/중/강 + 그레이 chip UI: lead) |
| + | First screen: A picks UI theme 필름레일 (screens3 concept 1) or 귀여운 (new, polished from the pre-reskin look); B follows A (operator 2026-10-28) | r6-cute mockups → lead | mockups running |

## Carried from the audit (2026-10-28)

| Item | Owner | Status |
|---|---|---|
| Result themes 클린/Y2K/다이어리 | r6-themes | done a47e4d7 |
| Filters: soft 뽀샤시, two B&W, 3 strengths; light/colour match A↔B; pets in moving cut | r6-engine | done db5949e |
| HQ moving cut progress display | r6-zoom | running |
| Shrink/move my own cut-out (promise 1196) | r6-zoom | running |
| Save photo + video at once; toggle copy; video "0%" label; outline default (r3 review #5-#7) | r6-zoom | running |
| Delete public dev-*.html + dev-media before launch | lead | |
| Two-viewpoint film cover | merged e518116 | done |
| Clean reskin | WIP in e518116, finish after #14 pick | |

Deferred on purpose: reels / list tile / manifest (after phone check), ViTMatte fur model (52-104 MB, after phone check).
