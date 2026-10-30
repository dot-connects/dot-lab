"""One TTS take of the ad lines per female voice (run with content-system's venv from content-system).
usage: python voice_samples.py <out dir> Leda Zephyr Aoede"""
import json, sys
from pathlib import Path
from uuid import uuid4
from content_system import config, db, llm
from content_system.ledger import Ledger

OUT = Path(sys.argv[1]); OUT.mkdir(parents=True, exist_ok=True)
LINES = json.loads((OUT / "lines.json").read_text(encoding="utf-8"))
import os
STYLE = os.environ.get("STYLE") or ("A bright, cheerful Korean woman in her early twenties talking to her best friend: warm, playful, smiling, "
         "casual banmal, light and natural like a vlog, quick pace, no long pauses, not an announcer.")
TAG = os.environ.get("TAG", "")
cfg = config.for_bot(config.load(), "ai")
conn = db.connect(cfg.db_path)
ledger = Ledger(conn, uuid4().hex[:12], cfg.per_run_call_cap, pricing=cfg.llm_pricing)
for voice in sys.argv[2:]:
    dest = OUT / f"take-{voice}{TAG}.wav"
    if dest.is_file():
        continue
    dest.write_bytes(llm.speak("reel_tts", " <long pause> ".join(LINES), voice, STYLE, key=cfg.gemini_key, ledger=ledger))
    print("ok", voice, dest.stat().st_size)
