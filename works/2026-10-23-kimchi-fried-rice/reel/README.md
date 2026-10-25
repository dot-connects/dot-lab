# Launch reel (v4, 2026-10-25)

Python with Playwright + Pillow + ffmpeg (content-system's venv has them).

1. `python rec2.py take3 42` — plays the game with the scripted bot (`bot.py`) at 390x844 dpr 2.5, one screenshot
   per 1/30 s of game time, plus `timeline.jsonl` (phase, clock, combo, flicks, visible word).
2. `python compose3.py take3 edl3.json reel.mp4 <score>` — top band (fixed title + one changing caption) over a
   975x1350 game panel; captions never sit on the game. Copy to `../out/reel.mp4`.

v4 (round 14) keeps the v3 frame and cuts the whole game flow: `python rec2.py take4 42 "0.92,1.58" 4` (the 4th flick
is a sideways over-swing for the spill beat; the last toss aims at the gold peak so the slow-mo fires; seed 4 =
seeded Math.random, but takes are only roughly repeatable, so check `timeline.jsonl` after recording), then
`python compose4.py take4 edl4.json reel4.mp4 75`. `rec2.py ... probe` runs a take without screenshots.
v5: `python compose4.py take4 edl5.json reel5.mp4 75` (same take, smoother joins, receipt ending).
v6: `python rec2.py take6 44 "0.92,1.58" 4 blur title` then `python compose4.py take6 edl6.json reel6.mp4 73`.
v7: `python rec2.py take8 45 "0.92,1.58" 4 blur title clean` then `python compose4.py take8 edl7.json reel7.mp4 77`.
