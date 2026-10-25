# Launch reel (v3, 2026-10-25)

Python with Playwright + Pillow + ffmpeg (content-system's venv has them).

1. `python rec2.py take3 42` — plays the game with the scripted bot (`bot.py`) at 390x844 dpr 2.5, one screenshot
   per 1/30 s of game time, plus `timeline.jsonl` (phase, clock, combo, flicks, visible word).
2. `python compose3.py take3 edl3.json reel.mp4 <score>` — top band (fixed title + one changing caption) over a
   975x1350 game panel; captions never sit on the game. Copy to `../out/reel.mp4`.
