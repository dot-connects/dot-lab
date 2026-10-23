# Art bible (art-design lead, 2026-10-23)

## 0. Core rules
- Chunky beats accurate (Overcooked: items chunky so they don't get lost). Rice grains ~2x real scale, fewer bigger pieces (~220, not 400+).
- Cool environment desaturated, food saturated. Counter + wok blue-grey; only food and flames carry warm chroma.
- 3-tone cel shading (base, shadow, one hard highlight), no outlines on food. Wok gets one 1px darker rim line.
- Single key light top-left: every highlight upper-left. Fire adds warm bounce from bottom/sides.
- Steam and oil bubbles sell warmth (Ghibli) — particles are half the appetite.

## 1. Palette (hex)
Counter: #E9EDF0 / #C3CBD2 / #8E99A3, brushed streaks rgba(255,255,255,.35) — subtle HORIZONTAL streaks
Wok: base #1E2226, patina #2A3440 (blue-black), heat tint #3B4A6B → #6A5A3A (straw), rim highlight #C9D3DC, handle wood #6B3E22 / #A0673C
Rice: raw #F4EEE2 → sauced #E8743A → glossy #F0552A (+spec #FFF6E8) → golden #D98A2B → burnt #5A2A14 → char #231510
Kimchi: #D7261E base, #FF6A3D lit edge, #F7C9A8 rib, cooked #A8231A
Scallion: #6FBF3A / #2F7A2A, ring centre #CFE9A8
Egg: raw white rgba(255,255,255,.35); set white #FFFDF6, shadow #EADFC8; lace #C98A3E / #8A4E1E; yolk #FFB21A, rim #F28A00, cooked yolk #F5C26B
Flame: base #2F6BFF / #7FB4FF, tongue #FF7A1A → #FFC24A → core #FFF2C2
Steam rgba(255,255,255,.5); smoke #4A4440 → #2A2624
HUD ink #1F2A33, accent #E8481C, success #2BB673, warning #FFC21A, paper #FBF6EA, stamp #C81E1E

## 2. Objects (R = wok radius, ky = cos35 ≈ .82)
- Wok (cache offscreen, redraw only heat overlay): drop shadow, outer wall, rim ring (R·1.04, 0.025R thick, lighter arc 200°–340°), inner bowl radial (centre #3A4350 → patina #2A3440 at .6 → #15181B at wall), straw-blue heat ring (radial R·.55–.8, alpha .15 → .35 with heat), soft specular crescent top-left (white .12, width .15R), 2–3 faint seasoning rings at .3R/.55R (carbon steel, not non-stick).
- Rice grain: rounded capsule 0.045R × 0.022R; cook colour fill; lower-right shade sliver base×0.75; hard spec dot 0.012R upper-left only while glossy (cook .4–1.2). CLUMPS: clusters of 3–6 grains overlapping 60%, one clump shadow ellipse (alpha .25) per cluster — kills the "confetti" read.
- Kimchi: irregular quads 0.07–0.1R, red base, 1px lit edge top-left (#FF6A3D), pale rib on 40%. Cooking: shrink to 85%, darken, gain gloss.
- Scallion: filled rings dark outer + pale centre, 0.035R, plus a few 0.06R slanted strips. <8% of count.
- Fried egg (r ≈ 0.38R): (1) lace skirt 28-pt noisy polygon at r·1.08 #C98A3E with 6–8 holes (destination-out), appears at cook > .6; (2) white = offset blob r·.95, translucent → #FFFDF6, soft #EADFC8 shadow lower-right; (3) yolk dome radial #FFD04A → #FFB21A → rim #F28A00 at r·.32 + cast shadow down-right 0.04r alpha .2; (4) hard specular ellipse upper-left on yolk (white .9) + tiny second dot.
- Underside after flip: golden-brown #D9A050, darker lace, 10–14 small bubble craters.

## 3. Cook-level change
| cook | rice | gloss | particles |
|---|---|---|---|
| 0 raw | pale, matte | none | none |
| .4–1.0 | red-orange | peak spec, wet sheen over clumps | steam wisps, oil spatter |
| 1.0–1.3 | golden, edges toast | fading | little steam, crackle sparks |
| 1.3+ | dark brown → char spots on 30% | none | grey smoke columns = burn signal |

## 4. Flames, steam, smoke
- Flames: 14–18 tongues around the wok, teardrop/hook shapes of varied size. Short blue base (#2F6BFF), orange → yellow → cream core stacked with `lighter`. Flicker height with two summed sines (not random). Orange underglow radial on the wok wall.
- Steam: white, soft, rises/spreads, fades in 0.8 s, pre-rendered radial sprites.
- Smoke: grey-brown, rises slower, curls, lives 2 s, darkens food under it slightly. Alpha ≤ .35 for both.
- Oil spatter: 2–3 px hot-white dots, pop and vanish in 120 ms when food lands.

## 5. HUD and result slip
Fonts: Black Han Sans (big timer, prompts), Jua (friendly labels), Gowun Dodum (body), Nanum Gothic Coding (slip), Song Myung (반점 name).
- Timer: white rounded pill, ink digits, drop shadow. Under 3 s → #E8481C and pulse 1 → 1.12 each second. Pill gauge instead of thin bar.
- Prompts: thick stroke (white 6px behind ink), pop-in scale 0 → 1.2 → 1 over 250 ms, 2° wobble; one at a time, auto-hide.
- Grades ("좋아!"): big yellow with burst star behind.
- Result slip: slides up with overshoot, lines type out (60 ms each), score counts up, stamp slams last (scale 1.6 → 1 in 120 ms, 3px shake, ink specks). Bowl image is the hero at top.

## 6. Signature moments
1. Rice wave: at toss peak grains form an arc sheet, stretched along velocity (velocity-scaled ellipse), +15% value ("in the light"); wok shadow shrinks while rice is airborne.
2. Landing sizzle: 12 spatter dots, steam puff, 1-frame rim highlight flash.
3. Egg flop: squash on landing (y .8 → 1.05 → 1 over 200 ms), yolk wobble (offset spring), lace ripple.
4. Golden moment: when rice hits golden band, one sparkle glint sweeps across the clumps.
5. Stamp slam.

## 7. Current mockup weaknesses (ranked)
1. Rice = confetti (no clumps/shadow, salmon hue, kimchi same size as grains).
2. Egg = flat sticker (no dome, shadow, lace; hard white polygon).
3. Wok looks non-stick (no patina, heat tint, rim highlight; plastic-looking collar).
4. Flames pasted on sides (no blue base, no variety, no wall underglow).
5. Smoke vs steam not distinct.
6. HUD = plain web UI.
7. Slip bowl food = dark noisy speckle; weak stamp.
8. Counter streaks too contrasty/diagonal.

Sources: katom.com Overcooked; itch.io devlog art bible; clip-studio tips 3745; ghibli-museum.jp; creativebloq Ghibli food;
vfxapprentice stylized fire; thewoksoflife wok seasoning; mariowiki Sizzling_Stakes; familygamingdatabase Cooking Mama;
fonts.adobe.com Black Han Sans; AIGA eye on design Korean fonts; fonts.google.com Gowun Dodum.
