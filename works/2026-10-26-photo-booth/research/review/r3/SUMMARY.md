# Round 3 reviews — summary (2026-10-27)

9 reviewers on the round-3 build (celeb-frame layout, move option, sticker outline, MODNet stills, landing motion):
photo artist, booth photographer, designer, teen girls ×3 (중3 idol fan / 고2 film-aesthetic / 고3 busy), women in their
20s ×3 (하루필름 fan / long-distance couple / fashion creator), plus a speed study for the HQ cut-out. Full texts in this
folder. Nothing was changed in site/ after these reviews — the operator reviews first.

## Agreed by several reviewers (fix candidates, ranked)

| # | Issue | Who | Proposed fix |
|---|---|---|---|
| 1 | Two people lit differently → looks like two stickers | photo artist, photographer, 20s-f1 | match face luma + white balance before compose (L* 62–68, dark skin floor 58, ≤ ±1.2 stop), light wrap, contact shadow |
| 2 | Cut-out still rough: flat bottom cut, chunky/teal fringe, hair mush | 7 of 9 | feather the bottom 12 %, decontaminate every still, extend alpha to cell edge |
| 3 | 뽀샤시 = fog, not 하루필름 skin; 흑백 flat; 필름 ≈ 원본; banding in gradients | photo artist, photographer, teen-f2, 20s-f1 | skin-masked smoothing (keep 35 % texture), +0.3 stop face cap, 3-step strength; B&W split into 포토그레이/시그니처 with S-curve + grain; film grain 9 + vignette; 1–2 % dither |
| 4 | Shoot screen looks nothing like the result (grey box, empty pink, camera ~1/3) | designer, teen-f1 | fill the stage with the video (object-fit: cover), stage min-height 56svh, preview the result look |
| 5 | Confusing copy/controls: move toggle, "원래 자리", video "0%" | 6 of 9 | toggle "친구가 내 위치를 바꿀 수 있게" + plain sub-line; drag handles + one-time coach; reset only after a move; real progress ring |
| 6 | Too many steps after shooting | teen-f3, designer, 20s-f3 | sensible defaults + one sticky "저장" (photo + video together) |
| 7 | Sticker outline split: white glitter cute (2) vs noisy (2) | teen-f1, 20s-f1 vs photo artist, teen-f2 | default = white solid normal + soft shadow; glitter stays an option; drop black glitter |

## Requests worth a decision
- Couple use: anniversary date → auto D+N, "친구"/"애인" wording, couple frames (20s-f2).
- Print-size download 1200×3600 / 300 dpi, vertical face crop (20s-f1, photographer).
- Pose/position guide on the shoot screen (eye line ~38 %, arm length, light) (photographer).
- Link expiry extension; more than 2 people for group chats (20s-f1, teen-f3).
- Neutral/cream mode instead of pink for film-aesthetic users (teen-f2, teen boys earlier).

## HQ cut-out speed (measured, research/cutout-speed/)
| Option | Before → after | Effort |
|---|---|---|
| Prefetch + create session + warm-up on the landing page | ready 27 s after camera → 15.5 s from landing, done while reading | low |
| Input 512×384 instead of 672×512 | 1.33–1.5 s → 0.81–0.9 s per still, quality ~same | one line |
| Self-host the .onnx on Pages + Cache Storage | HF sends no-store; revisit becomes instant | low |
| Move the first-run GPU freeze to the landing | freeze no longer during shooting | low |
| fp16 / int8 / session options | broken or slower — dropped | — |
