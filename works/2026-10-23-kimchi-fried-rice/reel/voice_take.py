"""One real lab-voice TTS take of the operator's lines, cut on its pauses and sped up like the bot does."""
import json, shutil, sys
from pathlib import Path
from uuid import uuid4
from content_system import config, db, lab_work, llm, reel_voice
from content_system.ledger import Ledger

OUT = Path(sys.argv[1]); OUT.mkdir(parents=True, exist_ok=True)
LINES = json.loads((OUT / "lines.json").read_text(encoding="utf-8"))
cfg = config.for_bot(config.load(), "ai")
ff = shutil.which("ffmpeg")
take = OUT / "take.wav"
if not take.is_file():
    conn = db.connect(cfg.db_path)
    ledger = Ledger(conn, uuid4().hex[:12], cfg.per_run_call_cap, pricing=cfg.llm_pricing)
    take.write_bytes(llm.speak("reel_tts", " <long pause> ".join(LINES), lab_work.VOICE, lab_work.VOICE_STYLE,
                               key=cfg.gemini_key, ledger=ledger))
spans = lab_work._speech_spans(take, ff)
print("spans", len(spans), "lines", len(LINES))
segs = [reel_voice.Seg(a, b, "", True) for a, b in spans]
cut = reel_voice.split(take, segs, OUT / "lines", ffmpeg=ff, pre=0.03, post=0.15)
clips = reel_voice.speed(cut, lab_work.VOICE_TEMPO, OUT / "fast", ffmpeg=ff)
for (p, secs), (a, b) in zip(clips, spans):
    print(f"{p.name} {secs:.2f}s  raw {a:.2f}-{b:.2f}")
